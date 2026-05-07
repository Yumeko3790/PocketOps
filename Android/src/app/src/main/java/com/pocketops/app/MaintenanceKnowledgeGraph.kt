package com.pocketops.app

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.LinkedList

private const val TAG = "PocketOpsKG"

class MaintenanceKnowledgeGraph {
    private val nodes = mutableMapOf<String, GraphNode>()
    private val adjacency = mutableMapOf<String, MutableList<Pair<String, GraphEdge>>>()

    fun loadFromJson(jsonString: String) {
        val root = runCatching { JsonParser.parseString(jsonString).asJsonObject }
            .getOrElse { error ->
                Log.e(TAG, "Failed to parse knowledge graph JSON", error)
                return
            }
        val rawNodes = root.jsonArray("nodes")
        val rawEdges = root.jsonArray("edges")

        nodes.clear()
        adjacency.clear()

        for (element in rawNodes) {
            val raw = element.asObjectOrNull() ?: continue
            val id = raw.stringValue("id") ?: continue
            val typeName = raw.stringValue("type") ?: continue
            val label = raw.stringValue("label") ?: continue
            val type = runCatching { NodeType.valueOf(typeName) }.getOrNull() ?: continue
            nodes[id] = GraphNode(
                id = id,
                type = type,
                label = label,
                properties = raw.properties(),
            )
        }

        for (element in rawEdges) {
            val raw = element.asObjectOrNull() ?: continue
            val source = raw.stringValue("source") ?: continue
            val target = raw.stringValue("target") ?: continue
            val relation = raw.stringValue("relation") ?: continue
            val edge = GraphEdge(
                source = source,
                target = target,
                relation = relation,
                properties = raw.properties(),
            )
            adjacency.getOrPut(source) { mutableListOf() }.add(target to edge)
            adjacency.getOrPut(target) { mutableListOf() }.add(source to edge)
        }

        Log.d(TAG, "Loaded ${nodes.size} nodes, ${rawEdges.size()} edges")
    }

    fun findEquipment(query: String): List<GraphNode> {
        return nodes.values.filter { node ->
            node.type == NodeType.EQUIPMENT && (
                node.label.contains(query) ||
                    query.contains(node.label) ||
                    node.properties.values.any { it.contains(query) || query.contains(it) }
                )
        }
    }

    fun findSymptoms(equipmentId: String, symptomQuery: String): List<GraphNode> {
        val neighbors = adjacency[equipmentId] ?: return emptyList()
        return neighbors
            .filter { (targetId, edge) ->
                edge.relation == "HAS_SYMPTOM" && nodes[targetId]?.type == NodeType.FAULT_SYMPTOM
            }
            .filter { (targetId, _) ->
                val node = nodes[targetId] ?: return@filter false
                node.label.contains(symptomQuery) ||
                    symptomQuery.contains(node.label) ||
                    levenshteinDistance(node.label, symptomQuery) <= 2
            }
            .mapNotNull { (targetId, _) -> nodes[targetId] }
    }

    fun traverseFromNode(startId: String, maxHops: Int = 4): SubGraph {
        val visited = mutableSetOf<String>()
        val resultNodes = mutableListOf<GraphNode>()
        val resultEdges = mutableListOf<GraphEdge>()
        val queue: LinkedList<Pair<String, Int>> = LinkedList()

        queue.add(startId to 0)
        visited.add(startId)
        nodes[startId]?.let { resultNodes.add(it) }

        while (queue.isNotEmpty()) {
            val (currentId, depth) = queue.removeFirst()
            if (depth >= maxHops) continue

            val neighbors = adjacency[currentId] ?: continue
            for ((neighborId, edge) in neighbors) {
                if (neighborId in visited) continue
                visited.add(neighborId)
                val neighborNode = nodes[neighborId] ?: continue
                resultNodes.add(neighborNode)
                resultEdges.add(edge)
                queue.add(neighborId to depth + 1)
            }
        }

        Log.d(TAG, "Traversal from $startId: ${resultNodes.size} nodes, ${resultEdges.size} edges in $maxHops hops")
        return SubGraph(nodes = resultNodes, edges = resultEdges, centerNodeId = startId)
    }

    fun matchWorkOrders(faultId: String, maxResults: Int = 5): List<GraphNode> {
        val neighbors = adjacency[faultId] ?: return emptyList()
        return neighbors
            .filter { (targetId, edge) ->
                edge.relation == "HAS_WORK_ORDER" && nodes[targetId]?.type == NodeType.WORK_ORDER
            }
            .mapNotNull { (targetId, _) -> nodes[targetId] }
            .take(maxResults)
    }

    fun subGraphToJson(subGraph: SubGraph): String {
        val sb = StringBuilder()
        sb.append("{\"centerNodeId\":\"${subGraph.centerNodeId}\",\"nodes\":[")
        subGraph.nodes.forEachIndexed { i, node ->
            if (i > 0) sb.append(",")
            sb.append("{\"id\":\"${node.id}\",\"type\":\"${node.type.name}\",\"label\":\"${escapeJson(node.label)}\"")
            if (node.properties.isNotEmpty()) {
                sb.append(",\"properties\":{")
                node.properties.entries.forEachIndexed { j, (key, value) ->
                    if (j > 0) sb.append(",")
                    sb.append("\"${escapeJson(key)}\":\"${escapeJson(value)}\"")
                }
                sb.append("}")
            }
            sb.append("}")
        }
        sb.append("],\"edges\":[")
        subGraph.edges.forEachIndexed { i, edge ->
            if (i > 0) sb.append(",")
            sb.append("{\"source\":\"${edge.source}\",\"target\":\"${edge.target}\",\"relation\":\"${escapeJson(edge.relation)}\"")
            if (edge.properties.isNotEmpty()) {
                sb.append(",\"properties\":{")
                edge.properties.entries.forEachIndexed { j, (key, value) ->
                    if (j > 0) sb.append(",")
                    sb.append("\"${escapeJson(key)}\":\"${escapeJson(value)}\"")
                }
                sb.append("}")
            }
            sb.append("}")
        }
        sb.append("]}")
        return sb.toString()
    }

    fun getNodeById(id: String): GraphNode? = nodes[id]

    fun getNodeCount(): Int = nodes.size

    private fun JsonObject.jsonArray(name: String): JsonArray {
        val value = get(name)
        return if (value != null && value.isJsonArray) value.asJsonArray else JsonArray()
    }

    private fun JsonElement.asObjectOrNull(): JsonObject? {
        return if (isJsonObject) asJsonObject else null
    }

    private fun JsonObject.stringValue(name: String): String? {
        val value = get(name) ?: return null
        return if (!value.isJsonNull && value.isJsonPrimitive) value.asString else null
    }

    private fun JsonObject.properties(): Map<String, String> {
        val value = get("properties")
        if (value == null || !value.isJsonObject) return emptyMap()
        return value.asJsonObject.entrySet().associate { (key, propertyValue) ->
            key to propertyValue.plainString()
        }
    }

    private fun JsonElement.plainString(): String {
        return when {
            isJsonNull -> ""
            isJsonPrimitive -> asJsonPrimitive.asString
            else -> toString()
        }
    }

    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1,
                )
            }
        }
        return dp[a.length][b.length]
    }
}
