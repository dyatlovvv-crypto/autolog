package ru.autolog.app.ui.map

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import ru.autolog.app.data.map.StationMapSnapshots
import ru.autolog.app.ui.theme.AutologRadii

object StationMiniMapTags {
    const val MAP = "station_mini_map"
}

/**
 * Static PNG snapshot of АЗС. Cache hit is sync; miss builds on a daemon thread
 * (not LaunchedEffect/IO) so Compose UI tests are not stuck waiting on tiles/Nominatim.
 */
@Composable
fun StationMiniMap(
    lat: Double?,
    lon: Double?,
    label: String?,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    @Suppress("UNUSED_PARAMETER")
    refuelId: Long? = null,
) {
    val context = LocalContext.current
    var snapshot by remember(lat, lon) { mutableStateOf<File?>(null) }
    var status by remember(lat, lon) { mutableStateOf("Снимок карты…") }

    DisposableEffect(lat, lon) {
        val la = lat?.takeIf { it.isFinite() }
        val lo = lon?.takeIf { it.isFinite() }
        if (la == null || lo == null) {
            snapshot = null
            status = "Нет координат АЗС"
            return@DisposableEffect onDispose { }
        }
        val cached = StationMapSnapshots.existingOrNull(context, la, lo)
        if (cached != null) {
            snapshot = cached
            return@DisposableEffect onDispose { }
        }
        status = "Снимок карты…"
        snapshot = null
        val alive = AtomicBoolean(true)
        val main = Handler(Looper.getMainLooper())
        StationMapSnapshots.prefetchAsync(context, la, lo) { file ->
            main.post {
                if (!alive.get()) return@post
                snapshot = file
                if (file == null) status = "Снимок недоступен"
            }
        }
        onDispose { alive.set(false) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(AutologRadii.card))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .testTag(StationMiniMapTags.MAP),
        contentAlignment = Alignment.Center,
    ) {
        val file = snapshot
        if (file != null && file.exists()) {
            AsyncImage(
                model = file,
                contentDescription = label?.takeIf { it.isNotBlank() } ?: "АЗС на карте",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = status,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
