package com.yueming.baby.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yueming.baby.ui.motion.BabyMotion
import com.yueming.baby.ui.motion.motionCardPress
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

enum class BabyStatusTone {
    Neutral,
    Info,
    Success,
    Warning,
    Danger
}

private fun BabyStatusTone.accentColor(): Color = when (this) {
    BabyStatusTone.Neutral -> Color(0xFF8E8E93)
    BabyStatusTone.Info -> BabyPalette.Blue
    BabyStatusTone.Success -> BabyPalette.Mint
    BabyStatusTone.Warning -> BabyPalette.Gold
    BabyStatusTone.Danger -> BabyPalette.RoseDeep
}

private fun BabyStatusTone.defaultIcon(): ImageVector = when (this) {
    BabyStatusTone.Neutral -> Icons.Default.Info
    BabyStatusTone.Info -> Icons.Default.Info
    BabyStatusTone.Success -> Icons.Default.CheckCircle
    BabyStatusTone.Warning -> Icons.Default.Warning
    BabyStatusTone.Danger -> Icons.Default.Error
}

@Composable
fun BabyStatusTag(
    label: String,
    modifier: Modifier = Modifier,
    tone: BabyStatusTone = BabyStatusTone.Neutral,
    icon: ImageVector? = tone.defaultIcon(),
    compact: Boolean = false
) {
    val accent = tone.accentColor()
    BabyGlassSurface(
        modifier = modifier.heightIn(min = if (compact) 30.dp else 34.dp),
        shape = RoundedCornerShape(999.dp),
        role = BabyGlassRole.ClearChrome,
        useBackdrop = false,
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 9.dp else 11.dp,
                vertical = if (compact) 5.dp else 7.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(if (compact) 13.dp else 15.dp), tint = accent)
            }
            Text(
                text = label,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun BabyWarningCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    tone: BabyStatusTone = BabyStatusTone.Warning,
    icon: ImageVector = tone.defaultIcon(),
    action: (@Composable (() -> Unit))? = null
) {
    val accent = tone.accentColor()
    BabyContentCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BabyIconBubble(icon = icon, accent = accent)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            action?.invoke()
        }
    }
}

