package com.yueming.baby.ui.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.min

object BabyMotion {
    val miuixEase = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val emphasizedEase = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val fadeThroughEase = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun pageFadeSpec() = tween<Float>(
        durationMillis = 190,
        easing = fadeThroughEase
    )

    fun pageScaleSpec() = tween<Float>(
        durationMillis = 260,
        easing = emphasizedEase
    )

    fun pageSlideSpec(): FiniteAnimationSpec<IntOffset> = tween(
        durationMillis = 300,
        easing = emphasizedEase
    )

    fun fadeThroughSpec(): FiniteAnimationSpec<Float> = tween(
        durationMillis = 180,
        easing = fadeThroughEase
    )

    fun colorFadeSpec(): AnimationSpec<Color> = tween(
        durationMillis = 180,
        easing = fadeThroughEase
    )

    fun <T> fastSpatial(): AnimationSpec<T> = spring(
        dampingRatio = 0.86f,
        stiffness = Spring.StiffnessMedium
    )

    fun <T> defaultSpatial(): AnimationSpec<T> = spring(
        dampingRatio = 0.78f,
        stiffness = Spring.StiffnessMediumLow
    )

    fun <T> emphasizedSpatial(): AnimationSpec<T> = spring(
        dampingRatio = 0.72f,
        stiffness = Spring.StiffnessLow
    )

    fun pressSpring(): AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    fun cardPressSpring(): AnimationSpec<Float> = spring(
        dampingRatio = 0.72f,
        stiffness = Spring.StiffnessMedium
    )

    fun cardShapeSpring(): AnimationSpec<Dp> = spring(
        dampingRatio = 0.82f,
        stiffness = Spring.StiffnessMedium
    )

    fun listStagger(index: Int, stepMillis: Int = 36, maxDelayMillis: Int = 180): Int {
        return min(index * stepMillis, maxDelayMillis)
    }

    fun pageSlideOffset(direction: Int): Density.(fullWidth: Int) -> Int = { fullWidth ->
        direction * fullWidth / 9
    }

    fun <S> contentSwitch(): AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        (fadeIn(animationSpec = fadeThroughSpec()) +
            scaleIn(initialScale = 0.96f, animationSpec = pageScaleSpec()))
            .togetherWith(
                fadeOut(animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing)) +
                    scaleOut(targetScale = 0.98f, animationSpec = tween(durationMillis = 120))
            )
            .using(SizeTransform(clip = false))
    }
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
fun Modifier.motionPressable(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.975f
): Modifier = miuixPressable(
    interactionSource = interactionSource,
    enabled = enabled,
    pressedScale = pressedScale
)

@Composable
fun Modifier.miuixCardPressable(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.965f
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) pressedScale else 1f,
        animationSpec = BabyMotion.cardPressSpring(),
        label = "miuixCardPressScale"
    )

    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun Modifier.motionCardPress(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.965f
): Modifier = miuixCardPressable(
    interactionSource = interactionSource,
    enabled = enabled,
    pressedScale = pressedScale
)

@Composable
fun Modifier.motionListItem(
    index: Int = 0,
    initialTranslationY: Float = 14f
): Modifier = miuixFadeSlideIn(
    delayMillis = BabyMotion.listStagger(index),
    initialTranslationY = initialTranslationY
)

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

@Composable
fun MotionReveal(
    visible: Boolean = true,
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    initialTranslationY: Float = 16f,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.miuixFadeSlideIn(
            delayMillis = if (visible) delayMillis else 0,
            initialTranslationY = initialTranslationY
        ),
        content = content
    )
}

@Composable
fun MotionCardSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    color: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
    border: BorderStroke? = BorderStroke(
        0.5.dp,
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
    ),
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        border = border,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        content = content
    )
}

@Composable
fun <S> MotionAnimatedContent(
    targetState: S,
    modifier: Modifier = Modifier,
    label: String = "motionAnimatedContent",
    content: @Composable AnimatedContentScope.(S) -> Unit
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = BabyMotion.contentSwitch(),
        label = label,
        content = content
    )
}
