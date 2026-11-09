package com.example

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.example.ui.pdf.PdfViewerScreen
import com.example.ui.pdf.LiquidGlassButton
import com.example.ui.offline.OfflineHubScreen
import com.example.util.NetworkMonitor
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import com.example.util.FileImportHelper
import com.example.util.DevicePerformanceProfile
import com.example.ui.pdf.DocxToPdfConverter
import com.example.ui.pdf.LiquidGlassOrbButton
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SwipeVertical
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private var webViewInstance: WebView? = null
    private var incomingPdfUri by mutableStateOf<Uri?>(null)
    private var incomingPdfName by mutableStateOf<String?>(null)
    private var incomingWebUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        handleIntent(intent)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                WebViewScreen(
                    url = "https://study-verse-lime.vercel.app/",
                    savedState = savedInstanceState,
                    initialPdfUri = incomingPdfUri,
                    initialPdfTitle = incomingPdfName,
                    initialWebUrl = incomingWebUrl,
                    onClearIncomingPdf = {
                        incomingPdfUri = null
                        incomingPdfName = null
                    },
                    onClearIncomingWebUrl = {
                        incomingWebUrl = null
                    },
                    onWebViewCreated = { webViewInstance = it }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        try {
            // 1. Handle URL shared from Chrome ("Share -> StudyVerse") so any key-activation or study link opens inside the app
            if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/plain", ignoreCase = true) == true) {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                val extractedUrl = Regex("https?://[^\\s\"'<>]+", RegexOption.IGNORE_CASE).find(sharedText)?.value
                if (!extractedUrl.isNullOrBlank()) {
                    if (isDirectStudyDocumentUrl(extractedUrl)) {
                        incomingPdfUri = Uri.parse(extractedUrl)
                        incomingPdfName = URLUtil.guessFileName(extractedUrl, null, "application/pdf")
                    } else {
                        incomingWebUrl = extractedUrl
                    }
                    return
                }
            }

            // 2. Handle direct HTTP/HTTPS deep links (e.g. StudyLime key activation callback)
            val dataUri = intent.data
            val scheme = dataUri?.scheme?.lowercase()
            val mime = intent.type?.lowercase() ?: ""
            if (intent.action == Intent.ACTION_VIEW && dataUri != null && (scheme == "http" || scheme == "https")) {
                val rawWebUrl = dataUri.toString()
                val isPdfOrDocMime = mime.contains("pdf") || mime.contains("word") || mime.contains("officedocument")
                if (!isPdfOrDocMime && !isDirectStudyDocumentUrl(rawWebUrl)) {
                    incomingWebUrl = rawWebUrl
                    return
                }
            }

            val uri = FileImportHelper.extractUriFromIntent(intent) ?: return

            // Take persistable permission if available
            try {
                val takeFlags = intent.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                if (takeFlags != 0) {
                    contentResolver.takePersistableUriPermission(uri, takeFlags)
                }
            } catch (_: Throwable) {
                // Non-persistable URIs will throw, safe to ignore
            }

            incomingPdfUri = uri
            incomingPdfName = FileImportHelper.resolveDisplayName(this, uri)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        try {
            webViewInstance?.saveState(outState)
        } catch (_: Throwable) {}
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        try {
            webViewInstance?.restoreState(savedInstanceState)
        } catch (_: Throwable) {}
    }

    override fun onPause() {
        try {
            webViewInstance?.onPause()
        } catch (_: Throwable) {}
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        try {
            webViewInstance?.onResume()
        } catch (_: Throwable) {}
    }

    override fun onDestroy() {
        webViewInstance = null
        super.onDestroy()
    }
}

fun setupStudyVerseMediaSession(context: Context): MediaSession? {
    return try {
        val bitmap = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        MediaSession(context, "StudyVerseMedia").apply {
            val metadataBuilder = MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, "StudyVerse")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "StudyVerse Learning Platform")
                .putString(MediaMetadata.METADATA_KEY_ALBUM, "StudyVerse Audio")
            if (bitmap != null) {
                metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap)
                metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, bitmap)
            }
            setMetadata(metadataBuilder.build())
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(
                        PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE
                    )
                    .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                    .build()
            )
            isActive = true
        }
    } catch (t: Throwable) {
        t.printStackTrace()
        null
    }
}

// Outside composable, or just define it as a class accepting a lambda
class WebAppInterface(
    private val onColorReceived: (String) -> Unit,
    private val onSettingsUpdateReceived: (String) -> Unit = {},
    private val onChunkLoadErrorReceived: (String) -> Unit = {},
    private val onBlankContentReceived: () -> Unit = {},
    private val onScrollStateChanged: (Boolean) -> Unit = {},
    private val onScrollDirectionChanged: (Boolean) -> Unit = {}
) {
    @android.webkit.JavascriptInterface
    fun postThemeColor(colorString: String) {
        onColorReceived(colorString)
    }

    @android.webkit.JavascriptInterface
    fun updateSettings(settingsJson: String) {
        onSettingsUpdateReceived(settingsJson)
    }

    @android.webkit.JavascriptInterface
    fun onChunkLoadError(errorDetails: String) {
        onChunkLoadErrorReceived(errorDetails)
    }

    @android.webkit.JavascriptInterface
    fun onBlankContent() {
        onBlankContentReceived()
    }

    @android.webkit.JavascriptInterface
    fun reportScrollState(scrolledDown: Boolean) {
        onScrollStateChanged(scrolledDown)
    }

    @android.webkit.JavascriptInterface
    fun reportScrollDirection(scrollingDown: Boolean) {
        onScrollDirectionChanged(scrollingDown)
    }
}

class WebScrollGuardState {
    @Volatile var isWebPageScrolledDown: Boolean = false
    @Volatile var touchStartedAtTop: Boolean = true
    @Volatile var isFirstSite: Boolean = true
    @Volatile var isTouchActive: Boolean = false
    @Volatile var lastScrollWasUp: Boolean = false
    @Volatile var lastScrollEventTime: Long = 0L
    @Volatile var settleCallbackScheduled: Boolean = false
}

private fun isRunningOnVirtualizedEmulator(): Boolean {
    val fp = android.os.Build.FINGERPRINT.lowercase()
    val model = android.os.Build.MODEL.lowercase()
    val hw = android.os.Build.HARDWARE.lowercase()
    val prod = android.os.Build.PRODUCT.lowercase()
    val board = android.os.Build.BOARD.lowercase()
    return fp.startsWith("generic") ||
            fp.contains("vbox") ||
            fp.contains("emulator") ||
            model.contains("google_sdk") ||
            model.contains("emulator") ||
            model.contains("sdk_gphone") ||
            model.contains("android sdk built for") ||
            hw.contains("goldfish") ||
            hw.contains("ranchu") ||
            hw.contains("cutf_cvm") ||
            hw.contains("vsoc") ||
            prod.contains("sdk") ||
            prod.contains("emulator") ||
            prod.contains("simulator") ||
            prod.startsWith("cf_") ||
            board.contains("goldfish") ||
            board.contains("cutf")
}

private fun isDirectStudyDocumentUrl(rawUrl: String): Boolean {
    val clean = rawUrl.lowercase().trim()
    val pathOnly = clean.substringBefore("?").substringBefore("#")
    if (pathOnly.endsWith(".pdf") || pathOnly.endsWith(".docx") || pathOnly.endsWith(".doc")) {
        return true
    }
    if ((clean.contains(".pdf") || clean.contains(".docx")) && !clean.contains(".html") && !clean.contains(".htm")) {
        return true
    }
    // Direct Google Drive / Google Docs single-document links convert cleanly to PDF in FileImportHelper
    if (clean.contains("drive.google.com/file/d/") ||
        clean.contains("drive.google.com/uc?") ||
        clean.contains("docs.google.com/document/d/")
    ) {
        return true
    }
    return false
}

/**
 * Unwraps Chrome redirect links (`intent://...#Intent;scheme=https;package=com.android.chrome;end`,
 * `googlechrome://navigate?url=...`, `googlechromes://...`) into standard `https://` or `http://` URLs
 * so ad-key generators and study shorteners stay inside the app's WebView session and auto-activate keys in-app.
 * Returns null if the link is for a non-browser native app (e.g., Telegram `tg://`, WhatsApp `whatsapp://`, UPI `upi://`).
 */
private fun resolveWebUrlFromIntentOrCustomScheme(rawUrl: String): String? {
    val trimmed = rawUrl.trim()
    val lower = trimmed.lowercase()

    if (lower.startsWith("googlechrome://navigate?url=")) {
        val extracted = trimmed.substring("googlechrome://navigate?url=".length)
        val decoded = try { java.net.URLDecoder.decode(extracted, "UTF-8") } catch (_: Throwable) { extracted }
        if (decoded.startsWith("http://", ignoreCase = true) || decoded.startsWith("https://", ignoreCase = true)) {
            return decoded
        }
    }
    if (lower.startsWith("googlechromes://")) {
        val rest = trimmed.substring("googlechromes://".length).removePrefix("navigate?url=")
        return if (rest.startsWith("http://", ignoreCase = true) || rest.startsWith("https://", ignoreCase = true)) rest else "https://$rest"
    }
    if (lower.startsWith("googlechrome://")) {
        val rest = trimmed.substring("googlechrome://".length).removePrefix("navigate?url=")
        if (rest.isNotBlank()) {
            return if (rest.startsWith("http://", ignoreCase = true) || rest.startsWith("https://", ignoreCase = true)) rest else "https://$rest"
        }
    }

    if (lower.startsWith("intent://") || lower.startsWith("intent:")) {
        try {
            val parsedIntent = Intent.parseUri(trimmed, Intent.URI_INTENT_SCHEME)
            val dataStr = parsedIntent.dataString
            if (!dataStr.isNullOrBlank()) {
                if (dataStr.startsWith("http://", ignoreCase = true) || dataStr.startsWith("https://", ignoreCase = true)) {
                    return dataStr
                }
                if (dataStr.startsWith("googlechrome://", ignoreCase = true) || dataStr.startsWith("googlechromes://", ignoreCase = true)) {
                    resolveWebUrlFromIntentOrCustomScheme(dataStr)?.let { return it }
                }
            }
            val fallbackUrl = parsedIntent.getStringExtra("browser_fallback_url")
                ?: parsedIntent.getStringExtra("S.browser_fallback_url")
            if (!fallbackUrl.isNullOrBlank() &&
                (fallbackUrl.startsWith("http://", ignoreCase = true) || fallbackUrl.startsWith("https://", ignoreCase = true))
            ) {
                return fallbackUrl
            }
        } catch (_: Throwable) {
        }

        // Regex fallback for malformed intent:// strings (e.g. intent://https://domain.com/...#Intent...)
        val bodyBeforeHash = trimmed.substringAfter("intent://").substringBefore("#Intent")
        val intentParams = trimmed.substringAfter("#Intent;", "")
        if (bodyBeforeHash.startsWith("http://", ignoreCase = true) || bodyBeforeHash.startsWith("https://", ignoreCase = true)) {
            return bodyBeforeHash
        }
        val schemeMatch = Regex("scheme=([a-zA-Z0-9+.-]+)").find(intentParams)?.groupValues?.getOrNull(1)?.lowercase()
        if ((schemeMatch == "https" || schemeMatch == "http") && bodyBeforeHash.isNotBlank()) {
            return "$schemeMatch://$bodyBeforeHash"
        }
        val fallbackMatch = Regex("S\\.browser_fallback_url=([^;]+)").find(intentParams)?.groupValues?.getOrNull(1)
        if (!fallbackMatch.isNullOrBlank()) {
            val decoded = try { java.net.URLDecoder.decode(fallbackMatch, "UTF-8") } catch (_: Throwable) { fallbackMatch }
            if (decoded.startsWith("http://", ignoreCase = true) || decoded.startsWith("https://", ignoreCase = true)) {
                return decoded
            }
        }
    }

    return null
}

private fun getPhoneNativeChromeUserAgent(context: Context): String {
    return try {
        val rawDefaultUa = WebSettings.getDefaultUserAgent(context) ?: ""
        if (rawDefaultUa.isNotBlank()) {
            rawDefaultUa
                .replace("; wv)", ")")
                .replace("; wv;", ";")
                .replace("Version/4.0 ", "")
                .trim()
        } else {
            val release = android.os.Build.VERSION.RELEASE ?: "14"
            val model = android.os.Build.MODEL ?: "K"
            "Mozilla/5.0 (Linux; Android $release; $model) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Mobile Safari/537.36"
        }
    } catch (_: Throwable) {
        val release = android.os.Build.VERSION.RELEASE ?: "14"
        val model = android.os.Build.MODEL ?: "K"
        "Mozilla/5.0 (Linux; Android $release; $model) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Mobile Safari/537.36"
    }
}

