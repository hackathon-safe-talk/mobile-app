package com.snow.safetalk.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val DarkColorScheme = darkColorScheme(
    primary        = DarkPalette.primaryBlue,
    secondary      = DarkPalette.primaryBlue,
    tertiary       = DarkPalette.primaryBlue,
    background     = DarkPalette.bgSolid,
    surface        = DarkPalette.bgSolid,
    surfaceVariant = DarkPalette.cardBg,
    onPrimary      = DarkPalette.textMain,
    onSecondary    = DarkPalette.textMain,
    onTertiary     = DarkPalette.textMain,
    onBackground   = DarkPalette.textMain,
    onSurface      = DarkPalette.textMain,
)

private val LightColorScheme = lightColorScheme(
    primary        = LightPalette.primaryBlue,
    secondary      = LightPalette.primaryBlue,
    tertiary       = LightPalette.primaryBlue,
    background     = LightPalette.bgSolid,
    surface        = LightPalette.bgSolid,
    surfaceVariant = LightPalette.cardBg,
    onPrimary      = LightPalette.textMain,
    onSecondary    = LightPalette.textMain,
    onTertiary     = LightPalette.textMain,
    onBackground   = LightPalette.textMain,
    onSurface      = LightPalette.textMain,
)

/**
 * App-wide theme wrapper.
 * Provides both Material3 colorScheme AND the SafeTalk palette via CompositionLocal
 * so AppColors.X auto-switches with theme.
 */
@Composable
fun SafeTalkTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val palette      = if (darkTheme) DarkPalette else LightPalette
    val colorScheme  = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalSafeTalkColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = Typography,
            content     = content
        )
    }
}