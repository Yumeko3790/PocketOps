package com.pocketops.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream

private const val DEMO_SERVER_PREFS = "pocketops_demo_server"
private const val DEMO_SERVER_BASE_URL_KEY = "base_url"
private const val DEMO_SYNC_ROOT_DIR = "pocketops_demo_sync"
private const val DEMO_MATERIALS_DIR = "documents/materials"

data class DemoSyncSummary(
    val equipmentCount: Int = 0,
    val faultCaseCount: Int = 0,
    val graphNodeCount: Int = 0,
    val graphEdgeCount: Int = 0,
    val sopCount: Int = 0,
    val partCount: Int = 0,
    val workOrderCount: Int = 0,
    val personnelCount: Int = 0,
)

data class DemoDisplayStep(
    val key: String = "",
    val title: String = "",
    val detail: String = "",
)

data class DemoResource(
    val id: String = "",
    val name: String = "",
    val category: String = "",
    val requiredAtBoot: Boolean = false,
    val version: String = "",
    val sizeBytes: Long = 0L,
    val sha256: String = "",
    val mimeType: String = "",
    val downloadUrl: String = "",
    val localPath: String = "",
    val zip: Boolean = false,
    val unzipDir: String = "",
)

data class DemoManifest(
    val syncVersion: String = "",
    val minSupportedAppVersion: String = "",
    val generatedAt: String = "",
    val summary: DemoSyncSummary = DemoSyncSummary(),
    val displaySteps: List<DemoDisplayStep> = emptyList(),
    val resources: List<DemoResource> = emptyList(),
)

data class DemoLinkedEntities(
    val equipmentIds: List<String> = emptyList(),
    val symptomIds: List<String> = emptyList(),
    val workOrderIds: List<String> = emptyList(),
)

data class DemoMaterial(
    val id: String = "",
    val title: String = "",
    val type: String = "",
    val category: String = "",
    val sizeBytes: Long = 0L,
    val sha256: String = "",
    val mimeType: String = "",
    val version: String = "",
    val previewable: Boolean = false,
    val shareable: Boolean = false,
    val downloadUrl: String = "",
    val thumbnailUrl: String = "",
    val linkedEntities: DemoLinkedEntities = DemoLinkedEntities(),
)

private data class DemoMaterialsResponse(
    val materials: List<DemoMaterial> = emptyList(),
)

private data class DemoHttpResponse(
    val code: Int,
    val body: String,
)

fun loadDemoServerBaseUrl(context: Context): String {
    val prefs = context.getSharedPreferences(DEMO_SERVER_PREFS, Context.MODE_PRIVATE)
    return prefs.getString(DEMO_SERVER_BASE_URL_KEY, "") ?: ""
}

fun saveDemoServerBaseUrl(context: Context, baseUrl: String) {
    context.getSharedPreferences(DEMO_SERVER_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(DEMO_SERVER_BASE_URL_KEY, baseUrl)
        .apply()
}

fun normalizeDemoServerBaseUrl(raw: String): String {
    val trimmed = raw.trim().removeSuffix("/")
    if (trimmed.isBlank()) {
        return ""
    }
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "http://$trimmed"
    }
}

fun Long.toDemoSizeLabel(): String {
    if (this <= 0L) {
        return "0 \u5b57\u8282"
    }

    val units = arrayOf("B", "KB", "MB", "GB")
    var value = toDouble()
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index += 1
    }
    val digits = if (value >= 100.0 || index == 0) 0 else 1
    return String.format(Locale.US, "%.${digits}f %s", value, units[index])
}

fun String.toDemoTypeLabel(): String {
    return when (trim().lowercase(Locale.ROOT)) {
        "pdf" -> "\u6587\u6863"
        "doc", "docx" -> "\u6587\u672c\u6587\u6863"
        "xls", "xlsx" -> "\u6570\u636e\u8868"
        "ppt", "pptx" -> "\u6f14\u793a\u6587\u7a3f"
        "zip", "rar", "7z" -> "\u538b\u7f29\u5305"
        "json" -> "\u6570\u636e\u6587\u4ef6"
        "txt", "md" -> "\u6587\u672c\u8d44\u6599"
        "jpg", "jpeg", "png", "webp", "gif", "bmp" -> "\u56fe\u7247\u8d44\u6599"
        "mp4", "mov", "avi", "mkv", "webm" -> "\u89c6\u9891\u8d44\u6599"
        else -> "\u8d44\u6599"
    }
}

