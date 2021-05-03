package test.cloud.skadi.web.hosting

import cloud.skadi.shared.hmac.signNonce
import cloud.skadi.web.hosting.routing.CONTAINER_LATEST
import cloud.skadi.web.hosting.mainModule
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.server.testing.*
import io.ktor.util.*
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import java.util.*
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
                assertEquals("http://localhost/login/github?rd=%2Fhome", response.headers[HttpHeaders.Location])
            }
        }
    }

    @Test
    fun `metrics are not available on public`() {
        ensureDbEmpty()
        withTestApplication({ mainModule(testing = true) }) {
            handleRequest(HttpMethod.Get, "/metrics").apply {
                assertEquals(HttpStatusCode.Forbidden, response.status())
            }
        }
    }

    @Test
    fun `health check not available on public`() {
        ensureDbEmpty()
        withTestApplication({ mainModule(testing = true) }) {
            handleRequest(HttpMethod.Get, "/health").apply {
                assertEquals(HttpStatusCode.Forbidden, response.status())
            }
        }
    }

    @Test
    fun `task queue  not available on public`() {
        ensureDbEmpty()
        withTestApplication({ mainModule(testing = true) }) {
            handleRequest(HttpMethod.Post, "/tasks/${UUID.randomUUID()}/dequeue").apply {
                assertEquals(HttpStatusCode.Forbidden, response.status())
            }
        }
    }

    @Test
    fun `task state success  not available on public`() {
        ensureDbEmpty()
        withTestApplication({ mainModule(testing = true) }) {
            handleRequest(HttpMethod.Post, "/tasks/${UUID.randomUUID()}/success").apply {
                assertEquals(HttpStatusCode.Forbidden, response.status())
            }
        }
    }

    fun `task state error  not available on public`() {
        ensureDbEmpty()
        withTestApplication({ mainModule(testing = true) }) {
            handleRequest(HttpMethod.Post, "/tasks/${UUID.randomUUID()}/success").apply {
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
                    setBody(listOf("version" to CONTAINER_LATEST.name).formUrlEncode())
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


}
