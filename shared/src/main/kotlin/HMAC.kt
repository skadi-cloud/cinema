package cloud.skadi.shared.hmac

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

fun sign(token: String): Pair<String, String> {
    val nonce = Random.nextBytes(32)
    val algo = "HmacSHA256"
    val mac = Mac.getInstance(algo)

    mac.init(SecretKeySpec(token.toByteArray(), algo))

    val sig = hex(mac.doFinal(nonce))
    return  Pair(sig, hex(nonce))
}


fun check(sig: String, nonceRaw: String, token: String): Boolean {
    val nonce = hex(nonceRaw)
    val algo = "HmacSHA256"
    val mac = Mac.getInstance(algo)

    mac.init(SecretKeySpec(token.toByteArray(), algo))
    val calculatedSig = mac.doFinal(nonce)
    println(sig)
    println(hex(calculatedSig))
    return calculatedSig.contentEquals(hex(sig))
}

/**
 * Encode [bytes] as a HEX string with no spaces, newlines and `0x` prefixes.
 */
private fun hex(bytes: ByteArray): String {
    val result = CharArray(bytes.size * 2)
    var resultIndex = 0
    val digits = "0123456789abcdef".toCharArray()

    for (index in 0 until bytes.size) {
        val b = bytes[index].toInt() and 0xff
        result[resultIndex++] = digits[b shr 4]
        result[resultIndex++] = digits[b and 0x0f]
    }

    return String(result)
}

/**
 * Decode bytes from HEX string. It should be no spaces and `0x` prefixes.
 */
private fun hex(s: String): ByteArray {
    val result = ByteArray(s.length / 2)
    for (idx in 0 until result.size) {
        val srcIdx = idx * 2
        val high = s[srcIdx].toString().toInt(16) shl 4
        val low = s[srcIdx + 1].toString().toInt(16)
        result[idx] = (high or low).toByte()
    }

    return result
}