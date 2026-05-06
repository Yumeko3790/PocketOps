#include <jni.h>
#include <string>
#include <cstdlib>
#include <cstring>
#include <cstdio>
#include <cmath>
#include <vector>
#include <android/log.h>
#include "Genie/GenieCommon.h"
#include "Genie/GenieDialog.h"
#include "Genie/GenieNode.h"
#include "Genie/GeniePipeline.h"
#include "Genie/GenieTokenizer.h"
#include "LibAppBuilder.hpp"

#define TAG "GenieJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static JavaVM* g_jvm = nullptr;

// Dialog API (text)
static GenieDialogConfig_Handle_t g_dialogConfig = nullptr;
static GenieDialog_Handle_t g_dialog = nullptr;
static GenieTokenizer_Handle_t g_tokenizer = nullptr;
static float* g_embeddingTable = nullptr;
static uint32_t g_vocabSize = 151936;
static uint32_t g_embeddingDim = 2048;

// Pipeline API (VLM)
static GeniePipelineConfig_Handle_t g_pipelineConfig = nullptr;
static GeniePipeline_Handle_t g_pipeline = nullptr;
static GenieNode_Handle_t g_imageEncoder = nullptr;
static GenieNode_Handle_t g_textEncoder = nullptr;
static GenieNode_Handle_t g_textGenerator = nullptr;
static bool g_pipelineReady = false;

// float16 to float32 conversion
static float fp16_to_fp32(uint16_t h) {
    uint32_t sign = (h >> 15) & 1;
    uint32_t exp = (h >> 10) & 0x1f;
    uint32_t mant = h & 0x3ff;
    uint32_t f;
    if (exp == 0) {
        if (mant == 0) f = sign << 31;
        else { exp = 1; while (!(mant & 0x400)) { mant <<= 1; exp--; } mant &= 0x3ff; f = (sign << 31) | ((exp + 127 - 15) << 23) | (mant << 13); }
    } else if (exp == 31) {
        f = (sign << 31) | 0x7f800000 | (mant << 13);
    } else {
        f = (sign << 31) | ((exp + 127 - 15) << 23) | (mant << 13);
    }
    float result; memcpy(&result, &f, 4); return result;
}

static float bf16_to_fp32(uint16_t h) {
    uint32_t f = ((uint32_t)h) << 16;
    float result; memcpy(&result, &f, 4); return result;
}

static uint16_t fp32_to_fp16(float v) {
    uint32_t f; memcpy(&f, &v, 4);
    uint32_t sign = (f >> 31) & 1;
    int32_t exp = ((f >> 23) & 0xFF) - 127 + 15;
    uint32_t mant = (f >> 13) & 0x3FF;
    if (exp <= 0) return (uint16_t)(sign << 15);
    if (exp >= 31) return (uint16_t)((sign << 15) | 0x7C00);
    return (uint16_t)((sign << 15) | (exp << 10) | mant);
}

// Image embedding from VEG
static float* g_imageEmbedding = nullptr;
static size_t g_imageEmbeddingSize = 0;  // in bytes (float32)
static uint32_t g_imageTokenCount = 0;
static uint16_t* g_imageEmbeddingRaw16 = nullptr;
static size_t g_imageEmbeddingRaw16Count = 0;
static float* g_lastImageEmbedding = nullptr;
static size_t g_lastImageEmbeddingSize = 0;
static uint32_t g_lastImageTokenCount = 0;
static uint16_t* g_lastImageEmbeddingRaw16 = nullptr;
static size_t g_lastImageEmbeddingRaw16Count = 0;

static void clearLastImageEmbeddingCache() {
    if (g_lastImageEmbedding) { delete[] g_lastImageEmbedding; g_lastImageEmbedding = nullptr; }
    if (g_lastImageEmbeddingRaw16) { delete[] g_lastImageEmbeddingRaw16; g_lastImageEmbeddingRaw16 = nullptr; }
    g_lastImageEmbeddingSize = 0;
    g_lastImageTokenCount = 0;
    g_lastImageEmbeddingRaw16Count = 0;
}

struct EmbeddingSanitizeStats {
    size_t totalValues = 0;
    size_t finiteValues = 0;
    size_t nanValues = 0;
    size_t posInfValues = 0;
    size_t negInfValues = 0;
    size_t clampedValues = 0;
    float minValue = 0.0f;
    float maxValue = 0.0f;
    double absMean = 0.0;
};

static EmbeddingSanitizeStats sanitizeEmbeddingInPlace(float* data,
                                                       size_t numElements,
                                                       float clampAbs = 64.0f) {
    EmbeddingSanitizeStats stats = {};
    stats.totalValues = numElements;
    if (!data || numElements == 0) return stats;

    bool hasFinite = false;
    double absSum = 0.0;
    for (size_t i = 0; i < numElements; i++) {
        float v = data[i];
        if (std::isnan(v)) {
            stats.nanValues++;
            data[i] = 0.0f;
            continue;
        }
        if (!std::isfinite(v)) {
            if (v > 0.0f) stats.posInfValues++;
            else stats.negInfValues++;
            data[i] = 0.0f;
            continue;
        }
        if (v > clampAbs) {
            v = clampAbs;
            data[i] = v;
            stats.clampedValues++;
        } else if (v < -clampAbs) {
            v = -clampAbs;
            data[i] = v;
            stats.clampedValues++;
        }
        if (!hasFinite) {
            stats.minValue = v;
            stats.maxValue = v;
            hasFinite = true;
        } else {
            if (v < stats.minValue) stats.minValue = v;
            if (v > stats.maxValue) stats.maxValue = v;
        }
        absSum += fabs((double)v);
        stats.finiteValues++;
    }
    if (stats.finiteValues > 0) {
        stats.absMean = absSum / (double)stats.finiteValues;
    }
    return stats;
}

struct EmbeddingArrayStats {
    size_t totalValues = 0;
    size_t finiteValues = 0;
    size_t nanValues = 0;
    size_t infValues = 0;
    float minValue = 0.0f;
    float maxValue = 0.0f;
    double mean = 0.0;
    double absMean = 0.0;
    double l2Norm = 0.0;
};

static EmbeddingArrayStats summarizeEmbedding(const float* data, size_t numElements) {
    EmbeddingArrayStats stats = {};
    stats.totalValues = numElements;
    if (!data || numElements == 0) return stats;

    bool hasFinite = false;
    double sum = 0.0;
    double absSum = 0.0;
    double l2 = 0.0;
    for (size_t i = 0; i < numElements; i++) {
        float v = data[i];
        if (std::isnan(v)) {
            stats.nanValues++;
            continue;
        }
        if (!std::isfinite(v)) {
            stats.infValues++;
            continue;
        }
        if (!hasFinite) {
            stats.minValue = v;
            stats.maxValue = v;
            hasFinite = true;
        } else {
            if (v < stats.minValue) stats.minValue = v;
            if (v > stats.maxValue) stats.maxValue = v;
        }
        sum += (double)v;
        absSum += fabs((double)v);
        l2 += (double)v * (double)v;
        stats.finiteValues++;
    }
    if (stats.finiteValues > 0) {
        stats.mean = sum / (double)stats.finiteValues;
        stats.absMean = absSum / (double)stats.finiteValues;
        stats.l2Norm = sqrt(l2);
    }
    return stats;
}

static void imageEmbeddingCallback(const uint32_t* dimensions,
                                    const uint32_t rank,
                                    const size_t embeddingBufferSize,
                                    const void* embeddingBuffer,
                                    const void* userData) {
    size_t numElements = 1;
    for (uint32_t i = 0; i < rank; i++) {
        LOGI("  VEG dim[%u]=%u", i, dimensions[i]);
        numElements *= dimensions[i];
    }
    LOGI("VEG embedding: rank=%u reported=%zu elements=%zu buf=%p", rank, embeddingBufferSize, numElements, embeddingBuffer);

    if (!embeddingBuffer || numElements == 0) {
        LOGE("VEG: no embedding data");
        return;
    }

    if (g_imageEmbedding) { delete[] g_imageEmbedding; g_imageEmbedding = nullptr; }

    // Detect float16 vs float32: reported size / numElements
    size_t bytesPerElement = (embeddingBufferSize > 0) ? (embeddingBufferSize / numElements) : 2;
    bool isFp16 = (bytesPerElement == 2);
    LOGI("VEG: bytesPerElement=%zu isFp16=%d", bytesPerElement, isFp16);

    g_imageEmbedding = new float[numElements];
    if (isFp16) {
        const uint16_t* src = (const uint16_t*)embeddingBuffer;
        g_imageEmbeddingRaw16 = new uint16_t[numElements];
        memcpy(g_imageEmbeddingRaw16, src, numElements * sizeof(uint16_t));
        g_imageEmbeddingRaw16Count = numElements;
        for (size_t i = 0; i < numElements; i++) {
            g_imageEmbedding[i] = fp16_to_fp32(src[i]);
        }
    } else {
        memcpy(g_imageEmbedding, embeddingBuffer, numElements * sizeof(float));
    }
    g_imageEmbeddingSize = numElements * sizeof(float);
    EmbeddingSanitizeStats stats = sanitizeEmbeddingInPlace(g_imageEmbedding, numElements);

    // Token count: product of all dims except last (embedding dim)
    if (rank >= 2) {
        g_imageTokenCount = 1;
        for (uint32_t i = 0; i < rank - 1; i++) g_imageTokenCount *= dimensions[i];
    }
    clearLastImageEmbeddingCache();
    g_lastImageEmbedding = new float[numElements];
    memcpy(g_lastImageEmbedding, g_imageEmbedding, numElements * sizeof(float));
    g_lastImageEmbeddingSize = g_imageEmbeddingSize;
    g_lastImageTokenCount = g_imageTokenCount;
    if (g_imageEmbeddingRaw16 && g_imageEmbeddingRaw16Count == numElements) {
        g_lastImageEmbeddingRaw16 = new uint16_t[numElements];
        memcpy(g_lastImageEmbeddingRaw16, g_imageEmbeddingRaw16, numElements * sizeof(uint16_t));
        g_lastImageEmbeddingRaw16Count = g_imageEmbeddingRaw16Count;
    }
    LOGI(
        "VEG embedding captured: %u tokens, %zu bytes (fp32), converted from fp16=%d, finite=%zu/%zu nan=%zu +inf=%zu -inf=%zu clamped=%zu min=%.6f max=%.6f abs_mean=%.6f",
        g_imageTokenCount,
        g_imageEmbeddingSize,
        isFp16,
        stats.finiteValues,
        stats.totalValues,
        stats.nanValues,
        stats.posInfValues,
        stats.negInfValues,
        stats.clampedValues,
        stats.minValue,
        stats.maxValue,
        stats.absMean
    );
}

