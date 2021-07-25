package cloud.skadi.gist.routing

import cloud.skadi.gist.data.*
import cloud.skadi.gist.plugins.gistSession
import cloud.skadi.gist.shared.GistVisibility
import cloud.skadi.gist.views.RootTemplate
import io.ktor.application.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import io.seruco.encoding.base62.Base62
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.io.InputStream
import java.nio.ByteBuffer
import java.time.LocalDateTime
import java.util.*
import kotlin.collections.set

data class GistPart(var name: String? = null, var node: String? = null, var image: InputStream? = null)

fun Application.configureGistRouting(upload: suspend (GistRoot, InputStream) -> Unit) {
    routing {
        post("/gist/create") {
            val token = call.request.header("X-SKADI-GIST-TOKEN")

            val user = if (token != null)
                newSuspendedTransaction {
                    Token.find { TokenTable.token eq token }.firstOrNull()?.user
                }
            else
                null


            val parts = call.receiveMultipart()
            val parsedRoot = mutableMapOf<Int, GistPart>()

            var name: String? = null
            var description: String? = null
            var visibility: GistVisibility? = null

            parts.forEachPart {
                when {
                    it.name?.startsWith("image-") ?: false -> {
                        val rootId = it.name?.substring(6)?.toIntOrNull() ?: return@forEachPart
                        val fileItem = it as PartData.FileItem
                        var parsed = parsedRoot[rootId]
                        if (parsed == null) {
                            parsed = GistPart()
                            parsedRoot[rootId] = parsed
                        }
                        parsed.image = fileItem.streamProvider()
                    }
                    it.name?.startsWith("node-") ?: false -> {
                        val rootId = it.name?.substring(5)?.toIntOrNull() ?: return@forEachPart
                        var parsed = parsedRoot[rootId]
                        if (parsed == null) {
                            parsed = GistPart()
                            parsedRoot[rootId] = parsed
                        }
                        val partItem = it as PartData.FormItem
                        parsed.node = partItem.value
                    }
                    it.name?.startsWith("name-") ?: false -> {
                        val rootId = it.name?.substring(5)?.toIntOrNull() ?: return@forEachPart
                        var parsed = parsedRoot[rootId]
                        if (parsed == null) {
                            parsed = GistPart()
                            parsedRoot[rootId] = parsed
                        }
                        val partItem = it as PartData.FormItem
                        parsed.name = partItem.value
                    }
                    it.name == "name" -> {
                        val partItem = it as PartData.FormItem
                        name = partItem.value
                    }
                    it.name == "description" -> {
                        val partItem = it as PartData.FormItem
                        description = partItem.value
                    }
                    it.name == "visibility" -> {
                        val partItem = it as PartData.FormItem
                        visibility = enumValueOf<GistVisibility>(partItem.value)
                    }
                    else -> {
                        log.warn("unknown part with name: ${it.name}")
                    }
                }
            }
            if (user == null) {
                visibility = GistVisibility.Public
            }

            if (name == null || description == null || visibility == null || parsedRoot.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest)
                log.error("missing property of the gist name=$name, description=$description, visibility=$visibility, partsIsEmpty=${parsedRoot.isEmpty()}")
                return@post
            }
            val gistAndRoots = newSuspendedTransaction {
                val gist = Gist.new {
                    this.description = description
                    this.name = name!!
                    this.visibility = visibility!!
                    this.created = LocalDateTime.now()
                    this.user = user
                }

                gist to parsedRoot.map {
                    GistRoot.new {
                        this.gist = gist
                        this.name = it.value.name!!
                        this.node = it.value.node!!
                    } to it.value.image!!
                }
            }

            gistAndRoots.second.forEach {
                upload(it.first, it.second)
            }
            val base62 = Base62.createInstance()

            val encodedId = base62.encode(
                ByteBuffer.allocate(16).putLong(gistAndRoots.first.id.value.mostSignificantBits)
                    .putLong(gistAndRoots.first.id.value.leastSignificantBits).array()
            ).decodeToString()

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
            val base62 = Base62.createInstance()

            val decoded = base62.decode(idParam.toByteArray())
            val bytes = ByteBuffer.allocate(decoded.size).put(decoded).rewind()
            val gistId = UUID(bytes.long, bytes.long)

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

            call.respondHtmlTemplate(RootTemplate("Skadi Gist", user = user)) {
                content {

                }
            }
        }
    }
}