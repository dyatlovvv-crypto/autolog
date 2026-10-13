package ru.autolog.app.ui.note

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import ru.autolog.app.AutologApp
import ru.autolog.app.data.repository.AutologRepository
import ru.autolog.app.ui.fluid.rubberBandOverscroll
import ru.autolog.app.ui.glass.GlassScaffold
import ru.autolog.app.ui.glass.GlassTopAppBar
import ru.autolog.app.ui.theme.AutologColors
import ru.autolog.app.ui.theme.AutologSpace
import java.io.File

object NoteTags {
    const val SCREEN = "note_screen"
    const val TEXT = "note_text"
    const val BACK = "note_back"
    const val ADD_CHECK = "note_add_check"
    const val ATTACH = "note_attach"
    const val ATTACH_GALLERY = "note_attach_gallery"
    const val ATTACH_CLIPBOARD = "note_attach_clipboard"
    fun checkItem(id: String) = "note_check_$id"
    fun checkToggle(id: String) = "note_check_toggle_$id"
}

data class NotepadUiState(
    val vehicleId: Long? = null,
    val vehicleName: String? = null,
    val document: NotepadDocument = NotepadDocument.empty(),
    val loading: Boolean = true,
    val error: String? = null,
    val toast: String? = null,
)

class NotepadViewModel(
    private val repository: AutologRepository,
    private val appContext: Context,
) : ViewModel() {
    private val document = MutableStateFlow(NotepadDocument.empty())
    private val meta = MutableStateFlow(NotepadUiState())
    private var saveJob: Job? = null
    private var loadedVehicleId: Long? = null

    val uiState: StateFlow<NotepadUiState> = combine(
        document,
        meta,
    ) { doc, m ->
        m.copy(document = doc)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        NotepadUiState(),
    )

    init {
        viewModelScope.launch {
            repository.observeActiveVehicle().collect { vehicle ->
                if (vehicle == null) {
                    meta.update {
                        it.copy(
                            vehicleId = null,
                            vehicleName = null,
                            loading = false,
                            error = "Сначала добавьте автомобиль",
                        )
                    }
                    document.value = NotepadDocument.empty()
                    loadedVehicleId = null
                    return@collect
                }
                if (loadedVehicleId != vehicle.id) {
                    loadedVehicleId = vehicle.id
                    val saved = repository.getNotepad(vehicle.id)?.text
                    document.value = NotepadDocument.decode(saved)
                    meta.update {
                        it.copy(
                            vehicleId = vehicle.id,
                            vehicleName = vehicle.name,
                            loading = false,
                            error = null,
                        )
                    }
                } else {
                    meta.update {
                        it.copy(
                            vehicleId = vehicle.id,
                            vehicleName = vehicle.name,
                            loading = false,
                            error = null,
                        )
                    }
                }
            }
        }
    }

    fun updateText(id: String, body: String) {
        mutate { blocks ->
            blocks.map { b ->
                if (b is NotepadBlock.Text && b.id == id) b.copy(body = body) else b
            }
        }
    }

    fun updateCheckLabel(id: String, label: String) {
        mutate { blocks ->
            blocks.map { b ->
                if (b is NotepadBlock.Check && b.id == id) b.copy(label = label) else b
            }
        }
    }

    fun toggleCheck(id: String) {
        mutate { blocks ->
            blocks.map { b ->
                if (b is NotepadBlock.Check && b.id == id) b.copy(done = !b.done) else b
            }
        }
    }

    fun addChecklistItem() {
        mutate { blocks -> blocks + NotepadBlock.Check(label = "") }
    }

    fun removeBlock(id: String) {
        mutate { blocks ->
            val next = blocks.filterNot { it.id == id }
            if (next.isEmpty()) listOf(NotepadBlock.Text()) else next
        }
    }

    fun addImageFromUri(uri: Uri) {
        val vehicleId = meta.value.vehicleId ?: return
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                NotepadPhotos.copyUri(appContext, vehicleId, uri)
            } ?: return@launch
            mutate { blocks -> blocks + NotepadBlock.Image(path = path) }
        }
    }

    fun addImageFromClipboard() {
        val vehicleId = meta.value.vehicleId ?: return
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                NotepadPhotos.fromClipboard(appContext, vehicleId)
            }
            if (path == null) {
                meta.update { it.copy(toast = "В буфере нет картинки") }
                return@launch
            }
            mutate { blocks -> blocks + NotepadBlock.Image(path = path) }
        }
    }

    fun consumeToast() {
        meta.update { it.copy(toast = null) }
    }

    fun flushSync() {
        saveJob?.cancel()
        saveJob = null
        val vehicleId = meta.value.vehicleId ?: return
        val encoded = document.value.encode()
        runBlocking(Dispatchers.IO) {
            repository.saveNotepad(vehicleId, encoded)
        }
    }

    private suspend fun flushAndAwait() {
        saveJob?.cancel()
        saveJob = null
        val vehicleId = meta.value.vehicleId ?: return
        val encoded = document.value.encode()
        withContext(Dispatchers.IO) {
            repository.saveNotepad(vehicleId, encoded)
        }
    }

    private fun mutate(transform: (List<NotepadBlock>) -> List<NotepadBlock>) {
        document.update { it.copy(blocks = transform(it.blocks)) }
        scheduleSave()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            flushAndAwait()
        }
    }

    companion object {
        private const val SAVE_DEBOUNCE_MS = 700L
    }
}

class NotepadVmFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val app = AutologApp.instance
        return NotepadViewModel(
            repository = app.container.repository,
            appContext = app.applicationContext,
        ) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NotepadScreen(
    onBack: () -> Unit,
) {
    val vm: NotepadViewModel = viewModel(factory = NotepadVmFactory())
    val state by vm.uiState.collectAsStateWithLifecycle()
    var attachDialog by remember { mutableStateOf(false) }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) vm.addImageFromUri(uri)
    }

    DisposableEffect(Unit) {
        onDispose { vm.flushSync() }
    }

    state.toast?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.consumeToast() },
            confirmButton = {
                TextButton(onClick = { vm.consumeToast() }) { Text("Ок") }
            },
            text = { Text(msg) },
        )
    }

    GlassScaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(NoteTags.SCREEN),
        topBar = {
            GlassTopAppBar(
                title = {
                    Column {
                        Text("Заметки", style = MaterialTheme.typography.titleMedium)
                        state.vehicleName?.let { name ->
                            Text(
                                name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            vm.flushSync()
                            onBack()
                        },
                        modifier = Modifier.testTag(NoteTags.BACK),
                    ) { Text("←") }
                },
                actions = {
                    if (state.vehicleId != null) {
                        IconButton(
                            onClick = { vm.addChecklistItem() },
                            modifier = Modifier.testTag(NoteTags.ADD_CHECK),
                        ) {
                            Icon(
                                Icons.Outlined.Checklist,
                                contentDescription = "Чек-лист",
                            )
                        }
                        IconButton(
                            onClick = { attachDialog = true },
                            modifier = Modifier.testTag(NoteTags.ATTACH),
                        ) {
                            Icon(
                                Icons.Outlined.AttachFile,
                                contentDescription = "Прикрепить",
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        val textStyle = MaterialTheme.typography.bodyLarge.merge(
            TextStyle(color = MaterialTheme.colorScheme.onBackground),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = AutologSpace.md, vertical = AutologSpace.sm)
                .rubberBandOverscroll(),
        ) {
            if (state.error != null) {
                Text(
                    state.error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    state.document.blocks.forEachIndexed { index, block ->
                        when (block) {
                            is NotepadBlock.Text -> TextBlockEditor(
                                block = block,
                                textStyle = textStyle,
                                isPrimary = index == state.document.blocks.indexOfFirst { it is NotepadBlock.Text },
                                onChange = { vm.updateText(block.id, it) },
                            )
                            is NotepadBlock.Check -> ChecklistRow(
                                block = block,
                                onToggle = { vm.toggleCheck(block.id) },
                                onLabel = { vm.updateCheckLabel(block.id, it) },
                                onRemove = { vm.removeBlock(block.id) },
                            )
                            is NotepadBlock.Image -> ImageBlock(
                                block = block,
                                onRemove = { vm.removeBlock(block.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (attachDialog) {
        AlertDialog(
            onDismissRequest = { attachDialog = false },
            title = { Text("Картинка") },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            attachDialog = false
                            galleryLauncher.launch("image/*")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(NoteTags.ATTACH_GALLERY),
                    ) { Text("Галерея") }
                    TextButton(
                        onClick = {
                            attachDialog = false
                            vm.addImageFromClipboard()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(NoteTags.ATTACH_CLIPBOARD),
                    ) { Text("Из буфера обмена") }
                }
            },
            confirmButton = {
                TextButton(onClick = { attachDialog = false }) { Text("Отмена") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TextBlockEditor(
    block: NotepadBlock.Text,
    textStyle: TextStyle,
    isPrimary: Boolean,
    onChange: (String) -> Unit,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val bringIntoView = remember { BringIntoViewRequester() }
    val links = remember(block.body) { NotepadLinks.find(block.body) }

    Column(modifier = Modifier.fillMaxWidth()) {
        BasicTextField(
            value = block.body,
            onValueChange = onChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (isPrimary) 120.dp else 40.dp)
                .bringIntoViewRequester(bringIntoView)
                .onFocusChanged { focus ->
                    if (focus.isFocused) {
                        scope.launch {
                            delay(280)
                            bringIntoView.bringIntoView()
                        }
                    }
                }
                .then(if (isPrimary) Modifier.testTag(NoteTags.TEXT) else Modifier),
            textStyle = textStyle,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { keyboard?.hide() },
            ),
            decorationBox = { inner ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (block.body.isEmpty() && isPrimary) {
                        Text(
                            "Пишите что угодно — ссылки, мысли, списки…",
                            style = textStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            },
        )
        if (links.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(top = AutologSpace.xs),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                links.forEach { span ->
                    TextButton(
                        onClick = { openNotepadUrl(context, span.url) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 0.dp,
                            vertical = 0.dp,
                        ),
                    ) {
                        Text(
                            span.url,
                            style = MaterialTheme.typography.bodySmall.merge(
                                TextStyle(
                                    color = MaterialTheme.colorScheme.primary,
                                    textDecoration = TextDecoration.Underline,
                                ),
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private fun openNotepadUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChecklistRow(
    block: NotepadBlock.Check,
    onToggle: () -> Unit,
    onLabel: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val labelStyle = MaterialTheme.typography.bodyLarge.merge(
        TextStyle(color = MaterialTheme.colorScheme.onBackground),
    )
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoView)
            .testTag(NoteTags.checkItem(block.id)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NotepadCheckCircle(
            checked = block.done,
            onClick = onToggle,
            modifier = Modifier.testTag(NoteTags.checkToggle(block.id)),
        )
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = block.label,
            onValueChange = onLabel,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focus ->
                    if (focus.isFocused) {
                        scope.launch {
                            delay(280)
                            bringIntoView.bringIntoView()
                        }
                    }
                },
            textStyle = labelStyle,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { keyboard?.hide() },
            ),
            decorationBox = { inner ->
                Box {
                    if (block.label.isEmpty()) {
                        Text(
                            "Пункт",
                            style = labelStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            },
        )
        TextButton(onClick = onRemove) {
            Text("×", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NotepadCheckCircle(
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val checkedFill = AutologColors.Fuel
    val uncheckedStroke = Color(0xFFBDBDBD)
    Canvas(
        modifier = modifier
            .size(26.dp)
            .clickable(onClick = onClick),
    ) {
        val strokeWidth = 1.8.dp.toPx()
        if (checked) {
            drawCircle(color = checkedFill)
            val path = Path().apply {
                moveTo(size.width * 0.28f, size.height * 0.52f)
                lineTo(size.width * 0.42f, size.height * 0.66f)
                lineTo(size.width * 0.72f, size.height * 0.34f)
            }
            drawPath(
                path = path,
                color = Color.White,
                style = Stroke(
                    width = 2.4.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        } else {
            drawCircle(
                color = uncheckedStroke,
                style = Stroke(width = strokeWidth),
            )
        }
    }
}

@Composable
private fun ImageBlock(
    block: NotepadBlock.Image,
    onRemove: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AsyncImage(
            model = File(block.path),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    RoundedCornerShape(12.dp),
                ),
            contentScale = ContentScale.Fit,
        )
        TextButton(onClick = onRemove) {
            Text("Удалить фото", color = MaterialTheme.colorScheme.error)
        }
    }
}
