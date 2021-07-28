package cloud.skadi.gist

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.seruco.encoding.base62.Base62
import java.nio.ByteBuffer
import java.util.*

private val base62 = Base62.createInstance()!!
private val mapper = JsonMapper.builder()
    .addModule(KotlinModule(strictNullChecks = true))
    .build()

fun Any.asJson() = mapper.writeValueAsString(this)
fun String.decodeBase62UUID(): UUID {
    val decoded = base62.decode(this.toByteArray())
    val bytes = ByteBuffer.allocate(decoded.size).put(decoded).rewind()
    return UUID(bytes.long, bytes.long)
}

fun UUID.encodeBase62() = base62.encode(
    ByteBuffer.allocate(16).putLong(this.mostSignificantBits)
        .putLong(this.leastSignificantBits).array()
).decodeToString()

fun String.decodeBase64() = Base64.getMimeDecoder().decode(this)!!