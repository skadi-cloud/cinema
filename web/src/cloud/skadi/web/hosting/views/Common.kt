package cloud.skadi.web.hosting.views

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