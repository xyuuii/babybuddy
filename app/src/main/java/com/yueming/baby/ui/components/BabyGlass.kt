package com.yueming.baby.ui.components

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop as AlgBackdrop
import com.kyant.backdrop.backdrops.LayerBackdrop as AlgLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.exposureAdjustment
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight as AlgHighlight
import com.kyant.backdrop.shadow.InnerShadow as AlgInnerShadow
import com.kyant.backdrop.shadow.Shadow as AlgShadow
import top.yukonga.miuix.kmp.blur.Backdrop as MiuixBackdrop
import com.yueming.baby.ui.motion.motionCardPress

val LocalBabyLiquidBackdrop = staticCompositionLocalOf<AlgBackdrop?> { null }
val LocalBabyFloatingBackdrop = staticCompositionLocalOf<AlgLayerBackdrop?> { null }
val LocalBabyLegacyBackdrop = staticCompositionLocalOf<MiuixBackdrop?> { null }
val LocalBabyBackdropSuppressed = staticCompositionLocalOf { false }

private fun isRealBackdropUnsafeDevice(): Boolean {
    val brand = Build.BRAND.orEmpty()
    val manufacturer = Build.MANUFACTURER.orEmpty()
    val display = Build.DISPLAY.orEmpty()
    return brand.contains("Redmi", ignoreCase = true) ||
        brand.contains("Xiaomi", ignoreCase = true) ||
        manufacturer.contains("Xiaomi", ignoreCase = true) ||
        display.contains("MIUI", ignoreCase = true) ||
        display.contains("HyperOS", ignoreCase = true)
}

enum class BabyGlassRole {
    Regular,
    Clear,
    Prominent,
    NavigationChrome,
    FloatingNav,
    FloatingNavIndicator,
    RegularChrome,
    ClearChrome,
    GlassSheet,
    TintedAction,
    FloatingTabBar,
    FloatingTabIndicator
}

private fun BabyGlassRole.isFloatingChrome(): Boolean =
    this == BabyGlassRole.FloatingNav ||
        this == BabyGlassRole.FloatingNavIndicator ||
        this == BabyGlassRole.FloatingTabBar ||
        this == BabyGlassRole.FloatingTabIndicator

private fun BabyGlassRole.isFloatingIndicator(): Boolean =
    this == BabyGlassRole.FloatingNavIndicator ||
        this == BabyGlassRole.FloatingTabIndicator

private fun BabyGlassRole.isClearChrome(): Boolean =
    this == BabyGlassRole.Clear ||
        this == BabyGlassRole.ClearChrome ||
        isFloatingChrome()

private fun BabyGlassRole.isNavigationChrome(): Boolean =
    this == BabyGlassRole.NavigationChrome ||
        this == BabyGlassRole.RegularChrome

private fun BabyGlassRole.isRegularChrome(): Boolean =
    this == BabyGlassRole.RegularChrome

data class BabyGlassSpec(
    val blurRadius: Dp,
    val lensHeight: Dp,
    val lensAmount: Float,
    val chromaticAberration: Boolean,
    val surfaceAlpha: Float,
    val strokeAlpha: Float,
    val shadowRadius: Dp
) {
    companion object {
        val Regular = BabyGlassSpec(
            blurRadius = 22.dp,
            lensHeight = 18.dp,
            lensAmount = 16f,
            chromaticAberration = true,
            surfaceAlpha = 0.46f,
            strokeAlpha = 0.42f,
            shadowRadius = 16.dp
        )
        val Clear = BabyGlassSpec(
            blurRadius = 14.dp,
            lensHeight = 14.dp,
            lensAmount = 12f,
            chromaticAberration = false,
            surfaceAlpha = 0.20f,
            strokeAlpha = 0.30f,
            shadowRadius = 10.dp
        )
        val Prominent = BabyGlassSpec(
            blurRadius = 28.dp,
            lensHeight = 24.dp,
            lensAmount = 28f,
            chromaticAberration = true,
            surfaceAlpha = 0.44f,
            strokeAlpha = 0.46f,
            shadowRadius = 18.dp
        )
        val NavigationChrome = BabyGlassSpec(
            blurRadius = 14.dp,
            lensHeight = 24.dp,
            lensAmount = 44f,
            chromaticAberration = true,
            surfaceAlpha = 0.12f,
            strokeAlpha = 0.34f,
            shadowRadius = 10.dp
        )
        val RegularChrome = BabyGlassSpec(
            blurRadius = 12.dp,
            lensHeight = 12.dp,
            lensAmount = 18f,
            chromaticAberration = false,
            surfaceAlpha = 0.10f,
            strokeAlpha = 0.28f,
            shadowRadius = 3.dp
        )
        val FloatingNav = BabyGlassSpec(
            blurRadius = 10.dp,
            lensHeight = 30.dp,
            lensAmount = 68f,
            chromaticAberration = true,
            surfaceAlpha = 0.010f,
            strokeAlpha = 0.34f,
            shadowRadius = 8.dp
        )
        val FloatingNavIndicator = BabyGlassSpec(
            blurRadius = 8.dp,
            lensHeight = 28.dp,
            lensAmount = 58f,
            chromaticAberration = true,
            surfaceAlpha = 0.002f,
            strokeAlpha = 0.10f,
            shadowRadius = 2.dp
        )
        val ClearChrome = BabyGlassSpec(
            blurRadius = 8.dp,
            lensHeight = 28.dp,
            lensAmount = 60f,
            chromaticAberration = true,
            surfaceAlpha = 0.020f,
            strokeAlpha = 0.28f,
            shadowRadius = 10.dp
        )
        val GlassSheet = BabyGlassSpec(
            blurRadius = 12.dp,
            lensHeight = 34.dp,
            lensAmount = 64f,
            chromaticAberration = true,
            surfaceAlpha = 0.09f,
            strokeAlpha = 0.46f,
            shadowRadius = 16.dp
        )
        val TintedAction = BabyGlassSpec(
            blurRadius = 14.dp,
            lensHeight = 28.dp,
            lensAmount = 52f,
            chromaticAberration = true,
            surfaceAlpha = 0.16f,
            strokeAlpha = 0.44f,
            shadowRadius = 12.dp
        )

        fun forRole(role: BabyGlassRole): BabyGlassSpec = when (role) {
            BabyGlassRole.Regular -> Regular
            BabyGlassRole.Clear -> Clear
            BabyGlassRole.Prominent -> Prominent
            BabyGlassRole.NavigationChrome -> NavigationChrome
            BabyGlassRole.FloatingNav -> FloatingNav
            BabyGlassRole.FloatingNavIndicator -> FloatingNavIndicator
            BabyGlassRole.RegularChrome -> RegularChrome
            BabyGlassRole.ClearChrome -> ClearChrome
            BabyGlassRole.GlassSheet -> GlassSheet
            BabyGlassRole.TintedAction -> TintedAction
            BabyGlassRole.FloatingTabBar -> FloatingNav
            BabyGlassRole.FloatingTabIndicator -> FloatingNavIndicator
        }
    }
}

@Composable
fun BabyGlassHost(
    legacyBackdrop: MiuixBackdrop?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val useRealBackdrop = remember { !isRealBackdropUnsafeDevice() }
    val liquidBackdrop = if (useRealBackdrop) rememberLayerBackdrop() else null
    val canvasColor = MaterialTheme.colorScheme.background
    val floatingBackdrop = if (useRealBackdrop) rememberLayerBackdrop {
        drawRect(canvasColor)
        drawContent()
    } else {
        null
    }
    CompositionLocalProvider(
        LocalBabyLiquidBackdrop provides liquidBackdrop,
        LocalBabyFloatingBackdrop provides floatingBackdrop,
        LocalBabyLegacyBackdrop provides legacyBackdrop
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(if (liquidBackdrop != null) Modifier.layerBackdrop(liquidBackdrop) else Modifier)
                    .background(babyGlassCanvasBrush())
            )
            content()
        }
    }
}

