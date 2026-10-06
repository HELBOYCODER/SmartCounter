package com.helboy.smartcounter.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.helboy.smartcounter.engine.TrackedObject
import com.helboy.smartcounter.ui.theme.CrimsonGlow
import com.helboy.smartcounter.ui.theme.CrimsonLine
import com.helboy.smartcounter.ui.theme.CyanNeon
import com.helboy.smartcounter.ui.theme.EmeraldCyber

@Composable
fun CountingOverlay(
    tracks: List<TrackedObject>,
    linePositionRatio: Float,
    isVertical: Boolean,
    onLinePositionChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isVertical) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val newRatio = if (isVertical) {
                        (change.position.x / size.width.toFloat()).coerceIn(0.1f, 0.9f)
                    } else {
                        (change.position.y / size.height.toFloat()).coerceIn(0.1f, 0.9f)
                    }
                    onLinePositionChanged(newRatio)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // 1. Draw detected bounding boxes
            for (track in tracks) {
                val left = track.rect.left * canvasWidth
                val top = track.rect.top * canvasHeight
                val width = track.rect.width() * canvasWidth
                val height = track.rect.height() * canvasHeight

                val boxColor = if (track.crossed) CyanNeon else EmeraldCyber

                // Bounding rect
                drawRoundRect(
                    color = boxColor,
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(12f, 12f),
                    style = Stroke(width = 3.5f)
                )

                // Centroid circle
                val cx = track.centroidX * canvasWidth
                val cy = track.centroidY * canvasHeight
                drawCircle(
                    color = boxColor,
                    radius = 6f,
                    center = Offset(cx, cy)
                )
            }

            // 2. Draw Counting Laser Line
            if (!isVertical) {
                val lineY = linePositionRatio * canvasHeight

                // Laser Outer Glow
                drawLine(
                    color = CrimsonGlow,
                    start = Offset(0f, lineY),
                    end = Offset(canvasWidth, lineY),
                    strokeWidth = 14f
                )

                // Laser Core Line
                drawLine(
                    color = CrimsonLine,
                    start = Offset(0f, lineY),
                    end = Offset(canvasWidth, lineY),
                    strokeWidth = 4f
                )

                // Laser Sensor nodes on sides
                drawCircle(color = CrimsonLine, radius = 9f, center = Offset(14f, lineY))
                drawCircle(color = Color.White, radius = 4f, center = Offset(14f, lineY))
                drawCircle(color = CrimsonLine, radius = 9f, center = Offset(canvasWidth - 14f, lineY))
                drawCircle(color = Color.White, radius = 4f, center = Offset(canvasWidth - 14f, lineY))

            } else {
                val lineX = linePositionRatio * canvasWidth

                // Laser Outer Glow
                drawLine(
                    color = CrimsonGlow,
                    start = Offset(lineX, 0f),
                    end = Offset(lineX, canvasHeight),
                    strokeWidth = 14f
                )

                // Laser Core Line
                drawLine(
                    color = CrimsonLine,
                    start = Offset(lineX, 0f),
                    end = Offset(lineX, canvasHeight),
                    strokeWidth = 4f
                )

                // Laser Sensor nodes
                drawCircle(color = CrimsonLine, radius = 9f, center = Offset(lineX, 14f))
                drawCircle(color = Color.White, radius = 4f, center = Offset(lineX, 14f))
                drawCircle(color = CrimsonLine, radius = 9f, center = Offset(lineX, canvasHeight - 14f))
                drawCircle(color = Color.White, radius = 4f, center = Offset(lineX, canvasHeight - 14f))
            }
        }
    }
}
