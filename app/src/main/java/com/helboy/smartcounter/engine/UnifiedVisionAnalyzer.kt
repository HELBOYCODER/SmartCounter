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
        YOLO_AI,   // Deep Learning YOLOv8 ONNX (High Accuracy)
        FAST_CV,   // 60 FPS 2D Spatial CV (Lightweight)
        HYBRID     // Unified Fusion (Best of both)
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
            // Measure FPS
            frameCount++
            val now = System.currentTimeMillis()
            val elapsed = now - lastFpsTimestamp
            if (elapsed >= 1000) {
                currentFps = (frameCount * 1000f) / elapsed
                frameCount = 0
                lastFpsTimestamp = now
            }

            var detectedBoxes: List<Pair<RectF, Float>> = emptyList()

            when (engineMode) {
                EngineMode.FAST_CV -> {
                    detectedBoxes = cvAnalyzer.analyzeYPlane(image, yoloAnalyzer.targetMode)
                    updateTrackingAndCounter(detectedBoxes, "Fast CV 60fps")
                    image.close()
                }

                EngineMode.YOLO_AI -> {
                    // Run YOLO asynchronously, use cached boxes for smooth 60fps tracking
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
                    updateTrackingAndCounter(detectedBoxes, "YOLOv8 AI")
                }

                EngineMode.HYBRID -> {
                    // 1. Fast CV runs on current frame
                    val cvBoxes = cvAnalyzer.analyzeYPlane(image, yoloAnalyzer.targetMode)

                    // 2. YOLO runs in background
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

                    // 3. Fuse CV boxes and YOLO boxes
                    val merged = mutableListOf<Pair<RectF, Float>>()
                    merged.addAll(cachedYoloBoxes)
                    for (cvBox in cvBoxes) {
                        val alreadyCovered = cachedYoloBoxes.any { yoloBox ->
                            IouTracker.calculateIou(cvBox.first, yoloBox.first) > 0.35f
                        }
                        if (!alreadyCovered) {
                            merged.add(cvBox)
                        }
                    }
                    detectedBoxes = merged
                    updateTrackingAndCounter(detectedBoxes, "Hybrid AI+CV")
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            try { image.close() } catch (_: Exception) {}
        }
    }

    private fun updateTrackingAndCounter(boxes: List<Pair<RectF, Float>>, engineName: String) {
        val activeTracks = tracker.update(boxes)
        lineCounter.processTracks(activeTracks)
        onAnalysisResult(activeTracks, currentFps, boxes.size, engineName)
    }

    fun release() {
        scope.cancel()
        yoloAnalyzer.close()
    }
}
