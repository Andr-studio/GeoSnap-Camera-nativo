# ──────────────────────────────────────────────────────────────────────────────
# ProGuard / R8 rules for GeoSnap Cam (Native Android)
# ──────────────────────────────────────────────────────────────────────────────

# ── Firebase ──────────────────────────────────────────────────────────────────
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Firebase Crashlytics
-keep class com.google.firebase.crashlytics.** { *; }
-dontwarn com.google.firebase.crashlytics.**

# Firebase Performance
-keep class com.google.firebase.perf.** { *; }
-dontwarn com.google.firebase.perf.**

# ── FFmpeg Kit ────────────────────────────────────────────────────────────────
-keep class com.arthenica.ffmpegkit.** { *; }
-dontwarn com.arthenica.ffmpegkit.**

# ── OkHttp ────────────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# ── Native / JNI ──────────────────────────────────────────────────────────────
-keep class com.andrives.geosnap_cam.media.GeoSnapVideoMuxer { *; }
-keepclassmembers class com.andrives.geosnap_cam.media.GeoSnapVideoMuxer {
    native <methods>;
}

# ── Hilt / Dagger ─────────────────────────────────────────────────────────────
-keep class dagger.** { *; }
-dontwarn dagger.**

# ── CameraX ───────────────────────────────────────────────────────────────────
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ── ExoPlayer / Media3 ────────────────────────────────────────────────────────
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ── Compose ───────────────────────────────────────────────────────────────────
-dontwarn androidx.compose.**

# ── Google Play Services ──────────────────────────────────────────────────────
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# ── General Android ──────────────────────────────────────────────────────────
-keep class * extends android.app.Activity
-keep class * extends android.app.Application
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver
