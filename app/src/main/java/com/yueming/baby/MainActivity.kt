package com.yueming.baby

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yueming.baby.data.BabyInfo
import com.yueming.baby.data.DataManager
import com.yueming.baby.ui.components.BabyGlassHost
import com.yueming.baby.ui.components.BabyGlassRole
import com.yueming.baby.ui.components.BabyGlassSurface
import com.yueming.baby.ui.components.LocalBabyBottomBarClearance
import com.yueming.baby.ui.components.LocalBabyStatusBarClearance
import com.yueming.baby.ui.components.BabyPalette
import com.yueming.baby.ui.components.babyFloatingGlassBackdropSource
import com.yueming.baby.ui.components.liquid.DampedDragAnimation
import com.yueming.baby.ui.components.liquid.InnerShadow
import com.yueming.baby.ui.components.liquid.InteractiveHighlight
import com.yueming.baby.ui.components.liquid.innerShadow
import com.yueming.baby.ui.components.liquid.lens
import com.yueming.baby.ui.components.liquid.rememberCombinedBackdrop
import com.yueming.baby.ui.components.liquid.vibrancy
import com.yueming.baby.ui.motion.BabyMotion
import com.yueming.baby.ui.motion.motionPressable
import com.yueming.baby.ui.screens.*
import com.yueming.baby.ui.theme.YueMingTheme
import com.yueming.baby.ui.theme.ThemeMode
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource
import top.yukonga.miuix.kmp.blur.isRenderEffectSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.sensor.rememberDeviceTilt
import java.io.StringWriter
import java.io.PrintWriter
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyDeviceOrientationPolicy()

        // Capture all crashes
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val msg = "Crash: ${throwable.message}\n${sw.toString().take(500)}"
            android.util.Log.e("YueMingCrash", msg)
            // Show toast on main thread
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(this@MainActivity, "崩溃: ${throwable.message?.take(100)}", Toast.LENGTH_LONG).show()
            }
            // Terminate
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(1)
        }

        try {
            DataManager.init(applicationContext)
            android.util.Log.d("YueMing", "DataManager initialized with cloud backend")
        } catch (e: Exception) {
            android.util.Log.e("YueMingCrash", "DataManager.init failed", e)
            Toast.makeText(this, "初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
        }

        enableEdgeToEdge()
        setContent {
            val themeMode by DataManager.themeMode.collectAsState()
            LaunchedEffect(Unit) {
                DataManager.mediaUploadEvents.collect { event ->
                    Toast.makeText(
                        this@MainActivity,
                        event.message,
                        if (event.success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                    ).show()
                }
            }
            val mappedTheme = when (themeMode) {
                com.yueming.baby.data.ThemeMode.LIGHT -> ThemeMode.LIGHT
                com.yueming.baby.data.ThemeMode.DARK -> ThemeMode.DARK
                com.yueming.baby.data.ThemeMode.SYSTEM -> ThemeMode.SYSTEM
            }
            YueMingTheme(themeMode = mappedTheme) {
                YueMingApp()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyDeviceOrientationPolicy()
    }

    private fun applyDeviceOrientationPolicy() {
        requestedOrientation = if (resources.configuration.smallestScreenWidthDp >= 600) {
            ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
}

sealed class Screen(
    val route: String,
    private val fallbackTitle: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    val title: String
        get() = when (route) {
            "dashboard" -> "首页"
            "timeline" -> "时间线"
            "photos" -> "照片墙"
            "ai" -> "AI"
            "settings" -> "设置"
            else -> fallbackTitle
        }

    data object Dashboard : Screen("dashboard", "首页", Icons.Filled.Home, Icons.Outlined.Home)
    data object Timeline  : Screen("timeline", "时间线", Icons.Filled.DateRange, Icons.Outlined.DateRange)
    data object Photos    : Screen("photos", "照片墙", Icons.Filled.PhotoLibrary, Icons.Outlined.PhotoLibrary)
    data object AI        : Screen("ai", "AI助手", Icons.Filled.SmartToy, Icons.Outlined.SmartToy)
    data object Settings  : Screen("settings", "设置", Icons.Filled.Settings, Icons.Outlined.Settings)
}

private fun screenRouteIndex(route: String?): Int = when (route) {
    Screen.Dashboard.route -> 0
    Screen.Timeline.route -> 1
    Screen.Photos.route -> 2
    Screen.AI.route -> 3
    Screen.Settings.route -> 4
    else -> 0
}

private fun screenTransitionDirection(fromRoute: String?, toRoute: String?): Int {
    return (screenRouteIndex(toRoute) - screenRouteIndex(fromRoute)).coerceIn(-1, 1)
}

@Composable
fun YueMingApp() {
    val navController = rememberNavController()
    val legacyBackdrop = rememberLayerBackdrop()
    val screens = remember {
        listOf(Screen.Dashboard, Screen.Timeline, Screen.Photos, Screen.AI, Screen.Settings)
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route
    val selectedIndex = screenRouteIndex(currentRoute).coerceIn(0, screens.lastIndex)
    val bottomBarClearance =
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
            BabyLiquidBottomBarHeight +
            BabyLiquidBottomBarVerticalPadding * 2 +
            BabyLiquidBottomBarContentGap
    val statusBarClearance = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    CompositionLocalProvider(
        LocalBabyBottomBarClearance provides bottomBarClearance,
        LocalBabyStatusBarClearance provides statusBarClearance
    ) {
        BabyGlassHost(
            legacyBackdrop = legacyBackdrop,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Dashboard.route,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .babyFloatingGlassBackdropSource()
                    .layerBackdrop(legacyBackdrop),
                enterTransition = {
                    val direction = screenTransitionDirection(
                        initialState.destination.route,
                        targetState.destination.route
                    )
                    fadeIn(animationSpec = BabyMotion.pageFadeSpec()) +
                        slideInHorizontally(animationSpec = BabyMotion.pageSlideSpec()) { direction * it / 9 } +
                        scaleIn(initialScale = 0.985f, animationSpec = BabyMotion.pageScaleSpec())
                },
                exitTransition = {
                    val direction = screenTransitionDirection(
                        initialState.destination.route,
                        targetState.destination.route
                    )
                    fadeOut(animationSpec = tween(120, easing = BabyMotion.fadeThroughEase)) +
                        slideOutHorizontally(
                            animationSpec = tween(220, easing = BabyMotion.miuixEase)
                        ) { -direction * it / 16 } +
                        scaleOut(targetScale = 0.992f, animationSpec = tween(180, easing = BabyMotion.miuixEase))
                },
                popEnterTransition = {
                    val direction = screenTransitionDirection(
                        initialState.destination.route,
                        targetState.destination.route
                    )
                    fadeIn(animationSpec = BabyMotion.pageFadeSpec()) +
                        slideInHorizontally(animationSpec = BabyMotion.pageSlideSpec()) { direction * it / 9 } +
                        scaleIn(initialScale = 0.985f, animationSpec = BabyMotion.pageScaleSpec())
                },
                popExitTransition = {
                    val direction = screenTransitionDirection(
                        initialState.destination.route,
                        targetState.destination.route
                    )
                    fadeOut(animationSpec = tween(120, easing = BabyMotion.fadeThroughEase)) +
                        slideOutHorizontally(
                            animationSpec = tween(220, easing = BabyMotion.miuixEase)
                        ) { -direction * it / 16 } +
                        scaleOut(targetScale = 0.992f, animationSpec = tween(180, easing = BabyMotion.miuixEase))
                }
            ) {
                composable(Screen.Dashboard.route) {
                    DashboardScreen(
                        onOpenTimeline = {
                            navController.navigate(Screen.Timeline.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
                composable(Screen.Timeline.route) { TimelineScreen() }
                composable(Screen.Photos.route) { PhotosScreen() }
                composable(Screen.AI.route) { AIScreen() }
                composable(Screen.Settings.route) { SettingsScreen() }
            }

            BabyLiquidBottomBar(
                screens = screens,
                selectedIndex = selectedIndex,
                backdrop = legacyBackdrop,
                onSelect = { screen ->
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

private val BabyLiquidBottomBarHeight = 62.dp
private val BabyLiquidBottomBarVerticalPadding = 8.dp
private val BabyLiquidBottomBarContentGap = 12.dp

private val BabyLiquidIndicatorSpecular: Highlight = Highlight(
    width = 1.dp,
    alpha = 1f,
    style = BloomStroke(
        color = Color.White.copy(alpha = 0.12f),
        innerBlurRadius = 2.0.dp,
        primaryLight = LightSource(
            position = LightPosition(0.5f, -0.3f, -0.05f),
            color = Color.White,
            intensity = 1f
        ),
        secondaryLight = LightSource(
            position = LightPosition(0.5f, 0.8f, -0.5f),
            color = Color.White,
            intensity = 0.4f
        ),
        dualPeak = true
    )
)

private const val BabyLiquidLightRefX = 0.5f
private const val BabyLiquidLightRefY = 0.7f
private const val BabyLiquidGravityThresholdSq = 0.01f

@Composable
private fun rememberGravityRotatedHighlight(
    base: Highlight,
    extraDegrees: Float = 0f
): Highlight {
    val baseStyle = base.style as BloomStroke
    val tilt by rememberDeviceTilt()
    val rotatedPrimary = remember(tilt, baseStyle.primaryLight, extraDegrees) {
        val basePrimary = baseStyle.primaryLight
        val gravityX = tilt.gravityX
        val gravityY = tilt.gravityY
        val gravityMagnitudeSq = gravityX * gravityX + gravityY * gravityY
        val (baseLightX, baseLightY) = if (gravityMagnitudeSq > BabyLiquidGravityThresholdSq) {
            val invMagnitude = 1f / sqrt(gravityMagnitudeSq)
            gravityX * invMagnitude to gravityY * invMagnitude
        } else {
            0f to -1f
        }
        val radians = extraDegrees * PI / 180.0
        val cos = cos(radians).toFloat()
        val sin = sin(radians).toFloat()
        val lightX = cos * baseLightX - sin * baseLightY
        val lightY = sin * baseLightX + cos * baseLightY
        basePrimary.copy(
            position = LightPosition(
                x = BabyLiquidLightRefX + lightX,
                y = BabyLiquidLightRefY + lightY,
                z = basePrimary.position.z
            )
        )
    }
    return remember(base, rotatedPrimary) {
        base.copy(style = baseStyle.copy(primaryLight = rotatedPrimary))
    }
}

@Composable
private fun BabyLiquidBottomBar(
    screens: List<Screen>,
    selectedIndex: Int,
    backdrop: LayerBackdrop?,
    onSelect: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val pillShape = remember { CircleShape }
    val accentColor = MaterialTheme.colorScheme.primary
    val baseContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.80f else 0.66f)
    val containerColor = if (isDark) {
        Color(0xFF1C1C1E).copy(alpha = 0.30f)
    } else {
        Color.White.copy(alpha = 0.18f)
    }
    val isBlurActive = backdrop != null && isRenderEffectSupported()
    val tabsBackdrop = if (isBlurActive) rememberLayerBackdrop() else null
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()
    val selectedIndexState by rememberUpdatedState(selectedIndex)
    val screensState by rememberUpdatedState(screens)
    val onSelectState by rememberUpdatedState(onSelect)
    val tabsCount = screens.size.coerceAtLeast(1)

    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }
    var currentIndex by remember { mutableIntStateOf(selectedIndex) }

    val offsetAnimation = remember { Animatable(0f) }
    val rubberBandPx = with(density) { 4.dp.toPx() }
    val panelOffset by remember(rubberBandPx) {
        derivedStateOf {
            if (totalWidthPx == 0f) {
                0f
            } else {
                val fraction = (offsetAnimation.value / totalWidthPx).coerceIn(-1f, 1f)
                rubberBandPx * fraction.sign * EaseOut.transform(abs(fraction))
            }
        }
    }

    class DampedDragHolder {
        var instance: DampedDragAnimation? = null
    }
    val holder = remember { DampedDragHolder() }
    val dampedDrag = remember(animationScope, tabsCount, density, isLtr) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedIndex.toFloat(),
            valueRange = 0f..(tabsCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            canDrag = { offset ->
                val anim = holder.instance ?: return@DampedDragAnimation true
                if (tabWidthPx == 0f) return@DampedDragAnimation false
                val indicatorX = anim.value * tabWidthPx
                val paddingPx = with(density) { 4.dp.toPx() }
                val globalTouchX = if (isLtr) {
                    paddingPx + indicatorX + offset.x
                } else {
                    totalWidthPx - paddingPx - tabWidthPx - indicatorX + offset.x
                }
                globalTouchX in 0f..totalWidthPx
            },
            onDragStarted = {},
            onDragStopped = {
                val targetIndex = targetValue.roundToInt().coerceIn(0, tabsCount - 1)
                if (currentIndex != targetIndex) {
                    currentIndex = targetIndex
                } else {
                    animateToValue(targetIndex.toFloat())
                }
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDrag = { _, dragAmount ->
                if (tabWidthPx > 0f) {
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .coerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            }
        ).also { holder.instance = it }
    }

    LaunchedEffect(selectedIndex) {
        if (currentIndex != selectedIndex) currentIndex = selectedIndex
        dampedDrag.animateToValue(selectedIndex.toFloat())
    }
    LaunchedEffect(dampedDrag) {
        snapshotFlow { currentIndex }.drop(1).collectLatest { index ->
            dampedDrag.animateToValue(index.toFloat())
            if (index != selectedIndexState) {
                screensState.getOrNull(index)?.let(onSelectState)
            }
        }
    }

    val interactiveHighlight = remember(animationScope, isLtr) {
        InteractiveHighlight(
            animationScope = animationScope,
            position = { size, _ ->
                Offset(
                    x = if (isLtr) {
                        (dampedDrag.value + 0.5f) * tabWidthPx + panelOffset
                    } else {
                        size.width - (dampedDrag.value + 0.5f) * tabWidthPx + panelOffset
                    },
                    y = size.height / 2f
                )
            }
        )
    }
    val baseHighlight = rememberGravityRotatedHighlight(BabyLiquidIndicatorSpecular, extraDegrees = -45f)
    val pillHighlight = rememberGravityRotatedHighlight(BabyLiquidIndicatorSpecular, extraDegrees = 90f)
    val combinedBackdrop = if (backdrop != null && tabsBackdrop != null) {
        rememberCombinedBackdrop(backdrop, tabsBackdrop)
    } else {
        null
    }

    val tabsContent: @Composable RowScope.(Color, Boolean) -> Unit = { tint, enabled ->
        screens.forEachIndexed { index, screen ->
            YueMingNavigationBarItem(
                screen = screen,
                selected = index == selectedIndex,
                tint = tint,
                tabScale = { lerp(1f, 1.2f, dampedDrag.pressProgress) },
                enabled = enabled,
                onClick = { currentIndex = index }
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = BabyLiquidBottomBarVerticalPadding),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .widthIn(max = 402.dp)
                .height(BabyLiquidBottomBarHeight),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier
                    .onSizeChanged { size ->
                        totalWidthPx = size.width.toFloat()
                        val contentWidthPx = totalWidthPx - with(density) { 8.dp.toPx() }
                        tabWidthPx = (contentWidthPx / tabsCount).coerceAtLeast(0f)
                    }
                    .graphicsLayer { translationX = panelOffset }
                    .dropShadow(
                        shape = pillShape,
                        shadow = Shadow(
                            radius = 10.dp,
                            color = Color.Black,
                            alpha = if (isDark) 0.18f else 0.10f
                        )
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .then(
                        if (isBlurActive) {
                            Modifier.drawBackdrop(
                                backdrop = backdrop,
                                shape = { pillShape },
                                effects = {
                                    vibrancy()
                                    blur(4.dp.toPx(), 4.dp.toPx())
                                    lens(
                                        refractionHeight = 24.dp.toPx(),
                                        refractionAmount = 24.dp.toPx()
                                    )
                                },
                                highlight = { baseHighlight.copy(alpha = 0.75f) },
                                layerBlock = {
                                    val width = size.width.coerceAtLeast(1f)
                                    val scale = lerp(1f, 1f + 16.dp.toPx() / width, dampedDrag.pressProgress)
                                    scaleX = scale
                                    scaleY = scale
                                },
                                onDrawSurface = { drawRect(containerColor) }
                            )
                        } else {
                            Modifier.background(
                                if (isDark) Color(0xFF242428).copy(alpha = 0.78f) else Color.White.copy(alpha = 0.58f),
                                pillShape
                            )
                        }
                    )
                    .then(interactiveHighlight.modifier)
                    .height(BabyLiquidBottomBarHeight)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabsContent(baseContentColor, true)
            }

            if (isBlurActive) {
                Row(
                    modifier = Modifier
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .then(if (tabsBackdrop != null) Modifier.layerBackdrop(tabsBackdrop) else Modifier)
                        .graphicsLayer { translationX = panelOffset }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { pillShape },
                            effects = {
                                vibrancy()
                                blur(4.dp.toPx(), 4.dp.toPx())
                                lens(
                                    refractionHeight = 24.dp.toPx(),
                                    refractionAmount = 24.dp.toPx()
                                )
                            },
                            onDrawSurface = { drawRect(containerColor) }
                        )
                        .then(interactiveHighlight.modifier)
                        .height(BabyLiquidBottomBarHeight - 8.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabsContent(accentColor, false)
                }
            }

            if (tabWidthPx > 0f) {
                val tabWidthDp = with(density) { tabWidthPx.toDp() }
                if (isBlurActive && combinedBackdrop != null) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .graphicsLayer {
                                val progressOffset = dampedDrag.value * tabWidthPx
                                translationX = if (isLtr) progressOffset + panelOffset else -progressOffset + panelOffset
                            }
                            .then(interactiveHighlight.gestureModifier)
                            .then(dampedDrag.modifier)
                            .drawBackdrop(
                                backdrop = combinedBackdrop,
                                shape = { pillShape },
                                effects = {
                                    val progress = dampedDrag.pressProgress
                                    lens(
                                        refractionHeight = 10.dp.toPx() * progress,
                                        refractionAmount = 14.dp.toPx() * progress,
                                        depthEffect = true,
                                        chromaticAberration = 0.5f
                                    )
                                },
                                highlight = { pillHighlight.copy(alpha = dampedDrag.pressProgress) },
                                layerBlock = {
                                    scaleX = dampedDrag.scaleX
                                    scaleY = dampedDrag.scaleY
                                    val velocity = dampedDrag.velocity / 10f
                                    scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                                    scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                                },
                                onDrawSurface = {
                                    val progress = dampedDrag.pressProgress
                                    drawRect(
                                        color = if (isDark) {
                                            Color.White.copy(alpha = 0.10f)
                                        } else {
                                            Color.Black.copy(alpha = 0.10f)
                                        },
                                        alpha = 1f - progress
                                    )
                                    drawRect(Color.Black.copy(alpha = 0.03f * progress))
                                }
                            )
                            .innerShadow(shape = pillShape) {
                                InnerShadow(
                                    radius = 8.dp * dampedDrag.pressProgress,
                                    color = Color.Black.copy(alpha = 0.15f),
                                    alpha = dampedDrag.pressProgress
                                )
                            }
                            .height(BabyLiquidBottomBarHeight - 8.dp)
                            .width(tabWidthDp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .graphicsLayer {
                                val progressOffset = dampedDrag.value * tabWidthPx
                                translationX = if (isLtr) progressOffset + panelOffset else -progressOffset + panelOffset
                                scaleX = dampedDrag.scaleX
                                scaleY = dampedDrag.scaleY
                                val velocity = dampedDrag.velocity / 10f
                                scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                                scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                            }
                            .then(interactiveHighlight.gestureModifier)
                            .then(dampedDrag.modifier)
                            .clip(pillShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = if (isDark) 0.20f else 0.58f),
                                        accentColor.copy(alpha = if (isDark) 0.20f else 0.14f),
                                        Color.Transparent
                                    ),
                                    center = Offset(tabWidthPx / 2f, 0f),
                                    radius = tabWidthPx.coerceAtLeast(1f) * 1.15f
                                ),
                                pillShape
                            )
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = if (isDark) 0.12f else 0.34f),
                                        Color.White.copy(alpha = if (isDark) 0.04f else 0.12f),
                                        Color.Black.copy(alpha = if (isDark) 0.16f else 0.04f)
                                    )
                                ),
                                pillShape
                            )
                            .border(
                                BorderStroke(
                                    0.8.dp,
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = if (isDark) 0.20f else 0.58f),
                                            Color.White.copy(alpha = if (isDark) 0.08f else 0.22f),
                                            Color.Black.copy(alpha = if (isDark) 0.14f else 0.03f)
                                        )
                                    )
                                ),
                                pillShape
                            )
                            .innerShadow(shape = pillShape) {
                                InnerShadow(
                                    radius = 8.dp * (0.45f + dampedDrag.pressProgress * 0.55f),
                                    color = Color.Black.copy(alpha = if (isDark) 0.20f else 0.10f),
                                    alpha = 0.36f + dampedDrag.pressProgress * 0.28f
                                )
                            }
                            .height(BabyLiquidBottomBarHeight - 8.dp)
                            .width(tabWidthDp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.YueMingNavigationBarItem(
    screen: Screen,
    selected: Boolean,
    tint: Color,
    tabScale: () -> Float,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    val tapPulse = remember { Animatable(1f) }
    val selectedProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = BabyMotion.fastSpatial<Float>(),
        label = "bottomBarSelectedProgress"
    )

    val itemColor by animateColorAsState(
        targetValue = tint.copy(alpha = if (selected) 0.98f else tint.alpha),
        animationSpec = BabyMotion.colorFadeSpec(),
        label = "bottomBarItemColor"
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(CircleShape)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Tab,
                        onClick = {
                            scope.launch {
                                tapPulse.snapTo(0.90f)
                                tapPulse.animateTo(1.10f, tween(durationMillis = 120, easing = FastOutSlowInEasing))
                                tapPulse.animateTo(1f, tween(durationMillis = 160, easing = FastOutSlowInEasing))
                            }
                            onClick()
                        }
                    )
                } else {
                    Modifier
                }
            )
            .graphicsLayer {
                val scale = tabScale()
                scaleX = scale
                scaleY = scale
            }
            .padding(horizontal = 1.dp, vertical = 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .height(32.dp)
                .width(46.dp + 2.dp * selectedProgress),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                contentDescription = screen.title,
                tint = itemColor,
                modifier = Modifier
                    .size(21.dp)
                    .graphicsLayer {
                        alpha = 0.78f + 0.22f * selectedProgress
                        scaleX = (0.94f + 0.08f * selectedProgress) * tapPulse.value
                        scaleY = (0.94f + 0.08f * selectedProgress) * tapPulse.value
                        translationY = -2f * selectedProgress
                        rotationZ = 5f * (tapPulse.value - 1f)
                    }
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            screen.title,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 11.sp),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = itemColor,
            maxLines = 1,
            overflow = TextOverflow.Visible,
            softWrap = false
        )
    }
}

private fun Modifier.liquidGlassChrome(
    shape: Shape,
    isDark: Boolean,
    borderColor: Color = Color.White.copy(alpha = if (isDark) 0.18f else 0.42f),
    topHighlightAlpha: Float = if (isDark) 0.14f else 0.34f
): Modifier = this.then(
    Modifier
        .background(
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = topHighlightAlpha),
                    Color.White.copy(alpha = if (isDark) 0.05f else 0.14f),
                    Color.Transparent,
                    Color.Black.copy(alpha = if (isDark) 0.16f else 0.055f)
                )
            ),
            shape
        )
        .background(
            Brush.radialGradient(
                listOf(
                    Color.White.copy(alpha = if (isDark) 0.12f else 0.34f),
                    Color.Transparent
                ),
                radius = 480f
            ),
            shape
        )
        .border(
            BorderStroke(
                0.8.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (isDark) 0.30f else 0.62f),
                        borderColor,
                        Color.Black.copy(alpha = if (isDark) 0.24f else 0.06f)
                    )
                )
            ),
            shape
        )
)

@Composable
fun BabySwitcher(
    babies: List<BabyInfo>,
    activeBaby: BabyInfo,
    onSelect: (BabyInfo) -> Unit,
    onAddBaby: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .clickable { expanded = true }
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(0xFFF8C8D8)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                activeBaby.nickname.take(1),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            activeBaby.nickname,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(2.dp))
        Icon(
            Icons.Default.ArrowDropDown,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            babies.forEach { baby ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(28.dp).clip(CircleShape)
                                    .background(if (baby.id == activeBaby.id) MaterialTheme.colorScheme.primary else Color(0xFFE0E0E0)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    baby.nickname.take(1),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(baby.nickname, style = MaterialTheme.typography.bodyMedium)
                                Text(baby.name, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    onClick = {
                        if (baby.id != activeBaby.id) onSelect(baby)
                        expanded = false
                    },
                    leadingIcon = if (baby.id == activeBaby.id) {
                        { Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) }
                    } else null
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("添加宝宝", color = MaterialTheme.colorScheme.primary) },
                onClick = {
                    expanded = false
                    onAddBaby()
                },
                leadingIcon = {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            )
        }
    }
}
