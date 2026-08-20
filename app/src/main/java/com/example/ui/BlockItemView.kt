package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

@Composable
fun ScratchBlockNodeView(
    node: ScratchBlockNode,
    blocksMap: JSONObject?,
    modifier: Modifier = Modifier
) {
    when (node) {
        is ScratchBlockNode.Simple -> {
            SimpleBlockItemView(
                opcode = node.opcode,
                blockJson = node.blockJson,
                blocksMap = blocksMap,
                stepIndex = node.stepIndex,
                modifier = modifier
            )
        }
        is ScratchBlockNode.Container -> {
            ContainerBlockItemView(
                node = node,
                blocksMap = blocksMap,
                modifier = modifier
            )
        }
    }
}

@Composable
fun SimpleBlockItemView(
    opcode: String,
    blockJson: JSONObject?,
    blocksMap: JSONObject?,
    stepIndex: Int? = null,
    modifier: Modifier = Modifier
) {
    val color = BlockTranslator.getBlockColor(opcode)
    val segments = BlockTextFormatter.formatBlock(opcode, blockJson, blocksMap)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(color = color)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 嵌套层级序号指示器（如 ↳ 1，与学生端 Scratch 工作台视觉完全统一）
            if (stepIndex != null) {
                Box(
                    modifier = Modifier
                        .background(
                            color = Color(0xFF1E293B).copy(alpha = 0.4f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "↳ $stepIndex",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            } else if (opcode == "event_whenflagclicked") {
                Text(
                    text = "当 🟢 被点击",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                return@Row
            }

            segments.forEach { segment ->
                when (segment) {
                    is BlockSegment.Text -> {
                        // 如果是边缘反弹或设置旋转方式，补充学生端同款趣味图标
                        val contentText = when (opcode) {
                            "motion_ifonedgebounce" -> if (segment.content.contains("反弹")) "${segment.content} 💥" else segment.content
                            else -> segment.content
                        }
                        Text(
                            text = contentText,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    is BlockSegment.Parameter -> {
                        val paramText = when {
                            opcode == "motion_setrotationstyle" -> "${segment.value} 🔄"
                            else -> segment.value
                        }
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .background(
                                    color = Color.White.copy(alpha = 0.28f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = paramText,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContainerBlockItemView(
    node: ScratchBlockNode.Container,
    blocksMap: JSONObject?,
    modifier: Modifier = Modifier
) {
    val containerColor = BlockTranslator.getBlockColor(node.opcode)
    val spineColor = containerColor

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFFFBEB))
            .border(1.dp, containerColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
    ) {
        // 1. C型积木顶部头部栏（例如：重复执行 [无限循环] ♾）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(containerColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (node.stepIndex != null) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color(0xFF1E293B).copy(alpha = 0.4f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "↳ ${node.stepIndex}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Text(
                    text = node.headerTitle,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        // 2. C型积木开口区域（内部子积木列表 + 左侧连接骨架柱）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // 左侧 C 型连接立柱（提供直观的 Scratch 容器结构视觉引导）
            Box(
                modifier = Modifier
                    .width(14.dp)
                    .fillMaxHeight()
                    .background(spineColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "↳",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // 内部子积木堆叠区
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (node.children.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .padding(8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "（循环内部暂无嵌套积木）",
                            color = Color(0xFF9E9E9E),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    node.children.forEach { childNode ->
                        ScratchBlockNodeView(
                            node = childNode,
                            blocksMap = blocksMap
                        )
                    }
                }
            }
        }

        // 3. 如果存在 else 分支（如 如果...那么...否则）
        if (node.elseChildren.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(containerColor)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "否则",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                Box(
                    modifier = Modifier
                        .width(14.dp)
                        .fillMaxHeight()
                        .background(spineColor)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    node.elseChildren.forEach { elseNode ->
                        ScratchBlockNodeView(
                            node = elseNode,
                            blocksMap = blocksMap
                        )
                    }
                }
            }
        }

        // 4. C型积木底部闭合底边
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(10.dp)
                .background(
                    color = containerColor,
                    shape = RoundedCornerShape(bottomEnd = 6.dp)
                )
        )
    }
}

// 兼容旧接口的顶层调用
@Composable
fun BlockItemView(
    opcode: String,
    blockJson: JSONObject?,
    blocksMap: JSONObject?,
    modifier: Modifier = Modifier
) {
    SimpleBlockItemView(
        opcode = opcode,
        blockJson = blockJson,
        blocksMap = blocksMap,
        stepIndex = null,
        modifier = modifier
    )
}
