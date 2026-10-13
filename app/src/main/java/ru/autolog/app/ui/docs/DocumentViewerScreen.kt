package ru.autolog.app.ui.docs

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.autolog.app.AutologApp
import ru.autolog.app.data.local.entity.VehicleDocumentEntity
import ru.autolog.app.ui.glass.GlassScaffold
import ru.autolog.app.ui.glass.GlassTopAppBar
import ru.autolog.app.ui.theme.AutologSpace
import java.io.File

object DocumentViewerTags {
    const val SCREEN = "document_viewer_screen"
    const val BACK = "document_viewer_back"
    const val OPEN_EXTERNAL = "document_viewer_open_external"
    const val PDF_LIST = "document_viewer_pdf_list"
    const val PDF_ERROR = "document_viewer_pdf_error"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    documentId: Long,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repo = AutologApp.instance.container.repository
    var doc by remember(documentId) { mutableStateOf<VehicleDocumentEntity?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(documentId) {
        doc = withContext(Dispatchers.IO) { repo.getVehicleDocument(documentId) }
        if (doc == null) loadError = "Документ не найден"
    }

    val current = doc
    GlassScaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(DocumentViewerTags.SCREEN),
        topBar = {
            GlassTopAppBar(
                title = {
                    Column {
                        Text(
                            current?.title ?: "Документ",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                        )
                        current?.let { d ->
                            Text(
                                if (d.isPdf) "PDF" else "Фото",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag(DocumentViewerTags.BACK),
                    ) { Text("←") }
                },
                actions = {
                    if (current != null) {
                        IconButton(
                            onClick = { openExternally(context, current) },
                            modifier = Modifier.testTag(DocumentViewerTags.OPEN_EXTERNAL),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = "Открыть снаружи",
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            loadError != null -> {
                Text(
                    loadError.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(padding)
                        .padding(AutologSpace.md),
                )
            }
            current == null -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
            !File(current.filePath).exists() -> {
                Text(
                    "Файл не найден на устройстве",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(padding)
                        .padding(AutologSpace.md),
                )
            }
            current.isPdf -> {
                InAppPdfViewer(
                    file = File(current.filePath),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )
            }
            else -> {
                InAppImageViewer(
                    file = File(current.filePath),
                    title = current.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )
            }
        }
    }
}

@Composable
private fun InAppPdfViewer(
    file: File,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val screenWidthPx = with(density) {
        context.resources.displayMetrics.widthPixels
    }
    var pageCount by remember { mutableStateOf(0) }
    var pageBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    DisposableEffect(file.absolutePath) {
        onDispose {
            pageBitmaps.forEach { it.recycle() }
        }
    }

    LaunchedEffect(file.absolutePath, screenWidthPx) {
        loading = true
        error = null
        pageBitmaps.forEach { it.recycle() }
        pageBitmaps = emptyList()
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                // PdfRenderer owns and closes the descriptor.
                PdfRenderer(pfd).use { renderer ->
                    val bitmaps = ArrayList<Bitmap>(renderer.pageCount)
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            val scale = screenWidthPx.toFloat() / page.width.coerceAtLeast(1)
                            val w = (page.width * scale).toInt().coerceAtLeast(1)
                            val h = (page.height * scale).toInt().coerceAtLeast(1)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmaps.add(bmp)
                        }
                    }
                    bitmaps to renderer.pageCount
                }
            }
        }
        result.onSuccess { (bitmaps, count) ->
            pageBitmaps = bitmaps
            pageCount = count
            loading = false
        }.onFailure { e ->
            error = e.message ?: "Не удалось открыть PDF"
            loading = false
        }
    }

    val visiblePage by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex + 1
        }
    }

    Box(modifier = modifier) {
        when {
            loading -> {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            error != null -> {
                Text(
                    error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(AutologSpace.md)
                        .testTag(DocumentViewerTags.PDF_ERROR),
                )
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(DocumentViewerTags.PDF_LIST),
                    contentPadding = PaddingValues(bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(pageBitmaps, key = { index, _ -> index }) { _, bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface),
                        )
                    }
                }
                if (pageCount > 0) {
                    Text(
                        "$visiblePage / $pageCount",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = AutologSpace.md)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                shape = MaterialTheme.shapes.small,
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun InAppImageViewer(
    file: File,
    title: String,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        if (scale > 1f) {
            offset += panChange
        } else {
            offset = Offset.Zero
        }
    }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = file,
            contentDescription = title,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .transformable(transformState),
        )
    }
}

private val VehicleDocumentEntity.isPdf: Boolean
    get() = mimeType.equals("application/pdf", ignoreCase = true)

private fun openExternally(context: android.content.Context, doc: VehicleDocumentEntity) {
    val file = File(doc.filePath)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, doc.mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, doc.title)) }
}
