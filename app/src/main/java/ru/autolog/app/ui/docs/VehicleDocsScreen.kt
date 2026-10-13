package ru.autolog.app.ui.docs

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.autolog.app.AutologApp
import ru.autolog.app.data.local.entity.VehicleDocumentEntity
import ru.autolog.app.data.repository.AutologRepository
import ru.autolog.app.data.settings.DocsViewMode
import ru.autolog.app.data.settings.UserSettings
import ru.autolog.app.ui.fluid.rubberBandOverscroll
import ru.autolog.app.ui.glass.GlassScaffold
import ru.autolog.app.ui.glass.GlassTopAppBar
import ru.autolog.app.ui.theme.AutologSpace
import ru.autolog.app.ui.util.Formatters
import java.io.File

object VehicleDocsTags {
    const val SCREEN = "vehicle_docs_screen"
    const val ADD = "vehicle_docs_add"
    const val EMPTY = "vehicle_docs_empty"
    const val BACK = "vehicle_docs_back"
    const val VIEW_MODE = "vehicle_docs_view_mode"
    const val VIEW_LIST = "vehicle_docs_view_list"
    const val VIEW_GRID = "vehicle_docs_view_grid"
    const val VIEW_CARDS = "vehicle_docs_view_cards"
    fun item(id: Long) = "vehicle_docs_item_$id"
    fun delete(id: Long) = "vehicle_docs_delete_$id"
}

