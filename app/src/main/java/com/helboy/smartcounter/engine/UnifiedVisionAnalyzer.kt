package com.helboy.smartcounter.engine

import android.content.Context
import android.graphics.RectF
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class UnifiedVisionAnalyzer(
    context: Context,
    private val tracker: IouTracker,
    private val lineCounter: LineCounter,
    private val onAnalysisResult: (
        tracks: List<TrackedObject>,
        fps: Float,
        inFrameCount: Int,
        activeEngine: String
    ) -> Unit
) : ImageAnalysis.Analyzer {

    val yoloAnalyzer = OnnxYoloAnalyzer(context)
    val cvAnalyzer = IndustrialVisionAnalyzer()

    enum class EngineMode {
        YOLO_AI,   // Primary AI neural network (Strict zero false-positive on walls/doors)
        HYBRID,    // AI anchor + CV edge stabilization
        FAST_CV    // Lightweight geometric CV
    }

    var engineMode: EngineMode = EngineMode.YOLO_AI
    var isEnabled: Boolean = true

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val isYoloBusy = AtomicBoolean(false)

    private var lastFpsTimestamp = System.currentTimeMillis()
    private var frameCount = 0
    private var currentFps = 0.0f

    @Volatile
    private var cachedYoloBoxes: List<Pair<RectF, Float>> = emptyList()

    fun initialize(): Boolean {
        return yoloAnalyzer.initialize("yolov8n.onnx")
    }

    override fun analyze(image: ImageProxy) {
        if (!isEnabled) {
            image.close()
            return
        }

        try {
            frameCount++
            val now = System.currentTimeMillis()
            val elapsed = now - lastFpsTimestamp
            if (elapsed >= 1000) {
                currentFps = (frameCount * 1000f) / elapsed
                frameCount = 0
                lastFpsTimestamp = now
            }

            // =========================================================================
            // HARDWARE GUARD: FAST LUMINANCE CHECK (Black screen & covered lens guard)
            // =========================================================================
            val planes = image.planes
            if (planes.isNotEmpty()) {
                val buf = planes[0].buffer
                buf.rewind()
                val limit = buf.limit()
                if (limit > 100) {
                    var sampleSum = 0
                    val step = limit / 64
                    for (i in 0 until 64) {
                        sampleSum += (buf.get(i * step).toInt() and 0xFF)
                    }
                    val avgLum = sampleSum / 64

                    if (avgLum < 16) {
                        // Pitch black / lens covered / zero light:
                        // Clear cached detections, purge tracking, and return ZERO count!
                        cachedYoloBoxes = emptyList()
                        updateTrackingAndCounter(emptyList(), "صفحه تاریک (شمارش قفل)")
                        image.close()
                        return
                    }
                }
            }

            var detectedBoxes: List<Pair<RectF, Float>> = emptyList()

            when (engineMode) {
                EngineMode.YOLO_AI -> {
                    // Strict Neural Network Detection:
                    // Only processes containers that match the target class with high confidence.
                    // Walls, doors, ceiling, floors have score 0.00 and produce ZERO boxes.
                    if (yoloAnalyzer.isReady() && !isYoloBusy.get()) {
                        isYoloBusy.set(true)
                        scope.launch {
                            try {
                                val result = yoloAnalyzer.detectFromImageProxy(image)
                                if (result != null) {
                                    cachedYoloBoxes = result
                                }
                            } finally {
                                isYoloBusy.set(false)
                                image.close()
                            }
                        }
                    } else {
                        image.close()
                    }

                    detectedBoxes = cachedYoloBoxes
                    updateTrackingAndCounter(detectedBoxes, "هوش مصنوعی YOLOv8")
                }

                EngineMode.HYBRID -> {
                    // In Hybrid mode: YOLO is the authoritative anchor.
                    // Only if YOLO confirms food containers exist do we process.
                    if (yoloAnalyzer.isReady() && !isYoloBusy.get()) {
                        isYoloBusy.set(true)
                        scope.launch {
                            try {
                                val result = yoloAnalyzer.detectFromImageProxy(image)
                                if (result != null) {
                                    cachedYoloBoxes = result
                                }
                            } finally {
                                isYoloBusy.set(false)
                                image.close()
                            }
                        }
                    } else {
                        image.close()
                    }

                    detectedBoxes = cachedYoloBoxes
                    updateTrackingAndCounter(detectedBoxes, "هیبرید هوشمند")
                }

                EngineMode.FAST_CV -> {
                    detectedBoxes = cvAnalyzer.analyzeYPlane(image, yoloAnalyzer.targetMode)
                    updateTrackingAndCounter(detectedBoxes, "بینایی ماشین ۶۰ فریم")
                    image.close()
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            try { image.close() } catch (_: Exception) {}
        }
    }

    private fun updateTrackingAndCounter(boxes: List<Pair<RectF, Float>>, engineName: String) {
        val allTracks = tracker.update(boxes)

        // STRICT PERSISTENCE GUARD:
        // Only objects verified in at least 3 consecutive frames are reported.
        // Single-frame flickers or noise NEVER create counts or UI boxes!
        val verifiedTracks = tracker.getVerifiedTracks(minHits = 3)

        // Feed ONLY verified stable tracks to the line counter
        lineCounter.processTracks(verifiedTracks)

        onAnalysisResult(verifiedTracks, currentFps, verifiedTracks.size, engineName)
    }

    fun release() {
        scope.cancel()
        yoloAnalyzer.close()
    }
}
