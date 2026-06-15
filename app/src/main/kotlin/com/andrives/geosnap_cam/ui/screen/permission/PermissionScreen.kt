package com.andrives.geosnap_cam.ui.screen.permission

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.andrives.geosnap_cam.service.PermissionManager
import com.google.accompanist.permissions.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PermissionViewModel @Inject constructor(
    private val permissionManager: PermissionManager,
) : ViewModel() {
    val requiredPermissions: List<String> = permissionManager.getRequiredPermissions()

    fun markOnboardingComplete() {
        permissionManager.setOnboardingComplete()
    }
}

/**
 * Permission onboarding screen requesting Camera, Microphone, Location, and Storage.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionScreen(
    onPermissionsGranted: () -> Unit,
    viewModel: PermissionViewModel = hiltViewModel(),
) {
    val multiplePermissionsState = rememberMultiplePermissionsState(viewModel.requiredPermissions)

    LaunchedEffect(multiplePermissionsState.allPermissionsGranted) {
        if (multiplePermissionsState.allPermissionsGranted) {
            viewModel.markOnboardingComplete()
            onPermissionsGranted()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A0F1A), Color(0xFF0D1B2A))
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(scrollState)
                .padding(horizontal = 32.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // App icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF007AFF).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = Color(0xFF007AFF),
                    modifier = Modifier.size(40.dp),
                )
            }

            Text(
                text = "GeoSnap Cam necesita\npermisos para funcionar",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 30.sp,
            )

            Text(
                text = "Para grabar videos y fotos con ubicación GPS necesitamos acceso a la cámara, micrófono, ubicación y galería.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )

            // Permission items
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PermissionItem(Icons.Default.CameraAlt, "Cámara", "Para capturar fotos y video")
                PermissionItem(Icons.Default.Mic, "Micrófono", "Para grabar audio con el video")
                PermissionItem(Icons.Default.LocationOn, "Ubicación", "Para la marca de agua GPS")
                PermissionItem(Icons.Default.Photo, "Galería", "Para guardar tus capturas")
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { multiplePermissionsState.launchMultiplePermissionRequest() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
            ) {
                Text(
                    text = "Conceder permisos",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF007AFF).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFF007AFF), modifier = Modifier.size(20.dp))
        }
        Column {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        }
    }
}
