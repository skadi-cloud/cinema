package cloud.skadi.gist.mps.plugin

import io.ktor.util.*
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.ide.HttpRequestHandler
import kotlin.random.Random

class LoginRequestHandler: HttpRequestHandler() {
    private var token : String? = null
    override fun isSupported(request: FullHttpRequest): Boolean {
        return (request.method() == HttpMethod.GET || request.method() == HttpMethod.POST) && (request.uri().startsWith("/skadi-gist/login"))
    }

    override fun process(
        urlDecoder: QueryStringDecoder,
        request: FullHttpRequest,
        context: ChannelHandlerContext
    ): Boolean {
        if(request.method() == HttpMethod.GET) {
            val nextBytes = Random.Default.nextBytes(4)
            token = hex(nextBytes)
            val resourceAsStream = LoginRequestHandler::class.java.getResourceAsStream("login.html")
            val indexWithToken = resourceAsStream.bufferedReader().readText().replace("{token}", token!!).toByteArray()
            sendData(indexWithToken, "index.html", request, context.channel(),request.headers())
            return true
        }
       return false
    }
}