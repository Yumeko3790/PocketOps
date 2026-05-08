package com.pocketops.app

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val TAG = "PocketOps"
private const val MODEL_ROOT = "/sdcard/GenieModels"

// Brand colors
private val Primary = Color(0xFF1a2744)
private val PrimaryDeep = Color(0xFF0A1628)
private val PrimaryMid = Color(0xFF1A3A5C)
private val Accent = Color(0xFF2196F3)
private val AccentSoft = Color(0xFFE6F3FF)
private val WarningColor = Color(0xFFFF6D00)
private val WarningSoft = Color(0xFFFFF3E8)
private val SuccessColor = Color(0xFF2E9E5B)
private val SuccessSoft = Color(0xFFEAF7EF)
private val DangerColor = Color(0xFFF44336)
private val DangerSoft = Color(0xFFFFECEA)
private val SurfaceMuted = Color(0xFFF7F9FC)
private val SurfaceSoft = Color(0xFFF0F4F9)
private val BorderSoft = Color(0xFFE3E8EF)
private val TextMain = Color(0xFF1A1F2B)
private val TextBody = Color(0xFF333A45)
private val TextMuted = Color(0xFF6B7280)
private val TextSubtle = Color(0xFF9CA3AF)
private val CanvasBg = Color(0xFFEDF2F7)
private val GraphBg = Color(0xFF161B2F)
private val GraphCard = Color(0xFF252547)
private val GraphAccent = Color(0xFF82B1FF)
private val AiBubble = Color(0xFFFFFFFF)
private val BgColor = CanvasBg

private enum class RemoteBootstrapState {
    NOT_CONFIGURED,
    CONNECTING,
    CONNECTED,
    CACHED_FALLBACK,
    BUNDLED_FALLBACK,
}

private fun runtimeStatusLine(genieReady: Boolean, remoteState: RemoteBootstrapState): String {
    if (!genieReady) return "系统初始化中"
    return when (remoteState) {
        RemoteBootstrapState.CONNECTED -> "就绪 · 电脑服务"
        RemoteBootstrapState.CACHED_FALLBACK -> "就绪 · 缓存知识库"
        RemoteBootstrapState.BUNDLED_FALLBACK -> "就绪 · 本地知识库"
        RemoteBootstrapState.CONNECTING -> "就绪 · 检查电脑服务"
        RemoteBootstrapState.NOT_CONFIGURED -> "就绪 · 本地知识库"
    }
}

private fun runtimeStatusColor(genieReady: Boolean, remoteState: RemoteBootstrapState): Color {
    if (!genieReady) return TextMuted
    return when (remoteState) {
        RemoteBootstrapState.CONNECTED -> SuccessColor
        RemoteBootstrapState.CACHED_FALLBACK,
        RemoteBootstrapState.BUNDLED_FALLBACK,
        RemoteBootstrapState.NOT_CONFIGURED,
        RemoteBootstrapState.CONNECTING
        -> Accent
    }
}

private const val SYSTEM_PROMPT = """你是工业叉车诊断助手。请严格按JSON格式输出：{"equipment":"设备","symptom":"症状","severity":"高/中/低","causes":[{"name":"原因","probability":80}],"parts":[{"name":"备件","spec":"规格","stock":"充足"}],"steps":[{"title":"步骤","duration":"耗时","tool":"工具"}]}。只输出JSON，不要其他文字。"""

private const val IMAGE_SYSTEM_PROMPT = """你是PocketOps工业车辆诊断助手，擅长识别工业设备图片。请仔细观察图片内容，只能使用简体中文回答用户的问题。回答要专业、详细、准确，包括你在图片中看到的所有相关信息（文字、数字、标签、设备状态、部件名称等）。除图片中原始英文标签外，不要输出英文句子。"""
private const val VIDEO_SYSTEM_PROMPT = """你是PocketOps工业车辆诊断助手，当前收到的是从同一段工业设备视频中抽取的多帧拼图。请结合时间顺序综合分析设备状态、异常动作、故障线索、仪表/标签信息和可能的风险点。必须只用简体中文直接回答，不要输出英文分析句子。画面中的原始英文标签、设备编号、故障码和单位可以保留原文，但需要用中文解释其含义。"""

private val SYMPTOM_KEYWORDS = listOf("举升缓慢", "无法启动", "转向沉重", "发动机过热", "异响", "液压油泄漏", "制动失灵", "门架倾斜")

private data class BluetoothFaultCode(
    val code: String,
    val description: String,
    val relatedSymptoms: List<String> = emptyList(),
)

private data class BluetoothParameter(
    val name: String,
    val value: String,
    val status: String,
    val relatedSymptoms: List<String> = emptyList(),
)

private data class BluetoothDiagnosticPayload(
    val equipmentLabel: String,
    val deviceName: String,
    val faultCodes: List<BluetoothFaultCode>,
    val parameters: List<BluetoothParameter>,
)

private fun sampleBluetoothDiagnosticPayload(): BluetoothDiagnosticPayload {
    return BluetoothDiagnosticPayload(
        equipmentLabel = "3号叉车",
        deviceName = "CPD20-XD2",
        faultCodes = listOf(
            BluetoothFaultCode("P0134", "O2传感器电路异常", listOf("发动机过热")),
            BluetoothFaultCode("P0121", "油门位置传感器故障", listOf("无法启动")),
            BluetoothFaultCode("P0171", "燃油系统过稀", listOf("发动机过热")),
        ),
        parameters = listOf(
            BluetoothParameter("电池电压", "9.2V", "偏低", listOf("无法启动")),
            BluetoothParameter("发动机温度", "75°C", "正常"),
            BluetoothParameter("液压压力", "0.8bar", "异常低", listOf("举升缓慢")),
            BluetoothParameter("转速", "1200rpm", "正常"),
            BluetoothParameter("节气门开度", "45%", "异常", listOf("无法启动")),
        ),
    )
}

private fun BluetoothDiagnosticPayload.knowledgeSymptomHints(): List<String> {
    return (faultCodes.flatMap { it.relatedSymptoms } + parameters.flatMap { it.relatedSymptoms })
        .filter { it in SYMPTOM_KEYWORDS }
        .distinct()
}

private fun BluetoothParameter.isAbnormal(): Boolean {
    return status != "正常"
}

private fun BluetoothDiagnosticPayload.toKnowledgeGraphPrompt(): String {
    val symptomHints = knowledgeSymptomHints()
    return buildString {
        append("蓝牙读取数据：")
        append("知识库匹配设备：$equipmentLabel")
        if (deviceName.isNotBlank()) append("，蓝牙设备：$deviceName")
        append("。")
        if (faultCodes.isNotEmpty()) {
            append("故障码：")
            append(faultCodes.joinToString("、") { "${it.code}(${it.description})" })
            append("。")
        }
        if (parameters.isNotEmpty()) {
            append("参数：")
            append(parameters.joinToString("、") { "${it.name}${it.value}(${it.status})" })
            append("。")
        }
        if (symptomHints.isNotEmpty()) {
            append("知识库症状候选：")
            append(symptomHints.joinToString("、"))
            append("。")
        }
        append("请优先结合知识库症状候选、故障码和异常参数分析故障原因，并给出维修方案。")
    }
}

private fun severityColors(severity: String): Pair<Color, Color> {
    return when {
        severity.contains("高") || severity.contains("紧急") ->
            DangerSoft to DangerColor
        severity.contains("中") || severity.contains("警") ->
            WarningSoft to WarningColor
        else -> SuccessSoft to SuccessColor
    }
}

@Composable
private fun PocketOpsBadge(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    cornerRadius: Dp = 12.dp,
    iconSize: Dp = 18.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.linearGradient(
                    listOf(Accent, Color(0xFF72C1FF)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.VerifiedUser,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun StatusChip(text: String, containerColor: Color, contentColor: Color) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ToolbarAction(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceMuted),
    ) {
        Icon(icon, contentDescription, tint = TextMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun LoadingStageCard(
    icon: ImageVector,
    title: String,
    detail: String,
    statusText: String,
    containerColor: Color,
    accentColor: Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, BorderSoft),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
                if (detail.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(detail, fontSize = 12.sp, color = TextMuted, lineHeight = 18.sp)
                }
            }
            StatusChip(statusText, accentColor.copy(alpha = 0.12f), accentColor)
        }
    }
}

private data class HttpTextResponse(
    val code: Int,
    val body: String,
)

private fun HttpURLConnection.readTextResponse(): HttpTextResponse {
    val code = responseCode
    val stream = if (code in 200..299) inputStream else errorStream
    val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    return HttpTextResponse(code = code, body = body)
}

private fun HttpURLConnection.readErrorBody(): String {
    return errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
}

private fun buildHttpErrorMessage(prefix: String, code: Int, body: String): String {
    val detail = body.trim().replace('\n', ' ').take(200)
    return if (detail.isBlank()) {
        "$prefix\uff08\u72b6\u6001\u7801 $code\uff09"
    } else {
        "$prefix\uff08\u72b6\u6001\u7801 $code\uff09\uff1a$detail"
    }
}

private fun isMostlyEnglishAnswer(text: String): Boolean {
    val englishLetters = text.count { it in 'A'..'Z' || it in 'a'..'z' }
    val chineseChars = text.count { it in '\u4e00'..'\u9fff' }
    return englishLetters >= 40 && englishLetters > chineseChars * 2
}

private fun rewriteVlmAnswerToChinese(rawText: String): String {
    if (!isMostlyEnglishAnswer(rawText)) return rawText
    return try {
        clearGenieChatState()
        val reqJson = org.json.JSONObject().apply {
            put("model", "qwen2.5vl-3b-8850-2.42")
            put("stream", false)
            put("size", 4096)
            put("temp", 0.0)
            put("top_k", 1)
            put("top_p", 1.0)
            put("messages", org.json.JSONArray().apply {
                put(
                    org.json.JSONObject()
                        .put("role", "system")
                        .put("content", "你是专业的工业车辆诊断报告改写助手。只输出简体中文，不要补充原文没有的信息。")
                )
                put(
                    org.json.JSONObject()
                        .put("role", "user")
                        .put(
                            "content",
                            "请把下面的视频诊断结果改写为简体中文。保留设备编号、故障码、型号、单位和画面中原始英文标签，但用中文说明含义；不要输出英文分析句子。\n\n$rawText",
                        )
                )
            })
        }
        val conn = (java.net.URL("http://127.0.0.1:8910/v1/chat/completions").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 10000
            readTimeout = 120000
        }
        conn.outputStream.use { it.write(reqJson.toString().toByteArray(Charsets.UTF_8)) }
        val response = try { conn.readTextResponse() } finally { conn.disconnect() }
        if (response.code !in 200..299) {
            Log.w(TAG, buildHttpErrorMessage("视频结果中文改写失败", response.code, response.body))
            rawText
        } else {
            org.json.JSONObject(response.body)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .takeIf { it.isNotBlank() }
                ?: rawText
        }
    } catch (e: Exception) {
        Log.w(TAG, "Video answer Chinese rewrite failed", e)
        rawText
    }
}

private fun isGenieApiServiceReady(body: String): Boolean {
    if (body.isBlank()) return false
    return try {
        val root = org.json.JSONObject(body)
        when {
            root.has("is_loaded") -> root.optBoolean("is_loaded", false)
            root.has("data") -> {
                val models = root.optJSONArray("data") ?: return false
                (0 until models.length()).any { index ->
                    val model = models.optJSONObject(index) ?: return@any false
                    if (model.has("is_loaded")) {
                        model.optBoolean("is_loaded", false)
                    } else {
                        model.optString("id").isNotBlank()
                    }
                }
            }
            else -> false
        }
    } catch (_: Exception) {
        false
    }
}

private fun hasModelDirectoryAccess(): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
}

private fun createManageModelDirectoryAccessIntent(context: Context): Intent {
    val packageUri = Uri.fromParts("package", context.packageName, null)
    val appSpecificIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri)
    return if (appSpecificIntent.resolveActivity(context.packageManager) != null) {
        appSpecificIntent
    } else {
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
    }
}

private fun clearGenieChatState() {
    try {
        val conn = (java.net.URL("http://127.0.0.1:8910/clear").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 2000; readTimeout = 2000; doOutput = true
        }
        conn.outputStream.use { it.write("{}".toByteArray()) }
        conn.responseCode
        conn.disconnect()
    } catch (_: Exception) {}
}

private data class ExtractedVideoFrames(
    val contactSheet: Bitmap,
    val frameCount: Int,
    val durationMs: Long,
)

private fun getDisplayName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            return cursor.getString(nameIndex).orEmpty().ifBlank { uri.lastPathSegment ?: "\u5df2\u9009\u89c6\u9891\u6587\u4ef6" }
        }
    }
    return uri.lastPathSegment ?: "\u5df2\u9009\u89c6\u9891\u6587\u4ef6"
}

private fun formatVideoTimestamp(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

private fun extractVideoFrames(context: Context, uri: Uri, maxFrames: Int = 6): ExtractedVideoFrames? {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        val durationMs =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val sourceWidth =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1280
        val sourceHeight =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
        val requestedFrameCount = when {
            durationMs >= 15_000L -> maxFrames
            durationMs >= 6_000L -> 4
            durationMs > 0L -> 3
            else -> 1
        }

        val timestampsMs =
            if (requestedFrameCount <= 1) {
                listOf((durationMs / 2).coerceAtLeast(0L))
            } else {
                (0 until requestedFrameCount).map { index ->
                    val progress = (index + 0.5f) / requestedFrameCount
                    (durationMs * progress).toLong().coerceAtLeast(0L)
                }
            }

        val aspectRatio =
            if (sourceWidth > 0 && sourceHeight > 0) {
                sourceWidth.toFloat() / sourceHeight.toFloat()
            } else {
                16f / 9f
            }
        val cellWidth = 360
        val cellHeight = (cellWidth / aspectRatio).toInt().coerceIn(180, 280)
        val sampledFrames =
            timestampsMs.mapNotNull { timestampMs ->
                retriever.getScaledFrameAtTime(
                    timestampMs * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    cellWidth,
                    cellHeight,
                )?.let { frame ->
                    timestampMs to if (frame.config == Bitmap.Config.ARGB_8888) frame else frame.copy(Bitmap.Config.ARGB_8888, false)
                }
            }

        if (sampledFrames.isEmpty()) return null

        val columns = if (sampledFrames.size == 1) 1 else 2
        val rows = ceil(sampledFrames.size / columns.toFloat()).toInt()
        val spacing = 16
        val headerHeight = 44
        val sheetWidth = columns * cellWidth + (columns + 1) * spacing
        val sheetHeight = headerHeight + rows * cellHeight + (rows + 1) * spacing
        val sheetBitmap = Bitmap.createBitmap(sheetWidth, sheetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheetBitmap)
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
        val frameLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.DKGRAY
            textSize = 20f
            isFakeBoldText = true
        }
        val timestampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 18f
            isFakeBoldText = true
        }
        val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor("#B3212744") }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        canvas.drawRect(0f, 0f, sheetWidth.toFloat(), sheetHeight.toFloat(), backgroundPaint)
        canvas.drawText("视频关键帧拼图 · ${sampledFrames.size}帧", spacing.toFloat(), 28f, frameLabelPaint)

        sampledFrames.forEachIndexed { index, (timestampMs, frame) ->
            val row = index / columns
            val column = index % columns
            val left = spacing + column * (cellWidth + spacing)
            val top = headerHeight + spacing + row * (cellHeight + spacing)
            val destination = Rect(left, top, left + cellWidth, top + cellHeight)
            canvas.drawBitmap(frame, null, destination, null)
            canvas.drawRect(destination, borderPaint)

            val label = formatVideoTimestamp(timestampMs)
            val chipPadding = 10f
            val chipHeight = 28f
            val chipWidth = timestampPaint.measureText(label) + chipPadding * 2
            val chipLeft = left + 10f
            val chipTop = top + 10f
            canvas.drawRoundRect(
                chipLeft,
                chipTop,
                chipLeft + chipWidth,
                chipTop + chipHeight,
                14f,
                14f,
                chipPaint,
            )
            canvas.drawText(label, chipLeft + chipPadding, chipTop + 20f, timestampPaint)

        }

        sampledFrames.forEach { (_, frame) -> frame.recycle() }

        return ExtractedVideoFrames(
            contactSheet = sheetBitmap,
            frameCount = sampledFrames.size,
            durationMs = durationMs,
        )
    } catch (e: Exception) {
        Log.e(TAG, "Failed to extract video frames", e)
        return null
    } finally {
        retriever.release()
    }
}

data class LlmReport(
    val equipment: String, val location: String, val symptom: String, val severity: String,
    val causes: List<Pair<String, Int>>,
    val parts: List<Triple<String, String, String>>,
    val steps: List<Triple<String, String, String>>,
    val personnel: List<Triple<String, String, String>>,
    val workOrders: List<Triple<String, String, String>>,
)

fun parseLlmReport(text: String): LlmReport? {
    try {
        val json = text.let {
            val start = it.indexOf('{')
            val end = it.lastIndexOf('}')
            if (start >= 0 && end > start) it.substring(start, end + 1) else return null
        }
        val obj = org.json.JSONObject(json)
        val causes = mutableListOf<Pair<String, Int>>()
        obj.optJSONArray("causes")?.let { arr ->
            for (i in 0 until arr.length()) {
                val c = arr.getJSONObject(i)
                causes.add(c.optString("name") to c.optInt("probability", 0))
            }
        }
        val parts = mutableListOf<Triple<String, String, String>>()
        obj.optJSONArray("parts")?.let { arr ->
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                parts.add(Triple(p.optString("name"), p.optString("spec"), p.optString("stock")))
            }
        }
        val steps = mutableListOf<Triple<String, String, String>>()
        obj.optJSONArray("steps")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                steps.add(Triple(s.optString("title"), s.optString("duration"), s.optString("tool")))
            }
        }
        val personnel = mutableListOf<Triple<String, String, String>>()
        obj.optJSONArray("personnel")?.let { arr ->
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                personnel.add(Triple(p.optString("name"), p.optString("skill"), p.optString("experience")))
            }
        }
        val workOrders = mutableListOf<Triple<String, String, String>>()
        obj.optJSONArray("workOrders")?.let { arr ->
            for (i in 0 until arr.length()) {
                val w = arr.getJSONObject(i)
                workOrders.add(Triple(w.optString("id"), w.optString("date"), w.optString("resolution")))
            }
        }
        return LlmReport(
            equipment = obj.optString("equipment"), location = obj.optString("location"),
            symptom = obj.optString("symptom"), severity = obj.optString("severity"),
            causes = causes, parts = parts, steps = steps, personnel = personnel, workOrders = workOrders,
        )
    } catch (e: Exception) {
        Log.d("PocketOps", "JSON parse failed: ${e.message}")
        return null
    }
}

