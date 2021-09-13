package cloud.skadi.web.hosting

import cloud.skadi.web.hosting.cron.markInstanceForSweep
import cloud.skadi.web.hosting.cron.sweepContainers
import cloud.skadi.web.hosting.cron.updateNewContainers
import cloud.skadi.web.hosting.cron.updateRunningContainers
import cloud.skadi.sharred.web.util.getEnvOfFail
import cloud.skadi.sharred.web.util.getEnvOrDefault
import cloud.skadi.web.hosting.data.*
import cloud.skadi.web.hosting.routing.containerApi
import cloud.skadi.web.hosting.routing.home
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.KubernetesClient
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.locations.*
import io.ktor.metrics.micrometer.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.sessions.*
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
import io.sentry.Sentry
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.TickerMode
import kotlinx.coroutines.channels.ticker
import java.lang.RuntimeException
import kotlin.time.ExperimentalTime




val SQL_PASSWORD = getEnvOfFail("SQL_PASSWORD")
val SQL_USER = getEnvOfFail("SQL_USER")
val SQL_DB = getEnvOfFail("SQL_DB")
val SQL_HOST = getEnvOfFail("SQL_HOST")
val GITHUB_SECRET = getEnvOrDefault("GITHUB_SECRET", "")
val GITHUB_ID = getEnvOrDefault("GITHUB_ID", "")
const val SALT_DEFAULT = "6819b57a326945c1968f45236589"
val COOKIE_SALT = getEnvOrDefault("COOKIE_SALT", SALT_DEFAULT)

val INSTANCE_HOST = getEnvOrDefault("INSTANCE_HOST", "localhost") // "staging.skadi.cloud"
val HOST = getEnvOrDefault("HOST", "localhost")
const val HOME_PATH = "/home"
const val INTERNAL_API_PORT = 9090

@KtorExperimentalLocationsAPI
@Location("/login/{type?}")
class Login(val type: String = "")

data class UserSession(
    val username: String,
    val email: String,
    val idToken: String
)

@ExperimentalStdlibApi
@KtorExperimentalLocationsAPI
@ObsoleteCoroutinesApi
@ExperimentalTime
fun main() {
    val env = applicationEngineEnvironment {
        developmentMode = getEnvOrDefault("ENV", "production") != "production"

        module {
            mainModule(false, DefaultKubernetesClient().inNamespace("default")!!)
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



@DelicateCoroutinesApi
@ExperimentalStdlibApi
@ObsoleteCoroutinesApi
@KtorExperimentalLocationsAPI
@ExperimentalTime
@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.mainModule(testing: Boolean = false, client : KubernetesClient) {
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
    } else {
        Sentry.init { config -> config.dsn = "" }
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

    if(!initDb("jdbc:postgresql://$SQL_HOST/$SQL_DB", SQL_USER, SQL_PASSWORD)) {
        log.error("can't init database")
        throw RuntimeException("failed to init db")
    }

    if (!testing) {
        GlobalScope.launch { updateNewContainers(client) }
        GlobalScope.launch { updateRunningContainers(client) }
        GlobalScope.launch { markInstanceForSweep() }
        GlobalScope.launch { sweepContainers(client) }
    }

    containerApi(client)
    installAuth(testing)
    installInternalApi(prometheusMeterRegistry)

    routing {
        home(client)
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


val ApplicationCall.session: UserSession?
    get() = sessions.get<UserSession>()

suspend fun ApplicationCall.respondSeeOther(url: String) {
    response.headers.append(HttpHeaders.Location, url)
    respond(HttpStatusCode.SeeOther)
}