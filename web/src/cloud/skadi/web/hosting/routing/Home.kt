package cloud.skadi.web.hosting.routing

import cloud.skadi.web.hosting.HOME_PATH
import cloud.skadi.web.hosting.data.createUser
import cloud.skadi.web.hosting.data.getUserById
import cloud.skadi.web.hosting.data.loginUser
import cloud.skadi.web.hosting.data.userExists
import cloud.skadi.web.hosting.session
import cloud.skadi.web.hosting.userStreams
import cloud.skadi.web.hosting.views.AppTemplate
import cloud.skadi.web.hosting.views.IndexTemplate
import cloud.skadi.web.hosting.views.appHome
import cloud.skadi.web.hosting.views.indexPage
import io.ktor.application.*
import io.ktor.html.*
import io.ktor.http.cio.websocket.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException

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
            call.respondRedirect("/")
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