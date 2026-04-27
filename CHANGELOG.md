# Changelog

## [0.1.0] — 2026-04-26

### Added
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
