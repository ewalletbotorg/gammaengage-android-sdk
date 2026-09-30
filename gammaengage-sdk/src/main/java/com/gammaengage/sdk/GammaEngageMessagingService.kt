package com.gammaengage.sdk

import com.google.firebase.messaging.FirebaseMessagingService

/** Re-registers the device whenever FCM rotates its token. See the manifest for why this is low-priority. */
class GammaEngageMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        GammaEngage.onNewToken(token)
    }
}
