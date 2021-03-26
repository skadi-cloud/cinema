package ws.logv.hosting

import com.fasterxml.jackson.databind.*
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder
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
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.css.*
import kotlinx.html.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.transaction
import org.kohsuke.github.GitHub
import org.kohsuke.github.GitHubBuilder
import java.util.*
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.toDuration
import com.fkorotkov.kubernetes.extensions.*
import io.fabric8.kubernetes.api.model.IntOrString
import ws.logv.hosting.data.KernelFContainers
import ws.logv.hosting.data.Users
import ws.logv.hosting.data.createUser
import ws.logv.hosting.data.userExists
import java.lang.IllegalArgumentException


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

@Location("/login/{type?}")
class Login(val type: String = "")

data class UserSession(
    val username: String,
    val email: String,
    val idToken: String
)

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@KtorExperimentalLocationsAPI
@ExperimentalTime
@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
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

    Database.connect(
        "jdbc:pgsql://$SQL_HOST/$SQL_DB", driver = "com.impossibl.postgres.jdbc.PGDriver",
        user = SQL_USER, password = SQL_PASSWORD
    )

    transaction {
        // print sql to std-out
        addLogger(StdOutSqlLogger)

        try {
            SchemaUtils.create(Users, KernelFContainers)
        } catch (_: Throwable) {

        }
    }

    containerApi()
    installAuth()

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

private fun createUserIfNotExists(email: String) {
    if (!userExists(email)) {
        createUser(email)
    }
}

val ApplicationCall.session: UserSession?
    get() = sessions.get<UserSession>()
