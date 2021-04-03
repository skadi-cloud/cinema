package cloud.skadi.web.hosting.views

import cloud.skadi.web.hosting.HOST_URL
import cloud.skadi.web.hosting.canStartContainer
import cloud.skadi.web.hosting.canStopContainer
import cloud.skadi.web.hosting.data.ContainerStatus
import cloud.skadi.web.hosting.data.KernelFContainer
import io.ktor.html.*
import kotlinx.html.*
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class IndexTemplate(private val pageName: String) : Template<HTML> {
    val content = Placeholder<HtmlBlockTag>()
    override fun HTML.apply() {
        head {
            meta {
                name = "viewport"
                content = "width=device-width, initial-scale=1.0"
            }
            title { +pageName }
            styleLink("/assets/styles/styles.css")
            script(src = "/assets/js/script.js") {}
        }
        body {
            div(classes = "container") {
                div {
                    id = "header"
                    h1 { +"skadi cloud" }
                    p { +"An experiment with JetBrains MPS and Projector" }
                }
                insert(content)
            }
        }
    }
}

class AppTemplate(private val pageName: String) : Template<HTML> {
    val content = Placeholder<HtmlBlockTag>()
    override fun HTML.apply() {
        head {
            meta {
                name = "viewport"
                content = "width=device-width, initial-scale=1.0"
            }
            title { +pageName }
            styleLink("/assets/styles/styles.css")
            script(src = "/assets/js/script.js") {}

        }
        body {
            div(classes = "container") {
                div {
                    id = "header"
                    h1 { +"skadi cloud" }
                    p { +"An experiment with JetBrains MPS and Projector" }
                }
                insert(content)
            }
        }
    }
}

fun instanceRowId(container: KernelFContainer) =
    "playground-status-${container.id.value}"

fun FlowContent.instanceTable(block: TBODY.() -> Unit) {
    div(classes = "instances") {
        turboFrame {
            id = "instances"
            table {
                instanceTableHeader()
                tbody {
                    block()
                }
            }
        }
    }
}

fun TABLE.instanceTableHeader() {
    thead {
        tr {
            th {
                scope = ThScope.col
                +"Status"
            }
            th {
                scope = ThScope.col
                +"Playground"
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
}

fun FlowContent.instanceStatusFrameContent(container: KernelFContainer) {
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
                ContainerStatus.Stopped -> +"paused"
                ContainerStatus.Stopping -> +"stopping"
                ContainerStatus.Running -> +"running"
                ContainerStatus.Error -> +"error"
                ContainerStatus.Deploying -> +"deploying"
                ContainerStatus.Created -> +"created"
            }
        }
    }

}

fun TBODY.containerRow(container: KernelFContainer) {
    tr {
        td {
            id = "status-${container.id.value}"
            instanceStatusFrameContent(container)
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
            attributes["data-controller"] = "relative-date"
            attributes["data-relative-date-target"] = "date"
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
        td(classes = "instance-controls") {
            form {
                button(classes = "start") {
                    type = ButtonType.submit
                    id = "start-${container.name}"
                    formAction = "/container/${container.id._value}/start"
                    formMethod = ButtonFormMethod.post
                    disabled = !canStartContainer(container)
                    i(classes = "far fa-play-circle")
                }
            }
            form {
                button(classes = "pause") {
                    type = ButtonType.submit
                    id = "pause-${container.name}"
                    formAction = "/container/${container.id._value}/stop"
                    formMethod = ButtonFormMethod.post
                    disabled = !canStopContainer(container)
                    i(classes = "far fa-pause-circle")
                }
            }

            form {
                button(classes = "delete") {
                    type = ButtonType.submit
                    id = "delete-${container.name}"
                    formAction = "/container/confirm/delete/${container.id._value}"
                    i(classes = "far fa-trash-alt")
                }
            }
        }
    }

}