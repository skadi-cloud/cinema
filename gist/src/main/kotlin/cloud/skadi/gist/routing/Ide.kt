package cloud.skadi.gist.routing

import cloud.skadi.gist.shared.PARAMETER_CALLBACK
import cloud.skadi.gist.shared.PARAMETER_DEVICE_NAME
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*

fun Application.configureIdeRoutes() {

    routing {
        route("/ide") {
            get("hello") {
                call.respond("Hello")
            }
            get("login") {
                val callback = call.parameters[PARAMETER_CALLBACK]
                val deviceName = call.parameters[PARAMETER_DEVICE_NAME]

                if(callback == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }
                if (deviceName == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }
            }
        }
    }

}