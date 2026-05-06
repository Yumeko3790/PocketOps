package com.google.ai.edge.gallery.customtasks.maintenance

interface GenieCallback {
    fun onToken(token: String, sentenceCode: Int)
}

object GenieNative {
    init {
        try { System.loadLibrary("QnnSystem") } catch (e: UnsatisfiedLinkError) { }
        try { System.loadLibrary("calculator") } catch (e: UnsatisfiedLinkError) { }
        try { System.loadLibrary("QnnHtp") } catch (e: UnsatisfiedLinkError) { }
        try { System.loadLibrary("QnnHtpNetRunExtensions") } catch (e: UnsatisfiedLinkError) { }
        try { System.loadLibrary("QnnHtpV81CalculatorStub") } catch (e: UnsatisfiedLinkError) { }
        try { System.loadLibrary("QnnHtpV81Stub") } catch (e: UnsatisfiedLinkError) { }
        try { System.loadLibrary("QnnHtpV81Skel") } catch (e: UnsatisfiedLinkError) { }
        try { System.loadLibrary("QnnHtpV81") } catch (e: UnsatisfiedLinkError) { }
        try { System.loadLibrary("QnnHtpPrepare") } catch (e: UnsatisfiedLinkError) { }
        try { System.loadLibrary("Genie") } catch (e: UnsatisfiedLinkError) { }
        try { System.loadLibrary("appbuilder") } catch (e: UnsatisfiedLinkError) { }
        System.loadLibrary("genie_jni")
    }

    external fun initialize(
        llmConfigJson: String,
        embeddingPath: String,
        vegModelPath: String,
    ): Boolean

    external fun query(prompt: String, callback: GenieCallback): Boolean
    external fun queryWithImage(prompt: String, imagePath: String, callback: GenieCallback): Boolean
    external fun queryWithImagePixels(
        prefixText: String,
        suffixText: String,
        pixels: ByteArray,
        width: Int,
        height: Int,
        callback: GenieCallback
    ): Boolean
    external fun queryWithEmbedding(prefixText: String, suffixText: String, embedding: FloatArray, numTokens: Int, callback: GenieCallback): Boolean
    external fun processImageAndQuery(rgbPixels: ByteArray, width: Int, height: Int, prompt: String, callback: GenieCallback): Boolean
    external fun compareLastImageEmbedding(referenceEmbedding: FloatArray): String?
    external fun isPipelineReady(): Boolean
    external fun reset(): Boolean
    external fun abort(): Boolean
    external fun destroy()

    // VEG via LibAppBuilder (NPU vision encoder)
    external fun initVEG(vegModelPath: String): Boolean
    external fun runVEG(pixelValues: FloatArray, posSin: FloatArray, posCos: FloatArray, fullAttnMask: FloatArray, windowAttnMask: FloatArray): FloatArray?
    external fun encodeImageVEG(rgbPixels: ByteArray, width: Int, height: Int): FloatArray?
    external fun destroyVEG()

    const val SENTENCE_COMPLETE = 0
    const val SENTENCE_BEGIN = 1
    const val SENTENCE_CONTINUE = 2
    const val SENTENCE_END = 3
    const val SENTENCE_ABORT = 4
}
