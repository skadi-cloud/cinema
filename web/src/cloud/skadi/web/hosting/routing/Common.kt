package cloud.skadi.web.hosting.routing

import cloud.skadi.web.hosting.data.KernelFContainer
import cloud.skadi.web.hosting.data.getContainerById
import cloud.skadi.web.hosting.redirectToLoginAndBack
import cloud.skadi.web.hosting.session
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import org.jetbrains.exposed.sql.transactions.transaction

suspend fun ApplicationCall.authenticated(body: suspend () -> Unit) {
    if (this.session == null) {
        this.redirectToLoginAndBack()
        return
    }
    body()
}

suspend fun ApplicationCall.withUserContainerViaParam(body: suspend (KernelFContainer) -> Unit) {
    val containerId = this.parameters["id"]!!
    val container = getContainerById(containerId)
    if (container == null) {
        this.respond(HttpStatusCode.NotFound)
        return
    }

    val isUsersContainer = transaction {
        this@withUserContainerViaParam.session?.email == container.user.email
    }
    if (!isUsersContainer) {
        this.respond(HttpStatusCode.Forbidden)
        return
    }
    body(container)
}