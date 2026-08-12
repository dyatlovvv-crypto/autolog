package ru.autolog.app.ui.service

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import java.util.Calendar
import java.util.UUID
import ru.autolog.app.AutologApp
import ru.autolog.app.domain.ServiceKind
import ru.autolog.app.ui.fluid.FluidCheckboxRow
import ru.autolog.app.ui.fluid.rubberBandOverscroll
import ru.autolog.app.ui.glass.GlassPanel
import ru.autolog.app.ui.glass.GlassScaffold
import ru.autolog.app.ui.glass.GlassShapeLarge
import ru.autolog.app.ui.glass.GlassShapeSmall
import ru.autolog.app.ui.glass.GlassTopAppBar
import ru.autolog.app.ui.glass.glassFieldColors

object ServiceTags {
    const val SCREEN = "service_screen"
    const val SAVE = "service_save"
    const val EDIT = "service_edit"
    const val TYPE = "service_type"
    const val AMOUNT = "service_amount"
    const val ODOMETER = "service_odometer"
    const val DATE = "service_date"
    const val DESCRIPTION = "service_description"
    const val CHECKLIST_INPUT = "service_checklist_input"
    const val CHECKLIST_ADD = "service_checklist_add"
    const val ATTACH = "service_attach"
    const val DELETE = "service_delete"
}

