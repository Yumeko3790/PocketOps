package com.google.ai.edge.gallery.customtasks.maintenance

import android.graphics.Bitmap
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import java.nio.LongBuffer

private const val TAG = "OnnxVIT"

object OnnxVIT {
    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null

    fun encode(bitmap: Bitmap): FloatArray? {
        try {
            // Load ONNX model on demand
            if (session == null) {
                Log.d(TAG, "Loading ONNX VIT model...")
                val startLoad = System.currentTimeMillis()
                env = OrtEnvironment.getEnvironment()
                val opts = OrtSession.SessionOptions()
                opts.setIntraOpNumThreads(4)
                // QNN EP for NPU acceleration would need onnxruntime-qnn package
                // For now use CPU with optimizations
                opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                session = env!!.createSession(
                    "/data/local/tmp/qwen2.5vl3b/vit_onnx/model.onnx", opts
                )
                Log.d(TAG, "ONNX VIT loaded in ${System.currentTimeMillis() - startLoad}ms")
            }

            // Resize to 644x644
            val resized = Bitmap.createScaledBitmap(bitmap, 644, 644, true)

            // CLIP normalize + permute to [2116, 1176]
            val mean = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
            val std = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)
            val w = 644; val h = 644
            val pixels = IntArray(w * h)
            resized.getPixels(pixels, 0, w, 0, 0, w, h)

            // Build NCHW normalized
            val nchw = FloatArray(3 * w * h)
            for (i in pixels.indices) {
                val p = pixels[i]
                nchw[0 * w * h + i] = (((p shr 16) and 0xFF) / 255.0f - mean[0]) / std[0]
                nchw[1 * w * h + i] = (((p shr 8) and 0xFF) / 255.0f - mean[1]) / std[1]
                nchw[2 * w * h + i] = ((p and 0xFF) / 255.0f - mean[2]) / std[2]
            }

            // HF 10-dim permute
            val PS = 14; val MS = 2; val TP = 2; val C = 3
            val llmH = 23; val llmW = 23
            val seqLen = 2116; val patchDim = 1176
            val pv = FloatArray(seqLen * patchDim)
            var idx = 0
            for (lh in 0 until llmH)
            for (lw in 0 until llmW)
            for (mh in 0 until MS)
            for (mw in 0 until MS)
            for (c in 0 until C)
            for (t in 0 until TP)
            for (ph in 0 until PS)
            for (pw in 0 until PS) {
                val row = (lh * MS + mh) * PS + ph
                val col = (lw * MS + mw) * PS + pw
                pv[idx++] = nchw[c * w * h + row * w + col]
            }

            // Create ONNX tensors
            val pvTensor = OnnxTensor.createTensor(
                env, FloatBuffer.wrap(pv), longArrayOf(seqLen.toLong(), patchDim.toLong())
            )
            val gridTensor = OnnxTensor.createTensor(
                env, LongBuffer.wrap(longArrayOf(1, 46, 46)), longArrayOf(1, 3)
            )

            // Run inference
            Log.d(TAG, "Running ONNX VIT inference...")
            val startTime = System.currentTimeMillis()
            val inputs = mapOf("pixel_values" to pvTensor, "grid_thw" to gridTensor)
            val results = session!!.run(inputs)
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "ONNX VIT inference done in ${elapsed}ms")

            // Extract embedding
            val outTensor = results[0] as OnnxTensor
            val embedding = outTensor.floatBuffer.array()
            Log.d(TAG, "Embedding: ${embedding.size} floats, first 5: ${embedding.take(5)}")

            pvTensor.close()
            gridTensor.close()
            results.close()

            return embedding
        } catch (e: Exception) {
            Log.e(TAG, "ONNX VIT failed: ${e.message}", e)
            return null
        }
    }

    fun release() {
        session?.close()
        session = null
        env?.close()
        env = null
        Log.d(TAG, "ONNX VIT released")
    }
}
