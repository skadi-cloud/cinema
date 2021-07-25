package cloud.skadi.gist.mps.plugin

import cloud.skadi.gist.shared.GistVisibility
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import jetbrains.mps.ide.actions.MPSCommonDataKeys
import kotlinx.coroutines.runBlocking
import javax.swing.event.HyperlinkEvent

class CreateGistFromNodeAction : AnAction() {
    val logger = Logger.getInstance(CreateGistFromNodeAction::class.java)
    override fun actionPerformed(e: AnActionEvent) {
        object : Task.Backgroundable(e.project, "Create Gist") {
            override fun run(indicator: ProgressIndicator) {
                runBlocking {
                    val url = upload(
                        "Dummy",
                        "yep",
                        GistVisibility.Public,
                        listOf((e.dataContext.getData(MPSCommonDataKeys.NODE)!! to null)),
                        e.dataContext.getData(MPSCommonDataKeys.CONTEXT_MODEL)!!.repository
                    )
                    val notificationGroup =
                        NotificationGroupManager.getInstance().getNotificationGroup("Skadi Gist")
                    if (url != null) {
                        val content = "Your gist is available <a href=\"$url\">here</a>"
                        notificationGroup.createNotification(
                            "Gist created",
                            content,
                            NotificationType.INFORMATION
                        ) { _, event ->
                            if (event.eventType != HyperlinkEvent.EventType.ACTIVATED) {
                                return@createNotification
                            }
                            if (event.url != null) {
                                BrowserUtil.browse(event.url.toExternalForm())
                            }
                        }.notify(project)
                    } else {
                        notificationGroup.createNotification(
                            "Error creating gist",
                            "Can't create gist. You might not be connected to the internet.",
                            NotificationType.ERROR
                        )
                    }
                }

            }
        }.queue()
    }

    override fun update(e: AnActionEvent) {
        val node = e.dataContext.getData(MPSCommonDataKeys.NODE)
        e.presentation.isEnabled = node != null
    }
}