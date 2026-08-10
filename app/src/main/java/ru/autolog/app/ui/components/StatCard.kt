package ru.autolog.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.autolog.app.ui.fluid.bouncePressable
import ru.autolog.app.ui.glass.GlassPanel
import ru.autolog.app.ui.glass.GlassShapeMedium

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

    GlassPanel(modifier = base, shape = GlassShapeMedium, elevation = 10.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = if (empty) {
                    MaterialTheme.typography.titleLarge
                } else {
                    MaterialTheme.typography.displaySmall
                },
                color = if (empty) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
