package ru.autolog.app.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.autolog.app.ui.theme.AutologColors
import ru.autolog.app.ui.util.Formatters
import kotlin.math.max

private val DonutPalette = listOf(
    AutologColors.Fuel,
    AutologColors.Service,
    AutologColors.Expense,
    AutologColors.Primary,
    AutologColors.Secondary,
    AutologColors.BlobViolet,
    AutologColors.BlobAmber,
    AutologColors.Reminder,
)

@Composable
fun AnalyticsLineChart(
    points: List<ChartPoint>,
    yUnit: String,
    lineColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (points.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Нет точек для графика", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return
        }
        val minX = points.minOf { it.xOdometer }
        val maxX = points.maxOf { it.xOdometer }
        val minY = points.minOf { it.yValue }
        val maxY = points.maxOf { it.yValue }
        val spanX = max(maxX - minX, 1f)
        val spanY = max(maxY - minY, 0.1f)
        val padY = spanY * 0.08f

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .testTag("analytics_line_chart"),
        ) {
            val left = 8.dp.toPx()
            val right = size.width - 8.dp.toPx()
            val top = 12.dp.toPx()
            val bottom = size.height - 12.dp.toPx()
            val w = right - left
            val h = bottom - top

            fun mapX(x: Float): Float = left + ((x - minX) / spanX) * w
            fun mapY(y: Float): Float {
                val y0 = minY - padY
                val y1 = maxY + padY
                return bottom - ((y - y0) / (y1 - y0)) * h
            }

            drawLine(
                color = Color.Gray.copy(alpha = 0.25f),
                start = Offset(left, bottom),
                end = Offset(right, bottom),
                strokeWidth = 2f,
            )
            drawLine(
                color = Color.Gray.copy(alpha = 0.25f),
                start = Offset(left, top),
                end = Offset(left, bottom),
                strokeWidth = 2f,
            )

            if (points.size == 1) {
                val p = points.first()
                drawCircle(
                    color = lineColor,
                    radius = 6.dp.toPx(),
                    center = Offset(mapX(p.xOdometer), mapY(p.yValue)),
                )
            } else {
                val path = Path()
                points.forEachIndexed { i, p ->
                    val o = Offset(mapX(p.xOdometer), mapY(p.yValue))
                    if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                }
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                )
                points.forEach { p ->
                    drawCircle(
                        color = lineColor,
                        radius = 4.dp.toPx(),
                        center = Offset(mapX(p.xOdometer), mapY(p.yValue)),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "${minX.toInt()} км",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Y: $yUnit · ${String.format("%.1f", minY)}…${String.format("%.1f", maxY)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${maxX.toInt()} км",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun AnalyticsDonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(112.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier
                    .size(112.dp)
                    .testTag("analytics_donut_chart"),
            ) {
                if (slices.isEmpty()) {
                    drawArc(
                        color = Color.Gray.copy(alpha = 0.2f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(8.dp.toPx(), 8.dp.toPx()),
                        size = Size(size.width - 16.dp.toPx(), size.height - 16.dp.toPx()),
                        style = Stroke(width = 18.dp.toPx()),
                    )
                    return@Canvas
                }
                var start = -90f
                val stroke = 18.dp.toPx()
                val inset = 8.dp.toPx()
                slices.forEachIndexed { i, slice ->
                    val sweep = slice.fraction * 360f
                    drawArc(
                        color = DonutPalette[i % DonutPalette.size],
                        startAngle = start,
                        sweepAngle = max(sweep, 1.5f),
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2),
                        style = Stroke(width = stroke, cap = StrokeCap.Butt),
                    )
                    start += sweep
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (slices.isEmpty()) {
                Text("Нет данных", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                slices.take(6).forEachIndexed { i, slice ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Canvas(modifier = Modifier.size(10.dp)) {
                            drawCircle(DonutPalette[i % DonutPalette.size])
                        }
                        Text(
                            text = "${slice.label} · ${Formatters.money(slice.value)} (${(slice.fraction * 100).toInt()}%)",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AnalyticsWorksList(
    works: List<WorkStat>,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("analytics_works_list"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (works.isEmpty()) {
            Text(
                "Пока нет работ в записях за период",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            works.forEach { work ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = work.label,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${work.count}×",
                            style = MaterialTheme.typography.labelLarge,
                            color = accent,
                        )
                    }
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                    ) {
                        val track = Color.Black.copy(alpha = 0.08f)
                        drawRoundRect(
                            color = track,
                            size = size,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                        )
                        val w = size.width * work.relative.coerceIn(0.08f, 1f)
                        drawRoundRect(
                            color = accent.copy(alpha = 0.85f),
                            size = Size(w, size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                        )
                    }
                }
            }
        }
    }
}