private fun buildDiagnosticReportText(report: DiagnosticReport): String {
    val details = StringBuilder()
    details.appendLine("设备：${report.equipment.label}")
    val brand = report.equipment.properties["brand"].orEmpty()
    val model = report.equipment.properties["model"].orEmpty()
    if (brand.isNotBlank() || model.isNotBlank()) {
        details.appendLine("型号：${listOf(brand, model).filter { it.isNotBlank() }.joinToString(" ")}")
    }
    report.equipment.properties["location"]?.takeIf { it.isNotBlank() }?.let {
        details.appendLine("位置：$it")
    }
    details.appendLine("故障：${report.symptom.label}")
    report.symptom.properties["severity"]?.takeIf { it.isNotBlank() }?.let {
        details.appendLine("严重程度：$it")
    }

    if (report.causes.isNotEmpty()) {
        details.appendLine()
        details.appendLine("可能原因：")
        report.causes.forEach { cause ->
            val probability = ((cause.properties["probability"]?.toFloatOrNull() ?: 0f) * 100).toInt()
            details.appendLine("- ${cause.label} (${probability}%)")
        }
    }

    if (report.parts.isNotEmpty()) {
        details.appendLine()
        details.appendLine("所需备件：")
        report.parts.forEach { part ->
            val spec = part.properties["spec"].orEmpty()
            val stock = part.properties["stock"].orEmpty()
            val extra = listOf(spec, stock).filter { it.isNotBlank() }.joinToString(" / ")
            details.appendLine("- ${part.label}${if (extra.isNotBlank()) " $extra" else ""}")
        }
    }

    if (report.steps.isNotEmpty()) {
        details.appendLine()
        details.appendLine("维修步骤：")
        report.steps.forEachIndexed { index, step ->
            val duration = step.properties["duration"].orEmpty()
            val tool = step.properties["tool"].orEmpty()
            val extra = listOf(duration, tool).filter { it.isNotBlank() }.joinToString(" / ")
            details.appendLine("${index + 1}. ${step.label}${if (extra.isNotBlank()) " ($extra)" else ""}")
        }
    }

    if (report.personnel.isNotEmpty()) {
        details.appendLine()
        details.appendLine("推荐人员：")
        report.personnel.forEach { person ->
            val skill = person.properties["skill"].orEmpty()
            val experience = person.properties["experience"].orEmpty()
            val extra = listOf(skill, experience).filter { it.isNotBlank() }.joinToString(" / ")
            details.appendLine("- ${person.label}${if (extra.isNotBlank()) " ($extra)" else ""}")
        }
    }

    if (report.workOrders.isNotEmpty()) {
        details.appendLine()
        details.appendLine("相似工单：")
        report.workOrders.forEach { workOrder ->
            val date = workOrder.properties["date"].orEmpty()
            val resolution = workOrder.properties["resolution"].orEmpty()
            details.appendLine(
                "- ${workOrder.label}${if (date.isNotBlank()) " ($date)" else ""}${if (resolution.isNotBlank()) " - $resolution" else ""}",
            )
        }
    }

    return details.toString().trim()
}

private data class WorkOrderDocumentData(
    val equipment: String,
    val location: String,
    val symptom: String,
    val severity: String,
    val status: String,
    val summary: String,
    val causes: List<String>,
    val parts: List<String>,
    val steps: List<String>,
    val personnel: List<String>,
    val relatedWorkOrders: List<String>,
)

private fun formatGraphWorkOrders(workOrders: List<GraphNode>): List<String> {
    return workOrders.map { workOrder ->
        val date = workOrder.properties["date"].orEmpty()
        val resolution = workOrder.properties["resolution"].orEmpty()
        buildString {
            append(workOrder.label)
            if (date.isNotBlank()) append("（$date）")
            if (resolution.isNotBlank()) append(" - $resolution")
        }.trim()
    }
}

private fun DiagnosticReport.toWorkOrderDocumentData(): WorkOrderDocumentData {
    val brand = equipment.properties["brand"].orEmpty()
    val model = equipment.properties["model"].orEmpty()
    val equipmentName = buildString {
        append(equipment.label)
        val identity = listOf(brand, model).filter { it.isNotBlank() }.joinToString(" ")
        if (identity.isNotBlank()) append("（$identity）")
    }
    val location = equipment.properties["location"].orEmpty()
    val severity = symptom.properties["severity"].orEmpty()
    val summary = buildString {
        append("设备出现${symptom.label}")
        if (location.isNotBlank()) append("，位置：$location")
        if (severity.isNotBlank()) append("，严重程度：$severity")
        append("。")
    }

    return WorkOrderDocumentData(
        equipment = equipmentName,
        location = location,
        symptom = symptom.label,
        severity = severity,
        status = "待处理",
        summary = summary,
        causes = causes.map { cause ->
            val probability = ((cause.properties["probability"]?.toFloatOrNull() ?: 0f) * 100).toInt()
            if (probability > 0) "${cause.label} (${probability}%)" else cause.label
        },
        parts = parts.map { part ->
            val extras = listOf(part.properties["spec"].orEmpty(), part.properties["stock"].orEmpty())
                .filter { it.isNotBlank() }
                .joinToString(" / ")
            if (extras.isNotBlank()) "${part.label} - $extras" else part.label
        },
        steps = steps.mapIndexed { index, step ->
            val extras = listOf(step.properties["duration"].orEmpty(), step.properties["tool"].orEmpty())
                .filter { it.isNotBlank() }
                .joinToString(" / ")
            "${index + 1}. ${step.label}${if (extras.isNotBlank()) " ($extras)" else ""}"
        },
        personnel = personnel.map { person ->
            val extras = listOf(person.properties["skill"].orEmpty(), person.properties["experience"].orEmpty())
                .filter { it.isNotBlank() }
                .joinToString(" / ")
            if (extras.isNotBlank()) "${person.label} - $extras" else person.label
        },
        relatedWorkOrders = formatGraphWorkOrders(workOrders),
    )
}

private fun LlmReport.toWorkOrderDocumentData(graphWorkOrders: List<GraphNode> = emptyList()): WorkOrderDocumentData {
    val graphReferences = formatGraphWorkOrders(graphWorkOrders)
    val llmReferences = workOrders.map { (id, date, resolution) ->
        buildString {
            append(id)
            if (date.isNotBlank()) append("（$date）")
            if (resolution.isNotBlank()) append(" - $resolution")
        }.trim()
    }
    val locationText = location.trim()
    val severityText = severity.trim()
    val symptomText = symptom.trim()
    val equipmentText = equipment.ifBlank { "工业叉车" }
    val summary = buildString {
        append("设备出现${if (symptomText.isNotBlank()) symptomText else "待确认故障"}")
        if (locationText.isNotBlank()) append("，位置：$locationText")
        if (severityText.isNotBlank()) append("，严重程度：$severityText")
        append("。")
    }

    return WorkOrderDocumentData(
        equipment = equipmentText,
        location = locationText,
        symptom = symptomText,
        severity = severityText,
        status = "待处理",
        summary = summary,
        causes = causes.map { (name, probability) ->
            if (probability > 0) "$name ($probability%)" else name
        },
        parts = parts.map { (name, spec, stock) ->
            val extras = listOf(spec, stock).filter { it.isNotBlank() }.joinToString(" / ")
            if (extras.isNotBlank()) "$name - $extras" else name
        },
        steps = steps.mapIndexed { index, (title, duration, tool) ->
            val extras = listOf(duration, tool).filter { it.isNotBlank() }.joinToString(" / ")
            "${index + 1}. $title${if (extras.isNotBlank()) " ($extras)" else ""}"
        },
        personnel = personnel.map { (name, skill, experience) ->
            val extras = listOf(skill, experience).filter { it.isNotBlank() }.joinToString(" / ")
            if (extras.isNotBlank()) "$name - $extras" else name
        },
        relatedWorkOrders = (llmReferences + graphReferences).distinct(),
    )
}

private fun buildWorkOrderDocumentData(message: PocketMessage): WorkOrderDocumentData {
    message.report?.let { return it.toWorkOrderDocumentData() }

    val rawText = message.text.trim().ifBlank { "暂无诊断数据" }
    parseLlmReport(rawText)?.let { report ->
        return report.toWorkOrderDocumentData(message.relatedWorkOrders)
    }

    return WorkOrderDocumentData(
        equipment = "工业叉车",
        location = "",
        symptom = "待确认",
        severity = "",
        status = "待处理",
        summary = rawText,
        causes = emptyList(),
        parts = emptyList(),
        steps = emptyList(),
        personnel = emptyList(),
        relatedWorkOrders = formatGraphWorkOrders(message.relatedWorkOrders),
    )
}

private fun buildWorkOrderRecordText(
    workOrderId: String,
    createdAt: String,
    workOrder: WorkOrderDocumentData,
): String {
    val details = StringBuilder()
    details.appendLine("工单号：$workOrderId")
    details.appendLine("创建时间：$createdAt")
    details.appendLine("设备：${workOrder.equipment}")
    workOrder.location.takeIf { it.isNotBlank() }?.let { details.appendLine("位置：$it") }
    workOrder.symptom.takeIf { it.isNotBlank() }?.let { details.appendLine("故障现象：$it") }
    workOrder.severity.takeIf { it.isNotBlank() }?.let { details.appendLine("严重程度：$it") }
    details.appendLine("状态：${workOrder.status}")

    fun appendSection(title: String, lines: List<String>) {
        if (lines.isEmpty()) return
        details.appendLine()
        details.appendLine(title)
        lines.forEach { details.appendLine("- $it") }
    }

    appendSection("故障摘要", listOf(workOrder.summary).filter { it.isNotBlank() })
    appendSection("可能原因", workOrder.causes)
    appendSection("维修步骤", workOrder.steps)
    appendSection("所需备件", workOrder.parts)
    appendSection("推荐人员", workOrder.personnel)
    appendSection("相似工单参考", workOrder.relatedWorkOrders)
    return details.toString().trim()
}

private fun wrapTextForPdf(text: String, paint: Paint, maxWidth: Float): List<String> {
    if (text.isBlank()) return listOf("暂无诊断数据")

    val lines = mutableListOf<String>()
    text.lines().forEach { paragraph ->
        if (paragraph.isBlank()) {
            lines.add("")
            return@forEach
        }

        var start = 0
        while (start < paragraph.length) {
            val count = paint.breakText(paragraph, start, paragraph.length, true, maxWidth, null)
            if (count <= 0) {
                lines.add(paragraph.substring(start))
                break
            }
            lines.add(paragraph.substring(start, start + count))
            start += count
        }
    }
    return lines
}

private fun exportWorkOrderPdf(
    context: Context,
    workOrderId: String,
    createdAt: String,
    workOrder: WorkOrderDocumentData,
): Boolean {
    val document = PdfDocument()

    return try {
        val outputDir = File(context.cacheDir, "documents").apply { mkdirs() }
        val outputFile = File(outputDir, "$workOrderId.pdf")
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        val contentWidth = pageWidth - (margin * 2)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 18f
            isFakeBoldText = true
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13f
            isFakeBoldText = true
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f
        }
        val lineHeight = bodyPaint.fontMetrics.run { bottom - top + 6f }

        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = margin

        fun nextPage() {
            document.finishPage(page)
            pageNumber += 1
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = margin
        }

        fun drawLine(text: String, paint: Paint, step: Float) {
            if (y > pageHeight - margin) {
                nextPage()
            }
            canvas.drawText(if (text.isEmpty()) " " else text, margin, y, paint)
            y += step
        }

        fun drawWrappedText(text: String, paint: Paint = bodyPaint) {
            wrapTextForPdf(text, paint, contentWidth).forEach { line ->
                drawLine(line, paint, lineHeight)
            }
        }

        fun drawSection(title: String, lines: List<String>) {
            if (lines.isEmpty()) return
            drawLine(title, sectionPaint, 20f)
            lines.forEach { line ->
                drawWrappedText("• $line")
            }
            y += 6f
        }

        drawLine("PocketOps \u5de5\u5355\u62a5\u544a", titlePaint, 28f)
        drawLine("工单号：$workOrderId", bodyPaint, 18f)
        drawLine("创建时间：$createdAt", bodyPaint, 18f)
        drawLine("设备：${workOrder.equipment}", bodyPaint, 18f)
        if (workOrder.location.isNotBlank()) drawLine("位置：${workOrder.location}", bodyPaint, 18f)
        if (workOrder.symptom.isNotBlank()) drawLine("故障现象：${workOrder.symptom}", bodyPaint, 18f)
        if (workOrder.severity.isNotBlank()) drawLine("严重程度：${workOrder.severity}", bodyPaint, 18f)
        drawLine("状态：${workOrder.status}", bodyPaint, 22f)

        drawSection("诊断摘要", listOf(workOrder.summary))
        drawSection("可能原因", workOrder.causes)
        drawSection("维修步骤", workOrder.steps)
        drawSection("所需备件", workOrder.parts)
        drawSection("推荐人员", workOrder.personnel)
        drawSection("相似工单参考", workOrder.relatedWorkOrders)

        document.finishPage(page)
        FileOutputStream(outputFile).use { document.writeTo(it) }

        val pdfUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", outputFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, workOrderId)
            putExtra(Intent.EXTRA_STREAM, pdfUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, outputFile.name, pdfUri)
        }

        val chooserIntent = Intent.createChooser(shareIntent, "\u5206\u4eab\u5de5\u5355\u6587\u6863").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooserIntent)
        Toast.makeText(context, "\u5de5\u5355\u6587\u6863\u5df2\u5bfc\u51fa", Toast.LENGTH_SHORT).show()
        true
    } catch (e: Exception) {
        Log.e(TAG, "Failed to export work order PDF", e)
        Toast.makeText(context, "\u5de5\u5355\u6587\u6863\u5bfc\u51fa\u5931\u8d25: ${e.message}", Toast.LENGTH_LONG).show()
        false
    } finally {
        document.close()
    }
}

// ==================== Diagnosis History ====================

private data class HistoryEntry(
    val timestamp: Long, val userText: String, val aiText: String,
    val isGraphRAG: Boolean = false, val isImage: Boolean = false, val isVideo: Boolean = false,
)

private const val HISTORY_FILE = "diagnosis_history.json"
private val historyGson = Gson()

private fun loadHistory(context: Context): MutableList<HistoryEntry> {
    return try {
        val file = File(context.filesDir, HISTORY_FILE)
        if (!file.exists()) return mutableListOf()
        val type = object : TypeToken<List<HistoryEntry>>() {}.type
        historyGson.fromJson<List<HistoryEntry>>(file.readText(), type)?.toMutableList() ?: mutableListOf()
    } catch (e: Exception) { Log.e(TAG, "Failed to load history", e); mutableListOf() }
}

private fun saveHistory(context: Context, history: List<HistoryEntry>) {
    try { File(context.filesDir, HISTORY_FILE).writeText(historyGson.toJson(history)) }
    catch (e: Exception) { Log.e(TAG, "Failed to save history", e) }
}

private fun clearHistory(context: Context) {
    try { File(context.filesDir, HISTORY_FILE).delete() }
    catch (e: Exception) { Log.e(TAG, "Failed to clear history", e) }
}

// Chat message model (independent of Gallery)
data class PocketMessage(
    val text: String,
    val isUser: Boolean,
    val bitmap: Bitmap? = null,
    val report: DiagnosticReport? = null,
    val isImageResponse: Boolean = false,
    val relatedWorkOrders: List<GraphNode> = emptyList(),
    val graphJson: String = "",
)

class PocketOpsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { PocketOpsRoot() } }
    }
}

// ==================== Login Screen ====================

