package cloud.skadi.web.hosting.routing

import cloud.skadi.web.hosting.HOME_PATH
import cloud.skadi.web.hosting.data.*
import cloud.skadi.web.hosting.k8s.*
import cloud.skadi.web.hosting.respondSeeOther
import cloud.skadi.web.hosting.session
import cloud.skadi.web.hosting.views.AppTemplate
import cloud.skadi.web.hosting.views.REDIRECT_AFTER_CREATE
import cloud.skadi.web.hosting.views.confirmDelete
import com.fkorotkov.kubernetes.newListOptions
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import io.ktor.application.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.seruco.encoding.base62.Base62
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.ByteBuffer
import java.time.LocalDateTime
import java.util.*

val CONTAINER_LATEST = ContainerVersion.V2020_3_4731_f5286c0
fun Application.containerApi(client: KubernetesClient) = routing {
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
            val container = createContainer(user, version, rwToken, roToken)
            val dbUser = getUserById(user)
            logEvent(EventType.Created, container, dbUser)
            transaction { container.status = ContainerStatus.Deploying }
            deployContainer(client, container)
            logEvent(EventType.Started, container, dbUser)

            val redirectTarget = params[REDIRECT_AFTER_CREATE] ?: HOME_PATH
            call.respondSeeOther(redirectTarget)
        }

    }

    get("/container/confirm/delete/{id}") {
        call.authenticated {
            call.withUserContainerViaParam { container ->
                call.respondHtmlTemplate(AppTemplate("Skadi Cloud")) {
                    content { transaction { confirmDelete(container.user, container) } }
                }
            }
        }
    }
    post("/container/{id}/delete") {
        call.authenticated {
            call.withUserContainerViaParam { container ->
                undeployContainer(client, container.id.value)
                deleteContainerById(container.id.value)
                logEvent(EventType.Deleted, container, getUserById(call.session!!.email))
                call.respondSeeOther(HOME_PATH)
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
                    startContainer(client, container.id.value)
                    logEvent(EventType.Started, container, getUserById(call.session!!.email))
                }
                call.respondSeeOther(HOME_PATH)
            }
        }
    }
    post("/container/{id}/stop") {
        call.authenticated {
            call.withUserContainerViaParam { container ->
                if (canStopContainer(container)) {
                    transaction { container.status = ContainerStatus.Stopping }
                    pauseContainer(client, container.id.value)
                    logEvent(EventType.Paused, container, getUserById(call.session!!.email))
                }
                call.respondSeeOther(HOME_PATH)
            }
        }
    }
    post("/container/{id}/edit") {
        call.authenticated {
            call.withUserContainerViaParam { container ->
                val params = call.receiveParameters()
                val versionParam = params["version"]!!

                val version = enumValueOf<ContainerVersion>(versionParam)
                if (container.version != version) {
                    transaction {
                        container.version = version
                        container.status = ContainerStatus.Deploying
                    }
                    logEvent(EventType.Updated, container, getUserById(call.session!!.email), "new version: $version")
                    updateContainer(client, container)
                }
                call.respondSeeOther(HOME_PATH)
            }
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

fun pauseContainer(client: KubernetesClient, id: UUID) {
    client.apps().deployments().withName(deploymentName(id)).scale(0)
}

fun startContainer(client: KubernetesClient, id: UUID) {
    client.apps().deployments().withName(deploymentName(id)).scale(1)
}


fun deployContainer(
    client: KubernetesClient,
    id: UUID,
    kernelFVersion: String,
    rwToken: String,
    roToken: String
) {
    client.persistentVolumeClaims().create(MPSInstancePVC(id))
    client.apps().deployments().create(MPSInstanceDeployment(id, kernelFVersion, rwToken, roToken))
    client.services().create(MPSInstanceService(id))
    client.network().ingresses().create(MPSInstanceIngress(id))
}

fun deployContainer(client: KubernetesClient, container: KernelFContainer) {
    deployContainer(client, container.id.value, container.version.tag, container.rwToken, container.roToken)
}

fun updateContainer(client: KubernetesClient, container: KernelFContainer) {
    client.apps().deployments().withName(deploymentName(container.id.value)).rolling()
        .updateImage(containerImage(container.version.tag))
}


fun undeployContainer(client: KubernetesClient, id: UUID) {
    client.network().ingresses().delete(MPSInstanceIngress(id))
    client.services().delete(MPSInstanceService(id))
    client.apps().deployments().delete(MPSInstanceDeployment(id, "", "", ""))
    client.persistentVolumeClaims().delete(MPSInstancePVC(id))
}

fun getPodStatus(client: KubernetesClient, id: UUID): ContainerStatus {
    val deployment = client.apps().deployments().withName(deploymentName(id)).get() ?: return ContainerStatus.Deploying

    if (deployment.spec.replicas == 0 && deployment.status.replicas == null) {
        return ContainerStatus.Stopped
    }

    val pod = client.pods().withLabels(id.appLabel()).list(newListOptions { limit = 1 }).items.firstOrNull()
        ?: return ContainerStatus.Error

    if (pod.status.phase == "Failed") {
        return ContainerStatus.Error
    }

    if (pod.status.phase == "Pending") {
        return ContainerStatus.Deploying
    }

    if (pod.status.conditions.find { it.type == "Ready" }?.status == "True") {
        return ContainerStatus.Running
    }
    return ContainerStatus.Deploying
}

