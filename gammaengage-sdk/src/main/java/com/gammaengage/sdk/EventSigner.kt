package com.gammaengage.sdk

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Builds the `/events` request body and its HMAC signature.
 *
 * The server verifies the signature over `JSON.stringify(JSON.parse(rawBody))`, so the body we
 * send must already be in that exact form: compact (no whitespace) and with strings escaped the
 * way JavaScript escapes them. [jsonString] mirrors `JSON.stringify` for that reason — a generic
 * JSON library escapes `/` as `\/`, which the server would re-serialize differently and reject.
 */
internal object EventSigner {

    fun hmacHex(secret: String, body: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(body.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    fun jsonString(s: String): String {
        val sb = StringBuilder("\"")
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\b' -> sb.append("\\b")
                c == '\u000C' -> sb.append("\\f")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' -> sb.append("\\u%04x".format(c.code))
                Character.isHighSurrogate(c) && i + 1 < s.length && Character.isLowSurrogate(s[i + 1]) -> {
                    sb.append(c).append(s[i + 1])
                    i++
                }
                Character.isSurrogate(c) -> sb.append("\\u%04x".format(c.code))
                else -> sb.append(c)
            }
            i++
        }
        return sb.append('"').toString()
    }

    fun buildBody(
        eventType: String,
        brandId: String,
        playerId: String,
        payload: List<Pair<String, String>>,
        eventId: String,
        idempotencyKey: String,
        occurredAt: String,
    ): String {
        val payloadJson = payload.joinToString(",") { (k, v) -> "${jsonString(k)}:${jsonString(v)}" }
        return "{" +
            "\"schema_version\":\"1.0\"," +
            "\"event_id\":${jsonString(eventId)}," +
            "\"event_type\":${jsonString(eventType)}," +
            "\"brand_id\":${jsonString(brandId)}," +
            "\"source_system\":\"android-sdk\"," +
            "\"occurred_at\":${jsonString(occurredAt)}," +
            "\"player_ref\":{\"external_player_id\":${jsonString(playerId)}}," +
            "\"payload\":{$payloadJson}," +
            "\"idempotency_key\":${jsonString(idempotencyKey)}" +
            "}"
    }
}
