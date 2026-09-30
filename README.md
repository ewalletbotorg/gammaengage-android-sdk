# GammaEngage Android SDK

Registers a device for GammaEngage mobile push and reports notification opens. The SDK fetches the FCM token itself, re-registers it when Firebase rotates it, and unregisters it on logout.

Requires Android 8.0+ (API 26).

## What you set up (Firebase — required by Google, not by this SDK)

1. Create a Firebase project and register your Android app in it.
2. Add its `google-services.json` to your app and apply the `com.google.gms.google-services` plugin.
3. In GammaEngage, upload that Firebase project's **service account JSON** under Settings → Channels → Push for your brand.

## Install

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories { maven("https://jitpack.io") }
}

// app/build.gradle.kts
dependencies {
    implementation("com.github.ewalletbotorg:gammaengage-android-sdk:0.1.0")
}
```

## Use

```kotlin
// Application.onCreate
GammaEngage.init(
    this,
    GammaEngageConfig(
        baseUrl = "https://your-gammaengage-host",
        brandId = "your-brand-id",
        apiKey = "your-api-key",
        hmacSecret = "your-hmac-secret", // optional; signs every request when set
    ),
)

// Activity: ask for the Android 13+ notification permission
GammaEngage.requestNotificationPermission(this)

// After the player logs in
GammaEngage.setPlayer("player-123")

// On logout — stops the previous player's pushes reaching this device
GammaEngage.logout()
```

### Notification taps

The SDK checks each Activity's launch intent for a tap. Android delivers taps on an already-running Activity to `onNewIntent`, so forward it there too:

```kotlin
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    GammaEngage.handleIntent(intent)
}
```

### If your app has its own `FirebaseMessagingService`

Yours keeps receiving everything (the SDK's service has a lower priority). Just forward new tokens:

```kotlin
override fun onNewToken(token: String) {
    GammaEngage.onNewToken(token)
}
```

### Passing a token yourself

`GammaEngage.registerPushToken(token)` registers a token you fetched yourself. Call `setPlayer` first.

## Behaviour to know about

- Registration is retried on the next app launch if a request fails. There is no retry loop inside a session.
- If unregistering on logout fails (offline), it is retried on the next launch, so a previous player's token is not left behind.
- The HMAC secret ships inside your app, so treat it as identifying the app, not as a private credential.
