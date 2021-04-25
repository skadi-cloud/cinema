package cloud.skadi.web.hosting.views

import cloud.skadi.web.hosting.HOME_PATH
import cloud.skadi.web.hosting.INSTANCE_HOST
import cloud.skadi.web.hosting.data.ContainerStatus
import cloud.skadi.web.hosting.data.ContainerVersion
import cloud.skadi.web.hosting.data.KernelFContainer
import cloud.skadi.web.hosting.routing.canStartContainer
import cloud.skadi.web.hosting.routing.canStopContainer
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
            script {
                src = "https://plausible.io/js/plausible.js"
                defer = true
                async = true
                attributes["data-domain"] = "skadi.cloud"
            }
        }
        body {
            div(classes = "container") {
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
                    div {
                        img { src = "/assets/icon.png" }
                        h1 { +"skadi cloud" }
                    }
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

fun FlowContent.instanceControls(container: KernelFContainer) {
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

fun TBODY.containerRow(container: KernelFContainer, edit: Boolean = false) {
    tr {
        td {
            id = "status-${container.id.value}"
            instanceStatusFrameContent(container)
        }
        td(classes = "container-name") {
            turboFrame {
                id = "container-settings-${container.id.value}"
                +container.name
                if (!edit) {
                    div {
                        p {
                            +container.version.description
                        }
                        a(classes = "edit") {
                            href = "/home/edit/${container.id.value}"
                            i(classes = "fas fa-cog")
                        }
                    }
                } else {
                    form(classes = "container-update") {
                        id = "update-${container.id.value}"
                        method = FormMethod.post
                        action = "/container/${container.id.value}/edit"
                        versionSelectBox(container.version)
                        a(classes = "cancel") {
                            href = HOME_PATH
                            i(classes = "far fa-times-circle")
                        }
                        button(classes = "confirm") {
                            type = ButtonType.submit
                            id = "confirm-edit-${container.name}"
                            i(classes = "fas fa-check-circle")
                        }
                    }
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
                val url = "https://${container.id._value}.$INSTANCE_HOST?token=${container.rwToken}"
                href = url
                target = "_blank"
                +"Full Access"
            }

            br

            a {
                val url = "https://${container.id._value}.$INSTANCE_HOST?token=${container.roToken}"
                href = url
                target = "_blank"
                +"Read Only"
            }
        }
        td(classes = "instance-controls") {
            id = "controls-${container.id.value}"
            instanceControls(container)
        }
    }
}

fun FORM.versionSelectBox(toSelect: ContainerVersion? = null) {
    select {
        this.name = "version"
        enumValues<ContainerVersion>().mapIndexed { i, version ->
            option {
                value = version.name
                if (toSelect == null && i == 0) {
                    selected = true
                } else if (toSelect == version) {
                    selected = true
                }
                +version.description
            }
        }
    }

}