struct QueryUserData {
    JavaVM* jvm;
    JNIEnv* env;
    jobject callback;
    jmethodID onTokenMethod;
};

static void allocCallback(const size_t size, const char** data) {
    *data = new char[size];
}

static void dialogQueryCallback(const char* response,
                                 const GenieDialog_SentenceCode_t sentenceCode,
                                 const void* userData) {
    auto* ctx = (QueryUserData*)userData;
    LOGI("Dialog callback: code=%d text=%s", sentenceCode, response ? response : "(null)");
    if (!ctx || !ctx->env || !ctx->callback) return;
    jstring jR = ctx->env->NewStringUTF(response ? response : "");
    ctx->env->CallVoidMethod(ctx->callback, ctx->onTokenMethod, jR, (jint)sentenceCode);
    ctx->env->DeleteLocalRef(jR);
}

static Genie_Status_t pipelineTextCallback(const char* response,
                                            const GenieNode_TextOutput_SentenceCode_t sentenceCode,
                                            const void* userData) {
    auto* ctx = (QueryUserData*)userData;
    if (!ctx || !ctx->jvm || !ctx->callback) return GENIE_STATUS_SUCCESS;

    JNIEnv* env = nullptr;
    bool needDetach = false;
    int r = ctx->jvm->GetEnv((void**)&env, JNI_VERSION_1_6);
    if (r == JNI_EDETACHED) { ctx->jvm->AttachCurrentThread(&env, nullptr); needDetach = true; }
    else if (r != JNI_OK) return GENIE_STATUS_SUCCESS;

    LOGI("Pipeline: code=%d text=%s", sentenceCode, response ? response : "");
    jstring jR = env->NewStringUTF(response ? response : "");
    env->CallVoidMethod(ctx->callback, ctx->onTokenMethod, jR, (jint)sentenceCode);
    env->DeleteLocalRef(jR);

    if (needDetach) ctx->jvm->DetachCurrentThread();
    return GENIE_STATUS_SUCCESS;
}

static void t2eCallback(const int32_t token, void* embedding,
                         const uint32_t embeddingSize, const void* userData) {
    if (g_embeddingTable && token >= 0 && (uint32_t)token < g_vocabSize)
        memcpy(embedding, g_embeddingTable + (size_t)token * g_embeddingDim, g_embeddingDim * sizeof(float));
    else
        memset(embedding, 0, embeddingSize);
}

static bool loadEmbeddingTable(const char* path) {
    size_t expected = (size_t)g_vocabSize * g_embeddingDim * sizeof(float);
    FILE* f = fopen(path, "rb");
    if (!f) { LOGE("Cannot open: %s", path); return false; }
    fseek(f, 0, SEEK_END); size_t sz = ftell(f); fseek(f, 0, SEEK_SET);
    if (sz != expected) { fclose(f); return false; }
    g_embeddingTable = new float[g_vocabSize * g_embeddingDim];
    fread(g_embeddingTable, 1, expected, f);
    fclose(f);
    LOGI("Embedding table loaded");
    return true;
}

// LibAppBuilder for VEG (NPU vision encoder)
static LibAppBuilder* g_appBuilder = nullptr;
static bool g_vegReady = false;
static std::string g_vegModelName = "Vision";

// Pre-computed VEG input buffers (loaded from .raw files)
static std::vector<uint8_t> g_posCos;
static std::vector<uint8_t> g_posSin;
static std::vector<uint8_t> g_windowAttn;
static std::vector<uint8_t> g_fullAttn;

static bool loadRawFile(const std::string& path, std::vector<uint8_t>& out) {
    FILE* f = fopen(path.c_str(), "rb");
    if (!f) { LOGE("Cannot open raw file: %s", path.c_str()); return false; }
    fseek(f, 0, SEEK_END); size_t sz = ftell(f); fseek(f, 0, SEEK_SET);
    out.resize(sz);
    fread(out.data(), 1, sz, f);
    fclose(f);
    LOGI("Loaded raw file: %s (%zu bytes)", path.c_str(), sz);
    return true;
}

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

JNIEXPORT jboolean JNICALL
Java_com_google_ai_edge_gallery_customtasks_maintenance_GenieNative_initialize(
        JNIEnv* env, jobject, jstring llmConfigJson, jstring embeddingPath,
        jstring vegModelPath) {

    setenv("ADSP_LIBRARY_PATH", "/data/local/tmp", 1);

    const char* llmStr = env->GetStringUTFChars(llmConfigJson, nullptr);
    const char* embPath = env->GetStringUTFChars(embeddingPath, nullptr);
    const char* vegPath = env->GetStringUTFChars(vegModelPath, nullptr);

    // 1. Load embedding table
    loadEmbeddingTable(embPath);

    // 2. Initialize VEG via LibAppBuilder FIRST (before Dialog)
    // This matches GenieAPIService's initialization order
    std::string vegPathStr(vegPath);
    std::string modelDir = vegPathStr.substr(0, vegPathStr.rfind('/'));
    std::string rawDir = modelDir + "/raw";

    if (!g_appBuilder) g_appBuilder = new LibAppBuilder();
    bool vegOk = g_appBuilder->ModelInitialize(g_vegModelName, vegPathStr,
                                                "libQnnHtp.so", "libQnnSystem.so");
    LOGI("VEG LibAppBuilder init: %s", vegOk ? "SUCCESS" : "FAILED");
    g_vegReady = vegOk;

    if (vegOk) {
        // Load pre-computed .raw files for VEG inputs
        loadRawFile(rawDir + "/position_ids_cos.raw", g_posCos);
        loadRawFile(rawDir + "/position_ids_sin.raw", g_posSin);
        loadRawFile(rawDir + "/window_attention_mask.raw", g_windowAttn);
        loadRawFile(rawDir + "/full_attention_mask.raw", g_fullAttn);
        LOGI("VEG raw inputs loaded: cos=%zu sin=%zu win=%zu full=%zu",
             g_posCos.size(), g_posSin.size(), g_windowAttn.size(), g_fullAttn.size());
    }

    // 3. Create Dialog AFTER LibAppBuilder
    Genie_Status_t dS = GenieDialogConfig_createFromJson(llmStr, &g_dialogConfig);
    if (dS == GENIE_STATUS_SUCCESS) {
        dS = GenieDialog_create(g_dialogConfig, &g_dialog);
        LOGI("Dialog: %d", dS);
    }
    if (dS == GENIE_STATUS_SUCCESS) {
        GenieDialog_getTokenizer(g_dialog, &g_tokenizer);
        LOGI("Dialog+Tokenizer ready");
    } else {
        LOGE("Dialog init failed: %d", dS);
    }

    env->ReleaseStringUTFChars(llmConfigJson, llmStr);
    env->ReleaseStringUTFChars(embeddingPath, embPath);
    env->ReleaseStringUTFChars(vegModelPath, vegPath);

    LOGI("Init done: Dialog=%s VEG=%s", g_dialog ? "yes" : "no", g_vegReady ? "yes" : "no");
    return (g_dialog != nullptr) ? JNI_TRUE : JNI_FALSE;
}

// Text query: Dialog if available, else Pipeline
JNIEXPORT jboolean JNICALL
Java_com_google_ai_edge_gallery_customtasks_maintenance_GenieNative_query(
        JNIEnv* env, jobject, jstring prompt, jobject callback) {

    jclass cls = env->GetObjectClass(callback);
    jmethodID method = env->GetMethodID(cls, "onToken", "(Ljava/lang/String;I)V");
    const char* p = env->GetStringUTFChars(prompt, nullptr);

    // Dialog path (full autoregressive)
    if (g_dialog && g_tokenizer && g_embeddingTable) {
        LOGI("Text query via Dialog: %zu chars", strlen(p));
        GenieDialog_reset(g_dialog);
        QueryUserData ud = { g_jvm, env, callback, method };
        const int32_t* tids = nullptr; uint32_t ntids = 0;
        GenieTokenizer_encode(g_tokenizer, p, allocCallback, &tids, &ntids);
        env->ReleaseStringUTFChars(prompt, p);
        if (!tids) return JNI_FALSE;

        size_t embSz = (size_t)ntids * g_embeddingDim * sizeof(float);
        float* emb = new float[ntids * g_embeddingDim];
        for (uint32_t i = 0; i < ntids; i++) {
            int32_t t = tids[i];
            if (t >= 0 && (uint32_t)t < g_vocabSize)
                memcpy(emb + (size_t)i*g_embeddingDim, g_embeddingTable + (size_t)t*g_embeddingDim, g_embeddingDim*sizeof(float));
            else
                memset(emb + (size_t)i*g_embeddingDim, 0, g_embeddingDim*sizeof(float));
        }
        delete[] tids;
        Genie_Status_t s = GenieDialog_embeddingQuery(g_dialog, emb, embSz,
            GENIE_DIALOG_SENTENCE_COMPLETE, t2eCallback, dialogQueryCallback, &ud);
        delete[] emb;
        LOGI("embeddingQuery: %d", s);
        return (s == GENIE_STATUS_SUCCESS || s == GENIE_STATUS_WARNING_ABORTED) ? JNI_TRUE : JNI_FALSE;
    }

    // Pipeline fallback (1 token only, but better than nothing)
    if (g_pipelineReady && g_textEncoder) {
        LOGI("Text query via Pipeline: %zu chars", strlen(p));
        QueryUserData* ud = new QueryUserData();
        ud->jvm = g_jvm; ud->env = env; ud->callback = env->NewGlobalRef(callback); ud->onTokenMethod = method;
        GenieNode_setData(g_textEncoder, GENIE_NODE_TEXT_ENCODER_TEXT_INPUT, p, strlen(p), nullptr);
        env->ReleaseStringUTFChars(prompt, p);
        Genie_Status_t s = GeniePipeline_execute(g_pipeline, ud);
        LOGI("Pipeline text: %d", s);
        env->DeleteGlobalRef(ud->callback);
        delete ud;
        return (s == GENIE_STATUS_SUCCESS || s == GENIE_STATUS_WARNING_ABORTED) ? JNI_TRUE : JNI_FALSE;
    }

    env->ReleaseStringUTFChars(prompt, p);
    LOGE("No backend ready");
    return JNI_FALSE;
}

