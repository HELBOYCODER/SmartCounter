package com.helboy.smartcounter.engine

import android.graphics.RectF
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Geometric 2D spatial contour analyzer with strict wall/door rejection.
 */
class IndustrialVisionAnalyzer {

    var sensitivity: Float = 0.50f
    var isEnabled: Boolean = true

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

            val step = max(2, width / 140)
            val sampleW = width / step
            val sampleH = height / step

            if (sampleW < 10 || sampleH < 10) return emptyList()

            val grid = IntArray(sampleW * sampleH)
            buffer.rewind()

            for (sy in 0 until sampleH) {
                val srcY = sy * step
                val rowOffset = srcY * rowStride
                for (sx in 0 until sampleW) {
                    val srcX = sx * step
                    val pos = rowOffset + srcX * pixelStride
                    val lum = if (pos < buffer.limit()) buffer.get(pos).toInt() and 0xFF else 128
                    grid[sy * sampleW + sx] = lum
                }
            }

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

            // Strict threshold to reject flat walls and ceilings
            val factor = (1.4f - (sensitivity * 0.5f)).coerceIn(0.8f, 1.6f)
            val threshold = gradMean + factor * gradStd

            val binary = BooleanArray(sampleW * sampleH)
            for (i in binary.indices) {
                binary[i] = grad[i] >= threshold
            }

            val visited = BooleanArray(sampleW * sampleH)
            val detectedBoxes = mutableListOf<Pair<RectF, Float>>()

            val minArea = (sampleW * sampleH * 0.012f).toInt().coerceAtLeast(15)
            val maxArea = (sampleW * sampleH * 0.22f).toInt()
            val queue = IntArray(sampleW * sampleH)

            for (sy in 1 until sampleH - 1) {
                for (sx in 1 until sampleW - 1) {
                    val idx = sy * sampleW + sx
                    if (binary[idx] && !visited[idx]) {
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

                        // Strict rejection of door frames, walls, sky:
                        // Real containers have compact rectangular aspect ratio (0.5 to 2.0)
                        val aspect = compW.toFloat() / max(1, compH)
                        val fillRatio = componentArea.toFloat() / (compW * compH)

                        if (componentArea in minArea..maxArea &&
                            compW in 12..(sampleW * 0.7).toInt() &&
                            compH in 12..(sampleH * 0.7).toInt() &&
                            aspect in 0.55f..2.0f &&
                            fillRatio >= 0.15f // Rejects hollow door frames / wall border lines
                        ) {
                            val left = (minX.toFloat() / sampleW).coerceIn(0f, 1f)
                            val top = (minY.toFloat() / sampleH).coerceIn(0f, 1f)
                            val right = (maxX.toFloat() / sampleW).coerceIn(0f, 1f)
                            val bottom = (maxY.toFloat() / sampleH).coerceIn(0f, 1f)

                            val rect = RectF(left, top, right, bottom)
                            detectedBoxes.add(Pair(rect, 0.75f))
                        }
                    }
                }
            }

            detectedBoxes.take(30)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
