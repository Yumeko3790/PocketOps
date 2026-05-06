package com.google.ai.edge.gallery.customtasks.maintenance

import android.content.Context
import androidx.compose.runtime.Composable
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.customtasks.common.CustomTask
import com.google.ai.edge.gallery.customtasks.common.CustomTaskData
import com.google.ai.edge.gallery.customtasks.common.CustomTaskDataForBuiltinTask
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.Category
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.runtime.runtimeHelper
import com.google.ai.edge.litertlm.Contents
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

private const val MAINTENANCE_SYSTEM_PROMPT = """你是一个工业设备维修诊断助手。你会收到知识图谱的检索结果作为参考信息。

请根据提供的知识图谱检索结果，用中文给出完整的诊断报告，格式如下：

## 诊断报告

### 故障原因（按概率排序）
列出每个原因及其概率。

### 所需备件
列出备件名称、规格、库存状态和价格。

### 维修步骤
按顺序列出步骤，包含预计时间和所需工具。

### 推荐维修人员
列出人员姓名、技能专长和资质。

### 相似历史工单
列出历史工单的解决方案和停机时间。

重要：所有输出必须使用中文。直接输出诊断报告，不要输出推理过程。"""

class MaintenanceTask @Inject constructor() : CustomTask {
    val knowledgeGraph = MaintenanceKnowledgeGraph()
    val maintenanceTools = MaintenanceTools(knowledgeGraph)

    override val task: Task = Task(
        id = BuiltInTaskId.INDUSTRIAL_MAINTENANCE,
        label = "工业维修助手",
        category = Category.LLM,
        iconVectorResourceId = R.drawable.agent,
        models = mutableListOf(),
        description = "基于端侧GraphRAG知识图谱的工业设备故障诊断助手，全程离线运行",
        shortDescription = "设备故障诊断",
        textInputPlaceHolderRes = R.string.text_input_placeholder_llm_chat,
        defaultSystemPrompt = MAINTENANCE_SYSTEM_PROMPT.trimIndent(),
    )

    override fun initializeModelFn(
        context: Context,
        coroutineScope: CoroutineScope,
        model: Model,
        onDone: (String) -> Unit,
    ) {
        val json = context.assets.open("maintenance/knowledge_graph.json")
            .bufferedReader().use { it.readText() }
        knowledgeGraph.loadFromJson(json)

        model.runtimeHelper.initialize(
            context = context,
            model = model,
            supportImage = model.llmSupportImage,
            supportAudio = false,
            onDone = onDone,
            systemInstruction = Contents.of(task.defaultSystemPrompt),
            coroutineScope = coroutineScope,
        )
    }

    override fun cleanUpModelFn(
        context: Context,
        coroutineScope: CoroutineScope,
        model: Model,
        onDone: () -> Unit,
    ) {
        model.runtimeHelper.cleanUp(model = model, onDone = onDone)
    }

    @Composable
    override fun MainScreen(data: Any) {
        val modelManagerViewModel: com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
        val navigateUp: () -> Unit
        when (data) {
            is CustomTaskDataForBuiltinTask -> {
                modelManagerViewModel = data.modelManagerViewModel
                navigateUp = data.onNavUp
            }
            is CustomTaskData -> {
                modelManagerViewModel = data.modelManagerViewModel
                navigateUp = {}
            }
            else -> return
        }
        MaintenanceScreen(
            task = task,
            modelManagerViewModel = modelManagerViewModel,
            navigateUp = navigateUp,
            maintenanceTools = maintenanceTools,
            knowledgeGraph = knowledgeGraph,
        )
    }
}