@Composable
fun BabyGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    role: BabyGlassRole = BabyGlassRole.Regular,
    useBackdrop: Boolean = true,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    val spec = BabyGlassSpec.forRole(role)
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val liquidBackdrop = if (role.isFloatingChrome()) {
        LocalBabyFloatingBackdrop.current ?: LocalBabyLiquidBackdrop.current
    } else {
        LocalBabyLiquidBackdrop.current
    }
    val useLiquid = useBackdrop &&
        !LocalBabyBackdropSuppressed.current &&
        liquidBackdrop != null &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val isFloating = role.isFloatingChrome()

    Box(
        modifier = modifier
            .shadow(
                elevation = spec.shadowRadius,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(
                    alpha = when {
                        isFloating && isDark -> 0.18f
                        isFloating -> 0.035f
                        isDark -> 0.24f
                        else -> 0.08f
                    }
                ),
                spotColor = Color.Black.copy(
                    alpha = when {
                        isFloating && isDark -> 0.16f
                        isFloating -> 0.05f
                        isDark -> 0.22f
                        else -> 0.10f
                    }
                )
            )
            .then(
                when {
                    useLiquid -> Modifier.drawBackdrop(
                        backdrop = liquidBackdrop,
                        shape = { shape },
                        effects = {
                            blur(spec.blurRadius.toPx())
                            colorControls(
                                brightness = if (isDark) -0.04f else 0.05f,
                                contrast = when {
                                    role.isFloatingChrome() -> 1.08f
                                    role.isClearChrome() -> 1.16f
                                    role.isRegularChrome() -> 1.12f
                                    role.isNavigationChrome() -> 1.22f
                                    else -> 1.18f
                                },
                                saturation = when {
                                    role.isFloatingChrome() -> 1.04f
                                    role.isClearChrome() -> 1.44f
                                    role.isRegularChrome() -> 1.12f
                                    role.isNavigationChrome() -> 1.34f
                                    else -> 1.34f
                                }
                            )
                            if (!role.isFloatingIndicator()) {
                                vibrancy()
                            }
                            exposureAdjustment(
                                when {
                                    role.isFloatingChrome() -> if (isDark) -0.055f else -0.035f
                                    role.isRegularChrome() -> if (isDark) -0.015f else 0.012f
                                    role.isNavigationChrome() -> if (isDark) -0.04f else 0.03f
                                    else -> if (isDark) -0.06f else 0.08f
                                }
                            )
                            lens(
                                refractionHeight = spec.lensHeight.toPx(),
                                refractionAmount = spec.lensAmount,
                                depthEffect = true,
                                chromaticAberration = spec.chromaticAberration
                            )
                        },
                        highlight = { AlgHighlight.Ambient },
                        shadow = {
                            AlgShadow(
                                radius = spec.shadowRadius,
                                color = Color.Black.copy(
                                    alpha = when {
                                        isFloating && isDark -> 0.26f
                                        isFloating -> 0.10f
                                        role.isRegularChrome() && isDark -> 0.16f
                                        role.isRegularChrome() -> 0.07f
                                        isDark -> 0.32f
                                        else -> 0.18f
                                    }
                                ),
                                alpha = when {
                                    role.isFloatingIndicator() -> 0.10f
                                    isFloating -> 0.44f
                                    role.isRegularChrome() -> 0.34f
                                    else -> 0.72f
                                }
                            )
                        },
                        innerShadow = {
                            AlgInnerShadow(
                                radius = if (role.isFloatingIndicator()) 4.dp else if (role.isRegularChrome()) 6.dp else 16.dp,
                                color = Color.Black.copy(
                                    alpha = when {
                                        role.isFloatingIndicator() && isDark -> 0.10f
                                        role.isFloatingIndicator() -> 0.025f
                                        isFloating && isDark -> 0.22f
                                        isFloating -> 0.08f
                                        role.isRegularChrome() && isDark -> 0.13f
                                        role.isRegularChrome() -> 0.045f
                                        isDark -> 0.32f
                                        else -> 0.14f
                                    }
                                ),
                                alpha = when {
                                    role.isFloatingIndicator() -> 0.07f
                                    isFloating -> 0.24f
                                    role.isRegularChrome() -> 0.16f
                                    role.isClearChrome() -> 0.30f
                                    else -> 0.48f
                                }
                            )
                        }
                    )
                    else -> Modifier
                }
            )
            .clip(shape)
            .background(glassSurfaceBrush(isDark, spec, role), shape)
            .background(glassSheenBrush(isDark, role), shape)
            .border(glassBorder(isDark, spec, role), shape),
        contentAlignment = contentAlignment
    ) {
        content()
    }
}

