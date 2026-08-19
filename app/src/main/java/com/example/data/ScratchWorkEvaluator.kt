package com.example.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 确定性与智能结合的 Scratch 3.0 项目 AST 分析与多维量化评分引擎。
 * 杜绝大模型随机幻觉高分，严格执行语法、逻辑、任务匹配与创意量化准则。
 */
object ScratchWorkEvaluator {

    data class ProjectAstInfo(
        val opcodes: List<String>,
        val variableNames: List<String>,
        val totalBlocks: Int,
        val hasFlagClicked: Boolean,
        val hasOtherEvent: Boolean,
        val hasStart: Boolean,
        val hasLoop: Boolean,
        val hasCondition: Boolean,
        val hasMotion: Boolean,
        val hasMove: Boolean,
        val hasTurn: Boolean,
        val hasBounce: Boolean,
        val hasRotationStyle: Boolean,
        val hasLooks: Boolean,
        val hasCostumeSwitch: Boolean,
        val hasWait: Boolean,
        val hasSound: Boolean,
        val hasSensing: Boolean,
        val hasOperators: Boolean,
        val hasVariables: Boolean,
        val categoryCount: Int,
        val codeSummary: String
    )

    fun analyzeProject(codeJson: String): ProjectAstInfo {
        val opcodes = mutableListOf<String>()
        val variableNames = mutableListOf<String>()
        var codeSummary = ""

        runCatching {
            val root = JSONObject(codeJson)
            codeSummary = root.optString("codeSummaryText", "")
            collectProjectInfo(root, opcodes, variableNames)
        }

        val hasFlagClicked = opcodes.any { it == "event_whenflagclicked" }
        val hasOtherEvent = opcodes.any {
            it.startsWith("event_") && it != "event_whenflagclicked"
        }
        val hasStart = hasFlagClicked || hasOtherEvent
        val hasLoop = opcodes.any { it == "control_repeat" || it == "control_forever" || it == "control_repeat_until" }
        val hasCondition = opcodes.any { it == "control_if" || it == "control_if_else" || it == "control_wait_until" }
        val hasMotion = opcodes.any { it.startsWith("motion_") }
        val hasMove = opcodes.any {
            it == "motion_movesteps" || it == "motion_gotoxy" || it == "motion_changexby" ||
            it == "motion_changeyby" || it == "motion_glidesecstoxy" || it == "motion_setx" || it == "motion_sety"
        }
        val hasTurn = opcodes.any { it == "motion_turnright" || it == "motion_turnleft" || it == "motion_pointindirection" }
        val hasBounce = opcodes.any { it == "motion_ifonedgebounce" }
        val hasRotationStyle = opcodes.any { it == "motion_setrotationstyle" }
        val hasLooks = opcodes.any { it.startsWith("looks_") }
        val hasCostumeSwitch = opcodes.any { it == "looks_nextcostume" || it == "looks_switchcostumeto" }
        val hasWait = opcodes.any { it == "control_wait" || it == "control_wait_until" }
        val hasSound = opcodes.any { it.startsWith("sound_") }
        val hasSensing = opcodes.any { it.startsWith("sensing_") }
        val hasOperators = opcodes.any { it.startsWith("operator_") }
        val hasVariables = opcodes.any { it.startsWith("data_") } || variableNames.isNotEmpty()

        val categoryCount = opcodes.mapNotNull { it.substringBefore('_').takeIf(String::isNotBlank) }.distinct().size

        return ProjectAstInfo(
            opcodes = opcodes,
            variableNames = variableNames,
            totalBlocks = opcodes.size,
            hasFlagClicked = hasFlagClicked,
            hasOtherEvent = hasOtherEvent,
            hasStart = hasStart,
            hasLoop = hasLoop,
            hasCondition = hasCondition,
            hasMotion = hasMotion,
            hasMove = hasMove,
            hasTurn = hasTurn,
            hasBounce = hasBounce,
            hasRotationStyle = hasRotationStyle,
            hasLooks = hasLooks,
            hasCostumeSwitch = hasCostumeSwitch,
            hasWait = hasWait,
            hasSound = hasSound,
            hasSensing = hasSensing,
            hasOperators = hasOperators,
            hasVariables = hasVariables,
            categoryCount = categoryCount,
            codeSummary = codeSummary
        )
    }

