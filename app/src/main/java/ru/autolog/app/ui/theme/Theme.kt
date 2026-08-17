package ru.autolog.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import ru.autolog.app.data.settings.AppPalette

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

private val LightColors = AutologLightScheme

private val DarkColors = AutologDarkScheme

private val WarmLight = lightColorScheme(
    primary = Color(0xFFBF360C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFCCBC),
    onPrimaryContainer = Color(0xFF3E2723),
    secondary = Color(0xFFEF6C00),
    onSecondary = Color.White,
    tertiary = Color(0xFFFF8F00),
    onTertiary = Color.White,
    background = Color(0xFFFFF8F1),
    onBackground = Color(0xFF3E2723),
    surface = Color(0xE6FFF3E0),
    onSurface = Color(0xFF3E2723),
    surfaceVariant = Color(0xCCFFE0B2),
    onSurfaceVariant = Color(0xFF5D4037),
    outline = Color(0x66BCAAA4),
)

private val CoolLight = lightColorScheme(
    primary = Color(0xFF0277BD),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB3E5FC),
    onPrimaryContainer = Color(0xFF01579B),
    secondary = Color(0xFF00838F),
    onSecondary = Color.White,
    tertiary = Color(0xFF5C6BC0),
    onTertiary = Color.White,
    background = Color(0xFFE3F2FD),
    onBackground = Color(0xFF0D2137),
    surface = Color(0xE6FFFFFF),
    onSurface = Color(0xFF0D2137),
    surfaceVariant = Color(0xCCBBDEFB),
    onSurfaceVariant = Color(0xFF37474F),
    outline = Color(0x6690A4AE),
)

private val AmoledDark = darkColorScheme(
    primary = Color(0xFF82B1FF),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF0D47A1),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFF80DEEA),
    onSecondary = Color.Black,
    tertiary = Color(0xFFFFB74D),
    onTertiary = Color.Black,
    background = Color(0xFF000000),
    onBackground = Color(0xFFECEFF1),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFECEFF1),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFB0BEC5),
    outline = Color(0xFF37474F),
)

@Composable
fun AutologTheme(
    palette: AppPalette = AppPalette.SYSTEM,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme: ColorScheme = when (palette) {
        AppPalette.SYSTEM -> if (darkTheme) DarkColors else LightColors
        AppPalette.WARM -> if (darkTheme) DarkColors.copy(
            primary = Color(0xFFFFAB91),
            secondary = Color(0xFFFFB74D),
            background = Color(0xFF1A120E),
            surface = Color(0xFF241A14),
        ) else WarmLight
        AppPalette.COOL -> if (darkTheme) DarkColors.copy(
            primary = Color(0xFF81D4FA),
            secondary = Color(0xFF80CBC4),
            background = Color(0xFF0A1620),
            surface = Color(0xFF12202C),
        ) else CoolLight
        AppPalette.AMOLED -> AmoledDark
        AppPalette.DYNAMIC -> {
            if (Build.VERSION.SDK_INT >= 31) {
                if (darkTheme || palette == AppPalette.AMOLED) {
                    dynamicDarkColorScheme(context)
                } else {
                    dynamicLightColorScheme(context)
                }
            } else {
                if (darkTheme) DarkColors else LightColors
            }
        }
    }

    val forceDarkGlass = palette == AppPalette.AMOLED ||
        (palette != AppPalette.WARM && palette != AppPalette.COOL && darkTheme) ||
        (palette == AppPalette.DYNAMIC && darkTheme) ||
        (palette == AppPalette.WARM && darkTheme) ||
        (palette == AppPalette.COOL && darkTheme)

    val glass = if (forceDarkGlass || palette == AppPalette.AMOLED) {
        GlassTokens(
            fill = if (palette == AppPalette.AMOLED) Color(0xF0181818) else AutologColors.GlassFillDark,
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
    CompositionLocalProvider(
        LocalGlassTokens provides glass,
        LocalAutologSpace provides AutologSpace,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AutologTypography,
            content = content,
        )
    }
}
