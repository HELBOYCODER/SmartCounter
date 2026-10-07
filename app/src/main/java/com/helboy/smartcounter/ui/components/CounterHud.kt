package com.helboy.smartcounter.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.smartcounter.engine.OnnxYoloAnalyzer
import com.helboy.smartcounter.engine.UnifiedVisionAnalyzer
import com.helboy.smartcounter.ui.theme.AmberAccent
import com.helboy.smartcounter.ui.theme.BlackObsidian
import com.helboy.smartcounter.ui.theme.CyanNeon
import com.helboy.smartcounter.ui.theme.EmeraldCyber
import com.helboy.smartcounter.ui.theme.SurfaceCard
import com.helboy.smartcounter.ui.theme.SurfaceGlass
import com.helboy.smartcounter.ui.theme.TextMuted
import com.helboy.smartcounter.ui.theme.TextPrimary
import com.helboy.smartcounter.ui.theme.TextSecondary

@Composable
fun CounterHudTop(
    totalCount: Int,
    inFrameCount: Int,
    countForward: Int,
    countBackward: Int,
    fps: Float,
    isBatchMode: Boolean,
    isPaused: Boolean,
    activeEngine: String,
    preset: OnnxYoloAnalyzer.DetectionPreset,
    sensitivityLabel: String,
    onCycleSensitivity: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App header pills row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Preset & Mode badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceGlass)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isPaused) AmberAccent else EmeraldCyber)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val presetName = when (preset) {
                        OnnxYoloAnalyzer.DetectionPreset.FOOD_CONTAINER -> "🍱 ظروف غذا"
                        OnnxYoloAnalyzer.DetectionPreset.SPOOL_CIRCULAR -> "🧵 قرقره"
                        OnnxYoloAnalyzer.DetectionPreset.ALL_OBJECTS -> "📦 کالاها"
                    }
                    val modeName = if (isBatchMode) "سینی/بسته" else "نوار نقاله"
                    Text(
                        text = "$presetName | $modeName",
                        fontSize = 11.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Sensitivity cycle pill (clickable)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceGlass)
                    .clickable { onCycleSensitivity() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = CyanNeon,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "حساسیت: $sensitivityLabel",
                        fontSize = 11.sp,
                        color = CyanNeon,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Big Digital Display Card
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(SurfaceGlass)
                .padding(horizontal = 30.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$totalCount",
                fontSize = 64.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = if (isBatchMode) CyanNeon else AmberAccent,
                letterSpacing = 2.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Badges row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            HudBadge(label = "در کادر", value = "$inFrameCount", color = CyanNeon)
            Spacer(modifier = Modifier.width(5.dp))
            if (!isBatchMode) {
                HudBadge(label = "↑ جلو", value = "$countForward", color = EmeraldCyber)
                Spacer(modifier = Modifier.width(5.dp))
                HudBadge(label = "↓ برگشت", value = "$countBackward", color = AmberAccent)
                Spacer(modifier = Modifier.width(5.dp))
            }
            HudBadge(label = "موتور", value = activeEngine, color = EmeraldCyber)
            Spacer(modifier = Modifier.width(5.dp))
            HudBadge(label = "FPS", value = "%.0f".format(fps), color = TextMuted)
        }
    }
}

@Composable
fun HudBadge(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceGlass)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, fontSize = 10.sp, color = TextSecondary)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = value,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun CounterHudBottom(
    isPaused: Boolean,
    isTorchOn: Boolean,
    isVertical: Boolean,
    isBatchMode: Boolean,
    preset: OnnxYoloAnalyzer.DetectionPreset,
    engineMode: UnifiedVisionAnalyzer.EngineMode,
    onTogglePause: () -> Unit,
    onReset: () -> Unit,
    onToggleTorch: () -> Unit,
    onFlipCamera: () -> Unit,
    onToggleOrientation: () -> Unit,
    onToggleMode: () -> Unit,
    onSelectPreset: (OnnxYoloAnalyzer.DetectionPreset) -> Unit,
    onCycleEngineMode: () -> Unit,
    onExportCsv: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Preset Switcher Row (Pills)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(SurfaceGlass)
                .padding(4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            PresetPill(
                title = "🍱 ظروف غذا",
                selected = preset == OnnxYoloAnalyzer.DetectionPreset.FOOD_CONTAINER,
                onClick = { onSelectPreset(OnnxYoloAnalyzer.DetectionPreset.FOOD_CONTAINER) }
            )
            Spacer(modifier = Modifier.width(4.dp))
            PresetPill(
                title = "🧵 قرقره",
                selected = preset == OnnxYoloAnalyzer.DetectionPreset.SPOOL_CIRCULAR,
                onClick = { onSelectPreset(OnnxYoloAnalyzer.DetectionPreset.SPOOL_CIRCULAR) }
            )
            Spacer(modifier = Modifier.width(4.dp))
            PresetPill(
                title = "📦 عمومی",
                selected = preset == OnnxYoloAnalyzer.DetectionPreset.ALL_OBJECTS,
                onClick = { onSelectPreset(OnnxYoloAnalyzer.DetectionPreset.ALL_OBJECTS) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Mode toggle pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceGlass)
                .clickable { onToggleMode() }
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Cached,
                    contentDescription = null,
                    tint = if (isBatchMode) CyanNeon else EmeraldCyber,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isBatchMode) "حالت سینی (کل کادر) ➔ کلیک برای نوار نقاله" else "حالت نوار نقاله ➔ کلیک برای سینی و کادر",
                    fontSize = 11.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Control Dock
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceGlass)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HudIconButton(
                    icon = Icons.Default.Refresh,
                    label = "صفر",
                    onClick = onReset,
                    tint = AmberAccent
                )

                HudIconButton(
                    icon = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    label = if (isPaused) "ادامه" else "توقف",
                    onClick = onTogglePause,
                    tint = if (isPaused) EmeraldCyber else TextPrimary
                )

                HudIconButton(
                    icon = Icons.Default.FlashOn,
                    label = "چراغ",
                    onClick = onToggleTorch,
                    tint = if (isTorchOn) AmberAccent else TextMuted
                )

                HudIconButton(
                    icon = Icons.Default.AutoAwesome,
                    label = when (engineMode) {
                        UnifiedVisionAnalyzer.EngineMode.YOLO_AI -> "YOLO"
                        UnifiedVisionAnalyzer.EngineMode.FAST_CV -> "سریع"
                        UnifiedVisionAnalyzer.EngineMode.HYBRID -> "هیبرید"
                    },
                    onClick = onCycleEngineMode,
                    tint = EmeraldCyber
                )

                HudIconButton(
                    icon = Icons.Default.SwapVert,
                    label = if (isVertical) "افقی" else "عمودی",
                    onClick = onToggleOrientation,
                    tint = CyanNeon
                )

                HudIconButton(
                    icon = Icons.Default.Cameraswitch,
                    label = "دوربین",
                    onClick = onFlipCamera,
                    tint = TextPrimary
                )

                HudIconButton(
                    icon = Icons.Default.Share,
                    label = "CSV",
                    onClick = onExportCsv,
                    tint = EmeraldCyber
                )
            }
        }
    }
}

@Composable
fun PresetPill(title: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) EmeraldCyber else SurfaceCard)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) BlackObsidian else TextSecondary
        )
    }
}

@Composable
fun HudIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(SurfaceCard),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(text = label, fontSize = 9.sp, color = TextSecondary)
    }
}
