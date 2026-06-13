package com.andrives.geosnap_cam.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val GeoSnapColorScheme = darkColorScheme(
    primary = AppleBlue,
    onPrimary = Color.White,
    secondary = GpsGreen,
    onSecondary = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    error = Destructive,
    onError = Color.White,
    surfaceVariant = SettingsCardBg,
    onSurfaceVariant = Color.White.copy(alpha = 0.7f),
    outline = SettingsBorder,
)

@Composable
fun GeoSnapTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = GeoSnapColorScheme,
        typography = GeoSnapTypography,
        content = content,
    )
}
