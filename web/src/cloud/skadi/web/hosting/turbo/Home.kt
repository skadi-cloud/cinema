package cloud.skadi.web.hosting.turbo

import cloud.skadi.web.hosting.data.KernelFContainer
import cloud.skadi.web.hosting.data.User
import cloud.skadi.web.hosting.views.instanceControls
import cloud.skadi.web.hosting.views.instanceStatusFrameContent
import cloud.skadi.web.hosting.views.template
import cloud.skadi.web.hosting.views.turboStream
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.html.stream.createHTML
import org.slf4j.LoggerFactory


class HomeTurboStream(val user: User, channel: SendChannel<Frame>) :
    WebsocketTurboChannel<KernelFContainer>(
        channel,
        LoggerFactory.getLogger("user-${user.id}-home-turbostream")
    ) {
    override fun matchesKey(key: String): Boolean {
        return key == user.email || user.containers.any { it.id.value.toString() == key }
    }

    override suspend fun sendUpdate(data: KernelFContainer): SendResult {
        val r = send(Frame.Text(instanceControlsUpdate(data)))
        if(r != SendResult.Success) {
            return r
        }
        return send(Frame.Text(instanceStatusUpdate(data)))
    }

    private fun instanceStatusUpdate(it: KernelFContainer) =
        createHTML().turboStream {
            target = "status-${it.id.value}"
            action = "update"
            template {
                instanceStatusFrameContent(it)
            }
        }

    private fun instanceControlsUpdate(it: KernelFContainer) =
        createHTML().turboStream {
            target = "controls-${it.id.value}"
            action = "update"
            template {
                instanceControls(it)
            }
        }

}