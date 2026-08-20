package com.example.ui

import org.json.JSONArray
import org.json.JSONObject

/**
 * Scratch 积木语法树节点（用于真实层级嵌套渲染）
 */
sealed class ScratchBlockNode {
    abstract val id: String
    abstract val opcode: String
    abstract val blockJson: JSONObject
    abstract val stepIndex: Int?

    // 简单/动作积木
    data class Simple(
        override val id: String,
        override val opcode: String,
        override val blockJson: JSONObject,
        override val stepIndex: Int? = null
    ) : ScratchBlockNode()

    // 容器/循环/条件分支 C型积木（如 重复执行、如果那么、如果那么否则）
    data class Container(
        override val id: String,
        override val opcode: String,
        override val blockJson: JSONObject,
        val headerTitle: String,
        val children: List<ScratchBlockNode>,
        val elseChildren: List<ScratchBlockNode> = emptyList(),
        override val stepIndex: Int? = null
    ) : ScratchBlockNode()
}

/**
 * Scratch 积木树解析器：解析 JSON 中的 blocks 字典为有序的脚本链和嵌套结构
 */
object ScratchBlockTreeParser {

    private val CONTAINER_OPCODES = setOf(
        "control_forever",
        "control_repeat",
        "control_if",
        "control_if_else",
        "control_repeat_until",
        "control_while"
    )

    fun parseBlocks(blocksObj: JSONObject): List<ScratchBlockNode> {
        val blockMap = mutableMapOf<String, JSONObject>()
        val keys = blocksObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val block = blocksObj.optJSONObject(key)
            if (block != null) {
                val opcode = block.optString("opcode")
                val isShadow = block.optBoolean("shadow", false)
                val isMenu = opcode.endsWith("_menu") || opcode.endsWith("_options") || opcode == "sensing_of_object_menu"
                if (opcode.isNotEmpty() && !isShadow && !isMenu) {
                    blockMap[key] = block
                }
            }
        }

        if (blockMap.isEmpty()) return emptyList()

        // 1. 收集所有作为 SUBSTACK / SUBSTACK2 子级链的 block id
        val substackChildIds = mutableSetOf<String>()
        val nextTargetIds = mutableSetOf<String>()

        blockMap.forEach { (id, block) ->
            val nextId = block.optString("next")
            if (nextId.isNotEmpty() && nextId != "null") {
                nextTargetIds.add(nextId)
            }

            val inputs = block.optJSONObject("inputs")
            if (inputs != null) {
                val sub1 = extractSubstackRootId(inputs.opt("SUBSTACK"))
                if (sub1 != null) {
                    collectChainIds(sub1, blockMap, substackChildIds)
                }
                val sub2 = extractSubstackRootId(inputs.opt("SUBSTACK2"))
                if (sub2 != null) {
                    collectChainIds(sub2, blockMap, substackChildIds)
                }
            }
        }

        // 2. 找到顶层入口根节点（topLevel == true，或者没有 parent 且不属于任何 substack 内部）
        val rootIds = mutableListOf<String>()
        blockMap.forEach { (id, block) ->
            val isTopLevel = block.optBoolean("topLevel", false)
            val parentId = block.optString("parent")
            val hasValidParent = parentId.isNotEmpty() && parentId != "null" && blockMap.containsKey(parentId)

            if (isTopLevel || (!hasValidParent && !substackChildIds.contains(id))) {
                if (!substackChildIds.contains(id)) {
                    rootIds.add(id)
                }
            }
        }

        // 优先将事件类型积木（如绿旗点击）排在最前
        rootIds.sortWith(Comparator { id1, id2 ->
            val op1 = blockMap[id1]?.optString("opcode") ?: ""
            val op2 = blockMap[id2]?.optString("opcode") ?: ""
            val isEvent1 = op1.startsWith("event_")
            val isEvent2 = op2.startsWith("event_")
            when {
                isEvent1 && !isEvent2 -> -1
                !isEvent1 && isEvent2 -> 1
                else -> 0
            }
        })

        // 3. 构建脚本执行流与层级树
        val visited = mutableSetOf<String>()
        val resultNodes = mutableListOf<ScratchBlockNode>()

        rootIds.forEach { rootId ->
            var currentId: String? = rootId
            while (currentId != null && currentId.isNotEmpty() && currentId != "null" && !visited.contains(currentId)) {
                val block = blockMap[currentId] ?: break
                visited.add(currentId)

                val node = buildNode(currentId, block, blockMap, visited, stepIndex = null)
                resultNodes.add(node)

                currentId = block.optString("next")
            }
        }

        // 容错：如果有未被遍历到的孤立有效积木，也追加展示
        blockMap.keys.forEach { id ->
            if (!visited.contains(id) && !substackChildIds.contains(id)) {
                val block = blockMap[id] ?: return@forEach
                visited.add(id)
                val node = buildNode(id, block, blockMap, visited, stepIndex = null)
                resultNodes.add(node)
            }
        }

