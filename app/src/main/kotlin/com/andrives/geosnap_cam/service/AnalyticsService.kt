package com.andrives.geosnap_cam.service

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.ktx.Firebase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AnalyticsService — Direct Firebase SDK calls without Flutter wrappers.
 *
 * Handles:
 * - Firebase Analytics (event logging)
 * - Firebase Crashlytics (error reporting)
 */
@Singleton
class AnalyticsService @Inject constructor() {

    private val analytics: FirebaseAnalytics = Firebase.analytics
    private val crashlytics: FirebaseCrashlytics = FirebaseCrashlytics.getInstance()

    fun logEvent(name: String, params: Map<String, Any?> = emptyMap()) {
        val bundle = Bundle().apply {
            params.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Double -> putDouble(key, value)
                    is Boolean -> putBoolean(key, value)
                }
            }
        }
        analytics.logEvent(name, bundle)
    }

    fun logPhotoCapture() {
        logEvent("photo_capture")
    }

    fun logVideoRecordingStart() {
        logEvent("video_recording_start")
    }

    fun logVideoRecordingStop(durationMs: Long) {
        logEvent("video_recording_stop", mapOf("duration_ms" to durationMs))
    }

    fun logWatermarkSettingsChange(setting: String, value: String) {
        logEvent("watermark_settings_change", mapOf("setting" to setting, "value" to value))
    }

    fun logError(message: String, throwable: Throwable? = null) {
        crashlytics.log(message)
        throwable?.let { crashlytics.recordException(it) }
    }

    fun setUserId(userId: String) {
        analytics.setUserId(userId)
        crashlytics.setUserId(userId)
    }
}