fun createCameraCaptureFile(context: Context): Pair<File, Uri> {
    val dir = File(context.cacheDir, "camera").also { it.mkdirs() }
    val file = File(dir, "capture_${UUID.randomUUID()}.jpg")
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    return file to uri
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    onDeleted: (() -> Unit)? = null,
    editServiceId: Long? = null,
    fixedKind: ServiceKind? = null,
    /** Open existing record in view mode; pencil switches to edit. */
    startReadOnly: Boolean = false,
) {
    val context = LocalContext.current
    val vm: ServiceViewModel = viewModel(
        key = "service-${editServiceId ?: fixedKind?.name ?: "new"}",
        factory = ServiceVmFactory(editServiceId, fixedKind, context.applicationContext),
    )
    val state by vm.form.collectAsStateWithLifecycle()
    vm.activeVehicle.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var typeMenuOpen by remember { mutableStateOf(false) }
    var attachDialog by remember { mutableStateOf(false) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var readOnly by remember(editServiceId, startReadOnly) {
        mutableStateOf(startReadOnly && editServiceId != null)
    }
    val editing = state.editId != null
    val dateLabel = remember(state.dateEpochMs) { vm.dateLabel() }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) vm.addPhotoFromUri(uri)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { ok ->
        val file = pendingCameraFile
        pendingCameraFile = null
        if (ok && file != null) {
            // Persist into app files
            val dir = File(context.filesDir, "service_photos").also { it.mkdirs() }
            val dest = File(dir, "${UUID.randomUUID()}.jpg")
            runCatching { file.copyTo(dest, overwrite = true) }
            vm.addPhotoFromFile(dest.absolutePath)
            runCatching { file.delete() }
        }
    }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val (file, uri) = createCameraCaptureFile(context)
            pendingCameraFile = file
            cameraLauncher.launch(uri)
        }
    }

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    GlassScaffold(
        modifier = Modifier.testTag(ServiceTags.SCREEN),
        topBar = {
            GlassTopAppBar(
                title = { Text(if (readOnly) "Просмотр · ${state.kind.label}" else state.kind.label) },
                navigationIcon = { IconButton(onClick = onBack) { Text("←") } },
                actions = {
                    if (readOnly && editing) {
                        IconButton(
                            onClick = { readOnly = false },
                            modifier = Modifier.testTag(ServiceTags.EDIT),
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Редактировать")
                        }
                    }
                    if (!readOnly && editing && onDeleted != null) {
                        TextButton(
                            onClick = { confirmDelete = true },
                            modifier = Modifier.testTag(ServiceTags.DELETE),
                        ) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
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
                GlassPanel(modifier = Modifier.fillMaxWidth(), shape = GlassShapeLarge, elevation = 10.dp) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (state.kindLocked) {
                            Text(
                                state.kind.label,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.testTag(ServiceTags.TYPE),
                            )
                        } else {
                            ExposedDropdownMenuBox(
                                expanded = typeMenuOpen,
                                onExpandedChange = { typeMenuOpen = it },
                            ) {
                                OutlinedTextField(
                                    value = state.kind.label,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Тип сервиса") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeMenuOpen) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor()
                                        .testTag(ServiceTags.TYPE),
                                    shape = GlassShapeLarge,
                                    colors = glassFieldColors(),
                                )
                                ExposedDropdownMenu(
                                    expanded = typeMenuOpen,
                                    onDismissRequest = { typeMenuOpen = false },
                                ) {
                                    ServiceKind.entries.forEach { kind ->
                                        DropdownMenuItem(
                                            text = { Text(kind.label) },
                                            onClick = {
                                                vm.onKind(kind)
                                                typeMenuOpen = false
                                            },
                                            modifier = Modifier.testTag("service_kind_${kind.name}"),
                                        )
                                    }
                                }
                            }
                        }

                        Text("Работы", style = MaterialTheme.typography.titleSmall)
                        if (!readOnly) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = state.checklistDraft,
                                    onValueChange = vm::onChecklistDraft,
                                    label = { Text("Название работы") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag(ServiceTags.CHECKLIST_INPUT),
                                    singleLine = true,
                                    shape = GlassShapeLarge,
                                    colors = glassFieldColors(),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(
                                        onDone = { vm.addChecklistItem() },
                                    ),
                                )
                                TextButton(
                                    onClick = vm::addChecklistItem,
                                    modifier = Modifier.testTag(ServiceTags.CHECKLIST_ADD),
                                ) {
                                    Text("Добавить")
                                }
                            }
                        }
                        if (state.checklist.isEmpty() && readOnly) {
                            Text(
                                "Список работ пуст",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        state.checklist.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (readOnly) {
                                    Text(
                                        text = buildString {
                                            append(if (item.done) "✓ " else "○ ")
                                            append(item.text)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("service_check_${item.id}"),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                } else {
                                    FluidCheckboxRow(
                                        label = item.text,
                                        checked = item.done,
                                        onCheckedChange = { vm.toggleChecklist(item.id, it) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("service_check_${item.id}"),
                                    )
                                    IconButton(onClick = { vm.removeChecklist(item.id) }) {
                                        Icon(Icons.Default.Close, contentDescription = "Удалить пункт")
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = state.odometer,
                            onValueChange = vm::onOdometer,
                            label = { Text("Пробег, км") },
                            modifier = Modifier.fillMaxWidth().testTag(ServiceTags.ODOMETER),
                            singleLine = true,
                            readOnly = readOnly,
                            enabled = !readOnly,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = GlassShapeLarge,
                            colors = glassFieldColors(),
                        )
                        OutlinedTextField(
                            value = dateLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Дата") },
                            modifier = Modifier.fillMaxWidth().testTag(ServiceTags.DATE),
                            singleLine = true,
                            shape = GlassShapeLarge,
                            colors = glassFieldColors(),
                            trailingIcon = if (readOnly) {
                                null
                            } else {
                                {
                                    TextButton(
                                        onClick = {
                                            val cal = Calendar.getInstance().apply {
                                                timeInMillis = state.dateEpochMs
                                            }
                                            DatePickerDialog(
                                                context,
                                                { _, y, m, d -> vm.onDate(y, m, d) },
                                                cal.get(Calendar.YEAR),
                                                cal.get(Calendar.MONTH),
                                                cal.get(Calendar.DAY_OF_MONTH),
                                            ).show()
                                        },
                                    ) { Text("Выбрать") }
                                }
                            },
                        )
                        OutlinedTextField(
                            value = state.amount,
                            onValueChange = vm::onAmount,
                            label = { Text("Сумма") },
                            modifier = Modifier.fillMaxWidth().testTag(ServiceTags.AMOUNT),
                            singleLine = true,
                            readOnly = readOnly,
                            enabled = !readOnly,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = GlassShapeLarge,
                            colors = glassFieldColors(),
                        )
                        OutlinedTextField(
                            value = state.description,
                            onValueChange = vm::onDescription,
                            label = { Text("Описание") },
                            modifier = Modifier.fillMaxWidth().testTag(ServiceTags.DESCRIPTION),
                            readOnly = readOnly,
                            enabled = !readOnly,
                            shape = GlassShapeLarge,
                            colors = glassFieldColors(),
                            trailingIcon = if (readOnly) {
                                null
                            } else {
                                {
                                    IconButton(
                                        onClick = { attachDialog = true },
                                        modifier = Modifier.testTag(ServiceTags.ATTACH),
                                    ) {
                                        Icon(Icons.Default.AttachFile, contentDescription = "Прикрепить фото")
                                    }
                                }
                            },
                        )
                        if (state.photoPaths.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                state.photoPaths.forEach { path ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        val bitmap = remember(path) {
                                            BitmapFactory.decodeFile(path)?.asImageBitmap()
                                        }
                                        if (bitmap != null) {
                                            Image(
                                                bitmap = bitmap,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(88.dp)
                                                    .clip(GlassShapeSmall),
                                                contentScale = ContentScale.Crop,
                                            )
                                        }
                                        if (!readOnly) {
                                            TextButton(onClick = { vm.removePhoto(path) }) {
                                                Text("Убрать")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (!readOnly) {
                            Button(
                                onClick = vm::save,
                                enabled = !state.saving,
                                modifier = Modifier.fillMaxWidth().testTag(ServiceTags.SAVE),
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
    }

    if (attachDialog) {
        AlertDialog(
            onDismissRequest = { attachDialog = false },
            title = { Text("Фото") },
            text = { Text("Галерея или камера") },
            confirmButton = {
                TextButton(
                    onClick = {
                        attachDialog = false
                        galleryLauncher.launch("image/*")
                    },
                ) { Text("Галерея") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        attachDialog = false
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            val (file, uri) = createCameraCaptureFile(context)
                            pendingCameraFile = file
                            cameraLauncher.launch(uri)
                        } else {
                            cameraPermission.launch(Manifest.permission.CAMERA)
                        }
                    },
                ) { Text("Камера") }
            },
        )
    }

    if (confirmDelete && onDeleted != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить сервис?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(onDeleted)
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

private class ServiceVmFactory(
    private val editServiceId: Long?,
    private val fixedKind: ServiceKind?,
    private val appContext: Context,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val container = AutologApp.instance.container
        return ServiceViewModel(
            repository = container.repository,
            editId = editServiceId,
            appContext = appContext,
            savedStateHandle = extras.createSavedStateHandle(),
            draftStore = container.formDraftStore,
            fixedKind = fixedKind,
        ) as T
    }
}
