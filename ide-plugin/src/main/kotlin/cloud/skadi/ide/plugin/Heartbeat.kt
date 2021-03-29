package cloud.skadi.ide.plugin

import com.intellij.openapi.application.PreloadingActivity
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.jetbrains.projector.agent.AgentLauncher
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.*


class SkadiHeartbeat : PreloadingActivity() {
    private val logger = Logger.getInstance(this::class.java)
    var timer = Timer(true)

    @ObsoleteCoroutinesApi
    override fun preload(indicator: ProgressIndicator) {
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

        timer.scheduleAtFixedRate(Task(address, id, token), 0, 60_000)
    }

    private class Task(val backendAddress: String, val skadiInstance: String, val token: String) : TimerTask() {
        private val logger = Logger.getInstance(this::class.java)
        override fun run() {
            if (AgentLauncher.getClientList().isNotEmpty()) {
                try {
                    val client = HttpClient.newHttpClient()
                    val request = HttpRequest.newBuilder()
                        .POST(HttpRequest.BodyPublishers.ofString(token))
                        .timeout(Duration.ofSeconds(30))
                        .uri(URI.create("http://$backendAddress/heartbeat/$skadiInstance"))
                        .build()
                    client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept {
                        logger.info("heartbeat send")
                        println("heartbeat send: $it")

                    }.join()
                } catch (e: Exception) {
                    logger.error("error sending heartbeat", e)
                }
            } else {
                logger.warn("no clients connected, not sending heartbeat.")
            }
        }
    }
}