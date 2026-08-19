package com.example.ui

import org.json.JSONArray
import org.json.JSONObject

sealed class BlockSegment {
    data class Text(val content: String) : BlockSegment()
    data class Parameter(val value: String) : BlockSegment()
}

object BlockTextFormatter {
    
    fun formatBlock(opcode: String, blockJson: JSONObject?, blocksMap: JSONObject?): List<BlockSegment> {
        val inputs = blockJson?.optJSONObject("inputs")
        val fields = blockJson?.optJSONObject("fields")
        
        return when (opcode) {
            // --- 事件类 ---
            "event_whenflagclicked" -> listOf(BlockSegment.Text("当 绿旗 被点击"))
            "event_whenkeypressed" -> {
                val key = formatMenuOption(getFieldValue(fields, "KEY_OPTION", "space"))
                listOf(
                    BlockSegment.Text("当按下 "),
                    BlockSegment.Parameter(key),
                    BlockSegment.Text(" 键")
                )
            }
            "event_whenthisspriteclicked" -> listOf(BlockSegment.Text("当角色被点击"))
            "event_whenstageclicked" -> listOf(BlockSegment.Text("当舞台被点击"))
            "event_whenbackdropswitchesto" -> {
                val backdrop = getFieldValue(fields, "BACKDROP", "背景1")
                listOf(
                    BlockSegment.Text("当背景换成 "),
                    BlockSegment.Parameter(backdrop)
                )
            }
            "event_whenbroadcastreceived" -> {
                val msg = getFieldValue(fields, "BROADCAST_OPTION", "消息1")
                listOf(
                    BlockSegment.Text("当接收到 "),
                    BlockSegment.Parameter(msg)
                )
            }
            "event_broadcast" -> {
                val msg = getInputValue(inputs, "BROADCAST_INPUT", "消息1", blocksMap)
                listOf(
                    BlockSegment.Text("广播 "),
                    BlockSegment.Parameter(msg)
                )
            }
            "event_broadcastandwait" -> {
                val msg = getInputValue(inputs, "BROADCAST_INPUT", "消息1", blocksMap)
                listOf(
                    BlockSegment.Text("广播 "),
                    BlockSegment.Parameter(msg),
                    BlockSegment.Text(" 并等待")
                )
            }

            // --- 控制类 ---
            "control_forever" -> listOf(BlockSegment.Text("重复执行"))
            "control_repeat" -> {
                val times = getInputValue(inputs, "TIMES", "10", blocksMap)
                listOf(
                    BlockSegment.Text("重复执行 "),
                    BlockSegment.Parameter(times),
                    BlockSegment.Text(" 次")
                )
            }
            "control_if" -> {
                val conditionStr = extractCondition(inputs, blocksMap)
                listOf(
                    BlockSegment.Text("如果 "),
                    BlockSegment.Parameter(conditionStr),
                    BlockSegment.Text(" 那么")
                )
            }
            "control_if_else" -> {
                val conditionStr = extractCondition(inputs, blocksMap)
                listOf(
                    BlockSegment.Text("如果 "),
                    BlockSegment.Parameter(conditionStr),
                    BlockSegment.Text(" 那么 ... 否则")
                )
            }
            "control_wait" -> {
                val secs = getInputValue(inputs, "DURATION", "1", blocksMap)
                listOf(
                    BlockSegment.Text("等待 "),
                    BlockSegment.Parameter(secs),
                    BlockSegment.Text(" 秒")
                )
            }
            "control_wait_until" -> {
                val cond = extractCondition(inputs, blocksMap)
                listOf(
                    BlockSegment.Text("等待直到 "),
                    BlockSegment.Parameter(cond)
                )
            }
            "control_repeat_until" -> {
                val cond = extractCondition(inputs, blocksMap)
                listOf(
                    BlockSegment.Text("重复执行直到 "),
                    BlockSegment.Parameter(cond)
                )
            }
            "control_stop" -> {
                val opt = formatMenuOption(getFieldValue(fields, "STOP_OPTION", "all"))
                listOf(
                    BlockSegment.Text("停止 "),
                    BlockSegment.Parameter(opt)
                )
            }
            "control_start_as_clone" -> listOf(BlockSegment.Text("当作为克隆体启动时"))
            "control_create_clone_of" -> {
                val target = getInputValue(inputs, "CLONE_OPTION", "_myself_", blocksMap)
                listOf(
                    BlockSegment.Text("克隆 "),
                    BlockSegment.Parameter(formatMenuOption(target))
                )
            }
            "control_delete_this_clone" -> listOf(BlockSegment.Text("删除此克隆体"))

            // --- 运动类 ---
            "motion_movesteps" -> {
                val steps = getInputValue(inputs, "STEPS", "10", blocksMap)
                listOf(
                    BlockSegment.Text("移动 "),
                    BlockSegment.Parameter(steps),
                    BlockSegment.Text(" 步")
                )
            }
            "motion_turnright" -> {
                val degrees = getInputValue(inputs, "DEGREES", "15", blocksMap)
                listOf(
                    BlockSegment.Text("右转 "),
                    BlockSegment.Parameter(degrees),
                    BlockSegment.Text(" 度")
                )
            }
            "motion_turnleft" -> {
                val degrees = getInputValue(inputs, "DEGREES", "15", blocksMap)
                listOf(
                    BlockSegment.Text("左转 "),
                    BlockSegment.Parameter(degrees),
                    BlockSegment.Text(" 度")
                )
            }
            "motion_goto" -> {
                val to = getInputValue(inputs, "TO", "_random_", blocksMap)
                listOf(
                    BlockSegment.Text("移到 "),
                    BlockSegment.Parameter(formatMenuOption(to))
                )
            }
            "motion_gotoxy" -> {
                val x = getInputValue(inputs, "X", "0", blocksMap)
                val y = getInputValue(inputs, "Y", "0", blocksMap)
                listOf(
                    BlockSegment.Text("移到 x: "),
                    BlockSegment.Parameter(x),
                    BlockSegment.Text(" y: "),
                    BlockSegment.Parameter(y)
                )
            }
            "motion_glideto" -> {
                val secs = getInputValue(inputs, "SECS", "1", blocksMap)
                val to = getInputValue(inputs, "TO", "_random_", blocksMap)
                listOf(
                    BlockSegment.Text("在 "),
                    BlockSegment.Parameter(secs),
                    BlockSegment.Text(" 秒内滑行到 "),
                    BlockSegment.Parameter(formatMenuOption(to))
                )
            }
            "motion_glidesecstoxy" -> {
                val secs = getInputValue(inputs, "SECS", "1", blocksMap)
                val x = getInputValue(inputs, "X", "0", blocksMap)
                val y = getInputValue(inputs, "Y", "0", blocksMap)
                listOf(
                    BlockSegment.Text("在 "),
                    BlockSegment.Parameter(secs),
                    BlockSegment.Text(" 秒内滑行到 x: "),
                    BlockSegment.Parameter(x),
                    BlockSegment.Text(" y: "),
                    BlockSegment.Parameter(y)
                )
            }
            "motion_pointindirection" -> {
                val dir = getInputValue(inputs, "DIRECTION", "90", blocksMap)
                listOf(
                    BlockSegment.Text("面向 "),
                    BlockSegment.Parameter(dir),
                    BlockSegment.Text(" 方向")
                )
            }
            "motion_pointtowards" -> {
                val towards = getInputValue(inputs, "TOWARDS", "_mouse_", blocksMap)
                listOf(
                    BlockSegment.Text("面向 "),
                    BlockSegment.Parameter(formatMenuOption(towards))
                )
            }
            "motion_changexby" -> {
                val dx = getInputValue(inputs, "DX", "10", blocksMap)
                listOf(
                    BlockSegment.Text("将 x 坐标增加 "),
                    BlockSegment.Parameter(dx)
                )
            }
            "motion_setx" -> {
                val x = getInputValue(inputs, "X", "0", blocksMap)
                listOf(
                    BlockSegment.Text("将 x 坐标设为 "),
                    BlockSegment.Parameter(x)
                )
            }
            "motion_changeyby" -> {
                val dy = getInputValue(inputs, "DY", "10", blocksMap)
                listOf(
                    BlockSegment.Text("将 y 坐标增加 "),
                    BlockSegment.Parameter(dy)
                )
            }
            "motion_sety" -> {
                val y = getInputValue(inputs, "Y", "0", blocksMap)
                listOf(
                    BlockSegment.Text("将 y 坐标设为 "),
                    BlockSegment.Parameter(y)
                )
            }
            "motion_ifonedgebounce" -> listOf(BlockSegment.Text("碰到边缘就反弹"))
            "motion_setrotationstyle" -> {
                val style = getFieldValue(fields, "STYLE", "left-right")
                listOf(
                    BlockSegment.Text("将旋转方式设为 "),
                    BlockSegment.Parameter(formatMenuOption(style))
                )
            }
            "motion_xposition" -> listOf(BlockSegment.Text("x 坐标"))
            "motion_yposition" -> listOf(BlockSegment.Text("y 坐标"))
            "motion_direction" -> listOf(BlockSegment.Text("方向"))

            // --- 外观类 ---
            "looks_sayforsecs" -> {
                val msg = getInputValue(inputs, "MESSAGE", "你好！", blocksMap)
                val secs = getInputValue(inputs, "SECS", "2", blocksMap)
                listOf(
                    BlockSegment.Text("说 "),
                    BlockSegment.Parameter(msg),
                    BlockSegment.Text(" "),
                    BlockSegment.Parameter(secs),
                    BlockSegment.Text(" 秒")
                )
            }
            "looks_say" -> {
                val msg = getInputValue(inputs, "MESSAGE", "你好！", blocksMap)
                listOf(
                    BlockSegment.Text("说 "),
                    BlockSegment.Parameter(msg)
                )
            }
            "looks_thinkforsecs" -> {
                val msg = getInputValue(inputs, "MESSAGE", "嗯...", blocksMap)
                val secs = getInputValue(inputs, "SECS", "2", blocksMap)
                listOf(
                    BlockSegment.Text("思考 "),
                    BlockSegment.Parameter(msg),
                    BlockSegment.Text(" "),
                    BlockSegment.Parameter(secs),
                    BlockSegment.Text(" 秒")
                )
            }
            "looks_think" -> {
                val msg = getInputValue(inputs, "MESSAGE", "嗯...", blocksMap)
                listOf(
                    BlockSegment.Text("思考 "),
                    BlockSegment.Parameter(msg)
                )
            }
            "looks_switchcostumeto" -> {
                val costume = getInputValue(inputs, "COSTUME", "造型1", blocksMap)
                listOf(
                    BlockSegment.Text("换成 "),
                    BlockSegment.Parameter(costume),
                    BlockSegment.Text(" 造型")
                )
            }
            "looks_nextcostume" -> listOf(BlockSegment.Text("下一个造型"))
            "looks_switchbackdropto" -> {
                val backdrop = getInputValue(inputs, "BACKDROP", "背景1", blocksMap)
                listOf(
                    BlockSegment.Text("换成 "),
                    BlockSegment.Parameter(backdrop),
                    BlockSegment.Text(" 背景")
                )
            }
            "looks_nextbackdrop" -> listOf(BlockSegment.Text("下一个背景"))
            "looks_changesizeby" -> {
                val change = getInputValue(inputs, "CHANGE", "10", blocksMap)
                listOf(
                    BlockSegment.Text("将大小增加 "),
                    BlockSegment.Parameter(change)
                )
            }
            "looks_setsizeto" -> {
                val size = getInputValue(inputs, "SIZE", "100", blocksMap)
                listOf(
                    BlockSegment.Text("将大小设为 "),
                    BlockSegment.Parameter(size),
                    BlockSegment.Text(" %")
                )
            }
            "looks_show" -> listOf(BlockSegment.Text("显示"))
            "looks_hide" -> listOf(BlockSegment.Text("隐藏"))
            "looks_cleargraphiceffects" -> listOf(BlockSegment.Text("清除图形特效"))

            // --- 声音类 ---
            "sound_playuntildone" -> {
                val sound = getInputValue(inputs, "SOUND_MENU", "喵", blocksMap)
                listOf(
                    BlockSegment.Text("播放声音 "),
                    BlockSegment.Parameter(sound),
                    BlockSegment.Text(" 等待播完")
                )
            }
            "sound_play" -> {
                val sound = getInputValue(inputs, "SOUND_MENU", "喵", blocksMap)
                listOf(
                    BlockSegment.Text("播放声音 "),
                    BlockSegment.Parameter(sound)
                )
            }
            "sound_stopallsounds" -> listOf(BlockSegment.Text("停止所有声音"))
            "sound_changevolumeby" -> {
                val vol = getInputValue(inputs, "VOLUME", "-10", blocksMap)
                listOf(
                    BlockSegment.Text("将音量增加 "),
                    BlockSegment.Parameter(vol)
                )
            }
            "sound_setvolumeto" -> {
                val vol = getInputValue(inputs, "VOLUME", "100", blocksMap)
                listOf(
                    BlockSegment.Text("将音量设为 "),
                    BlockSegment.Parameter(vol),
                    BlockSegment.Text(" %")
                )
            }

            // --- 侦测类 ---
            "sensing_touchingobject" -> {
                val obj = getInputValue(inputs, "TOUCHINGOBJECTMENU", "_mouse_", blocksMap)
                listOf(
                    BlockSegment.Text("碰到 "),
                    BlockSegment.Parameter(formatMenuOption(obj)),
                    BlockSegment.Text(" ?")
                )
            }
            "sensing_touchingcolor" -> {
                val color = getInputValue(inputs, "COLOR", "#FF0000", blocksMap)
                listOf(
                    BlockSegment.Text("碰到颜色 "),
                    BlockSegment.Parameter(color),
                    BlockSegment.Text(" ?")
                )
            }
            "sensing_distanceto" -> {
                val obj = getInputValue(inputs, "DISTANCETOMENU", "_mouse_", blocksMap)
                listOf(
                    BlockSegment.Text("到 "),
                    BlockSegment.Parameter(formatMenuOption(obj)),
                    BlockSegment.Text(" 的距离")
                )
            }
            "sensing_askandwait" -> {
                val q = getInputValue(inputs, "QUESTION", "你叫什么名字？", blocksMap)
                listOf(
                    BlockSegment.Text("询问 "),
                    BlockSegment.Parameter(q),
                    BlockSegment.Text(" 并等待")
                )
            }
            "sensing_answer" -> listOf(BlockSegment.Text("回答"))
            "sensing_keypressed" -> {
                val key = getInputValue(inputs, "KEY_OPTION", "", blocksMap).ifEmpty {
                    getFieldValue(fields, "KEY_OPTION", "space")
                }
                listOf(
                    BlockSegment.Text("按下 "),
                    BlockSegment.Parameter(formatMenuOption(key)),
                    BlockSegment.Text(" 键?")
                )
            }
            "sensing_mousedown" -> listOf(BlockSegment.Text("按下鼠标?"))
            "sensing_mousex" -> listOf(BlockSegment.Text("鼠标的 x 坐标"))
            "sensing_mousey" -> listOf(BlockSegment.Text("鼠标的 y 坐标"))
            "sensing_timer" -> listOf(BlockSegment.Text("计时器"))
            "sensing_resettimer" -> listOf(BlockSegment.Text("重置计时器"))

            // --- 运算类 ---
            "operator_add" -> {
                val num1 = getInputValue(inputs, "NUM1", "", blocksMap)
                val num2 = getInputValue(inputs, "NUM2", "", blocksMap)
                listOf(
                    BlockSegment.Parameter(num1),
                    BlockSegment.Text(" + "),
                    BlockSegment.Parameter(num2)
                )
            }
            "operator_subtract" -> {
                val num1 = getInputValue(inputs, "NUM1", "", blocksMap)
                val num2 = getInputValue(inputs, "NUM2", "", blocksMap)
                listOf(
                    BlockSegment.Parameter(num1),
                    BlockSegment.Text(" - "),
                    BlockSegment.Parameter(num2)
                )
            }
            "operator_multiply" -> {
                val num1 = getInputValue(inputs, "NUM1", "", blocksMap)
                val num2 = getInputValue(inputs, "NUM2", "", blocksMap)
                listOf(
                    BlockSegment.Parameter(num1),
                    BlockSegment.Text(" * "),
                    BlockSegment.Parameter(num2)
                )
            }
            "operator_divide" -> {
                val num1 = getInputValue(inputs, "NUM1", "", blocksMap)
                val num2 = getInputValue(inputs, "NUM2", "", blocksMap)
                listOf(
                    BlockSegment.Parameter(num1),
                    BlockSegment.Text(" / "),
                    BlockSegment.Parameter(num2)
                )
            }
            "operator_random" -> {
                val from = getInputValue(inputs, "FROM", "1", blocksMap)
                val to = getInputValue(inputs, "TO", "10", blocksMap)
                listOf(
                    BlockSegment.Text("在 "),
                    BlockSegment.Parameter(from),
                    BlockSegment.Text(" 到 "),
                    BlockSegment.Parameter(to),
                    BlockSegment.Text(" 间随机选一个数")
                )
            }
            "operator_gt" -> {
                val op1 = getInputValue(inputs, "OPERAND1", "50", blocksMap)
                val op2 = getInputValue(inputs, "OPERAND2", "10", blocksMap)
                listOf(
                    BlockSegment.Parameter(op1),
                    BlockSegment.Text(" > "),
                    BlockSegment.Parameter(op2)
                )
            }
            "operator_lt" -> {
                val op1 = getInputValue(inputs, "OPERAND1", "10", blocksMap)
                val op2 = getInputValue(inputs, "OPERAND2", "50", blocksMap)
                listOf(
                    BlockSegment.Parameter(op1),
                    BlockSegment.Text(" < "),
                    BlockSegment.Parameter(op2)
                )
            }
            "operator_equals" -> {
                val op1 = getInputValue(inputs, "OPERAND1", "", blocksMap)
                val op2 = getInputValue(inputs, "OPERAND2", "50", blocksMap)
                listOf(
                    BlockSegment.Parameter(op1),
                    BlockSegment.Text(" = "),
                    BlockSegment.Parameter(op2)
                )
            }
            "operator_and" -> {
                val op1 = getInputValue(inputs, "OPERAND1", "<条件1>", blocksMap)
                val op2 = getInputValue(inputs, "OPERAND2", "<条件2>", blocksMap)
                listOf(
                    BlockSegment.Parameter(op1),
                    BlockSegment.Text(" 与 "),
                    BlockSegment.Parameter(op2)
                )
            }
            "operator_or" -> {
                val op1 = getInputValue(inputs, "OPERAND1", "<条件1>", blocksMap)
                val op2 = getInputValue(inputs, "OPERAND2", "<条件2>", blocksMap)
                listOf(
                    BlockSegment.Parameter(op1),
                    BlockSegment.Text(" 或 "),
                    BlockSegment.Parameter(op2)
                )
            }
            "operator_not" -> {
                val op = getInputValue(inputs, "OPERAND", "<条件>", blocksMap)
                listOf(
                    BlockSegment.Text("不成立 "),
                    BlockSegment.Parameter(op)
                )
            }
            "operator_join" -> {
                val s1 = getInputValue(inputs, "STRING1", "apple", blocksMap)
                val s2 = getInputValue(inputs, "STRING2", "banana", blocksMap)
                listOf(
                    BlockSegment.Text("连接 "),
                    BlockSegment.Parameter(s1),
                    BlockSegment.Text(" 和 "),
                    BlockSegment.Parameter(s2)
                )
            }

            // --- 变量与数据类 ---
            "data_setvariableto" -> {
                val varName = getFieldValue(fields, "VARIABLE", "我的变量")
                val value = getInputValue(inputs, "VALUE", "0", blocksMap)
                listOf(
                    BlockSegment.Text("将 "),
                    BlockSegment.Parameter(varName),
                    BlockSegment.Text(" 设为 "),
                    BlockSegment.Parameter(value)
                )
            }
            "data_changevariableby" -> {
                val varName = getFieldValue(fields, "VARIABLE", "我的变量")
                val value = getInputValue(inputs, "VALUE", "1", blocksMap)
                listOf(
                    BlockSegment.Text("将 "),
                    BlockSegment.Parameter(varName),
                    BlockSegment.Text(" 增加 "),
                    BlockSegment.Parameter(value)
                )
            }
            "data_showvariable" -> {
                val varName = getFieldValue(fields, "VARIABLE", "我的变量")
                listOf(
                    BlockSegment.Text("显示变量 "),
                    BlockSegment.Parameter(varName)
                )
            }
            "data_hidevariable" -> {
                val varName = getFieldValue(fields, "VARIABLE", "我的变量")
                listOf(
                    BlockSegment.Text("隐藏变量 "),
                    BlockSegment.Parameter(varName)
                )
            }

            // --- 菜单子积木 (如单独渲染时的友好显示) ---
            "motion_goto_menu" -> {
                val to = getFieldValue(fields, "TO", "_random_")
                listOf(
                    BlockSegment.Text("位置选项: "),
                    BlockSegment.Parameter(formatMenuOption(to))
                )
            }
            "sensing_distancetomenu" -> {
                val dist = getFieldValue(fields, "DISTANCETOMENU", "_mouse_")
                listOf(
                    BlockSegment.Text("距离目标: "),
                    BlockSegment.Parameter(formatMenuOption(dist))
                )
            }
            "sensing_touchingobjectmenu" -> {
                val obj = getFieldValue(fields, "TOUCHINGOBJECTMENU", "_mouse_")
                listOf(
                    BlockSegment.Text("碰到对象: "),
                    BlockSegment.Parameter(formatMenuOption(obj))
                )
            }

            else -> listOf(BlockSegment.Text(BlockTranslator.getChineseName(opcode)))
        }
    }

