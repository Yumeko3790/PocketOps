package com.google.ai.edge.gallery.customtasks.maintenance

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.runtime.CleanUpListener
import com.google.ai.edge.gallery.runtime.LlmModelHelper
import com.google.ai.edge.gallery.runtime.ResultListener
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ToolProvider
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "AGGenieModelHelper"
private const val IMAGE_TOKEN_DIM = 2048

data class GenieModelInstance(
    val configPath: String,
    val conversationHistory: MutableList<Pair<String, String>> = mutableListOf(),
    var systemPrompt: String = "",
    val pipelineEnabled: Boolean = false,
)

object GenieModelHelper : LlmModelHelper {

    @Volatile
    private var abortRequested = false

    override fun initialize(
        context: Context,
        model: Model,
        supportImage: Boolean,
        supportAudio: Boolean,
        onDone: (String) -> Unit,
        systemInstruction: Contents?,
        tools: List<ToolProvider>,
        enableConversationConstrainedDecoding: Boolean,
        coroutineScope: CoroutineScope?,
    ) {
        val scope = coroutineScope ?: CoroutineScope(Dispatchers.IO)
        scope.launch(Dispatchers.IO) {
            try {
                val baseDir = "/data/local/tmp/qwen2.5vl3b"
                val llmConfigFile = File("$baseDir/config.json")
                val embeddingFile = File("$baseDir/embedding_weights_151936x2048.raw")
                val textGenFile = File("$baseDir/text_generator.json")
                val textEncFile = File("$baseDir/text_encoder.json")
                val imgEncFile = File("$baseDir/veg.json")

                if (!llmConfigFile.exists()) {
                    onDone("LLM config not found: ${llmConfigFile.path}")
                    return@launch
                }
                if (!embeddingFile.exists()) {
                    onDone("Embedding file not found: ${embeddingFile.path}")
                    return@launch
                }

                val llmConfigJson = llmConfigFile.readText()
                val enablePipeline =
                    supportImage &&
                        textGenFile.exists() &&
                        textEncFile.exists() &&
                        imgEncFile.exists()
                val textGenJson = if (enablePipeline) textGenFile.readText() else "{}"
                val textEncJson = if (enablePipeline) textEncFile.readText() else "{}"
                val imgEncJson = if (enablePipeline) imgEncFile.readText() else "{}"

                Log.d(TAG, "Initializing Genie, imagePipelineEnabled=$enablePipeline")
                val success =
                    GenieNative.initialize(
                        llmConfigJson,
                        embeddingFile.path,
                        imgEncFile.path,
                    )
                if (!success) {
                    onDone("Genie initialization failed")
                    return@launch
                }

                val pipelineReady = GenieNative.isPipelineReady()
                val instance =
                    GenieModelInstance(
                        configPath = llmConfigFile.path,
                        pipelineEnabled = pipelineReady,
                    )
                if (systemInstruction != null) {
                    instance.systemPrompt = systemInstruction.toString()
                }
                model.instance = instance

                Log.d(TAG, "Genie initialized, pipeline=$pipelineReady")
                onDone("")
            } catch (e: Exception) {
                Log.e(TAG, "Genie init error", e)
                onDone(e.message ?: "Unknown error")
            }
        }
    }

    override fun resetConversation(
        model: Model,
        supportImage: Boolean,
        supportAudio: Boolean,
        systemInstruction: Contents?,
        tools: List<ToolProvider>,
        enableConversationConstrainedDecoding: Boolean,
    ) {
        val instance = model.instance as? GenieModelInstance ?: return
        instance.conversationHistory.clear()
        GenieNative.reset()
        Log.d(TAG, "Conversation reset")
    }

    override fun cleanUp(model: Model, onDone: () -> Unit) {
        GenieNative.destroy()
        model.instance = null
        onDone()
        Log.d(TAG, "Genie cleaned up")
    }

