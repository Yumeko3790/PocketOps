package com.google.ai.edge.gallery.customtasks.maintenance

import android.util.Log
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageImage
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageText
import com.google.ai.edge.gallery.ui.common.chat.ChatSide
import com.google.ai.edge.gallery.ui.common.chat.ChatView
import com.google.ai.edge.gallery.ui.common.chat.SendMessageTrigger
import com.google.ai.edge.gallery.ui.llmchat.LlmChatViewModel
import com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatusType
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel

private const val TAG = "AGMaintenanceScreen"

private data class DemoPromptChip(val label: String, val prompt: String)

private val DEMO_CHIPS = listOf(
    DemoPromptChip("3号叉车举升缓慢", "3号叉车举升缓慢"),
    DemoPromptChip("5号叉车无法启动", "5号叉车无法启动"),
    DemoPromptChip("7号叉车转向沉重", "7号叉车转向沉重"),
    DemoPromptChip("2号叉车发动机过热", "2号叉车发动机过热"),
    DemoPromptChip("9号叉车异响", "9号叉车异响"),
)

fun buildGraphRAGContext(userInput: String, graph: MaintenanceKnowledgeGraph): String? {
    val equipmentNodes = graph.findEquipment(userInput)
    if (equipmentNodes.isEmpty()) return null

    val equipment = equipmentNodes.first()
    val allSymptoms = mutableListOf<GraphNode>()
    for (keyword in listOf("举升缓慢", "无法启动", "转向沉重", "发动机过热", "异响", "液压油泄漏", "制动失灵", "门架倾斜")) {
        if (userInput.contains(keyword)) {
            allSymptoms.addAll(graph.findSymptoms(equipment.id, keyword))
        }
    }
    if (allSymptoms.isEmpty()) return null

    val symptom = allSymptoms.first()
    val subGraph = graph.traverseFromNode(symptom.id, maxHops = 4)
    val workOrders = graph.matchWorkOrders(symptom.id)
    val causes = subGraph.nodes.filter { it.type == NodeType.ROOT_CAUSE }
    val parts = subGraph.nodes.filter { it.type == NodeType.PART }
    val steps = subGraph.nodes.filter { it.type == NodeType.REPAIR_STEP }
    val personnel = subGraph.nodes.filter { it.type == NodeType.PERSONNEL }

    val sb = StringBuilder()
    sb.appendLine("━━━ 端侧GraphRAG诊断报告 ━━━")
    sb.appendLine("检索路径：设备 > 故障症状 > 根因/备件/步骤/人员/工单（4跳遍历，${subGraph.nodes.size}个节点）")
    sb.appendLine()
    sb.appendLine("[ 设备信息 ] ${equipment.label}（${equipment.properties["brand"]} ${equipment.properties["model"]}），位置：${equipment.properties["location"]}")
    sb.appendLine("[ 故障症状 ] ${symptom.label}，严重程度：${symptom.properties["severity"]}，频率：${symptom.properties["frequency"]}")
    sb.appendLine()

    sb.appendLine("[ 故障原因 - 按概率排序 ]")
    for (cause in causes.sortedByDescending { it.properties["probability"]?.toFloatOrNull() ?: 0f }) {
        val prob = ((cause.properties["probability"]?.toFloatOrNull() ?: 0f) * 100).toInt()
        sb.appendLine("  ${cause.label} - 概率 ${prob}%")
    }
    sb.appendLine()

    sb.appendLine("[ 所需备件 ]")
    for (part in parts) {
        sb.appendLine("  ${part.label}：${part.properties["spec"]}，库存${part.properties["stock"]}，${part.properties["price"]}")
    }
    sb.appendLine()

    sb.appendLine("[ 推荐维修步骤 ]")
    var stepNum = 1
    for (step in steps.sortedBy { it.properties["order"]?.toIntOrNull() ?: 99 }) {
        sb.appendLine("  ${stepNum}. ${step.label}（${step.properties["duration"]}，工具：${step.properties["tool"]}）")
        stepNum++
    }
    sb.appendLine()

    sb.appendLine("[ 推荐维修人员 ]")
    for (person in personnel) {
        sb.appendLine("  ${person.label}：${person.properties["skill"]}专家，${person.properties["cert"]}，${person.properties["experience"]}经验")
    }
    sb.appendLine()

    if (workOrders.isNotEmpty()) {
        sb.appendLine("[ 相似历史工单 ]")
        for (wo in workOrders) {
            sb.appendLine("  ${wo.label}（${wo.properties["date"]}）：${wo.properties["equipment"]}${wo.properties["fault"]}，方案：${wo.properties["resolution"]}，停机${wo.properties["downtime"]}，费用${wo.properties["cost"]}")
        }
    }

    Log.d(TAG, "GraphRAG report: ${causes.size} causes, ${parts.size} parts, ${steps.size} steps")
    return sb.toString()
}

