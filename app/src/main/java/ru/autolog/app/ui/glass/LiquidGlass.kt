package ru.autolog.app.ui.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.autolog.app.ui.theme.AutologColors
import ru.autolog.app.ui.theme.LocalGlassTokens
import kotlin.math.cos
import kotlin.math.sin

val GlassShapeLarge = RoundedCornerShape(28.dp)
val GlassShapeMedium = RoundedCornerShape(22.dp)
val GlassShapeSmall = RoundedCornerShape(16.dp)
val GlassShapePill = RoundedCornerShape(percent = 50)

/**
 * Liquid Glass panel: frosted fill over mesh, hairline stroke, soft top highlight.
 * Specular is drawn UNDER content so text stays sharp and readable.
 */
fun Modifier.liquidGlassPanel(
    shape: Shape = GlassShapeMedium,
    elevation: Dp = 10.dp,
): Modifier = composed {
    val tokens = LocalGlassTokens.current
    this
        .shadow(elevation = elevation, shape = shape, clip = false, ambientColor = Color(0x33000000), spotColor = Color(0x22000000))
        .clip(shape)
        .background(tokens.fill, shape)
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    tokens.highlight,
                    tokens.stroke,
                    tokens.highlight.copy(alpha = 0.08f),
                ),
                tileMode = TileMode.Clamp,
            ),
            shape = shape,
        )
        .drawWithContent {
            // Highlight / rim behind children — never wash text.
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to tokens.highlight.copy(alpha = 0.22f),
                        0.22f to tokens.highlight.copy(alpha = 0.06f),
                        0.5f to Color.Transparent,
                    ),
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, tokens.scrim.copy(alpha = 0.06f)),
                    center = Offset(size.width * 0.5f, size.height * 0.15f),
                    radius = size.maxDimension * 0.9f,
                ),
            )
            drawContent()
        }
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = GlassShapeMedium,
    elevation: Dp = 8.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.liquidGlassPanel(shape = shape, elevation = elevation),
        content = content,
    )
}

/** Animated mesh blobs under every glass window. */
@Composable
fun LiquidMeshBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val transition = rememberInfiniteTransition(label = "liquidMesh")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "meshPhase",
    )

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    drawRect(if (dark) AutologColors.BackgroundDark else AutologColors.BackgroundLight)

                    fun blob(cx: Float, cy: Float, r: Float, color: Color, a: Float) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(color.copy(alpha = a), Color.Transparent),
                                center = Offset(cx, cy),
                                radius = r,
                            ),
                            radius = r,
                            center = Offset(cx, cy),
                        )
                    }

                    val a = if (dark) 0.28f else 0.32f
                    blob(
                        w * (0.2f + 0.08f * cos(t.toDouble()).toFloat()),
                        h * (0.16f + 0.06f * sin(t.toDouble()).toFloat()),
                        w * 0.58f,
                        AutologColors.BlobBlue,
                        a,
                    )
                    blob(
                        w * (0.84f + 0.06f * sin(t.toDouble()).toFloat()),
                        h * (0.26f + 0.08f * cos(t.toDouble()).toFloat()),
                        w * 0.52f,
                        AutologColors.BlobViolet,
                        a,
                    )
                    blob(
                        w * (0.32f + 0.1f * sin((t + 1.2f).toDouble()).toFloat()),
                        h * (0.8f + 0.05f * cos((t + 1.2f).toDouble()).toFloat()),
                        w * 0.62f,
                        AutologColors.BlobCyan,
                        a,
                    )
                    blob(
                        w * (0.72f + 0.05f * cos((t + 2.1f).toDouble()).toFloat()),
                        h * (0.7f + 0.07f * sin((t + 2.1f).toDouble()).toFloat()),
                        w * 0.44f,
                        AutologColors.BlobAmber,
                        a * 0.85f,
                    )
                },
        )
        content()
    }
}
