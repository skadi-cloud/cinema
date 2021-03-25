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

const val HOST_URL = "kernelf.logv.ws"

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

    install(Sessions) {
        cookie<UserSession>("KernelFSession") {
            val salt = hex(COOKIE_SALT)
            transform(SessionTransportTransformerMessageAuthentication(salt))
            cookie.httpOnly = true
            cookie.maxAge = 30.toDuration(DurationUnit.DAYS)
        }
    }

    val loginProviders = listOf(
        OAuthServerSettings.OAuth2ServerSettings(
            name = "github",
            authorizeUrl = "https://github.com/login/oauth/authorize",
            accessTokenUrl = "https://github.com/login/oauth/access_token",
            clientId = GITHUB_ID,
            clientSecret = GITHUB_SECRET,
            defaultScopes = listOf("user:email")
        )
    ).associateBy { it.name }

    install(Authentication) {
        oauth("gitHubOAuth") {
            client = HttpClient(Apache)
            providerLookup = { loginProviders[application.locations.resolve<Login>(Login::class, this).type] }
            urlProvider = { url(Login(it.name)) }
        }
    }

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

    routing {
        get("/") {
            call.index()
        }

        val HOME_PATH = "/home"
        authenticate("gitHubOAuth") {
            location<Login>() {
                param("error") {
                    handle {
                        call.loginFailedPage(call.parameters.getAll("error").orEmpty())
                    }
                }

                handle {
                    val principal = call.authentication.principal<OAuthAccessTokenResponse.OAuth2>()
                    val accessToken = principal!!.accessToken
                    val github =
                        withContext(Dispatchers.IO) {
                            GitHubBuilder().withOAuthToken(accessToken).build()
                        }
                    val myself = github.myself

                    val session = UserSession(
                        username = myself.name,
                        email = myself.email,
                        idToken = accessToken
                    )

                    call.sessions.set(session)
                    call.respondRedirect(HOME_PATH)
                }
            }
        }
        // Perform logout by cleaning cookies and start RP-initiated logout
        get("/logout") {
            call.sessions.clear<UserSession>()
            call.respondRedirect("/")
        }

        get(HOME_PATH) {
            if (call.session == null) {
                call.respondRedirect("/")
                return@get
            }
            call.home(call.session!!.username, call.session!!.email)
        }
        post("/new-container") {
            if (call.session == null) {
                call.respondRedirect("/")
                return@post
            }
            val user = call.getUserEMail()
            if (!canCreateContainer(user)) {
                call.respond(HttpStatusCode.ServiceUnavailable, "You can't create more containers!")
                return@post
            }
            createContainer(UUID.randomUUID().toString(), user, "latest")
            call.respondRedirect(HOME_PATH)
        }

        post("/container/{id}/delete") {
            if (call.session == null) {
                call.respondRedirect("/")
                return@post
            }

            val containerId = call.parameters["id"]!!
            deleteContainer(containerId)
            call.respondRedirect(HOME_PATH)
        }
        post("/container/{id}/start") {
            if (call.session == null) {
                call.respondRedirect("/")
                return@post
            }
            val containerId = call.parameters["id"]!!
            val container = getContainerByName(containerId)
            if (container == null) {
                call.respond(HttpStatusCode.NotFound, "unknown container")
                return@post
            }

            if (canStartContainer(container)) {
                startContainer(container)
            }
            call.respondRedirect(HOME_PATH)
        }
        post("/container/{id}/stop") {
            if (call.session == null) {
                call.respondRedirect("/")
                return@post
            }

            val containerId = call.parameters["id"]!!
            val container = getContainerByName(containerId)

            if (container == null) {
                call.respond(HttpStatusCode.NotFound, "unknown container")
                return@post
            }

            if (canStopContainer(container)) {
                stopContainer(container)
            }

            call.respondRedirect(HOME_PATH)
        }

        // Static feature. Try to access `/static/ktor_logo.svg`
        static("/static") {
            resources("static")
        }
    }
}

private suspend fun ApplicationCall.index() {
    respondHtml {
        defaultHead("KernelF in k8s")
        body {
            a {
                href = url(Login("github"))
                +"Log in with Github"
            }
        }
    }
}

fun stopContainer(container: KernelFContainer) {
    transaction { container.status = "Stopping" }
    GlobalScope.launch {
        delay(60000)
        transaction { container.status = "Stopped" }
    }
}

fun startContainer(container: KernelFContainer) {
    transaction { container.status = "Starting" }
    GlobalScope.launch {
        delay(60000)
        transaction { container.status = "Running" }
    }
}

private fun ApplicationCall.getUserEMail(): String {
    return this.session!!.email
}

private fun createUserIfNotExists(email: String) {
    if (!userExists(email)) {
        createUser(email)
    }
}

private fun HTML.defaultHead(title: String) {
    head { title { +title } }
}

private suspend fun ApplicationCall.home(username: String, email: String) {
    val user = this.getUserEMail()
    createUserIfNotExists(user)
    respondHtml {
        defaultHead("KernelF on k8s")
        body {
            p {
                +"Hello $username (${email})"
            }
            div {
                form {
                    button {
                        type = ButtonType.submit
                        disabled = !canCreateContainer(user)
                        id = "new-containers"
                        formAction = "/new-container"
                        formMethod = ButtonFormMethod.post
                        +"New KernelF Instance"

                    }
                }

            }
            table {
                thead {
                    tr {
                        th {
                            scope = ThScope.col
                            +"Container"
                        }
                        th {
                            scope = ThScope.col
                            +"Kernel F Version"
                        }
                        th {
                            scope = ThScope.col
                            +"Created"
                        }
                        th {
                            scope = ThScope.col
                            +"Status"
                        }
                        th {
                            scope = ThScope.col
                            +"Url"
                        }
                        th {
                            scope = ThScope.col
                            +"Actions"
                        }
                    }
                }
                tbody {
                    containers(user).forEach { container ->
                        tr {
                            td {
                                +container.name
                            }
                            td {
                                +container.kernelFVersion
                            }
                            td {
                                +container.created.toString()
                            }
                            td {
                                +container.status
                            }
                            td {
                                a {
                                    val url = "https://${container.name}.$HOST_URL"
                                    href = url
                                    +url
                                }
                            }
                            td {
                                form {
                                    button {
                                        type = ButtonType.submit
                                        id = "delete-${container.name}"
                                        formAction = "/container/${container.name}/start"
                                        formMethod = ButtonFormMethod.post
                                        disabled = !canStartContainer(container)
                                        +"Start"
                                    }
                                }
                                form {
                                    button {
                                        type = ButtonType.submit
                                        id = "delete-${container.name}"
                                        formAction = "/container/${container.name}/stop"
                                        formMethod = ButtonFormMethod.post
                                        disabled = !canStopContainer(container)
                                        +"Stop"
                                    }
                                }
                                form {
                                    button {
                                        type = ButtonType.submit
                                        id = "delete-${container.name}"
                                        formAction = "/container/${container.name}/delete"
                                        formMethod = ButtonFormMethod.post
                                        +"Delete"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun canStopContainer(container: KernelFContainer): Boolean {
    return container.status == "Running"
}

fun canStartContainer(container: KernelFContainer): Boolean {
    return container.status == "Created" || container.status == "Stopped"
}

private suspend fun ApplicationCall.loginFailedPage(errors: List<String>) {
    respondHtml {
        head {
            title { +"Login with" }
        }
        body {
            h1 {
                +"Login error"
            }

            for (e in errors) {
                p {
                    +e
                }
            }
        }
    }
}

val ApplicationCall.session: UserSession?
    get() = sessions.get<UserSession>()