fun String.toDemoCategoryLabel(): String {
    return when (trim().lowercase(Locale.ROOT)) {
        "sop" -> "\u4f5c\u4e1a\u6307\u5bfc\u4e66"
        "work_order_attachment" -> "\u5de5\u5355\u9644\u4ef6"
        "report" -> "\u8bca\u65ad\u62a5\u544a"
        else -> ""
    }
}

fun DemoMaterial.toDisplayTitle(): String {
    return when {
        title.equals("Hydraulic pump SOP", ignoreCase = true) -> "\u6db2\u538b\u6cf5\u6807\u51c6\u4f5c\u4e1a\u6307\u5bfc\u4e66"
        title.equals("History attachments bundle", ignoreCase = true) -> "\u5386\u53f2\u5de5\u5355\u9644\u4ef6\u5305"
        title.equals("Diagnostic summary", ignoreCase = true) -> "\u8bca\u65ad\u6458\u8981\u6570\u636e"
        title.isNotBlank() -> title
        category.equals("sop", ignoreCase = true) -> "\u6807\u51c6\u4f5c\u4e1a\u8d44\u6599"
        category.equals("work_order_attachment", ignoreCase = true) -> "\u5de5\u5355\u9644\u4ef6\u5305"
        category.equals("report", ignoreCase = true) -> "\u8bca\u65ad\u6458\u8981"
        else -> "\u8d44\u6599\u9644\u4ef6"
    }
}

fun DemoSyncSummary.toStatusLine(): String {
    val parts = mutableListOf<String>()
    if (equipmentCount > 0) {
        parts.add("\u8bbe\u5907 ${equipmentCount} \u53f0")
    }
    if (workOrderCount > 0) {
        parts.add("\u5de5\u5355 ${workOrderCount} \u6761")
    }
    if (graphNodeCount > 0) {
        parts.add("\u77e5\u8bc6\u8282\u70b9 ${graphNodeCount} \u4e2a")
    }
    return parts.joinToString(" · ")
}

suspend fun fetchDemoManifest(baseUrl: String): DemoManifest {
    val normalized = normalizeDemoServerBaseUrl(baseUrl)
    check(normalized.isNotBlank()) { "\u7535\u8111\u670d\u52a1\u5730\u5740\u4e0d\u80fd\u4e3a\u7a7a" }

    val response = sendDemoJsonRequest(
        url = "$normalized/api/pocketops/bootstrap/manifest?tenantId=demo&siteId=demo&appVersion=1.0.11",
        method = "GET",
    )
    if (response.code !in 200..299) {
        throw IllegalStateException(buildDemoHttpError("获取同步清单失败", response.code, response.body))
    }
    return Gson().fromJson(response.body, DemoManifest::class.java)
}

suspend fun syncDemoResource(context: Context, resource: DemoResource): File {
    val targetFile = getDemoResourceFile(context, resource.localPath)
    val expectedSha = resource.sha256.trim().lowercase(Locale.US)

    if (targetFile.exists() && expectedSha.isNotBlank()) {
        val currentSha = sha256(targetFile)
        if (currentSha.equals(expectedSha, ignoreCase = true)) {
            return targetFile
        }
    }

    targetFile.parentFile?.mkdirs()
    val tempFile = File("${targetFile.absolutePath}.download")
    tempFile.parentFile?.mkdirs()

    val connection = URL(resource.downloadUrl).openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.connectTimeout = 10_000
    connection.readTimeout = 30_000
    connection.connect()

    try {
        if (connection.responseCode !in 200..299) {
            val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw IllegalStateException(
                buildDemoHttpError("下载 ${resource.name} 失败", connection.responseCode, errorBody),
            )
        }

        FileOutputStream(tempFile).use { output ->
            connection.inputStream.use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
            }
        }
    } finally {
        connection.disconnect()
    }

    if (resource.sizeBytes > 0 && tempFile.length() != resource.sizeBytes) {
        tempFile.delete()
        throw IllegalStateException(
            "下载 ${resource.name} 失败: 文件大小不匹配 expected=${resource.sizeBytes}, actual=${tempFile.length()}",
        )
    }

    if (expectedSha.isNotBlank()) {
        val actualSha = sha256(tempFile)
        if (!actualSha.equals(expectedSha, ignoreCase = true)) {
            tempFile.delete()
            throw IllegalStateException("下载 ${resource.name} 失败: sha256 校验未通过")
        }
    }

    if (targetFile.exists()) {
        targetFile.delete()
    }
    if (!tempFile.renameTo(targetFile)) {
        tempFile.copyTo(targetFile, overwrite = true)
        tempFile.delete()
    }

    if (resource.zip && resource.unzipDir.isNotBlank()) {
        unzipToDir(targetFile, getDemoResourceFile(context, resource.unzipDir))
    }

    return targetFile
}

