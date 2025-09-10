package test.cloud.skadi.web.hosting

import cloud.skadi.shared.data.Task
import cloud.skadi.shared.data.TaskContainer
import cloud.skadi.shared.hmac.signNonce
import cloud.skadi.web.hosting.data.TaskState
import cloud.skadi.web.hosting.data.createTask
import cloud.skadi.web.hosting.data.getContainerById
import cloud.skadi.web.hosting.data.getTask
import cloud.skadi.web.hosting.mainModule
import cloud.skadi.web.hosting.routing.emptyUUID
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient
import io.fabric8.kubernetes.client.server.mock.KubernetesServer
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.server.testing.*
import io.ktor.util.*
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

@ExperimentalStdlibApi
@KtorExperimentalAPI
@KtorExperimentalLocationsAPI
@ObsoleteCoroutinesApi
@ExperimentalTime


class InternalApiTests {

    lateinit var client: KubernetesClient
    lateinit var server: KubernetesServer

    @BeforeEach
    fun before() {
        server = KubernetesServer(true, true)
        server.before()
        client =  server.client
    }

    @Test
    fun `heartbeat v1 works`() {
        ensureDbEmpty()
        withTestApplication({ mainModule(testing = true, client) }) {
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
        withTestApplication({ mainModule(testing = true, client) }) {
            cookiesSession {
                withContainer {
                    handleRequest(HttpMethod.Post, "/heartbeat/${it.id}") {
                        addHeader(HttpHeaders.Host, "localhost:9090")
                        addHeader("X-Heartbeat-Version", "2")
                        addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                        val signature = signNonce(it.rwToken)
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

    @Test
    fun `healthcheck available on internal api`() {
        withTestApplication({ mainModule(testing = true, client) }) {
            handleRequest(HttpMethod.Get, "/health") {
                addHeader(HttpHeaders.Host, "localhost:9090")
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    @Test
    fun `metrics are available on internal api`() {
        withTestApplication({ mainModule(testing = true, client) }) {
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
    fun `task queue returns 404 for none existing instance`() {
        ensureDbEmpty()
        withTestApplication({ mainModule(testing = true, client) }) {
            handleRequest(HttpMethod.Post, "/tasks/${UUID.randomUUID()}/dequeue") {
                addHeader(HttpHeaders.Host, "localhost:9090")
            }.apply {
                assertEquals(HttpStatusCode.NotFound, response.status())
            }
        }
    }

    @Test
    fun `empty task queue for existing instance returns 404`() {
        ensureDbEmpty()
        withTestApplication({ mainModule(testing = true, client) }) {
            cookiesSession {
                withContainer {
                    handleRequest(HttpMethod.Post, "/tasks/${it.id}/dequeue") {
                        addHeader(HttpHeaders.Host, "localhost:9090")
                        addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                        val signature = signNonce(it.rwToken)
                        val body =
                            listOf(Pair("nonce", signature.second), Pair("signature", signature.first)).formUrlEncode()
                        setBody(body)
                    }.apply {
                        assertEquals(HttpStatusCode.NotFound, response.status())
                    }
                }
            }
        }
    }

    @Test
    fun `empty task queue for existing instance fails with invalid signature`() {
        ensureDbEmpty()
        withTestApplication({ mainModule(testing = true, client) }) {
            cookiesSession {
                withContainer {
                    handleRequest(HttpMethod.Post, "/tasks/${it.id}/dequeue") {
                        addHeader(HttpHeaders.Host, "localhost:9090")
                        addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                        val signature = signNonce(it.rwToken)
                        val body =
                            listOf(
                                Pair("nonce", signature.second),
                                Pair("signature", signature.first + "123456")
                            ).formUrlEncode()
                        setBody(body)
                    }.apply {
                        assertEquals(HttpStatusCode.BadRequest, response.status())
                    }
                }
            }
        }
    }

    @Test
    fun `dequeue for existing instance works`() {
        ensureDbEmpty()
        val mapper = JsonMapper.builder()
            .addModule(KotlinModule.Builder().withStrictNullChecks(true).build())
            .build()
        withTestApplication({ mainModule(testing = true, client) }) {
            cookiesSession {
                withContainer { container ->
                    val task = transaction {
                        createTask(
                            getContainerById(container.id)!!,
                            Task.CloneRepo("https://github.com/IETS3/iets3.opensource", emptyUUID)
                        )
                    }
                    handleRequest(HttpMethod.Post, "/tasks/${container.id}/dequeue") {
                        addHeader(HttpHeaders.Host, "localhost:9090")
                        addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                        val signature = signNonce(container.rwToken)
                        val body =
                            listOf(Pair("nonce", signature.second), Pair("signature", signature.first)).formUrlEncode()
                        setBody(body)
                    }.apply {
                        assertEquals(HttpStatusCode.OK, response.status())
                        val savedTask = transaction { getTask(task.id.value) }
                        assertEquals(TaskState.InProgress, savedTask?.state)
                        val tc: TaskContainer = mapper.readValue(response.content!!)
                        val checkResults = cloud.skadi.shared.hmac.check(tc.signature, tc.payload, container.rwToken)
                        assertTrue(checkResults)
                        val deserializedTask: Task.CloneRepo = mapper.readValue(tc.payload)
                        assertEquals(task.id.value, deserializedTask.id)
                        assertEquals("https://github.com/IETS3/iets3.opensource", deserializedTask.url)
                    }
                }
            }
        }
    }
    @Test
    fun `dequeue twice works correctly`() {
        ensureDbEmpty()
        val mapper = JsonMapper.builder()
            .addModule(KotlinModule.Builder().withStrictNullChecks(true).build())
            .build()
        withTestApplication({ mainModule(testing = true, client) }) {
            cookiesSession {
                withContainer { container ->
                    val task = transaction {
                        createTask(
                            getContainerById(container.id)!!,
                            Task.CloneRepo("https://github.com/IETS3/iets3.opensource", emptyUUID)
                        )
                    }
                    handleRequest(HttpMethod.Post, "/tasks/${container.id}/dequeue") {
                        addHeader(HttpHeaders.Host, "localhost:9090")
                        addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                        val signature = signNonce(container.rwToken)
                        val body =
                            listOf(Pair("nonce", signature.second), Pair("signature", signature.first)).formUrlEncode()
                        setBody(body)
                    }.apply {
                        assertEquals(HttpStatusCode.OK, response.status())
                        handleRequest(HttpMethod.Post, "/tasks/${container.id}/dequeue") {
                            addHeader(HttpHeaders.Host, "localhost:9090")
                            addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                            val signature = signNonce(container.rwToken)
                            val body =
                                listOf(Pair("nonce", signature.second), Pair("signature", signature.first)).formUrlEncode()
                            setBody(body)
                        }.apply {
                            assertEquals(HttpStatusCode.NotFound, response.status())
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `finishing a task works`() {
        ensureDbEmpty()
        val mapper = JsonMapper.builder()
            .addModule(KotlinModule.Builder().withStrictNullChecks(true).build())
            .build()
        withTestApplication({ mainModule(testing = true, client) }) {
            cookiesSession {
                withContainer { container ->
                    val task = transaction {
                        createTask(
                            getContainerById(container.id)!!,
                            Task.CloneRepo("https://github.com/IETS3/iets3.opensource", emptyUUID)
                        )
                    }
                    handleRequest(HttpMethod.Post, "/tasks/${container.id}/dequeue") {
                        addHeader(HttpHeaders.Host, "localhost:9090")
                        addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                        val signature = signNonce(container.rwToken)
                        val body =
                            listOf(Pair("nonce", signature.second), Pair("signature", signature.first)).formUrlEncode()
                        setBody(body)
                    }.apply {
                        assertEquals(HttpStatusCode.OK, response.status())
                        val tc: TaskContainer = mapper.readValue(response.content!!)
                        val deserializedTask: Task.CloneRepo = mapper.readValue(tc.payload)
                        handleRequest(HttpMethod.Post, "/tasks/${deserializedTask.id}/success") {
                            addHeader(HttpHeaders.Host, "localhost:9090")
                            addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                            val signature = signNonce(container.rwToken)
                            val body =
                                listOf(
                                    Pair("nonce", signature.second),
                                    Pair("signature", signature.first)
                                ).formUrlEncode()
                            setBody(body)
                        }.apply {
                            assertEquals(HttpStatusCode.OK, response.status())
                            val savedTask = transaction { getTask(task.id.value) }
                            assertEquals(TaskState.Succeeded, savedTask?.state)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `can't finish a task that isn't started`() {
        ensureDbEmpty()
        withTestApplication({ mainModule(testing = true, client) }) {
            cookiesSession {
                withContainer { container ->
                    val task = transaction {
                        createTask(
                            getContainerById(container.id)!!,
                            Task.CloneRepo("https://github.com/IETS3/iets3.opensource", emptyUUID)
                        )
                    }

                    handleRequest(HttpMethod.Post, "/tasks/${task.id.value}/success") {
                        addHeader(HttpHeaders.Host, "localhost:9090")
                        addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                        val signature = signNonce(container.rwToken)
                        val body =
                            listOf(
                                Pair("nonce", signature.second),
                                Pair("signature", signature.first)
                            ).formUrlEncode()
                        setBody(body)
                    }.apply {
                        assertEquals(HttpStatusCode.BadRequest, response.status())
                        val savedTask = transaction { getTask(task.id.value) }
                        assertEquals(TaskState.New, savedTask?.state)
                    }

                }
            }
        }
    }

    @Test
    fun `can't fail a task that isn't started`() {
        ensureDbEmpty()
        withTestApplication({ mainModule(testing = true, client) }) {
            cookiesSession {
                withContainer { container ->
                    val task = transaction {
                        createTask(
                            getContainerById(container.id)!!,
                            Task.CloneRepo("https://github.com/IETS3/iets3.opensource", emptyUUID)
                        )
                    }

                    handleRequest(HttpMethod.Post, "/tasks/${task.id.value}/error") {
                        addHeader(HttpHeaders.Host, "localhost:9090")
                        addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                        val signature = signNonce(container.rwToken)
                        val body =
                            listOf(
                                Pair("nonce", signature.second),
                                Pair("signature", signature.first)
                            ).formUrlEncode()
                        setBody(body)
                    }.apply {
                        assertEquals(HttpStatusCode.BadRequest, response.status())
                        val savedTask = transaction { getTask(task.id.value) }
                        assertEquals(TaskState.New, savedTask?.state)
                    }

                }
            }
        }
    }

    @Test
    fun `failing a task works`() {
        ensureDbEmpty()
        val mapper = JsonMapper.builder()
            .addModule(KotlinModule.Builder().withStrictNullChecks(true).build())
            .build()
        withTestApplication({ mainModule(testing = true, client) }) {
            cookiesSession {
                withContainer { container ->
                    val task = transaction {
                        createTask(
                            getContainerById(container.id)!!,
                            Task.CloneRepo("https://github.com/IETS3/iets3.opensource", emptyUUID)
                        )
                    }
                    handleRequest(HttpMethod.Post, "/tasks/${container.id}/dequeue") {
                        addHeader(HttpHeaders.Host, "localhost:9090")
                        addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                        val signature = signNonce(container.rwToken)
                        val body =
                            listOf(Pair("nonce", signature.second), Pair("signature", signature.first)).formUrlEncode()
                        setBody(body)
                    }.apply {
                        assertEquals(HttpStatusCode.OK, response.status())
                        val tc: TaskContainer = mapper.readValue(response.content!!)
                        val deserializedTask: Task.CloneRepo = mapper.readValue(tc.payload)
                        handleRequest(HttpMethod.Post, "/tasks/${deserializedTask.id}/error") {
                            addHeader(HttpHeaders.Host, "localhost:9090")
                            addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                            val signature = signNonce(container.rwToken)
                            val body =
                                listOf(
                                    Pair("nonce", signature.second),
                                    Pair("signature", signature.first)
                                ).formUrlEncode()
                            setBody(body)
                        }.apply {
                            assertEquals(HttpStatusCode.OK, response.status())
                            val savedTask = transaction { getTask(task.id.value) }
                            assertEquals(TaskState.Failed, savedTask?.state)
                        }
                    }
                }
            }
        }
    }
}