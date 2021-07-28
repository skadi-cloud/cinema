package cloud.skadi.gist.routing

import cloud.skadi.gist.*
import cloud.skadi.gist.data.*
import cloud.skadi.gist.plugins.gistSession
import cloud.skadi.gist.shared.GistCreationRequest
import cloud.skadi.gist.shared.GistVisibility
import cloud.skadi.gist.views.RootTemplate
import io.ktor.application.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import kotlinx.html.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.io.InputStream
import java.time.LocalDateTime

fun Application.configureGistRouting(
    upload: suspend (GistRoot, InputStream) -> Unit,
    url: (ApplicationCall, GistRoot) -> UrlList
) {
    routing {
        post("/gist/create") {
            val token = call.request.header("X-SKADI-GIST-TOKEN")

            val user = if (token != null)
                newSuspendedTransaction {
                    Token.find { TokenTable.token eq token }.firstOrNull()?.user
                }
            else
                null

            val (name, description, visibility, roots) = call.receive<GistCreationRequest>()

            if (roots.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest)
                log.error("missing property of the gist name=$name, description=$description, visibility=$visibility, partsIsEmpty=${roots.isEmpty()}")
                return@post
            }
            val gistAndRoots = newSuspendedTransaction {
                val gist = Gist.new {
                    this.description = description
                    this.name = name
                    if(user == null || visibility == null) {
                        this.visibility = GistVisibility.Public
                    } else {
                        this.visibility = visibility
                    }
                    this.created = LocalDateTime.now()
                    this.user = user
                }

                gist to roots.map {
                    GistRoot.new {
                        this.gist = gist
                        this.name = it.name
                        this.node = it.serialised.asJson()
                    } to it.base64Img.decodeBase64().inputStream()
                }
            }

            newSuspendedTransaction {
                gistAndRoots.second.forEach {
                    upload(it.first, it.second)
                }
            }

            val encodedId = gistAndRoots.first.id.value.encodeBase62()
            call.respondRedirect(call.url {
                path("gist", encodedId)
            })

        }
        get("/gist/{id}") {
            val session = call.gistSession()
            var user: User? = null
            if (session != null) {
                user = newSuspendedTransaction { User.find { Users.email eq session.email }.firstOrNull() }
            }

            val idParam = call.parameters["id"]

            if (idParam == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            val gistId = idParam.decodeBase62UUID()

            val gist = newSuspendedTransaction { Gist.findById(gistId) }
            if (gist == null) {
                log.warn("unknown gist: $gistId")
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            if (gist.visibility == GistVisibility.Private && gist.user != user) {
                log.warn("gist $gistId not visible for user")
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            newSuspendedTransaction {
                call.respondHtmlTemplate(RootTemplate("Skadi Gist", user = user)) {
                    content {
                        h2 {
                            +gist.name
                        }
                        p {
                            +(gist.description ?: "")
                        }
                        gist.roots.notForUpdate().forEach { root ->
                            div(classes = "root") {
                                h3 {
                                    root.name
                                }
                                img(classes = "rendered") {
                                    src = url(call, root).mainUrl
                                }
                                textInput { value = root.node }

                                div(classes = "comments") {
                                    root.comments.notForUpdate().forEach { comment ->
                                        div(classes = "comment") {
                                            p {
                                                +comment.markdown
                                            }
                                        }
                                    }
                                    if (call.gistSession() != null) {
                                        div(classes = "create-comment") {
                                            form {

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
    }
}