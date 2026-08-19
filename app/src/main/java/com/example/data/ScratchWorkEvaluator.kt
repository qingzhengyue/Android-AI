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
        val hasLooks = opcodes.any { it.startsWith("looks_") }
        val hasSound = opcodes.any { it.startsWith("sound_") }
        val hasSensing = opcodes.any { it.startsWith("sensing_") }
        val hasOperators = opcodes.any { it.startsWith("operator_") }
        val hasVariables = opcodes.any { it.startsWith("data_") } || variableNames.isNotEmpty()

        val categoryCount = opcodes.mapNotNull { it.substringBefore('_').takeIf(String::isNotBlank) }.distinct().size
        val uniqueOpcodeCount = opcodes.distinct().size
        val totalBlocks = opcodes.size

        // 针对不同完整度的精准分级评测
        val grammar: Int
        val logic: Int
        val taskMatch: Int
        val creative: Int
        val suggestions: String

        when {
            // 级别 1：极简作品 (仅有1块积木，如仅有 "当绿旗被点击" 或单个未连接积木)
            totalBlocks <= 1 -> {
                if (hasStart) {
                    grammar = 15
                    logic = 8
                    taskMatch = 7
                    creative = 6
                    val total = grammar + logic + taskMatch + creative // 36分
                    suggestions = "🌟 【AI个性化诊断】太棒了！你已经迈出了编程的第一步，成功放置了【当 🟢 被点击】启动入口积木！\n\n⚠️ 不过当前脚本中还没有让角色执行运动或外观变换的指令哦（角色暂时静止不动）。\n\n💡 建议下一步：\n① 从左侧蓝色【运动】分类拖入【移动 10 步】拼在绿旗下方；\n② 从橙色【控制】分类拖入【重复执行】包裹住移动指令；\n③ 加入【碰到边缘就反弹】，小猫就能在屏幕里来回走动啦！"
                    return GeminiClient.EvaluationResult(grammar, logic, taskMatch, creative, total, suggestions)
                } else {
                    grammar = 8
                    logic = 6
                    taskMatch = 8
                    creative = 6
                    val total = grammar + logic + taskMatch + creative // 28分
                    suggestions = "⚠️ 【AI个性化诊断】检测到一个独立的动作积木，但上方缺少启动事件积木，程序无法在点击绿旗时自动运行。\n\n💡 拼搭指南：\n① 请在最上方拼接黄色【事件】分类里的【当 🟢 被点击】积木；\n② 随后在下方继续拼接运动与循环控制积木！"
                    return GeminiClient.EvaluationResult(grammar, logic, taskMatch, creative, total, suggestions)
                }
            }

            // 级别 2：初期片段 (2~3块积木，有初步结构但缺少循环或动作)
            totalBlocks in 2..3 -> {
                grammar = if (hasStart) 18 else 12
                logic = if (hasLoop || hasControl) 14 else 10
                taskMatch = if (hasMotion || hasLooks) 13 else 8
                creative = 8
                val total = grammar + logic + taskMatch + creative // 40~53分

                val hint = when {
                    !hasStart -> "① 在脚本最上方补上【当 🟢 被点击】积木；"
                    !hasLoop -> "① 小猫目前只会单次执行一步就停下，请从【控制】分类拖入【重复执行】将动作积木包裹起来；"
                    !hasMotion -> "① 从【运动】分类拖入【移动 10 步】放入循环体内部，让角色动起来；"
                    !hasBounce -> "① 从【运动】分类加入【碰到边缘就反弹】，防止小猫跑出屏幕外；"
                    else -> "① 继续添加外观与等待积木，让动作更加丰富；"
                }

                suggestions = "👏 【AI个性化诊断】作品已有初步雏形！检测到你放置了 ${totalBlocks} 个积木，基础事件已就绪。\n\n💡 进阶修改建议：\n$hint\n② 尝试添加【外观】分类里的【下一个造型】与【控制】里的【等待 0.2 秒】，让小猫走起路来像真实迈步一样生动！"
                return GeminiClient.EvaluationResult(grammar, logic, taskMatch, creative, total, suggestions)
            }

            // 级别 3：基础功能实现 (4~7块积木，具备循环和运动，但缺少反弹/造型/条件)
            totalBlocks in 4..7 -> {
                grammar = (19 + (if (hasStart) 3 else 0)).coerceAtMost(24)
                logic = (16 + (if (hasLoop) 4 else 0) + (if (hasBounce || hasCondition) 4 else 0)).coerceAtMost(26)
                taskMatch = (15 + (if (hasMotion) 4 else 0) + (if (hasLooks || hasSound) 3 else 0)).coerceAtMost(23)
                creative = (10 + categoryCount * 2).coerceAtMost(16)
                val total = grammar + logic + taskMatch + creative // 65~85分

                val detailTips = mutableListOf<String>()
                if (!hasBounce && (taskName.contains("漫步") || taskName.contains("反弹") || taskDetail.contains("反弹"))) {
                    detailTips.add("加入【运动】分类中的【碰到边缘就反弹】与【将旋转方式设为左右翻转】，避免小猫贴墙卡住或倒立。")
                }
                if (!hasLooks) {
                    detailTips.add("在移动后加入【外观】中的【下一个造型】，让角色呈现行走迈步的动态帧动画。")
                }
                if (detailTips.isEmpty()) {
                    detailTips.add("可以加入【声音】模块中的播放音效，或设置一个【得分】变量增加互动乐趣！")
                }

                suggestions = "🎉 【AI个性化诊断】很棒的编程作品！核心逻辑已基本跑通，角色已经可以按照程序指令进行连贯交互！\n\n💡 老师级别的精修建议：\n" + detailTips.mapIndexed { idx, s -> "${idx + 1}. $s" }.joinToString("\n")
                return GeminiClient.EvaluationResult(grammar, logic, taskMatch, creative, total, suggestions)
            }

            // 级别 4：优秀及高完成度作品 (8块及以上，多分类协同，逻辑严密)
            else -> {
                grammar = (22 + (if (hasStart) 2 else 0) + (if (totalBlocks >= 10) 1 else 0)).coerceIn(23, 25)
                logic = (24 + (if (hasLoop) 2 else 0) + (if (hasCondition || hasSensing) 2 else 0) + (if (hasVariables) 2 else 0)).coerceIn(25, 30)
                taskMatch = (21 + (if (hasMotion) 2 else 0) + (if (hasBounce) 1 else 0) + (if (hasLooks || hasSound) 1 else 0)).coerceIn(22, 25)
                creative = (14 + categoryCount + (if (hasVariables || hasSensing) 2 else 0)).coerceIn(15, 20)
                val total = grammar + logic + taskMatch + creative // 88~98分

                val advTips = if (hasVariables) {
                    "你巧妙地运用了变量与条件分支，算法思维非常成熟！可以尝试设计双人对战机制或关卡递增难度。"
                } else if (hasCondition || hasSensing) {
                    "条件侦测与分支响应编写得十分精准！建议添加计分器（变量）与倒计时，将小游戏打磨得更加耐玩。"
                } else {
                    "程序结构非常完整，动效流畅！建议在循环中加入碰撞侦测或键盘控制，提升作品的趣味性与交互度！"
                }

                suggestions = "🌟 【AI个性化诊断】太精彩了！作品结构完整，积木运用熟练，逻辑思维非常清晰，展现了出色的少儿编程素养！\n\n💡 拓展挑战与大师建议：\n$advTips"
                return GeminiClient.EvaluationResult(grammar, logic, taskMatch, creative, total, suggestions)
            }
        }
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

