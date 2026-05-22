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
    secondary = BabyBlue,
    onSecondary = OnSurfaceLight,
    secondaryContainer = Color(0xFFEAF4FC),
    onSecondaryContainer = Color(0xFF244B63),
    tertiary = BabyGold,
    onTertiary = Color(0xFF402D00),
    tertiaryContainer = Color(0xFFFFF2D9),
    onTertiaryContainer = Color(0xFF5B3C00),
    surface = SurfaceLight,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF7F2),
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = Color(0xFFFFEAF0),
    surfaceContainerHighest = Color(0xFFFFDCE7),
    onSurface = OnSurfaceLight,
    onSurfaceVariant = Color(0xFF705D62),
    surfaceVariant = Color(0xFFF8E7E5),
    outline = Color(0xFFD9C6C3),
    outlineVariant = Color(0xFFEEDDD8),
    background = Color(0xFFFFF8F2),
    onBackground = OnSurfaceLight,
    surfaceTint = Pink40,
)

private val DarkColorScheme = darkColorScheme(
    primary = PinkPrimaryDark,
    onPrimary = SurfaceDark,
    primaryContainer = Pink20,
    onPrimaryContainer = Pink80,
    secondary = Color(0xFFA7CCE8),
    secondaryContainer = Color(0xFF263D4C),
    tertiary = PinkTertiaryDark,
    tertiaryContainer = Color(0xFF553F24),
    surface = SurfaceDark,
    surfaceContainerLowest = Color(0xFF151315),
    surfaceContainerLow = Color(0xFF1D191E),
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = Color(0xFF302930),
    surfaceContainerHighest = Color(0xFF3B3039),
    onSurface = OnSurfaceDark,
    onSurfaceVariant = Color(0xFFD4C5CC),
    surfaceVariant = Color(0xFF42383F),
    outline = Color(0xFF94838B),
    outlineVariant = Color(0xFF54474F),
    background = Color(0xFF141113),
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
