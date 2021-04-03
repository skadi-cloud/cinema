package cloud.skadi.web.hosting

import cloud.skadi.web.hosting.data.*
import cloud.skadi.web.hosting.views.*
import com.fasterxml.jackson.databind.*
import com.fkorotkov.kubernetes.extensions.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.content.*
import io.ktor.features.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.*
import io.ktor.locations.*
import io.ktor.metrics.micrometer.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.sessions.*
import io.ktor.util.*
import io.ktor.util.collections.*
import io.ktor.websocket.*
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.ticker
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.ExperimentalTime


fun getEnvOfFail(env: String): String {
    return System.getenv(env) ?: throw IllegalArgumentException("missing $env")
}

fun getEnvOrDefault(env: String, default: String): String {
    val value = System.getenv(env) ?: return default
    if (value.isEmpty()) {
        return default
    } else {
        return value
    }
}

val SQL_PASSWORD = getEnvOfFail("SQL_PASSWORD")
val SQL_USER = getEnvOfFail("SQL_USER")
val SQL_DB = getEnvOfFail("SQL_DB")
val SQL_HOST = getEnvOfFail("SQL_HOST")
val GITHUB_SECRET = getEnvOrDefault("GITHUB_SECRET", "")
val GITHUB_ID = getEnvOrDefault("GITHUB_ID", "")
const val SALT_DEFAULT = "6819b57a326945c1968f45236589"
val COOKIE_SALT = getEnvOrDefault("COOKIE_SALT", SALT_DEFAULT)

const val HOST_URL = "kernelf-staging.logv.ws"
const val HOME_PATH = "/home"
const val INTERNAL_API_PORT = 9090

@Location("/login/{type?}")
class Login(val type: String = "")

data class UserSession(
    val username: String,
    val email: String,
    val idToken: String
)

@ExperimentalTime
fun main(args: Array<String>): Unit {
    val env = applicationEngineEnvironment {
        developmentMode = true
        module {
            mainModule(false)
        }
        //watchPaths = listOf("classes", "resources")
        connector {
            host = "0.0.0.0"
            port = 8080
        }
        connector {
            host = "0.0.0.0"
            port = INTERNAL_API_PORT
        }
    }
    embeddedServer(Netty, env).start(true)
}

@ObsoleteCoroutinesApi
val containerStatusTicker = ticker(10_000)

@ObsoleteCoroutinesApi
val runningContainerStatusTicker = ticker(60_000)

val userStreams = ConcurrentHashMap<Int, SendChannel<Frame>>()

fun getChannelToUser(id: Int): SendChannel<Frame>? {
    return userStreams.get(id)
}

fun setChannelForUser(id: Int, channel: SendChannel<Frame>) {

}

@KtorExperimentalLocationsAPI
@ExperimentalTime
@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.mainModule(testing: Boolean = false) {
    if (!testing) {
        if (GITHUB_ID.isEmpty()) {
            log.error("GITHUB_ID is empty!")
            throw IllegalArgumentException("GITHUB_ID is empty!")
        }

        if (GITHUB_SECRET.isEmpty()) {
            log.error("GITHUB_SECRET is empty!")
            throw IllegalArgumentException("GITHUB_SECRET is empty!")
        }
        if (COOKIE_SALT == SALT_DEFAULT) {
            log.error("COOKIE_SALT not set!")
            throw IllegalArgumentException("COOKIE_SALT is default value!")
        }
    }

    install(Compression) {
        gzip {
            priority = 1.0
        }
        deflate {
            priority = 10.0
            minimumSize(1024) // condition
        }
    }

    install(ForwardedHeaderSupport) // WARNING: for security, do not include this if not behind a reverse proxy
    install(XForwardedHeaderSupport) // WARNING: for security, do not include this if not behind a reverse proxy
    install(Locations)

    val prometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) {
        registry = prometheusMeterRegistry
        meterBinders = listOf(
            ClassLoaderMetrics(),
            JvmMemoryMetrics(),
            JvmGcMetrics(),
            ProcessorMetrics(),
            JvmThreadMetrics(),
            FileDescriptorMetrics(),
            UptimeMetrics()
        )
    }
    install(WebSockets)


    Database.connect(
        "jdbc:postgresql://$SQL_HOST/$SQL_DB", driver = "org.postgresql.Driver",
        user = SQL_USER, password = SQL_PASSWORD
    )

    transaction {
        try {
            SchemaUtils.create(Users, KernelFContainers)
        } catch (_: Throwable) {

        }
    }
    if (!testing) {
        GlobalScope.launch {
            updateNewContainers()
        }
        GlobalScope.launch {
            updateRunningContainers()
        }
    }

    containerApi()
    installAuth(testing)
    installInternalApi(prometheusMeterRegistry)

    routing {
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
                }
            } catch (e: ClosedReceiveChannelException) {
                userStreams.remove(user.id.value)
                log.info("connection closed for user ${user.id.value}")
            } catch (e: Throwable) {
                userStreams.remove(user.id.value)
                log.error("websocket error for user ${user.id.value}", e)
            }
        }

        // Static feature. Try to access `/static/ktor_logo.svg`
        static("assets") {
            static("webfonts") {
                resources("webfonts")
            }
            static("styles") {
                resources("styles")
            }
            static("js") {
                resources("js")
            }
            resources("static")
        }
    }
}

