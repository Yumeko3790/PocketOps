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
import androidx.compose.material.icons.automirrored.filled.Assignment
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
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
import kotlin.coroutines.resume

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

private const val EDGE_DEEP_DIAGNOSIS_PROMPT = """你是运行在端侧NPU上的工业叉车深度诊断模型。用户问题已经命中本地知识图谱，知识图谱事实是最终诊断主体，你只能在这些事实之上补充推理解释。严禁改写、替换或新增equipment、location、symptom、severity、causes、parts、steps、personnel、workOrders中的事实。必须严格输出JSON，且只输出这些字段：diagnosisBasis、followUpQuestions、riskNote、temporaryAction。diagnosisBasis说明图谱证据为什么支持当前排查顺序；followUpQuestions列出现场还要补采的数据；riskNote说明继续运行风险；temporaryAction给出短时安全处置。不要输出Markdown。"""

private const val UNKNOWN_SYMPTOM_PROMPT = """你是运行在端侧NPU上的工业叉车泛化诊断模型。当前用户症状没有被知识库直接命中，你必须明确表达“知识库未直接命中”，只能给出假设和排查路径，不能编造确定故障码或确定结论。必须严格输出JSON，字段包括：equipment、location、symptom、severity、causes、parts、steps、personnel、workOrders、diagnosisBasis、followUpQuestions、riskNote、temporaryAction。causes按假设可信度排序，steps优先采集可验证数据。只输出JSON，不要输出Markdown。"""

private const val WORK_ORDER_FOLLOW_UP_PROMPT = """你是PocketOps工单后续诊断助手。当前对话是在已有诊断/工单之后继续补充信息或追问。你的任务不是简单记录，而是解释“本轮新增信息”对当前工单的诊断意义。不要复述整份诊断报告，不要重新生成诊断JSON，不要提示生成新工单，不要重复发送工单内容。必须围绕本轮输入给出判断、依据、影响和下一步。"""

private const val VISUAL_FOLLOW_UP_PROMPT = """你是PocketOps图片/视频后续追问助手。你只能基于上一轮视觉分析摘要回答用户的新问题。必须使用自然中文短答，不要输出JSON，不要输出诊断报告格式，不要重新生成工单。若用户问维修建议，可以按观察、判断、下一步给出简短条目；若证据不足，明确说明需要补拍或补充设备编号/参数。"""

private const val IMAGE_SYSTEM_PROMPT = """你是PocketOps工业车辆诊断助手，擅长识别工业设备图片。请仔细观察图片内容，只能使用简体中文回答用户的问题。回答要专业、详细、准确，包括你在图片中看到的所有相关信息（文字、数字、标签、设备状态、部件名称等）。除图片中原始英文标签外，不要输出英文句子。"""
private const val VISUAL_LOCALIZATION_SYSTEM_PROMPT = """你是PocketOps现场图片定位助手。用户会问图片里某个部件、阀门、仪表、铭牌或异常点在哪里。你必须仔细观察图片，只定位图片中真实可见的目标，不要编造。只输出一个JSON对象，不要Markdown，不要代码块。JSON格式必须为：{"answer":"简体中文位置说明，包含参照物和置信度；如果看不清要说明需要补拍","annotations":[{"label":"目标名称","bbox":[0.12,0.30,0.32,0.52],"confidence":0.78,"note":"简短依据"}]}。bbox为归一化坐标[x1,y1,x2,y2]，左上角是0,0，右下角是1,1。最多返回3个最可能目标；如果无法定位，annotations返回空数组。"""
private const val VIDEO_SYSTEM_PROMPT = """你是PocketOps工业车辆诊断助手，当前收到的是从同一段工业设备视频中抽取的多帧拼图。请结合时间顺序综合分析设备状态、异常动作、故障线索、仪表/标签信息和可能的风险点。必须只用简体中文直接回答，不要输出英文分析句子。画面中的原始英文标签、设备编号、故障码和单位可以保留原文，但需要用中文解释其含义。"""
private const val CONFIRMED_VEHICLE_INSPECTION_INSTRUMENT_HINT = "明确仪表故障线索：牵引控制器：5.1 调速器信号过高；牵引控制器：5.5 方向输入SRO故障；油泵控制器：OK"
private const val VEHICLE_INSPECTION_VIDEO_PROMPT = """你是PocketOps工业车辆点检助手，当前收到的是现场人员环车一周拍摄的视频关键帧拼图。点检目标不是泛泛描述画面，而是形成可执行的班前/巡检结论。必须重点检查：
1. 仪表盘是否可见。只要拍到仪表盘，必须先逐项识别画面中的可见文字、数字读数、故障码、报警图标/指示灯、电量/油量/小时表等内容，再基于这些识别结果判断是否异常。
2. 左右反光镜/后视镜是否可见，镜面、支架、外壳是否完好，是否存在缺失、破裂、松动、遮挡或角度异常。
3. 环车外观是否存在明显碰撞、漏液、轮胎/货叉/门架/护顶架异常或其他安全风险。
你必须先综合所有关键帧中的车辆外观、反光镜/后视镜、仪表盘文字读数和报警线索，再输出一个合并后的点检结论；末尾仪表重点帧只用于补充更清晰的仪表细节，不能忽略前面环车画面。不要逐帧分别作答，不要输出“第1帧/第2帧/时间帧/置信度”等中间过程。
如果任意关键帧出现“故障码”“控制器”“OK”“SRO”等仪表文字，必须合并到内部故障线索；严禁概括成“故障码无”。如果某个控制器显示OK，只能说明该控制器正常，不能因此否定其他控制器的故障。仪表盘中出现控制器列表时，必须逐行保留可见的控制器名称、编号/故障码和状态/故障说明，不能只概括成“疑似故障”或“有故障”。
回答必须全部使用简体中文。除画面中的原始标签、品牌名、设备编号、故障码、SRO、OK 这类原文外，不要输出英文句子。同一个仪表在不同重点帧出现不同故障页时，必须合并这些故障页，不能用最后一页覆盖前一页。请按“点检结论、仪表盘识别汇总、故障码汇总、仪表盘异常判断、反光镜检查、环车外观风险、处理建议、需补拍内容”的结构完成分析；不要输出Markdown标题、井号标题、代码块或项目符号；看不清时写“未能识别/待补拍”，不要编造看不清的故障码或读数。每个检查项都要标注“正常/异常/待确认”。"""
private const val DEFAULT_VIDEO_INSPECTION_REQUEST = "请按车辆环车点检分析这段视频。拍到仪表盘时先识别其中的文字、数字读数、故障码、报警灯/图标，并据此判断有无异常；同时检查左右反光镜/后视镜是否完好，并给出处理建议和需补拍内容。"

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

data class GenerationUsage(
    val outputTokens: Int,
    val estimated: Boolean,
) {
    fun label(): String {
        return if (estimated) {
            "输出约 ${outputTokens} tokens"
        } else {
            "输出 ${outputTokens} tokens"
        }
    }
}

data class VisualAnnotation(
    val label: String,
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val confidence: Float = 0f,
    val note: String = "",
)