    override fun runInference(
        model: Model,
        input: String,
        resultListener: ResultListener,
        cleanUpListener: CleanUpListener,
        onError: (message: String) -> Unit,
        images: List<Bitmap>,
        audioClips: List<ByteArray>,
        coroutineScope: CoroutineScope?,
        extraContext: Map<String, String>?,
    ) {
        val instance = model.instance as? GenieModelInstance
        if (instance == null) {
            onError("Model not initialized")
            return
        }

        abortRequested = false
        val scope = coroutineScope ?: CoroutineScope(Dispatchers.IO)
        scope.launch(Dispatchers.IO) {
            try {
                val responseBuilder = StringBuilder()
                val callback =
                    object : GenieCallback {
                        override fun onToken(token: String, sentenceCode: Int) {
                            when (sentenceCode) {
                                GenieNative.SENTENCE_BEGIN,
                                GenieNative.SENTENCE_CONTINUE,
                                GenieNative.SENTENCE_COMPLETE -> {
                                    responseBuilder.append(token)
                                    resultListener(token, false, null)
                                }
                                GenieNative.SENTENCE_END -> {
                                    instance.conversationHistory.add("user" to input)
                                    instance.conversationHistory.add(
                                        "assistant" to responseBuilder.toString()
                                    )
                                    resultListener("", true, null)
                                }
                                GenieNative.SENTENCE_ABORT -> {
                                    resultListener("", true, null)
                                }
                            }
                        }
                    }

                if (images.isNotEmpty()) {
                    val bitmap = images.first()
                    Log.d(TAG, "Image query: ${bitmap.width}x${bitmap.height}")
                    runImageInference(instance, bitmap, input, callback, onError)
                } else {
                    val prompt = buildChatMLPrompt(instance, input)
                    Log.d(TAG, "Running text inference, prompt length=${prompt.length}")
                    val success = GenieNative.query(prompt, callback)
                    if (!success) {
                        onError("Genie query failed")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Inference error", e)
                onError(e.message ?: "Inference error")
            }
        }
    }

    override fun stopResponse(model: Model) {
        abortRequested = true
        GenieNative.abort()
    }

    private fun runImageInference(
        instance: GenieModelInstance,
        bitmap: Bitmap,
        input: String,
        callback: GenieCallback,
        onError: (message: String) -> Unit,
    ) {
        if (instance.pipelineEnabled && GenieNative.isPipelineReady()) {
            Log.d(TAG, "Running VLM query via Genie Pipeline ImageEncoder + Dialog")
            val prefix = buildChatMLPromptPrefix(instance)
            val suffix = "<|vision_end|>请用中文回答：$input<|im_end|>\n<|im_start|>assistant\n"
            val success =
                GenieNative.queryWithImagePixels(
                    prefix,
                    suffix,
                    bitmapToRgbByteArray(bitmap),
                    bitmap.width,
                    bitmap.height,
                    callback,
                )
            if (success) {
                return
            }
            Log.w(TAG, "Pipeline VLM query failed, falling back to ONNX VIT")
        } else {
            Log.d(TAG, "Pipeline unavailable, falling back to ONNX VIT")
        }
        runOnnxFallback(bitmap, input, callback, onError)
    }

    private fun runOnnxFallback(
        bitmap: Bitmap,
        input: String,
        callback: GenieCallback,
        onError: (message: String) -> Unit,
    ) {
        Log.d(TAG, "Running ONNX VIT fallback...")
        val embedding = OnnxVIT.encode(bitmap)
        if (embedding == null) {
            onError("VIT encoding failed")
            return
        }

        val numTokens = embedding.size / IMAGE_TOKEN_DIM
        Log.d(TAG, "VIT embedding: ${embedding.size} floats, $numTokens tokens")
        val prefix = "<|im_start|>user\n<|vision_start|>"
        val suffix = "<|vision_end|>请用中文回答：$input<|im_end|>\n<|im_start|>assistant\n"
        val success = GenieNative.queryWithEmbedding(prefix, suffix, embedding, numTokens, callback)
        if (!success) {
            onError("VLM query failed")
        }
    }

    private fun buildChatMLPrompt(instance: GenieModelInstance, userInput: String): String {
        val sb = StringBuilder(buildChatMLPromptPrefix(instance))
        sb.append(userInput)
        sb.append("<|im_end|>\n")
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }

    private fun buildChatMLPromptPrefix(instance: GenieModelInstance): String {
        val sb = StringBuilder()
        if (instance.systemPrompt.isNotEmpty()) {
            sb.append("<|im_start|>system\n${instance.systemPrompt}<|im_end|>\n")
        }
        for ((role, content) in instance.conversationHistory) {
            sb.append("<|im_start|>$role\n$content<|im_end|>\n")
        }
        sb.append("<|im_start|>user\n")
        return sb.toString()
    }

    private fun bitmapToRgbByteArray(bitmap: Bitmap): ByteArray {
        val argbBitmap =
            if (bitmap.config == Bitmap.Config.ARGB_8888) {
                bitmap
            } else {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            }
        val pixels = IntArray(argbBitmap.width * argbBitmap.height)
        argbBitmap.getPixels(
            pixels,
            0,
            argbBitmap.width,
            0,
            0,
            argbBitmap.width,
            argbBitmap.height,
        )
        val rgb = ByteArray(argbBitmap.width * argbBitmap.height * 3)
        var out = 0
        for (pixel in pixels) {
            rgb[out++] = ((pixel shr 16) and 0xFF).toByte()
            rgb[out++] = ((pixel shr 8) and 0xFF).toByte()
            rgb[out++] = (pixel and 0xFF).toByte()
        }
        return rgb
    }
}