    fun evaluate(
        codeJson: String,
        taskName: String = "",
        taskDetail: String = "",
        workName: String = ""
    ): GeminiClient.EvaluationResult {
        val info = analyzeProject(codeJson)

        // 1. 空作品或无积木
        if (info.totalBlocks == 0) {
            return GeminiClient.EvaluationResult(
                grammarScore = 5,
                logicScore = 5,
                taskMatchScore = 5,
                creativeScore = 5,
                averageScore = 20,
                suggestions = "⚠️ 【AI诊断】检测到当前作品工作区为空，未发现任何积木代码。\n\n💡 快速起步指南：\n① 从左侧黄色【事件】分类拖出【当 🟢 被点击】积木作为启动入口；\n② 从蓝色【运动】分类拖出【移动 10 步】拼在绿旗下方；\n③ 用黄色【控制】里的【重复执行】包裹运动指令，点击绿旗让角色动起来吧！"
            )
        }

        // 2. 极简单积木作品 (仅 1 块积木)
        if (info.totalBlocks == 1) {
            if (info.hasFlagClicked) {
                // 仅有一块绿旗启动积木
                return GeminiClient.EvaluationResult(
                    grammarScore = 14,
                    logicScore = 6,
                    taskMatchScore = 5,
                    creativeScore = 5,
                    averageScore = 30,
                    suggestions = "🌟 【AI诊断】太棒了！你已经成功放置了【当 🟢 被点击】启动入口积木！\n\n⚠️ 不过当前脚本中还没有让角色执行运动或外观变换的指令哦（角色暂时静止不动）。\n\n💡 建议下一步：\n① 从左侧蓝色【运动】分类拖入【移动 10 步】拼在绿旗下方；\n② 从黄色【控制】分类拖入【重复执行】包裹住移动指令；\n③ 加入【碰到边缘就反弹】，小猫就能在屏幕里来回走动啦！"
                )
            } else {
                // 仅有一块孤立动作或控制积木，没有绿旗
                return GeminiClient.EvaluationResult(
                    grammarScore = 6,
                    logicScore = 5,
                    taskMatchScore = 6,
                    creativeScore = 5,
                    averageScore = 22,
                    suggestions = "⚠️ 【核心语法缺失】检测到当前仅有孤立的动作积木，最上方缺少【当 🟢 被点击】启动事件积木！\n在 Scratch 中，缺少启动事件会导致点击绿旗后程序无法执行。\n\n💡 拼搭指南：\n① 请先从黄色【事件】分类拖出【当 🟢 被点击】放在最上方；\n② 然后将动作积木拼接在绿旗下方！"
                )
            }
        }

        // 3. 多维度精细量化评分 (2 块及以上积木)
        // 维度 1: 语法合规性 (满分 25 分)
        var grammar = 0
        if (!info.hasStart) {
            // 致命语法缺陷：没有任何事件入口积木！严禁给高分！
            grammar = when {
                info.totalBlocks in 2..3 -> 7
                info.totalBlocks in 4..6 -> 8
                else -> 8
            }
        } else {
            // 具备起始事件入口
            grammar = if (info.hasFlagClicked) 14 else 12
            if (info.totalBlocks in 2..3) grammar += 4
            else if (info.totalBlocks in 4..6) grammar += 7
            else grammar += 10
            if (info.hasLoop) grammar += 1
        }
        grammar = grammar.coerceIn(5, 25)

        // 维度 2: 逻辑完整性 (满分 30 分)
        var logic = 0
        if (!info.hasStart) {
            // 无启动事件，逻辑无法自闭环
            logic = when {
                info.hasLoop -> 10
                info.totalBlocks >= 3 -> 8
                else -> 6
            }
        } else {
            if (info.hasLoop) logic += 12 else if (info.totalBlocks >= 3) logic += 6 else logic += 3
            if (info.hasCondition || info.hasSensing) logic += 8 else if (info.hasBounce) logic += 6 else logic += 2
            if (info.hasWait) logic += 4
            if (info.totalBlocks >= 6) logic += 6 else if (info.totalBlocks >= 4) logic += 4 else logic += 2
        }
        logic = logic.coerceIn(5, 30)

        // 维度 3: 任务匹配度 (满分 25 分)
        var taskMatch = 0
        val isWalkTask = taskName.contains("漫步") || taskName.contains("反弹") || workName.contains("漫步") || taskDetail.contains("反弹")
        val isFruitTask = taskName.contains("水果") || workName.contains("水果") || taskName.contains("接") || taskDetail.contains("接")
        val isMusicTask = taskName.contains("音") || taskName.contains("乐") || workName.contains("琴")

        if (info.hasMotion || info.hasMove) taskMatch += 8 else taskMatch += 2
        if (isWalkTask) {
            if (info.hasBounce) taskMatch += 8 else taskMatch += 2
            if (info.hasRotationStyle) taskMatch += 3
            if (info.hasLoop) taskMatch += 4
        } else if (isFruitTask) {
            if (info.hasSensing) taskMatch += 8 else taskMatch += 2
            if (info.hasCondition) taskMatch += 4
            if (info.hasMove) taskMatch += 3
        } else if (isMusicTask) {
            if (info.hasSound) taskMatch += 8 else taskMatch += 2
            if (info.hasOtherEvent || info.hasSensing) taskMatch += 5
        } else {
            if (info.hasBounce || info.hasSensing || info.hasLooks) taskMatch += 6 else taskMatch += 2
            if (info.hasLoop) taskMatch += 4
        }
        if (info.hasLooks || info.hasCostumeSwitch) taskMatch += 2
        taskMatch = taskMatch.coerceIn(5, 25)

        // 维度 4: 创意实现度 (满分 20 分)
        var creative = 0
        creative += (info.categoryCount * 2.5).toInt().coerceAtMost(8)
        if (info.hasLooks || info.hasCostumeSwitch) creative += 3
        if (info.hasSound) creative += 3
        if (info.hasVariables) creative += 3
        if (info.hasSensing || info.hasCondition) creative += 2
        if (info.totalBlocks >= 7) creative += 1
        creative = creative.coerceIn(5, 20)

        val total = grammar + logic + taskMatch + creative

        // 生成针对性个性化诊断与拼搭指导
        val suggestions = generateDiagnosticFeedback(info, taskName, taskDetail, workName, total)

        return GeminiClient.EvaluationResult(grammar, logic, taskMatch, creative, total, suggestions)
    }