// VLM query: raw files -> VEG (NPU) -> embedding -> Dialog
JNIEXPORT jboolean JNICALL
Java_com_google_ai_edge_gallery_customtasks_maintenance_GenieNative_queryWithImage(
        JNIEnv* env, jobject, jstring prompt, jstring imagePath, jobject callback) {
    if (!g_dialog || !g_tokenizer || !g_embeddingTable) { LOGE("Dialog not ready"); return JNI_FALSE; }
    if (!g_pipelineReady || !g_imageEncoder || !g_textEncoder) { LOGE("Pipeline not ready"); return JNI_FALSE; }
    jclass cls = env->GetObjectClass(callback);
    jmethodID method = env->GetMethodID(cls, "onToken", "(Ljava/lang/String;I)V");
    const char* promptStr = env->GetStringUTFChars(prompt, nullptr);
    LOGI("VLM query: %zu chars", strlen(promptStr));
    GeniePipeline_reset(g_pipeline);
    GenieNode_setData(g_textEncoder, GENIE_NODE_TEXT_ENCODER_TEXT_INPUT, "<|im_start|>user\n<|vision_start|>", 33, nullptr);
    const char* base = "/data/local/tmp/qwen2.5vl3b/raw";
    const char* fnames[] = {"pixel_values.raw","position_ids_sin.raw","position_ids_cos.raw","full_attention_mask.raw","window_attention_mask.raw"};
    GenieNode_IOName_t ios[] = {GENIE_NODE_IMAGE_ENCODER_IMAGE_INPUT,GENIE_NODE_IMAGE_ENCODER_IMAGE_POS_SIN,GENIE_NODE_IMAGE_ENCODER_IMAGE_POS_COS,GENIE_NODE_IMAGE_ENCODER_IMAGE_FULL_ATTN_MASK,GENIE_NODE_IMAGE_ENCODER_IMAGE_WINDOW_ATTN_MASK};
    for (int i = 0; i < 5; i++) {
        char path[512]; snprintf(path, sizeof(path), "%s/%s", base, fnames[i]);
        FILE* f = fopen(path, "rb");
        if (f) { fseek(f,0,SEEK_END); size_t sz=ftell(f); fseek(f,0,SEEK_SET); void* d=malloc(sz); fread(d,1,sz,f); fclose(f); GenieNode_setData(g_imageEncoder, ios[i], d, sz, nullptr); free(d); }
    }
    char suffix[2048];
    snprintf(suffix, sizeof(suffix), "<|vision_end|>请用中文回答：%s<|im_end|>\n<|im_start|>assistant\n", promptStr);
    env->ReleaseStringUTFChars(prompt, promptStr);
    if (g_imageEmbedding) { delete[] g_imageEmbedding; g_imageEmbedding = nullptr; }
    g_imageEmbeddingSize = 0; g_imageTokenCount = 0;
    Genie_Status_t status = GeniePipeline_execute(g_pipeline, nullptr);
    LOGI("VEG: %d emb=%p sz=%zu tok=%u", status, g_imageEmbedding, g_imageEmbeddingSize, g_imageTokenCount);
    if (g_imageEmbedding && g_imageEmbeddingSize > 0) {
        LOGI("VEG first5: %.4f %.4f %.4f %.4f %.4f", g_imageEmbedding[0],g_imageEmbedding[1],g_imageEmbedding[2],g_imageEmbedding[3],g_imageEmbedding[4]);
        const char* prefix = "<|im_start|>user\n<|vision_start|>";
        const int32_t* preTids=nullptr; uint32_t nPre=0;
        const int32_t* sufTids=nullptr; uint32_t nSuf=0;
        GenieTokenizer_encode(g_tokenizer, prefix, allocCallback, &preTids, &nPre);
        GenieTokenizer_encode(g_tokenizer, suffix, allocCallback, &sufTids, &nSuf);
        uint32_t total = nPre + g_imageTokenCount + nSuf;
        float* combined = new float[total * g_embeddingDim]; size_t off = 0;
        for(uint32_t i=0;i<nPre;i++){int32_t t=preTids[i];float*dst=combined+off*g_embeddingDim;if(t>=0&&(uint32_t)t<g_vocabSize)memcpy(dst,g_embeddingTable+(size_t)t*g_embeddingDim,g_embeddingDim*sizeof(float));else memset(dst,0,g_embeddingDim*sizeof(float));off++;}
        memcpy(combined+off*g_embeddingDim, g_imageEmbedding, g_imageEmbeddingSize); off+=g_imageTokenCount;
        for(uint32_t i=0;i<nSuf;i++){int32_t t=sufTids[i];float*dst=combined+off*g_embeddingDim;if(t>=0&&(uint32_t)t<g_vocabSize)memcpy(dst,g_embeddingTable+(size_t)t*g_embeddingDim,g_embeddingDim*sizeof(float));else memset(dst,0,g_embeddingDim*sizeof(float));off++;}
        delete[] preTids; delete[] sufTids;
        GenieDialog_reset(g_dialog);
        QueryUserData ud = { g_jvm, env, callback, method };
        Genie_Status_t s = GenieDialog_embeddingQuery(g_dialog, combined, total*g_embeddingDim*sizeof(float), GENIE_DIALOG_SENTENCE_COMPLETE, t2eCallback, dialogQueryCallback, &ud);
        delete[] combined;
        LOGI("VLM embeddingQuery: %d", s);
        return (s==GENIE_STATUS_SUCCESS||s==GENIE_STATUS_WARNING_ABORTED)?JNI_TRUE:JNI_FALSE;
    }
    LOGE("VEG failed"); return JNI_FALSE;
}
// --- Image preprocessing for Qwen2.5-VL VEG (verified parameters) ---

static void resizeRGB(const uint8_t* src, int srcW, int srcH, uint8_t* dst, int dstW, int dstH) {
    for (int y = 0; y < dstH; y++) {
        float srcY = (float)y * srcH / dstH;
        int y0 = (int)srcY; int y1 = y0+1 < srcH ? y0+1 : y0;
        float fy = srcY - y0;
        for (int x = 0; x < dstW; x++) {
            float srcX = (float)x * srcW / dstW;
            int x0 = (int)srcX; int x1 = x0+1 < srcW ? x0+1 : x0;
            float fx = srcX - x0;
            for (int c = 0; c < 3; c++) {
                float v = (1-fy)*(1-fx)*src[(y0*srcW+x0)*3+c] + (1-fy)*fx*src[(y0*srcW+x1)*3+c]
                        + fy*(1-fx)*src[(y1*srcW+x0)*3+c] + fy*fx*src[(y1*srcW+x1)*3+c];
                dst[(y*dstW+x)*3+c] = (uint8_t)(v + 0.5f);
            }
        }
    }
}

struct VEGInputs {
    void* pixel_values;  size_t pv_size;   // float32
    void* pos_sin;       size_t ps_size;   // float32
    void* pos_cos;       size_t pc_size;   // float32
    float* full_attn;    size_t fa_size;
    float* window_attn;  size_t wa_size;
    int seq_len;  // output tokens (529)
};