private suspend fun updateNewContainers() {
    for (tick in containerStatusTicker) {
        val updatesToSend = transaction {
            KernelFContainer.find {
                (KernelFContainers.status eq ContainerStatus.Stopping) or (KernelFContainers.status eq ContainerStatus.Deploying)
            }.mapNotNull {
                when (it.status) {
                    ContainerStatus.Created -> null
                    ContainerStatus.Error -> null
                    ContainerStatus.Stopped -> null
                    ContainerStatus.Stopping -> Pair(it, getPodStatus(it.id.value))
                    ContainerStatus.Deploying -> Pair(it, getPodStatus(it.id.value))
                    ContainerStatus.Running -> null
                }
            }.filter { it.first.status != it.second }.map {
                it.first.status = it.second
                Pair(it.first.user.id.value, instanceStatusUpdate(it.first))
            }
        }
        updatesToSend.forEach {
            try {
                println("trying to send updates to user ${it.first}")
                val channel = getChannelToUser(it.first) ?: return@forEach
                print("sending update to user ${it.first}")
                channel.send(Frame.Text(it.second))
            } catch (e: Throwable) {
                println("error sending update ${e.message}")
            }
        }
    }
}

private fun instanceStatusUpdate(it: KernelFContainer) =
    createHTML().turboStream {
        target = "status-${it.id.value}"
        action = "update"
        template {
            instanceStatusFrameContent(it)
        }
    }

private suspend fun updateRunningContainers() {
    for (tick in runningContainerStatusTicker) {
        var updatesToSend = transaction {
            KernelFContainer.find {
                (KernelFContainers.status eq ContainerStatus.Running)
            }.mapNotNull {
                when (it.status) {
                    ContainerStatus.Created -> null
                    ContainerStatus.Error -> null
                    ContainerStatus.Stopped -> null
                    ContainerStatus.Stopping -> null
                    ContainerStatus.Deploying -> null
                    ContainerStatus.Running -> Pair(it, getPodStatus(it.id.value))
                }
            }.filter { it.first.status != it.second }.map {
                it.first.status = it.second
                Pair(it.first.user.id.value, instanceStatusUpdate(it.first))
            }
        }
        updatesToSend = updatesToSend + transaction {
            KernelFContainer.find {
                (KernelFContainers.lastHeartBeat less (LocalDateTime.now()
                    .minusMinutes(30))) and (KernelFContainers.status eq ContainerStatus.Running)
            }.map {
                it.status = ContainerStatus.Stopping
                pauseContainer(it.id.value)
                Pair(it.user.id.value, instanceStatusUpdate(it))
            }
        }
        updatesToSend.forEach {
            try {
                val channel = getChannelToUser(it.first) ?: return@forEach
                channel.send(Frame.Text(it.second))
            } catch (e: Throwable) {
                println("error sending update ${e.message}")
            }
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

val ApplicationCall.session: UserSession?
    get() = sessions.get<UserSession>()
