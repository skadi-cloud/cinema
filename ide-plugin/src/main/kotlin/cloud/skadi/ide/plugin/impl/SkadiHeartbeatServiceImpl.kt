package cloud.skadi.ide.plugin.impl

import cloud.skadi.ide.plugin.SkadiHeartbeatService
import cloud.skadi.ide.plugin.ofFormData
import cloud.skadi.shared.hmac.signNonce
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandEvent
import com.intellij.openapi.command.CommandListener
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.*
import org.apache.http.HttpHeaders
import org.apache.http.entity.ContentType
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicInteger


class SkadiHeartbeatServiceImpl : SkadiHeartbeatService, Disposable {
    private var wasActive = false
    private val caretListener: CaretListener
    private val documentListener: DocumentListener
    private val logger = Logger.getInstance(this::class.java)
    private val timer = Timer(true)
    private var activityLock = AtomicInteger(0)

    init {
        val multicaster = EditorFactory.getInstance().eventMulticaster

        caretListener = object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) {
                wasActive = true
            }

            override fun caretAdded(event: CaretEvent) {
                wasActive = true
            }

            override fun caretRemoved(event: CaretEvent) {
                wasActive = true
            }
        }

        documentListener = object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                wasActive = true
            }
        }

        val selectionListener = object : SelectionListener {
            override fun selectionChanged(e: SelectionEvent) {
                wasActive = true
            }
        }

        ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(CommandListener.TOPIC,
            object : CommandListener {

                override fun commandFinished(event: CommandEvent) {
                    wasActive = true
                }

                override fun undoTransparentActionFinished() {
                    wasActive = true
                }
            })

        multicaster.addCaretListener(caretListener, this)
        multicaster.addDocumentListener(documentListener, this)
        multicaster.addSelectionListener(selectionListener, this)
        installHeartbeat()
    }

    override fun acquireActivityLock() {
        this.activityLock.incrementAndGet()
    }

    override fun releaseActivityLock() {
        this.activityLock.decrementAndGet()
    }

    private fun installHeartbeat() {
        logger.info("setting up heartbeat")

        val id = System.getenv("SKADI_INSTANCE_ID")
        if (id == null) {
            logger.error("can't get instance id")
            return
        }

        val address = System.getenv("SKADI_BACKEND_ADDRESS")
        if (address == null) {
            logger.error("can't get backend address")
            return
        }

        val token = System.getenv("ORG_JETBRAINS_PROJECTOR_SERVER_HANDSHAKE_TOKEN")
        if (token == null) {
            logger.error("can't get token")
        }

        timer.scheduleAtFixedRate(Task(address, id, token) {
            val result = this.wasActive
            this.wasActive = false
            result || this.activityLock.get() > 0
        }, 0, 60_000)
    }

    override fun dispose() {
        logger.warn("disposed")
    }

    private class Task(
        val backendAddress: String,
        val skadiInstance: String,
        val token: String,
        val wasActive: () -> Boolean
    ) : TimerTask() {
        private val logger = Logger.getInstance(this::class.java)
        override fun run() {
            if (wasActive()) {
                try {
                    val signature = signNonce(token)

                    val body = mapOf(Pair("nonce", signature.second), Pair("signature", signature.first))

                    val client = HttpClient.newHttpClient()
                    val request = HttpRequest.newBuilder()
                        .POST(ofFormData(body))
                        .timeout(Duration.ofSeconds(30))
                        .uri(URI.create("http://$backendAddress/heartbeat/$skadiInstance"))
                        .header("X-Heartbeat-Version", "2")
                        .header(
                            HttpHeaders.CONTENT_TYPE,
                            ContentType.APPLICATION_FORM_URLENCODED.mimeType
                        )
                        .build()
                    client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept {
                        logger.info("heartbeat send")
                    }.join()
                } catch (e: Exception) {
                    logger.error("error sending heartbeat", e)
                }
            } else {
                logger.warn("no activity not sending heartbeat.")
            }
        }


    }
}


