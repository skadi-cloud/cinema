package cloud.skadi.web.hosting.views

import cloud.skadi.web.hosting.data.KernelFContainer
import cloud.skadi.web.hosting.routing.REPO_PARAM
import cloud.skadi.web.hosting.routing.openInContainerUrl
import io.ktor.application.*
import kotlinx.html.*

fun FlowContent.openInSkadi() {

    div(classes = "center") {
        form {
            id = "openinskadi"
            method = FormMethod.get
            action = "/open-in-playground"
            label {
                htmlFor = "repo"
                +"Repository Url"
            }
            textInput {
                name = REPO_PARAM
                id = REPO_PARAM
            }
            br
            buttonInput {
                type = InputType.submit
                value = "Open in Skadi"
            }
        }
        noScript {
            p {
                +"Seems like you have JavaScript disabled. Please enter the URL of the form you would like to open above and submit."
            }
        }
        script {
            unsafe {
                raw(
                    """

                    if(window.location.hash) {
                        document.getElementById("$REPO_PARAM").value = window.location.hash.substring(1);
                        document.getElementById("openinskadi").submit();
                    }   
                
                
            """.trimIndent()
                )
            }
        }
    }
}

fun FlowContent.selectOrCreatePlayground(email: String, containers: List<KernelFContainer>, repo: String, afterCreate: String, call: ApplicationCall) {
    div(classes = "center") {
        if (containers.isEmpty()) {
            p {
                +"You have no playgrounds, please create a new one to open the repository in."
            }
            createPlaygroundForm(email, afterCreate)
        } else {
            p {
                +"Open $repo in: "
            }
            ul {
                containers.map {
                    li {
                        a {
                            href = call.openInContainerUrl(it, repo)
                            +"${it.name} (${it.version.mpsVersion.fullVersion})"
                        }
                    }
                }
            }
        }
    }
}

fun FlowContent.opening(instance: KernelFContainer, stated : Boolean, repo: String) {
    div(classes = "center") {
        p {
            if(stated)
            {
                +"Your playground ${instance.name} was started and will open the repository ($repo) when it is stared."
            } else {
                +"Your playground ${instance.name} will open the repository ($repo) shortly."
            }

        }
        p(classes = "passive-text") {
            +"""If the playground is starting for the first time you will have to accept the license terms first. Opening the
                |repository will start once you see the welcome screen and can take up to 10 seconds before it stated.
            """.trimMargin()
        }
    }

}