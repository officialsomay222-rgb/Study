package com.example.ui.pdf

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max

/**
 * Ultra-Premium Inbuilt PDF Viewer with:
 * - Minimalist Floating Liquid Glass Ball (Orb) in the bottom-right corner
 * - Refined spring physics & liquid expanding animation on ball click
 * - Full-screen adaptive blur backdrop according to PDF theme (White, Sepia, OLED Dark)
 * - Compressed graphics pipeline (RGB_565, adaptive resolution, prefetching) for buttery 60fps
 * - Clean expanded glass controls with live scrubber, jump dialog, swipe modes & eye-care themes
 * - Seamless horizontal swipe navigation (eBook style) & continuous vertical flow
 * - Auto-hiding chrome for distraction-free reading
 * - Pinch-to-zoom & double tap adaptive reset
 */
@Composable
fun PdfViewerScreen(
    pdfSource: String,
    title: String? = null,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val displayMetrics = context.resources.displayMetrics
    val screenDensity = displayMetrics.densityDpi / 160f

    var localPdfFile by remember { mutableStateOf<File?>(null) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var isDownloading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var renderEngine by remember { mutableStateOf<PdfRenderEngine?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    var currentPageIndex by remember { mutableStateOf(0) }

    // UI state: auto-hide bars on scroll down
    var barsVisible by remember { mutableStateOf(true) }
    var viewMode by remember { mutableStateOf(PdfViewMode.PAGE_SWIPE) }
    var paperTheme by remember { mutableStateOf(PaperTheme.CLEAN_WHITE) }

    // Floating Orb Menu State (Bottom-Right Ball)
    var isOrbMenuOpen by remember { mutableStateOf(false) }

    // Zoom & pan state
    var zoomScale by remember { mutableStateOf(1f) }
    var panOffsetX by remember { mutableStateOf(0f) }
    var panOffsetY by remember { mutableStateOf(0f) }

    // Adaptive slider state
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubbedPage by remember { mutableStateOf(0) }
    var showQuickJumpDialog by remember { mutableStateOf(false) }
    var jumpTargetInput by remember { mutableStateOf("") }

    // Floating adaptive badge auto-fade timer
    var showAdaptivePageBadge by remember { mutableStateOf(false) }

    val resolvedTitle = remember(pdfSource, title) {
        if (!title.isNullOrBlank()) title
        else {
            val fileName = pdfSource.substringAfterLast("/").substringBefore("?")
            if (fileName.endsWith(".pdf", ignoreCase = true)) fileName else "$fileName.pdf"
        }
    }

    // Intercept back button to close orb menu first if open
    BackHandler {
        if (isOrbMenuOpen) {
            isOrbMenuOpen = false
        } else {
            onClose()
        }
    }

    // Trigger auto-fade on adaptive badge
    LaunchedEffect(currentPageIndex) {
        showAdaptivePageBadge = true
        delay(2500)
        showAdaptivePageBadge = false
    }

    // Document download & resolver worker
    LaunchedEffect(pdfSource) {
        isDownloading = true
        errorMessage = null
        withContext(Dispatchers.IO) {
            try {
                if (pdfSource.startsWith("http://") || pdfSource.startsWith("https://")) {
                    val pdfCacheDir = File(context.cacheDir, "study_pdfs").apply { mkdirs() }
                    val safeName = resolvedTitle.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                    val cachedFile = File(pdfCacheDir, safeName)

                    if (cachedFile.exists() && cachedFile.length() > 1024) {
                        localPdfFile = cachedFile
                        isDownloading = false
                    } else {
                        val url = URL(pdfSource)
                        val connection = url.openConnection() as HttpURLConnection
                        connection.connectTimeout = 15000
                        connection.readTimeout = 20000
                        connection.connect()

                        val fileLength = connection.contentLength
                        val input: InputStream = connection.inputStream
                        val output = FileOutputStream(cachedFile)

                        val data = ByteArray(8192)
                        var total: Long = 0
                        var count: Int
                        while (input.read(data).also { count = it } != -1) {
                            total += count
                            if (fileLength > 0) {
                                downloadProgress = total.toFloat() / fileLength
                            }
                            output.write(data, 0, count)
                        }
                        output.flush()
                        output.close()
                        input.close()

                        localPdfFile = cachedFile
                        isDownloading = false
                    }
                } else if (pdfSource.startsWith("content://")) {
                    val uri = Uri.parse(pdfSource)
                    val pdfCacheDir = File(context.cacheDir, "study_pdfs").apply { mkdirs() }
                    val cachedFile = File(pdfCacheDir, "imported_${System.currentTimeMillis()}.pdf")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(cachedFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    localPdfFile = cachedFile
                    isDownloading = false
                } else {
                    val f = File(pdfSource)
                    if (f.exists()) {
                        localPdfFile = f
                        isDownloading = false
                    } else {
                        errorMessage = "PDF document not found on this device."
                        isDownloading = false
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Could not load document: ${e.localizedMessage ?: "Network error"}"
                isDownloading = false
            }
        }
    }

    // Initialize PdfRenderEngine
    LaunchedEffect(localPdfFile) {
        val file = localPdfFile ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                renderEngine?.close()
                val engine = PdfRenderEngine(file, screenDensity)
                renderEngine = engine
                pageCount = engine.pageCount
                currentPageIndex = 0
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Invalid or encrypted PDF file format."
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            renderEngine?.close()
        }
    }

    // Pager state for horizontal swipe mode with prefetching
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { max(1, pageCount) }
    )

    // LazyListState for continuous vertical flow mode
    val listState = rememberLazyListState()

    // Sync current page between modes and states
    LaunchedEffect(pagerState.currentPage) {
        if (viewMode == PdfViewMode.PAGE_SWIPE && pageCount > 0) {
            currentPageIndex = pagerState.currentPage
        }
    }

    LaunchedEffect(listState.firstVisibleItemIndex) {
        if (viewMode == PdfViewMode.CONTINUOUS_FLOW && pageCount > 0) {
            currentPageIndex = listState.firstVisibleItemIndex
        }
    }

    // Adaptive NestedScroll connection to detect swipe-down (hide chrome) vs swipe-up (show chrome)
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -15f && barsVisible && !isOrbMenuOpen) {
                    barsVisible = false
                } else if (available.y > 15f && !barsVisible) {
                    barsVisible = true
                }
                return Offset.Zero
            }
        }
    }

    val paperColorFilter = remember(paperTheme) {
        when (paperTheme) {
            PaperTheme.CLEAN_WHITE -> null
            PaperTheme.WARM_SEPIA -> ColorFilter.lighting(
                multiply = Color(0xFFF7ECDF),
                add = Color(0x00000000)
            )
            PaperTheme.DARK_MODE -> ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        -1f,  0f,  0f, 0f, 255f,
                         0f, -1f,  0f, 0f, 255f,
                         0f,  0f, -1f, 0f, 255f,
                         0f,  0f,  0f, 1f,   0f
                    )
                )
            )
        }
    }

    val readerBackgroundColor = remember(paperTheme) {
        when (paperTheme) {
            PaperTheme.CLEAN_WHITE -> Color(0xFF090D18)
            PaperTheme.WARM_SEPIA -> Color(0xFF1E1712)
            PaperTheme.DARK_MODE -> Color(0xFF000000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(readerBackgroundColor)
            .nestedScroll(nestedScrollConnection)
    ) {
        // Subtle ambient atmospheric background mesh
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFF00E5FF).copy(alpha = 0.06f), Color.Transparent),
                            center = Offset(size.width * 0.8f, size.height * 0.2f),
                            radius = size.width * 0.7f
                        )
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFF7C4DFF).copy(alpha = 0.05f), Color.Transparent),
                            center = Offset(size.width * 0.2f, size.height * 0.85f),
                            radius = size.width * 0.8f
                        )
                    )
                }
        )

        // MAIN CONTENT AREA: SWIPEABLE PAGES WITH COMPRESSED GRAPHICS
        if (pageCount > 0 && renderEngine != null) {
            val engine = renderEngine!!

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                if (isOrbMenuOpen) {
                                    isOrbMenuOpen = false
                                } else {
                                    barsVisible = !barsVisible
                                }
                            },
                            onDoubleTap = {
                                if (zoomScale > 1.2f) {
                                    zoomScale = 1f
                                    panOffsetX = 0f
                                    panOffsetY = 0f
                                } else {
                                    zoomScale = 2.0f
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            if (zoomScale > 1.05f || zoom > 1.05f) {
                                zoomScale = (zoomScale * zoom).coerceIn(1f, 4.0f)
                                if (zoomScale > 1f) {
                                    val maxOffset = 500f * zoomScale
                                    panOffsetX = (panOffsetX + pan.x * zoomScale).coerceIn(-maxOffset, maxOffset)
                                    panOffsetY = (panOffsetY + pan.y * zoomScale).coerceIn(-maxOffset, maxOffset)
                                } else {
                                    panOffsetX = 0f
                                    panOffsetY = 0f
                                }
                            }
                        }
                    }
            ) {
                if (viewMode == PdfViewMode.PAGE_SWIPE) {
                    // HORIZONTAL SWIPE PAGER WITH BEYOND-VIEWPORT PREFETCHING
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoomScale
                                scaleY = zoomScale
                                translationX = panOffsetX
                                translationY = panOffsetY
                            },
                        pageSpacing = 16.dp,
                        userScrollEnabled = zoomScale <= 1.05f
                    ) { pageIdx ->
                        PdfSinglePageView(
                            pageIndex = pageIdx,
                            engine = engine,
                            colorFilter = paperColorFilter,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    top = if (barsVisible) 76.dp else 24.dp,
                                    bottom = 24.dp,
                                    start = 8.dp,
                                    end = 8.dp
                                )
                        )
                    }
                } else {
                    // CONTINUOUS VERTICAL FLOW
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoomScale
                                scaleY = zoomScale
                                translationX = panOffsetX
                                translationY = panOffsetY
                            },
                        contentPadding = PaddingValues(
                            top = if (barsVisible) 80.dp else 32.dp,
                            bottom = 40.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(pageCount) { pageIdx ->
                            PdfSinglePageView(
                                pageIndex = pageIdx,
                                engine = engine,
                                colorFilter = paperColorFilter,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp)
                            )
                        }
                    }
                }
            }
        }

        // LOADING OVERLAY
        if (isDownloading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE6080B14)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(32.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF1E263E).copy(alpha = 0.88f),
                                    Color(0xFF0F1528).copy(alpha = 0.96f)
                                )
                            ),
                            RoundedCornerShape(26.dp)
                        )
                        .border(
                            BorderStroke(1.2.dp, Brush.linearGradient(listOf(Color.White.copy(0.4f), Color(0xFF00E5FF).copy(0.3f)))),
                            RoundedCornerShape(26.dp)
                        )
                        .padding(horizontal = 32.dp, vertical = 28.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { if (downloadProgress > 0) downloadProgress else 0.5f },
                        color = Color(0xFF00E5FF),
                        strokeWidth = 3.5.dp,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "Opening Study Document...",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = resolvedTitle,
                        color = Color(0xFF90CAF9),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (downloadProgress > 0f) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "${(downloadProgress * 100).toInt()}%",
                            color = Color(0xFF00E5FF),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // ERROR BANNER
        if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xD9060912)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(32.dp)
                        .background(
                            Brush.verticalGradient(listOf(Color(0xFF2E1A22), Color(0xFF190D13))),
                            RoundedCornerShape(24.dp)
                        )
                        .border(BorderStroke(1.2.dp, Color(0xFFFF5252).copy(alpha = 0.5f)), RoundedCornerShape(24.dp))
                        .padding(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Document Error",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "Unknown error",
                        color = Color(0xFFFFCDD2),
                        fontSize = 13.5.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                    ) {
                        Text("Go Back", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ADAPTIVE FLOATING PAGE PILL (appears on page turn when chrome is hidden)
        AnimatedVisibility(
            visible = !barsVisible && !isOrbMenuOpen && showAdaptivePageBadge && pageCount > 0,
            enter = fadeIn(animationSpec = tween(180)) + slideInVertically(animationSpec = tween(180)) { -it },
            exit = fadeOut(animationSpec = tween(220)) + slideOutVertically(animationSpec = tween(220)) { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Transparent,
                modifier = Modifier.shadow(8.dp, RoundedCornerShape(20.dp), spotColor = Color(0x6600E5FF))
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.28f),
                                    Color(0xFF131A2E).copy(alpha = 0.85f),
                                    Color(0xFF090D18).copy(alpha = 0.95f)
                                )
                            )
                        )
                        .border(
                            BorderStroke(1.dp, Brush.linearGradient(listOf(Color.White.copy(0.6f), Color(0xFF00E5FF).copy(0.4f)))),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "Page ${currentPageIndex + 1} of $pageCount",
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // FLOATING TOP BAR - LIQUID GLASS
        AnimatedVisibility(
            visible = barsVisible,
            enter = fadeIn(animationSpec = tween(220)) + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut(animationSpec = tween(220)) + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                LiquidGlassButton(
                    onClick = onClose,
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    accentColor = Color(0xFF00E5FF)
                )

                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .shadow(8.dp, RoundedCornerShape(22.dp), spotColor = Color(0x3300E5FF))
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.20f),
                                        Color(0xFF141C30).copy(alpha = 0.80f),
                                        Color(0xFF0A0F1E).copy(alpha = 0.94f)
                                    )
                                )
                            )
                            .border(
                                BorderStroke(
                                    1.dp,
                                    Brush.linearGradient(
                                        listOf(Color.White.copy(alpha = 0.55f), Color(0xFF00E5FF).copy(alpha = 0.35f))
                                    )
                                ),
                                RoundedCornerShape(22.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = resolvedTitle,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Share
                    LiquidGlassButton(
                        onClick = {
                            val file = localPdfFile
                            if (file != null && file.exists()) {
                                try {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Study PDF"))
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Toast.makeText(context, "Could not share PDF", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Document not ready", Toast.LENGTH_SHORT).show()
                            }
                        },
                        icon = Icons.Default.Share,
                        contentDescription = "Share",
                        accentColor = Color(0xFF7C4DFF)
                    )

                    // Download to Device
                    LiquidGlassButton(
                        onClick = {
                            val file = localPdfFile
                            if (file != null && file.exists()) {
                                try {
                                    val target = File(
                                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                        resolvedTitle
                                    )
                                    file.copyTo(target, overwrite = true)
                                    Toast.makeText(context, "Saved to Downloads: ${target.name}", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            } else if (pdfSource.startsWith("http")) {
                                try {
                                    val request = DownloadManager.Request(Uri.parse(pdfSource)).apply {
                                        setMimeType("application/pdf")
                                        setTitle(resolvedTitle)
                                        setDescription("Downloading $resolvedTitle")
                                        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, resolvedTitle)
                                    }
                                    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                                    dm?.enqueue(request)
                                    Toast.makeText(context, "Downloading $resolvedTitle...", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        },
                        icon = Icons.Default.FileDownload,
                        contentDescription = "Save",
                        accentColor = Color(0xFF00E676)
                    )
                }
            }
        }

        // ATMOSPHERIC BLUR BACKDROP WHEN ORB MENU IS OPEN
        // (Black or White Frosted blur effect according to active PDF Theme)
        AnimatedVisibility(
            visible = isOrbMenuOpen,
            enter = fadeIn(animationSpec = tween(220, easing = LinearOutSlowInEasing)),
            exit = fadeOut(animationSpec = tween(200, easing = FastOutLinearInEasing)),
            modifier = Modifier.fillMaxSize()
        ) {
            LiquidGlassBackdrop(
                paperTheme = paperTheme,
                onDismiss = { isOrbMenuOpen = false }
            )
        }

        // EXPANDED LIQUID GLASS CONTROLS CARD - REFINED FLUID SPRING EXPANSION
        AnimatedVisibility(
            visible = isOrbMenuOpen && pageCount > 0,
            enter = fadeIn(animationSpec = tween(180, easing = LinearOutSlowInEasing)) +
                    scaleIn(
                        animationSpec = spring(
                            dampingRatio = 0.74f,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        transformOrigin = TransformOrigin(0.92f, 0.92f)
                    ) +
                    slideInVertically(
                        animationSpec = spring(
                            dampingRatio = 0.78f,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) { it / 4 },
            exit = fadeOut(animationSpec = tween(150, easing = FastOutLinearInEasing)) +
                    scaleOut(
                        animationSpec = tween(160, easing = FastOutLinearInEasing),
                        transformOrigin = TransformOrigin(0.92f, 0.92f)
                    ) +
                    slideOutVertically(animationSpec = tween(160)) { it / 5 },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(end = 16.dp, bottom = 86.dp, start = 16.dp)
        ) {
            LiquidGlassOrbMenuCard(
                currentPageIndex = currentPageIndex,
                pageCount = pageCount,
                viewMode = viewMode,
                paperTheme = paperTheme,
                zoomScale = zoomScale,
                isScrubbing = isScrubbing,
                scrubbedPage = scrubbedPage,
                resolvedTitle = resolvedTitle,
                onScrubChange = { page ->
                    isScrubbing = true
                    scrubbedPage = page
                },
                onScrubFinished = { targetPage ->
                    isScrubbing = false
                    currentPageIndex = targetPage
                    coroutineScope.launch {
                        if (viewMode == PdfViewMode.PAGE_SWIPE) {
                            pagerState.animateScrollToPage(targetPage)
                        } else {
                            listState.animateScrollToItem(targetPage)
                        }
                    }
                },
                onQuickJumpClick = {
                    jumpTargetInput = "${currentPageIndex + 1}"
                    showQuickJumpDialog = true
                },
                onPrevPage = {
                    if (currentPageIndex > 0) {
                        val target = currentPageIndex - 1
                        currentPageIndex = target
                        coroutineScope.launch {
                            if (viewMode == PdfViewMode.PAGE_SWIPE) {
                                pagerState.animateScrollToPage(target)
                            } else {
                                listState.animateScrollToItem(target)
                            }
                        }
                    }
                },
                onNextPage = {
                    if (currentPageIndex < pageCount - 1) {
                        val target = currentPageIndex + 1
                        currentPageIndex = target
                        coroutineScope.launch {
                            if (viewMode == PdfViewMode.PAGE_SWIPE) {
                                pagerState.animateScrollToPage(target)
                            } else {
                                listState.animateScrollToItem(target)
                            }
                        }
                    }
                },
                onToggleViewMode = {
                    val newMode = if (viewMode == PdfViewMode.PAGE_SWIPE) {
                        PdfViewMode.CONTINUOUS_FLOW
                    } else {
                        PdfViewMode.PAGE_SWIPE
                    }
                    viewMode = newMode
                    coroutineScope.launch {
                        if (newMode == PdfViewMode.PAGE_SWIPE) {
                            pagerState.scrollToPage(currentPageIndex)
                        } else {
                            listState.scrollToItem(currentPageIndex)
                        }
                    }
                    Toast.makeText(
                        context,
                        if (newMode == PdfViewMode.PAGE_SWIPE) "Swipe Pager Mode" else "Continuous Scroll Mode",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onCycleTheme = {
                    paperTheme = when (paperTheme) {
                        PaperTheme.CLEAN_WHITE -> PaperTheme.WARM_SEPIA
                        PaperTheme.WARM_SEPIA -> PaperTheme.DARK_MODE
                        PaperTheme.DARK_MODE -> PaperTheme.CLEAN_WHITE
                    }
                },
                onResetZoom = {
                    zoomScale = 1f
                    panOffsetX = 0f
                    panOffsetY = 0f
                },
                onClose = {
                    isOrbMenuOpen = false
                }
            )
        }

        // MINIMALIST FLOATING LIQUID GLASS BALL (ORB) IN BOTTOM RIGHT CORNER
        AnimatedVisibility(
            visible = (barsVisible || isOrbMenuOpen) && pageCount > 0,
            enter = fadeIn(animationSpec = tween(220)) + scaleIn(animationSpec = tween(220), transformOrigin = TransformOrigin(1f, 1f)),
            exit = fadeOut(animationSpec = tween(220)) + scaleOut(animationSpec = tween(220), transformOrigin = TransformOrigin(1f, 1f)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(end = 18.dp, bottom = 18.dp)
        ) {
            LiquidGlassOrbButton(
                onClick = { isOrbMenuOpen = !isOrbMenuOpen },
                isOpen = isOrbMenuOpen,
                pageText = "${currentPageIndex + 1} / $pageCount",
                accentColor = Color(0xFF00E5FF)
            )
        }

        // QUICK JUMP DIALOG
        if (showQuickJumpDialog) {
            AlertDialog(
                onDismissRequest = { showQuickJumpDialog = false },
                title = {
                    Text(
                        text = "Jump to Page",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Enter a page number between 1 and $pageCount:",
                            color = Color(0xFFB0BEC5),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = jumpTargetInput,
                            onValueChange = { jumpTargetInput = it.filter { ch -> ch.isDigit() } },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00E5FF),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val parsed = jumpTargetInput.toIntOrNull()
                            if (parsed != null && parsed in 1..pageCount) {
                                val target = parsed - 1
                                currentPageIndex = target
                                coroutineScope.launch {
                                    if (viewMode == PdfViewMode.PAGE_SWIPE) {
                                        pagerState.animateScrollToPage(target)
                                    } else {
                                        listState.animateScrollToItem(target)
                                    }
                                }
                                showQuickJumpDialog = false
                            } else {
                                Toast.makeText(context, "Please enter a valid page number", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                    ) {
                        Text("Jump", color = Color(0xFF070B18), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showQuickJumpDialog = false }) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                    }
                },
                containerColor = Color(0xFF13192B),
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}
