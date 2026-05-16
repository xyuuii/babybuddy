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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yueming.baby.data.BabyInfo
import com.yueming.baby.data.DataManager
import com.yueming.baby.ui.motion.BabyMotion
import com.yueming.baby.ui.motion.miuixPressable
import com.yueming.baby.ui.screens.*
import com.yueming.baby.ui.theme.YueMingTheme
import com.yueming.baby.ui.theme.ThemeMode
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YueMingApp() {
    val navController = rememberNavController()
    val screens = listOf(Screen.Dashboard, Screen.Timeline, Screen.Photos, Screen.AI, Screen.Settings)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            MiuixShellNavigationBar {
                screens.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    YueMingNavigationBarItem(
                        screen = screen,
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            enterTransition = {
                fadeIn(animationSpec = BabyMotion.pageFadeSpec()) +
                    scaleIn(initialScale = 0.985f, animationSpec = BabyMotion.pageScaleSpec())
            },
            exitTransition = {
                fadeOut(animationSpec = tween(80))
            },
            popEnterTransition = {
                fadeIn(animationSpec = BabyMotion.pageFadeSpec()) +
                    scaleIn(initialScale = 0.985f, animationSpec = BabyMotion.pageScaleSpec())
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(80))
            }
        ) {
            composable(Screen.Dashboard.route) { DashboardScreen() }
            composable(Screen.Timeline.route) { TimelineScreen() }
            composable(Screen.Photos.route) { PhotosScreen() }
            composable(Screen.AI.route) { AIScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}

@Composable
private fun MiuixShellNavigationBar(
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(80.dp)
                .padding(horizontal = 14.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
private fun RowScope.YueMingNavigationBarItem(
    screen: Screen,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val selectedProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "bottomBarSelectedProgress"
    )

    val itemColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
        },
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "bottomBarItemColor"
    )
    val indicatorColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "bottomBarIndicatorColor"
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(26.dp))
            .miuixPressable(interactionSource, pressedScale = 0.94f)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 2.dp, vertical = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .height(34.dp)
                .width(60.dp + 16.dp * selectedProgress)
                .clip(RoundedCornerShape(20.dp))
                .background(indicatorColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                contentDescription = screen.title,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else itemColor,
                modifier = Modifier
                    .size(21.dp)
                    .graphicsLayer {
                        alpha = 0.78f + 0.22f * selectedProgress
                        scaleX = 0.94f + 0.08f * selectedProgress
                        scaleY = 0.94f + 0.08f * selectedProgress
                        translationY = -2f * selectedProgress
                    }
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            screen.title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = itemColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

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
