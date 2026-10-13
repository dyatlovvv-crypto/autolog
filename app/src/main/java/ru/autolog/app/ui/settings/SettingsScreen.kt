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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
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
import ru.autolog.app.data.settings.SyncPeriod
import ru.autolog.app.data.settings.UserSettings
import ru.autolog.app.data.settings.VolumeUnit
import ru.autolog.app.data.sync.CloudBackupGateway
import ru.autolog.app.data.sync.CloudSyncWorker
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
    const val BACKUP_EXPORT = "settings_backup_export"
    const val BACKUP_IMPORT = "settings_backup_import"
    const val RESTORE_CONFIRM = "settings_restore_confirm"
    const val GOOGLE_SECTION = "settings_google_section"
    const val GOOGLE_LINK = "settings_google_link"
    const val GOOGLE_UNLINK = "settings_google_unlink"
    const val GOOGLE_SYNC_NOW = "settings_google_sync_now"
    const val GOOGLE_RESTORE = "settings_google_restore"
    const val GOOGLE_RESTORE_CONFIRM = "settings_google_restore_confirm"
    const val GOOGLE_ERROR_DIALOG = "settings_google_error_dialog"
    const val VEHICLE_SECTION = "settings_vehicle_section"
    const val DELETE_VEHICLE = "settings_delete_vehicle"
    const val DELETE_VEHICLE_CONFIRM = "settings_delete_vehicle_confirm"
    const val UPDATE_SECTION = "settings_update_section"
    const val UPDATE_CHECK = "settings_update_check"
    const val UPDATE_INSTALL = "settings_update_install"
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
    var showLinkConflict by remember { mutableStateOf(false) }
    var showEmptyPhoneRestore by remember { mutableStateOf(false) }
    var showGoogleRestoreConfirm by remember { mutableStateOf(false) }
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

    fun googleDrive(): CloudBackupGateway = AutologApp.instance.container.googleDrive

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

    suspend fun persistLinkedAccount() {
        val info = googleDrive().accountInfo() ?: return
        store.setGoogleAccount(info.email, info.displayName, info.photoUrl)
    }

    suspend fun uploadPhoneToCloud() {
        val json = withContext(Dispatchers.IO) { repo.exportBackupJson() }
        withContext(Dispatchers.IO) { googleDrive().uploadSnapshot(json).getOrThrow() }
        store.setLastCloudSyncEpochMs(System.currentTimeMillis())
        if (settings.syncPeriod == SyncPeriod.OFF) {
            store.setSyncPeriod(SyncPeriod.DAILY)
            CloudSyncWorker.enqueue(context, SyncPeriod.DAILY)
        } else {
            CloudSyncWorker.enqueue(context, settings.syncPeriod)
        }
        message = "Данные телефона сохранены в Google"
    }

    suspend fun restoreFromGoogle() {
        val json = withContext(Dispatchers.IO) { googleDrive().downloadSnapshot().getOrThrow() }
        withContext(Dispatchers.IO) { repo.restoreBackupJson(json).getOrThrow() }
        store.settings.first()
        store.setLastCloudSyncEpochMs(System.currentTimeMillis())
        message = "Данные восстановлены из Google"
    }

    suspend fun afterSignInLinked() {
        // Never wipe local DB here.
        persistLinkedAccount()
        val localHas = withContext(Dispatchers.IO) { repo.hasLocalJournalData() }
        val meta = withContext(Dispatchers.IO) { googleDrive().findBackupMeta() }
        when {
            meta == null -> {
                if (localHas) uploadPhoneToCloud()
                else message = "Google привязан. Локальных записей пока нет."
            }
            !localHas -> showEmptyPhoneRestore = true
            else -> showLinkConflict = true
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        scope.launch {
            busy = true
            runCatching {
                if (result.resultCode != android.app.Activity.RESULT_OK) {
                    error("Не удалось войти в Google. Попробуйте ещё раз.")
                }
                googleDrive().completeSignIn(result.data).getOrThrow()
                afterSignInLinked()
            }.onFailure {
                errorDialog = Formatters.userMessage(it, "Не удалось войти в Google")
                message = errorDialog
            }
            busy = false
        }
    }

    fun launchGoogleSignIn() {
        runCatching {
            googleSignInLauncher.launch(googleDrive().signInIntent())
        }.onFailure {
            errorDialog = Formatters.userMessage(it, "Не удалось открыть вход")
            message = errorDialog
        }
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

            SettingsSection(title = "Google-аккаунт") {
                Column(
                    modifier = Modifier.testTag(SettingsTags.GOOGLE_SECTION),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!settings.googleLinked) {
                        Button(
                            onClick = { launchGoogleSignIn() },
                            enabled = !busy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(SettingsTags.GOOGLE_LINK),
                        ) { Text("Привязать Google") }
                        Text(
                            "Можно привязать в любой момент. Записи на телефоне не удаляются.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = { launchGoogleSignIn() },
                            enabled = !busy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(SettingsTags.GOOGLE_RESTORE),
                        ) { Text("Восстановить из Google") }
                        Text(
                            "Заменит записи на телефоне копией из Google. Сначала откроется вход в аккаунт.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val photo = settings.googlePhotoUrl
                            if (!photo.isNullOrBlank()) {
                                AsyncImage(
                                    model = photo,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        (settings.googleDisplayName ?: settings.googleEmail ?: "?")
                                            .take(1)
                                            .uppercase(),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    settings.googleDisplayName ?: "Google",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    settings.googleEmail.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            "Период автосохранения в Google",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        SyncPeriod.entries.forEach { period ->
                            SettingChipRow(
                                selected = settings.syncPeriod == period,
                                label = period.label,
                                onClick = {
                                    scope.launch {
                                        store.setSyncPeriod(period)
                                        CloudSyncWorker.enqueue(context, period)
                                    }
                                },
                            )
                        }
                        val last = settings.lastCloudSyncEpochMs
                        Text(
                            if (last != null) {
                                "Последнее сохранение: ${Formatters.date(last)}"
                            } else {
                                "Ещё не сохраняли в Google"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    runCatching { uploadPhoneToCloud() }
                                        .onSuccess {
                                            message = "Данные телефона сохранены в Google"
                                        }
                                        .onFailure {
                                            val text = Formatters.userMessage(
                                                it,
                                                "Не удалось сохранить в Google",
                                            )
                                            message = text
                                            errorDialog = text
                                        }
                                    busy = false
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(SettingsTags.GOOGLE_SYNC_NOW),
                        ) { Text("Сохранить в Google сейчас") }
                        OutlinedButton(
                            onClick = { showGoogleRestoreConfirm = true },
                            enabled = !busy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(SettingsTags.GOOGLE_RESTORE),
                        ) { Text("Восстановить из Google") }
                        Text(
                            "Заменит записи на телефоне копией из Google.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    runCatching {
                                        googleDrive().signOut()
                                        store.clearGoogleAccount()
                                        CloudSyncWorker.enqueue(context, SyncPeriod.OFF)
                                        message = "Google отвязан. Записи на телефоне на месте."
                                    }.onFailure {
                                        message = Formatters.userMessage(it, "Не удалось отвязать")
                                    }
                                    busy = false
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.testTag(SettingsTags.GOOGLE_UNLINK),
                        ) { Text("Отвязать Google") }
                        Text(
                            "В Google уходят записи, суммы и список работ. Фото из сервиса пока остаются только на телефоне.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (busy) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Подождите…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
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

            SettingsSection(title = "Резервное копирование") {
                Button(
                    onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                        exportLauncher.launch("autolog_backup_$stamp.json")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(SettingsTags.BACKUP_EXPORT),
                ) { Text("Создать резервную копию") }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(SettingsTags.BACKUP_IMPORT),
                ) { Text("Восстановить данные") }
                Text(
                    "Восстановление заменит текущие записи содержимым файла.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                                        updateStatus =
                                            "Доступна ${result.release.versionName}"
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
                    pendingRelease?.let { release ->
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
                    updateStatus?.let { status ->
                        Text(
                            status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
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
            modifier = Modifier.testTag(SettingsTags.GOOGLE_ERROR_DIALOG),
            title = { Text("Google") },
            text = {
                Text(
                    errorDialog.orEmpty(),
                    modifier = Modifier.testTag("${SettingsTags.GOOGLE_ERROR_DIALOG}_text"),
                )
            },
            confirmButton = {
                TextButton(onClick = { errorDialog = null }) { Text("ОК") }
            },
        )
    }

    if (showLinkConflict) {
        AlertDialog(
            onDismissRequest = { showLinkConflict = false },
            title = { Text("Данные и на телефоне, и в Google") },
            text = {
                Text(
                    "Аккаунт уже привязан, записи на телефоне не тронуты.\n\n" +
                        "Что сделать?",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLinkConflict = false
                        scope.launch {
                            busy = true
                            runCatching { uploadPhoneToCloud() }
                                .onFailure {
                                    message = Formatters.userMessage(
                                        it,
                                        "Не удалось сохранить в Google",
                                    )
                                }
                            busy = false
                        }
                    },
                ) { Text("Оставить данные телефона") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showLinkConflict = false
                            showGoogleRestoreConfirm = true
                        },
                    ) { Text("Взять из Google") }
                    TextButton(onClick = { showLinkConflict = false }) { Text("Позже") }
                }
            },
        )
    }

    if (showEmptyPhoneRestore) {
        AlertDialog(
            onDismissRequest = { showEmptyPhoneRestore = false },
            title = { Text("Найти копию в Google?") },
            text = {
                Text(
                    "На телефоне записей нет, а в Google есть копия. Восстановить её сюда?",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEmptyPhoneRestore = false
                        showGoogleRestoreConfirm = true
                    },
                ) { Text("Восстановить") }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyPhoneRestore = false }) { Text("Не сейчас") }
            },
        )
    }

    if (showGoogleRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showGoogleRestoreConfirm = false },
            title = { Text("Восстановить из Google?") },
            text = {
                Text("Текущие автомобили и записи на телефоне будут заменены копией из Google.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showGoogleRestoreConfirm = false
                        scope.launch {
                            busy = true
                            runCatching { restoreFromGoogle() }
                                .onFailure {
                                    val text = Formatters.userMessage(
                                        it,
                                        "Не удалось восстановить из Google",
                                    )
                                    message = text
                                    errorDialog = text
                                }
                            busy = false
                        }
                    },
                    modifier = Modifier.testTag(SettingsTags.GOOGLE_RESTORE_CONFIRM),
                ) { Text("Восстановить") }
            },
            dismissButton = {
                TextButton(onClick = { showGoogleRestoreConfirm = false }) { Text("Отмена") }
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
