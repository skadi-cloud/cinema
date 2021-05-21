package cloud.skadi.web.hosting.routing

import cloud.skadi.shared.data.Task
import cloud.skadi.web.hosting.HOME_PATH
import cloud.skadi.web.hosting.data.*
import cloud.skadi.web.hosting.redirectToLoginAndBack
import cloud.skadi.web.hosting.session
import cloud.skadi.web.hosting.turbo.HomeTurboStream
import cloud.skadi.web.hosting.turbo.OpenTurboStream
import cloud.skadi.web.hosting.turbo.runWebSocket
import cloud.skadi.web.hosting.views.*
import io.fabric8.kubernetes.client.KubernetesClient
import io.ktor.application.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import io.ktor.websocket.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime
import java.util.*

const val REPO_PARAM = "repo"

val emptyUUID: UUID
    get() = UUID(0, 0)

fun ApplicationCall.openInContainerUrl(container: KernelFContainer, repo: String): String {
    return URLBuilder.createFromCall(this).apply {
        takeFrom("/open-in-playground/${container.id.value}")
        parameters.append(REPO_PARAM, repo)
    }.build().fullPath
}

@ExperimentalStdlibApi
fun Application.home(client: KubernetesClient) = routing {
    get("/") {
        call.respondHtmlTemplate(IndexTemplate("Skadi Cloud")) {
            content {
                indexPage()
            }
        }
    }

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
                    if (canStartContainer(container)) {
                        container.status = ContainerStatus.Deploying
                        container.lastHeartBeat = LocalDateTime.now()
                        startContainer(client, container.id.value)
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
                if (repo == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@withUserContainerViaParam
                }
                runWebSocket(OpenTurboStream(container, repo, outgoing))
            }
        }
    }

    get(HOME_PATH) {
        if (call.session == null) {
            call.redirectToLoginAndBack()
            return@get
        }
        val email = call.session!!.email
        val name = call.session!!.username
        createUserIfNotExists(email)
        call.respondHtmlTemplate(AppTemplate("Skadi Cloud")) {
            content { appHome(email, name) }
        }
    }
    get("$HOME_PATH/edit/{id}") {
        call.authenticated {
            call.withUserContainerViaParam { container ->
                val email = call.session!!.email
                val name = call.session!!.username
                call.respondHtmlTemplate(AppTemplate("Skadi Cloud")) {
                    content { appHome(email, name, container.id.value) }
                }
            }
        }
    }
    webSocket("$HOME_PATH/stream") {
        if (call.session == null) {
            call.respondRedirect("/")
            return@webSocket
        }
        val user = getUserById(call.session!!.email)!!
        log.info("streaming events for user ${user.id.value}")
        runWebSocket(HomeTurboStream(user, outgoing))
    }
}

private fun createUserIfNotExists(email: String) {
    if (!userExists(email)) {
        createUser(email)
    } else {
        loginUser(email)
    }
}