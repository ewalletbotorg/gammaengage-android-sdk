package com.gammaengage.sdk

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import java.util.Collections

/**
 * GammaEngage mobile push SDK.
 *
 * ```
 * // Application.onCreate
 * GammaEngage.init(this, GammaEngageConfig(baseUrl, brandId, apiKey, hmacSecret))
 *
 * // after the player logs in
 * GammaEngage.setPlayer("player-123")
 *
 * // on logout
 * GammaEngage.logout()
 * ```
 */
object GammaEngage {

    private const val TAG = "GammaEngage"
    private const val PREFS = "gammaengage_sdk"
    private const val KEY_PLAYER = "player_id"
    private const val KEY_TOKEN = "token"
    private const val KEY_PENDING_PLAYER = "pending_removal_player"
    private const val KEY_PENDING_TOKEN = "pending_removal_token"
    private const val EXTRA_TRACKING_ID = "tracking_event_id"

    private var config: GammaEngageConfig? = null
    private var api: ApiClient? = null
    private var prefs: SharedPreferences? = null
    private var inFlightToken: String? = null
    private val reportedOpens: MutableSet<String> = Collections.synchronizedSet(HashSet())

    /** Call once from `Application.onCreate`. Repeat calls are ignored. */
    @Synchronized
    @JvmStatic
    fun init(context: Context, config: GammaEngageConfig) {
        if (this.config != null) return
        val app = context.applicationContext as Application
        this.config = config
        api = ApiClient(config)
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = handleIntent(activity.intent)
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        flushPendingRemoval()
        if (prefs?.getString(KEY_PLAYER, null) != null) syncToken()
    }

    /**
     * Ties this device to a player and registers its FCM token for that player. Call after login.
     * Switching to a different player unregisters the device from the previous one first.
     */
    @JvmStatic
    fun setPlayer(playerId: String) {
        val p = prefs ?: return notInitialised()
        val previous = p.getString(KEY_PLAYER, null)
        if (previous != null && previous != playerId) logout()
        p.edit().putString(KEY_PLAYER, playerId).apply()
        syncToken()
    }

    /**
     * Manually register a token you obtained yourself. Needs [setPlayer] first. Only needed when
     * you can't use the SDK's own token fetching.
     */
    @JvmStatic
    fun registerPushToken(token: String) = register(token)

    /**
     * Forward tokens here from your own `FirebaseMessagingService.onNewToken`. Not needed if your
     * app has no such service — the SDK's own service handles it.
     */
    @JvmStatic
    fun onNewToken(token: String) {
        if (config == null) {
            Log.w(TAG, "onNewToken before init() — ignored")
            return
        }
        register(token)
    }

    /** Unregisters this device from the current player so the next user doesn't receive their pushes. */
    @JvmStatic
    fun logout() {
        val p = prefs ?: return notInitialised()
        val player = p.getString(KEY_PLAYER, null) ?: return
        val token = p.getString(KEY_TOKEN, null)
        p.edit().remove(KEY_PLAYER).remove(KEY_TOKEN).apply()
        if (token == null) return
        p.edit().putString(KEY_PENDING_PLAYER, player).putString(KEY_PENDING_TOKEN, token).apply()
        removeToken(player, token) { ok -> if (ok) clearPendingRemoval() }
    }

    /**
     * Reports a notification tap if [intent] carries a GammaEngage tracking id. The SDK already
     * checks each Activity's launch intent; call this from `onNewIntent` too, since Android
     * delivers taps on an already-running Activity there.
     */
    @JvmStatic
    fun handleIntent(intent: Intent?) {
        val id = intent?.getStringExtra(EXTRA_TRACKING_ID) ?: return
        if (!reportedOpens.add(id)) return
        api?.reportOpen(id)
    }

    /** Android 13+ requires a runtime prompt before notifications can be shown. No-op below that. */
    @JvmOverloads
    @JvmStatic
    fun requestNotificationPermission(activity: Activity, requestCode: Int = 4242) {
        if (Build.VERSION.SDK_INT >= 33 &&
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), requestCode)
        }
    }

    private fun syncToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) register(task.result) else Log.w(TAG, "could not fetch FCM token", task.exception)
        }
    }

    private fun register(token: String) {
        val p = prefs ?: return notInitialised()
        val player = p.getString(KEY_PLAYER, null)
        if (player == null) {
            Log.w(TAG, "call setPlayer() before registering a token")
            return
        }
        synchronized(this) {
            if (inFlightToken == token) return
            inFlightToken = token
        }
        val previous = p.getString(KEY_TOKEN, null)
        if (previous != null && previous != token) removeToken(player, previous)
        api?.sendEvent(
            "push_token_registered",
            player,
            listOf("device_token" to token, "platform" to "android"),
        ) { ok ->
            synchronized(this) { if (inFlightToken == token) inFlightToken = null }
            if (ok) p.edit().putString(KEY_TOKEN, token).apply()
        }
    }

    private fun removeToken(player: String, token: String, onDone: ((Boolean) -> Unit)? = null) {
        api?.sendEvent("push_token_removed", player, listOf("device_token" to token), onDone)
    }

    private fun flushPendingRemoval() {
        val p = prefs ?: return
        val player = p.getString(KEY_PENDING_PLAYER, null) ?: return
        val token = p.getString(KEY_PENDING_TOKEN, null) ?: return
        removeToken(player, token) { ok -> if (ok) clearPendingRemoval() }
    }

    private fun clearPendingRemoval() {
        prefs?.edit()?.remove(KEY_PENDING_PLAYER)?.remove(KEY_PENDING_TOKEN)?.apply()
    }

    private fun notInitialised() {
        Log.w(TAG, "GammaEngage.init() must be called first (Application.onCreate)")
    }
}
