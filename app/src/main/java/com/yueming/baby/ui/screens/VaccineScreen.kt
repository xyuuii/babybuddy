package com.yueming.baby.ui.screens

import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.yueming.baby.data.*
import com.yueming.baby.ui.motion.BabyMotion
import com.yueming.baby.ui.motion.MotionAnimatedContent
import com.yueming.baby.ui.motion.motionCardPress
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VaccineScreen(onDismiss: () -> Unit) {
    val appBarColor = MaterialTheme.colorScheme.background
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.applyDialogSystemBars(view, appBarColor)
        }
    }

    val babyInfo by DataManager.babyInfo.collectAsState()
    val vaccineStatuses by DataManager.vaccineStatuses.collectAsState()
    val scopedVaccineStatuses = remember(vaccineStatuses, babyInfo.id) {
        vaccineStatuses.filter { belongsToBaby(it.babyId, babyInfo.id) }
    }
    var showOptional by remember { mutableStateOf(false) }
    var markDialogVaccine by remember { mutableStateOf<VaccineItem?>(null) }
    var markDate by remember { mutableStateOf(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)) }
    var markBatch by remember { mutableStateOf("") }
    var showUndoConfirm by remember { mutableStateOf<VaccineItem?>(null) }

    val ageMonths = DataManager.getAgeInMonths(babyInfo.birthDate)
    val doneMap = remember(scopedVaccineStatuses) {
        scopedVaccineStatuses.filter { it.isDone }.associate { it.vaccineId to it }
    }

    val filteredSchedule = remember(showOptional) {
        CHINA_VACCINE_SCHEDULE.filter { showOptional || it.isRequired }
    }

    val nextVaccine = remember(filteredSchedule, doneMap, ageMonths) {
        filteredSchedule
            .filter { it.id !in doneMap && it.scheduledAgeMonths >= ageMonths }
            .minByOrNull { it.scheduledAgeMonths }
    }

    val groupedByAge = remember(filteredSchedule) {
        filteredSchedule.groupBy { it.scheduledAgeMonths }.toSortedMap()
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Status bar background fill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(appBarColor)
        )
        // Top bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = appBarColor,
            shadowElevation = 0.dp
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "关闭")
                }
                Text("疫苗接种", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Row {
                    TextButton(onClick = { showOptional = !showOptional }) {
                        Text(if (showOptional) "隐藏自费" else "显示自费", fontSize = 12.sp)
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Age header
            item {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
                ) {
                    Row(
                        Modifier.padding(20.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${babyInfo.nickname}现在", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${ageMonths} 个月", fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.headlineSmall)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("已完成", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${doneMap.size} 针", fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color(0xFF4CAF50))
                        }
                    }
                }
            }

            // Next vaccine alert
            if (nextVaccine != null) {
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFA726).copy(alpha = 0.12f)),
                        border = BorderStroke(0.5.dp, Color(0xFFFFA726).copy(alpha = 0.24f))
                    ) {
                        Row(
                            Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Notifications, null, Modifier.size(24.dp),
                                tint = Color(0xFFFFA726))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("下一针", fontSize = 11.sp,
                                    color = Color(0xFFFFA726), fontWeight = FontWeight.Medium)
                                Text("${nextVaccine.name} 第${nextVaccine.doseNumber}剂",
                                    fontWeight = FontWeight.Bold)
                                Text("${nextVaccine.scheduledAgeMonths}月龄接种 · ${nextVaccine.diseasePrevented}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (nextVaccine.scheduledAgeMonths <= ageMonths) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFFF5252)
                                ) {
                                    Text("到期", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                            } else {
                                Text("${nextVaccine.scheduledAgeMonths - ageMonths}个月后",
                                    fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                    color = Color(0xFFFFA726))
                            }
                        }
                    }
                }
            }

            // Vaccine schedule grouped by age
            groupedByAge.forEach { (age, vaccines) ->
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            when {
                                age < 12 -> "${age}月龄"
                                age % 12 == 0 -> "${age / 12}岁"
                                else -> "${age / 12}岁${age % 12}月"
                            },
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text("${vaccines.count { it.id in doneMap }}/${vaccines.size}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(vaccines, key = { it.id }) { vaccine ->
                    val status = doneMap[vaccine.id]
                    val isDone = status != null
                    val isOverdue = !isDone && vaccine.scheduledAgeMonths <= ageMonths
                    val isUpcoming = !isDone && vaccine.scheduledAgeMonths > ageMonths
                    val interactionSource = remember(vaccine.id) { MutableInteractionSource() }
                    val pressed by interactionSource.collectIsPressedAsState()
                    val cardCorner by animateDpAsState(
                        targetValue = if (pressed) 24.dp else 20.dp,
                        animationSpec = BabyMotion.cardShapeSpring(),
                        label = "vaccineCardCorner"
                    )
                    val containerColor by animateColorAsState(
                        targetValue = when {
                            isDone -> Color(0xFFE8F5E9)
                            isOverdue -> Color(0xFFFFF3E0)
                            else -> MaterialTheme.colorScheme.surface.copy(alpha = if (pressed) 1f else 0.96f)
                        },
                        animationSpec = tween(durationMillis = 180, easing = BabyMotion.fadeThroughEase),
                        label = "vaccineCardContainer"
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth().animateItem()
                            .motionCardPress(interactionSource = interactionSource, pressedScale = 0.985f)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) {
                                if (isDone) {
                                    showUndoConfirm = vaccine
                                } else {
                                    markDialogVaccine = vaccine
                                    markDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                                    markBatch = ""
                                }
                            },
                        shape = RoundedCornerShape(cardCorner),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        colors = CardDefaults.cardColors(containerColor = containerColor),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f))
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Status indicator
                            Box(
                                Modifier.size(36.dp).clip(CircleShape)
                                    .background(
                                        when {
                                            isDone -> Color(0xFF4CAF50)
                                            isOverdue -> Color(0xFFFFA726)
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                MotionAnimatedContent(
                                    targetState = when {
                                        isDone -> "done"
                                        isOverdue -> "overdue"
                                        else -> "pending"
                                    },
                                    label = "vaccineStatus"
                                ) { state ->
                                    when (state) {
                                        "done" -> Icon(Icons.Default.Check, null, Modifier.size(20.dp), tint = Color.White)
                                        "overdue" -> Text("!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        else -> Text("${vaccine.scheduledAgeMonths}", fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${vaccine.name} 第${vaccine.doseNumber}剂",
                                        fontWeight = FontWeight.Medium,
                                        style = MaterialTheme.typography.bodySmall)
                                    if (!vaccine.isRequired) {
                                        Spacer(Modifier.width(6.dp))
                                        Surface(shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFF7C4DFF).copy(alpha = 0.1f)) {
                                            Text("自费", modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                fontSize = 9.sp, color = Color(0xFF7C4DFF))
                                        }
                                    }
                                }
                                Text(vaccine.diseasePrevented, fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (isDone) {
                                    Text("已接种: ${status?.administeredDate ?: ""}",
                                        fontSize = 11.sp, color = Color(0xFF4CAF50),
                                        fontWeight = FontWeight.Medium)
                                } else if (isOverdue) {
                                    Text("建议接种", fontSize = 11.sp, color = Color(0xFFFFA726))
                                }
                            }

                            if (isDone) {
                                Icon(Icons.Default.CheckCircle, null, Modifier.size(20.dp),
                                    tint = Color(0xFF4CAF50))
                            } else {
                                Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    // Mark as done dialog
    markDialogVaccine?.let { vaccine ->
        AlertDialog(
            onDismissRequest = { markDialogVaccine = null },
            title = {
                Text("${vaccine.name} 第${vaccine.doseNumber}剂",
                    fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("预防: ${vaccine.diseasePrevented}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = markDate,
                        onValueChange = { markDate = it },
                        label = { Text("接种日期 (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = markBatch,
                        onValueChange = { markBatch = it },
                        label = { Text("批号（可选）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        DataManager.markVaccineDone(vaccine.id, markDate, markBatch)
                        markDialogVaccine = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) { Text("标记已接种", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { markDialogVaccine = null }) { Text("取消") }
            }
        )
    }

    // Undo confirm
    showUndoConfirm?.let { vaccine ->
        AlertDialog(
            onDismissRequest = { showUndoConfirm = null },
            title = { Text("撤销接种记录") },
            text = { Text("确定要撤销「${vaccine.name} 第${vaccine.doseNumber}剂」的接种记录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    DataManager.undoVaccine(vaccine.id)
                    showUndoConfirm = null
                }) { Text("撤销", color = Color(0xFFFFA726)) }
            },
            dismissButton = { TextButton(onClick = { showUndoConfirm = null }) { Text("取消") } }
        )
    }
}

private fun Window.applyDialogSystemBars(view: android.view.View, statusBarColor: Color) {
    addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    WindowCompat.setDecorFitsSystemWindows(this, false)
    setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
    setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
    @Suppress("DEPRECATION")
    this.statusBarColor = statusBarColor.toArgb()
    WindowCompat.getInsetsController(this, view).isAppearanceLightStatusBars =
        statusBarColor.luminance() > 0.5f
}
