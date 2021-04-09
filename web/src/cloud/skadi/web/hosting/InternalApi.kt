package cloud.skadi.web.hosting

import cloud.skadi.web.hosting.data.ContainerStatus
import com.fkorotkov.kubernetes.newListOptions
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import io.micrometer.prometheus.PrometheusMeterRegistry
import org.jetbrains.exposed.sql.transactions.transaction
import cloud.skadi.web.hosting.data.getContainerById
import cloud.skadi.web.hosting.k8s.appLabel
import io.ktor.util.*
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
                call.respond(HttpStatusCode.Forbidden)
                return@internalApiOnly
            }
            val token = call.receiveText()
            if (token != container.rwToken) {
                log.error("token did not match expected: ${container.rwToken} but got $token")
                call.respond(HttpStatusCode.Forbidden)
                return@internalApiOnly
            }
            transaction { container.lastHeartBeat = LocalDateTime.now() }
            call.respond(HttpStatusCode.OK)
        }
    }
}