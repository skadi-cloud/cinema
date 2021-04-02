package cloud.skadi.web.hosting.views

import cloud.skadi.web.hosting.HOST_URL
import cloud.skadi.web.hosting.canStartContainer
import cloud.skadi.web.hosting.canStopContainer
import cloud.skadi.web.hosting.data.*
import io.ktor.http.*
import kotlinx.html.*
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter


fun TBODY.containerRow(container: KernelFContainer) {
    tr {
        id = "instance-status-${container.id.value}"
        td {
            div(classes = "tooltip") {
                fun classFromStatus(): String {
                    return when (container.status) {
                        ContainerStatus.Stopped -> "stopped"
                        ContainerStatus.Stopping -> "working"
                        ContainerStatus.Running -> "running"
                        ContainerStatus.Error -> "error"
                        ContainerStatus.Deploying -> "working"
                        ContainerStatus.Created -> "working"
                    }
                }

                span(classes = "dot ${classFromStatus()}") { }
                span(classes = "tooltiptext") {
                    when (container.status) {
                        ContainerStatus.Stopped -> +"stopped"
                        ContainerStatus.Stopping -> +"stopping"
                        ContainerStatus.Running -> +"running"
                        ContainerStatus.Error -> +"error"
                        ContainerStatus.Deploying -> +"deploying"
                        ContainerStatus.Created -> +"created"
                    }
                }
            }
        }
        td(classes = "container-name") {
            +container.name
            p {
                if (container.version.buildNumber != null && container.version.commit != null) {
                    +"MPS: ${container.version.mpsVersion.fullVersion} KernelF: ${container.version.buildNumber}.${container.version.commit}"
                } else {
                    +"MPS: ${container.version.mpsVersion.fullVersion}"
                }
            }
        }
        td(classes = "date-relative") {
            val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
            attributes["data-date"] = "${container.created.toEpochSecond(ZoneOffset.UTC) * 1000}"
            +formatter.format(container.created)
        }

        td {
            a {
                val url = "https://${container.id._value}.$HOST_URL?token=${container.rwToken}"
                href = url
                target = "_blank"
                +"Full Access"
            }

            br

            a {
                val url = "https://${container.id._value}.$HOST_URL?token=${container.roToken}"
                href = url
                target = "_blank"
                +"Read Only"
            }
        }
        td {
            form {
                button {
                    type = ButtonType.submit
                    id = "delete-${container.name}"
                    formAction = "/container/${container.id._value}/start"
                    formMethod = ButtonFormMethod.post
                    disabled = !canStartContainer(container)
                    +"Start"
                }
            }
            form {
                button {
                    type = ButtonType.submit
                    id = "delete-${container.name}"
                    formAction = "/container/${container.id._value}/stop"
                    formMethod = ButtonFormMethod.post
                    disabled = !canStopContainer(container)
                    +"Stop"
                }
            }
            form {
                button {
                    type = ButtonType.submit
                    id = "delete-${container.name}"
                    formAction = "/container/${container.id._value}/delete"
                    formMethod = ButtonFormMethod.post
                    +"Delete"
                }
            }
        }
    }
}

fun FlowContent.appHome(email: String, name: String) {
    p {
        +"Hello $name (${email})"
    }
    div {
        form {
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
                formAction = "/new-container"
                formMethod = ButtonFormMethod.post
                +"New Playground"
            }
        }

    }
    div(classes = "instances") {
        table {
            thead {
                tr {
                    th {
                        scope = ThScope.col
                        +"Status"
                    }
                    th {
                        scope = ThScope.col
                        +"Container"
                    }
                    th {
                        scope = ThScope.col
                        +"Created"
                    }

                    th {
                        scope = ThScope.col
                        +"Url"
                    }
                    th {
                        scope = ThScope.col
                        +"Actions"
                    }
                }
            }
            tbody {
                containers(email).forEach { container ->
                    containerRow(container)
                }
            }
        }
    }

}
