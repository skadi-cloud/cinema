package cloud.skadi.web.hosting.views

import cloud.skadi.web.hosting.data.*
import kotlinx.html.*

fun FlowContent.appHome(email: String, name: String) {
    p {
        +"Hello $name (${email})"
    }
    div {
        form {
            attributes["data-turbo-frame"] = "instances"
            id = "new-playground"
            method = FormMethod.post
            action = "/new-container"
            select() {
                this.name = "version"
                enumValues<ContainerVersion>().mapIndexed { i, version ->
                    option {
                        value = version.name
                        if (i == 0) {
                            selected = true
                        }
                        if (version.buildNumber != null && version.commit != null) {
                            +"MPS ${version.mpsVersion.fullVersion} KernelF ${version.buildNumber}"
                        } else {
                            +"MPS ${version.mpsVersion.fullVersion}"
                        }
                    }
                }
            }

            button {
                type = ButtonType.submit
                disabled = !canCreateContainer(email)
                id = "create-new-playground"
                i(classes = "fas fa-plus")
                p {
                    +"Create Playground"
                }
            }
        }

    }
    instanceTable {
        containers(email).forEach { container ->
            containerRow(container)
        }
    }
}