static void preprocessForVEG(const uint8_t* rgb, int srcW, int srcH, VEGInputs* out) {
    const int PS = 14;       // patch_size
    const int MS = 2;        // merge_size
    const int TP = 2;        // temporal_patch_size
    const int C = 3;
    const int modelH = 644, modelW = 644;  // fixed VEG input size
    const float mean[3] = {0.48145466f, 0.4578275f, 0.40821073f};
    const float std_v[3] = {0.26862954f, 0.26130258f, 0.27577711f};
    const float THETA = 10000.0f;
    const int ROPE_DIM = 40;  // head_dim//2 = 80//2
    const int ROPE_HALF = 20; // ROPE_DIM // 2

    int grid_h = modelH / PS;  // 46
    int grid_w = modelW / PS;  // 46
    int llm_h = grid_h / MS;   // 23
    int llm_w = grid_w / MS;   // 23
    int seq_len = grid_h * grid_w;  // 2116
    out->seq_len = llm_h * llm_w;  // 529

    LOGI("VEG preprocess: %dx%d -> %dx%d, seq=%d, out_tokens=%d", srcW, srcH, modelW, modelH, seq_len, out->seq_len);

    // 1. Resize
    uint8_t* resized = (uint8_t*)malloc(modelW * modelH * 3);
    resizeRGB(rgb, srcW, srcH, resized, modelW, modelH);

    // 2. CLIP normalize + NCHW + temporal expand
    int numPx = modelW * modelH;
    float* nchw = (float*)malloc(C * numPx * sizeof(float));
    for (int c = 0; c < C; c++)
        for (int i = 0; i < numPx; i++)
            nchw[c * numPx + i] = ((float)resized[i*3+c] / 255.0f - mean[c]) / std_v[c];
    free(resized);

    // 3. HF 10-dim permute: input [batch=1, grid_t=1, TP=2, C=3, llm_h, MS, PS, llm_w, MS, PS]
    //    permute (0,1,4,7,5,8,3,2,6,9) -> [1, 1, llm_h, llm_w, MS, MS, C, TP, PS, PS]
    //    flatten to [seq_len, C*TP*PS*PS] = [2116, 1176]
    // nchw is [C=3, H=644, W=644], temporal frames are identical
    // After permute: output[lh][lw][mh][mw][c][t][ph][pw] = nchw[c][(lh*MS+mh)*PS+ph][(lw*MS+mw)*PS+pw]
    int patch_dim = C * TP * PS * PS;  // 1176
    float* pv = (float*)malloc(seq_len * patch_dim * sizeof(float));
    if (!pv) { LOGE("OOM pv"); free(nchw); return; }

    int outIdx = 0;
    for (int lh = 0; lh < llm_h; lh++)
    for (int lw = 0; lw < llm_w; lw++)
    for (int mh = 0; mh < MS; mh++)
    for (int mw = 0; mw < MS; mw++)
    for (int cc = 0; cc < C; cc++)
    for (int t = 0; t < TP; t++)
    for (int ph = 0; ph < PS; ph++)
    for (int pw = 0; pw < PS; pw++) {
        int row = (lh * MS + mh) * PS + ph;
        int col = (lw * MS + mw) * PS + pw;
        pv[outIdx++] = nchw[cc * numPx + row * modelW + col];
    }
    free(nchw);
    out->pixel_values = pv;
    out->pv_size = seq_len * patch_dim * sizeof(float);
    LOGI("pixel_values: [%d, %d] %zu bytes", seq_len, patch_dim, out->pv_size);

    // 4. Position IDs: HF merge pattern + RoPE sin/cos (float32, 40-dim)
    // pos_ids[i] = (h_pos, w_pos) with merge permute
    int* hpos = (int*)malloc(seq_len * sizeof(int));
    int* wpos = (int*)malloc(seq_len * sizeof(int));
    // HF: hpos_ids.reshape(h//MS, MS, w//MS, MS).permute(0,2,1,3).flatten()
    int idx = 0;
    for (int lh = 0; lh < llm_h; lh++)
    for (int lw = 0; lw < llm_w; lw++)
    for (int mh = 0; mh < MS; mh++)
    for (int mw = 0; mw < MS; mw++) {
        hpos[idx] = lh * MS + mh;
        wpos[idx] = lw * MS + mw;
        idx++;
    }

    // inv_freq = 1/(theta^(arange(0,40,2)/40)) -> 20 values
    float inv_freq[ROPE_HALF];
    for (int i = 0; i < ROPE_HALF; i++)
        inv_freq[i] = 1.0f / powf(THETA, (float)(2*i) / (float)ROPE_DIM);

    // freqs[pos] = pos * inv_freq -> [max_grid, 20]
    int max_grid = grid_h > grid_w ? grid_h : grid_w;
    float* freqs_table = (float*)malloc(max_grid * ROPE_HALF * sizeof(float));
    for (int p = 0; p < max_grid; p++)
        for (int d = 0; d < ROPE_HALF; d++)
            freqs_table[p * ROPE_HALF + d] = (float)p * inv_freq[d];

    // rotary_pos_emb = [h_freqs(20), w_freqs(20)] = [seq_len, 40]
    float* rope = (float*)malloc(seq_len * ROPE_DIM * sizeof(float));
    for (int s = 0; s < seq_len; s++) {
        memcpy(rope + s * ROPE_DIM, freqs_table + hpos[s] * ROPE_HALF, ROPE_HALF * sizeof(float));
        memcpy(rope + s * ROPE_DIM + ROPE_HALF, freqs_table + wpos[s] * ROPE_HALF, ROPE_HALF * sizeof(float));
    }
    free(freqs_table); free(hpos); free(wpos);

    // Window index reordering
    int vws = 4; // vit_merger_window_size
    int pad_h = (vws - llm_h % vws) % vws;
    int pad_w = (vws - llm_w % vws) % vws;
    int nwh = (llm_h + pad_h) / vws;
    int nww = (llm_w + pad_w) / vws;
    int smu = MS * MS; // spatial_merge_unit = 4

    // Build padded index grid
    int padded_h = llm_h + pad_h, padded_w = llm_w + pad_w;
    int* grid_idx = (int*)malloc(padded_h * padded_w * sizeof(int));
    for (int i = 0; i < padded_h * padded_w; i++) grid_idx[i] = -100;
    for (int i = 0; i < llm_h; i++)
        for (int j = 0; j < llm_w; j++)
            grid_idx[i * padded_w + j] = i * llm_w + j;

    // Extract window_index (valid indices only)
    int* window_index = (int*)malloc(llm_h * llm_w * sizeof(int));
    int wi_count = 0;
    for (int wh = 0; wh < nwh; wh++)
    for (int ww = 0; ww < nww; ww++)
    for (int i = 0; i < vws; i++)
    for (int j = 0; j < vws; j++) {
        int gi = wh * vws + i, gj = ww * vws + j;
        int v = grid_idx[gi * padded_w + gj];
        if (v != -100) window_index[wi_count++] = v;
    }
    free(grid_idx);

    // Apply window reordering: rope[seq_len//smu, smu, 40] -> rope[window_index, smu, 40]
    float* rope_reordered = (float*)malloc(seq_len * ROPE_DIM * sizeof(float));
    for (int i = 0; i < wi_count; i++)
        memcpy(rope_reordered + i * smu * ROPE_DIM,
               rope + window_index[i] * smu * ROPE_DIM,
               smu * ROPE_DIM * sizeof(float));
    free(window_index); free(rope);

    // Compute sin/cos (float32, 40-dim, no duplicate)
    float* ps = (float*)malloc(seq_len * ROPE_DIM * sizeof(float));
    float* pc = (float*)malloc(seq_len * ROPE_DIM * sizeof(float));
    for (int i = 0; i < seq_len * ROPE_DIM; i++) {
        ps[i] = sinf(rope_reordered[i]);
        pc[i] = cosf(rope_reordered[i]);
    }
    free(rope_reordered);
    out->pos_sin = ps; out->ps_size = seq_len * ROPE_DIM * sizeof(float);
    out->pos_cos = pc; out->pc_size = seq_len * ROPE_DIM * sizeof(float);
    LOGI("pos_sin/cos: [%d, %d] %zu bytes each (float32)", seq_len, ROPE_DIM, out->ps_size);
    LOGI("pos_sin first 10: %.4f %.4f %.4f %.4f %.4f %.4f %.4f %.4f %.4f %.4f",
         ps[0],ps[1],ps[2],ps[3],ps[4],ps[5],ps[6],ps[7],ps[8],ps[9]);
    LOGI("pos_sin[40..49]: %.4f %.4f %.4f %.4f %.4f %.4f %.4f %.4f %.4f %.4f",
         ps[40],ps[41],ps[42],ps[43],ps[44],ps[45],ps[46],ps[47],ps[48],ps[49]);

    // 5. Attention masks [seq_len, seq_len] float32
    int64_t maskN = (int64_t)seq_len * seq_len;
    out->full_attn = (float*)calloc(maskN, sizeof(float));
    out->fa_size = maskN * sizeof(float);

    out->window_attn = (float*)malloc(maskN * sizeof(float));
    for (int64_t i = 0; i < maskN; i++) out->window_attn[i] = -1000.0f;
    // Build cu_window_seqlens
    std::vector<int> cu = {0};
    for (int wh = 0; wh < nwh; wh++)
        for (int ww = 0; ww < nww; ww++) {
            int cnt = 0;
            for (int i = 0; i < vws; i++) {
                if (wh*vws+i >= llm_h) continue;
                for (int j = 0; j < vws; j++)
                    if (ww*vws+j < llm_w) cnt++;
            }
            cu.push_back(cu.back() + cnt * smu);
        }
    for (size_t w = 1; w < cu.size(); w++)
        for (int r = cu[w-1]; r < cu[w] && r < seq_len; r++)
            for (int c = cu[w-1]; c < cu[w] && c < seq_len; c++)
                out->window_attn[r * seq_len + c] = 0.0f;
    out->wa_size = maskN * sizeof(float);
    LOGI("attention masks: [%d, %d] %zu bytes each", seq_len, seq_len, out->fa_size);
}

static void freeVEGInputs(VEGInputs* v) {
    if (v->pixel_values) { free(v->pixel_values); v->pixel_values = nullptr; }
    if (v->pos_sin) { free(v->pos_sin); v->pos_sin = nullptr; }
    if (v->pos_cos) { free(v->pos_cos); v->pos_cos = nullptr; }
    if (v->full_attn) { free(v->full_attn); v->full_attn = nullptr; }
    if (v->window_attn) { free(v->window_attn); v->window_attn = nullptr; }
}

