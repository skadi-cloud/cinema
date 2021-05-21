package cloud.skadi.web.hosting

import cloud.skadi.web.hosting.data.*
import cloud.skadi.web.hosting.routing.getPodStatus
import cloud.skadi.web.hosting.routing.pauseContainer
import cloud.skadi.web.hosting.turbo.sendTurboChannelUpdate
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

@ExperimentalStdlibApi
@ObsoleteCoroutinesApi
suspend fun updateNewContainers(client : KubernetesClient) {
    val logger = LoggerFactory.getLogger("updateNewContainers")
    for (tick in containerStatusTicker) {
        newSuspendedTransaction {
            KernelFContainer.find {
                (KernelFContainers.status eq ContainerStatus.Stopping) or (KernelFContainers.status eq ContainerStatus.Deploying)
            }.mapNotNull {
                when (it.status) {
                    ContainerStatus.Created -> null
                    ContainerStatus.Error -> try {
                        Pair(it, getPodStatus(client, it.id.value))
                    } catch (e: Exception) {
                        logger.error("error updating pod status for ${it.id.value}", e)
                        null
                    }
                    ContainerStatus.Stopped -> null
                    ContainerStatus.Stopping -> try {
                        Pair(it, getPodStatus(client, it.id.value))
                    } catch (e: Exception) {
                        logger.error("error updating pod status for ${it.id.value}", e)
                        null
                    }
                    ContainerStatus.Deploying -> try {
                        Pair(it, getPodStatus(client, it.id.value))
                    } catch (e: Exception) {
                        logger.error("error updating pod status for ${it.id.value}", e)
                        null
                    }
                    ContainerStatus.Running -> null
                }
            }.filter { it.first.status != it.second }.forEach {
                it.first.status = it.second
                sendTurboChannelUpdate(it.first.id.value.toString(), it.first)
            }
        }
    }
}


@ExperimentalStdlibApi
@ObsoleteCoroutinesApi
suspend fun updateRunningContainers(client :KubernetesClient) {
    val logger = LoggerFactory.getLogger("updateRunningContainers")
    for (tick in runningContainerStatusTicker) {
        newSuspendedTransaction {
            KernelFContainer.find {
                (KernelFContainers.status eq ContainerStatus.Running)
            }.mapNotNull {
                when (it.status) {
                    ContainerStatus.Created -> null
                    ContainerStatus.Error -> null
                    ContainerStatus.Stopped -> try {
                        Pair(it, getPodStatus(client, it.id.value))
                    } catch (e: Exception) {
                        logger.error("error updating pod status for ${it.id.value}", e)
                        null
                    }
                    ContainerStatus.Stopping -> null
                    ContainerStatus.Deploying -> null
                    ContainerStatus.Running -> try {
                        Pair(it, getPodStatus(client, it.id.value))
                    } catch (e: Exception) {
                        logger.error("error updating pod status for ${it.id.value}", e)
                        null
                    }
                }
            }.filter { it.first.status != it.second }.forEach {
                it.first.status = it.second
                sendTurboChannelUpdate(it.first.id.value.toString(), it.first)
            }
        }
        newSuspendedTransaction {
            KernelFContainer.find {
                (KernelFContainers.lastHeartBeat less (LocalDateTime.now()
                    .minusMinutes(30))) and (KernelFContainers.status eq ContainerStatus.Running)
            }.forEach {
                it.status = ContainerStatus.Stopping
                pauseContainer(client, it.id.value)
                GlobalScope.launch {
                    logEvent(EventType.Paused, it, data = "heartbead timeout")
                }
                sendTurboChannelUpdate(it.id.value.toString(), it)
            }
        }
    }
}