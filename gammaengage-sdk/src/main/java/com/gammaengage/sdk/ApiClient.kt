package com.gammaengage.sdk

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.Executors

internal class ApiClient(private val config: GammaEngageConfig) {

    private val baseUrl = config.baseUrl.trimEnd('/')
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "gammaengage-sdk").apply { isDaemon = true } }

    fun sendEvent(
        eventType: String,
        playerId: String,
        payload: List<Pair<String, String>>,
        onDone: ((Boolean) -> Unit)? = null,
    ) {
        run(onDone) {
            val body = EventSigner.buildBody(
                eventType = eventType,
                brandId = config.brandId,
                playerId = playerId,
                payload = payload,
                eventId = UUID.randomUUID().toString(),
                idempotencyKey = UUID.randomUUID().toString(),
                occurredAt = isoNow(),
            )
            val conn = open("$baseUrl/events", "POST")
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-Tenant-API-Key", config.apiKey)
            config.hmacSecret?.let {
                conn.setRequestProperty("X-GammaEngage-Signature-Content", EventSigner.hmacHex(it, body))
                conn.setRequestProperty("X-GammaEngage-Signature-Version", "1")
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            finish(conn, eventType)
        }
    }

    /** Public, unsigned endpoint — records that the notification carrying this tracking id was tapped. */
    fun reportOpen(trackingEventId: String) {
        run(null) {
            val id = java.net.URLEncoder.encode(trackingEventId, "UTF-8")
            val brand = java.net.URLEncoder.encode(config.brandId, "UTF-8")
            finish(open("$baseUrl/t/po/$brand/$id", "GET"), "push open")
        }
    }

    private fun open(url: String, method: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        return conn
    }

    private fun finish(conn: HttpURLConnection, what: String): Boolean {
        try {
            val code = conn.responseCode
            val ok = code in 200..299
            if (ok) log("$what -> HTTP $code") else Log.w(TAG, "$what -> HTTP $code")
            return ok
        } finally {
            conn.disconnect()
        }
    }

    private fun run(onDone: ((Boolean) -> Unit)?, block: () -> Boolean) {
        executor.execute {
            val ok = try {
                block()
            } catch (e: Exception) {
                Log.w(TAG, "request failed", e)
                false
            }
            onDone?.invoke(ok)
        }
    }

    private fun log(msg: String) {
        if (config.debug) Log.d(TAG, msg)
    }

    private fun isoNow(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(java.util.Date())
    }

    private companion object {
        const val TAG = "GammaEngage"
    }
}
