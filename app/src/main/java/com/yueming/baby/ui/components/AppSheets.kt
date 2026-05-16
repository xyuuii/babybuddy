package com.yueming.baby.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

private val MotionEnterEasing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
private val MotionExitEasing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppFullHeightSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    maxHeightFraction: Float = 0.94f,
    shape: Shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
    containerColor: Color = MaterialTheme.colorScheme.background,
    tonalElevation: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier.fillMaxHeight(maxHeightFraction),
        shape = shape,
        containerColor = containerColor,
        tonalElevation = tonalElevation,
        dragHandle = { AppSheetDragHandle() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .navigationBarsPadding()
                .imePadding()
        ) {
            content()
        }
    }
}

@Composable
fun AppEditorDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.background,
    content: @Composable BoxScope.(requestClose: () -> Unit) -> Unit
) {
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
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(durationMillis = 120, easing = MotionEnterEasing)) +
                    slideInVertically(
                        animationSpec = tween(durationMillis = 320, easing = MotionEnterEasing),
                        initialOffsetY = { it / 14 }
                    ),
                exit = fadeOut(tween(durationMillis = 120, easing = MotionExitEasing)) +
                    slideOutVertically(
                        animationSpec = tween(durationMillis = 200, easing = MotionExitEasing),
                        targetOffsetY = { it / 12 }
                    )
            ) {
                Surface(
                    modifier = modifier.fillMaxSize(),
                    color = containerColor
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
private fun AppSheetDragHandle() {
    Box(
        modifier = Modifier
            .padding(top = 10.dp, bottom = 8.dp)
            .width(42.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f))
    )
}