    private fun generateDiagnosticFeedback(
        info: ProjectAstInfo,
        taskName: String,
        taskDetail: String,
        workName: String,
        total: Int
    ): String {
        // 核心缺陷 1: 缺少启动事件
        if (!info.hasStart) {
            return "⚠️ 【核心语法缺失】未检测到【当 🟢 被点击】启动事件积木！\n在 Scratch 编程中，缺少绿旗启动入口会导致程序无法在点击绿旗后运行。语法合规性已做严格扣分处理（当前语法得分：6分/25分）。\n\n💡 快速修复指南：\n① 从左侧黄色【事件】分类拖出【当 🟢 被点击】积木；\n② 把它紧紧拼接在当前脚本的最顶端；\n③ 随后点击舞台绿旗即可启动小猫！"
        }

        // 核心缺陷 2: 积木数量极少且无循环
        if (info.totalBlocks <= 3 && !info.hasLoop) {
            return "👏 【AI诊断】已成功放置启动积木与基础动作指令！\n⚠️ 不过当前角色只会单次执行一步便停下，尚未形成连续动画。\n\n💡 建议下一步：\n① 从黄色【控制】分类拖出【重复执行】；\n② 将移动指令包裹进【重复执行】内部；\n③ 角色就能持续在舞台上奔跑啦！"
        }

        // 根据总分与特性提供差异化指导
        return when {
            total >= 90 -> {
                val extraTip = if (info.hasVariables) "变量记分系统与状态控制运用非常成熟！" else "可以尝试添加【变量】记分器或计时器，打造更具挑战性的趣味小游戏！"
                "🌟 【AI诊断】太精彩了！作品结构规范，积木运用熟练，逻辑思维非常清晰（具备完整的事件启动、循环控制与动作动画），展现了出色的少儿编程素养！\n\n💡 拓展挑战建议：\n$extraTip"
            }
            total >= 75 -> {
                val needTips = mutableListOf<String>()
                if (!info.hasBounce && (taskName.contains("漫步") || workName.contains("漫步") || taskDetail.contains("反弹"))) {
                    needTips.add("从蓝色【运动】分类拖入【碰到边缘就反弹】，防止小猫走出舞台边缘卡住。")
                }
                if (!info.hasRotationStyle && info.hasBounce) {
                    needTips.add("在绿旗正下方加入【将旋转方式设为 左右翻转】，避免反弹时小猫倒立。")
                }
                if (!info.hasCostumeSwitch) {
                    needTips.add("在动作后加入紫色【外观】中的【下一个造型】，为角色赋予灵动的迈步动画。")
                }
                if (!info.hasWait && info.hasCostumeSwitch) {
                    needTips.add("在造型切换后加入黄色【控制】里的【等待 0.1 秒】，避免造型闪烁过快。")
                }
                if (needTips.isEmpty()) {
                    needTips.add("尝试加入按键互动或背景音效，增加视听互动体验！")
                }
                "🎉 【AI诊断】很棒的编程作品！核心逻辑已基本跑通，角色已经可以按照程序指令进行连贯交互！\n\n💡 进阶精修建议：\n" + needTips.mapIndexed { idx, s -> "${idx + 1}. $s" }.joinToString("\n")
            }
            total >= 50 -> {
                val fixTips = mutableListOf<String>()
                if (!info.hasLoop) {
                    fixTips.add("角色目前只会单次执行一步，请用黄色【控制】里的【重复执行】将动作积木包裹起来。")
                }
                if (!info.hasBounce) {
                    fixTips.add("在循环内添加蓝色【碰到边缘就反弹】，避免角色移出舞台。")
                }
                if (!info.hasCostumeSwitch) {
                    fixTips.add("添加紫色【外观】中的【下一个造型】，让小猫跑动更生动。")
                }
                "👏 【AI诊断】作品已有初步结构，基础运动指令已就位！\n\n⚠️ 待优化问题与指导：\n" + fixTips.mapIndexed { idx, s -> "${idx + 1}. $s" }.joinToString("\n")
            }
            else -> {
                "⚠️ 【AI诊断】检测到当前积木数量较少（共 ${info.totalBlocks} 块），逻辑尚未闭环。\n\n💡 起步指导：\n1. 确保最上方拼接黄色【当 🟢 被点击】；\n2. 从黄色【控制】拖入【重复执行】；\n3. 在循环内部放入蓝色【移动 10 步】和【碰到边缘就反弹】！"
            }
        }
    }