@Composable
fun Modifier.babyFloatingGlassBackdropSource(): Modifier {
    val floatingBackdrop = LocalBabyFloatingBackdrop.current
    return if (floatingBackdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        layerBackdrop(floatingBackdrop)
    } else {
        this
    }
}

@Composable
fun BabyGlassTitleBar(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(999.dp),
    content: @Composable BoxScope.() -> Unit
) {
    BabyGlassSurface(
        modifier = modifier,
        shape = shape,
        role = BabyGlassRole.RegularChrome,
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
            content = content
        )
    }
}

@Composable
fun BabyGlassButton(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(999.dp),
    role: BabyGlassRole = BabyGlassRole.Regular,
    useBackdrop: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    BabyGlassSurface(
        modifier = if (onClick != null) {
            modifier
                .motionCardPress(interactionSource = interactionSource, pressedScale = 0.955f)
                .clip(shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
        } else {
            modifier
        },
        shape = shape,
        role = role,
        useBackdrop = useBackdrop,
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
fun BabyGlassSheetSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    content: @Composable BoxScope.() -> Unit
) {
    BabyGlassSurface(
        modifier = modifier,
        shape = shape,
        role = BabyGlassRole.GlassSheet,
        useBackdrop = false
    ) {
        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(sheetContentBrush(), shape)
                .background(sheetFrostBrush(), shape)
                .background(sheetFrostSheenBrush(), shape)
                .frostedGrain(isDark)
        )
        CompositionLocalProvider(LocalBabyBackdropSuppressed provides true) {
            content()
        }
    }
}

@Composable
fun BabyGlassActionRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    BabyGlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        role = BabyGlassRole.ClearChrome,
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            content()
        }
    }
}

@Composable
fun babyGlassCanvasBrush(): Brush {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) {
        Brush.verticalGradient(
            listOf(
                Color(0xFF0F0F13),
                Color(0xFF15161A),
                Color(0xFF101014)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color(0xFFFBFBFD),
                Color(0xFFF7F7F8),
                Color(0xFFFCFCFE)
            )
        )
    }
}

private fun glassSurfaceBrush(
    isDark: Boolean,
    spec: BabyGlassSpec,
    role: BabyGlassRole
): Brush {
    val whiteAlpha = when {
        role.isFloatingIndicator() -> if (isDark) 0.004f else 0.004f
        role.isFloatingChrome() -> if (isDark) 0.012f else 0.032f
        role.isRegularChrome() -> if (isDark) 0.024f else 0.044f
        role.isNavigationChrome() -> if (isDark) 0.030f else 0.052f
        role == BabyGlassRole.GlassSheet -> if (isDark) 0.038f else 0.056f
        else -> if (isDark) spec.surfaceAlpha * 0.14f else spec.surfaceAlpha
    }
    val baseAlpha = when {
        role.isFloatingIndicator() -> if (isDark) 0.008f else 0.006f
        role.isFloatingChrome() -> if (isDark) 0.020f else 0.045f
        role == BabyGlassRole.Clear || role == BabyGlassRole.ClearChrome -> if (isDark) 0.070f else 0.038f
        role == BabyGlassRole.Regular -> if (isDark) 0.24f else 0.30f
        role == BabyGlassRole.Prominent || role == BabyGlassRole.TintedAction -> if (isDark) 0.22f else 0.18f
        role.isRegularChrome() -> if (isDark) 0.072f else 0.060f
        role.isNavigationChrome() -> if (isDark) 0.10f else 0.078f
        role == BabyGlassRole.GlassSheet -> if (isDark) 0.15f else 0.085f
        else -> if (isDark) 0.20f else 0.24f
    }
    return Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = whiteAlpha),
            Color.White.copy(alpha = baseAlpha),
            Color(0xFFEDEDF2).copy(
                alpha = when {
                    role.isFloatingIndicator() -> if (isDark) 0.002f else 0.003f
                    role.isFloatingChrome() -> if (isDark) 0.006f else 0.010f
                    role.isRegularChrome() -> if (isDark) 0.008f else 0.010f
                    role.isNavigationChrome() -> if (isDark) 0.012f else 0.016f
                    role == BabyGlassRole.GlassSheet -> if (isDark) 0.026f else 0.026f
                    else -> if (isDark) 0.04f else 0.16f
                }
            ),
            Color.Black.copy(
                alpha = when {
                    role.isFloatingIndicator() -> if (isDark) 0.030f else 0.000f
                    role.isFloatingChrome() -> if (isDark) 0.060f else 0.000f
                    role.isRegularChrome() -> if (isDark) 0.034f else 0.000f
                    role.isNavigationChrome() -> if (isDark) 0.065f else 0.004f
                    else -> if (isDark) 0.14f else 0.025f
                }
            )
        )
    )
}

