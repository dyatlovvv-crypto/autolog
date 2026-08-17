package ru.autolog.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Autolog design tokens. Screens should use these instead of one-off dp / hex.
 *
 * Type contract for product copy:
 * - TitleLarge: screen and section titles
 * - BodyMedium: primary reading text
 * - LabelSmall: captions, month rails, units
 */
object AutologSpace {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
}

object AutologRadii {
    val card: Dp = 20.dp
}

object AutologElevation {
    val card: Dp = 2.dp
}

val LocalAutologSpace = staticCompositionLocalOf { AutologSpace }

/** Instrument-cluster palette: asphalt night, fuel amber, paper-white. */
object AutologPalette {
    val Primary = Color(0xFF0E4AA8)
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryContainer = Color(0xFFD6E4FF)
    val OnPrimaryContainer = Color(0xFF001A41)

    val Secondary = Color(0xFF0B6B73)
    val OnSecondary = Color(0xFFFFFFFF)
    val SecondaryContainer = Color(0xFFC5F0F4)
    val OnSecondaryContainer = Color(0xFF002022)

    val Tertiary = Color(0xFFB45309)
    val OnTertiary = Color(0xFFFFFFFF)

    val BackgroundLight = Color(0xFFF3F5F8)
    val SurfaceLight = Color(0xFFFFFFFF)
    val OnLight = Color(0xFF121820)
    val OnLightMuted = Color(0xFF3A4556)
    val OutlineLight = Color(0xFF6B7686)

    val BackgroundDark = Color(0xFF0B1020)
    val SurfaceDark = Color(0xFF1A2233)
    val OnDark = Color(0xFFF2F4F7)
    val OnDarkMuted = Color(0xFFC5CDD8)
    val OutlineDark = Color(0xFF8B95A5)
    val PrimaryDark = Color(0xFF9EC0FF)
    val OnPrimaryDark = Color(0xFF001B3D)
    val SecondaryDark = Color(0xFF7FDEE6)
    val OnSecondaryDark = Color(0xFF00363A)
    val TertiaryDark = Color(0xFFFFB74D)
    val OnTertiaryDark = Color(0xFF3A2200)

    val Error = Color(0xFFB3261E)
    val OnError = Color(0xFFFFFFFF)
    val ErrorDark = Color(0xFFFFB4AB)
    val OnErrorDark = Color(0xFF410002)
}

val AutologLightScheme = lightColorScheme(
    primary = AutologPalette.Primary,
    onPrimary = AutologPalette.OnPrimary,
    primaryContainer = AutologPalette.PrimaryContainer,
    onPrimaryContainer = AutologPalette.OnPrimaryContainer,
    secondary = AutologPalette.Secondary,
    onSecondary = AutologPalette.OnSecondary,
    secondaryContainer = AutologPalette.SecondaryContainer,
    onSecondaryContainer = AutologPalette.OnSecondaryContainer,
    tertiary = AutologPalette.Tertiary,
    onTertiary = AutologPalette.OnTertiary,
    background = AutologPalette.BackgroundLight,
    onBackground = AutologPalette.OnLight,
    surface = AutologPalette.SurfaceLight,
    onSurface = AutologPalette.OnLight,
    surfaceVariant = Color(0xFFE4E8EE),
    onSurfaceVariant = AutologPalette.OnLightMuted,
    outline = AutologPalette.OutlineLight,
    outlineVariant = Color(0xFFC5CDD6),
    error = AutologPalette.Error,
    onError = AutologPalette.OnError,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F8FA),
    surfaceContainer = Color(0xFFEEF1F5),
    surfaceContainerHigh = Color(0xFFE6EAEE),
    surfaceContainerHighest = Color(0xFFDEE3E9),
    inverseSurface = AutologPalette.BackgroundDark,
    inverseOnSurface = AutologPalette.OnDark,
    inversePrimary = AutologPalette.PrimaryDark,
)

val AutologDarkScheme = darkColorScheme(
    primary = AutologPalette.PrimaryDark,
    onPrimary = AutologPalette.OnPrimaryDark,
    primaryContainer = Color(0xFF163A78),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = AutologPalette.SecondaryDark,
    onSecondary = AutologPalette.OnSecondaryDark,
    secondaryContainer = Color(0xFF004F55),
    onSecondaryContainer = Color(0xFFC5F0F4),
    tertiary = AutologPalette.TertiaryDark,
    onTertiary = AutologPalette.OnTertiaryDark,
    background = AutologPalette.BackgroundDark,
    onBackground = AutologPalette.OnDark,
    surface = AutologPalette.SurfaceDark,
    onSurface = AutologPalette.OnDark,
    surfaceVariant = Color(0xFF2A3346),
    onSurfaceVariant = AutologPalette.OnDarkMuted,
    outline = AutologPalette.OutlineDark,
    outlineVariant = Color(0xFF3E4858),
    error = AutologPalette.ErrorDark,
    onError = AutologPalette.OnErrorDark,
    surfaceContainerLowest = Color(0xFF070A12),
    surfaceContainerLow = Color(0xFF121826),
    surfaceContainer = AutologPalette.SurfaceDark,
    surfaceContainerHigh = Color(0xFF243044),
    surfaceContainerHighest = Color(0xFF2E3A50),
    inverseSurface = AutologPalette.BackgroundLight,
    inverseOnSurface = AutologPalette.OnLight,
    inversePrimary = AutologPalette.Primary,
)

private val Display = FontFamily.SansSerif

val AutologTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.4).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.2).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
)
