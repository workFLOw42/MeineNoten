package de.workflow42.meinenoten.ui.components

import android.annotation.SuppressLint
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import de.workflow42.meinenoten.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Renders a MusicXML file using OpenSheetMusicDisplay inside a WebView.
 *
 * Supports both plain MusicXML (.xml / .musicxml) and compressed MusicXML (.mxl),
 * which is a ZIP container holding the actual score.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MusicXmlView(
    fileUri: String,
    modifier: Modifier = Modifier,
    zoom: Float = 1.0f,
) {
    val context = LocalContext.current
    var xmlContent by remember(fileUri) { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var errorMessage by remember(fileUri) { mutableStateOf<String?>(null) }
    var isRendering by remember(fileUri) { mutableStateOf(value = true) }

    // Resolved up front: these are assigned from a background dispatcher and from the
    // WebView callback, neither of which is a composable scope.
    val readFailedMessage = stringResource(R.string.error_file_read_failed)
    val unknownErrorMessage = stringResource(R.string.error_unknown)
    val noScoreMessage = stringResource(R.string.error_no_score_in_mxl)

    LaunchedEffect(fileUri) {
        withContext(Dispatchers.IO) {
            try {
                val uri = fileUri.toUri()
                val bytes = if (uri.scheme == "content") {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } else {
                    File(uri.path ?: "").takeIf { it.exists() }?.readBytes()
                } ?: throw IllegalStateException(readFailedMessage)

                val text = if (isZip(bytes)) {
                    extractFromMxl(bytes, noScoreMessage)
                } else {
                    bytes.toString(Charsets.UTF_8)
                }
                withContext(Dispatchers.Main) { xmlContent = text }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = e.message ?: unknownErrorMessage
                    isRendering = false
                }
            }
        }
    }

    // Push the score into the WebView once both the content and the page are ready.
    LaunchedEffect(xmlContent, webView) {
        val content = xmlContent ?: return@LaunchedEffect
        val view = webView ?: return@LaunchedEffect
        val encoded = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        // decodeURIComponent(escape(atob(..))) restores UTF-8 characters correctly.
        view.evaluateJavascript(
            "loadMusicXML(decodeURIComponent(escape(atob('$encoded'))))",
            null,
        )
    }

    LaunchedEffect(zoom, webView) {
        webView?.evaluateJavascript("setZoom($zoom)", null)
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    // No network access needed - everything is bundled in assets.
                    settings.blockNetworkLoads = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false

                    addJavascriptInterface(
                        object {
                            // Called from JavaScript in assets/osmd.html – unused from
                            // Kotlin, which is why it must survive R8 (keep rule for
                            // @JavascriptInterface).
                            @Suppress("unused")
                            @JavascriptInterface
                            fun onStatus(type: String, message: String) {
                                post {
                                    when (type) {
                                        "error" -> {
                                            errorMessage = message
                                            isRendering = false
                                        }
                                        "rendered" -> {
                                            errorMessage = null
                                            isRendering = false
                                        }
                                        "loading" -> isRendering = true
                                    }
                                }
                            }
                        },
                        "AndroidOsmd"
                    )

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            super.onPageFinished(view, url)
                            webView = view
                        }
                    }
                    loadUrl("file:///android_asset/osmd/index.html")
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isRendering && (errorMessage == null)) {
            CircularProgressIndicator()
        }

        errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}

private fun isZip(bytes: ByteArray): Boolean =
    (bytes.size > 4) && (bytes[0] == 0x50.toByte()) && (bytes[1] == 0x4B.toByte())

/**
 * Extracts the score from a compressed MusicXML (.mxl) container.
 *
 * [notFoundMessage] is passed in because this runs off the main thread with no composable
 * scope to resolve it from.
 */
private fun extractFromMxl(bytes: ByteArray, notFoundMessage: String): String {
    ZipInputStream(bytes.inputStream()).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            val name = entry.name
            val isScore = !entry.isDirectory &&
                    !name.startsWith("META-INF") &&
                    (name.endsWith(".xml", ignoreCase = true) ||
                            name.endsWith(".musicxml", ignoreCase = true))
            if (isScore) return zip.readBytes().toString(Charsets.UTF_8)
            entry = zip.nextEntry
        }
    }
    throw IllegalStateException(notFoundMessage)
}
