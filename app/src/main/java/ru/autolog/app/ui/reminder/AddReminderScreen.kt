package ru.autolog.app.ui.reminder

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Calendar
import ru.autolog.app.AutologApp
import ru.autolog.app.notifications.ReminderCheckWorker
import ru.autolog.app.ui.fluid.FluidCheckboxRow
import ru.autolog.app.ui.fluid.rubberBandOverscroll
import ru.autolog.app.ui.glass.GlassPanel
import ru.autolog.app.ui.glass.GlassScaffold
import ru.autolog.app.ui.glass.GlassShapeLarge
import ru.autolog.app.ui.glass.GlassTopAppBar
import ru.autolog.app.ui.glass.glassFieldColors

object AddReminderTags {
    const val SCREEN = "add_reminder_screen"
    const val SAVE = "add_reminder_save"
    const val TITLE = "add_reminder_title"
    const val INTERVAL_KM = "add_reminder_interval_km"
    const val INTERVAL_DAYS = "add_reminder_interval_days"
    const val LAST_ODO = "add_reminder_last_odo"
    const val LAST_DATE = "add_reminder_last_date"
    const val CHANGED_OK = "add_reminder_changed_ok"
    const val DELETE = "add_reminder_delete"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReminderScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    onDeleted: (() -> Unit)? = null,
    editReminderId: Long? = null,
) {
    val viewModel: AddReminderViewModel = viewModel(
        key = "reminder-${editReminderId ?: "new"}",
        factory = AddReminderVmFactory(editReminderId),
    )
    val state by viewModel.form.collectAsStateWithLifecycle()
    viewModel.activeVehicle.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val dateLabel = remember(state.lastChangeDateEpochMs) { viewModel.lastChangeDateLabel() }
    var confirmDelete by remember { mutableStateOf(false) }
    val editing = state.editId != null

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            ReminderCheckWorker.enqueue(context)
            viewModel.onNotifications(true)
        } else {
            viewModel.onNotifications(false)
        }
    }

    LaunchedEffect(state.saved) {
        if (state.saved && !state.showChangedDialog) onSaved()
    }

    GlassScaffold(
        modifier = Modifier.testTag(AddReminderTags.SCREEN),
        topBar = {
            GlassTopAppBar(
                title = { Text("Напоминание") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                },
                actions = {
                    if (editing && onDeleted != null) {
                        TextButton(
                            onClick = { confirmDelete = true },
                            modifier = Modifier.testTag(AddReminderTags.DELETE),
                        ) {
                            Text("Удалить", color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (!state.loaded) {
            Text("Загрузка…", modifier = Modifier.padding(padding).padding(16.dp))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .rubberBandOverscroll()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = GlassShapeLarge,
                    elevation = 10.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Интервал: км ИЛИ дни — что наступит раньше.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedTextField(
                            value = state.title,
                            onValueChange = viewModel::onTitle,
                            label = { Text("Название") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(AddReminderTags.TITLE),
                            singleLine = true,
                            shape = GlassShapeLarge,
                            colors = glassFieldColors(),
                        )
                        OutlinedTextField(
                            value = state.intervalKm,
                            onValueChange = viewModel::onIntervalKm,
                            label = { Text("Интервал, км") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(AddReminderTags.INTERVAL_KM),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = GlassShapeLarge,
                            colors = glassFieldColors(),
                        )
                        OutlinedTextField(
                            value = state.intervalDays,
                            onValueChange = viewModel::onIntervalDays,
                            label = { Text("Интервал, дни") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(AddReminderTags.INTERVAL_DAYS),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = GlassShapeLarge,
                            colors = glassFieldColors(),
                        )
                        OutlinedTextField(
                            value = state.lastChangeOdometer,
                            onValueChange = viewModel::onLastChangeOdometer,
                            label = { Text("Пробег последней замены, км") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(AddReminderTags.LAST_ODO),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = GlassShapeLarge,
                            colors = glassFieldColors(),
                        )
                        OutlinedTextField(
                            value = dateLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Дата последней замены") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(AddReminderTags.LAST_DATE),
                            singleLine = true,
                            shape = GlassShapeLarge,
                            colors = glassFieldColors(),
                            trailingIcon = {
                                TextButton(
                                    onClick = {
                                        val cal = Calendar.getInstance().apply {
                                            timeInMillis = state.lastChangeDateEpochMs
                                        }
                                        DatePickerDialog(
                                            context,
                                            { _, y, m, d -> viewModel.onLastChangeDate(y, m, d) },
                                            cal.get(Calendar.YEAR),
                                            cal.get(Calendar.MONTH),
                                            cal.get(Calendar.DAY_OF_MONTH),
                                        ).show()
                                    },
                                ) { Text("Выбрать") }
                            },
                        )
                        FluidCheckboxRow(
                            label = "Показывать в уведомлениях",
                            checked = state.showPhoneNotifications,
                            onCheckedChange = { enabled ->
                                if (!enabled) {
                                    viewModel.onNotifications(false)
                                } else if (android.os.Build.VERSION.SDK_INT >= 33) {
                                    notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    ReminderCheckWorker.enqueue(context)
                                    viewModel.onNotifications(true)
                                }
                            },
                        )
                        FluidCheckboxRow(
                            label = "Сделано при покупке ТС",
                            checked = state.doneAtPurchase,
                            onCheckedChange = viewModel::onDoneAtPurchase,
                        )
                        state.error?.let {
                            Text(text = it, color = MaterialTheme.colorScheme.error)
                        }
                        Button(
                            onClick = viewModel::save,
                            enabled = !state.saving,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(AddReminderTags.SAVE),
                            shape = GlassShapeLarge,
                        ) {
                            Text(
                                when {
                                    state.saving -> "Сохранение…"
                                    editing -> "Сохранить изменения"
                                    else -> "Сохранить"
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.showChangedDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissChangedDialog,
            title = { Text(if (editing) "Изменено" else "Создано") },
            text = { Text(state.changedSummary) },
            confirmButton = {
                TextButton(
                    onClick = viewModel::dismissChangedDialog,
                    modifier = Modifier.testTag(AddReminderTags.CHANGED_OK),
                ) {
                    Text("ОК")
                }
            },
        )
    }

    if (confirmDelete && onDeleted != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить напоминание?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete(onDeleted)
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

private class AddReminderVmFactory(
    private val editReminderId: Long?,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        return AddReminderViewModel(
            AutologApp.instance.container.repository,
            editReminderId,
            extras.createSavedStateHandle(),
        ) as T
    }
}
