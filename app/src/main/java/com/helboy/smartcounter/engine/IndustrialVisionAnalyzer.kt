package com.helboy.smartcounter.engine

import android.graphics.RectF
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * High-speed native industrial object analyzer.
 * Processes Y-plane (luminance) directly from CameraX ImageProxy at 30-60 FPS.
 * Detects repeated uniform products (food trays, spools, packages, bottles)
 * via adaptive gradient and contrast projection.
 */
class IndustrialVisionAnalyzer(
    private val tracker: IouTracker,
    private val lineCounter: LineCounter,
    private val onAnalysisResult: (
        tracks: List<TrackedObject>,
        fps: Float,
        inFrameCount: Int
    ) -> Unit
) : ImageAnalysis.Analyzer {

    var sensitivity: Float = 0.5f // 0.1 to 1.0 (threshold sensitivity)
    var isEnabled: Boolean = true

    private var lastFpsTimestamp = System.currentTimeMillis()
    private var frameCount = 0
    private var currentFps = 0.0f

    override fun analyze(image: ImageProxy) {
        if (!isEnabled) {
            image.close()
            return
        }

        try {
            val planes = image.planes
            if (planes.isEmpty()) {
                image.close()
                return
            }

            val yPlane = planes[0]
            val buffer: ByteBuffer = yPlane.buffer
            val width = image.width
            val height = image.height
            val rowStride = yPlane.rowStride
            val pixelStride = yPlane.pixelStride

            // Calculate FPS
            frameCount++
            val now = System.currentTimeMillis()
            val elapsed = now - lastFpsTimestamp
            if (elapsed >= 1000) {
                currentFps = (frameCount * 1000f) / elapsed
                frameCount = 0
                lastFpsTimestamp = now
            }

            // Downsample for fast 60fps scanning (scale to ~160x120)
            val step = max(2, width / 160)
            val sampleW = width / step
            val sampleH = height / step

            val detectedBoxes = detectObjectsFast(
                buffer = buffer,
                srcWidth = width,
                srcHeight = height,
                rowStride = rowStride,
                pixelStride = pixelStride,
                step = step,
                sampleW = sampleW,
                sampleH = sampleH
            )

            // Update tracker
            val activeTracks = tracker.update(detectedBoxes)

            // Update line counter
            lineCounter.processTracks(activeTracks)

            // Notify UI
            onAnalysisResult(activeTracks, currentFps, detectedBoxes.size)

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            image.close()
        }
    }

    private fun detectObjectsFast(
        buffer: ByteBuffer,
        srcWidth: Int,
        srcHeight: Int,
        rowStride: Int,
        pixelStride: Int,
        step: Int,
        sampleW: Int,
        sampleH: Int
    ): List<Pair<RectF, Float>> {
        val boxes = mutableListOf<Pair<RectF, Float>>()
        if (sampleW <= 4 || sampleH <= 4) return boxes

        // Read sampled grid into flat luminance array
        val grid = IntArray(sampleW * sampleH)
        var sum = 0L

        buffer.rewind()
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

        val avgLum = (sum / (sampleW * sampleH)).toInt()
        val contrastDelta = (35 * (1.1f - sensitivity)).toInt().coerceIn(12, 60)

        // Find candidate bounding regions via 2D projection profiling
        // Horizontal projection of high contrast deviations
        val rowEnergy = IntArray(sampleH)
        for (sy in 0 until sampleH) {
            var energy = 0
            for (sx in 1 until sampleW) {
                val diff = abs(grid[sy * sampleW + sx] - grid[sy * sampleW + (sx - 1)])
                val dev = abs(grid[sy * sampleW + sx] - avgLum)
                if (diff > contrastDelta || dev > contrastDelta) {
                    energy++
                }
            }
            rowEnergy[sy] = energy
        }

        // Segment rows into object bands
        val energyThresh = (sampleW * 0.15f).toInt().coerceAtLeast(3)
        var inBand = false
        var bandStart = 0

        for (sy in 0 until sampleH) {
            val high = rowEnergy[sy] >= energyThresh
            if (high && !inBand) {
                inBand = true
                bandStart = sy
            } else if (!high && inBand) {
                inBand = false
                val bandHeight = sy - bandStart
                if (bandHeight in 4..(sampleH * 0.75).toInt()) {
                    // Within this vertical band, find horizontal clusters (objects side by side)
                    findHorizontalObjectsInBand(
                        grid = grid,
                        sampleW = sampleW,
                        sampleH = sampleH,
                        startY = bandStart,
                        endY = sy,
                        avgLum = avgLum,
                        contrastDelta = contrastDelta,
                        boxes = boxes
                    )
                }
            }
        }

        return boxes.take(30) // Cap to prevent UI clutter
    }

    private fun findHorizontalObjectsInBand(
        grid: IntArray,
        sampleW: Int,
        sampleH: Int,
        startY: Int,
        endY: Int,
        avgLum: Int,
        contrastDelta: Int,
        boxes: MutableList<Pair<RectF, Float>>
    ) {
        val colEnergy = IntArray(sampleW)
        for (sx in 0 until sampleW) {
            var count = 0
            for (sy in startY until endY) {
                val dev = abs(grid[sy * sampleW + sx] - avgLum)
                if (dev > contrastDelta) count++
            }
            colEnergy[sx] = count
        }

        val colThresh = ((endY - startY) * 0.25f).toInt().coerceAtLeast(2)
        var inObj = false
        var objStart = 0

        for (sx in 0 until sampleW) {
            val active = colEnergy[sx] >= colThresh
            if (active && !inObj) {
                inObj = true
                objStart = sx
            } else if (!active && inObj) {
                inObj = false
                val objWidth = sx - objStart
                if (objWidth in 3..(sampleW * 0.8).toInt()) {
                    // Normalized coordinates (0f to 1f)
                    val left = objStart.toFloat() / sampleW
                    val top = startY.toFloat() / sampleH
                    val right = sx.toFloat() / sampleW
                    val bottom = endY.toFloat() / sampleH

                    val rect = RectF(
                        left.coerceIn(0f, 1f),
                        top.coerceIn(0f, 1f),
                        right.coerceIn(0f, 1f),
                        bottom.coerceIn(0f, 1f)
                    )
                    boxes.add(Pair(rect, 0.95f))
                }
            }
        }
    }
}
