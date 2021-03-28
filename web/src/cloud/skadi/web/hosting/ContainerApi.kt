package cloud.skadi.web.hosting

import cloud.skadi.web.hosting.data.*
import cloud.skadi.web.hosting.k8s.*
import com.fkorotkov.kubernetes.*
import com.fkorotkov.kubernetes.apps.*
import com.fkorotkov.kubernetes.networking.v1beta1.*
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.ktor.application.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*


@Suppress("EnumEntryName")
enum class KernelFVersion(val tag: String) {
    V2020_3_4731_f5286c0("2020.3.4731.f5286c0"),
}

val KERNELF_LATEST = KernelFVersion.V2020_3_4731_f5286c0

fun Application.containerApi() = routing {
    post("/new-container") {
        if (call.session == null) {
            call.respondRedirect("/")
            return@post
        }
        val user = call.session!!.email
        if (!canCreateContainer(user)) {
            call.respond(HttpStatusCode.ServiceUnavailable, "You can't create more containers!")
            return@post
        }

        val container = createContainer(getName(), user, KERNELF_LATEST.tag)
        transaction { container.status = ContainerStatus.Deploying }
        deployContainer(container.id.value, KERNELF_LATEST.tag)
        call.respondRedirect(HOME_PATH)
    }

    post("/container/{id}/delete") {
        if (call.session == null) {
            call.respondRedirect("/")
            return@post
        }

        val containerId = call.parameters["id"]!!
        val container = getContainerById(containerId)
        if (container == null) {
            call.respond(HttpStatusCode.NotFound)
            return@post
        }
        undeployContainer(container.id.value)
        deleteContainerById(containerId)
        call.respondRedirect(HOME_PATH)
    }
    post("/container/{id}/start") {
        if (call.session == null) {
            call.respondRedirect("/")
            return@post
        }
        val containerId = call.parameters["id"]!!
        val container = getContainerById(containerId)
        if (container == null) {
            call.respond(HttpStatusCode.NotFound, "unknown container")
            return@post
        }

        if (canStartContainer(container)) {
            transaction { container.status = ContainerStatus.Deploying }
            startContainer(container.id.value)
        }
        call.respondRedirect(HOME_PATH)
    }
    post("/container/{id}/stop") {
        if (call.session == null) {
            call.respondRedirect("/")
            return@post
        }

        val containerId = call.parameters["id"]!!
        val container = getContainerById(containerId)

        if (container == null) {
            call.respond(HttpStatusCode.NotFound, "unknown container")
            return@post
        }

        if (canStopContainer(container)) {
            transaction { container.status = ContainerStatus.Stopping }
            pauseContainer(container.id.value)
        }

        call.respondRedirect(HOME_PATH)
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

fun deployContainer(id: UUID, kernelFVersion: String) = GlobalScope.launch {
    client.persistentVolumeClaims().create(KernelFInstancePVC(id))
    client.apps().deployments().create(KernelFInstanceDeployment(id, kernelFVersion))
    client.services().create(KernelFInstanceService(id))
    client.network().ingresses().create(KernelFInstanceIngress(id))
}

fun undeployContainer(id: UUID) = GlobalScope.launch {
    client.network().ingresses().delete(KernelFInstanceIngress(id))
    client.services().delete(KernelFInstanceService(id))
    client.apps().deployments().delete(KernelFInstanceDeployment(id, ""))
    client.persistentVolumeClaims().delete(KernelFInstancePVC(id))
}

fun getPodStatus(id: UUID): ContainerStatus {
    val deployment = client.apps().deployments().withName(deploymentName(id)).get()

    if (deployment.spec.replicas == 0 && deployment.status.replicas == null) {
        return ContainerStatus.Stopped
    }

    val pod = client.pods().withLabels(id.appLabel()).list(newListOptions { limit = 1 }).items.firstOrNull() ?: return ContainerStatus.Error
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

