package cloud.skadi.ide.plugin

import com.intellij.openapi.components.ServiceManager

interface SkadiCloudTasksService {
    companion object {
        fun getInstance(): SkadiCloudTasksService? {
            return ServiceManager.getService(SkadiCloudTasksService::class.java)
        }
    }
}