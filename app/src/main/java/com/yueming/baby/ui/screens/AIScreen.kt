package com.yueming.baby.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yueming.baby.data.BabyInfo
import com.yueming.baby.data.ChatMessage
import com.yueming.baby.data.DataManager
import com.yueming.baby.data.belongsToBaby
import com.yueming.baby.ui.components.BabyGlassAlertDialog
import com.yueming.baby.ui.components.BabyDangerButton
import com.yueming.baby.ui.components.BabyGlassButton
import com.yueming.baby.ui.components.BabyGlassChip
import com.yueming.baby.ui.components.BabyGlassRole
import com.yueming.baby.ui.components.BabyGlassSurface
import com.yueming.baby.ui.components.BabyGlassTextField
import com.yueming.baby.ui.components.BabyGlassTitle
import com.yueming.baby.ui.components.BabyPalette
import com.yueming.baby.ui.components.BabySecondaryButton
import com.yueming.baby.ui.components.LocalBabyBottomBarClearance
import com.yueming.baby.ui.components.LocalBabyStatusBarClearance
import com.yueming.baby.ui.components.MarkdownText
import com.yueming.baby.ui.components.babyPageBackground
import com.yueming.baby.ui.motion.BabyMotion
import com.yueming.baby.ui.motion.MotionAnimatedContent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}

@Composable
fun AIScreen() {
    val babyInfo by DataManager.babyInfo.collectAsState()
    val timeline by DataManager.timeline.collectAsState()
    val messages by DataManager.chatMessages.collectAsState()
    val isLoading by DataManager.isAIProcessing.collectAsState()

    val isConfigured = DataManager.isAIConfigured
    val listState = rememberLazyListState()
    val shouldAutoScroll by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            layoutInfo.totalItemsCount == 0 || lastVisibleIndex >= layoutInfo.totalItemsCount - 2
        }
    }

    var input by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }
    val bottomBarClearance = LocalBabyBottomBarClearance.current
    val statusBarClearance = LocalBabyStatusBarClearance.current

    val ageMonths = remember(babyInfo.birthDate) { DataManager.getAgeInMonths(babyInfo.birthDate) }
    val babyContextSummary = remember(babyInfo, timeline) {
        val recent = timeline
            .filter { belongsToBaby(it.babyId, babyInfo.id) }
            .sortedByDescending { it.date }
            .take(3)
            .joinToString(" · ") { it.title }
            .ifBlank { "暂无成长记录" }
        "${babyInfo.nickname.ifBlank { "宝宝" }}，${ageMonths} 月龄，最近：$recent"
    }

    val sendMessage: (String) -> Unit = { text ->
        val trimmed = text.trim()
        if (trimmed.isNotEmpty() && !isLoading) {
            DataManager.addMessage(
                ChatMessage(
                    id = "msg-${System.currentTimeMillis()}",
                    role = "user",
                    content = trimmed,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size)
        }
    }

    LaunchedEffect(messages.lastOrNull()?.content, isLoading) {
        if (messages.isNotEmpty() && shouldAutoScroll) {
            // Token streaming updates frequently; snap to the bottom anchor instead of restarting animations.
            listState.scrollToItem(messages.size)
        }
    }

    if (!isConfigured) {
        NotConfiguredView()
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .babyPageBackground()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusBarClearance)
                .padding(bottom = bottomBarClearance)
        ) {
            CompactAssistantHeader(
                messageCount = messages.count { it.role in setOf("user", "assistant") },
                onClear = { showClearConfirm = true }
            )

            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 14.dp, bottom = 30.dp)
                ) {
                    if (messages.isEmpty()) {
                        item(key = "welcome") {
                            WelcomeContent(
                                babyInfo = babyInfo,
                                ageMonths = ageMonths,
                                babyContextSummary = babyContextSummary,
                                onSendSuggestion = sendMessage
                            )
                        }
                    }

                    items(messages, key = { it.id }) { msg ->
                        ChatBubble(
                            msg = msg,
                            isUser = msg.role == "user",
                            isLoading = isLoading && msg == messages.lastOrNull(),
                            modifier = Modifier
                                .fillMaxWidth()
                        )
                    }

                    item(key = "bottom-anchor") {
                        Spacer(Modifier.height(1.dp))
                    }
                }
            }

            InputRow(
                value = input,
                onValueChange = { input = it },
                enabled = !isLoading,
                onSend = {
                    sendMessage(input)
                    input = ""
                }
            )
        }
    }

    if (showClearConfirm) {
        BabyGlassAlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空对话历史？") },
            text = { Text("本地和 NAS 同步的 AI 对话记录都会被清空，宝宝成长数据不会受影响。") },
            confirmButton = {
                BabyDangerButton(
                    text = "清空",
                    onClick = {
                        DataManager.clearMessages()
                        showClearConfirm = false
                    }
                )
            },
            dismissButton = {
                BabySecondaryButton(text = "取消", onClick = { showClearConfirm = false })
            }
        )
    }

    BackHandler(enabled = showClearConfirm) {
        showClearConfirm = false
    }
}

