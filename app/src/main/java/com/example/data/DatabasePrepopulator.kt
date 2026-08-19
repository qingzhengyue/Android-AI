package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DatabasePrepopulator {

    suspend fun populateIfEmpty(db: AppDatabase) = withContext(Dispatchers.IO) {
        val dao = db.appDao
        
        // --- 数据清理：修复早期测试数据导致学号带有 S 的问题 ---
        try {
            db.openHelper.writableDatabase.execSQL("UPDATE student SET studentNumber = REPLACE(studentNumber, 'S', '') WHERE studentNumber LIKE '%S%'")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 1. 确保教师王老师存在并获取其 teacherId
        var existingTeacher = dao.getTeacherByWorkId("T1001")
        val teacherId = if (existingTeacher == null) {
            dao.insertTeacher(
                Teacher(
                    workId = "T1001",
                    name = "王老师",
                    password = "123456"
                )
            ).toInt()
        } else {
            existingTeacher.teacherId
        }

        // 2. 检查并补全班级
        val existingClasses = dao.getAllClassesList()
        var classId1 = existingClasses.firstOrNull { it.className == "三年级一班" }?.classId ?: 0
        var classId2 = existingClasses.firstOrNull { it.className == "四年级二班" }?.classId ?: 0

        if (existingClasses.isEmpty()) {
            classId1 = dao.insertClass(
                ClassEntity(
                    className = "三年级一班",
                    grade = "三年级",
                    teacherId = teacherId
                )
            ).toInt()

            classId2 = dao.insertClass(
                ClassEntity(
                    className = "四年级二班",
                    grade = "四年级",
                    teacherId = teacherId
                )
            ).toInt()
        }

        // 3. 检查并补全学生数据
        val existingStudents = dao.getAllStudents()
        var studentId1 = existingStudents.firstOrNull { it.studentNumber == "3101" }?.studentId ?: 0
        var studentId2 = existingStudents.firstOrNull { it.studentNumber == "3102" }?.studentId ?: 0
        var studentId3 = existingStudents.firstOrNull { it.studentNumber == "3103" }?.studentId ?: 0

        if (existingStudents.isEmpty() && classId1 != 0 && classId2 != 0) {
            val class1Names = listOf(
                "张小帅", "李小美", "周杰伦", "蔡徐坤", "谷爱凌", 
                "陈小明", "林华华", "王壮壮", "徐佳佳", "刘飞飞", 
                "杨晨晨", "黄洋洋", "周涛涛", "吴圆圆", "徐婷婷", 
                "孙蕾蕾", "胡帅帅", "朱佩佩", "高健健", "林欢欢", 
                "何欣欣", "邓鹏鹏", "郭萌萌", "马丽丽", "罗阳阳"
            )

            for (i in 0 until 25) {
                val sNum = (3101 + i).toString()
                val sName = class1Names.getOrElse(i) { "学生$sNum" }
                val sId = dao.insertStudent(
                    Student(
                        studentNumber = sNum,
                        name = sName,
                        password = "123456",
                        classId = classId1
                    )
                ).toInt()
                if (i == 0) studentId1 = sId
                if (i == 1) studentId2 = sId
                if (i == 2) studentId3 = sId
            }

            val class2Names = listOf(
                "王小飞", "赵丽颖", "易烊千玺", "迪丽热巴", "王源", 
                "王俊凯", "肖战", "王一博", "杨幂", "赵露思", 
                "张艺兴", "白敬亭", "吴磊", "关晓彤", "张子枫", 
                "彭昱畅", "郭麒麟", "毛不易", "周深", "薛之谦", 
                "邓紫棋", "李宇春", "张靓颖", "华晨宇", "张杰"
            )

            for (i in 0 until 25) {
                val sNum = (4201 + i).toString()
                val sName = class2Names.getOrElse(i) { "学生$sNum" }
                dao.insertStudent(
                    Student(
                        studentNumber = sNum,
                        name = sName,
                        password = "123456",
                        classId = classId2
                    )
                )
            }
        }

        // 4. 插入或检查 AI 教学配置 (ai_teaching_config)
        if (classId1 != 0 && dao.getConfigByClassId(classId1) == null) {
            dao.insertConfig(
                AiTeachingConfig(
                    classId = classId1,
                    teacherId = teacherId,
                    aiHintLevel = "入门级",
                    codeGenerationLimit = 0,
                    creativeGuideDailyLimit = 5
                )
            )
        }

        if (classId2 != 0 && dao.getConfigByClassId(classId2) == null) {
            dao.insertConfig(
                AiTeachingConfig(
                    classId = classId2,
                    teacherId = teacherId,
                    aiHintLevel = "进阶级",
                    codeGenerationLimit = 1,
                    creativeGuideDailyLimit = 8
                )
            )
        }

        // 5. 检查并补全常规 Scratch 学习任务
        val existingTasks = dao.getAllTasksList()
        var taskId1 = existingTasks.firstOrNull { it.taskName.contains("猫咪漫步") }?.taskId ?: 0
        var taskId2 = existingTasks.firstOrNull { it.taskName.contains("水果大作战") }?.taskId ?: 0

        val now = System.currentTimeMillis()
        val thirtyDaysMs = 30 * 24 * 60 * 60 * 1000L

        if (existingTasks.isEmpty() && classId1 != 0 && classId2 != 0) {
            taskId1 = dao.insertTask(
                LearningTask(
                    taskName = "猫咪漫步游戏 (左右弹跳)",
                    taskDetail = "小猫漫步活动是 Scratch 必修基础。要求在舞台放置小猫角色，使其启动后一直面向前方走动，碰到边缘后反弹回来。你必须学习使用【重复执行】、【移动 10 步】、【碰到边缘反弹】和【旋转方式设为左右翻转】这四个核心积木，并配上一张漂亮的海洋或森林舞台背景。",
                    grade = "三年级",
                    deadline = "2026-06-30",
                    deadlineTime = now + thirtyDaysMs,
                    teacherId = teacherId,
                    classId = classId1,
                    status = "进行中"
                )
            ).toInt()

            taskId2 = dao.insertTask(
                LearningTask(
                    taskName = "水果大作战 - 接水果趣味小游戏",
                    taskDetail = "设计一个接糖果或者接苹果的捕获类游戏。苹果在上方产生随机的 X 轴并以一个速度向下坠落，玩家使用键盘左右方向键控制碗（Bowl）左右移动接住掉落的水果。设计加分点：如果碗接到水果则播放 Pop 音效、分数变量加 1，并重置水果位置到最上方。",
                    grade = "三年级",
                    deadline = "2026-07-15",
                    deadlineTime = now + (thirtyDaysMs * 2),
                    teacherId = teacherId,
                    classId = classId1,
                    status = "进行中"
                )
            ).toInt()

            dao.insertTask(
                LearningTask(
                    taskName = "走迷宫 (碰到黑色反弹)",
                    taskDetail = "制作一个经典的键盘控方向‘走迷宫逃跑’游戏。画笔绘制深色迷宫围墙边线颜色。操作甲虫或小恐龙在迷宫内前进。重点编程逻辑：如果玩家操控角色碰到了‘黑色的迷宫围墙边线颜色’，则执行角色反向后退 15 步的指令；当走到重点黄色金币时发出 Clap 音乐奖励声，游戏获胜。",
                    grade = "四年级",
                    deadline = "2026-06-25",
                    deadlineTime = now + thirtyDaysMs,
                    teacherId = teacherId,
                    classId = classId2,
                    status = "进行中"
                )
            )

            dao.insertTask(
                LearningTask(
                    taskName = "神奇的电子琴 - 太空音效器",
                    taskDetail = "创建炫酷的外星太空打击乐组合！通过绑定按键键盘按键 A、S、D、F 分别对应四个不同的声音，并联动发声的四名乐队主唱角色的摇滚跳跃换发型动画状态，探索【播放声音】、【当按下特定键】和【广播和接收消息】的联动运用。",
                    grade = "四年级",
                    deadline = "2026-08-01",
                    deadlineTime = now + (thirtyDaysMs * 3),
                    teacherId = teacherId,
                    classId = classId2,
                    status = "进行中"
                )
            )
        }

        // 6. 检查并补全示例作品 (ScratchWork) 与 评测报告
        val existingWorks = dao.getAllWorksList()
        if (existingWorks.isEmpty() && studentId1 != 0 && taskId1 != 0) {
            val sampleCatCode = """{"targets":[{"isStage":true,"name":"Stage","variables":{},"lists":{},"broadcasts":{},"blocks":{},"comments":{},"currentCostume":0,"costumes":[{"name":"背景1","bitmapResolution":1,"dataFormat":"svg","assetId":"cd21584322f79459ecb5864133b44723","md5ext":"cd21584322f79459ecb5864133b44723.svg","rotationCenterX":240,"rotationCenterY":180}],"sounds":[],"volume":100,"layerOrder":0},{"isStage":false,"name":"角色1","variables":{},"lists":{},"broadcasts":{},"blocks":{"a":{"opcode":"event_whenflagclicked","next":"b","parent":null,"inputs":{},"fields":{},"shadow":false,"topLevel":true,"x":100,"y":100},"b":{"opcode":"control_forever","next":null,"parent":"a","inputs":{"SUBSTACK":[2,"c"]},"fields":{},"shadow":false,"topLevel":false},"c":{"opcode":"motion_movesteps","next":"d","parent":"b","inputs":{"STEPS":[1,[4,"10"]]},"fields":{},"shadow":false,"topLevel":false},"d":{"opcode":"motion_ifonedgebounce","next":null,"parent":"c","inputs":{},"fields":{},"shadow":false,"topLevel":false}},"comments":{},"currentCostume":0,"costumes":[{"name":"造型1","bitmapResolution":1,"dataFormat":"svg","assetId":"b7853f557e44241d288a7593e62c0d58","md5ext":"b7853f557e44241d288a7593e62c0d58.svg","rotationCenterX":48,"rotationCenterY":50}],"sounds":[],"volume":100,"visible":true,"x":0,"y":0,"size":100,"direction":90,"draggable":false,"rotationStyle":"all around","layerOrder":1}],"monitors":[],"extensions":[],"meta":{"semver":"3.0.0","vm":"0.2.0","agent":"Android"}}"""
            val sampleFruitCode = """{"targets":[{"isStage":true,"name":"Stage","variables":{},"lists":{},"broadcasts":{},"blocks":{},"comments":{},"currentCostume":0,"costumes":[{"name":"背景1","bitmapResolution":1,"dataFormat":"svg","assetId":"cd21584322f79459ecb5864133b44723","md5ext":"cd21584322f79459ecb5864133b44723.svg","rotationCenterX":240,"rotationCenterY":180}],"sounds":[],"volume":100,"layerOrder":0},{"isStage":false,"name":"碗","variables":{},"lists":{},"broadcasts":{},"blocks":{"a":{"opcode":"event_whenflagclicked","next":"b","parent":null,"inputs":{},"fields":{},"shadow":false,"topLevel":true,"x":100,"y":100},"b":{"opcode":"control_forever","next":null,"parent":"a","inputs":{"SUBSTACK":[2,"c"]},"fields":{},"shadow":false,"topLevel":false},"c":{"opcode":"control_if","next":null,"parent":"b","inputs":{"CONDITION":[2,"d"],"SUBSTACK":[2,"e"]},"fields":{},"shadow":false,"topLevel":false},"d":{"opcode":"sensing_keypressed","next":null,"parent":"c","inputs":{},"fields":{"KEY_OPTION":["right arrow",null]},"shadow":false,"topLevel":false},"e":{"opcode":"motion_changexby","next":null,"parent":"c","inputs":{"DX":[1,[4,"10"]]},"fields":{},"shadow":false,"topLevel":false}},"comments":{},"currentCostume":0,"costumes":[{"name":"造型1","bitmapResolution":1,"dataFormat":"svg","assetId":"b7853f557e44241d288a7593e62c0d58","md5ext":"b7853f557e44241d288a7593e62c0d58.svg","rotationCenterX":48,"rotationCenterY":50}],"sounds":[],"volume":100,"visible":true,"x":0,"y":0,"size":100,"direction":90,"draggable":false,"rotationStyle":"all around","layerOrder":1}],"monitors":[],"extensions":[],"meta":{"semver":"3.0.0","vm":"0.2.0","agent":"Android"}}"""

            val eval1 = ScratchWorkEvaluator.evaluate(
                sampleCatCode,
                "猫咪趣味漫步",
                "让小猫在舞台上一直左右移动漫步，碰到边缘自动反弹",
                "张小帅的猫咪漫步作品"
            )
            val workId1 = dao.insertWork(
                ScratchWork(
                    workName = "张小帅的猫咪漫步作品",
                    workCode = sampleCatCode,
                    studentId = studentId1,
                    classId = classId1,
                    taskId = taskId1,
                    submitCount = 1,
                    submitTime = now - 2 * 3600 * 1000L,
                    reviewStatus = "已打分",
                    teacherScore = eval1.averageScore,
                    teacherComment = "双向弹跳逻辑写得非常规范，小猫咪非常欢快地动起来了，加油！",
                    teacherReviewTime = now - 1 * 3600 * 1000L
                )
            ).toInt()

            dao.insertAiReport(
                WorkAiReport(
                    workId = workId1,
                    studentId = studentId1,
                    grammarScore = eval1.grammarScore,
                    logicScore = eval1.logicScore,
                    taskMatchScore = eval1.taskMatchScore,
                    creativeScore = eval1.creativeScore,
                    averageScore = eval1.averageScore,
                    optimizationSuggestions = eval1.suggestions,
                    reportTime = now - 2 * 3600 * 1000L
                )
            )

            if (studentId2 != 0 && taskId2 != 0) {
                val eval2 = ScratchWorkEvaluator.evaluate(
                    sampleFruitCode,
                    "接水果大作战",
                    "用键盘方向键控制碗左右移动，接住从上方掉落的水果",
                    "李小美的接水果大作战"
                )
                val workId2 = dao.insertWork(
                    ScratchWork(
                        workName = "李小美的接水果大作战",
                        workCode = sampleFruitCode,
                        studentId = studentId2,
                        classId = classId1,
                        taskId = taskId2,
                        submitCount = 1,
                        submitTime = now - 4 * 3600 * 1000L,
                        reviewStatus = "待审核"
                    )
                ).toInt()

                dao.insertAiReport(
                    WorkAiReport(
                        workId = workId2,
                        studentId = studentId2,
                        grammarScore = eval2.grammarScore,
                        logicScore = eval2.logicScore,
                        taskMatchScore = eval2.taskMatchScore,
                        creativeScore = eval2.creativeScore,
                        averageScore = eval2.averageScore,
                        optimizationSuggestions = eval2.suggestions,
                        reportTime = now - 4 * 3600 * 1000L
                    )
                )
            }

            if (studentId3 != 0) {
                val eval3 = ScratchWorkEvaluator.evaluate(
                    sampleCatCode,
                    "猫咪趣味漫步",
                    "让小猫在舞台上一直左右移动漫步，碰到边缘自动反弹",
                    "周杰伦的猫咪左右摇摆"
                )
                val workId3 = dao.insertWork(
                    ScratchWork(
                        workName = "周杰伦的猫咪左右摇摆",
                        workCode = sampleCatCode,
                        studentId = studentId3,
                        classId = classId1,
                        taskId = taskId1,
                        submitCount = 1,
                        submitTime = now - 6 * 3600 * 1000L,
                        reviewStatus = "已评测",
                        teacherScore = eval3.averageScore,
                        teacherComment = "积木结构完整，小猫动作流畅，表现优秀！",
                        teacherReviewTime = now - 5 * 3600 * 1000L
                    )
                ).toInt()

                dao.insertAiReport(
                    WorkAiReport(
                        workId = workId3,
                        studentId = studentId3,
                        grammarScore = eval3.grammarScore,
                        logicScore = eval3.logicScore,
                        taskMatchScore = eval3.taskMatchScore,
                        creativeScore = eval3.creativeScore,
                        averageScore = eval3.averageScore,
                        optimizationSuggestions = eval3.suggestions,
                        reportTime = now - 6 * 3600 * 1000L
                    )
                )
            }

            // 7. 插入 AI 问答对话历史记录 (AiAssistRecord)
            dao.insertAssistRecord(
                AiAssistRecord(
                    studentId = studentId1,
                    classId = classId1,
                    assistType = "语法纠错",
                    assistTypeInt = 1,
                    callTime = now - 3 * 3600 * 1000L,
                    requestContent = "我的小猫为什么碰到边缘之后头朝下倒过来了？",
                    aiResult = "💡 这是因为 Scratch 默认角色的旋转方式是全方位旋转哦！你可以加入一个【将旋转方式设为左右翻转】的积木块，这样小猫反弹回来时就会正常直立，不会倒立啦！✨",
                    draftId = null
                )
            )

            dao.insertAssistRecord(
                AiAssistRecord(
                    studentId = studentId1,
                    classId = classId1,
                    assistType = "创意引导",
                    assistTypeInt = 2,
                    callTime = now - 1 * 3600 * 1000L,
                    requestContent = "我想设计一个关于太空探险的猫咪游戏，有什么好的点子吗？",
                    aiResult = "🚀 太空猫咪探险记点子包：\n1. **重力改变**：通过减慢下落或移动速度来模拟太空微重力环境！\n2. **太空氧气值**：添加一个氧气计数器，每秒递减，必须触碰绿色的太空补给罐才能回满氧气！\n3. **流星避险**：让几块灰色陨石从右往左滚动，触碰猫咪则扣除一滴生命值！",
                    draftId = null
                )
            )
        }

        // 8. 修复与校准历史数据中可能存在的幽灵评分，确保同等代码评分完全客观一致
        try {
            val allExistingWorks = dao.getAllWorksList()
            for (w in allExistingWorks) {
                val rep = dao.getReportByWorkId(w.workId)
                if (rep == null || (rep.averageScore == 55 && rep.optimizationSuggestions.contains("缺少让小猫向前走动的【移动 10 步】") && w.workCode.contains("motion_movesteps"))) {
                    val task = dao.getTaskById(w.taskId)
                    val eval = ScratchWorkEvaluator.evaluate(
                        w.workCode,
                        task?.taskName ?: "Scratch 编程作品",
                        task?.taskDetail ?: "完成指定逻辑",
                        w.workName
                    )
                    dao.insertAiReport(
                        WorkAiReport(
                            workId = w.workId,
                            studentId = w.studentId,
                            grammarScore = eval.grammarScore,
                            logicScore = eval.logicScore,
                            taskMatchScore = eval.taskMatchScore,
                            creativeScore = eval.creativeScore,
                            averageScore = eval.averageScore,
                            optimizationSuggestions = eval.suggestions,
                            reportTime = rep?.reportTime ?: (System.currentTimeMillis() - 3600 * 1000L)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DatabasePrepopulator", "Reconciliation error: ${e.message}")
        }
    }
}
