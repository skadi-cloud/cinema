package test.cloud.skadi.web.hosting

import cloud.skadi.shared.hmac.sign
import cloud.skadi.web.hosting.data.ContainerVersion
import cloud.skadi.web.hosting.mainModule
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.server.testing.*
import io.ktor.util.*
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

@KtorExperimentalAPI
@ObsoleteCoroutinesApi
@KtorExperimentalLocationsAPI
@ExperimentalTime
class ApplicationTest {

    fun internalApi() = Stream.of("/health", "/metrics", "")

    @Test
    fun testRoot() {
        ensureDbEmpty()
        withTestApplication({ mainModule(testing = true) }) {
            handleRequest(HttpMethod.Get, "/").apply {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    @Test
    fun `login allows home access`() {
        ensureDbEmpty()
        withTestApplication({ mainModule(testing = true) }) {
            cookiesSession {
                handleRequest(HttpMethod.Post, "/testlogin").apply {
                    assertEquals(HttpStatusCode.OK, response.status())
                }
                handleRequest(HttpMethod.Get, "/home").apply {
                    assertEquals(HttpStatusCode.OK, response.status())
                }
            }
        }
    }

    @Test
    fun `home is not accessible without login`() {
        ensureDbEmpty()
        withTestApplication({ mainModule(testing = true) }) {
            handleRequest(HttpMethod.Get, "/home").apply {
                assertEquals(HttpStatusCode.Found, response.status())
                assertEquals("/", response.headers[HttpHeaders.Location])
            }
        }
    }

    @Test
    fun `metrics are available on internal api`() {
        withTestApplication({ mainModule(testing = true) }) {
            handleRequest(HttpMethod.Get, "/metrics") {
                addHeader(HttpHeaders.Host, "localhost:9090")
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assert(response.content!!.contains("jvm_buffer_total_capacity_bytes"))
                assert(response.content!!.contains("ktor_http_server_requests_active"))
            }
        }
    }

    @Test
    fun `metrics are not available on public`() {
        withTestApplication({ mainModule(testing = true) }) {
            handleRequest(HttpMethod.Get, "/metrics").apply {
                assertEquals(HttpStatusCode.Forbidden, response.status())
            }
        }
    }

    @Test
    fun `healthcheck available on internal api`() {
        withTestApplication({ mainModule(testing = true) }) {
            handleRequest(HttpMethod.Get, "/health") {
                addHeader(HttpHeaders.Host, "localhost:9090")
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    @Test
    fun `health check not available on public`() {
        withTestApplication({ mainModule(testing = true) }) {
            handleRequest(HttpMethod.Get, "/health").apply {
                assertEquals(HttpStatusCode.Forbidden, response.status())
            }
        }
    }

    @Test
    fun `hearbeat not available on public`() {
        withTestApplication({ mainModule(testing = true) }) {
            handleRequest(HttpMethod.Post, "/heartbeat/ce49e6c1-54b0-4e74-b705-3d4e185879f3").apply {
                assertEquals(HttpStatusCode.Forbidden, response.status())
            }
        }
    }

    @Test
    fun `hearbeat available on internal api`() {
        withTestApplication({ mainModule(testing = true) }) {
            handleRequest(HttpMethod.Post, "/heartbeat/ce49e6c1-54b0-4e74-b705-3d4e185879f3") {
                addHeader(HttpHeaders.Host, "localhost:9090")
            }.apply {
                assertEquals(HttpStatusCode.NotFound, response.status())
            }
        }
    }

    @Test
    fun `creating a new playground works`() {
        ensureDbEmpty()
        withTestApplication({ mainModule(testing = true) }) {
            cookiesSession {
                handleRequest(HttpMethod.Post, "/testlogin").apply {
                    assertEquals(HttpStatusCode.OK, response.status())
                }
                handleRequest(HttpMethod.Get, "/home").apply {
                    assertEquals(HttpStatusCode.OK, response.status())
                }
                handleRequest(HttpMethod.Post, "/new-container") {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                    setBody(listOf("version" to ContainerVersion.V2020_3_4731_f5286c0.name).formUrlEncode())
                }.apply {
                    assertEquals(HttpStatusCode.SeeOther, response.status())
                    assertEquals("/home", response.headers[HttpHeaders.Location])
                }
                handleRequest(HttpMethod.Get, "/home").apply {
                    assertEquals(HttpStatusCode.OK, response.status())
                    val body = Jsoup.parse(response.content)
                    val rows = body.select("#instances > table > tbody > tr")
                    assertEquals(1, rows.size)
                }
            }
        }
    }

    @Test
    fun `deleting a playground works`() {
        ensureDbEmpty()
        withTestApplication({ mainModule(testing = true) }) {
            cookiesSession {
                withContainer {
                    handleRequest(HttpMethod.Post, "/container/${it.id}/delete").apply {
                        assertEquals(HttpStatusCode.SeeOther, response.status())
                    }
                }
            }
        }
    }

    @Test
    fun `heartbeat v1 works`() {
        ensureDbEmpty()
        withTestApplication({ mainModule(testing = true) }) {
            cookiesSession {
                withContainer {
                    handleRequest(HttpMethod.Post, "/heartbeat/${it.id}") {
                        addHeader(HttpHeaders.Host, "localhost:9090")
                        setBody(it.rwToken)
                    }.apply {
                        assertEquals(HttpStatusCode.OK, response.status())
                    }
                }
            }
        }
    }

    @Test
    fun `heartbeat v2 works`() {
        ensureDbEmpty()
        withTestApplication({ mainModule(testing = true) }) {
            cookiesSession {
                withContainer {
                    handleRequest(HttpMethod.Post, "/heartbeat/${it.id}") {
                        addHeader(HttpHeaders.Host, "localhost:9090")
                        addHeader("X-Heartbeat-Version", "2")
                        addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                        val signature = sign(it.rwToken)
                        val body =
                            listOf(Pair("nonce", signature.second), Pair("signature", signature.first)).formUrlEncode()
                        setBody(body)
                    }.apply {
                        assertEquals(HttpStatusCode.OK, response.status())
                    }
                }
            }
        }
    }
}
