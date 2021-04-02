package cloud.skadi.web.hosting

import cloud.skadi.web.hosting.data.*
import cloud.skadi.web.hosting.k8s.*
import cloud.skadi.web.hosting.views.IndexTemplate
import cloud.skadi.web.hosting.views.confirmDelete
import com.fkorotkov.kubernetes.*
import com.fkorotkov.kubernetes.apps.*
import com.fkorotkov.kubernetes.networking.v1beta1.*
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.ktor.application.*
import io.ktor.client.request.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.*
import io.seruco.encoding.base62.Base62
import java.nio.ByteBuffer

val CONTAINER_LATEST = ContainerVersion.V2020_3_4731_f5286c0

suspend fun ApplicationCall.authenticated(body: suspend () -> Unit) {
    if (this.session == null) {
        this.respondRedirect("/")
        return
    }
    body()
}

suspend fun ApplicationCall.withUserContainerViaParam(body: suspend (KernelFContainer) -> Unit) {
    val containerId = this.parameters["id"]!!
    val container = getContainerById(containerId)
    if (container == null) {
        this.respond(HttpStatusCode.NotFound)
        return
    }

    val isUsersContainer = transaction {
        this@withUserContainerViaParam.session?.email == container.user.email
    }
    if (!isUsersContainer) {
        this.respond(HttpStatusCode.Forbidden)
        return
    }
    body(container)
}

fun Application.containerApi() = routing {
    post("/new-container") {
        call.authenticated {
            val user = call.session!!.email
            if (!canCreateContainer(user)) {
                call.respond(HttpStatusCode.ServiceUnavailable, "You can't create more containers!")
                return@authenticated
            }

            val params = call.receiveParameters()
            val versionParam = params["version"]

            var version = CONTAINER_LATEST
            if (versionParam != null) {
                version = enumValueOf(versionParam)
            }

            val base62 = Base62.createInstance()

            val roId = UUID.randomUUID()
            val roToken = base62.encode(
                ByteBuffer.allocate(16).putLong(roId.mostSignificantBits).putLong(roId.leastSignificantBits).array()
            ).decodeToString()
            val rwId = UUID.randomUUID()
            val rwToken = base62.encode(
                ByteBuffer.allocate(16).putLong(rwId.mostSignificantBits).putLong(rwId.leastSignificantBits).array()
            ).decodeToString()
            val container = createContainer(getName(), user, version, rwToken, roToken)
            transaction { container.status = ContainerStatus.Deploying }
            deployContainer(container.id.value, CONTAINER_LATEST.tag, rwToken, roToken)
            call.respondRedirect(HOME_PATH)
        }

    }

    get("/container/confirm/delete/{id}") {
        call.authenticated {
            call.withUserContainerViaParam { container ->
                call.respondHtmlTemplate(IndexTemplate("Skadi Cloud")) {
                    content { confirmDelete(container) }
                }
            }
        }
    }
    post("/container/{id}/delete") {
        call.authenticated {
            call.withUserContainerViaParam { container ->
                undeployContainer(container.id.value)
                deleteContainerById(container.id.value)
                call.respondRedirect(HOME_PATH)
            }
        }
    }
    post("/container/{id}/start") {
        call.authenticated {
            call.withUserContainerViaParam { container ->
                if (canStartContainer(container)) {
                    transaction {
                        container.status = ContainerStatus.Deploying
                        container.lastHeartBeat = LocalDateTime.now()
                    }
                    startContainer(container.id.value)
                }
                call.respondRedirect(HOME_PATH)
            }
        }
    }
    post("/container/{id}/stop") {
        call.authenticated {
            call.withUserContainerViaParam { container ->
                if (canStopContainer(container)) {
                    transaction { container.status = ContainerStatus.Stopping }
                    pauseContainer(container.id.value)
                }

                call.respondRedirect(HOME_PATH)
            }
        }
        if (call.session == null) {
            call.respondRedirect("/")
            return@post
        }
    }
}

fun canStopContainer(container: KernelFContainer): Boolean {
    val status = container.status
    return status != ContainerStatus.Stopped && status != ContainerStatus.Stopping
}

fun canStartContainer(container: KernelFContainer): Boolean {
    val status = container.status
    return status == ContainerStatus.Stopped
}

private val client = DefaultKubernetesClient().inNamespace("default")!!

fun pauseContainer(id: UUID) = GlobalScope.launch {
    client.apps().deployments().withName(deploymentName(id)).scale(0)
}

fun startContainer(id: UUID) = GlobalScope.launch {
    client.apps().deployments().withName(deploymentName(id)).scale(1)
}

fun deployContainer(id: UUID, kernelFVersion: String, rwToken: String, roToken: String) = GlobalScope.launch {
    client.persistentVolumeClaims().create(MPSInstancePVC(id))
    client.apps().deployments().create(MPSInstanceDeployment(id, kernelFVersion, rwToken, roToken))
    client.services().create(MPSInstanceService(id))
    client.network().ingresses().create(MPSInstanceIngress(id))
}

fun undeployContainer(id: UUID) = GlobalScope.launch {
    client.network().ingresses().delete(MPSInstanceIngress(id))
    client.services().delete(MPSInstanceService(id))
    client.apps().deployments().delete(MPSInstanceDeployment(id, "", "", ""))
    client.persistentVolumeClaims().delete(MPSInstancePVC(id))
}

fun getPodStatus(id: UUID): ContainerStatus {
    val deployment = client.apps().deployments().withName(deploymentName(id)).get()

    if (deployment.spec.replicas == 0 && deployment.status.replicas == null) {
        return ContainerStatus.Stopped
    }

    val pod = client.pods().withLabels(id.appLabel()).list(newListOptions { limit = 1 }).items.firstOrNull()
        ?: return ContainerStatus.Error
    val state = pod.status.containerStatuses.firstOrNull() ?: return ContainerStatus.Deploying

    if (state.state.waiting != null) {
        return ContainerStatus.Deploying
    }
    if (state.state.terminated != null) {
        return ContainerStatus.Error
    }
    if (state.state.running != null) {
        return ContainerStatus.Running
    }
    return ContainerStatus.Error
}

