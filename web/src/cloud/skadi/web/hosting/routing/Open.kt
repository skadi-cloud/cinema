package cloud.skadi.web.hosting.routing

import cloud.skadi.shared.data.Task
import cloud.skadi.web.hosting.data.containers
import cloud.skadi.web.hosting.data.createTask
import cloud.skadi.web.hosting.session
import cloud.skadi.web.hosting.turbo.OpenTurboStream
import cloud.skadi.web.hosting.turbo.runWebSocket
import cloud.skadi.web.hosting.views.*
import io.ktor.application.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.websocket.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

@ExperimentalStdlibApi
fun Application.open() = routing {
    get("/open") {
        call.respondHtmlTemplate(AppTemplateWithoutScript("Open Repository")) {
            content {
                openInSkadi()
            }
        }
    }

    get("/open-in-playground") {
        call.authenticated {
            newSuspendedTransaction {

                if (call.parameters[REPO_PARAM] == null) {
                    call.respond(HttpStatusCode.BadRequest, "no repository specified")
                    return@newSuspendedTransaction
                }

                val email = call.session!!.email
                val userContainers = containers(email)
                val repo = call.parameters[REPO_PARAM]!!
                if (userContainers.size == 1) {
                    val container = userContainers.first()
                    val target = call.openInContainerUrl(container, repo)
                    createTask(container, Task.CloneRepo(repo, emptyUUID))
                    if(canStartContainer(container)) {
                        startContainer(container.id.value)
                    }
                    call.respondRedirect(target)
                    return@newSuspendedTransaction
                } else {
                    call.respondHtmlTemplate(AppTemplateWithoutScript("Create Playground")) {
                        content {
                            selectOrCreatePlayground(email, userContainers, repo, call.request.uri, call)
                        }
                    }
                }
            }
        }
    }

    get("/open-in-playground/{id}") {
        call.authenticated {
            call.withUserContainerViaParam { container ->
                newSuspendedTransaction {
                    val repo = call.parameters[REPO_PARAM]!!
                    call.respondHtmlTemplate(AppTemplate("Opening")) {
                        content {
                            opening(container, repo)
                        }
                    }
                }
            }
        }
    }
    webSocket("/open-in-playground/{id}/stream") {
        call.authenticated {
            call.withUserContainerViaParam { container ->
                val repo = call.parameters[REPO_PARAM]
                if(repo == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@withUserContainerViaParam
                }
                runWebSocket(OpenTurboStream(container, repo, outgoing))
            }
        }
    }
}