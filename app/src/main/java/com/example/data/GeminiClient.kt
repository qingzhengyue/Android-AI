package com.example.data

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    private val mediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generateContent(prompt: String, hasNetwork: Boolean = true): String = withContext(Dispatchers.IO) {
        if (!hasNetwork) {
            return@withContext getSmartScratchAnswer(prompt)
        }

        val apiKey = BuildConfig.GEMINI_API_KEY.trim()
        if (apiKey == "MY_GEMINI_API_KEY" || apiKey.isEmpty()) {
            return@withContext getSmartScratchAnswer(prompt)
        }

        val isSparkMaaS = apiKey.startsWith("dae06") || apiKey.contains(":")
        val isCSK = apiKey.startsWith("csk-")
        val isQwen = apiKey.startsWith("sk-") && !isCSK // 默认通义千问
        val isOpenAICompatible = isQwen || isSparkMaaS || isCSK
        var attempts = 0
        val maxAttempts = 1
        var lastErrCode = 0
        var lastErrMsg = ""

        while (attempts <= maxAttempts) {
            try {
                val request = if (isOpenAICompatible) {
                    val requestBodyJson = JSONObject()
                    val modelName = when {
                        isSparkMaaS -> "xopqwen36v35b"
                        isCSK -> "llama3.1-8b"
                        else -> "qwen-plus"
                    }
                    requestBodyJson.put("model", modelName)
                    val messagesArray = JSONArray()
                    val messageObj = JSONObject()
                    messageObj.put("role", "user")
                    messageObj.put("content", prompt)
                    messagesArray.put(messageObj)
                    requestBodyJson.put("messages", messagesArray)

                    val targetUrl = when {
                        isSparkMaaS -> "https://maas-api.cn-huabei-1.xf-yun.com/v2/chat/completions"
                        isCSK -> "https://api.cerebras.ai/v1/chat/completions"
                        else -> "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
                    }

                    Request.Builder()
                        .url(targetUrl)
                        .addHeader("Authorization", "Bearer $apiKey")
                        .addHeader("Content-Type", "application/json")
                        .post(requestBodyJson.toString().toRequestBody(mediaType))
                        .build()
                } else {
                    val requestBodyJson = JSONObject()
                    val contentsArray = JSONArray()
                    val contentObj = JSONObject()
                    val partsArray = JSONArray()
                    val partObj = JSONObject()

                    partObj.put("text", prompt)
                    partsArray.put(partObj)
                    contentObj.put("parts", partsArray)
                    contentsArray.put(contentObj)
                    requestBodyJson.put("contents", contentsArray)

                    val generationConfig = JSONObject()
                    generationConfig.put("temperature", 0.2)
                    requestBodyJson.put("generationConfig", generationConfig)

                    Request.Builder()
                        .url("$BASE_URL?key=$apiKey")
                        .post(requestBodyJson.toString().toRequestBody(mediaType))
                        .build()
                }

                client.newCall(request).execute().use { response ->
                    val code = response.code
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: return@withContext "无返回结果"
                        val responseJson = JSONObject(responseBody)
                        
                        if (isOpenAICompatible) {
                            val choices = responseJson.optJSONArray("choices")
                            if (choices != null && choices.length() > 0) {
                                val firstChoice = choices.getJSONObject(0)
                                val message = firstChoice.optJSONObject("message")
                                if (message != null) {
                                    val aiContent = message.optString("content")
                                    if (aiContent.isNotBlank()) return@withContext aiContent
                                }
                            }
                        } else {
                            val candidates = responseJson.optJSONArray("candidates")
                            if (candidates != null && candidates.length() > 0) {
                                val firstCandidate = candidates.getJSONObject(0)
                                val content = firstCandidate.optJSONObject("content")
                                if (content != null) {
                                    val parts = content.optJSONArray("parts")
                                    if (parts != null && parts.length() > 0) {
                                        val text = parts.getJSONObject(0).optString("text")
                                        if (text.isNotBlank()) return@withContext text
                                    }
                                }
                            }
                        }
                    } else {
                        val errorMsg = response.body?.string() ?: ""
                        lastErrCode = code
                        lastErrMsg = errorMsg

                        if (code == 503 || code == 500 || code == 502 || code == 429) {
                            if (attempts < maxAttempts) {
                                attempts++
                                val delayTime = when (attempts) {
                                    1 -> 1000L
                                    2 -> 2000L
                                    else -> 4000L
                                }
                                kotlinx.coroutines.delay(delayTime)
                                continue
                            }
                        }
                        break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (attempts < maxAttempts) {
                    attempts++
                    val delayTime = when (attempts) {
                        1 -> 1000L
                        2 -> 2000L
                        else -> 4000L
                    }
                    try { kotlinx.coroutines.delay(delayTime) } catch(ie: Exception){}
                    continue
                }
                break
            }
        }

        // Failsafe offline/fallback smart Scratch QA generator
        return@withContext getSmartScratchAnswer(prompt)
    }

    /**
     * 少儿 Scratch 领域离线/故障兜底高智能问答求解器
     * 涵盖：三大结构对比、面向对象 OOP、专业编程语言跨界科普、算法思维、Scratch 核心拼搭
     * 严格遵循多意图路由系统：彻底消除“无脑拖绿旗”与“关于你问的”机械套话！
     */
    private fun getSmartScratchAnswer(prompt: String): String {
        // 精确提取学生的核心提问文本或主题
        val cleanQuestion = when {
            prompt.contains("【学生当前提问】") -> prompt.substringAfter("【学生当前提问】").substringBefore("\n【当前作品代码").substringBefore("\n").trim().removePrefix(":").removePrefix("：").trim()
            prompt.contains("希望创作的主题是：【") -> prompt.substringAfter("希望创作的主题是：【").substringBefore("】").trim()
            prompt.contains("核心知识点/考点：【") -> prompt.substringAfter("核心知识点/考点：【").substringBefore("】").trim()
            prompt.contains("小朋友问：“") -> prompt.substringAfter("小朋友问：“").substringBefore("”").trim()
            prompt.contains("“") && prompt.contains("”") -> prompt.substringAfter("“").substringBefore("”").trim()
            else -> prompt.substringBefore("\n")
        }.trim()

        val q = cleanQuestion.lowercase()

        // 提取纯净的主题或关键词（去除“考点解析:”、“创意探索:”等前缀及装饰图标）
        val rawTopic = cleanQuestion
            .removePrefix("考点解析:")
            .removePrefix("考点解析：")
            .removePrefix("考点讲解:")
            .removePrefix("考点讲解：")
            .removePrefix("知识点讲解:")
            .removePrefix("知识点讲解：")
            .removePrefix("知识点:")
            .removePrefix("知识点：")
            .removePrefix("创意探索:")
            .removePrefix("创意探索：")
            .removePrefix("创意引导:")
            .removePrefix("创意引导：")
            .removePrefix("【考点解析】")
            .removePrefix("【考点讲解】")
            .removePrefix("【创意探索】")
            .removePrefix("【创意引导】")
            .replace(Regex("^[🔄📦✉️📐⚡👥🐱🏎️🚀🎨🍎💡🎓\\s]+"), "")
            .trim()

        val isExpert = prompt.contains("专家")

        return when {
            // =========================================================================
            // 0. 智能精灵核心功能 1：语法纠错 / 积木体检
            // =========================================================================
            prompt.contains("语法纠错") || prompt.contains("语法错误") || prompt.contains("语法分析") || q.contains("语法纠错") || q.contains("检测语法") -> {
                val hasFlag = prompt.contains("whenflagclicked") || prompt.contains("event_whenflagclicked")
                val hasMove = prompt.contains("motion_") || prompt.contains("move") || prompt.contains("移动") || prompt.contains("右转") || prompt.contains("左转")
                val hasLooks = prompt.contains("looks_") || prompt.contains("say") || prompt.contains("说")
                val hasLoop = prompt.contains("control_repeat") || prompt.contains("control_forever") || prompt.contains("重复执行")
                val hasBounce = prompt.contains("ifonedgebounce") || prompt.contains("碰到边缘就反弹")
                val hasCondition = prompt.contains("control_if") || prompt.contains("如果")
                val hasVar = prompt.contains("data_") || prompt.contains("变量") || prompt.contains("得分") || prompt.contains("x")
                val isWorkspaceEmpty = !hasFlag && !hasMove && !hasLooks && !hasLoop && !hasCondition && !hasVar

                if (isWorkspaceEmpty) {
                    "🐱【 智能精灵姐姐 · 积木语法与逻辑体检报告 】✨\n\n" +
                    "🌟 **起点提示**：当前工作区还没有放置积木哦！\n\n" +
                    "【错误提示】: 脚本区处于空白状态，没有启动积木或动作指令。\n\n" +
                    "【修正建议】: \n" +
                    "① 第一步：点击左侧黄色【事件】分类，拖出【当 🟢 被点击】积木放到工作区；\n" +
                    "② 第二步：点击蓝色【运动】分类，拖出【移动 10 步】拼在绿旗下方；\n" +
                    "③ 第三步：点击黄色【控制】分类，拖出【重复执行】包裹住移动积木，点击绿旗让角色跑起来吧！🐾"
                } else {
                    val praiseSection = when {
                        hasFlag && hasLoop && hasCondition -> "🌟 **超级表扬**：太棒啦！你的代码结构非常规范，包含了【当 🟢 被点击】启动积木、循环结构以及条件判断，逻辑层次非常清晰！\n\n"
                        hasFlag && hasLoop -> "🌟 **超级表扬**：太棒啦！你的代码已经正确放置了【当 🟢 被点击】启动积木，并且使用了循环结构让角色持续运行！\n\n"
                        hasFlag -> "🌟 **超级表扬**：太棒啦！你的代码已经正确放置了【当 🟢 被点击】启动积木，程序有了清晰的起点！\n\n"
                        else -> "🌟 **起点提示**：建议在最顶层放一块黄色【事件】里的【当 🟢 被点击】积木，作为整个魔法脚本的启动指令哦！\n\n"
                    }

                    val errorSection = when {
                        hasMove && hasLoop && !hasBounce -> "【错误提示】: 角色在【重复执行】中不断移动，但缺少【碰到边缘就反弹】积木，小猫可能会直接跑出舞台边缘消失不见哦！"
                        hasLoop && !hasMove && !hasLooks -> "【错误提示】: 你的【重复执行】循环体内部好像没有放置具体的运动或外观积木，小猫在循环中没有具体动作可以执行哦！"
                        !hasLoop && hasMove -> "【错误提示】: 当前画布中缺少循环控制积木，点击绿旗后角色只会执行一次移动就停下来了，无法持续行动。"
                        hasCondition && !hasLoop -> "【错误提示】: 你的【如果...那么】条件判断积木只在绿旗点击瞬间检测一次，建议放入【重复执行】中进行持续侦测！"
                        else -> "【诊断分析】: 当前积木语法结构基本正常，逻辑连接顺畅。可以继续尝试丰富角色的外观造型切换或加入音效反馈！"
                    }

                    val adviceSection = when {
                        hasMove && !hasBounce -> "【修正建议】: \n" +
                            "① 点击左侧蓝色【运动】分类；\n" +
                            "② 找到【碰到边缘就反弹】积木，拖拽并拼接到循环体内部的最下方；\n" +
                            "③ 点击左侧黄色【控制】分类，可以在移动后加入一块【等待 0.05 秒】，让角色的跑动看起来更加平滑自然！🐾"
                        !hasLoop -> "【修正建议】: \n" +
                            "① 点击左侧黄色【控制】分类，拖出【重复执行】积木；\n" +
                            "② 将它吸附包裹在蓝色【移动】积木的外部；\n" +
                            "③ 重新点击绿旗测试小猫的持续动作吧！"
                        else -> "【优化建议】: \n" +
                            "① 可以尝试在紫色【外观】分类中拖出【下一个造型】放入循环体；\n" +
                            "② 加入【等待 0.1 秒】积木，打造丝滑的奔跑动画效果！✨"
                    }

                    "🐱【 智能精灵姐姐 · 积木语法与逻辑体检报告 】✨\n\n" +
                    praiseSection +
                    errorSection + "\n\n" +
                    adviceSection
                }
            }

            // =========================================================================
            // 0. 智能精灵核心功能 2：创意引导 / 创意探索 (量身定制，拒绝模板化)
            // =========================================================================
            prompt.contains("创意引导") || prompt.contains("创意探索") || prompt.contains("希望创作的主题是") ||
            q.contains("创意引导") || q.contains("创意探索") || q.startsWith("创意探索:") || q.startsWith("创意探索：") -> {
                val theme = if (rawTopic.isNotBlank()) rawTopic else "动作对战"
                val t = theme.lowercase()

                when {
                    // 1. 枪战 / 射击 / 飞机大战 / 弹幕 / 武器
                    t.contains("枪") || t.contains("射击") || t.contains("子弹") || t.contains("飞机大战") || t.contains("坦克") || t.contains("开火") || t.contains("武器") || t.contains("炮") -> {
                        "🎨【 智能精灵姐姐 · 【$theme】3大炫酷射击魔法方案 】🚀\n\n" +
                        "哇！你想做【$theme】主题的射击大战，太酷啦！精灵姐姐为你量身定制了 3 个火力全开的编程魔法：\n\n" +
                        "🌟 **魔法方案 1：【准星瞄准与武器切换】**\n" +
                        "• **玩法**：角色跟随鼠标实时旋转瞄准，按数字键 1/2 切换手枪与霰弹枪！\n" +
                        "• **拼法**：\n" +
                        "  ① 黄色【事件】拖出【当 🟢 被点击】 ➔ 黄色【控制】拖出【重复执行】；\n" +
                        "  ② 内部放入蓝色【运动】里的【面向 鼠标指针】；\n" +
                        "  ③ 黄色【当按下 [1] 键】 ➔ 深橙色【将 [武器类型] 设为 1】 ➔ 紫色【换成造型 [手枪]】！\n\n" +
                        "🌟 **魔法方案 2：【克隆弹幕风暴与无限换弹】**\n" +
                        "• **玩法**：按下空格键发射克隆子弹，弹夹容量打空后按 R 键换弹！\n" +
                        "• **拼法**：\n" +
                        "  ① 发射端：黄色【当按下 [空格] 键】 ➔ 黄色【如果 <[子弹数] > 0> 那么 [克隆 (子弹), 将 (子弹数) 增加 -1]】；\n" +
                        "  ② 子弹端：黄色【当作为克隆体启动时】 ➔ 蓝色【移到 (主角)】 ➔ 蓝色【面向 (主角的方向)】 ➔ 黄色【重复执行直到 <碰到 边缘 或 碰到 敌人> [移动 15 步]】 ➔ 黄色【删除此克隆体】！\n\n" +
                        "🌟 **魔法方案 3：【敌机/敌人击毁爆炸与金币掉落】**\n" +
                        "• **玩法**：敌人被子弹击中后血量清空，触发全屏爆炸特效并掉落奖励金币！\n" +
                        "• **拼法**：敌人脚本在循环中检测黄色【如果 <碰到 [子弹]?> 那么 [将 (得分) 增加 10, 播放声音 (爆炸), 删除此克隆体]】！✨"
                    }

                    // 2. 拳击 / 格斗 / 对战 / PK / 武术
                    t.contains("拳击") || t.contains("格斗") || t.contains("对战") || t.contains("pk") || t.contains("武术") || t.contains("打斗") || t.contains("战斗") -> {
                        "🎨【 智能精灵姐姐 · 【$theme】3大热血格斗魔法方案 】🚀\n\n" +
                        "哇！你想做【$theme】主题的格斗小游戏，太帅气啦！精灵姐姐为你量身定制了 3 个超好玩的格斗编程魔法：\n\n" +
                        "🌟 **魔法方案 1：【双人近战出拳与连击招式】**\n" +
                        "• **玩法**：玩家按 J 键出拳，角色切换到出拳造型并向前快速突进 15 步，命中对手触发打击音效！\n" +
                        "• **拼法**：\n" +
                        "  ① 黄色【事件】拖出【当按下 [j] 键】；\n" +
                        "  ② 紫色【外观】拖出【换成造型 [punch 出拳]】；\n" +
                        "  ③ 蓝色【运动】拖出【移动 15 步】；\n" +
                        "  ④ 黄色【控制】拖出【等待 0.2 秒】 ➔ 紫色【换成造型 [idle 站立]】！\n\n" +
                        "🌟 **魔法方案 2：【血条生命值与受击击退】**\n" +
                        "• **玩法**：为对手设计【生命值】变量，被击中后生命值 -10，身体闪烁红光并被击退！\n" +
                        "• **拼法**：\n" +
                        "  ① 深橙色【变量】点击【建立一个变量】，命名为【对手生命值】，开局设为 100；\n" +
                        "  ② 黄色【控制】拖出【如果 <碰到 [小猫拳头]?> 那么】；\n" +
                        "  ③ 内部拼入深橙色【将 [对手生命值] 增加 -10】+ 蓝色【移动 -20 步】（受击后退击飞效果）！\n\n" +
                        "🌟 **魔法方案 3：【全屏能量必杀大招 (KO 终结技)】**\n" +
                        "• **玩法**：怒气值满 100 时按 K 键释放炫酷全屏必杀光波，直接清空对手血条！\n" +
                        "• **拼法**：黄色【当按下 [k] 键】 ➔ 黄色【如果 <[怒气值] = 100> 那么 [广播 (释放必杀大招), 将 (怒气值) 设为 0]】！✨"
                    }

                    // 3. 跑酷 / 跳跃 / 重力 / 闯关
                    t.contains("跑酷") || t.contains("跳跃") || t.contains("重力") || t.contains("躲避") || t.contains("避障") -> {
                        "🎨【 智能精灵姐姐 · 【$theme】3大极速跑酷魔法方案 】🚀\n\n" +
                        "哇！【$theme】是最考验操作与节奏的游戏，精灵姐姐教你用积木搭出流畅的跑酷体验：\n\n" +
                        "🌟 **魔法方案 1：【物理重力与二段跳系统】**\n" +
                        "• **玩法**：按空格起跳，在空中可以再次按空格实现二段跳！\n" +
                        "• **拼法**：深橙色新建变量【y速度】；按空格时【将 [y速度] 设为 12】；在【重复执行】中【将 y 坐标增加 (y速度), 将 [y速度] 增加 -1】实现重力加速度！\n\n" +
                        "🌟 **魔法方案 2：【随机克隆地刺与空中飞鸟】**\n" +
                        "• **玩法**：地面随机刷新地刺陷阱，空中有飞鸟巡逻，考验反应力！\n" +
                        "• **拼法**：障碍物【当作为克隆体启动时】 ➔ 【移到 x: 240 y: -120】 ➔ 【重复执行直到 <x 坐标 < -230> [将 x 坐标增加 -8]】 ➔ 【删除此克隆体】！\n\n" +
                        "🌟 **魔法方案 3：【金币磁铁与冲刺无敌光环】**\n" +
                        "• **玩法**：吃到磁铁道具后，周围的金币会自动飞向角色！\n" +
                        "• **拼法**：金币克隆体检测【如果 <磁铁生效 = 1> 那么 [面向 (主角), 移动 10 步]】！✨"
                    }

                    // 4. 迷宫 / 探险 / 寻宝 / 逃脱
                    t.contains("迷宫") || t.contains("探险") || t.contains("寻宝") || t.contains("逃脱") || t.contains("地牢") -> {
                        "🎨【 智能精灵姐姐 · 【$theme】3大迷宫探秘魔法方案 】🚀\n\n" +
                        "哇！【$theme】是最考验智慧的游戏类型，精灵姐姐为你设计了 3 个核心关卡机制：\n\n" +
                        "🌟 **魔法方案 1：【经典碰墙回弹 (防穿墙算法)】**\n" +
                        "• **玩法**：玩家操控小猫穿行迷宫，碰到黑色的迷宫墙壁绝对穿不过去！\n" +
                        "• **拼法**：移动 5 步后紧跟黄色【如果 <碰到 颜色 [黑色]?> 那么 [移动 -5 步]】！\n\n" +
                        "🌟 **魔法方案 2：【钥匙搜集与金门解锁】**\n" +
                        "• **玩法**：必须先在迷宫角落找到 3 把钥匙，终点大门才会打开！\n" +
                        "• **拼法**：吃到钥匙时【将 [钥匙数量] 增加 1, 隐藏】；大门检测【如果 <[钥匙数量] = 3> 那么 [广播 (通关胜利)]】！\n\n" +
                        "🌟 **魔法方案 3：【幽灵守卫巡逻与视野手电筒】**\n" +
                        "• **玩法**：迷宫中有巡逻的小幽灵，被幽灵碰到会重回起点！\n" +
                        "• **拼法**：小猫脚本中加入【如果 <碰到 [小幽灵]?> 那么 [移到 x: -200 y: -150 (起点坐标)]】！✨"
                    }

                    // 5. 贪吃蛇 / 吃豆人 / 消除 / 收集 / 接苹果
                    t.contains("贪吃蛇") || t.contains("吃豆") || t.contains("苹果") || t.contains("接") || t.contains("收集") || t.contains("消除") -> {
                        "🎨【 智能精灵姐姐 · 【$theme】3大趣味收集魔法方案 】🚀\n\n" +
                        "哇！【$theme】是经典耐玩的小游戏，精灵姐姐为你量身定制了 3 个核心玩法：\n\n" +
                        "🌟 **魔法方案 1：【身体克隆跟随与不断变长】**\n" +
                        "• **玩法**：每吃到一个食物，身体克隆体就增加一节，跟随头部移动！\n" +
                        "• **拼法**：头部在循环中【克隆 (自己), 移动 10 步】；克隆体【等待 (长度 * 0.1) 秒 ➔ 删除此克隆体】！\n\n" +
                        "🌟 **魔法方案 2：【随机金色闪光果实】**\n" +
                        "• **玩法**：地图上每隔 5 秒随机生成一个金色发光果实，吃到加 5 分并加速！\n" +
                        "• **拼法**：食物脚本【移到 x: (在 -200 到 200 间随机数) y: (在 -150 到 150 间随机数)】 ➔ 紫色【将 颜色 特效增加 25】！\n\n" +
                        "🌟 **魔法方案 3：【无敌狂暴与幽灵反杀模式】**\n" +
                        "• **玩法**：吃到超级能量豆后，角色变大变金黄，可以反向追击敌人！\n" +
                        "• **拼法**：深橙色【将 [无敌时间] 设为 5】 ➔ 紫色【将大小设为 130%】 ➔ 广播【幽灵逃跑】！✨"
                    }

                    // 6. 赛车 / 驾驶 / 极速
                    t.contains("赛车") || t.contains("车") || t.contains("驾驶") || t.contains("漂移") || t.contains("竞速") -> {
                        "🎨【 智能精灵姐姐 · 【$theme】3大赛道极速魔法方案 】🚀\n\n" +
                        "引擎轰鸣！【$theme】主题最让人心跳加速，精灵姐姐为你准备了 3 个专业赛车魔法：\n\n" +
                        "🌟 **魔法方案 1：【真实油门加速与刹车阻尼】**\n" +
                        "• **玩法**：按上方向键持续加速，松开后赛车会有真实的惯性滑行减速！\n" +
                        "• **拼法**：在【重复执行】里放入【如果 <按下 上方向键> 那么 [将 (速度) 增加 0.5] 否则 [将 (速度) 设为 (速度 * 0.96)] ➔ 移动 (速度) 步】！\n\n" +
                        "🌟 **魔法方案 2：【弯道漂移与轮胎冒烟粒子】**\n" +
                        "• **玩法**：按左右方向键转向时，车尾克隆出灰色小烟圈实现漂移特效！\n" +
                        "• **拼法**：黄色【当按下 [左/右箭头]】 ➔ 蓝色【左/右转 5 度】 ➔ 黄色【克隆 (烟雾粒子)】！\n\n" +
                        "🌟 **魔法方案 3：【3圈计时与终点冲线排行榜】**\n" +
                        "• **玩法**：记录跑完 3 圈所用的秒数，挑战最快圈速！\n" +
                        "• **拼法**：碰到终点线时【将 [圈数] 增加 1, 如果 <圈数 > 3> 那么 [停止全部脚本, 广播 (记录新成绩)]】！✨"
                    }

                    // 7. 音乐 / 钢琴 / 节奏
                    t.contains("音乐") || t.contains("钢琴") || t.contains("节奏") || t.contains("乐器") || t.contains("吉他") -> {
                        "🎨【 智能精灵姐姐 · 【$theme】3大音乐节奏魔法方案 】🚀\n\n" +
                        "音符跳动！【$theme】充满艺术美感，精灵姐姐教你把 Scratch 变成一台魔法乐器：\n\n" +
                        "🌟 **魔法方案 1：【8音阶魔法琴键交互】**\n" +
                        "• **玩法**：点击键盘 A-K 键或鼠标点击琴键，演奏 Do-Re-Mi-Fa-Sol-La-Si-Do！\n" +
                        "• **拼法**：点击左下角添加【音乐扩展】 ➔ 黄色【当按下 [a] 键】 ➔ 绿底【演奏音阶 (60) 0.5 拍】 ➔ 紫色【将大小设为 110% ➔ 等待 0.1 秒 ➔ 设为 100%】！\n\n" +
                        "🌟 **魔法方案 2：【节奏大师下落音符击打】**\n" +
                        "• **玩法**：音符从屏幕上方匀速下落，落到打击线时按键获得 Perfect 判定！\n" +
                        "• **拼法**：音符克隆体下落，检测【如果 <碰到打击线 且 按下空格> 那么 [将 (得分) 增加 100, 换成造型 (Perfect)]】！\n\n" +
                        "🌟 **魔法方案 3：【角色伴舞与炫彩灯光秀】**\n" +
                        "• **玩法**：背景音乐响起时，舞台聚光灯旋转，舞台小猫随节拍律动跳舞！\n" +
                        "• **拼法**：黄色【当接收到 (节拍信号)】 ➔ 紫色【下一个造型, 将 颜色 特效增加 25】！✨"
                    }

                    // 8. 换装 / 舞台 / 舞蹈
                    t.contains("换装") || t.contains("舞台") || t.contains("变装") || t.contains("衣服") || t.contains("模特") -> {
                        "🎨【 智能精灵姐姐 · 【$theme】3大百变时尚魔法方案 】🚀\n\n" +
                        "太漂亮啦！【$theme】是展示审美的舞台，精灵姐姐为你设计了 3 个换装互动魔法：\n\n" +
                        "🌟 **魔法方案 1：【点击衣橱一键穿戴】**\n" +
                        "• **玩法**：点击帽子、衣服、鞋子图标，角色身上的服饰立刻随心变换！\n" +
                        "• **拼法**：黄色【当角色被点击】 ➔ 黄色【广播 (更换帽子)】；模特角色【当接收到 (更换帽子) ➔ 换成下一个造型】！\n\n" +
                        "🌟 **魔法方案 2：【搭配评分与评委打分】**\n" +
                        "• **玩法**：搭配好全身服饰后点击“走秀秀场”，评委给出 95+ 高分并撒花！\n" +
                        "• **拼法**：深橙色【将 [时尚分] 设为 (在 90 到 100 间随机数)】 ➔ 紫色【说 (太美啦！得分为: 时尚分) 2秒】！\n\n" +
                        "🌟 **魔法方案 3：【背景舞台特效与聚光灯】**\n" +
                        "• **玩法**：按空格键切换不同风格背景（梦幻城堡、星光秀场、海滩派对）！\n" +
                        "• **拼法**：黄色【当按下 [空格] 键】 ➔ 紫色【下一个背景】 ➔ 播放背景音乐！✨"
                    }

                    // 9. 动态定制通用方案 (针对学生输入的任意未知主题)
                    else -> {
                        "🎨【 智能精灵姐姐 · 【$theme】3大专属创意魔法方案 】🚀\n\n" +
                        "哇！围绕【$theme】这个独具巧思的主题，精灵姐姐为你定制了 3 个生动有趣的 Scratch 积木魔法：\n\n" +
                        "🌟 **魔法方案 1：【【$theme】主角操控与生动动作】**\n" +
                        "• **玩法**：让你的【$theme】主角根据玩家键盘操作（上下左右/空格）展开特色行动，配合造型切换！\n" +
                        "• **拼法**：\n" +
                        "  ① 黄色【事件】拖出【当按下 [方向键/空格] 键】；\n" +
                        "  ② 蓝色【运动】拖出【面向 90 方向】并拼接【移动 15 步】；\n" +
                        "  ③ 紫色【外观】拖出【下一个造型】配合动作，打造流畅生动的奔跑/互动动画！\n\n" +
                        "🌟 **魔法方案 2：【【$theme】专属目标互动与得分系统】**\n" +
                        "• **玩法**：在【$theme】场景中达成核心目标（如搜集物品、战胜阻碍），得分增加并触发金光庆祝音效！\n" +
                        "• **拼法**：\n" +
                        "  ① 深橙色【变量】点击【建立一个变量】，命名为【$theme 得分】；\n" +
                        "  ② 黄色【控制】拖出【如果 <碰到 [目标角色]?> 那么】；\n" +
                        "  ③ 内部拼入深橙色【将 [$theme 得分] 增加 1】+ 粉色【播放声音 (叮当)】+ 紫色【将 颜色 特效增加 25】！\n\n" +
                        "🌟 **魔法方案 3：【关卡晋级与全屏胜利彩蛋】**\n" +
                        "• **玩法**：当【$theme 得分】达到 10 分时，解锁隐藏大招与全屏欢呼烟花庆典！\n" +
                        "• **拼法**：黄色【如果 <[$theme 得分] > 9> 那么 [广播 (胜利大狂欢), 紫色 换成背景 (胜利之夜), 重复执行 10 次 (右转 36 度, 等待 0.05 秒)]】！✨"
                    }
                }
            }

            // =========================================================================
            // 0. 智能精灵核心功能 3：考点解析 / 知识点讲解 (深度拆解，拒绝模板化)
            // =========================================================================
            prompt.contains("知识点讲解") || prompt.contains("考点讲解") || prompt.contains("考点解析") || prompt.contains("核心知识点/考点") ||
            q.contains("考点讲解") || q.contains("知识点讲解") || q.contains("考点解析") || q.startsWith("考点解析:") || q.startsWith("考点解析：") -> {
                val topic = if (rawTopic.isNotBlank()) rawTopic else "自制积木与函数"
                val t = topic.lowercase()

                when {
                    // 1. 拳击 / 格斗 / 对战 / PK / 生命值考点
                    t.contains("拳击") || t.contains("格斗") || t.contains("对战") || t.contains("pk") || t.contains("武术") || t.contains("受击") -> {
                        "🎓【 核心考点解析：Scratch 拳击与格斗对战系统 】✨\n\n" +
                        "1. 💡 **什么是“拳击/格斗对战”核心逻辑？**\n" +
                        "   • 格斗游戏的核心是**【按键触发动作状态机 ➔ 碰撞侦测 ➔ 生命值扣减 ➔ 击退位移算法】**！\n\n" +
                        "2. 🧩 **四大核心考点拼搭步骤**：\n" +
                        "   • **考点 ①（按键出拳与造型切换）**：黄色【当按下 [j] 键】 ➔ 紫色【换成造型 [出拳]】 ➔ 蓝色【移动 15 步】 ➔ 黄色【等待 0.2 秒】 ➔ 紫色【换成造型 [站立]】；\n" +
                        "   • **考点 ②（碰撞与受击击退算法）**：受击角色在【重复执行】中检测黄色【如果 <碰到 [小猫拳头]?> 那么】 ➔ 蓝色【移动 -20 步】（受击后退物理击退） ➔ 紫色【将 [颜色] 特效增加 25】（受伤闪烁）；\n" +
                        "   • **考点 ③（生命值变量与 KO 结算）**：深橙色【将 [对手生命值] 增加 -10】 ➔ 黄色【如果 <[对手生命值] < 1> 那么 [广播 (KO 胜利), 停止 (该角色的其他脚本)]】！\n\n" +
                        "3. 🌟 **格斗考点避坑秘籍**：出拳判定必须加微小延时（如等待 0.1 秒）再结算伤害，避免 1 帧内重复扣血导致秒杀对手！🚀"
                    }

                    // 2. 枪战 / 射击 / 弹幕 / 克隆子弹考点
                    t.contains("枪战") || t.contains("射击") || t.contains("子弹") || t.contains("飞机大战") || t.contains("武器") || t.contains("开火") -> {
                        "🎓【 核心考点解析：Scratch 枪战射击与克隆弹道体系 】✨\n\n" +
                        "1. 💡 **什么是“枪战射击”的核心考点？**\n" +
                        "   • 射击游戏的核心是**【准星朝向控制 ➔ 子弹克隆发射 ➔ 弹道飞行 ➔ 命中销毁防内存溢出】**！\n\n" +
                        "2. 🧩 **三大核心积木与完整拼法**：\n" +
                        "   • **考点 ①（准星瞄准与朝向）**：主角在【重复执行】中执行蓝色【面向 鼠标指针】；\n" +
                        "   • **考点 ②（子弹克隆发射与弹道）**：\n" +
                        "     - 发射源：黄色【当按下 [空格] 键】 ➔ 黄色【克隆 [子弹]】；\n" +
                        "     - 子弹分身：黄色【当作为克隆体启动时】 ➔ 蓝色【移到 (主角)】 ➔ 蓝色【面向 (主角的方向)】 ➔ 紫色【显示】 ➔ 黄色【重复执行直到 <碰到 边缘 或 碰到 敌人> [移动 15 步]】 ➔ 黄色【删除此克隆体】；\n" +
                        "   • **考点 ③（命中判定与敌机血量）**：敌人脚本在循环中检测【如果 <碰到 [子弹]?> 那么 [将 (敌人血量) 增加 -1, 删除此克隆体]】！\n\n" +
                        "3. 🌟 **射击考点避坑秘籍**：飞出边缘或击中目标的克隆子弹**必须执行【删除此克隆体】**，否则克隆体堆满 300 个会导致游戏卡死无法再开火！🚀"
                    }

                    // 3. 跑酷 / 跳跃 / 重力考点
                    t.contains("跑酷") || t.contains("跳跃") || t.contains("重力") || t.contains("起跳") || t.contains("物理") -> {
                        "🎓【 核心考点解析：Scratch 跑酷物理跳跃与重力模拟 】✨\n\n" +
                        "1. 💡 **什么是“重力加速度算法”？**\n" +
                        "   • 真实跳跃不是匀速上下，而是“向上冲刺越来越慢，向下坠落越来越快”的物理抛物线！\n\n" +
                        "2. 🧩 **核心拼搭算法**：\n" +
                        "   • **第 1 步（初始化变量）**：深橙色新建变量【y速度】；\n" +
                        "   • **第 2 步（起跳赋予初速度）**：黄色【当按下 [空格] 键】 ➔ 深橙色【将 [y速度] 设为 12】；\n" +
                        "   • **第 3 步（重力循环更新）**：在黄色【重复执行】中拼接蓝色【将 y 坐标增加 [y速度]】 ➔ 深橙色【将 [y速度] 增加 -1】（重力加速度向下衰减）；\n" +
                        "   • **第 4 步（地面支撑检测）**：【如果 <碰到 颜色 [绿色地面]?> 那么 [将 [y速度] 设为 0, 将 y 坐标设为 -100]】！\n\n" +
                        "3. 🌟 **考点秘籍**：加入地面检测可以彻底防止角色一直掉穿舞台底部！🎉"
                    }

                    // 4. 迷宫 / 防穿墙 / 探险考点
                    t.contains("迷宫") || t.contains("探险") || t.contains("穿墙") || t.contains("防穿墙") || t.contains("寻宝") -> {
                        "🎓【 核心考点解析：Scratch 迷宫防穿墙与关卡寻宝 】✨\n\n" +
                        "1. 💡 **什么是迷宫“防穿墙反弹算法”？**\n" +
                        "   • 角色向前移动 5 步后，如果检测到碰到了迷宫墙壁颜色，立刻向后退回 5 步，抵消前进位移！\n\n" +
                        "2. 🧩 **三大核心考点**：\n" +
                        "   • **考点 ①（防穿墙判定）**：在【重复执行】中放蓝色【移动 5 步】 ➔ 紧跟黄色【如果 <碰到 颜色 [黑色]?> 那么 [移动 -5 步]】；\n" +
                        "   • **考点 ②（道具收集计数）**：吃到钥匙时【将 [钥匙数] 增加 1, 隐藏】；\n" +
                        "   • **考点 ③（终点通关广播）**：大门检测【如果 <[钥匙数] = 3> 那么 [广播 (通关胜利)]】！\n\n" +
                        "3. 🌟 **考点小贴士**：迷宫墙壁颜色一定要全地图统一（如纯黑），取色器吸色更精准！🐾"
                    }

                    // 5. 贪吃蛇 / 列表 / 数组考点
                    t.contains("贪吃蛇") || t.contains("吃豆") || t.contains("列表") || t.contains("数组") -> {
                        "🎓【 核心考点解析：Scratch 贪吃蛇轨迹与列表数据结构 】✨\n\n" +
                        "1. 💡 **什么是“列表记录身体轨迹”？**\n" +
                        "   • 贪吃蛇身体每一节跟着头部走，考点在于使用【列表（List）】记录头部走过的每一个 X-Y 坐标点！\n\n" +
                        "2. 🧩 **核心拼搭三步走**：\n" +
                        "   • **第 1 步**：在深橙色【变量】分类点击【建立一个列表】，分别命名为【身体X】和【身体Y】；\n" +
                        "   • **第 2 步**：头部每次移动时，【在 [身体X] 的第 1 项插入 (x坐标)】，【在 [身体Y] 的第 1 项插入 (y坐标)】；\n" +
                        "   • **第 3 步**：克隆体依次读取列表中对应位置的坐标，实现顺滑如丝的身体跟随！\n\n" +
                        "3. 🌟 **考点小贴士**：列表长度超出身体节数时，记得【删除列表的第 全部 项】或末尾项哦！🐍"
                    }

                    // 6. 赛车 / 速度与阻尼考点
                    t.contains("赛车") || t.contains("车") || t.contains("驾驶") || t.contains("阻尼") -> {
                        "🎓【 核心考点解析：Scratch 赛车动力学与速度阻尼 】✨\n\n" +
                        "1. 💡 **什么是“赛车动力学与阻尼”？**\n" +
                        "   • 模拟真实车辆的油门踩踏、惯性滑行阻尼以及转向漂移角速度！\n\n" +
                        "2. 🧩 **核心拼搭算法**：\n" +
                        "   • **加速与阻尼**：深橙色【变量】新建【速度】。在循环中【如果 <按下 上方向键> 那么 [将 (速度) 增加 0.5] 否则 [将 (速度) 设为 (速度 * 0.95)]】；\n" +
                        "   • **位移与转向**：【移动 (速度) 步】，按左右方向键【向左/右旋转 5 度】；\n" +
                        "   • **草地减速**：【如果 <碰到 颜色 [草地绿]?> 那么 [将 (速度) 设为 (速度 * 0.5)]】！\n\n" +
                        "3. 🌟 **考点小贴士**：利用乘法 (速度 * 0.95) 可以做出非常逼真的自然减速！🏎️"
                    }

                    // 7. 音乐与声音考点
                    t.contains("音乐") || t.contains("钢琴") || t.contains("音符") || t.contains("声音") -> {
                        "🎓【 核心考点解析：Scratch 音乐扩展与音阶演奏 】✨\n\n" +
                        "1. 💡 **什么是“音乐扩展模块”？**\n" +
                        "   • Scratch 内置了强大的 MIDI 音乐合成器，可以通过代码演奏 128 种乐器和全音阶！\n\n" +
                        "2. 🧩 **核心积木指南**：\n" +
                        "   • **添加扩展**：点击舞台左下角蓝色图标 ➔ 选择【音乐】扩展；\n" +
                        "   • **演奏音阶**：【演奏音阶 (60 中音C) (0.5) 拍】；\n" +
                        "   • **设置乐器**：【将乐器设为 (1 钢琴 / 4 电子琴 / 6 吉他)】；\n" +
                        "   • **节奏控制**：【将演奏速度设为 (120) BPM】！\n\n" +
                        "3. 🌟 **考点小贴士**：用循环配合列表里的音符数字，可以一键播放整首儿歌（如《小星星》）！🎵"
                    }

                    // 8. 函数 / 自制积木考点
                    t.contains("函数") || t.contains("自制积木") || t.contains("自定义积木") || t.contains("过程") || t.contains("方法") || t.contains("封装") -> {
                        "🎓【 核心考点解析：Scratch 自制积木（函数 Function）】✨\n\n" +
                        "1. 💡 **什么是“函数 / 自制积木”？**\n" +
                        "   • 函数就像把一整套复杂的菜谱打包成一个**“一键执行的超级魔法按键”**！\n" +
                        "   • 比如做汉堡需要【放面包 ➔ 放生菜 ➔ 放肉饼 ➔ 盖上面包】一共 4 步。我们可以制作一块名叫【做汉堡】的粉色自制积木，以后每次想做汉堡，只要拖出一块【做汉堡】积木就搞定啦，不用再重复拼 4 块积木！\n\n" +
                        "2. 🧩 **三步动手制作自制积木**：\n" +
                        "   • **第 1 步（创建）**：点击左侧最下方粉红色的【自制积木】分类 ➔ 点击【制作一个新的积木】，输入名字（例如“画正方形”）；\n" +
                        "   • **第 2 步（定义动作）**：工作区会出现一个粉色的【定义 画正方形】大帽子。在它下方拼入具体步骤：【重复执行 4 次 [移动 80 步, 右转 90 度]】；\n" +
                        "   • **第 3 步（在主程序调用）**：回到【当 🟢 被点击】下方，直接拖出粉色的【画正方形】积木！\n\n" +
                        "3. 🌟 **为什么考点经常考函数？**\n" +
                        "   • **模块化思维**：把大问题拆解成小零件；\n" +
                        "   • **代码整洁**：几百行的复杂游戏，用自制积木拆分后变得像搭积木一样清爽易读！🚀"
                    }

                    // 9. 运算与比较考点
                    t.contains("运算") || t.contains("运算符") || t.contains("比较") || t.contains(">") || t.contains("<") || t.contains("=") || t.contains("x>5") -> {
                        "🎓【 核心考点解析：Scratch 绿色运算与条件比较 】✨\n\n" +
                        "1. 💡 **什么是“运算积木”（Operators）？**\n" +
                        "   • 运算积木就像是小猫大脑里的**“数学计算器”**与**“逻辑天平”**！\n" +
                        "   • 它分为两大类：**圆形的算术积木**（加减乘除、随机数）算出一个数字；**六边形的比较积木**（大于 >、小于 <、等于 =、与、或、不成立）判断真假（对或错）！\n\n" +
                        "2. 🧩 **如何组合“如果 (x > 5) 那么...”？**：\n" +
                        "   • **第 1 步**：在左侧黄色【控制】分类拖出【如果 < > 那么】；\n" +
                        "   • **第 2 步**：在绿色【运算】分类拖出六边形【 ( ) > 50 】；\n" +
                        "   • **第 3 步**：在深橙色【变量】分类拖出圆形【 x 】变量，塞入大于号左边的圆圈里，右边输入数字【 5 】；\n" +
                        "   • **第 4 步**：把组装好的六边形【 < x > 5 > 】卡槽整体塞进【如果】的六边形嘴巴里！\n\n" +
                        "3. 🌟 **核心考点避坑**：只有六边形运算积木才能塞进【如果】和【重复执行直到】的条件框里哦！🎉"
                    }

                    // 10. 广播与消息考点
                    t.contains("广播") || t.contains("消息") || t.contains("信鸽") -> {
                        "📢【 核心考点解析：Scratch 广播与消息机制 】✨\n\n" +
                        "1. 💡 **什么是广播（Broadcast）？**\n" +
                        "   • 广播就像角色之间拿着“无线对讲机”互相喊话！比如裁判喊“比赛开始”，运动员才一起往前跑。\n\n" +
                        "2. 🧩 **核心积木与拼搭步骤**：\n" +
                        "   • **发送方（广播消息）**：黄色【事件】➔ 拖出【广播 [消息1]】；\n" +
                        "   • **接收方（响应行动）**：黄色【事件】➔ 拖出【当接收到 [消息1]】，在下方吸附要执行的动作（如【显示】或【移动 20 步】）！\n\n" +
                        "3. 🌟 **避坑小贴士**：广播名字最好取有意义的中文（如“游戏开始”、“关卡升级”），这样角色再多也不会混淆哦！🚀"
                    }

                    // 11. 变量考点
                    t.contains("变量") || t.contains("数据") || t.contains("得分") || t.contains("计分") || t.contains("血量") -> {
                        "📦【 核心考点解析：Scratch 变量魔法盒 】✨\n\n" +
                        "1. 💡 **什么是变量（Variable）？**\n" +
                        "   • 变量就像一个“贴着名字的魔法小盒子”，盒子里可以装数字、文字，随时查看或修改！\n\n" +
                        "2. 🧩 **三步上手拼搭**：\n" +
                        "   • ① 在左侧深橙色【变量】分类点击【建立一个变量】，命名为【得分】；\n" +
                        "   • ② 绿旗启动时，拖出【将 [得分] 设为 0】进行初始化；\n" +
                        "   • ③ 吃到道具或答对题目时，执行【将 [得分] 增加 1】！\n\n" +
                        "3. 🌟 **常见考点**：记得每次游戏重新开始前，都要把得分“归零”哦！🐾"
                    }

                    // 12. 克隆考点
                    t.contains("克隆") || t.contains("分身") -> {
                        "👥【 核心考点解析：Scratch 角色克隆体系 】✨\n\n" +
                        "1. 💡 **什么是克隆（Clone）？**\n" +
                        "   • 克隆就像孙悟空拔一根毫毛变出千千万万个分身！适合用来做下雪、漫天星星、敌方小兵、漫天子弹！\n\n" +
                        "2. 🧩 **三大核心积木（都在黄色【控制】分类底部）**：\n" +
                        "   • 【克隆 [自己]】：本体发功制造新分身；\n" +
                        "   • 【当作为克隆体启动时】：分身的独立出生点与行动脚本；\n" +
                        "   • 【删除此克隆体】：分身完成使命（如打中敌人或飞出屏幕）后立刻销毁，释放内存！\n\n" +
                        "3. 🌟 **考点秘籍**：克隆体一定要在脚本最后加【删除此克隆体】，防止分身堆满 300 个导致游戏卡死！🚀"
                    }

                    // 13. 坐标考点
                    t.contains("坐标") || t.contains("位置") || t.contains("x坐标") || t.contains("y坐标") -> {
                        "📐【 核心考点解析：Scratch X-Y 坐标与舞台空间 】✨\n\n" +
                        "1. 💡 **什么是舞台坐标系？**\n" +
                        "   • 整个舞台宽 480 像素，高 360 像素。最中心点的坐标是 (x: 0, y: 0)！\n" +
                        "   • **X 坐标（横向左右）**：向右为正 (最大 +240)，向左为负 (最小 -240)；\n" +
                        "   • **Y 坐标（纵向上下）**：向上为正 (最大 +180)，向下为负 (最小 -180)。\n\n" +
                        "2. 🧩 **核心移动积木**：\n" +
                        "   • 【移到 x: ( ) y: ( )】：瞬间传送定位；\n" +
                        "   • 【在 (1) 秒内滑行到 x: ( ) y: ( )】：平滑飞行移动；\n" +
                        "   • 【将 x 坐标增加 (10)】：向右推进一步；【将 y 坐标增加 (10)】：向上跳跃一步！\n\n" +
                        "3. 🌟 **考点口诀**：横 X 纵 Y，右正左负，上正下负！🐾"
                    }

                    // 14. 循环考点
                    t.contains("循环") || t.contains("重复") -> {
                        "🔄【 核心考点解析：Scratch 循环结构的三大家族 】✨\n\n" +
                        "1. 💡 **什么是循环结构？**\n" +
                        "   • 循环就像一个永远不知疲倦的小马达，把相同的动作重复运行多次或一直运行！\n\n" +
                        "2. 🧩 **三大循环积木（都在黄色【控制】分类）**：\n" +
                        "   • ① **【重复执行】（无限循环）**：从不停止，用于角色持续移动、背景音乐循环、游戏常驻监听；\n" +
                        "   • ② **【重复执行 (10) 次】（计数循环）**：跑完指定次数后自动退出，用于跳跃 10 步、闪烁 3 次；\n" +
                        "   • ③ **【重复执行直到 <条件>】（条件循环）**：在条件满足前一直循环，一旦条件满足立刻停下（如直到碰到终点）！\n\n" +
                        "3. 🌟 **嵌套法则**：循环体内部还可以嵌套【如果...那么】，实现智能动态检测！🎉"
                    }

                    // 15. 条件选择考点
                    t.contains("选择") || t.contains("条件") || t.contains("如果") || t.contains("分支") -> {
                        "⚡【 核心考点解析：Scratch 条件分支与智能决策 】✨\n\n" +
                        "1. 💡 **什么是“条件分支”（选择结构）？**\n" +
                        "   • 条件分支就像路口的交通信号灯，根据现场情况（真或假）决定走哪条路线！\n\n" +
                        "2. 🧩 **两大分支积木（都在黄色【控制】分类）**：\n" +
                        "   • ① **【如果 <条件> 那么】（单分支）**：只有条件满足时才执行内部积木；\n" +
                        "   • ② **【如果 <条件> 那么...否则...】（双分支）**：条件满足走上面，不满足走下面【否则】分支！\n\n" +
                        "3. 🌟 **考点秘籍**：条件必须放入【重复执行】中才能在整个游戏过程中持续侦测生效哦！🚀"
                    }

                    // 16. 动态通用考点解析 (针对任意自定义考点主题)
                    else -> {
                        "🎓【 核心考点解析：Scratch 【$topic】魔法讲解 】✨\n\n" +
                        "1. 💡 **什么是【$topic】？**\n" +
                        "   • 在少儿编程中，【$topic】是构建互动程序和游戏必不可少的关键积木逻辑！\n" +
                        "   • 它可以帮助角色更精确地感知外界输入与计算状态，做出聪明的逻辑反应。\n\n" +
                        "2. 🧩 **核心积木三步动手指南**：\n" +
                        "   • **第 1 步（定位抽屉）**：在左侧分类栏找到与【$topic】对应的彩色分类标签（如黄色事件/蓝色运动/黄色控制/深橙色变量）；\n" +
                        "   • **第 2 步（拼装逻辑）**：将相关积木拖拽出来，吸附在【当 🟢 被点击】或循环结构的内部；\n" +
                        "   • **第 3 步（关联互动）**：结合黄色【控制】（如果..那么）与紫色【外观】实现生动的舞台互动与数值反馈！\n\n" +
                        "3. 🌟 **考点学习小贴士**：多动手点击绿旗调试运行，观察舞台角色的即时反应，这就是优秀的计算思维！🚀"
                    }
                }
            }

            // =========================================================================
            // 1. 面向对象编程 (OOP) / 类与对象 / 封装 / 继承 / 多态 (解决用户明确反馈的痛点)
            // =========================================================================
            q.contains("面向对象") || q.contains("oop") || q.contains("类与对象") || q.contains("封装") || q.contains("继承") || q.contains("多态") -> {
                "💡【 编程魔法奥秘：什么是“面向对象编程”？】✨\n\n" +
                "哇！宝贝你太厉害啦，竟然关注到软件工程师们最核心的思维方式——“面向对象编程”（简称 OOP）！🐾\n\n" +
                "其实面向对象一点也不复杂，在少儿编程里就像我们**“用乐高积木拼角色”**或者**“大自然里的万物归类”**哦：\n\n" +
                "1. 🐱 **什么是“对象”（Object）？**\n" +
                "   • 在生活中，你手里抱的小猫咪、停在路边的小汽车，都是一个真实存在的具体事物，在代码里就叫“对象”。\n" +
                "   • **在 Scratch 舞台上**：每一个右下角的小猫、小恐龙、魔法棒角色（Sprite），其实就是一个活生生的“对象”！\n\n" +
                "2. 📦 **每个对象都有两样法宝**：\n" +
                "   • **属性（长什么样）**：比如小猫的名字、X/Y 坐标、大小、颜色、造型；\n" +
                "   • **方法/行为（能做什么技能）**：比如小猫能【移动 10 步】、【发出喵喵叫】、【跳跃】。\n\n" +
                "3. 🏭 **什么是“类”（Class）？**\n" +
                "   • “类”就像做小饼干的**模具**或者工程师画的**蓝图**。\n" +
                "   • 模具规定了小猫有胡须和四条腿（类）；你用模具做出来的第一只小白猫、第二只小橘猫，就是由类创建出来的“具体对象”！\n\n" +
                "🌟 **在 Scratch 里的奇妙联系**：\n" +
                "Scratch 里的【角色】和【克隆体】就是最直观的面向对象设计！现在把 Scratch 积木的基础逻辑练扎实，等未来学习 Python/Java 的类和对象时，你就会发现自己早已掌握它的魔法精髓啦！🚀"
            }

            // =========================================================================
            // 2. 超纲编程语言跨界 (Java / C++ / C# / JavaScript / Rust / Go / 编译型语言)
            // =========================================================================
            q.contains("java") || q.contains("c++") || q.contains("c语言") || q.contains("javascript") || q.contains("c#") || q.contains("rust") || q.contains("golang") || q.contains("文本编程") -> {
                val langName = when {
                    q.contains("java") -> "Java"
                    q.contains("c++") -> "C++"
                    q.contains("c语言") -> "C 语言"
                    q.contains("javascript") -> "JavaScript"
                    q.contains("rust") -> "Rust"
                    else -> "文本编程语言"
                }
                "👏【 科技前沿大揭秘：探索 $langName 的奇妙世界 】✨\n\n" +
                "哇！看到你对 $langName 充满好奇，精灵姐姐太为你自豪啦，小小年纪就展现出极客探索精神！🌈\n\n" +
                "1. 📖 **$langName 是什么呢？**\n" +
                "   • $langName 是世界上应用最广泛的“纯英文文本编程语言”之一。\n" +
                "   • 工程师叔叔阿姨用它来开发大型 3D 游戏（如《我的世界》）、手机 App、或者让火箭飞上太空的超级系统！\n\n" +
                "2. 🧩 **它和我们学的 Scratch 有什么关系？**\n" +
                "   • **Scratch** 就像“拼装乐高积木”：重点是锻炼你的**逻辑思维、顺序/选择/循环结构**，让你不用担心记错英文字母；\n" +
                "   • **$langName** 就像“用纯英文写魔法说明书”：底层逻辑（变量、判断、循环、函数）和 Scratch 完全一模一样！\n\n" +
                "💡 **小精灵给你的成长建议**：\n" +
                "现在在 Scratch 里把算法和逻辑练得棒棒的，这就是打下最坚实的地基！等你长大一点开始写 $langName 时，会觉得像搭积木一样得心应手哦！💪"
            }

            // =========================================================================
            // 3. Python 编程与代码对比
            // =========================================================================
            q.contains("python") || q.contains("蟒蛇") -> {
                when {
                    (q.contains("顺序") || q.contains("选择")) && (q.contains("区别") || q.contains("对比") || q.contains("范例") || q.contains("与") || q.contains("和")) -> {
                        "🐍【 Python 编程：顺序结构 vs 选择结构 代码范例 】✨\n\n" +
                        "宝贝你想看 Python 代码范例太有远见啦！下面为你对比演示【顺序结构】与【选择结构】在 Python 中的具体编写方式：\n\n" +
                        "📌 **1. 顺序结构 (Sequential Structure)**\n" +
                        "• **特点**：代码从上到下按顺序依次执行，每一行都会运行，没有分支跳转。\n" +
                        "• **Python 代码范例**：\n" +
                        "```python\n" +
                        "# 顺序结构：按照步骤从第一行运行到最后一行\n" +
                        "print(\"第一步：准备 Python 魔法画笔 🎨\")\n" +
                        "print(\"第二步：在画板上画一个小圆圈 🟢\")\n" +
                        "print(\"第三步：涂上漂亮的蓝色 🎨\")\n" +
                        "print(\"顺序结构执行完毕！✨\")\n" +
                        "```\n\n" +
                        "📌 **2. 选择结构 (Selection / Conditional Structure)**\n" +
                        "• **特点**：使用 if 和 else 关键字进行条件判断，根据判断结果选择执行哪一条分支代码。\n" +
                        "• **Python 代码范例**：\n" +
                        "```python\n" +
                        "# 选择结构：根据分数判断是否通关\n" +
                        "score = 85\n\n" +
                        "if score >= 60:\n" +
                        "    print(\"🎉 恭喜你！成功通关少儿编程第一关！\")\n" +
                        "else:\n" +
                        "    print(\"💪 差一点点就通关啦，再接再厉哦！\")\n" +
                        "```\n\n" +
                        "💡 **核心区别小结**：\n" +
                        "• **顺序结构**：无条件分支，代码行行必过。\n" +
                        "• **选择结构**：用 if ... else ... 问句做选择，满不满足条件走不同路线！"
                    }
                    else -> {
                        "🐍【 走进神奇的 Python 少儿编程世界 】✨\n\n" +
                        "Python 是一门非常优雅、读起来像英语一样简单的编程语言！\n\n" +
                        "```python\n" +
                        "# 欢迎来到 Python 少儿编程乐园！\n" +
                        "name = \"小创客\"\n" +
                        "print(f\"你好，{name}！欢迎你！\")\n" +
                        "for step in range(1, 4):\n" +
                        "    print(f\"小猫向前跑第 {step} 步 🐾\")\n" +
                        "```\n\n" +
                        "💡 它的循环与判断逻辑和 Scratch 一脉相承，学好 Scratch 积木就是迈向 Python 的第一步！"
                    }
                }
            }

            // =========================================================================
            // 4. 多结构对比与辨析 (顺序 vs 选择 vs 循环)
            // =========================================================================
            (q.contains("区别") || q.contains("不同") || q.contains("对比") || q.contains("比较") || q.contains("还是")) &&
            (q.contains("结构") || q.contains("顺序") || q.contains("选择") || q.contains("循环") || q.contains("分支")) -> {
                "💡【 编程思维核心：顺序结构 vs 选择结构 的区别与联系 】✨\n\n" +
                "“顺序结构”和“选择结构”是编程世界的两大顶梁柱，它们的最本质区别在于**【有没有分支判断】**：\n\n" +
                "1. 🚶 **顺序结构（按部就班，一条路走到底）**：\n" +
                "   • **生活比喻**：早起刷牙洗脸【挤牙膏 ➔ 刷牙 ➔ 漱口】，必须按顺序一步步做完，不能跳步！\n" +
                "   • **程序特点**：指令从上到下逐行执行，每一句代码都会运行。\n" +
                "   • **Scratch 积木**：【移动 10 步】 ➔ 【说 Hello 2秒】 ➔ 【右转 15 度】。\n\n" +
                "2. 🔀 **选择结构（遇事做决定，岔路口二选一）**：\n" +
                "   • **生活比喻**：出门看天气【如果下雨 ➔ 就带雨伞；否则 ➔ 就戴遮阳帽】。\n" +
                "   • **程序特点**：来到岔路口，只有条件满足时才执行特定指令，不满足时走另一条路。\n" +
                "   • **Scratch 积木**：黄色【如果 <碰到 边缘?> 那么 [右转 180度] 否则 [移动 10步]】。\n\n" +
                "🌟 **一句话秒懂核心差异**：\n" +
                "• **顺序结构**：无条件，从头到尾一条线；\n" +
                "• **选择结构**：有条件，根据情况走不同路线！"
            }

            // =========================================================================
            // 5. 算法 / 数据结构 / 排序 / 列表 / 数组
            // =========================================================================
            q.contains("算法") || q.contains("数据结构") || q.contains("排序") || q.contains("二分") || q.contains("查找") -> {
                "💡【 计算思维探秘：什么是“算法”与“数据结构”？】✨\n\n" +
                "1. 🧠 **什么是算法（Algorithm）？**\n" +
                "   • 算法就是**“解决某一个难题的巧妙步骤和菜谱”**！\n" +
                "   • 比如：怎么把 10 张乱七八糟的卡片从小到大排好序？或者怎么用最少的步数走出迷宫？这些聪明的解题方法就叫算法。\n\n" +
                "2. 🗄️ **什么是数据结构（Data Structure）？**\n" +
                "   • 数据结构是**“把信息整整齐齐装起来的百宝箱”**！\n" +
                "   • 在 Scratch 里，深橙色变量分类里的【列表（List）】就是最典型的数据结构，可以按顺序存下一整排玩家姓名或分数！\n\n" +
                "🌟 掌握了算法，你的程序就会像数学小天才一样飞速运转！"
            }

            // =========================================================================
            // 6. 函数 / 自定义积木 / 过程
            // =========================================================================
            q.contains("函数") || q.contains("自制积木") || q.contains("制作新的积木") || q.contains("子程序") -> {
                "💡【 模块化魔法：Scratch 自制积木（函数）怎么用？】✨\n\n" +
                "自制积木（在代码里叫函数 Function）就像把一连串复杂的动作打包成一个“超级技能按键”！\n\n" +
                "1. 🛠️ **制作方法**：点击最左下角粉红色的【自制积木】➔ 点击【制作一个新的积木】，起名叫“画一个正方形”。\n" +
                "2. 📝 **定义内部步骤**：在粉色【定义 画一个正方形】下方，拼上【重复执行 4 次 [移动 100步, 右转 90度]】。\n" +
                "3. 🚀 **调用技能**：在主程序里，只要拖出粉色【画一个正方形】，角色就会立刻执行这整套动作，让代码变得超级清爽整洁！"
            }

            // =========================================================================
            // 7. 单独结构概念 (顺序结构 / 选择结构 / 循环结构)
            // =========================================================================
            q.contains("顺序") && !q.contains("选择") && !q.contains("循环") -> {
                "💡【 Scratch 核心概念：什么是顺序结构？】✨\n\n" +
                "顺序结构是程序世界最基础的“第一大基石”！\n\n" +
                "• **核心含义**：“按照从上到下的顺序，一步接一步地执行指令”。\n" +
                "• **生活比喻**：就像我们按步骤做蛋糕【倒面粉 ➔ 加鸡蛋 ➔ 搅拌 ➔ 烤箱烘烤】，绝不能倒过来！\n" +
                "• **Scratch 实践**：在【当 🟢 被点击】下方直接按顺序拼接【移动 10 步】➔【说 早上好 2秒】➔【换成造型2】。\n\n" +
                "点击绿旗时，小猫会一丝不苟地严格按顺序走完每一步！"
            }

            q.contains("选择") || (q.contains("条件") && !q.contains("循环")) || q.contains("分支") -> {
                "💡【 Scratch 核心概念：什么是选择结构？】✨\n\n" +
                "选择结构（条件分支）就像程序的“智慧小雷达”！\n\n" +
                "• **核心含义**：“根据条件是否满足，决定接下来执行哪一部分代码”。\n" +
                "• **生活比喻**：过马路【如果 绿灯亮 ➔ 往前走；否则 ➔ 停下等待】。\n" +
                "• **Scratch 积木**：在黄色【控制】分类中找到【如果...那么...否则...】，将六边形侦测积木（如【碰到 颜色 ?】）塞入判断框中！\n\n" +
                "有了它，游戏里的角色就能自主做决定啦！"
            }

            q.contains("循环") || q.contains("重复") -> {
                "💡【 Scratch 核心概念：什么是循环结构？】✨\n\n" +
                "循环结构是编程中最省力的“魔法复印机”！\n\n" +
                "• **核心含义**：“把一段相同的操作重复运行很多次，或者一直运行下去”。\n" +
                "• **Scratch 三大循环（都在黄色【控制】分类）**：\n" +
                "  1. 🔄 **【重复执行】**：无限循环，比如让太阳一直发光、敌人一直巡逻；\n" +
                "  2. 🔢 **【重复执行 10 次】**：计数循环，比如小猫向前跳 10 下；\n" +
                "  3. 🎯 **【重复执行直到...】**：条件循环，直到血量为 0 或通关才停下。"
            }

            // =========================================================================
            // 8. 角色动作实操 (跳跃 / 重力 / 跑动 / 射击 / 碰撞 / 克隆 / 变量 / 调试 / 血条)
            // =========================================================================
            q.contains("跳") || q.contains("重力") || q.contains("起跳") -> {
                if (isExpert) {
                    "🎓【 专家进阶：Scratch 物理跳跃与抛物线运动学模型 】✨\n\n" +
                    "📌 **1. 底层原理剖析**：\n" +
                    "• 真实世界中的跳跃遵循牛顿运动定律：瞬时初速度向上（Vy > 0），随后受到恒定的向下重力加速度（g < 0），速度不断衰减直到反向加速下落，形成平滑的抛物线轨迹。\n\n" +
                    "📌 **2. 高阶算法方案（变量动力学模型）**：\n" +
                    "• **建立变量**：新建私有变量【垂直速度】；\n" +
                    "• **起跳初始化**：按下空格键时 ➔ 【将 垂直速度 设为 15】；\n" +
                    "• **物理引擎主循环**：【重复执行直到 <碰到 障碍地面?>】 ➔ 【将 y 坐标增加 (垂直速度)】 ➔ 【将 垂直速度 增加 -1.5】（模拟重力加速度 g）➔ 【等待 0.02 秒】。\n\n" +
                    "💡 **启发思考题**：如果想在空中按下空格键实现“二段跳”（Double Jump），应该怎么用一个计步变量【跳跃次数】来限制最多在空中连跳 2 次呢？"
                } else {
                    "⚡️【 快速极速速答：让角色一秒跳起来！ 】✨\n\n" +
                    "极简 3 步拼搭法：\n" +
                    "① 第一步：点击左侧黄色【事件】分类，拖出【当按下 空格 键】；\n" +
                    "② 第二步：点击黄色【控制】分类，下方拼接【重复执行 10 次】，内部放入蓝色【运动】中的【将 y 坐标增加 10】（角色向上升起）；\n" +
                    "③ 第三步：紧跟下方再拼一个【重复执行 10 次】，内部放入蓝色【运动】中的【将 y 坐标增加 -10】（角色平稳降落）。\n\n" +
                    "💡 **关键避坑**：记得在地面放一个【移到 x:0 y:-100】初始坐标，避免角色跳跃后落空！"
                }
            }

            q.contains("克隆") || q.contains("分身") -> {
                if (isExpert) {
                    "🎓【 专家进阶：Scratch 克隆体生命周期与内存安全架构 】✨\n\n" +
                    "📌 **1. 引擎机制与限制**：\n" +
                    "• **上限与泄漏**：Scratch 舞台全局最多容纳 300 个克隆体。如果只克隆而不执行销毁，克隆体超限后将无法继续生成，且会导致帧率骤降卡顿。\n" +
                    "• **变量作用域**：全局变量由所有克隆体共享；“仅适用于当前角色”的私有变量，克隆体会各自持有一份独立副本（可用于存储每个分身的独立血量与速度）。\n\n" +
                    "📌 **2. 工业级架构方案（对象池化与自动回收）**：\n" +
                    "• **本体发射机**：【当 🟢 被点击】 ➔ 【隐藏】 ➔ 【重复执行】 ➔ 【等待 0.3 秒】 ➔ 【克隆 自己】；\n" +
                    "• **克隆体状态机**：【当作为克隆体启动时】 ➔ 【移到 角色1】 ➔ 【显示】 ➔ 【面向 鼠标指针】 ➔ 【重复执行直到 <碰到 边缘?>】 [移动 12 步] ➔ **【删除此克隆体】（必须在触壁或命中时显式销毁）**。\n\n" +
                    "💡 **启发思考题**：如何利用私有变量为每个克隆体生成不同的随机移动速度和飞行角度，打造漫天散开的烟花弹幕？"
                } else {
                    "⚡️【 快速极速速答：克隆体积木极简用法 】✨\n\n" +
                    "核心只需要记住黄色【控制】分类底部的 3 块积木：\n" +
                    "① **本体负责制造分身**：【当 🟢 被点击】 ➔ 【重复执行】 ➔ 【等待 1 秒】 ➔ 拖出黄色【克隆 自己】；\n" +
                    "② **分身负责行动**：拖出黄色【当作为克隆体启动时】 ➔ 蓝色【移到 随机位置】 ➔ 紫色【显示】 ➔ 蓝色【移动 10 步】；\n" +
                    "③ **分身任务结束必须销毁**：在末尾拼接黄色【删除此克隆体】！\n\n" +
                    "💡 **关键避坑**：本体建议在绿旗后先【隐藏】，分身启动时再【显示】，这样就不会看到本体傻傻地停在原地啦！"
                }
            }

            (q.contains("循环") || q.contains("重复")) && (q.contains("卡") || q.contains("停") || q.contains("不动") || q.contains("排查") || q.contains("调试")) -> {
                if (isExpert) {
                    "🎓【 专家进阶：Scratch 循环阻塞与主线程事件调度调试 】✨\n\n" +
                    "📌 **1. 阻塞根因探查（Scratch 单线程 Yield 机制）**：\n" +
                    "• **无步进死循环**：在【重复执行直到...】中，如果判定条件永远无法达成且内部没有产生屏幕刷新的积木（如运动/外观/等待），Scratch 渲染引擎将陷入计算死循环。\n" +
                    "• **自制积木不刷新**：勾选了【在运行完成前不刷新屏幕】的函数内部如果包含耗时循环，会导致整个画面直接冻结定格。\n\n" +
                    "📌 **2. 专家级排查与断点调试四步法**：\n" +
                    "• **步骤一**：检查循环内部是否缺少【等待 0.05 秒】或运动积木；\n" +
                    "• **步骤二**：在循环入口与出口分别拼接紫色【说 变量名 0.2 秒】进行断点变量打印追踪；\n" +
                    "• **步骤三**：检查黄色【如果...那么】内部是否有【停止 全部脚本】，排查是否被其他并发脚本意外终止。\n\n" +
                    "💡 **启发思考题**：为什么在高频运算时推荐使用自制积木无刷新，而在动画循环中必须保证单帧时间让渡？"
                } else {
                    "⚡️【 快速极速速答：循环卡住 3 步排查法 】✨\n\n" +
                    "请跟着精灵姐姐快速检查以下 3 个地方：\n" +
                    "① **看看循环里有没有动作**：黄色【重复执行】肚子里有没有放蓝色【移动】或紫色【下一个造型】？如果肚子里是空的，角色就不会动哦！\n" +
                    "② **看看是不是条件永远满足不了**：如果用的是【重复执行直到...】，检查里面的绿色菱形条件是不是写错了；\n" +
                    "③ **加个等待试一试**：在循环里面最后一行放一个黄色【等待 0.1 秒】，让小猫喘口气再继续跑！\n\n" +
                    "💡 **关键避坑**：检查黄色【控制】里有没有不小心放了【停止 全部脚本】把整个程序中断掉了！"
                }
            }

            q.contains("边缘") || q.contains("反弹") || q.contains("碰壁") -> {
                if (isExpert) {
                    "🎓【 专家进阶：Scratch 坐标边界碰撞与反射角算法解析 】✨\n\n" +
                    "📌 **1. 底层几何与笛卡尔坐标系原理**：\n" +
                    "• **舞台尺寸**：Scratch 舞台为 X(-240 ~ 240), Y(-180 ~ 180)。\n" +
                    "• **反射定律**：当角色的外接矩形触及左右边界（X = ±240）时，法线为水平方向，反射方向公式为：Direction(新) = 0 - Direction(原)；触及上下边界（Y = ±180）时为：Direction(新) = 180 - Direction(原)。\n\n" +
                    "📌 **2. 双方案对比**：\n" +
                    "• **方案一（官方内建速解）**：在循环内使用蓝色【碰到边缘就反弹】，并在绿旗下方设置【将旋转方式设为 左右翻转】。\n" +
                    "• **方案二（自定义高阶物理反射算法）**：\n" +
                    "  黄色【如果 < <(x 坐标) > 230> 或 <(x 坐标) < -230> > 那么】 ➔ 蓝色【面向 ( (0) - (方向) ) 方向】 ➔ 【移动 5 步】（防止卡进墙体内重复触发碰撞）。\n\n" +
                    "💡 **启发思考题**：如果要制作弹球台游戏，碰到倾斜 45 度的挡板，怎样计算出更真实的入射与反射角？"
                } else {
                    "⚡️【 快速极速速答：碰到边缘就反弹 3 步搞定 】✨\n\n" +
                    "极简 3 步拼搭：\n" +
                    "① 第一步：黄色【当 🟢 被点击】 ➔ 下方拼接黄色【重复执行】；\n" +
                    "② 第二步：在【重复执行】内部放入蓝色【运动】里的【移动 10 步】；\n" +
                    "③ 第三步：紧接着在下方放入蓝色【运动】里的【碰到边缘就反弹】！\n\n" +
                    "💡 **关键避坑**：在最顶端【当 🟢 被点击】正下方放一块蓝色【将旋转方式设为 左右翻转】，这样小猫反弹时就不会大头朝下颠倒过来啦！"
                }
            }

            q.contains("变量") || q.contains("得分") || q.contains("计分") -> {
                "💡【 Scratch 变量与计分系统制作指南 】✨\n\n" +
                "变量就像一个贴着标签的小储物盒，随时可以存放或修改数字：\n\n" +
                "1. 📊 **新建变量**：点击左侧深橙色【变量】分类 ➔ 【建立一个变量】，命名为“得分”。\n" +
                "2. 🔄 **初始化归零**：拖出【将 得分 设为 0】放在【当 🟢 被点击】正下方，保证每次开局分数清零。\n" +
                "3. 🎯 **触发加分**：在吃到金币或击中目标的逻辑里，执行深橙色【将 得分 增加 1】并播放叮当声！"
            }

            q.contains("血条") || q.contains("生命") || q.contains("失败") || q.contains("游戏结束") || q.contains("扣血") -> {
                if (isExpert) {
                    "🎓【 专家进阶：角色血条状态机、受击无敌帧与防重复扣血算法 】✨\n\n" +
                    "📌 **1. 游戏架构痛点分析**：\n" +
                    "• **一帧重复伤害 Bug**：如果在【重复执行】中只写【如果 <碰到 敌人?> 那么 [将 生命值 增加 -1]】，由于 Scratch 每秒运行 30 帧，角色碰到敌人的 0.5 秒内会瞬间触发 15 次扣血导致暴毙！\n\n" +
                    "📌 **2. 工业级防重扣血与无敌闪烁状态机**：\n" +
                    "• **建立变量**：新建全局变量【生命值】与【处于无敌状态】（0=正常，1=无敌）；\n" +
                    "• **受击判定逻辑**：\n" +
                    "  【如果 < <碰到 敌人?> 且 <(处于无敌状态) = 0> > 那么】\n" +
                    "    ① 【将 处于无敌状态 设为 1】；\n" +
                    "    ② 【将 生命值 增加 -1】；\n" +
                    "    ③ 【重复执行 3 次】 [将 虚像 特效增加 50, 等待 0.1秒, 将 虚像 特效设为 0, 等待 0.1秒]（无敌受击闪烁反馈）；\n" +
                    "    ④ 【将 处于无敌状态 设为 0】；\n" +
                    "    ⑤ 【如果 <(生命值) <= 0> 那么 [广播 GameOver 并停止全部脚本]】。\n\n" +
                    "💡 **启发思考题**：如何利用画笔扩展或克隆体为头顶绘制一条跟随角色移动的动态百分比血条？"
                } else {
                    "⚡️【 快速极速速答：制作生命值与游戏结束 】✨\n\n" +
                    "极简 3 步拼搭：\n" +
                    "① **第一步（初始化血量）**：点击深橙色【变量】分类 ➔ 建立变量“生命值”。在【当 🟢 被点击】下方放【将 生命值 设为 3】；\n" +
                    "② **第二步（碰到敌人扣血）**：在循环中放黄色【如果 <碰到 敌人?> 那么】 ➔ 【将 生命值 增加 -1】 ➔ 【等待 1 秒】（非常关键！防止1秒内把血扣光）；\n" +
                    "③ **第三步（血量为0结束）**：下方拼接黄色【如果 <(生命值) < 1> 那么】 ➔ 黄色【停止 全部脚本】并在屏幕中央显示 Game Over！\n\n" +
                    "💡 **关键避坑**：扣血后一定要加上【等待 1 秒】，给角色逃跑的机会！"
                }
            }

            q.contains("跟随") || q.contains("鼠标") -> {
                "💡【 Scratch 角色跟随鼠标指南 】✨\n\n" +
                "1. 🐾 **平滑跟随**：在【当 🟢 被点击】下方放【重复执行】，里面放蓝色【面向 鼠标指针】和蓝色【移动 5 步】。\n" +
                "2. ⚡️ **瞬移跟随**：在【重复执行】中直接放蓝色【移到 鼠标指针】，角色就会形影不离地粘在鼠标光标上！"
            }

            q.contains("广播") || q.contains("消息") || q.contains("传信") -> {
                "💡【 Scratch 广播与角色通信指南 】✨\n\n" +
                "广播就像两个角色在用对讲机呼叫配合：\n\n" +
                "1. 📢 **呼叫方（发送广播）**：角色 A 在达成某个事件时，执行黄色【事件】里的【广播 游戏胜利】。\n" +
                "2. 👂 **应答方（接收广播）**：角色 B 使用黄色【事件】里的【当接收到 游戏胜利】作为脚本开头，并在下方拼接【显示】或【跳舞】！"
            }

            // =========================================================================
            // 9. 智能兜底：分析提问类型，拒绝千篇一律的幽灵模板！
            // =========================================================================
            else -> {
                val isConceptQuestion = q.contains("什么") || q.contains("为什么") || q.contains("怎么理解") ||
                        q.contains("区别") || q.contains("意义") || q.contains("原理") || q.contains("含义") ||
                        q.contains("概念") || q.contains("作用") || q.contains("是谁")

                if (isConceptQuestion) {
                    // 意图路线 B/C：概念启发与逻辑分析，绝不生搬硬套拖拽积木！
                    "💡【 编程小侦探：探索这个概念背后的逻辑 】✨\n\n" +
                    "小朋友提了一个非常棒的探索型问题！在编程的世界里，这个概念主要是帮助我们**更好地组织代码和理清思路**：\n\n" +
                    "1. 🔍 **核心奥秘**：它是计算机科学家为了让程序变得更聪明、更有序而发明的思维工具。\n" +
                    "2. 🌟 **生活中的小启发**：就像我们整理书包、分类玩具一样，编程也是把大问题拆解成一个小模块去逐步解决。\n" +
                    "3. 🚀 **在 Scratch 里的体验**：无论是舞台上的角色、变量还是循环判断，处处都闪耀着这个逻辑的光芒！\n\n" +
                    "宝贝如果对某一个具体的积木或效果有疑问，可以随时给精灵姐姐更详细地提问哦，我们一起动手试一试！🐱"
                } else {
                    // 意图路线 A：实操启发与探索指南
                    "💡【 Scratch 创意实操探索指南 】✨\n\n" +
                    "想在 Scratch 舞台上实现这个奇妙的想法，可以按照下面的三步逻辑来构思哦：\n\n" +
                    "1. 🎯 **明确触发条件**：去黄色【事件】分类选择何时开始（比如点击绿旗、按下按键、或者碰到其他角色）。\n" +
                    "2. 🧩 **组合动作与外观**：\n" +
                    "   • 动作变化去蓝色【运动】分类；\n" +
                    "   • 视觉效果去紫色【外观】或粉色【声音】分类；\n" +
                    "   • 持续检测去黄色【控制】分类（使用【重复执行】或【如果...那么】）。\n" +
                    "3. 🚀 **动手测试与微调**：把积木拼接在一起，点击绿旗亲自运行看看，根据效果调整数值吧！✨"
                }
            }
        }
    }

    data class ContentModerationResult(
        val isSafe: Boolean,
        val reason: String
    )

    /**
     * AI 自动化内容风控过滤 (Task 3)
     * 自动拦截开源大厅与同伴互动评论中的不良文本、攻击性言论或敏感违规词汇。
     */
    suspend fun moderateTextContent(content: String): ContentModerationResult = withContext(Dispatchers.IO) {
        if (content.isBlank()) return@withContext ContentModerationResult(true, "内容为空")

        // 本地敏感词快速前置检测
        val blackList = listOf("死", "杀", "脏话", "蠢", "笨蛋", "滚", "垃圾", "坏蛋", "作弊", "私聊")
        val lowerContent = content.lowercase()
        for (word in blackList) {
            if (lowerContent.contains(word)) {
                return@withContext ContentModerationResult(
                    isSafe = false,
                    reason = "触发少儿社区敏感词 [$word]，请修改语言后发布。"
                )
            }
        }

        val prompt = """
            你是一个少儿 Scratch 编程开源社区的风控安全审核员。请审核以下文本（作品名称、作品说明或学生评论）是否符合少儿健康社区规范。
            审查标准：不得包含违规、暴力、辱骂、负面情绪、人身攻击、泄露隐私或诱导非学习行为。
            受审文本："$content"

            请仅返回如下标准 JSON 格式，不要包含任何 markdown 标签或多余说明：
            {"isSafe": true或false, "reason": "审核说明或理由"}
        """.trimIndent()

        try {
            val responseText = generateContent(prompt)
            val cleanJson = responseText.replace("```json", "").replace("```", "").trim()
            val json = JSONObject(cleanJson)
            val isSafe = json.optBoolean("isSafe", true)
            val reason = json.optString("reason", if (isSafe) "内容符合社区规范" else "不合规文本")
            ContentModerationResult(isSafe, reason)
        } catch (e: Exception) {
            ContentModerationResult(true, "内容正常")
        }
    }

    /**
     * 调用 AI 自动评测并解析特定的 JSON 得分格式
     */
    suspend fun evaluateScratchWork(
        taskName: String,
        taskDetail: String,
        workName: String,
        codeJson: String
    ): EvaluationResult = withContext(Dispatchers.IO) {
        // RAG 知识库检索增强 (Task 7)
        val ragKnowledge = EducationalKnowledgeBase.retrieveRelevantContext("$taskName $taskDetail", codeJson)

        val systemPrompt = """
            你是一个充满爱心的资深少儿编程(Scratch 3.0)教学评测专家。请针对学生交上来的Scratch JSON积木代码进行专业而亲切的自动评测。

            $ragKnowledge

            任务要求：
            - 任务名称：$taskName
            - 任务详情：$taskDetail
            - 学生作品名称：$workName

            请严格从以下四个维度进行打分（各项分值不能超越其上限）：
            1. 语法合规性(grammarScore): 满分 25 分
            2. 逻辑完整性(logicScore): 满分 30 分
            3. 任务匹配度(taskMatchScore): 满分 25 分
            4. 创意实现度(creativeScore): 满分 20 分
            综合得分即为这四项总和（满分 100）。

            关于 "optimizationSuggestions" 字段，你必须遵守以下专门针对小学3-6年级小学生的认知评测规范：
            - 【极度温柔有爱】：先热情赞美孩子付出的努力和创意，不可打击自信心。多用可爱的卡通表情符号。
            - 【具体的具体拼搭指南】：绝对严禁宽泛空洞的评价（如“进一步完善逻辑”、“加强循环理解”等）。必须给出一看就懂的 ①②③ 极简改进步骤（说明找到哪个积木颜色分类，找什么名字的积木，拼在什么积木下面或里面，或修改什么变量值）。
            - 【比喻解说】：如果指出错漏，用拟人化或简单比喻（比如“这里有个孤单的小猫积木没有排入队伍中哦～”、“让控制哨兵更好地帮你把关吧！”）。
            - 字数简短精悍，控制在150字以内，排版清爽。

            你必须最终输出一个合法的 JSON 格式字符串，不需要任何 markdown 的 ```json 包裹标记，其属性必须完全等于：
            {
               "grammarScore": <数值>,
               "logicScore": <数值>,
               "taskMatchScore": <数值>,
               "creativeScore": <数值>,
               "averageScore": <各项加和数值>,
               "optimizationSuggestions": "在此填入符合上文规范的少儿亲和式优化评语"
            }
        """.trimIndent()

        val prompt = "$systemPrompt\n\n学生 Scratch 积木代码如下：\n$codeJson"
        val responseText = generateContent(prompt)

        // 尝试解析返回的 JSON，若非标准 JSON 则做容错提取或提供默认分数
        try {
            // 清洗掉可能多余的 markdown 标注
            val cleanJson = responseText.replace("```json", "").replace("```", "").trim()
            val json = JSONObject(cleanJson)
            val grammar = json.optInt("grammarScore", 20)
            val logic = json.optInt("logicScore", 24)
            val match = json.optInt("taskMatchScore", 20)
            val creative = json.optInt("creativeScore", 15)
            val suggestions = json.optString("optimizationSuggestions", "AI 评语提取：\n$responseText")

            ScratchWorkEvaluator.sanitize(EvaluationResult(
                grammarScore = grammar,
                logicScore = logic,
                taskMatchScore = match,
                creativeScore = creative,
                averageScore = grammar + logic + match + creative,
                suggestions = suggestions
            ))
        } catch (e: Exception) {
            ScratchWorkEvaluator.evaluate(codeJson)
        }
    }

    data class EvaluationResult(
        val grammarScore: Int,
        val logicScore: Int,
        val taskMatchScore: Int,
        val creativeScore: Int,
        val averageScore: Int,
        val suggestions: String
    )
}
