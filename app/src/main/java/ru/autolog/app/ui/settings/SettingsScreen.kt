package ru.autolog.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.autolog.app.AutologApp
import ru.autolog.app.BuildConfig
import ru.autolog.app.data.settings.AppBackground
import ru.autolog.app.data.settings.AppPalette
import ru.autolog.app.data.settings.ConsumptionUnit
import ru.autolog.app.data.settings.UserSettings
import ru.autolog.app.data.settings.VolumeUnit
import ru.autolog.app.data.update.AppUpdateChecker
import ru.autolog.app.ui.fluid.rubberBandOverscroll
import ru.autolog.app.ui.glass.GlassPanel
import ru.autolog.app.ui.glass.GlassScaffold
import ru.autolog.app.ui.glass.GlassShapeSmall
import ru.autolog.app.ui.glass.GlassTopAppBar
import ru.autolog.app.ui.util.Formatters

object SettingsTags {
    const val ROOT = "settings_root"
    const val BACK = "settings_back"
    const val BACKUP_SECTION = "settings_backup_section"
    const val BACKUP_EXPORT = "settings_backup_export"
    const val BACKUP_IMPORT = "settings_backup_import"
    const val RESTORE_CONFIRM = "settings_restore_confirm"
    const val ERROR_DIALOG = "settings_error_dialog"
    const val VEHICLE_SECTION = "settings_vehicle_section"
    const val DELETE_VEHICLE = "settings_delete_vehicle"
    const val DELETE_VEHICLE_CONFIRM = "settings_delete_vehicle_confirm"
    const val UPDATE_SECTION = "settings_update_section"
    const val UPDATE_CHECK = "settings_update_check"
    const val UPDATE_INSTALL = "settings_update_install"
    const val UPDATE_NOTES = "settings_update_notes"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val store = AutologApp.instance.container.settingsStore
    val repo = AutologApp.instance.container.repository
    val settings by store.settings.collectAsStateWithLifecycle(initialValue = UserSettings())
    val activeVehicle by repo.observeActiveVehicle()
        .collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var errorDialog by remember { mutableStateOf<String?>(null) }
    var showDeleteVehicleConfirm by remember { mutableStateOf(false) }
    var updateBusy by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var pendingRelease by remember {
        mutableStateOf<AppUpdateChecker.RemoteRelease?>(null)
    }
    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // User returned from install-permission screen; they can tap Update again.
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            scope.launch {
                store.setCustomBackgroundUri(uri.toString())
                store.setBackground(AppBackground.CUSTOM)
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val json = withContext(Dispatchers.IO) { repo.exportBackupJson() }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("Не удалось открыть файл")
                }
                message = "Резервная копия сохранена"
            }.onFailure {
                message = Formatters.userMessage(it, "Не удалось сохранить копию")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) pendingRestoreUri = uri
    }

    GlassScaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(SettingsTags.ROOT),
        topBar = {
            GlassTopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(SettingsTags.BACK)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .rubberBandOverscroll()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(modifier = Modifier.height(4.dp))


            SettingsSection(title = "Резервная копия") {
                Column(
                    modifier = Modifier.testTag(SettingsTags.BACKUP_SECTION),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Сохраните файл на телефон, в мессенджер или на диск. " +
                            "Восстановление заменит все данные в приложении.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                            exportLauncher.launch("autolog_backup_$stamp.json")
                        },
                        enabled = !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(SettingsTags.BACKUP_EXPORT),
                    ) { Text("Сохранить в файл") }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                        enabled = !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(SettingsTags.BACKUP_IMPORT),
                    ) { Text("Восстановить из файла") }
                    Text(
                        "В копию входят машины, заправки, сервисы, напоминания, заметки, " +
                            "документы (PDF/фото) и фото сервиса.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val vehicle = activeVehicle
            if (vehicle != null) {
                SettingsSection(title = "Автомобиль") {
                    Column(
                        modifier = Modifier.testTag(SettingsTags.VEHICLE_SECTION),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            vehicle.name,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        val plate = vehicle.plate?.takeIf { it.isNotBlank() }
                        Text(
                            buildString {
                                append("${vehicle.currentOdometerKm} км")
                                if (plate != null) {
                                    append(" · ")
                                    append(plate)
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = { showDeleteVehicleConfirm = true },
                            enabled = !busy,
                            modifier = Modifier.testTag(SettingsTags.DELETE_VEHICLE),
                        ) {
                            Text(
                                "Удалить автомобиль…",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Text(
                            "Удаление только здесь, чтобы случайно не задеть в списке машин.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SettingsSection(title = "Фон приложения") {
                AppBackground.entries.forEach { bg ->
                    SettingChipRow(
                        selected = settings.background == bg,
                        label = bg.label,
                        onClick = {
                            scope.launch {
                                store.setBackground(bg)
                                if (bg == AppBackground.CUSTOM && settings.customBackgroundUri == null) {
                                    pickImage.launch(arrayOf("image/*"))
                                }
                            }
                        },
                    )
                }
                if (settings.background == AppBackground.CUSTOM) {
                    TextButton(onClick = { pickImage.launch(arrayOf("image/*")) }) {
                        Text("Выбрать изображение")
                    }
                }
            }

            SettingsSection(title = "Цветовая палитра") {
                AppPalette.entries.forEach { palette ->
                    SettingChipRow(
                        selected = settings.palette == palette,
                        label = palette.label,
                        onClick = { scope.launch { store.setPalette(palette) } },
                    )
                }
            }

            SettingsSection(title = "Единицы расхода") {
                ConsumptionUnit.entries.forEach { unit ->
                    SettingChipRow(
                        selected = settings.consumptionUnit == unit,
                        label = unit.label,
                        onClick = { scope.launch { store.setConsumptionUnit(unit) } },
                    )
                }
            }

            SettingsSection(title = "Объём бака") {
                VolumeUnit.entries.forEach { unit ->
                    SettingChipRow(
                        selected = settings.volumeUnit == unit,
                        label = unit.label,
                        onClick = { scope.launch { store.setVolumeUnit(unit) } },
                    )
                }
            }

            SettingsSection(title = "Обновления") {
                Column(modifier = Modifier.testTag(SettingsTags.UPDATE_SECTION)) {
                    Text(
                        "Сейчас: ${BuildConfig.APP_VERSION}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Источник: GitHub ${BuildConfig.UPDATE_GITHUB_OWNER}/${BuildConfig.UPDATE_GITHUB_REPO}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                updateBusy = true
                                pendingRelease = null
                                updateStatus = "Проверяю…"
                                when (val result = AppUpdateChecker.checkForUpdate()) {
                                    is AppUpdateChecker.CheckResult.UpToDate -> {
                                        updateStatus =
                                            "Актуально (в GitHub ${result.remote})"
                                    }
                                    is AppUpdateChecker.CheckResult.UpdateAvailable -> {
                                        pendingRelease = result.release
                                        updateStatus = "Доступна ${result.release.versionName}"
                                    }
                                    is AppUpdateChecker.CheckResult.Failed -> {
                                        updateStatus = result.message
                                    }
                                }
                                updateBusy = false
                            }
                        },
                        enabled = !updateBusy && !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(SettingsTags.UPDATE_CHECK),
                    ) {
                        if (updateBusy && pendingRelease == null) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(18.dp)
                                    .padding(end = 8.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        Text("Проверить обновления")
                    }
                    updateStatus?.let { status ->
                        Text(
                            status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    pendingRelease?.let { release ->
                        if (release.notes.isNotBlank()) {
                            Text(
                                "Что нового",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                            Text(
                                release.notes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .testTag(SettingsTags.UPDATE_NOTES),
                            )
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    updateBusy = true
                                    updateStatus = "Скачиваю ${release.versionName}…"
                                    runCatching {
                                        if (!AppUpdateChecker.canInstallPackages(context)) {
                                            installPermissionLauncher.launch(
                                                AppUpdateChecker.installPermissionSettingsIntent(context),
                                            )
                                            updateStatus =
                                                "Разрешите установку из этого приложения и нажмите снова"
                                            return@runCatching
                                        }
                                        val apk = AppUpdateChecker.downloadApk(context, release)
                                        updateStatus = "Установка…"
                                        AppUpdateChecker.installApk(context, apk)
                                        updateStatus =
                                            "Открыл установщик ${release.versionName}"
                                    }.onFailure {
                                        updateStatus = Formatters.userMessage(
                                            it,
                                            "Не удалось обновить",
                                        )
                                    }
                                    updateBusy = false
                                }
                            },
                            enabled = !updateBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .testTag(SettingsTags.UPDATE_INSTALL),
                        ) { Text("Обновить до ${release.versionName}") }
                    }
                }
            }

            message?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (errorDialog != null) {
        AlertDialog(
            onDismissRequest = { errorDialog = null },
            modifier = Modifier.testTag(SettingsTags.ERROR_DIALOG),
            title = { Text("Ошибка") },
            text = {
                Text(
                    errorDialog.orEmpty(),
                    modifier = Modifier.testTag("${SettingsTags.ERROR_DIALOG}_text"),
                )
            },
            confirmButton = {
                TextButton(onClick = { errorDialog = null }) { Text("ОК") }
            },
        )
    }




    if (showDeleteVehicleConfirm) {
        val vehicle = activeVehicle
        AlertDialog(
            onDismissRequest = { showDeleteVehicleConfirm = false },
            title = { Text("Удалить автомобиль?") },
            text = {
                Text(
                    if (vehicle != null) {
                        "«${vehicle.name}» и все связанные записи будут удалены."
                    } else {
                        "Автомобиль и все связанные записи будут удалены."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = vehicle?.id
                        showDeleteVehicleConfirm = false
                        if (id == null) return@TextButton
                        scope.launch {
                            busy = true
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    repo.deleteVehicle(id).getOrThrow()
                                }
                                message = "Автомобиль удалён"
                            }.onFailure {
                                message = Formatters.userMessage(it, "Не удалось удалить автомобиль")
                                errorDialog = message
                            }
                            busy = false
                        }
                    },
                    modifier = Modifier.testTag(SettingsTags.DELETE_VEHICLE_CONFIRM),
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteVehicleConfirm = false }) { Text("Отмена") }
            },
        )
    }

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("Восстановить из копии?") },
            text = { Text("Текущие автомобили и записи будут заменены данными из файла.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = uri
                        pendingRestoreUri = null
                        scope.launch {
                            runCatching {
                                val json = withContext(Dispatchers.IO) {
                                    context.contentResolver.openInputStream(target)?.bufferedReader()
                                        ?.readText()
                                        ?: error("Не удалось прочитать файл")
                                }
                                withContext(Dispatchers.IO) { repo.restoreBackupJson(json).getOrThrow() }
                                store.settings.first()
                                message = "Данные восстановлены"
                            }.onFailure {
                                message = Formatters.userMessage(
                                    it,
                                    "Не удалось восстановить из файла",
                                )
                            }
                        }
                    },
                    modifier = Modifier.testTag(SettingsTags.RESTORE_CONFIRM),
                ) { Text("Восстановить") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = GlassShapeSmall,
        elevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SettingChipRow(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = { Text(label) },
        )
    }
}
