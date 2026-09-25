package de.workflow42.meinenoten.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import de.workflow42.meinenoten.model.PageView
import de.workflow42.meinenoten.model.ScoreDarkMode
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import de.workflow42.meinenoten.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Global cache for all PDFs, so switching between songs in a setlist is instant. */
private object GlobalPdfCache {
    // Map of URI -> (Page -> Bitmap)
    private val caches = mutableMapOf<String, MutableMap<Int, Bitmap>>()
    private const val MAX_TOTAL_BITMAPS = 12

    fun get(uri: String, page: Int): Bitmap? = caches[uri]?.get(page)

    fun put(uri: String, page: Int, bitmap: Bitmap) {
        val songCache = caches.getOrPut(uri) { mutableMapOf() }
        songCache[page] = bitmap
        trim()
    }

    private fun trim() {
        // Flatten all cached pages to a list of (uri, page)
        val allEntries = caches.flatMap { (uri, map) -> map.keys.map { uri to it } }
        if (allEntries.size <= MAX_TOTAL_BITMAPS) return

        // Simple cleanup: remove pages furthest from any "active" state could be hard.
        // For now, just remove the first few entries found until we are under limit.
        var removed = 0
        val targetRemove = allEntries.size - MAX_TOTAL_BITMAPS
        
        val iterator = caches.iterator()
        while (iterator.hasNext() && (removed < targetRemove)) {
            val entry = iterator.next()
            val pageIterator = entry.value.iterator()
            while (pageIterator.hasNext() && (removed < targetRemove)) {
                val pageEntry = pageIterator.next()
                pageEntry.value.recycle()
                pageIterator.remove()
                removed++
            }
            if (entry.value.isEmpty()) {
                iterator.remove()
            }
        }
    }
}

/**
 * Renders a single page of a PDF file.
 *
 * Neighbouring pages are pre-rendered in the background so that turning a page
 * during a performance happens without any visible delay.
 */