data class VehicleDocsUiState(
    val vehicleId: Long? = null,
    val vehicleName: String? = null,
    val documents: List<VehicleDocumentEntity> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class VehicleDocsViewModel(
    private val repository: AutologRepository,
) : ViewModel() {
    private val actionError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<VehicleDocsUiState> = repository.observeActiveVehicle()
        .flatMapLatest { vehicle ->
            if (vehicle == null) {
                flowOf(
                    VehicleDocsUiState(
                        loading = false,
                        error = "Сначала добавьте автомобиль",
                    ),
                )
            } else {
                combine(
                    repository.observeVehicleDocuments(vehicle.id),
                    actionError,
                ) { docs, err ->
                    VehicleDocsUiState(
                        vehicleId = vehicle.id,
                        vehicleName = vehicle.name,
                        documents = docs,
                        loading = false,
                        error = err,
                    )
                }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            VehicleDocsUiState(),
        )

    fun addFromUri(uri: Uri) {
        val vehicleId = uiState.value.vehicleId ?: return
        viewModelScope.launch {
            repository.addVehicleDocument(vehicleId, uri)
                .onFailure { e ->
                    actionError.value = e.message ?: "Не удалось добавить файл"
                }
                .onSuccess { actionError.value = null }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            repository.deleteVehicleDocument(id)
        }
    }

    fun clearError() {
        actionError.value = null
    }
}

class VehicleDocsVmFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return VehicleDocsViewModel(
            repository = AutologApp.instance.container.repository,
        ) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleDocsScreen(
    onBack: () -> Unit,
    onOpenDocument: (Long) -> Unit,
) {
    val vm: VehicleDocsViewModel = viewModel(factory = VehicleDocsVmFactory())
    val state by vm.uiState.collectAsStateWithLifecycle()
    val settingsStore = AutologApp.instance.container.settingsStore
    val settings by settingsStore.settings.collectAsStateWithLifecycle(initialValue = UserSettings())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var confirmDeleteId by remember { mutableStateOf<Long?>(null) }
    var showViewOptions by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            vm.addFromUri(uri)
        }
    }

    if (state.error != null && state.vehicleId != null) {
        AlertDialog(
            onDismissRequest = { vm.clearError() },
            confirmButton = {
                TextButton(onClick = { vm.clearError() }) { Text("Ок") }
            },
            text = { Text(state.error.orEmpty()) },
        )
    }

    if (showViewOptions) {
        AlertDialog(
            onDismissRequest = { showViewOptions = false },
            title = { Text("Отображение") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DocsViewMode.entries.forEach { mode ->
                        val selected = settings.docsViewMode == mode
                        val tag = when (mode) {
                            DocsViewMode.LIST -> VehicleDocsTags.VIEW_LIST
                            DocsViewMode.GRID -> VehicleDocsTags.VIEW_GRID
                            DocsViewMode.CARDS -> VehicleDocsTags.VIEW_CARDS
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(tag)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    scope.launch { settingsStore.setDocsViewMode(mode) }
                                    showViewOptions = false
                                }
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                )
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = when (mode) {
                                    DocsViewMode.LIST -> Icons.Outlined.ViewList
                                    DocsViewMode.GRID -> Icons.Outlined.GridView
                                    DocsViewMode.CARDS -> Icons.Outlined.ViewAgenda
                                },
                                contentDescription = null,
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            Text(
                                mode.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showViewOptions = false }) { Text("Закрыть") }
            },
        )
    }

    GlassScaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(VehicleDocsTags.SCREEN),
        topBar = {
            GlassTopAppBar(
                title = {
                    Column {
                        Text("Документы", style = MaterialTheme.typography.titleMedium)
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
                        onClick = onBack,
                        modifier = Modifier.testTag(VehicleDocsTags.BACK),
                    ) { Text("←") }
                },
                actions = {
                    IconButton(
                        onClick = { showViewOptions = true },
                        modifier = Modifier.testTag(VehicleDocsTags.VIEW_MODE),
                    ) {
                        Icon(
                            imageVector = when (settings.docsViewMode) {
                                DocsViewMode.LIST -> Icons.Outlined.ViewList
                                DocsViewMode.GRID -> Icons.Outlined.GridView
                                DocsViewMode.CARDS -> Icons.Outlined.ViewAgenda
                            },
                            contentDescription = "Отображение",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.vehicleId != null) {
                FloatingActionButton(
                    onClick = {
                        picker.launch(arrayOf("application/pdf", "image/*"))
                    },
                    modifier = Modifier.testTag(VehicleDocsTags.ADD),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Добавить документ")
                }
            }
        },
    ) { padding ->
        when {
            state.vehicleId == null && !state.loading -> {
                Text(
                    state.error ?: "Сначала добавьте автомобиль",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(padding)
                        .padding(AutologSpace.md),
                )
            }
            state.documents.isEmpty() && !state.loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(AutologSpace.md)
                        .testTag(VehicleDocsTags.EMPTY),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.AttachFile,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "PDF и фото документов\nОСАГО, права, СТС…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = AutologSpace.sm),
                        )
                    }
                }
            }
            else -> {
                when (settings.docsViewMode) {
                    DocsViewMode.LIST -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .rubberBandOverscroll(),
                            contentPadding = PaddingValues(
                                horizontal = AutologSpace.md,
                                vertical = AutologSpace.sm,
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.documents, key = { it.id }) { doc ->
                                DocumentListRow(
                                    doc = doc,
                                    onOpen = { onOpenDocument(doc.id) },
                                    onDelete = { confirmDeleteId = doc.id },
                                )
                            }
                        }
                    }
                    DocsViewMode.GRID -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 104.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .rubberBandOverscroll(),
                            contentPadding = PaddingValues(
                                horizontal = AutologSpace.md,
                                vertical = AutologSpace.sm,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(state.documents, key = { it.id }) { doc ->
                                DocumentGridCell(
                                    doc = doc,
                                    onOpen = { onOpenDocument(doc.id) },
                                    onDelete = { confirmDeleteId = doc.id },
                                )
                            }
                        }
                    }
                    DocsViewMode.CARDS -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .rubberBandOverscroll(),
                            contentPadding = PaddingValues(
                                horizontal = AutologSpace.md,
                                vertical = AutologSpace.sm,
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.documents, key = { it.id }) { doc ->
                                DocumentCard(
                                    doc = doc,
                                    onOpen = { onOpenDocument(doc.id) },
                                    onDelete = { confirmDeleteId = doc.id },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    confirmDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDeleteId = null },
            title = { Text("Удалить документ?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.delete(id)
                        confirmDeleteId = null
                    },
                ) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteId = null }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun DocumentListRow(
    doc: VehicleDocumentEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val isPdf = doc.isPdf
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(VehicleDocsTags.item(doc.id))
            .clickable(onClick = onOpen)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DocThumb(doc = doc, modifier = Modifier.size(40.dp), corner = 8.dp)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = AutologSpace.sm),
        ) {
            Text(doc.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildString {
                    append(if (isPdf) "PDF" else "Фото")
                    append(" · ")
                    append(Formatters.date(doc.createdAtEpochMs))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.testTag(VehicleDocsTags.delete(doc.id)),
        ) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "Удалить",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DocumentGridCell(
    doc: VehicleDocumentEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .testTag(VehicleDocsTags.item(doc.id))
            .clickable(onClick = onOpen),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            DocThumb(
                doc = doc,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                corner = 12.dp,
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(32.dp)
                    .testTag(VehicleDocsTags.delete(doc.id)),
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Удалить",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            doc.title,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DocumentCard(
    doc: VehicleDocumentEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val isPdf = doc.isPdf
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(VehicleDocsTags.item(doc.id))
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Column {
            DocThumb(
                doc = doc,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp),
                corner = 0.dp,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        doc.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        buildString {
                            append(if (isPdf) "PDF" else "Фото")
                            append(" · ")
                            append(Formatters.date(doc.createdAtEpochMs))
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag(VehicleDocsTags.delete(doc.id)),
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Удалить",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DocThumb(
    doc: VehicleDocumentEntity,
    modifier: Modifier = Modifier,
    corner: androidx.compose.ui.unit.Dp,
) {
    val shape = RoundedCornerShape(corner)
    val bg = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = modifier
            .clip(shape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        if (doc.isPdf) {
            Icon(
                Icons.Outlined.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        } else {
            AsyncImage(
                model = File(doc.filePath),
                contentDescription = doc.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private val VehicleDocumentEntity.isPdf: Boolean
    get() = mimeType.equals("application/pdf", ignoreCase = true)
