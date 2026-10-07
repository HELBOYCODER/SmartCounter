package com.helboy.smartcounter.engine

import android.graphics.RectF
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * High-speed native industrial 2D spatial vision analyzer.
 * Processes luminance (Y-plane) directly at 60 FPS.
 * Detects repeated uniform products (food trays, spools, packages, bottles)
 * via 2D spatial gradient magnitude, adaptive variance profiling, and connected components.
 */
class IndustrialVisionAnalyzer {

    var sensitivity: Float = 0.65f // 0.1 to 1.0 (threshold sensitivity)
    var isEnabled: Boolean = true

    /**
     * Fast 2D spatial object detection on downsampled luminance grid.
     */
    fun analyzeYPlane(
        image: ImageProxy,
        preset: OnnxYoloAnalyzer.DetectionPreset = OnnxYoloAnalyzer.DetectionPreset.FOOD_CONTAINER
    ): List<Pair<RectF, Float>> {
        if (!isEnabled) return emptyList()

        return try {
            val planes = image.planes
            if (planes.isEmpty()) return emptyList()

            val yPlane = planes[0]
            val buffer: ByteBuffer = yPlane.buffer
            val width = image.width
            val height = image.height
            val rowStride = yPlane.rowStride
            val pixelStride = yPlane.pixelStride

            // Downsample grid (approx 160x160 for high FPS on mobile CPU)
            val step = max(2, width / 160)
            val sampleW = width / step
            val sampleH = height / step

            if (sampleW < 10 || sampleH < 10) return emptyList()

            val grid = IntArray(sampleW * sampleH)
            buffer.rewind()

            var sum = 0L
            for (sy in 0 until sampleH) {
                val srcY = sy * step
                val rowOffset = srcY * rowStride
                for (sx in 0 until sampleW) {
                    val srcX = sx * step
                    val pos = rowOffset + srcX * pixelStride
                    val lum = if (pos < buffer.limit()) buffer.get(pos).toInt() and 0xFF else 128
                    grid[sy * sampleW + sx] = lum
                    sum += lum
                }
            }

            val meanLum = (sum / (sampleW * sampleH)).toFloat()

            // 1. Calculate 2D spatial gradient magnitude (Sobel kernel)
            val grad = FloatArray(sampleW * sampleH)
            var gradSum = 0.0f
            var gradSqSum = 0.0f
            val count = (sampleW - 2) * (sampleH - 2)

            for (sy in 1 until sampleH - 1) {
                val rowIdx = sy * sampleW
                for (sx in 1 until sampleW - 1) {
                    val gx = (grid[rowIdx + sx + 1] - grid[rowIdx + sx - 1]).toFloat()
                    val gy = (grid[(sy + 1) * sampleW + sx] - grid[(sy - 1) * sampleW + sx]).toFloat()
                    val mag = sqrt(gx * gx + gy * gy)
                    grad[rowIdx + sx] = mag
                    gradSum += mag
                    gradSqSum += mag * mag
                }
            }

            if (count <= 0) return emptyList()
            val gradMean = gradSum / count
            val gradVariance = max(0.0f, (gradSqSum / count) - (gradMean * gradMean))
            val gradStd = sqrt(gradVariance)

            // Dynamic threshold based on sensitivity
            val factor = (1.1f - sensitivity).coerceIn(0.2f, 1.2f)
            val threshold = gradMean + factor * gradStd

            // 2. Binary edge / rim map
            val binary = BooleanArray(sampleW * sampleH)
            for (i in binary.indices) {
                binary[i] = grad[i] >= threshold
            }

            // 3. Connected components on 2D grid (flood fill)
            val visited = BooleanArray(sampleW * sampleH)
            val detectedBoxes = mutableListOf<Pair<RectF, Float>>()

            val minArea = when (preset) {
                OnnxYoloAnalyzer.DetectionPreset.FOOD_CONTAINER -> (sampleW * sampleH * 0.002f).toInt().coerceAtLeast(8)
                OnnxYoloAnalyzer.DetectionPreset.SPOOL_CIRCULAR -> (sampleW * sampleH * 0.003f).toInt().coerceAtLeast(10)
                OnnxYoloAnalyzer.DetectionPreset.ALL_OBJECTS -> (sampleW * sampleH * 0.002f).toInt().coerceAtLeast(8)
            }
            val maxArea = (sampleW * sampleH * 0.28f).toInt()

            val queue = IntArray(sampleW * sampleH)

            for (sy in 1 until sampleH - 1) {
                for (sx in 1 until sampleW - 1) {
                    val idx = sy * sampleW + sx
                    if (binary[idx] && !visited[idx]) {
                        // Start flood fill
                        var head = 0
                        var tail = 0
                        queue[tail++] = idx
                        visited[idx] = true

                        var minX = sx
                        var maxX = sx
                        var minY = sy
                        var maxY = sy
                        var componentArea = 0

                        while (head < tail) {
                            val curr = queue[head++]
                            componentArea++
                            val cy = curr / sampleW
                            val cx = curr % sampleW

                            if (cx < minX) minX = cx
                            if (cx > maxX) maxX = cx
                            if (cy < minY) minY = cy
                            if (cy > maxY) maxY = cy

                            // Check 4-connected neighbors
                            val neighbors = intArrayOf(
                                curr - 1, curr + 1, curr - sampleW, curr + sampleW
                            )
                            for (n in neighbors) {
                                if (n in binary.indices && binary[n] && !visited[n]) {
                                    visited[n] = true
                                    queue[tail++] = n
                                }
                            }
                        }

                        val compW = maxX - minX + 1
                        val compH = maxY - minY + 1

                        if (componentArea in minArea..maxArea && compW >= 6 && compH >= 6) {
                            val aspect = compW.toFloat() / max(1, compH)
                            val validAspect = when (preset) {
                                OnnxYoloAnalyzer.DetectionPreset.FOOD_CONTAINER -> aspect in 0.32f..3.2f
                                OnnxYoloAnalyzer.DetectionPreset.SPOOL_CIRCULAR -> aspect in 0.65f..1.55f
                                OnnxYoloAnalyzer.DetectionPreset.ALL_OBJECTS -> aspect in 0.25f..4.0f
                            }

                            if (validAspect) {
                                // Normalized coordinates (0f to 1f)
                                val left = (minX.toFloat() / sampleW).coerceIn(0f, 1f)
                                val top = (minY.toFloat() / sampleH).coerceIn(0f, 1f)
                                val right = (maxX.toFloat() / sampleW).coerceIn(0f, 1f)
                                val bottom = (maxY.toFloat() / sampleH).coerceIn(0f, 1f)

                                val rect = RectF(left, top, right, bottom)
                                val confidence = (0.50f + (componentArea.toFloat() / maxArea) * 0.45f).coerceIn(0.50f, 0.95f)
                                detectedBoxes.add(Pair(rect, confidence))
                            }
                        }
                    }
                }
            }

            // Cap to avoid clutter
            detectedBoxes.take(40)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
