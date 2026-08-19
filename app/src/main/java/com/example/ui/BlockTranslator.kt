package com.example.ui

import androidx.compose.ui.graphics.Color

object BlockTranslator {
    // 完整的 Scratch 3.0 Opcode 中文名称字典表
    private val blockNameMap = mapOf(
        // --- 运动 (Motion) ---
        "motion_movesteps" to "移动 [步数] 步",
        "motion_turnright" to "右转 [度数] 度",
        "motion_turnleft" to "左转 [度数] 度",
        "motion_goto" to "移到 [位置]",
        "motion_gotoxy" to "移到 x: [X] y: [Y]",
        "motion_glideto" to "在 [秒数] 秒内滑行到 [位置]",
        "motion_glidesecstoxy" to "在 [秒数] 秒内滑行到 x: [X] y: [Y]",
        "motion_pointindirection" to "面向 [方向] 方向",
        "motion_pointtowards" to "面向 [目标]",
        "motion_changexby" to "将 x 坐标增加 [值]",
        "motion_setx" to "将 x 坐标设为 [值]",
        "motion_changeyby" to "将 y 坐标增加 [值]",
        "motion_sety" to "将 y 坐标设为 [值]",
        "motion_ifonedgebounce" to "碰到边缘就反弹",
        "motion_setrotationstyle" to "将旋转方式设为 [模式]",
        "motion_xposition" to "x 坐标",
        "motion_yposition" to "y 坐标",
        "motion_direction" to "方向",
        "motion_goto_menu" to "位置选项",
        "motion_glideto_menu" to "滑行目标选项",
        "motion_pointtowards_menu" to "面向目标选项",

        // --- 外观 (Looks) ---
        "looks_sayforsecs" to "说 [内容] [秒数] 秒",
        "looks_say" to "说 [内容]",
        "looks_thinkforsecs" to "思考 [内容] [秒数] 秒",
        "looks_think" to "思考 [内容]",
        "looks_switchcostumeto" to "换成 [造型] 造型",
        "looks_nextcostume" to "下一个造型",
        "looks_switchbackdropto" to "换成 [背景] 背景",
        "looks_switchbackdroptoandwait" to "换成 [背景] 背景并等待",
        "looks_nextbackdrop" to "下一个背景",
        "looks_changesizeby" to "将大小增加 [值]",
        "looks_setsizeto" to "将大小设为 [值] %",
        "looks_changeeffectby" to "将 [特效] 增加 [值]",
        "looks_seteffectto" to "将 [特效] 设定为 [值]",
        "looks_cleargraphiceffects" to "清除图形特效",
        "looks_show" to "显示",
        "looks_hide" to "隐藏",
        "looks_gotofrontback" to "移到 [顶层/底层]",
        "looks_goforwardbackwardlayers" to "[前移/后移] [层数] 层",
        "looks_costumenumbername" to "造型 [编号/名称]",
        "looks_backdropnumbername" to "背景 [编号/名称]",
        "looks_size" to "大小",
        "looks_costumemenu" to "造型选项",
        "looks_backdrops" to "背景选项",

        // --- 声音 (Sound) ---
        "sound_playuntildone" to "播放声音 [音频] 等待播完",
        "sound_play" to "播放声音 [音频]",
        "sound_stopallsounds" to "停止所有声音",
        "sound_changeeffectby" to "将 [音效] 增加 [值]",
        "sound_seteffectto" to "将 [音效] 设为 [值]",
        "sound_cleareffects" to "清除音效",
        "sound_changevolumeby" to "将音量增加 [值]",
        "sound_setvolumeto" to "将音量设为 [值] %",
        "sound_volume" to "音量",
        "sound_sounds_menu" to "声音选项",

        // --- 事件 (Event) ---
        "event_whenflagclicked" to "当 绿旗 被点击",
        "event_whenkeypressed" to "当按下 [按键] 键",
        "event_whenthisspriteclicked" to "当角色被点击",
        "event_whenstageclicked" to "当舞台被点击",
        "event_whenbackdropswitchesto" to "当背景换成 [背景]",
        "event_whengreaterthan" to "当 [响度/计时器] > [值]",
        "event_whenbroadcastreceived" to "当接收到 [广播消息]",
        "event_broadcast" to "广播 [广播消息]",
        "event_broadcastandwait" to "广播 [广播消息] 并等待",
        "event_broadcast_menu" to "广播消息选项",

        // --- 控制 (Control) ---
        "control_wait" to "等待 [秒数] 秒",
        "control_repeat" to "重复执行 [次数] 次",
        "control_forever" to "重复执行",
        "control_if" to "如果 <条件> 那么",
        "control_if_else" to "如果 <条件> 那么 ... 否则 ...",
        "control_wait_until" to "等待直到 <条件>",
        "control_repeat_until" to "重复执行直到 <条件>",
        "control_stop" to "停止 [全部/此脚本/其他脚本]",
        "control_start_as_clone" to "当作为克隆体启动时",
        "control_create_clone_of" to "克隆 [对象]",
        "control_create_clone_of_menu" to "克隆目标选项",
        "control_delete_this_clone" to "删除此克隆体",

        // --- 侦测 (Sensing) ---
        "sensing_touchingobject" to "碰到 [目标]?",
        "sensing_touchingobjectmenu" to "碰到目标选项",
        "sensing_touchingcolor" to "碰到颜色 [颜色]?",
        "sensing_coloristouchingcolor" to "颜色 [颜色1] 碰到 [颜色2]?",
        "sensing_distanceto" to "到 [目标] 的距离",
        "sensing_distancetomenu" to "目标距离选项",
        "sensing_askandwait" to "询问 [问题] 并等待",
        "sensing_answer" to "回答",
        "sensing_keypressed" to "按下 [按键] 键?",
        "sensing_keyoptions" to "按键选项",
        "sensing_mousedown" to "按下鼠标?",
        "sensing_mousex" to "鼠标的 x 坐标",
        "sensing_mousey" to "鼠标的 y 坐标",
        "sensing_setdragmode" to "将拖动模式设为 [模式]",
        "sensing_loudness" to "响度",
        "sensing_timer" to "计时器",
        "sensing_resettimer" to "重置计时器",
        "sensing_of" to "[对象] 的 [属性]",
        "sensing_of_object_menu" to "对象属性选项",
        "sensing_current" to "当前的 [年/月/日/时/分/秒]",
        "sensing_dayssince2000" to "2000年以来的天数",
        "sensing_username" to "用户名",

        // --- 运算 (Operators) ---
        "operator_add" to "[数1] + [数2]",
        "operator_subtract" to "[数1] - [数2]",
        "operator_multiply" to "[数1] * [数2]",
        "operator_divide" to "[数1] / [数2]",
        "operator_random" to "在 [起始] 到 [结束] 间随机选一个数",
        "operator_gt" to "[数1] > [数2]",
        "operator_lt" to "[数1] < [数2]",
        "operator_equals" to "[数1] = [数2]",
        "operator_and" to "<条件1> 与 <条件2>",
        "operator_or" to "<条件1> 或 <条件2>",
        "operator_not" to "不成立 <条件>",
        "operator_join" to "连接 [文本1] 和 [文本2]",
        "operator_letter_of" to "[文本] 的第 [序号] 个字符",
        "operator_length" to "[文本] 的字符数",
        "operator_contains" to "[文本1] 包含 [文本2]?",
        "operator_mod" to "[数1] 除以 [数2] 的余数",
        "operator_round" to "四舍五入 [数值]",
        "operator_mathop" to "[运算] ( [数值] )",

        // --- 变量与列表 (Data) ---
        "data_variable" to "变量 [名称]",
        "data_setvariableto" to "将变量 [名称] 设为 [值]",
        "data_changevariableby" to "将变量 [名称] 增加 [值]",
        "data_showvariable" to "显示变量 [名称]",
        "data_hidevariable" to "隐藏变量 [名称]",
        "data_listcontents" to "列表 [名称]",
        "data_addtolist" to "将 [内容] 加入 [列表]",
        "data_deleteoflist" to "删除 [列表] 的第 [项数] 项",
        "data_deletealloflist" to "删除 [列表] 的全部项目",
        "data_insertatlist" to "在 [列表] 的第 [项数] 项前插入 [内容]",
        "data_replaceitemoflist" to "将 [列表] 的第 [项数] 项替换为 [内容]",
        "data_itemoflist" to "[列表] 的第 [项数] 项",
        "data_itemnumoflist" to "[列表] 中 [内容] 的编号",
        "data_lengthoflist" to "[列表] 的项目数",
        "data_listcontainsitem" to "[列表] 包含 [内容]?",
        "data_showlist" to "显示列表 [名称]",
        "data_hidelist" to "隐藏列表 [名称]",

        // --- 自定义积木 (Procedures) ---
        "procedures_definition" to "定义积木",
        "procedures_call" to "调用积木",
        "argument_reporter_string_number" to "参数 (数字/文本)",
        "argument_reporter_boolean" to "参数 (布尔)"
    )

