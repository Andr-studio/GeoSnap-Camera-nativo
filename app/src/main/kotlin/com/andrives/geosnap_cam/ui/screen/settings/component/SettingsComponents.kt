package com.andrives.geosnap_cam.ui.screen.settings.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Section wrapper (glass card) ─────────────────────────────────────────────

@Composable
fun SettingsSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier) {
        Text(
            text = title.uppercase(),
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp)),
        ) {
            content()
        }
    }
}

// ── Divider ──────────────────────────────────────────────────────────────────

@Composable
fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        thickness = 0.5.dp,
        color = Color.White.copy(alpha = 0.1f),
    )
}

// ── Standard tile with icon + title + trailing ────────────────────────────────

@Composable
fun SettingTile(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
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

        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp)
            if (subtitle != null) {
                Text(subtitle, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, lineHeight = 16.sp)
            }
        }

        trailing?.invoke()
    }
}

// ── Slider tile ───────────────────────────────────────────────────────────────

@Composable
fun SliderSettingTile(
    icon: ImageVector,
    title: String,
    valueLabel: String,
    value: Float,
    min: Float,
    max: Float,
    steps: Int = 0,
    onChanged: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
            Text(valueLabel, color = Color(0xFF007AFF), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }

        Slider(
            value = value,
            onValueChange = onChanged,
            valueRange = min..max,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF007AFF),
                activeTrackColor = Color(0xFF007AFF),
                inactiveTrackColor = Color.White.copy(alpha = 0.15f),
            ),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

// ── Toggle tile ───────────────────────────────────────────────────────────────

@Composable
fun ToggleSettingTile(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingTile(
        icon = icon,
        title = title,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF34C759),
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color.White.copy(alpha = 0.3f),
                ),
            )
        }
    )
}

// ── Color picker tile ─────────────────────────────────────────────────────────

@Composable
fun ColorPickerTile(
    icon: ImageVector,
    title: String,
    selectedArgb: Int,
    onSelected: (Int) -> Unit,
    colors: List<Int>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
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

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            colors.forEach { argb ->
                val isSelected = argb == selectedArgb
                val composeColor = if (argb == 0) Color.Transparent else Color(argb)

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            if (argb == 0) Color.Transparent else composeColor
                        )
                        .then(
                            if (argb == 0) Modifier.border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                            else Modifier
                        )
                        .then(
                            if (isSelected) Modifier.border(3.dp, Color(0xFF007AFF), CircleShape)
                            else Modifier
                        )
                        .clickable { onSelected(argb) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (argb == 0) {
                        // Multi-color option
                        Text("G", color = Color(0xFF4285F4), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ── Segmented control ─────────────────────────────────────────────────────────

@Composable
fun <T> SegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) Color(0xFF007AFF) else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}
