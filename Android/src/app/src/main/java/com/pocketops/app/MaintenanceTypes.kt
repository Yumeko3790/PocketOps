package com.pocketops.app

enum class NodeType {
    EQUIPMENT,
    FAULT_SYMPTOM,
    ROOT_CAUSE,
    PART,
    REPAIR_STEP,
    PERSONNEL,
    WORK_ORDER,
}

data class GraphNode(
    val id: String,
    val type: NodeType,
    val label: String,
    val properties: Map<String, String> = emptyMap(),
)

data class GraphEdge(
    val source: String,
    val target: String,
    val relation: String,
    val properties: Map<String, String> = emptyMap(),
)

data class SubGraph(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val centerNodeId: String,
)

data class DiagnosticReport(
    val equipment: GraphNode,
    val symptom: GraphNode,
    val causes: List<GraphNode>,
    val parts: List<GraphNode>,
    val steps: List<GraphNode>,
    val personnel: List<GraphNode>,
    val workOrders: List<GraphNode>,
    val nodeCount: Int,
)