    // 获取中文名称，如果没有精确匹配，根据前缀做友好降级显示，不再出现生硬的“未知积木”
    fun getChineseName(opcode: String): String {
        blockNameMap[opcode]?.let { return it }

        // 智能前缀降级翻译
        return when {
            opcode.startsWith("motion_") -> "运动指令: ${opcode.removePrefix("motion_")}"
            opcode.startsWith("looks_") -> "外观指令: ${opcode.removePrefix("looks_")}"
            opcode.startsWith("sound_") -> "声音指令: ${opcode.removePrefix("sound_")}"
            opcode.startsWith("event_") -> "事件指令: ${opcode.removePrefix("event_")}"
            opcode.startsWith("control_") -> "控制指令: ${opcode.removePrefix("control_")}"
            opcode.startsWith("sensing_") -> "侦测指令: ${opcode.removePrefix("sensing_")}"
            opcode.startsWith("operator_") -> "运算指令: ${opcode.removePrefix("operator_")}"
            opcode.startsWith("data_") -> "数据指令: ${opcode.removePrefix("data_")}"
            opcode.startsWith("procedures_") -> "自定义积木: ${opcode.removePrefix("procedures_")}"
            else -> opcode
        }
    }
    
    // 获取积木对应的颜色主题（严格匹配 Scratch 官方分类色体系）
    fun getBlockColor(opcode: String): Color {
        return when {
            opcode.startsWith("event_") -> Color(0xFFFFBF00)      // 事件：明黄色
            opcode.startsWith("control_") -> Color(0xFFFFAB19)    // 控制：橙色
            opcode.startsWith("motion_") -> Color(0xFF4C97FF)     // 运动：天蓝色
            opcode.startsWith("sensing_") -> Color(0xFF5CB1D6)    // 侦测：青蓝色
            opcode.startsWith("looks_") -> Color(0xFF9966FF)      // 外观：紫色
            opcode.startsWith("sound_") -> Color(0xFFCF63CF)      // 声音：粉紫色
            opcode.startsWith("operator_") -> Color(0xFF59C059)   // 运算：鲜绿色
            opcode.startsWith("data_") -> Color(0xFFFF8C1A)       // 变量/列表：深橙色
            opcode.startsWith("procedures_") -> Color(0xFFFF6680) // 自定义积木：粉红色
            else -> Color(0xFF4A90E2)
        }
    }
}