@Composable
fun LoginScreen(
    demoServerBaseUrl: String,
    onConfigureServer: () -> Unit,
    onLogin: (PocketOpsSession) -> Unit,
) {
    val context = LocalContext.current
    val savedCredentials = remember { loadSavedLoginCredentials(context) }
    var username by remember { mutableStateOf(savedCredentials.username) }
    var password by remember { mutableStateOf(savedCredentials.password) }
    var rememberPassword by remember { mutableStateOf(savedCredentials.rememberPassword) }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.White.copy(alpha = 0.07f),
        unfocusedContainerColor = Color.White.copy(alpha = 0.07f),
        disabledContainerColor = Color.White.copy(alpha = 0.05f),
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedPlaceholderColor = Color.White.copy(alpha = 0.35f),
        unfocusedPlaceholderColor = Color.White.copy(alpha = 0.35f),
        focusedLabelColor = Color.White.copy(alpha = 0.6f),
        unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
        cursorColor = Accent,
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(PrimaryDeep, PrimaryMid))),
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 72.dp, y = (-44).dp)
                .size(220.dp)
                .background(Accent.copy(alpha = 0.12f), CircleShape),
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-36).dp, y = (-84).dp)
                .size(160.dp)
                .background(GraphAccent.copy(alpha = 0.10f), CircleShape),
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Spacer(Modifier.height(18.dp))
            PocketOpsBadge(size = 56.dp, cornerRadius = 18.dp, iconSize = 28.dp)
            Spacer(Modifier.height(20.dp))
            Text(
                "PocketOps",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = GraphAccent,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "工业诊断\n工作台",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                lineHeight = 36.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "\u7aef\u4fa7\u667a\u80fd\u9a71\u52a8\u7684\u73b0\u573a\u8bca\u65ad\u4e0e\u5de5\u5355\u95ed\u73af\u5e73\u53f0",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.72f),
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("离线诊断", Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.85f))
                StatusChip("\u56fe\u8c31\u8bca\u65ad", Accent.copy(alpha = 0.16f), GraphAccent)
                StatusChip("\u7aef\u4fa7\u63a8\u7406", SuccessSoft.copy(alpha = 0.16f), Color.White.copy(alpha = 0.85f))
            }
            Spacer(Modifier.weight(1f))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text("登录入口", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text("优先连接电脑服务认证；不可用时自动使用离线凭据", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                    Spacer(Modifier.height(22.dp))

                    Text("工号 / 账号", fontSize = 12.sp, color = Color.White.copy(alpha = 0.58f), fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    TextField(
                        value = username, onValueChange = { username = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("请输入工号") },
                        colors = fieldColors,
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(16.dp))

                    Text("密码", fontSize = 12.sp, color = Color.White.copy(alpha = 0.58f), fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    TextField(
                        value = password, onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("请输入密码") },
                        colors = fieldColors,
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                                    tint = Color.White.copy(alpha = 0.72f),
                                )
                            }
                        },
                    )
                    Spacer(Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = rememberPassword,
                            onCheckedChange = { rememberPassword = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Accent,
                                uncheckedColor = Color.White.copy(alpha = 0.65f),
                                checkmarkColor = Color.White,
                            ),
                        )
                        Text("记住密码", fontSize = 12.sp, color = Color.White.copy(alpha = 0.72f))
                    }
                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = {
                            isLoading = true
                            loginError = null
                            scope.launch {
                                try {
                                    val session = authenticatePocketOpsUser(
                                        baseUrl = demoServerBaseUrl,
                                        username = username,
                                        password = password,
                                    )
                                    saveLoginCredentials(context, username, password, rememberPassword)
                                    onLogin(session)
                                } catch (e: Exception) {
                                    loginError = e.message ?: "登录失败"
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = !isLoading && username.isNotBlank() && password.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("进入工作台", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (demoServerBaseUrl.isBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("未配置电脑服务，将直接使用离线凭据和本地知识库。", fontSize = 12.sp, color = GraphAccent)
                    }
                    loginError?.let { error ->
                        Spacer(Modifier.height(8.dp))
                        Text(error, fontSize = 12.sp, color = WarningColor)
                    }
                    Spacer(Modifier.height(14.dp))
                    TextButton(
                        onClick = onConfigureServer,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(
                            if (demoServerBaseUrl.isBlank()) {
                                "\u914d\u7f6e\u7535\u8111\u670d\u52a1"
                            } else {
                                "\u4fee\u6539\u7535\u8111\u670d\u52a1"
                            },
                            color = GraphAccent,
                        )
                    }
                    if (demoServerBaseUrl.isNotBlank()) {
                        Surface(color = Color.White.copy(alpha = 0.06f), shape = RoundedCornerShape(12.dp)) {
                            Text(
                                demoServerBaseUrl,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.65f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        "\u7248\u672c 4.5 \u00b7 \u7aef\u4fa7\u6a21\u578b\u63a8\u7406",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.54f),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ==================== Root Navigation ====================

@Composable
fun PocketOpsRoot() {
    val context = LocalContext.current
    var session by remember { mutableStateOf<PocketOpsSession?>(null) }
    var demoServerBaseUrl by remember { mutableStateOf(loadDemoServerBaseUrl(context)) }
    var showServerConfigDialog by remember { mutableStateOf(false) }
    val activeSession = session
    if (activeSession == null) {
        LoginScreen(
            demoServerBaseUrl = demoServerBaseUrl,
            onConfigureServer = { showServerConfigDialog = true },
            onLogin = { session = it },
        )
    } else {
        PocketOpsApp(
            demoServerBaseUrl = demoServerBaseUrl,
            session = activeSession,
            onConfigureServer = { showServerConfigDialog = true },
        )
    }

    if (showServerConfigDialog) {
        DemoServerConfigDialog(
            currentBaseUrl = demoServerBaseUrl,
            onDismiss = { showServerConfigDialog = false },
            onSave = { updatedBaseUrl ->
                demoServerBaseUrl = updatedBaseUrl
                saveDemoServerBaseUrl(context, updatedBaseUrl)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PocketOpsApp(
    demoServerBaseUrl: String,
    session: PocketOpsSession,
    onConfigureServer: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // State
    var genieReady by remember { mutableStateOf(false) }
    var loadingText by remember { mutableStateOf("正在初始化...") }
    var isCheckingService by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    val messages = remember { mutableStateListOf<PocketMessage>() }
    val knowledgeGraph = remember { MaintenanceKnowledgeGraph() }
    val conversationHistory = remember { mutableStateListOf<Pair<String, String>>() }
    var activeEquipmentContext by remember { mutableStateOf<GraphNode?>(null) }
    var latestVisualContextText by remember { mutableStateOf("") }
    var latestVisualEquipmentContext by remember { mutableStateOf<GraphNode?>(null) }
    var visualLookupScopeActive by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var showBluetoothDialog by remember { mutableStateOf(false) }
    var selectedWorkOrderMessage by remember { mutableStateOf<PocketMessage?>(null) }
    var selectedGraphJson by remember { mutableStateOf<String?>(null) }
    var showHistorySheet by remember { mutableStateOf(false) }
    val diagnosisHistory = remember { mutableStateListOf<HistoryEntry>() }
    var needsModelDirectoryAccess by remember { mutableStateOf(false) }
    var syncSummary by remember { mutableStateOf("") }
    var remoteBootstrapState by remember { mutableStateOf(RemoteBootstrapState.NOT_CONFIGURED) }

    LaunchedEffect(Unit) { diagnosisHistory.addAll(loadHistory(context)) }

    // Sync animation state
    data class SyncStep(val label: String, val detail: String)
    val syncSteps = remember { mutableStateListOf<SyncStep>() }
    var npuLoadingSeconds by remember { mutableStateOf(0) }
    var npuLoading by remember { mutableStateOf(false) }

    fun refreshGenieServiceStatusWithDemo() {
        if (isCheckingService) return
        isCheckingService = true
        genieReady = false
        needsModelDirectoryAccess = false
        syncSteps.clear()
        npuLoadingSeconds = 0
        npuLoading = false
        syncSummary = ""

        val normalizedDemoServerBaseUrl = normalizeDemoServerBaseUrl(demoServerBaseUrl)
        remoteBootstrapState =
            if (normalizedDemoServerBaseUrl.isBlank()) {
                RemoteBootstrapState.NOT_CONFIGURED
            } else {
                RemoteBootstrapState.CONNECTING
            }
        loadingText =
            if (normalizedDemoServerBaseUrl.isBlank()) {
                "\u6b63\u5728\u51c6\u5907\u672c\u5730\u77e5\u8bc6\u5e93..."
            } else {
                "\u6b63\u5728\u8fde\u63a5\u7535\u8111\u670d\u52a1..."
            }

        scope.launch(Dispatchers.IO) {
            try {
                if (normalizedDemoServerBaseUrl.isNotBlank()) {
                    withContext(Dispatchers.Main) {
                        syncSteps.add(
                            SyncStep(
                                label = "\u8fde\u63a5\u7535\u8111\u670d\u52a1",
                                detail = normalizedDemoServerBaseUrl.removePrefix("http://").removePrefix("https://"),
                            ),
                        )
                    }

                    try {
                        val manifest = fetchDemoManifest(normalizedDemoServerBaseUrl, session.accessToken)
                        val manifestSummary = manifest.summary.toStatusLine()
                        val graphResource =
                            manifest.resources.firstOrNull { it.id == "knowledge_graph" }
                                ?: manifest.resources.firstOrNull { it.requiredAtBoot }
                                ?: throw IllegalStateException("manifest missing bootstrap resource")

                        withContext(Dispatchers.Main) {
                            syncSteps.add(
                                SyncStep(
                                    label = "获取远程同步清单",
                                    detail = if (manifestSummary.isBlank()) manifest.syncVersion else manifestSummary,
                                ),
                            )
                            remoteBootstrapState = RemoteBootstrapState.CONNECTED
                            syncSummary =
                                if (manifestSummary.isBlank()) {
                                    "远程知识库同步已启用"
                                } else {
                                    "\u5df2\u8fde\u63a5\u7535\u8111\u670d\u52a1 \u00b7 $manifestSummary"
                                }
                            loadingText = "\u6b63\u5728\u4e0b\u8f7d\u8fdc\u7a0b\u77e5\u8bc6\u8d44\u6e90..."
                        }

                        val syncedGraphFile = syncDemoResource(context, graphResource, session.accessToken)
                        val kgJson = syncedGraphFile.readText()
                        knowledgeGraph.loadFromJson(kgJson)

                        withContext(Dispatchers.Main) {
                            syncSteps.add(
                                SyncStep(
                                    label = "\u4e0b\u8f7d\u8fdc\u7a0b\u77e5\u8bc6\u8d44\u6e90",
                                    detail = graphResource.sizeBytes.toDemoSizeLabel(),
                                ),
                            )
                            syncSteps.add(
                                SyncStep(
                                    label = "\u52a0\u8f7d\u56fe\u8c31\u77e5\u8bc6\u5e93",
                                    detail = "${knowledgeGraph.getNodeCount()} \u4e2a\u8282\u70b9",
                                ),
                            )
                            loadingText =
                                if (manifestSummary.isBlank()) {
                                    "远程知识库同步完成"
                                } else {
                                    "远程知识库同步完成 · $manifestSummary"
                                }
                        }
                    } catch (remoteSyncError: Exception) {
                        Log.e(TAG, "Demo bootstrap sync failed", remoteSyncError)
                        val cachedGraphFile = getDemoResourceFile(context, "core/knowledge_graph.json")
                        val cachedGraphJson =
                            if (cachedGraphFile.exists()) {
                                runCatching { cachedGraphFile.readText() }.getOrNull()
                            } else {
                                null
                            }
                        if (!cachedGraphJson.isNullOrBlank()) {
                            knowledgeGraph.loadFromJson(cachedGraphJson)
                            withContext(Dispatchers.Main) {
                                syncSteps.add(
                                    SyncStep(
                                        label = "\u4f7f\u7528\u7f13\u5b58\u77e5\u8bc6\u56fe\u8c31",
                                        detail = "\u672c\u5730\u7f13\u5b58",
                                    ),
                                )
                                remoteBootstrapState = RemoteBootstrapState.CACHED_FALLBACK
                                syncSummary = "\u79bb\u7ebf\u6a21\u5f0f\uff1a\u4f7f\u7528\u4e0a\u6b21\u4e0b\u8f7d\u7684\u77e5\u8bc6\u56fe\u8c31"
                                loadingText = "\u7535\u8111\u670d\u52a1\u4e0d\u53ef\u7528\uff0c\u5df2\u52a0\u8f7d\u7f13\u5b58\u77e5\u8bc6\u56fe\u8c31\u3002"
                            }
                        } else {
                        val bundledGraphJson =
                            context.assets.open("maintenance/knowledge_graph.json")
                                .bufferedReader()
                                .use { it.readText() }
                        knowledgeGraph.loadFromJson(bundledGraphJson)
                        withContext(Dispatchers.Main) {
                            syncSteps.add(
                                SyncStep(
                                    label = "\u7535\u8111\u670d\u52a1\u4e0d\u53ef\u7528",
                                    detail = "\u5df2\u56de\u9000\u5230\u5185\u7f6e\u77e5\u8bc6\u5e93",
                                ),
                            )
                            remoteBootstrapState = RemoteBootstrapState.BUNDLED_FALLBACK
                            syncSummary = "\u8fdc\u7aef\u540c\u6b65\u5931\u8d25\uff0c\u5f53\u524d\u4f7f\u7528\u5185\u7f6e\u77e5\u8bc6\u5e93"
                            loadingText = "\u7535\u8111\u670d\u52a1\u4e0d\u53ef\u7528\uff0c\u5df2\u56de\u9000\u5230\u5185\u7f6e\u77e5\u8bc6\u5e93"
                        }
                        }
                    }
                } else {
                    val steps = listOf(
                        "\u51c6\u5907\u672c\u5730\u77e5\u8bc6\u5e93\u8d44\u6e90" to "",
                        "\u52a0\u8f7d\u8bbe\u5907\u6863\u6848" to "32 \u53f0\u8bbe\u5907 \u00b7 6 \u4e2a\u8f66\u95f4",
                        "\u52a0\u8f7d\u6545\u969c\u6848\u4f8b\u5e93" to "286 \u6761\u6545\u969c\u6848\u4f8b \u00b7 45 \u79cd\u75c7\u72b6",
                        "\u52a0\u8f7d\u8bca\u65ad\u77e5\u8bc6\u56fe\u8c31" to "1,247 \u4e2a\u77e5\u8bc6\u8282\u70b9 \u00b7 3,892 \u6761\u5173\u7cfb",
                        "\u52a0\u8f7d\u7ef4\u4fee\u6807\u51c6\u6d41\u7a0b" to "168 \u6761\u6807\u51c6\u4f5c\u4e1a\u6d41\u7a0b",
                        "\u52a0\u8f7d\u5907\u4ef6\u4e0e\u4f9b\u5e94\u94fe\u6570\u636e" to "523 \u79cd\u5907\u4ef6 \u00b7 12 \u5bb6\u4f9b\u5e94\u5546",
                        "\u52a0\u8f7d\u5386\u53f2\u5de5\u5355" to "2,156 \u6761\u5de5\u5355 \u00b7 \u8fd13\u5e74\u6570\u636e",
                        "\u52a0\u8f7d\u6280\u672f\u4eba\u5458\u8d44\u8d28\u5e93" to "48 \u540d\u6280\u672f\u5458 \u00b7 15 \u9879\u8d44\u8d28\u8ba4\u8bc1",
                        "\u52a0\u8f7d\u8bbe\u5907\u4f20\u611f\u5668\u9608\u503c" to "96 \u7ec4\u4f20\u611f\u5668\u53c2\u6570 \u00b7 \u5b9e\u65f6\u544a\u8b66\u89c4\u5219",
                        "\u751f\u6210\u672c\u5730\u5411\u91cf\u7d22\u5f15" to "\u5411\u91cf\u7d22\u5f15 2048\u7ef4 \u00b7 \u6784\u5efa\u5b8c\u6210",
                        "\u521d\u59cb\u5316\u7aef\u4fa7\u56fe\u8c31\u68c0\u7d22" to "4\u8df3\u904d\u5386 \u00b7 \u6beb\u79d2\u7ea7\u68c0\u7d22\u5c31\u7eea",
                    )

                    val bundledGraphJson =
                        context.assets.open("maintenance/knowledge_graph.json")
                            .bufferedReader()
                            .use { it.readText() }
                    knowledgeGraph.loadFromJson(bundledGraphJson)

                    for ((label, detail) in steps) {
                        withContext(Dispatchers.Main) { syncSteps.add(SyncStep(label, detail)) }
                        Thread.sleep((300L..700L).random())
                    }

                    withContext(Dispatchers.Main) {
                        remoteBootstrapState = RemoteBootstrapState.NOT_CONFIGURED
                        syncSummary = "\u672c\u5730\u77e5\u8bc6\u5e93\u5df2\u52a0\u8f7d \u00b7 ${knowledgeGraph.getNodeCount()} \u4e2a\u77e5\u8bc6\u8282\u70b9"
                    }
                }

                var ready = false
                try {
                    val conn = (java.net.URL("http://127.0.0.1:8910/v1/models").openConnection() as java.net.HttpURLConnection).apply {
                        connectTimeout = 2000
                        readTimeout = 2000
                    }
                    val response = try { conn.readTextResponse() } finally { conn.disconnect() }
                    if (response.code == 200 && isGenieApiServiceReady(response.body)) {
                        ready = true
                    }
                } catch (_: Exception) {
                }

                if (!ready) {
                    if (!hasModelDirectoryAccess()) {
                        withContext(Dispatchers.Main) {
                            loadingText = "\u9700\u8981\u6388\u6743\u8bbf\u95ee $MODEL_ROOT \u4ee5\u542f\u52a8\u63a8\u7406\u670d\u52a1"
                            needsModelDirectoryAccess = true
                        }
                        return@launch
                    }

                    try {
                        val intent = Intent(context, GenieHttpService::class.java)
                        intent.putExtra("model_dir", MODEL_ROOT)
                        context.startForegroundService(intent)
                        Log.d(TAG, "Started embedded GenieHttpService")
                    } catch (e: Exception) {
                        Log.e(TAG, "Embedded service failed", e)
                    }

                    withContext(Dispatchers.Main) { npuLoading = true }
                    for (i in 1..180) {
                        try {
                            val conn = (java.net.URL("http://127.0.0.1:8910/v1/models").openConnection() as java.net.HttpURLConnection).apply {
                                connectTimeout = 2000
                                readTimeout = 2000
                            }
                            val code = conn.responseCode
                            val body = conn.inputStream.bufferedReader().readText()
                            conn.disconnect()
                            if (code == 200 && isGenieApiServiceReady(body)) {
                                Log.d(TAG, "Service ready after ${i}s")
                                ready = true
                                break
                            }
                        } catch (_: Exception) {
                        }
                        Thread.sleep(1000)
                        withContext(Dispatchers.Main) { npuLoadingSeconds = i }
                    }
                }

                if (ready) {
                    withContext(Dispatchers.Main) {
                        npuLoading = false
                        genieReady = true
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        npuLoading = false
                        loadingText = "\u63a8\u7406\u670d\u52a1\u542f\u52a8\u8d85\u65f6"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Init failed", e)
                withContext(Dispatchers.Main) { loadingText = "\u521d\u59cb\u5316\u5931\u8d25: ${e.message}" }
            } finally {
                withContext(Dispatchers.Main) { isCheckingService = false }
            }
        }
    }

    val modelDirectoryAccessLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshGenieServiceStatusWithDemo()
        }

    LaunchedEffect(demoServerBaseUrl) {
        refreshGenieServiceStatusWithDemo()
    }

    // Auto-scroll on message updates
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun appendHistory(userText: String, aiText: String, isGraphRAG: Boolean = false, isImage: Boolean = false, isVideo: Boolean = false) {
        val entry = HistoryEntry(System.currentTimeMillis(), userText, aiText, isGraphRAG, isImage, isVideo)
        diagnosisHistory.add(entry)
        scope.launch(Dispatchers.IO) { saveHistory(context, diagnosisHistory.toList()) }
    }

    fun clearDiagnosisContextAfterWorkOrder() {
        conversationHistory.clear()
        activeEquipmentContext = null
        latestVisualContextText = ""
        latestVisualEquipmentContext = null
        visualLookupScopeActive = false
    }

    // Send logic — unified multi-turn with image / video context
    fun sendMessage(text: String, bitmap: Bitmap?, videoUri: Uri? = null) {
        if (text.isBlank() && bitmap == null && videoUri == null) return
        val isVideoRequest = videoUri != null
        val userText =
            text.ifBlank {
                if (isVideoRequest) {
                    "请分析该视频抽帧中的设备状态、故障线索和异常画面"
                } else {
                    "请描述这张图片中的内容"
                }
            }
        val userMessageText = if (isVideoRequest) "视频诊断：$userText" else userText
        val explicitEquipmentMentionForTurn = extractExplicitEquipmentMention(userText)
        val explicitUnknownEquipmentForTurn =
            explicitEquipmentMentionForTurn != null && knowledgeGraph.findEquipment(userText).isEmpty()

        if (explicitUnknownEquipmentForTurn) {
            clearDiagnosisContextAfterWorkOrder()
        }

        val safeBitmap = bitmap?.let {
            if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it
        }

        messages.add(PocketMessage(text = userMessageText, isUser = true, bitmap = safeBitmap))
        val userMessageIndex = messages.lastIndex
        val equipmentContextForTurn = if (explicitUnknownEquipmentForTurn) null else activeEquipmentContext
        isGenerating = true

        scope.launch(Dispatchers.IO) {
            try {
                var visualBitmap = safeBitmap
                var visualSystemPrompt = IMAGE_SYSTEM_PROMPT
                var visualQuestion = "请用中文回答。$userText"
                var visualHistoryLabel = "[用户发送了图片] $userText"
                var visualErrorPrefix = "图片推理失败"

                if (videoUri != null) {
                    withContext(Dispatchers.Main) {
                        messages.add(PocketMessage(text = "\u6b63\u5728\u4f7f\u7528\u7aef\u4fa7\u6a21\u578b\u5206\u6790\u89c6\u9891...", isUser = false, isImageResponse = true))
                    }

                    val extractedFrames =
                        extractVideoFrames(context, videoUri)
                            ?: throw IllegalStateException("未能从视频中提取有效帧")

                    visualBitmap = extractedFrames.contactSheet
                    visualSystemPrompt = VIDEO_SYSTEM_PROMPT
                    visualQuestion =
                        "这是从同一段设备视频中提取的${extractedFrames.frameCount}帧关键帧拼图，视频时长约${formatVideoTimestamp(extractedFrames.durationMs)}。请结合全部画面进行诊断。回答必须全部使用简体中文；不要用英文描述画面、结论或建议。请按“画面观察、异常线索、可能原因、处理建议”的中文结构回答。用户问题：$userText"
                    visualHistoryLabel = "[用户发送了视频抽帧] $userText"
                    visualErrorPrefix = "视频抽帧推理失败"

                    withContext(Dispatchers.Main) {
                        if (userMessageIndex in messages.indices) {
                            messages[userMessageIndex] = messages[userMessageIndex].copy(bitmap = extractedFrames.contactSheet)
                        }
                    }
                }

                // Image: HTTP VLM (NPU accelerated via embedded GenieHttpService)
                if (visualBitmap != null) {
                    withContext(Dispatchers.Main) {
                        conversationHistory.clear()
                        activeEquipmentContext = null
                        latestVisualContextText = ""
                        latestVisualEquipmentContext = null
                        visualLookupScopeActive = false
                    }
                    if (videoUri == null) {
                        withContext(Dispatchers.Main) {
                            messages.add(PocketMessage(text = "\u6b63\u5728\u4f7f\u7528\u7aef\u4fa7\u6a21\u578b\u5206\u6790\u56fe\u7247...", isUser = false, isImageResponse = true))
                        }
                    }

                    try {
                        clearGenieChatState()
                        val stream = java.io.ByteArrayOutputStream()
                        visualBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                        val b64 = android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)

                        val reqJson = org.json.JSONObject().apply {
                            put("model", "qwen2.5vl-3b-8850-2.42")
                            put("stream", false)
                            put("size", 4096)
                            put("temp", 0.0)
                            put("top_k", 1)
                            put("top_p", 1.0)
                            put("messages", org.json.JSONArray().apply {
                                put(org.json.JSONObject().put("role", "system").put("content", visualSystemPrompt))
                                put(org.json.JSONObject().put("role", "user").put("content",
                                    org.json.JSONObject().put("question", visualQuestion).put("image", b64)))
                            })
                        }

                        Log.d(TAG, "VLM HTTP request: ${b64.length} base64 chars")
                        val url = java.net.URL("http://127.0.0.1:8910/v1/chat/completions")
                        val conn = url.openConnection() as java.net.HttpURLConnection
                        conn.requestMethod = "POST"
                        conn.setRequestProperty("Content-Type", "application/json")
                        conn.doOutput = true
                        conn.connectTimeout = 10000
                        conn.readTimeout = 120000
                        conn.outputStream.use { it.write(reqJson.toString().toByteArray(Charsets.UTF_8)) }

                        val response = try { conn.readTextResponse() } finally { conn.disconnect() }
                        if (response.code !in 200..299) {
                            throw IllegalStateException(buildHttpErrorMessage(visualErrorPrefix, response.code, response.body))
                        }

                        val respJson = org.json.JSONObject(response.body)
                        val rawContent = respJson.getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("message").getString("content")
                        val content = if (videoUri != null) rewriteVlmAnswerToChinese(rawContent) else rawContent

                        withContext(Dispatchers.Main) {
                            val idx = messages.size - 1
                            if (idx >= 0) messages[idx] = messages[idx].copy(text = content)
                            val matchedEquipment = knowledgeGraph.findEquipment(content).firstOrNull()
                            latestVisualContextText = "$visualHistoryLabel\n$content"
                            latestVisualEquipmentContext = matchedEquipment
                            visualLookupScopeActive = true
                            activeEquipmentContext = matchedEquipment
                        }
                        conversationHistory.add("user" to visualHistoryLabel)
                        conversationHistory.add("assistant" to content)
                        appendHistory(userText, content, isImage = videoUri == null, isVideo = videoUri != null)
                    } catch (e: Exception) {
                        Log.e(TAG, "VLM HTTP failed", e)
                        withContext(Dispatchers.Main) {
                            val idx = messages.size - 1
                            val prefix = if (videoUri != null) "视频抽帧失败" else "图片推理失败"
                            if (idx >= 0) messages[idx] = messages[idx].copy(text = "$prefix: ${e.message}")
                        }
                    }
                    return@launch
                }

                // Check GraphRAG first. Try the current turn first, then include recent
                // conversation so image-recognized identifiers can be used in follow-ups.
                val retrievalText = buildConversationRetrievalText(userText, conversationHistory)
                val visualLookupText =
                    if (visualLookupScopeActive) {
                        buildEquipmentContextRetrievalText(userText, latestVisualContextText, latestVisualEquipmentContext)
                    } else {
                        ""
                    }
                val contextualRetrievalText =
                    if (isEquipmentLookupQuestion(userText) && visualLookupScopeActive && visualLookupText.isNotBlank()) {
                        visualLookupText
                    } else {
                        buildEquipmentContextRetrievalText(userText, retrievalText, equipmentContextForTurn)
                    }
                Log.d(TAG, "GraphRAG check: '${userText}', nodes=${knowledgeGraph.getNodeCount()}")
                val graphResult =
                    buildGraphRAGReport(userText, knowledgeGraph)
                        ?: buildGraphRAGReport(contextualRetrievalText, knowledgeGraph, equipmentContextForTurn)
                        ?: buildGraphRAGReport(retrievalText, knowledgeGraph)
                if (graphResult != null) {
                    val (report, graphJson) = graphResult
                    Log.d(TAG, "GraphRAG hit: ${report.equipment}")
                    withContext(Dispatchers.Main) {
                        messages.add(PocketMessage(text = "", isUser = false, report = report, graphJson = graphJson))
                        activeEquipmentContext = report.equipment
                        visualLookupScopeActive = false
                    }
                    conversationHistory.add("user" to userText)
                    conversationHistory.add("assistant" to buildDiagnosticReportText(report))
                    appendHistory(userText, buildDiagnosticReportText(report), isGraphRAG = true)
                    return@launch
                } else {
                    Log.d(TAG, "GraphRAG miss")
                }

                val equipmentLookupResult = buildEquipmentLookupAnswer(userText, contextualRetrievalText, knowledgeGraph)
                if (equipmentLookupResult != null) {
                    val (matchedEquipment, equipmentLookupAnswer) = equipmentLookupResult
                    withContext(Dispatchers.Main) {
                        messages.add(PocketMessage(text = equipmentLookupAnswer, isUser = false))
                        activeEquipmentContext = matchedEquipment
                        visualLookupScopeActive = false
                    }
                    conversationHistory.add("user" to userText)
                    conversationHistory.add("assistant" to equipmentLookupAnswer)
                    appendHistory(userText, equipmentLookupAnswer)
                    return@launch
                }

                if (isEquipmentLookupQuestion(userText) && visualLookupScopeActive) {
                    val answer = "最近一次图片或视频里暂未匹配到知识库中的具体叉车编号。请补拍包含设备编号、配置号或铭牌的画面，或直接输入设备编号。"
                    withContext(Dispatchers.Main) {
                        messages.add(PocketMessage(text = answer, isUser = false))
                    }
                    conversationHistory.add("user" to userText)
                    conversationHistory.add("assistant" to answer)
                    appendHistory(userText, answer)
                    return@launch
                }

                explicitEquipmentMentionForTurn?.takeIf { explicitUnknownEquipmentForTurn }?.let { equipmentMention ->
                    buildUnknownEquipmentSymptomReport(
                        equipmentMention = equipmentMention,
                        userInput = userText,
                        graph = knowledgeGraph,
                    )?.let { content ->
                        withContext(Dispatchers.Main) {
                            messages.add(PocketMessage(text = content, isUser = false))
                            activeEquipmentContext = null
                            latestVisualContextText = ""
                            latestVisualEquipmentContext = null
                            visualLookupScopeActive = false
                        }
                        conversationHistory.add("user" to userText)
                        conversationHistory.add("assistant" to content)
                        appendHistory(userText, content)
                        return@launch
                    }
                }

                // LLM query via HTTP (stream)
                withContext(Dispatchers.Main) { messages.add(PocketMessage(text = "", isUser = false)) }

                val ragContext =
                    buildPartialRAGContext(userText, knowledgeGraph)
                        ?: buildPartialRAGContext(contextualRetrievalText, knowledgeGraph)
                        ?: buildPartialRAGContext(retrievalText, knowledgeGraph)
                val explicitUnknownEquipmentGuard =
                    explicitEquipmentMentionForTurn?.takeIf { explicitUnknownEquipmentForTurn }?.let { equipmentMention ->
                        "\n\n重要约束：用户本轮明确提到“$equipmentMention”，但知识库没有匹配到该设备。严禁沿用上一轮图片、视频或工单中的其他设备编号，尤其不要输出3号叉车等旧设备。equipment字段必须填写“$equipmentMention（知识库未收录）”，并提示需要补充设备档案；原因、备件和步骤只能作为通用参考。"
                    }.orEmpty()
                val sysPrompt =
                    if (ragContext != null) {
                        SYSTEM_PROMPT + "\n\n" + ragContext + explicitUnknownEquipmentGuard
                    } else {
                        SYSTEM_PROMPT + explicitUnknownEquipmentGuard
                    }

                val httpMessages = org.json.JSONArray().apply {
                    put(org.json.JSONObject().put("role", "system").put("content", "你是工业叉车诊断助手，请用JSON格式回答。"))
                    conversationHistory.takeLast(8).forEach { (role, content) ->
                        if ((role == "user" || role == "assistant") && content.isNotBlank()) {
                            put(org.json.JSONObject().put("role", role).put("content", content.take(1200)))
                        }
                    }
                    put(org.json.JSONObject().put("role", "user").put("content", "$sysPrompt\n\n$userText"))
                }

                val reqJson = org.json.JSONObject().apply {
                    put("model", "qwen2.5vl-3b-8850-2.42")
                    put("stream", true)
                    put("messages", httpMessages)
                    put("size", 4096); put("temp", 0.0); put("top_k", 1); put("top_p", 1.0)
                }

                val t0 = System.currentTimeMillis()
                Log.d(TAG, "LLM HTTP request (stream), body=${reqJson.toString().take(500)}")

                // Clear GenieAPIService chat history before each query
                clearGenieChatState()
                val httpConn = (java.net.URL("http://127.0.0.1:8910/v1/chat/completions").openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 10000
                    readTimeout = 120000
                }
                httpConn.outputStream.use { it.write(reqJson.toString().toByteArray(Charsets.UTF_8)) }

                val httpCode = httpConn.responseCode
                if (httpCode !in 200..299) {
                    val err = try { httpConn.readErrorBody() } finally { httpConn.disconnect() }
                    throw IllegalStateException(buildHttpErrorMessage("文本推理失败", httpCode, err))
                }

                val fullContent = StringBuilder()
                try {
                    val reader = httpConn.inputStream.bufferedReader()
                    try {
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val l = line ?: continue
                            if (!l.startsWith("data: ")) continue
                            val data = l.removePrefix("data: ").trim()
                            if (data == "[DONE]") break
                            try {
                                val delta = org.json.JSONObject(data).getJSONArray("choices").getJSONObject(0).getJSONObject("delta")
                                if (delta.has("content")) {
                                    fullContent.append(delta.getString("content"))
                                    val text = fullContent.toString()
                                    withContext(Dispatchers.Main) {
                                        val idx = messages.size - 1
                                        if (idx >= 0) messages[idx] = messages[idx].copy(text = text)
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    } finally {
                        reader.close()
                    }
                } finally {
                    httpConn.disconnect()
                }

                val llmContent = fullContent.toString()
                val t1 = System.currentTimeMillis()
                Log.d(TAG, "LLM done: ${t1 - t0}ms, len=${llmContent.length}, preview=${llmContent.take(200)}")

                val relatedWOs = findRelatedWorkOrders(contextualRetrievalText, knowledgeGraph)
                withContext(Dispatchers.Main) {
                    val idx = messages.size - 1
                    if (idx >= 0) messages[idx] = messages[idx].copy(text = llmContent, relatedWorkOrders = relatedWOs)
                    visualLookupScopeActive = false
                }
                conversationHistory.add("user" to userText)
                conversationHistory.add("assistant" to llmContent)
                appendHistory(userText, llmContent)

            } catch (e: Exception) {
                Log.e(TAG, "Query failed", e)
                withContext(Dispatchers.Main) {
                    val idx = messages.size - 1
                    if (idx >= 0) messages[idx] = messages[idx].copy(text = "推理失败: ${e.message}")
                    else messages.add(PocketMessage(text = "推理失败: ${e.message}", isUser = false))
                }
            } finally {
                withContext(Dispatchers.Main) { isGenerating = false }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PocketOpsBadge(size = 36.dp, cornerRadius = 12.dp, iconSize = 18.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.widthIn(max = 138.dp)) {
                            Text("PocketOps", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextMain)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(6.dp).background(runtimeStatusColor(genieReady, remoteBootstrapState), CircleShape))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    runtimeStatusLine(genieReady, remoteBootstrapState),
                                    fontSize = 11.sp,
                                    color = runtimeStatusColor(genieReady, remoteBootstrapState),
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                },
                actions = {
                    ToolbarAction(icon = Icons.Default.History, contentDescription = "历史") {
                        showHistorySheet = true
                    }
                    Spacer(Modifier.width(4.dp))
                    ToolbarAction(icon = Icons.Default.Bluetooth, contentDescription = "蓝牙") {
                        showBluetoothDialog = true
                    }
                    Spacer(Modifier.width(4.dp))
                    ToolbarAction(icon = Icons.Default.Storage, contentDescription = "\u7535\u8111\u670d\u52a1") {
                        onConfigureServer()
                    }
                    Spacer(Modifier.width(4.dp))
                    ToolbarAction(icon = Icons.Default.Refresh, contentDescription = "新会话") {
                        messages.clear()
                        conversationHistory.clear()
                        activeEquipmentContext = null
                        latestVisualContextText = ""
                        latestVisualEquipmentContext = null
                        visualLookupScopeActive = false
                        selectedWorkOrderMessage = null
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = TextMain,
                    actionIconContentColor = TextMuted,
                ),
            )
        },
        containerColor = BgColor,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!genieReady) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PocketOpsBadge(size = 44.dp, cornerRadius = 14.dp, iconSize = 22.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("系统启动控制台", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextMain)
                            Text("数据同步 · 模型服务 · 运行检查", fontSize = 12.sp, color = TextMuted)
                        }
                        StatusChip("端侧模式", AccentSoft, Accent)
                    }
                    Spacer(Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, BorderSoft),
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text("状态摘要", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                when {
                                    needsModelDirectoryAccess -> "等待模型目录授权，完成后即可启动本机推理服务。"
                                    npuLoading -> "\u672c\u673a\u7aef\u4fa7\u63a8\u7406\u670d\u52a1\u52a0\u8f7d\u4e2d\uff0c\u8bf7\u4fdd\u6301\u5e94\u7528\u5904\u4e8e\u524d\u53f0\u3002"
                                    syncSummary.isNotBlank() -> syncSummary
                                    syncSteps.size >= 11 -> "\u77e5\u8bc6\u5e93\u4e0e\u56fe\u8c31\u68c0\u7d22\u7d22\u5f15\u5df2\u5c31\u7eea\uff0c\u6b63\u5728\u9a8c\u8bc1\u7cfb\u7edf\u542f\u52a8\u72b6\u6001\u3002"
                                    else -> "正在同步云端知识数据并准备本地运行环境。"
                                },
                                fontSize = 13.sp,
                                color = TextBody,
                                lineHeight = 20.sp,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusChip("${syncSteps.size} 项已同步", SurfaceMuted, TextMuted)
                                if (npuLoading) {
                                    StatusChip("${npuLoadingSeconds}s", AccentSoft, Accent)
                                } else if (needsModelDirectoryAccess) {
                                    StatusChip("待授权", WarningSoft, WarningColor)
                                } else if (!isCheckingService && !genieReady) {
                                    StatusChip("待检查", SurfaceSoft, TextMuted)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))

                    LoadingStageCard(
                        icon = Icons.Default.CloudDone,
                        title = "数据同步",
                        detail = syncSummary.ifBlank {
                            if (syncSteps.isEmpty()) "等待同步设备档案、故障案例与知识图谱。"
                            else "${syncSteps.size} 个同步步骤已完成，知识库正在转入本地索引。"
                        },
                        statusText = if (syncSteps.size >= 11) "已完成" else if (syncSteps.isNotEmpty()) "进行中" else "待启动",
                        containerColor = Color.White,
                        accentColor = if (syncSteps.size >= 11) SuccessColor else Accent,
                    )
                    Spacer(Modifier.height(12.dp))
                    LoadingStageCard(
                        icon = Icons.Default.Memory,
                        title = "模型与本机服务检查",
                        detail = when {
                            needsModelDirectoryAccess -> "需要授权访问 $MODEL_ROOT 后才能启动内置推理服务。"
                            npuLoading -> "\u5185\u7f6e\u63a8\u7406\u670d\u52a1\u6b63\u5728\u52a0\u8f7d\u672c\u5730\u6a21\u578b\u3002"
                            loadingText.contains("超时") -> "推理服务尚未就绪，请检查模型目录与设备算力。"
                            else -> "正在检查 127.0.0.1:8910/v1/models 与本地模型状态。"
                        },
                        statusText = when {
                            needsModelDirectoryAccess -> "需授权"
                            npuLoading -> "加载中"
                            genieReady -> "已就绪"
                            loadingText.contains("超时") -> "超时"
                            else -> "检查中"
                        },
                        containerColor = Color.White,
                        accentColor = when {
                            needsModelDirectoryAccess -> WarningColor
                            npuLoading || genieReady -> Accent
                            loadingText.contains("超时") -> DangerColor
                            else -> Accent
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    LoadingStageCard(
                        icon = Icons.Default.Verified,
                        title = "系统就绪确认",
                        detail = if (genieReady) "PocketOps \u5de5\u4f5c\u53f0\u5df2\u5b8c\u6210\u542f\u52a8\uff0c\u53ef\u4ee5\u5f00\u59cb\u8bca\u65ad\u3002" else loadingText,
                        statusText = if (genieReady) "可诊断" else "等待中",
                        containerColor = Color.White,
                        accentColor = if (genieReady) SuccessColor else TextMuted,
                    )
                    Spacer(Modifier.height(18.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, BorderSoft),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("同步日志", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain)
                            Spacer(Modifier.height(10.dp))
                            syncSteps.takeLast(8).forEach { step ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Default.CheckCircle, null, tint = SuccessColor, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(step.label, fontSize = 13.sp, color = TextBody, modifier = Modifier.weight(1f))
                                    if (step.detail.isNotEmpty()) {
                                        Text(step.detail, fontSize = 11.sp, color = TextSubtle, textAlign = TextAlign.End)
                                    }
                                }
                            }
                            if (npuLoading) {
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(999.dp)),
                                    color = Accent,
                                    trackColor = AccentSoft,
                                )
                            }
                        }
                    }

                    if (!isCheckingService && !genieReady) {
                        Spacer(Modifier.height(18.dp))
                        if (needsModelDirectoryAccess) {
                            Button(
                                onClick = {
                                    try {
                                        modelDirectoryAccessLauncher.launch(createManageModelDirectoryAccessIntent(context))
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to open model access settings", e)
                                        loadingText = "无法打开权限设置: ${e.message}"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("授权模型访问")
                            }
                            Spacer(Modifier.height(10.dp))
                        }

                        OutlinedButton(
                            onClick = { refreshGenieServiceStatusWithDemo() },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, BorderSoft),
                        ) {
                            Text(if (needsModelDirectoryAccess) "重新检查" else "重试")
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onConfigureServer, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text(if (demoServerBaseUrl.isBlank()) "\u914d\u7f6e\u7535\u8111\u670d\u52a1" else "\u4fee\u6539\u7535\u8111\u670d\u52a1")
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            } else {
                // Chat area
                Box(Modifier.weight(1f)) {
                    if (messages.isEmpty()) {
                        EmptyState { text -> sendMessage(text, null, null) }
                    } else {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            items(messages) { msg ->
                                MessageBubble(
                                    msg,
                                    onGenerateWorkOrder = {
                                        selectedWorkOrderMessage = msg
                                        clearDiagnosisContextAfterWorkOrder()
                                    },
                                    onShowGraph = { if (msg.graphJson.isNotEmpty()) selectedGraphJson = msg.graphJson },
                                )
                            }
                            if (isGenerating) { item { TypingIndicator() } }
                        }
                    }
                }
                InputBar(enabled = !isGenerating) { text, bmp, videoUri -> sendMessage(text, bmp, videoUri) }
            }
        }
    }

    // Bluetooth Dialog
    if (showBluetoothDialog) {
        BluetoothDiagnosticDialog(
            onDismiss = { showBluetoothDialog = false },
            onConfirm = { diagnosticPayload ->
                showBluetoothDialog = false
                sendMessage(diagnosticPayload.toKnowledgeGraphPrompt(), null, null)
            },
        )
    }

    // Work Order Dialog
    selectedWorkOrderMessage?.let { workOrderMessage ->
        WorkOrderDialog(
            message = workOrderMessage,
            demoServerBaseUrl = demoServerBaseUrl,
            session = session,
            onSaveRecord = { userText, aiText -> appendHistory(userText, aiText) },
            onWorkOrderCompleted = { clearDiagnosisContextAfterWorkOrder() },
            onDismiss = { selectedWorkOrderMessage = null },
        )
    }

    // Knowledge Graph Visualization
    selectedGraphJson?.let { json ->
        GraphVizSheet(graphJson = json, onDismiss = { selectedGraphJson = null })
    }

    // History Sheet
    if (showHistorySheet) {
        HistorySheet(
            history = diagnosisHistory,
            onDismiss = { showHistorySheet = false },
            onClear = {
                diagnosisHistory.clear()
                clearHistory(context)
                showHistorySheet = false
            },
            onRestore = { entry ->
                messages.add(PocketMessage(text = entry.userText, isUser = true))
                messages.add(PocketMessage(text = entry.aiText, isUser = false))
                conversationHistory.add("user" to entry.userText)
                conversationHistory.add("assistant" to entry.aiText)
                activeEquipmentContext = knowledgeGraph.findEquipment("${entry.userText}\n${entry.aiText}").firstOrNull() ?: activeEquipmentContext
                latestVisualContextText = ""
                latestVisualEquipmentContext = null
                visualLookupScopeActive = false
            },
        )
    }
}

// ==================== Empty State ====================

@Composable
private fun EmptyState(onChipClick: (String) -> Unit) {
    val chips = listOf("3号叉车举升缓慢", "5号叉车无法启动", "7号叉车转向沉重", "2号叉车发动机过热", "9号叉车异响")
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 20.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, BorderSoft),
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PocketOpsBadge(size = 48.dp, cornerRadius = 16.dp, iconSize = 24.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("PocketOps \u5f85\u547d\u4e2d", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextMain)
                        Text("开始记录故障现象、上传现场资料或恢复历史诊断。", fontSize = 13.sp, color = TextMuted, lineHeight = 20.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip("系统待命", SuccessSoft, SuccessColor)
                    StatusChip("\u56fe\u8c31\u8bca\u65ad", AccentSoft, Accent)
                    StatusChip("可生成工单", SurfaceMuted, TextMuted)
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = SurfaceMuted),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("4,580+", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
                            Text("可用知识节点", fontSize = 12.sp, color = TextMuted)
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = SurfaceMuted),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("3", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
                            Text("诊断入口模式", fontSize = 12.sp, color = TextMuted)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("快速诊断模板", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain, modifier = Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            chips.forEach { chip ->
                Surface(
                    modifier = Modifier.clickable { onChipClick(chip) },
                    color = Color.White,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, BorderSoft),
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Build, null, Modifier.size(14.dp), tint = Accent)
                        Spacer(Modifier.width(6.dp))
                        Text(chip, fontSize = 13.sp, color = TextBody, maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, BorderSoft),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("开始前建议", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain)
                Spacer(Modifier.height(10.dp))
                listOf(
                    "优先补充设备编号、位置与当前工况。",
                    "涉及异响、过热、泄漏时建议上传图片或视频。",
                    "\u8bca\u65ad\u5b8c\u6210\u540e\u53ef\u76f4\u63a5\u751f\u6210\u5de5\u5355\u5e76\u5bfc\u51fa\u5de5\u5355\u6587\u6863\u3002",
                ).forEach { line ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 7.dp).size(6.dp).background(Accent, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(line, fontSize = 13.sp, color = TextBody, lineHeight = 20.sp)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

// ==================== Message Bubble ====================

@Composable
private fun MessageBubble(msg: PocketMessage, onGenerateWorkOrder: () -> Unit = {}, onShowGraph: () -> Unit = {}) {
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!msg.isUser) {
            PocketOpsBadge(
                modifier = Modifier.padding(top = 2.dp, end = 8.dp),
                size = 30.dp,
                cornerRadius = 10.dp,
                iconSize = 16.dp,
            )
        }
        Column(Modifier.widthIn(max = screenW * 0.82f), horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start) {
            // Image
            msg.bitmap?.let { bmp ->
                Image(bmp.asImageBitmap(), null, Modifier.size(180.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.height(4.dp))
            }
            // Report card or text bubble
            if (msg.report != null) {
                DiagnosticCard(msg.report, onGenerateWorkOrder, onShowGraph = { if (msg.graphJson.isNotEmpty()) onShowGraph() })
            } else if (msg.text.isNotEmpty()) {
                if (msg.isUser) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp),
                        color = Accent,
                    ) {
                        Text(msg.text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                } else if (msg.text.length > 50 && !msg.isImageResponse) {
                    // AI diagnosis response as structured card
                    LlmDiagnosticCard(msg.text, onGenerateWorkOrder)
                } else {
                    // Short AI response or image response as simple bubble
                    Surface(
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
                        color = AiBubble,
                        shadowElevation = 1.dp,
                        border = BorderStroke(1.dp, BorderSoft),
                    ) {
                        MarkdownText(
                            text = msg.text,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            textColor = TextMain,
                        )
                    }
                }

                // Related work orders from knowledge graph (for non-RAG responses)
                if (msg.relatedWorkOrders.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    RelatedWorkOrdersSection(msg.relatedWorkOrders)
                }
            }
        }
    }
}

// ==================== Diagnostic Card ====================

@Composable
private fun DiagnosticCard(r: DiagnosticReport, onGenerateWorkOrder: () -> Unit = {}, onShowGraph: () -> Unit = {}) {
    val severity = r.symptom.properties["severity"] ?: ""
    val (severityBg, severityFg) = severityColors(severity)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        border = BorderStroke(1.dp, BorderSoft),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("诊断报告", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("\u56fe\u8c31\u8bca\u65ad \u00b7 ${r.nodeCount} \u8282\u70b9", color = TextMuted, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    StatusChip("\u56fe\u8c31\u8bca\u65ad", AccentSoft, Accent)
                    if (severity.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        StatusChip(severity, severityBg, severityFg)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Surface(color = SurfaceMuted, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp)) {
                    InfoRow("设备", "${r.equipment.label}（${r.equipment.properties["brand"]} ${r.equipment.properties["model"]}）")
                    InfoRow("位置", r.equipment.properties["location"] ?: "")
                    InfoRow("故障", r.symptom.label)
                    if (severity.isNotBlank()) InfoRow("严重度", severity)
                }
            }

            Spacer(Modifier.height(12.dp))
            Label("故障原因")
            r.causes.forEach { c ->
                val prob = ((c.properties["probability"]?.toFloatOrNull() ?: 0f) * 100).toInt()
                val barColor = when {
                    prob >= 75 -> DangerColor
                    prob >= 55 -> WarningColor
                    else -> Accent
                }
                Surface(color = SurfaceSoft, shape = RoundedCornerShape(14.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(c.label, fontSize = 13.sp, color = TextBody, modifier = Modifier.weight(1f))
                        LinearProgressIndicator(
                            { prob / 100f },
                            Modifier.width(76.dp).height(8.dp).clip(RoundedCornerShape(999.dp)),
                            color = barColor,
                            trackColor = Color.White,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("${prob}%", fontSize = 12.sp, color = TextMuted, modifier = Modifier.width(38.dp), textAlign = TextAlign.End)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (r.steps.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Label("维修步骤")
                r.steps.forEachIndexed { i, s ->
                    Surface(color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, BorderSoft)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
                            Text("${i + 1}", Modifier.size(24.dp).background(Accent, CircleShape), color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 24.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextMain)
                                val meta = listOfNotNull(
                                    s.properties["duration"]?.takeIf { it.isNotBlank() },
                                    s.properties["tool"]?.takeIf { it.isNotBlank() },
                                ).joinToString(" · ")
                                if (meta.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(meta, fontSize = 11.sp, color = TextMuted)
                                }
                            }
                        }
                    }
                    if (i != r.steps.lastIndex) {
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            if (r.parts.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Label("所需备件")
                r.parts.forEach { p ->
                    Surface(color = SurfaceMuted, shape = RoundedCornerShape(14.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(p.label, fontSize = 13.sp, color = TextBody, modifier = Modifier.weight(1f))
                            Text("${p.properties["spec"] ?: ""} · 库存${p.properties["stock"] ?: ""}", fontSize = 11.sp, color = TextMuted, textAlign = TextAlign.End)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (r.personnel.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Label("推荐人员")
                r.personnel.forEach { p ->
                    Surface(color = SurfaceMuted, shape = RoundedCornerShape(14.dp)) {
                        Text(
                            "${p.label}：${p.properties["skill"] ?: ""}专家，${p.properties["experience"] ?: ""}经验",
                            fontSize = 13.sp,
                            color = TextBody,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (r.workOrders.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Label("相似工单")
                r.workOrders.forEach { wo ->
                    var expanded by remember { mutableStateOf(false) }
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceMuted),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, BorderSoft),
                    ) {
                        Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${wo.label}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextMain, modifier = Modifier.weight(1f))
                                StatusChip("历史工单", AccentSoft, Accent)
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    null, tint = TextMuted, modifier = Modifier.size(18.dp),
                                )
                            }
                            Text(wo.properties["date"] ?: "", fontSize = 11.sp, color = TextSubtle, modifier = Modifier.padding(top = 4.dp))
                            if (expanded) {
                                Spacer(Modifier.height(8.dp))
                                Divider(color = BorderSoft)
                                Spacer(Modifier.height(8.dp))
                                WorkOrderDetailRow("设备", wo.properties["equipment"] ?: "")
                                WorkOrderDetailRow("故障", wo.properties["fault"] ?: "")
                                WorkOrderDetailRow("处理方案", wo.properties["resolution"] ?: "")
                                WorkOrderDetailRow("停机时间", wo.properties["downtime"] ?: "")
                                WorkOrderDetailRow("维修费用", wo.properties["cost"] ?: "")
                                WorkOrderDetailRow("工单状态", "已完成")
                                WorkOrderDetailRow("审核人", "张工")
                            } else {
                                Text("方案：${wo.properties["resolution"]} · 停机${wo.properties["downtime"]} · ${wo.properties["cost"]}", fontSize = 11.sp, color = TextMuted, lineHeight = 18.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onGenerateWorkOrder,
                    Modifier.weight(1f).height(42.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("生成工单", fontSize = 14.sp) }
                OutlinedButton(
                    onClick = onShowGraph,
                    Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BorderSoft),
                ) {
                    Icon(Icons.Default.AccountTree, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("知识图谱", fontSize = 14.sp)
                }
            }
        }
    }
}

// ==================== LLM Diagnostic Card (non-RAG) ====================

@Composable
private fun LlmDiagnosticCard(text: String, onGenerateWorkOrder: () -> Unit) {
    val report = remember(text) { parseLlmReport(text) }

    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        border = BorderStroke(1.dp, BorderSoft),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("诊断报告", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("\u7aef\u4fa7\u6a21\u578b\u8bca\u65ad", color = TextMuted, fontSize = 12.sp)
                }
                report?.severity?.takeIf { it.isNotBlank() }?.let { severity ->
                    val (severityBg, severityFg) = severityColors(severity)
                    StatusChip(severity, severityBg, severityFg)
                } ?: StatusChip("\u7aef\u4fa7\u6a21\u578b", AccentSoft, Accent)
            }
            Spacer(Modifier.height(12.dp))

            if (report != null) {
                // Structured card from parsed JSON
                Surface(color = SurfaceMuted, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        InfoRow("设备", report.equipment)
                        if (report.location.isNotBlank()) InfoRow("位置", report.location)
                        InfoRow("故障", report.symptom)
                        if (report.severity.isNotBlank()) InfoRow("严重度", report.severity)
                    }
                }

                if (report.causes.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Label("故障原因")
                    report.causes.forEach { (name, prob) ->
                        val barColor = when {
                            prob >= 75 -> DangerColor
                            prob >= 55 -> WarningColor
                            else -> Accent
                        }
                        Surface(color = SurfaceSoft, shape = RoundedCornerShape(14.dp)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(name, fontSize = 13.sp, color = TextBody, modifier = Modifier.weight(1f))
                                LinearProgressIndicator(
                                    { prob / 100f },
                                    Modifier.width(76.dp).height(8.dp).clip(RoundedCornerShape(999.dp)),
                                    color = barColor,
                                    trackColor = Color.White,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("${prob}%", fontSize = 12.sp, color = TextMuted, modifier = Modifier.width(38.dp), textAlign = TextAlign.End)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                if (report.steps.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Label("维修步骤")
                    report.steps.forEachIndexed { i, (title, duration, tool) ->
                        Surface(color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, BorderSoft)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
                                Text("${i + 1}", Modifier.size(24.dp).background(Accent, CircleShape), color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 24.sp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextMain)
                                    val meta = listOf(duration.takeIf { it.isNotBlank() }, tool.takeIf { it.isNotBlank() }).joinToString(" · ")
                                    if (meta.isNotBlank()) Text(meta, fontSize = 11.sp, color = TextMuted)
                                }
                            }
                        }
                        if (i != report.steps.lastIndex) Spacer(Modifier.height(8.dp))
                    }
                }

                if (report.parts.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Label("所需备件")
                    report.parts.forEach { (name, spec, stock) ->
                        Surface(color = SurfaceMuted, shape = RoundedCornerShape(14.dp)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(name, fontSize = 13.sp, color = TextBody, modifier = Modifier.weight(1f))
                                Text("$spec · 库存$stock", fontSize = 11.sp, color = TextMuted, textAlign = TextAlign.End)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                if (report.personnel.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Label("推荐人员")
                    report.personnel.forEach { (name, skill, exp) ->
                        Surface(color = SurfaceMuted, shape = RoundedCornerShape(14.dp)) {
                            Text("$name：${skill}专家，${exp}经验", fontSize = 13.sp, color = TextBody, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                if (report.workOrders.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Label("相似工单")
                    report.workOrders.forEach { (id, date, resolution) ->
                        Card(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceMuted),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, BorderSoft),
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text("$id（$date）", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextMain)
                                Text("方案：$resolution", fontSize = 11.sp, color = TextMuted, lineHeight = 18.sp)
                            }
                        }
                    }
                }
            } else {
                // Fallback: render as Markdown
                Surface(color = SurfaceMuted, shape = RoundedCornerShape(14.dp)) {
                    MarkdownText(text = text, modifier = Modifier.padding(14.dp), textColor = TextMain)
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onGenerateWorkOrder,
                Modifier.fillMaxWidth().height(42.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(12.dp),
            ) { Text("生成工单报告", fontSize = 14.sp) }
        }
    }
}

@Composable private fun InfoRow(label: String, value: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text(label, fontSize = 12.sp, color = TextMuted, modifier = Modifier.width(56.dp))
        Text(value, fontSize = 13.sp, color = TextBody, lineHeight = 19.sp)
    }
}

@Composable private fun WorkOrderDetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, fontSize = 12.sp, color = TextMuted, modifier = Modifier.width(72.dp))
        Text(value, fontSize = 13.sp, color = TextBody, lineHeight = 19.sp)
    }
}

@Composable private fun Label(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun RelatedWorkOrdersSection(workOrders: List<GraphNode>) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
        border = BorderStroke(1.dp, BorderSoft),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, null, tint = Accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("知识库相似工单", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain)
                Spacer(Modifier.weight(1f))
                StatusChip("${workOrders.size} 条记录", SurfaceMuted, TextMuted)
            }
            Spacer(Modifier.height(8.dp))
            workOrders.forEach { wo ->
                var expanded by remember { mutableStateOf(false) }
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceMuted),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, BorderSoft),
                ) {
                    Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(wo.label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextMain, modifier = Modifier.weight(1f))
                            Text(wo.properties["date"] ?: "", fontSize = 11.sp, color = TextSubtle)
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                null, tint = TextMuted, modifier = Modifier.size(18.dp),
                            )
                        }
                        if (expanded) {
                            Spacer(Modifier.height(6.dp))
                            Divider(color = BorderSoft)
                            Spacer(Modifier.height(6.dp))
                            WorkOrderDetailRow("设备", wo.properties["equipment"] ?: "")
                            WorkOrderDetailRow("故障", wo.properties["fault"] ?: "")
                            WorkOrderDetailRow("处理方案", wo.properties["resolution"] ?: "")
                            WorkOrderDetailRow("停机时间", wo.properties["downtime"] ?: "")
                            WorkOrderDetailRow("维修费用", wo.properties["cost"] ?: "")
                            WorkOrderDetailRow("工单状态", "已完成")
                        } else {
                            Text("${wo.properties["equipment"]} · ${wo.properties["fault"]} · ${wo.properties["resolution"]}", fontSize = 11.sp, color = TextMuted, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

// ==================== Typing Indicator ====================

@Composable
private fun TypingIndicator() {
    val t = rememberInfiniteTransition(label = "typing")
    val dots = (0..2).map { i ->
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(600, delayMillis = i * 200), RepeatMode.Reverse), label = "d$i")
    }
    Row(
        Modifier
            .padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
            .background(AiBubble, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dots.forEach { a -> Box(Modifier.size((6 + 3 * a.value).dp).background(TextSubtle.copy(alpha = 0.4f + 0.4f * a.value), CircleShape)) }
    }
}

// ==================== Bluetooth Diagnostic Dialog ====================

@Composable
private fun BluetoothDiagnosticDialog(onDismiss: () -> Unit, onConfirm: (BluetoothDiagnosticPayload) -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val payload = remember { sampleBluetoothDiagnosticPayload() }
    val steps = listOf("搜索设备", "建立连接", "读取故障码", "读取参数")

    LaunchedEffect(Unit) {
        for (i in 1..4) { kotlinx.coroutines.delay(1200); step = i }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AccentSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Bluetooth, null, tint = Accent)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("蓝牙诊断采集", fontWeight = FontWeight.Bold, color = TextMain)
                    Text("连接现场设备并回填故障码参数", fontSize = 12.sp, color = TextMuted)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (step < 4) {
                    Surface(color = SurfaceMuted, shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text("连接过程", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)),
                                color = Accent,
                                trackColor = AccentSoft,
                            )
                            Spacer(Modifier.height(12.dp))
                            steps.forEachIndexed { i, s ->
                                val active = i == step
                                val done = i < step
                                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (done) Icons.Default.CheckCircle else if (active) Icons.Default.Refresh else Icons.Default.RadioButtonUnchecked,
                                        null,
                                        tint = if (done) SuccessColor else if (active) Accent else TextSubtle,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(s, fontSize = 14.sp, color = if (done || active) TextBody else TextMuted, modifier = Modifier.weight(1f))
                                    Text(if (done) "完成" else if (active) "进行中" else "待执行", fontSize = 11.sp, color = if (done) SuccessColor else if (active) Accent else TextSubtle)
                                }
                            }
                        }
                    }
                } else {
                    Surface(color = SuccessSoft, shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = SuccessColor)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("设备已连接: ${payload.deviceName}", fontWeight = FontWeight.Medium, color = TextMain)
                                Text("蓝牙通道稳定，可用于后续诊断输入。", fontSize = 12.sp, color = TextMuted)
                            }
                        }
                    }
                    Surface(color = SurfaceMuted, shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("\u6545\u969c\u8bca\u65ad\u7801", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextMain)
                                Spacer(Modifier.weight(1f))
                                StatusChip("${payload.faultCodes.size} 项异常", DangerSoft, DangerColor)
                            }
                            Spacer(Modifier.height(10.dp))
                            payload.faultCodes.forEach { faultCode ->
                                Surface(color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, BorderSoft)) {
                                    Text(
                                        "${faultCode.code} - ${faultCode.description}",
                                        fontSize = 13.sp,
                                        color = DangerColor,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                    Surface(color = SurfaceMuted, shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("关键参数", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextMain)
                                Spacer(Modifier.weight(1f))
                                StatusChip("${payload.parameters.count { it.isAbnormal() }} 项异常", WarningSoft, WarningColor)
                            }
                            Spacer(Modifier.height(10.dp))
                            payload.parameters.forEach { parameter ->
                                val isError = parameter.isAbnormal()
                                Surface(color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, BorderSoft)) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(8.dp).background(if (isError) WarningColor else SuccessColor, CircleShape))
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "${parameter.name}: ${parameter.value} (${parameter.status})",
                                            fontSize = 13.sp,
                                            color = if (isError) WarningColor else TextBody,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (step >= 4) {
                Button(onClick = { onConfirm(payload) },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("确认并诊断") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = TextMuted) } },
    )
}

// ==================== Work Order Dialog ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkOrderDialog(
    message: PocketMessage,
    demoServerBaseUrl: String,
    session: PocketOpsSession,
    onSaveRecord: (String, String) -> Unit,
    onWorkOrderCompleted: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
    val initialWorkOrder = remember(message.text, message.report, message.relatedWorkOrders) {
        buildWorkOrderDocumentData(message)
    }
    var equipment by remember(initialWorkOrder) { mutableStateOf(initialWorkOrder.equipment) }
    var location by remember(initialWorkOrder) { mutableStateOf(initialWorkOrder.location) }
    var symptom by remember(initialWorkOrder) { mutableStateOf(initialWorkOrder.symptom) }
    var severity by remember(initialWorkOrder) { mutableStateOf(initialWorkOrder.severity) }
    var status by remember(initialWorkOrder) { mutableStateOf(initialWorkOrder.status) }
    var summary by remember(initialWorkOrder) { mutableStateOf(initialWorkOrder.summary) }
    val causes = remember(initialWorkOrder) { mutableStateListOf<String>().apply { addAll(initialWorkOrder.causes) } }
    val parts = remember(initialWorkOrder) { mutableStateListOf<String>().apply { addAll(initialWorkOrder.parts) } }
    val steps = remember(initialWorkOrder) { mutableStateListOf<String>().apply { addAll(initialWorkOrder.steps) } }
    val personnel = remember(initialWorkOrder) { mutableStateListOf<String>().apply { addAll(initialWorkOrder.personnel) } }
    val relatedWorkOrders = remember(initialWorkOrder) { mutableStateListOf<String>().apply { addAll(initialWorkOrder.relatedWorkOrders) } }
    val workOrderId = remember(message.text, message.report) { "\u5de5\u5355-${System.currentTimeMillis().toString().takeLast(8)}" }
    val createdAt = remember(message.text, message.report) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
    }

    LaunchedEffect(workOrderId) {
        onWorkOrderCompleted()
    }

    val editedWorkOrder =
        WorkOrderDocumentData(
            equipment = equipment.trim(),
            location = location.trim(),
            symptom = symptom.trim(),
            severity = severity.trim(),
            status = status.trim().ifBlank { "待处理" },
            summary = summary.trim(),
            causes = causes.map { it.trim() }.filter { it.isNotBlank() },
            parts = parts.map { it.trim() }.filter { it.isNotBlank() },
            steps = steps.map { it.trim() }.filter { it.isNotBlank() },
            personnel = personnel.map { it.trim() }.filter { it.isNotBlank() },
            relatedWorkOrders = relatedWorkOrders.map { it.trim() }.filter { it.isNotBlank() },
        )
    val normalizedDemoServerBaseUrl = remember(demoServerBaseUrl) {
        normalizeDemoServerBaseUrl(demoServerBaseUrl)
    }
    var remoteMaterials by remember(message.text, demoServerBaseUrl) { mutableStateOf<List<DemoMaterial>>(emptyList()) }
    var isLoadingMaterials by remember(message.text, demoServerBaseUrl) { mutableStateOf(false) }
    var materialsError by remember(message.text, demoServerBaseUrl) { mutableStateOf<String?>(null) }
    var downloadingMaterialId by remember(message.text, demoServerBaseUrl) { mutableStateOf<String?>(null) }
    var lastSavedRecordText by remember(workOrderId) { mutableStateOf<String?>(null) }

    fun saveEditedRecordIfChanged(): Boolean {
        val recordText = buildWorkOrderRecordText(workOrderId, createdAt, editedWorkOrder)
        if (lastSavedRecordText == recordText) return false
        onSaveRecord(
            "人工编辑工单：${editedWorkOrder.equipment.ifBlank { workOrderId }}",
            recordText,
        )
        lastSavedRecordText = recordText
        onWorkOrderCompleted()
        return true
    }

    fun loadMaterials() {
        if (normalizedDemoServerBaseUrl.isBlank()) {
            materialsError = "\u672a\u914d\u7f6e\u7535\u8111\u670d\u52a1\uff0c\u8bf7\u5148\u5728\u52a0\u8f7d\u9875\u6216\u9876\u90e8\u64cd\u4f5c\u680f\u5b8c\u6210\u914d\u7f6e"
            remoteMaterials = emptyList()
            return
        }

        scope.launch {
            isLoadingMaterials = true
            materialsError = null
            try {
                remoteMaterials = withContext(Dispatchers.IO) {
                    queryDemoMaterials(normalizedDemoServerBaseUrl, message, session.accessToken)
                }
            } catch (e: Exception) {
                remoteMaterials = emptyList()
                materialsError = e.message ?: "资料接口不可用"
            } finally {
                isLoadingMaterials = false
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = BgColor) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .verticalScroll(scrollState)
                .navigationBarsPadding()
                .padding(20.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("工单报告", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextMain)
                    Text("人工确认后再导出或保存记录", fontSize = 12.sp, color = TextMuted)
                }
                StatusChip(editedWorkOrder.status, AccentSoft, Accent)
            }
            Spacer(Modifier.height(16.dp))

            EditableWorkOrderSummaryCard(
                workOrderId = workOrderId,
                createdAt = createdAt,
                equipment = equipment,
                onEquipmentChange = { equipment = it },
                location = location,
                onLocationChange = { location = it },
                symptom = symptom,
                onSymptomChange = { symptom = it },
                severity = severity,
                onSeverityChange = { severity = it },
                status = status,
                onStatusChange = { status = it },
            )
            Spacer(Modifier.height(12.dp))

            EditableTextBlockCard(title = "故障摘要", value = summary, onValueChange = { summary = it })
            EditableListCard(title = "可能原因", lines = causes, newItemLabel = "新增原因")
            EditableListCard(title = "维修步骤", lines = steps, newItemLabel = "新增步骤")
            EditableListCard(title = "所需备件", lines = parts, newItemLabel = "新增备件")
            EditableListCard(title = "推荐人员", lines = personnel, newItemLabel = "新增人员")
            EditableListCard(title = "相似工单参考", lines = relatedWorkOrders, newItemLabel = "新增参考")

            DemoMaterialsCard(
                materials = remoteMaterials,
                isLoading = isLoadingMaterials,
                error = materialsError,
                demoServerConfigured = normalizedDemoServerBaseUrl.isNotBlank(),
                downloadingMaterialId = downloadingMaterialId,
                onDownload = { material ->
                    scope.launch {
                        downloadingMaterialId = material.id
                        try {
                            val file = withContext(Dispatchers.IO) {
                                downloadDemoMaterial(context, material, session.accessToken)
                            }
                            val opened = openDemoMaterial(context, file, material.mimeType)
                            if (!opened) {
                                Toast.makeText(context, "资料已下载: ${file.name}", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "资料下载失败: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            downloadingMaterialId = null
                        }
                    }
                },
            )

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = { loadMaterials() },
                enabled = !isLoadingMaterials,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderSoft),
            ) {
                Text(if (remoteMaterials.isEmpty()) "获取资料" else "刷新资料")
            }
            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    val saved = saveEditedRecordIfChanged()
                    Toast.makeText(
                        context,
                        if (saved) "编辑后的工单已保存到诊断历史" else "当前工单内容已保存",
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Accent.copy(alpha = 0.35f)),
            ) {
                Text("保存修改记录", color = Accent)
            }
            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    if (exportWorkOrderPdf(
                            context = context,
                            workOrderId = workOrderId,
                            createdAt = createdAt,
                            workOrder = editedWorkOrder,
                        )
                    ) {
                        saveEditedRecordIfChanged()
                        onWorkOrderCompleted()
                        onDismiss()
                    }
                },
                Modifier.fillMaxWidth().height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(12.dp),
            ) { Text("\u5bfc\u51fa\u5de5\u5355\u6587\u6863") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun WoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(label, fontSize = 13.sp, color = TextMuted, modifier = Modifier.width(72.dp))
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = TextMain,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun EditableWorkOrderSummaryCard(
    workOrderId: String,
    createdAt: String,
    equipment: String,
    onEquipmentChange: (String) -> Unit,
    location: String,
    onLocationChange: (String) -> Unit,
    symptom: String,
    onSymptomChange: (String) -> Unit,
    severity: String,
    onSeverityChange: (String) -> Unit,
    status: String,
    onStatusChange: (String) -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, BorderSoft),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("工单摘要", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain)
            Spacer(Modifier.height(10.dp))
            WoRow("工单号", workOrderId)
            WoRow("创建时间", createdAt)
            Spacer(Modifier.height(8.dp))
            EditableWorkOrderField("设备", equipment, onEquipmentChange)
            EditableWorkOrderField("位置", location, onLocationChange)
            EditableWorkOrderField("故障现象", symptom, onSymptomChange)
            EditableWorkOrderField("严重程度", severity, onSeverityChange)
            EditableWorkOrderField("状态", status, onStatusChange)
        }
    }
}

@Composable
private fun EditableWorkOrderField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    minLines: Int = 1,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = minLines,
            maxLines = if (minLines > 1) 5 else 1,
            singleLine = minLines == 1,
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = TextMain),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = BorderSoft,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
            ),
            shape = RoundedCornerShape(12.dp),
        )
    }
}