fun getDemoResourceFile(context: Context, localPath: String): File {
    val safePath = localPath.replace('/', File.separatorChar)
    return File(File(context.filesDir, DEMO_SYNC_ROOT_DIR), safePath)
}

suspend fun queryDemoMaterials(baseUrl: String, message: PocketMessage): List<DemoMaterial> {
    val normalized = normalizeDemoServerBaseUrl(baseUrl)
    if (normalized.isBlank()) {
        return emptyList()
    }

    val payload = org.json.JSONObject().apply {
        put("tenantId", "demo")
        put("siteId", "demo")
        message.report?.let { report ->
            put("equipmentId", report.equipment.id)
            put("symptomId", report.symptom.id)
            put(
                "workOrderIds",
                org.json.JSONArray().apply {
                    report.workOrders.forEach { put(it.id) }
                },
            )
            put(
                "keywords",
                org.json.JSONArray().apply {
                    put(report.equipment.label)
                    put(report.symptom.label)
                    report.causes.firstOrNull()?.let { put(it.label) }
                },
            )
        } ?: run {
            put(
                "keywords",
                org.json.JSONArray().apply {
                    extractDemoKeywords(message.text).forEach { put(it) }
                },
            )
        }
    }

    val response = sendDemoJsonRequest(
        url = "$normalized/api/pocketops/materials/query",
        method = "POST",
        body = payload.toString(),
    )
    if (response.code !in 200..299) {
        throw IllegalStateException(buildDemoHttpError("获取资料列表失败", response.code, response.body))
    }
    return Gson().fromJson(response.body, DemoMaterialsResponse::class.java).materials
}

suspend fun downloadDemoMaterial(context: Context, material: DemoMaterial): File {
    val targetDir = File(context.cacheDir, DEMO_MATERIALS_DIR).apply { mkdirs() }
    val extension = guessDemoExtension(material)
    val fileName = sanitizeDemoFileName(material.title.ifBlank { material.id.ifBlank { "material" } })
    val targetFile = File(targetDir, fileName + extension)
    val expectedSha = material.sha256.trim().lowercase(Locale.US)

    if (targetFile.exists() && expectedSha.isNotBlank()) {
        val currentSha = sha256(targetFile)
        if (currentSha.equals(expectedSha, ignoreCase = true)) {
            return targetFile
        }
    }

    val tempFile = File("${targetFile.absolutePath}.download")
    val connection = URL(material.downloadUrl).openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.connectTimeout = 10_000
    connection.readTimeout = 30_000
    connection.connect()

    try {
        if (connection.responseCode !in 200..299) {
            val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw IllegalStateException(
                buildDemoHttpError("下载 ${material.title} 失败", connection.responseCode, errorBody),
            )
        }

        FileOutputStream(tempFile).use { output ->
            connection.inputStream.use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
            }
        }
    } finally {
        connection.disconnect()
    }

    if (material.sizeBytes > 0 && tempFile.length() != material.sizeBytes) {
        tempFile.delete()
        throw IllegalStateException(
            "下载 ${material.title} 失败: 文件大小不匹配 expected=${material.sizeBytes}, actual=${tempFile.length()}",
        )
    }

    if (expectedSha.isNotBlank()) {
        val actualSha = sha256(tempFile)
        if (!actualSha.equals(expectedSha, ignoreCase = true)) {
            tempFile.delete()
            throw IllegalStateException("下载 ${material.title} 失败: sha256 校验未通过")
        }
    }

    if (targetFile.exists()) {
        targetFile.delete()
    }
    if (!tempFile.renameTo(targetFile)) {
        tempFile.copyTo(targetFile, overwrite = true)
        tempFile.delete()
    }

    return targetFile
}

fun openDemoMaterial(context: Context, file: File, mimeType: String): Boolean {
    val finalMimeType = mimeType.ifBlank { guessMimeTypeFromFileName(file.name) }
    val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, finalMimeType)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    return try {
        val chooserIntent = Intent.createChooser(viewIntent, "打开资料").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooserIntent)
        true
    } catch (_: Exception) {
        false
    }
}

