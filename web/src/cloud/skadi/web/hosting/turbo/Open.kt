package cloud.skadi.web.hosting.turbo

import cloud.skadi.web.hosting.data.KernelFContainer
import cloud.skadi.web.hosting.views.openingText
import cloud.skadi.web.hosting.views.template
import cloud.skadi.web.hosting.views.turboStream
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.html.stream.createHTML
import org.slf4j.LoggerFactory

class OpenTurboStream(private val instance: KernelFContainer, private val repo: String, channel: SendChannel<Frame>) :
    WebsocketTurboChannel<KernelFContainer>(
        channel,
        LoggerFactory.getLogger("instance-${instance.id.value}-open-turbostream")
    ) {
    override fun matchesKey(key: String) = key == instance.id.value.toString()

    override suspend fun sendUpdate(data: KernelFContainer): SendResult {
        return send(Frame.Text(instanceStatusUpdate(data)))
    }

    private fun instanceStatusUpdate(data: KernelFContainer) =
        createHTML().turboStream {
            target = "state"
            action = "update"
            template {
                openingText(data, repo)
            }
        }
}