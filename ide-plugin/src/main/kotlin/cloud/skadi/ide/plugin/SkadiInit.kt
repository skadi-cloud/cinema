package cloud.skadi.ide.plugin

import com.intellij.openapi.application.PreloadingActivity
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator

class SkadiInit : PreloadingActivity() {
    private val logger = Logger.getInstance(this::class.java)
    override fun preload(indicator: ProgressIndicator) {
        val heartbeatService = SkadiHeartbeatService.getInstance()
        if(heartbeatService == null) {
            logger.error("Can't get heartbeat service")
        }
        val cloudTasksService = SkadiCloudTasksService.getInstance()
        if(cloudTasksService == null) {
            logger.error("Can't get tasks service")
        }
    }
}