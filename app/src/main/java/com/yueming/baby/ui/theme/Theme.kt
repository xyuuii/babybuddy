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
    tertiary = Pink60,
    tertiaryContainer = Pink80,
    surface = SurfaceLight,
    surfaceContainerLowest = Color(0xFFFFFBF8),
    surfaceContainerLow = Color(0xFFFFF3EF),
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = Color(0xFFFFF0EC),
    surfaceContainerHighest = Color(0xFFFEEAE5),
    onSurface = OnSurfaceLight,
    onSurfaceVariant = Color(0xFF6B5B5B),
    surfaceVariant = Color(0xFFF5E8E4),
    outline = Color(0xFFD4C4C0),
    outlineVariant = Color(0xFFE8DCD8),
    background = Color(0xFFFFF8F0),
    onBackground = OnSurfaceLight,
    surfaceTint = Pink40,
)

private val DarkColorScheme = darkColorScheme(
    primary = PinkPrimaryDark,
    onPrimary = SurfaceDark,
    primaryContainer = Pink20,
    onPrimaryContainer = Pink80,
    secondary = PinkSecondaryDark,
    tertiary = PinkTertiaryDark,
    surface = SurfaceDark,
    surfaceContainerLowest = Color(0xFF151315),
    surfaceContainerLow = Color(0xFF1B191B),
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = Color(0xFF2E2A32),
    surfaceContainerHighest = Color(0xFF39343E),
    onSurface = OnSurfaceDark,
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceVariant = Color(0xFF3D3840),
    outline = Color(0xFF8A848C),
    outlineVariant = Color(0xFF4A444C),
    background = Color(0xFF121212),
    onBackground = OnSurfaceDark,
    surfaceTint = PinkPrimaryDark,
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