    fun extractCondition(inputs: JSONObject?, blocksMap: JSONObject?): String {
        if (inputs == null || blocksMap == null) return "<条件>"
        val conditionArr = inputs.optJSONArray("CONDITION") ?: return "<条件>"
        val condBlockId = conditionArr.optString(1)
        if (condBlockId.isEmpty()) return "<条件>"
        val condBlock = blocksMap.optJSONObject(condBlockId) ?: return "<条件>"
        
        val opcode = condBlock.optString("opcode")
        val condInputs = condBlock.optJSONObject("inputs")
        val condFields = condBlock.optJSONObject("fields")

        return when (opcode) {
            "sensing_keypressed" -> {
                val key = getInputValue(condInputs, "KEY_OPTION", "", blocksMap).ifEmpty {
                    getFieldValue(condFields, "KEY_OPTION", "space")
                }
                "按下 ${formatMenuOption(key)} 键"
            }
            "sensing_touchingobject" -> {
                val obj = getInputValue(condInputs, "TOUCHINGOBJECTMENU", "_mouse_", blocksMap)
                "碰到 ${formatMenuOption(obj)}"
            }
            "sensing_mousedown" -> "按下鼠标"
            "operator_lt" -> {
                val op1 = getInputValue(condInputs, "OPERAND1", "x", blocksMap)
                val op2 = getInputValue(condInputs, "OPERAND2", "10", blocksMap)
                "$op1 < $op2"
            }
            "operator_gt" -> {
                val op1 = getInputValue(condInputs, "OPERAND1", "x", blocksMap)
                val op2 = getInputValue(condInputs, "OPERAND2", "10", blocksMap)
                "$op1 > $op2"
            }
            "operator_equals" -> {
                val op1 = getInputValue(condInputs, "OPERAND1", "x", blocksMap)
                val op2 = getInputValue(condInputs, "OPERAND2", "10", blocksMap)
                "$op1 = $op2"
            }
            "operator_and" -> {
                val op1 = extractCondition(condInputs, blocksMap)
                val op2 = getInputValue(condInputs, "OPERAND2", "<条件2>", blocksMap)
                "$op1 与 $op2"
            }
            "operator_or" -> {
                val op1 = extractCondition(condInputs, blocksMap)
                val op2 = getInputValue(condInputs, "OPERAND2", "<条件2>", blocksMap)
                "$op1 或 $op2"
            }
            "operator_not" -> {
                val op = extractCondition(condInputs, blocksMap)
                "不成立 ($op)"
            }
            else -> BlockTranslator.getChineseName(opcode)
        }
    }

