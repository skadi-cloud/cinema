package cloud.skadi.web.hosting.views

import cloud.skadi.web.hosting.HOST_URL
import cloud.skadi.web.hosting.canStartContainer
import cloud.skadi.web.hosting.canStopContainer
import io.ktor.html.*
import kotlinx.html.*
import cloud.skadi.web.hosting.data.ContainerStatus
import cloud.skadi.web.hosting.data.KernelFContainer
import cloud.skadi.web.hosting.data.canCreateContainer
import cloud.skadi.web.hosting.data.containers





fun TBODY.containerRow(container: KernelFContainer) {
    tr {
        td {
            +container.name
        }
        td {
            +container.kernelFVersion
        }
        td {
            +container.created.toString()
        }
        td {
            when (container.status) {
                ContainerStatus.Stopped -> +"stopped"
                ContainerStatus.Stopping -> +"stopping"
                ContainerStatus.Running -> +"running"
                ContainerStatus.Error -> +"error"
                ContainerStatus.Deploying -> +"deploying"
                ContainerStatus.Created -> +"created"
            }
        }
        td {
            a {
                val url = "https://${container.id._value}.$HOST_URL"
                href = url
                +url
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
            button {
                type = ButtonType.submit
                disabled = !canCreateContainer(email)
                id = "new-containers"
                formAction = "/new-container"
                formMethod = ButtonFormMethod.post
                +"New KernelF Instance"

            }
        }

    }
    table {
        thead {
            tr {
                th {
                    scope = ThScope.col
                    +"Container"
                }
                th {
                    scope = ThScope.col
                    +"Kernel F Version"
                }
                th {
                    scope = ThScope.col
                    +"Created"
                }
                th {
                    scope = ThScope.col
                    +"Status"
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
