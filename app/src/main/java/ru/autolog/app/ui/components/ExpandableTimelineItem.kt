package ru.autolog.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.autolog.app.ui.theme.AutologElevation
import ru.autolog.app.ui.theme.AutologRadii
import ru.autolog.app.ui.theme.AutologSpace

object TimelineTags {
    fun item(kind: String, id: Long) = "timeline_item_${kind}_$id"
    fun actionDelete(kind: String, id: Long) = "timeline_action_delete_${kind}_$id"
    fun actionEdit(kind: String, id: Long) = "timeline_action_edit_${kind}_$id"
    fun actionView(kind: String, id: Long) = "timeline_action_view_${kind}_$id"
    fun roadKm(kind: String, id: Long) = "timeline_road_km_${kind}_$id"
    const val DELETE_CONFIRM = "timeline_delete_confirm"
}

private val RoadAsphalt = Color(0xFF4A525A)
private val RoadMarking = Color(0xFFF5F7FA)

/** Gutter between road rail and card for trip km + consumption. */
val RoadKmGutter = 64.dp

private val RoadCardGap = AutologSpace.sm

@Composable
fun TimelineRoadConnector(
    modifier: Modifier = Modifier,
    alpha: Float = 0.85f,
) {
    Canvas(modifier = modifier.width(AutologSpace.xl)) {
        drawTimelineRoad(
            topY = 0f,
            bottomY = size.height,
            asphalt = RoadAsphalt.copy(alpha = 0.62f * alpha),
            edge = RoadMarking.copy(alpha = 0.95f * alpha),
            center = RoadMarking.copy(alpha = 0.92f * alpha),
            roundEnds = false,
        )
    }
}

