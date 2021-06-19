package cloud.skadi.plugins

import io.ktor.auth.*
import io.ktor.util.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.locations.*
import io.ktor.sessions.*
import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*

fun Application.configureSecurity() {

    val loginProviders = listOf(
            OAuthServerSettings.OAuth2ServerSettings(
                    name = "github",
                    authorizeUrl = "https://github.com/login/oauth/authorize",
                    accessTokenUrl = "https://github.com/login/oauth/access_token",
                    clientId = "***",
                    clientSecret = "***"
            )
    ).associateBy { it.name }
    authentication {
        oauth("gitHubOAuth") {
            client = HttpClient(Apache)
            providerLookup = { loginProviders[application.locations.resolve<login>(login::class, this).type] }
            urlProvider = { url(login(it.name)) }
        }
    }
    install(Sessions) {
        cookie<MySession>("MY_SESSION") {
            cookie.extensions["SameSite"] = "lax"
        }
    }
}
