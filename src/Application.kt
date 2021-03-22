package ws.logv.hosting

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.http.*
import io.ktor.html.*
import kotlinx.html.*
import kotlinx.css.*
import io.ktor.content.*
import io.ktor.http.content.*
import io.ktor.features.*
import io.ktor.auth.*
import com.fasterxml.jackson.databind.*
import io.ktor.jackson.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

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

    install(Authentication) {
    }

    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }

    Database.connect(
        "jdbc:pgsql://localhost:5432/kernelf", driver = "com.impossibl.postgres.jdbc.PGDriver",
        user = "postgres", password = "mysecretpassword"
    )

    transaction {
        // print sql to std-out
        addLogger(StdOutSqlLogger)
    }

    routing {
        get("/") {
            call.index()
        }
        post("/new-container") {
            val user = call.getUserEMail()
            if(!canCreateContainer(user)) {
                call.respond(HttpStatusCode.ServiceUnavailable, "You can't create more containers!")
                return@post
            }
            createContainer(UUID.randomUUID().toString())

        }

        get("/html-dsl") {
            call.respondHtml {
                body {
                    h1 { +"HTML" }
                    ul {
                        for (n in 1..10) {
                            li { +"$n" }
                        }
                    }
                }
            }
        }

        get("/styles.css") {
            call.respondCss {
                body {
                    backgroundColor = Color.red
                }
                p {
                    fontSize = 2.em
                }
                rule("p.myclass") {
                    color = Color.blue
                }
            }
        }

        // Static feature. Try to access `/static/ktor_logo.svg`
        static("/static") {
            resources("static")
        }

        get("/json/jackson") {
            call.respond(mapOf("hello" to "world"))
        }
    }
}

private suspend fun ApplicationCall.getUserEMail(): String {
    return "k.dummann@gmail.com"
}

private fun createUserIfNotExists(email: String) {
    if (!userExists(email)) {
        createUser(email)
    }
}

private suspend fun ApplicationCall.index() {
    val user = this.getUserEMail()
    createUserIfNotExists(user)
    respondHtml {
        head { title { +"Kernel F on k8s" } }
        body {
            div {
                form {
                    button {
                        type = ButtonType.submit
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
                                +"actions"
                            }
                        }
                    }
                }
            }
        }
    }
}

fun FlowOrMetaDataContent.styleCss(builder: CSSBuilder.() -> Unit) {
    style(type = ContentType.Text.CSS.toString()) {
        +CSSBuilder().apply(builder).toString()
    }
}

fun CommonAttributeGroupFacade.style(builder: CSSBuilder.() -> Unit) {
    this.style = CSSBuilder().apply(builder).toString().trim()
}

suspend inline fun ApplicationCall.respondCss(builder: CSSBuilder.() -> Unit) {
    this.respondText(CSSBuilder().apply(builder).toString(), ContentType.Text.CSS)
}
