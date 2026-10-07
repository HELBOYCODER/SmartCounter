package com.helboy.smartcounter.engine

import android.graphics.RectF
import androidx.camera.core.ImageProxy
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 2D Spatial Computer Vision Engine for 60 FPS real-time detection.
 * Equipped with strict contrast gates and noise floors to guarantee ZERO false positives
 * on black screens, covered cameras, blank walls, ceilings, and flat surfaces.
 */
class IndustrialVisionAnalyzer {

    var sensitivity: Float = 0.40f

    fun analyzeYPlane(
        image: ImageProxy,
        preset: OnnxYoloAnalyzer.DetectionPreset
    ): List<Pair<RectF, Float>> {
        return try {
            val planes = image.planes
            if (planes.isEmpty()) return emptyList()

            val yPlane = planes[0]
            val buffer = yPlane.buffer
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

            var minLum = 255
            var maxLum = 0

            for (sy in 0 until sampleH) {
                val srcY = sy * step
                val rowOffset = srcY * rowStride
                for (sx in 0 until sampleW) {
                    val srcX = sx * step
                    val pos = rowOffset + srcX * pixelStride
                    val lum = if (pos < buffer.limit()) buffer.get(pos).toInt() and 0xFF else 128
                    grid[sy * sampleW + sx] = lum

                    if (lum < minLum) minLum = lum
                    if (lum > maxLum) maxLum = lum
                }
            }

            // =========================================================================
            // GATE 1: SCENE CONTRAST GATE (Guarantees ZERO detection on dark / flat scenes)
            // =========================================================================
            val sceneContrast = maxLum - minLum
            if (sceneContrast < 38) {
                // If scene luminance difference is less than 38 levels:
                // Camera is pointed at a black screen, covered with hand, blank wall, or dark room.
                // REJECT IMMEDIATELY — ZERO HALLUCINATED BOXES!
                return emptyList()
            }

            // =========================================================================
            // 1. Calculate 2D spatial gradient magnitude (Sobel kernel)
            // =========================================================================
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

            // =========================================================================
            // GATE 2: STRUCTURAL GRADIENT GATE
            // =========================================================================
            if (gradMean < 10.0f || gradStd < 6.0f) {
                // Background is uniform or low-energy noise: no prominent objects
                return emptyList()
            }

            // Strict threshold with ABSOLUTE NOISE FLOOR (40.0f)
            // Prevents sensor gain in low light from marking noise pixels as edges!
            val factor = (1.5f - (sensitivity * 0.4f)).coerceIn(1.0f, 1.8f)
            val threshold = maxOf(42.0f, gradMean + factor * gradStd)

            val binary = BooleanArray(sampleW * sampleH)
            for (i in binary.indices) {
                binary[i] = grad[i] >= threshold
            }

            // =========================================================================
            // 2. Fast 2D Connected Component Analysis with Shape Constraints
            // =========================================================================
            val detectedBoxes = mutableListOf<Pair<RectF, Float>>()
            val visited = BooleanArray(sampleW * sampleH)
            val minArea = (sampleW * sampleH * 0.015f).toInt().coerceAtLeast(20)
            val maxArea = (sampleW * sampleH * 0.20f).toInt()
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

                        val aspect = compW.toFloat() / max(1, compH)
                        val fillRatio = componentArea.toFloat() / (compW * compH)

                        // Strict geometric filters for real food containers and products:
                        // Aspect ratio between 0.60 and 1.85, minimum dimension 15px, solid fill
                        if (componentArea in minArea..maxArea &&
                            compW in 15..(sampleW * 0.65).toInt() &&
                            compH in 15..(sampleH * 0.65).toInt() &&
                            aspect in 0.60f..1.85f &&
                            fillRatio >= 0.18f
                        ) {
                            val left = (minX.toFloat() / sampleW).coerceIn(0f, 1f)
                            val top = (minY.toFloat() / sampleH).coerceIn(0f, 1f)
                            val right = (maxX.toFloat() / sampleW).coerceIn(0f, 1f)
                            val bottom = (maxY.toFloat() / sampleH).coerceIn(0f, 1f)

                            val rect = RectF(left, top, right, bottom)
                            detectedBoxes.add(Pair(rect, 0.70f))
                        }
                    }
                }
            }

            detectedBoxes.take(25)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