private fun glassSheenBrush(isDark: Boolean, role: BabyGlassRole): Brush {
    val alpha = when {
        role.isFloatingIndicator() -> if (isDark) 0.010f else 0.006f
        role.isFloatingChrome() -> if (isDark) 0.028f else 0.026f
        role.isRegularChrome() -> if (isDark) 0.026f else 0.038f
        role.isNavigationChrome() -> if (isDark) 0.048f else 0.076f
        else -> if (isDark) 0.10f else 0.28f
    }
    return Brush.radialGradient(
        colors = listOf(
            Color.White.copy(alpha = alpha),
            Color.Transparent
        ),
        center = Offset.Zero,
        radius = 620f
    )
}

private fun glassBorder(isDark: Boolean, spec: BabyGlassSpec, role: BabyGlassRole): BorderStroke {
    val topAlpha = when {
        role.isFloatingIndicator() -> if (isDark) 0.08f else 0.12f
        role.isFloatingChrome() -> if (isDark) 0.16f else 0.42f
        role.isRegularChrome() -> if (isDark) 0.15f else 0.32f
        role.isNavigationChrome() -> if (isDark) 0.20f else 0.46f
        else -> if (isDark) 0.34f else 0.78f
    }
    val bottomAlpha = when {
        role.isFloatingIndicator() -> if (isDark) 0.08f else 0.006f
        role.isFloatingChrome() -> if (isDark) 0.12f else 0.018f
        role.isRegularChrome() -> if (isDark) 0.10f else 0.010f
        role.isNavigationChrome() -> if (isDark) 0.15f else 0.024f
        else -> if (isDark) 0.26f else 0.07f
    }
    return BorderStroke(
        0.8.dp,
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = topAlpha),
                Color.White.copy(alpha = spec.strokeAlpha),
                Color.Black.copy(alpha = bottomAlpha)
            )
        )
    )
}

@Composable
private fun sheetContentBrush(): Brush {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.92f else 0.970f),
            MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.975f else 0.992f),
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (isDark) 0.92f else 0.950f)
        )
    )
}

@Composable
private fun sheetFrostBrush(): Brush {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return Brush.radialGradient(
        listOf(
            Color.White.copy(alpha = if (isDark) 0.045f else 0.34f),
            Color.White.copy(alpha = if (isDark) 0.020f else 0.16f),
            Color.Transparent
        ),
        center = Offset(0f, 0f),
        radius = 760f
    )
}

@Composable
private fun sheetFrostSheenBrush(): Brush {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = if (isDark) 0.070f else 0.24f),
            Color.Transparent,
            Color.Black.copy(alpha = if (isDark) 0.14f else 0.025f)
        ),
        start = Offset(0f, 0f),
        end = Offset(680f, 980f)
    )
}

private fun Modifier.frostedGrain(isDark: Boolean): Modifier = drawWithContent {
    drawContent()
    val gap = 18.dp.toPx()
    val dotRadius = 0.42.dp.toPx()
    val dotColor = if (isDark) {
        Color.White.copy(alpha = 0.018f)
    } else {
        Color.White.copy(alpha = 0.045f)
    }
    var row = 0
    var y = gap * 0.6f
    while (y < size.height) {
        var x = if (row % 2 == 0) gap * 0.45f else gap * 0.95f
        while (x < size.width) {
            drawCircle(dotColor, radius = dotRadius, center = Offset(x, y))
            x += gap
        }
        y += gap
        row += 1
    }
}
