# StreamForge Android SDK — Integration Guide

## Requirements

- Android SDK 23+ (Android 6.0 Marshmallow)
- Java 17 / Kotlin 1.9+
- Android Gradle Plugin 8.2+

## 1. Add Dependency

### Via JitPack

Add JitPack repository to your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add the SDK dependency to your `app/build.gradle.kts`:

```kotlin
dependencies {
    // Core SDK (required)
    implementation("com.github.streamforge.android-sdk:sdk:0.1.0")

    // Chromecast support (optional)
    implementation("com.github.streamforge.android-sdk:sdk-cast:0.1.0")
}
```

## 2. Quick Start — One-Call API

The simplest way to integrate the player:

```kotlin
class PlayerActivity : AppCompatActivity() {

    private var playerView: StreamForgePlayerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = FrameLayout(this)
        setContentView(container)

        StreamForge.createPlayer(
            context = this,
            apiKey = "sf_live_YOUR_API_KEY",
            streamId = "YOUR_STREAM_UUID",
            playerSetupListener = object : StreamForge.PlayerSetupListener {
                override fun onPlayerSetupSuccess(player: StreamForgePlayerView) {
                    playerView = player
                    container.addView(player)

                    // Wire up controls (optional)
                    val sfPlayer = player.player ?: return

                    player.setOnBackClickListener { finish() }

                    player.setOnQualityClickListener {
                        val qualities = sfPlayer.getAvailableQualities()
                        if (qualities.isNotEmpty()) {
                            player.showQualitySelector(qualities, QualityOption.AUTO) { selected ->
                                if (selected.isAuto) sfPlayer.setAutoQuality()
                                else sfPlayer.setQuality(selected)
                            }
                        }
                    }

                    if (sfPlayer.isPipSupported()) {
                        player.setPipButtonVisible(true)
                        player.setOnPipClickListener { sfPlayer.enterPip(this@PlayerActivity) }
                    }
                }

                override fun onPlayerSetupFailed(error: StreamForgeException) {
                    Log.e("StreamForge", "Player setup failed", error)
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        playerView?.player?.release()
    }
}
```

## 3. Configuration Options

```kotlin
StreamForgeConfig.builder()
    .enableLogging(true)                    // HTTP logging (dev only)
    .trustAllCertificates(true)             // Skip SSL validation (dev only)
    .minBitrateKbps(500)                    // Min adaptive bitrate
    .maxBitrateKbps(8000)                   // Max adaptive bitrate
    .maxVideoWidth(1920)                    // Max video width
    .maxVideoHeight(1080)                   // Max video height
    .bufferForPlaybackMs(2500)              // Buffer before playback
    .bufferForPlaybackAfterRebufferMs(5000) // Buffer after rebuffer
    .build()
```

## 4. Advanced Usage — Manual Init

For more control, use the two-step API:

```kotlin
// Step 1: Initialize
val tenantConfig = StreamForge.init(
    context = applicationContext,
    apiKey = "sf_live_...",
    streamId = "stream-uuid",
    config = StreamForgeConfig.builder().build()
)

// Step 2: Create player
val player = StreamForge.createPlayer(context)
val playerView = StreamForgePlayerView(context)
player.attachView(playerView)
container.addView(playerView)

// Step 3: Load and play
player.load(object : PlayerEventListener {
    override fun onPlaybackStateChanged(state: PlaybackState) {
        if (state == PlaybackState.READY) player.play()
    }
    override fun onPlayerError(error: Exception) {
        Log.e("StreamForge", "Playback error", error)
    }
})
```

## 5. Player Controls

```kotlin
val player = playerView.player!!

// Playback
player.play()
player.pause()
player.setVolume(0.5f)
player.setMuted(true)

// Quality
val qualities = player.getAvailableQualities()
player.setQuality(qualities[0])
player.setAutoQuality()

// Picture-in-Picture
if (player.isPipSupported()) {
    player.enterPip(activity)
}

// State
player.isPlaying         // Boolean
player.playbackState     // PlaybackState enum
player.currentPosition   // Long (ms)
player.duration          // Long (ms)
```

## 6. Event Listening

### Via Callback

```kotlin
player.setEventListener(object : PlayerEventListener {
    override fun onPlaybackStateChanged(state: PlaybackState) { }
    override fun onPlayerReady() { }
    override fun onPlayerError(error: Exception) { }
    override fun onVideoSizeChanged(width: Int, height: Int) { }
    override fun onStreamStatusChanged(isLive: Boolean) { }
    override fun onQualityChanged(width: Int, height: Int, bitrate: Int) { }
    override fun onPipChanged(isInPip: Boolean) { }
})
```

### Via Kotlin Flow

```kotlin
lifecycleScope.launch {
    player.observeEvents().collect { event ->
        when (event) {
            is StreamForgeEvent.PlaybackStateChanged -> { }
            is StreamForgeEvent.PlayerError -> { }
            is StreamForgeEvent.StreamStatusChanged -> { }
            is StreamForgeEvent.QualityChanged -> { }
            is StreamForgeEvent.PipChanged -> { }
            else -> { }
        }
    }
}
```

## 7. Chromecast (Optional)

Add the `sdk-cast` dependency, then:

```kotlin
// In Activity.onCreate()
try {
    StreamForgeCast.initialize(this)
} catch (e: Exception) {
    // Cast SDK not available — non-fatal
}

// Start casting
StreamForgeCast.startCasting(streamUrl, title = "My Stream")

// Listen for cast state
StreamForgeCast.setOnCastStateChangedListener { isCasting ->
    Log.d("Cast", "Casting: $isCasting")
}

// Cleanup in onDestroy()
StreamForgeCast.release()
```

## 8. Picture-in-Picture

Handle PiP lifecycle in your Activity:

```kotlin
override fun onPictureInPictureModeChanged(isInPip: Boolean, newConfig: Configuration) {
    super.onPictureInPictureModeChanged(isInPip, newConfig)
    playerView?.player?.onPictureInPictureModeChanged(isInPip)
}

override fun onUserLeaveHint() {
    super.onUserLeaveHint()
    val p = playerView?.player ?: return
    if (p.isPipSupported() && p.isPlaying) {
        p.enterPip(this)
    }
}
```

## 9. UI Overlay

The player view provides a YouTube-like tap-to-show overlay:

```kotlin
// Show/hide title
playerView.showTitle = true  // or false

// Set title text
playerView.setTitle("My Stream Title")

// Back button
playerView.setOnBackClickListener { finish() }

// Quality button (always visible in control bar)
playerView.setOnQualityClickListener { /* show quality picker */ }

// PiP button (hidden by default)
playerView.setPipButtonVisible(true)
playerView.setOnPipClickListener { /* enter PiP */ }
```

The overlay auto-hides after 4 seconds.

## 10. ProGuard / R8

No additional ProGuard configuration is needed. The SDK includes consumer rules
that are automatically applied when your app is minified.

## 11. Error Handling

The SDK uses typed exceptions:

| Exception | When |
|-----------|------|
| `SFAuthException` | Invalid or expired API key (401/403) |
| `SFNetworkException` | No internet, timeout |
| `SFStreamNotFoundException` | Stream UUID not found (404) |
| `SFNotInitializedException` | SDK used before `init()` |
| `SFApiException` | Other API errors |
| `SFInsufficientScopeException` | API key missing required scope |

All extend `StreamForgeException` which extends `Exception`.
