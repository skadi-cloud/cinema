package cloud.skadi.gist.mps.plugin.config

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.layout.*
import javax.swing.JComboBox
import javax.swing.JTextField

class SkadiConfigurable : BoundConfigurable("Skadi Gist") {

    private val settings = SkadiGistSettings.getInstance()
    private val listeners = mutableListOf<(Boolean) -> Unit>()

    override fun disposeUIResources() {
        super.disposeUIResources()
        settings.unregisterLoginListener(this)
    }

    override fun reset() {
        super.reset()
        settings.unregisterLoginListener(this)
        settings.registerLoginListener(this) {
            listeners.forEach { listener ->  listener(it) }
        }
    }

    private val ifLoginChanged: ComponentPredicate = object : ComponentPredicate() {
        override fun addListener(listener: (Boolean) -> Unit) {
            listeners.add(listener)
        }

        override fun invoke() = settings.isLoggedIn
    }

    override fun createPanel(): DialogPanel {

        return panel {
            row("Backend Address") {
                textField(settings::backendAddress).withValidationOnApply(validateBackend())
            }
            row("Default visibility") {
                buttonGroup(settings::visiblility) {
                    row { radioButton("Public", SkadiGistSettings.Visiblility.Public) }
                    row { radioButton("Internal", SkadiGistSettings.Visiblility.Internal) }
                    row { radioButton("Private", SkadiGistSettings.Visiblility.Private) }
                }
                checkBox("Remeber visiblilty", settings::rememberVisiblility)
            }
            row {

            }
            row("Logged in as") {
                textField(settings::loggedInUser).visibleIf(ifLoginChanged)
                label("Not logged in").visibleIf(ifLoginChanged.not())
                browserLink("Login", "${settings.backendAddress}/login-ide").visibleIf(ifLoginChanged.not())
                link("Log out") {
                    settings.logout()
                }.visibleIf(ifLoginChanged).withLargeLeftGap()
            }
        }
    }

    private fun validateBackend():  ValidationInfoBuilder.(JTextField) -> ValidationInfo? = {
        null
    }

}