package cloud.skadi.web.hosting.views

import cloud.skadi.web.hosting.data.KernelFContainer
import io.ktor.html.*
import kotlinx.html.*

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
        table {
            instanceTableHeader()
            tbody {
                block()
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