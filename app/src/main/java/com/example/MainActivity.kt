package com.example

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private var webViewInstance: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                WebViewScreen(
                    url = "https://pw-thor-two.vercel.app",
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
    
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    val context = LocalContext.current

    // Handle system back button for custom view (full screen video) OR step-by-step internal WebView navigation
    BackHandler(enabled = customView != null || canGoBack) {
        if (customView != null) {
            customViewCallback?.onCustomViewHidden()
            customView = null
            customViewCallback = null
            (context as? Activity)?.let { activity ->
                val windowInsetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        } else if (webView?.canGoBack() == true) {
            webView?.goBack()
            canGoBack = webView?.canGoBack() == true
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
                factory = { customView!! },
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        // Root container uplifted using WindowInsets.systemBars
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F0F13))
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        
                        // Hardware Acceleration for smooth 60fps rendering
                        setLayerType(View.LAYER_TYPE_HARDWARE, null)

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
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

                            // Offline caching & speed optimization
                            cacheMode = if (isNetworkAvailable(ctx)) {
                                WebSettings.LOAD_DEFAULT
                            } else {
                                WebSettings.LOAD_CACHE_ELSE_NETWORK
                            }
                        }
                        
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress / 100f
                                if (newProgress >= 95) {
                                    isLoading = false
                                }
                                canGoBack = view?.canGoBack() == true
                            }

                            override fun onPermissionRequest(request: PermissionRequest?) {
                                request?.grant(request.resources)
                            }

                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: android.os.Message?
                            ): Boolean {
                                val result = view?.hitTestResult
                                val data = result?.extra
                                if (data != null) {
                                    view.loadUrl(data)
                                    return true
                                }
                                val newWebView = WebView(ctx)
                                newWebView.webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        newView: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        view?.loadUrl(request?.url.toString())
                                        return true
                                    }
                                }
                                val transport = resultMsg?.obj as? WebView.WebViewTransport
                                transport?.webView = newWebView
                                resultMsg?.sendToTarget()
                                return true
                            }

                            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                if (customView != null) {
                                    callback?.onCustomViewHidden()
                                    return
                                }
                                customView = view
                                customViewCallback = callback

                                (ctx as? Activity)?.let { activity ->
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
                                    val windowInsetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                                    windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                                }
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val requestUrl = request?.url?.toString() ?: return false
                                if (requestUrl.startsWith("http://") || requestUrl.startsWith("https://")) {
                                    return false // Let WebView handle it normally
                                }
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(requestUrl))
                                    ctx.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                return true
                            }

                            @Suppress("DEPRECATION")
                            override fun shouldOverrideUrlLoading(view: WebView?, urlStr: String?): Boolean {
                                if (urlStr == null) return false
                                if (urlStr.startsWith("http://") || urlStr.startsWith("https://")) {
                                    return false // Let WebView handle it normally
                                }
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(urlStr))
                                    ctx.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                return true
                            }

                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                canGoBack = view?.canGoBack() == true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                canGoBack = view?.canGoBack() == true
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame == true) {
                                    if (!isNetworkAvailable(ctx)) {
                                        hasError = true
                                        errorMessage = "No internet connection available. Please check your network."
                                    }
                                }
                            }
                        }

                        if (savedState != null) {
                            restoreState(savedState)
                        } else {
                            loadUrl(url)
                        }

                        webView = this
                        onWebViewCreated(this)
                    }
                },
                update = {
                    webView = it
                    canGoBack = it.canGoBack()
                },
                modifier = Modifier.fillMaxSize()
            )

            // Error / Offline Screen Overlay
            if (hasError) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0F0F13))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SignalWifiOff,
                            contentDescription = "Offline",
                            tint = Color(0xFF90CAF9),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Connection Issue",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFB0BEC5),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                hasError = false
                                isLoading = true
                                webView?.reload()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry Loading", color = Color.White)
                        }
                    }
                }
            }

            // Full Screen Animated Butter Wave Landing / Processing Overlay with StudyVerse Logo
            AnimatedVisibility(
                visible = isLoading && !hasError,
                enter = fadeIn(),
                exit = fadeOut(animationSpec = tween(700))
            ) {
                LoadingOverlay(progress = progress)
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
            val topWave1 = Path().apply {
                moveTo(0f, height * 0.35f)
                var x = 0f
                while (x <= width + 20f) {
                    val y = height * 0.35f + sin(x * 0.004f + phase1) * 140f + cos(x * 0.007f - phase2) * 60f
                    lineTo(x, y)
                    x += 10f
                }
                lineTo(width, 0f)
                lineTo(0f, 0f)
                close()
            }
            drawPath(
                path = topWave1,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0x441A237E), Color(0x153F51B5), Color.Transparent),
                    startY = 0f, endY = height * 0.55f
                )
            )

            val topWave2 = Path().apply {
                moveTo(0f, height * 0.20f)
                var x = 0f
                while (x <= width + 20f) {
                    val y = height * 0.20f + sin(x * 0.006f - phase2) * 90f + sin(x * 0.01f + phase3) * 50f
                    lineTo(x, y)
                    x += 10f
                }
                lineTo(width, 0f)
                lineTo(0f, 0f)
                close()
            }
            drawPath(
                path = topWave2,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0x5580DEEA), Color(0x153F51B5), Color.Transparent),
                    startY = 0f, endY = height * 0.40f
                )
            )

            // 6. BOTTOM Waves (Rising up from the floor)
            val bottomWave1 = Path().apply {
                moveTo(0f, height * 0.65f)
                var x = 0f
                while (x <= width + 20f) {
                    val y = height * 0.65f + sin(x * 0.005f + phase2) * 150f + sin(x * 0.008f - phase3) * 80f
                    lineTo(x, y)
                    x += 10f
                }
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
            drawPath(
                path = bottomWave1,
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0x22E040FB), Color(0x44311B92)),
                    startY = height * 0.45f, endY = height
                )
            )

            val bottomWave2 = Path().apply {
                moveTo(0f, height * 0.80f)
                var x = 0f
                while (x <= width + 20f) {
                    val y = height * 0.80f + cos(x * 0.007f - phase1) * 100f + sin(x * 0.012f + phase2) * 55f
                    lineTo(x, y)
                    x += 10f
                }
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
            drawPath(
                path = bottomWave2,
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0x224FC3F7), Color(0x5500897B)),
                    startY = height * 0.60f, endY = height
                )
            )

            // 7. Dynamic Shimmer Energy Lines cutting across the screen
            val energyLine1 = Path().apply {
                moveTo(0f, height * 0.42f)
                var x = 0f
                while (x <= width + 20f) {
                    val y = height * 0.42f + sin(x * 0.004f + phase1 * 1.4f) * 160f
                    lineTo(x, y)
                    x += 15f
                }
            }
            drawPath(
                path = energyLine1,
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color(0x6680DEEA), Color(0x66E040FB), Color.Transparent)
                ),
                style = Stroke(width = 3.5.dp.toPx())
            )

            val energyLine2 = Path().apply {
                moveTo(0f, height * 0.58f)
                var x = 0f
                while (x <= width + 20f) {
                    val y = height * 0.58f + cos(x * 0.005f - phase2 * 1.3f) * 130f
                    lineTo(x, y)
                    x += 15f
                }
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
