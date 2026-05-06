package com.google.ai.edge.gallery.customtasks.maintenance

import android.util.Log
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.runBlocking

private const val TAG = "AGMaintenanceTools"

class MaintenanceTools(
    private val graph: MaintenanceKnowledgeGraph,
) : ToolSet {

    private val _actionChannel = Channel<MaintenanceAction>(Channel.UNLIMITED)
    val actionChannel: ReceiveChannel<MaintenanceAction> = _actionChannel

    var resultGraphDataJson: String? = null

    @Tool(
        description = "Query the equipment fault knowledge graph with multi-hop retrieval. " +
            "Returns diagnosis context including root causes, required parts, repair steps, and recommended personnel."
    )
    fun queryFaultGraph(
        @ToolParam(description = "Equipment name or ID, e.g. '3号叉车', '合力叉车'")
        equipment: String,
        @ToolParam(description = "Fault symptom description, e.g. '举升缓慢', '无法启动'")
        symptom: String,
    ): Map<String, Any> {
        return runBlocking(Dispatchers.Default) {
            Log.d(TAG, "queryFaultGraph: equipment=$equipment, symptom=$symptom")

            _actionChannel.send(
                MaintenanceAction.GraphRetrievalProgress(
                    step = "正在检索知识图谱...",
                    inProgress = true,
                    detail = "设备: $equipment, 症状: $symptom",
                )
            )

            val equipmentNodes = graph.findEquipment(equipment)
            if (equipmentNodes.isEmpty()) {
                _actionChannel.send(
                    MaintenanceAction.GraphRetrievalProgress(
                        step = "未找到匹配设备",
                        inProgress = false,
                    )
                )
                return@runBlocking mapOf(
                    "status" to "not_found",
                    "message" to "未找到匹配的设备: $equipment",
                )
            }

            val equipmentNode = equipmentNodes.first()
            Log.d(TAG, "Found equipment: ${equipmentNode.label} (${equipmentNode.id})")

            val symptomNodes = graph.findSymptoms(equipmentNode.id, symptom)
            if (symptomNodes.isEmpty()) {
                _actionChannel.send(
                    MaintenanceAction.GraphRetrievalProgress(
                        step = "未找到匹配症状",
                        inProgress = false,
                    )
                )
                return@runBlocking mapOf(
                    "status" to "not_found",
                    "message" to "设备 ${equipmentNode.label} 未找到症状: $symptom",
                )
            }

            val symptomNode = symptomNodes.first()
            Log.d(TAG, "Found symptom: ${symptomNode.label} (${symptomNode.id})")

            _actionChannel.send(
                MaintenanceAction.GraphRetrievalProgress(
                    step = "正在进行4跳图谱遍历...",
                    inProgress = true,
                    detail = "从 ${symptomNode.label} 出发",
                )
            )

            val subGraph = graph.traverseFromNode(symptomNode.id, maxHops = 4)

            val causes = subGraph.nodes.filter { it.type == NodeType.ROOT_CAUSE }
            val parts = subGraph.nodes.filter { it.type == NodeType.PART }
            val steps = subGraph.nodes.filter { it.type == NodeType.REPAIR_STEP }
            val personnel = subGraph.nodes.filter { it.type == NodeType.PERSONNEL }
            val workOrders = subGraph.nodes.filter { it.type == NodeType.WORK_ORDER }

            _actionChannel.send(
                MaintenanceAction.GraphRetrievalProgress(
                    step = "知识图谱检索完成",
                    inProgress = false,
                    detail = "找到 ${causes.size} 个原因, ${parts.size} 个备件, ${steps.size} 个步骤",
                )
            )

            val graphJson = graph.subGraphToJson(subGraph)

            mapOf(
                "status" to "success",
                "equipment" to mapOf(
                    "id" to equipmentNode.id,
                    "name" to equipmentNode.label,
                    "brand" to (equipmentNode.properties["brand"] ?: ""),
                    "model" to (equipmentNode.properties["model"] ?: ""),
                ),
                "symptom" to mapOf(
                    "id" to symptomNode.id,
                    "name" to symptomNode.label,
                    "severity" to (symptomNode.properties["severity"] ?: ""),
                ),
                "root_causes" to causes.map { cause ->
                    mapOf(
                        "id" to cause.id,
                        "name" to cause.label,
                        "probability" to (cause.properties["probability"] ?: "0"),
                    )
                },
                "required_parts" to parts.map { part ->
                    mapOf(
                        "name" to part.label,
                        "spec" to (part.properties["spec"] ?: ""),
                        "stock" to (part.properties["stock"] ?: ""),
                        "price" to (part.properties["price"] ?: ""),
                    )
                },
                "repair_steps" to steps.sortedBy {
                    it.properties["order"]?.toIntOrNull() ?: 99
                }.map { step ->
                    mapOf(
                        "name" to step.label,
                        "duration" to (step.properties["duration"] ?: ""),
                        "tool" to (step.properties["tool"] ?: ""),
                    )
                },
                "recommended_personnel" to personnel.map { person ->
                    mapOf(
                        "name" to person.label,
                        "skill" to (person.properties["skill"] ?: ""),
                        "cert" to (person.properties["cert"] ?: ""),
                        "experience" to (person.properties["experience"] ?: ""),
                    )
                },
                "graph_data" to graphJson,
                "traversal_stats" to mapOf(
                    "total_nodes" to subGraph.nodes.size,
                    "total_edges" to subGraph.edges.size,
                    "hops" to 4,
                ),
            )
        }
    }

    @Tool(description = "Query historical work orders matching a specific fault symptom")
    fun queryWorkOrders(
        @ToolParam(description = "Fault symptom ID from queryFaultGraph result, e.g. 'sym_001'")
        faultId: String,
    ): Map<String, Any> {
        return runBlocking(Dispatchers.Default) {
            Log.d(TAG, "queryWorkOrders: faultId=$faultId")

            _actionChannel.send(
                MaintenanceAction.GraphRetrievalProgress(
                    step = "正在匹配历史工单...",
                    inProgress = true,
                )
            )

            val workOrders = graph.matchWorkOrders(faultId)

            _actionChannel.send(
                MaintenanceAction.GraphRetrievalProgress(
                    step = "历史工单匹配完成",
                    inProgress = false,
                    detail = "找到 ${workOrders.size} 条相关工单",
                )
            )

            if (workOrders.isEmpty()) {
                return@runBlocking mapOf(
                    "status" to "no_records",
                    "message" to "未找到相关历史工单",
                )
            }

            mapOf(
                "status" to "success",
                "work_orders" to workOrders.map { wo ->
                    mapOf(
                        "id" to wo.label,
                        "date" to (wo.properties["date"] ?: ""),
                        "equipment" to (wo.properties["equipment"] ?: ""),
                        "fault" to (wo.properties["fault"] ?: ""),
                        "resolution" to (wo.properties["resolution"] ?: ""),
                        "downtime" to (wo.properties["downtime"] ?: ""),
                        "cost" to (wo.properties["cost"] ?: ""),
                    )
                },
                "total_count" to workOrders.size,
            )
        }
    }

    @Tool(description = "Render the retrieved knowledge subgraph as an interactive force-directed graph visualization")
    fun renderGraph(
        @ToolParam(description = "The subgraph data JSON string from queryFaultGraph result's graph_data field")
        graphData: String,
    ): Map<String, Any> {
        return runBlocking(Dispatchers.Default) {
            Log.d(TAG, "renderGraph called, data length=${graphData.length}")

            _actionChannel.send(
                MaintenanceAction.GraphRetrievalProgress(
                    step = "正在渲染知识图谱...",
                    inProgress = true,
                )
            )

            resultGraphDataJson = graphData

            val deferred = CompletableDeferred<String>()
            _actionChannel.send(
                MaintenanceAction.RenderGraph(
                    graphDataJson = graphData,
                    result = deferred,
                )
            )

            val result = deferred.await()

            _actionChannel.send(
                MaintenanceAction.GraphRetrievalProgress(
                    step = "知识图谱渲染完成",
                    inProgress = false,
                )
            )

            mapOf(
                "status" to "success",
                "message" to "知识图谱可视化已生成",
                "render_result" to result,
            )
        }
    }

    fun sendAction(action: MaintenanceAction) {
        runBlocking(Dispatchers.Default) { _actionChannel.send(action) }
    }
}
