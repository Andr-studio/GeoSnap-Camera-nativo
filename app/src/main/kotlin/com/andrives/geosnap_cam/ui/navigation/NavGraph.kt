package com.andrives.geosnap_cam.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.andrives.geosnap_cam.ui.screen.camera.CameraScreen
import com.andrives.geosnap_cam.ui.screen.permission.PermissionScreen
import com.andrives.geosnap_cam.ui.screen.splash.SplashScreen

object NavRoutes {
    const val SPLASH = "splash"
    const val PERMISSION = "permission"
    const val CAMERA = "camera"
    const val PREVIEW = "preview/{mediaPath}/{isVideo}"
    const val SETTINGS = "settings"

    fun preview(mediaPath: String, isVideo: Boolean): String {
        val encodedPath = java.net.URLEncoder.encode(mediaPath, "UTF-8")
        return "preview/$encodedPath/$isVideo"
    }
}

@Composable
fun GeoSnapNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = NavRoutes.SPLASH,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
    ) {
        composable(NavRoutes.SPLASH) {
            SplashScreen(
                onNavigateToCamera = {
                    navController.navigate(NavRoutes.CAMERA) {
                        popUpTo(NavRoutes.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToPermission = {
                    navController.navigate(NavRoutes.PERMISSION) {
                        popUpTo(NavRoutes.SPLASH) { inclusive = true }
                    }
                },
            )
        }

        composable(NavRoutes.PERMISSION) {
            PermissionScreen(
                onPermissionsGranted = {
                    navController.navigate(NavRoutes.CAMERA) {
                        popUpTo(NavRoutes.PERMISSION) { inclusive = true }
                    }
                },
            )
        }

        composable(NavRoutes.CAMERA) {
            CameraScreen(
                onNavigateToPreview = { path, isVideo, sessionPaths, sessionIsVideo ->
                    // Encode path for URL-safe navigation
                    navController.navigate(NavRoutes.preview(path, isVideo))
                    // Store session in back stack entry for preview
                    navController.currentBackStackEntry?.savedStateHandle?.apply {
                        set("sessionPaths", sessionPaths)
                        set("sessionIsVideo", sessionIsVideo)
                    }
                },
                onNavigateToSettings = {
                    navController.navigate(NavRoutes.SETTINGS)
                },
            )
        }

        composable(
            route = NavRoutes.PREVIEW,
            arguments = listOf(
                navArgument("mediaPath") { type = NavType.StringType },
                navArgument("isVideo") { type = NavType.BoolType },
            ),
        ) { backStackEntry ->
            val rawPath = backStackEntry.arguments?.getString("mediaPath") ?: return@composable
            val mediaPath = java.net.URLDecoder.decode(rawPath, "UTF-8")
            val isVideo = backStackEntry.arguments?.getBoolean("isVideo") ?: false

            // Retrieve session from back stack entry (camera puts session in saved state)
            val previousEntry = navController.previousBackStackEntry
            val sessionPaths = previousEntry?.savedStateHandle
                ?.get<List<String>>("sessionPaths") ?: listOf(mediaPath)
            val sessionIsVideo = previousEntry?.savedStateHandle
                ?.get<List<Boolean>>("sessionIsVideo") ?: listOf(isVideo)

            com.andrives.geosnap_cam.ui.screen.preview.MediaPreviewScreen(
                initialPath = mediaPath,
                initialIsVideo = isVideo,
                sessionPaths = sessionPaths,
                sessionIsVideo = sessionIsVideo,
                onBack = { navController.popBackStack() },
            )
        }

        composable(NavRoutes.SETTINGS) {
            val previousEntry = navController.previousBackStackEntry
            val locationLat = previousEntry?.savedStateHandle?.get<Double>("locationLat")
            val locationLon = previousEntry?.savedStateHandle?.get<Double>("locationLon")

            com.andrives.geosnap_cam.ui.screen.settings.WatermarkSettingsScreen(
                currentLocation = null, // Location passed via ViewModel from CameraScreen
                onBack = { navController.popBackStack() },
            )
        }
    }
}
