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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yueming.baby.data.BabyInfo
import com.yueming.baby.data.DataManager
import com.yueming.baby.ui.components.BabyPalette
import com.yueming.baby.ui.motion.BabyMotion
import com.yueming.baby.ui.motion.motionPressable
import com.yueming.baby.ui.screens.*
import com.yueming.baby.ui.theme.YueMingTheme
import com.yueming.baby.ui.theme.ThemeMode
import kotlinx.coroutines.launch
import java.io.StringWriter
import java.io.PrintWriter

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

sealed class Screen(val route: String, val title: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    data object Dashboard : Screen("dashboard", "首页", Icons.Filled.Home, Icons.Outlined.Home)
    data object Timeline  : Screen("timeline", "时间线", Icons.Filled.DateRange, Icons.Outlined.DateRange)
    data object Photos    : Screen("photos", "照片", Icons.Filled.PhotoLibrary, Icons.Outlined.PhotoLibrary)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YueMingApp() {
    val navController = rememberNavController()
    val screens = listOf(Screen.Dashboard, Screen.Timeline, Screen.Photos, Screen.AI, Screen.Settings)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route
    val selectedIndex = screenRouteIndex(currentRoute).coerceIn(0, screens.lastIndex)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            BabyLiquidBottomBar(
                screens = screens,
                selectedIndex = selectedIndex,
                onSelect = { screen ->
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Dashboard.route,
                modifier = Modifier
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background),
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
        }
    }
}

@Composable
private fun BabyLiquidBottomBar(
    screens: List<Screen>,
    selectedIndex: Int,
    onSelect: (Screen) -> Unit
) {
    val shape = CircleShape
    val fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 430.dp)
                .height(68.dp)
                .shadow(
                    elevation = 18.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = if (isDark) 0.28f else 0.14f),
                    spotColor = Color.Black.copy(alpha = if (isDark) 0.22f else 0.12f)
                )
                .clip(shape)
                .background(fallbackColor, shape)
                .liquidGlassBorder(shape = shape)
                .padding(4.dp)
        ) {
            val tabWidth = maxWidth / screens.size
            val selectedPosition by animateFloatAsState(
                targetValue = selectedIndex.toFloat(),
                animationSpec = spring(
                    dampingRatio = 0.76f,
                    stiffness = 420f
                ),
                label = "liquidBottomBarIndicatorPosition"
            )

            Box(
                modifier = Modifier
                    .offset(x = tabWidth * selectedPosition)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(BabyPalette.Rose.copy(alpha = if (isDark) 0.28f else 0.18f))
                    .liquidGlassBorder(
                        shape = shape,
                        color = BabyPalette.Rose.copy(alpha = 0.28f)
                    )
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                screens.forEachIndexed { index, screen ->
                    val selected = index == selectedIndex
                    YueMingNavigationBarItem(
                        screen = screen,
                        selected = selected,
                        onClick = { onSelect(screen) }
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
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
        },
        animationSpec = BabyMotion.colorFadeSpec(),
        label = "bottomBarItemColor"
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(CircleShape)
            .motionPressable(interactionSource, pressedScale = 0.94f)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    scope.launch {
                        tapPulse.snapTo(0.88f)
                        tapPulse.animateTo(1.14f, tween(durationMillis = 120, easing = FastOutSlowInEasing))
                        tapPulse.animateTo(1f, tween(durationMillis = 160, easing = FastOutSlowInEasing))
                    }
                    onClick()
                }
            )
            .padding(horizontal = 1.dp, vertical = 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .height(31.dp)
                .width(42.dp + 8.dp * selectedProgress)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Transparent),
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

private fun Modifier.liquidGlassBorder(
    shape: Shape,
    color: Color = Color.White.copy(alpha = 0.28f)
): Modifier = this.then(
    Modifier
        .background(
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.22f),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.035f)
                )
            ),
            shape
        )
        .border(BorderStroke(0.8.dp, color), shape)
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
