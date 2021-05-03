package cloud.skadi.ide.plugin

import com.intellij.openapi.components.ServiceManager

interface SkadiHeartbeatService {
    companion object {
        fun getInstance(): SkadiHeartbeatService? {
            return ServiceManager.getService(SkadiHeartbeatService::class.java)
        }
    }
    fun acquireActivityLock()
    fun releaseActivityLock()
}