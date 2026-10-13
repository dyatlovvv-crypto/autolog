package ru.autolog.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.CarRepair
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.LocalCarWash
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ru.autolog.app.domain.ServiceKind
import ru.autolog.app.ui.fluid.bouncePressable
import ru.autolog.app.ui.glass.GlassPanel
import ru.autolog.app.ui.glass.GlassShapeSmall
import ru.autolog.app.ui.theme.AutologColors

enum class ActivityKind {
    REFUEL,
    /** Плановое ТО / обслуживание. */
    TO,
    /** Ремонт. */
    REPAIR,
    /** Запланированные работы. */
    PLANNED,
    /** Мойка. */
    WASH,
    /** Прочий расход. */
    EXPENSE,
    REMINDER,
    /** Free-text journal note. */
    NOTE,
    /** Fallback / generic service (maps to TO icon). */
    SERVICE,
}

fun ActivityKind.icon(): ImageVector = when (this) {
    ActivityKind.REFUEL -> Icons.Outlined.LocalGasStation
    ActivityKind.TO, ActivityKind.SERVICE -> Icons.Outlined.Handyman
    ActivityKind.REPAIR -> Icons.Outlined.CarRepair
    ActivityKind.PLANNED -> Icons.AutoMirrored.Outlined.EventNote
    ActivityKind.WASH -> Icons.Outlined.LocalCarWash
    ActivityKind.EXPENSE -> Icons.Outlined.Payments
    ActivityKind.REMINDER -> Icons.Outlined.NotificationsActive
    ActivityKind.NOTE -> Icons.AutoMirrored.Outlined.StickyNote2
}

fun ActivityKind.tint(): Color = when (this) {
    ActivityKind.REFUEL -> AutologColors.Fuel
    ActivityKind.TO, ActivityKind.SERVICE -> AutologColors.To
    ActivityKind.REPAIR -> AutologColors.Repair
    ActivityKind.PLANNED -> AutologColors.Planned
    ActivityKind.WASH -> AutologColors.Wash
    ActivityKind.EXPENSE -> AutologColors.Expense
    ActivityKind.REMINDER -> AutologColors.Reminder
    ActivityKind.NOTE -> AutologColors.Note
}

fun ServiceKind.toActivityKind(): ActivityKind = when (this) {
    ServiceKind.TO -> ActivityKind.TO
    ServiceKind.REPAIR -> ActivityKind.REPAIR
    ServiceKind.PLANNED -> ActivityKind.PLANNED
}

fun expenseActivityKind(category: String): ActivityKind =
    if (category.contains("мойк", ignoreCase = true)) ActivityKind.WASH else ActivityKind.EXPENSE

@Composable
fun ActivityFeedCard(
    kind: ActivityKind,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val icon = kind.icon()
    val tint = kind.tint()
    val clickable = if (onClick != null) {
        Modifier.bouncePressable(onClick = onClick)
    } else {
        Modifier
    }
    GlassPanel(
        modifier = modifier
            .fillMaxWidth()
            .then(clickable),
        shape = GlassShapeSmall,
        elevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
