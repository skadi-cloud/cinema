package cloud.skadi.ide.plugin

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.io.ZipUtil
import jetbrains.mps.workbench.actions.OpenMPSProjectFileChooserDescriptor
import java.io.File

private const val ZIP_NAME = "community-samples.zip"
private const val SAMPLE_FOLDER = "community-samples"

class CommunitySamplesAction : AnAction() {
    override fun update(e: AnActionEvent) {
        val file = File(PathManager.getHomePath(), ZIP_NAME)
        if(!file.exists()) {
            e.presentation.isEnabled = false
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val file = File(PathManager.getHomePath(), ZIP_NAME)
        if (!file.exists()) {
            return
        }
        if (!samplesPathInUserHome.exists()) {
            ProgressManager.getInstance().run(SamplesExtractionTask(file))
        }
        val samplesFolder = LocalFileSystem.getInstance().findFileByIoFile(samplesPathInUserHome) ?: return

        val firstSample = samplesFolder.children.minByOrNull { it.name } ?: samplesFolder
        val project = PlatformDataKeys.PROJECT.getData(e.dataContext)
        val descriptor = OpenMPSProjectFileChooserDescriptor(true)
        descriptor.title = "Community Samples"
        val result = FileChooser.chooseFile(descriptor, project, firstSample)

        if(result != null) {
            ProjectUtil.openProject(result.path, project,false )
        }

    }
}

private class SamplesExtractionTask(val zipFile: File) : Task.Modal(null, "Extract Community Samples", false) {
    override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = true

        val temp = FileUtil.createTempDirectory("Community Samples", null)
        indicator.text2 = "Extracting samples"
        ZipUtil.extract(zipFile, temp, null)
        val from = File(temp, SAMPLE_FOLDER)
        val to = samplesPathInUserHome
        if (!FileUtil.moveDirWithContent(from, to) && !to.exists()) {
            FileUtil.copyDir(from, to)
        }
        indicator.cancel()
    }

    override fun onSuccess() {
        Messages.showInfoMessage(
            "Community samples extracted to ${samplesPathInUserHome.absolutePath}",
            "Community Samples Extracted"
        )
    }

    override fun onThrowable(error: Throwable) {
        Messages.showErrorDialog("""Could not extract samples to ${samplesPathInUserHome.absolutePath}
            | Error:
            | ${error.message}
        """.trimMargin(), "Community Samples Extraction Failed")
    }
}

private val samplesPathInUserHome = File(System.getProperty("user.home"), SAMPLE_FOLDER)