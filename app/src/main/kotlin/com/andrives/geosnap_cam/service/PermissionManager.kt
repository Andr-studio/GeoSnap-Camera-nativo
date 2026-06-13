package com.andrives.geosnap_cam.service

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PermissionManager — handles runtime permission checks for Android 9 to 16.
 *
 * Equivalent of Flutter's PermissionService. Actual permission requesting
 * is done via Accompanist Permissions in Compose UI.
 */
@Singleton
class PermissionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: SharedPreferences,
) {
    companion object {
        private const val ONBOARDING_COMPLETE_KEY = "onboarding_complete"
    }

    /**
     * Returns the list of permissions that need to be requested based on SDK version.
     */
    fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )

        if (Build.VERSION.SDK_INT <= 28) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        return permissions
    }

    /**
     * Check if all mandatory permissions are granted.
     */
    fun hasAllPermissions(): Boolean {
        val cameraGranted = isGranted(Manifest.permission.CAMERA)
        val micGranted = isGranted(Manifest.permission.RECORD_AUDIO)
        val locationGranted = isGranted(Manifest.permission.ACCESS_FINE_LOCATION)

        val storageGranted = if (Build.VERSION.SDK_INT <= 28) {
            isGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            true
        }

        return cameraGranted && micGranted && locationGranted && storageGranted
    }

    fun isOnboardingComplete(): Boolean {
        return prefs.getBoolean(ONBOARDING_COMPLETE_KEY, false)
    }

    fun setOnboardingComplete() {
        prefs.edit().putBoolean(ONBOARDING_COMPLETE_KEY, true).apply()
    }

    private fun isGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }
}
