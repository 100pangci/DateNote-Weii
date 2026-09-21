package com.datenote.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.datenote.app.data.repository.ThemeMode

private val LightColors = lightColorScheme(
    primary = RosePrimary,
    onPrimary = RoseOnPrimary,
    primaryContainer = RoseContainer,
    onPrimaryContainer = RoseOnContainer,
    secondary = Lavender,
    background = CreamBackground,
    onBackground = WarmOnLight,
    surface = WarmSurfaceLight,
    onSurface = WarmOnLight,
    surfaceVariant = WarmSurfaceVariantLight,
    onSurfaceVariant = WarmOnLightVariant,
    outline = WarmOutlineLight,
    outlineVariant = WarmOutlineVariantLight,
    surfaceContainer = WarmSurfaceContainerLight,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB0D0),
    onPrimary = Color(0xFF541337),
    primaryContainer = Color(0xFF713352),
    onPrimaryContainer = Color(0xFFFFD9E7),
    secondary = Color(0xFFD0BCFF),
    background = WarmBackgroundDark,
    onBackground = WarmOnDark,
    surface = WarmSurfaceDark,
    onSurface = WarmOnDark,
    surfaceVariant = WarmSurfaceVariantDark,
    onSurfaceVariant = WarmOnDarkVariant,
    outline = WarmOutlineDark,
    outlineVariant = WarmOutlineVariantDark,
    surfaceContainer = WarmSurfaceContainerDark,
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun DateNoteTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
            dynamicDarkColorScheme(context)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
