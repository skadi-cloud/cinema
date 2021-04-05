package cloud.skadi.ide.plugin

import com.intellij.openapi.application.PreloadingActivity
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator

class SkadiInit : PreloadingActivity() {
    private val logger = Logger.getInstance(this::class.java)
    override fun preload(indicator: ProgressIndicator) {
        val instance = SkadiHeartbeatService.getInstance()
        if(instance == null) {
            logger.error("Can't get service")
        }
    }
}