# StreamForge Android SDK

Android SDK for StreamForge live streaming platform. Provides a seamless, one-call API to embed live stream players in your Android app.

## Features

- One-call player setup — init, load, and auto-play in a single API call
- HLS/DASH playback via ExoPlayer (Media3)
- Adaptive bitrate (ABR) with configurable min/max
- YouTube-style tap overlay (back, title, quality, PiP)
- Picture-in-Picture (Android 8.0+)
- Chromecast support (optional module)
- Stream status polling (live/offline)
- Auto-retry with exponential backoff
- Kotlin Flow & callback event system
- ProGuard/R8 ready

## Requirements

| Requirement | Version |
|-------------|---------|
| Android SDK | 23+ (Android 6.0) |
| Kotlin | 1.9+ |
| Java | 17 |
| AGP | 8.2+ |

## Installation

Add JitPack to `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add dependencies to `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.shoelfikar.streamforge-android-sdk:sdk:v0.1.0")

    // Optional — Chromecast
    implementation("com.github.shoelfikar.streamforge-android-sdk:sdk-cast:v0.1.0")
}
```

## Quick Start

```kotlin
StreamForge.createPlayer(
    context = this,
    apiKey = "sf_live_YOUR_API_KEY",
    streamId = "YOUR_STREAM_UUID",
    playerSetupListener = object : StreamForge.PlayerSetupListener {
        override fun onPlayerSetupSuccess(player: StreamForgePlayerView) {
            container.addView(player)

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
            Log.e("StreamForge", "Setup failed", error)
        }
    }
)
```

## Modules

| Module | Artifact | Description |
|--------|----------|-------------|
| `sdk` | `com.streamforge:sdk` | Core SDK — player, events, API client |
| `sdk-cast` | `com.streamforge:sdk-cast` | Chromecast support (optional) |

## Documentation

- [Integration Guide](INTEGRATION_GUIDE.md) — full setup, configuration, controls, events, Chromecast, PiP
- [Changelog](CHANGELOG.md) — release history

## Project Structure

```
streamforge-android-sdk/
├── sdk/              Core SDK library
├── sdk-cast/         Chromecast module
├── sample/           Sample app
├── .github/
│   └── workflows/    CI/CD pipelines
├── jitpack.yml       JitPack config
└── gradle.properties Versioning & POM metadata
```

## License

Apache License 2.0 — see [LICENSE](LICENSE) for details.
