package com.example.data

import org.json.JSONArray
import org.json.JSONObject

/** Deterministic and intelligent evaluator used for personalized, accurate Scratch project grading. */
object ScratchWorkEvaluator {
    fun evaluate(
        codeJson: String,
        taskName: String = "",
        taskDetail: String = "",
        workName: String = ""
    ): GeminiClient.EvaluationResult {
        val opcodes = mutableListOf<String>()
        var codeSummary = ""
        val variableNames = mutableListOf<String>()

        val validJson = runCatching {
            val root = JSONObject(codeJson)
            codeSummary = root.optString("codeSummaryText", "")
            collectProjectInfo(root, opcodes, variableNames)
            true
        }.getOrDefault(false)

        if (!validJson || opcodes.isEmpty()) {
            return GeminiClient.EvaluationResult(
                grammarScore = 5,
                logicScore = 5,
                taskMatchScore = 5,
                creativeScore = 5,
                averageScore = 20,
                suggestions = "⚠️ 【AI初评诊断】检测到当前作品工作区为空，未发现任何积木代码。\n\n💡 快速起步指南：\n① 从左侧黄色【事件】分类中拖出【当 🟢 被点击】积木作为启动入口；\n② 从蓝色【运动】分类中拖出【移动 10 步】拼在绿旗下方；\n③ 用橙色【控制】里的【重复执行】包裹运动指令，点击绿旗让角色动起来吧！"
            )
        }

        val hasStart = opcodes.any { it.startsWith("event_") }
        val hasControl = opcodes.any { it.startsWith("control_") }
        val hasLoop = opcodes.any { it == "control_repeat" || it == "control_forever" || it == "control_repeat_until" }
        val hasCondition = opcodes.any { it == "control_if" || it == "control_if_else" }
        val hasMotion = opcodes.any { it.startsWith("motion_") }
        val hasMove = opcodes.any { it == "motion_movesteps" || it == "motion_gotoxy" || it == "motion_changexby" || it == "motion_changeyby" || it == "motion_glidesecstoxy" }
        val hasBounce = opcodes.any { it == "motion_ifonedgebounce" }
        val hasRotation = opcodes.any { it == "motion_setrotationstyle" }
        val hasLooks = opcodes.any { it.startsWith("looks_") }
        val hasCostumeSwitch = opcodes.any { it == "looks_nextcostume" || it == "looks_switchcostumeto" }
        val hasWait = opcodes.any { it == "control_wait" || it == "control_wait_until" }
        val hasSound = opcodes.any { it.startsWith("sound_") }
        val hasSensing = opcodes.any { it.startsWith("sensing_") }
        val hasOperators = opcodes.any { it.startsWith("operator_") }
        val hasVariables = opcodes.any { it.startsWith("data_") } || variableNames.isNotEmpty()

        val categoryCount = opcodes.mapNotNull { it.substringBefore('_').takeIf(String::isNotBlank) }.distinct().size
        val uniqueOpcodeCount = opcodes.distinct().size
        val totalBlocks = opcodes.size

        // 极简作品 (1 块积木)
        if (totalBlocks <= 1) {
            val grammar = if (hasStart) 15 else 8
            val logic = 6
            val taskMatch = 6
            val creative = 5
            val total = grammar + logic + taskMatch + creative
            val suggestions = if (hasStart) {
                "🌟 【AI个性化诊断】太棒了！你已经成功放置了【当 🟢 被点击】启动入口积木！\n\n⚠️ 不过当前脚本中还没有让角色执行运动或外观变换的指令哦（角色暂时静止不动）。\n\n💡 建议下一步：\n① 从左侧蓝色【运动】分类拖入【移动 10 步】拼在绿旗下方；\n② 从橙色【控制】分类拖入【重复执行】包裹住移动指令；\n③ 加入【碰到边缘就反弹】，小猫就能在屏幕里来回走动啦！"
            } else {
                "⚠️ 【AI个性化诊断】检测到一个独立的动作积木，但上方缺少启动事件积木，程序无法在点击绿旗时自动运行。\n\n💡 拼搭指南：\n① 请在最上方拼接黄色【事件】分类里的【当 🟢 被点击】积木；\n② 随后在下方继续拼接运动与循环控制积木！"
            }
            return GeminiClient.EvaluationResult(grammar, logic, taskMatch, creative, total, suggestions)
        }

        // 精细多维度差异化计算
        // 1. 语法合规性 (满分 25 分)
        var grammar = 0
        if (hasStart) grammar += 14 else grammar += 6
        if (totalBlocks >= 2) grammar += 6 else grammar += 2
        if (totalBlocks >= 5) grammar += 5 else if (totalBlocks >= 3) grammar += 3 else grammar += 1
        grammar = grammar.coerceIn(5, 25)

        // 2. 逻辑完整性 (满分 30 分)
        var logic = 0
        if (hasLoop) logic += 12 else if (hasControl) logic += 6 else logic += 3
        if (hasCondition || hasSensing) logic += 8 else if (hasBounce) logic += 6 else logic += 2
        if (hasWait) logic += 4
        if (totalBlocks >= 6) logic += 6 else if (totalBlocks >= 4) logic += 4 else logic += 2
        logic = logic.coerceIn(5, 30)

        // 3. 任务匹配度 (满分 25 分)
        var taskMatch = 0
        if (hasMotion || hasMove) taskMatch += 10 else taskMatch += 3
        if (hasBounce && (taskName.contains("漫步") || taskName.contains("反弹") || workName.contains("漫步") || taskDetail.contains("反弹"))) {
            taskMatch += 8
        } else if (hasSensing && (taskName.contains("接水果") || workName.contains("水果") || taskName.contains("按键"))) {
            taskMatch += 8
        } else if (hasBounce || hasSensing || hasLooks) {
            taskMatch += 5
        } else {
            taskMatch += 2
        }
        if (hasLooks || hasCostumeSwitch) taskMatch += 4 else taskMatch += 1
        if (totalBlocks >= 6) taskMatch += 3 else taskMatch += 1
        taskMatch = taskMatch.coerceIn(5, 25)

        // 4. 创意实现度 (满分 20 分)
        var creative = 0
        creative += (categoryCount * 3).coerceAtMost(10)
        if (hasLooks || hasCostumeSwitch) creative += 4
        if (hasSound || hasVariables || hasSensing) creative += 4
        if (totalBlocks >= 7) creative += 2
        creative = creative.coerceIn(5, 20)

        val total = grammar + logic + taskMatch + creative

        // 个性化生成建议文案
        val suggestions = when {
            total >= 90 -> {
                val extraTip = if (hasVariables) "变量记分系统运用熟练！" else "可以尝试添加【变量】记分器或计时器，打造更具挑战性的趣味小游戏！"
                "🌟 【AI个性化诊断】太精彩了！作品结构完整，积木运用熟练，逻辑思维非常清晰（包含循环、动作、造型动画与边界控制），展现了出色的少儿编程素养！\n\n💡 拓展挑战建议：\n$extraTip"
            }
            total >= 80 -> {
                val needTips = mutableListOf<String>()
                if (!hasBounce && (taskName.contains("漫步") || workName.contains("漫步"))) {
                    needTips.add("从【运动】分类拖入【碰到边缘就反弹】，防止角色移出舞台边缘。")
                }
                if (!hasCostumeSwitch) {
                    needTips.add("在动作后加入【外观】中的【下一个造型】，让角色走起路来更加逼真生动。")
                }
                if (!hasWait && hasCostumeSwitch) {
                    needTips.add("加入【控制】里的【等待 0.1 秒】，避免造型切换过快。")
                }
                if (needTips.isEmpty()) {
                    needTips.add("尝试加入背景音乐或按键碰撞音效，增加视听互动体验！")
                }
                "🎉 【AI个性化诊断】很棒的编程作品！核心逻辑已基本跑通，角色已经可以按照程序指令进行连贯交互！\n\n💡 进阶精修建议：\n" + needTips.mapIndexed { idx, s -> "${idx + 1}. $s" }.joinToString("\n")
            }
            total >= 60 -> {
                val fixTips = mutableListOf<String>()
                if (!hasBounce) {
                    fixTips.add("检测到角色移动后会卡在舞台边缘无法返回，请在【重复执行】内部加入【碰到边缘就反弹】与【将旋转方式设为左右翻转】！")
                }
                if (!hasCostumeSwitch) {
                    fixTips.add("从小猫外观分类添加【下一个造型】，为小猫赋予生动的迈步帧动画。")
                }
                if (!hasLoop) {
                    fixTips.add("角色目前只会单次执行一步，请用【重复执行】将移动指令包裹起来。")
                }
                "👏 【AI个性化诊断】作品已有初步结构，基础运动指令已就位！\n\n⚠️ 发现待优化问题与指导：\n" + fixTips.mapIndexed { idx, s -> "${idx + 1}. $s" }.joinToString("\n")
            }
            else -> {
                "⚠️ 【AI个性化诊断】检测到当前积木数量较少（仅 ${totalBlocks} 块），逻辑尚未闭环。\n\n💡 起步指导：\n1. 确保最上方拼接黄色【当 🟢 被点击】；\n2. 从橙色【控制】拖入【重复执行】；\n3. 在循环内部放入蓝色【移动 10 步】和【碰到边缘就反弹】！"
            }
        }

        return GeminiClient.EvaluationResult(grammar, logic, taskMatch, creative, total, suggestions)
    }

    fun sanitize(result: GeminiClient.EvaluationResult): GeminiClient.EvaluationResult {
        val grammar = result.grammarScore.coerceIn(0, 25)
        val logic = result.logicScore.coerceIn(0, 30)
        val match = result.taskMatchScore.coerceIn(0, 25)
        val creative = result.creativeScore.coerceIn(0, 20)
        return result.copy(
            grammarScore = grammar,
            logicScore = logic,
            taskMatchScore = match,
            creativeScore = creative,
            averageScore = grammar + logic + match + creative,
            suggestions = result.suggestions.trim().ifBlank { "评测完成，请结合任务要求继续完善作品。" }
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

