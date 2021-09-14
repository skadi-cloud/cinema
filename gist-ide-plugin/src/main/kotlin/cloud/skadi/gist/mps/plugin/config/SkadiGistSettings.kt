package cloud.skadi.gist.mps.plugin.config

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@Service
@State(name = "SkadiGistSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)], reportStatistic = false)
class SkadiGistSettings : PersistentStateComponentWithModificationTracker<SkadiGistSettings.State> {
    enum class Visiblility {
        Public,
        Internal,
        Private
    }

    class State : BaseState() {
        var visiblity by enum(Visiblility.Public)
    }

    var visiblility
        get() = state.visiblity
        set(value) {
            state.visiblity = value
        }

    private var state = State()

    override fun getState() = state

    override fun loadState(state: State) {
        this.state = state
    }

    override fun getStateModificationCount() = state.modificationCount

    companion object {
        @JvmStatic
        fun getInstance(project: Project) = project.service<SkadiGistSettings>()
    }


}