package com.example.ui.pdf

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import com.example.util.FileImportHelper
import com.example.ui.pdf.DocxToPdfConverter

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
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()
    val displayMetrics = context.resources.displayMetrics
    val screenDensity = displayMetrics.densityDpi / 160f

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val cutoutTop = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    val safeCutout = if (cutoutTop > 0.dp) cutoutTop else maxOf(statusBarTop, 24.dp)
    // 3px visible gap situated strictly after camera notch
    val notchGap = with(LocalDensity.current) { 3.toDp() }
    val topBarNotchSafePadding = safeCutout + notchGap

    // Fullscreen Immersive Mode: Hide phone buttons & status bar in PDF opener state
    // Automatically restores phone buttons & status bar on exit
    DisposableEffect(activity) {
        val window = activity?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }

        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    var isVisible by remember { mutableStateOf(false) }
    var isClosing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    fun safeClose() {
        if (isClosing) return
        isClosing = true
        coroutineScope.launch {
            delay(180)
            onClose()
        }
    }

    var localPdfFile by remember { mutableStateOf<File?>(null) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var isDownloading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var renderEngine by remember { mutableStateOf<PdfRenderEngine?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    var currentPageIndex by remember { mutableStateOf(0) }

    // UI state: auto-hide bars on scroll down
    var barsVisible by remember { mutableStateOf(true) }
    var viewMode by remember { mutableStateOf(PdfViewMode.CONTINUOUS_FLOW) }
    var paperTheme by remember { mutableStateOf(PaperTheme.CLEAN_WHITE) }

    // Floating Orb Menu State (Bottom-Right Ball)
    var isOrbMenuOpen by remember { mutableStateOf(false) }

    // Fluid continuous Zoom & pan state with spring interpolation
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

    var zoomAnimJob by remember { mutableStateOf<Job?>(null) }

    fun animateZoomAndPan(targetZoom: Float, targetPanX: Float = 0f, targetPanY: Float = 0f) {
        zoomAnimJob?.cancel()
        zoomAnimJob = coroutineScope.launch {
            val startZoom = zoomScale
            val startPanX = panOffsetX
            val startPanY = panOffsetY
            animate(0f, 1f, animationSpec = tween(220, easing = FastOutSlowInEasing)) { progress, _ ->
                zoomScale = startZoom + (targetZoom - startZoom) * progress
                panOffsetX = startPanX + (targetPanX - startPanX) * progress
                panOffsetY = startPanY + (targetPanY - startPanY) * progress
            }
        }
    }

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

    // Intercept back button to close orb menu first if open, or trigger smooth exit
    BackHandler {
        if (isOrbMenuOpen) {
            isOrbMenuOpen = false
        } else {
            safeClose()
        }
    }

    // Trigger auto-fade on adaptive badge
    LaunchedEffect(currentPageIndex) {
        showAdaptivePageBadge = true
        delay(2500)
        showAdaptivePageBadge = false
    }

    // Document download, resolver & renderer worker
    LaunchedEffect(pdfSource) {
        localPdfFile = null
        try {
            renderEngine?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        renderEngine = null
        pageCount = 0
        currentPageIndex = 0
        zoomScale = 1f
        panOffsetX = 0f
        panOffsetY = 0f
        isDownloading = true
        errorMessage = null

        withContext(Dispatchers.IO) {
            try {
                val targetFile: File = when {
                    pdfSource.startsWith("http://") || pdfSource.startsWith("https://") -> {
                        // 1. Check if the file is already downloaded in public Downloads or cache
                        var foundFile: File? = null
                        try {
                            val candidateName = resolvedTitle.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                            val f1 = File(downloadsDir, resolvedTitle)
                            val f2 = File(downloadsDir, candidateName)
                            val f3 = File(downloadsDir, "$candidateName.pdf")
                            when {
                                f1.exists() && f1.length() > 512 -> foundFile = f1
                                f2.exists() && f2.length() > 512 -> foundFile = f2
                                f3.exists() && f3.length() > 512 -> foundFile = f3
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        if (foundFile != null) {
                            if (DocxToPdfConverter.isWordOrOfficeDocument(foundFile.name, null) || FileImportHelper.isDocxZip(foundFile)) {
                                DocxToPdfConverter.convertToPdf(context, foundFile, resolvedTitle)
                            } else {
                                FileImportHelper.sanitizePdfFile(foundFile)
                            }
                        } else {
                            // Download using CookieManager & UserAgent into app cache
                            val prepared = FileImportHelper.importAndPrepareDocument(context, Uri.parse(pdfSource), resolvedTitle)
                            prepared?.first ?: throw IllegalStateException("Could not load document from web.")
                        }
                    }
                    pdfSource.startsWith("content://") -> {
                        val uri = Uri.parse(pdfSource)
                        val prepared = FileImportHelper.importAndPrepareDocument(context, uri, resolvedTitle)
                        prepared?.first ?: throw IllegalStateException("Could not read document from external app.")
                    }
                    else -> {
                        val f = if (pdfSource.startsWith("file://")) {
                            try {
                                File(Uri.parse(pdfSource).path ?: pdfSource.removePrefix("file://"))
                            } catch (e: Exception) {
                                File(pdfSource.removePrefix("file://"))
                            }
                        } else {
                            File(pdfSource)
                        }

                        if (f.exists() && f.length() > 0) {
                            if (DocxToPdfConverter.isWordOrOfficeDocument(f.name, null) || FileImportHelper.isDocxZip(f)) {
                                DocxToPdfConverter.convertToPdf(context, f, resolvedTitle)
                            } else {
                                FileImportHelper.sanitizePdfFile(f)
                            }
                        } else {
                            val uri = Uri.parse(pdfSource)
                            val prepared = FileImportHelper.importAndPrepareDocument(context, uri, resolvedTitle)
                            prepared?.first ?: throw IllegalStateException("PDF file not found on device.")
                        }
                    }
                }

                val engine = PdfRenderEngine(targetFile, screenDensity)
                renderEngine = engine
                pageCount = engine.pageCount
                currentPageIndex = 0
                localPdfFile = targetFile
                isDownloading = false
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Document Error: ${e.localizedMessage ?: "Invalid or encrypted document format"}"
                isDownloading = false
            }
        }
    }

    DisposableEffect(renderEngine) {
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

    // Sync current page between modes and states, resetting zoom on page flip
    LaunchedEffect(pagerState.currentPage) {
        if (viewMode == PdfViewMode.PAGE_SWIPE && pageCount > 0) {
            currentPageIndex = pagerState.currentPage
            if (zoomScale > 1.05f) {
                zoomScale = 1f
                panOffsetX = 0f
                panOffsetY = 0f
            }
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
                if (zoomScale <= 1.05f) {
                    if (available.y < -15f && barsVisible && !isOrbMenuOpen) {
                        barsVisible = false
                    } else if (available.y > 15f && !barsVisible) {
                        barsVisible = true
                    }
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

    val screenAlpha by animateFloatAsState(
        targetValue = if (isVisible && !isClosing) 1f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "screenAlpha"
    )
    val screenScale by animateFloatAsState(
        targetValue = if (isVisible && !isClosing) 1f else 0.96f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "screenScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = screenAlpha
                scaleX = screenScale
                scaleY = screenScale
            }
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
                    .onSizeChanged { containerSize = it }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            zoomAnimJob?.cancel()
                            val firstDown = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            zoomAnimJob?.cancel()
                            val downTime = System.currentTimeMillis()
                            val downPos = firstDown.position
                            var isMultiTouch = false
                            var hasMoved = false
                            val touchSlop = viewConfiguration.touchSlop

                            val cw = if (containerSize.width > 0) containerSize.width.toFloat() else 1080f
                            val ch = if (containerSize.height > 0) containerSize.height.toFloat() else 1920f
                            val centerX = cw / 2f
                            val centerY = ch / 2f

                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val activePointers = event.changes.filter { it.pressed }
                                val count = activePointers.size

                                if (count >= 2) {
                                    zoomAnimJob?.cancel()
                                    // TWO-FINGER GESTURE: Dynamic centroid focal-point pinch-to-zoom & pan
                                    isMultiTouch = true
                                    hasMoved = true

                                    val centroid = event.calculateCentroid(useCurrent = true)
                                    val rawZoom = event.calculateZoom()
                                    val panDelta = event.calculatePan()

                                    // Dynamic zooming: sensitive, continuous scaling with focal anchoring
                                    if (rawZoom > 0f && rawZoom != 1f) {
                                        val oldZoom = zoomScale
                                        val targetZoom = (oldZoom * rawZoom).coerceIn(0.85f, 5.5f)
                                        val scaleRatio = targetZoom / oldZoom

                                        // Centroid focal shift: keeps content under fingers locked to hand gesture
                                        val focusX = centroid.x - centerX
                                        val focalShiftX = (focusX - panOffsetX) * (1f - scaleRatio)

                                        zoomScale = targetZoom
                                        panOffsetX = panOffsetX + focalShiftX + panDelta.x

                                        val focusY = centroid.y - centerY
                                        val focalShiftY = (focusY - panOffsetY) * (1f - scaleRatio)
                                        val maxFlowPanY = max(0f, ch * (zoomScale - 1f) * 0.75f + 400f)
                                        panOffsetY = (panOffsetY + focalShiftY + panDelta.y).coerceIn(-maxFlowPanY, maxFlowPanY)
                                    } else if (panDelta != Offset.Zero) {
                                        val maxPanX = max(0f, (cw * (zoomScale - 1f)) / 2f + 120f)
                                        val maxFlowPanY = max(0f, ch * (zoomScale - 1f) * 0.75f + 400f)
                                        panOffsetX = (panOffsetX + panDelta.x).coerceIn(-maxPanX, maxPanX)
                                        panOffsetY = (panOffsetY + panDelta.y).coerceIn(-maxFlowPanY, maxFlowPanY)
                                    }

                                    // Intercept and consume so underlying pager does not scroll during pinch
                                    event.changes.forEach { it.consume() }
                                } else if (count == 1) {
                                    val change = activePointers.first()
                                    val dist = (change.position - downPos).getDistance()
                                    if (dist > touchSlop) {
                                        hasMoved = true
                                    }

                                    if (zoomScale > 1.05f) {
                                        // ONE FINGER DRAG WHILE ZOOMED IN: Smoothly scroll and pan
                                        if (dist > touchSlop || hasMoved) {
                                            val panDelta = event.calculatePan()
                                            if (panDelta != Offset.Zero) {
                                                val maxPanX = max(0f, (cw * (zoomScale - 1f)) / 2f + 120f)
                                                panOffsetX = (panOffsetX + panDelta.x).coerceIn(-maxPanX, maxPanX)

                                                if (viewMode == PdfViewMode.CONTINUOUS_FLOW) {
                                                    // In Flow mode: Seamless list scrolling + vertical top/bottom panning
                                                    val maxFlowPanY = max(0f, ch * (zoomScale - 1f) * 0.75f + 400f)
                                                    if (panDelta.y != 0f) {
                                                        val canScrollBack = listState.canScrollBackward
                                                        val canScrollFwd = listState.canScrollForward

                                                        if (panDelta.y > 0f) {
                                                            // Swiping finger DOWN (revealing top content of page)
                                                            if (!canScrollBack || panOffsetY < maxFlowPanY) {
                                                                panOffsetY = (panOffsetY + panDelta.y).coerceAtMost(maxFlowPanY)
                                                            } else {
                                                                val consumed = listState.dispatchRawDelta(panDelta.y)
                                                                if (consumed == 0f) {
                                                                    panOffsetY = (panOffsetY + panDelta.y).coerceAtMost(maxFlowPanY)
                                                                }
                                                            }
                                                        } else {
                                                            // Swiping finger UP (moving down through document)
                                                            if (panOffsetY > -maxFlowPanY) {
                                                                panOffsetY = (panOffsetY + panDelta.y).coerceAtLeast(-maxFlowPanY)
                                                            } else if (canScrollFwd) {
                                                                listState.dispatchRawDelta(panDelta.y)
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    // In Page Swipe mode: Pan inside the current page
                                                    val maxPanX = max(0f, (cw * (zoomScale - 1f)) / 2f + 80f)
                                                    val maxPanY = max(0f, (ch * (zoomScale - 1f)) / 2f + 120f)
                                                    panOffsetX = (panOffsetX + panDelta.x).coerceIn(-maxPanX, maxPanX)
                                                    panOffsetY = (panOffsetY + panDelta.y).coerceIn(-maxPanY, maxPanY)
                                                }
                                            }
                                            change.consume()
                                        }
                                    }
                                    // When zoomScale <= 1.05f, we DO NOT consume!
                                    // Native gestures scroll list or flip pages freely
                                }
                            } while (event.changes.any { it.pressed })

                            // Snap-back bounds checking on finger release
                            val maxPanX = max(0f, (cw * (zoomScale - 1f)) / 2f)
                            val maxPanY = max(0f, if (viewMode == PdfViewMode.CONTINUOUS_FLOW) (ch * (zoomScale - 1f) * 0.75f + 200f) else (ch * (zoomScale - 1f)) / 2f)

                            if (zoomScale < 1.0f) {
                                animateZoomAndPan(1f, 0f, 0f)
                            } else if (zoomScale > 5.0f) {
                                animateZoomAndPan(
                                    5.0f,
                                    panOffsetX.coerceIn(-maxPanX, maxPanX),
                                    panOffsetY.coerceIn(-maxPanY, maxPanY)
                                )
                            } else if (zoomScale > 1.05f) {
                                val clampedX = panOffsetX.coerceIn(-maxPanX, maxPanX)
                                val clampedY = panOffsetY.coerceIn(-maxPanY, maxPanY)
                                if (clampedX != panOffsetX || clampedY != panOffsetY) {
                                    animateZoomAndPan(zoomScale, clampedX, clampedY)
                                }
                            }

                            // Tap & Double Tap detection (fires only when stationary single touch)
                            if (!isMultiTouch && !hasMoved) {
                                val upTime = System.currentTimeMillis()
                                if (upTime - downTime < 320L) {
                                    if (upTime - lastTapTime < 320L) {
                                        // DOUBLE TAP: Dynamic focal zoom in to 2.25x or reset to 1x
                                        lastTapTime = 0L
                                        if (zoomScale > 1.15f) {
                                            animateZoomAndPan(1f, 0f, 0f)
                                        } else {
                                            val focusX = downPos.x - centerX
                                            val focusY = downPos.y - centerY
                                            val targetPanX = (-focusX * 1.25f).coerceIn(-cw * 0.625f, cw * 0.625f)
                                            val targetPanY = (-focusY * 1.25f).coerceIn(-ch * 0.625f, ch * 0.625f)
                                            animateZoomAndPan(2.25f, targetPanX, targetPanY)
                                        }
                                    } else {
                                        lastTapTime = upTime
                                        coroutineScope.launch {
                                            delay(320L)
                                            if (lastTapTime == upTime) {
                                                lastTapTime = 0L
                                                // SINGLE TAP: Toggle clean UI chrome or close orb menu
                                                if (isOrbMenuOpen) {
                                                    isOrbMenuOpen = false
                                                } else {
                                                    barsVisible = !barsVisible
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
            ) {
                if (viewMode == PdfViewMode.PAGE_SWIPE) {
                    // HORIZONTAL SWIPE PAGER WITH BEYOND-VIEWPORT PREFETCHING
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 2,
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
                            isSwipeMode = true,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    top = if (barsVisible) topBarNotchSafePadding + 46.dp else safeCutout,
                                    bottom = if (barsVisible) 68.dp else 16.dp,
                                    start = 8.dp,
                                    end = 8.dp
                                )
                        )
                    }
                } else {
                    // CONTINUOUS VERTICAL FLOW
                    LazyColumn(
                        state = listState,
                        userScrollEnabled = zoomScale <= 1.05f,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoomScale
                                scaleY = zoomScale
                                translationX = panOffsetX
                                translationY = panOffsetY
                            },
                        contentPadding = PaddingValues(
                            top = if (barsVisible) topBarNotchSafePadding + 48.dp else safeCutout,
                            bottom = 40.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(pageCount) { pageIdx ->
                            PdfSinglePageView(
                                pageIndex = pageIdx,
                                engine = engine,
                                colorFilter = paperColorFilter,
                                isSwipeMode = false,
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
                        onClick = { safeClose() },
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
                .padding(top = topBarNotchSafePadding + 2.dp)
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
        // Positioned cleanly after the camera notch with a dedicated visible gap of 3px
        AnimatedVisibility(
            visible = barsVisible,
            enter = fadeIn(animationSpec = tween(220)) + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut(animationSpec = tween(220)) + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(top = topBarNotchSafePadding, bottom = 8.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                LiquidGlassButton(
                    onClick = { safeClose() },
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
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                        val contentValues = android.content.ContentValues().apply {
                                            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, resolvedTitle)
                                            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                                            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                                        }
                                        val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                                        if (uri != null) {
                                            context.contentResolver.openOutputStream(uri)?.use { output ->
                                                file.inputStream().use { input -> input.copyTo(output) }
                                            }
                                            Toast.makeText(context, "Saved to Downloads: $resolvedTitle", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Failed to create file in Downloads", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        @Suppress("DEPRECATION")
                                        val target = File(
                                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                            resolvedTitle
                                        )
                                        file.copyTo(target, overwrite = true)
                                        Toast.makeText(context, "Saved to Downloads: ${target.name}", Toast.LENGTH_SHORT).show()
                                    }
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
                    if (zoomScale > 1.05f) {
                        animateZoomAndPan(1f, 0f, 0f)
                    }
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
                    zoomScale = 1f
                    panOffsetX = 0f
                    panOffsetY = 0f
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
                    animateZoomAndPan(1f, 0f, 0f)
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
                                if (zoomScale > 1.05f) {
                                    zoomScale = 1f
                                    panOffsetX = 0f
                                    panOffsetY = 0f
                                }
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
