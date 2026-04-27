# ════════════════════════════════════════════════════════════════
# StreamForge SDK Cast — Consumer ProGuard / R8 Rules
# ════════════════════════════════════════════════════════════════

# ── Public API ─────────────────────────────────────────────────
-keep class com.streamforge.sdk.cast.StreamForgeCast { *; }
-keep class com.streamforge.sdk.cast.CastOptionsProvider { *; }

# ── Google Cast SDK ────────────────────────────────────────────
-keep class com.google.android.gms.cast.** { *; }
-keep class com.google.android.gms.cast.framework.** { *; }
-dontwarn com.google.android.gms.**

# ── MediaRouter ────────────────────────────────────────────────
-keep class androidx.mediarouter.** { *; }
