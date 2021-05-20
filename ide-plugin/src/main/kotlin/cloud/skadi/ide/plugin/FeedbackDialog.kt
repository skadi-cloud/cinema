package cloud.skadi.ide.plugin

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.layout.GrowPolicy
import com.intellij.ui.layout.panel
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent

data class FeedbackResult(
    var name: String = "",
    var email: String = "",
    var message: String = "",
    var includeLogs: Boolean = false
)

class FeedbackDialog(project: Project?) : DialogWrapper(project) {
    val result = FeedbackResult()

    init {
        init()
        title = "Submit Feedback"
    }
    override fun createCenterPanel(): JComponent {
        return panel {
            row {
                label("Your feedback will be processed using ")
                link("sentry.io") {
                    BrowserUtil.browse("https://sentry.io/privacy/")
                }
            }
            row("Name:") {
                textField(result::name)
            }
            row("Email:") {
                textField(result::email)
            }
            row("Message:") {
                textField(result::message).growPolicy(GrowPolicy.MEDIUM_TEXT)
            }
            titledRow("Logs") {
                checkBox("Include logs:", result::includeLogs)
                label(
                    "Logs might include sensitive information like file names or URLs.",
                    UIUtil.ComponentStyle.SMALL,
                    UIUtil.FontColor.BRIGHTER
                )
            }
        }
    }
}