@Composable
internal fun TimelineKmPost(
    km: Int?,
    label: String?,
    modifier: Modifier = Modifier,
    consumptionLabel: String? = null,
    consumptionUnit: String? = null,
) {
    val hasKm = km != null && km > 0 && !label.isNullOrBlank()
    val hasConsumption = !consumptionLabel.isNullOrBlank() && !consumptionUnit.isNullOrBlank()
    if (!hasKm && !hasConsumption) {
        Spacer(modifier = modifier)
        return
    }
    Box(
        modifier = modifier.padding(top = AutologSpace.md, end = AutologSpace.xs),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
            shape = RoundedCornerShape(AutologSpace.sm),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = AutologSpace.xs),
            ) {
                if (hasKm) {
                    Text(
                        text = "$label км",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                }
                if (hasConsumption) {
                    if (hasKm) Spacer(modifier = Modifier.height(AutologSpace.xs))
                    Text(
                        text = "$consumptionLabel $consumptionUnit",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}

@Composable
fun ExpandableTimelineItem(
    kind: ActivityKind,
    kindKey: String,
    id: Long,
    title: String,
    subtitle: String,
    detailLines: List<String>,
    expanded: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onToggle: () -> Unit,
    onView: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    dateText: String? = null,
    kmText: String? = null,
    moneyText: String? = null,
    roadKm: Int? = null,
    roadKmLabel: String? = null,
    roadConsumptionLabel: String? = null,
    roadConsumptionUnit: String? = null,
) {
    val tint = kind.tint()
    val icon = kind.icon()
    var confirmDelete by remember { mutableStateOf(false) }
    val roadAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0.85f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "roadAlpha",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TimelineTags.item(kindKey, id)),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.width(AutologSpace.xl + RoadKmGutter + RoadCardGap))
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = if (isLast) AutologSpace.sm else AutologSpace.xs)
                    .clip(RoundedCornerShape(AutologRadii.card))
                    .clickable(onClick = onToggle),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(AutologRadii.card),
                tonalElevation = AutologElevation.card,
                shadowElevation = 0.dp,
            ) {
                Column(modifier = Modifier.padding(AutologSpace.md)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!dateText.isNullOrBlank() || !kmText.isNullOrBlank() || !moneyText.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = AutologSpace.xs),
                        horizontalArrangement = Arrangement.spacedBy(AutologSpace.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        dateText?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        kmText?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        moneyText?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = AutologSpace.xs),
                    )
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(
                        animationSpec = tween(280, easing = FastOutSlowInEasing),
                    ) + fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)),
                    exit = shrinkVertically(
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                    ) + fadeOut(animationSpec = tween(160)),
                ) {
                    Column {
                        if (detailLines.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(AutologSpace.sm))
                            detailLines.forEachIndexed { lineIndex, line ->
                                val accent = lineIndex == 0 && line.startsWith("С прошлого")
                                Text(
                                    text = line,
                                    style = if (accent) {
                                        MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    } else {
                                        MaterialTheme.typography.bodyMedium
                                    },
                                    color = if (accent) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.padding(top = AutologSpace.xs),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(AutologSpace.sm))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AutologSpace.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = onView,
                                modifier = Modifier.testTag(TimelineTags.actionView(kindKey, id)),
                            ) {
                                AccentIcon(
                                    imageVector = Icons.Outlined.Visibility,
                                    contentDescription = "Просмотреть",
                                )
                            }
                            IconButton(
                                onClick = onEdit,
                                modifier = Modifier.testTag(TimelineTags.actionEdit(kindKey, id)),
                            ) {
                                AccentIcon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = "Редактировать",
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = { confirmDelete = true },
                                modifier = Modifier.testTag(TimelineTags.actionDelete(kindKey, id)),
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "Удалить",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
                }
            }
        }
        Row(modifier = Modifier.matchParentSize()) {
            TimelineRoadRail(
                tint = tint,
                icon = {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(AutologSpace.md),
                    )
                },
                isFirst = isFirst,
                isLast = isLast,
                alpha = roadAlpha,
                modifier = Modifier
                    .width(AutologSpace.xl)
                    .fillMaxHeight(),
            )
            TimelineKmPost(
                km = roadKm,
                label = roadKmLabel,
                consumptionLabel = roadConsumptionLabel,
                consumptionUnit = roadConsumptionUnit,
                modifier = Modifier
                    .width(RoadKmGutter)
                    .fillMaxHeight()
                    .testTag(TimelineTags.roadKm(kindKey, id)),
            )
            Spacer(modifier = Modifier.width(RoadCardGap))
            Spacer(modifier = Modifier.weight(1f))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить запись?") },
            text = { Text("Действие нельзя отменить.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    modifier = Modifier.testTag(TimelineTags.DELETE_CONFIRM),
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun TimelineRoadRail(
    tint: Color,
    icon: @Composable () -> Unit,
    isFirst: Boolean,
    isLast: Boolean,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    val asphalt = RoadAsphalt.copy(alpha = 0.62f * alpha)
    val edge = RoadMarking.copy(alpha = 0.95f * alpha)
    val center = RoadMarking.copy(alpha = 0.92f * alpha)
    Box(
        modifier = modifier.fillMaxHeight(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxHeight()
                .width(AutologSpace.xl),
        ) {
            val iconCenterY = AutologSpace.lg.toPx()
            val topY = if (isFirst) iconCenterY else 0f
            val bottomY = if (isLast) iconCenterY else size.height
            drawTimelineRoad(
                topY = topY,
                bottomY = bottomY,
                asphalt = asphalt,
                edge = edge,
                center = center,
                roundEnds = true,
            )
        }
        Box(
            modifier = Modifier
                .padding(top = AutologSpace.sm)
                .size(AutologSpace.lg)
                .clip(CircleShape)
                .background(tint),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTimelineRoad(
    topY: Float,
    bottomY: Float,
    asphalt: Color,
    edge: Color,
    center: Color,
    roundEnds: Boolean,
) {
    val height = (bottomY - topY).coerceAtLeast(0f)
    if (height <= 0f) return

    val roadWidth = AutologSpace.md.toPx()
    val left = (size.width - roadWidth) / 2f
    val corner = if (roundEnds) AutologSpace.xs.toPx() else 0f

    drawRoundRect(
        color = asphalt,
        topLeft = Offset(left, topY),
        size = androidx.compose.ui.geometry.Size(roadWidth, height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
    )

    val edgeStroke = AutologSpace.xs.toPx() / 4f
    drawLine(
        color = edge,
        start = Offset(left + edgeStroke, topY),
        end = Offset(left + edgeStroke, bottomY),
        strokeWidth = edgeStroke,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = edge,
        start = Offset(left + roadWidth - edgeStroke, topY),
        end = Offset(left + roadWidth - edgeStroke, bottomY),
        strokeWidth = edgeStroke,
        cap = StrokeCap.Round,
    )

    val dash = PathEffect.dashPathEffect(
        floatArrayOf(AutologSpace.sm.toPx() + AutologSpace.xs.toPx(), AutologSpace.sm.toPx()),
        0f,
    )
    drawLine(
        color = center,
        start = Offset(size.width / 2f, topY),
        end = Offset(size.width / 2f, bottomY),
        strokeWidth = AutologSpace.xs.toPx() / 2f,
        cap = StrokeCap.Round,
        pathEffect = dash,
    )
}