// VLM query with actual image pixels
JNIEXPORT jboolean JNICALL
Java_com_google_ai_edge_gallery_customtasks_maintenance_GenieNative_queryWithImagePixels(
        JNIEnv* env, jobject, jstring prefixText, jstring suffixText, jbyteArray pixels, jint width, jint height, jobject callback) {
    if (!g_pipelineReady || !g_imageEncoder || !g_dialog || !g_tokenizer || !g_embeddingTable) {
        LOGE("Direct VLM path unavailable: pipeline=%d img=%d dialog=%d tokenizer=%d emb=%d",
             g_pipelineReady ? 1 : 0,
             g_imageEncoder ? 1 : 0,
             g_dialog ? 1 : 0,
             g_tokenizer ? 1 : 0,
             g_embeddingTable ? 1 : 0);
        return JNI_FALSE;
    }

    jclass cls = env->GetObjectClass(callback);
    jmethodID method = env->GetMethodID(cls, "onToken", "(Ljava/lang/String;I)V");

    const char* prefixStr = env->GetStringUTFChars(prefixText, nullptr);
    const char* suffixStr = env->GetStringUTFChars(suffixText, nullptr);
    LOGI("VLM pixel query: prefix=%zu chars, suffix=%zu chars, img=%dx%d", strlen(prefixStr), strlen(suffixStr), width, height);
    clearLastImageEmbeddingCache();

    // Preprocess image on-device
    jbyte* pixelData = env->GetByteArrayElements(pixels, nullptr);
    jsize pixelLen = env->GetArrayLength(pixels);
    LOGI("Pixel data: len=%d, first RGB=[%u,%u,%u], expected=%dx%dx3=%d",
         pixelLen, (uint8_t)pixelData[0], (uint8_t)pixelData[1], (uint8_t)pixelData[2],
         width, height, width*height*3);
    VEGInputs vegIn = {};
    preprocessForVEG((const uint8_t*)pixelData, width, height, &vegIn);
    env->ReleaseByteArrayElements(pixels, pixelData, JNI_ABORT);

    // Log first few pixel_values after preprocessing
    float* pvCheck = (float*)vegIn.pixel_values;
    LOGI("Preprocessed pv first 5: %.4f %.4f %.4f %.4f %.4f",
         pvCheck[0], pvCheck[1], pvCheck[2], pvCheck[3], pvCheck[4]);

    // Reset Pipeline BEFORE setting new data
    GeniePipeline_reset(g_pipeline);

    // Set all 5 VEG inputs. The image-only pipeline exists purely to obtain the
    // NPU image embedding through the callback above.
    GenieNode_setData(g_imageEncoder, GENIE_NODE_IMAGE_ENCODER_IMAGE_INPUT, vegIn.pixel_values, vegIn.pv_size, nullptr);
    GenieNode_setData(g_imageEncoder, GENIE_NODE_IMAGE_ENCODER_IMAGE_POS_SIN, vegIn.pos_sin, vegIn.ps_size, nullptr);
    GenieNode_setData(g_imageEncoder, GENIE_NODE_IMAGE_ENCODER_IMAGE_POS_COS, vegIn.pos_cos, vegIn.pc_size, nullptr);
    GenieNode_setData(g_imageEncoder, GENIE_NODE_IMAGE_ENCODER_IMAGE_FULL_ATTN_MASK, vegIn.full_attn, vegIn.fa_size, nullptr);
    GenieNode_setData(g_imageEncoder, GENIE_NODE_IMAGE_ENCODER_IMAGE_WINDOW_ATTN_MASK, vegIn.window_attn, vegIn.wa_size, nullptr);
    freeVEGInputs(&vegIn);

    if (g_imageEmbedding) { delete[] g_imageEmbedding; g_imageEmbedding = nullptr; }
    if (g_imageEmbeddingRaw16) { delete[] g_imageEmbeddingRaw16; g_imageEmbeddingRaw16 = nullptr; }
    g_imageEmbeddingRaw16Count = 0;
    g_imageEmbeddingSize = 0; g_imageTokenCount = 0;

    Genie_Status_t status = GeniePipeline_execute(g_pipeline, nullptr);
    LOGI("Pipeline VLM execute: %d, embedding=%p size=%zu tokens=%u", status, g_imageEmbedding, g_imageEmbeddingSize, g_imageTokenCount);
    if (g_imageEmbedding && g_imageEmbeddingSize > 0) {
        LOGI("VEG embedding first 5: %.4f %.4f %.4f %.4f %.4f",
             g_imageEmbedding[0], g_imageEmbedding[1], g_imageEmbedding[2], g_imageEmbedding[3], g_imageEmbedding[4]);
    }

    if (g_imageEmbedding && g_imageEmbeddingSize > 0 && g_dialog && g_tokenizer && g_embeddingTable) {
        LOGI("Building combined embedding for Dialog...");
        const int32_t* prefixTids = nullptr; uint32_t nPrefixTids = 0;
        GenieTokenizer_encode(g_tokenizer, prefixStr, allocCallback, &prefixTids, &nPrefixTids);
        const int32_t* suffixTids = nullptr; uint32_t nSuffixTids = 0;
        GenieTokenizer_encode(g_tokenizer, suffixStr, allocCallback, &suffixTids, &nSuffixTids);

        uint32_t totalTokens = nPrefixTids + g_imageTokenCount + nSuffixTids;
        LOGI("Combined: prefix=%u + img=%u + suffix=%u = %u", nPrefixTids, g_imageTokenCount, nSuffixTids, totalTokens);

        size_t totalEmbSz = (size_t)totalTokens * g_embeddingDim * sizeof(float);
        float* combinedEmb = new float[totalTokens * g_embeddingDim];
        size_t off = 0;
        for (uint32_t i = 0; i < nPrefixTids; i++) {
            int32_t t = prefixTids[i]; float* dst = combinedEmb + off*g_embeddingDim;
            if (t>=0 && (uint32_t)t<g_vocabSize) memcpy(dst, g_embeddingTable+(size_t)t*g_embeddingDim, g_embeddingDim*sizeof(float));
            else memset(dst, 0, g_embeddingDim*sizeof(float));
            off++;
        }
        memcpy(combinedEmb + off*g_embeddingDim, g_imageEmbedding, g_imageEmbeddingSize);
        off += g_imageTokenCount;
        for (uint32_t i = 0; i < nSuffixTids; i++) {
            int32_t t = suffixTids[i]; float* dst = combinedEmb + off*g_embeddingDim;
            if (t>=0 && (uint32_t)t<g_vocabSize) memcpy(dst, g_embeddingTable+(size_t)t*g_embeddingDim, g_embeddingDim*sizeof(float));
            else memset(dst, 0, g_embeddingDim*sizeof(float));
            off++;
        }
        delete[] prefixTids; delete[] suffixTids;
        env->ReleaseStringUTFChars(prefixText, prefixStr);
        env->ReleaseStringUTFChars(suffixText, suffixStr);

        GenieDialog_reset(g_dialog);
        QueryUserData ud2 = { g_jvm, env, callback, method };
        Genie_Status_t s = GenieDialog_embeddingQuery(g_dialog, combinedEmb, totalEmbSz,
            GENIE_DIALOG_SENTENCE_COMPLETE, t2eCallback, dialogQueryCallback, &ud2);
        delete[] combinedEmb;
        LOGI("VLM Dialog embeddingQuery: %d", s);
        return (s == GENIE_STATUS_SUCCESS || s == GENIE_STATUS_WARNING_ABORTED) ? JNI_TRUE : JNI_FALSE;
    }

    env->ReleaseStringUTFChars(prefixText, prefixStr);
    env->ReleaseStringUTFChars(suffixText, suffixStr);
    LOGI("Direct VLM path did not produce usable image embedding");
    return JNI_FALSE;
}


