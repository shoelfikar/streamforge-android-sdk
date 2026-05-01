# Changelog

## [0.1.0] — 2026-04-26

### Added
- Dual-orientation player UI: automatic portrait/landscape layout detection via `onConfigurationChanged()`
- Portrait mode: back button, LIVE badge, viewer count, info card (icon + title + subtitle), volume, quality selector, fullscreen
- Landscape mode: back + title/subtitle row, LIVE badge + viewers, volume, quality + PiP + fullscreen
- `StreamForgeErrorView` — built-in error UI with user-friendly messages per error type (auth, network, stream not found, permission denied)
- `PlayerSetupListener.onPlayerSetupFailed()` now provides a ready-to-use `StreamForgeErrorView` alongside the exception
- New `StreamForgePlayerView` APIs: `setSubtitle()`, `setViewerCount()`, `setLiveStatus()`, `setMuteState()`, `setCurrentQualityLabel()`
- New click listeners: `setOnVolumeClickListener()`, `setOnFullscreenClickListener()`
- `StreamForgePlayer.toggleMute()` and `isMuted` property
- Gradient overlays (top/bottom) for better control visibility over video
- Retry button on retryable errors (network timeout, connection issues)

### Changed
- `StreamForge.createPlayer()` now auto-sets `setLiveStatus(true)` on the player view
- Sample app updated to support both portrait and landscape orientations (`fullSensor`)

### Previously added
- Core SDK with `StreamForge.init()` and seamless `StreamForge.createPlayer()` API
- `StreamForgePlayerView` with tap-to-toggle overlay (title, quality, PiP, back button)
- ExoPlayer (Media3 1.3.1) integration for HLS/DASH playback
- Adaptive bitrate (ABR) configuration via `StreamForgeConfig`
- Quality selector with auto and manual quality options
- Picture-in-Picture (PiP) support for Android 8.0+
- Chromecast support via `sdk-cast` module
- Stream status polling (live/offline detection)
- Auto-retry with exponential backoff on playback errors
- Kotlin Flow-based event system (`StreamForgeEvent`)
- Callback-based event system (`PlayerEventListener`)
- Consumer ProGuard/R8 rules for safe minification
- KDoc documentation for all public APIs
- Unit tests for API client, models, config, and quality options
- JitPack publishing support
- GitHub Actions CI/CD pipeline
- Integration guide documentation