        return resultNodes
    }

    private fun buildNode(
        id: String,
        block: JSONObject,
        blockMap: Map<String, JSONObject>,
        visited: MutableSet<String>,
        stepIndex: Int?
    ): ScratchBlockNode {
        val opcode = block.optString("opcode")
        val inputs = block.optJSONObject("inputs")

        if (CONTAINER_OPCODES.contains(opcode)) {
            val headerTitle = when (opcode) {
                "control_forever" -> "重复执行 [无限循环] ♾"
                "control_repeat" -> {
                    val times = extractInputValue(inputs, "TIMES", "10")
                    "重复执行 $times 次"
                }
                "control_if" -> {
                    val cond = extractConditionValue(inputs, blockMap)
                    "如果 $cond 那么"
                }
                "control_if_else" -> {
                    val cond = extractConditionValue(inputs, blockMap)
                    "如果 $cond 那么"
                }
                "control_repeat_until" -> {
                    val cond = extractConditionValue(inputs, blockMap)
                    "重复执行直到 $cond"
                }
                else -> "控制结构"
            }

            // 解析 SUBSTACK 内部子积木链
            val children = mutableListOf<ScratchBlockNode>()
            val sub1Root = extractSubstackRootId(inputs?.opt("SUBSTACK"))
            var curChildId = sub1Root
            var cIndex = 1
            while (curChildId != null && curChildId.isNotEmpty() && curChildId != "null" && blockMap.containsKey(curChildId)) {
                val cBlock = blockMap[curChildId]!!
                visited.add(curChildId)
                val childNode = buildNode(curChildId, cBlock, blockMap, visited, stepIndex = cIndex)
                children.add(childNode)
                cIndex++
                curChildId = cBlock.optString("next")
            }

            // 如果有 else 分支
            val elseChildren = mutableListOf<ScratchBlockNode>()
            val sub2Root = extractSubstackRootId(inputs?.opt("SUBSTACK2"))
            var curElseId = sub2Root
            var eIndex = 1
            while (curElseId != null && curElseId.isNotEmpty() && curElseId != "null" && blockMap.containsKey(curElseId)) {
                val eBlock = blockMap[curElseId]!!
                visited.add(curElseId)
                val elseNode = buildNode(curElseId, eBlock, blockMap, visited, stepIndex = eIndex)
                elseChildren.add(elseNode)
                eIndex++
                curElseId = eBlock.optString("next")
            }

            return ScratchBlockNode.Container(
                id = id,
                opcode = opcode,
                blockJson = block,
                headerTitle = headerTitle,
                children = children,
                elseChildren = elseChildren,
                stepIndex = stepIndex
            )
        } else {
            return ScratchBlockNode.Simple(
                id = id,
                opcode = opcode,
                blockJson = block,
                stepIndex = stepIndex
            )
        }
    }

    private fun extractSubstackRootId(subVal: Any?): String? {
        if (subVal == null) return null
        if (subVal is JSONArray) {
            return if (subVal.length() >= 2) subVal.optString(1) else subVal.optString(0)
        }
        if (subVal is String && subVal.isNotEmpty() && subVal != "null") {
            return subVal
        }
        return null
    }

    private fun collectChainIds(rootId: String, blockMap: Map<String, JSONObject>, outSet: MutableSet<String>) {
        var cur: String? = rootId
        while (cur != null && cur.isNotEmpty() && cur != "null" && !outSet.contains(cur)) {
            outSet.add(cur)
            val b = blockMap[cur] ?: break
            val inp = b.optJSONObject("inputs")
            if (inp != null) {
                val s1 = extractSubstackRootId(inp.opt("SUBSTACK"))
                if (s1 != null) collectChainIds(s1, blockMap, outSet)
                val s2 = extractSubstackRootId(inp.opt("SUBSTACK2"))
                if (s2 != null) collectChainIds(s2, blockMap, outSet)
            }
            cur = b.optString("next")
        }
    }

    private fun extractInputValue(inputs: JSONObject?, key: String, defaultVal: String): String {
        if (inputs == null) return defaultVal
        val item = inputs.opt(key) ?: return defaultVal
        if (item is JSONArray) {
            val last = item.opt(item.length() - 1)
            if (last is JSONArray) {
                return last.optString(1, last.optString(0, defaultVal))
            }
            return last.toString()
        }
        return item.toString()
    }

    private fun extractConditionValue(inputs: JSONObject?, blockMap: Map<String, JSONObject>): String {
        if (inputs == null) return "条件"
        val cond = inputs.opt("CONDITION") ?: return "条件"
        if (cond is JSONArray) {
            val blockId = if (cond.length() >= 2) cond.optString(1) else cond.optString(0)
            val subBlock = blockMap[blockId]
            if (subBlock != null) {
                val op = subBlock.optString("opcode")
                return when (op) {
                    "sensing_touchingobject" -> "碰到边缘"
                    "operator_gt" -> "x > 5"
                    "operator_lt" -> "x < 5"
                    "operator_equals" -> "x = 5"
                    else -> "条件满足"
                }
            }
        }
        return "条件"
    }

    // 递归统计总积木数量
    fun countTotalBlocks(nodes: List<ScratchBlockNode>): Int {
        var count = 0
        nodes.forEach { node ->
            count += 1
            if (node is ScratchBlockNode.Container) {
                count += countTotalBlocks(node.children)
                count += countTotalBlocks(node.elseChildren)
            }
        }
        return count
    }
}
