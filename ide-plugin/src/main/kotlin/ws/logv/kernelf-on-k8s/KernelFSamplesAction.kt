package ws.logv.`kernelf-on-k8s`

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.ZipUtil
import java.io.File
import java.util.function.Supplier

private const val ZIP_NAME = "kernelf-samples.zip"
private const val SAMPLE_FOLDER = "kernelf-samples"

class KernelFSamplesAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val file = File(PathManager.getHomePath(), ZIP_NAME)
        if (!file.exists()) {
            return
        }
        if (!samplesPathInUserHome.exists()) {
            ProgressManager.getInstance().run(SamplesExtractionTask(file))
        }
    }
}

private class SamplesExtractionTask(val zipFile: File) : Task.Modal(null, "Extract KernelF Samples", false) {
    override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = true

        val temp = FileUtil.createTempDirectory("KernelF Samples", null)
        indicator.text2 = "Extracting samples"
        ZipUtil.extract(zipFile.toPath(), temp.toPath(), null)
        val from = File(temp, SAMPLE_FOLDER)
        val to = samplesPathInUserHome
        if (!FileUtil.moveDirWithContent(from, to) && !to.exists()) {
            FileUtil.copyDir(from, to)
        }
        indicator.cancel()
    }

    override fun onSuccess() {
        super.onSuccess()
    }
}

private val samplesPathInUserHome = File(System.getProperty("user.home"), SAMPLE_FOLDER)