@Composable
private fun CompactAssistantHeader(
    messageCount: Int,
    onClear: () -> Unit
) {
    Surface(color = Color.Transparent, tonalElevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BabyGlassTitle(
                title = "AI助手",
                subtitle = "$messageCount 条对话",
                modifier = Modifier.weight(1f)
            )
            IconButton(enabled = messageCount > 0, onClick = onClear) {
                Icon(
                    Icons.Default.DeleteSweep,
                    contentDescription = "\u6e05\u7a7a\u5bf9\u8bdd",
                    tint = if (messageCount > 0) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    }
                )
            }
        }
    }
}

@Composable
private fun WelcomeContent(
    babyInfo: BabyInfo,
    ageMonths: Int,
    babyContextSummary: String,
    onSendSuggestion: (String) -> Unit
) {
    val suggestions = listOf(
        "\u4eca\u5929\u600e\u4e48\u5b89\u6392\u5b9d\u5b9d\u4f5c\u606f\uff1f",
        "${babyInfo.nickname.ifBlank { "\u5b9d\u5b9d" }} ${ageMonths} \u6708\u9f84\u8981\u6ce8\u610f\u4ec0\u4e48\uff1f",
        "\u5e2e\u6211\u603b\u7ed3\u6700\u8fd1\u6210\u957f\u8bb0\u5f55",
        "\u7ed9\u6211\u4e00\u4e9b\u5582\u517b\u5efa\u8bae"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AssistantIntroCard(
            title = "AI 成长助手",
            subtitle = babyContextSummary,
            badge = "Ask BabyBuddy",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 146.dp)
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "\u6709\u4ec0\u4e48\u60f3\u95ee\u7684\uff1f",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(22.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            suggestions.forEach { suggestion ->
                BabyGlassChip(
                    label = suggestion,
                    selected = false,
                    onClick = { onSendSuggestion(suggestion) },
                    accent = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
@Composable
private fun ChatBubble(
    msg: ChatMessage,
    isUser: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    var appeared by remember(msg.id) { mutableStateOf(false) }

    LaunchedEffect(msg.id) {
        appeared = true
    }

    AnimatedVisibility(
        visible = appeared,
        enter = fadeIn(animationSpec = tween(160)) +
            slideInVertically(
                animationSpec = tween(durationMillis = 220, easing = BabyMotion.miuixEase),
                initialOffsetY = { it / 5 }
            ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            if (!isUser) {
                AssistantAvatar(isActive = isLoading)
                Spacer(Modifier.width(9.dp))
            }

            Column(
                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
                modifier = Modifier.widthIn(max = if (isUser) 292.dp else 330.dp)
            ) {
                Surface(
                    shape = if (isUser) {
                        RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp)
                    } else {
                        RoundedCornerShape(6.dp, 22.dp, 22.dp, 22.dp)
                    },
                    color = if (isUser) {
                        BabyPalette.Rose
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
                    },
                    tonalElevation = if (isUser) 0.dp else 2.dp,
                    shadowElevation = if (isUser) 0.dp else 1.dp,
                    border = if (isUser) null else BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
                ) {
                    Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp)) {
                        when {
                            !isUser && msg.content.isBlank() && isLoading -> ThinkingContent()
                            isUser -> Text(
                                msg.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                lineHeight = 22.sp
                            )
                            else -> MarkdownText(msg.content, style = MaterialTheme.typography.bodyMedium)
                        }

                        if (!isUser && isLoading && msg.content.isNotBlank()) {
                            Row(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TinyDots()
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "\u7ee7\u7eed\u751f\u6210\u4e2d",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
                                )
                            }
                        }
                    }
                }

                Text(
                    formatTime(msg.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f),
                    modifier = Modifier.padding(top = 4.dp, start = 6.dp, end = 6.dp)
                )
            }

            if (isUser) {
                Spacer(Modifier.width(9.dp))
                UserAvatar()
            }
        }
    }
}
@Composable
private fun AssistantAvatar(isActive: Boolean) {
    val avatarScale by animateFloatAsState(
        targetValue = if (isActive) 1.08f else 1f,
        animationSpec = BabyMotion.defaultSpatial<Float>(),
        label = "assistantAvatarScale"
    )
    val avatarColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = tween(durationMillis = 180, easing = BabyMotion.fadeThroughEase),
        label = "assistantAvatarColor"
    )
    val iconColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        animationSpec = tween(durationMillis = 180, easing = BabyMotion.fadeThroughEase),
        label = "assistantAvatarIcon"
    )

    Box(
        modifier = Modifier
            .size(34.dp)
            .graphicsLayer {
                scaleX = avatarScale
                scaleY = avatarScale
            }
            .clip(CircleShape)
            .background(avatarColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.SmartToy,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun AssistantIntroCard(
    title: String,
    subtitle: String,
    badge: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        border = BorderStroke(0.6.dp, BabyPalette.Blue.copy(alpha = 0.22f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(BabyPalette.Blue.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = BabyPalette.Blue,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = BabyPalette.Blue.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = badge,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = BabyPalette.Blue
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3
                )
            }
        }
    }
}

@Composable
private fun UserAvatar() {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ThinkingContent() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TinyDots()
        Spacer(Modifier.width(8.dp))
        Text(
            "正在结合成长记录思考",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TinyDots() {
    val transition = rememberInfiniteTransition(label = "typing-dots")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(460, delayMillis = index * 130),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "typing-dot-$index"
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha), CircleShape)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputRow(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onSend: () -> Unit
) {
    BabyGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        role = BabyGlassRole.NavigationChrome
    ) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BabyGlassTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    minLines = 1,
                    maxLines = 4,
                    placeholder = if (enabled) "问问睡眠、喂养、疫苗或成长记录..." else "AI 正在回复"
                )

                BabyGlassButton(
                    onClick = if (enabled && value.isNotBlank()) onSend else null,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    role = if (enabled && value.isNotBlank()) BabyGlassRole.Prominent else BabyGlassRole.Clear
                ) {
                    val buttonState = when {
                        !enabled -> "loading"
                        value.isNotBlank() -> "ready"
                        else -> "idle"
                    }
                    MotionAnimatedContent(
                        targetState = buttonState,
                        label = "aiSendButtonState"
                    ) { state ->
                        if (state == "loading") {
                            TinyDots()
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "发送",
                                tint = if (state == "ready") {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotConfiguredView() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .babyPageBackground()
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        AssistantIntroCard(
            title = "先配置一个 AI Profile",
            subtitle = "配置 API Key 后，AI 助手会结合宝宝资料、成长记录、喂养和疫苗状态给出建议。",
            badge = "AI",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 188.dp)
        )
    }
}
