package cloud.skadi.web.hosting.views

import cloud.skadi.web.hosting.HOST_URL
import cloud.skadi.web.hosting.canStartContainer
import cloud.skadi.web.hosting.canStopContainer
import cloud.skadi.web.hosting.data.*
import io.ktor.http.*
import kotlinx.html.*
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

fun FlowContent.appHome(email: String, name: String) {
    p {
        +"Hello $name (${email})"
    }
    div {
        form {
            attributes["data-turbo-frame"] = "instances"
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
                id = "new-containers"
                +"New Playground"
            }
        }

    }
    instanceTable {
        containers(email).forEach { container ->
            containerRow(container)
        }
    }
}
