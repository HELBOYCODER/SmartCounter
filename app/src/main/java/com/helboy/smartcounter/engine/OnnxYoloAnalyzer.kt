package com.helboy.smartcounter.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import java.util.Collections
import kotlin.math.max
import kotlin.math.min

class OnnxYoloAnalyzer(private val context: Context) {

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var isInitialized = false

    private val inputSize = 640
    private val confThreshold = 0.35f
    private val iouThreshold = 0.45f

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
                var maxClassScore = 0.0f
                for (c in 0 until numClasses) {
                    val score = tensor84x8400[4 + c][anchorIdx]
                    if (score > maxClassScore) {
                        maxClassScore = score
                    }
                }

                if (maxClassScore >= confThreshold) {
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
                    rawBoxes.add(RawBox(rect, maxClassScore))
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

    private data class RawBox(val rect: RectF, val score: Float)

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
