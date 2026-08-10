package ru.autolog.app.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.autolog.app.AutologApp
import ru.autolog.app.data.local.entity.ReminderEntity
import ru.autolog.app.ui.fluid.FluidCheckboxRow
import ru.autolog.app.ui.glass.GlassPanel
import ru.autolog.app.ui.glass.GlassScaffold
import ru.autolog.app.ui.glass.GlassShapeLarge
import ru.autolog.app.ui.glass.GlassTopAppBar
import ru.autolog.app.ui.glass.glassFieldColors
import ru.autolog.app.ui.util.Formatters

object RefuelDetailTags {
    const val SCREEN = "refuel_detail_screen"
    const val SAVE = "refuel_detail_save"
    const val DELETE = "refuel_detail_delete"
    const val ODOMETER = "refuel_detail_odometer"
    const val LITERS = "refuel_detail_liters"
    const val PRICE = "refuel_detail_price"
    const val STATION = "refuel_detail_station"
    const val TANK_FULL = "refuel_detail_tank_full"
}

object ReminderDetailTags {
    const val SCREEN = "reminder_detail_screen"
    const val DELETE = "reminder_detail_delete"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefuelDetailScreen(
    refuelId: Long,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
) {
    val repo = AutologApp.instance.container.repository
    var loaded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var odometer by remember { mutableStateOf("") }
    var liters by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var station by remember { mutableStateOf("") }
    var tankFull by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refuelId) {
        val r = repo.getRefuelling(refuelId)
        if (r != null) {
            odometer = r.odometerKm.toString()
            liters = r.volumeLiters.toString()
            price = r.pricePerLiter.toString()
            station = r.stationName.orEmpty()
            tankFull = r.isTankFull
            loaded = true
        }
    }

    GlassScaffold(
        modifier = Modifier.testTag(RefuelDetailTags.SCREEN),
        topBar = {
            GlassTopAppBar(
                title = { Text("Заправка") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                },
                actions = {
                    TextButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.testTag(RefuelDetailTags.DELETE),
                    ) {
                        Text("Удалить", color = MaterialTheme.colorScheme.error)
                    }
                },
            )
        },
    ) { padding ->
        if (!loaded) {
            Text("Загрузка…", modifier = Modifier.padding(padding).padding(16.dp))
        } else {
            GlassPanel(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .fillMaxWidth(),
                shape = GlassShapeLarge,
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Исправить запись", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = odometer,
                        onValueChange = { odometer = it.filter(Char::isDigit) },
                        label = { Text("Пробег, км") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(RefuelDetailTags.ODOMETER),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = GlassShapeLarge,
                        colors = glassFieldColors(),
                    )
                    OutlinedTextField(
                        value = liters,
                        onValueChange = { liters = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                        label = { Text("Литры") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(RefuelDetailTags.LITERS),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = GlassShapeLarge,
                        colors = glassFieldColors(),
                    )
                    FluidCheckboxRow(
                        label = "До полного",
                        checked = tankFull,
                        onCheckedChange = { tankFull = it },
                        modifier = Modifier.testTag(RefuelDetailTags.TANK_FULL),
                    )
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                        label = { Text("Цена за литр") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(RefuelDetailTags.PRICE),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = GlassShapeLarge,
                        colors = glassFieldColors(),
                    )
                    OutlinedTextField(
                        value = station,
                        onValueChange = { station = it },
                        label = { Text("АЗС") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(RefuelDetailTags.STATION),
                        singleLine = true,
                        shape = GlassShapeLarge,
                        colors = glassFieldColors(),
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(
                        onClick = {
                            val odo = odometer.toIntOrNull()
                            val vol = liters.replace(',', '.').toDoubleOrNull()
                            val pr = price.replace(',', '.').toDoubleOrNull()
                            if (odo == null || vol == null || vol <= 0 || pr == null || pr < 0) {
                                error = "Проверьте поля"
                                return@Button
                            }
                            scope.launch {
                                saving = true
                                error = null
                                val result = repo.updateRefuelling(
                                    id = refuelId,
                                    odometerKm = odo,
                                    volumeLiters = vol,
                                    pricePerLiter = pr,
                                    isTankFull = tankFull,
                                    stationName = station,
                                )
                                withContext(Dispatchers.Main.immediate) {
                                    saving = false
                                    result.onSuccess { onBack() }
                                        .onFailure { e -> error = e.message ?: "Ошибка" }
                                }
                            }
                        },
                        enabled = !saving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(RefuelDetailTags.SAVE),
                        shape = GlassShapeLarge,
                    ) {
                        Text(if (saving) "Сохранение…" else "Сохранить изменения")
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить заправку?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repo.deleteRefuelling(refuelId)
                            withContext(Dispatchers.Main.immediate) {
                                confirmDelete = false
                                onDeleted()
                            }
                        }
                    },
                    modifier = Modifier.testTag("refuel_delete_confirm"),
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Отмена") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDetailScreen(
    reminderId: Long,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
) {
    val repo = AutologApp.instance.container.repository
    var item by remember { mutableStateOf<ReminderEntity?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reminderId) {
        item = repo.getReminder(reminderId)
    }

    GlassScaffold(
        modifier = Modifier.testTag(ReminderDetailTags.SCREEN),
        topBar = {
            GlassTopAppBar(
                title = { Text("Напоминание") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                },
                actions = {
                    TextButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.testTag(ReminderDetailTags.DELETE),
                    ) {
                        Text("Удалить", color = MaterialTheme.colorScheme.error)
                    }
                },
            )
        },
    ) { padding ->
        val r = item
        if (r == null) {
            Text("Загрузка…", modifier = Modifier.padding(padding).padding(16.dp))
        } else {
            GlassPanel(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .fillMaxWidth(),
                shape = GlassShapeLarge,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(r.title, style = MaterialTheme.typography.headlineSmall)
                    Text("Интервал: ${r.intervalKm ?: "—"} км / ${r.intervalDays ?: "—"} дн.")
                    Text("Последняя замена: ${r.baselineOdometerKm} км · ${Formatters.date(r.baselineDateEpochMs)}")
                    Text(
                        "Осталось ${r.remainingKm ?: "—"} км или ${r.remainingDays ?: "—"} дн.",
                    )
                    Text("Примерно: ${Formatters.date(r.nextDueDateEpochMs)}")
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить напоминание?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repo.deleteReminder(reminderId)
                            withContext(Dispatchers.Main.immediate) {
                                confirmDelete = false
                                onDeleted()
                            }
                        }
                    },
                    modifier = Modifier.testTag("reminder_delete_confirm"),
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Отмена") }
            },
        )
    }
}
