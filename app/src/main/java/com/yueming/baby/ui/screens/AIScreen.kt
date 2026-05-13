package com.yueming.baby.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yueming.baby.data.*
import kotlinx.coroutines.launch

@Composable
fun AIScreen() {
    val babyInfo by DataManager.babyInfo.collectAsState()
    val timeline by DataManager.timeline.collectAsState()
    val aiProfiles by DataManager.aiProfiles.collectAsState()
    val messages by DataManager.chatMessages.collectAsState()

    val isConfigured = DataManager.isAIConfigured
    val activeProfile = DataManager.activeAIProfile
    var input by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showProfilePicker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val ageMonths = DataManager.getAgeInMonths(babyInfo.birthDate)
    val ageDays = DataManager.getAgeInDays(babyInfo.birthDate)

    // Build baby context for system prompt
    val babyContext = remember(babyInfo, timeline) {
        val recentRecords = timeline
            .sortedByDescending { it.date }
            .take(3)
            .joinToString("\n") { "- ${it.date}: ${it.title} (${it.category})" }
        """
宝宝信息：
- 名字：${babyInfo.name}（昵称：${babyInfo.nickname}）
- 性别：${if (babyInfo.gender == "boy") "男孩" else "女孩"}
- 出生日期：${babyInfo.birthDate}
- 当前年龄：${ageMonths}个月（${ageDays}天）

最近3条时间线记录：
${recentRecords.ifBlank { "暂无记录" }}
        """.trimIndent()
    }

    if (!isConfigured) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.SmartToy, null, Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
                Spacer(Modifier.height(16.dp))
                Text("AI 助手尚未配置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text("在设置中配置 API Key，即可获得基于宝宝数据的智能育儿建议",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Text("支持的模型：DeepSeek / Kimi / MiniMax / GLM / Qwen / GPT / Claude",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        return
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("AI 育儿助手",
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Box {
                AssistChip(
                    onClick = { showProfilePicker = true },
                    label = { Text(activeProfile?.name ?: "未配置", fontSize = 11.sp) },
                    shape = RoundedCornerShape(12.dp)
                )
                DropdownMenu(
                    expanded = showProfilePicker,
                    onDismissRequest = { showProfilePicker = false }
                ) {
                    aiProfiles.forEach { profile ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(profile.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                    Text("${profile.model}", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            onClick = {
                                DataManager.setActiveAIProfile(profile.id)
                                showProfilePicker = false
                            },
                            leadingIcon = if (profile.isActive) {
                                { Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) }
                            } else null
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.SmartToy, null, Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
                            Spacer(Modifier.height(12.dp))
                            Text("${babyInfo.nickname}现在 ${ageMonths} 个月了",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text("向我提问，我会基于成长数据给出建议",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            Spacer(Modifier.height(16.dp))
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                )
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("已注入宝宝信息作为上下文",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        babyContext.take(200) + if (babyContext.length > 200) "..." else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
            items(messages) { msg ->
                val isUser = msg.role == "user"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .clip(RoundedCornerShape(
                                topStart = 18.dp, topEnd = 18.dp,
                                bottomStart = if (isUser) 18.dp else 4.dp,
                                bottomEnd = if (isUser) 4.dp else 18.dp
                            ))
                            .background(if (isUser) Color(0xFFF8C8D8) else MaterialTheme.colorScheme.surfaceContainer)
                            .padding(14.dp)
                    ) {
                        Text(msg.content, style = MaterialTheme.typography.bodySmall,
                            color = if (isUser) Color(0xFF3D2C2C) else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            if (isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            repeat(3) {
                                Box(Modifier.size(8.dp).background(Color(0xFFEC407A), RoundedCornerShape(4.dp)))
                            }
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("输入育儿问题...") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = !isLoading,
                shape = RoundedCornerShape(20.dp)
            )
            IconButton(
                onClick = {
                    val userMsg = ChatMessage(
                        id = "msg-${System.currentTimeMillis()}",
                        role = "user",
                        content = input.trim(),
                        timestamp = System.currentTimeMillis()
                    )
                    DataManager.addMessage(userMsg)
                    input = ""
                },
                enabled = !isLoading && input.isNotBlank()
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, null,
                    tint = if (input.isNotBlank()) Color(0xFFEC407A)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            }
        }
    }
}
