package cloud.skadi.web.hosting

import cloud.skadi.shared.data.Task
import cloud.skadi.shared.data.TaskContainer
import cloud.skadi.shared.hmac.sign
import cloud.skadi.web.hosting.data.*
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.*

suspend fun ApplicationCall.internalApiOnly(body: suspend (ApplicationCall) -> Unit) {
    if (this.request.local.port != INTERNAL_API_PORT) {
        application.log.warn("request to internal api over external port on ${this.request.uri}")
        this.respond(HttpStatusCode.Forbidden)
        return
    }
    body(this)
}

private val mapper = JsonMapper.builder()
    .addModule(kotlinModule())
    .build()

suspend fun ApplicationCall.checkSignature(container: KernelFContainer): Boolean {
    val params = this.receiveParameters()
    val nonce = params["nonce"]
    val signature = params["signature"]

    if (nonce == null) {
        application.log.error("nonce missing")
        this.respond(HttpStatusCode.BadRequest)
        return false
    }

    if (signature == null) {
        application.log.error("signature missing")
        this.respond(HttpStatusCode.BadRequest)
        return false
    }

    if (!cloud.skadi.shared.hmac.checkNonce(signature, nonce, container.rwToken)) {
        application.log.error("signature mismatch for container ${container.id.value} with signature $signature and nonce $nonce")
        this.respond(HttpStatusCode.BadRequest)
        return false
    }
    return true
}

fun Application.installInternalApi(registry: PrometheusMeterRegistry) = routing {
    get("/health") {
        call.internalApiOnly {
            call.respond(HttpStatusCode.OK)
        }
    }

    get("/metrics") {
        call.internalApiOnly {
            call.respondText {
                registry.scrape()
            }
        }
    }

    post("/heartbeat/{containerId}") {
        call.internalApiOnly {
            val containerId = call.parameters["containerId"]!!
            val container = getContainerById(containerId)
            if (container == null) {
                log.warn("container with $containerId not found!")
                call.respond(HttpStatusCode.NotFound)
                return@internalApiOnly
            }
            if (container.status != ContainerStatus.Running) {
                log.warn("container isn't running but got heartbeat: $containerId")
            }

            if (call.request.header("X-Heartbeat-Version") == "2") {
                if (!call.checkSignature(container)) {
                    return@internalApiOnly
                }
            } else {
                val token = call.receiveText()
                if (token != container.rwToken) {
                    log.error("token did not match expected: ${container.rwToken} but got $token")
                    call.respond(HttpStatusCode.Forbidden)
                    return@internalApiOnly
                }
            }

            transaction { container.lastHeartBeat = LocalDateTime.now() }
            call.respond(HttpStatusCode.OK)
        }
    }
    post("/tasks/{containerId}/dequeue") {
        call.internalApiOnly {
            val containerId = call.parameters["containerId"]!!
            val container = getContainerById(containerId)
            if (container == null) {
                log.warn("container with $containerId not found!")
                call.respond(HttpStatusCode.NotFound)
                return@internalApiOnly
            }
            if (container.status != ContainerStatus.Running) {
                log.warn("container isn't running but got task request: $containerId")
            }
            if (!call.checkSignature(container)) {
                return@internalApiOnly
            }
            newSuspendedTransaction {
                val task = peekTask(container)
                if (task == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@newSuspendedTransaction
                }
                val innerTask: Task = mapper.readValue(task.data)
                innerTask.id = task.id.value
                val serializedTask = withContext(Dispatchers.IO) {
                    mapper.writeValueAsString(innerTask)
                }
                val signature = sign(container.rwToken, serializedTask)
                val taskContainer = TaskContainer(serializedTask, signature)
                call.respondText(contentType = ContentType.Application.Json) {
                    mapper.writeValueAsString(taskContainer)
                }
            }
        }
    }
    post("/tasks/{taskId}/error") {
        call.internalApiOnly {
            val taskId = call.parameters["taskId"]!!
            newSuspendedTransaction {
                val taskEntry = getTask(UUID.fromString(taskId))
                if (taskEntry == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@newSuspendedTransaction
                }

                if (!call.checkSignature(taskEntry.instance)) {
                    return@newSuspendedTransaction
                }

                if (taskEntry.state == TaskState.Failed) {
                    call.respond(HttpStatusCode.NotModified)
                    return@newSuspendedTransaction
                } else if (taskEntry.state != TaskState.InProgress) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@newSuspendedTransaction
                }

                taskEntry.lastChange = LocalDateTime.now()
                taskEntry.state = TaskState.Failed
                call.respond(HttpStatusCode.OK)
            }
        }
    }

    post("/tasks/{taskId}/success") {
        call.internalApiOnly {
            val taskId = call.parameters["taskId"]!!
            newSuspendedTransaction {
                val taskEntry = getTask(UUID.fromString(taskId))
                if (taskEntry == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@newSuspendedTransaction
                }

                if (!call.checkSignature(taskEntry.instance)) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@newSuspendedTransaction
                }
                if (taskEntry.state == TaskState.Failed) {
                    call.respond(HttpStatusCode.NotModified)
                    return@newSuspendedTransaction
                } else if (taskEntry.state != TaskState.InProgress) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@newSuspendedTransaction
                }
                taskEntry.lastChange = LocalDateTime.now()
                taskEntry.state = TaskState.Succeeded
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}