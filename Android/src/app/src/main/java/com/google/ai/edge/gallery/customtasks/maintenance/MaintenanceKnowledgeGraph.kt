package com.google.ai.edge.gallery.customtasks.maintenance

import android.util.Log
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.util.LinkedList

private const val TAG = "AGMaintenanceKG"

class MaintenanceKnowledgeGraph {
    private val nodes = mutableMapOf<String, GraphNode>()
    private val adjacency = mutableMapOf<String, MutableList<Pair<String, GraphEdge>>>()

    fun loadFromJson(jsonString: String) {
        val moshi = Moshi.Builder().build()
        val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val adapter: JsonAdapter<Map<String, Any>> = moshi.adapter(mapType)
        val data = adapter.fromJson(jsonString) ?: return

        @Suppress("UNCHECKED_CAST")
        val rawNodes = data["nodes"] as? List<Map<String, Any>> ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val rawEdges = data["edges"] as? List<Map<String, Any>> ?: emptyList()

        nodes.clear()
        adjacency.clear()

        for (raw in rawNodes) {
            val id = raw["id"] as? String ?: continue
            val typeStr = raw["type"] as? String ?: continue
            val label = raw["label"] as? String ?: continue
            @Suppress("UNCHECKED_CAST")
            val props = (raw["properties"] as? Map<String, String>) ?: emptyMap()
            val type = try { NodeType.valueOf(typeStr) } catch (e: Exception) { continue }
            nodes[id] = GraphNode(id = id, type = type, label = label, properties = props)
        }

        for (raw in rawEdges) {
            val source = raw["source"] as? String ?: continue
            val target = raw["target"] as? String ?: continue
            val relation = raw["relation"] as? String ?: continue
            @Suppress("UNCHECKED_CAST")
            val props = (raw["properties"] as? Map<String, String>) ?: emptyMap()
            val edge = GraphEdge(source = source, target = target, relation = relation, properties = props)
            adjacency.getOrPut(source) { mutableListOf() }.add(Pair(target, edge))
            adjacency.getOrPut(target) { mutableListOf() }.add(Pair(source, edge))
        }

        Log.d(TAG, "Loaded ${nodes.size} nodes, ${rawEdges.size} edges")
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

        queue.add(Pair(startId, 0))
        visited.add(startId)
        nodes[startId]?.let { resultNodes.add(it) }

        while (queue.isNotEmpty()) {
            val (currentId, depth) = queue.poll()
            if (depth >= maxHops) continue

            val neighbors = adjacency[currentId] ?: continue
            for ((neighborId, edge) in neighbors) {
                if (neighborId in visited) continue
                visited.add(neighborId)
                val neighborNode = nodes[neighborId] ?: continue
                resultNodes.add(neighborNode)
                resultEdges.add(edge)
                queue.add(Pair(neighborId, depth + 1))
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
                node.properties.entries.forEachIndexed { j, (k, v) ->
                    if (j > 0) sb.append(",")
                    sb.append("\"${escapeJson(k)}\":\"${escapeJson(v)}\"")
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
                edge.properties.entries.forEachIndexed { j, (k, v) ->
                    if (j > 0) sb.append(",")
                    sb.append("\"${escapeJson(k)}\":\"${escapeJson(v)}\"")
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
