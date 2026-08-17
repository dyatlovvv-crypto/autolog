package ru.autolog.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import ru.autolog.app.ui.fluid.bouncePressable
import ru.autolog.app.ui.theme.AutologElevation
import ru.autolog.app.ui.theme.AutologRadii
import ru.autolog.app.ui.theme.AutologSpace

@Composable
fun StatCard(
    title: String,
    value: String,
    unit: String,
    cardModifier: Modifier = Modifier,
    testTag: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val empty = value.equals("нет данных", ignoreCase = true)
    val base = cardModifier
        .fillMaxWidth()
        .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
        .bouncePressable(onClick = onClick ?: {})

    Surface(
        modifier = base,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(AutologRadii.card),
        tonalElevation = AutologElevation.card,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(AutologSpace.md)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = if (empty) {
                    MaterialTheme.typography.labelSmall
                } else {
                    MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                },
                color = if (empty) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = AutologSpace.xs),
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = AutologSpace.xs),
            )
        }
    }
}
