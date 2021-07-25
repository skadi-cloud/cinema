package cloud.skadi.gist.plugins

import cloud.skadi.sharred.web.util.getEnvOrDefault
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.locations.*
import io.ktor.sessions.*
import io.ktor.util.*

const val SALT_DEFAULT = "1234567890"
val COOKIE_SALT = getEnvOrDefault("COOKIE_SALT", SALT_DEFAULT)
val GITHUB_SECRET = getEnvOrDefault("GITHUB_SECRET", "")
val GITHUB_ID = getEnvOrDefault("GITHUB_ID", "")

fun Application.configureSecurity() {

    val loginProviders = listOf(
        OAuthServerSettings.OAuth2ServerSettings(
            name = "github",
            authorizeUrl = "https://github.com/login/oauth/authorize",
            accessTokenUrl = "https://github.com/login/oauth/access_token",
            defaultScopes = listOf("user:email"),
            clientId = GITHUB_ID,
            clientSecret = GITHUB_SECRET
        )
    ).associateBy { it.name }
    authentication {
        oauth("gitHubOAuth") {
            client = HttpClient(Apache)
            providerLookup = { loginProviders[application.locations.resolve<Login>(Login::class, this).type] }
            urlProvider = { url(Login(it.name)) }
        }
    }
    install(Sessions) {
        cookie<GistSession>("GistSession") {
            val salt = hex(COOKIE_SALT)
            transform(SessionTransportTransformerMessageAuthentication(salt))
            cookie.extensions["SameSite"] = "lax"
        }
    }
}

data class GistSession(val login: String, val email: String, val ghToken: String)

@Location("/login/{type?}")
class Login(val type: String = "")

fun ApplicationCall.gistSession() = sessions.get<GistSession>()