@Composable
fun DemoServerConfigDialog(
    currentBaseUrl: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var draftUrl by rememberSaveable(currentBaseUrl) { mutableStateOf(currentBaseUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("\u914d\u7f6e\u7535\u8111\u670d\u52a1") },
        text = {
            Column {
                Text("\u7528\u4e8e\u8fde\u63a5\u7535\u8111\u7aef\u6a21\u62df\u670d\u52a1\uff0c\u540c\u6b65\u77e5\u8bc6\u5e93\u8d44\u6599\u5e76\u4e0b\u8f7d\u9644\u4ef6\u3002")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = draftUrl,
                    onValueChange = { draftUrl = it },
                    label = { Text("\u7535\u8111\u670d\u52a1\u5730\u5740") },
                    placeholder = { Text("http://192.168.1.8:8080") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Text("\u4e0e\u7535\u8111\u5904\u4e8e\u540c\u4e00\u5c40\u57df\u7f51\u65f6\u586b\u5199\u7535\u8111\u5730\u5740\uff1b\u82e5\u5df2\u505a\u7aef\u53e3\u53cd\u5411\u4ee3\u7406\uff0c\u8bf7\u586b\u5199 http://127.0.0.1:8080")
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(normalizeDemoServerBaseUrl(draftUrl))
                    onDismiss()
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onSave("")
                    onDismiss()
                },
            ) {
                Text("清空")
            }
        },
    )
}

private fun extractDemoKeywords(text: String): List<String> {
    return text.replace('\n', ' ')
        .split(' ')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .take(4)
        .ifEmpty { listOf(text.take(40)) }
}

private fun guessDemoExtension(material: DemoMaterial): String {
    val path = runCatching { Uri.parse(material.downloadUrl).lastPathSegment.orEmpty() }.getOrDefault("")
    if (path.contains('.')) {
        return ".${path.substringAfterLast('.')}"
    }
    return when (material.mimeType.lowercase(Locale.US)) {
        "application/pdf" -> ".pdf"
        "application/zip" -> ".zip"
        "application/json" -> ".json"
        "image/png" -> ".png"
        "image/jpeg" -> ".jpg"
        "video/mp4" -> ".mp4"
        else -> ".bin"
    }
}

private fun guessMimeTypeFromFileName(fileName: String): String {
    return when (fileName.substringAfterLast('.', "").lowercase(Locale.US)) {
        "pdf" -> "application/pdf"
        "zip" -> "application/zip"
        "json" -> "application/json"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "mp4" -> "video/mp4"
        else -> "*/*"
    }
}

private fun sanitizeDemoFileName(fileName: String): String {
    return fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").trim('_').ifBlank { "material" }
}

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun unzipToDir(zipFile: File, targetDir: File) {
    targetDir.mkdirs()
    ZipInputStream(zipFile.inputStream().buffered()).use { zipInput ->
        var entry = zipInput.nextEntry
        while (entry != null) {
            val outputFile = File(targetDir, entry.name)
            if (entry.isDirectory) {
                outputFile.mkdirs()
            } else {
                outputFile.parentFile?.mkdirs()
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytesRead: Int
                    while (zipInput.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }
            zipInput.closeEntry()
            entry = zipInput.nextEntry
        }
    }
}

private fun sendDemoJsonRequest(
    url: String,
    method: String,
    body: String? = null,
): DemoHttpResponse {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.requestMethod = method
    connection.connectTimeout = 10_000
    connection.readTimeout = 30_000
    connection.setRequestProperty("Content-Type", "application/json")

    if (!body.isNullOrBlank()) {
        connection.doOutput = true
        connection.outputStream.use { output ->
            output.write(body.toByteArray(Charsets.UTF_8))
        }
    }

    return try {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        DemoHttpResponse(code = code, body = responseBody)
    } finally {
        connection.disconnect()
    }
}

private fun buildDemoHttpError(prefix: String, code: Int, body: String): String {
    val detail = body.trim().replace('\n', ' ').take(200)
    return if (detail.isBlank()) {
        "$prefix\uff08\u72b6\u6001\u7801 $code\uff09"
    } else {
        "$prefix\uff08\u72b6\u6001\u7801 $code\uff09\uff1a$detail"
    }
}
