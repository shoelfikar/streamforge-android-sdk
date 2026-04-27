# ════════════════════════════════════════════════════════════════
# StreamForge SDK — Consumer ProGuard / R8 Rules
# These rules are automatically applied to apps that depend on the SDK.
# ════════════════════════════════════════════════════════════════

# ── Public API ──────────────────────────────────────────────────
-keep class com.streamforge.sdk.StreamForge { *; }
-keep interface com.streamforge.sdk.StreamForge$InitCallback { *; }
-keep interface com.streamforge.sdk.StreamForge$PlayerSetupListener { *; }
-keep class com.streamforge.sdk.StreamForgeConfig { *; }
-keep class com.streamforge.sdk.StreamForgeConfig$Builder { *; }
-keep class com.streamforge.sdk.StreamForgeConfig$Companion { *; }

# ── Player (public) ────────────────────────────────────────────
-keep class com.streamforge.sdk.player.StreamForgePlayer { *; }
-keep class com.streamforge.sdk.player.StreamForgePlayerView { *; }
-keep class com.streamforge.sdk.player.QualityOption { *; }
-keep class com.streamforge.sdk.player.QualityOption$Companion { *; }
-keep class com.streamforge.sdk.player.PlaybackState { *; }
-keep class com.streamforge.sdk.player.PlaybackProtocol { *; }
-keep interface com.streamforge.sdk.player.PlayerEventListener { *; }

# ── Events (public) ────────────────────────────────────────────
-keep class com.streamforge.sdk.event.StreamForgeEvent { *; }
-keep class com.streamforge.sdk.event.StreamForgeEvent$* { *; }

# ── Models (JSON deserialization) ──────────────────────────────
-keep class com.streamforge.sdk.model.** { *; }
-keepclassmembers class com.streamforge.sdk.model.** {
    <init>(...);
}

# ── Exceptions (public) ───────────────────────────────────────
-keep class com.streamforge.sdk.exception.** { *; }

# ── Moshi JSON adapters ───────────────────────────────────────
-keep class com.streamforge.sdk.model.*JsonAdapter { *; }
-keep class **JsonAdapter {
    <init>(...);
    **;
}
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
    @com.squareup.moshi.JsonClass <fields>;
}
-keep @com.squareup.moshi.JsonClass class * { *; }

# ── Retrofit ──────────────────────────────────────────────────
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keepattributes InnerClasses,EnclosingMethod
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# ── OkHttp ────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# ── Kotlin Coroutines ─────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ── ExoPlayer / Media3 ───────────────────────────────────────
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
