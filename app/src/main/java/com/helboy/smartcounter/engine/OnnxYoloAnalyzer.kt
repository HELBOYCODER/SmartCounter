package com.helboy.smartcounter.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

class OnnxYoloAnalyzer(private val context: Context) {

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var isInitialized = false
    private val isBusy = AtomicBoolean(false)

    var confThreshold: Float = 0.15f // Default tuned for food containers
    var iouThreshold: Float = 0.40f
    var targetMode: DetectionPreset = DetectionPreset.FOOD_CONTAINER

    private val inputSize = 640

    enum class DetectionPreset {
        FOOD_CONTAINER, // Prioritizes bowls (45), cups (41), trays, and food boxes
        SPOOL_CIRCULAR, // Prioritizes circular & spool objects
        ALL_OBJECTS     // Class-agnostic objectness
    }

    // High-priority COCO class IDs for food containers
    // 45 = bowl (food tray / container / bowl)
    // 41 = cup, 39 = bottle, 48 = sandwich, 51 = carrot, 53 = pizza, 55 = cake
    // 69 = oven, 73 = book (flat rectangular pack)
    private val foodContainerClasses = setOf(45, 41, 39, 44, 48, 51, 53, 55, 69, 73)

    fun initialize(modelAssetName: String = "yolov8n.onnx"): Boolean {
        return try {
            ortEnv = OrtEnvironment.getEnvironment()
            val modelFile = getOrCopyAssetFile(modelAssetName)
            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(4)
            }
            ortSession = ortEnv?.createSession(modelFile.absolutePath, sessionOptions)
            isInitialized = true
            true
        } catch (e: Exception) {
            e.printStackTrace()
            isInitialized = false
            false
        }
    }

    private fun getOrCopyAssetFile(assetName: String): File {
        val file = File(context.filesDir, assetName)
        if (!file.exists() || file.length() == 0L) {
            context.assets.open(assetName).use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return file
    }

    fun isReady(): Boolean = isInitialized && ortSession != null

    /**
     * Process ImageProxy from CameraX asynchronously without blocking UI.
     */
    fun detectFromImageProxy(imageProxy: ImageProxy): List<Pair<RectF, Float>>? {
        if (!isReady()) return null
        if (!isBusy.compareAndSet(false, true)) {
            // Drop frame if analyzer is busy with previous inference
            return null
        }

        return try {
            // CameraX provides toBitmap() in camera-core 1.3+
            val bitmap = imageProxy.toBitmap()
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            val orientedBitmap = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }

            detect(orientedBitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            isBusy.set(false)
        }
    }

    fun detect(bitmap: Bitmap): List<Pair<RectF, Float>> {
        val env = ortEnv ?: return emptyList()
        val session = ortSession ?: return emptyList()
        if (!isInitialized) return emptyList()

        return try {
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
            val floatBuffer = FloatBuffer.allocate(1 * 3 * inputSize * inputSize)
            val pixels = IntArray(inputSize * inputSize)
            scaledBitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

            // Normalize CHW format (1, 3, 640, 640)
            val channelSize = inputSize * inputSize
            for (i in pixels.indices) {
                val color = pixels[i]
                val r = ((color shr 16) and 0xFF) / 255.0f
                val g = ((color shr 8) and 0xFF) / 255.0f
                val b = (color and 0xFF) / 255.0f

                floatBuffer.put(i, r)
                floatBuffer.put(channelSize + i, g)
                floatBuffer.put(2 * channelSize + i, b)
            }
            floatBuffer.rewind()

            val shape = longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())
            val inputTensor = OnnxTensor.createTensor(env, floatBuffer, shape)

            val inputName = session.inputNames.iterator().next()
            val results = session.run(Collections.singletonMap(inputName, inputTensor))

            val outputTensor = results[0] as? OnnxTensor ?: return emptyList()
            val outputArray = outputTensor.value as? Array<Array<FloatArray>> ?: return emptyList()

            // YOLOv8 shape is [1, 84, 8400]
            val tensor84x8400 = outputArray[0]
            val numAnchors = tensor84x8400[0].size
            val numClasses = tensor84x8400.size - 4

            val rawBoxes = mutableListOf<RawBox>()

            for (anchorIdx in 0 until numAnchors) {
                var bestScore = 0.0f
                var bestClass = -1

                when (targetMode) {
                    DetectionPreset.FOOD_CONTAINER -> {
                        // In Food Container mode, prioritize class 45 (bowl/tray) & food pack classes
                        for (c in 0 until numClasses) {
                            val score = tensor84x8400[4 + c][anchorIdx]
                            val boostedScore = if (c in foodContainerClasses) {
                                // Boost food-related classes for meal trays and containers
                                score * (if (c == 45) 1.25f else 1.10f)
                            } else {
                                score * 0.85f
                            }
                            if (boostedScore > bestScore) {
                                bestScore = boostedScore
                                bestClass = c
                            }
                        }
                    }
                    DetectionPreset.SPOOL_CIRCULAR -> {
                        for (c in 0 until numClasses) {
                            val score = tensor84x8400[4 + c][anchorIdx]
                            if (score > bestScore) {
                                bestScore = score
                                bestClass = c
                            }
                        }
                    }
                    DetectionPreset.ALL_OBJECTS -> {
                        // Class-agnostic objectness
                        for (c in 0 until numClasses) {
                            val score = tensor84x8400[4 + c][anchorIdx]
                            if (score > bestScore) {
                                bestScore = score
                                bestClass = c
                            }
                        }
                    }
                }

                if (bestScore >= confThreshold) {
                    val cx = tensor84x8400[0][anchorIdx]
                    val cy = tensor84x8400[1][anchorIdx]
                    val w = tensor84x8400[2][anchorIdx]
                    val h = tensor84x8400[3][anchorIdx]

                    val left = (cx - w / 2f) / inputSize
                    val top = (cy - h / 2f) / inputSize
                    val right = (cx + w / 2f) / inputSize
                    val bottom = (cy + h / 2f) / inputSize

                    val rect = RectF(
                        left.coerceIn(0f, 1f),
                        top.coerceIn(0f, 1f),
                        right.coerceIn(0f, 1f),
                        bottom.coerceIn(0f, 1f)
                    )
                    rawBoxes.add(RawBox(rect, bestScore, bestClass))
                }
            }

            inputTensor.close()
            results.close()

            applyNms(rawBoxes)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private data class RawBox(val rect: RectF, val score: Float, val classId: Int)

    private fun applyNms(boxes: List<RawBox>): List<Pair<RectF, Float>> {
        val sorted = boxes.sortedByDescending { it.score }
        val selected = mutableListOf<Pair<RectF, Float>>()
        val suppressed = BooleanArray(sorted.size)

        for (i in sorted.indices) {
            if (suppressed[i]) continue
            val current = sorted[i]
            selected.add(Pair(current.rect, current.score))

            for (j in i + 1 until sorted.size) {
                if (suppressed[j]) continue
                val iou = IouTracker.calculateIou(current.rect, sorted[j].rect)
                if (iou > iouThreshold) {
                    suppressed[j] = true
                }
            }
        }
        return selected
    }

    fun close() {
        ortSession?.close()
        ortEnv?.close()
        isInitialized = false
    }
}
