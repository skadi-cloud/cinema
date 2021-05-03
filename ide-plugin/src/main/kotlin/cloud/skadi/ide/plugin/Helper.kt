package cloud.skadi.ide.plugin

import java.net.URLEncoder
import java.net.http.HttpRequest
import java.nio.charset.StandardCharsets

fun ofFormData(data: Map<String, String>): HttpRequest.BodyPublisher? {
    val result = StringBuilder()
    for ((key, value) in data) {
        if (result.isNotEmpty()) {
            result.append("&")
        }
        val encodedName = URLEncoder.encode(key, StandardCharsets.UTF_8)
        val encodedValue = URLEncoder.encode(value, StandardCharsets.UTF_8)
        result.append(encodedName)
        if (encodedValue != null) {
            result.append("=")
            result.append(encodedValue)
        }
    }
    return HttpRequest.BodyPublishers.ofString(result.toString())
}