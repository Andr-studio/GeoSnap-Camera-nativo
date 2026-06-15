package com.andrives.geosnap_cam.ui.screen.settings.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ColorPickerTile(
    icon: ImageVector,
    title: String,
    selectedArgb: Int,
    onSelected: (Int) -> Unit,
    colors: List<Int>,
    allowCustomColor: Boolean = true,
) {
    var showCustomDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        ColorPickerHeader(icon, title)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (allowCustomColor) {
                CustomColorOption(
                    selected = selectedArgb !in colors,
                    onClick = { showCustomDialog = true },
                )
            }
            colors.forEach { argb ->
                PresetColorOption(
                    argb = argb,
                    selected = argb == selectedArgb,
                    onClick = { onSelected(argb) },
                )
            }
        }
    }

    if (showCustomDialog) {
        CustomColorDialog(
            initialArgb = selectedArgb.takeIf { it != 0 } ?: 0xFFFFFFFF.toInt(),
            onDismiss = { showCustomDialog = false },
            onSelected = {
                onSelected(it)
                showCustomDialog = false
            },
        )
    }
}

@Composable
private fun ColorPickerHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF007AFF).copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFF007AFF), modifier = Modifier.size(17.dp))
        }
        Text(title, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CustomColorOption(selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(2.dp, Color.White.copy(alpha = 0.45f), CircleShape)
            .then(if (selected) Modifier.border(3.dp, Color(0xFF007AFF), CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("+", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PresetColorOption(argb: Int, selected: Boolean, onClick: () -> Unit) {
    val composeColor = if (argb == 0) Color.Transparent else Color(argb)
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (argb == 0) Color.Transparent else composeColor)
            .then(if (argb == 0) Modifier.border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape) else Modifier)
            .then(if (selected) Modifier.border(3.dp, Color(0xFF007AFF), CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (argb == 0) {
            Text("G", color = Color(0xFF4285F4), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CustomColorDialog(
    initialArgb: Int,
    onDismiss: () -> Unit,
    onSelected: (Int) -> Unit,
) {
    var red by remember(initialArgb) { mutableIntStateOf((initialArgb shr 16) and 0xFF) }
    var green by remember(initialArgb) { mutableIntStateOf((initialArgb shr 8) and 0xFF) }
    var blue by remember(initialArgb) { mutableIntStateOf(initialArgb and 0xFF) }
    val selected = 0xFF000000.toInt() or (red shl 16) or (green shl 8) or blue

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF16181C),
        title = { Text("Color personalizado", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(selected)),
                )
                ColorChannelSlider("Rojo", red) { red = it }
                ColorChannelSlider("Verde", green) { green = it }
                ColorChannelSlider("Azul", blue) { blue = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelected(selected) }) {
                Text("Aplicar", color = Color(0xFF007AFF))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color.White.copy(alpha = 0.75f))
            }
        },
    )
}

@Composable
private fun ColorChannelSlider(label: String, value: Int, onChanged: (Int) -> Unit) {
    Column {
        Text("$label $value", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChanged(it.toInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF007AFF),
                activeTrackColor = Color(0xFF007AFF),
                inactiveTrackColor = Color.White.copy(alpha = 0.15f),
            ),
        )
    }
}
