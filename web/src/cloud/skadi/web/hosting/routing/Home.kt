package cloud.skadi.web.hosting.routing

import cloud.skadi.shared.data.Task
import cloud.skadi.web.hosting.HOME_PATH
import cloud.skadi.web.hosting.data.*
import cloud.skadi.web.hosting.redirectToLoginAndBack
import cloud.skadi.web.hosting.session
import cloud.skadi.web.hosting.turbo.*
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
    return URLBuilder.createFromCall(this).apply {
        takeFrom("/open-in-playground/${container.id.value}")
        parameters.append(REPO_PARAM, repo)
    }.build().fullPath
}

@ExperimentalStdlibApi
fun Application.home() = routing {
    get("/") {
        call.respondHtmlTemplate(IndexTemplate("Skadi Cloud")) {
            content {
                indexPage()
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