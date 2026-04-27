# ════════════════════════════════════════════════════════════════
# StreamForge SDK — Internal ProGuard / R8 Rules
# Applied when the SDK module itself is minified.
# ════════════════════════════════════════════════════════════════

# Keep all model classes (used by Moshi for JSON serialization)
-keep class com.streamforge.sdk.model.** { *; }

# Keep Moshi-generated adapters
-keep class **JsonAdapter { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep @com.squareup.moshi.JsonClass class * { *; }

# Keep Retrofit interface methods
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Keep Kotlin metadata for reflection
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses,EnclosingMethod
