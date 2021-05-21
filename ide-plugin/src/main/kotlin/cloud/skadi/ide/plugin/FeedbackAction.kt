package cloud.skadi.ide.plugin

import com.intellij.diagnostic.PerformanceWatcher
import com.intellij.ide.troubleshooting.CompositeGeneralTroubleInfoCollector
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.troubleshooting.TroubleInfoCollector
import com.intellij.util.SystemProperties
import com.intellij.util.io.Compressor
import io.sentry.Attachment
import io.sentry.Sentry
import io.sentry.UserFeedback
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*

class FeedbackAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {

        val feedbackDialog = FeedbackDialog(e.project)
        if (feedbackDialog.showAndGet()) {
            val (name, email, message, includeLogs) = feedbackDialog.result
            Sentry.init()
            Sentry.withScope { scope ->
                if (includeLogs) {
                    val troubleshooting = collectInfoFromExtensions(e.project)
                    PerformanceWatcher.getInstance().dumpThreads("", false)
                    val zip = createZip(troubleshooting)
                    val fileAttachment = Attachment(zip.absolutePath)
                    scope.addAttachment(fileAttachment)
                }

                val sentryId = Sentry.captureMessage("User Feedback")
                val userFeedback = UserFeedback(sentryId).apply {
                    comments = message
                    this.email = email
                    this.name = name
                }
                Sentry.captureUserFeedback(userFeedback)
            }
        }
    }

    private fun createZip(troubleshooting: StringBuilder?): File {
        val productName = StringUtil.toLowerCase(ApplicationNamesInfo.getInstance().lowercaseProductName)
        val zippedLogsFile = FileUtil.createTempFile("$productName-logs-$date", ".zip")
        try {
            Compressor.Zip(zippedLogsFile).use { zip ->
                zip.addDirectory(File(PathManager.getLogPath()))
                if (troubleshooting != null) {
                    zip.addFile(
                        "troubleshooting.txt",
                        troubleshooting.toString().toByteArray(StandardCharsets.UTF_8)
                    )
                }
                for (javaErrorLog in javaErrorLogs) {
                    zip.addFile(javaErrorLog.name, javaErrorLog)
                }
            }
        } catch (exception: IOException) {
            FileUtil.delete(zippedLogsFile)
            throw exception
        }
        return zippedLogsFile
    }

    private fun collectInfoFromExtensions(project: Project?): StringBuilder? {
        var settings: StringBuilder? = null
        if (project != null) {
            settings = StringBuilder()
            settings.append(CompositeGeneralTroubleInfoCollector().collectInfo(project))
            for (troubleInfoCollector in TroubleInfoCollector.EP_SETTINGS.extensions) {
                settings.append(troubleInfoCollector.collectInfo(project)).append('\n')
            }
        }
        return settings
    }

    private val javaErrorLogs = File(SystemProperties.getUserHome())
        .listFiles { file: File ->
            file.isFile && file.name.startsWith("java_error_in") && !file.name.endsWith("hprof")
        } ?: emptyArray()

    private val date = SimpleDateFormat("yyyyMMdd-HHmmss").format(Date())


}