    private fun getInputValue(inputs: JSONObject?, inputName: String, defaultValue: String, blocksMap: JSONObject? = null): String {
        if (inputs == null) return defaultValue
        val input = inputs.optJSONArray(inputName) ?: return defaultValue
        val valObj = input.opt(1)
        
        if (valObj is JSONArray) {
            return valObj.optString(1, defaultValue)
        } else if (valObj is String && blocksMap != null) {
            val childBlock = blocksMap.optJSONObject(valObj)
            if (childBlock != null) {
                val opcode = childBlock.optString("opcode")
                val childFields = childBlock.optJSONObject("fields")
                val childInputs = childBlock.optJSONObject("inputs")

                // 如果是操作符积木，递归格式化出计算表达式
                when (opcode) {
                    "operator_add" -> {
                        val n1 = getInputValue(childInputs, "NUM1", "", blocksMap)
                        val n2 = getInputValue(childInputs, "NUM2", "", blocksMap)
                        return "($n1 + $n2)"
                    }
                    "operator_subtract" -> {
                        val n1 = getInputValue(childInputs, "NUM1", "", blocksMap)
                        val n2 = getInputValue(childInputs, "NUM2", "", blocksMap)
                        return "($n1 - $n2)"
                    }
                    "operator_multiply" -> {
                        val n1 = getInputValue(childInputs, "NUM1", "", blocksMap)
                        val n2 = getInputValue(childInputs, "NUM2", "", blocksMap)
                        return "($n1 * $n2)"
                    }
                    "operator_divide" -> {
                        val n1 = getInputValue(childInputs, "NUM1", "", blocksMap)
                        val n2 = getInputValue(childInputs, "NUM2", "", blocksMap)
                        return "($n1 / $n2)"
                    }
                    "operator_lt" -> {
                        val n1 = getInputValue(childInputs, "OPERAND1", "", blocksMap)
                        val n2 = getInputValue(childInputs, "OPERAND2", "", blocksMap)
                        return "$n1 < $n2"
                    }
                    "operator_gt" -> {
                        val n1 = getInputValue(childInputs, "OPERAND1", "", blocksMap)
                        val n2 = getInputValue(childInputs, "OPERAND2", "", blocksMap)
                        return "$n1 > $n2"
                    }
                    "operator_equals" -> {
                        val n1 = getInputValue(childInputs, "OPERAND1", "", blocksMap)
                        val n2 = getInputValue(childInputs, "OPERAND2", "", blocksMap)
                        return "$n1 = $n2"
                    }
                    "sensing_distanceto" -> {
                        val target = getInputValue(childInputs, "DISTANCETOMENU", "_mouse_", blocksMap)
                        return "到 ${formatMenuOption(target)} 的距离"
                    }
                }

                if (childFields != null) {
                    if (childFields.has("NUM")) return getFieldValue(childFields, "NUM", defaultValue)
                    if (childFields.has("TEXT")) return getFieldValue(childFields, "TEXT", defaultValue)
                    if (childFields.has("VALUE")) return getFieldValue(childFields, "VALUE", defaultValue)
                    if (childFields.has("VARIABLE")) return getFieldValue(childFields, "VARIABLE", defaultValue)
                    if (childFields.has("TO")) return formatMenuOption(getFieldValue(childFields, "TO", defaultValue))
                    if (childFields.has("DISTANCETOMENU")) return formatMenuOption(getFieldValue(childFields, "DISTANCETOMENU", defaultValue))
                    if (childFields.has("TOUCHINGOBJECTMENU")) return formatMenuOption(getFieldValue(childFields, "TOUCHINGOBJECTMENU", defaultValue))
                    if (childFields.has("KEY_OPTION")) return formatMenuOption(getFieldValue(childFields, "KEY_OPTION", defaultValue))
                    if (childFields.has("SOUND_MENU")) return getFieldValue(childFields, "SOUND_MENU", defaultValue)
                    if (childFields.has("COSTUME")) return getFieldValue(childFields, "COSTUME", defaultValue)
                    if (childFields.has("BACKDROP")) return getFieldValue(childFields, "BACKDROP", defaultValue)
                }
            }
        }
        return defaultValue
    }
    
    private fun getFieldValue(fields: JSONObject?, fieldName: String, defaultValue: String): String {
        if (fields == null) return defaultValue
        val field = fields.optJSONArray(fieldName) ?: return defaultValue
        return field.optString(0, defaultValue)
    }

    fun formatMenuOption(option: String): String {
        return when (option) {
            "_random_" -> "随机位置"
            "_mouse_" -> "鼠标指针"
            "_stage_" -> "舞台"
            "_myself_" -> "自己"
            "left-right" -> "左右翻转"
            "don't rotate" -> "不可旋转"
            "all around" -> "任意旋转"
            "space" -> "空格"
            "up arrow" -> "向上箭头"
            "down arrow" -> "向下箭头"
            "right arrow" -> "向右箭头"
            "left arrow" -> "向左箭头"
            "any" -> "任意"
            "all" -> "全部"
            "this script" -> "当前脚本"
            "other scripts in sprite" -> "该角色的其他脚本"
            "右移键" -> "向右箭头"
            "左移键" -> "向左箭头"
            "上移键" -> "向上箭头"
            "下移键" -> "向下箭头"
            else -> option
        }
    }
}