@Composable
fun BabyGlassSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector = Icons.Default.Search
) {
    BabyGlassSurface(
        modifier = modifier.heightIn(min = 50.dp),
        shape = RoundedCornerShape(999.dp),
        role = BabyGlassRole.RegularChrome,
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Icon(
                leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isBlank()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )
            AnimatedVisibility(
                visible = value.isNotBlank(),
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(120))
            ) {
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .motionCardPress(interactionSource, pressedScale = 0.92f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.52f))
                        .clickable(interactionSource = interactionSource, indication = null) {
                            onValueChange("")
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun BabyDateWheelDialog(
    initialDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    title: String = "选择日期"
) {
    val currentYear = LocalDate.now().year
    val years = remember(initialDate) {
        (minOf(1990, initialDate.year - 2)..maxOf(currentYear + 10, initialDate.year + 2)).toList()
    }
    var selectedYear by remember(initialDate) { mutableIntStateOf(initialDate.year) }
    var selectedMonth by remember(initialDate) { mutableIntStateOf(initialDate.monthValue) }
    var selectedDay by remember(initialDate) { mutableIntStateOf(initialDate.dayOfMonth) }
    val days = remember(selectedYear, selectedMonth) {
        (1..YearMonth.of(selectedYear, selectedMonth).lengthOfMonth()).toList()
    }

    LaunchedEffect(days) {
        if (selectedDay > days.last()) selectedDay = days.last()
    }

    BabyWheelDialogShell(
        title = title,
        subtitle = "%04d-%02d-%02d".format(selectedYear, selectedMonth, selectedDay),
        onDismiss = onDismiss,
        onConfirm = {
            onDateSelected(LocalDate.of(selectedYear, selectedMonth, selectedDay))
            onDismiss()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(232.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BabyWheelPicker(
                values = years,
                selectedValue = selectedYear,
                onValueChange = { selectedYear = it },
                valueLabel = { "${it}年" },
                modifier = Modifier.weight(1.16f)
            )
            BabyWheelPicker(
                values = (1..12).toList(),
                selectedValue = selectedMonth,
                onValueChange = { selectedMonth = it },
                valueLabel = { "${it}月" },
                modifier = Modifier.weight(0.92f)
            )
            BabyWheelPicker(
                values = days,
                selectedValue = selectedDay,
                onValueChange = { selectedDay = it },
                valueLabel = { "${it}日" },
                modifier = Modifier.weight(0.92f)
            )
        }
    }
}

@Composable
fun BabyTimeWheelDialog(
    initialTime: LocalTime,
    onTimeSelected: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
    title: String = "选择时间"
) {
    var selectedHour by remember(initialTime) { mutableIntStateOf(initialTime.hour) }
    var selectedMinute by remember(initialTime) { mutableIntStateOf(initialTime.minute) }

    BabyWheelDialogShell(
        title = title,
        subtitle = "%02d:%02d".format(selectedHour, selectedMinute),
        onDismiss = onDismiss,
        onConfirm = {
            onTimeSelected(LocalTime.of(selectedHour, selectedMinute))
            onDismiss()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(232.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BabyWheelPicker(
                values = (0..23).toList(),
                selectedValue = selectedHour,
                onValueChange = { selectedHour = it },
                valueLabel = { "%02d".format(it) },
                modifier = Modifier.weight(1f)
            )
            Text(
                ":",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            BabyWheelPicker(
                values = (0..59).toList(),
                selectedValue = selectedMinute,
                onValueChange = { selectedMinute = it },
                valueLabel = { "%02d".format(it) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BabyWheelDialogShell(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.10f))
                .systemBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center
        ) {
            BabyGlassSheetSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 430.dp),
                shape = RoundedCornerShape(32.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(17.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        BabyGlassIconButton(
                            icon = Icons.Default.Close,
                            onClick = onDismiss,
                            contentDescription = "关闭"
                        )
                    }
                    content()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BabySecondaryButton(
                            text = "取消",
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        )
                        BabyPrimaryButton(
                            text = "确定",
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BabyWheelPicker(
    values: List<Int>,
    selectedValue: Int,
    onValueChange: (Int) -> Unit,
    valueLabel: (Int) -> String,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 42.dp
) {
    val visibleItems = 5
    val centerPadding = visibleItems / 2
    val displayValues = remember(values) {
        List(centerPadding) { null } + values.map<Int, Int?> { it } + List(centerPadding) { null }
    }
    val initialIndex = remember(values, selectedValue) {
        values.indexOf(selectedValue).coerceAtLeast(0)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val scope = rememberCoroutineScope()
    val latestOnValueChange by rememberUpdatedState(onValueChange)

    fun nearestValueIndex(): Int {
        if (values.isEmpty()) return 0
        val scrollRows = (listState.firstVisibleItemScrollOffset / itemHeightPx).roundToInt()
        return (listState.firstVisibleItemIndex + scrollRows).coerceIn(values.indices)
    }

    LaunchedEffect(values, listState, itemHeightPx) {
        snapshotFlow { nearestValueIndex() }
            .distinctUntilChanged()
            .collect { index ->
                values.getOrNull(index)?.let(latestOnValueChange)
            }
    }

    LaunchedEffect(values, listState, itemHeightPx) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { isScrolling ->
                if (!isScrolling && listState.firstVisibleItemScrollOffset != 0) {
                    listState.animateScrollToItem(nearestValueIndex())
                }
            }
    }

    Box(
        modifier = modifier
            .height(itemHeight * visibleItems)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.44f))
            .border(
                BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
                RoundedCornerShape(22.dp)
            )
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), userScrollEnabled = true) {
            itemsIndexed(
                items = displayValues,
                key = { index, value -> value?.let { "value-$it" } ?: "pad-$index" }
            ) { index, value ->
                val valueIndex = index - centerPadding
                val selectedIndex = values.indexOf(selectedValue)
                val distance = abs(valueIndex - selectedIndex)
                val isSelected = value == selectedValue
                val contentColor = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = when (distance) {
                            1 -> 0.70f
                            2 -> 0.42f
                            else -> 0.22f
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .clickable(enabled = value != null) {
                            val targetIndex = valueIndex.coerceIn(values.indices)
                            values.getOrNull(targetIndex)?.let(latestOnValueChange)
                            scope.launch { listState.animateScrollToItem(targetIndex) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (value != null) {
                        Text(
                            text = valueLabel(value),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = if (isSelected) {
                                MaterialTheme.typography.titleLarge
                            } else {
                                MaterialTheme.typography.titleMedium
                            },
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = contentColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(itemHeight)
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.60f))
                .border(
                    BorderStroke(0.7.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                    RoundedCornerShape(16.dp)
                )
        )
    }
}
