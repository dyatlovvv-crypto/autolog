package ru.autolog.app.ui.fluid

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Gooey / liquid appearance: blur + alpha contrast ColorMatrix (API 31+).
 * Animate [progress] 0→1 as the sheet “pours” into place.
 */
fun Modifier.liquidGooey(progress: Float): Modifier = graphicsLayer {
    val t = progress.coerceIn(0f, 1f)
    alpha = 0.35f + 0.65f * t
    scaleX = 0.92f + 0.08f * t
    scaleY = 0.94f + 0.06f * t
    translationY = (1f - t) * 48f
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val blurPx = (1f - t) * 28f + 0.1f
        val contrast = 1f + (1f - t) * 8f
        val matrix = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, contrast, (-0.5f * (contrast - 1f)) * 255f,
            ),
        )
        renderEffect = RenderEffect.createChainEffect(
            RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP),
            RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(matrix)),
        ).asComposeRenderEffect()
    }
}

/** Soft press-in with low-stiffness bouncy spring (iOS-like). */
fun Modifier.bouncePressable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "bouncePress",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures(
                onPress = {
                    pressed = true
                    try {
                        awaitRelease()
                    } finally {
                        pressed = false
                    }
                },
                onTap = { onClick() },
            )
        }
}

/**
 * Rubber-band overscroll: list stretches with translation, then springs back.
 */
fun Modifier.rubberBandOverscroll(): Modifier = composed {
    val scope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    val maxPull = with(LocalDensity.current) { 96.dp.toPx() }

    val connection = remember(maxPull) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (available.y == 0f) return Offset.Zero
                val next = (offsetY.value + available.y * 0.45f).coerceIn(-maxPull, maxPull)
                scope.launch { offsetY.snapTo(next) }
                return Offset(0f, available.y)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                offsetY.animateTo(
                    0f,
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
                )
                return available
            }
        }
    }

    this
        .nestedScroll(connection)
        .graphicsLayer {
            translationY = offsetY.value
            val stretch = 1f + (abs(offsetY.value) / maxPull) * 0.03f
            scaleY = stretch
        }
}

/** Animate gooey progress when [visible] becomes true. */
fun Modifier.liquidAppear(visible: Boolean): Modifier = composed {
    val progress = remember { Animatable(if (visible) 1f else 0f) }
    LaunchedEffect(visible) {
        if (visible) {
            progress.snapTo(0f)
            progress.animateTo(
                1f,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            )
        } else {
            progress.snapTo(0f)
        }
    }
    liquidGooey(progress.value)
}