    /**
     * 校准并清洗大模型返回的评测结果，强制执行 AST 事实基准，杜绝幻觉虚高分与缺失绿旗得满分的荒谬现象。
     */
    fun calibrateWithRuleEngine(
        llmResult: GeminiClient.EvaluationResult,
        codeJson: String,
        taskName: String = "",
        taskDetail: String = "",
        workName: String = ""
    ): GeminiClient.EvaluationResult {
        val ast = analyzeProject(codeJson)
        val deterministic = evaluate(codeJson, taskName, taskDetail, workName)

        // 1. 若无积木或极简单积木，直接完全使用确定性引擎评分
        if (ast.totalBlocks <= 1) {
            return deterministic
        }

        // 2. 核心语法铁律：若没有启动事件（绿旗等），语法分必须严格受限（最高8分），总分不得超 45 分！
        var grammar = llmResult.grammarScore.coerceIn(0, 25)
        var logic = llmResult.logicScore.coerceIn(0, 30)
        var taskMatch = llmResult.taskMatchScore.coerceIn(0, 25)
        var creative = llmResult.creativeScore.coerceIn(0, 20)

        if (!ast.hasStart) {
            grammar = deterministic.grammarScore // 强制使用 AST 严格语法扣分 (6-8分)
            if (logic > 12) logic = deterministic.logicScore
            if (taskMatch > 12) taskMatch = deterministic.taskMatchScore
            if (creative > 10) creative = deterministic.creativeScore
        } else {
            // 具备起始事件时，平滑校准模型分与确定性基准分
            grammar = grammar.coerceIn((deterministic.grammarScore - 3).coerceAtLeast(5), (deterministic.grammarScore + 3).coerceAtMost(25))
            logic = logic.coerceIn((deterministic.logicScore - 4).coerceAtLeast(5), (deterministic.logicScore + 4).coerceAtMost(30))
            taskMatch = taskMatch.coerceIn((deterministic.taskMatchScore - 4).coerceAtLeast(5), (deterministic.taskMatchScore + 4).coerceAtMost(25))
            creative = creative.coerceIn((deterministic.creativeScore - 3).coerceAtLeast(5), (deterministic.creativeScore + 3).coerceAtMost(20))
        }

        val total = grammar + logic + taskMatch + creative

        var suggestions = llmResult.suggestions.trim()
        if (!ast.hasStart && !suggestions.contains("当 🟢 被点击") && !suggestions.contains("当绿旗被点击") && !suggestions.contains("启动")) {
            suggestions = "⚠️ 【核心语法缺失】未检测到【当 🟢 被点击】启动事件积木！在 Scratch 中，缺少启动积木会导致程序点击绿旗后无法执行。\n\n" + suggestions
        }
        if (suggestions.isBlank()) {
            suggestions = deterministic.suggestions
        }

        return GeminiClient.EvaluationResult(
            grammarScore = grammar,
            logicScore = logic,
            taskMatchScore = taskMatch,
            creativeScore = creative,
            averageScore = total,
            suggestions = suggestions
        )
    }

    private fun collectProjectInfo(value: Any?, opcodes: MutableList<String>, variableNames: MutableList<String>) {
        when (value) {
            is JSONObject -> value.keys().forEach { key ->
                val child = value.opt(key)
                if (key == "opcode" && child is String) opcodes += child
                if (key == "variables" && child is JSONObject) {
                    child.keys().forEach { vKey -> variableNames += vKey }
                }
                collectProjectInfo(child, opcodes, variableNames)
            }
            is JSONArray -> for (index in 0 until value.length()) collectProjectInfo(value.opt(index), opcodes, variableNames)
        }
    }
}

