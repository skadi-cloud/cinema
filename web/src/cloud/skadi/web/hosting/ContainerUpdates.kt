package cloud.skadi.web.hosting

import cloud.skadi.web.hosting.data.ContainerStatus
import cloud.skadi.web.hosting.data.KernelFContainer
import cloud.skadi.web.hosting.data.KernelFContainers
import cloud.skadi.web.hosting.views.instanceControls
import cloud.skadi.web.hosting.views.instanceStatusFrameContent
import cloud.skadi.web.hosting.views.template
import cloud.skadi.web.hosting.views.turboStream
import io.ktor.http.cio.websocket.*
import kotlinx.html.stream.createHTML
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

suspend fun updateNewContainers() {
    for (tick in containerStatusTicker) {
        val updatesToSend = transaction {
            KernelFContainer.find {
                (KernelFContainers.status eq ContainerStatus.Stopping) or (KernelFContainers.status eq ContainerStatus.Deploying)
            }.mapNotNull {
                when (it.status) {
                    ContainerStatus.Created -> null
                    ContainerStatus.Error -> null
                    ContainerStatus.Stopped -> null
                    ContainerStatus.Stopping -> Pair(it, getPodStatus(it.id.value))
                    ContainerStatus.Deploying -> Pair(it, getPodStatus(it.id.value))
                    ContainerStatus.Running -> null
                }
            }.filter { it.first.status != it.second }.map {
                it.first.status = it.second
                Pair(it.first.user.id.value, listOf(instanceStatusUpdate(it.first), instanceControlsUpdate(it.first)))
            }
        }
        updatesToSend.forEach { sendUpdatesTo(it.first, it.second) }
    }
}

private suspend fun sendUpdatesTo(user: Int, updates: List<String>) {
    try {
        println("trying to send updates to user $user")
        val channel = getChannelToUser(user) ?: return
        print("sending update to user $user")
        updates.forEach {
            channel.send(Frame.Text(it))
        }
    } catch (e: Throwable) {
        println("error sending update ${e.message}")
    }
}

private fun instanceStatusUpdate(it: KernelFContainer) =
    createHTML().turboStream {
        target = "status-${it.id.value}"
        action = "update"
        template {
            instanceStatusFrameContent(it)
        }
    }

private fun instanceControlsUpdate(it: KernelFContainer) =
    createHTML().turboStream {
        target = "controls-${it.id.value}"
        action = "update"
        template {
            instanceControls(it)
        }
    }

suspend fun updateRunningContainers() {
    for (tick in runningContainerStatusTicker) {
        var updatesToSend = transaction {
            KernelFContainer.find {
                (KernelFContainers.status eq ContainerStatus.Running)
            }.mapNotNull {
                when (it.status) {
                    ContainerStatus.Created -> null
                    ContainerStatus.Error -> null
                    ContainerStatus.Stopped -> null
                    ContainerStatus.Stopping -> null
                    ContainerStatus.Deploying -> null
                    ContainerStatus.Running -> Pair(it, getPodStatus(it.id.value))
                }
            }.filter { it.first.status != it.second }.map {
                it.first.status = it.second
                Pair(it.first.user.id.value, listOf(instanceStatusUpdate(it.first), instanceControlsUpdate(it.first)))
            }
        }
        updatesToSend = updatesToSend + transaction {
            KernelFContainer.find {
                (KernelFContainers.lastHeartBeat less (LocalDateTime.now()
                    .minusMinutes(30))) and (KernelFContainers.status eq ContainerStatus.Running)
            }.map {
                it.status = ContainerStatus.Stopping
                pauseContainer(it.id.value)
                Pair(it.user.id.value, listOf(instanceStatusUpdate(it), instanceControlsUpdate(it)))
            }
        }
        updatesToSend.forEach { sendUpdatesTo(it.first, it.second) }
    }
}