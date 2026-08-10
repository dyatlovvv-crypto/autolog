package ru.autolog.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class GlassTokens(
    val fill: Color,
    val stroke: Color,
    val highlight: Color,
    val scrim: Color,
)

val LocalGlassTokens = staticCompositionLocalOf {
    GlassTokens(
        fill = AutologColors.GlassFillLight,
        stroke = AutologColors.GlassStrokeLight,
        highlight = AutologColors.GlassHighlight,
        scrim = Color(0x14000000),
    )
}

private val LightColors = lightColorScheme(
    primary = AutologColors.Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xAAD6E4FF),
    onPrimaryContainer = AutologColors.PrimaryDark,
    secondary = AutologColors.Secondary,
    onSecondary = Color.White,
    tertiary = AutologColors.Fuel,
    onTertiary = Color.White,
    background = AutologColors.BackgroundLight,
    onBackground = AutologColors.Ink,
    surface = Color(0xE6FFFFFF),
    onSurface = AutologColors.Ink,
    surfaceVariant = Color(0xCCE3E8EF),
    onSurfaceVariant = Color(0xFF2F3A4D),
    outline = Color(0x66A8B4C8),
    surfaceContainerHighest = Color(0xD9FFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = AutologColors.PrimaryLight,
    onPrimary = AutologColors.BackgroundDark,
    primaryContainer = Color(0xAA0D47A1),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFF4DD0E1),
    onSecondary = AutologColors.BackgroundDark,
    tertiary = Color(0xFFFFB74D),
    onTertiary = AutologColors.BackgroundDark,
    background = AutologColors.BackgroundDark,
    onBackground = AutologColors.OnDark,
    surface = Color(0xCC1A2235),
    onSurface = AutologColors.OnDark,
    surfaceVariant = Color(0x992A3442),
    onSurfaceVariant = Color(0xFFB8C2D0),
    outline = Color(0x443E4A5A),
    surfaceContainerHighest = Color(0xBB243044),
)

@Composable
fun AutologTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val glass = if (darkTheme) {
        GlassTokens(
            fill = AutologColors.GlassFillDark,
            stroke = AutologColors.GlassStrokeDark,
            highlight = Color(0x33FFFFFF),
            scrim = Color(0x66000000),
        )
    } else {
        GlassTokens(
            fill = AutologColors.GlassFillLight,
            stroke = AutologColors.GlassStrokeLight,
            highlight = AutologColors.GlassHighlight,
            scrim = Color(0x10000000),
        )
    }
    CompositionLocalProvider(LocalGlassTokens provides glass) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            content = content,
        )
    }
}