@Composable
fun PdfView(
    fileUri: String,
    currentPage: Int,
    pageView: PageView = PageView(),
    onPageViewChange: (PageView) -> Unit = {},
    modifier: Modifier = Modifier,
    onPageCountReady: (Int) -> Unit = {},
    /** Look of the page in the dark design, see [effectiveScoreMode]. */
    darkMode: ScoreDarkMode = ScoreDarkMode.NORMAL,
) {
    val colorFilter = scoreColorFilter(effectiveScoreMode(darkMode))
    val context = LocalContext.current
    var visible by remember(fileUri, currentPage) { 
        mutableStateOf(GlobalPdfCache.get(fileUri, currentPage)) 
    }
    var pageCount by remember(fileUri) { mutableIntStateOf(0) }
    var error by remember(fileUri) { mutableStateOf<String?>(null) }

    var scale by remember(fileUri, currentPage) { mutableStateOf(pageView.scale) }
    var offsetXRatio by remember(fileUri, currentPage) { mutableStateOf(pageView.offsetXRatio) }
    var offsetYRatio by remember(fileUri, currentPage) { mutableStateOf(pageView.offsetYRatio) }

    LaunchedEffect(pageView) {
        scale = pageView.scale
        offsetXRatio = pageView.offsetXRatio
        offsetYRatio = pageView.offsetYRatio
    }

    // Target width in pixels, capped so large scores stay memory friendly.
    val targetWidth = remember {
        val metrics = context.resources.displayMetrics
        (maxOf(metrics.widthPixels, metrics.heightPixels) * 1.5f).toInt().coerceAtMost(3000)
    }

    // Resolved up front: the messages are assigned from a background dispatcher, where
    // stringResource cannot be called.
    val noFileMessage = stringResource(R.string.error_no_file_selected)
    val loadFailedMessage = stringResource(R.string.error_pdf_load_failed)
    val openFailedMessage = stringResource(R.string.error_file_open_failed)
    val fileMissingMessage = stringResource(R.string.error_file_missing)

    LaunchedEffect(fileUri, currentPage) {
        if (fileUri.isEmpty()) {
            error = noFileMessage
            return@LaunchedEffect
        }

        GlobalPdfCache.get(fileUri, currentPage)?.let { visible = it }

        withContext(Dispatchers.IO) {
            try {
                openRenderer(context, fileUri, openFailedMessage, fileMissingMessage).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (pageCount != renderer.pageCount) {
                            pageCount = renderer.pageCount
                            withContext(Dispatchers.Main) { onPageCountReady(renderer.pageCount) }
                        }
                        if (currentPage !in 0 until renderer.pageCount) return@use

                        // Render the requested page first, then its neighbours.
                        val order = listOf(currentPage, currentPage + 1, currentPage - 1)
                        for (index in order) {
                            if (index !in 0 until renderer.pageCount) continue
                            if (GlobalPdfCache.get(fileUri, index) != null) {
                                if (index == currentPage) withContext(Dispatchers.Main) {
                                    visible = GlobalPdfCache.get(fileUri, index)
                                    error = null
                                }
                                continue
                            }
                            val bitmap = renderer.renderPage(index, targetWidth)
                            GlobalPdfCache.put(fileUri, index, bitmap)
                            if (index == currentPage) withContext(Dispatchers.Main) {
                                visible = bitmap
                                error = null
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { error = e.message ?: loadFailedMessage }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        visible?.takeIf { !it.isRecycled }?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.cd_page_number, currentPage + 1),
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(fileUri, currentPage) {
                        // No double-tap zoom on purpose: a double-tap detector has to hold
                        // back every single tap for ~300 ms to rule out a second one, which
                        // would make the parent's page-turn tap zones feel sluggish.
                        //
                        // Events are only consumed while actually zooming or panning, so a
                        // plain tap (and any tap at scale 1) still reaches the tap zones.
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressedPointers = event.changes.filter { it.pressed }
                                
                                if (pressedPointers.size >= 2) {
                                    val zoomDelta = event.calculateZoom()
                                    val panDelta = event.calculatePan()
                                    
                                    val oldScale = scale
                                    scale = (scale * zoomDelta).coerceIn(1f, 5f)
                                    
                                    val maxOffset = (scale - 1f) / 2f
                                    offsetXRatio = (offsetXRatio + panDelta.x / size.width).coerceIn(-maxOffset, maxOffset)
                                    offsetYRatio = (offsetYRatio + panDelta.y / size.height).coerceIn(-maxOffset, maxOffset)
                                    
                                    if (zoomDelta != 1f || panDelta != Offset.Zero || scale != oldScale) {
                                        onPageViewChange(PageView(scale, offsetXRatio, offsetYRatio))
                                        event.changes.forEach { it.consume() }
                                    }
                                } else if (pressedPointers.size == 1 && scale > 1.01f) {
                                    val panDelta = event.calculatePan()
                                    if (panDelta != Offset.Zero) {
                                        val maxOffset = (scale - 1f) / 2f
                                        offsetXRatio = (offsetXRatio + panDelta.x / size.width).coerceIn(-maxOffset, maxOffset)
                                        offsetYRatio = (offsetYRatio + panDelta.y / size.height).coerceIn(-maxOffset, maxOffset)
                                        
                                        onPageViewChange(PageView(scale, offsetXRatio, offsetYRatio))
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetXRatio * size.width
                        translationY = offsetYRatio * size.height
                    },
            )
        }

        if (visible == null && error == null) {
            CircularProgressIndicator()
        }

        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}

/**
 * Opens [fileUri] for reading, whether it is a content URI or a plain file path.
 *
 * The failure messages are passed in rather than resolved here: this runs off the main
 * thread, where there is no composable scope to read them from.
 */
private fun openRenderer(
    context: Context,
    fileUri: String,
    openFailedMessage: String,
    fileMissingMessage: String,
): ParcelFileDescriptor {
    val uri = fileUri.toUri()
    return if (uri.scheme == "content") {
        context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException(openFailedMessage)
    } else {
        val file = File(uri.path ?: "")
        if (!file.exists()) throw IllegalStateException(fileMissingMessage)
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }
}

/** Renders one page scaled to [targetWidth], preserving the aspect ratio. */
private fun PdfRenderer.renderPage(index: Int, targetWidth: Int): Bitmap =
    openPage(index).use { page ->
        val scale = targetWidth.toFloat() / page.width
        val height = (page.height * scale).toInt().coerceAtLeast(1)
        createBitmap(targetWidth, height).also { bitmap ->
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        }
    }

/**
 * Invisible component that pre-loads the first page of a PDF into the global cache.
 */
@Composable
fun PdfPreloader(fileUri: String) {
    val context = LocalContext.current
    val targetWidth = remember {
        val metrics = context.resources.displayMetrics
        (maxOf(metrics.widthPixels, metrics.heightPixels) * 1.5f).toInt().coerceAtMost(3000)
    }

    // Never surfaced here – a failed preload stays silent – but [openRenderer] runs off
    // the main thread and so cannot resolve them itself.
    val openFailedMessage = stringResource(R.string.error_file_open_failed)
    val fileMissingMessage = stringResource(R.string.error_file_missing)

    LaunchedEffect(fileUri) {
        if (fileUri.isEmpty() || GlobalPdfCache.get(fileUri, 0) != null) return@LaunchedEffect

        withContext(Dispatchers.IO) {
            try {
                openRenderer(context, fileUri, openFailedMessage, fileMissingMessage).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (renderer.pageCount > 0) {
                            val bitmap = renderer.renderPage(0, targetWidth)
                            GlobalPdfCache.put(fileUri, 0, bitmap)
                        }
                    }
                }
            } catch (_: Exception) {
                // Preload failure is silent
            }
        }
    }
}
