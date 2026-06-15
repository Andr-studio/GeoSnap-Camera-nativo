package com.andrives.geosnap_cam.ui.screen.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.andrives.geosnap_cam.service.PermissionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val permissionManager: PermissionManager,
) : ViewModel() {

    fun shouldNavigateToCamera(): Boolean {
        return permissionManager.isOnboardingComplete() && permissionManager.hasAllPermissions()
    }
}

@Composable
fun SplashScreen(
    onNavigateToCamera: () -> Unit,
    onNavigateToPermission: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(250),
        label = "splash_fade",
    )

    LaunchedEffect(Unit) {
        visible = true
        delay(300)

        if (viewModel.shouldNavigateToCamera()) {
            onNavigateToCamera()
        } else {
            onNavigateToPermission()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .alpha(alpha),
    )
}
