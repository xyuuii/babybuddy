package com.yueming.baby.ui.motion

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

object BabyMotion {
    val miuixEase = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun pageFadeSpec() = tween<Float>(
        durationMillis = 170,
        easing = FastOutSlowInEasing
    )

    fun pageScaleSpec() = tween<Float>(
        durationMillis = 210,
        easing = miuixEase
    )

    fun pressSpring(): AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}

@Composable
fun Modifier.miuixPressable(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.975f
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) pressedScale else 1f,
        animationSpec = BabyMotion.pressSpring(),
        label = "miuixPressScale"
    )

    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun Modifier.miuixFadeSlideIn(
    delayMillis: Int = 0,
    initialTranslationY: Float = 18f
): Modifier {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) {
            delay(delayMillis.toLong())
        }
        visible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 260, easing = BabyMotion.miuixEase),
        label = "miuixAppearAlpha"
    )
    val translationY by animateFloatAsState(
        targetValue = if (visible) 0f else initialTranslationY,
        animationSpec = tween(durationMillis = 320, easing = BabyMotion.miuixEase),
        label = "miuixAppearTranslationY"
    )

    return graphicsLayer {
        this.alpha = alpha
        this.translationY = translationY
    }
}
