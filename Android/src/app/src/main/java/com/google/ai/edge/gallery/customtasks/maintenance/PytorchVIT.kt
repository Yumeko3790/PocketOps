package com.google.ai.edge.gallery.customtasks.maintenance

import android.graphics.Bitmap
import android.util.Log
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import kotlin.math.roundToInt

private const val TAG = "PytorchVIT"

object PytorchVIT {
    private var module: Module? = null
    private var isLoaded = false

    fun load(): Boolean {
        if (isLoaded) return true
        val modelPath = "/data/local/tmp/qwen25vl_vit.pt"
        try {
            Log.d(TAG, "Loading VIT model from $modelPath...")
            module = Module.load(modelPath)
            isLoaded = true
            Log.d(TAG, "VIT model loaded successfully")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load VIT model: ${e.message}", e)
            return false
        }
    }

    fun encode(bitmap: Bitmap): FloatArray? {
        try {
            // Load model on demand, run inference, then release to save memory
            val modelPath = "/data/local/tmp/qwen25vl_vit.pt"
            Log.d(TAG, "Loading VIT model...")
            val startLoad = System.currentTimeMillis()
            val mod = Module.load(modelPath)
            Log.d(TAG, "VIT loaded in ${System.currentTimeMillis() - startLoad}ms")

            // Resize to 644x644
            val resized = Bitmap.createScaledBitmap(bitmap, 644, 644, true)

            // CLIP normalize + permute to [2116, 1176]
            val mean = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
            val std = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)
            val w = 644; val h = 644
            val pixels = IntArray(w * h)
            resized.getPixels(pixels, 0, w, 0, 0, w, h)

            // Build NCHW normalized float array
            val nchw = FloatArray(3 * w * h)
            for (i in pixels.indices) {
                val p = pixels[i]
                val r = ((p shr 16) and 0xFF) / 255.0f
                val g = ((p shr 8) and 0xFF) / 255.0f
                val b = (p and 0xFF) / 255.0f
                nchw[0 * w * h + i] = (r - mean[0]) / std[0]
                nchw[1 * w * h + i] = (g - mean[1]) / std[1]
                nchw[2 * w * h + i] = (b - mean[2]) / std[2]
            }

            // HF 10-dim permute: [batch=1, grid_t=1, TP=2, C=3, llm_h=23, MS=2, PS=14, llm_w=23, MS=2, PS=14]
            // -> (0,1,4,7,5,8,3,2,6,9) -> flatten to [2116, 1176]
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

            // Create tensors (float32 - PyTorch Android doesn't support fp16 Conv3d)
            val pvTensor = Tensor.fromBlob(pv, longArrayOf(seqLen.toLong(), patchDim.toLong()))
            val gridTensor = Tensor.fromBlob(longArrayOf(1, 46, 46), longArrayOf(1, 3))

            // Run VIT
            Log.d(TAG, "Running VIT inference...")
            val startTime = System.currentTimeMillis()
            val output = mod.forward(IValue.from(pvTensor), IValue.from(gridTensor))
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "VIT inference done in ${elapsed}ms")

            // Extract embedding [1, 529, 2048] or [529, 2048]
            val outTensor = output.toTensor()
            val shape = outTensor.shape()
            Log.d(TAG, "VIT output shape: ${shape.toList()}")

            val embedding = outTensor.dataAsFloatArray
            Log.d(TAG, "Embedding: ${embedding.size} floats, first 5: ${embedding.take(5)}")

            // Release VIT model to free memory for Genie Dialog
            mod.destroy()
            Log.d(TAG, "VIT model released")

            return embedding
        } catch (e: Exception) {
            Log.e(TAG, "VIT inference failed", e)
            return null
        }
    }

    fun isReady(): Boolean = isLoaded
}
