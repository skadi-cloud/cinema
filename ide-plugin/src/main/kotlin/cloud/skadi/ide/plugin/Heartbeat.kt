package cloud.skadi.ide.plugin

import com.intellij.openapi.application.PreloadingActivity
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import org.jetbrains.projector.agent.AgentLauncher
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration


class SkadiHeartbeat : PreloadingActivity() {
    private val logger = Logger.getInstance(this::class.java)
    private lateinit var backendAddress: String
    private lateinit var skadiInstance: String

    @ObsoleteCoroutinesApi
    val ticker = ticker(60_000, initialDelayMillis = 120_000)

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

        this.skadiInstance = id
        this.backendAddress = address

        GlobalScope.launch {
            for (e in ticker) {
                heartbeat()
            }
        }
    }

    private suspend fun heartbeat() {
        if (AgentLauncher.getClientList().isNotEmpty()) {
            try {
                val client = HttpClient.newHttpClient()
                val request = HttpRequest.newBuilder()
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .uri(URI.create("http://$backendAddress/heartbeat/$skadiInstance"))
                    .build()
                client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
                logger.info("heartbeat send")
            } catch (e: Exception) {
                logger.error("error sending heartbeat", e)
            }
        } else {
            logger.info("no clients connected, not sending heartbeat.")
        }
    }
}