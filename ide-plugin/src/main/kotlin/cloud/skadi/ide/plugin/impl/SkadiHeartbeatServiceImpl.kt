package cloud.skadi.ide.plugin.impl

import cloud.skadi.ide.plugin.SkadiHeartbeatService
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.*

class SkadiHeartbeatServiceImpl : SkadiHeartbeatService, Disposable {
    private var wasActive = false
    private val caretListener: CaretListener
    private val documentListener: DocumentListener
    private val logger = Logger.getInstance(this::class.java)
    private val timer = Timer(true)

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

        multicaster.addCaretListener(caretListener, this)
        multicaster.addDocumentListener(documentListener, this)
        installHeartbeat()
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
            result
        }, 0, 60_000)
    }

    override fun dispose() {
        val multicaster = EditorFactory.getInstance().eventMulticaster
        multicaster.removeCaretListener(caretListener)
        multicaster.removeDocumentListener(documentListener)
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
                    val client = HttpClient.newHttpClient()
                    val request = HttpRequest.newBuilder()
                        .POST(HttpRequest.BodyPublishers.ofString(token))
                        .timeout(Duration.ofSeconds(30))
                        .uri(URI.create("http://$backendAddress/heartbeat/$skadiInstance"))
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