@Composable
fun MaintenanceScreen(
    task: Task,
    modelManagerViewModel: ModelManagerViewModel,
    navigateUp: () -> Unit,
    maintenanceTools: MaintenanceTools,
    knowledgeGraph: MaintenanceKnowledgeGraph,
    viewModel: LlmChatViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val realTask = modelManagerViewModel.getTaskById(id = BuiltInTaskId.INDUSTRIAL_MAINTENANCE)!!
    var sendMessageTrigger by remember { mutableStateOf<SendMessageTrigger?>(null) }

    // Legacy local VLM path kept for the Gallery shell. The PocketOps launcher now uses
    // PocketOpsActivity + GenieAPIService HTTP as the primary production flow.

    ChatView(
        task = realTask,
        viewModel = viewModel,
        modelManagerViewModel = modelManagerViewModel,
        onSendMessage = { model, messages ->
            // Show original user message in chat
            for (message in messages) {
                viewModel.addMessage(model = model, message = message)
            }

            // Legacy image path for the Gallery shell.
            val imageMessages = messages.filterIsInstance<ChatMessageImage>()
            if (imageMessages.isNotEmpty()) {
                val userText = messages.filterIsInstance<ChatMessageText>().firstOrNull()?.content ?: "请描述这张图片中的内容"
                Log.d(TAG, "Image received, using legacy local VLM pipeline")
                modelManagerViewModel.addTextInputHistory(userText)
                viewModel.generateResponse(
                    model = model,
                    input = userText,
                    images = imageMessages.first().bitmaps,
                    onError = { errorMessage ->
                        viewModel.handleError(context = context, task = realTask, model = model, errorMessage = errorMessage, modelManagerViewModel = modelManagerViewModel)
                    },
                )
                return@ChatView
            }

            var userText = ""
            var chatMessageText: ChatMessageText? = null
            for (message in messages) {
                if (message is ChatMessageText) {
                    chatMessageText = message
                    userText = message.content
                }
            }

            if (userText.isNotEmpty() && chatMessageText != null) {
                val ragReport = buildGraphRAGContext(userText, knowledgeGraph)
                if (ragReport != null) {
                    // GraphRAG found results - show report directly, no LLM needed
                    Log.d(TAG, "GraphRAG report generated, showing directly")
                    viewModel.addMessage(
                        model = model,
                        message = ChatMessageText(content = ragReport, side = ChatSide.AGENT),
                    )
                } else {
                    // No GraphRAG match - fall back to LLM
                    Log.d(TAG, "No GraphRAG match, falling back to LLM")
                    modelManagerViewModel.addTextInputHistory(userText)
                    viewModel.generateResponse(
                        model = model,
                        input = userText,
                        onError = { errorMessage ->
                            viewModel.handleError(
                                context = context,
                                task = realTask,
                                model = model,
                                errorMessage = errorMessage,
                                modelManagerViewModel = modelManagerViewModel,
                            )
                        },
                    )
                }
            }
        },
        onRunAgainClicked = { _, _ -> },
        onBenchmarkClicked = { _, _, _, _ -> },
        onResetSessionClicked = { model ->
            viewModel.resetSession(task = realTask, model = model)
        },
        showStopButtonInInputWhenInProgress = true,
        onStopButtonClicked = { model -> viewModel.stopResponse(model = model) },
        navigateUp = navigateUp,
        showAudioPicker = true,
        showImagePicker = true,
        emptyStateComposable = { model ->
            val uiState by viewModel.uiState.collectAsState()
            val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
            val modelInitStatus = modelManagerUiState.modelInitializationStatus[model.name]

            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 48.dp)
                        .padding(bottom = 80.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "工业维修助手",
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Medium),
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                    Text(
                        "基于端侧GraphRAG知识图谱的设备故障诊断\n全程离线运行\n\n点击下方示例开始体验",
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (chip in DEMO_CHIPS) {
                        FilledTonalButton(
                            enabled = modelInitStatus?.status == ModelInitializationStatusType.INITIALIZED
                                && !uiState.isResettingSession,
                            onClick = {
                                sendMessageTrigger = SendMessageTrigger(
                                    model = model,
                                    messages = listOf(ChatMessageText(content = chip.prompt, side = ChatSide.USER)),
                                )
                            },
                        ) {
                            Icon(Icons.Outlined.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(chip.label, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
        },
        sendMessageTrigger = sendMessageTrigger,
    )
}
