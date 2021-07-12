package cloud.skadi.gist.mps.plugin

import cloud.skadi.gist.shared.GistVisibility
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import jetbrains.mps.ide.actions.MPSCommonDataKeys
import kotlinx.coroutines.runBlocking

class CreateGistFromNodeAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        object : Task.Backgroundable(e.project, "Create Gist") {
            override fun run(indicator: ProgressIndicator) {
                runBlocking {
                    upload(
                        "Dummy",
                        "yep",
                        GistVisibility.Public,
                        listOf((e.dataContext.getData(MPSCommonDataKeys.NODE)!! to null)),
                        e.dataContext.getData(MPSCommonDataKeys.CONTEXT_MODEL)!!.repository
                    )
                }
            }
        }.queue()
    }

    override fun update(e: AnActionEvent) {
        val node = e.dataContext.getData(MPSCommonDataKeys.NODE)
        e.presentation.isEnabled = node != null
    }
}