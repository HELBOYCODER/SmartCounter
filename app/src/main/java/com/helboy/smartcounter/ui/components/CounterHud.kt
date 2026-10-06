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
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App header pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceGlass)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isPaused) AmberAccent else EmeraldCyber)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isBatchMode) "حالت شمارش سینی و بسته" else "شمارش خط نوار نقاله",
                    fontSize = 12.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Big Digital Display Card
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceGlass)
                .padding(horizontal = 32.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$totalCount",
                fontSize = 68.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = if (isBatchMode) CyanNeon else AmberAccent,
                letterSpacing = 2.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Badges row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            HudBadge(label = "در کادر", value = "$inFrameCount", color = CyanNeon)
            Spacer(modifier = Modifier.width(6.dp))
            HudBadge(label = "↑ رو به جلو", value = "$countForward", color = EmeraldCyber)
            Spacer(modifier = Modifier.width(6.dp))
            HudBadge(label = "↓ برگشت", value = "$countBackward", color = AmberAccent)
            Spacer(modifier = Modifier.width(6.dp))
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
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, fontSize = 10.sp, color = TextSecondary)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = value,
                fontSize = 11.sp,
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
    onTogglePause: () -> Unit,
    onReset: () -> Unit,
    onToggleTorch: () -> Unit,
    onFlipCamera: () -> Unit,
    onToggleOrientation: () -> Unit,
    onToggleMode: () -> Unit,
    onExportCsv: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Mode toggle pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceGlass)
                .clickable { onToggleMode() }
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Cached,
                    contentDescription = null,
                    tint = EmeraldCyber,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isBatchMode) "تغییر به حالت نوار نقاله ➔" else "تغییر به حالت سینی و بسته ➔",
                    fontSize = 12.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Control Dock
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(26.dp))
                .background(SurfaceGlass)
                .padding(horizontal = 14.dp, vertical = 10.dp)
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
                    icon = Icons.Default.Cameraswitch,
                    label = "دوربین",
                    onClick = onFlipCamera,
                    tint = TextPrimary
                )

                HudIconButton(
                    icon = Icons.Default.SwapVert,
                    label = if (isVertical) "افقی" else "عمودی",
                    onClick = onToggleOrientation,
                    tint = CyanNeon
                )

                HudIconButton(
                    icon = Icons.Default.Share,
                    label = "گزارش",
                    onClick = onExportCsv,
                    tint = EmeraldCyber
                )
            }
        }
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
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(SurfaceCard),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, fontSize = 10.sp, color = TextSecondary)
    }
}
