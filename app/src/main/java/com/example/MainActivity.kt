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

class MainActivity : ComponentActivity() {
    private var webViewInstance: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                WebViewScreen(
                    url = "https://study-verse-lime.vercel.app/",
                    savedState = savedInstanceState,
                    onWebViewCreated = { webViewInstance = it }
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webViewInstance?.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        webViewInstance?.restoreState(savedInstanceState)
    }
}

fun setupStudyVerseMediaSession(context: Context): MediaSession? {
    return try {
        val bitmap = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        MediaSession(context, "StudyVerseMedia").apply {
            val metadata = MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, "StudyVerse")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "StudyVerse Learning Platform")
                .putString(MediaMetadata.METADATA_KEY_ALBUM, "StudyVerse Audio")
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap)
                .putBitmap(MediaMetadata.METADATA_KEY_ART, bitmap)
                .build()
            setMetadata(metadata)
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
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// Outside composable, or just define it as a class accepting a lambda
class WebAppInterface(
    private val onColorReceived: (String) -> Unit,
    private val onSettingsUpdateReceived: (String) -> Unit = {},
    private val onChunkLoadErrorReceived: (String) -> Unit = {},
    private val onBlankContentReceived: () -> Unit = {}
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
}

@Composable
fun WebViewScreen(
    url: String,
    savedState: Bundle? = null,
    onWebViewCreated: (WebView) -> Unit = {}
) {
    var webView: WebView? by remember { mutableStateOf(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableStateOf(0f) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var currentLoadedUrl by remember { mutableStateOf(url) }
    var isPullToRefreshRuleEnabled by remember { mutableStateOf(true) }

    // Inbuilt PDF Opener State
    var activePdfSource by remember { mutableStateOf<String?>(null) }
    var activePdfTitle by remember { mutableStateOf<String?>(null) }

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
    val context = LocalContext.current
    val isOnline by NetworkMonitor.observeNetwork(context).collectAsState(initial = NetworkMonitor.isOnline(context))

    fun triggerSiteUpdateRecovery(reason: String) {
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

    LaunchedEffect(webView) {
        if (webView != null) {
            try {
                val remoteConfig = Firebase.remoteConfig
                val configSettings = remoteConfigSettings {
                    minimumFetchIntervalInSeconds = 3600
                }
                remoteConfig.setConfigSettingsAsync(configSettings)
                remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        try {
                            val customUserAgent = remoteConfig.getString("webview_user_agent")
                            if (customUserAgent.isNotEmpty()) {
                                webView?.settings?.userAgentString = customUserAgent
                            }
                            
                            if (remoteConfig.all.containsKey("webview_cache_mode")) {
                                webView?.settings?.cacheMode = remoteConfig.getLong("webview_cache_mode").toInt()
                            }
                            
                            if (remoteConfig.all.containsKey("webview_java_script_enabled")) {
                                webView?.settings?.javaScriptEnabled = remoteConfig.getBoolean("webview_java_script_enabled")
                            }
                            
                            if (remoteConfig.all.containsKey("webview_dom_storage_enabled")) {
                                webView?.settings?.domStorageEnabled = remoteConfig.getBoolean("webview_dom_storage_enabled")
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                // Firebase might not be initialized if google-services.json is missing
                e.printStackTrace()
            }
        }
    }

    DisposableEffect(context) {
        val mediaSession = setupStudyVerseMediaSession(context)
        onDispose {
            try {
                mediaSession?.isActive = false
                mediaSession?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val isInitialLink = !canGoBack || currentLoadedUrl.contains("study-verse-lime.vercel.app")

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

    // Handle system back button for PDF viewer OR custom view (full screen video) OR step-by-step internal WebView navigation
    BackHandler(enabled = activePdfSource != null || customView != null || canGoBack) {
        if (activePdfSource != null) {
            activePdfSource = null
            activePdfTitle = null
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
            webView?.goBack()
            canGoBack = webView?.canGoBack() == true
        }
    }

    // Root container with dynamically updated background
    var themeColor by remember { mutableStateOf(android.graphics.Color.parseColor("#0288D1")) }
        
        SideEffect {
            (context as? Activity)?.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)
                if (isInitialLink) {
                    window.statusBarColor = android.graphics.Color.TRANSPARENT
                    window.navigationBarColor = android.graphics.Color.TRANSPARENT
                } else {
                    window.statusBarColor = themeColor
                    window.navigationBarColor = themeColor
                }
                val isLight = (android.graphics.Color.red(themeColor) * 0.299 +
                        android.graphics.Color.green(themeColor) * 0.587 +
                        android.graphics.Color.blue(themeColor) * 0.114) > 186
                val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
                windowInsetsController.isAppearanceLightStatusBars = isLight
                windowInsetsController.isAppearanceLightNavigationBars = isLight
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(themeColor))
                    .then(
                        if (isInitialLink) Modifier else Modifier.windowInsetsPadding(WindowInsets.systemBars)
                    )
            ) {
                // Top Loading Progress Bar
                if (isLoading && progress < 1f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = Color(0xFFF44336),
                        trackColor = Color.Transparent
                    )
                }

                AndroidView(
                factory = { ctx ->
                    val density = ctx.resources.displayMetrics.density
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

                    fun applyDynamicSettings(targetUrl: String?, webView: WebView) {
                        if (targetUrl == null) return
                        try {
                            val prefs = ctx.getSharedPreferences("StudyVersePrefs", Context.MODE_PRIVATE)
                            val rulesJsonStr = prefs.getString("domain_rules", "{}")
                            val rulesJson = org.json.JSONObject(rulesJsonStr ?: "{}")
                            
                            var zoom = true
                            var pullToRefresh = true
                            var desktop = false
                            var jsEnabled = true
                            var domStorage = true
                            var allowAutoPlay = false
                            var hardwareAccel = true
                            var allowMixedContent = true
                            
                            // Check defaults
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
                            }
                            
                            // Check specific domains
                            val host = android.net.Uri.parse(targetUrl).host ?: ""
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
                                        break
                                    }
                                }
                            }
                            
                            // Apply settings
                            webView.settings.setSupportZoom(zoom)
                            webView.settings.builtInZoomControls = zoom
                            webView.settings.javaScriptEnabled = jsEnabled
                            webView.settings.domStorageEnabled = domStorage
                            webView.settings.mediaPlaybackRequiresUserGesture = !allowAutoPlay
                            webView.settings.mixedContentMode = if (allowMixedContent) android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW else android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            
                            if (hardwareAccel) {
                                webView.setLayerType(android.view.View.LAYER_TYPE_NONE, null)
                            } else {
                                webView.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                            }
                            
                            if (desktop) {
                                webView.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36"
                                webView.settings.loadWithOverviewMode = true
                                webView.settings.useWideViewPort = true
                            } else {
                                webView.settings.userAgentString = "Mozilla/5.0 (Linux; Android 16; Pixel 9 Pro Build/AP3A.241005.015) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36"
                            }
                            
                            val isAllowed = pullToRefresh && activePdfSource == null
                            isPullToRefreshRuleEnabled = isAllowed
                            swipeRefreshLayout.isEnabled = isAllowed
                            
                        } catch (e: Exception) {
                            e.printStackTrace()
                            // Safe fallbacks
                            webView.settings.setSupportZoom(true)
                            webView.settings.builtInZoomControls = true
                            webView.settings.javaScriptEnabled = true
                            webView.settings.domStorageEnabled = true
                            webView.settings.mediaPlaybackRequiresUserGesture = true
                            webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            webView.setLayerType(android.view.View.LAYER_TYPE_NONE, null)
                            webView.settings.userAgentString = "Mozilla/5.0 (Linux; Android 16; Pixel 9 Pro Build/AP3A.241005.015) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36"
                            val isAllowed = activePdfSource == null
                            isPullToRefreshRuleEnabled = isAllowed
                            swipeRefreshLayout.isEnabled = isAllowed
                        }
                    }

                    val webViewInstance = WebView(ctx).apply {
                        val jsInterface = WebAppInterface(
                            onColorReceived = { colorStr ->
                                try {
                                    val parsed = android.graphics.Color.parseColor(colorStr)
                                    (ctx as? Activity)?.runOnUiThread {
                                        themeColor = parsed
                                    }
                                } catch (e: Exception) { e.printStackTrace() }
                            },
                            onSettingsUpdateReceived = { jsonStr ->
                                (ctx as? Activity)?.runOnUiThread {
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
                                        
                                        applyDynamicSettings(this.url, this)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            },
                            onChunkLoadErrorReceived = { errorDetails ->
                                (ctx as? Activity)?.runOnUiThread {
                                    triggerSiteUpdateRecovery("chunk_error: $errorDetails")
                                }
                            },
                            onBlankContentReceived = {
                                (ctx as? Activity)?.runOnUiThread {
                                    triggerSiteUpdateRecovery("blank_body")
                                }
                            }
                        )
                        addJavascriptInterface(jsInterface, "AndroidApp")
                        addJavascriptInterface(jsInterface, "AndroidInterface")
                        
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        
                        // Native hardware-accelerated direct rendering (avoids offscreen buffer MESA issues)
                        setLayerType(View.LAYER_TYPE_NONE, null)
                        overScrollMode = View.OVER_SCROLL_NEVER
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false

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

                            userAgentString = "Mozilla/5.0 (Linux; Android 16; Pixel 9 Pro Build/AP3A.241005.015) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36"

                            // High-speed caching & smooth scrolling optimization: fallback to cache when offline
                            cacheMode = if (isOnline) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_CACHE_ELSE_NETWORK
                            loadsImagesAutomatically = true
                            offscreenPreRaster = true
                            @Suppress("DEPRECATION")
                            setRenderPriority(WebSettings.RenderPriority.HIGH)
                        }
                        
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        setDownloadListener { downloadUrl, userAgent, contentDisposition, mimeType, contentLength ->
                            var rawFileName = URLUtil.guessFileName(downloadUrl, contentDisposition, mimeType)
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

                            val finalMimeType = if (isPdf || finalFileName.endsWith(".pdf", ignoreCase = true)) "application/pdf" else (if (mimeType.isNullOrEmpty()) "application/octet-stream" else mimeType)

                            try {
                                val request = DownloadManager.Request(android.net.Uri.parse(downloadUrl)).apply {
                                    setMimeType(finalMimeType)
                                    addRequestHeader("User-Agent", userAgent)
                                    val cookie = CookieManager.getInstance().getCookie(downloadUrl)
                                    if (cookie != null) addRequestHeader("Cookie", cookie)
                                    setTitle("StudyVerse: $finalFileName")
                                    setDescription("Downloading $finalFileName...")
                                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, finalFileName)
                                }
                                val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                                dm?.enqueue(request)
                                Toast.makeText(ctx, "StudyVerse: Downloading $finalFileName to Downloads...", Toast.LENGTH_LONG).show()

                                // Load PDF in inbuilt PDF viewer with Liquid Glass UI
                                if (isPdf || finalFileName.endsWith(".pdf", ignoreCase = true)) {
                                    (ctx as? Activity)?.runOnUiThread {
                                        activePdfSource = downloadUrl
                                        activePdfTitle = finalFileName
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(ctx, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
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
                                progress = newProgress / 100f
                                if (newProgress >= 95) {
                                    isLoading = false
                                    swipeRefreshLayout.isRefreshing = false
                                }
                                canGoBack = view?.canGoBack() == true
                            }

                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: android.os.Message?
                            ): Boolean {
                                val newWebView = WebView(view!!.context)
                                newWebView.setLayerType(View.LAYER_TYPE_NONE, null)
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
                                    userAgentString = "Mozilla/5.0 (Linux; Android 16; Pixel 9 Pro Build/AP3A.241005.015) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36"
                                }
                                newWebView.webChromeClient = this
                                newWebView.webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(v: WebView?, req: WebResourceRequest?): Boolean {
                                        val targetUrl = req?.url?.toString() ?: return false
                                        if (targetUrl.endsWith(".pdf", ignoreCase = true) || 
                                            (targetUrl.contains(".pdf", ignoreCase = true) && !targetUrl.contains("html"))) {
                                            (ctx as? Activity)?.runOnUiThread {
                                                activePdfSource = targetUrl
                                                activePdfTitle = URLUtil.guessFileName(targetUrl, null, "application/pdf")
                                            }
                                        } else {
                                            view.loadUrl(targetUrl)
                                        }
                                        return true
                                    }
                                    
                                    @Suppress("DEPRECATION")
                                    override fun shouldOverrideUrlLoading(v: WebView?, urlStr: String?): Boolean {
                                        val targetUrl = urlStr ?: return false
                                        if (targetUrl.endsWith(".pdf", ignoreCase = true) || 
                                            (targetUrl.contains(".pdf", ignoreCase = true) && !targetUrl.contains("html"))) {
                                            (ctx as? Activity)?.runOnUiThread {
                                                activePdfSource = targetUrl
                                                activePdfTitle = URLUtil.guessFileName(targetUrl, null, "application/pdf")
                                            }
                                        } else {
                                            view.loadUrl(targetUrl)
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

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val requestUrl = request?.url?.toString() ?: return false
                                currentLoadedUrl = requestUrl

                                if (requestUrl.endsWith(".pdf", ignoreCase = true) || 
                                    (requestUrl.contains(".pdf", ignoreCase = true) && !requestUrl.contains("html"))) {
                                    val guessName = URLUtil.guessFileName(requestUrl, null, "application/pdf")
                                    (ctx as? Activity)?.runOnUiThread {
                                        activePdfSource = requestUrl
                                        activePdfTitle = guessName
                                    }
                                    return true
                                }

                                if (requestUrl.startsWith("http://") || requestUrl.startsWith("https://")) {
                                    return false // Let WebView handle it normally
                                }
                                try {
                                    val intent = if (requestUrl.startsWith("intent://")) {
                                        android.content.Intent.parseUri(requestUrl, android.content.Intent.URI_INTENT_SCHEME)
                                    } else {
                                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(requestUrl))
                                    }
                                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    ctx.startActivity(intent)
                                } catch (e: Exception) {
                                    if (requestUrl.startsWith("intent://")) {
                                        try {
                                            val parsedIntent = android.content.Intent.parseUri(requestUrl, android.content.Intent.URI_INTENT_SCHEME)
                                            val fallbackUrl = parsedIntent.getStringExtra("browser_fallback_url")
                                            if (fallbackUrl != null) {
                                                view?.loadUrl(fallbackUrl)
                                                return true
                                            }
                                            val packageName = parsedIntent.`package`
                                            if (packageName != null) {
                                                val playStoreIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=$packageName"))
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
                                return true
                            }

                            @Suppress("DEPRECATION")
                            override fun shouldOverrideUrlLoading(view: WebView?, urlStr: String?): Boolean {
                                if (urlStr == null) return false
                                currentLoadedUrl = urlStr

                                if (urlStr.endsWith(".pdf", ignoreCase = true) || 
                                    (urlStr.contains(".pdf", ignoreCase = true) && !urlStr.contains("html"))) {
                                    val guessName = URLUtil.guessFileName(urlStr, null, "application/pdf")
                                    (ctx as? Activity)?.runOnUiThread {
                                        activePdfSource = urlStr
                                        activePdfTitle = guessName
                                    }
                                    return true
                                }

                                if (urlStr.startsWith("http://") || urlStr.startsWith("https://")) {
                                    return false // Let WebView handle it normally
                                }
                                try {
                                    val intent = if (urlStr.startsWith("intent://")) {
                                        android.content.Intent.parseUri(urlStr, android.content.Intent.URI_INTENT_SCHEME)
                                    } else {
                                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(urlStr))
                                    }
                                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    ctx.startActivity(intent)
                                } catch (e: Exception) {
                                    if (urlStr.startsWith("intent://")) {
                                        try {
                                            val parsedIntent = android.content.Intent.parseUri(urlStr, android.content.Intent.URI_INTENT_SCHEME)
                                            val fallbackUrl = parsedIntent.getStringExtra("browser_fallback_url")
                                            if (fallbackUrl != null) {
                                                view?.loadUrl(fallbackUrl)
                                                return true
                                            }
                                            val packageName = parsedIntent.`package`
                                            if (packageName != null) {
                                                val playStoreIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=$packageName"))
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
                                return true
                            }

                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                canGoBack = view?.canGoBack() == true
                                if (url != null) currentLoadedUrl = url
                                view?.let { webView ->
                                    applyDynamicSettings(url, webView)
                                }
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                progress = 0f
                                if (url != null) currentLoadedUrl = url
                                
                                view?.let { webView ->
                                    applyDynamicSettings(url, webView)
                                }

                                // Install runtime ChunkLoadError listener early
                                view?.evaluateJavascript("""
                                    (function() {
                                        if (window.__studyverse_safety_guard_installed) return;
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
                                    })();
                                """.trimIndent(), null)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                swipeRefreshLayout.isRefreshing = false
                                canGoBack = view?.canGoBack() == true
                                if (url != null) currentLoadedUrl = url
                                
                                // Send theme color to match system bars
                                view?.evaluateJavascript("""
                                    (function() {
                                        function sendColor() {
                                            var metaThemeColor = document.querySelector('meta[name="theme-color"]');
                                            if (metaThemeColor && metaThemeColor.content) {
                                                window.AndroidInterface.postThemeColor(metaThemeColor.content);
                                            } else {
                                                var bgColor = window.getComputedStyle(document.body).backgroundColor;
                                                if (bgColor && bgColor.startsWith('rgb')) {
                                                    var match = bgColor.match(/^rgba?\((\d+),\s*(\d+),\s*(\d+)/);
                                                    if (match) {
                                                        var r = parseInt(match[1]).toString(16).padStart(2, '0');
                                                        var g = parseInt(match[2]).toString(16).padStart(2, '0');
                                                        var b = parseInt(match[3]).toString(16).padStart(2, '0');
                                                        window.AndroidInterface.postThemeColor('#' + r + g + b);
                                                    }
                                                }
                                            }
                                        }
                                        sendColor();
                                        var observer = new MutationObserver(function() {
                                            sendColor();
                                        });
                                        observer.observe(document.head, { childList: true, subtree: true, attributes: true });
                                        observer.observe(document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ['style', 'class'] });
                                    })();
                                """.trimIndent(), null)

                                // White screen / React blank body crash safety check after 1.5s
                                view?.postDelayed({
                                    if (!hasError && !isLoading) {
                                        view.evaluateJavascript("""
                                            (function() {
                                                var body = document.body;
                                                if (!body) return 'BLANK';
                                                var text = (body.innerText || '').trim();
                                                var elems = body.children.length;
                                                if (elems === 0 || (text.length === 0 && body.querySelectorAll('img, svg, canvas, iframe, button, a').length === 0)) {
                                                    return 'BLANK';
                                                }
                                                return 'OK';
                                            })();
                                        """.trimIndent()) { result ->
                                            if (result != null && result.contains("BLANK")) {
                                                triggerSiteUpdateRecovery("blank_body_after_load")
                                            }
                                        }
                                    }
                                }, 1500)
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
                    // Return true if child can scroll up, which blocks SwipeRefreshLayout from intercepting touches.
                    // SwipeRefresh is only permitted if WebView is stationary at the true top (scrollY <= 0) AND cannot scroll up (-1).
                    swipeRefreshLayout.setOnChildScrollUpCallback { _, _ ->
                        val child = swipeRefreshLayout.getChildAt(0) as? WebView
                        if (child == null) {
                            false
                        } else {
                            child.scrollY > 0 || child.canScrollVertically(-1)
                        }
                    }

                    swipeRefreshLayout.setOnRefreshListener {
                        hasError = false
                        // Do NOT clearCache(true); fast reload revalidates assets without heavy redownloads
                        webViewInstance.reload()
                    }

                    swipeRefreshLayout
                },
                update = { container ->
                    val childWebView = container.getChildAt(0) as? WebView
                    if (childWebView != null) {
                        webView = childWebView
                        canGoBack = childWebView.canGoBack()
                    }
                    container.isEnabled = isPullToRefreshRuleEnabled && activePdfSource == null
                },
                onRelease = { container ->
                    val childWebView = container.getChildAt(0) as? WebView
                    childWebView?.let {
                        it.stopLoading()
                        it.removeAllViews()
                        it.destroy()
                    }
                },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            } // Close Column

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

            // Full Screen Animated Butter Wave Landing / Processing Overlay with StudyVerse Logo
            AnimatedVisibility(
                visible = isLoading && !hasError,
                enter = fadeIn(animationSpec = tween(250)),
                exit = fadeOut(animationSpec = tween(350, easing = FastOutSlowInEasing))
            ) {
                LoadingOverlay(progress = progress)
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
    val infiniteTransition = rememberInfiniteTransition(label = "ButterWaveDetailed")
    
    // Wave Phase Animations for butter fluidity
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Phase1"
    )

    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(5500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Phase2"
    )

    val phase3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(7200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Phase3"
    )

    // Continuous Rotation for Glowing Shimmer Ring
    val rotateAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RotateAngle"
    )

    // Breathing Pulse for the Main StudyVerse Emblem
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    // Radial Ripple Wave Cycle
    val wavePulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavePulse"
    )

    // Floating particle drift offsets
    val particleTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ParticleTime"
    )

    val topWave1 = remember { Path() }
    val topWave2 = remember { Path() }
    val bottomWave1 = remember { Path() }
    val bottomWave2 = remember { Path() }
    val energyLine1 = remember { Path() }
    val energyLine2 = remember { Path() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080912)),
        contentAlignment = Alignment.Center
    ) {
        // Detailed Multi-Layer Butter Wave Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val maxDim = maxOf(width, height)
            val centerX = width / 2f
            val centerY = height / 2f

            // 1. Full screen shifting gradient background
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF06070D), Color(0xFF0F1322), Color(0xFF06070D)),
                    start = Offset(0f, sin(phase1) * height),
                    end = Offset(width, height - cos(phase2) * height)
                )
            )

            // 2. Massive animated ambient orbs for the full screen
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF80DEEA).copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(width * 0.15f + sin(phase1) * 150f, height * 0.15f + cos(phase2) * 150f),
                    radius = maxDim * 0.65f
                ),
                center = Offset(width * 0.15f + sin(phase1) * 150f, height * 0.15f + cos(phase2) * 150f),
                radius = maxDim * 0.65f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFE040FB).copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(width * 0.85f - cos(phase3) * 150f, height * 0.85f - sin(phase1) * 150f),
                    radius = maxDim * 0.65f
                ),
                center = Offset(width * 0.85f - cos(phase3) * 150f, height * 0.85f - sin(phase1) * 150f),
                radius = maxDim * 0.65f
            )

            // 3. Expanding Butter Wave Radial Ripples (Goes beyond screen now)
            val currentRadius = (maxDim * 1.4f) * wavePulse
            val rippleAlpha = (1f - wavePulse).coerceIn(0f, 1f) * 0.28f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF81D4FA).copy(alpha = rippleAlpha),
                        Color(0xFF3F51B5).copy(alpha = rippleAlpha * 0.45f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = (currentRadius + 200f).coerceAtLeast(10f)
                ),
                center = Offset(centerX, centerY),
                radius = currentRadius
            )

            // 4. Drifting Full-Screen Glowing Particles
            val particleCount = 28
            for (i in 0 until particleCount) {
                val seed = i * 137.5f
                val startX = (sin(seed) * 0.5f + 0.5f) * width
                val startY = (cos(seed) * 0.5f + 0.5f) * height
                
                // Full screen organic drift logic
                val driftX = sin(particleTime * 2 * PI + seed).toFloat() * 120f
                val driftY = cos(particleTime * 2 * PI + seed * 1.5f).toFloat() * 120f
                
                val px = (startX + driftX + width * 2) % width
                val py = (startY + driftY + height * 2) % height
                
                val pAlpha = (sin(particleTime * 3 * PI + seed).toFloat() * 0.5f + 0.5f) * 0.7f
                val pRadius = 2.dp.toPx() + (i % 4) * 2f

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            if (i % 2 == 0) Color(0xFF80DEEA).copy(alpha = pAlpha) else Color(0xFFE040FB).copy(alpha = pAlpha),
                            Color.Transparent
                        )
                    ),
                    center = Offset(px, py),
                    radius = pRadius * 3f
                )
            }

            // 5. TOP Waves (Hanging down from the ceiling)
            topWave1.rewind()
            topWave1.moveTo(0f, height * 0.35f)
            var x = 0f
            while (x <= width + 20f) {
                val y = height * 0.35f + sin(x * 0.004f + phase1) * 140f + cos(x * 0.007f - phase2) * 60f
                topWave1.lineTo(x, y)
                x += 10f
            }
            topWave1.lineTo(width, 0f)
            topWave1.lineTo(0f, 0f)
            topWave1.close()
            drawPath(
                path = topWave1,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0x441A237E), Color(0x153F51B5), Color.Transparent),
                    startY = 0f, endY = height * 0.55f
                )
            )

            topWave2.rewind()
            topWave2.moveTo(0f, height * 0.20f)
            x = 0f
            while (x <= width + 20f) {
                val y = height * 0.20f + sin(x * 0.006f - phase2) * 90f + sin(x * 0.01f + phase3) * 50f
                topWave2.lineTo(x, y)
                x += 10f
            }
            topWave2.lineTo(width, 0f)
            topWave2.lineTo(0f, 0f)
            topWave2.close()
            drawPath(
                path = topWave2,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0x5580DEEA), Color(0x153F51B5), Color.Transparent),
                    startY = 0f, endY = height * 0.40f
                )
            )

            // 6. BOTTOM Waves (Rising up from the floor)
            bottomWave1.rewind()
            bottomWave1.moveTo(0f, height * 0.65f)
            x = 0f
            while (x <= width + 20f) {
                val y = height * 0.65f + sin(x * 0.005f + phase2) * 150f + sin(x * 0.008f - phase3) * 80f
                bottomWave1.lineTo(x, y)
                x += 10f
            }
            bottomWave1.lineTo(width, height)
            bottomWave1.lineTo(0f, height)
            bottomWave1.close()
            drawPath(
                path = bottomWave1,
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0x22E040FB), Color(0x44311B92)),
                    startY = height * 0.45f, endY = height
                )
            )

            bottomWave2.rewind()
            bottomWave2.moveTo(0f, height * 0.80f)
            x = 0f
            while (x <= width + 20f) {
                val y = height * 0.80f + cos(x * 0.007f - phase1) * 100f + sin(x * 0.012f + phase2) * 55f
                bottomWave2.lineTo(x, y)
                x += 10f
            }
            bottomWave2.lineTo(width, height)
            bottomWave2.lineTo(0f, height)
            bottomWave2.close()
            drawPath(
                path = bottomWave2,
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0x224FC3F7), Color(0x5500897B)),
                    startY = height * 0.60f, endY = height
                )
            )

            // 7. Dynamic Shimmer Energy Lines cutting across the screen
            energyLine1.rewind()
            energyLine1.moveTo(0f, height * 0.42f)
            x = 0f
            while (x <= width + 20f) {
                val y = height * 0.42f + sin(x * 0.004f + phase1 * 1.4f) * 160f
                energyLine1.lineTo(x, y)
                x += 15f
            }
            drawPath(
                path = energyLine1,
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color(0x6680DEEA), Color(0x66E040FB), Color.Transparent)
                ),
                style = Stroke(width = 3.5.dp.toPx())
            )

            energyLine2.rewind()
            energyLine2.moveTo(0f, height * 0.58f)
            x = 0f
            while (x <= width + 20f) {
                val y = height * 0.58f + cos(x * 0.005f - phase2 * 1.3f) * 130f
                energyLine2.lineTo(x, y)
                x += 15f
            }
            drawPath(
                path = energyLine2,
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color(0x664FC3F7), Color(0x663F51B5), Color.Transparent)
                ),
                style = Stroke(width = 2.5.dp.toPx())
            )
        }

        // MAIN APP LOGO (StudyVerse Badge) with Shimmer & Progress Ring
        Box(
            modifier = Modifier
                .scale(pulseScale)
                .size(136.dp),
            contentAlignment = Alignment.Center
        ) {
            // Rotated Glowing Shimmer Aura Ring
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(rotateAngle)
            ) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFF80DEEA),
                            Color(0xFF3F51B5),
                            Color(0xFFE040FB),
                            Color(0x0080DEEA),
                            Color(0xFF80DEEA)
                        )
                    ),
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            // Dynamic Radial Progress Halo Ring
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            ) {
                val sweepAngle = (progress.coerceIn(0.05f, 1f)) * 360f
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFF80DEEA),
                            Color(0xFFE040FB),
                            Color(0xFF80DEEA)
                        )
                    ),
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Inner StudyVerse Circular Badge Container
            Box(
                modifier = Modifier
                    .size(118.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF80DEEA),
                                Color(0xFF3F51B5),
                                Color(0xFFE040FB)
                            )
                        )
                    )
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0D111E)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF1E2A4A),
                                    Color(0xFF090D18)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // StudyVerse Graduation Cap & Sparkles Emblem
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(54.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = "StudyVerse App Icon",
                                tint = Color(0xFF80DEEA),
                                modifier = Modifier.size(48.dp)
                            )
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFFE040FB),
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.TopEnd)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(2.dp))
                        
                        Text(
                            text = "STUDYVERSE",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 9.sp,
                                letterSpacing = 1.2.sp
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

private fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val activeNetwork = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
