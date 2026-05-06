package com.google.ai.edge.gallery.customtasks.maintenance

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

private const val TAG = "AGObjectDetector"
private const val MODEL_FILE = "detect.tflite"
private const val LABEL_FILE = "labelmap.txt"
private const val INPUT_SIZE = 300
private const val NUM_DETECTIONS = 10

data class DetectionResult(
    val label: String,
    val confidence: Float,
    val top: Float,
    val left: Float,
    val bottom: Float,
    val right: Float,
)

class ObjectDetector(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()

    fun initialize(): Boolean {
        try {
            labels = loadLabels()
            val model = loadModelFile()

            // Try QNN Delegate (NPU) first
            try {
                val delegateOptions = com.qualcomm.qti.QnnDelegate.Options()
                val delegate = com.qualcomm.qti.QnnDelegate(delegateOptions)
                val options = Interpreter.Options().apply {
                    setNumThreads(4)
                    addDelegate(delegate)
                }
                interpreter = Interpreter(model, options)
                Log.d(TAG, "ObjectDetector initialized with QNN NPU")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "QNN Delegate failed, falling back to GPU/CPU: ${e.message}")
            }

            // Fallback to CPU
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(model, options)
            Log.d(TAG, "ObjectDetector initialized with CPU")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ObjectDetector", e)
            return false
        }
    }

    fun detect(bitmap: Bitmap): List<DetectionResult> {
        val interp = interpreter ?: return emptyList()

        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val inputBuffer = bitmapToByteBuffer(resized)

        val outputLocations = Array(1) { Array(NUM_DETECTIONS) { FloatArray(4) } }
        val outputClasses = Array(1) { FloatArray(NUM_DETECTIONS) }
        val outputScores = Array(1) { FloatArray(NUM_DETECTIONS) }
        val numDetections = FloatArray(1)

        val outputs = mapOf(
            0 to outputLocations,
            1 to outputClasses,
            2 to outputScores,
            3 to numDetections,
        )

        interp.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)

        val results = mutableListOf<DetectionResult>()
        val count = numDetections[0].toInt().coerceAtMost(NUM_DETECTIONS)
        for (i in 0 until count) {
            val score = outputScores[0][i]
            if (score < 0.5f) continue
            val classIdx = outputClasses[0][i].toInt()
            val label = if (classIdx in labels.indices) labels[classIdx] else "unknown"
            results.add(
                DetectionResult(
                    label = label,
                    confidence = score,
                    top = outputLocations[0][i][0],
                    left = outputLocations[0][i][1],
                    bottom = outputLocations[0][i][2],
                    right = outputLocations[0][i][3],
                )
            )
        }
        Log.d(TAG, "Detected ${results.size} objects")
        return results
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (pixel in pixels) {
            buffer.put(((pixel shr 16) and 0xFF).toByte()) // R
            buffer.put(((pixel shr 8) and 0xFF).toByte())  // G
            buffer.put((pixel and 0xFF).toByte())           // B
        }
        buffer.rewind()
        return buffer
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fd = context.assets.openFd(MODEL_FILE)
        val inputStream = java.io.FileInputStream(fd.fileDescriptor)
        val channel = inputStream.channel
        return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    private fun loadLabels(): List<String> {
        val labels = mutableListOf<String>()
        val reader = BufferedReader(InputStreamReader(context.assets.open(LABEL_FILE)))
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            line?.let { if (it.isNotBlank()) labels.add(it.trim()) }
        }
        reader.close()
        Log.d(TAG, "Loaded ${labels.size} labels")
        return labels
    }
}
