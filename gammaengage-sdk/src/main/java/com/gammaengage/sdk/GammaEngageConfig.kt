package com.gammaengage.sdk

/**
 * @param baseUrl    Your GammaEngage API origin, e.g. `https://api.example.com`.
 * @param brandId    The brand (tenant) id.
 * @param apiKey     The brand's API key, sent as `X-Tenant-API-Key`.
 * @param hmacSecret The API key's HMAC secret. Optional — when set, every request is signed.
 * @param debug      Log SDK activity to Logcat (tag `GammaEngage`).
 */
data class GammaEngageConfig(
    val baseUrl: String,
    val brandId: String,
    val apiKey: String,
    val hmacSecret: String? = null,
    val debug: Boolean = false,
)
