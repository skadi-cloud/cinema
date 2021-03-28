package cloud.skadi.web.hosting

import cloud.skadi.web.hosting.data.*
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
import io.ktor.http.content.*
import io.ktor.jackson.*
import io.ktor.locations.*
import io.ktor.metrics.micrometer.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.sessions.*
import io.ktor.util.*
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
import kotlinx.coroutines.channels.ticker
import kotlinx.css.*
import kotlinx.html.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.*
import kotlin.time.ExperimentalTime


fun getEnvOfFail(env: String): String {
    return System.getenv(env) ?: throw IllegalArgumentException("missing $env")
}

fun getEnvOrDefault(env: String, default: String): String {
    return System.getenv(env) ?: default
}

val SQL_PASSWORD = getEnvOfFail("SQL_PASSWORD")
val SQL_USER = getEnvOfFail("SQL_USER")
val SQL_DB = getEnvOrDefault("SQL_DB", "kernelf")
val SQL_HOST = getEnvOrDefault("SQL_HOST", "localhost:5432")
val GITHUB_SECRET = getEnvOfFail("GITHUB_SECRET")
val GITHUB_ID = getEnvOfFail("GITHUB_ID")
val COOKIE_SALT = getEnvOfFail("COOKIE_SALT")

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

@KtorExperimentalLocationsAPI
@ExperimentalTime
@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.mainModule(testing: Boolean = false) {
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
    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }
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

    GlobalScope.launch {
        updateNewContainers()
    }
    GlobalScope.launch {
        updateRunningContainers()
    }

    containerApi()
    installAuth()
    installInternalApi(prometheusMeterRegistry)

    routing {
        get("/") {
            call.respondHtmlTemplate(IndexTemplate("KernelF Playground")) {
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
            call.respondHtmlTemplate(IndexTemplate("KernelF Playground")) {
                content { appHome(email, name) }
            }
        }

        // Static feature. Try to access `/static/ktor_logo.svg`
        static("/static") {
            resources("static")
        }
    }
}

private suspend fun updateNewContainers() {
    for (tick in containerStatusTicker) {
        transaction {
            KernelFContainer.find {
                (KernelFContainers.status eq ContainerStatus.Stopping) or (KernelFContainers.status eq ContainerStatus.Deploying)
            }.map {
                when (it.status) {
                    ContainerStatus.Created -> null
                    ContainerStatus.Error -> null
                    ContainerStatus.Stopped -> null
                    ContainerStatus.Stopping -> Pair(it, getPodStatus(it.id.value))
                    ContainerStatus.Deploying -> Pair(it, getPodStatus(it.id.value))
                    ContainerStatus.Running -> null
                }
            }.filterNotNull().filter { it.first.status != it.second }.forEach { it.first.status = it.second }
        }
    }
}

private suspend fun updateRunningContainers() {
    for (tick in runningContainerStatusTicker) {
        transaction {
            KernelFContainer.find {
                (KernelFContainers.status eq ContainerStatus.Running)
            }.map {
                when (it.status) {
                    ContainerStatus.Created -> null
                    ContainerStatus.Error -> null
                    ContainerStatus.Stopped -> null
                    ContainerStatus.Stopping -> null
                    ContainerStatus.Deploying -> null
                    ContainerStatus.Running -> Pair(it, getPodStatus(it.id.value))
                }
            }.filterNotNull().filter { it.first.status != it.second }.forEach { it.first.status = it.second }
        }
        transaction {
            KernelFContainer.find {
                (KernelFContainers.lastHeartBeat less (LocalDateTime.now()
                    .minusMinutes(30))) and (KernelFContainers.status eq ContainerStatus.Running)
            }.forEach {
                it.status = ContainerStatus.Stopping
                pauseContainer(it.id.value)
            }
        }
    }
}


private fun createUserIfNotExists(email: String) {
    if (!userExists(email)) {
        createUser(email)
    }
}

val ApplicationCall.session: UserSession?
    get() = sessions.get<UserSession>()