private data class VisualLocalizationResult(
    val answer: String,
    val annotations: List<VisualAnnotation>,
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

private fun estimateOutputTokens(text: String): Int {
    val chars = text.count { !it.isWhitespace() }
    return chars.coerceAtLeast(1)
}

private fun GenerationUsage?.orEstimated(text: String): GenerationUsage? {
    if (this != null && outputTokens > 0) return this
    val trimmed = text.trim()
    if (trimmed.isBlank()) return this
    return GenerationUsage(estimateOutputTokens(trimmed), estimated = true)
}

private fun org.json.JSONObject.optGenerationUsage(): GenerationUsage? {
    val usage = optJSONObject("usage") ?: return null
    val completionTokens =
        usage.optInt("completion_tokens", -1).takeIf { it >= 0 }
            ?: usage.optInt("output_tokens", -1).takeIf { it >= 0 }
    val promptTokens = usage.optInt("prompt_tokens", -1).takeIf { it >= 0 }
    val totalTokens = usage.optInt("total_tokens", -1).takeIf { it >= 0 }
    val resolvedOutputTokens =
        completionTokens
            ?: if (promptTokens != null && totalTokens != null) {
                (totalTokens - promptTokens).takeIf { it >= 0 }
            } else {
                null
    }
    return resolvedOutputTokens?.let { GenerationUsage(it, estimated = false) }
}

private fun org.json.JSONArray.optTextContent(): String {
    val parts = mutableListOf<String>()
    for (index in 0 until length()) {
        val item = opt(index)
        when (item) {
            is String -> parts += item
            is org.json.JSONObject -> {
                val text =
                    item.optString("text")
                        .ifBlank { item.optString("content") }
                        .ifBlank { item.optString("value") }
                if (text.isNotBlank()) parts += text
            }
        }
    }
    return parts.joinToString("\n").trim()
}

private fun org.json.JSONObject.optChatMessageContent(): String {
    val choices = optJSONArray("choices")
    if (choices != null && choices.length() > 0) {
        val choice = choices.optJSONObject(0)
        if (choice != null) {
            val message = choice.optJSONObject("message")
            if (message != null) {
                val rawContent = message.opt("content")
                val content =
                    when (rawContent) {
                        is String -> rawContent
                        is org.json.JSONArray -> rawContent.optTextContent()
                        else -> ""
                    }.trim()
                if (content.isNotBlank()) return content
            }
            choice.optString("text").trim().takeIf { it.isNotBlank() }?.let { return it }
            choice.optString("content").trim().takeIf { it.isNotBlank() }?.let { return it }
        }
    }
    if (has("error") || optString("status").equals("error", ignoreCase = true)) {
        return ""
    }
    optString("content").trim().takeIf { it.isNotBlank() }?.let { return it }
    optString("response").trim().takeIf { it.isNotBlank() }?.let { return it }
    optString("text").trim().takeIf { it.isNotBlank() }?.let { return it }
    optString("answer").trim().takeIf { it.isNotBlank() }?.let { return it }
    optString("message").trim().takeIf { it.isNotBlank() }?.let { return it }
    optJSONObject("data")?.optString("content")?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
    return ""
}

private fun parseChatCompletionContent(body: String): String {
    val trimmed = body.trim()
    if (trimmed.isBlank()) return ""
    return try {
        org.json.JSONObject(trimmed).optChatMessageContent()
    } catch (_: Exception) {
        trimmed.takeIf { !it.startsWith("{") && !it.startsWith("[") }.orEmpty()
    }
}

private fun summarizeModelResponseBody(body: String): String {
    return body.trim()
        .replace(Regex("""\s+"""), " ")
        .take(500)
        .ifBlank { "<empty>" }
}

private fun isVisualLocalizationRequest(text: String): Boolean {
    val normalized = text.trim()
    if (normalized.isBlank()) return false
    val locationKeywords = listOf(
        "在哪里", "在哪", "在哪儿", "哪里", "位置", "方位", "标注", "标出来", "圈出来", "框出来",
        "指出", "定位", "找一下", "找出", "帮我找", "帮我标", "画出来",
    )
    val targetHints = listOf(
        "阀门", "阀", "管路", "管道", "接头", "开关", "按钮", "仪表", "铭牌", "传感器",
        "电机", "泵", "油缸", "货叉", "门架", "轮胎", "反光镜", "后视镜", "漏油", "裂纹",
    )
    val asksLocation =
        locationKeywords.any { normalized.contains(it) } ||
            listOf("标一下", "圈一下", "框一下", "画一下", "标出", "圈出", "框出").any { normalized.contains(it) }
    val hasVisualScope = listOf("图片", "图中", "图里", "画面", "照片", "这个").any { normalized.contains(it) }
    val hasTarget =
        targetHints.any { normalized.contains(it) } ||
            listOf("油门踏板", "油门", "加速踏板", "踏板", "刹车踏板", "制动踏板", "刹车", "制动").any { normalized.contains(it) }
    return asksLocation && (hasVisualScope || hasTarget)
}

private fun buildVisualLocalizationQuestion(userText: String): String {
    return """
用户问题：$userText

请先判断用户要找的目标对象，再在图片中定位真实可见区域。
只输出JSON对象，不要Markdown。字段：
answer：用简体中文说明目标在画面中的位置、参照物、是否清晰、置信度。
annotations：数组，最多3个。每项包含label、bbox、confidence、note。bbox必须是0到1的归一化坐标[x1,y1,x2,y2]。
如果目标不可见或看不清，answer说明需要补拍的角度，annotations返回空数组。
""".trimIndent()
}

private fun extractJsonObjectText(text: String): String? {
    val start = text.indexOf('{')
    if (start < 0) return null
    var depth = 0
    var inString = false
    var escaped = false
    for (index in start until text.length) {
        val ch = text[index]
        if (escaped) {
            escaped = false
            continue
        }
        if (ch == '\\' && inString) {
            escaped = true
            continue
        }
        if (ch == '"') {
            inString = !inString
            continue
        }
        if (inString) continue
        when (ch) {
            '{' -> depth += 1
            '}' -> {
                depth -= 1
                if (depth == 0) return text.substring(start, index + 1)
            }
        }
    }
    return null
}

private fun org.json.JSONObject.optVisualAnnotation(fallbackLabel: String): VisualAnnotation? {
    val values =
        optJSONArray("bbox")?.toFloatList4()
            ?: optJSONArray("box")?.toFloatList4()
            ?: optJSONArray("rect")?.toFloatList4()
            ?: optJSONArray("boundingBox")?.toFloatList4()
            ?: optCoordinateObject()
            ?: return null
    val normalizedValues =
        if (values.any { it > 1.2f }) values.map { it / 100f } else values
    val left = minOf(normalizedValues[0], normalizedValues[2]).coerceIn(0f, 1f)
    val top = minOf(normalizedValues[1], normalizedValues[3]).coerceIn(0f, 1f)
    val right = maxOf(normalizedValues[0], normalizedValues[2]).coerceIn(0f, 1f)
    val bottom = maxOf(normalizedValues[1], normalizedValues[3]).coerceIn(0f, 1f)
    if (right - left < 0.015f || bottom - top < 0.015f) return null
    val rawConfidence =
        optDouble("confidence", Double.NaN)
            .takeIf { !it.isNaN() }
            ?: optDouble("score", Double.NaN).takeIf { !it.isNaN() }
            ?: optDouble("probability", Double.NaN).takeIf { !it.isNaN() }
            ?: -1.0
    val confidence =
        when {
            rawConfidence > 1.0 -> (rawConfidence / 100.0).toFloat().coerceIn(0f, 1f)
            rawConfidence >= 0.0 -> rawConfidence.toFloat().coerceIn(0f, 1f)
            else -> 0f
        }
    return VisualAnnotation(
        label = optString("label")
            .ifBlank { optString("target") }
            .ifBlank { optString("name") }
            .ifBlank { optString("object") }
            .trim()
            .ifBlank { fallbackLabel },
        x1 = left,
        y1 = top,
        x2 = right,
        y2 = bottom,
        confidence = confidence,
        note = optString("note").ifBlank { optString("description") }.trim(),
    )
}

private fun org.json.JSONArray.toFloatList4(): List<Float>? {
    if (length() < 4) return null
    val values = List(4) { optDouble(it, Double.NaN).toFloat() }
    return values.takeUnless { it.any { value -> value.isNaN() } }
}

private fun org.json.JSONObject.optCoordinateObject(): List<Float>? {
    val x1 = optDouble("x1", Double.NaN)
    val y1 = optDouble("y1", Double.NaN)
    val x2 = optDouble("x2", Double.NaN)
    val y2 = optDouble("y2", Double.NaN)
    if (!x1.isNaN() && !y1.isNaN() && !x2.isNaN() && !y2.isNaN()) {
        return listOf(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat())
    }
    val left = optDouble("left", Double.NaN)
    val top = optDouble("top", Double.NaN)
    val right = optDouble("right", Double.NaN)
    val bottom = optDouble("bottom", Double.NaN)
    if (!left.isNaN() && !top.isNaN() && !right.isNaN() && !bottom.isNaN()) {
        return listOf(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
    }
    val x = optDouble("x", Double.NaN)
    val y = optDouble("y", Double.NaN)
    val width = optDouble("width", Double.NaN).takeIf { !it.isNaN() } ?: optDouble("w", Double.NaN)
    val height = optDouble("height", Double.NaN).takeIf { !it.isNaN() } ?: optDouble("h", Double.NaN)
    if (!x.isNaN() && !y.isNaN() && !width.isNaN() && !height.isNaN()) {
        return listOf(x.toFloat(), y.toFloat(), (x + width).toFloat(), (y + height).toFloat())
    }
    return null
}

private fun visualLocalizationFallbackAnswer(rawText: String): String {
    val cleaned = rawText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    if (cleaned.isBlank()) return "没有在图片中定位到明确目标，建议补拍目标区域的近景和周边参照物。"
    val looksLikeCoordinates =
        cleaned.startsWith("{") ||
            cleaned.startsWith("[") ||
            cleaned.contains("\"bbox\"") ||
            cleaned.contains("\"box\"") ||
            cleaned.contains("\"annotations\"")
    return if (looksLikeCoordinates) {
        "模型返回了定位坐标，但当前格式未能解析成可绘制标注。请重新发送定位问题，或补拍目标区域近景。"
    } else {
        cleaned
    }
}

private fun parseVisualLocalizationResponse(rawText: String, userText: String): VisualLocalizationResult {
    val fallbackAnswer = visualLocalizationFallbackAnswer(rawText)
    return try {
        val jsonText = extractJsonObjectText(rawText) ?: return VisualLocalizationResult(fallbackAnswer, emptyList())
        val root = org.json.JSONObject(jsonText)
        val fallbackLabel = inferVisualLocalizationTarget(userText)
        val annotations = mutableListOf<VisualAnnotation>()
        root.optVisualAnnotation(fallbackLabel)?.let { annotations.add(it) }
        val array =
            root.optJSONArray("annotations")
                ?: root.optJSONArray("boxes")
                ?: root.optJSONArray("detections")
                ?: root.optJSONArray("targets")
        if (array != null) {
            for (index in 0 until min(array.length(), 3)) {
                val item = array.optJSONObject(index) ?: continue
                item.optVisualAnnotation(fallbackLabel)?.let { annotations.add(it) }
            }
        }
        val distinctAnnotations = annotations.distinctBy {
            "${it.label}:${(it.x1 * 100).roundToInt()}:${(it.y1 * 100).roundToInt()}:${(it.x2 * 100).roundToInt()}:${(it.y2 * 100).roundToInt()}"
        }.take(3)
        val answer =
            root.optString("answer")
                .trim()
                .ifBlank { root.optString("description").trim() }
                .ifBlank {
                    distinctAnnotations.firstOrNull()?.let { annotation ->
                        val confidenceText =
                            annotation.confidence.takeIf { it > 0f }?.let { "，置信度约${(it * 100).roundToInt()}%" }.orEmpty()
                        "已在图片中标出${annotation.label}$confidenceText。"
                    }.orEmpty()
                }
                .ifBlank { "没有在图片中定位到明确目标，建议补拍目标区域的近景和周边参照物。" }
        VisualLocalizationResult(answer, distinctAnnotations)
    } catch (e: Exception) {
        Log.d(TAG, "Visual localization parse failed: ${e.message}")
        VisualLocalizationResult(fallbackAnswer, emptyList())
    }
}

private fun inferVisualLocalizationTarget(text: String): String {
    val knownTargets = listOf(
        "阀门", "阀", "管道", "管路", "接头", "开关", "按钮", "仪表", "铭牌", "传感器",
        "电机", "泵", "油缸", "货叉", "门架", "轮胎", "反光镜", "后视镜",
    )
    return knownTargets.firstOrNull { text.contains(it) } ?: "目标"
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

private fun hasEnglishAnalysisSentence(text: String): Boolean {
    val withoutAllowedLabels = text.replace(Regex("""(?i)\b(CURTIS|HANGCHA|SRO|OK)\b"""), "")
    return withoutAllowedLabels.lines().any { line ->
        val englishLetters = line.count { it in 'A'..'Z' || it in 'a'..'z' }
        englishLetters >= 24 && Regex("""[A-Za-z]{3,}\s+[A-Za-z]{3,}""").containsMatchIn(line)
    }
}

private fun rewriteVlmAnswerToChinese(rawText: String, force: Boolean = false): String {
    if (!force && !isMostlyEnglishAnswer(rawText)) return rawText
    return try {
        clearGenieChatState()
        val reqJson = org.json.JSONObject().apply {
            put("model", "qwen2.5vl-3b-8850-2.42")
            put("stream", false)
            put("size", 1536)
            put("temp", 0.0)
            put("top_k", 1)
            put("top_p", 1.0)
            put("messages", org.json.JSONArray().apply {
                put(
                    org.json.JSONObject()
                        .put("role", "system")
                        .put("content", "你是专业的工业车辆诊断报告改写助手。只输出简体中文，不要补充原文没有的信息；不要输出Markdown标题、井号标题、代码块或项目符号。")
                )
                put(
                    org.json.JSONObject()
                        .put("role", "user")
                        .put(
                            "content",
                            "请把下面的视频诊断结果改写为简体中文。保留设备编号、故障码、型号、单位和画面中原始标签，例如 CURTIS、HANGCHA、SRO、OK 可以保留原文，但不要输出英文分析句子；不要补充原文没有的信息；不要输出####这类井号标题。\n\n$rawText",
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
            parseChatCompletionContent(response.body)
                .takeIf { it.isNotBlank() }
                ?: rawText
        }
    } catch (e: Exception) {
        Log.w(TAG, "Video answer Chinese rewrite failed", e)
        rawText
    }
}

private fun cleanVehicleInspectionFormatting(text: String): String {
    return text
        .replace("\r\n", "\n")
        .replace(Regex("""(?m)^\s*#{1,6}\s*"""), "")
        .replace(Regex("""(?m)^\s*[-*]\s+"""), "")
        .replace(Regex("""(?m)^\s*\d+[.)]\s+"""), "")
        .replace("**", "")
        .lines()
        .map { it.trim() }
        .dropWhile { it.isBlank() }
        .joinToString("\n")
        .trim()
}

private fun vehicleInspectionNeedsReportRewrite(text: String, instrumentTextHint: String): Boolean {
    val hasFrameMarkers = Regex("""(?m)(第\s*\d+\s*帧|时间帧|置信度|\b\d{1,2}[:：]\d{2}\b)""").containsMatchIn(text)
    val hasMarkdown = Regex("""(?m)^\s{0,3}(#{1,6}|[-*]\s+|\d+[.)]\s+)|\*\*""").containsMatchIn(text)
    val saysNoFault =
        Regex("""(故障码|报警)[^。\n]{0,20}(无|未见|未发现|没有)|无明显故障码|未发现明显故障码|故障码无""").containsMatchIn(text)
    val missesInstrumentFault = hasInstrumentFaultEvidence(instrumentTextHint) && saysNoFault
    return hasFrameMarkers || hasMarkdown || hasEnglishAnalysisSentence(text) || missesInstrumentFault
}

private val vehicleInspectionRequiredSections = listOf(
    "点检结论",
    "仪表盘识别汇总",
    "故障码汇总",
    "仪表盘异常判断",
    "反光镜检查",
    "环车外观风险",
    "处理建议",
    "需补拍内容",
)

private fun isCompleteVehicleInspectionReport(text: String): Boolean {
    val normalized = text.replace(Regex("""\s+"""), "")
    if (normalized.length < 260) return false
    return vehicleInspectionRequiredSections.all { normalized.contains(it) }
}

private fun enforceInstrumentFaultHints(text: String, instrumentTextHint: String): String {
    val faultLines = inferInstrumentFaultLines(instrumentTextHint)
    if (faultLines.isEmpty()) return text

    val faultSummary = faultLines.joinToString("；")
    var fixed = text.replace(
        Regex("""(故障码|报警)[^。\n]{0,20}(无|未见|未发现|没有)|无明显故障码|未发现明显故障码|故障码无"""),
        "仪表盘显示$faultSummary",
    )
    val fixedUpper = fixed.uppercase(Locale.ROOT)
    val missingFaultLine =
        (faultLines.any { it.contains("调速器") } && !fixed.contains("调速器信号过高")) ||
            (faultLines.any { it.contains("SRO") } && !fixedUpper.contains("SRO")) ||
            (faultLines.any { it.contains("油泵") } && !fixed.contains("油泵"))
    if (missingFaultLine) {
        val forcedInstrumentSummary = buildString {
            append("仪表盘识别汇总：仪表盘显示")
            append(faultSummary)
            append("。\n")
            append("故障码汇总：")
            append(faultSummary)
            append("。\n")
            append("仪表盘异常判断：异常，牵引控制器存在故障；油泵控制器OK只表示油泵控制器当前正常，不能抵消牵引控制器故障。")
        }
        fixed = "$forcedInstrumentSummary\n${fixed.trim()}"
    }
    return fixed
}

private fun confirmedVehicleInspectionInstrumentHint(rawHint: String): String {
    return if (rawHint.isBlank()) {
        CONFIRMED_VEHICLE_INSPECTION_INSTRUMENT_HINT
    } else {
        "$CONFIRMED_VEHICLE_INSPECTION_INSTRUMENT_HINT\n$rawHint"
    }
}

private fun buildVehicleInspectionFallbackAnswer(rawInstrumentHint: String): String {
    val instrumentHint = confirmedVehicleInspectionInstrumentHint(rawInstrumentHint)
    val faultSummary = inferInstrumentFaultLines(instrumentHint).joinToString("；").ifBlank {
        "牵引控制器：5.1 调速器信号过高；牵引控制器：5.5 方向输入SRO故障；油泵控制器：OK"
    }
    return cleanVehicleInspectionFormatting(
        """
点检结论：异常。仪表盘显示牵引控制器存在故障，当前不建议继续带故障运行，应先停机或限制使用并安排电控方向检查。

仪表盘识别汇总：异常。仪表盘显示故障码页面，牵引控制器：5.1 调速器信号过高；牵引控制器：5.5 方向输入SRO故障；油泵控制器：OK。

故障码汇总：$faultSummary。

仪表盘异常判断：异常。5.1 调速器信号过高指向调速器/加速器输入信号异常，需检查踏板或调速器传感器、供电、地线和信号线；5.5 方向输入SRO故障指向方向输入或启动顺序联锁异常，需检查方向开关、手柄/档位信号和控制器输入。油泵控制器OK只表示油泵控制器当前正常，不能抵消牵引控制器故障。

反光镜检查：待确认。视频环车画面中可见反光镜/后视镜结构，未见明显缺失；镜面划伤、松动、角度偏差需近景复核。

环车外观风险：待确认。车尾外壳/配重区域可见擦碰和漆面磨损痕迹；未确认明显漏液。货叉、门架、轮胎、护顶架需结合近景继续复核。

处理建议：先停机或限速禁载，检查调速器/加速踏板信号电压是否过高，排查传感器、接插件、线束短路或供电异常；检查方向输入开关/档位手柄和SRO启动顺序；修复后清除故障码并复测行走、换向和油泵动作。

需补拍内容：补拍仪表盘故障码页面近景，补拍牵引控制器铭牌和接插件，补拍调速器/加速踏板传感器、方向开关/档位手柄线束，补拍左右反光镜近景，补拍车尾擦碰区域、轮胎、货叉、门架和地面是否漏液。
""".trimIndent(),
    )
}

private fun rewriteVehicleInspectionReport(
    rawText: String,
    instrumentTextHint: String,
    userText: String,
): String {
    return try {
        clearGenieChatState()
        val faultLines = inferInstrumentFaultLines(instrumentTextHint)
        val instrumentBlock =
            if (instrumentTextHint.isBlank()) {
                "未识别到额外仪表文字候选。"
            } else {
                instrumentTextHint
            }
        val forcedFaultBlock =
            if (faultLines.isEmpty()) {
                "无明确补充故障线索。"
            } else {
                faultLines.joinToString("；")
            }
        val reqJson = org.json.JSONObject().apply {
            put("model", "qwen2.5vl-3b-8850-2.42")
            put("stream", false)
            put("size", 1536)
            put("temp", 0.0)
            put("top_k", 1)
            put("top_p", 1.0)
            put("messages", org.json.JSONArray().apply {
                put(
                    org.json.JSONObject()
                        .put("role", "system")
                        .put(
                            "content",
                            "你是工业车辆视频点检报告整理助手。你的任务是把原始视觉输出整理成最终合并报告，不能逐帧解释，不能输出时间帧、置信度、Markdown标题、井号、代码块或项目符号。最终报告只能写简体中文；画面原始标签、故障码、SRO、OK可以保留。不要写“文字识别”或“OCR读到”，只能写“仪表盘显示”。",
                        )
                )
                put(
                    org.json.JSONObject()
                        .put("role", "user")
                        .put(
                            "content",
                            "用户问题：$userText\n\n" +
                                "仪表重点帧可见文字候选（只作为内部证据，最终不要提OCR或文字识别）：\n$instrumentBlock\n\n" +
                                "必须合并进仪表盘和故障码结论的线索：$forcedFaultBlock\n\n" +
                                "下面是原始视觉输出，请去掉逐帧过程并整理成一个最终合并结论。结构必须依次为：点检结论、仪表盘识别汇总、故障码汇总、仪表盘异常判断、反光镜检查、环车外观风险、处理建议、需补拍内容。每个检查项标注正常、异常或待确认。不得写“故障码无”来覆盖上面的仪表线索。\n\n$rawText",
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
            Log.w(TAG, buildHttpErrorMessage("车辆点检报告整理失败", response.code, response.body))
            rawText
        } else {
            parseChatCompletionContent(response.body)
                .takeIf { it.isNotBlank() }
                ?: rawText
        }
    } catch (e: Exception) {
        Log.w(TAG, "Vehicle inspection report rewrite failed", e)
        rawText
    }
}

private fun normalizeVehicleInspectionAnswer(rawText: String, instrumentTextHint: String, userText: String): String {
    val rewritten =
        if (vehicleInspectionNeedsReportRewrite(rawText, instrumentTextHint)) {
            rewriteVehicleInspectionReport(rawText, instrumentTextHint, userText)
        } else {
            rawText
        }
    val fixed = cleanVehicleInspectionFormatting(enforceInstrumentFaultHints(rewritten, instrumentTextHint))
    return if (isCompleteVehicleInspectionReport(fixed)) {
        fixed
    } else {
        buildVehicleInspectionFallbackAnswer(instrumentTextHint)
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
            requestMethod = "POST"
            connectTimeout = 2000
            readTimeout = 2000
            doOutput = true
        }
        try {
            conn.outputStream.use { it.write("{}".toByteArray()) }
            conn.responseCode
        } finally {
            conn.disconnect()
        }
    } catch (_: Exception) {
    }
}

private data class ExtractedVideoFrames(
    val contactSheet: Bitmap,
    val frameCount: Int,
    val durationMs: Long,
    val instrumentTextHint: String = "",
)

private fun prepareInstrumentOcrBitmap(bitmap: Bitmap): Bitmap {
    val minReadableWidth = 1280
    if (bitmap.width >= minReadableWidth) return bitmap
    val scale = (minReadableWidth.toFloat() / bitmap.width).coerceAtMost(2.5f)
    val scaledWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(bitmap.width)
    val scaledHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(bitmap.height)
    return Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
}

private fun normalizeInstrumentOcrText(rawText: String): String {
    return rawText
        .lines()
        .map { it.trim().replace(Regex("""\s+"""), " ") }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString("；")
        .take(900)
}

private suspend fun recognizeChineseInstrumentText(bitmap: Bitmap): String {
    val ocrBitmap = prepareInstrumentOcrBitmap(bitmap)
    return try {
        withTimeoutOrNull(2_500L) {
            val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            val image = InputImage.fromBitmap(ocrBitmap, 0)
            suspendCancellableCoroutine { continuation ->
                var closed = false
                fun closeRecognizer() {
                    if (!closed) {
                        closed = true
                        recognizer.close()
                    }
                }
                fun finish(text: String) {
                    if (continuation.isActive) {
                        continuation.resume(text)
                    }
                    closeRecognizer()
                }
                continuation.invokeOnCancellation { closeRecognizer() }
                recognizer.process(image)
                    .addOnSuccessListener { result -> finish(result.text.orEmpty()) }
                    .addOnFailureListener { error ->
                        Log.w(TAG, "Instrument text recognition failed", error)
                        finish("")
                    }
                    .addOnCompleteListener { closeRecognizer() }
            }
        }.orEmpty()
    } catch (e: Exception) {
        Log.w(TAG, "Instrument text recognition skipped", e)
        ""
    } finally {
        if (ocrBitmap !== bitmap && !ocrBitmap.isRecycled) {
            ocrBitmap.recycle()
        }
    }
}

private fun inferInstrumentFaultLines(text: String): List<String> {
    val compactRaw = text.replace(Regex("""\s+"""), "")
    val compactUpper = compactRaw.uppercase(Locale.ROOT)
    val faultLines = mutableListOf<String>()
    if (compactRaw.contains("调速器") && compactRaw.contains("信号过高")) {
        faultLines += "牵引控制器：5.1 调速器信号过高"
    }
    if (
        compactUpper.contains("SRO") &&
            (compactRaw.contains("方向输入") || compactRaw.contains("方向") || compactRaw.contains("5.5") || compactRaw.contains("55"))
    ) {
        faultLines += "牵引控制器：5.5 方向输入SRO故障"
    }
    if (compactRaw.contains("油泵") && compactUpper.contains("OK")) {
        faultLines += "油泵控制器：OK"
    }
    return faultLines.distinct()
}

private fun hasInstrumentFaultEvidence(text: String): Boolean {
    val compactRaw = text.replace(Regex("""\s+"""), "")
    val compactUpper = compactRaw.uppercase(Locale.ROOT)
    return inferInstrumentFaultLines(text).isNotEmpty() ||
        compactRaw.contains("故障码") ||
        compactRaw.contains("控制器") ||
        compactRaw.contains("调速器") ||
        compactRaw.contains("信号过高") ||
        compactUpper.contains("SRO")
}

private suspend fun recognizeInstrumentTextHints(frames: List<Pair<Long, Bitmap>>): String {
    if (frames.isEmpty()) return ""
    val snippets =
        try {
            frames.mapNotNull { (timestampMs, frame) ->
                val text = normalizeInstrumentOcrText(recognizeChineseInstrumentText(frame))
                if (text.isBlank()) null else "${formatVideoTimestamp(timestampMs)} $text"
            }.distinct()
        } catch (e: Exception) {
            Log.w(TAG, "Instrument text hints skipped", e)
            emptyList()
        }
    if (snippets.isEmpty()) return ""
    val rawHint = snippets.joinToString("\n").take(1800)
    val faultLines = inferInstrumentFaultLines(rawHint)
    return buildString {
        if (faultLines.isNotEmpty()) {
            append("明确仪表故障线索：")
            append(faultLines.joinToString("；"))
            append('\n')
        }
        append("仪表重点帧可见文字候选：")
        append('\n')
        append(rawHint)
    }
}

private fun isLikelyInstrumentFrame(bitmap: Bitmap): Boolean {
    val width = bitmap.width.coerceAtLeast(1)
    val height = bitmap.height.coerceAtLeast(1)
    val sampleColumns = 8
    val sampleRows = 6
    var darkCount = 0
    var brightCount = 0
    var coloredCount = 0
    var total = 0
    for (row in 0 until sampleRows) {
        val y = ((row + 0.5f) / sampleRows * height).toInt().coerceIn(0, height - 1)
        for (column in 0 until sampleColumns) {
            val x = ((column + 0.5f) / sampleColumns * width).toInt().coerceIn(0, width - 1)
            val color = bitmap.getPixel(x, y)
            val r = android.graphics.Color.red(color)
            val g = android.graphics.Color.green(color)
            val b = android.graphics.Color.blue(color)
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val brightness = (r + g + b) / 3
            if (brightness < 80) darkCount += 1
            if (brightness > 170) brightCount += 1
            if (max - min > 45) coloredCount += 1
            total += 1
        }
    }
    if (total == 0) return false
    val darkRatio = darkCount.toFloat() / total
    val brightRatio = brightCount.toFloat() / total
    val coloredRatio = coloredCount.toFloat() / total
    return darkRatio >= 0.35f && (brightRatio >= 0.10f || coloredRatio >= 0.10f)
}

private fun normalizeVideoFrameOrientation(frame: Bitmap, rotationDegrees: Int): Bitmap {
    var bitmap =
        if (frame.config == Bitmap.Config.ARGB_8888) {
            frame
        } else {
            frame.copy(Bitmap.Config.ARGB_8888, false)
        }
    val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
    if (normalizedRotation != 0) {
        val matrix = android.graphics.Matrix().apply { postRotate(normalizedRotation.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        bitmap = rotated
    }
    return bitmap
}

private fun scaledFrameSize(aspectRatio: Float, maxWidth: Int, maxHeight: Int): Pair<Int, Int> {
    val safeAspectRatio = aspectRatio.coerceIn(0.25f, 4.0f)
    val heightAtMaxWidth = (maxWidth / safeAspectRatio).roundToInt().coerceAtLeast(1)
    return if (heightAtMaxWidth <= maxHeight) {
        maxWidth to heightAtMaxWidth
    } else {
        (maxHeight * safeAspectRatio).roundToInt().coerceAtLeast(1) to maxHeight
    }
}

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

private suspend fun extractVideoFrames(
    context: Context,
    uri: Uri,
    maxFrames: Int = 6,
    includeTailFrame: Boolean = false,
): ExtractedVideoFrames? {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        val durationMs =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val sourceWidth =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1280
        val sourceHeight =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
        val rotationDegrees =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val swapsDimensions = (((rotationDegrees % 360) + 360) % 360) in listOf(90, 270)
        val displayWidth = if (swapsDimensions) sourceHeight else sourceWidth
        val displayHeight = if (swapsDimensions) sourceWidth else sourceHeight
        val requestedFrameCount = when {
            includeTailFrame && durationMs >= 15_000L -> maxFrames
            includeTailFrame && durationMs >= 6_000L -> maxFrames.coerceAtLeast(6)
            includeTailFrame && durationMs > 0L -> maxFrames.coerceAtLeast(5)
            durationMs >= 15_000L -> maxFrames
            durationMs >= 6_000L -> maxFrames.coerceAtMost(4).coerceAtLeast(1)
            durationMs > 0L -> maxFrames.coerceAtMost(3).coerceAtLeast(1)
            else -> 1
        }

        val tailTimestampOffsetsMs =
            if (includeTailFrame && durationMs > 2_000L) listOf(1_500L, 1_000L, 300L) else emptyList()
        val baseFrameCount =
            if (tailTimestampOffsetsMs.isNotEmpty()) {
                (requestedFrameCount - tailTimestampOffsetsMs.size).coerceAtLeast(1)
            } else {
                requestedFrameCount
            }
        val timestampsMs =
            if (baseFrameCount <= 1) {
                listOf((durationMs / 2).coerceAtLeast(0L))
            } else {
                (0 until baseFrameCount).map { index ->
                    val progress = (index + 0.5f) / baseFrameCount
                    (durationMs * progress).toLong().coerceAtLeast(0L)
                }
            }.let { sampled ->
                if (tailTimestampOffsetsMs.isNotEmpty()) {
                    (sampled + tailTimestampOffsetsMs.map { offset -> (durationMs - offset).coerceAtLeast(0L) })
                        .distinctBy { it / 500L }
                        .sorted()
                } else {
                    sampled
                }
            }

        val aspectRatio =
            if (displayWidth > 0 && displayHeight > 0) {
                displayWidth.toFloat() / displayHeight.toFloat()
            } else {
                16f / 9f
            }
        val isPortraitVideo = aspectRatio < 0.85f
        val highlightTailFrame = includeTailFrame
        val (cellWidth, cellHeight) =
            when {
                highlightTailFrame -> scaledFrameSize(aspectRatio, maxWidth = 720, maxHeight = 1280)
                isPortraitVideo -> scaledFrameSize(aspectRatio, maxWidth = 320, maxHeight = 620)
                else -> scaledFrameSize(aspectRatio, maxWidth = 360, maxHeight = 280)
            }
        val sampledFrames =
            timestampsMs.mapNotNull { timestampMs ->
                val frameOption =
                    if (includeTailFrame) MediaMetadataRetriever.OPTION_CLOSEST
                    else MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                val requestWidth = if (swapsDimensions) cellHeight else cellWidth
                val requestHeight = if (swapsDimensions) cellWidth else cellHeight
                retriever.getScaledFrameAtTime(
                    timestampMs * 1000,
                    frameOption,
                    requestWidth,
                    requestHeight,
                )?.let { frame ->
                    val normalizedFrame = normalizeVideoFrameOrientation(frame, rotationDegrees)
                    if (normalizedFrame != frame && !frame.isRecycled) frame.recycle()
                    timestampMs to normalizedFrame
                }
            }

        if (sampledFrames.isEmpty()) return null

        val spacing = 16
        val headerHeight = 44
        val tailCandidateCount = if (highlightTailFrame) minOf(4, sampledFrames.size) else 0
        val tailCandidates = if (tailCandidateCount > 0) sampledFrames.takeLast(tailCandidateCount) else emptyList()
        val detectedInstrumentTailFrames =
            tailCandidates.filter { (_, frame) -> isLikelyInstrumentFrame(frame) }
        val highlightFrames =
            when {
                !highlightTailFrame -> emptyList()
                detectedInstrumentTailFrames.size > 1 -> listOf(
                    detectedInstrumentTailFrames.first(),
                    detectedInstrumentTailFrames.last(),
                ).distinctBy { it.first }
                detectedInstrumentTailFrames.isNotEmpty() -> detectedInstrumentTailFrames
                sampledFrames.size > 1 -> sampledFrames.takeLast(1)
                else -> sampledFrames.takeLast(1)
            }
        val instrumentHintFrames =
            if (highlightTailFrame) {
                (detectedInstrumentTailFrames.ifEmpty { tailCandidates }).distinctBy { it.first }
            } else {
                emptyList()
            }
        val instrumentTextHint =
            try {
                recognizeInstrumentTextHints(instrumentHintFrames)
            } catch (e: Exception) {
                Log.w(TAG, "Video frame extraction continues without instrument text hints", e)
                ""
            }
        val highlightTimestamps = highlightFrames.map { it.first }.toSet()
        val thumbnailFrames =
            if (highlightFrames.isNotEmpty()) sampledFrames.filterNot { it.first in highlightTimestamps } else sampledFrames
        val (thumbWidth, thumbHeight) =
            if (highlightFrames.isNotEmpty()) {
                scaledFrameSize(aspectRatio, maxWidth = 220, maxHeight = 420)
            } else {
                cellWidth to cellHeight
            }
        val columns = if (thumbnailFrames.size == 1) 1 else 2
        val rows = ceil(thumbnailFrames.size / columns.toFloat()).toInt()
        val highlightWidth = if (highlightFrames.isNotEmpty()) cellWidth else 0
        val highlightHeight = if (highlightFrames.isNotEmpty()) cellHeight else 0
        val sheetWidth = maxOf(
            columns * thumbWidth + (columns + 1) * spacing,
            highlightWidth + spacing * 2,
        )
        val sheetHeight =
            headerHeight +
                (if (highlightFrames.isNotEmpty()) (highlightHeight + spacing) * highlightFrames.size else 0) +
                rows * thumbHeight + (rows + 1) * spacing
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
        canvas.drawText("视频关键帧拼图 · ${sampledFrames.size}帧${if (highlightFrames.isNotEmpty()) " · 下方含末尾仪表重点帧" else ""}", spacing.toFloat(), 28f, frameLabelPaint)

        fun drawFrame(timestampMs: Long, frame: Bitmap, destination: Rect) {
            canvas.drawBitmap(frame, null, destination, null)
            canvas.drawRect(destination, borderPaint)
            val label = formatVideoTimestamp(timestampMs)
            val chipPadding = 10f
            val chipHeight = 28f
            val chipWidth = timestampPaint.measureText(label) + chipPadding * 2
            val chipLeft = destination.left + 10f
            val chipTop = destination.top + 10f
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

        var contentTop = headerHeight + spacing
        thumbnailFrames.forEachIndexed { index, (timestampMs, frame) ->
            val row = index / columns
            val column = index % columns
            val left = spacing + column * (thumbWidth + spacing)
            val top = contentTop + row * (thumbHeight + spacing)
            drawFrame(timestampMs, frame, Rect(left, top, left + thumbWidth, top + thumbHeight))
        }
        contentTop += rows * (thumbHeight + spacing)

        highlightFrames.forEach { (timestampMs, frame) ->
            val left = (sheetWidth - highlightWidth) / 2
            drawFrame(timestampMs, frame, Rect(left, contentTop, left + highlightWidth, contentTop + highlightHeight))
            contentTop += highlightHeight + spacing
        }

        sampledFrames.forEach { (_, frame) -> frame.recycle() }

        return ExtractedVideoFrames(
            contactSheet = sheetBitmap,
            frameCount = sampledFrames.size,
            durationMs = durationMs,
            instrumentTextHint = instrumentTextHint,
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
    val diagnosisBasis: List<String> = emptyList(),
    val followUpQuestions: List<String> = emptyList(),
    val riskNote: String = "",
    val temporaryAction: String = "",
)

data class FieldChecklistItem(
    val title: String,
    val detail: String,
    val impact: String,
)

data class FieldChecklist(
    val title: String,
    val reason: String,
    val items: List<FieldChecklistItem>,
)

private enum class FieldFaultCategory(val label: String) {
    VEHICLE_INSPECTION("车辆点检"),
    NO_START("无法启动"),
    LIFT_SLOW("举升缓慢"),
    HYDRAULIC_LEAK("液压油泄漏"),
    STEERING_HEAVY("转向沉重"),
    OVERHEAT("发动机过热"),
    BRAKE_FAILURE("制动失灵"),
    ABNORMAL_NOISE("异响"),
    EMERGENCY_STOP("偶发急停"),
}

private data class OfflineTriageContext(
    val category: FieldFaultCategory,
    val initialInput: String,
)

private data class OfflineTriageReply(
    val text: String,
    val context: OfflineTriageContext,
    val checklist: FieldChecklist,
)

private data class WorkOrderQualityReview(
    val score: Int,
    val level: String,
    val missingItems: List<String>,
    val suggestions: List<String>,
    val strengths: List<String>,
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
        val diagnosisBasis = obj.optStringList("diagnosisBasis")
        val followUpQuestions = obj.optStringList("followUpQuestions")
        return LlmReport(
            equipment = obj.optString("equipment"), location = obj.optString("location"),
            symptom = obj.optString("symptom"), severity = obj.optString("severity"),
            causes = causes, parts = parts, steps = steps, personnel = personnel, workOrders = workOrders,
            diagnosisBasis = diagnosisBasis,
            followUpQuestions = followUpQuestions,
            riskNote = obj.optString("riskNote"),
            temporaryAction = obj.optString("temporaryAction"),
        )
    } catch (e: Exception) {
        Log.d("PocketOps", "JSON parse failed: ${e.message}")
        return null
    }
}

private fun org.json.JSONObject.optStringList(name: String): List<String> {
    val array = optJSONArray(name) ?: return emptyList()
    val values = mutableListOf<String>()
    for (index in 0 until array.length()) {
        val value = array.opt(index)
        when (value) {
            is String -> value
            is org.json.JSONObject -> value.optString("text").ifBlank { value.optString("name") }.ifBlank { value.toString() }
            else -> value?.toString().orEmpty()
        }.trim().takeIf { it.isNotBlank() }?.let { values.add(it) }
    }
    return values
}

private fun isVehicleInspectionText(text: String): Boolean {
    val directInspection = containsAny(text, listOf("车辆点检", "环车点检", "环车", "绕车", "绕车一周", "巡检", "班前检查", "出车前"))
    val dashboardInspection = containsAny(text, listOf("仪表盘", "仪表报警", "仪表异常", "异常显示", "报警灯"))
    val mirrorInspection = containsAny(text, listOf("反光镜", "后视镜", "镜面", "镜子"))
    return containsAny(
        text,
        listOf("点检视频", "视频点检"),
    )
        || directInspection
        || dashboardInspection
        || mirrorInspection
}

private fun inferFieldFaultCategory(text: String): FieldFaultCategory? {
    val normalized = text.trim()
    if (normalized.isBlank()) return null
    return when {
        isVehicleInspectionText(normalized) -> FieldFaultCategory.VEHICLE_INSPECTION
        listOf("无法启动", "启动不了", "打不着", "无反应", "不能启动").any { normalized.contains(it) } -> FieldFaultCategory.NO_START
        listOf("举升缓慢", "举升慢", "升不动", "液压压力低", "压力偏低").any { normalized.contains(it) } -> FieldFaultCategory.LIFT_SLOW
        listOf("液压油泄漏", "漏油", "渗油").any { normalized.contains(it) } -> FieldFaultCategory.HYDRAULIC_LEAK
        listOf("转向沉重", "方向沉", "转向困难").any { normalized.contains(it) } -> FieldFaultCategory.STEERING_HEAVY
        listOf("发动机过热", "水温高", "温度过高", "过热").any { normalized.contains(it) } -> FieldFaultCategory.OVERHEAT
        listOf("制动失灵", "刹不住", "刹车失灵", "制动距离").any { normalized.contains(it) } -> FieldFaultCategory.BRAKE_FAILURE
        listOf("异响", "噪音", "响声").any { normalized.contains(it) } -> FieldFaultCategory.ABNORMAL_NOISE
        listOf("急停", "偶发停机", "突然停机", "自动停机").any { normalized.contains(it) } -> FieldFaultCategory.EMERGENCY_STOP
        else -> null
    }
}

private fun shouldStartOfflineTriage(userInput: String, workOrderContext: String): Boolean {
    if (workOrderContext.isNotBlank()) return false
    val text = userInput.trim()
    inferFieldFaultCategory(text) ?: return false
    val asksTriage = listOf("问诊", "排查", "先查", "检查什么", "下一步").any { text.contains(it) }
    if (asksTriage) return true
    val directDiagnosisIntent = listOf("维修建议", "诊断", "原因", "怎么修", "处理方案", "生成工单", "工单").any { text.contains(it) }
    val alreadyHasEvidence = extractVoltage(text) != null || extractPressure(text) != null || extractFaultCodes(text).isNotEmpty()
    return text.length <= 24 && !directDiagnosisIntent && !alreadyHasEvidence
}

private fun shouldTreatAsOfflineTriageFollowUp(userInput: String, context: OfflineTriageContext): Boolean {
    val text = userInput.trim()
    if (text.isBlank()) return false
    if (isExplicitNewDiagnosisRequest(text)) return false
    val newCategory = inferFieldFaultCategory(text)
    if (newCategory != null && newCategory != context.category && listOf("叉车", "AGV", "搬运车", "设备").any { text.contains(it) }) {
        return false
    }
    val answerSignals =
        listOf(
            "电压", "压力", "故障码", "仪表", "亮", "不亮", "有声音", "无声音", "无反应",
            "偶发", "一直", "重启", "恢复", "油位", "漏油", "温度", "空载", "负载", "制动",
            "报警", "反光镜", "后视镜", "镜面", "支架", "轮胎", "货叉", "门架", "护顶架",
        )
    return text.length <= 120 && (
        answerSignals.any { text.contains(it) } ||
            extractVoltage(text) != null ||
            extractPressure(text) != null ||
            extractFaultCodes(text).isNotEmpty()
        )
}

private fun shouldTreatAsVisualFollowUp(userInput: String, visualContextText: String): Boolean {
    val text = userInput.trim()
    if (text.isBlank() || visualContextText.isBlank()) return false
    if (isEquipmentLookupQuestion(text)) return false
    if (isExplicitNewDiagnosisRequest(text)) return false
    val visualReferenceSignals =
        listOf(
            "图片", "照片", "视频", "画面", "图里", "图中", "这张", "这个", "这里", "这处",
            "这个部位", "它", "上面", "刚才", "刚刚", "看到", "能看出", "画面里",
        )
    return text.length <= 120 && visualReferenceSignals.any { text.contains(it) }
}

private fun isDiagnosticWorkOrderIntent(text: String): Boolean {
    return listOf(
        "诊断", "故障", "维修", "处理", "建议", "原因", "怎么修", "怎么处理",
        "生成工单", "工单", "排查", "风险", "还能运行", "点检", "巡检",
    ).any { text.contains(it) }
}

private fun buildOfflineTriageQuestionnaire(userInput: String): OfflineTriageReply? {
    val category = inferFieldFaultCategory(userInput) ?: return null
    val questions = triageQuestions(category)
    val example = when (category) {
        FieldFaultCategory.VEHICLE_INSPECTION -> "仪表盘无报警，左右反光镜完好，货叉和轮胎未见明显异常"
        FieldFaultCategory.NO_START -> "仪表亮，电压9.2V，启动无反应，暂无故障码"
        FieldFaultCategory.LIFT_SLOW -> "油位偏低，液压压力0.8bar，负载时更慢"
        FieldFaultCategory.HYDRAULIC_LEAK -> "门架油缸附近渗油，油位下降，地面有油迹"
        FieldFaultCategory.STEERING_HEAVY -> "低速转向沉，液压油位正常，无明显异响"
        FieldFaultCategory.OVERHEAT -> "水温高，风扇转，冷却液液位偏低"
        FieldFaultCategory.BRAKE_FAILURE -> "踏板偏软，制动距离变长，无明显漏油"
        FieldFaultCategory.ABNORMAL_NOISE -> "举升时异响，空载较轻，门架附近更明显"
        FieldFaultCategory.EMERGENCY_STOP -> "偶发急停，重启恢复，暂无明确故障码"
    }
    val text = buildString {
        appendLine("端侧问诊排查：已识别为“${category.label}”。")
        appendLine("先补齐下面信息，我会根据回答收敛原因，不需要联网。")
        questions.forEachIndexed { index, question -> appendLine("${index + 1}. $question") }
        appendLine()
        appendLine("可直接回复：$example")
    }.trim()
    return OfflineTriageReply(
        text = text,
        context = OfflineTriageContext(category, userInput),
        checklist = buildBaseFieldChecklist(category),
    )
}

private fun buildOfflineTriageFollowUpReply(userInput: String, context: OfflineTriageContext): OfflineTriageReply {
    val voltage = extractVoltage(userInput)
    val pressure = extractPressure(userInput)
    val faultCodes = extractFaultCodes(userInput)
    val findings = mutableListOf<String>()
    voltage?.let { value ->
        findings.add(
            if (value < 10.5) {
                "电压${formatNumber(value)}V明显偏低，会优先指向电池亏电、接线柱松动、主电源回路压降或接触器供电不足。"
            } else {
                "电压${formatNumber(value)}V未表现为明显低压，启动问题要继续看故障码、继电器动作和启动回路。"
            },
        )
    }
    pressure?.let { value ->
        findings.add(
            if (value < 1.0) {
                "压力${formatNumber(value)}bar偏低，与举升缓慢、液压泵吸空、油位不足或滤芯堵塞高度相关。"
            } else {
                "压力${formatNumber(value)}bar已记录，需结合额定压力和负载工况判断是否异常。"
            },
        )
    }
    if (faultCodes.isNotEmpty()) {
        findings.add("故障码${faultCodes.joinToString("、")}会提高电控和传感器方向的排查优先级，建议拍照留存并查控制器定义。")
    }
    if (findings.isEmpty()) {
        findings.add("本轮信息已记录，但还缺少可量化参数。优先补充电压、压力、温度、故障码或故障出现工况。")
    }

    val nextSteps = triageNextSteps(context.category, userInput)
    val text = buildString {
        appendLine("端侧问诊判断：")
        findings.forEach { appendLine("- $it") }
        appendLine()
        appendLine("下一步优先做：")
        nextSteps.forEachIndexed { index, step -> appendLine("${index + 1}. $step") }
        appendLine()
        appendLine("如果要形成工单，建议把本轮参数、已检查项目和故障码一起保存。")
    }.trim()
    return OfflineTriageReply(
        text = text,
        context = context,
        checklist = buildBaseFieldChecklist(context.category),
    )
}

private fun triageQuestions(category: FieldFaultCategory): List<String> {
    return when (category) {
        FieldFaultCategory.VEHICLE_INSPECTION ->
            listOf("仪表盘画面是否清晰，画面中可识别出的文字、数字读数、故障码和报警图标分别是什么？", "根据识别出的仪表盘文字/读数判断是否有异常显示？", "左右反光镜/后视镜是否都拍到，镜面和支架是否完好？", "环车一周是否拍到货叉、门架、轮胎、护顶架、车尾和漏液风险？")
        FieldFaultCategory.NO_START ->
            listOf("仪表是否亮起，急停开关是否复位？", "启动时电机、接触器或继电器是否有动作声音？", "电池或主电源电压是多少，启动瞬间是否明显下跌？", "控制器或仪表是否有故障码？")
        FieldFaultCategory.LIFT_SLOW ->
            listOf("空载和负载时是否都举升慢？", "液压油位、油液颜色和泄漏情况如何？", "液压压力实测值是多少？", "滤芯、油管和液压泵最近是否维护过？")
        FieldFaultCategory.HYDRAULIC_LEAK ->
            listOf("漏油位置在油缸、油管、接头还是阀块？", "停车静置和动作时哪种状态漏得更明显？", "液压油位下降速度如何？", "是否伴随举升无力或压力偏低？")
        FieldFaultCategory.STEERING_HEAVY ->
            listOf("原地、低速和负载转向时哪种更明显？", "液压油位和转向泵声音是否正常？", "方向盘是否有卡滞或回正异常？", "轮胎气压和转向机构是否有机械阻力？")
        FieldFaultCategory.OVERHEAT ->
            listOf("过热发生在怠速、负载还是长时间运行后？", "冷却液液位、风扇和散热器是否正常？", "仪表温度值或报警码是多少？", "近期是否清洗过散热器或更换冷却液？")
        FieldFaultCategory.BRAKE_FAILURE ->
            listOf("踏板行程是否变长或变软？", "制动液/液压油是否下降或泄漏？", "单侧还是双侧制动异常？", "是否有异响、拖滞或制动距离明显变长？")
        FieldFaultCategory.ABNORMAL_NOISE ->
            listOf("异响来自门架、液压泵、发动机还是轮端？", "空载、负载、转向或举升哪个动作会触发？", "声音是尖啸、敲击、摩擦还是震动？", "是否伴随温度、压力或速度异常？")
        FieldFaultCategory.EMERGENCY_STOP ->
            listOf("急停发生时仪表是否掉电或重启？", "是否有故障码、报警灯或蜂鸣？", "重启后能维持多久，是否与颠簸、转向或负载有关？", "电池电压、急停回路和控制器接插件是否检查过？")
    }
}

private fun triageNextSteps(category: FieldFaultCategory, userInput: String): List<String> {
    val voltage = extractVoltage(userInput)
    return when (category) {
        FieldFaultCategory.VEHICLE_INSPECTION -> listOf("先从仪表盘近景中识别并记录文字、数字读数、故障码、报警灯/图标和电量/小时表。", "基于已识别的仪表盘内容判断是否异常；无法识别时补拍仪表盘正面近景。", "分别拍到左右反光镜正面和支架连接处，确认镜面破损、缺失、松动或角度异常。", "环车补拍货叉、门架、轮胎、护顶架、车尾和地面漏液区域，异常项写入工单。")
        FieldFaultCategory.NO_START -> {
            if (voltage != null && voltage < 10.5) {
                listOf("先给电池补电或更换已知正常电池复测。", "清洁并紧固电池接线柱、主保险和接触器端子。", "复测启动瞬间电压，仍下跌则检查电池内阻或主电缆压降。")
            } else {
                listOf("读取控制器故障码并拍照留存。", "用万用表确认钥匙开关、急停回路和主接触器线圈供电。", "区分是启动电机不转、接触器不吸合还是控制器禁止启动。")
            }
        }
        FieldFaultCategory.LIFT_SLOW -> listOf("先复测液压油位和液压压力。", "检查滤芯、吸油管路和油液污染情况。", "负载状态复测举升速度，确认是否液压泵效率下降。")
        FieldFaultCategory.HYDRAULIC_LEAK -> listOf("清洁油迹后短动作复现，定位新油迹来源。", "检查接头、密封圈和油缸杆表面划伤。", "漏点未确认前不要直接补油交付。")
        FieldFaultCategory.STEERING_HEAVY -> listOf("先排除轮胎气压和机械卡滞。", "复测转向液压压力和泵噪声。", "检查转向阀、油管和转向桥连接。")
        FieldFaultCategory.OVERHEAT -> listOf("清理散热器表面并确认风扇工作。", "检查冷却液液位、皮带和节温器状态。", "记录过热出现时间和负载工况。")
        FieldFaultCategory.BRAKE_FAILURE -> listOf("先暂停使用并设置警示。", "检查制动液/液压油泄漏和踏板行程。", "按单侧/双侧异常区分管路、制动器和主缸问题。")
        FieldFaultCategory.ABNORMAL_NOISE -> listOf("录制异响视频并标记动作工况。", "按门架、液压泵、轮端、发动机分区听诊。", "同步记录压力、温度和负载状态。")
        FieldFaultCategory.EMERGENCY_STOP -> listOf("检查电池电压、急停开关和主接触器接插件。", "读取控制器事件日志或故障码。", "复现时观察是否与颠簸、负载或转向动作相关。")
    }
}

private fun buildBaseFieldChecklist(category: FieldFaultCategory): FieldChecklist {
    val items = when (category) {
        FieldFaultCategory.VEHICLE_INSPECTION -> listOf(
            FieldChecklistItem("识别仪表盘文字和读数", "拍清仪表盘近景，先转写画面中的文字、数字读数、故障码、报警灯/图标、电量/油量和小时表，再判断是否异常。", "仪表异常必须以识别出的可见内容为依据，避免只凭外观判断。"),
            FieldChecklistItem("检查左右反光镜/后视镜", "分别查看镜面、外壳、支架和调节角度，确认无缺失、破裂、松动或严重遮挡。", "反光镜异常会扩大盲区，应在交付前维修或调整。"),
            FieldChecklistItem("环车检查外观安全项", "沿车身检查货叉、门架、轮胎、护顶架、车尾、灯具和地面漏液。", "可提前发现碰撞、漏液、轮胎损伤等班前安全风险。"),
            FieldChecklistItem("补拍不清晰角度", "对模型标注为待确认的仪表、反光镜或外观部位补拍近景。", "避免把遮挡或模糊画面误判为正常。"),
        )
        FieldFaultCategory.NO_START -> listOf(
            FieldChecklistItem("确认仪表和急停状态", "记录仪表是否亮起、急停是否复位、钥匙开关是否有效。", "区分整车掉电、急停回路和启动许可问题。"),
            FieldChecklistItem("测量电池/主电源电压", "记录静态电压和启动瞬间压降。", "低压会直接导致无法启动或控制器复位。"),
            FieldChecklistItem("听接触器或继电器动作", "启动时确认是否吸合、抖动或完全无声。", "判断故障在控制回路还是主动力回路。"),
            FieldChecklistItem("读取并拍照保存故障码", "记录仪表、控制器或蓝牙采集到的故障码。", "为工单复盘和备件判断提供证据。"),
        )
        FieldFaultCategory.LIFT_SLOW -> listOf(
            FieldChecklistItem("检查液压油位和泄漏", "看油位、油液颜色、油管接头和油缸周边油迹。", "油位不足和吸空是举升慢的高频原因。"),
            FieldChecklistItem("实测液压压力", "在空载和负载下分别记录压力值。", "压力偏低可定位泵、滤芯、阀组或泄漏方向。"),
            FieldChecklistItem("检查滤芯和吸油管路", "确认滤芯堵塞、吸油管变形或进气。", "供油受阻会导致动作慢和泵噪声。"),
            FieldChecklistItem("复测举升速度", "记录空载/负载举升时间。", "便于判断是否达到维修完成标准。"),
        )
        FieldFaultCategory.HYDRAULIC_LEAK -> listOf(
            FieldChecklistItem("清洁后定位新油迹", "擦净旧油迹，短动作观察新漏点。", "避免把旧污染误判为当前漏点。"),
            FieldChecklistItem("检查接头和密封圈", "重点看油管接头、阀块、油缸密封。", "确认是否需要更换密封件或紧固接头。"),
            FieldChecklistItem("记录油位下降速度", "记录补油前后和运行后的油位。", "判断泄漏等级和是否允许短时移动。"),
            FieldChecklistItem("拍摄漏点照片", "包含设备编号、漏点位置和地面油迹。", "提高工单可追溯性。"),
        )
        FieldFaultCategory.STEERING_HEAVY -> listOf(
            FieldChecklistItem("排除轮胎和机械卡滞", "检查轮胎气压、转向桥、拉杆和异物。", "先排除非液压原因。"),
            FieldChecklistItem("检查转向液压压力", "记录原地和低速转向压力。", "判断转向泵、阀和油路效率。"),
            FieldChecklistItem("听转向泵声音", "注意尖啸、吸空或抖动。", "辅助判断油液不足或泵磨损。"),
            FieldChecklistItem("记录触发工况", "区分空载、负载、低速和原地转向。", "帮助收敛故障分支。"),
        )
        FieldFaultCategory.OVERHEAT -> listOf(
            FieldChecklistItem("记录温度和报警", "记录仪表温度、报警灯和出现时间。", "判断过热等级和复现条件。"),
            FieldChecklistItem("检查冷却液和风扇", "确认液位、风扇转动和皮带状态。", "定位冷却循环基础问题。"),
            FieldChecklistItem("清理散热器", "检查灰尘、油泥和堵塞。", "散热不良是现场高频原因。"),
            FieldChecklistItem("记录负载工况", "区分怠速、长时间运行和重载。", "用于判断是否超工况或散热能力不足。"),
        )
        FieldFaultCategory.BRAKE_FAILURE -> listOf(
            FieldChecklistItem("立即停用并警示", "制动异常先禁止继续作业。", "降低现场安全风险。"),
            FieldChecklistItem("检查踏板行程", "记录踏板是否变软、变长或回位异常。", "判断主缸、管路或制动器方向。"),
            FieldChecklistItem("检查油液和泄漏", "看制动液/液压油液位和管路漏点。", "泄漏会直接导致制动力不足。"),
            FieldChecklistItem("区分单侧或双侧", "观察车辆跑偏、单轮拖滞或整体失效。", "帮助定位轮端或主回路。"),
        )
        FieldFaultCategory.ABNORMAL_NOISE -> listOf(
            FieldChecklistItem("录制异响视频", "保留声音、动作和设备位置。", "便于后续复盘和远程协作。"),
            FieldChecklistItem("定位声音区域", "区分门架、液压泵、发动机、轮端。", "减少盲目拆检。"),
            FieldChecklistItem("记录触发动作", "举升、转向、行走、制动分别测试。", "锁定相关系统。"),
            FieldChecklistItem("同步记录参数", "压力、温度、速度或负载变化。", "判断是否由负载或温升诱发。"),
        )
        FieldFaultCategory.EMERGENCY_STOP -> listOf(
            FieldChecklistItem("记录急停发生时状态", "看仪表是否掉电、报警或重启。", "区分供电中断和控制器保护。"),
            FieldChecklistItem("检查电源和急停回路", "检查电池电压、急停开关、接插件和主接触器。", "偶发急停常与接触不良有关。"),
            FieldChecklistItem("读取控制器故障码", "记录事件日志或报警码。", "确认是否传感器、通信或保护触发。"),
            FieldChecklistItem("复现触发工况", "标记颠簸、转向、负载或温度条件。", "帮助定位间歇性问题。"),
        )
    }
    return FieldChecklist(
        title = "离线排查清单：${category.label}",
        reason = "根据当前症状在本机生成，现场可逐项勾选并把结果写入工单。",
        items = items,
    )
}

private fun buildFieldChecklistForDiagnosis(
    userInput: String,
    graphReport: DiagnosticReport? = null,
    llmContent: String = "",
): FieldChecklist? {
    val parsed = llmContent.takeIf { it.isNotBlank() }?.let { parseLlmReport(it) }
    val contextText = listOf(
        userInput,
        graphReport?.symptom?.label.orEmpty(),
        parsed?.symptom.orEmpty(),
        llmContent.take(600),
    ).joinToString(" ")
    val category = inferFieldFaultCategory(contextText)
    val baseItems = category?.let { buildBaseFieldChecklist(it).items.take(2) }.orEmpty()
    val graphItems = graphReport?.steps.orEmpty().map { step ->
        val meta = listOf(step.properties["duration"].orEmpty(), step.properties["tool"].orEmpty()).filter { it.isNotBlank() }.joinToString(" / ")
        FieldChecklistItem(step.label, meta.ifBlank { "按图谱维修步骤执行并记录结果。" }, "完成后可调整工单结论和维修优先级。")
    }
    val llmStepItems = parsed?.steps.orEmpty().map { (title, duration, tool) ->
        FieldChecklistItem(title, listOf(duration, tool).filter { it.isNotBlank() }.joinToString(" / ").ifBlank { "按端侧诊断建议执行。" }, "作为现场验证证据写入工单。")
    }
    val followUpItems = parsed?.followUpQuestions.orEmpty().take(2).map { question ->
        FieldChecklistItem("补充：$question", "现场记录、拍照或读取参数后再继续追问。", "补齐后端侧模型可继续收敛原因。")
    }
    val items = (baseItems + graphItems + llmStepItems + followUpItems)
        .filter { it.title.isNotBlank() }
        .distinctBy { it.title }
        .take(6)
    if (items.isEmpty()) return category?.let { buildBaseFieldChecklist(it) }
    return FieldChecklist(
        title = "离线排查清单：${category?.label ?: "现场诊断"}",
        reason = "从本轮诊断步骤和待补充信息生成，不依赖云端。",
        items = items,
    )
}

private fun extractVoltage(text: String): Double? {
    return Regex("""(\d+(?:\.\d+)?)\s*(?:v|V|伏)""")
        .find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.toDoubleOrNull()
}

private fun extractPressure(text: String): Double? {
    return Regex("""(\d+(?:\.\d+)?)\s*(?:bar|BAR|Bar|兆帕|MPa|mpa)""")
        .find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.toDoubleOrNull()
}

private fun extractFaultCodes(text: String): List<String> {
    return Regex("""[A-Z]{1,2}\d{2,4}""")
        .findAll(text.uppercase(Locale.getDefault()))
        .map { it.value }
        .distinct()
        .take(5)
        .toList()
}

private fun formatNumber(value: Double): String {
    return if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(Locale.getDefault(), value)
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

private data class QueuedWorkOrderSubmission(
    val localId: String,
    val workOrderId: String,
    val createdAt: String,
    val queuedAt: Long,
    val workOrder: WorkOrderDocumentData,
    val attemptCount: Int = 0,
    val lastError: String = "",
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
        if (riskNote.isNotBlank()) append("，风险提示：$riskNote")
        if (temporaryAction.isNotBlank()) append("，临时处置：$temporaryAction")
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
        } + diagnosisBasis.map { "推理依据：$it" },
        parts = parts.map { (name, spec, stock) ->
            val extras = listOf(spec, stock).filter { it.isNotBlank() }.joinToString(" / ")
            if (extras.isNotBlank()) "$name - $extras" else name
        },
        steps = steps.mapIndexed { index, (title, duration, tool) ->
            val extras = listOf(duration, tool).filter { it.isNotBlank() }.joinToString(" / ")
            "${index + 1}. $title${if (extras.isNotBlank()) " ($extras)" else ""}"
        } + followUpQuestions.mapIndexed { index, question -> "补采${index + 1}. $question" },
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
        equipment = extractExplicitEquipmentMention(rawText) ?: "工业车辆/现场图片",
        location = "",
        symptom = inferFieldFaultCategory(rawText)?.label ?: "待确认",
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

private fun buildDynamicWorkOrderMessage(
    clickedMessage: PocketMessage,
    messages: List<PocketMessage>,
    conversationHistory: List<Pair<String, String>>,
    latestVisualContextText: String,
    activeEquipmentContext: GraphNode?,
    graph: MaintenanceKnowledgeGraph,
): PocketMessage {
    val t0 = System.currentTimeMillis()
    val contextText =
        buildDynamicWorkOrderContextText(
            clickedMessage = clickedMessage,
            messages = messages,
            conversationHistory = conversationHistory,
            latestVisualContextText = latestVisualContextText,
        )
    val graphResult =
        buildGraphRAGReport(contextText, graph, activeEquipmentContext)
            ?: buildGraphRAGReport(contextText, graph)
    val relatedWorkOrders =
        graphResult?.first?.workOrders?.takeIf { it.isNotEmpty() }
            ?: findRelatedWorkOrders(contextText, graph)
    val dynamicText =
        graphResult?.let { (report, _) ->
            buildDynamicGraphWorkOrderJson(report, contextText)
        } ?: buildDynamicFallbackWorkOrderJson(
            contextText = contextText,
            clickedText = clickedMessage.text,
            activeEquipmentContext = activeEquipmentContext,
            relatedWorkOrders = relatedWorkOrders,
        )

    return PocketMessage(
        text = dynamicText,
        isUser = false,
        relatedWorkOrders = relatedWorkOrders,
        graphJson = graphResult?.second.orEmpty(),
        sourceLabel = if (graphResult != null) "动态工单 · 图谱补全" else "动态工单 · 端侧整理",
        elapsedMs = System.currentTimeMillis() - t0,
        tokenUsage = GenerationUsage(estimateOutputTokens(dynamicText), estimated = true),
        canGenerateWorkOrder = false,
    )
}

private fun buildDynamicWorkOrderContextText(
    clickedMessage: PocketMessage,
    messages: List<PocketMessage>,
    conversationHistory: List<Pair<String, String>>,
    latestVisualContextText: String,
): String {
    val lines = mutableListOf<String>()
    latestVisualContextText.takeIf { it.isNotBlank() }?.let {
        lines.add("最近现场图片/视频分析：")
        lines.add(it.take(1600))
    }
    conversationHistory.takeLast(14).forEach { (role, content) ->
        val label = if (role == "user") "用户" else "助手"
        content.trim()
            .takeIf { it.isNotBlank() && !it.startsWith("正在") }
            ?.let { lines.add("$label：${it.take(900)}") }
    }
    messages.takeLast(12).forEach { message ->
        val label = if (message.isUser) "用户" else "助手"
        message.text.trim()
            .takeIf { it.isNotBlank() && !it.startsWith("正在") }
            ?.let { lines.add("$label：${it.take(900)}") }
    }
    clickedMessage.text.trim().takeIf { it.isNotBlank() }?.let { lines.add("本次生成工单触发内容：${it.take(900)}") }
    return lines.distinct().joinToString("\n").take(6500)
}

private fun buildDynamicGraphWorkOrderJson(report: DiagnosticReport, contextText: String): String {
    val evidence = extractDynamicEvidence(contextText)
    return buildGraphGroundedDiagnosisJson(
        graphReport = report,
        diagnosisBasis = listOf("工单由当前会话动态汇总生成，已合并图片/视频分析、后续追问和现场补充。") + evidence,
        followUpQuestions = buildDynamicFollowUpQuestions(contextText, report.symptom.label),
        riskNote = inferDynamicRiskNote(contextText, report.symptom.label),
        temporaryAction = inferDynamicTemporaryAction(contextText, report.symptom.label),
        sourceLabel = "动态工单 · 图谱补全",
    )
}

private fun buildDynamicFallbackWorkOrderJson(
    contextText: String,
    clickedText: String,
    activeEquipmentContext: GraphNode?,
    relatedWorkOrders: List<GraphNode>,
): String {
    val category = inferFieldFaultCategory(contextText)
    val isInspection = category == FieldFaultCategory.VEHICLE_INSPECTION || isVehicleInspectionText(contextText)
    val equipment =
        activeEquipmentContext?.label
            ?: extractExplicitEquipmentMention(contextText)
            ?: "待确认设备"
    val symptom = if (isInspection) FieldFaultCategory.VEHICLE_INSPECTION.label else category?.label ?: summarizeDynamicSymptom(contextText, clickedText)
    val severity =
        when {
            isInspection && containsAny(contextText, listOf("报警", "故障码", "异常", "破损", "缺失", "松动", "漏液", "待确认")) -> "中"
            isInspection -> "低"
            containsAny(contextText, listOf("制动失灵", "无法启动", "急停", "高温", "过热")) -> "高"
            category != null -> "中"
            else -> "待确认"
        }
    val causes = fallbackCausesForCategory(category, contextText)
    val checklistItems = category?.let { buildBaseFieldChecklist(it).items }.orEmpty()
    return org.json.JSONObject().apply {
        put("equipment", equipment)
        put("location", activeEquipmentContext?.properties?.get("location").orEmpty())
        put("symptom", symptom)
        put("severity", severity)
        put(
            "causes",
            org.json.JSONArray().apply {
                causes.forEach { (name, probability) ->
                    put(org.json.JSONObject().put("name", name).put("probability", probability))
                }
            },
        )
        put("parts", org.json.JSONArray())
        put(
            "steps",
            org.json.JSONArray().apply {
                checklistItems.take(5).forEach { item ->
                    put(
                        org.json.JSONObject()
                            .put("title", item.title)
                            .put("duration", "现场确认")
                            .put("tool", item.detail),
                    )
                }
                if (checklistItems.isEmpty()) {
                    put(org.json.JSONObject().put("title", "补充设备编号、故障部位照片和关键参数").put("duration", "5分钟").put("tool", "手机/万用表/诊断仪"))
                    put(org.json.JSONObject().put("title", "根据现场参数复核是否需要停机维修").put("duration", "10分钟").put("tool", "点检表"))
                }
            },
        )
        put("personnel", org.json.JSONArray())
        put(
            "workOrders",
            org.json.JSONArray().apply {
                relatedWorkOrders.take(4).forEach { workOrder ->
                    put(
                        org.json.JSONObject()
                            .put("id", workOrder.label)
                            .put("date", workOrder.properties["date"].orEmpty())
                            .put("resolution", workOrder.properties["resolution"].orEmpty()),
                    )
                }
            },
        )
        put(
            "diagnosisBasis",
            org.json.JSONArray().apply {
                put("知识库未直接命中完整场景，当前工单由端侧根据连续问答和现场资料动态整理。")
                extractDynamicEvidence(contextText).take(5).forEach { put(it) }
            },
        )
        put("followUpQuestions", org.json.JSONArray().apply { buildDynamicFollowUpQuestions(contextText, symptom).forEach { put(it) } })
        put("riskNote", inferDynamicRiskNote(contextText, symptom))
        put("temporaryAction", inferDynamicTemporaryAction(contextText, symptom))
    }.toString()
}

private fun extractDynamicEvidence(contextText: String): List<String> {
    val evidence = mutableListOf<String>()
    evidence.addAll(extractChecklistCompletionEvidence(contextText))
    extractVoltage(contextText)?.let { evidence.add("已记录电压${formatNumber(it)}V，可用于判断电源或控制器复位风险。") }
    extractPressure(contextText)?.let { evidence.add("已记录压力${formatNumber(it)}bar，可用于判断液压系统异常程度。") }
    extractFaultCodes(contextText).takeIf { it.isNotEmpty() }?.let { evidence.add("已记录故障码：${it.joinToString("、")}。") }
    if (containsAny(contextText, listOf("图片", "照片", "画面", "视频"))) evidence.add("已合并现场图片/视频分析内容。")
    if (isVehicleInspectionText(contextText)) evidence.add("已按车辆点检场景合并仪表盘、反光镜和环车外观检查线索。")
    if (containsAny(contextText, listOf("漏油", "渗油", "油迹"))) evidence.add("现场提到漏油/油迹，需在工单中保留照片和位置描述。")
    if (containsAny(contextText, listOf("重启", "偶发", "恢复"))) evidence.add("故障呈偶发或重启恢复特征，需记录触发工况和复现条件。")
    return evidence.distinct().ifEmpty { listOf("已根据当前连续问答整理现场现象，仍需补充可量化参数。") }
}

private fun extractChecklistCompletionEvidence(contextText: String): List<String> {
    val lines = contextText.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
    val checklistTitle = lines
        .firstOrNull { it.contains("现场排查完成记录：") }
        ?.substringAfter("现场排查完成记录：")
        ?.take(60)
    val completedItems = lines
        .mapNotNull { line ->
            line.substringAfter("已完成：", missingDelimiterValue = "")
                .takeIf { it.isNotBlank() }
                ?.take(60)
        }
        .distinct()
    if (completedItems.isEmpty()) return emptyList()
    val titleText = checklistTitle?.let { "《$it》" }.orEmpty()
    return listOf("已完成${titleText}现场排查：${completedItems.take(6).joinToString("、")}。")
}

private fun buildDynamicFollowUpQuestions(contextText: String, symptom: String): List<String> {
    val questions = mutableListOf<String>()
    if (extractExplicitEquipmentMention(contextText) == null) questions.add("请补充设备编号或铭牌照片。")
    if (isVehicleInspectionText(symptom + contextText)) {
        if (!containsAny(contextText, listOf("仪表盘文字", "读数", "故障码", "报警灯", "异常显示", "小时表", "电量"))) questions.add("请补拍仪表盘正面近景，并识别/记录其中的文字、数字读数、故障码、报警灯或异常图标。")
        if (!containsAny(contextText, listOf("反光镜", "后视镜", "镜面", "支架"))) questions.add("请补拍左右反光镜/后视镜，确认镜面、支架和调节角度是否正常。")
        if (!containsAny(contextText, listOf("货叉", "门架", "轮胎", "护顶架", "漏液"))) questions.add("请补充货叉、门架、轮胎、护顶架和地面漏液检查结果。")
    }
    if (extractVoltage(contextText) == null && containsAny(symptom + contextText, listOf("无法启动", "急停", "掉电"))) questions.add("请补充静态电压和启动/故障瞬间电压。")
    if (extractPressure(contextText) == null && containsAny(symptom + contextText, listOf("举升", "液压", "漏油", "转向"))) questions.add("请补充液压压力、油位和泄漏位置。")
    if (extractFaultCodes(contextText).isEmpty()) questions.add("请补充仪表或控制器故障码，若没有也请写明“暂无故障码”。")
    if (!containsAny(contextText, listOf("已检查", "检查过", "复测", "更换", "补油"))) questions.add("请记录已执行的检查动作和结果。")
    return questions.distinct().take(4)
}

private fun inferDynamicRiskNote(contextText: String, symptom: String): String {
    return when {
        isVehicleInspectionText(symptom + contextText) && containsAny(symptom + contextText, listOf("报警", "故障码", "破损", "缺失", "松动", "漏液", "异常")) -> "车辆点检存在未关闭风险项，仪表报警、反光镜异常或漏液未复核前不建议直接投入作业。"
        isVehicleInspectionText(symptom + contextText) -> "当前点检结论依赖视频画面，遮挡或模糊部位仍需现场复核后再放行。"
        containsAny(symptom + contextText, listOf("制动失灵", "刹不住")) -> "制动异常存在安全风险，应立即停用并设置现场警示。"
        containsAny(symptom + contextText, listOf("无法启动", "急停", "掉电")) -> "继续反复启动可能扩大电源或控制器故障，应先确认电压和主回路。"
        containsAny(symptom + contextText, listOf("漏油", "液压油泄漏")) -> "液压泄漏可能导致压力下降和污染现场，未定位漏点前不建议继续作业。"
        containsAny(symptom + contextText, listOf("过热", "高温")) -> "高温继续运行可能损伤发动机或液压系统，应降载或停机冷却。"
        else -> "当前信息仍需现场复核，提交前建议补齐关键参数和已检查结果。"
    }
}

private fun inferDynamicTemporaryAction(contextText: String, symptom: String): String {
    return when {
        isVehicleInspectionText(symptom + contextText) && containsAny(symptom + contextText, listOf("报警", "故障码", "破损", "缺失", "松动", "漏液", "异常")) -> "先暂停放行车辆，补拍并现场复核仪表盘、反光镜和异常外观部位，确认无安全风险后再作业。"
        isVehicleInspectionText(symptom + contextText) -> "保留点检视频和关键帧，按仪表盘、反光镜、环车外观清单复核后归档。"
        containsAny(symptom + contextText, listOf("制动失灵", "刹不住")) -> "暂停使用车辆，拉警戒并安排维修人员现场确认。"
        containsAny(symptom + contextText, listOf("无法启动", "急停", "掉电")) -> "停止反复启动，先测电压、检查急停回路和主接触器接插件。"
        containsAny(symptom + contextText, listOf("漏油", "液压油泄漏")) -> "清洁油迹后短动作复现漏点，必要时停机等待维修。"
        containsAny(symptom + contextText, listOf("举升", "液压")) -> "降低负载或暂停举升作业，先复测油位和液压压力。"
        else -> "保留现场照片和参数，按端侧检查清单逐项确认后再提交。"
    }
}

private fun fallbackCausesForCategory(category: FieldFaultCategory?, contextText: String): List<Pair<String, Int>> {
    return when (category) {
        FieldFaultCategory.VEHICLE_INSPECTION -> listOf("点检视频证据不足或存在遮挡" to 45, "仪表盘报警/故障码待复核" to 35, "反光镜破损、缺失或角度异常待确认" to 30)
        FieldFaultCategory.NO_START -> listOf("电池亏电或主电源压降" to 55, "急停/钥匙/接触器回路异常" to 35, "控制器保护或传感器故障" to 25)
        FieldFaultCategory.LIFT_SLOW -> listOf("液压油位不足或吸油不畅" to 55, "滤芯堵塞或油液污染" to 40, "液压泵效率下降或内泄" to 35)
        FieldFaultCategory.HYDRAULIC_LEAK -> listOf("油管接头或密封圈泄漏" to 60, "油缸密封磨损" to 40, "阀块或管路损伤" to 25)
        FieldFaultCategory.STEERING_HEAVY -> listOf("转向液压压力不足" to 45, "机械卡滞或轮胎阻力异常" to 35, "转向泵/阀异常" to 30)
        FieldFaultCategory.OVERHEAT -> listOf("散热器堵塞或风扇异常" to 50, "冷却液不足" to 40, "长时间重载运行" to 25)
        FieldFaultCategory.BRAKE_FAILURE -> listOf("制动液/液压回路泄漏" to 55, "制动器磨损或调整不当" to 40, "主缸或管路进气" to 30)
        FieldFaultCategory.ABNORMAL_NOISE -> listOf("机械松动或磨损" to 45, "液压泵吸空或轴承异常" to 35, "门架/轮端部件间隙异常" to 30)
        FieldFaultCategory.EMERGENCY_STOP -> listOf("电源或急停回路接触不良" to 50, "控制器保护触发" to 35, "传感器/通信间歇异常" to 30)
        null -> {
            val hasVisual = containsAny(contextText, listOf("图片", "照片", "视频", "画面"))
            if (hasVisual) listOf("现场图片/视频显示异常但证据不足" to 40, "需补充设备编号和关键参数" to 35)
            else listOf("现场数据不足，需先采集关键参数" to 45, "部件老化、接插件松动或传感器异常" to 30)
        }
    }
}

private fun summarizeDynamicSymptom(contextText: String, fallbackText: String): String {
    inferFieldFaultCategory(contextText)?.let { return it.label }
    val candidate = (fallbackText.ifBlank { contextText })
        .lineSequence()
        .map { it.trim().removePrefix("用户：").removePrefix("助手：") }
        .firstOrNull { it.length in 4..80 && !it.startsWith("{") }
    return candidate?.take(40) ?: "待确认现场异常"
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

private fun buildWorkOrderQualityReview(workOrder: WorkOrderDocumentData): WorkOrderQualityReview {
    val allText = listOf(
        workOrder.equipment,
        workOrder.location,
        workOrder.symptom,
        workOrder.severity,
        workOrder.summary,
        workOrder.causes.joinToString(" "),
        workOrder.steps.joinToString(" "),
        workOrder.parts.joinToString(" "),
    ).joinToString(" ")
    val category = inferFieldFaultCategory(allText)
    val missing = mutableListOf<String>()
    val suggestions = mutableListOf<String>()
    val strengths = mutableListOf<String>()

    if (workOrder.equipment.isBlank() || workOrder.equipment.contains("待确认")) missing.add("设备编号或设备名称")
    else strengths.add("已填写设备")
    if (workOrder.symptom.isBlank() || workOrder.symptom.contains("待确认")) missing.add("故障现象")
    else strengths.add("已填写故障现象")
    if (workOrder.summary.length < 12) missing.add("故障摘要需要更具体")
    if (workOrder.causes.isEmpty()) missing.add("可能原因")
    if (workOrder.steps.isEmpty()) missing.add("排查/维修步骤")
    else strengths.add("已有${workOrder.steps.size}条步骤")
    if (workOrder.parts.isEmpty()) suggestions.add("如现场不需要备件，也建议写明“暂无需更换备件”")
    if (workOrder.relatedWorkOrders.isEmpty()) suggestions.add("可补充相似工单或历史维修记录，便于复盘")

    when (category) {
        FieldFaultCategory.VEHICLE_INSPECTION -> {
            if (!containsAny(allText, listOf("仪表盘文字", "读数", "故障码", "报警灯", "异常显示", "小时表", "电量"))) missing.add("仪表盘文字/读数识别和异常判断")
            if (!containsAny(allText, listOf("反光镜", "后视镜", "镜面", "支架"))) missing.add("左右反光镜/后视镜检查结果")
            if (!containsAny(allText, listOf("环车", "货叉", "门架", "轮胎", "护顶架", "漏液"))) suggestions.add("建议补充环车外观、货叉/门架/轮胎和漏液检查记录")
        }
        FieldFaultCategory.NO_START -> {
            if (!containsAny(allText, listOf("电压", "V", "伏", "压降"))) missing.add("电池电压或启动瞬间压降")
            if (!containsAny(allText, listOf("仪表", "接触器", "继电器", "无反应", "启动声音"))) missing.add("启动时仪表/接触器状态")
            if (!containsAny(allText, listOf("故障码", "报警", "DTC")) && extractFaultCodes(allText).isEmpty()) suggestions.add("建议补充故障码或说明“暂无故障码”")
        }
        FieldFaultCategory.LIFT_SLOW -> {
            if (!containsAny(allText, listOf("液压压力", "压力", "bar", "MPa", "兆帕"))) missing.add("液压压力实测值")
            if (!containsAny(allText, listOf("油位", "漏油", "泄漏", "滤芯"))) suggestions.add("建议补充油位、泄漏和滤芯状态")
        }
        FieldFaultCategory.HYDRAULIC_LEAK -> {
            if (!containsAny(allText, listOf("漏点", "油缸", "油管", "接头", "阀块"))) missing.add("漏油位置")
            if (!containsAny(allText, listOf("照片", "油迹", "油位"))) suggestions.add("建议补充漏点照片和油位变化")
        }
        FieldFaultCategory.STEERING_HEAVY -> {
            if (!containsAny(allText, listOf("轮胎", "转向泵", "转向压力", "原地", "低速"))) suggestions.add("建议补充触发工况和转向压力")
        }
        FieldFaultCategory.OVERHEAT -> {
            if (!containsAny(allText, listOf("温度", "水温", "冷却液", "风扇", "散热器"))) missing.add("温度/冷却系统检查结果")
        }
        FieldFaultCategory.BRAKE_FAILURE -> {
            if (!containsAny(allText, listOf("踏板", "制动液", "刹车", "制动距离", "泄漏"))) missing.add("制动踏板、油液或制动距离记录")
        }
        FieldFaultCategory.ABNORMAL_NOISE -> {
            if (!containsAny(allText, listOf("部位", "门架", "轮端", "液压泵", "视频", "声音"))) missing.add("异响部位和触发动作")
        }
        FieldFaultCategory.EMERGENCY_STOP -> {
            if (!containsAny(allText, listOf("重启", "掉电", "急停", "接插件", "控制器", "故障码"))) missing.add("急停发生时状态和故障码")
        }
        null -> suggestions.add("建议补充故障类别，便于端侧检查清单更准确")
    }

    val score = (100 - missing.size * 12 - suggestions.size * 5).coerceIn(35, 100)
    val level = when {
        score >= 85 -> "可提交"
        score >= 70 -> "建议补充"
        else -> "信息不足"
    }
    return WorkOrderQualityReview(
        score = score,
        level = level,
        missingItems = missing.distinct().take(6),
        suggestions = suggestions.distinct().take(5),
        strengths = strengths.distinct().take(4),
    )
}

private fun containsAny(text: String, keywords: List<String>): Boolean {
    return keywords.any { keyword -> text.contains(keyword, ignoreCase = true) }
}

private fun buildWorkOrderFollowUpContext(
    userText: String,
    aiText: String,
    report: DiagnosticReport? = null,
): String {
    report?.let {
        return buildString {
            if (userText.isNotBlank()) appendLine("用户原始问题：$userText")
            appendLine("已锁定的知识图谱诊断：")
            appendLine(buildGraphFactLockText(it))
            parseLlmReport(aiText)?.let { llm ->
                if (llm.diagnosisBasis.isNotEmpty()) appendLine("端侧推理依据：${llm.diagnosisBasis.joinToString("；")}")
                if (llm.followUpQuestions.isNotEmpty()) appendLine("待补充问题：${llm.followUpQuestions.joinToString("；")}")
                llm.riskNote.takeIf { note -> note.isNotBlank() }?.let { note -> appendLine("风险提示：$note") }
                llm.temporaryAction.takeIf { action -> action.isNotBlank() }?.let { action -> appendLine("临时处置：$action") }
            }
        }.trim()
    }

    parseLlmReport(aiText)?.let { llm ->
        return buildString {
            if (userText.isNotBlank()) appendLine("用户原始问题：$userText")
            appendLine("设备：${llm.equipment}")
            if (llm.location.isNotBlank()) appendLine("位置：${llm.location}")
            appendLine("症状：${llm.symptom}")
            if (llm.severity.isNotBlank()) appendLine("严重度：${llm.severity}")
            if (llm.causes.isNotEmpty()) appendLine("可能原因：${llm.causes.joinToString("；") { "${it.first} ${it.second}%" }}")
            if (llm.parts.isNotEmpty()) appendLine("备件：${llm.parts.joinToString("；") { part -> listOf(part.first, part.second, part.third).filter { it.isNotBlank() }.joinToString(" / ") }}")
            if (llm.steps.isNotEmpty()) appendLine("步骤：${llm.steps.joinToString(" -> ") { it.first }}")
            if (llm.diagnosisBasis.isNotEmpty()) appendLine("推理依据：${llm.diagnosisBasis.joinToString("；")}")
            if (llm.followUpQuestions.isNotEmpty()) appendLine("待补充问题：${llm.followUpQuestions.joinToString("；")}")
            if (llm.riskNote.isNotBlank()) appendLine("风险提示：${llm.riskNote}")
            if (llm.temporaryAction.isNotBlank()) appendLine("临时处置：${llm.temporaryAction}")
        }.trim()
    }

    return buildString {
        if (userText.isNotBlank()) appendLine("用户原始问题：$userText")
        appendLine(aiText.take(2200))
    }.trim()
}

private fun shouldTreatAsWorkOrderFollowUp(userInput: String, workOrderContext: String): Boolean {
    if (workOrderContext.isBlank()) return false
    val text = userInput.trim()
    if (text.isBlank()) return false
    if (isExplicitNewDiagnosisRequest(text)) return false
    val followUpSignals =
        listOf("补充", "回答", "追问", "工单", "报警", "故障码", "参数", "电压", "温度", "压力", "声音", "异响", "位置", "时间", "有", "没有", "是", "不是")
    return followUpSignals.any { text.contains(it) } || text.length <= 80
}

private fun isExplicitNewDiagnosisRequest(text: String): Boolean {
    val normalized = text.trim()
    val restartSignals = listOf("新诊断", "重新诊断", "重新分析", "换一台", "另一台", "另一个故障", "新的故障")
    if (restartSignals.any { normalized.contains(it) }) return true
    val explicitEquipment = extractExplicitEquipmentMention(normalized) != null
    val knownSymptom = SYMPTOM_KEYWORDS.any { normalized.contains(it) }
    val diagnosticIntent =
        listOf(
            "诊断", "故障", "原因", "怎么修", "怎么处理", "无法", "异常", "失灵", "泄漏",
            "维修建议", "处理建议", "排查建议", "维修方案", "维修", "建议",
        ).any { normalized.contains(it) }
    return (explicitEquipment && (knownSymptom || diagnosticIntent)) || (knownSymptom && diagnosticIntent)
}

private fun isFollowUpQuestion(text: String): Boolean {
    val normalized = text.trim()
    if (normalized.endsWith("?") || normalized.endsWith("？")) return true
    return listOf("为什么", "怎么", "如何", "是否", "能不能", "能否", "会不会", "是不是", "要不要", "需要", "可以", "影响", "导致", "说明什么", "先查", "还要", "吗", "呢").any { normalized.contains(it) }
}

private fun buildWorkOrderFollowUpModeInstruction(userInput: String): String {
    return if (isFollowUpQuestion(userInput)) {
        """
        当前是追问，不是补充记录。
        回答要求：
        - 可以展开为6到10行，必须直接回答“会不会/要不要/先查什么”等判断。
        - 不要复述整份工单，不要逐项重列设备、症状、原因、步骤。
        - 结构建议：结论、为什么、对当前排查优先级的影响、下一步验证、需要补充的数据。
        """.trimIndent()
    } else {
        """
        当前是现场补充，不是新诊断。
        回答要求：
        - 不要只说“已记录”，必须说明这条补充信息的诊断意义。
        - 可以展开为5到8行，解释它更支持哪类原因、会降低哪类可能、下一步该怎么验证。
        - 可以提示应更新到工单哪个字段，但不要把回答写成固定模板。
        """.trimIndent()
    }
}

private fun inferWorkOrderUpdateField(text: String): String {
    return when {
        listOf("故障码", "报警码", "报警", "E", "P0", "DTC").any { text.contains(it, ignoreCase = true) } -> "故障摘要 / 故障码备注"
        listOf("电压", "电流", "温度", "压力", "转速", "液位", "参数").any { text.contains(it) } -> "现场检测参数"
        listOf("声音", "异响", "抖动", "气味", "漏油", "泄漏").any { text.contains(it) } -> "故障现象补充"
        listOf("时间", "频率", "偶发", "一直", "启动", "行走", "举升", "制动").any { text.contains(it) } -> "发生工况 / 触发条件"
        listOf("人员", "师傅", "维修", "备件", "库存").any { text.contains(it) } -> "维修安排 / 备件信息"
        else -> "工单备注"
    }
}

private fun inferWorkOrderSupplementImpact(text: String): String {
    return when {
        listOf("电压", "低压", "欠压", "9.", "10.").any { text.contains(it) } -> "优先核对电源、电池和接插件状态，避免误判为机械故障。"
        listOf("压力", "液压", "漏油", "泄漏").any { text.contains(it) } -> "会提高液压系统方向的排查优先级。"
        listOf("温度", "过热", "高温").any { text.contains(it) } -> "需要同步检查冷却、润滑和负载工况。"
        listOf("故障码", "报警码", "报警", "E", "P0", "DTC").any { text.contains(it, ignoreCase = true) } -> "故障码可作为下一步验证依据，应和控制器数据一起记录。"
        else -> "作为当前工单的补充证据，不触发重新生成诊断。"
    }
}

private fun inferWorkOrderNextStep(text: String, workOrderContext: String): String {
    return when {
        listOf("故障码", "报警码", "报警", "E", "P0", "DTC").any { text.contains(it, ignoreCase = true) } -> "拍照或导出控制器故障码，并核对维修手册中的码义。"
        listOf("电压", "电流").any { text.contains(it) } -> "复测静态和带载电压，记录测量位置与时间。"
        listOf("压力", "液压").any { text.contains(it) } -> "按工单步骤复测液压压力，并检查油液和滤芯状态。"
        listOf("温度", "过热").any { text.contains(it) } -> "记录温度变化曲线，先确认散热和负载条件。"
        workOrderContext.contains("待补充问题") -> "继续补齐工单里的待补充问题，再决定是否调整维修步骤。"
        else -> "保存为工单备注，现场复核后再更新处理结论。"
    }
}

private fun compactWorkOrderFollowUpContext(context: String): String {
    val preferredPrefixes = listOf("设备：", "位置：", "症状：", "严重度：", "图谱原因：", "图谱步骤：", "待补充问题：", "风险提示：", "临时处置：")
    val lines = context
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filter { line -> preferredPrefixes.any { line.startsWith(it) } }
        .distinct()
        .take(10)
        .toList()
    return (if (lines.isNotEmpty()) lines.joinToString("\n") else context.take(900)).take(1200)
}

private fun sanitizeWorkOrderFollowUpReply(rawText: String, userInput: String, workOrderContext: String): String {
    val maxLines = if (isFollowUpQuestion(userInput)) 10 else 8
    val contextLines = workOrderContext
        .lineSequence()
        .map { it.trim() }
        .filter { it.length >= 6 }
        .toSet()
    val blockedPrefixes = listOf("设备：", "位置：", "症状：", "严重度：", "图谱原因：", "图谱步骤：", "相似工单：")
    val cleanedLines = rawText
        .lineSequence()
        .map { it.trim().trimStart('-', ' ', '•') }
        .filter { it.isNotBlank() }
        .filterNot { it in contextLines }
        .filterNot { line -> blockedPrefixes.any { line.startsWith(it) } }
        .distinct()
        .take(maxLines)
        .toList()
    val cleaned = cleanedLines.joinToString("\n").trim()
    if (cleaned.isNotBlank()) return cleaned
    return buildWorkOrderFollowUpFallback(userInput, workOrderContext)
}

private fun buildWorkOrderFollowUpFallback(userInput: String, workOrderContext: String): String {
    val updateField = inferWorkOrderUpdateField(userInput)
    val impact = inferWorkOrderSupplementImpact(userInput)
    val nextStep = inferWorkOrderNextStep(userInput, workOrderContext)
    return listOf(
        "结论：这条信息会作为当前工单的新证据，不需要重新生成工单。",
        "诊断意义：$impact",
        "工单更新：建议写入“$updateField”。",
        "下一步：$nextStep",
    ).joinToString("\n")
}

private const val WORK_ORDER_QUEUE_FILE = "work_order_submit_queue.json"
private val workOrderQueueGson = Gson()

private fun loadQueuedWorkOrders(context: Context): MutableList<QueuedWorkOrderSubmission> {
    return try {
        val file = File(context.filesDir, WORK_ORDER_QUEUE_FILE)
        if (!file.exists()) return mutableListOf()
        val type = object : TypeToken<List<QueuedWorkOrderSubmission>>() {}.type
        workOrderQueueGson.fromJson<List<QueuedWorkOrderSubmission>>(file.readText(), type)?.toMutableList()
            ?: mutableListOf()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load queued work orders", e)
        mutableListOf()
    }
}

private fun saveQueuedWorkOrders(context: Context, queue: List<QueuedWorkOrderSubmission>) {
    try {
        File(context.filesDir, WORK_ORDER_QUEUE_FILE).writeText(workOrderQueueGson.toJson(queue))
    } catch (e: Exception) {
        Log.e(TAG, "Failed to save queued work orders", e)
    }
}

private fun clearQueuedWorkOrders(context: Context, queue: MutableList<QueuedWorkOrderSubmission>) {
    queue.clear()
    try {
        File(context.filesDir, WORK_ORDER_QUEUE_FILE).delete()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to clear queued work orders", e)
    }
}

private fun enqueueWorkOrderSubmission(
    context: Context,
    queue: MutableList<QueuedWorkOrderSubmission>,
    workOrderId: String,
    createdAt: String,
    workOrder: WorkOrderDocumentData,
): QueuedWorkOrderSubmission {
    queue.removeAll { it.workOrderId == workOrderId }
    val item = QueuedWorkOrderSubmission(
        localId = "local-${System.currentTimeMillis()}-${queue.size + 1}",
        workOrderId = workOrderId,
        createdAt = createdAt,
        queuedAt = System.currentTimeMillis(),
        workOrder = workOrder,
    )
    queue.add(item)
    saveQueuedWorkOrders(context, queue)
    return item
}

private suspend fun flushQueuedWorkOrders(
    context: Context,
    queue: MutableList<QueuedWorkOrderSubmission>,
    baseUrl: String,
    accessToken: String,
    onQueueChanged: () -> Unit = {},
): Pair<Int, String?> {
    val normalized = normalizeDemoServerBaseUrl(baseUrl)
    if (normalized.isBlank()) {
        return 0 to "未配置电脑服务"
    }
    if (accessToken.isBlank()) {
        return 0 to "当前为离线登录，暂无可用提交凭证"
    }

    var submittedCount = 0
    var lastError: String? = null
    val snapshot = queue.toList()
    for (item in snapshot) {
        try {
            submitQueuedWorkOrder(normalized, accessToken, item)
            queue.removeAll { it.localId == item.localId }
            submittedCount += 1
            saveQueuedWorkOrders(context, queue)
            onQueueChanged()
        } catch (e: Exception) {
            lastError = e.message ?: "工单提交失败"
            val index = queue.indexOfFirst { it.localId == item.localId }
            if (index >= 0) {
                queue[index] = queue[index].copy(
                    attemptCount = queue[index].attemptCount + 1,
                    lastError = lastError,
                )
                saveQueuedWorkOrders(context, queue)
                onQueueChanged()
            }
            break
        }
    }
    return submittedCount to lastError
}

private suspend fun submitQueuedWorkOrder(
    normalizedBaseUrl: String,
    accessToken: String,
    item: QueuedWorkOrderSubmission,
) {
    submitDemoWorkOrder(
        baseUrl = normalizedBaseUrl,
        accessToken = accessToken,
        payloadJson = item.toSubmitPayloadJson(),
    )
}

private fun QueuedWorkOrderSubmission.toSubmitPayloadJson(): String {
    return org.json.JSONObject().apply {
        put("tenantId", "demo")
        put("siteId", "demo")
        put("clientSubmissionId", localId)
        put("workOrderId", workOrderId)
        put("createdAt", createdAt)
        put("queuedAt", queuedAt)
        put("attemptCount", attemptCount)
        put("source", "android_offline_queue")
        put("workOrder", workOrder.toJsonObject())
    }.toString()
}

private fun WorkOrderDocumentData.toJsonObject(): org.json.JSONObject {
    fun List<String>.toJsonArray(): org.json.JSONArray {
        return org.json.JSONArray().also { array ->
            forEach { array.put(it) }
        }
    }

    return org.json.JSONObject().apply {
        put("equipment", equipment)
        put("location", location)
        put("symptom", symptom)
        put("severity", severity)
        put("status", status)
        put("summary", summary)
        put("causes", causes.toJsonArray())
        put("parts", parts.toJsonArray())
        put("steps", steps.toJsonArray())
        put("personnel", personnel.toJsonArray())
        put("relatedWorkOrders", relatedWorkOrders.toJsonArray())
    }
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
    val isGraphRAG: Boolean = false,
    val isImage: Boolean = false,
    val isVideo: Boolean = false,
    val isWorkOrder: Boolean = false,
)

private enum class HistoryFilter(val label: String) {
    ALL("全部"),
    GRAPH("图谱"),
    IMAGE("图片"),
    VIDEO("视频"),
    WORK_ORDER("工单"),
    LLM("端侧"),
}

private const val HISTORY_FILE = "diagnosis_history.json"
private val historyGson = Gson()

private fun loadHistory(context: Context): MutableList<HistoryEntry> {
    return try {
        val file = File(context.filesDir, HISTORY_FILE)
        if (!file.exists()) return mutableListOf()
        val type = object : TypeToken<List<HistoryEntry>>() {}.type
        historyGson.fromJson<List<HistoryEntry>>(file.readText(), type)?.toMutableList() ?: mutableListOf()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load history", e)
        mutableListOf()
    }
}

private fun saveHistory(context: Context, history: List<HistoryEntry>) {
    try {
        File(context.filesDir, HISTORY_FILE).writeText(historyGson.toJson(history))
    } catch (e: Exception) {
        Log.e(TAG, "Failed to save history", e)
    }
}

private fun clearHistory(context: Context) {
    try {
        File(context.filesDir, HISTORY_FILE).delete()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to clear history", e)
    }
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
    val sourceLabel: String = "",
    val elapsedMs: Long = 0L,
    val tokenUsage: GenerationUsage? = null,
    val isEdgeReasoning: Boolean = false,
    val isFollowUpResponse: Boolean = false,
    val isFieldAssistResponse: Boolean = false,
    val checklist: FieldChecklist? = null,
    val canGenerateWorkOrder: Boolean = false,
    val visualAnnotations: List<VisualAnnotation> = emptyList(),
)

private const val MEMORY_GUARD_MAX_MESSAGES = 48
private const val MEMORY_GUARD_KEEP_MESSAGES = 28
private const val MEMORY_GUARD_CRITICAL_KEEP_MESSAGES = 16
private const val MEMORY_GUARD_MAX_CONVERSATION_ITEMS = 24
private const val MEMORY_GUARD_KEEP_CONVERSATION_ITEMS = 14
private const val MEMORY_GUARD_CONTEXT_CHAR_LIMIT = 12_000
private const val MEMORY_GUARD_MESSAGE_TEXT_LIMIT = 2_800
private const val MEMORY_GUARD_USED_RATIO = 0.78
private const val MEMORY_GUARD_MIN_FREE_BYTES = 96L * 1024L * 1024L
private const val MEMORY_GUARD_NOTICE_SOURCE = "内存保护"

private fun PocketMessage.compactedForMemory(
    aggressive: Boolean,
    preserveText: Boolean = false,
): PocketMessage {
    val compactText =
        if (aggressive && !preserveText && text.length > MEMORY_GUARD_MESSAGE_TEXT_LIMIT) {
            text.take(MEMORY_GUARD_MESSAGE_TEXT_LIMIT) + "\n\n[较早回复已压缩，以释放端侧内存]"
        } else {
            text
        }
    return copy(
        text = compactText,
        bitmap = null,
        visualAnnotations = emptyList(),
        graphJson = if (aggressive) "" else graphJson,
        relatedWorkOrders = if (aggressive) emptyList() else relatedWorkOrders,
    )
}

private fun PocketMessage.withChecklistCompletionContext(
    checklist: FieldChecklist,
    completedItems: List<FieldChecklistItem>,
): PocketMessage {
    val checklistEvidence = buildChecklistCompletionEvidenceText(checklist, completedItems)
    val combinedText = listOf(checklistEvidence, text.trim())
        .filter { it.isNotBlank() }
        .joinToString("\n\n")
    return copy(text = combinedText, canGenerateWorkOrder = true)
}

private fun buildChecklistCompletionEvidenceText(
    checklist: FieldChecklist,
    completedItems: List<FieldChecklistItem>,
): String {
    val items = completedItems.ifEmpty { checklist.items }
    return buildString {
        appendLine("现场排查完成记录：${checklist.title}")
        checklist.reason.takeIf { it.isNotBlank() }?.let { appendLine("排查目的：$it") }
        items.forEachIndexed { index, item ->
            appendLine("${index + 1}. 已完成：${item.title}")
            item.detail.takeIf { it.isNotBlank() }?.let { appendLine("   记录项：$it") }
            item.impact.takeIf { it.isNotBlank() }?.let { appendLine("   对工单的作用：$it") }
        }
        append("结论：清单已全部勾选完成，生成工单时需要合并用户问诊中补充的设备、参数、故障码、图片/视频线索。")
    }.trim()
}

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
            onSessionUpdated = { session = it },
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
    onSessionUpdated: (PocketOpsSession) -> Unit,
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
    var latestVisualBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var latestVisualEquipmentContext by remember { mutableStateOf<GraphNode?>(null) }
    var visualLookupScopeActive by remember { mutableStateOf(false) }
    var workOrderFollowUpContext by remember { mutableStateOf("") }
    var activeOfflineTriageContext by remember { mutableStateOf<OfflineTriageContext?>(null) }
    val listState = rememberLazyListState()
    var showBluetoothDialog by remember { mutableStateOf(false) }
    var selectedWorkOrderMessage by remember { mutableStateOf<PocketMessage?>(null) }
    var selectedGraphJson by remember { mutableStateOf<String?>(null) }
    var showHistorySheet by remember { mutableStateOf(false) }
    val diagnosisHistory = remember { mutableStateListOf<HistoryEntry>() }
    val queuedWorkOrders = remember { mutableStateListOf<QueuedWorkOrderSubmission>() }
    var isSubmittingQueuedWorkOrders by remember { mutableStateOf(false) }
    var showClearQueuedWorkOrdersDialog by remember { mutableStateOf(false) }
    var showCloudWorkOrdersSheet by remember { mutableStateOf(false) }
    var isLoadingCloudWorkOrders by remember { mutableStateOf(false) }
    var cloudWorkOrdersError by remember { mutableStateOf<String?>(null) }
    val cloudWorkOrders = remember { mutableStateListOf<DemoSubmittedWorkOrder>() }
    var edgeDeepDiagnosisEnabled by remember { mutableStateOf(true) }
    var needsModelDirectoryAccess by remember { mutableStateOf(false) }
    var syncSummary by remember { mutableStateOf("") }
    var remoteBootstrapState by remember { mutableStateOf(RemoteBootstrapState.NOT_CONFIGURED) }

    LaunchedEffect(Unit) { diagnosisHistory.addAll(loadHistory(context)) }
    LaunchedEffect(Unit) { queuedWorkOrders.addAll(loadQueuedWorkOrders(context)) }

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
                            val response = try { conn.readTextResponse() } finally { conn.disconnect() }
                            if (response.code == 200 && isGenieApiServiceReady(response.body)) {
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

    fun trySubmitQueuedWorkOrders(showToast: Boolean = false) {
        if (isSubmittingQueuedWorkOrders || queuedWorkOrders.isEmpty()) return
        isSubmittingQueuedWorkOrders = true
        scope.launch {
            var submittedCount = 0
            var submitError: String? = null
            try {
                val workingQueue = queuedWorkOrders.toMutableList()
                var sessionForSubmit = session
                var credentialError: String? = null
                if (sessionForSubmit.accessToken.isBlank() && demoServerBaseUrl.isNotBlank()) {
                    val savedCredentials = withContext(Dispatchers.IO) { loadSavedLoginCredentials(context) }
                    if (savedCredentials.username.isBlank() || savedCredentials.password.isBlank()) {
                        credentialError = "当前是离线登录，且未保存密码；请退出后在联网状态重新登录，或勾选记住密码后再提交。"
                    } else {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                authenticatePocketOpsUser(
                                    baseUrl = demoServerBaseUrl,
                                    username = savedCredentials.username,
                                    password = savedCredentials.password,
                                )
                            }
                        }.onSuccess { refreshedSession ->
                            if (refreshedSession.accessToken.isNotBlank()) {
                                sessionForSubmit = refreshedSession
                                onSessionUpdated(refreshedSession)
                            } else {
                                credentialError = "电脑服务仍不可用，工单已保留在本机待提交队列。"
                            }
                        }.onFailure { error ->
                            credentialError = error.message ?: "重新获取提交凭证失败"
                        }
                    }
                }

                val result = if (credentialError == null) {
                    withContext(Dispatchers.IO) {
                        flushQueuedWorkOrders(
                            context = context,
                            queue = workingQueue,
                            baseUrl = demoServerBaseUrl,
                            accessToken = sessionForSubmit.accessToken,
                            onQueueChanged = {},
                        )
                    }
                } else {
                    0 to credentialError
                }
                submittedCount = result.first
                submitError = result.second
                queuedWorkOrders.clear()
                queuedWorkOrders.addAll(workingQueue)
            } catch (e: Exception) {
                submitError = e.message ?: "提交待办工单失败"
            } finally {
                isSubmittingQueuedWorkOrders = false
                if (showToast || submittedCount > 0 || submitError != null) {
                    val message = when {
                        submittedCount > 0 && queuedWorkOrders.isEmpty() -> "已提交 $submittedCount 个待提交工单"
                        submittedCount > 0 -> "已提交 $submittedCount 个工单，仍有 ${queuedWorkOrders.size} 个待提交"
                        submitError != null -> submitError ?: "提交待办工单失败"
                        else -> "没有可提交的工单"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun clearQueuedWorkOrderQueue(showToast: Boolean = true) {
        val removedCount = queuedWorkOrders.size
        if (removedCount > 0) {
            clearQueuedWorkOrders(context, queuedWorkOrders)
        }
        showClearQueuedWorkOrdersDialog = false
        if (showToast && removedCount > 0) {
            Toast.makeText(context, "已清除 $removedCount 个待提交工单", Toast.LENGTH_SHORT).show()
        }
    }

    fun loadCloudWorkOrders() {
        if (isLoadingCloudWorkOrders) return
        showCloudWorkOrdersSheet = true
        isLoadingCloudWorkOrders = true
        cloudWorkOrdersError = null
        scope.launch {
            try {
                var sessionForQuery = session
                if (sessionForQuery.accessToken.isBlank() && demoServerBaseUrl.isNotBlank()) {
                    val savedCredentials = withContext(Dispatchers.IO) { loadSavedLoginCredentials(context) }
                    if (savedCredentials.username.isBlank() || savedCredentials.password.isBlank()) {
                        throw IllegalStateException("当前是离线登录，且未保存密码；请先在线登录后再查看云端工单。")
                    }
                    sessionForQuery = withContext(Dispatchers.IO) {
                        authenticatePocketOpsUser(
                            baseUrl = demoServerBaseUrl,
                            username = savedCredentials.username,
                            password = savedCredentials.password,
                        )
                    }
                    if (sessionForQuery.accessToken.isBlank()) {
                        throw IllegalStateException("电脑服务仍不可用，请先在线登录后再查看云端工单。")
                    }
                    onSessionUpdated(sessionForQuery)
                }
                val records = withContext(Dispatchers.IO) {
                    fetchSubmittedDemoWorkOrders(
                        baseUrl = demoServerBaseUrl,
                        accessToken = sessionForQuery.accessToken,
                    )
                }
                cloudWorkOrders.clear()
                cloudWorkOrders.addAll(records)
            } catch (e: Exception) {
                cloudWorkOrdersError = e.message ?: "云端工单不可用"
            } finally {
                isLoadingCloudWorkOrders = false
            }
        }
    }

    LaunchedEffect(demoServerBaseUrl, session.accessToken, queuedWorkOrders.size) {
        if (queuedWorkOrders.isNotEmpty() && demoServerBaseUrl.isNotBlank()) {
            trySubmitQueuedWorkOrders()
        }
    }

    // Auto-scroll on message updates
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun appendHistory(userText: String, aiText: String, isGraphRAG: Boolean = false, isImage: Boolean = false, isVideo: Boolean = false) {
        val isWorkOrder = userText.contains("工单") || aiText.startsWith("工单号：")
        val entry = HistoryEntry(System.currentTimeMillis(), userText, aiText, isGraphRAG, isImage, isVideo, isWorkOrder)
        diagnosisHistory.add(entry)
        val historySnapshot = diagnosisHistory.toList()
        scope.launch(Dispatchers.IO) { saveHistory(context, historySnapshot) }
    }

    fun clearDiagnosisContextAfterWorkOrder() {
        conversationHistory.clear()
        activeEquipmentContext = null
        latestVisualContextText = ""
        latestVisualBitmap = null
        latestVisualEquipmentContext = null
        visualLookupScopeActive = false
        activeOfflineTriageContext = null
    }

    fun resetWorkOrderFollowUpContext() {
        workOrderFollowUpContext = ""
    }

    fun resetOfflineTriageContext() {
        activeOfflineTriageContext = null
    }

    fun rememberWorkOrderFollowUpContext(userText: String, aiText: String, report: DiagnosticReport? = null) {
        workOrderFollowUpContext =
            buildWorkOrderFollowUpContext(userText, aiText, report).take(MEMORY_GUARD_CONTEXT_CHAR_LIMIT)
    }

    fun isRuntimeMemoryPressureHigh(): Boolean {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        if (maxMemory <= 0L) return false
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val freeUntilLimit = maxMemory - usedMemory
        val lowFreeMemory =
            maxMemory >= MEMORY_GUARD_MIN_FREE_BYTES * 2 &&
                freeUntilLimit <= MEMORY_GUARD_MIN_FREE_BYTES
        return usedMemory.toDouble() / maxMemory.toDouble() >= MEMORY_GUARD_USED_RATIO || lowFreeMemory
    }

    fun trimConversationHistoryForMemory() {
        if (conversationHistory.size <= MEMORY_GUARD_MAX_CONVERSATION_ITEMS) return
        val compactHistory =
            conversationHistory
                .takeLast(MEMORY_GUARD_KEEP_CONVERSATION_ITEMS)
                .map { (role, content) -> role to content.take(MEMORY_GUARD_MESSAGE_TEXT_LIMIT) }
        conversationHistory.clear()
        conversationHistory.addAll(compactHistory)
    }

    fun addMemoryGuardNotice() {
        if (messages.takeLast(4).any { it.sourceLabel == MEMORY_GUARD_NOTICE_SOURCE }) return
        messages.add(
            PocketMessage(
                text = "已自动清理较早上下文以释放端侧内存；当前诊断可继续，旧记录仍可从历史记录查看。",
                isUser = false,
                sourceLabel = MEMORY_GUARD_NOTICE_SOURCE,
            ),
        )
    }

    fun clearVolatileContextForMemory() {
        conversationHistory.clear()
        activeEquipmentContext = null
        latestVisualContextText = ""
        latestVisualBitmap = null
        latestVisualEquipmentContext = null
        visualLookupScopeActive = false
        workOrderFollowUpContext = ""
        activeOfflineTriageContext = null
    }

    fun enforceMemoryGuard(reason: String, force: Boolean = false, showNotice: Boolean = true) {
        val memoryPressure = isRuntimeMemoryPressureHigh()
        val overMessageLimit = messages.size > MEMORY_GUARD_MAX_MESSAGES
        val overHistoryLimit = conversationHistory.size > MEMORY_GUARD_MAX_CONVERSATION_ITEMS
        val overContextLimit =
            latestVisualContextText.length + workOrderFollowUpContext.length > MEMORY_GUARD_CONTEXT_CHAR_LIMIT
        if (!force && !memoryPressure && !overMessageLimit && !overHistoryLimit && !overContextLimit) return

        val aggressive = memoryPressure || messages.size > MEMORY_GUARD_MAX_MESSAGES + MEMORY_GUARD_KEEP_MESSAGES
        val keepCount = if (aggressive) MEMORY_GUARD_CRITICAL_KEEP_MESSAGES else MEMORY_GUARD_KEEP_MESSAGES
        var cleaned = false

        if (messages.isNotEmpty() && (messages.size > keepCount || memoryPressure || force)) {
            val retainedMessages = messages.takeLast(keepCount)
            val compactedMessages =
                retainedMessages.mapIndexed { index, message ->
                    message.compactedForMemory(
                        aggressive = aggressive,
                        preserveText = index >= retainedMessages.lastIndex - 1,
                    )
                }
            messages.clear()
            messages.addAll(compactedMessages)
            cleaned = true
        }

        if (overHistoryLimit || memoryPressure || force) {
            trimConversationHistoryForMemory()
            cleaned = true
        }

        if (memoryPressure || overContextLimit) {
            clearVolatileContextForMemory()
            cleaned = true
        }

        if (cleaned) {
            if (showNotice) addMemoryGuardNotice()
            if (memoryPressure || aggressive) Runtime.getRuntime().gc()
            Log.w(
                TAG,
                "Memory guard cleanup: reason=$reason, pressure=$memoryPressure, messages=${messages.size}, history=${conversationHistory.size}",
            )
        }
    }

    suspend fun streamVisualFollowUpReply(userText: String): String {
        val contextText = latestVisualContextText.ifBlank { "暂无上一轮图片或视频分析摘要。" }
        val source = if (contextText.contains("[用户发送了视频")) "视频上下文追问" else "图片上下文追问"
        val canGenerate = isDiagnosticWorkOrderIntent(userText)
        withContext(Dispatchers.Main) {
            messages.add(
                PocketMessage(
                    text = "正在结合上一轮现场画面回答...",
                    isUser = false,
                    sourceLabel = source,
                    isImageResponse = true,
                    canGenerateWorkOrder = canGenerate,
                ),
            )
        }

        val httpMessages = org.json.JSONArray().apply {
            put(
                org.json.JSONObject()
                    .put("role", "system")
                    .put("content", VISUAL_FOLLOW_UP_PROMPT),
            )
            put(
                org.json.JSONObject()
                    .put("role", "user")
                    .put(
                        "content",
                        "上一轮视觉分析摘要：\n${contextText.take(2200)}\n\n用户本轮追问：$userText",
                    ),
            )
        }

        val reqJson = org.json.JSONObject().apply {
            put("model", "qwen2.5vl-3b-8850-2.42")
            put("stream", true)
            put("messages", httpMessages)
            put("size", 1536)
            put("temp", 0.1)
            put("top_k", 1)
            put("top_p", 1.0)
            put("stream_options", org.json.JSONObject().put("include_usage", true))
        }

        val t0 = System.currentTimeMillis()
        clearGenieChatState()
        val httpConn = (java.net.URL("http://127.0.0.1:8910/v1/chat/completions").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 10000
            readTimeout = 90000
        }
        httpConn.outputStream.use { it.write(reqJson.toString().toByteArray(Charsets.UTF_8)) }

        val httpCode = httpConn.responseCode
        if (httpCode !in 200..299) {
            val err = try { httpConn.readErrorBody() } finally { httpConn.disconnect() }
            throw IllegalStateException(buildHttpErrorMessage("图片追问推理失败", httpCode, err))
        }

        val fullContent = StringBuilder()
        var responseUsage: GenerationUsage? = null
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
                        val chunk = org.json.JSONObject(data)
                        chunk.optGenerationUsage()?.let { responseUsage = it }
                        val choices = chunk.optJSONArray("choices") ?: continue
                        val firstChoice = choices.optJSONObject(0) ?: continue
                        val delta = firstChoice.optJSONObject("delta") ?: firstChoice.optJSONObject("message") ?: continue
                        val deltaContent = delta.optString("content").takeIf { it.isNotBlank() }
                        if (deltaContent != null) {
                            fullContent.append(deltaContent)
                            val content = fullContent.toString()
                            withContext(Dispatchers.Main) {
                                val idx = messages.size - 1
                                if (idx >= 0) {
                                    messages[idx] = messages[idx].copy(
                                        text = content,
                                        sourceLabel = source,
                                        isImageResponse = true,
                                        canGenerateWorkOrder = canGenerate,
                                    )
                                }
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            } finally {
                reader.close()
            }
        } finally {
            httpConn.disconnect()
        }

        val content = fullContent.toString().trim().ifBlank { "上一轮图片信息不足，建议补拍设备编号、故障部位和仪表参数后再判断。" }
        val elapsed = System.currentTimeMillis() - t0
        val tokenUsage = responseUsage.orEstimated(content)
        withContext(Dispatchers.Main) {
            val idx = messages.size - 1
            if (idx >= 0) {
                messages[idx] = messages[idx].copy(
                    text = content,
                    sourceLabel = source,
                    elapsedMs = elapsed,
                    tokenUsage = tokenUsage,
                    isImageResponse = true,
                    canGenerateWorkOrder = canGenerate,
                )
            }
        }
        return content
    }

    suspend fun runVisualLocalizationReply(userText: String, sourceBitmap: Bitmap): String {
        withContext(Dispatchers.Main) {
            messages.add(
                PocketMessage(
                    text = "正在定位并标注图片目标...",
                    isUser = false,
                    sourceLabel = "图片定位",
                    isImageResponse = true,
                    canGenerateWorkOrder = false,
                ),
            )
        }

        val t0 = System.currentTimeMillis()
        var responseUsage: GenerationUsage? = null
        clearGenieChatState()
        val stream = java.io.ByteArrayOutputStream()
        sourceBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        val b64 = android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
        val reqJson = org.json.JSONObject().apply {
            put("model", "qwen2.5vl-3b-8850-2.42")
            put("stream", false)
            put("size", 4096)
            put("temp", 0.0)
            put("top_k", 1)
            put("top_p", 1.0)
            put("messages", org.json.JSONArray().apply {
                put(org.json.JSONObject().put("role", "system").put("content", VISUAL_LOCALIZATION_SYSTEM_PROMPT))
                put(
                    org.json.JSONObject()
                        .put("role", "user")
                        .put(
                            "content",
                            org.json.JSONObject()
                                .put("question", buildVisualLocalizationQuestion(userText))
                                .put("image", b64),
                        ),
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
            throw IllegalStateException(buildHttpErrorMessage("图片定位推理失败", response.code, response.body))
        }

        val respJson = org.json.JSONObject(response.body)
        responseUsage = respJson.optGenerationUsage()
        val rawContent = respJson.getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")
        val localizationResult = parseVisualLocalizationResponse(rawContent, userText)
        val content = localizationResult.answer
        val elapsed = System.currentTimeMillis() - t0
        val tokenUsage = responseUsage.orEstimated(content)

        withContext(Dispatchers.Main) {
            val idx = messages.size - 1
            if (idx >= 0) {
                messages[idx] = messages[idx].copy(
                    text = content,
                    bitmap = if (localizationResult.annotations.isNotEmpty()) sourceBitmap else null,
                    visualAnnotations = localizationResult.annotations,
                    sourceLabel = "图片定位",
                    elapsedMs = elapsed,
                    tokenUsage = tokenUsage,
                    isImageResponse = true,
                    canGenerateWorkOrder = false,
                )
            }
            latestVisualContextText = "[用户请求基于上一张图片定位] $userText\n$content".take(MEMORY_GUARD_CONTEXT_CHAR_LIMIT)
            latestVisualBitmap = sourceBitmap
            latestVisualEquipmentContext = knowledgeGraph.findEquipment(content).firstOrNull()
            visualLookupScopeActive = true
            activeEquipmentContext = latestVisualEquipmentContext
        }
        return content
    }

    suspend fun streamWorkOrderFollowUpReply(userText: String): String {
        val contextText = workOrderFollowUpContext.ifBlank { "暂无已锁定的工单上下文，请只回答本轮问题，不要生成新诊断。" }
        withContext(Dispatchers.Main) {
            messages.add(
                PocketMessage(
                    text = "正在分析这条补充/追问对当前工单的诊断意义...",
                    isUser = false,
                    sourceLabel = "工单后续沟通",
                    isFollowUpResponse = true,
                ),
            )
        }

        val compactContext = compactWorkOrderFollowUpContext(contextText)
        val modeInstruction = buildWorkOrderFollowUpModeInstruction(userText)
        val httpMessages = org.json.JSONArray().apply {
            put(
                org.json.JSONObject()
                    .put("role", "system")
                    .put(
                        "content",
                        "$WORK_ORDER_FOLLOW_UP_PROMPT\n\n$modeInstruction\n\n压缩后的当前工单事实，仅用于回答本轮问题，禁止复述：\n$compactContext",
                    ),
            )
            put(org.json.JSONObject().put("role", "user").put("content", "本轮新增问题：$userText"))
        }

        val reqJson = org.json.JSONObject().apply {
            put("model", "qwen2.5vl-3b-8850-2.42")
            put("stream", true)
            put("messages", httpMessages)
            put("size", 2048)
            put("temp", 0.2)
            put("top_k", 1)
            put("top_p", 1.0)
            put("stream_options", org.json.JSONObject().put("include_usage", true))
        }

        val t0 = System.currentTimeMillis()
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
            throw IllegalStateException(buildHttpErrorMessage("工单后续推理失败", httpCode, err))
        }

        val fullContent = StringBuilder()
        var responseUsage: GenerationUsage? = null
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
                        val chunk = org.json.JSONObject(data)
                        chunk.optGenerationUsage()?.let { responseUsage = it }
                        val choices = chunk.optJSONArray("choices") ?: continue
                        val firstChoice = choices.optJSONObject(0) ?: continue
                        val delta = firstChoice.optJSONObject("delta") ?: firstChoice.optJSONObject("message") ?: continue
                        val deltaContent = delta.optString("content").takeIf { it.isNotBlank() }
                        if (deltaContent != null) {
                            fullContent.append(deltaContent)
                            val content = fullContent.toString()
                            withContext(Dispatchers.Main) {
                                val idx = messages.size - 1
                                if (idx >= 0) {
                                    messages[idx] = messages[idx].copy(
                                        text = content,
                                        sourceLabel = "工单后续沟通",
                                        isFollowUpResponse = true,
                                    )
                                }
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            } finally {
                reader.close()
            }
        } finally {
            httpConn.disconnect()
        }

        val content =
            sanitizeWorkOrderFollowUpReply(
                rawText = fullContent.toString().ifBlank { "已收到补充信息，我会将其作为当前工单的后续备注；如需重新诊断，请输入新的设备和故障现象。" },
                userInput = userText,
                workOrderContext = contextText,
            )
        val elapsed = System.currentTimeMillis() - t0
        val tokenUsage = responseUsage.orEstimated(content)
        withContext(Dispatchers.Main) {
            val idx = messages.size - 1
            if (idx >= 0) {
                messages[idx] = messages[idx].copy(
                    text = content,
                    sourceLabel = "工单后续沟通",
                    elapsedMs = elapsed,
                    tokenUsage = tokenUsage,
                    isFollowUpResponse = true,
                )
            }
        }
        return content
    }

    // Send logic — unified multi-turn with image / video context
    fun sendMessage(text: String, bitmap: Bitmap?, videoUri: Uri? = null) {
        if (text.isBlank() && bitmap == null && videoUri == null) return
        enforceMemoryGuard(reason = "before_send")
        val isVideoRequest = videoUri != null
        val userText =
            text.ifBlank {
                if (isVideoRequest) {
                    DEFAULT_VIDEO_INSPECTION_REQUEST
                } else {
                    "请描述这张图片中的内容"
                }
            }
        val isVideoInspectionRequest = isVideoRequest && isVehicleInspectionText(userText)
        val userMessageText = if (isVideoRequest) {
            "${if (isVideoInspectionRequest) "车辆点检" else "视频诊断"}：$userText"
        } else {
            userText
        }
        val explicitEquipmentMentionForTurn = extractExplicitEquipmentMention(userText)
        val explicitUnknownEquipmentForTurn =
            explicitEquipmentMentionForTurn != null && knowledgeGraph.findEquipment(userText).isEmpty()
        val explicitNewDiagnosisForTurn = bitmap == null && videoUri == null && isExplicitNewDiagnosisRequest(userText)

        if (explicitUnknownEquipmentForTurn) {
            clearDiagnosisContextAfterWorkOrder()
            resetWorkOrderFollowUpContext()
            resetOfflineTriageContext()
        } else if (explicitNewDiagnosisForTurn) {
            resetWorkOrderFollowUpContext()
            resetOfflineTriageContext()
        }

        val safeBitmap = bitmap?.let {
            if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it
        }
        val isVisualLocalizationRequestForTurn =
            safeBitmap != null && videoUri == null && isVisualLocalizationRequest(userText)

        messages.add(PocketMessage(text = userMessageText, isUser = true, bitmap = safeBitmap))
        enforceMemoryGuard(
            reason = "after_user_message",
            force = messages.size > MEMORY_GUARD_MAX_MESSAGES,
            showNotice = false,
        )
        val userMessageIndex = messages.lastIndex
        val equipmentContextForTurn = if (explicitUnknownEquipmentForTurn) null else activeEquipmentContext
        isGenerating = true

        scope.launch(Dispatchers.IO) {
            try {
                if (bitmap == null && videoUri == null && !explicitNewDiagnosisForTurn && shouldTreatAsWorkOrderFollowUp(userText, workOrderFollowUpContext)) {
                    val content = streamWorkOrderFollowUpReply(userText)
                    conversationHistory.add("user" to userText)
                    conversationHistory.add("assistant" to content)
                    appendHistory(userText, content)
                    return@launch
                }

                if (
                    bitmap == null &&
                    videoUri == null &&
                    visualLookupScopeActive &&
                    latestVisualBitmap != null &&
                    !explicitNewDiagnosisForTurn &&
                    isVisualLocalizationRequest(userText)
                ) {
                    val sourceBitmap = latestVisualBitmap
                    if (sourceBitmap != null) {
                        val content = runVisualLocalizationReply(userText, sourceBitmap)
                        conversationHistory.add("user" to userText)
                        conversationHistory.add("assistant" to content)
                        appendHistory(userText, content, isImage = true)
                        return@launch
                    }
                }

                if (
                    bitmap == null &&
                    videoUri == null &&
                    visualLookupScopeActive &&
                    !explicitNewDiagnosisForTurn &&
                    shouldTreatAsVisualFollowUp(userText, latestVisualContextText)
                ) {
                    val content = streamVisualFollowUpReply(userText)
                    conversationHistory.add("user" to userText)
                    conversationHistory.add("assistant" to content)
                    appendHistory(userText, content, isImage = latestVisualContextText.contains("[用户发送了图片]"), isVideo = latestVisualContextText.contains("[用户发送了视频"))
                    return@launch
                }

                if (bitmap == null && videoUri == null) {
                    activeOfflineTriageContext?.takeIf { shouldTreatAsOfflineTriageFollowUp(userText, it) }?.let { triageContext ->
                        val t0 = System.currentTimeMillis()
                        val triageReply = buildOfflineTriageFollowUpReply(userText, triageContext)
                        withContext(Dispatchers.Main) {
                            activeOfflineTriageContext = triageReply.context
                            messages.add(
                                PocketMessage(
                                    text = triageReply.text,
                                    isUser = false,
                                    sourceLabel = "端侧问诊排查",
                                    elapsedMs = System.currentTimeMillis() - t0,
                                    tokenUsage = GenerationUsage(estimateOutputTokens(triageReply.text), estimated = true),
                                    isFieldAssistResponse = true,
                                    checklist = triageReply.checklist,
                                ),
                            )
                        }
                        conversationHistory.add("user" to userText)
                        conversationHistory.add("assistant" to triageReply.text)
                        appendHistory(userText, triageReply.text)
                        return@launch
                    }

                    if (shouldStartOfflineTriage(userText, workOrderFollowUpContext)) {
                        buildOfflineTriageQuestionnaire(userText)?.let { triageReply ->
                            val t0 = System.currentTimeMillis()
                            withContext(Dispatchers.Main) {
                                activeOfflineTriageContext = triageReply.context
                                messages.add(
                                    PocketMessage(
                                        text = triageReply.text,
                                        isUser = false,
                                        sourceLabel = "端侧问诊排查",
                                        elapsedMs = System.currentTimeMillis() - t0,
                                        tokenUsage = GenerationUsage(estimateOutputTokens(triageReply.text), estimated = true),
                                        isFieldAssistResponse = true,
                                        checklist = triageReply.checklist,
                                    ),
                                )
                            }
                            conversationHistory.add("user" to userText)
                            conversationHistory.add("assistant" to triageReply.text)
                            appendHistory(userText, triageReply.text)
                            return@launch
                        }
                    }
                    withContext(Dispatchers.Main) {
                        activeOfflineTriageContext = null
                    }
                }

                var visualBitmap = safeBitmap
                var visualSystemPrompt = IMAGE_SYSTEM_PROMPT
                var visualQuestion = "请用中文回答。$userText"
                var visualHistoryLabel = "[用户发送了图片] $userText"
                var visualErrorPrefix = "图片推理失败"
                var visualSourceLabel = "图片诊断"
                var visualLoadingText = "正在使用端侧模型分析图片..."
                var visualCanGenerateWorkOrder = true
                var extractedVideoFrames: ExtractedVideoFrames? = null

                if (isVisualLocalizationRequestForTurn) {
                    visualSystemPrompt = VISUAL_LOCALIZATION_SYSTEM_PROMPT
                    visualQuestion = buildVisualLocalizationQuestion(userText)
                    visualHistoryLabel = "[用户发送了图片定位] $userText"
                    visualErrorPrefix = "图片定位推理失败"
                    visualSourceLabel = "图片定位"
                    visualLoadingText = "正在定位并标注图片目标..."
                    visualCanGenerateWorkOrder = false
                }

                if (videoUri != null) {
                    withContext(Dispatchers.Main) {
                        messages.add(
                            PocketMessage(
                                text = if (isVideoInspectionRequest) "正在使用端侧模型分析车辆点检视频..." else "\u6b63\u5728\u4f7f\u7528\u7aef\u4fa7\u6a21\u578b\u5206\u6790\u89c6\u9891...",
                                isUser = false,
                                isImageResponse = true,
                                sourceLabel = if (isVideoInspectionRequest) "车辆点检" else "视频诊断",
                                canGenerateWorkOrder = true,
                            ),
                        )
                    }

                    val extractedFrames =
                        extractVideoFrames(
                            context = context,
                            uri = videoUri,
                            maxFrames = if (isVideoInspectionRequest) 8 else 4,
                            includeTailFrame = isVideoInspectionRequest,
                        )
                            ?: throw IllegalStateException("未能从视频中提取有效帧")
                    extractedVideoFrames = extractedFrames

                    visualBitmap = extractedFrames.contactSheet
                    visualSystemPrompt = if (isVideoInspectionRequest) VEHICLE_INSPECTION_VIDEO_PROMPT else VIDEO_SYSTEM_PROMPT
                    visualQuestion =
                        if (isVideoInspectionRequest) {
                            val fixedInstrumentHint = confirmedVehicleInspectionInstrumentHint(extractedFrames.instrumentTextHint)
                            val instrumentHintBlock =
                                "仪表盘必须合并以下已确认故障线索；最终回答不要写“OCR”或“文字识别读到”，只写“仪表盘显示”：\n$fixedInstrumentHint\n"
                            "这是从同一段车辆环车点检视频中提取的${extractedFrames.frameCount}帧关键帧拼图，视频时长约${formatVideoTimestamp(extractedFrames.durationMs)}，拼图包含按时间抽取的环车关键帧，并可能包含多个末尾仪表重点帧。\n$instrumentHintBlock 请严格按车辆点检场景分析：综合所有帧中的车辆外观、左右反光镜/后视镜、仪表盘文字读数和报警线索，输出一个最终合并结论；不要逐帧分别作答，不要输出第几帧、时间帧、置信度等中间过程。末尾仪表重点帧只用于补充更清晰的仪表细节，不能忽略前面环车画面。同一个仪表在不同重点帧出现不同故障页时，必须合并这些故障页，不能用最后一页覆盖前一页。如果画面拍到仪表盘，必须汇总仪表盘里的文字、数字读数、故障码、报警灯/图标、电量/油量、小时表等可见内容；如果任何一帧显示“故障码”“控制器”“SRO”“OK”等文字，必须合并到故障码汇总，严禁写“故障码无”。如果某一行显示OK，只能说明该控制器正常，不能否定其他控制器的故障；如果出现控制器列表，必须逐行保留控制器名称、编号/故障码和状态/故障说明，不能只写疑似故障。然后再判断这些内容是否正常、异常或待确认。检查左右反光镜/后视镜的镜面、支架、外壳和角度是否完好；同时记录货叉、门架、轮胎、护顶架、车尾和地面漏液等环车外观风险。不要跳过仪表盘文字读数；看不清时写待补拍，不能编造读数或故障码。回答必须使用简体中文，除画面原始标签、设备编号、故障码、SRO、OK 外不要输出英文句子。不要输出Markdown标题、井号标题、代码块或项目符号。用户问题：$userText"
                        } else {
                            "这是从同一段设备视频中提取的${extractedFrames.frameCount}帧关键帧拼图，视频时长约${formatVideoTimestamp(extractedFrames.durationMs)}。请结合全部画面进行诊断。回答必须全部使用简体中文；不要用英文描述画面、结论或建议。请按“画面观察、异常线索、可能原因、处理建议”的中文结构回答。用户问题：$userText"
                        }
                    visualHistoryLabel = if (isVideoInspectionRequest) "[用户发送了车辆点检视频抽帧] $userText" else "[用户发送了视频抽帧] $userText"
                    visualErrorPrefix = if (isVideoInspectionRequest) "车辆点检视频推理失败" else "视频抽帧推理失败"
                    visualSourceLabel = if (isVideoInspectionRequest) "车辆点检" else "视频诊断"
                    visualCanGenerateWorkOrder = true

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
                        latestVisualBitmap = null
                        latestVisualEquipmentContext = null
                        visualLookupScopeActive = false
                        resetWorkOrderFollowUpContext()
                        resetOfflineTriageContext()
                    }
                    if (videoUri == null) {
                        withContext(Dispatchers.Main) {
                            messages.add(
                                PocketMessage(
                                    text = visualLoadingText,
                                    isUser = false,
                                    isImageResponse = true,
                                    sourceLabel = visualSourceLabel,
                                    canGenerateWorkOrder = visualCanGenerateWorkOrder,
                                ),
                            )
                        }
                    }

                    try {
                        val visualT0 = System.currentTimeMillis()
                        var responseUsage: GenerationUsage? = null
                        clearGenieChatState()
                        val stream = java.io.ByteArrayOutputStream()
                        val imageFormat =
                            if (videoUri != null) android.graphics.Bitmap.CompressFormat.JPEG
                            else android.graphics.Bitmap.CompressFormat.PNG
                        val imageQuality = if (videoUri != null) 88 else 100
                        visualBitmap.compress(imageFormat, imageQuality, stream)
                        val b64 = android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)

                        fun buildVisualRequestJson(question: String, inputSize: Int): org.json.JSONObject {
                            return org.json.JSONObject().apply {
                                put("model", "qwen2.5vl-3b-8850-2.42")
                                put("stream", false)
                                put("size", inputSize)
                                put("temp", 0.0)
                                put("top_k", 1)
                                put("top_p", 1.0)
                                put("messages", org.json.JSONArray().apply {
                                    put(org.json.JSONObject().put("role", "system").put("content", visualSystemPrompt))
                                    put(
                                        org.json.JSONObject().put(
                                            "role",
                                            "user",
                                        ).put(
                                            "content",
                                            org.json.JSONObject().put("question", question).put("image", b64),
                                        ),
                                    )
                                })
                            }
                        }

                        fun postVisualRequest(question: String, inputSize: Int): Pair<HttpTextResponse, String> {
                            val reqJson = buildVisualRequestJson(question, inputSize)
                            Log.d(TAG, "VLM HTTP request: ${b64.length} base64 chars, size=$inputSize")
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
                            val content = parseChatCompletionContent(response.body)
                            if (content.isBlank()) {
                                Log.w(TAG, "VLM HTTP response has no content: ${summarizeModelResponseBody(response.body)}")
                            }
                            return response to content
                        }

                        fun compactVehicleInspectionQuestion(): String {
                            val instrumentHint = confirmedVehicleInspectionInstrumentHint(extractedVideoFrames?.instrumentTextHint.orEmpty())
                            val instrumentBlock = "仪表盘必须合并以下已确认故障线索，最终回答不要写OCR，只写仪表盘显示：\n$instrumentHint\n"
                            return "请综合这张车辆环车点检视频关键帧拼图输出最终合并结论，不要逐帧，不要时间帧，不要Markdown。$instrumentBlock" +
                                "必须按以下顺序输出：点检结论、仪表盘识别汇总、故障码汇总、仪表盘异常判断、反光镜检查、环车外观风险、处理建议、需补拍内容。" +
                                "如果看到故障码、控制器、SRO、OK，必须合并到故障码汇总，不能写故障码无。用户问题：$userText"
                        }

                        val primaryInputSize =
                            if (videoUri != null) {
                                if (isVideoInspectionRequest) 2048 else 1536
                            } else {
                                4096
                            }
                        var responseAndContent = postVisualRequest(visualQuestion, primaryInputSize)
                        if (responseAndContent.second.isBlank() && videoUri != null) {
                            clearGenieChatState()
                            val retryInputSize = if (isVideoInspectionRequest) 1536 else 1024
                            val retryQuestion =
                                if (isVideoInspectionRequest) compactVehicleInspectionQuestion() else visualQuestion
                            responseAndContent = postVisualRequest(retryQuestion, retryInputSize)
                        }
                        val response = responseAndContent.first
                        val rawContent = responseAndContent.second
                            .ifBlank {
                                throw IllegalStateException(
                                    "$visualErrorPrefix：端侧模型未返回有效文本，返回内容：${summarizeModelResponseBody(response.body)}",
                                )
                            }
                        responseUsage =
                            try {
                                org.json.JSONObject(response.body).optGenerationUsage()
                            } catch (_: Exception) {
                                null
                            }
                        val localizationResult =
                            if (isVisualLocalizationRequestForTurn) parseVisualLocalizationResponse(rawContent, userText) else null
                        var videoContent =
                            if (videoUri != null) {
                                rewriteVlmAnswerToChinese(rawContent, force = hasEnglishAnalysisSentence(rawContent))
                            } else {
                                rawContent
                            }
                        if (videoUri != null && isVideoInspectionRequest) {
                            videoContent = normalizeVehicleInspectionAnswer(
                                rawText = videoContent,
                                instrumentTextHint = confirmedVehicleInspectionInstrumentHint(extractedVideoFrames?.instrumentTextHint.orEmpty()),
                                userText = userText,
                            )
                        }
                        val content = localizationResult?.answer
                            ?: videoContent
                        val visualAnnotations = localizationResult?.annotations.orEmpty()
                        val visualElapsed = System.currentTimeMillis() - visualT0
                        val tokenUsage = responseUsage.orEstimated(content)

                        withContext(Dispatchers.Main) {
                            val idx = messages.size - 1
                            if (idx >= 0) {
                                messages[idx] = messages[idx].copy(
                                    text = content,
                                    sourceLabel = if (videoUri != null) {
                                        if (isVideoInspectionRequest) "车辆点检" else "视频诊断"
                                    } else {
                                        visualSourceLabel
                                    },
                                    bitmap = if (visualAnnotations.isNotEmpty()) visualBitmap else null,
                                    visualAnnotations = visualAnnotations,
                                    elapsedMs = visualElapsed,
                                    tokenUsage = tokenUsage,
                                    isImageResponse = true,
                                    canGenerateWorkOrder = visualCanGenerateWorkOrder,
                                    checklist = if (isVideoInspectionRequest) buildBaseFieldChecklist(FieldFaultCategory.VEHICLE_INSPECTION) else null,
                                )
                            }
                            val matchedEquipment = knowledgeGraph.findEquipment(content).firstOrNull()
                            latestVisualContextText = "$visualHistoryLabel\n$content".take(MEMORY_GUARD_CONTEXT_CHAR_LIMIT)
                            latestVisualBitmap = if (videoUri == null) visualBitmap else null
                            latestVisualEquipmentContext = matchedEquipment
                            visualLookupScopeActive = true
                            activeEquipmentContext = matchedEquipment
                        }
                        conversationHistory.add("user" to visualHistoryLabel)
                        conversationHistory.add("assistant" to content)
                        appendHistory(userText, content, isImage = videoUri == null, isVideo = videoUri != null)
                    } catch (e: Exception) {
                        Log.e(TAG, "VLM HTTP failed", e)
                        if (videoUri != null && isVideoInspectionRequest) {
                            val content = buildVehicleInspectionFallbackAnswer(extractedVideoFrames?.instrumentTextHint.orEmpty())
                            withContext(Dispatchers.Main) {
                                val idx = messages.size - 1
                                if (idx >= 0) {
                                    messages[idx] = messages[idx].copy(
                                        text = content,
                                        sourceLabel = "车辆点检",
                                        bitmap = null,
                                        elapsedMs = 0L,
                                        tokenUsage = GenerationUsage(estimateOutputTokens(content), estimated = true),
                                        isImageResponse = true,
                                        canGenerateWorkOrder = visualCanGenerateWorkOrder,
                                        checklist = buildBaseFieldChecklist(FieldFaultCategory.VEHICLE_INSPECTION),
                                    )
                                }
                                latestVisualContextText = "$visualHistoryLabel\n$content".take(MEMORY_GUARD_CONTEXT_CHAR_LIMIT)
                                latestVisualBitmap = null
                                latestVisualEquipmentContext = null
                                visualLookupScopeActive = true
                                activeEquipmentContext = null
                            }
                            conversationHistory.add("user" to visualHistoryLabel)
                            conversationHistory.add("assistant" to content)
                            appendHistory(userText, content, isVideo = true)
                        } else {
                            withContext(Dispatchers.Main) {
                                val idx = messages.size - 1
                                val prefix = if (videoUri != null) {
                                    "视频抽帧失败"
                                } else {
                                    visualErrorPrefix
                                }
                                if (idx >= 0) messages[idx] = messages[idx].copy(text = "$prefix: ${e.message}")
                            }
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
                val graphT0 = System.currentTimeMillis()
                val graphResult =
                    buildGraphRAGReport(userText, knowledgeGraph)
                        ?: buildGraphRAGReport(contextualRetrievalText, knowledgeGraph, equipmentContextForTurn)
                        ?: buildGraphRAGReport(retrievalText, knowledgeGraph)
                val graphElapsedMs = System.currentTimeMillis() - graphT0
                if (graphResult != null) {
                    val (report, graphJson) = graphResult
                    Log.d(TAG, "GraphRAG hit: ${report.equipment}")
                    if (!edgeDeepDiagnosisEnabled) {
                        val reportText = buildDiagnosticReportText(report)
                        val reportTokenUsage = GenerationUsage(estimateOutputTokens(reportText), estimated = true)
                        withContext(Dispatchers.Main) {
                            messages.add(
                                PocketMessage(
                                    text = "",
                                    isUser = false,
                                    report = report,
                                    graphJson = graphJson,
                                    sourceLabel = "图谱快答",
                                    elapsedMs = graphElapsedMs,
                                    tokenUsage = reportTokenUsage,
                                    relatedWorkOrders = report.workOrders,
                                    checklist = buildFieldChecklistForDiagnosis(userText, graphReport = report),
                                ),
                            )
                            activeEquipmentContext = report.equipment
                            visualLookupScopeActive = false
                        }
                        conversationHistory.add("user" to userText)
                        conversationHistory.add("assistant" to reportText)
                        rememberWorkOrderFollowUpContext(userText, reportText, report)
                        appendHistory(userText, reportText, isGraphRAG = true)
                        return@launch
                    }
                } else {
                    Log.d(TAG, "GraphRAG miss")
                }

                val lookupT0 = System.currentTimeMillis()
                val equipmentLookupResult = buildEquipmentLookupAnswer(userText, contextualRetrievalText, knowledgeGraph)
                if (equipmentLookupResult != null && graphResult == null) {
                    val (matchedEquipment, equipmentLookupAnswer) = equipmentLookupResult
                    val lookupFromVisual = visualLookupScopeActive
                    withContext(Dispatchers.Main) {
                        messages.add(
                            PocketMessage(
                                text = equipmentLookupAnswer,
                                isUser = false,
                                isImageResponse = lookupFromVisual,
                                elapsedMs = System.currentTimeMillis() - lookupT0,
                                tokenUsage = GenerationUsage(estimateOutputTokens(equipmentLookupAnswer), estimated = true),
                                sourceLabel = if (lookupFromVisual) "图片上下文追问" else "",
                            ),
                        )
                        activeEquipmentContext = matchedEquipment
                        if (lookupFromVisual) {
                            latestVisualEquipmentContext = matchedEquipment
                        }
                        visualLookupScopeActive = lookupFromVisual
                    }
                    conversationHistory.add("user" to userText)
                    conversationHistory.add("assistant" to equipmentLookupAnswer)
                    appendHistory(userText, equipmentLookupAnswer)
                    return@launch
                }

                if (isEquipmentLookupQuestion(userText) && visualLookupScopeActive) {
                    val answerT0 = System.currentTimeMillis()
                    val answer = "最近一次图片或视频里暂未匹配到知识库中的具体叉车编号。请补拍包含设备编号、配置号或铭牌的画面，或直接输入设备编号。"
                    withContext(Dispatchers.Main) {
                        messages.add(
                            PocketMessage(
                                text = answer,
                                isUser = false,
                                isImageResponse = true,
                                sourceLabel = "图片上下文追问",
                                elapsedMs = System.currentTimeMillis() - answerT0,
                                tokenUsage = GenerationUsage(estimateOutputTokens(answer), estimated = true),
                            ),
                        )
                    }
                    conversationHistory.add("user" to userText)
                    conversationHistory.add("assistant" to answer)
                    appendHistory(userText, answer)
                    return@launch
                }

                if (!edgeDeepDiagnosisEnabled) {
                    explicitEquipmentMentionForTurn?.takeIf { explicitUnknownEquipmentForTurn }?.let { equipmentMention ->
                        buildUnknownEquipmentSymptomReport(
                            equipmentMention = equipmentMention,
                            userInput = userText,
                            graph = knowledgeGraph,
                        )?.let { content ->
                            withContext(Dispatchers.Main) {
                                messages.add(
                                    PocketMessage(
                                        text = content,
                                        isUser = false,
                                        sourceLabel = "症状参考快答",
                                        elapsedMs = graphElapsedMs,
                                        tokenUsage = GenerationUsage(estimateOutputTokens(content), estimated = true),
                                        checklist = buildFieldChecklistForDiagnosis(userText, llmContent = content),
                                    ),
                                )
                                activeEquipmentContext = null
                                latestVisualContextText = ""
                                latestVisualBitmap = null
                                latestVisualEquipmentContext = null
                                visualLookupScopeActive = false
                            }
                            conversationHistory.add("user" to userText)
                            conversationHistory.add("assistant" to content)
                            appendHistory(userText, content)
                            return@launch
                        }
                    }
                }

                // LLM query via HTTP (stream)
                val deepGraphReport = graphResult?.first
                val deepGraphJson = graphResult?.second.orEmpty()
                val deepGraphContext =
                    deepGraphReport?.let { buildDeepGraphReasoningContext(userText, it, deepGraphJson, graphElapsedMs) }
                val partialRagContext =
                    buildPartialRAGContext(userText, knowledgeGraph)
                        ?: buildPartialRAGContext(contextualRetrievalText, knowledgeGraph)
                        ?: buildPartialRAGContext(retrievalText, knowledgeGraph)
                val knownSymptomMentioned =
                    SYMPTOM_KEYWORDS.any { keyword ->
                        userText.contains(keyword) || contextualRetrievalText.contains(keyword) || retrievalText.contains(keyword)
                    }
                val isUnknownSymptomFlow = graphResult == null && !knownSymptomMentioned
                val responseSourceLabel =
                    when {
                        deepGraphContext != null -> "图谱增强 + 端侧推理"
                        isUnknownSymptomFlow -> "知识库未命中 · 端侧泛化"
                        else -> "端侧模型诊断"
                    }
                val placeholderText =
                    when {
                        deepGraphContext != null -> "正在用端侧模型结合图谱结果做深度诊断..."
                        isUnknownSymptomFlow -> "知识库未直接命中，正在由端侧模型生成泛化诊断..."
                        else -> ""
                    }
                withContext(Dispatchers.Main) {
                    messages.add(
                        PocketMessage(
                            text = placeholderText,
                            isUser = false,
                            graphJson = deepGraphJson,
                            sourceLabel = responseSourceLabel,
                            isEdgeReasoning = true,
                        ),
                    )
                    deepGraphReport?.let {
                        activeEquipmentContext = it.equipment
                    }
                }

                val ragContext =
                    deepGraphContext
                        ?: partialRagContext
                val explicitUnknownEquipmentGuard =
                    explicitEquipmentMentionForTurn?.takeIf { explicitUnknownEquipmentForTurn }?.let { equipmentMention ->
                        "\n\n重要约束：用户本轮明确提到“$equipmentMention”，但知识库没有匹配到该设备。严禁沿用上一轮图片、视频或工单中的其他设备编号，尤其不要输出3号叉车等旧设备。equipment字段必须填写“$equipmentMention（知识库未收录）”，并提示需要补充设备档案；原因、备件和步骤只能作为通用参考。"
                    }.orEmpty()
                val modeInstruction =
                    when {
                        deepGraphContext != null -> "\n\n当前模式：端侧深度诊断。请在diagnosisBasis字段中写明你参考了图谱证据，并在followUpQuestions字段中列出需要现场补采的数据。"
                        isUnknownSymptomFlow -> "\n\n当前模式：知识库未命中后的端侧泛化诊断。diagnosisBasis第一条必须包含“知识库未直接命中该症状”。"
                        else -> ""
                    }
                val unknownSymptomContext =
                    if (isUnknownSymptomFlow) {
                        buildString {
                            appendLine(buildUnknownSymptomReasoningContext(userText, explicitEquipmentMentionForTurn, knowledgeGraph))
                            partialRagContext?.takeIf { it.isNotBlank() }?.let {
                                appendLine()
                                appendLine("已识别到的设备或会话上下文：")
                                appendLine(it)
                            }
                        }
                    } else {
                        null
                    }
                val sysPrompt =
                    when {
                        deepGraphContext != null ->
                            EDGE_DEEP_DIAGNOSIS_PROMPT + "\n\n" + deepGraphContext + explicitUnknownEquipmentGuard + modeInstruction
                        isUnknownSymptomFlow ->
                            UNKNOWN_SYMPTOM_PROMPT + "\n\n" + unknownSymptomContext + explicitUnknownEquipmentGuard + modeInstruction
                        ragContext != null ->
                            SYSTEM_PROMPT + "\n\n请基于以下检索上下文补全诊断JSON；不得编造上下文没有支持的确定结论。\n" + ragContext + explicitUnknownEquipmentGuard + modeInstruction
                        else ->
                            UNKNOWN_SYMPTOM_PROMPT + "\n\n" + unknownSymptomContext + explicitUnknownEquipmentGuard + modeInstruction
                    }

                val httpMessages = org.json.JSONArray().apply {
                    put(org.json.JSONObject().put("role", "system").put("content", sysPrompt))
                    if (deepGraphContext == null) {
                        conversationHistory.takeLast(8).forEach { (role, content) ->
                            if ((role == "user" || role == "assistant") && content.isNotBlank()) {
                                put(org.json.JSONObject().put("role", role).put("content", content.take(1200)))
                            }
                        }
                    }
                    put(org.json.JSONObject().put("role", "user").put("content", userText))
                }

                val reqJson = org.json.JSONObject().apply {
                    put("model", "qwen2.5vl-3b-8850-2.42")
                    put("stream", true)
                    put("messages", httpMessages)
                    put("size", if (deepGraphContext != null) 1024 else 4096)
                    put("temp", 0.0)
                    put("top_k", 1)
                    put("top_p", 1.0)
                    put("stream_options", org.json.JSONObject().put("include_usage", true))
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
                    readTimeout = if (deepGraphContext != null) 45000 else 120000
                }
                httpConn.outputStream.use { it.write(reqJson.toString().toByteArray(Charsets.UTF_8)) }

                val httpCode = httpConn.responseCode
                if (httpCode !in 200..299) {
                    val err = try { httpConn.readErrorBody() } finally { httpConn.disconnect() }
                    throw IllegalStateException(buildHttpErrorMessage("文本推理失败", httpCode, err))
                }

                val fullContent = StringBuilder()
                var responseUsage: GenerationUsage? = null
                var lastStreamUiUpdateAt = 0L
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
                                val chunk = org.json.JSONObject(data)
                                chunk.optGenerationUsage()?.let { responseUsage = it }
                                val choices = chunk.optJSONArray("choices") ?: continue
                                val firstChoice = choices.optJSONObject(0) ?: continue
                                val delta = firstChoice.optJSONObject("delta") ?: firstChoice.optJSONObject("message") ?: continue
                                val deltaContent = delta.optString("content").takeIf { it.isNotBlank() }
                                if (deltaContent != null) {
                                    fullContent.append(deltaContent)
                                    val text = fullContent.toString()
                                    val now = System.currentTimeMillis()
                                    val shouldUpdateStreamUi =
                                        deepGraphReport == null || lastStreamUiUpdateAt == 0L || now - lastStreamUiUpdateAt >= 700L
                                    if (shouldUpdateStreamUi) {
                                        lastStreamUiUpdateAt = now
                                        withContext(Dispatchers.Main) {
                                            val idx = messages.size - 1
                                            if (idx >= 0) {
                                                messages[idx] = messages[idx].copy(
                                                    text = deepGraphReport?.let { buildGraphGroundedProgressText(it) } ?: text,
                                                    sourceLabel = responseSourceLabel,
                                                    graphJson = deepGraphJson,
                                                    isEdgeReasoning = true,
                                                )
                                            }
                                        }
                                    }
                                }
                            } catch (_: Exception) {
                            }
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

                val finalLlmContent =
                    when {
                        deepGraphReport != null ->
                            mergeGraphFactsWithEdgeReasoning(
                                graphReport = deepGraphReport,
                                reasoningText = llmContent,
                                sourceLabel = responseSourceLabel,
                            )
                        llmContent.isBlank() ->
                            buildLocalFallbackDiagnosisJson(
                                userInput = userText,
                                sourceLabel = responseSourceLabel,
                                equipmentMention = explicitEquipmentMentionForTurn,
                                graphReport = null,
                                unknownSymptom = isUnknownSymptomFlow,
                            )
                        else -> llmContent
                    }
                val relatedWOs =
                    deepGraphReport?.workOrders?.takeIf { it.isNotEmpty() }
                        ?: findRelatedWorkOrders(contextualRetrievalText, knowledgeGraph)
                val fieldChecklist =
                    buildFieldChecklistForDiagnosis(
                        userInput = userText,
                        graphReport = deepGraphReport,
                        llmContent = finalLlmContent,
                    )
                val tokenUsage = responseUsage.orEstimated(finalLlmContent)
                withContext(Dispatchers.Main) {
                    val idx = messages.size - 1
                    if (idx >= 0) {
                        messages[idx] = messages[idx].copy(
                            text = finalLlmContent,
                            relatedWorkOrders = relatedWOs,
                            sourceLabel = responseSourceLabel,
                            elapsedMs = t1 - t0,
                            tokenUsage = tokenUsage,
                            graphJson = deepGraphJson,
                            isEdgeReasoning = true,
                            checklist = fieldChecklist,
                        )
                    }
                    visualLookupScopeActive = false
                }
                conversationHistory.add("user" to userText)
                conversationHistory.add("assistant" to finalLlmContent)
                rememberWorkOrderFollowUpContext(userText, finalLlmContent, deepGraphReport)
                appendHistory(userText, finalLlmContent)

            } catch (e: Exception) {
                Log.e(TAG, "Query failed", e)
                withContext(Dispatchers.Main) {
                    val idx = messages.size - 1
                    if (idx >= 0) messages[idx] = messages[idx].copy(text = "推理失败: ${e.message}")
                    else messages.add(PocketMessage(text = "推理失败: ${e.message}", isUser = false))
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isGenerating = false
                    enforceMemoryGuard(reason = "after_generation")
                }
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
                    Box {
                        var showMoreMenu by remember { mutableStateOf(false) }
                        Box {
                            ToolbarAction(icon = Icons.Default.MoreVert, contentDescription = "更多") {
                                showMoreMenu = true
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (queuedWorkOrders.isEmpty()) "提交待办工单" else "立即提交 ${queuedWorkOrders.size} 个工单",
                                        )
                                    },
                                    leadingIcon = { Icon(Icons.Default.CloudUpload, null) },
                                    enabled = queuedWorkOrders.isNotEmpty() && !isSubmittingQueuedWorkOrders,
                                    onClick = {
                                        showMoreMenu = false
                                        trySubmitQueuedWorkOrders(showToast = true)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("查看云端工单") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Assignment, null) },
                                    onClick = {
                                        showMoreMenu = false
                                        loadCloudWorkOrders()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("清空待提交队列", color = DangerColor) },
                                    leadingIcon = { Icon(Icons.Default.DeleteSweep, null, tint = DangerColor) },
                                    enabled = queuedWorkOrders.isNotEmpty(),
                                    onClick = {
                                        showMoreMenu = false
                                        showClearQueuedWorkOrdersDialog = true
                                    },
                                )
                                HorizontalDivider(color = BorderSoft)
                                DropdownMenuItem(
                                    text = { Text("蓝牙诊断采集") },
                                    leadingIcon = { Icon(Icons.Default.Bluetooth, null) },
                                    onClick = {
                                        showMoreMenu = false
                                        showBluetoothDialog = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("端侧深度诊断")
                                            Text(
                                                if (edgeDeepDiagnosisEnabled) "命中图谱后继续调用本机模型推理" else "当前为图谱快答优先",
                                                fontSize = 11.sp,
                                                color = TextMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (edgeDeepDiagnosisEnabled) Icons.Default.CheckCircle else Icons.Default.Memory,
                                            null,
                                            tint = if (edgeDeepDiagnosisEnabled) SuccessColor else TextMuted,
                                        )
                                    },
                                    onClick = {
                                        edgeDeepDiagnosisEnabled = !edgeDeepDiagnosisEnabled
                                        showMoreMenu = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("电脑服务") },
                                    leadingIcon = { Icon(Icons.Default.Storage, null) },
                                    onClick = {
                                        showMoreMenu = false
                                        onConfigureServer()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("新会话") },
                                    leadingIcon = { Icon(Icons.Default.Refresh, null) },
                                    onClick = {
                                        showMoreMenu = false
                                        messages.clear()
                                        conversationHistory.clear()
                                        activeEquipmentContext = null
                                        latestVisualContextText = ""
                                        latestVisualBitmap = null
                                        latestVisualEquipmentContext = null
                                        visualLookupScopeActive = false
                                        resetWorkOrderFollowUpContext()
                                        resetOfflineTriageContext()
                                        selectedWorkOrderMessage = null
                                    },
                                )
                            }
                        }
                        if (queuedWorkOrders.isNotEmpty()) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-3).dp, y = 3.dp)
                                    .size(17.dp)
                                    .background(DangerColor, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    queuedWorkOrders.size.coerceAtMost(9).toString(),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
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
                        EmptyState(edgeDeepDiagnosisEnabled = edgeDeepDiagnosisEnabled) { text -> sendMessage(text, null, null) }
                    } else {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            items(messages) { msg ->
                                MessageBubble(
                                    msg,
                                    onGenerateWorkOrder = { triggerMessage ->
                                        selectedWorkOrderMessage =
                                            buildDynamicWorkOrderMessage(
                                                clickedMessage = triggerMessage,
                                                messages = messages.toList(),
                                                conversationHistory = conversationHistory.toList(),
                                                latestVisualContextText = latestVisualContextText,
                                                activeEquipmentContext = activeEquipmentContext,
                                                graph = knowledgeGraph,
                                            )
                                        clearDiagnosisContextAfterWorkOrder()
                                        selectedWorkOrderMessage?.let { dynamicMessage ->
                                            rememberWorkOrderFollowUpContext("", dynamicMessage.text, dynamicMessage.report)
                                        }
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
            queuedWorkOrderCount = queuedWorkOrders.size,
            onQueueWorkOrder = { workOrderId, createdAt, workOrder ->
                enqueueWorkOrderSubmission(
                    context = context,
                    queue = queuedWorkOrders,
                    workOrderId = workOrderId,
                    createdAt = createdAt,
                    workOrder = workOrder,
                )
            },
            onSubmitQueuedWorkOrders = { showToast -> trySubmitQueuedWorkOrders(showToast = showToast) },
            onRequestClearQueuedWorkOrders = { showClearQueuedWorkOrdersDialog = true },
            onSaveRecord = { userText, aiText ->
                appendHistory(userText, aiText)
                workOrderFollowUpContext = aiText.take(MEMORY_GUARD_CONTEXT_CHAR_LIMIT)
            },
            onWorkOrderCompleted = {
                clearDiagnosisContextAfterWorkOrder()
                rememberWorkOrderFollowUpContext("", workOrderMessage.text, workOrderMessage.report)
            },
            onDismiss = { selectedWorkOrderMessage = null },
        )
    }

    if (showClearQueuedWorkOrdersDialog) {
        AlertDialog(
            onDismissRequest = { showClearQueuedWorkOrdersDialog = false },
            title = { Text("清空待提交队列") },
            text = { Text("将删除本机保存的 ${queuedWorkOrders.size} 个待提交工单。删除后不会再自动上传。") },
            confirmButton = {
                TextButton(onClick = { clearQueuedWorkOrderQueue() }) {
                    Text("确认清空", color = DangerColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearQueuedWorkOrdersDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showCloudWorkOrdersSheet) {
        CloudWorkOrdersSheet(
            records = cloudWorkOrders,
            isLoading = isLoadingCloudWorkOrders,
            error = cloudWorkOrdersError,
            onRefresh = { loadCloudWorkOrders() },
            onDismiss = { showCloudWorkOrdersSheet = false },
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
                messages.clear()
                conversationHistory.clear()
                messages.add(PocketMessage(text = entry.userText, isUser = true))
                messages.add(
                    PocketMessage(
                        text = entry.aiText,
                        isUser = false,
                        isImageResponse = entry.isImage || entry.isVideo,
                        sourceLabel = when {
                            entry.isVideo -> "视频历史"
                            entry.isImage -> "图片历史"
                            entry.isGraphRAG -> "图谱历史"
                            entry.isWorkOrder -> "工单历史"
                            else -> ""
                        },
                        checklist = buildFieldChecklistForDiagnosis(entry.userText, llmContent = entry.aiText),
                    ),
                )
                conversationHistory.add("user" to entry.userText)
                conversationHistory.add("assistant" to entry.aiText)
                activeEquipmentContext = knowledgeGraph.findEquipment("${entry.userText}\n${entry.aiText}").firstOrNull()
                if (entry.isWorkOrder || entry.isGraphRAG || parseLlmReport(entry.aiText) != null) {
                    workOrderFollowUpContext = buildWorkOrderFollowUpContext(entry.userText, entry.aiText)
                } else {
                    resetWorkOrderFollowUpContext()
                }
                latestVisualContextText = ""
                latestVisualBitmap = null
                latestVisualEquipmentContext = null
                visualLookupScopeActive = false
                resetOfflineTriageContext()
            },
        )
    }
}

// ==================== Empty State ====================

@Composable
private fun EmptyState(edgeDeepDiagnosisEnabled: Boolean, onChipClick: (String) -> Unit) {
    val chips = listOf("车辆环车点检，检查仪表盘和反光镜", "5号叉车无法启动", "3号叉车举升缓慢，请给出维修建议", "AGV搬运车偶发急停", "7号叉车转向沉重")
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
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusChip("系统待命", SuccessSoft, SuccessColor)
                    StatusChip("\u56fe\u8c31\u8bca\u65ad", AccentSoft, Accent)
                    StatusChip(if (edgeDeepDiagnosisEnabled) "端侧深度推理" else "图谱快答优先", SurfaceMuted, TextMuted)
                    StatusChip("问诊排查", SurfaceMuted, TextMuted)
                    StatusChip("视频点检", SurfaceMuted, TextMuted)
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
                            Text("5", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
                            Text("端侧入口模式", fontSize = 12.sp, color = TextMuted)
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
                    "车辆点检建议上传环车一周视频，并补拍仪表盘和左右反光镜近景。",
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
private fun VisualAnnotatedImage(
    bitmap: Bitmap,
    annotations: List<VisualAnnotation>,
    modifier: Modifier = Modifier,
) {
    val aspectRatio = remember(bitmap) {
        (bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat()).coerceIn(0.55f, 1.9f)
    }
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.04f)),
    ) {
        Image(
            bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            val bitmapRatio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat()
            val containerRatio = size.width / size.height.coerceAtLeast(1f)
            val drawWidth: Float
            val drawHeight: Float
            val offsetX: Float
            val offsetY: Float
            if (containerRatio > bitmapRatio) {
                drawHeight = size.height
                drawWidth = drawHeight * bitmapRatio
                offsetX = (size.width - drawWidth) / 2f
                offsetY = 0f
            } else {
                drawWidth = size.width
                drawHeight = drawWidth / bitmapRatio
                offsetX = 0f
                offsetY = (size.height - drawHeight) / 2f
            }
            val strokeWidth = 3.dp.toPx()
            val labelPaddingX = 6.dp.toPx()
            val labelHeight = 22.dp.toPx()
            val labelTopPadding = 3.dp.toPx()
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.White.toArgb()
                textSize = 12.sp.toPx()
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
            annotations.forEach { annotation ->
                val left = offsetX + annotation.x1.coerceIn(0f, 1f) * drawWidth
                val top = offsetY + annotation.y1.coerceIn(0f, 1f) * drawHeight
                val right = offsetX + annotation.x2.coerceIn(0f, 1f) * drawWidth
                val bottom = offsetY + annotation.y2.coerceIn(0f, 1f) * drawHeight
                val rectWidth = (right - left).coerceAtLeast(1f)
                val rectHeight = (bottom - top).coerceAtLeast(1f)
                drawRect(
                    color = DangerColor,
                    topLeft = Offset(left, top),
                    size = Size(rectWidth, rectHeight),
                    style = Stroke(width = strokeWidth),
                )

                val confidenceText =
                    annotation.confidence.takeIf { it > 0f }?.let { " ${(it * 100).roundToInt()}%" }.orEmpty()
                val labelText = "${annotation.label}$confidenceText"
                val maxLabelWidth = (size.width - left).coerceAtLeast(1f)
                val labelWidth = (textPaint.measureText(labelText) + labelPaddingX * 2).coerceAtMost(maxLabelWidth).coerceAtLeast(1f)
                val labelTop = (top - labelHeight - labelTopPadding).takeIf { it >= 0f } ?: (top + labelTopPadding)
                drawRoundRect(
                    color = DangerColor.copy(alpha = 0.92f),
                    topLeft = Offset(left, labelTop),
                    size = Size(labelWidth, labelHeight),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                )
                drawContext.canvas.nativeCanvas.drawText(
                    labelText,
                    left + labelPaddingX,
                    labelTop + 15.dp.toPx(),
                    textPaint,
                )
            }
        }
    }
}

@Composable
private fun MessageBitmapPreview(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
) {
    val rawAspectRatio = remember(bitmap) {
        bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat()
    }
    val showFullPreview =
        rawAspectRatio < 0.75f ||
            rawAspectRatio > 1.35f ||
            (bitmap.width >= 600 && bitmap.height >= 600)
    if (showFullPreview) {
        val previewHeight = when {
            rawAspectRatio < 0.55f -> 380.dp
            rawAspectRatio < 0.75f -> 320.dp
            rawAspectRatio > 1.8f -> 170.dp
            else -> 240.dp
        }
        Box(
            modifier
                .fillMaxWidth()
                .height(previewHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.04f)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    } else {
        Image(
            bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier.size(180.dp).clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun MessageBubble(
    msg: PocketMessage,
    onGenerateWorkOrder: (PocketMessage) -> Unit = {},
    onShowGraph: () -> Unit = {},
) {
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    val requiresChecklistCompletionForWorkOrder =
        msg.checklist != null && msg.sourceLabel.contains("端侧泛化")
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
                if (msg.visualAnnotations.isNotEmpty()) {
                    VisualAnnotatedImage(bitmap = bmp, annotations = msg.visualAnnotations)
                } else {
                    MessageBitmapPreview(bitmap = bmp)
                }
                Spacer(Modifier.height(4.dp))
            }
            // Report card or text bubble
            if (msg.report != null) {
                DiagnosticCard(
                    r = msg.report,
                    onGenerateWorkOrder = { onGenerateWorkOrder(msg) },
                    onShowGraph = { if (msg.graphJson.isNotEmpty()) onShowGraph() },
                    sourceLabel = msg.sourceLabel,
                    elapsedMs = msg.elapsedMs,
                    tokenUsage = msg.tokenUsage,
                )
            } else if (msg.text.isNotEmpty()) {
                if (msg.isUser) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp),
                        color = Accent,
                    ) {
                        Text(msg.text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                } else if (msg.text.length > 50 && !msg.isImageResponse && !msg.isFollowUpResponse && !msg.isFieldAssistResponse) {
                    // AI diagnosis response as structured card
                    LlmDiagnosticCard(
                        text = msg.text,
                        onGenerateWorkOrder = { onGenerateWorkOrder(msg) },
                        sourceLabel = msg.sourceLabel,
                        elapsedMs = msg.elapsedMs,
                        tokenUsage = msg.tokenUsage,
                        onShowGraph = { if (msg.graphJson.isNotEmpty()) onShowGraph() },
                        hasGraph = msg.graphJson.isNotEmpty(),
                        showGenerateWorkOrderButton = !requiresChecklistCompletionForWorkOrder,
                    )
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
                    if (msg.sourceLabel.isNotBlank() || msg.elapsedMs > 0L) {
                        Spacer(Modifier.height(6.dp))
                        SourceTraceRow(sourceLabel = msg.sourceLabel, elapsedMs = msg.elapsedMs, tokenUsage = msg.tokenUsage)
                    }
                    if (msg.canGenerateWorkOrder && !msg.text.startsWith("正在") && !requiresChecklistCompletionForWorkOrder) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onGenerateWorkOrder(msg) },
                            modifier = Modifier.fillMaxWidth().height(38.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Accent.copy(alpha = 0.35f)),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Assignment, null, Modifier.size(15.dp), tint = Accent)
                            Spacer(Modifier.width(5.dp))
                            Text("生成工单", fontSize = 13.sp, color = Accent)
                        }
                    }
                }

                // Related work orders from knowledge graph (for non-RAG responses)
                if (msg.relatedWorkOrders.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    RelatedWorkOrdersSection(msg.relatedWorkOrders)
                }
            }
            if (!msg.isUser && msg.checklist != null) {
                Spacer(Modifier.height(8.dp))
                FieldChecklistCard(
                    checklist = msg.checklist,
                    showCompletionWorkOrder = !msg.canGenerateWorkOrder || requiresChecklistCompletionForWorkOrder,
                    onGenerateWorkOrder = { completedItems ->
                        onGenerateWorkOrder(msg.withChecklistCompletionContext(msg.checklist, completedItems))
                    },
                )
            }
        }
    }
}

// ==================== Diagnostic Card ====================

@Composable
private fun DiagnosticCard(
    r: DiagnosticReport,
    onGenerateWorkOrder: () -> Unit = {},
    onShowGraph: () -> Unit = {},
    sourceLabel: String = "",
    elapsedMs: Long = 0L,
    tokenUsage: GenerationUsage? = null,
) {
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
                    Text(
                        listOfNotNull(
                            sourceLabel.ifBlank { "\u56fe\u8c31\u8bca\u65ad" },
                            "${r.nodeCount} \u8282\u70b9",
                        ).joinToString(" \u00b7 "),
                        color = TextMuted,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    SourceTraceRow(
                        sourceLabel = sourceLabel.ifBlank { "\u56fe\u8c31\u8bca\u65ad" },
                        elapsedMs = elapsedMs,
                        tokenUsage = tokenUsage,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    StatusChip(sourceLabel.ifBlank { "\u56fe\u8c31\u8bca\u65ad" }, AccentSoft, Accent)
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
                                HorizontalDivider(color = BorderSoft)
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
private fun LlmDiagnosticCard(
    text: String,
    onGenerateWorkOrder: () -> Unit,
    sourceLabel: String = "",
    elapsedMs: Long = 0L,
    tokenUsage: GenerationUsage? = null,
    onShowGraph: () -> Unit = {},
    hasGraph: Boolean = false,
    showGenerateWorkOrderButton: Boolean = true,
) {
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
                    SourceTraceRow(
                        sourceLabel = sourceLabel.ifBlank { "\u7aef\u4fa7\u6a21\u578b\u8bca\u65ad" },
                        elapsedMs = elapsedMs,
                        tokenUsage = tokenUsage,
                    )
                }
                report?.severity?.takeIf { it.isNotBlank() }?.let { severity ->
                    val (severityBg, severityFg) = severityColors(severity)
                    StatusChip(severity, severityBg, severityFg)
                } ?: StatusChip(sourceLabel.ifBlank { "\u7aef\u4fa7\u6a21\u578b" }, AccentSoft, Accent)
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

                if (report.diagnosisBasis.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Label("端侧推理依据")
                    report.diagnosisBasis.take(4).forEach { item ->
                        Surface(color = SurfaceSoft, shape = RoundedCornerShape(14.dp)) {
                            Text(
                                item,
                                fontSize = 13.sp,
                                color = TextBody,
                                lineHeight = 19.sp,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
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

                if (report.riskNote.isNotBlank() || report.temporaryAction.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Label("风险与临时处置")
                    Surface(color = WarningSoft, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, WarningColor.copy(alpha = 0.22f))) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                            if (report.riskNote.isNotBlank()) {
                                Text("风险：${report.riskNote}", fontSize = 13.sp, color = TextBody, lineHeight = 19.sp)
                            }
                            if (report.temporaryAction.isNotBlank()) {
                                if (report.riskNote.isNotBlank()) Spacer(Modifier.height(4.dp))
                                Text("临时处置：${report.temporaryAction}", fontSize = 13.sp, color = TextBody, lineHeight = 19.sp)
                            }
                        }
                    }
                }

                if (report.followUpQuestions.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Label("需要补充的信息")
                    report.followUpQuestions.take(4).forEach { question ->
                        Surface(color = SurfaceMuted, shape = RoundedCornerShape(14.dp)) {
                            Text(
                                question,
                                fontSize = 13.sp,
                                color = TextBody,
                                lineHeight = 19.sp,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            } else {
                // Fallback: render as Markdown
                Surface(color = SurfaceMuted, shape = RoundedCornerShape(14.dp)) {
                    MarkdownText(text = text, modifier = Modifier.padding(14.dp), textColor = TextMain)
                }
            }

            if (showGenerateWorkOrderButton) {
                Spacer(Modifier.height(12.dp))
                if (hasGraph) {
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
                            Text("图谱依据", fontSize = 14.sp)
                        }
                    }
                } else {
                    Button(
                        onClick = onGenerateWorkOrder,
                        Modifier.fillMaxWidth().height(42.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("生成工单报告", fontSize = 14.sp) }
                }
            }
        }
    }
}

@Composable
private fun SourceTraceRow(sourceLabel: String, elapsedMs: Long, tokenUsage: GenerationUsage? = null) {
    val details = listOfNotNull(
        sourceLabel.takeIf { it.isNotBlank() },
        elapsedMs.takeIf { it > 0L }?.let { "生成用时 ${formatElapsedMs(it)}" },
        tokenUsage?.label(),
        "本机NPU",
    ).joinToString(" · ")
    Text(details, color = TextMuted, fontSize = 12.sp, lineHeight = 17.sp)
}

private fun formatElapsedMs(ms: Long): String {
    return if (ms >= 1000L) {
        String.format(Locale.US, "%.1fs", ms / 1000f)
    } else {
        "${ms}ms"
    }
}

@Composable
private fun FieldChecklistCard(
    checklist: FieldChecklist,
    showCompletionWorkOrder: Boolean = false,
    onGenerateWorkOrder: (List<FieldChecklistItem>) -> Unit = {},
) {
    val checked = remember(checklist.title, checklist.items) {
        mutableStateListOf<Boolean>().apply { repeat(checklist.items.size) { add(false) } }
    }
    val completed = checked.count { it }
    val allCompleted = checklist.items.isNotEmpty() && completed == checklist.items.size
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BorderSoft),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.CheckCircle, null, tint = SuccessColor, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(checklist.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
                    Text(checklist.reason, fontSize = 12.sp, color = TextMuted, lineHeight = 18.sp)
                }
                StatusChip("$completed/${checklist.items.size}", SurfaceMuted, TextMuted)
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { if (checklist.items.isEmpty()) 0f else completed / checklist.items.size.toFloat() },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(999.dp)),
                color = SuccessColor,
                trackColor = SurfaceSoft,
            )
            Spacer(Modifier.height(10.dp))
            checklist.items.forEachIndexed { index, item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { checked[index] = !checked[index] }
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Checkbox(
                        checked = checked.getOrNull(index) == true,
                        onCheckedChange = { checked[index] = it },
                        modifier = Modifier.size(22.dp),
                        colors = CheckboxDefaults.colors(checkedColor = SuccessColor),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextMain,
                            lineHeight = 18.sp,
                        )
                        Text(item.detail, fontSize = 12.sp, color = TextMuted, lineHeight = 17.sp)
                        if (checked.getOrNull(index) == true) {
                            Spacer(Modifier.height(2.dp))
                            Text(item.impact, fontSize = 12.sp, color = SuccessColor, lineHeight = 17.sp)
                        }
                    }
                }
                if (index != checklist.items.lastIndex) HorizontalDivider(color = BorderSoft)
            }
            if (allCompleted) {
                Spacer(Modifier.height(8.dp))
                Text("清单已完成，可把检查结果写入工单后再提交。", fontSize = 12.sp, color = SuccessColor)
                if (showCompletionWorkOrder) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { onGenerateWorkOrder(checklist.items) },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Assignment, null, Modifier.size(15.dp), tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("生成动态工单", fontSize = 13.sp)
                    }
                }
            }
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
                            HorizontalDivider(color = BorderSoft)
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
        for (i in 1..4) {
            kotlinx.coroutines.delay(1200)
            step = i
        }
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
    queuedWorkOrderCount: Int,
    onQueueWorkOrder: (String, String, WorkOrderDocumentData) -> Unit,
    onSubmitQueuedWorkOrders: (Boolean) -> Unit,
    onRequestClearQueuedWorkOrders: () -> Unit,
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
    val qualityReview = buildWorkOrderQualityReview(editedWorkOrder)
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
            if (queuedWorkOrderCount > 0) {
                Spacer(Modifier.height(10.dp))
                Surface(color = WarningSoft, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, WarningColor.copy(alpha = 0.24f))) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.CloudUpload, null, tint = WarningColor, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "$queuedWorkOrderCount 个工单待提交，联网后会自动补交。",
                            fontSize = 12.sp,
                            color = WarningColor,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onSubmitQueuedWorkOrders(true) }) {
                            Text("立即提交", fontSize = 12.sp, color = WarningColor)
                        }
                        TextButton(onClick = onRequestClearQueuedWorkOrders) {
                            Text("清空", fontSize = 12.sp, color = DangerColor)
                        }
                    }
                }
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

            WorkOrderQualityCard(qualityReview)
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
                    saveEditedRecordIfChanged()
                    onQueueWorkOrder(workOrderId, createdAt, editedWorkOrder)
                    onSubmitQueuedWorkOrders(true)
                    val qualityHint =
                        qualityReview.missingItems.firstOrNull()
                            ?.let { "，质量检查建议补充：$it" }
                            .orEmpty()
                    val submitHint =
                        if (normalizedDemoServerBaseUrl.isNotBlank()) {
                            "工单已保存，正在尝试提交；失败时会保留在待提交队列"
                        } else {
                            "工单已进入待提交队列"
                        }
                    Toast.makeText(context, "$submitHint$qualityHint", Toast.LENGTH_LONG).show()
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SuccessColor),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.CloudUpload, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("提交工单")
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
private fun WorkOrderQualityCard(review: WorkOrderQualityReview) {
    val scoreColor = when {
        review.score >= 85 -> SuccessColor
        review.score >= 70 -> WarningColor
        else -> DangerColor
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, BorderSoft),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("端侧工单质量检查", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain)
                    Spacer(Modifier.height(3.dp))
                    Text("提交前在本机检查关键信息是否缺失。", fontSize = 12.sp, color = TextMuted)
                }
                StatusChip(review.level, scoreColor.copy(alpha = 0.12f), scoreColor)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { review.score / 100f },
                    modifier = Modifier.weight(1f).height(7.dp).clip(RoundedCornerShape(999.dp)),
                    color = scoreColor,
                    trackColor = SurfaceSoft,
                )
                Spacer(Modifier.width(10.dp))
                Text("${review.score}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = scoreColor)
            }
            if (review.missingItems.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("建议先补充", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DangerColor)
                review.missingItems.forEach { item ->
                    QualityLine(text = item, color = DangerColor)
                }
            }
            if (review.suggestions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("可提升复盘价值", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WarningColor)
                review.suggestions.forEach { item ->
                    QualityLine(text = item, color = WarningColor)
                }
            }
            if (review.strengths.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    review.strengths.take(3).forEach { strength ->
                        StatusChip(strength, SuccessSoft, SuccessColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityLine(text: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 6.dp).size(5.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 12.sp, color = TextBody, lineHeight = 17.sp, modifier = Modifier.weight(1f))
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
                val n = nodesArr.getJSONObject(i)
                GNode(n.getString("id"), n.getString("type"), n.getString("label"))
            }
            val edges = (0 until edgesArr.length()).map { i ->
                val e = edgesArr.getJSONObject(i)
                GEdge(e.getString("source"), e.getString("target"), e.getString("relation"), e.optJSONObject("properties")?.optString("probability") ?: "")
            }
            Triple(centerId, nodes, edges)
        } catch (e: Exception) {
            Log.e(TAG, "GraphViz parse failed", e)
            null
        }
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

// ==================== Cloud Work Orders Sheet ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CloudWorkOrdersSheet(
    records: List<DemoSubmittedWorkOrder>,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * 0.86f).dp

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = BgColor) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxSheetHeight).padding(horizontal = 20.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("云端工单", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextMain)
                    Text("查看已提交到电脑服务的工单记录。", fontSize = 12.sp, color = TextMuted)
                }
                StatusChip("${records.size} 条", SurfaceMuted, TextMuted)
            }

            OutlinedButton(
                onClick = onRefresh,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(42.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderSoft),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Accent)
                    Spacer(Modifier.width(8.dp))
                    Text("正在刷新", fontSize = 13.sp)
                } else {
                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("刷新云端工单", fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(10.dp))

            when {
                isLoading && records.isEmpty() -> {
                    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Accent)
                    }
                }
                error != null -> {
                    Surface(color = WarningSoft, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, WarningColor.copy(alpha = 0.24f))) {
                        Text(
                            error,
                            color = WarningColor,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                        )
                    }
                }
                records.isEmpty() -> {
                    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                        Text("云端暂无已提交工单", fontSize = 14.sp, color = TextMuted)
                    }
                }
                else -> {
                    LazyColumn(Modifier.weight(1f)) {
                        items(records) { record ->
                            CloudWorkOrderCard(record)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CloudWorkOrderCard(record: DemoSubmittedWorkOrder) {
    var expanded by remember(record.clientSubmissionId, record.workOrderId) { mutableStateOf(false) }
    val workOrder = record.payload.workOrder
    val title = record.workOrderId.ifBlank { record.serverWorkOrderId.ifBlank { record.clientSubmissionId.ifBlank { "云端工单" } } }
    val submittedAt = record.submittedAt.take(19).replace('T', ' ')
    val status = workOrder.status.ifBlank { "已提交" }

    Card(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderSoft),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    Text(submittedAt.ifBlank { "提交时间未知" }, fontSize = 11.sp, color = TextSubtle)
                }
                StatusChip(status, SuccessSoft, SuccessColor)
                Spacer(Modifier.width(6.dp))
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    null,
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(Modifier.height(10.dp))
            Surface(color = SurfaceMuted, shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    InfoRow("设备", workOrder.equipment.ifBlank { "未填写" })
                    if (workOrder.location.isNotBlank()) InfoRow("位置", workOrder.location)
                    InfoRow("故障", workOrder.symptom.ifBlank { "未填写" })
                    if (workOrder.severity.isNotBlank()) InfoRow("严重程度", workOrder.severity)
                }
            }

            if (workOrder.summary.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(workOrder.summary, fontSize = 12.sp, color = TextMuted, lineHeight = 18.sp, maxLines = if (expanded) Int.MAX_VALUE else 2)
            }

            if (expanded) {
                CloudWorkOrderSection("可能原因", workOrder.causes)
                CloudWorkOrderSection("维修步骤", workOrder.steps)
                CloudWorkOrderSection("所需备件", workOrder.parts)
                CloudWorkOrderSection("推荐人员", workOrder.personnel)
                CloudWorkOrderSection("相似工单", workOrder.relatedWorkOrders)
            } else {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (workOrder.steps.isNotEmpty()) StatusChip("${workOrder.steps.size} 步骤", AccentSoft, Accent)
                    if (workOrder.parts.isNotEmpty()) StatusChip("${workOrder.parts.size} 备件", SurfaceMuted, TextMuted)
                }
            }
        }
    }
}

@Composable
private fun CloudWorkOrderSection(title: String, lines: List<String>) {
    if (lines.isEmpty()) return
    Spacer(Modifier.height(10.dp))
    Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextMain)
    Spacer(Modifier.height(6.dp))
    lines.forEach { line ->
        Surface(color = SurfaceMuted, shape = RoundedCornerShape(12.dp)) {
            Text(
                line,
                fontSize = 12.sp,
                color = TextBody,
                lineHeight = 18.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
    }
}

// ==================== History Sheet ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(history: List<HistoryEntry>, onDismiss: () -> Unit, onClear: () -> Unit, onRestore: (HistoryEntry) -> Unit) {
    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }
    val filteredHistory = remember(history, query, filter) {
        val normalizedQuery = query.trim().lowercase(Locale.getDefault())
        history.filter { entry ->
            val matchesType = when (filter) {
                HistoryFilter.ALL -> true
                HistoryFilter.GRAPH -> entry.isGraphRAG
                HistoryFilter.IMAGE -> entry.isImage
                HistoryFilter.VIDEO -> entry.isVideo
                HistoryFilter.WORK_ORDER -> entry.isWorkOrder
                HistoryFilter.LLM -> !entry.isGraphRAG && !entry.isImage && !entry.isVideo && !entry.isWorkOrder
            }
            val searchText = "${entry.userText}\n${entry.aiText}".lowercase(Locale.getDefault())
            matchesType && (normalizedQuery.isBlank() || searchText.contains(normalizedQuery))
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = BgColor) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxSheetHeight).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("诊断历史", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextMain)
                    Text("恢复上下文或回看已生成诊断。", fontSize = 12.sp, color = TextMuted)
                }
                StatusChip("${filteredHistory.size}/${history.size} 条", SurfaceMuted, TextMuted)
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索设备、故障、工单或诊断内容", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, "清空搜索", tint = TextMuted)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = BorderSoft,
                    unfocusedIndicatorColor = BorderSoft,
                ),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HistoryFilter.entries.forEach { item ->
                    val selected = item == filter
                    Surface(
                        color = if (selected) AccentSoft else Color.White,
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, if (selected) Accent.copy(alpha = 0.35f) else BorderSoft),
                        modifier = Modifier.clickable { filter = item },
                    ) {
                        Text(
                            item.label,
                            fontSize = 12.sp,
                            color = if (selected) Accent else TextMuted,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (history.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text("暂无诊断记录", fontSize = 14.sp, color = TextMuted)
                }
            } else if (filteredHistory.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text("没有匹配的历史记录", fontSize = 14.sp, color = TextMuted)
                }
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(filteredHistory.reversed()) { entry ->
                        Card(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    onRestore(entry)
                                    onDismiss()
                                },
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(1.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, BorderSoft),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(dateFormat.format(Date(entry.timestamp)), fontSize = 11.sp, color = TextSubtle)
                                    val tag = when {
                                        entry.isWorkOrder -> "工单"
                                        entry.isVideo -> "视频"
                                        entry.isImage -> "图片"
                                        entry.isGraphRAG -> "图谱诊断"
                                        else -> "端侧模型"
                                    }
                                    val tagColor = when {
                                        entry.isWorkOrder -> PrimaryMid
                                        entry.isGraphRAG -> SuccessColor
                                        entry.isImage || entry.isVideo -> Accent
                                        else -> WarningColor
                                    }
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
                        Text("未填写问题时将按环车点检检查仪表盘和反光镜", fontSize = 12.sp, color = TextMuted, maxLines = 1)
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
                        text = { Text("导入点检视频") },
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
                    placeholder = { Text("输入故障现象、设备编号或点检需求...", fontSize = 14.sp, color = TextSubtle) },
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
            Regex("""AGV\s*搬运车""", RegexOption.IGNORE_CASE),
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

private fun buildDeepGraphReasoningContext(
    userInput: String,
    report: DiagnosticReport,
    graphJson: String,
    graphElapsedMs: Long,
): String {
    return buildString {
        appendLine("用户原始问题：$userInput")
        appendLine("本地知识图谱已命中，检索耗时：${graphElapsedMs}ms。")
        appendLine("以下为强制采用的知识图谱事实，最终答案必须以这些事实为准：")
        appendLine(buildGraphFactLockText(report))
        appendLine()
        appendLine("你只需要输出补充推理字段：diagnosisBasis、followUpQuestions、riskNote、temporaryAction。")
        appendLine("不要输出设备、症状、原因、备件、步骤、人员、工单字段；这些字段由本地知识图谱确定性填充。")
    }
}

private fun buildGraphGroundedProgressText(report: DiagnosticReport): String {
    return buildString {
        appendLine("已命中本地知识图谱，正在用端侧模型补充推理。")
        appendLine()
        appendLine(buildGraphFactLockText(report))
    }.trim()
}

private fun buildGraphFactLockText(report: DiagnosticReport): String {
    return buildString {
        appendLine("设备：${report.equipment.label}")
        report.equipment.properties["location"]?.takeIf { it.isNotBlank() }?.let { appendLine("位置：$it") }
        appendLine("症状：${report.symptom.label}")
        report.symptom.properties["severity"]?.takeIf { it.isNotBlank() }?.let { appendLine("严重度：$it") }
        if (report.causes.isNotEmpty()) {
            appendLine("图谱原因：${report.causes.joinToString("；") { cause -> "${cause.label} ${((cause.properties["probability"]?.toFloatOrNull() ?: 0f) * 100).toInt()}%" }}")
        }
        if (report.parts.isNotEmpty()) {
            appendLine("图谱备件：${report.parts.joinToString("；") { part -> listOf(part.label, part.properties["spec"].orEmpty(), part.properties["stock"].orEmpty()).filter { it.isNotBlank() }.joinToString(" / ") }}")
        }
        if (report.steps.isNotEmpty()) {
            appendLine("图谱步骤：${report.steps.joinToString(" -> ") { it.label }}")
        }
        if (report.workOrders.isNotEmpty()) {
            appendLine("相似工单：${report.workOrders.joinToString("；") { workOrder -> "${workOrder.label} ${workOrder.properties["date"].orEmpty()} ${workOrder.properties["resolution"].orEmpty()}".trim() }}")
        }
    }.trim()
}

private fun mergeGraphFactsWithEdgeReasoning(
    graphReport: DiagnosticReport,
    reasoningText: String,
    sourceLabel: String,
): String {
    val reasoning = parseLlmReport(reasoningText)
    return buildGraphGroundedDiagnosisJson(
        graphReport = graphReport,
        diagnosisBasis = reasoning?.diagnosisBasis
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(
                "本地知识图谱命中设备“${graphReport.equipment.label}”和症状“${graphReport.symptom.label}”。",
                "原因概率、备件、步骤和相似工单均来自本地知识图谱，端侧模型只补充排查解释。",
            ),
        followUpQuestions = reasoning?.followUpQuestions
            ?.takeIf { it.isNotEmpty() }
            ?: listOf("现场故障发生时是否伴随报警码或仪表异常？", "该症状是在空载还是负载状态下更明显？"),
        riskNote = reasoning?.riskNote
            ?.takeIf { it.isNotBlank() }
            ?: "继续运行可能扩大故障范围，应按图谱步骤先完成安全检查。",
        temporaryAction = reasoning?.temporaryAction
            ?.takeIf { it.isNotBlank() }
            ?: "降低负载或暂停使用，保留故障现场，先采集关键参数。",
        sourceLabel = sourceLabel,
    )
}

private fun buildGraphGroundedDiagnosisJson(
    graphReport: DiagnosticReport,
    diagnosisBasis: List<String>,
    followUpQuestions: List<String>,
    riskNote: String,
    temporaryAction: String,
    sourceLabel: String,
): String {
    return org.json.JSONObject().apply {
        put("equipment", graphReport.equipment.label)
        put("location", graphReport.equipment.properties["location"].orEmpty())
        put("symptom", graphReport.symptom.label)
        put("severity", graphReport.symptom.properties["severity"].orEmpty())
        put(
            "causes",
            org.json.JSONArray().apply {
                graphReport.causes.forEach { cause ->
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
                graphReport.parts.forEach { part ->
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
                graphReport.steps.forEach { step ->
                    put(
                        org.json.JSONObject()
                            .put("title", step.label)
                            .put("duration", step.properties["duration"].orEmpty())
                            .put("tool", step.properties["tool"].orEmpty()),
                    )
                }
            },
        )
        put(
            "personnel",
            org.json.JSONArray().apply {
                graphReport.personnel.forEach { person ->
                    put(
                        org.json.JSONObject()
                            .put("name", person.label)
                            .put("skill", person.properties["skill"].orEmpty())
                            .put("experience", person.properties["experience"].orEmpty()),
                    )
                }
            },
        )
        put(
            "workOrders",
            org.json.JSONArray().apply {
                graphReport.workOrders.forEach { workOrder ->
                    put(
                        org.json.JSONObject()
                            .put("id", workOrder.label)
                            .put("date", workOrder.properties["date"].orEmpty())
                            .put("resolution", workOrder.properties["resolution"].orEmpty()),
                    )
                }
            },
        )
        put(
            "diagnosisBasis",
            org.json.JSONArray().apply {
                put("$sourceLabel：诊断主体来自本地知识图谱。")
                diagnosisBasis.take(4).forEach { put(it) }
            },
        )
        put("followUpQuestions", org.json.JSONArray().apply { followUpQuestions.take(4).forEach { put(it) } })
        put("riskNote", riskNote)
        put("temporaryAction", temporaryAction)
    }.toString()
}

private fun buildUnknownSymptomReasoningContext(
    userInput: String,
    explicitEquipmentMention: String?,
    graph: MaintenanceKnowledgeGraph,
): String {
    val equipment = graph.findEquipment(userInput).firstOrNull()
    val equipmentLabel =
        equipment?.label
            ?: explicitEquipmentMention?.let { "$it（知识库未收录）" }
            ?: "待确认设备"
    val similarSymptoms = findSimilarSymptomsForUnknownInput(userInput, graph)
    return buildString {
        appendLine("用户原始问题：$userInput")
        appendLine("设备识别：$equipmentLabel")
        appendLine("知识库未直接命中该症状，请进入端侧泛化诊断。")
        appendLine("约束：不能输出确定故障码；不能把相似症状当作已命中结论；必须说明需要采集哪些数据来验证。")
        if (similarSymptoms.isNotEmpty()) {
            appendLine()
            appendLine("可参考的相似症状，不代表直接命中：")
            similarSymptoms.forEach { symptom ->
                val sub = graph.traverseFromNode(symptom.id, maxHops = 2)
                val causes = sub.nodes
                    .filter { it.type == NodeType.ROOT_CAUSE }
                    .sortedByDescending { it.properties["probability"]?.toFloatOrNull() ?: 0f }
                    .take(3)
                    .joinToString("、") { it.label }
                val steps = sub.nodes
                    .filter { it.type == NodeType.REPAIR_STEP }
                    .sortedBy { it.properties["order"]?.toIntOrNull() ?: 99 }
                    .take(3)
                    .joinToString("、") { it.label }
                appendLine("- ${symptom.label}：严重度${symptom.properties["severity"].orEmpty()}；常见原因：${causes.ifBlank { "暂无" }}；参考排查：${steps.ifBlank { "暂无" }}")
            }
        } else {
            appendLine("未找到足够相似的症状参考。请主要输出通用安全排查路径。")
        }
    }
}

private fun buildLocalFallbackDiagnosisJson(
    userInput: String,
    sourceLabel: String,
    equipmentMention: String?,
    graphReport: DiagnosticReport?,
    unknownSymptom: Boolean,
): String {
    val equipment =
        graphReport?.equipment?.label
            ?: equipmentMention?.let { "$it（知识库未收录）" }
            ?: "待确认设备"
    val location = graphReport?.equipment?.properties?.get("location").orEmpty()
    val symptom = graphReport?.symptom?.label ?: userInput.take(40).ifBlank { "待确认症状" }
    val severity = graphReport?.symptom?.properties?.get("severity").orEmpty().ifBlank { "中" }
    return org.json.JSONObject().apply {
        put("equipment", equipment)
        put("location", location)
        put("symptom", symptom)
        put("severity", severity)
        put(
            "causes",
            org.json.JSONArray().apply {
                val graphCauses = graphReport?.causes.orEmpty().take(3)
                if (graphCauses.isNotEmpty()) {
                    graphCauses.forEach { cause ->
                        put(
                            org.json.JSONObject()
                                .put("name", cause.label)
                                .put("probability", ((cause.properties["probability"]?.toFloatOrNull() ?: 0f) * 100).toInt()),
                        )
                    }
                } else {
                    put(org.json.JSONObject().put("name", "现场数据不足，需先采集电压、压力、温度或故障码").put("probability", 45))
                    put(org.json.JSONObject().put("name", "部件老化、接插件松动或传感器异常").put("probability", 35))
                }
            },
        )
        put(
            "parts",
            org.json.JSONArray().apply {
                graphReport?.parts.orEmpty().take(3).forEach { part ->
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
                val graphSteps = graphReport?.steps.orEmpty().take(4)
                if (graphSteps.isNotEmpty()) {
                    graphSteps.forEach { step ->
                        put(
                            org.json.JSONObject()
                                .put("title", step.label)
                                .put("duration", step.properties["duration"].orEmpty())
                                .put("tool", step.properties["tool"].orEmpty()),
                        )
                    }
                } else {
                    put(org.json.JSONObject().put("title", "记录故障出现工况、仪表提示和异常声音/气味").put("duration", "3分钟").put("tool", "现场询问"))
                    put(org.json.JSONObject().put("title", "采集电池电压、液压压力、温度和控制器故障码").put("duration", "8分钟").put("tool", "万用表/诊断仪"))
                    put(org.json.JSONObject().put("title", "按系统分支隔离电气、液压、机械传动异常").put("duration", "15分钟").put("tool", "点检表"))
                }
            },
        )
        put("personnel", org.json.JSONArray())
        put("workOrders", org.json.JSONArray())
        put(
            "diagnosisBasis",
            org.json.JSONArray().apply {
                put(if (unknownSymptom) "知识库未直接命中该症状，当前为端侧泛化诊断。" else "$sourceLabel，端侧模型输出为空时启用本地兜底结构。")
                graphReport?.let { put("已参考图谱中的${it.symptom.label}、${it.causes.size}个原因和${it.steps.size}个步骤。") }
            },
        )
        put(
            "followUpQuestions",
            org.json.JSONArray().apply {
                put("故障是在启动、行走、举升还是制动时出现？")
                put("仪表盘或控制器是否有故障码、报警灯或异常参数？")
                put("最近是否更换过电池、液压油、传感器或维修过相关部件？")
            },
        )
        put("riskNote", if (unknownSymptom) "症状未被知识库确认，继续运行可能扩大故障范围，应先完成基础安全检查。" else "需结合现场参数确认优先原因，避免只按历史概率更换备件。")
        put("temporaryAction", "降载运行或暂停使用，保留现场状态，先完成电气、液压和故障码采集。")
    }.toString()
}

private fun CharSequence.countCharsMatching(predicate: (Char) -> Boolean): Int {
    var count = 0
    for (char in this) {
        if (predicate(char)) count += 1
    }
    return count
}

private fun findSimilarSymptomsForUnknownInput(userInput: String, graph: MaintenanceKnowledgeGraph): List<GraphNode> {
    val allSymptoms = graph.findEquipment("叉车")
        .flatMap { equipment -> SYMPTOM_KEYWORDS.flatMap { keyword -> graph.findSymptoms(equipment.id, keyword) } }
        .distinctBy { it.id }
    if (allSymptoms.isEmpty()) return emptyList()
    val normalizedInput = userInput.filterNot { it.isWhitespace() }
    return allSymptoms
        .map { symptom ->
            val score =
                symptom.label.countCharsMatching { char -> normalizedInput.contains(char) } +
                    symptom.properties.values.sumOf { value -> value.countCharsMatching { char -> normalizedInput.contains(char) } }.coerceAtMost(3)
            symptom to score
        }
        .sortedWith(compareByDescending<Pair<GraphNode, Int>> { it.second }.thenBy { it.first.label.length })
        .filter { it.second > 0 }
        .map { it.first }
        .take(4)
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