private fun openUrlInExternalChrome(context: Context, targetUrl: String?) {
    val cleanUrl = targetUrl?.trim()?.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
        ?: "https://study-verse-lime.vercel.app/"
    try {
        val chromeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
            setPackage("com.android.chrome")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chromeIntent)
    } catch (_: Throwable) {
        try {
            val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
        } catch (e: Throwable) {
            Toast.makeText(context, "Unable to open external browser", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun WebViewScreen(
    url: String,
    savedState: Bundle? = null,
    initialPdfUri: Uri? = null,
    initialPdfTitle: String? = null,
    initialWebUrl: String? = null,
    onClearIncomingPdf: () -> Unit = {},
    onClearIncomingWebUrl: () -> Unit = {},
    onWebViewCreated: (WebView?) -> Unit = {}
) {
    val context = LocalContext.current
    val studyPrefs = remember { context.getSharedPreferences("StudyVersePrefs", Context.MODE_PRIVATE) }

    var webView: WebView? by remember { mutableStateOf(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var hasCompletedInitialBoot by remember { mutableStateOf(savedState != null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var currentLoadedUrl by remember { mutableStateOf(url) }
    var isPullToRefreshRuleEnabled by remember { mutableStateOf(true) }

    // User-controlled Swipe to Refresh Switch (OFF by default so scrolling up never accidentally reloads & wipes content)
    var userSwipeToRefreshEnabled by remember {
        mutableStateOf(studyPrefs.getBoolean("user_swipe_to_refresh_enabled", false))
    }
    // Non-recomposing scroll state holder so 120fps scrolling never triggers Compose recomposition
    val scrollGuard = remember { WebScrollGuardState() }
    // Movable Web Browsing Ball state & menu state on WebView screen
    var isWebOrbMenuOpen by remember { mutableStateOf(false) }
    var webContainerSize by remember { mutableStateOf(IntSize.Zero) }
    var webBallOffsetX by remember { mutableFloatStateOf(-1f) }
    var webBallOffsetY by remember { mutableFloatStateOf(-1f) }
    var webMenuSize by remember { mutableStateOf(IntSize.Zero) }

    // Fullscreen domains controllable live via Firebase Remote Config ("webview_fullscreen_domains")
    var fullscreenDomainsCsv by remember {
        mutableStateOf(
            studyPrefs.getString("rc_fullscreen_domains", "study-verse-lime.vercel.app,study-verse-lime,studylime")
                ?: "study-verse-lime.vercel.app,study-verse-lime,studylime"
        )
    }
    var configRevision by remember { mutableIntStateOf(0) }

    // Only our main StudyLime site (or domains listed in webview_fullscreen_domains) loads in fullscreen and hides the Web Floating Ball;
    // external websites open smoothly in standard browser mode (status + nav bar colored by site theme).
    val isFirstStudyLimeSite = remember(currentLoadedUrl, fullscreenDomainsCsv) {
        val lower = currentLoadedUrl.lowercase().trim()
        if (lower.isBlank() || lower == "about:blank") {
            true
        } else {
            val tokens = fullscreenDomainsCsv.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            tokens.any { lower.contains(it) } ||
                lower.contains("study-verse-lime.vercel.app") ||
                lower.contains("study-verse-lime") ||
                lower.contains("studylime")
        }
    }

    fun syncMainFrameUrl(wv: WebView?) {
        val actualMainUrl = wv?.url?.takeIf { it.isNotBlank() && it != "about:blank" } ?: return
        if (currentLoadedUrl != actualMainUrl) {
            currentLoadedUrl = actualMainUrl
        }
    }

    LaunchedEffect(isFirstStudyLimeSite) {
        scrollGuard.isFirstSite = isFirstStudyLimeSite
        if (isFirstStudyLimeSite) {
            isWebOrbMenuOpen = false
        }
    }

    // Inbuilt PDF Opener State
    var activePdfSource by remember { mutableStateOf<String?>(null) }
    var activePdfTitle by remember { mutableStateOf<String?>(null) }

    // On-Demand Power Management: Hide & Pause WebView rendering while PDF Reader is open so RenderThread never draws stale mailboxes
    LaunchedEffect(activePdfSource != null, webView) {
        val wv = webView ?: return@LaunchedEffect
        try {
            if (activePdfSource != null) {
                wv.visibility = View.INVISIBLE
                (wv.parent as? View)?.visibility = View.INVISIBLE
                wv.onPause()
            } else {
                wv.onResume()
                (wv.parent as? View)?.visibility = View.VISIBLE
                wv.visibility = View.VISIBLE
                syncMainFrameUrl(wv)
            }
        } catch (_: Throwable) {
        }
    }

    // Handle incoming PDF from external apps / file manager / downloads
    LaunchedEffect(initialPdfUri) {
        if (initialPdfUri != null) {
            val displayName = initialPdfTitle ?: FileImportHelper.resolveDisplayName(context, initialPdfUri)
            val path = if (initialPdfUri.scheme == "file") {
                initialPdfUri.path ?: initialPdfUri.toString().removePrefix("file://")
            } else {
                initialPdfUri.toString()
            }
            activePdfSource = path
            activePdfTitle = displayName
            onClearIncomingPdf()
        }
    }

    // Handle incoming web deep-links (e.g., key activation redirects from external Chrome returning to StudyLime)
    LaunchedEffect(initialWebUrl, webView) {
        val targetWebUrl = initialWebUrl
        val wv = webView
        if (!targetWebUrl.isNullOrBlank() && wv != null) {
            activePdfSource = null
            activePdfTitle = null
            currentLoadedUrl = targetWebUrl
            wv.loadUrl(targetWebUrl)
            onClearIncomingWebUrl()
        }
    }

    // Automatically load downloaded study materials as soon as DownloadManager finishes
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                    if (id != -1L) {
                        try {
                            val dm = ctx?.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                            val query = DownloadManager.Query().setFilterById(id)
                            dm?.query(query)?.use { c ->
                                if (c.moveToFirst()) {
                                    val statusIdx = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
                                    if (statusIdx != -1 && c.getInt(statusIdx) == DownloadManager.STATUS_SUCCESSFUL) {
                                        val uriIdx = c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                                        val titleIdx = c.getColumnIndex(DownloadManager.COLUMN_TITLE)
                                        val localUri = if (uriIdx != -1) c.getString(uriIdx) else null
                                        val fileTitle = if (titleIdx != -1) c.getString(titleIdx)?.removePrefix("StudyVerse: ") else null
                                        if (!localUri.isNullOrEmpty()) {
                                            val uri = Uri.parse(localUri)
                                            val name = fileTitle ?: FileImportHelper.resolveDisplayName(context, uri)
                                            if (name.endsWith(".pdf", ignoreCase = true) || DocxToPdfConverter.isWordOrOfficeDocument(name, null)) {
                                                if (activePdfSource == null) {
                                                    activePdfSource = uri.toString()
                                                    activePdfTitle = name
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Site redeployment / white screen cache-busting recovery
    var lastSiteUpdateRecovery by remember { mutableStateOf(0L) }
    
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    var fileChooserCallback by remember { mutableStateOf<android.webkit.ValueCallback<Array<android.net.Uri>>?>(null) }
    val fileChooserLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val uris = if (data?.data != null) {
                arrayOf(data.data!!)
            } else if (data?.clipData != null) {
                val clipData = data.clipData!!
                Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
            } else {
                null
            }
            fileChooserCallback?.onReceiveValue(uris)
        } else {
            fileChooserCallback?.onReceiveValue(null)
        }
        fileChooserCallback = null
    }

    val coroutineScope = rememberCoroutineScope()
    val isOnline by NetworkMonitor.observeNetwork(context).collectAsState(initial = NetworkMonitor.isOnline(context))

    fun triggerSiteUpdateRecovery(reason: String) {
        if (activePdfSource != null) return
        val now = System.currentTimeMillis()
        if (now - lastSiteUpdateRecovery > 7000) {
            lastSiteUpdateRecovery = now
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                Toast.makeText(context, "StudyVerse updated! Refreshing latest version...", Toast.LENGTH_SHORT).show()
                webView?.clearCache(true)
                webView?.settings?.cacheMode = WebSettings.LOAD_NO_CACHE
                webView?.reload()
                webView?.postDelayed({
                    webView?.settings?.cacheMode = if (isOnline) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_CACHE_ELSE_NETWORK
                }, 4000)
            }
        }
    }

    // Firebase RemoteConfig Live Browser Control Engine:
    // Applies R.xml.remote_config_defaults, fetches latest config from Firebase, and listens for real-time updates
    // via addOnConfigUpdateListener so publishing any change in Firebase Console updates the browser immediately!
    DisposableEffect(webView, hasCompletedInitialBoot) {
        var configListenerRegistration: com.google.firebase.remoteconfig.ConfigUpdateListenerRegistration? = null
        val job = if (webView != null && hasCompletedInitialBoot) {
            coroutineScope.launch {
                kotlinx.coroutines.delay(4_000L)
                try {
                    val remoteConfig = Firebase.remoteConfig
                    val configSettings = remoteConfigSettings {
                        minimumFetchIntervalInSeconds = 60
                    }
                    remoteConfig.setConfigSettingsAsync(configSettings)
                    remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)

                    fun applyRemoteConfigToPrefs() {
                        try {
                            val editor = studyPrefs.edit()
                            editor.putBoolean("rc_keep_intent_in_app", remoteConfig.getBoolean("webview_keep_intent_in_app"))
                            editor.putBoolean("rc_block_external_redirects", remoteConfig.getBoolean("webview_block_external_redirects"))
                            editor.putBoolean("rc_absorb_ad_popups", remoteConfig.getBoolean("webview_absorb_ad_popups"))
                            editor.putBoolean("rc_spoof_chrome_client_hints", remoteConfig.getBoolean("webview_spoof_chrome_client_hints"))
                            editor.putBoolean("rc_desktop_mode", remoteConfig.getBoolean("webview_desktop_mode"))
                            editor.putBoolean("rc_zoom_enabled", remoteConfig.getBoolean("webview_zoom_enabled"))
                            editor.putBoolean("rc_pull_to_refresh", remoteConfig.getBoolean("webview_pull_to_refresh"))
                            editor.putBoolean("rc_js_enabled", remoteConfig.getBoolean("webview_java_script_enabled"))
                            editor.putBoolean("rc_dom_storage", remoteConfig.getBoolean("webview_dom_storage_enabled"))
                            editor.putBoolean("rc_third_party_cookies", remoteConfig.getBoolean("webview_third_party_cookies"))
                            editor.putBoolean("rc_autoplay_media", remoteConfig.getBoolean("webview_autoplay_media"))
                            editor.putBoolean("rc_hardware_accel", remoteConfig.getBoolean("webview_hardware_accel"))
                            editor.putBoolean("rc_mixed_content", remoteConfig.getBoolean("webview_mixed_content"))
                            val remoteUa = remoteConfig.getString("webview_user_agent").trim()
                            val cleanRemoteUa = if (remoteUa.contains("Pixel 9 Pro Build/AP3A.241005.015")) "" else remoteUa
                            editor.putString("rc_user_agent", cleanRemoteUa)
                            val fsDomains = remoteConfig.getString("webview_fullscreen_domains")
                            if (fsDomains.isNotBlank()) {
                                editor.putString("rc_fullscreen_domains", fsDomains)
                                fullscreenDomainsCsv = fsDomains
                            }
                            editor.putString("rc_open_in_chrome_domains", remoteConfig.getString("webview_open_in_chrome_domains"))
                            editor.putString("rc_blocked_ad_domains", remoteConfig.getString("webview_blocked_ad_domains"))
                            editor.putString("rc_custom_js_inject", remoteConfig.getString("webview_custom_js_inject"))

                            val remoteDomainRules = remoteConfig.getString("domain_rules")
                            if (remoteDomainRules.isNotBlank() && remoteDomainRules.trim().startsWith("{") && remoteDomainRules.trim() != "{}") {
                                editor.putString("domain_rules", remoteDomainRules)
                            }
                            editor.apply()
                            configRevision++
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            applyRemoteConfigToPrefs()
                        }
                    }

                    configListenerRegistration = remoteConfig.addOnConfigUpdateListener(object : ConfigUpdateListener {
                        override fun onUpdate(configUpdate: ConfigUpdate) {
                            remoteConfig.activate().addOnCompleteListener {
                                applyRemoteConfigToPrefs()
                            }
                        }

                        override fun onError(error: FirebaseRemoteConfigException) {
                        }
                    })
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else null

        onDispose {
            job?.cancel()
            try {
                configListenerRegistration?.remove()
            } catch (_: Throwable) {}
        }
    }

    DisposableEffect(context, hasCompletedInitialBoot) {
        var mediaSession: MediaSession? = null
        val job = if (hasCompletedInitialBoot) {
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                kotlinx.coroutines.delay(6_000L)
                val created = setupStudyVerseMediaSession(context)
                mediaSession = created
            }
        } else null
        onDispose {
            job?.cancel()
            try {
                mediaSession?.isActive = false
                mediaSession?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Strictly load ONLY our main site (study-verse-lime.vercel.app) in fullscreen;
    // all external websites use visible status bar and navigation bar colored by site theme.
    val isInitialLink = isFirstStudyLimeSite

    LaunchedEffect(isOnline) {
        webView?.settings?.cacheMode = if (isOnline) {
            WebSettings.LOAD_DEFAULT
        } else {
            WebSettings.LOAD_CACHE_ELSE_NETWORK
        }
        if (isOnline && hasError) {
            hasError = false
            isLoading = true
            webView?.reload()
        }
    }

    // Handle system back button for PDF viewer OR Web Orb menu OR custom view (full screen video) OR step-by-step internal WebView navigation
    BackHandler(enabled = activePdfSource != null || isWebOrbMenuOpen || customView != null || canGoBack) {
        if (activePdfSource != null) {
            activePdfSource = null
            activePdfTitle = null
        } else if (isWebOrbMenuOpen) {
            isWebOrbMenuOpen = false
        } else if (customView != null) {
            customViewCallback?.onCustomViewHidden()
            customView = null
            customViewCallback = null
            (context as? Activity)?.let { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                val windowInsetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        } else if (webView?.canGoBack() == true) {
            val wv = webView
            // Check back-forward history target immediately so returning to StudyLime restores fullscreen without delay
            try {
                val history = wv?.copyBackForwardList()
                if (history != null && history.currentIndex > 0) {
                    val prevUrl = history.getItemAtIndex(history.currentIndex - 1)?.url
                    if (!prevUrl.isNullOrBlank() && prevUrl != "about:blank") {
                        currentLoadedUrl = prevUrl
                    }
                }
            } catch (_: Throwable) {}
            wv?.goBack()
            canGoBack = wv?.canGoBack() == true
            canGoForward = wv?.canGoForward() == true
            wv?.postDelayed({ syncMainFrameUrl(wv) }, 60)
        }
    }

    // Root container with dynamically updated background
    var themeColor by remember { mutableIntStateOf(android.graphics.Color.parseColor("#090D16")) }
    val isPdfOrCustomOverlay = activePdfSource != null || customView != null

    // Update WindowInsets & SystemBar colors ONLY when fullscreen mode or themeColor actually changes (zero per-frame IPC lag)
    LaunchedEffect(isInitialLink, isPdfOrCustomOverlay, themeColor) {
        (context as? Activity)?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                window.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
                window.isStatusBarContrastEnforced = false
            }
            val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isPdfOrCustomOverlay) {
                windowInsetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            }
            @Suppress("DEPRECATION")
            if (isInitialLink || isPdfOrCustomOverlay) {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
            } else {
                window.statusBarColor = themeColor
                window.navigationBarColor = themeColor
            }
            // Dark theme (isLight == false) -> status bar text/icons are WHITE (isAppearanceLightStatusBars = false)
            // Light theme (isLight == true) -> status bar text/icons are DARK (isAppearanceLightStatusBars = true)
            val isLight = (android.graphics.Color.red(themeColor) * 0.299 +
                    android.graphics.Color.green(themeColor) * 0.587 +
                    android.graphics.Color.blue(themeColor) * 0.114) > 160
            windowInsetsController.isAppearanceLightStatusBars = isLight
            windowInsetsController.isAppearanceLightNavigationBars = isLight
        }
    }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    webContainerSize = size
                }
        ) {
            val isEmulatorDevice = remember { isRunningOnVirtualizedEmulator() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(themeColor))
                    .then(
                        if (isInitialLink) Modifier else Modifier.windowInsetsPadding(WindowInsets.systemBars)
                    )
            ) {
                AndroidView(
                factory = { ctx ->
                    val density = ctx.resources.displayMetrics.density
                    val defaultLayerType = if (isEmulatorDevice) View.LAYER_TYPE_SOFTWARE else View.LAYER_TYPE_NONE
                    val phoneNativeChromeUa = getPhoneNativeChromeUserAgent(ctx)
                    var lastAppliedSettingsKey = ""
                    var cachedRulesJsonStr = studyPrefs.getString("domain_rules", "{}") ?: "{}"
                    var cachedRulesJson = try { org.json.JSONObject(cachedRulesJsonStr) } catch (_: Throwable) { org.json.JSONObject() }

                    fun extractBaseDomain(host: String): String {
                        val parts = host.lowercase().trim().removePrefix("www.").split(".")
                        return if (parts.size >= 2) parts.takeLast(2).joinToString(".") else host.lowercase()
                    }

                    fun isBlockedAdDomain(targetUrl: String?): Boolean {
                        if (targetUrl.isNullOrBlank()) return false
                        return try {
                            val host = (android.net.Uri.parse(targetUrl).host ?: "").lowercase()
                            if (host.isBlank()) return false
                            val blockedCsv = studyPrefs.getString("rc_blocked_ad_domains", "") ?: ""
                            if (blockedCsv.isBlank()) return false
                            blockedCsv.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.any { host.contains(it) }
                        } catch (_: Throwable) {
                            false
                        }
                    }

                    fun shouldKeepIntentInAppForUrl(targetUrl: String?): Boolean {
                        val defaultKeep = studyPrefs.getBoolean("rc_keep_intent_in_app", true) &&
                            studyPrefs.getBoolean("rc_block_external_redirects", true)
                        if (targetUrl.isNullOrBlank()) return defaultKeep
                        return try {
                            val host = android.net.Uri.parse(targetUrl).host ?: ""
                            var keepInApp = defaultKeep
                            if (cachedRulesJson.has("default")) {
                                val def = cachedRulesJson.getJSONObject("default")
                                if (def.has("keepIntentInApp")) keepInApp = def.getBoolean("keepIntentInApp")
                            }
                            if (cachedRulesJson.has("domains")) {
                                val domains = cachedRulesJson.getJSONObject("domains")
                                val keys = domains.keys()
                                while (keys.hasNext()) {
                                    val key = keys.next()
                                    if (host.contains(key, ignoreCase = true)) {
                                        val d = domains.getJSONObject(key)
                                        if (d.has("keepIntentInApp")) keepInApp = d.getBoolean("keepIntentInApp")
                                        break
                                    }
                                }
                            }
                            keepInApp
                        } catch (_: Throwable) {
                            defaultKeep
                        }
                    }

                    fun shouldAutoOpenInExternalChrome(targetUrl: String?): Boolean {
                        if (targetUrl.isNullOrBlank()) return false
                        return try {
                            val host = (android.net.Uri.parse(targetUrl).host ?: "").lowercase()
                            val chromeDomainsCsv = studyPrefs.getString("rc_open_in_chrome_domains", "") ?: ""
                            if (chromeDomainsCsv.isNotBlank()) {
                                val matchesCsv = chromeDomainsCsv.split(",")
                                    .map { it.trim().lowercase() }
                                    .filter { it.isNotEmpty() }
                                    .any { host.contains(it) }
                                if (matchesCsv) return true
                            }
                            var openExternal = false
                            if (cachedRulesJson.has("default")) {
                                val def = cachedRulesJson.getJSONObject("default")
                                if (def.has("openInChrome")) openExternal = def.getBoolean("openInChrome")
                            }
                            if (cachedRulesJson.has("domains")) {
                                val domains = cachedRulesJson.getJSONObject("domains")
                                val keys = domains.keys()
                                while (keys.hasNext()) {
                                    val key = keys.next()
                                    if (host.contains(key, ignoreCase = true)) {
                                        val d = domains.getJSONObject(key)
                                        if (d.has("openInChrome")) openExternal = d.getBoolean("openInChrome")
                                        break
                                    }
                                }
                            }
                            openExternal
                        } catch (_: Throwable) {
                            false
                        }
                    }

                    val swipeRefreshLayout = SwipeRefreshLayout(ctx).apply {
                        setColorSchemeColors(
                            android.graphics.Color.parseColor("#0288D1"),
                            android.graphics.Color.parseColor("#00BCD4")
                        )
                        setProgressBackgroundColorSchemeColor(android.graphics.Color.parseColor("#1E293B"))
                        // Require a deliberate pull distance (140dp) to prevent accidental trigger on upward scrolls
                        setDistanceToTriggerSync((140 * density).toInt())
                        setSlingshotDistance((160 * density).toInt())
                    }

                    fun applyDynamicSettings(targetUrl: String?, webView: WebView, forceUpdate: Boolean = false) {
                        if (targetUrl == null) return
                        try {
                            if (forceUpdate) {
                                cachedRulesJsonStr = studyPrefs.getString("domain_rules", "{}") ?: "{}"
                                cachedRulesJson = try { org.json.JSONObject(cachedRulesJsonStr) } catch (_: Throwable) { org.json.JSONObject() }
                            }
                            val host = android.net.Uri.parse(targetUrl).host ?: ""
                            val rcRev = studyPrefs.getString("rc_user_agent", "") ?: ""
                            val settingsKey = "$host|$cachedRulesJsonStr|$rcRev|${studyPrefs.getBoolean("rc_desktop_mode", false)}"

                            val rulesJson = cachedRulesJson
                            
                            var zoom = studyPrefs.getBoolean("rc_zoom_enabled", true)
                            var pullToRefresh = studyPrefs.getBoolean("rc_pull_to_refresh", true)
                            var desktop = studyPrefs.getBoolean("rc_desktop_mode", false)
                            var jsEnabled = studyPrefs.getBoolean("rc_js_enabled", true)
                            var domStorage = studyPrefs.getBoolean("rc_dom_storage", true)
                            var allowAutoPlay = studyPrefs.getBoolean("rc_autoplay_media", true)
                            var hardwareAccel = studyPrefs.getBoolean("rc_hardware_accel", true)
                            var allowMixedContent = studyPrefs.getBoolean("rc_mixed_content", true)
                            var thirdPartyCookies = studyPrefs.getBoolean("rc_third_party_cookies", true)
                            var customUaOverride: String? = studyPrefs.getString("rc_user_agent", null)
                                ?.takeIf { it.isNotBlank() && !it.contains("Pixel 9 Pro Build/AP3A.241005.015") }
                            
                            // Check defaults in domain_rules JSON
                            if (rulesJson.has("default")) {
                                val def = rulesJson.getJSONObject("default")
                                if (def.has("zoomEnabled")) zoom = def.getBoolean("zoomEnabled")
                                if (def.has("pullToRefresh")) pullToRefresh = def.getBoolean("pullToRefresh")
                                if (def.has("desktopMode")) desktop = def.getBoolean("desktopMode")
                                if (def.has("jsEnabled")) jsEnabled = def.getBoolean("jsEnabled")
                                if (def.has("domStorage")) domStorage = def.getBoolean("domStorage")
                                if (def.has("allowAutoPlay")) allowAutoPlay = def.getBoolean("allowAutoPlay")
                                if (def.has("hardwareAccel")) hardwareAccel = def.getBoolean("hardwareAccel")
                                if (def.has("allowMixedContent")) allowMixedContent = def.getBoolean("allowMixedContent")
                                if (def.has("thirdPartyCookies")) thirdPartyCookies = def.getBoolean("thirdPartyCookies")
                                if (def.has("userAgent")) customUaOverride = def.getString("userAgent").takeIf { it.isNotBlank() }
                            }
                            
                            // Check specific domains in domain_rules JSON
                            if (rulesJson.has("domains")) {
                                val domains = rulesJson.getJSONObject("domains")
                                val keys = domains.keys()
                                while (keys.hasNext()) {
                                    val key = keys.next()
                                    if (host.contains(key, ignoreCase = true)) {
                                        val domainRules = domains.getJSONObject(key)
                                        if (domainRules.has("zoomEnabled")) zoom = domainRules.getBoolean("zoomEnabled")
                                        if (domainRules.has("pullToRefresh")) pullToRefresh = domainRules.getBoolean("pullToRefresh")
                                        if (domainRules.has("desktopMode")) desktop = domainRules.getBoolean("desktopMode")
                                        if (domainRules.has("jsEnabled")) jsEnabled = domainRules.getBoolean("jsEnabled")
                                        if (domainRules.has("domStorage")) domStorage = domainRules.getBoolean("domStorage")
                                        if (domainRules.has("allowAutoPlay")) allowAutoPlay = domainRules.getBoolean("allowAutoPlay")
                                        if (domainRules.has("hardwareAccel")) hardwareAccel = domainRules.getBoolean("hardwareAccel")
                                        if (domainRules.has("allowMixedContent")) allowMixedContent = domainRules.getBoolean("allowMixedContent")
                                        if (domainRules.has("thirdPartyCookies")) thirdPartyCookies = domainRules.getBoolean("thirdPartyCookies")
                                        if (domainRules.has("userAgent")) customUaOverride = domainRules.getString("userAgent").takeIf { it.isNotBlank() }
                                        break
                                    }
                                }
                            }

                            val isAllowed = pullToRefresh && activePdfSource == null
                            if (isPullToRefreshRuleEnabled != isAllowed) {
                                isPullToRefreshRuleEnabled = isAllowed
                            }
                            val targetRefreshEnabled = isAllowed && userSwipeToRefreshEnabled
                            if (swipeRefreshLayout.isEnabled != targetRefreshEnabled) {
                                swipeRefreshLayout.isEnabled = targetRefreshEnabled
                            }

                            if (!forceUpdate && settingsKey == lastAppliedSettingsKey) {
                                return
                            }
                            lastAppliedSettingsKey = settingsKey
                            
                            // Apply settings only when host or rules actually changed so active GPU mailboxes are not invalidated mid-frame
                            webView.settings.setSupportZoom(zoom)
                            webView.settings.builtInZoomControls = zoom
                            if (webView.settings.javaScriptEnabled != jsEnabled) {
                                webView.settings.javaScriptEnabled = jsEnabled
                            }
                            if (webView.settings.domStorageEnabled != domStorage) {
                                webView.settings.domStorageEnabled = domStorage
                            }
                            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, thirdPartyCookies)
                            val reqGesture = !allowAutoPlay
                            if (webView.settings.mediaPlaybackRequiresUserGesture != reqGesture) {
                                webView.settings.mediaPlaybackRequiresUserGesture = reqGesture
                            }
                            val targetMixedMode = if (allowMixedContent) android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW else android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            if (webView.settings.mixedContentMode != targetMixedMode) {
                                webView.settings.mixedContentMode = targetMixedMode
                            }
                            
                            val desiredLayerType = if (hardwareAccel && !isEmulatorDevice) {
                                android.view.View.LAYER_TYPE_NONE
                            } else {
                                android.view.View.LAYER_TYPE_SOFTWARE
                            }
                            if (webView.layerType != desiredLayerType) {
                                webView.setLayerType(desiredLayerType, null)
                            }
                            
                            val desiredUa = if (desktop) {
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"
                            } else {
                                customUaOverride ?: phoneNativeChromeUa
                            }
                            if (webView.settings.userAgentString != desiredUa) {
                                webView.settings.userAgentString = desiredUa
                            }
                            if (desktop) {
                                webView.settings.loadWithOverviewMode = true
                                webView.settings.useWideViewPort = true
                            }
                            
                        } catch (e: Exception) {
                            e.printStackTrace()
                            if (webView.layerType != defaultLayerType) {
                                webView.setLayerType(defaultLayerType, null)
                            }
                            val isAllowed = activePdfSource == null
                            isPullToRefreshRuleEnabled = isAllowed
                            swipeRefreshLayout.isEnabled = isAllowed && userSwipeToRefreshEnabled
                        }
                    }

                    val webViewInstance = WebView(ctx).apply {

                        val jsInterface = WebAppInterface(
                            onColorReceived = { colorStr ->
                                try {
                                    val parsed = android.graphics.Color.parseColor(colorStr)
                                    post {
                                        if (themeColor != parsed) {
                                            themeColor = parsed
                                        }
                                    }
                                } catch (e: Exception) { e.printStackTrace() }
                            },
                            onSettingsUpdateReceived = { jsonStr ->
                                post {
                                    try {
                                        val prefs = ctx.getSharedPreferences("StudyVersePrefs", Context.MODE_PRIVATE)
                                        val oldRulesStr = prefs.getString("domain_rules", "{}")
                                        val oldRules = org.json.JSONObject(oldRulesStr ?: "{}")
                                        val newRules = org.json.JSONObject(jsonStr)
                                        
                                        // Smart Merge 'default'
                                        if (newRules.has("default")) {
                                            oldRules.put("default", newRules.getJSONObject("default"))
                                        }
                                        
                                        // Smart Merge 'domains'
                                        if (newRules.has("domains")) {
                                            val newDomains = newRules.getJSONObject("domains")
                                            val oldDomains = if (oldRules.has("domains")) oldRules.getJSONObject("domains") else org.json.JSONObject()
                                            
                                            val keys = newDomains.keys()
                                            while (keys.hasNext()) {
                                                val key = keys.next()
                                                oldDomains.put(key, newDomains.getJSONObject(key))
                                            }
                                            oldRules.put("domains", oldDomains)
                                        }
                                        
                                        prefs.edit().putString("domain_rules", oldRules.toString()).apply()
                                        
                                        applyDynamicSettings(this.url, this, forceUpdate = true)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            },
                            onChunkLoadErrorReceived = { errorDetails ->
                                post {
                                    triggerSiteUpdateRecovery("chunk_error: $errorDetails")
                                }
                            },
                            onBlankContentReceived = {
                                post {
                                    triggerSiteUpdateRecovery("blank_body")
                                }
                            },
                            onScrollStateChanged = { scrolledDown ->
                                scrollGuard.isWebPageScrolledDown = scrolledDown
                                if (scrolledDown) {
                                    scrollGuard.touchStartedAtTop = false
                                }
                            },
                            onScrollDirectionChanged = { _ -> }
                        )
                        addJavascriptInterface(jsInterface, "AndroidApp")
                        addJavascriptInterface(jsInterface, "AndroidInterface")
                        
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        
                        // Native direct rendering on physical devices; software layer on virtualized emulator GPU to prevent SharedImage mailbox races
                        setLayerType(defaultLayerType, null)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
                        }
                        overScrollMode = View.OVER_SCROLL_NEVER
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false

                        // Zero-allocation Native WebView scroll listener:
                        // Updates primitive scroll guard state only (zero UI thread posts or Compose recompositions)
                        setOnScrollChangeListener { _, _, scrollY, _, _ ->
                            if (scrollY > 0) {
                                scrollGuard.isWebPageScrolledDown = true
                                scrollGuard.touchStartedAtTop = false
                            } else if (scrollY == 0) {
                                scrollGuard.isWebPageScrolledDown = false
                            }
                        }

                        // Zero-allocation Touch listener:
                        // Locks touchStartedAtTop on ACTION_DOWN so upward scroll never triggers refresh mid-swipe
                        setOnTouchListener { v, event ->
                            if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                                val wv = v as? WebView
                                val atTopNow = (wv == null || (wv.scrollY <= 0 && !wv.canScrollVertically(-1))) && !scrollGuard.isWebPageScrolledDown
                                scrollGuard.touchStartedAtTop = atTopNow
                            }
                            false
                        }

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            @Suppress("DEPRECATION")
                            databaseEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            mediaPlaybackRequiresUserGesture = false
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            javaScriptCanOpenWindowsAutomatically = true
                            setSupportMultipleWindows(true)
                            allowFileAccess = true
                            allowContentAccess = true
                            setGeolocationEnabled(true)

                            userAgentString = phoneNativeChromeUa

                            // High-speed caching & smooth scrolling optimization: fallback to cache when offline
                            cacheMode = if (isOnline) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_CACHE_ELSE_NETWORK
                            loadsImagesAutomatically = true
                            offscreenPreRaster = false
                        }
                        
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        setDownloadListener { downloadUrl, userAgent, contentDisposition, mimeType, contentLength ->
                            // Ensure main frame URL stays synced to the actual displayed page (e.g. StudyLime fullscreen)
                            syncMainFrameUrl(this)
                            var rawFileName = URLUtil.guessFileName(downloadUrl, contentDisposition, mimeType)
                            val isWordDoc = DocxToPdfConverter.isWordOrOfficeDocument(rawFileName, mimeType) ||
                                            downloadUrl.contains(".docx", ignoreCase = true) ||
                                            downloadUrl.contains(".doc", ignoreCase = true)
                            val isPdf = (mimeType?.contains("pdf", ignoreCase = true) == true) ||
                                        (downloadUrl.contains(".pdf", ignoreCase = true)) ||
                                        (contentDisposition?.contains(".pdf", ignoreCase = true) == true) ||
                                        rawFileName.endsWith(".pdf", ignoreCase = true)

                            val finalFileName = if (isPdf || rawFileName.endsWith(".bin", ignoreCase = true)) {
                                when {
                                    rawFileName.endsWith(".bin", ignoreCase = true) -> rawFileName.substringBeforeLast(".bin") + ".pdf"
                                    !rawFileName.endsWith(".pdf", ignoreCase = true) -> {
                                        if (rawFileName.contains(".")) rawFileName.substringBeforeLast(".") + ".pdf" else "$rawFileName.pdf"
                                    }
                                    else -> rawFileName
                                }
                            } else {
                                rawFileName
                            }

                            val finalMimeType = if (isPdf || finalFileName.endsWith(".pdf", ignoreCase = true)) {
                                "application/pdf"
                            } else if (isWordDoc) {
                                if (finalFileName.endsWith(".docx", ignoreCase = true)) "application/vnd.openxmlformats-officedocument.wordprocessingml.document" else "application/msword"
                            } else {
                                if (mimeType.isNullOrEmpty()) "application/octet-stream" else mimeType
                            }

                            try {
                                if (downloadUrl.startsWith("data:", ignoreCase = true)) {
                                    if (isPdf || isWordDoc || finalFileName.endsWith(".pdf", ignoreCase = true)) {
                                        (ctx as? Activity)?.runOnUiThread {
                                            if (activePdfSource == null) {
                                                activePdfSource = downloadUrl
                                                activePdfTitle = finalFileName
                                            }
                                        }
                                    }
                                } else if (downloadUrl.startsWith("http://", ignoreCase = true) || downloadUrl.startsWith("https://", ignoreCase = true)) {
                                    // Open study PDFs and Office documents directly in the inbuilt viewer without race conditions
                                    if (isPdf || isWordDoc || finalFileName.endsWith(".pdf", ignoreCase = true)) {
                                        (ctx as? Activity)?.runOnUiThread {
                                            if (activePdfSource == null) {
                                                activePdfSource = downloadUrl
                                                activePdfTitle = finalFileName
                                            }
                                        }
                                    } else {
                                        val safeDownloadName = finalFileName
                                            .replace("[^a-zA-Z0-9._ -]".toRegex(), "_")
                                            .trim()
                                            .ifBlank { "study_document.pdf" }
                                        val request = DownloadManager.Request(android.net.Uri.parse(downloadUrl)).apply {
                                            setMimeType(finalMimeType)
                                            addRequestHeader("User-Agent", userAgent)
                                            val cookie = CookieManager.getInstance().getCookie(downloadUrl)
                                            if (cookie != null) addRequestHeader("Cookie", cookie)
                                            setTitle("StudyVerse: $safeDownloadName")
                                            setDescription("Downloading $safeDownloadName...")
                                            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeDownloadName)
                                        }
                                        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                                        dm?.enqueue(request)
                                        Toast.makeText(ctx, "StudyVerse: Downloading $safeDownloadName to Downloads...", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } catch (t: Throwable) {
                                t.printStackTrace()
                                Toast.makeText(ctx, "Download failed: ${t.message}", Toast.LENGTH_SHORT).show()
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onShowFileChooser(
                                webView: WebView?,
                                filePathCallback: android.webkit.ValueCallback<Array<android.net.Uri>>?,
                                fileChooserParams: WebChromeClient.FileChooserParams?
                            ): Boolean {
                                fileChooserCallback?.onReceiveValue(null)
                                fileChooserCallback = filePathCallback
                                val intent = fileChooserParams?.createIntent()
                                try {
                                    if (intent != null) {
                                        fileChooserLauncher.launch(intent)
                                    }
                                } catch (e: Exception) {
                                    fileChooserCallback?.onReceiveValue(null)
                                    fileChooserCallback = null
                                    return false
                                }
                                return true
                            }

                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                val nextProg = newProgress / 100f
                                if (isLoading && (kotlin.math.abs(nextProg - progress) >= 0.05f || newProgress >= 75)) {
                                    progress = nextProg
                                }
                                if (newProgress >= 75 && !hasCompletedInitialBoot) {
                                    hasCompletedInitialBoot = true
                                }
                                if (newProgress >= 90) {
                                    if (isLoading) isLoading = false
                                    if (swipeRefreshLayout.isRefreshing) swipeRefreshLayout.isRefreshing = false
                                }
                                val back = view?.canGoBack() == true
                                val fwd = view?.canGoForward() == true
                                if (canGoBack != back) canGoBack = back
                                if (canGoForward != fwd) canGoForward = fwd
                            }

                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: android.os.Message?
                            ): Boolean {
                                val newWebView = WebView(view!!.context)
                                newWebView.setLayerType(defaultLayerType, null)
                                newWebView.settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    @Suppress("DEPRECATION")
                                    databaseEnabled = true
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    mediaPlaybackRequiresUserGesture = false
                                    setSupportZoom(true)
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    javaScriptCanOpenWindowsAutomatically = true
                                    setSupportMultipleWindows(true)
                                    allowFileAccess = true
                                    allowContentAccess = true
                                    setGeolocationEnabled(true)
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                    loadsImagesAutomatically = true
                                    userAgentString = phoneNativeChromeUa
                                }
                                newWebView.webChromeClient = this
                                newWebView.webViewClient = object : WebViewClient() {
                                    private var popupHandled = false
                                    private fun handlePopupUrl(v: WebView?, targetUrl: String): Boolean {
                                        if (popupHandled) return true
                                        popupHandled = true
                                        val unwrappedWebUrl = resolveWebUrlFromIntentOrCustomScheme(targetUrl)
                                        val effectiveUrl = unwrappedWebUrl ?: targetUrl
                                        if (isBlockedAdDomain(effectiveUrl)) {
                                            try {
                                                v?.stopLoading()
                                                v?.destroy()
                                            } catch (_: Throwable) {}
                                            return true
                                        }

                                        val isDoc = isDirectStudyDocumentUrl(effectiveUrl)
                                        if (isDoc) {
                                            (ctx as? Activity)?.runOnUiThread {
                                                if (activePdfSource == null) {
                                                    activePdfSource = effectiveUrl
                                                    activePdfTitle = URLUtil.guessFileName(
                                                        effectiveUrl,
                                                        null,
                                                        if (effectiveUrl.contains(".doc")) "application/msword" else "application/pdf"
                                                    )
                                                }
                                            }
                                            syncMainFrameUrl(view)
                                        } else if (effectiveUrl.startsWith("http://", ignoreCase = true) || effectiveUrl.startsWith("https://", ignoreCase = true)) {
                                            val mainUrl = view.url ?: currentLoadedUrl
                                            val mainHost = android.net.Uri.parse(mainUrl).host ?: ""
                                            val popupHost = android.net.Uri.parse(effectiveUrl).host ?: ""
                                            val isFromStudyLime = mainHost.contains("study-verse-lime", ignoreCase = true) ||
                                                mainHost.contains("studylime", ignoreCase = true)
                                            val isToStudyLime = popupHost.contains("study-verse-lime", ignoreCase = true) ||
                                                popupHost.contains("studylime", ignoreCase = true)
                                            val isSameBaseDomain = extractBaseDomain(mainHost).isNotEmpty() &&
                                                extractBaseDomain(mainHost) == extractBaseDomain(popupHost)
                                            val absorbAdPopups = studyPrefs.getBoolean("rc_absorb_ad_popups", false)

                                            if (activePdfSource == null) {
                                                if (isUserGesture || isFromStudyLime || isToStudyLime || isSameBaseDomain || !absorbAdPopups) {
                                                    // Load inside the phone's WebView with Referer preserved so multi-domain key generation steps pass seamlessly!
                                                    val headers = if (mainUrl.startsWith("http", ignoreCase = true)) {
                                                        mapOf("Referer" to mainUrl)
                                                    } else emptyMap()
                                                    view.loadUrl(effectiveUrl, headers)
                                                } else {
                                                    v?.postDelayed({
                                                        try {
                                                            v.stopLoading()
                                                            v.destroy()
                                                        } catch (_: Throwable) {}
                                                    }, 1200L)
                                                    return false
                                                }
                                            }
                                        } else {
                                            // Non-web native app scheme (e.g. tg://, whatsapp://, upi://)
                                            try {
                                                val nativeIntent = if (effectiveUrl.startsWith("intent://", ignoreCase = true)) {
                                                    Intent.parseUri(effectiveUrl, Intent.URI_INTENT_SCHEME)
                                                } else {
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(effectiveUrl))
                                                }
                                                nativeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                ctx.startActivity(nativeIntent)
                                            } catch (_: Throwable) {
                                            }
                                            syncMainFrameUrl(view)
                                        }
                                        try {
                                            v?.stopLoading()
                                            v?.destroy()
                                        } catch (_: Throwable) {}
                                        return true
                                    }

                                    override fun shouldOverrideUrlLoading(v: WebView?, req: WebResourceRequest?): Boolean {
                                        val targetUrl = req?.url?.toString() ?: return false
                                        return handlePopupUrl(v, targetUrl)
                                    }

                                    @Suppress("DEPRECATION")
                                    override fun shouldOverrideUrlLoading(v: WebView?, urlStr: String?): Boolean {
                                        val targetUrl = urlStr ?: return false
                                        return handlePopupUrl(v, targetUrl)
                                    }

                                    override fun onPageStarted(v: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                        super.onPageStarted(v, url, favicon)
                                        if (!url.isNullOrBlank() && url != "about:blank") {
                                            handlePopupUrl(v, url)
                                        }
                                    }

                                    override fun onRenderProcessGone(v: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                                        v?.let { wv ->
                                            (wv.parent as? android.view.ViewGroup)?.removeView(wv)
                                            wv.destroy()
                                        }
                                        return true
                                    }
                                }
                                val transport = resultMsg?.obj as? WebView.WebViewTransport
                                transport?.webView = newWebView
                                resultMsg?.sendToTarget()
                                return true
                            }

                            override fun onPermissionRequest(request: PermissionRequest?) {
                                request?.grant(request.resources)
                            }

                            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                if (customView != null) {
                                    callback?.onCustomViewHidden()
                                    return
                                }
                                customView = view
                                customViewCallback = callback

                                (ctx as? Activity)?.let { activity ->
                                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    val windowInsetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                                    windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                    windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                                }
                            }

                            override fun onHideCustomView() {
                                customView = null
                                customViewCallback?.onCustomViewHidden()
                                customViewCallback = null

                                (ctx as? Activity)?.let { activity ->
                                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                                    val windowInsetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                                    windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                                }
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onReceivedSslError(
                                view: WebView?,
                                handler: android.webkit.SslErrorHandler?,
                                error: android.net.http.SslError?
                            ) {
                                handler?.proceed()
                            }

                            private fun handleCustomOrNavigationUrl(view: WebView?, rawRequestUrl: String): Boolean {
                                if (activePdfSource != null) {
                                    return true
                                }
                                if (isBlockedAdDomain(rawRequestUrl)) {
                                    return true
                                }

                                // 1. Unwrap Chrome redirect intents (intent://...#Intent;scheme=https;package=com.android.chrome;end)
                                // and googlechrome:// schemes so ad-key generation stays 100% inside the app's WebView (unless remotely disabled via keepIntentInApp: false)!
                                val unwrappedWebUrl = resolveWebUrlFromIntentOrCustomScheme(rawRequestUrl)
                                if (unwrappedWebUrl != null && shouldKeepIntentInAppForUrl(unwrappedWebUrl)) {
                                    if (isDirectStudyDocumentUrl(unwrappedWebUrl)) {
                                        val guessName = URLUtil.guessFileName(
                                            unwrappedWebUrl,
                                            null,
                                            if (unwrappedWebUrl.contains(".doc")) "application/msword" else "application/pdf"
                                        )
                                        (ctx as? Activity)?.runOnUiThread {
                                            if (activePdfSource == null) {
                                                activePdfSource = unwrappedWebUrl
                                                activePdfTitle = guessName
                                            }
                                        }
                                        view?.stopLoading()
                                        syncMainFrameUrl(view)
                                        return true
                                    }
                                    if (shouldAutoOpenInExternalChrome(unwrappedWebUrl)) {
                                        openUrlInExternalChrome(ctx, unwrappedWebUrl)
                                        syncMainFrameUrl(view)
                                        return true
                                    }
                                    val currentRef = view?.url ?: currentLoadedUrl
                                    val headers = if (currentRef.startsWith("http", ignoreCase = true)) {
                                        mapOf("Referer" to currentRef)
                                    } else emptyMap()
                                    view?.loadUrl(unwrappedWebUrl, headers)
                                    return true
                                }

                                // 2. Check direct PDF / Office document links
                                if (isDirectStudyDocumentUrl(rawRequestUrl)) {
                                    val guessName = URLUtil.guessFileName(
                                        rawRequestUrl,
                                        null,
                                        if (rawRequestUrl.contains(".doc")) "application/msword" else "application/pdf"
                                    )
                                    (ctx as? Activity)?.runOnUiThread {
                                        if (activePdfSource == null) {
                                            activePdfSource = rawRequestUrl
                                            activePdfTitle = guessName
                                        }
                                    }
                                    view?.stopLoading()
                                    syncMainFrameUrl(view)
                                    return true
                                }

                                // 3. Standard HTTP / HTTPS web navigation stays inside WebView (unless domain is remotely configured with openInChrome: true)
                                if (rawRequestUrl.startsWith("http://", ignoreCase = true) || rawRequestUrl.startsWith("https://", ignoreCase = true)) {
                                    if (shouldAutoOpenInExternalChrome(rawRequestUrl)) {
                                        openUrlInExternalChrome(ctx, rawRequestUrl)
                                        syncMainFrameUrl(view)
                                        return true
                                    }
                                    return false
                                }

                                // 4. True external app intents (Telegram tg://, WhatsApp whatsapp://, UPI, Play Store, etc.)
                                try {
                                    val intent = if (rawRequestUrl.startsWith("intent://", ignoreCase = true)) {
                                        android.content.Intent.parseUri(rawRequestUrl, android.content.Intent.URI_INTENT_SCHEME)
                                    } else {
                                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(rawRequestUrl))
                                    }
                                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    ctx.startActivity(intent)
                                } catch (e: Exception) {
                                    if (rawRequestUrl.startsWith("intent://", ignoreCase = true)) {
                                        try {
                                            val parsedIntent = android.content.Intent.parseUri(rawRequestUrl, android.content.Intent.URI_INTENT_SCHEME)
                                            val fallbackUrl = parsedIntent.getStringExtra("browser_fallback_url")
                                                ?: parsedIntent.getStringExtra("S.browser_fallback_url")
                                            if (fallbackUrl != null) {
                                                view?.loadUrl(fallbackUrl)
                                                return true
                                            }
                                            val packageName = parsedIntent.`package`
                                            if (packageName != null && packageName != "com.android.chrome") {
                                                val playStoreIntent = android.content.Intent(
                                                    android.content.Intent.ACTION_VIEW,
                                                    android.net.Uri.parse("market://details?id=$packageName")
                                                )
                                                playStoreIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                ctx.startActivity(playStoreIntent)
                                                return true
                                            }
                                        } catch (ex: Exception) {
                                            ex.printStackTrace()
                                        }
                                    }
                                    e.printStackTrace()
                                }
                                syncMainFrameUrl(view)
                                return true
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val requestUrl = request?.url?.toString() ?: return false
                                return handleCustomOrNavigationUrl(view, requestUrl)
                            }

                            @Suppress("DEPRECATION")
                            override fun shouldOverrideUrlLoading(view: WebView?, urlStr: String?): Boolean {
                                if (urlStr == null) return false
                                return handleCustomOrNavigationUrl(view, urlStr)
                            }

                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                                syncMainFrameUrl(view)
                                view?.let { webView ->
                                    applyDynamicSettings(webView.url ?: url, webView)
                                }
                            }

                            private fun extractAndPostThemeColor(view: WebView?) {
                                view?.evaluateJavascript("""
                                    (function() {
                                        function parseRgbToHex(bgColor) {
                                            if (!bgColor || !bgColor.startsWith('rgb')) return null;
                                            var match = bgColor.match(/^rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([0-9.]+))?\)/);
                                            if (!match) return null;
                                            if (match[4] !== undefined && parseFloat(match[4]) < 0.12) return null;
                                            var r = parseInt(match[1]).toString(16).padStart(2, '0');
                                            var g = parseInt(match[2]).toString(16).padStart(2, '0');
                                            var b = parseInt(match[3]).toString(16).padStart(2, '0');
                                            return '#' + r + g + b;
                                        }
                                        function detectExplicitThemeMode() {
                                            var html = document.documentElement;
                                            var body = document.body;
                                            var checkNodes = [html, body];
                                            for (var i = 0; i < checkNodes.length; i++) {
                                                var el = checkNodes[i];
                                                if (!el) continue;
                                                if (el.classList) {
                                                    if (el.classList.contains('dark') || el.classList.contains('theme-dark') || el.classList.contains('dark-mode')) return 'dark';
                                                    if (el.classList.contains('light') || el.classList.contains('theme-light') || el.classList.contains('light-mode')) return 'light';
                                                }
                                                var dt = (el.getAttribute('data-theme') || el.getAttribute('data-mode') || el.getAttribute('data-color-scheme') || '').toLowerCase();
                                                if (dt.indexOf('dark') !== -1) return 'dark';
                                                if (dt.indexOf('light') !== -1) return 'light';
                                                if (el.style && el.style.colorScheme) {
                                                    var cs = el.style.colorScheme.toLowerCase();
                                                    if (cs === 'dark') return 'dark';
                                                    if (cs === 'light') return 'light';
                                                }
                                            }
                                            try {
                                                var stored = (localStorage.getItem('theme') || localStorage.getItem('color-theme') || localStorage.getItem('studyverse-theme') || '').toLowerCase();
                                                if (stored === 'dark') return 'dark';
                                                if (stored === 'light') return 'light';
                                            } catch (e) {}
                                            return null;
                                        }
                                        function getVisibleBackgroundHex() {
                                            var selectors = [document.body, document.documentElement];
                                            var rootEl = document.querySelector('#__next, #root, main, [data-theme], .app, .min-h-screen');
                                            if (rootEl) selectors.unshift(rootEl);
                                            for (var i = 0; i < selectors.length; i++) {
                                                var el = selectors[i];
                                                if (!el) continue;
                                                var hex = parseRgbToHex(window.getComputedStyle(el).backgroundColor);
                                                if (hex) return hex;
                                            }
                                            return null;
                                        }
                                        function hexLuminance(hex) {
                                            if (!hex || hex.charAt(0) !== '#' || hex.length < 7) return null;
                                            var r = parseInt(hex.substr(1, 2), 16);
                                            var g = parseInt(hex.substr(3, 2), 16);
                                            var b = parseInt(hex.substr(5, 2), 16);
                                            return r * 0.299 + g * 0.587 + b * 0.114;
                                        }
                                        function sendColor() {
                                            var isMainSite = window.location.hostname.indexOf('study-verse-lime.vercel.app') !== -1;
                                            var explicitMode = detectExplicitThemeMode();
                                            var bgHex = getVisibleBackgroundHex();
                                            var resolved = null;

                                            if (isMainSite) {
                                                // On our main site, status bar is transparent and text color must strictly follow Dark (white text) vs Light (dark text)
                                                if (explicitMode === 'dark') {
                                                    var lumD = hexLuminance(bgHex);
                                                    resolved = (bgHex && lumD !== null && lumD < 150) ? bgHex : '#090D16';
                                                } else if (explicitMode === 'light') {
                                                    var lumL = hexLuminance(bgHex);
                                                    resolved = (bgHex && lumL !== null && lumL >= 150) ? bgHex : '#FFFFFF';
                                                } else if (bgHex) {
                                                    resolved = bgHex;
                                                } else {
                                                    resolved = '#090D16';
                                                }
                                            } else {
                                                // On external sites, color status & nav bars by site theme
                                                var metaThemeColor = document.querySelector('meta[name="theme-color"]');
                                                if (explicitMode === 'dark' && bgHex && hexLuminance(bgHex) < 150) {
                                                    resolved = bgHex;
                                                } else if (explicitMode === 'light' && bgHex && hexLuminance(bgHex) >= 150) {
                                                    resolved = bgHex;
                                                } else if (metaThemeColor && metaThemeColor.content && metaThemeColor.content.trim().length > 0) {
                                                    resolved = metaThemeColor.content.trim();
                                                } else if (bgHex) {
                                                    resolved = bgHex;
                                                } else if (explicitMode === 'dark') {
                                                    resolved = '#0F172A';
                                                } else {
                                                    resolved = '#FFFFFF';
                                                }
                                            }

                                            if (resolved && resolved !== window.__studyverse_last_theme_color) {
                                                window.__studyverse_last_theme_color = resolved;
                                                if (window.AndroidInterface && window.AndroidInterface.postThemeColor) {
                                                    window.AndroidInterface.postThemeColor(resolved);
                                                }
                                            }
                                        }
                                            sendColor();
                                            if (!window.__studyverse_theme_observer_installed) {
                                                window.__studyverse_theme_observer_installed = true;
                                                var themeTimer = null;
                                                function scheduleSendColor() {
                                                    if (themeTimer) clearTimeout(themeTimer);
                                                    themeTimer = setTimeout(sendColor, 260);
                                                }
                                                // Observe ONLY theme attributes on html/body (never document.head childList/subtree,
                                                // which Next.js mutates dozens of times during initial 25s prefetching!)
                                                var observer = new MutationObserver(scheduleSendColor);
                                                if (document.documentElement) {
                                                    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['class', 'data-theme', 'data-mode', 'data-color-scheme'] });
                                                }
                                                if (document.body) {
                                                    observer.observe(document.body, { attributes: true, attributeFilter: ['class', 'data-theme', 'data-mode', 'data-color-scheme'] });
                                                }
                                            }
                                        })();
                                    """.trimIndent(), null)
                            }

                            private fun installScrollAndSafetyGuard(view: WebView?) {
                                val spoofChrome = studyPrefs.getBoolean("rc_spoof_chrome_client_hints", true)
                                val customJs = studyPrefs.getString("rc_custom_js_inject", "") ?: ""
                                val realModel = (android.os.Build.MODEL ?: "Android").replace("'", "").replace("\"", "")
                                val realRelease = (android.os.Build.VERSION.RELEASE ?: "14.0.0").replace("'", "").replace("\"", "")
                                // Uses the phone's real Chromium version, real Android release, and real device model
                                // while reporting Google Chrome brands + keeping target="_blank" forms/links inside the phone's native WebView!
                                val safetyGuardScript = """
                                    (function() {
                                        try {
                                            if ($spoofChrome) {
                                                if (!window.chrome) {
                                                    window.chrome = {};
                                                }
                                                if (!window.chrome.runtime) window.chrome.runtime = {};
                                                if (!window.chrome.app) window.chrome.app = { isInstalled: false };
                                                if (!window.chrome.csi) window.chrome.csi = function() { return {}; };
                                                if (!window.chrome.loadTimes) window.chrome.loadTimes = function() { return {}; };
                                                if (navigator.webdriver) {
                                                    Object.defineProperty(navigator, 'webdriver', { get: function() { return false; } });
                                                }
                                                var ua = navigator.userAgent || '';
                                                var m = ua.match(/Chrome\/(([0-9]+)\.[0-9]+\.[0-9]+\.[0-9]+)/);
                                                var fullVer = m ? m[1] : '134.0.0.0';
                                                var majorVer = m ? m[2] : '134';
                                                var chromeBrands = [
                                                    { brand: 'Google Chrome', version: majorVer },
                                                    { brand: 'Chromium', version: majorVer },
                                                    { brand: 'Not-A.Brand', version: '24' }
                                                ];
                                                var uaData = {
                                                    brands: chromeBrands,
                                                    mobile: true,
                                                    platform: 'Android',
                                                    getHighEntropyValues: function(hints) {
                                                        return Promise.resolve({
                                                            brands: chromeBrands,
                                                            fullVersionList: [
                                                                { brand: 'Google Chrome', version: fullVer },
                                                                { brand: 'Chromium', version: fullVer },
                                                                { brand: 'Not-A.Brand', version: '24.0.0.0' }
                                                            ],
                                                            mobile: true,
                                                            model: '$realModel',
                                                            platform: 'Android',
                                                            platformVersion: '$realRelease',
                                                            architecture: 'arm',
                                                            bitness: '64',
                                                            uaFullVersion: fullVer
                                                        });
                                                    },
                                                    toJSON: function() {
                                                        return { brands: chromeBrands, mobile: true, platform: 'Android' };
                                                    }
                                                };
                                                Object.defineProperty(navigator, 'userAgentData', {
                                                    get: function() { return uaData; },
                                                    configurable: true
                                                });
                                            }
                                            if (!window.__studyverse_native_target_guard) {
                                                window.__studyverse_native_target_guard = true;
                                                document.addEventListener('click', function(e) {
                                                    var el = e.target && e.target.closest ? e.target.closest('a[target="_blank"], form[target="_blank"]') : null;
                                                    if (el && el.getAttribute('target') === '_blank') {
                                                        el.setAttribute('target', '_self');
                                                    }
                                                }, true);
                                                document.addEventListener('submit', function(e) {
                                                    var form = e.target;
                                                    if (form && form.getAttribute && form.getAttribute('target') === '_blank') {
                                                        form.setAttribute('target', '_self');
                                                    }
                                                }, true);
                                            }
                                        } catch (e) {}
                                        if (!document.getElementById('__studyverse_fast_tap') && document.head) {
                                            var st = document.createElement('style');
                                            st.id = '__studyverse_fast_tap';
                                            st.textContent = 'button, a, [role="button"], input, select, textarea, label, summary { touch-action: manipulation; -webkit-tap-highlight-color: transparent; }';
                                            document.head.appendChild(st);
                                        }
                                        if (!window.__studyverse_safety_guard_installed) {
                                            window.__studyverse_safety_guard_installed = true;
                                            window.addEventListener('error', function(e) {
                                                var msg = (e && e.message) ? e.message : '';
                                                if (msg.indexOf('Loading chunk') !== -1 || 
                                                    msg.indexOf('ChunkLoadError') !== -1 || 
                                                    msg.indexOf('Failed to fetch dynamically imported module') !== -1 ||
                                                    msg.indexOf('css chunk') !== -1) {
                                                    if (window.AndroidApp && window.AndroidApp.onChunkLoadError) {
                                                        window.AndroidApp.onChunkLoadError(msg);
                                                    }
                                                }
                                            }, true);
                                            window.addEventListener('unhandledrejection', function(e) {
                                                var reason = (e && e.reason && e.reason.message) ? e.reason.message : '';
                                                if (reason.indexOf('Chunk') !== -1 || reason.indexOf('module') !== -1) {
                                                    if (window.AndroidApp && window.AndroidApp.onChunkLoadError) {
                                                        window.AndroidApp.onChunkLoadError(reason);
                                                    }
                                                }
                                            });
                                        }
                                    })();
                                """.trimIndent()
                                view?.evaluateJavascript(safetyGuardScript, null)
                                if (customJs.isNotBlank()) {
                                    view?.evaluateJavascript(customJs, null)
                                }
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                progress = 0f
                                scrollGuard.isWebPageScrolledDown = false
                                
                                view?.let { webView ->
                                    applyDynamicSettings(webView.url ?: url, webView)
                                }
                                installScrollAndSafetyGuard(view)
                            }

                            override fun onPageCommitVisible(view: WebView?, url: String?) {
                                super.onPageCommitVisible(view, url)
                                if (!hasCompletedInitialBoot) {
                                    hasCompletedInitialBoot = true
                                }
                                if (isLoading) {
                                    isLoading = false
                                }
                                syncMainFrameUrl(view)
                                installScrollAndSafetyGuard(view)
                                extractAndPostThemeColor(view)
                                try {
                                    CookieManager.getInstance().flush()
                                } catch (_: Throwable) {}
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                if (!hasCompletedInitialBoot) {
                                    hasCompletedInitialBoot = true
                                }
                                isLoading = false
                                swipeRefreshLayout.isRefreshing = false
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                                syncMainFrameUrl(view)
                                installScrollAndSafetyGuard(view)
                                extractAndPostThemeColor(view)
                                try {
                                    CookieManager.getInstance().flush()
                                } catch (_: Throwable) {}

                                // Safe blank body check strictly for StudyLime main site after full hydration (3.5s)
                                view?.postDelayed({
                                    val currentHost = view.url?.lowercase() ?: ""
                                    if (!hasError && !isLoading && activePdfSource == null && currentHost.contains("study-verse-lime.vercel.app")) {
                                        view.evaluateJavascript("""
                                            (function() {
                                                if (document.readyState !== 'complete') return 'LOADING';
                                                var body = document.body;
                                                if (!body) return 'BLANK';
                                                var text = (body.innerText || '').trim();
                                                var elems = body.children.length;
                                                if (elems === 0 || (text.length === 0 && body.querySelectorAll('img, svg, canvas, iframe, video, button, a, input').length === 0)) {
                                                    return 'BLANK';
                                                }
                                                return 'OK';
                                            })();
                                        """.trimIndent()) { result ->
                                            if (result != null && result.contains("BLANK") && activePdfSource == null) {
                                                triggerSiteUpdateRecovery("blank_body_after_load")
                                            }
                                        }
                                    }
                                }, 3500)
                            }

                            override fun onReceivedHttpError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                errorResponse: android.webkit.WebResourceResponse?
                            ) {
                                super.onReceivedHttpError(view, request, errorResponse)
                                val reqUrl = request?.url?.toString() ?: ""
                                val status = errorResponse?.statusCode ?: 0
                                // 404 on JS or CSS chunks means the site was updated with new hashed bundles
                                if (status == 404 && (reqUrl.contains("/_next/static/") || reqUrl.contains(".chunk.js") || reqUrl.contains(".chunk.css"))) {
                                    view?.post {
                                        triggerSiteUpdateRecovery("http_404_chunk: $reqUrl")
                                    }
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                swipeRefreshLayout.isRefreshing = false
                                if (request?.isForMainFrame == true) {
                                    val errorCode = error?.errorCode ?: 0
                                    if (errorCode != WebViewClient.ERROR_UNSUPPORTED_SCHEME && errorCode != -10) {
                                        if (!NetworkMonitor.isOnline(ctx)) {
                                            hasError = true
                                            errorMessage = "No internet connection available. Offline mode active."
                                        } else {
                                            if (errorCode == WebViewClient.ERROR_HOST_LOOKUP || 
                                                errorCode == WebViewClient.ERROR_CONNECT || 
                                                errorCode == WebViewClient.ERROR_TIMEOUT) {
                                                hasError = true
                                                errorMessage = "Unable to connect to StudyVerse. Please check your network."
                                            }
                                        }
                                    }
                                }
                            }

                            override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                                view?.let { wv ->
                                    (wv.parent as? android.view.ViewGroup)?.removeView(wv)
                                    wv.destroy()
                                }
                                hasError = true
                                errorMessage = "Graphics renderer refreshed. Tap retry to reload StudyVerse."
                                return true
                            }
                        }

                        if (savedState != null) {
                            this.restoreState(savedState)
                        } else {
                            loadUrl(url)
                        }

                        webView = this
                        onWebViewCreated(this)
                    }

                    swipeRefreshLayout.addView(
                        webViewInstance,
                        android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )

                    // Strict Child Scroll Up Callback:
                    // Return true if child OR any inner DOM container is scrolled down, if touch did not start at true top,
                    // or if Swipe-to-Refresh switch is OFF. This guarantees scrolling upward on EVERY website never triggers refresh!
                    swipeRefreshLayout.setOnChildScrollUpCallback { _, _ ->
                        if (!userSwipeToRefreshEnabled || !isPullToRefreshRuleEnabled || activePdfSource != null || !scrollGuard.touchStartedAtTop) {
                            true
                        } else {
                            val child = swipeRefreshLayout.getChildAt(0) as? WebView
                            if (child == null) {
                                false
                            } else {
                                child.scrollY > 0 || child.canScrollVertically(-1) || scrollGuard.isWebPageScrolledDown
                            }
                        }
                    }

                    swipeRefreshLayout.setOnRefreshListener {
                        if (!userSwipeToRefreshEnabled || !scrollGuard.touchStartedAtTop || scrollGuard.isWebPageScrolledDown) {
                            swipeRefreshLayout.isRefreshing = false
                            return@setOnRefreshListener
                        }
                        hasError = false
                        webViewInstance.reload()
                    }

                    swipeRefreshLayout
                },
                update = { container ->
                    val shouldEnableRefresh = userSwipeToRefreshEnabled && isPullToRefreshRuleEnabled && activePdfSource == null
                    if (container.isEnabled != shouldEnableRefresh) {
                        container.isEnabled = shouldEnableRefresh
                    }
                },
                onRelease = { container ->
                    try {
                        onWebViewCreated(null)
                        webView = null
                        val childWebView = container.getChildAt(0) as? WebView
                        childWebView?.let {
                            it.stopLoading()
                            it.removeAllViews()
                            it.destroy()
                        }
                    } catch (_: Throwable) {}
                },
                modifier = Modifier.fillMaxSize()
            )

                // Top Loading Progress Bar overlaid so WebView never resizes when loading completes
                if (isLoading && progress < 1f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(4.dp),
                        color = Color(0xFFF44336),
                        trackColor = Color.Transparent
                    )
                }
            } // Close Box

            // Offline Mode Banner when browsing cached content
            if (!isOnline && !hasError && activePdfSource == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    LiquidGlassButton(
                        onClick = {
                            if (NetworkMonitor.isOnline(context)) {
                                hasError = false
                                isLoading = true
                                webView?.reload()
                            } else {
                                Toast.makeText(context, "Viewing Offline Cached Study Content", Toast.LENGTH_SHORT).show()
                            }
                        },
                        icon = Icons.Default.SignalWifiOff,
                        text = "Offline Mode · Cached View",
                        accentColor = Color(0xFF00E5FF),
                        isPill = true
                    )
                }
            }

            // Offline Hub Screen when disconnected / un-cached page requested
            if (hasError) {
                OfflineHubScreen(
                    onRetry = {
                        hasError = false
                        isLoading = true
                        webView?.reload()
                    },
                    onTryLoadCache = {
                        hasError = false
                        isLoading = true
                        webView?.settings?.cacheMode = WebSettings.LOAD_CACHE_ONLY
                        webView?.reload()
                    },
                    onOpenPdf = { filePath, title ->
                        activePdfSource = filePath
                        activePdfTitle = title
                    }
                )
            }

            // Inbuilt High-Performance PDF Opener with Liquid Glass UI
            if (activePdfSource != null) {
                PdfViewerScreen(
                    pdfSource = activePdfSource!!,
                    title = activePdfTitle,
                    onClose = {
                        activePdfSource = null
                        activePdfTitle = null
                    }
                )
            }

            // Full Screen Animated Butter Wave Landing Overlay (only during initial cold boot before first visible commit)
            AnimatedVisibility(
                visible = !hasCompletedInitialBoot && isLoading && !hasError && activePdfSource == null,
                enter = fadeIn(animationSpec = tween(180)),
                exit = fadeOut(animationSpec = tween(220, easing = FastOutSlowInEasing))
            ) {
                LoadingOverlay(progress = progress)
            }

            // Scrim when WebView Floating Ball Chrome Menu is open
            AnimatedVisibility(
                visible = !isFirstStudyLimeSite && isWebOrbMenuOpen && activePdfSource == null && customView == null && !hasError,
                enter = fadeIn(animationSpec = tween(180)),
                exit = fadeOut(animationSpec = tween(160)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x88040812))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { isWebOrbMenuOpen = false }
                )
            }

            // Movable Web Browsing Ball & Adaptive Multi-Directional Chrome Menu (Active on external sites)
            if (!isFirstStudyLimeSite && activePdfSource == null && customView == null && !hasError && !isLoading) {
                val density = LocalDensity.current
                val ballSizePx = with(density) { 52.dp.toPx() }
                val edgeMarginPx = with(density) { 14.dp.toPx() }
                val menuGapPx = with(density) { 10.dp.toPx() }
                val topInsetPx = with(density) { WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx() }
                val bottomInsetPx = with(density) { WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().toPx() }

                val screenW = if (webContainerSize.width > 0) webContainerSize.width.toFloat() else with(density) { 380.dp.toPx() }
                val screenH = if (webContainerSize.height > 0) webContainerSize.height.toFloat() else with(density) { 780.dp.toPx() }

                val minBallX = edgeMarginPx
                val maxBallX = (screenW - ballSizePx - edgeMarginPx).coerceAtLeast(minBallX)
                val minBallY = topInsetPx + edgeMarginPx
                val maxBallY = (screenH - ballSizePx - bottomInsetPx - edgeMarginPx).coerceAtLeast(minBallY)

                val defaultBallX = maxBallX
                val defaultBallY = (maxBallY - with(density) { 4.dp.toPx() }).coerceIn(minBallY, maxBallY)

                val fallbackMenuW = with(density) { 316.dp.toPx() }
                val fallbackMenuH = with(density) { 210.dp.toPx() }
                val menuW = (if (webMenuSize.width > 0) webMenuSize.width.toFloat() else fallbackMenuW)
                    .coerceAtMost((screenW - edgeMarginPx * 2f).coerceAtLeast(180f))
                val menuH = if (webMenuSize.height > 0) webMenuSize.height.toFloat() else fallbackMenuH

                // Expanded Chrome-Style Quick Menu positioned dynamically around the Movable Ball
                if (isWebOrbMenuOpen) {
                    val currentBallX = if (webBallOffsetX < 0f) defaultBallX else webBallOffsetX.coerceIn(minBallX, maxBallX)
                    val currentBallY = if (webBallOffsetY < 0f) defaultBallY else webBallOffsetY.coerceIn(minBallY, maxBallY)
                    val ballCenterX = currentBallX + ballSizePx / 2f
                    val spaceAbove = currentBallY - minBallY
                    val spaceBelow = (screenH - bottomInsetPx - edgeMarginPx) - (currentBallY + ballSizePx)
                    val openAbove = spaceAbove >= menuH + menuGapPx || spaceAbove >= spaceBelow

                    val minMenuX = edgeMarginPx
                    val maxMenuX = (screenW - edgeMarginPx - menuW).coerceAtLeast(minMenuX)
                    val rawMenuX = when {
                        ballCenterX < screenW * 0.38f -> currentBallX
                        ballCenterX > screenW * 0.62f -> currentBallX + ballSizePx - menuW
                        else -> ballCenterX - menuW / 2f
                    }
                    val resolvedMenuX = rawMenuX.coerceIn(minMenuX, maxMenuX)

                    val minMenuY = minBallY
                    val maxMenuY = (screenH - bottomInsetPx - edgeMarginPx - menuH).coerceAtLeast(minMenuY)
                    val rawMenuY = if (openAbove) {
                        currentBallY - menuH - menuGapPx
                    } else {
                        currentBallY + ballSizePx + menuGapPx
                    }
                    val resolvedMenuY = rawMenuY.coerceIn(minMenuY, maxMenuY)

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset {
                                IntOffset(resolvedMenuX.roundToInt(), resolvedMenuY.roundToInt())
                            }
                    ) {
                        WebFloatingBallChromeMenu(
                            canGoBack = canGoBack,
                            canGoForward = canGoForward,
                            swipeToRefreshEnabled = userSwipeToRefreshEnabled,
                            onToggleSwipeToRefresh = { enabled ->
                                userSwipeToRefreshEnabled = enabled
                                studyPrefs.edit().putBoolean("user_swipe_to_refresh_enabled", enabled).apply()
                                Toast.makeText(
                                    context,
                                    if (enabled) "Swipe to Refresh Enabled" else "Swipe to Refresh Disabled (Content Protected)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onRefreshPage = {
                                isWebOrbMenuOpen = false
                                hasError = false
                                isLoading = true
                                webView?.reload()
                            },
                            onGoBack = {
                                val wv = webView
                                if (wv?.canGoBack() == true) {
                                    try {
                                        val history = wv.copyBackForwardList()
                                        if (history.currentIndex > 0) {
                                            val prevUrl = history.getItemAtIndex(history.currentIndex - 1)?.url
                                            if (!prevUrl.isNullOrBlank() && prevUrl != "about:blank") {
                                                currentLoadedUrl = prevUrl
                                            }
                                        }
                                    } catch (_: Throwable) {}
                                    wv.goBack()
                                    canGoBack = wv.canGoBack()
                                    canGoForward = wv.canGoForward()
                                    wv.postDelayed({ syncMainFrameUrl(wv) }, 60)
                                }
                                isWebOrbMenuOpen = false
                            },
                            onGoForward = {
                                val wv = webView
                                if (wv?.canGoForward() == true) {
                                    wv.goForward()
                                    canGoBack = wv.canGoBack()
                                    canGoForward = wv.canGoForward()
                                    wv.postDelayed({ syncMainFrameUrl(wv) }, 60)
                                }
                                isWebOrbMenuOpen = false
                            },
                            onScrollToTop = {
                                isWebOrbMenuOpen = false
                                webView?.evaluateJavascript(
                                    """
                                    (function() {
                                        window.scrollTo({ top: 0, behavior: 'smooth' });
                                        if (document.documentElement) document.documentElement.scrollTop = 0;
                                        if (document.body) document.body.scrollTop = 0;
                                        var containers = document.querySelectorAll('main, [data-radix-scroll-area-viewport], .overflow-y-auto, .overflow-auto');
                                        for (var i = 0; i < containers.length; i++) {
                                            containers[i].scrollTo({ top: 0, behavior: 'smooth' });
                                        }
                                        if (window.AndroidApp && window.AndroidApp.reportScrollState) {
                                            window.AndroidApp.reportScrollState(false);
                                        }
                                    })();
                                    """.trimIndent(),
                                    null
                                )
                                scrollGuard.isWebPageScrolledDown = false
                                scrollGuard.touchStartedAtTop = true
                            },
                            onClose = { isWebOrbMenuOpen = false },
                            modifier = Modifier.onSizeChanged { webMenuSize = it }
                        )
                    }
                }

                // Always-Visible Movable Web Browsing Ball (Reads webBallOffsetX/Y strictly inside layout offset lambda for 0 recompositions on drag)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset {
                            val bx = if (webBallOffsetX < 0f) defaultBallX else webBallOffsetX.coerceIn(minBallX, maxBallX)
                            val by = if (webBallOffsetY < 0f) defaultBallY else webBallOffsetY.coerceIn(minBallY, maxBallY)
                            IntOffset(bx.roundToInt(), by.roundToInt())
                        }
                        .pointerInput(minBallX, maxBallX, minBallY, maxBallY) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val baseX = if (webBallOffsetX < 0f) defaultBallX else webBallOffsetX.coerceIn(minBallX, maxBallX)
                                val baseY = if (webBallOffsetY < 0f) defaultBallY else webBallOffsetY.coerceIn(minBallY, maxBallY)
                                webBallOffsetX = (baseX + dragAmount.x).coerceIn(minBallX, maxBallX)
                                webBallOffsetY = (baseY + dragAmount.y).coerceIn(minBallY, maxBallY)
                            }
                        }
                ) {
                    LiquidGlassOrbButton(
                        onClick = { isWebOrbMenuOpen = !isWebOrbMenuOpen },
                        isOpen = isWebOrbMenuOpen,
                        pageText = null,
                        accentColor = Color(0xFF38BDF8)
                    )
                }
            }
            
            if (customView != null) {
                // Immersive Full Screen Video Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            android.widget.FrameLayout(ctx).apply {
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setBackgroundColor(android.graphics.Color.BLACK)
                                
                                customView?.let { cv ->
                                    (cv.parent as? android.view.ViewGroup)?.removeView(cv)
                                    addView(cv, android.widget.FrameLayout.LayoutParams(
                                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                        android.view.Gravity.CENTER
                                    ))
                                }
                            }
                        },
                        onRelease = { container ->
                            container.removeAllViews()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

@Composable
fun LoadingOverlay(progress: Float) {
    val context = LocalContext.current
    val perfProfile = remember { DevicePerformanceProfile.get(context) }
    val infiniteTransition = rememberInfiniteTransition(label = "RealisticAuraLoader")

    // Smooth atmospheric aura & silk wave phase
    val auraPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "AuraPhase"
    )

    // Secondary harmonic wave phase for layered volumetric curtains
    val harmonicPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(6800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "HarmonicPhase"
    )

    // Primary orbital rotation (clockwise)
    val primaryOrbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(5400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PrimaryOrbitAngle"
    )

    // Secondary orbital rotation (counter-clockwise)
    val counterOrbitAngle by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "CounterOrbitAngle"
    )

    // Subtle realistic breathing depth for core emblem & volumetric aura
    val auraBreath by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(2100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AuraBreath"
    )

    // Expanding volumetric light horizon pulse (dual staggered shockwaves)
    val horizonPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "HorizonPulse"
    )

    // Specular light sheen sweeping across glass surfaces
    val shimmerSweep by infiniteTransition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerSweep"
    )

    // Smoothly interpolated progress value for realistic dial & bar motion
    val clampedProgress = progress.coerceIn(0f, 1f)
    val displayPercent = (clampedProgress * 100).toInt().coerceIn(8, 99)
    val effectiveSweepProgress = clampedProgress.coerceIn(0.14f, 0.98f)

    val statusStageText = remember(displayPercent) {
        when {
            displayPercent < 32 -> "Initializing Neural Study Core..."
            displayPercent < 68 -> "Synchronizing Learning Workspace..."
            else -> "Rendering Interactive Environment..."
        }
    }

    val bookLeftPath = remember { Path() }
    val bookRightPath = remember { Path() }
    val prismPath = remember { Path() }
    val topCurtainPath1 = remember { Path() }
    val topCurtainPath2 = remember { Path() }
    val bottomCurtainPath1 = remember { Path() }
    val bottomCurtainPath2 = remember { Path() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF03050B)),
        contentAlignment = Alignment.Center
    ) {
        // 1. REALISTIC MULTI-LAYER VOLUMETRIC AURA & ATMOSPHERIC DEPTH CANVAS
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val minDim = minOf(width, height)
            val maxDim = maxOf(width, height)
            val centerX = width / 2f
            val centerY = height * 0.43f

            // Deep obsidian-midnight base gradient
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF02040A),
                        Color(0xFF080E20),
                        Color(0xFF050814),
                        Color(0xFF020308)
                    )
                )
            )

            // Ambient upper-left cyan nebula bloom
            val neb1X = width * 0.20f + sin(auraPhase) * (width * 0.12f)
            val neb1Y = height * 0.20f + cos(harmonicPhase) * (height * 0.08f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF00E5FF).copy(alpha = 0.18f),
                        Color(0xFF0284C7).copy(alpha = 0.07f),
                        Color.Transparent
                    ),
                    center = Offset(neb1X, neb1Y),
                    radius = maxDim * 0.58f
                ),
                center = Offset(neb1X, neb1Y),
                radius = maxDim * 0.58f
            )

            // Ambient lower-right royal violet nebula bloom
            val neb2X = width * 0.80f - cos(auraPhase) * (width * 0.12f)
            val neb2Y = height * 0.74f - sin(harmonicPhase) * (height * 0.08f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFA855F7).copy(alpha = 0.16f),
                        Color(0xFF4F46E5).copy(alpha = 0.07f),
                        Color.Transparent
                    ),
                    center = Offset(neb2X, neb2Y),
                    radius = maxDim * 0.58f
                ),
                center = Offset(neb2X, neb2Y),
                radius = maxDim * 0.58f
            )

            // Volumetric Top Aurora Silk Curtains
            val step = (width / perfProfile.waveStepDivisor).coerceAtLeast(14f)
            topCurtainPath1.rewind()
            topCurtainPath1.moveTo(0f, 0f)
            topCurtainPath1.lineTo(0f, height * 0.28f)
            var x = 0f
            while (x <= width + step) {
                val y = height * 0.28f +
                        sin(x * 0.0045f + auraPhase) * (height * 0.065f) +
                        cos(x * 0.008f - harmonicPhase) * (height * 0.032f)
                topCurtainPath1.lineTo(x, y)
                x += step
            }
            topCurtainPath1.lineTo(width, 0f)
            topCurtainPath1.close()
            drawPath(
                path = topCurtainPath1,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF00E5FF).copy(alpha = 0.20f),
                        Color(0xFF4F46E5).copy(alpha = 0.09f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = height * 0.38f
                )
            )

            topCurtainPath2.rewind()
            topCurtainPath2.moveTo(0f, height * 0.22f)
            x = 0f
            while (x <= width + step) {
                val y = height * 0.22f +
                        cos(x * 0.0055f - auraPhase * 1.2f) * (height * 0.05f) +
                        sin(x * 0.009f + harmonicPhase) * (height * 0.025f)
                topCurtainPath2.lineTo(x, y)
                x += step
            }
            drawPath(
                path = topCurtainPath2,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFF38BDF8).copy(alpha = 0.34f),
                        Color(0xFFA855F7).copy(alpha = 0.30f),
                        Color.Transparent
                    )
                ),
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Volumetric Bottom Aurora Silk Curtains
            bottomCurtainPath1.rewind()
            bottomCurtainPath1.moveTo(0f, height)
            bottomCurtainPath1.lineTo(0f, height * 0.74f)
            x = 0f
            while (x <= width + step) {
                val y = height * 0.74f +
                        sin(x * 0.0042f + harmonicPhase) * (height * 0.065f) +
                        cos(x * 0.0075f - auraPhase) * (height * 0.03f)
                bottomCurtainPath1.lineTo(x, y)
                x += step
            }
            bottomCurtainPath1.lineTo(width, height)
            bottomCurtainPath1.close()
            drawPath(
                path = bottomCurtainPath1,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFF6366F1).copy(alpha = 0.11f),
                        Color(0xFFA855F7).copy(alpha = 0.22f)
                    ),
                    startY = height * 0.64f,
                    endY = height
                )
            )

            bottomCurtainPath2.rewind()
            bottomCurtainPath2.moveTo(0f, height * 0.78f)
            x = 0f
            while (x <= width + step) {
                val y = height * 0.78f +
                        cos(x * 0.005f + auraPhase) * (height * 0.048f) +
                        sin(x * 0.0085f - harmonicPhase) * (height * 0.024f)
                bottomCurtainPath2.lineTo(x, y)
                x += step
            }
            drawPath(
                path = bottomCurtainPath2,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFFC084FC).copy(alpha = 0.30f),
                        Color(0xFF00E5FF).copy(alpha = 0.32f),
                        Color.Transparent
                    )
                ),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Ascending & Orbiting Luminous Photon Particles (device-adaptive count)
            for (i in 0 until perfProfile.auraParticleCount) {
                val seed = i * 83.7f
                val horizontalWave = sin(auraPhase + i * 0.6f) * 32f
                val sx = ((sin(seed) * 0.5f + 0.5f) * width + horizontalWave + width) % width
                val upwardFlow = (auraPhase / (2 * PI).toFloat()) * height * 0.35f
                val sy = (((cos(seed * 1.3f) * 0.5f + 0.5f) * height - upwardFlow) + height * 2f) % height
                val twinkle = (sin(auraPhase * 2.2f + seed) * 0.5f + 0.5f).coerceIn(0.20f, 0.95f)
                val orbCoreR = (1.5f + (i % 3) * 0.9f).dp.toPx()
                val orbColor = when (i % 3) {
                    0 -> Color(0xFF00E5FF)
                    1 -> Color(0xFF818CF8)
                    else -> Color(0xFFE879F9)
                }
                // Soft outer photon bloom
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            orbColor.copy(alpha = twinkle * 0.42f),
                            Color.Transparent
                        ),
                        center = Offset(sx, sy),
                        radius = orbCoreR * 3.6f
                    ),
                    center = Offset(sx, sy),
                    radius = orbCoreR * 3.6f
                )
                // Crisp bright photon core
                drawCircle(
                    color = Color.White.copy(alpha = twinkle * 0.85f),
                    radius = orbCoreR * 0.75f,
                    center = Offset(sx, sy)
                )
            }

            // CORE VOLUMETRIC AURA LAYERS (Behind the 3D Medallion)
            // Layer A: Wide outer atmospheric halo
            val outerAuraRadius = (minDim * 0.48f * auraBreath).coerceAtLeast(20f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF00E5FF).copy(alpha = 0.25f),
                        Color(0xFF6366F1).copy(alpha = 0.16f),
                        Color(0xFFA855F7).copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = outerAuraRadius
                ),
                center = Offset(centerX, centerY),
                radius = outerAuraRadius
            )

            // Layer B1 & B2: Dual staggered expanding light horizon rings
            for (waveIdx in 0..1) {
                val wavePhase = (horizonPulse + waveIdx * 0.5f) % 1f
                val pulseRadius = (minDim * 0.16f) + (minDim * 0.34f * wavePhase)
                val pulseAlpha = (1f - wavePhase).coerceIn(0f, 1f) * 0.36f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF00E5FF).copy(alpha = pulseAlpha * 0.55f),
                            Color(0xFF818CF8).copy(alpha = pulseAlpha),
                            Color.Transparent
                        ),
                        center = Offset(centerX, centerY),
                        radius = pulseRadius.coerceAtLeast(12f)
                    ),
                    center = Offset(centerX, centerY),
                    radius = pulseRadius.coerceAtLeast(12f)
                )
            }

            // Layer C: Intense inner corona glow directly behind medallion
            val innerCoronaRadius = (minDim * 0.24f / auraBreath).coerceAtLeast(16f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF00E5FF).copy(alpha = 0.42f),
                        Color(0xFF818CF8).copy(alpha = 0.24f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = innerCoronaRadius
                ),
                center = Offset(centerX, centerY),
                radius = innerCoronaRadius
            )
        }

        // 2. FOREGROUND CONTENT: 3D MEDALLION + FROSTED GLASS TELEMETRY CARD
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
        ) {
            // 3D LAYERED GLASS MEDALLION, GYROSCOPIC ORBITS & PRECISION CHRONOGRAPH RINGS
            Box(
                modifier = Modifier
                    .scale(auraBreath)
                    .size(226.dp),
                contentAlignment = Alignment.Center
            ) {
                // Concentric Orbital Rings, 3D Gyroscopic Ellipses & Precision Micro-Ticks
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val maxR = size.minDimension / 2f

                    // 3D Perspective Gyroscopic Energy Rings (2 tilted 3D orbital planes)
                    for (g in 0..1) {
                        val tiltAngle = if (g == 0) primaryOrbitAngle + 28f else counterOrbitAngle - 32f
                        val rx = maxR * 0.94f
                        val ry = maxR * 0.36f
                        val numSegments = perfProfile.gyroRingSegments
                        for (s in 0 until numSegments) {
                            val a1 = (s * (360f / numSegments)) * (PI / 180.0)
                            val a2 = ((s + 1) * (360f / numSegments)) * (PI / 180.0)
                            val tiltRad = tiltAngle * (PI / 180.0)
                            val cosT = cos(tiltRad).toFloat()
                            val sinT = sin(tiltRad).toFloat()

                            val x1Local = cos(a1).toFloat() * rx
                            val y1Local = sin(a1).toFloat() * ry
                            val x2Local = cos(a2).toFloat() * rx
                            val y2Local = sin(a2).toFloat() * ry

                            val x1 = cx + (x1Local * cosT - y1Local * sinT)
                            val y1 = cy + (x1Local * sinT + y1Local * cosT)
                            val x2 = cx + (x2Local * cosT - y2Local * sinT)
                            val y2 = cy + (x2Local * sinT + y2Local * cosT)

                            val depthAlpha = ((sin(a1).toFloat() + 1f) * 0.5f).coerceIn(0.08f, 0.55f)
                            drawLine(
                                color = if (g == 0) Color(0xFF00E5FF).copy(alpha = depthAlpha * 0.65f)
                                else Color(0xFFC084FC).copy(alpha = depthAlpha * 0.60f),
                                start = Offset(x1, y1),
                                end = Offset(x2, y2),
                                strokeWidth = 1.4.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }

                        // Orbiting photon satellite on each 3D gyroscopic ring
                        val satAngle = (if (g == 0) primaryOrbitAngle * 2.2f else counterOrbitAngle * 2.0f) * (PI / 180.0)
                        val tiltRad = tiltAngle * (PI / 180.0)
                        val cosT = cos(tiltRad).toFloat()
                        val sinT = sin(tiltRad).toFloat()
                        val sxLocal = cos(satAngle).toFloat() * rx
                        val syLocal = sin(satAngle).toFloat() * ry
                        val satX = cx + (sxLocal * cosT - syLocal * sinT)
                        val satY = cy + (sxLocal * sinT + syLocal * cosT)

                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White,
                                    if (g == 0) Color(0xFF00E5FF) else Color(0xFFE879F9),
                                    Color.Transparent
                                ),
                                center = Offset(satX, satY),
                                radius = 7.dp.toPx()
                            ),
                            center = Offset(satX, satY),
                            radius = 7.dp.toPx()
                        )
                    }

                    // Ring 1: Outer 60-Tick Precision Chronograph Dial
                    val tickOuterR = maxR - 4.dp.toPx()
                    val tickInnerMajorR = tickOuterR - 7.5.dp.toPx()
                    val tickInnerMinorR = tickOuterR - 4.dp.toPx()
                    val activeTickLimit = (effectiveSweepProgress * 60).toInt()

                    for (t in 0 until 60) {
                        val deg = t * 6f - 90f
                        val rad = deg * (PI / 180.0)
                        val isMajor = t % 5 == 0
                        val innerR = if (isMajor) tickInnerMajorR else tickInnerMinorR
                        val sx = cx + cos(rad).toFloat() * innerR
                        val sy = cy + sin(rad).toFloat() * innerR
                        val ex = cx + cos(rad).toFloat() * tickOuterR
                        val ey = cy + sin(rad).toFloat() * tickOuterR
                        val isActiveTick = t <= activeTickLimit

                        drawLine(
                            color = when {
                                isActiveTick && isMajor -> Color(0xFF00E5FF).copy(alpha = 0.95f)
                                isActiveTick -> Color(0xFF818CF8).copy(alpha = 0.65f)
                                isMajor -> Color.White.copy(alpha = 0.24f)
                                else -> Color.White.copy(alpha = 0.10f)
                            },
                            start = Offset(sx, sy),
                            end = Offset(ex, ey),
                            strokeWidth = if (isMajor) 1.9.dp.toPx() else 1.1.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    // Ring 2: Counter-Rotating Dual-Arc Specular Aura Ring
                    val orbitRingR = maxR - 17.dp.toPx()
                    drawCircle(
                        color = Color.White.copy(alpha = 0.07f),
                        radius = orbitRingR,
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF38BDF8).copy(alpha = 0.85f),
                                Color(0xFFC084FC).copy(alpha = 0.90f),
                                Color.Transparent
                            )
                        ),
                        startAngle = counterOrbitAngle,
                        sweepAngle = 145f,
                        useCenter = false,
                        topLeft = Offset(cx - orbitRingR, cy - orbitRingR),
                        size = Size(orbitRingR * 2f, orbitRingR * 2f),
                        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF00E5FF).copy(alpha = 0.80f),
                                Color(0xFF6366F1).copy(alpha = 0.85f),
                                Color.Transparent
                            )
                        ),
                        startAngle = counterOrbitAngle + 180f,
                        sweepAngle = 115f,
                        useCenter = false,
                        topLeft = Offset(cx - orbitRingR, cy - orbitRingR),
                        size = Size(orbitRingR * 2f, orbitRingR * 2f),
                        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Ring 3: Primary Live Progress Arc with Glowing Leading Orb
                    val progressRingR = maxR - 28.dp.toPx()
                    val sweepDeg = effectiveSweepProgress * 360f
                    drawCircle(
                        color = Color.White.copy(alpha = 0.09f),
                        radius = progressRingR,
                        style = Stroke(width = 3.4.dp.toPx())
                    )
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color(0xFF00E5FF),
                                Color(0xFF38BDF8),
                                Color(0xFF818CF8),
                                Color(0xFFC084FC),
                                Color(0xFF00E5FF)
                            )
                        ),
                        startAngle = -90f + primaryOrbitAngle * 0.2f,
                        sweepAngle = sweepDeg,
                        useCenter = false,
                        topLeft = Offset(cx - progressRingR, cy - progressRingR),
                        size = Size(progressRingR * 2f, progressRingR * 2f),
                        style = Stroke(width = 3.8.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Leading light point at tip of progress arc
                    val tipAngleRad = (-90f + primaryOrbitAngle * 0.2f + sweepDeg) * (PI / 180.0)
                    val tipX = cx + cos(tipAngleRad).toFloat() * progressRingR
                    val tipY = cy + sin(tipAngleRad).toFloat() * progressRingR
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White,
                                Color(0xFF00E5FF).copy(alpha = 0.85f),
                                Color.Transparent
                            ),
                            center = Offset(tipX, tipY),
                            radius = 10.dp.toPx()
                        ),
                        center = Offset(tipX, tipY),
                        radius = 10.dp.toPx()
                    )
                    drawCircle(
                        color = Color.White,
                        center = Offset(tipX, tipY),
                        radius = 3.dp.toPx()
                    )
                }

                // 3D OBSIDIAN & LIQUID-GLASS CORE MEDALLION
                Box(
                    modifier = Modifier
                        .size(138.dp)
                        .shadow(
                            elevation = 28.dp,
                            shape = CircleShape,
                            ambientColor = Color(0xFF00E5FF),
                            spotColor = Color(0xFF6366F1)
                        )
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.55f),
                                    Color(0xFF00E5FF).copy(alpha = 0.48f),
                                    Color(0xFF1E293B).copy(alpha = 0.92f),
                                    Color(0xFFA855F7).copy(alpha = 0.50f)
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(300f, 300f)
                            )
                        )
                        .padding(2.4.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF192542),
                                    Color(0xFF0B1120),
                                    Color(0xFF050811)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Custom 3D Specular Lens Reflection + Geometric StudyVerse Book & Prism Crest
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val cx = w / 2f
                        val cy = h / 2f

                        // Subtle inner ambient core glow
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF00E5FF).copy(alpha = 0.26f),
                                    Color(0xFF6366F1).copy(alpha = 0.10f),
                                    Color.Transparent
                                ),
                                center = Offset(cx, cy - h * 0.06f),
                                radius = w * 0.44f
                            ),
                            center = Offset(cx, cy - h * 0.06f),
                            radius = w * 0.44f
                        )

                        // Realistic Top-Left Curved Glass Specular Highlight
                        drawArc(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.42f),
                                    Color.White.copy(alpha = 0.08f),
                                    Color.Transparent
                                ),
                                start = Offset(w * 0.15f, h * 0.10f),
                                end = Offset(w * 0.65f, h * 0.55f)
                            ),
                            startAngle = 195f,
                            sweepAngle = 120f,
                            useCenter = false,
                            topLeft = Offset(w * 0.07f, h * 0.07f),
                            size = Size(w * 0.86f, h * 0.86f),
                            style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // Custom Geometric Open Book Wings & Levitating Knowledge Prism
                        val crestCenterY = cy - h * 0.05f
                        val bookW = w * 0.24f
                        val bookH = h * 0.18f
                        val prismFloatOffset = sin(auraPhase * 2f) * (h * 0.025f)

                        // Lower Secondary Book Page Accents (matches App Launcher Icon)
                        bookLeftPath.rewind()
                        bookLeftPath.moveTo(cx - w * 0.01f, crestCenterY + bookH * 0.72f)
                        bookLeftPath.lineTo(cx - bookW * 0.94f, crestCenterY + bookH * 0.42f)
                        bookLeftPath.lineTo(cx - bookW * 0.94f, crestCenterY + bookH * 0.28f)
                        bookLeftPath.lineTo(cx - w * 0.01f, crestCenterY + bookH * 0.58f)
                        bookLeftPath.close()
                        drawPath(
                            path = bookLeftPath,
                            color = Color(0xFF00E5FF).copy(alpha = 0.42f)
                        )

                        bookRightPath.rewind()
                        bookRightPath.moveTo(cx + w * 0.01f, crestCenterY + bookH * 0.72f)
                        bookRightPath.lineTo(cx + bookW * 0.94f, crestCenterY + bookH * 0.42f)
                        bookRightPath.lineTo(cx + bookW * 0.94f, crestCenterY + bookH * 0.28f)
                        bookRightPath.lineTo(cx + w * 0.01f, crestCenterY + bookH * 0.58f)
                        bookRightPath.close()
                        drawPath(
                            path = bookRightPath,
                            color = Color(0xFFC084FC).copy(alpha = 0.42f)
                        )

                        // Left Illuminated Book Wing
                        bookLeftPath.rewind()
                        bookLeftPath.moveTo(cx - w * 0.008f, crestCenterY + bookH * 0.52f)
                        bookLeftPath.lineTo(cx - bookW, crestCenterY + bookH * 0.18f)
                        bookLeftPath.lineTo(cx - bookW, crestCenterY - bookH * 0.55f)
                        bookLeftPath.lineTo(cx - w * 0.008f, crestCenterY - bookH * 0.15f)
                        bookLeftPath.close()
                        drawPath(
                            path = bookLeftPath,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF00E5FF),
                                    Color(0xFF38BDF8).copy(alpha = 0.82f)
                                ),
                                start = Offset(cx - bookW, crestCenterY - bookH),
                                end = Offset(cx, crestCenterY + bookH)
                            )
                        )

                        // Right Illuminated Book Wing
                        bookRightPath.rewind()
                        bookRightPath.moveTo(cx + w * 0.008f, crestCenterY + bookH * 0.52f)
                        bookRightPath.lineTo(cx + bookW, crestCenterY + bookH * 0.18f)
                        bookRightPath.lineTo(cx + bookW, crestCenterY - bookH * 0.55f)
                        bookRightPath.lineTo(cx + w * 0.008f, crestCenterY - bookH * 0.15f)
                        bookRightPath.close()
                        drawPath(
                            path = bookRightPath,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF818CF8),
                                    Color(0xFFC084FC).copy(alpha = 0.90f)
                                ),
                                start = Offset(cx, crestCenterY - bookH),
                                end = Offset(cx + bookW, crestCenterY + bookH)
                            )
                        )

                        // Levitating Diamond Knowledge Prism above the Book Spine
                        val prismTopY = crestCenterY - bookH * 1.18f + prismFloatOffset
                        val prismBottomY = crestCenterY - bookH * 0.34f + prismFloatOffset
                        val prismMidY = (prismTopY + prismBottomY) / 2f
                        val prismHalfW = w * 0.088f

                        prismPath.rewind()
                        prismPath.moveTo(cx, prismTopY)
                        prismPath.lineTo(cx + prismHalfW, prismMidY)
                        prismPath.lineTo(cx, prismBottomY)
                        prismPath.lineTo(cx - prismHalfW, prismMidY)
                        prismPath.close()

                        drawPath(
                            path = prismPath,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White,
                                    Color(0xFF00E5FF),
                                    Color(0xFFC084FC)
                                ),
                                start = Offset(cx - prismHalfW, prismTopY),
                                end = Offset(cx + prismHalfW, prismBottomY)
                            )
                        )

                        // Inner Bright Core Facet of Knowledge Prism
                        val innerPrismHalfW = prismHalfW * 0.54f
                        val innerPrismTopY = prismTopY + (prismBottomY - prismTopY) * 0.16f
                        val innerPrismBottomY = prismBottomY - (prismBottomY - prismTopY) * 0.28f
                        prismPath.rewind()
                        prismPath.moveTo(cx, innerPrismTopY)
                        prismPath.lineTo(cx + innerPrismHalfW, prismMidY)
                        prismPath.lineTo(cx, innerPrismBottomY)
                        prismPath.lineTo(cx - innerPrismHalfW, prismMidY)
                        prismPath.close()
                        drawPath(
                            path = prismPath,
                            color = Color.White.copy(alpha = 0.72f)
                        )

                        // Dynamic Specular Shimmer Sweep across Medallion Lens
                        val sweepX = w * shimmerSweep
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.18f),
                                    Color.Transparent
                                ),
                                center = Offset(sweepX, h * 0.35f),
                                radius = w * 0.45f
                            ),
                            center = Offset(sweepX, h * 0.35f),
                            radius = w * 0.45f
                        )
                    }

                    // Crisp Typography Lockup inside Bottom of Medallion
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 20.dp)
                    ) {
                        Text(
                            text = "STUDYVERSE",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 9.5.sp,
                                letterSpacing = 1.8.sp
                            ),
                            color = Color(0xFFF8FAFC)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(34.dp))

            // 3. REALISTIC FROSTED GLASS TELEMETRY & PROGRESS PILL
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .widthIn(min = 270.dp, max = 340.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF151F36).copy(alpha = 0.82f),
                                Color(0xFF090E1A).copy(alpha = 0.92f)
                            )
                        )
                    )
                    .border(
                        BorderStroke(
                            1.1.dp,
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.32f),
                                    Color(0xFF00E5FF).copy(alpha = 0.28f),
                                    Color(0xFFA855F7).copy(alpha = 0.18f)
                                )
                            )
                        ),
                        RoundedCornerShape(24.dp)
                    )
                    .padding(horizontal = 22.dp, vertical = 18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .scale(auraBreath)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E5FF))
                        )
                        Text(
                            text = "STUDYVERSE AURA",
                            color = Color(0xFFBAE6FD),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.4.sp
                        )
                    }

                    Text(
                        text = "$displayPercent%",
                        color = Color(0xFF38BDF8),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Realistic Glass Progress Bar with Animated Specular Fill
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(effectiveSweepProgress)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF00E5FF),
                                        Color(0xFF38BDF8),
                                        Color(0xFF818CF8),
                                        Color(0xFFC084FC)
                                    )
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = statusStageText,
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun isNetworkAvailable(context: Context): Boolean {
    return NetworkMonitor.isOnline(context)
}

