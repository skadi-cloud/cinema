package cloud.skadi.web.hosting

import cloud.skadi.web.hosting.data.ContainerStatus
import cloud.skadi.web.hosting.data.getContainerById
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.micrometer.prometheus.PrometheusMeterRegistry
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

suspend fun ApplicationCall.internalApiOnly(body: suspend (ApplicationCall) -> Unit) {
    if (this.request.local.port != INTERNAL_API_PORT) {
        application.log.warn("request to internal api over external port on ${this.request.uri}")
        this.respond(HttpStatusCode.Forbidden)
        return
    }
    body(this)
}

private val client = DefaultKubernetesClient().inNamespace("default")!!

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
                val params = call.receiveParameters()
                val nonce = params["nonce"]
                val signature = params["signature"]

                if (nonce == null) {
                    log.error("nonce missing")
                    call.respond(HttpStatusCode.BadRequest)
                    return@internalApiOnly
                }

                if (signature == null) {
                    log.error("signature missing")
                    call.respond(HttpStatusCode.BadRequest)
                    return@internalApiOnly
                }

                if (!cloud.skadi.shared.hmac.checkNonce(signature, nonce, container.rwToken)) {
                    log.error("signature mismatch for container $containerId with signature $signature and nonce $nonce")
                    call.respond(HttpStatusCode.BadRequest)
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
}