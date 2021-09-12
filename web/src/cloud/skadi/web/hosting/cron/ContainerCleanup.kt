package cloud.skadi.web.hosting.cron

import cloud.skadi.web.hosting.data.*
import cloud.skadi.web.hosting.routing.undeployContainer
import io.fabric8.kubernetes.client.KubernetesClient
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.TickerMode
import kotlinx.coroutines.channels.ticker
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

@ObsoleteCoroutinesApi
val markTimer = ticker(600_000, mode = TickerMode.FIXED_DELAY)

@ObsoleteCoroutinesApi
suspend fun markInstanceForSweep() {
    val logger = LoggerFactory.getLogger("markInstanceForSweep")

    for (tick in markTimer) {
        newSuspendedTransaction {
            KernelFContainer.find {
                ((KernelFContainers.status eq ContainerStatus.Stopped) or
                        (KernelFContainers.status eq ContainerStatus.Error)) and
                        (KernelFContainers.lastHeartBeat less LocalDateTime.now().minusDays(30)) and
                        (KernelFContainers.scheduledForDeletion.isNull())
            }.apply {
                logger.info("Marking ${this.copy()} for deletion in 14 days.")
            }
                .forUpdate()
                .forEach { it.scheduledForDeletion = LocalDateTime.now().plusDays(14) }
        }
    }
}

@ObsoleteCoroutinesApi
val deleteTimer = ticker(3600_000, mode = TickerMode.FIXED_DELAY)

@ObsoleteCoroutinesApi
suspend fun sweepContainers(client: KubernetesClient) {
    val logger = LoggerFactory.getLogger("sweepContainers")

    for(tick in deleteTimer) {
        newSuspendedTransaction {
            KernelFContainer.find {
                (KernelFContainers.scheduledForDeletion less LocalDateTime.now())
            }.forUpdate()
                .forEach {
                    logger.warn("deleting ${it.name} (${it.id}) from user ${it.user.id}")
                    undeployContainer(client, it.id.value)
                    deleteContainerById(it.id.value)
                    logEvent(EventType.Deleted, it, data = "container inactive for to long")
                }
        }
    }
}