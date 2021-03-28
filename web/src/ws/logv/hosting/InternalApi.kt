package ws.logv.hosting

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
import ws.logv.hosting.data.getContainerById
import ws.logv.hosting.k8s.appLabel
import java.time.LocalDateTime

suspend fun ApplicationCall.internalApiOnly(body: suspend (ApplicationCall) -> Unit) {
    if (this.request.local.port != INTERNAL_API_PORT) {
        application.log.warn("request to internal api over external port")
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
            val containerId = call.parameters["id"]!!
            val container = getContainerById(containerId)
            if (container == null) {
                log.error("container with $containerId not found!")
                call.respond(HttpStatusCode.NotFound)
                return@internalApiOnly
            }
            val host = call.request.origin.host
            val podIp = client.pods().withLabels(container.id.value.appLabel())
                .list(newListOptions { limit = 1 }).items.firstOrNull()?.status?.podIP

            if (podIp == null) {
                log.error("no ip found for pods of container: $containerId")
                call.respond(HttpStatusCode.NotFound)
                return@internalApiOnly
            }

            if(podIp != host) {
                log.error("ip of the request and the container don't match: $host != $podIp ")
                call.respond(HttpStatusCode.Forbidden)
                return@internalApiOnly
            }

            transaction { container.lastHeartBeat = LocalDateTime.now() }
            call.respond(HttpStatusCode.OK)
        }
    }
}