/**
 * Chrome-style compact Floating Ball Menu for WebView with:
 * - Zero site/domain name clutter
 * - Refresh Current Page primary button + Close button
 * - Swipe-to-Refresh Protection Switch (OFF by default so scrolling upward never wipes out page content)
 * - 3-Column Chrome Quick Navigation Bar (Back, Forward, Scroll Top — no PDF Opener column)
 */
@Composable
fun WebFloatingBallChromeMenu(
    canGoBack: Boolean,
    canGoForward: Boolean,
    swipeToRefreshEnabled: Boolean,
    onToggleSwipeToRefresh: (Boolean) -> Unit,
    onRefreshPage: () -> Unit,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onScrollToTop: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val menuBgBrush = remember {
        Brush.verticalGradient(
            listOf(
                Color(0xFF1B2436),
                Color(0xFF101726),
                Color(0xFF0A0F1A)
            )
        )
    }
    val menuBorderBrush = remember {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.28f),
                Color.White.copy(alpha = 0.08f)
            )
        )
    }
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color.Transparent,
        shadowElevation = 6.dp,
        modifier = modifier.widthIn(max = 340.dp)
    ) {
        Box(
            modifier = Modifier
                .background(menuBgBrush)
                .border(BorderStroke(1.1.dp, menuBorderBrush), RoundedCornerShape(26.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { /* Consume clicks inside menu */ }
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(11.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. Primary Chrome-style Refresh Page Button + Close Button Row (No site name displayed)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        onClick = onRefreshPage,
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF38BDF8).copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.42f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 11.dp, horizontal = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Page",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Refresh Current Page",
                                color = Color(0xFFF8FAFC),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        onClick = onClose,
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.18f))
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Menu",
                                tint = Color(0xFFE2E8F0),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }

                // 2. Swipe-to-Refresh Protection Switch Row
                Surface(
                    onClick = { onToggleSwipeToRefresh(!swipeToRefreshEnabled) },
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    border = BorderStroke(
                        0.9.dp,
                        if (swipeToRefreshEnabled) Color(0xFF38BDF8).copy(alpha = 0.40f)
                        else Color.White.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 13.dp, vertical = 9.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwipeVertical,
                                contentDescription = null,
                                tint = if (swipeToRefreshEnabled) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Swipe to Refresh",
                                    color = Color(0xFFF8FAFC),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (swipeToRefreshEnabled) "ON · Pull from top edge to reload"
                                    else "OFF · Scroll up safely without reload",
                                    color = if (swipeToRefreshEnabled) Color(0xFF38BDF8) else Color(0xFF34D399),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Switch(
                            checked = swipeToRefreshEnabled,
                            onCheckedChange = onToggleSwipeToRefresh,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF090D16),
                                checkedTrackColor = Color(0xFF38BDF8),
                                uncheckedThumbColor = Color(0xFFE2E8F0),
                                uncheckedTrackColor = Color(0xFF1E293B)
                            ),
                            modifier = Modifier.scale(0.85f)
                        )
                    }
                }

                // 3. Compact 3-Column Chrome Quick Actions Row (Back, Forward, Top)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    WebQuickActionItem(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        label = "Back",
                        enabled = canGoBack,
                        accentColor = Color(0xFF38BDF8),
                        onClick = onGoBack,
                        modifier = Modifier.weight(1f)
                    )

                    WebQuickActionItem(
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        label = "Forward",
                        enabled = canGoForward,
                        accentColor = Color(0xFF38BDF8),
                        onClick = onGoForward,
                        modifier = Modifier.weight(1f)
                    )

                    WebQuickActionItem(
                        icon = Icons.Default.KeyboardArrowUp,
                        label = "Top",
                        enabled = true,
                        accentColor = Color(0xFF34D399),
                        onClick = onScrollToTop,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun WebQuickActionItem(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alpha = if (enabled) 1f else 0.38f
    Surface(
        onClick = { if (enabled) onClick() },
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.06f * alpha),
        border = BorderStroke(0.9.dp, Color.White.copy(alpha = 0.14f * alpha)),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accentColor.copy(alpha = alpha),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = Color(0xFFF1F5F9).copy(alpha = alpha),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

