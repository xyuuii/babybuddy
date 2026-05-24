package com.yueming.baby.ui.theme

import android.app.Activity
import android.content.Context
import android.os.Build
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
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

enum class ThemeMode { LIGHT, DARK, SYSTEM }

private val LightColorScheme = lightColorScheme(
    primary = Pink40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE8EF),
    onPrimaryContainer = Pink20,
    secondary = BabyBlue,
    onSecondary = OnSurfaceLight,
    secondaryContainer = Color(0xFFF0F6FA),
    onSecondaryContainer = Color(0xFF244B63),
    tertiary = BabyGold,
    onTertiary = Color(0xFF402D00),
    tertiaryContainer = Color(0xFFFFF6E7),
    onTertiaryContainer = Color(0xFF5B3C00),
    surface = SurfaceLight,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFAFB),
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = Color(0xFFF0F0F2),
    surfaceContainerHighest = Color(0xFFE6E6EA),
    onSurface = OnSurfaceLight,
    onSurfaceVariant = Color(0xFF636366),
    surfaceVariant = Color(0xFFEDEDEF),
    outline = Color(0xFFC7C7CC),
    outlineVariant = Color(0xFFE5E5EA),
    background = Color(0xFFFBFBFD),
    onBackground = OnSurfaceLight,
    surfaceTint = Pink40,
)

private val DarkColorScheme = darkColorScheme(
    primary = PinkPrimaryDark,
    onPrimary = Color(0xFF17171A),
    primaryContainer = Color(0xFF3A2430),
    onPrimaryContainer = Pink80,
    secondary = Color(0xFFA7CCE8),
    onSecondary = Color(0xFF101418),
    secondaryContainer = Color(0xFF1D3342),
    onSecondaryContainer = Color(0xFFD8EEFF),
    tertiary = Color(0xFFFFC56F),
    onTertiary = Color(0xFF18130A),
    tertiaryContainer = Color(0xFF3D2D12),
    onTertiaryContainer = Color(0xFFFFE6B9),
    surface = SurfaceDark,
    surfaceContainerLowest = Color(0xFF0B0B0F),
    surfaceContainerLow = Color(0xFF16161A),
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = Color(0xFF2A2A2E),
    surfaceContainerHighest = Color(0xFF34343A),
    onSurface = OnSurfaceDark,
    onSurfaceVariant = Color(0xFFAEAEB2),
    surfaceVariant = Color(0xFF303036),
    outline = Color(0xFF767680),
    outlineVariant = Color(0xFF3F3F46),
    background = Color(0xFF0F0F13),
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
        displayLarge = base.displayLarge.copy(
            fontFamily = appFontFamily,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp
        ),
        displayMedium = base.displayMedium.copy(
            fontFamily = appFontFamily,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp
        ),
        displaySmall = base.displaySmall.copy(
            fontFamily = appFontFamily,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp
        ),
        headlineLarge = base.headlineLarge.copy(
            fontFamily = appFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 30.sp,
            lineHeight = 36.sp,
            letterSpacing = 0.sp
        ),
        headlineMedium = base.headlineMedium.copy(
            fontFamily = appFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 27.sp,
            lineHeight = 33.sp,
            letterSpacing = 0.sp
        ),
        headlineSmall = base.headlineSmall.copy(
            fontFamily = appFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            letterSpacing = 0.sp
        ),
        titleLarge = base.titleLarge.copy(
            fontFamily = appFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 21.sp,
            lineHeight = 27.sp,
            letterSpacing = 0.sp
        ),
        titleMedium = base.titleMedium.copy(
            fontFamily = appFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 17.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.sp
        ),
        titleSmall = base.titleSmall.copy(
            fontFamily = appFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.sp
        ),
        bodyLarge = base.bodyLarge.copy(
            fontFamily = appFontFamily,
            fontSize = 17.sp,
            lineHeight = 23.sp,
            letterSpacing = 0.sp
        ),
        bodyMedium = base.bodyMedium.copy(
            fontFamily = appFontFamily,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            letterSpacing = 0.sp
        ),
        bodySmall = base.bodySmall.copy(
            fontFamily = appFontFamily,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.sp
        ),
        labelLarge = base.labelLarge.copy(
            fontFamily = appFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.sp
        ),
        labelMedium = base.labelMedium.copy(
            fontFamily = appFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.sp
        ),
        labelSmall = base.labelSmall.copy(
            fontFamily = appFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            letterSpacing = 0.sp
        ),
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
            window.navigationBarColor = colorScheme.background.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
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
