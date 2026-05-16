package com.yueming.baby.ui.theme

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

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
    background = Color(0xFFFFF6F0),
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
    background = Color(0xFF0F0F12),
    onBackground = OnSurfaceDark,
    surfaceTint = PinkPrimaryDark,
)

private fun Context.optionalMiSansFontFamily(): FontFamily? {
    val regular = resources.getIdentifier("misans_regular", "font", packageName)
    val medium = resources.getIdentifier("misans_medium", "font", packageName)
    val semibold = resources.getIdentifier("misans_semibold", "font", packageName)
    val bold = resources.getIdentifier("misans_bold", "font", packageName)

    if (regular == 0 || medium == 0 || semibold == 0 || bold == 0) {
        return null
    }

    return FontFamily(
        Font(regular, FontWeight.Normal),
        Font(medium, FontWeight.Medium),
        Font(semibold, FontWeight.SemiBold),
        Font(bold, FontWeight.Bold),
    )
}

private fun appTypography(appFontFamily: FontFamily) = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = appFontFamily, fontWeight = FontWeight.SemiBold),
        displayMedium = base.displayMedium.copy(fontFamily = appFontFamily, fontWeight = FontWeight.SemiBold),
        displaySmall = base.displaySmall.copy(fontFamily = appFontFamily, fontWeight = FontWeight.Medium),
        headlineLarge = base.headlineLarge.copy(fontFamily = appFontFamily, fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontFamily = appFontFamily, fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontFamily = appFontFamily, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontFamily = appFontFamily, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = appFontFamily, fontWeight = FontWeight.Medium),
        titleSmall = base.titleSmall.copy(fontFamily = appFontFamily, fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.copy(fontFamily = appFontFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = appFontFamily),
        bodySmall = base.bodySmall.copy(fontFamily = appFontFamily),
        labelLarge = base.labelLarge.copy(fontFamily = appFontFamily, fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontFamily = appFontFamily, fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.copy(fontFamily = appFontFamily, fontWeight = FontWeight.Medium),
    )
}

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
    val context = LocalContext.current
    val appFontFamily = remember(context) {
        context.optionalMiSansFontFamily() ?: FontFamily.Default
    }
    val typography = remember(appFontFamily) {
        appTypography(appFontFamily)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    val miuixMode = when (themeMode) {
        ThemeMode.LIGHT -> ColorSchemeMode.Light
        ThemeMode.DARK -> ColorSchemeMode.Dark
        ThemeMode.SYSTEM -> ColorSchemeMode.System
    }
    val miuixController = remember(themeMode) {
        ThemeController(miuixMode, keyColor = Pink40)
    }

    MiuixTheme(controller = miuixController) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
