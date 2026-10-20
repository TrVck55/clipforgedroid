# ClipForge ProGuard / R8 rules

# Keep Compose runtime
-keep class androidx.compose.** { *; }

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# FFmpeg Kit
-keep class com.arthenica.ffmpegkit.** { *; }
-dontwarn com.arthenica.ffmpegkit.**

# youtubedl-android
-keep class com.yausername.** { *; }
-dontwarn com.yausername.**

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep data classes used with serialization / reflection if any
-keepclassmembers class dev.clipforge.app.model.** { *; }
