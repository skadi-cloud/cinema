package cloud.skadi.gist.plugins

import io.ktor.locations.*
import io.ktor.features.*
import io.ktor.application.*

fun Application.configureRouting() {
    install(Locations) {
    }
    install(AutoHeadResponse)

}
