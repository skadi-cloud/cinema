package cloud.skadi.web.hosting

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.features.*
import io.ktor.html.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.html.*
import org.kohsuke.github.GitHubBuilder
import kotlin.time.ExperimentalTime

@ExperimentalTime
fun Application.installAuth(testing: Boolean) {
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

    install(Sessions) {
        cookie<UserSession>("SkadiSession") {
            val salt = hex(COOKIE_SALT)
            transform(SessionTransportTransformerMessageAuthentication(salt))
            cookie.httpOnly = true
            cookie.maxAge = null
            //cookie.secure = true
            cookie.extensions["SameSite"] = "strict"
        }
    }

    routing {
        if(testing) {
            post("/testlogin") {
                val session = UserSession(
                    username = "testuser",
                    email = "test@test.de",
                    idToken = "accessToken"
                )
                call.sessions.set(session)
                call.loginSuccess(HOME_PATH)
            }
        }
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
                    call.loginSuccess(HOME_PATH)
                }
            }
        }
        // Perform logout by cleaning cookies and start RP-initiated logout
        get("/logout") {
            call.sessions.clear<UserSession>()
            call.respondRedirect("/")
        }
    }
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

private suspend fun ApplicationCall.loginSuccess(target: String) {
    respondHtml {
        head {
            title { +"Login Successful" }
            meta { httpEquiv="refresh"
            content ="0; url=$target"}
        }
        body {
            h1 {
                +"Login Successful"
            }
        }
    }
}