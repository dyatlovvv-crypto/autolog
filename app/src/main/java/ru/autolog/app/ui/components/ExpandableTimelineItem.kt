package ru.autolog.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp

object TimelineTags {
    fun item(kind: String, id: Long) = "timeline_item_${kind}_$id"
    fun actionDelete(kind: String, id: Long) = "timeline_action_delete_${kind}_$id"
    fun actionEdit(kind: String, id: Long) = "timeline_action_edit_${kind}_$id"
    fun actionView(kind: String, id: Long) = "timeline_action_view_${kind}_$id"
    const val DELETE_CONFIRM = "timeline_delete_confirm"
}

private val RoadAsphalt = Color(0xFF4A525A)
private val RoadMarking = Color(0xFFF5F7FA)

/** Continuous asphalt strip for gaps between timeline rows (e.g. month headers). */
@Composable
fun TimelineRoadConnector(
    modifier: Modifier = Modifier,
    alpha: Float = 0.85f,
) {
    Canvas(modifier = modifier.width(40.dp)) {
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


/**
 * Drivvo-style timeline row: two-lane dashed road + circular icon + expandable body
 * with View / Edit / Delete.
 */
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
) {
    val tint = kind.tint()
    val icon = kind.icon()
    var confirmDelete by remember { mutableStateOf(false) }
    val roadAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0.85f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "roadAlpha",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
            .testTag(TimelineTags.item(kindKey, id)),
    ) {
        TimelineRoadRail(
            tint = tint,
            icon = { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) },
            isFirst = isFirst,
            isLast = isLast,
            alpha = roadAlpha,
            modifier = Modifier
                .width(40.dp)
                .fillMaxHeight(),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp, end = 4.dp, bottom = if (isLast) 8.dp else 4.dp)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onToggle)
                .padding(horizontal = 8.dp, vertical = 10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) + fadeIn(),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) + fadeOut(),
            ) {
                Column {
                    if (detailLines.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        detailLines.forEach { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
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
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.testTag(TimelineTags.actionEdit(kindKey, id)),
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Редактировать")
                        }
                        IconButton(
                            onClick = onView,
                            modifier = Modifier.testTag(TimelineTags.actionView(kindKey, id)),
                        ) {
                            Icon(Icons.Outlined.Visibility, contentDescription = "Просмотреть")
                        }
                    }
                }
            }
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
                .width(40.dp),
        ) {
            val iconCenterY = 22.dp.toPx()
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
                .padding(top = 8.dp)
                .size(28.dp)
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

    val roadWidth = 16.dp.toPx()
    val left = (size.width - roadWidth) / 2f
    val corner = if (roundEnds) 4.dp.toPx() else 0f

    drawRoundRect(
        color = asphalt,
        topLeft = Offset(left, topY),
        size = androidx.compose.ui.geometry.Size(roadWidth, height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
    )

    val edgeStroke = 1.4.dp.toPx()
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
        floatArrayOf(10.dp.toPx(), 8.dp.toPx()),
        0f,
    )
    drawLine(
        color = center,
        start = Offset(size.width / 2f, topY),
        end = Offset(size.width / 2f, bottomY),
        strokeWidth = 2.2.dp.toPx(),
        cap = StrokeCap.Round,
        pathEffect = dash,
    )
}
