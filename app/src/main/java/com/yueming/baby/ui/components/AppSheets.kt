package com.yueming.baby.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yueming.baby.ui.motion.BabyMotion
import kotlinx.coroutines.delay

private val MotionEnterEasing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
private val MotionExitEasing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppFullHeightSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    maxHeightFraction: Float = 0.94f,
    shape: Shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    containerColor: Color = Color.Transparent,
    tonalElevation: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val sheetContainerColor = if (containerColor == Color.Transparent) {
        MaterialTheme.colorScheme.background.copy(alpha = 0.01f)
    } else {
        containerColor
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier.fillMaxHeight(maxHeightFraction),
        shape = shape,
        containerColor = sheetContainerColor,
        tonalElevation = tonalElevation,
        scrimColor = Color.Black.copy(alpha = if (isDark) 0.42f else 0.16f),
        dragHandle = { AppSheetDragHandle() }
    ) {
        BabyGlassSheetSurface(
            modifier = Modifier
                .fillMaxHeight()
                .imePadding(),
            shape = shape
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BabyGlassModalSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
        shape = shape,
        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.01f),
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = if (isDark) 0.42f else 0.16f),
        dragHandle = { AppSheetDragHandle() }
    ) {
        BabyGlassSheetSurface(
            modifier = Modifier
                .imePadding(),
            shape = shape,
            content = content
        )
    }
}

@Composable
fun AppEditorDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Transparent,
    content: @Composable BoxScope.(requestClose: () -> Unit) -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var visible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }

    fun requestClose() {
        if (!closing) {
            closing = true
            visible = false
        }
    }

    LaunchedEffect(Unit) {
        visible = true
    }

    LaunchedEffect(closing) {
        if (closing) {
            delay(220)
            onDismissRequest()
        }
    }

    Dialog(
        onDismissRequest = { requestClose() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (isDark) 0.42f else 0.16f))
            )
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(durationMillis = 120, easing = MotionEnterEasing)) +
                    scaleIn(initialScale = 0.965f, animationSpec = BabyMotion.pageScaleSpec()) +
                    slideInVertically(
                        animationSpec = tween(durationMillis = 320, easing = MotionEnterEasing),
                        initialOffsetY = { it / 14 }
                    ),
                exit = fadeOut(tween(durationMillis = 120, easing = MotionExitEasing)) +
                    scaleOut(targetScale = 0.985f, animationSpec = tween(180, easing = MotionExitEasing)) +
                    slideOutVertically(
                        animationSpec = tween(durationMillis = 200, easing = MotionExitEasing),
                        targetOffsetY = { it / 12 }
                    )
            ) {
                BabyGlassSheetSurface(
                    modifier = modifier.fillMaxSize(),
                    shape = RoundedCornerShape(30.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .systemBarsPadding()
                            .navigationBarsPadding()
                            .imePadding()
                    ) {
                        content(::requestClose)
                    }
                }
            }
        }
    }
}

@Composable
fun BabyGlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var visible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }

    fun requestClose() {
        if (!closing) {
            closing = true
            visible = false
        }
    }

    LaunchedEffect(Unit) {
        visible = true
    }

    LaunchedEffect(closing) {
        if (closing) {
            delay(180)
            onDismissRequest()
        }
    }

    Dialog(
        onDismissRequest = { requestClose() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (isDark) 0.38f else 0.14f))
                .systemBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(durationMillis = 130, easing = MotionEnterEasing)) +
                    scaleIn(initialScale = 0.92f, animationSpec = BabyMotion.pageScaleSpec()) +
                    slideInVertically(
                        animationSpec = tween(durationMillis = 300, easing = MotionEnterEasing),
                        initialOffsetY = { it / 10 }
                    ),
                exit = fadeOut(tween(durationMillis = 110, easing = MotionExitEasing)) +
                    scaleOut(targetScale = 0.97f, animationSpec = tween(160, easing = MotionExitEasing))
            ) {
                BabyGlassSheetSurface(
                    modifier = modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp),
                    shape = RoundedCornerShape(34.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (icon != null) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                BabyGlassSurface(
                                    modifier = Modifier.size(54.dp),
                                    shape = RoundedCornerShape(19.dp),
                                    role = BabyGlassRole.ClearChrome,
                                    contentAlignment = Alignment.Center
                                ) {
                                    icon()
                                }
                            }
                        }
                        if (title != null) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                title()
                            }
                        }
                        if (text != null) {
                            BabyContentCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Box(modifier = Modifier.padding(14.dp)) {
                                    text()
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            dismissButton?.invoke()
                            confirmButton()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppSheetDragHandle() {
    BabyGlassSurface(
        modifier = Modifier
            .padding(top = 10.dp, bottom = 8.dp)
            .width(52.dp)
            .height(5.dp),
        shape = RoundedCornerShape(50),
        role = BabyGlassRole.ClearChrome,
        useBackdrop = false
    ) {}
}
