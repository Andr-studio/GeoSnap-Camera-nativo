package com.andrives.geosnap_cam.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.andrives.geosnap_cam.util.InAppUpdateManager
import com.andrives.geosnap_cam.util.UpdateState

@Composable
fun UpdateBanner(
    inAppUpdateManager: InAppUpdateManager,
    onStartUpdate: () -> Unit,
    onCompleteUpdate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val updateState by inAppUpdateManager.updateState.collectAsState()

    val isVisible = updateState == UpdateState.UPDATE_AVAILABLE ||
                    updateState == UpdateState.DOWNLOADING ||
                    updateState == UpdateState.DOWNLOADED

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF2C2C2E).copy(alpha = 0.9f))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (updateState == UpdateState.DOWNLOADED) Icons.Default.SystemUpdate else Icons.Default.Info,
                        contentDescription = "Update Icon",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = when (updateState) {
                            UpdateState.UPDATE_AVAILABLE -> "Nueva actualización disponible"
                            UpdateState.DOWNLOADING -> "Descargando actualización..."
                            UpdateState.DOWNLOADED -> "Actualización lista para instalar"
                            else -> ""
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (updateState == UpdateState.UPDATE_AVAILABLE) {
                    TextButton(
                        onClick = onStartUpdate,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF0A84FF))
                    ) {
                        Text("Actualizar", fontWeight = FontWeight.Bold)
                    }
                } else if (updateState == UpdateState.DOWNLOADED) {
                    TextButton(
                        onClick = onCompleteUpdate,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF34C759))
                    ) {
                        Text("Reiniciar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