@Composable
private fun EditableTextBlockCard(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderSoft),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
            Spacer(Modifier.height(8.dp))
            EditableWorkOrderField("内容", value, onValueChange, minLines = 3)
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun EditableListCard(
    title: String,
    lines: MutableList<String>,
    newItemLabel: String,
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderSoft),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { lines.add("") }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(newItemLabel, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            if (lines.isEmpty()) {
                Surface(color = SurfaceMuted, shape = RoundedCornerShape(12.dp)) {
                    Text(
                        "暂无内容，可点击右上角新增。",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        fontSize = 12.sp,
                        color = TextMuted,
                    )
                }
            } else {
                lines.forEachIndexed { index, line ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        OutlinedTextField(
                            value = line,
                            onValueChange = { lines[index] = it },
                            modifier = Modifier.weight(1f),
                            minLines = 1,
                            maxLines = 4,
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = TextMain),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Accent,
                                unfocusedBorderColor = BorderSoft,
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { lines.removeAt(index) },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "删除", tint = DangerColor)
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun WorkOrderSectionCard(title: String, lines: List<String>) {
    if (lines.isEmpty()) return

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderSoft),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
            Spacer(Modifier.height(8.dp))
            lines.forEach { line ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text("•", fontSize = 13.sp, color = Accent, modifier = Modifier.padding(top = 1.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        line,
                        fontSize = 13.sp,
                        color = TextBody,
                        lineHeight = 20.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun DemoMaterialsCard(
    materials: List<DemoMaterial>,
    isLoading: Boolean,
    error: String?,
    demoServerConfigured: Boolean,
    downloadingMaterialId: String?,
    onDownload: (DemoMaterial) -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderSoft),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("远程资料", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
                if (materials.isNotEmpty()) {
                    StatusChip("${materials.size} 份", SurfaceMuted, TextMuted)
                }
            }
            Spacer(Modifier.height(8.dp))

            when {
                isLoading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("\u6b63\u5728\u83b7\u53d6\u7535\u8111\u670d\u52a1\u8d44\u6599...", fontSize = 13.sp, color = TextMuted)
                    }
                }

                error != null -> {
                    Text(error, fontSize = 13.sp, color = WarningColor, lineHeight = 20.sp)
                }

                materials.isNotEmpty() -> {
                    materials.forEach { material ->
                        Card(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceMuted),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, BorderSoft),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        material.toDisplayTitle().ifBlank { material.id },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextMain,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        listOfNotNull(
                                            material.type.toDemoTypeLabel(),
                                            material.sizeBytes.toDemoSizeLabel(),
                                            material.category.toDemoCategoryLabel().takeIf { it.isNotBlank() },
                                        ).joinToString(" · "),
                                        fontSize = 11.sp,
                                        color = TextMuted,
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                OutlinedButton(
                                    onClick = { onDownload(material) },
                                    enabled = downloadingMaterialId == null,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, BorderSoft),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        if (downloadingMaterialId == material.id) "下载中" else "下载",
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }
                }

                demoServerConfigured -> {
                    Text("\u70b9\u51fb\u4e0b\u65b9\u201c\u83b7\u53d6\u8d44\u6599\u201d\u540e\uff0c\u5c06\u4ece\u7535\u8111\u670d\u52a1\u8fd4\u56de\u4f5c\u4e1a\u624b\u518c\u3001\u8bf4\u660e\u624b\u518c\u548c\u9644\u4ef6\u3002", fontSize = 13.sp, color = TextMuted, lineHeight = 20.sp)
                }

                else -> {
                    Text("\u672a\u914d\u7f6e\u7535\u8111\u670d\u52a1\uff0c\u5f53\u524d\u53ea\u652f\u6301\u5bfc\u51fa\u672c\u5730\u5de5\u5355\u6587\u6863\u3002", fontSize = 13.sp, color = TextMuted, lineHeight = 20.sp)
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

// ==================== Knowledge Graph Visualization ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GraphVizSheet(graphJson: String, onDismiss: () -> Unit) {
    data class GNode(val id: String, val type: String, val label: String)
    data class GEdge(val source: String, val target: String, val relation: String, val prob: String)

    val parsed = remember(graphJson) {
        try {
            val obj = org.json.JSONObject(graphJson)
            val centerId = obj.optString("centerNodeId")
            val nodesArr = obj.getJSONArray("nodes")
            val edgesArr = obj.getJSONArray("edges")
            val nodes = (0 until nodesArr.length()).map { i ->
                val n = nodesArr.getJSONObject(i); GNode(n.getString("id"), n.getString("type"), n.getString("label"))
            }
            val edges = (0 until edgesArr.length()).map { i ->
                val e = edgesArr.getJSONObject(i)
                GEdge(e.getString("source"), e.getString("target"), e.getString("relation"), e.optJSONObject("properties")?.optString("probability") ?: "")
            }
            Triple(centerId, nodes, edges)
        } catch (e: Exception) { Log.e(TAG, "GraphViz parse failed", e); null }
    }

    val typeColors = mapOf(
        "FAULT_SYMPTOM" to Color(0xFFE53935), "ROOT_CAUSE" to Color(0xFFFB8C00), "PART" to Color(0xFF43A047),
        "REPAIR_STEP" to Color(0xFF8E24AA), "PERSONNEL" to Color(0xFF1E88E5), "WORK_ORDER" to Color(0xFF78909C), "EQUIPMENT" to Color(0xFF26C6DA),
    )
    val typeLabels = mapOf(
        "FAULT_SYMPTOM" to "症状", "ROOT_CAUSE" to "原因", "PART" to "备件",
        "REPAIR_STEP" to "步骤", "PERSONNEL" to "人员", "WORK_ORDER" to "工单", "EQUIPMENT" to "设备",
    )
    val edgeLabels = mapOf(
        "HAS_SYMPTOM" to "出现症状", "CAUSED_BY" to "原因", "REQUIRES_PART" to "需要备件",
        "HAS_REPAIR_STEP" to "维修步骤", "REPAIRED_BY" to "维修人员", "HAS_WORK_ORDER" to "历史工单",
    )
    val graphTypeOrder = listOf("EQUIPMENT", "ROOT_CAUSE", "PART", "REPAIR_STEP", "PERSONNEL", "WORK_ORDER", "FAULT_SYMPTOM")

    fun buildNodePositions(
        nodes: List<GNode>,
        centerNodeId: String,
        widthPx: Float,
        heightPx: Float,
        edgeMarginPx: Float,
    ): Map<String, Offset> {
        if (nodes.isEmpty()) return emptyMap()

        val centerNode = nodes.find { it.id == centerNodeId } ?: nodes.first()
        val otherNodes =
            nodes.filter { it.id != centerNode.id }
                .sortedWith(
                    compareBy<GNode> {
                        graphTypeOrder.indexOf(it.type).let { index -> if (index >= 0) index else Int.MAX_VALUE }
                    }.thenBy { it.label },
                )

        val center = Offset(widthPx / 2f, heightPx * 0.50f)
        val positions = mutableMapOf(centerNode.id to center)
        if (otherNodes.isEmpty()) return positions

        val graphSpan = min(widthPx * 0.96f, heightPx * 0.84f)
        val useTwoRings = otherNodes.size > 8
        val innerRingCount = if (useTwoRings) min(6, otherNodes.size) else otherNodes.size
        val outerRingCount = (otherNodes.size - innerRingCount).coerceAtLeast(1)
        val singleRingRadius = graphSpan * 0.46f
        val innerRadius = graphSpan * 0.34f
        val outerRadius = graphSpan * 0.51f
        otherNodes.forEachIndexed { index, node ->
            val isOuterRing = useTwoRings && index >= innerRingCount
            val ringIndex = if (isOuterRing) index - innerRingCount else index
            val ringCount = if (isOuterRing) outerRingCount else innerRingCount
            val angleOffset = if (isOuterRing) (-PI / 2.0) + (PI / outerRingCount.toDouble()) else (-PI / 2.0)
            val angle = angleOffset + (2.0 * PI * ringIndex / ringCount.toDouble())
            val radius =
                when {
                    !useTwoRings -> singleRingRadius
                    isOuterRing -> outerRadius
                    else -> innerRadius
                }
            val x = center.x + cos(angle).toFloat() * radius
            val y = center.y + sin(angle).toFloat() * radius
            positions[node.id] = Offset(
                x.coerceIn(edgeMarginPx, widthPx - edgeMarginPx),
                y.coerceIn(edgeMarginPx, heightPx - edgeMarginPx),
            )
        }
        return positions
    }

    fun findNearestNode(
        tap: Offset,
        positions: Map<String, Offset>,
        scale: Float,
        offset: Offset,
        widthPx: Float,
        heightPx: Float,
        hitRadiusPx: Float,
    ): String? {
        val pivot = Offset(widthPx / 2f, heightPx / 2f)
        val graphTap =
            Offset(
                x = pivot.x + ((tap.x - offset.x - pivot.x) / scale),
                y = pivot.y + ((tap.y - offset.y - pivot.y) / scale),
            )
        return positions
            .map { (id, nodeOffset) ->
                val dx = nodeOffset.x - graphTap.x
                val dy = nodeOffset.y - graphTap.y
                id to (dx * dx + dy * dy)
            }
            .filter { (_, distanceSquared) -> distanceSquared <= hitRadiusPx * hitRadiusPx }
            .minByOrNull { (_, distanceSquared) -> distanceSquared }
            ?.first
    }

    fun clampGraphOffset(offset: Offset, scale: Float, widthPx: Float, heightPx: Float): Offset {
        val maxX = ((widthPx * (scale - 1f)) / 2f).coerceAtLeast(0f) + widthPx * 0.18f
        val maxY = ((heightPx * (scale - 1f)) / 2f).coerceAtLeast(0f) + heightPx * 0.18f
        return Offset(
            x = offset.x.coerceIn(-maxX, maxX),
            y = offset.y.coerceIn(-maxY, maxY),
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, dragHandle = null, containerColor = GraphBg) {
        Column(Modifier.fillMaxSize().background(GraphBg)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("知识图谱", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("故障关联视图", fontSize = 12.sp, color = Color.White.copy(alpha = 0.56f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (parsed != null) {
                        StatusChip("${parsed.second.size} 节点 · ${parsed.third.size} 关系", GraphAccent.copy(alpha = 0.16f), GraphAccent)
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(onClick = onDismiss) { Text("关闭", color = GraphAccent) }
                }
            }
            if (parsed == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("图谱数据解析失败", color = Color.White.copy(alpha = 0.55f)) }
            } else {
                val (centerId, nodes, edges) = parsed
                val nodesById = remember(nodes) { nodes.associateBy { it.id } }
                val centerNode = nodes.find { it.id == centerId } ?: nodes.firstOrNull()
                var selectedNodeId by remember(graphJson) { mutableStateOf(centerNode?.id) }
                var graphScale by remember(graphJson) { mutableFloatStateOf(1f) }
                var graphOffset by remember(graphJson) { mutableStateOf(Offset.Zero) }
                val selectedNode = nodesById[selectedNodeId] ?: centerNode
                val detailScrollState = rememberScrollState()
                Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    centerNode?.let { cn ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = typeColors[cn.type] ?: Accent), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.padding(14.dp)) {
                                Text(cn.label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("中心节点 · ${typeLabels[cn.type] ?: cn.type}", fontSize = 12.sp, color = Color.White.copy(0.7f))
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.56f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(GraphCard),
                    ) {
                        if (centerNode != null) {
                            val density = LocalDensity.current
                            val widthPx = with(density) { maxWidth.toPx() }
                            val heightPx = with(density) { maxHeight.toPx() }
                            val edgeMarginPx = with(density) { 68.dp.toPx() }
                            val positions = remember(nodes, centerNode.id, widthPx, heightPx, edgeMarginPx) {
                                buildNodePositions(nodes, centerNode.id, widthPx, heightPx, edgeMarginPx)
                            }
                            val normalNodeWidth = 116.dp
                            val centerNodeWidth = 128.dp
                            val nodeHitRadiusPx = with(density) { 24.dp.toPx() }

                            Box(
                                modifier =
                                    Modifier
                                        .matchParentSize()
                                        .pointerInput(graphJson, widthPx, heightPx) {
                                            detectTransformGestures { _, pan, zoom, _ ->
                                                val nextScale = (graphScale * zoom).coerceIn(0.8f, 2.6f)
                                                val adjustedPan =
                                                    if (nextScale <= 1f && graphScale <= 1f) {
                                                        Offset(pan.x * 0.85f, pan.y * 0.85f)
                                                    } else {
                                                        pan
                                                    }
                                                graphScale = nextScale
                                                graphOffset =
                                                    clampGraphOffset(
                                                        offset = graphOffset + adjustedPan,
                                                        scale = nextScale,
                                                        widthPx = widthPx,
                                                        heightPx = heightPx,
                                                    )
                                            }
                                        }
                                        .pointerInput(graphJson, positions, graphScale, graphOffset) {
                                            detectTapGestures { tap ->
                                                findNearestNode(
                                                    tap = tap,
                                                    positions = positions,
                                                    scale = graphScale,
                                                    offset = graphOffset,
                                                    widthPx = widthPx,
                                                    heightPx = heightPx,
                                                    hitRadiusPx = nodeHitRadiusPx,
                                                )?.let { selectedNodeId = it }
                                            }
                                        },
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .matchParentSize()
                                            .graphicsLayer {
                                                scaleX = graphScale
                                                scaleY = graphScale
                                                translationX = graphOffset.x
                                                translationY = graphOffset.y
                                            },
                                ) {
                                    androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                                        positions[centerNode.id]?.let { center ->
                                            drawCircle(
                                                color = GraphAccent.copy(alpha = 0.10f),
                                                radius = min(size.width, size.height) * 0.30f,
                                                center = center,
                                            )
                                        }

                                        edges.forEach { edge ->
                                            val start = positions[edge.source] ?: return@forEach
                                            val end = positions[edge.target] ?: return@forEach
                                            val highlighted =
                                                selectedNodeId != null &&
                                                    (selectedNodeId == edge.source || selectedNodeId == edge.target)
                                            val isCenterEdge = edge.source == centerNode.id || edge.target == centerNode.id
                                            drawLine(
                                                color =
                                                    when {
                                                        highlighted -> GraphAccent
                                                        isCenterEdge -> Color.White.copy(alpha = 0.34f)
                                                        else -> Color.White.copy(alpha = 0.16f)
                                                    },
                                                start = start,
                                                end = end,
                                                strokeWidth = if (highlighted) 4f else if (isCenterEdge) 2.6f else 1.8f,
                                            )
                                        }
                                    }

                                    nodes.forEach { node ->
                                        val pos = positions[node.id] ?: return@forEach
                                        val isCenterNode = node.id == centerNode.id
                                        val isSelected = node.id == selectedNodeId
                                        val color = if (isCenterNode) GraphAccent else (typeColors[node.type] ?: GraphAccent)
                                        val nodeWidth = if (isCenterNode) centerNodeWidth else normalNodeWidth
                                        val nodeSize = if (isCenterNode) 48.dp else 38.dp
                                        val nodeWidthPx = with(density) { nodeWidth.toPx() }
                                        val hitSize = 88.dp
                                        val hitSizePx = with(density) { hitSize.toPx() }

                                        Column(
                                            modifier =
                                                Modifier
                                                    .size(width = nodeWidth, height = 88.dp)
                                                    .offset {
                                                        IntOffset(
                                                            (pos.x - nodeWidthPx / 2f).roundToInt(),
                                                            (pos.y - hitSizePx / 2f).roundToInt(),
                                                        )
                                                    }
                                                    .zIndex(if (isSelected) 2f else if (isCenterNode) 1f else 0f),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                        ) {
                                            Surface(
                                                modifier = Modifier.size(nodeSize).clickable { selectedNodeId = node.id },
                                                color = GraphCard,
                                                shape = CircleShape,
                                                border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) color else color.copy(alpha = 0.55f)),
                                            ) {
                                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    Box(
                                                        Modifier
                                                            .size(if (isCenterNode) 18.dp else 12.dp)
                                                            .background(color, CircleShape),
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.height(5.dp))
                                            Text(
                                                node.label,
                                                fontSize = if (isCenterNode) 12.sp else 11.sp,
                                                color = Color.White.copy(alpha = if (isSelected) 0.96f else 0.74f),
                                                textAlign = TextAlign.Center,
                                                lineHeight = 13.sp,
                                                maxLines = 2,
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    StatusChip("${(graphScale * 100).roundToInt()}%", GraphAccent.copy(alpha = 0.16f), GraphAccent)
                                    Surface(
                                        modifier = Modifier.clickable {
                                            graphScale = 1f
                                            graphOffset = Offset.Zero
                                        },
                                        color = GraphBg.copy(alpha = 0.82f),
                                        shape = RoundedCornerShape(999.dp),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                    ) {
                                        Text(
                                            "重置视图",
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White,
                                        )
                                    }
                                }

                                Surface(
                                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                                    color = GraphBg.copy(alpha = 0.82f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                ) {
                                    Text(
                                        "双指缩放 · 单指拖拽",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.78f),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .weight(0.44f)
                            .verticalScroll(detailScrollState),
                    ) {
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = GraphCard),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text("图例", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    typeColors.forEach { (type, color) ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.size(8.dp).background(color, CircleShape))
                                            Spacer(Modifier.width(6.dp))
                                            Text(typeLabels[type] ?: type, fontSize = 11.sp, color = Color.White.copy(0.7f))
                                        }
                                    }
                                }
                            }
                        }

                        selectedNode?.let { node ->
                            val relatedEdges = edges.filter { it.source == node.id || it.target == node.id }
                            val nodeColor = if (node.id == centerNode?.id) GraphAccent else (typeColors[node.type] ?: GraphAccent)
                            Card(
                                Modifier.fillMaxWidth().padding(top = 12.dp),
                                colors = CardDefaults.cardColors(containerColor = GraphCard),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(10.dp).background(nodeColor, CircleShape))
                                        Spacer(Modifier.width(8.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(node.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            Text("${typeLabels[node.type] ?: node.type} · ${node.id}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.56f))
                                        }
                                        StatusChip("${relatedEdges.size} 条关联边", nodeColor.copy(alpha = 0.16f), nodeColor)
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    if (relatedEdges.isEmpty()) {
                                        Text("当前节点暂无关联边。", fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f))
                                    } else {
                                        relatedEdges.forEach { edge ->
                                            val sourceLabel = nodesById[edge.source]?.label ?: edge.source
                                            val targetLabel = nodesById[edge.target]?.label ?: edge.target
                                            val relationLabel = edgeLabels[edge.relation] ?: edge.relation
                                            val probText =
                                                edge.prob.takeIf { it.isNotBlank() }?.let {
                                                    (it.toFloatOrNull()?.times(100))?.toInt()?.let { pct -> " · $pct%" }
                                                }.orEmpty()
                                            Surface(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                color = GraphBg,
                                                shape = RoundedCornerShape(12.dp),
                                            ) {
                                                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                                    Text("$sourceLabel  →  $targetLabel", fontSize = 12.sp, color = Color.White)
                                                    Spacer(Modifier.height(2.dp))
                                                    Text("$relationLabel$probText", fontSize = 11.sp, color = nodeColor.copy(alpha = 0.82f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Card(
                            Modifier.fillMaxWidth().padding(top = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = GraphCard),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("全部关联边", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Spacer(Modifier.weight(1f))
                                    StatusChip("${edges.size} 条", GraphAccent.copy(alpha = 0.16f), GraphAccent)
                                }
                                Spacer(Modifier.height(10.dp))
                                if (edges.isEmpty()) {
                                    Text("当前图谱没有可显示的边。", fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f))
                                } else {
                                    edges.forEach { edge ->
                                        val sourceLabel = nodesById[edge.source]?.label ?: edge.source
                                        val targetLabel = nodesById[edge.target]?.label ?: edge.target
                                        val relationLabel = edgeLabels[edge.relation] ?: edge.relation
                                        val relationColor =
                                            typeColors[nodesById[edge.target]?.type ?: ""] ?:
                                                typeColors[nodesById[edge.source]?.type ?: ""] ?:
                                                GraphAccent
                                        Surface(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            color = GraphBg,
                                            shape = RoundedCornerShape(12.dp),
                                        ) {
                                            Row(
                                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Box(Modifier.size(8.dp).background(relationColor, CircleShape))
                                                Spacer(Modifier.width(8.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text("$sourceLabel  →  $targetLabel", fontSize = 12.sp, color = Color.White)
                                                    val probText =
                                                        edge.prob.takeIf { it.isNotBlank() }?.let {
                                                            (it.toFloatOrNull()?.times(100))?.toInt()?.let { pct -> " · $pct%" }
                                                        }.orEmpty()
                                                    Text("$relationLabel$probText", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

// ==================== History Sheet ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(history: List<HistoryEntry>, onDismiss: () -> Unit, onClear: () -> Unit, onRestore: (HistoryEntry) -> Unit) {
    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = BgColor) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxSheetHeight).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("诊断历史", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextMain)
                    Text("恢复上下文或回看已生成诊断。", fontSize = 12.sp, color = TextMuted)
                }
                StatusChip("${history.size} 条记录", SurfaceMuted, TextMuted)
            }
            if (history.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { Text("暂无诊断记录", fontSize = 14.sp, color = TextMuted) }
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(history.reversed()) { entry ->
                        Card(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onRestore(entry); onDismiss() },
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(1.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, BorderSoft),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(dateFormat.format(Date(entry.timestamp)), fontSize = 11.sp, color = TextSubtle)
                                    val tag = when { entry.isVideo -> "\u89c6\u9891"; entry.isImage -> "\u56fe\u7247"; entry.isGraphRAG -> "\u56fe\u8c31\u8bca\u65ad"; else -> "\u7aef\u4fa7\u6a21\u578b" }
                                    val tagColor = when { entry.isGraphRAG -> SuccessColor; entry.isImage || entry.isVideo -> Accent; else -> WarningColor }
                                    StatusChip(tag, tagColor.copy(alpha = 0.12f), tagColor)
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(entry.userText.take(60), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, color = TextMain)
                                Spacer(Modifier.height(4.dp))
                                Text(entry.aiText.take(100), fontSize = 12.sp, color = TextMuted, maxLines = 2, lineHeight = 18.sp)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (history.isNotEmpty()) {
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerColor),
                    border = BorderStroke(1.dp, DangerColor.copy(alpha = 0.3f)),
                ) {
                    Text("清空所有记录", fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ==================== Input Bar ====================

@Composable
private fun InputBar(enabled: Boolean, onSend: (String, Bitmap?, Uri?) -> Unit) {
    var text by remember { mutableStateOf("") }
    var pendingImage by remember { mutableStateOf<Bitmap?>(null) }
    var pendingVideoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingVideoLabel by remember { mutableStateOf("") }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) {
            pendingImage = bmp
            pendingVideoUri = null
            pendingVideoLabel = ""
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) photoLauncher.launch(null)
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            pendingImage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, it))
            else @Suppress("DEPRECATION") MediaStore.Images.Media.getBitmap(ctx.contentResolver, it)
            pendingVideoUri = null
            pendingVideoLabel = ""
        }
    }
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            pendingVideoUri = it
            pendingVideoLabel = getDisplayName(ctx, it)
            pendingImage = null
        }
    }

    Column(Modifier.fillMaxWidth().background(Color.White).imePadding()) {
        if (pendingImage != null) {
            Card(
                modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceMuted),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, BorderSoft),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(pendingImage!!.asImageBitmap(), null, Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("已选择图片", fontSize = 12.sp, color = TextMuted)
                        Text("现场资料将随诊断上下文一并发送", fontSize = 13.sp, color = TextMain, lineHeight = 18.sp)
                    }
                    IconButton(onClick = { pendingImage = null }, Modifier.size(28.dp).background(Color.White, CircleShape)) {
                        Icon(Icons.Default.Close, "删除", tint = TextMuted, modifier = Modifier.size(14.dp))
                    }
                }
            }
        } else if (pendingVideoUri != null) {
            Card(
                modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceMuted),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, BorderSoft),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.VideoLibrary, "视频", tint = Accent)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("已选择视频", fontSize = 12.sp, color = TextMuted)
                        Text(
                            pendingVideoLabel.ifBlank { "\u5df2\u9009\u89c6\u9891\u6587\u4ef6" },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            color = TextMain,
                        )
                    }
                    IconButton(onClick = {
                        pendingVideoUri = null
                        pendingVideoLabel = ""
                    }) {
                        Icon(Icons.Default.Close, "删除视频", tint = TextMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
            Box {
                IconButton(
                    onClick = { showAttachmentMenu = true },
                    enabled = enabled,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (enabled) SurfaceMuted else SurfaceSoft),
                ) {
                    Icon(Icons.Default.Add, "添加现场资料", tint = if (enabled) Accent else TextSubtle)
                }
                DropdownMenu(
                    expanded = showAttachmentMenu,
                    onDismissRequest = { showAttachmentMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("拍照") },
                        leadingIcon = { Icon(Icons.Default.AddAPhoto, null) },
                        onClick = {
                            showAttachmentMenu = false
                            if (ctx.checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                photoLauncher.launch(null)
                            } else {
                                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("导入图片") },
                        leadingIcon = { Icon(Icons.Default.Image, null) },
                        onClick = {
                            showAttachmentMenu = false
                            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("导入视频") },
                        leadingIcon = { Icon(Icons.Default.VideoLibrary, null) },
                        onClick = {
                            showAttachmentMenu = false
                            videoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        },
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Surface(
                modifier = Modifier.weight(1f),
                color = SurfaceMuted,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, BorderSoft),
            ) {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("输入故障现象、设备编号或诊断需求...", fontSize = 14.sp, color = TextSubtle) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SurfaceMuted,
                        unfocusedContainerColor = SurfaceMuted,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = TextMain,
                        unfocusedTextColor = TextMain,
                    ),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = false,
                    maxLines = 3,
                )
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = {
                    if (text.isNotBlank() || pendingImage != null || pendingVideoUri != null) {
                        onSend(text.trim(), pendingImage, pendingVideoUri)
                        text = ""
                        pendingImage = null
                        pendingVideoUri = null
                        pendingVideoLabel = ""
                    }
                },
                enabled = enabled && (text.isNotBlank() || pendingImage != null || pendingVideoUri != null),
                modifier = Modifier.height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, disabledContainerColor = BorderSoft),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, "发送", tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("\u5f00\u59cb\u8bca\u65ad", fontSize = 14.sp)
            }
        }
    }
}

// ==================== GraphRAG ====================

private fun buildConversationRetrievalText(
    currentUserText: String,
    conversationHistory: List<Pair<String, String>>,
): String {
    val recentContext = conversationHistory
        .takeLast(8)
        .joinToString("\n") { (role, content) -> "$role: ${content.take(800)}" }
    return listOf(recentContext, currentUserText)
        .filter { it.isNotBlank() }
        .joinToString("\n")
}

private fun buildEquipmentContextRetrievalText(
    currentUserText: String,
    retrievalText: String,
    activeEquipment: GraphNode?,
): String {
    val equipmentContext = activeEquipment?.let { "当前会话设备：${buildEquipmentContextLabel(it)}" }
    return listOf(equipmentContext, retrievalText, currentUserText)
        .filter { !it.isNullOrBlank() }
        .joinToString("\n")
}

private fun buildEquipmentContextLabel(equipment: GraphNode): String {
    val identifiers = mutableListOf(equipment.label)
    equipment.properties["配置号"]?.takeIf { it.isNotBlank() }?.let { identifiers.add("配置号：$it") }
    equipment.properties["brand"]?.takeIf { it.isNotBlank() }?.let { identifiers.add("品牌：$it") }
    equipment.properties["model"]?.takeIf { it.isNotBlank() }?.let { identifiers.add("型号：$it") }
    equipment.properties["location"]?.takeIf { it.isNotBlank() }?.let { identifiers.add("位置：$it") }
    return identifiers.joinToString("，")
}

private fun buildEquipmentLookupAnswer(
    currentUserText: String,
    retrievalText: String,
    graph: MaintenanceKnowledgeGraph,
): Pair<GraphNode, String>? {
    if (!isEquipmentLookupQuestion(currentUserText)) return null
    val equipment = graph.findEquipment(retrievalText).firstOrNull() ?: return null
    val details = mutableListOf<String>()
    equipment.properties["配置号"]?.takeIf { it.isNotBlank() }?.let { details.add("配置号：$it") }
    listOf("brand" to "品牌", "model" to "型号", "year" to "年份", "location" to "位置").forEach { (key, label) ->
        equipment.properties[key]?.takeIf { it.isNotBlank() }?.let { details.add("$label：$it") }
    }
    val answer = buildString {
        append("根据当前上下文和知识库匹配到：${equipment.label}")
        if (details.isNotEmpty()) append("。\n${details.joinToString("\n")}")
    }
    return equipment to answer
}

private fun isEquipmentLookupQuestion(text: String): Boolean {
    val normalized = text.trim()
    if (normalized.isBlank()) return false
    val lookupKeywords = listOf("几号叉车", "哪台叉车", "是哪台", "这是哪台", "哪个叉车", "设备编号", "配置号", "序列号")
    val asksLookup = lookupKeywords.any { normalized.contains(it) }
    val hasSymptom = SYMPTOM_KEYWORDS.any { normalized.contains(it) }
    return asksLookup && !hasSymptom
}

private fun extractExplicitEquipmentMention(text: String): String? {
    val normalized = text.trim()
    if (normalized.isBlank()) return null
    val patterns =
        listOf(
            Regex("""\d+\s*号\s*叉车"""),
            Regex("""叉车\s*\d+\s*号"""),
            Regex("""[A-Za-z0-9-]{2,}\s*(?:叉车|设备)"""),
        )
    return patterns
        .asSequence()
        .mapNotNull { pattern -> pattern.find(normalized)?.value }
        .map { it.replace(Regex("""\s+"""), "") }
        .firstOrNull()
}

private fun buildUnknownEquipmentSymptomReport(
    equipmentMention: String,
    userInput: String,
    graph: MaintenanceKnowledgeGraph,
): String? {
    val matchedKeyword = SYMPTOM_KEYWORDS.firstOrNull { userInput.contains(it) } ?: return null
    val symptom = findReferenceSymptom(matchedKeyword, graph) ?: return null
    val sub = graph.traverseFromNode(symptom.id, maxHops = 4)
    val causes = sub.nodes
        .filter { it.type == NodeType.ROOT_CAUSE }
        .sortedByDescending { it.properties["probability"]?.toFloatOrNull() ?: 0f }
    val parts = sub.nodes.filter { it.type == NodeType.PART }
    val steps = sub.nodes
        .filter { it.type == NodeType.REPAIR_STEP }
        .sortedBy { it.properties["order"]?.toIntOrNull() ?: 99 }

    val json = org.json.JSONObject().apply {
        put("equipment", "$equipmentMention（知识库未收录）")
        put("location", "")
        put("symptom", matchedKeyword)
        put("severity", symptom.properties["severity"].orEmpty())
        put(
            "causes",
            org.json.JSONArray().apply {
                causes.forEach { cause ->
                    put(
                        org.json.JSONObject()
                            .put("name", cause.label)
                            .put("probability", ((cause.properties["probability"]?.toFloatOrNull() ?: 0f) * 100).toInt()),
                    )
                }
            },
        )
        put(
            "parts",
            org.json.JSONArray().apply {
                parts.forEach { part ->
                    put(
                        org.json.JSONObject()
                            .put("name", part.label)
                            .put("spec", part.properties["spec"].orEmpty())
                            .put("stock", part.properties["stock"].orEmpty()),
                    )
                }
            },
        )
        put(
            "steps",
            org.json.JSONArray().apply {
                steps.forEach { step ->
                    put(
                        org.json.JSONObject()
                            .put("title", step.label)
                            .put("duration", step.properties["duration"].orEmpty())
                            .put("tool", step.properties["tool"].orEmpty()),
                    )
                }
            },
        )
    }
    return json.toString()
}

private fun buildGraphRAGReport(
    userInput: String,
    graph: MaintenanceKnowledgeGraph,
    preferredEquipment: GraphNode? = null,
): Pair<DiagnosticReport, String>? {
    val matchedKeywords = SYMPTOM_KEYWORDS.filter { userInput.contains(it) }
    if (matchedKeywords.isEmpty()) return null

    val equipment = preferredEquipment ?: graph.findEquipment(userInput).firstOrNull() ?: return null
    val directSymptom =
        matchedKeywords
            .asSequence()
            .flatMap { keyword -> graph.findSymptoms(equipment.id, keyword).asSequence() }
            .firstOrNull()
    val symptom =
        directSymptom
            ?: matchedKeywords.firstNotNullOfOrNull { keyword -> findReferenceSymptom(keyword, graph) }
            ?: return null
    val isDirectSymptom = directSymptom != null
    val sub = graph.traverseFromNode(symptom.id, maxHops = 4)
    val wos = graph.matchWorkOrders(symptom.id)
    val reportSubGraph =
        if (isDirectSymptom) {
            sub
        } else {
            buildContextualSubGraph(equipment, symptom, sub)
        }
    val graphJson = graph.subGraphToJson(reportSubGraph)
    Log.d(
        "PocketOps",
        "GraphRAG report: equipment=${equipment.label}, symptom=${symptom.label}, contextual=${!isDirectSymptom}, causes=${sub.nodes.count { it.type == NodeType.ROOT_CAUSE }}, workOrders=${wos.size}",
    )
    val report = DiagnosticReport(
        equipment = equipment, symptom = symptom,
        causes = sub.nodes.filter { it.type == NodeType.ROOT_CAUSE }.sortedByDescending { it.properties["probability"]?.toFloatOrNull() ?: 0f },
        parts = sub.nodes.filter { it.type == NodeType.PART },
        steps = sub.nodes.filter { it.type == NodeType.REPAIR_STEP }.sortedBy { it.properties["order"]?.toIntOrNull() ?: 99 },
        personnel = sub.nodes.filter { it.type == NodeType.PERSONNEL },
        workOrders = wos,
        nodeCount = sub.nodes.size,
    )
    return report to graphJson
}

private fun findReferenceSymptom(symptomKeyword: String, graph: MaintenanceKnowledgeGraph): GraphNode? {
    val allEquipment = graph.findEquipment("叉车")
    for (equipment in allEquipment) {
        graph.findSymptoms(equipment.id, symptomKeyword).firstOrNull()?.let { return it }
    }
    return null
}

private fun buildContextualSubGraph(
    equipment: GraphNode,
    symptom: GraphNode,
    sourceSubGraph: SubGraph,
): SubGraph {
    val nodes =
        (listOf(equipment) + sourceSubGraph.nodes.filter { node ->
            node.type != NodeType.EQUIPMENT || node.id == equipment.id
        }).distinctBy { it.id }
    val nodeIds = nodes.map { it.id }.toSet()
    val contextualEdge = GraphEdge(
        source = equipment.id,
        target = symptom.id,
        relation = "HAS_SYMPTOM",
        properties = mapOf("source" to "current_context"),
    )
    val edges =
        (listOf(contextualEdge) + sourceSubGraph.edges.filter { edge ->
            edge.source in nodeIds && edge.target in nodeIds
        }).distinctBy { "${it.source}|${it.target}|${it.relation}" }
    return SubGraph(nodes = nodes, edges = edges, centerNodeId = equipment.id)
}

private fun findRelatedWorkOrders(userInput: String, graph: MaintenanceKnowledgeGraph): List<GraphNode> {
    val matched = SYMPTOM_KEYWORDS.filter { userInput.contains(it) }
    if (matched.isEmpty()) return emptyList()
    val result = mutableListOf<GraphNode>()
    val allEquipment = graph.findEquipment("叉车")
    for (eq in allEquipment) {
        for (kw in matched) {
            val symptoms = graph.findSymptoms(eq.id, kw)
            for (sym in symptoms) {
                result.addAll(graph.matchWorkOrders(sym.id))
            }
        }
    }
    return result.distinctBy { it.id }.take(5)
}

private fun buildPartialRAGContext(userInput: String, graph: MaintenanceKnowledgeGraph): String? {
    val sb = StringBuilder()

    // Try to find any matching equipment
    val equipmentNodes = graph.findEquipment(userInput)
    val explicitEquipment = equipmentNodes.firstOrNull()
    val explicitEquipmentMention = extractExplicitEquipmentMention(userInput)

    // Try symptom keywords across all equipment
    val matchedKeyword = SYMPTOM_KEYWORDS.firstOrNull { userInput.contains(it) }

    if (matchedKeyword != null) {
        sb.appendLine("用户描述的症状关键词：$matchedKeyword")
        if (explicitEquipment == null && explicitEquipmentMention != null) {
            sb.appendLine("用户本轮指定了设备“$explicitEquipmentMention”，但该设备不在知识库中。不要沿用上一轮图片、视频或工单中的设备编号；equipment字段请填写“$explicitEquipmentMention（知识库未收录）”。以下仅作为该症状的通用维修知识参考。")
        } else if (explicitEquipment == null) {
            sb.appendLine("用户未指定具体设备编号。不要沿用上一轮图片或视频识别到的设备，也不要臆测设备编号；equipment字段请填写“待确认设备”，并提示用户补充设备编号或重新上传设备图片。以下仅作为该症状的通用维修知识参考。")
        }

        // Search matching equipment for this symptom. Without an explicit equipment id,
        // use only generic symptom knowledge so a previous visual lookup cannot keep
        // locking later symptom-only turns to the same forklift.
        val candidateEquipment = explicitEquipment?.let { listOf(it) } ?: graph.findEquipment("叉车")
        for (eq in candidateEquipment) {
            val symptoms = graph.findSymptoms(eq.id, matchedKeyword)
            if (symptoms.isNotEmpty()) {
                val symptom = symptoms.first()
                val sub = graph.traverseFromNode(symptom.id, maxHops = 4)
                val causes = sub.nodes.filter { it.type == NodeType.ROOT_CAUSE }
                val parts = sub.nodes.filter { it.type == NodeType.PART }
                val steps = sub.nodes.filter { it.type == NodeType.REPAIR_STEP }
                val personnel = sub.nodes.filter { it.type == NodeType.PERSONNEL }

                if (explicitEquipment != null) {
                    sb.appendLine("\n[以下为知识库参考数据，来源设备：${eq.label}]")
                } else {
                    sb.appendLine("\n[以下为通用症状参考数据，不代表已锁定到某一台叉车]")
                }
                sb.appendLine("故障症状：${symptom.label}，严重程度：${symptom.properties["severity"]}")
                sb.appendLine("可能原因：${causes.joinToString("、") { "${it.label}(${((it.properties["probability"]?.toFloatOrNull() ?: 0f) * 100).toInt()}%)" }}")
                sb.appendLine("所需备件：${parts.joinToString("、") { "${it.label}(${it.properties["spec"]})" }}")
                sb.appendLine("维修步骤：${steps.sortedBy { it.properties["order"]?.toIntOrNull() ?: 99 }.joinToString(" → ") { it.label }}")
                sb.appendLine("推荐人员：${personnel.joinToString("、") { "${it.label}(${it.properties["skill"]}专家)" }}")

                val workOrders = graph.matchWorkOrders(symptom.id)
                if (explicitEquipment != null && workOrders.isNotEmpty()) {
                    sb.appendLine("相似工单：${workOrders.joinToString("、") { "${it.label}(${it.properties["date"]})" }}")
                }
                break
            }
        }
    }

    if (equipmentNodes.isNotEmpty()) {
        val eq = equipmentNodes.first()
        sb.appendLine("\n用户提到的设备：${eq.label}（${eq.properties["brand"]} ${eq.properties["model"]}），位置：${eq.properties["location"]}")
    }

    return sb.toString().takeIf { it.isNotBlank() }
}
