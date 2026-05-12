package com.yueming.baby.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class ThemeMode { LIGHT, DARK, SYSTEM }

private val LightColorScheme = lightColorScheme(
    primary = Pink40,
    onPrimary = Color.White,
    primaryContainer = Pink80,
    onPrimaryContainer = Pink20,
    secondary = Peach80,
    onSecondary = OnSurfaceLight,
    surface = SurfaceLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerLight,
    surfaceContainerHighest = SurfaceContainerLight.copy(alpha = 0.8f),
    onSurface = OnSurfaceLight,
    onSurfaceVariant = Color(0xFF6B5B5B),
    background = Color(0xFFFFF8F0),
    onBackground = OnSurfaceLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = PinkPrimaryDark,
    onPrimary = SurfaceDark,
    primaryContainer = Pink20,
    onPrimaryContainer = Pink80,
    secondary = PinkSecondaryDark,
    tertiary = PinkTertiaryDark,
    surface = SurfaceDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerDark,
    surfaceContainerHighest = SurfaceContainerDark.copy(alpha = 0.8f),
    onSurface = OnSurfaceDark,
    onSurfaceVariant = Color(0xFFCAC4D0),
    background = Color(0xFF121212),
    onBackground = OnSurfaceDark,
)

@Composable
fun YueMingTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
