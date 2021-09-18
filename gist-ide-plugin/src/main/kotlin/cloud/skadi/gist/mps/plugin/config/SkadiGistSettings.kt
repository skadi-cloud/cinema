package cloud.skadi.gist.mps.plugin.config

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.ui.layout.ComponentPredicate

const val DEFAULT_BACKEND = "http://localhost:8080/gist/create"
@Service
@State(name = "SkadiGistSettings", storages = [Storage("skadi-settings.xml")], reportStatistic = false)
class SkadiGistSettings : PersistentStateComponentWithModificationTracker<SkadiGistSettings.State> {
    enum class Visiblility {
        Public,
        Internal,
        Private
    }

    private val listeners = mutableMapOf<Any, (Boolean) -> Unit>()

    class State : BaseState() {
        var visiblity by enum(Visiblility.Public)
        var backendAddress by string(DEFAULT_BACKEND)
        var rememberVisiblility by property(true)
        var loggedInUser by string()
    }

    var visiblility
        get() = state.visiblity
        set(value) {
            state.visiblity = value
        }

    var backendAddress: String
        get() = state.backendAddress.orEmpty().ifEmpty { DEFAULT_BACKEND }
        set(value) {
            state.backendAddress = value
        }

    var rememberVisiblility
        get() = state.rememberVisiblility
        set(value) {
            state.rememberVisiblility = value
        }

    var loggedInUser
        get() = state.loggedInUser ?: ""
        set(value) {
            state.loggedInUser = value
            listeners.forEach { it.value(isLoggedIn) }
        }

    val isLoggedIn
        get () = state.loggedInUser != null

    fun logout() {
        state.loggedInUser = null
        listeners.forEach { it.value(isLoggedIn) }
    }

    fun registerLoginListener(key: Any, listener: (Boolean) -> Unit) {
        listeners[key] = listener
    }

    fun unregisterLoginListener(key: Any) {
        listeners.remove(key)
    }

    private var state = State()

    override fun getState() = state

    override fun loadState(state: State) {
        this.state = state
    }

    override fun getStateModificationCount() = state.modificationCount

    companion object {
        @JvmStatic
        fun getInstance() = ApplicationManager.getApplication().getService(SkadiGistSettings::class.java)
    }


}