// VLM query with pre-computed VIT embedding from PyTorch Mobile
JNIEXPORT jboolean JNICALL
Java_com_google_ai_edge_gallery_customtasks_maintenance_GenieNative_queryWithEmbedding(
        JNIEnv* env, jobject, jstring prefixText, jstring suffixText, jfloatArray embedding, jint numTokens, jobject callback) {
    if (!g_dialog || !g_tokenizer || !g_embeddingTable) {
        LOGE("Dialog not ready"); return JNI_FALSE;
    }
    jclass cls = env->GetObjectClass(callback);
    jmethodID method = env->GetMethodID(cls, "onToken", "(Ljava/lang/String;I)V");
    const char* preStr = env->GetStringUTFChars(prefixText, nullptr);
    const char* sufStr = env->GetStringUTFChars(suffixText, nullptr);
    LOGI("VLM embedding query: prefix=%zu chars, suffix=%zu chars, %d img tokens", strlen(preStr), strlen(sufStr), numTokens);

    jfloat* embData = env->GetFloatArrayElements(embedding, nullptr);
    jsize embLen = env->GetArrayLength(embedding);
    size_t embSize = embLen * sizeof(float);
    LOGI("Embedding: %d floats, first 5: %.4f %.4f %.4f %.4f %.4f",
         embLen, embData[0], embData[1], embData[2], embData[3], embData[4]);

    const int32_t* preTids = nullptr; uint32_t nPre = 0;
    const int32_t* sufTids = nullptr; uint32_t nSuf = 0;
    GenieTokenizer_encode(g_tokenizer, preStr, allocCallback, &preTids, &nPre);
    GenieTokenizer_encode(g_tokenizer, sufStr, allocCallback, &sufTids, &nSuf);
    env->ReleaseStringUTFChars(prefixText, preStr);
    env->ReleaseStringUTFChars(suffixText, sufStr);

    uint32_t total = nPre + (uint32_t)numTokens + nSuf;
    LOGI("Combined: %u + %d + %u = %u tokens", nPre, numTokens, nSuf, total);

    float* combined = new float[total * g_embeddingDim];
    size_t off = 0;
    for (uint32_t i = 0; i < nPre; i++) {
        int32_t t = preTids[i]; float* dst = combined + off * g_embeddingDim;
        if (t >= 0 && (uint32_t)t < g_vocabSize) memcpy(dst, g_embeddingTable + (size_t)t * g_embeddingDim, g_embeddingDim * sizeof(float));
        else memset(dst, 0, g_embeddingDim * sizeof(float));
        off++;
    }
    memcpy(combined + off * g_embeddingDim, embData, embSize);
    off += numTokens;
    for (uint32_t i = 0; i < nSuf; i++) {
        int32_t t = sufTids[i]; float* dst = combined + off * g_embeddingDim;
        if (t >= 0 && (uint32_t)t < g_vocabSize) memcpy(dst, g_embeddingTable + (size_t)t * g_embeddingDim, g_embeddingDim * sizeof(float));
        else memset(dst, 0, g_embeddingDim * sizeof(float));
        off++;
    }
    delete[] preTids; delete[] sufTids;
    env->ReleaseFloatArrayElements(embedding, embData, JNI_ABORT);

    GenieDialog_reset(g_dialog);
    QueryUserData ud = { g_jvm, env, callback, method };
    Genie_Status_t s = GenieDialog_embeddingQuery(g_dialog, combined, total * g_embeddingDim * sizeof(float),
        GENIE_DIALOG_SENTENCE_COMPLETE, t2eCallback, dialogQueryCallback, &ud);
    delete[] combined;
    LOGI("VLM embeddingQuery: %d", s);
    return (s == GENIE_STATUS_SUCCESS || s == GENIE_STATUS_WARNING_ABORTED) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_google_ai_edge_gallery_customtasks_maintenance_GenieNative_compareLastImageEmbedding(
        JNIEnv* env, jobject, jfloatArray referenceEmbedding) {
    if (!g_lastImageEmbedding || g_lastImageEmbeddingSize == 0 || g_embeddingDim == 0) {
        return env->NewStringUTF("VEG vs ONNX: unavailable, no cached pipeline embedding");
    }

    jfloat* refData = env->GetFloatArrayElements(referenceEmbedding, nullptr);
    jsize refLen = env->GetArrayLength(referenceEmbedding);
    size_t vegLen = g_lastImageEmbeddingSize / sizeof(float);
    size_t sharedLen = vegLen < (size_t)refLen ? vegLen : (size_t)refLen;
    size_t sharedTokens = sharedLen / g_embeddingDim;
    size_t sharedElements = sharedTokens * g_embeddingDim;

    EmbeddingArrayStats vegStats = summarizeEmbedding(g_lastImageEmbedding, vegLen);
    EmbeddingArrayStats refStats = summarizeEmbedding(refData, (size_t)refLen);

    double dot = 0.0;
    double vegSq = 0.0;
    double refSq = 0.0;
    double absDiff = 0.0;
    double sqDiff = 0.0;
    double maxAbsDiff = 0.0;
    size_t comparedValues = 0;

    double tokenCosSum = 0.0;
    size_t tokenCosCount = 0;
    double firstTokenCos = 0.0;
    double midTokenCos = 0.0;
    double lastTokenCos = 0.0;

    for (size_t i = 0; i < sharedElements; i++) {
        double a = (double)g_lastImageEmbedding[i];
        double b = (double)refData[i];
        if (!std::isfinite(a) || !std::isfinite(b)) continue;
        dot += a * b;
        vegSq += a * a;
        refSq += b * b;
        double d = fabs(a - b);
        absDiff += d;
        sqDiff += d * d;
        if (d > maxAbsDiff) maxAbsDiff = d;
        comparedValues++;
    }

    for (size_t token = 0; token < sharedTokens; token++) {
        double tokenDot = 0.0;
        double tokenVegSq = 0.0;
        double tokenRefSq = 0.0;
        size_t base = token * g_embeddingDim;
        for (uint32_t j = 0; j < g_embeddingDim; j++) {
            double a = (double)g_lastImageEmbedding[base + j];
            double b = (double)refData[base + j];
            if (!std::isfinite(a) || !std::isfinite(b)) continue;
            tokenDot += a * b;
            tokenVegSq += a * a;
            tokenRefSq += b * b;
        }
        double denom = sqrt(tokenVegSq) * sqrt(tokenRefSq);
        double tokenCos = denom > 0.0 ? (tokenDot / denom) : 0.0;
        tokenCosSum += tokenCos;
        tokenCosCount++;
        if (token == 0) firstTokenCos = tokenCos;
        if (token == sharedTokens / 2) midTokenCos = tokenCos;
        if (token + 1 == sharedTokens) lastTokenCos = tokenCos;
    }

    double globalCos = (vegSq > 0.0 && refSq > 0.0) ? (dot / (sqrt(vegSq) * sqrt(refSq))) : 0.0;
    double mae = comparedValues > 0 ? (absDiff / (double)comparedValues) : 0.0;
    double rmse = comparedValues > 0 ? sqrt(sqDiff / (double)comparedValues) : 0.0;
    double avgTokenCos = tokenCosCount > 0 ? (tokenCosSum / (double)tokenCosCount) : 0.0;

    double bf16Cos = 0.0;
    double bf16Mae = 0.0;
    double int16Cos = 0.0;
    double int16Scale = 0.0;
    double int16Mae = 0.0;
    double int16Rmse = 0.0;
    if (g_lastImageEmbeddingRaw16 && g_lastImageEmbeddingRaw16Count >= sharedElements) {
        double bfDot = 0.0;
        double bfSq = 0.0;
        double bfRefSq = 0.0;
        double bfAbsDiff = 0.0;
        size_t bfCount = 0;

        double qDotRef = 0.0;
        double qSq = 0.0;
        double qRefSq = 0.0;
        for (size_t i = 0; i < sharedElements; i++) {
            double ref = (double)refData[i];
            if (!std::isfinite(ref)) continue;

            double bf = (double)bf16_to_fp32(g_lastImageEmbeddingRaw16[i]);
            if (std::isfinite(bf)) {
                bfDot += bf * ref;
                bfSq += bf * bf;
                bfRefSq += ref * ref;
                bfAbsDiff += fabs(bf - ref);
                bfCount++;
            }

            double q = (double)((int16_t)g_lastImageEmbeddingRaw16[i]);
            qDotRef += q * ref;
            qSq += q * q;
            qRefSq += ref * ref;
        }
        bf16Cos = (bfSq > 0.0 && bfRefSq > 0.0) ? (bfDot / (sqrt(bfSq) * sqrt(bfRefSq))) : 0.0;
        bf16Mae = bfCount > 0 ? (bfAbsDiff / (double)bfCount) : 0.0;

        int16Cos = (qSq > 0.0 && qRefSq > 0.0) ? (qDotRef / (sqrt(qSq) * sqrt(qRefSq))) : 0.0;
        int16Scale = qSq > 0.0 ? (qDotRef / qSq) : 0.0;
        double scaledAbsDiff = 0.0;
        double scaledSqDiff = 0.0;
        for (size_t i = 0; i < sharedElements; i++) {
            double ref = (double)refData[i];
            if (!std::isfinite(ref)) continue;
            double scaled = (double)((int16_t)g_lastImageEmbeddingRaw16[i]) * int16Scale;
            double d = fabs(scaled - ref);
            scaledAbsDiff += d;
            scaledSqDiff += d * d;
        }
        int16Mae = sharedElements > 0 ? (scaledAbsDiff / (double)sharedElements) : 0.0;
        int16Rmse = sharedElements > 0 ? sqrt(scaledSqDiff / (double)sharedElements) : 0.0;
    }

    char summary[1200];
    snprintf(
        summary,
        sizeof(summary),
        "VEG vs ONNX: veg_len=%zu ref_len=%d shared_tokens=%zu dim=%u fp16_cos=%.6f avg_token_cos=%.6f first_token_cos=%.6f mid_token_cos=%.6f last_token_cos=%.6f fp16_mae=%.6f fp16_rmse=%.6f max_abs_diff=%.6f bf16_cos=%.6f bf16_mae=%.6f int16_cos=%.6f int16_scale=%.9f int16_mae=%.6f int16_rmse=%.6f veg_mean=%.6f veg_abs_mean=%.6f ref_mean=%.6f ref_abs_mean=%.6f veg_min=%.6f veg_max=%.6f ref_min=%.6f ref_max=%.6f veg_finite=%zu/%zu ref_finite=%zu/%zu raw16=%zu first_raw=0x%04x",
        vegLen,
        refLen,
        sharedTokens,
        g_embeddingDim,
        globalCos,
        avgTokenCos,
        firstTokenCos,
        midTokenCos,
        lastTokenCos,
        mae,
        rmse,
        maxAbsDiff,
        bf16Cos,
        bf16Mae,
        int16Cos,
        int16Scale,
        int16Mae,
        int16Rmse,
        vegStats.mean,
        vegStats.absMean,
        refStats.mean,
        refStats.absMean,
        vegStats.minValue,
        vegStats.maxValue,
        refStats.minValue,
        refStats.maxValue,
        vegStats.finiteValues,
        vegStats.totalValues,
        refStats.finiteValues,
        refStats.totalValues,
        g_lastImageEmbeddingRaw16Count,
        g_lastImageEmbeddingRaw16Count > 0 ? g_lastImageEmbeddingRaw16[0] : 0
    );
    LOGI("%s", summary);
    clearLastImageEmbeddingCache();
    env->ReleaseFloatArrayElements(referenceEmbedding, refData, JNI_ABORT);
    return env->NewStringUTF(summary);
}
JNIEXPORT jboolean JNICALL
Java_com_google_ai_edge_gallery_customtasks_maintenance_GenieNative_isPipelineReady(JNIEnv*, jobject) {
    return (g_pipelineReady && g_imageEncoder) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_google_ai_edge_gallery_customtasks_maintenance_GenieNative_reset(JNIEnv*, jobject) {
    if (g_dialog) GenieDialog_reset(g_dialog);
    if (g_pipelineReady) GeniePipeline_reset(g_pipeline);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_google_ai_edge_gallery_customtasks_maintenance_GenieNative_abort(JNIEnv*, jobject) {
    if (g_dialog) GenieDialog_signal(g_dialog, GENIE_DIALOG_ACTION_ABORT);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_google_ai_edge_gallery_customtasks_maintenance_GenieNative_destroy(JNIEnv*, jobject) {
    if (g_imageEncoder) { GenieNode_free(g_imageEncoder); g_imageEncoder = nullptr; }
    if (g_textEncoder) { GenieNode_free(g_textEncoder); g_textEncoder = nullptr; }
    if (g_textGenerator) { GenieNode_free(g_textGenerator); g_textGenerator = nullptr; }
    if (g_pipeline) { GeniePipeline_free(g_pipeline); g_pipeline = nullptr; }
    if (g_pipelineConfig) { GeniePipelineConfig_free(g_pipelineConfig); g_pipelineConfig = nullptr; }
    g_pipelineReady = false;
    g_tokenizer = nullptr;
    if (g_dialog) { GenieDialog_free(g_dialog); g_dialog = nullptr; }
    if (g_dialogConfig) { GenieDialogConfig_free(g_dialogConfig); g_dialogConfig = nullptr; }
    if (g_embeddingTable) { delete[] g_embeddingTable; g_embeddingTable = nullptr; }
    if (g_imageEmbedding) { delete[] g_imageEmbedding; g_imageEmbedding = nullptr; }
    if (g_imageEmbeddingRaw16) { delete[] g_imageEmbeddingRaw16; g_imageEmbeddingRaw16 = nullptr; }
    g_imageEmbeddingSize = 0;
    g_imageTokenCount = 0;
    g_imageEmbeddingRaw16Count = 0;
    LOGI("Destroyed");
}

// ==================== VEG via LibAppBuilder (NPU) ====================

JNIEXPORT jboolean JNICALL
Java_com_google_ai_edge_gallery_customtasks_maintenance_GenieNative_initVEG(
        JNIEnv* env, jobject, jstring vegModelPath) {
    const char* path = env->GetStringUTFChars(vegModelPath, nullptr);
    LOGI("initVEG: %s", path);

    if (!g_appBuilder) g_appBuilder = new LibAppBuilder();

    std::string modelPath(path);
    std::string backendLib = "libQnnHtp.so";
    std::string systemLib = "libQnnSystem.so";
    env->ReleaseStringUTFChars(vegModelPath, path);

    bool ok = g_appBuilder->ModelInitialize(g_vegModelName, modelPath, backendLib, systemLib);
    LOGI("initVEG: %s", ok ? "SUCCESS" : "FAILED");
    g_vegReady = ok;

    if (ok) {
        auto shapes = g_appBuilder->getInputShapes(g_vegModelName);
        auto dtypes = g_appBuilder->getInputDataType(g_vegModelName);
        auto names = g_appBuilder->getInputName(g_vegModelName);
        LOGI("VEG inputs: %zu", shapes.size());
        for (size_t i = 0; i < shapes.size(); i++) {
            std::string shapeStr;
            for (auto d : shapes[i]) shapeStr += std::to_string(d) + ",";
            LOGI("  [%zu] %s shape=[%s] dtype=%s", i,
                 i < names.size() ? names[i].c_str() : "?",
                 shapeStr.c_str(),
                 i < dtypes.size() ? dtypes[i].c_str() : "?");
        }
        auto outShapes = g_appBuilder->getOutputShapes(g_vegModelName);
        auto outDtypes = g_appBuilder->getOutputDataType(g_vegModelName);
        auto outNames = g_appBuilder->getOutputName(g_vegModelName);
        LOGI("VEG outputs: %zu", outShapes.size());
        for (size_t i = 0; i < outShapes.size(); i++) {
            std::string shapeStr;
            for (auto d : outShapes[i]) shapeStr += std::to_string(d) + ",";
            LOGI("  [%zu] %s shape=[%s] dtype=%s", i,
                 i < outNames.size() ? outNames[i].c_str() : "?",
                 shapeStr.c_str(),
                 i < outDtypes.size() ? outDtypes[i].c_str() : "?");
        }
    }
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jfloatArray JNICALL
Java_com_google_ai_edge_gallery_customtasks_maintenance_GenieNative_runVEG(
        JNIEnv* env, jobject,
        jfloatArray pixelValues, jfloatArray posSin, jfloatArray posCos,
        jfloatArray fullAttnMask, jfloatArray windowAttnMask) {
    if (!g_vegReady || !g_appBuilder) {
        LOGE("VEG not ready");
        return nullptr;
    }

    // Get input arrays (float32 from Java)
    jsize pvLen = env->GetArrayLength(pixelValues);
    jsize psLen = env->GetArrayLength(posSin);
    jsize pcLen = env->GetArrayLength(posCos);
    jsize faLen = env->GetArrayLength(fullAttnMask);
    jsize waLen = env->GetArrayLength(windowAttnMask);

    jfloat* pvF = env->GetFloatArrayElements(pixelValues, nullptr);
    jfloat* psF = env->GetFloatArrayElements(posSin, nullptr);
    jfloat* pcF = env->GetFloatArrayElements(posCos, nullptr);
    jfloat* faF = env->GetFloatArrayElements(fullAttnMask, nullptr);
    jfloat* waF = env->GetFloatArrayElements(windowAttnMask, nullptr);

    // Convert float32 → float16 for VEG (ufp16 inputs)
    LOGI("runVEG: converting inputs to fp16 (pv=%d ps=%d pc=%d fa=%d wa=%d)", pvLen, psLen, pcLen, faLen, waLen);
    uint16_t* pvH = new uint16_t[pvLen];
    uint16_t* psH = new uint16_t[psLen];
    uint16_t* pcH = new uint16_t[pcLen];
    uint16_t* faH = new uint16_t[faLen];
    uint16_t* waH = new uint16_t[waLen];
    for (jsize i = 0; i < pvLen; i++) pvH[i] = fp32_to_fp16(pvF[i]);
    for (jsize i = 0; i < psLen; i++) psH[i] = fp32_to_fp16(psF[i]);
    for (jsize i = 0; i < pcLen; i++) pcH[i] = fp32_to_fp16(pcF[i]);
    for (jsize i = 0; i < faLen; i++) faH[i] = fp32_to_fp16(faF[i]);
    for (jsize i = 0; i < waLen; i++) waH[i] = fp32_to_fp16(waF[i]);

    env->ReleaseFloatArrayElements(pixelValues, pvF, JNI_ABORT);
    env->ReleaseFloatArrayElements(posSin, psF, JNI_ABORT);
    env->ReleaseFloatArrayElements(posCos, pcF, JNI_ABORT);
    env->ReleaseFloatArrayElements(fullAttnMask, faF, JNI_ABORT);
    env->ReleaseFloatArrayElements(windowAttnMask, waF, JNI_ABORT);

    // VEG input order: pixel_values, pos_cos, pos_sin, window_attn, full_attn
    std::vector<uint8_t*> inputs = {
        (uint8_t*)pvH, (uint8_t*)pcH, (uint8_t*)psH, (uint8_t*)waH, (uint8_t*)faH
    };

    std::vector<uint8_t*> outputs;
    std::vector<size_t> outputSizes;
    std::string perfProfile = "burst";

    LOGI("runVEG: executing on NPU...");
    auto start = std::chrono::steady_clock::now();
    bool ok = g_appBuilder->ModelInference(g_vegModelName, inputs, outputs, outputSizes, perfProfile);
    auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now() - start).count();
    LOGI("runVEG: %s in %lldms, outputs=%zu", ok ? "SUCCESS" : "FAILED", (long long)elapsed, outputs.size());

    delete[] pvH; delete[] psH; delete[] pcH; delete[] faH; delete[] waH;

    if (!ok || outputs.empty() || outputSizes.empty()) {
        LOGE("VEG inference failed");
        return nullptr;
    }

    // Output is float16 vision_embedding [529, 2048] → convert to float32
    size_t numFp16 = outputSizes[0] / sizeof(uint16_t);
    const uint16_t* outH = (const uint16_t*)outputs[0];
    LOGI("VEG output: %zu fp16 values (%zu bytes)", numFp16, outputSizes[0]);

    float* embF32 = new float[numFp16];
    for (size_t i = 0; i < numFp16; i++) embF32[i] = fp16_to_fp32(outH[i]);

    // Stats
    double absSum = 0;
    for (size_t i = 0; i < numFp16; i++) absSum += fabs(embF32[i]);
    LOGI("VEG output: %zu floats, abs_mean=%.4f, first 5: %.4f %.4f %.4f %.4f %.4f",
         numFp16, absSum / numFp16, embF32[0], embF32[1], embF32[2], embF32[3], embF32[4]);

    jfloatArray result = env->NewFloatArray(numFp16);
    env->SetFloatArrayRegion(result, 0, numFp16, embF32);
    delete[] embF32;
    return result;
}

// All-in-one: preprocess bitmap + run VEG via mini Pipeline (NPU) → return float32 embedding
JNIEXPORT jfloatArray JNICALL
Java_com_google_ai_edge_gallery_customtasks_maintenance_GenieNative_encodeImageVEG(
        JNIEnv* env, jobject, jbyteArray rgbPixels, jint width, jint height) {
    if (!g_pipelineReady || !g_imageEncoder || !g_pipeline) {
        LOGE("VEG Pipeline not ready");
        return nullptr;
    }

    jbyte* pixels = env->GetByteArrayElements(rgbPixels, nullptr);
    LOGI("encodeImageVEG: %dx%d via Pipeline", width, height);

    // Preprocess: resize 644x644 + CLIP normalize + permute + RoPE + attention masks
    VEGInputs vegIn = {};
    preprocessForVEG((const uint8_t*)pixels, width, height, &vegIn);
    env->ReleaseByteArrayElements(rgbPixels, pixels, JNI_ABORT);

    // Set all 5 VEG inputs on ImageEncoder node
    GeniePipeline_reset(g_pipeline);
    GenieNode_setData(g_imageEncoder, GENIE_NODE_IMAGE_ENCODER_IMAGE_INPUT,
                      vegIn.pixel_values, vegIn.pv_size, nullptr);
    GenieNode_setData(g_imageEncoder, GENIE_NODE_IMAGE_ENCODER_IMAGE_POS_SIN,
                      vegIn.pos_sin, vegIn.ps_size, nullptr);
    GenieNode_setData(g_imageEncoder, GENIE_NODE_IMAGE_ENCODER_IMAGE_POS_COS,
                      vegIn.pos_cos, vegIn.pc_size, nullptr);
    GenieNode_setData(g_imageEncoder, GENIE_NODE_IMAGE_ENCODER_IMAGE_FULL_ATTN_MASK,
                      vegIn.full_attn, vegIn.fa_size, nullptr);
    GenieNode_setData(g_imageEncoder, GENIE_NODE_IMAGE_ENCODER_IMAGE_WINDOW_ATTN_MASK,
                      vegIn.window_attn, vegIn.wa_size, nullptr);
    freeVEGInputs(&vegIn);

    // Clear previous embedding
    if (g_imageEmbedding) { delete[] g_imageEmbedding; g_imageEmbedding = nullptr; }
    g_imageEmbeddingSize = 0; g_imageTokenCount = 0;

    // Execute Pipeline → embedding callback captures VEG output
    LOGI("encodeImageVEG: executing Pipeline...");
    auto start = std::chrono::steady_clock::now();
    Genie_Status_t s = GeniePipeline_execute(g_pipeline, nullptr);
    auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now() - start).count();
    LOGI("encodeImageVEG: Pipeline execute=%d in %lldms, embedding=%p size=%zu tokens=%u",
         s, (long long)elapsed, g_imageEmbedding, g_imageEmbeddingSize, g_imageTokenCount);

    if (!g_imageEmbedding || g_imageEmbeddingSize == 0) {
        LOGE("VEG: no embedding captured from callback");
        return nullptr;
    }

    // g_imageEmbedding is already float32 (converted in imageEmbeddingCallback)
    size_t numFloats = g_imageEmbeddingSize / sizeof(float);
    double absSum = 0;
    for (size_t i = 0; i < numFloats; i++) absSum += fabs(g_imageEmbedding[i]);
    LOGI("VEG embedding: %zu floats, abs_mean=%.4f, first 5: %.4f %.4f %.4f %.4f %.4f",
         numFloats, absSum / numFloats,
         g_imageEmbedding[0], g_imageEmbedding[1], g_imageEmbedding[2],
         g_imageEmbedding[3], g_imageEmbedding[4]);

    jfloatArray result = env->NewFloatArray(numFloats);
    env->SetFloatArrayRegion(result, 0, numFloats, g_imageEmbedding);
    return result;
}

JNIEXPORT void JNICALL
Java_com_google_ai_edge_gallery_customtasks_maintenance_GenieNative_destroyVEG(
        JNIEnv* env, jobject) {
    if (g_appBuilder && g_vegReady) {
        g_appBuilder->ModelDestroy(g_vegModelName);
        g_vegReady = false;
    }
    LOGI("VEG destroyed");
}

// ==================== processImageAndQuery: VEG + Dialog in one call ====================

static void bilinearResize(const uint8_t* src, int srcW, int srcH,
                           uint8_t* dst, int dstW, int dstH, int channels) {
    for (int y = 0; y < dstH; y++) {
        float srcY = (float)y * srcH / dstH;
        int y0 = (int)srcY; int y1 = y0 + 1 < srcH ? y0 + 1 : y0;
        float fy = srcY - y0;
        for (int x = 0; x < dstW; x++) {
            float srcX = (float)x * srcW / dstW;
            int x0 = (int)srcX; int x1 = x0 + 1 < srcW ? x0 + 1 : x0;
            float fx = srcX - x0;
            for (int c = 0; c < channels; c++) {
                float v = (1-fy)*(1-fx)*src[(y0*srcW+x0)*channels+c]
                        + (1-fy)*fx*src[(y0*srcW+x1)*channels+c]
                        + fy*(1-fx)*src[(y1*srcW+x0)*channels+c]
                        + fy*fx*src[(y1*srcW+x1)*channels+c];
                dst[(y*dstW+x)*channels+c] = (uint8_t)(v + 0.5f);
            }
        }
    }
}

JNIEXPORT jboolean JNICALL
Java_com_google_ai_edge_gallery_customtasks_maintenance_GenieNative_processImageAndQuery(
        JNIEnv* env, jobject,
        jbyteArray rgbPixels, jint width, jint height,
        jstring promptText, jobject callback) {

    if (!g_dialog || !g_tokenizer || !g_embeddingTable) {
        LOGE("processImageAndQuery: Dialog not ready");
        return JNI_FALSE;
    }
    if (!g_vegReady || !g_appBuilder) {
        LOGE("processImageAndQuery: VEG not ready");
        return JNI_FALSE;
    }
    if (g_posCos.empty() || g_posSin.empty() || g_windowAttn.empty() || g_fullAttn.empty()) {
        LOGE("processImageAndQuery: raw files not loaded");
        return JNI_FALSE;
    }

    jclass cls = env->GetObjectClass(callback);
    jmethodID method = env->GetMethodID(cls, "onToken", "(Ljava/lang/String;I)V");
    const char* prompt = env->GetStringUTFChars(promptText, nullptr);

    // ---- Step 1: Image preprocessing (resize to 644x644, CLIP normalize, patch flatten) ----
    // smart_resize aligns to PATCH_SIZE*MERGE_SIZE=28. 640 -> 644 (nearest multiple of 28 is 616 or 644)
    // Pre-computed .raw files are for 644x644 (grid=46x46, seq=2116)
    const int TARGET_H = 644, TARGET_W = 644;
    const int PATCH = 14, MERGE = 2, T = 2, C = 3;
    const float MEAN[] = {0.48145466f, 0.4578275f, 0.40821073f};
    const float STD[] = {0.26862954f, 0.26130258f, 0.27577711f};

    jsize rgbLen = env->GetArrayLength(rgbPixels);
    jbyte* rgbData = env->GetByteArrayElements(rgbPixels, nullptr);

    // Resize to 640x640
    std::vector<uint8_t> resized(TARGET_H * TARGET_W * C);
    bilinearResize((const uint8_t*)rgbData, width, height,
                   resized.data(), TARGET_W, TARGET_H, C);
    env->ReleaseByteArrayElements(rgbPixels, rgbData, JNI_ABORT);

    // CLIP normalize + patch flatten (matching qwen25_image_processor.hpp)
    int grid_h = TARGET_H / PATCH;  // 640/14 ≈ 45 (but smart_resize ensures alignment)
    int grid_w = TARGET_W / PATCH;
    int grid_h_outer = grid_h / MERGE;
    int grid_w_outer = grid_w / MERGE;
    int seq_len = grid_h * grid_w;
    int patch_dim = C * T * PATCH * PATCH;  // 3*2*14*14 = 1176

    // Normalize to float and duplicate for T=2
    std::vector<float> normalized(C * T * TARGET_H * TARGET_W);
    for (int y = 0; y < TARGET_H; y++) {
        for (int x = 0; x < TARGET_W; x++) {
            for (int c = 0; c < C; c++) {
                float v = ((float)(resized[(y*TARGET_W+x)*C+c] & 0xFF) / 255.0f - MEAN[c]) / STD[c];
                // T=2: duplicate same frame
                normalized[((c*T+0)*TARGET_H + y)*TARGET_W + x] = v;
                normalized[((c*T+1)*TARGET_H + y)*TARGET_W + x] = v;
            }
        }
    }

    // Patch flatten: [grid_h_outer, grid_w_outer, MERGE, MERGE] -> [seq_len, patch_dim]
    // Order: ho, wo, mh, mw -> inner: c, t, ph, pw
    std::vector<float> pixel_values_f32(seq_len * patch_dim);
    for (int ho = 0; ho < grid_h_outer; ho++) {
        for (int wo = 0; wo < grid_w_outer; wo++) {
            for (int mh = 0; mh < MERGE; mh++) {
                for (int mw = 0; mw < MERGE; mw++) {
                    int row_idx = ((ho * grid_w_outer + wo) * MERGE + mh) * MERGE + mw;
                    int gy = ho * MERGE + mh;
                    int gx = wo * MERGE + mw;
                    int out_off = row_idx * patch_dim;
                    int idx = 0;
                    for (int c = 0; c < C; c++) {
                        for (int t = 0; t < T; t++) {
                            for (int ph = 0; ph < PATCH; ph++) {
                                for (int pw = 0; pw < PATCH; pw++) {
                                    int py = gy * PATCH + ph;
                                    int px = gx * PATCH + pw;
                                    pixel_values_f32[out_off + idx] = normalized[((c*T+t)*TARGET_H + py)*TARGET_W + px];
                                    idx++;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Convert pixel_values to fp16
    size_t pvCount = pixel_values_f32.size();
    std::vector<uint16_t> pixel_values_fp16(pvCount);
    for (size_t i = 0; i < pvCount; i++) {
        pixel_values_fp16[i] = fp32_to_fp16(pixel_values_f32[i]);
    }
    pixel_values_f32.clear();

    LOGI("VEG preprocess: %dx%d -> %dx%d, seq=%d, patch_dim=%d, fp16 pixels=%zu",
         width, height, TARGET_W, TARGET_H, seq_len, patch_dim, pvCount);

    // ---- Step 2: VEG inference via LibAppBuilder ----
    std::vector<uint8_t*> inputBuffers = {
        reinterpret_cast<uint8_t*>(pixel_values_fp16.data()),
        g_posCos.data(),
        g_posSin.data(),
        g_windowAttn.data(),
        g_fullAttn.data()
    };
    std::vector<uint8_t*> outputBuffers;
    std::vector<size_t> outputSize;
    std::string perfProfile = "burst";

    LOGI("VEG inference start");
    bool vegOk = g_appBuilder->ModelInference(g_vegModelName, inputBuffers,
                                               outputBuffers, outputSize, perfProfile);
    LOGI("VEG inference: %s, outputs=%zu", vegOk ? "OK" : "FAILED", outputBuffers.size());

    if (!vegOk || outputBuffers.empty() || outputSize.empty()) {
        LOGE("VEG inference failed");
        env->ReleaseStringUTFChars(promptText, prompt);
        return JNI_FALSE;
    }

    // VEG output is fp16, convert to fp32
    size_t vegOutBytes = outputSize[0];
    size_t vegOutCount = vegOutBytes / sizeof(uint16_t);
    const uint16_t* vegOutFp16 = reinterpret_cast<const uint16_t*>(outputBuffers[0]);
    std::vector<float> vegEmbedding(vegOutCount);
    for (size_t i = 0; i < vegOutCount; i++) {
        vegEmbedding[i] = fp16_to_fp32(vegOutFp16[i]);
    }
    free(outputBuffers[0]);

    size_t N_feat = vegOutCount / g_embeddingDim;  // should be 529 for 3B
    LOGI("VEG output: %zu floats, N_feat=%zu, dim=%u", vegOutCount, N_feat, g_embeddingDim);

    // ---- Step 3: MergeEmbedding (text + vision) ----
    // Build ChatML prompt with image placeholder
    std::string sysPrompt = "你是PocketOps工业车辆诊断助手，擅长识别工业设备图片。请仔细观察图片内容，用中文直接回答用户的问题。";
    std::string fullPrompt = "<|im_start|>system\n" + sysPrompt + "<|im_end|>\n"
                           + "<|im_start|>user\n<|vision_start|><|image_pad|><|vision_end|>请用中文回答。"
                           + std::string(prompt) + "<|im_end|>\n<|im_start|>assistant\n";
    env->ReleaseStringUTFChars(promptText, prompt);

    // Tokenize
    const int32_t* tokenIds = nullptr;
    uint32_t numTokens = 0;
    GenieTokenizer_encode(g_tokenizer, fullPrompt.c_str(), allocCallback, &tokenIds, &numTokens);
    if (!tokenIds || numTokens == 0) {
        LOGE("Tokenize failed");
        return JNI_FALSE;
    }
    LOGI("Tokenized: %u tokens", numTokens);

    // Find image_pad token (151655) position and count
    const int32_t IMAGE_PAD_TOKEN = 151655;
    int padPos = -1;
    int padCount = 0;
    for (uint32_t i = 0; i < numTokens; i++) {
        if (tokenIds[i] == IMAGE_PAD_TOKEN) {
            if (padPos < 0) padPos = (int)i;
            padCount++;
        }
    }
    LOGI("Image pad token 151655: pos=%d count=%d, N_feat=%zu", padPos, padCount, N_feat);

    // Build merged embedding
    size_t newTokenCount;
    std::vector<float> mergedEmb;

    if (padCount == 1 && N_feat > 1) {
        // Expand: 1 placeholder -> N_feat image tokens
        newTokenCount = numTokens - 1 + N_feat;
        mergedEmb.resize(newTokenCount * g_embeddingDim);

        // Left segment (before pad)
        for (int i = 0; i < padPos; i++) {
            int32_t t = tokenIds[i];
            if (t >= 0 && (uint32_t)t < g_vocabSize)
                memcpy(&mergedEmb[i * g_embeddingDim], &g_embeddingTable[t * g_embeddingDim], g_embeddingDim * sizeof(float));
            else
                memset(&mergedEmb[i * g_embeddingDim], 0, g_embeddingDim * sizeof(float));
        }
        // Image embedding (N_feat tokens)
        memcpy(&mergedEmb[padPos * g_embeddingDim], vegEmbedding.data(), N_feat * g_embeddingDim * sizeof(float));
        // Right segment (after pad)
        for (uint32_t i = padPos + 1; i < numTokens; i++) {
            size_t outIdx = padPos + N_feat + (i - padPos - 1);
            int32_t t = tokenIds[i];
            if (t >= 0 && (uint32_t)t < g_vocabSize)
                memcpy(&mergedEmb[outIdx * g_embeddingDim], &g_embeddingTable[t * g_embeddingDim], g_embeddingDim * sizeof(float));
            else
                memset(&mergedEmb[outIdx * g_embeddingDim], 0, g_embeddingDim * sizeof(float));
        }
    } else {
        // Direct replacement or no image
        newTokenCount = numTokens;
        mergedEmb.resize(newTokenCount * g_embeddingDim);
        size_t imgIdx = 0;
        for (uint32_t i = 0; i < numTokens; i++) {
            if (tokenIds[i] == IMAGE_PAD_TOKEN && imgIdx < N_feat) {
                memcpy(&mergedEmb[i * g_embeddingDim], &vegEmbedding[imgIdx * g_embeddingDim], g_embeddingDim * sizeof(float));
                imgIdx++;
            } else {
                int32_t t = tokenIds[i];
                if (t >= 0 && (uint32_t)t < g_vocabSize)
                    memcpy(&mergedEmb[i * g_embeddingDim], &g_embeddingTable[t * g_embeddingDim], g_embeddingDim * sizeof(float));
                else
                    memset(&mergedEmb[i * g_embeddingDim], 0, g_embeddingDim * sizeof(float));
            }
        }
    }
    delete[] tokenIds;
    LOGI("Merged embedding: %zu tokens x %u dim", newTokenCount, g_embeddingDim);

    // ---- Step 4: Dialog embeddingQuery ----
    GenieDialog_reset(g_dialog);
    QueryUserData ud = { g_jvm, env, callback, method };
    size_t totalBytes = newTokenCount * g_embeddingDim * sizeof(float);

    Genie_Status_t rs = GenieDialog_embeddingQuery(
        g_dialog,
        reinterpret_cast<uint8_t*>(mergedEmb.data()),
        totalBytes,
        GENIE_DIALOG_SENTENCE_COMPLETE,
        t2eCallback,
        dialogQueryCallback,
        &ud
    );
    LOGI("embeddingQuery: status=%d", rs);

    return (rs == GENIE_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
