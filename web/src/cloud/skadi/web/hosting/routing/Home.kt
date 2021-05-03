package cloud.skadi.web.hosting.routing

import cloud.skadi.shared.data.Task
import cloud.skadi.web.hosting.HOME_PATH
import cloud.skadi.web.hosting.data.*
import cloud.skadi.web.hosting.redirectToLoginAndBack
import cloud.skadi.web.hosting.session
import cloud.skadi.web.hosting.userStreams
import cloud.skadi.web.hosting.views.*
import io.ktor.application.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

const val REPO_PARAM = "repo"

val emptyUUID: UUID
    get() = UUID(0,0)

fun ApplicationCall.openInContainerUrl(container: KernelFContainer, repo: String): String {
    return this.url {
        takeFrom("/open-in-playground/${container.id.value}")
        parameters.append(REPO_PARAM, repo)
    }
}

fun Application.home() = routing {
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
                if (userContainers.size == 1) {
                    val target = call.openInContainerUrl(userContainers.first(), call.parameters[REPO_PARAM]!!)
                    call.respondRedirect(target)
                    return@newSuspendedTransaction
                } else {
                    call.respondHtmlTemplate(AppTemplateWithoutScript("Create Playground")) {
                        content {
                            selectOrCreatePlayground(email, userContainers, call.parameters[REPO_PARAM]!!, call.request.uri, call)
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
                    createTask(container, Task.CloneRepo(repo, emptyUUID))
                    call.respondHtmlTemplate(AppTemplateWithoutScript("Opening")) {
                        content {
                            opening(container, false, repo)
                        }
                    }
                }

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
        val old = userStreams.put(user.id.value, outgoing)
        try {
            log.info("old value is $old")
            old?.close()
        } catch (e: Throwable) {
            log.error("can't close old client connection", e)
        }
        try {
            for (frame in incoming) {
                val text = (frame as Frame.Text).readText()
                log.info("client send $text")
            }
        } catch (e: ClosedReceiveChannelException) {
            userStreams.remove(user.id.value)
            log.info("connection closed for user ${user.id.value}")
        } catch (e: Throwable) {
            userStreams.remove(user.id.value)
            log.error("websocket error for user ${user.id.value}", e)
        }
    }
}

private fun createUserIfNotExists(email: String) {
    if (!userExists(email)) {
        createUser(email)
    } else {
        loginUser(email)
    }
}