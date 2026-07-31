package com.example

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme
import kotlin.math.PI
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                WebViewScreen(url = "https://pw-thor-two.vercel.app")
            }
        }
    }
}

@Composable
fun WebViewScreen(url: String) {
    var webView: WebView? by remember { mutableStateOf(null) }
    var isLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableStateOf(0f) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    val context = LocalContext.current

    // Handle system back button for WebView internal history navigation
    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }

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
                    
                    // Enable Hardware Acceleration for fast rendering
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
                        allowFileAccess = true
                        allowContentAccess = true

                        // Offline compatibility & cache optimization
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
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val requestUrl = request?.url?.toString()
                            if (requestUrl != null && (requestUrl.startsWith("http://") || requestUrl.startsWith("https://"))) {
                                view?.loadUrl(requestUrl)
                                return true
                            }
                            return false
                        }

                        @Suppress("DEPRECATION")
                        override fun shouldOverrideUrlLoading(view: WebView?, urlStr: String?): Boolean {
                            if (urlStr != null && (urlStr.startsWith("http://") || urlStr.startsWith("https://"))) {
                                view?.loadUrl(urlStr)
                                return true
                            }
                            return false
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
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
                    loadUrl(url)
                    webView = this
                }
            },
            update = {
                webView = it
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

        // Full Screen Animated Butter Wave Landing / Processing Overlay
        AnimatedVisibility(
            visible = isLoading && !hasError,
            enter = fadeIn(),
            exit = fadeOut(animationSpec = tween(600))
        ) {
            LoadingOverlay(progress = progress)
        }
    }
}

@Composable
fun LoadingOverlay(progress: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "ButterWave")
    
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Phase1"
    )

    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(6500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Phase2"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val wavePulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavePulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090A0F)),
        contentAlignment = Alignment.Center
    ) {
        // Butter Wave Canvas Background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerX = width / 2f
            val centerY = height / 2f

            // Expanding butter wave radial ripple
            val maxRadius = maxOf(width, height) * 0.75f
            val currentRadius = maxRadius * wavePulse
            val rippleAlpha = (1f - wavePulse).coerceIn(0f, 1f) * 0.4f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF64B5F6).copy(alpha = rippleAlpha),
                        Color(0xFF3F51B5).copy(alpha = rippleAlpha * 0.5f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = (currentRadius + 120f).coerceAtLeast(10f)
                ),
                center = Offset(centerX, centerY),
                radius = currentRadius
            )

            // Flowing Butter Wave 1 (Mid-Screen Silky Curve)
            val path1 = Path().apply {
                moveTo(0f, height * 0.45f)
                var x = 0f
                while (x <= width + 20f) {
                    val y = height * 0.45f + sin(x * 0.007f + phase1) * 65f + sin(x * 0.003f - phase2) * 45f
                    lineTo(x, y)
                    x += 10f
                }
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
            drawPath(
                path = path1,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0x333F51B5),
                        Color(0x551A237E),
                        Color(0x99090A0F)
                    )
                )
            )

            // Flowing Butter Wave 2 (Lower Silky Smooth Wave)
            val path2 = Path().apply {
                moveTo(0f, height * 0.55f)
                var x = 0f
                while (x <= width + 20f) {
                    val y = height * 0.55f + sin(x * 0.005f - phase1) * 85f + sin(x * 0.011f + phase2) * 35f
                    lineTo(x, y)
                    x += 10f
                }
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
            drawPath(
                path = path2,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0x4464B5F6),
                        Color(0x2200897B),
                        Color(0xEE090A0F)
                    )
                )
            )

            // Glowing Wave Accent Line across screen
            val pathLine = Path().apply {
                moveTo(0f, height * 0.52f)
                var x = 0f
                while (x <= width + 20f) {
                    val y = height * 0.52f + sin(x * 0.006f + phase1 * 1.2f) * 75f
                    lineTo(x, y)
                    x += 10f
                }
            }
            drawPath(
                path = pathLine,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0x0090CAF9),
                        Color(0xFF90CAF9),
                        Color(0xFFE040FB),
                        Color(0x0090CAF9)
                    )
                ),
                style = Stroke(width = 3.5.dp.toPx())
            )
        }

        // Super Premium Logo ONLY (No text elements behind or below)
        Box(
            modifier = Modifier
                .scale(pulseScale)
                .size(112.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF90CAF9),
                            Color(0xFF3F51B5),
                            Color(0xFFE040FB)
                        )
                    )
                )
                .padding(2.5.dp)
                .clip(CircleShape)
                .background(Color(0xFF0F121C)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF1E2942),
                                Color(0xFF0A0D16)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "PW",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 40.sp,
                        letterSpacing = (-1).sp
                    ),
                    color = Color.White
                )
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
