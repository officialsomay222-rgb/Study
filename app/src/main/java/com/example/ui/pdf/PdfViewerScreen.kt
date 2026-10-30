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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
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

    // True Game Mode Immersive Fullscreen:
    // Content renders 100% edge-to-edge across the entire screen including cutout/navigation areas.
    // Swiping from bottom, top, or sides transiently overlays system bars while PDF content remains visible underneath.
    DisposableEffect(activity) {
        val window = activity?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }

        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                window.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode =
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                        } else {
                            android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                        }
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
                window.isStatusBarContrastEnforced = false
            }
        }
        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    var isVisible by remember { mutableStateOf(false) }
    var isClosing by remember { mutableStateOf(false) }

    // Keep Game Mode system bars hidden across any recomposition while PDF viewer is active
    SideEffect {
        if (!isClosing) {
            val window = activity?.window
            if (window != null) {
                @Suppress("DEPRECATION")
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                @Suppress("DEPRECATION")
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

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

    // UI state: auto-hide ball & bars immediately on any scroll; show only when upward scroll stops
    var barsVisible by remember { mutableStateOf(true) }
    var lastPdfScrollWasUp by remember { mutableStateOf(false) }
    var isZoomGestureScrolling by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(PdfViewMode.CONTINUOUS_FLOW) }
    var paperTheme by remember { mutableStateOf(PaperTheme.CLEAN_WHITE) }

    // Floating Orb Menu State (Bottom-Right Ball)
    var isOrbMenuOpen by remember { mutableStateOf(false) }

    // Pager state for horizontal swipe mode with prefetching
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { max(1, pageCount) }
    )

    // LazyListState for continuous vertical flow mode
    val listState = rememberLazyListState()

    // Combined active scroll state for Google Drive-style fast scroll preview -> chunk-by-chunk sharpening
    val isUserScrolling by remember {
        derivedStateOf {
            listState.isScrollInProgress || pagerState.isScrollInProgress || isZoomGestureScrolling
        }
    }

    // Immediately hide ball when scrolling starts; only reveal ball when scrolling stops AND last scroll was upward
    LaunchedEffect(isUserScrolling, renderEngine) {
        renderEngine?.isUserActivelyScrolling = isUserScrolling
        if (isUserScrolling) {
            if (barsVisible && !isOrbMenuOpen) {
                barsVisible = false
            }
        } else {
            delay(95)
            if (!isUserScrolling && lastPdfScrollWasUp && !barsVisible) {
                barsVisible = true
            }
        }
    }

    // Fluid continuous Zoom & pan state with spring interpolation
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

    // Derived thresholds prevent per-frame pinch-to-zoom recomposition of page items
    val isHighZoomMode by remember { derivedStateOf { zoomScale >= 1.55f } }
    val isNativeScrollEnabled by remember { derivedStateOf { zoomScale <= 1.05f } }
    val itemWidthFraction by remember {
        derivedStateOf {
            if (zoomScale < 0.995f) zoomScale.coerceIn(0.75f, 1f) else 1f
        }
    }

    var zoomAnimJob by remember { mutableStateOf<Job?>(null) }
    var flingJob by remember { mutableStateOf<Job?>(null) }

    fun applyFlowVerticalDelta(screenDeltaY: Float, currentZoom: Float, viewHeight: Float) {
        if (screenDeltaY == 0f) return
        val safeZoom = currentZoom.coerceAtLeast(0.5f)
        if (safeZoom <= 1.02f) {
            panOffsetY = 0f
            listState.dispatchRawDelta(-screenDeltaY)
            return
        }
        val maxPanY = max(0f, (viewHeight * (safeZoom - 1f)) / 2f)
        var remainingScreenY = screenDeltaY

        // 1. First consume any existing boundary panOffsetY back toward 0f before scrolling the list
        if (panOffsetY > 0f && remainingScreenY < 0f) {
            val consumePan = max(remainingScreenY, -panOffsetY)
            panOffsetY += consumePan
            remainingScreenY -= consumePan
        } else if (panOffsetY < 0f && remainingScreenY > 0f) {
            val consumePan = kotlin.math.min(remainingScreenY, -panOffsetY)
            panOffsetY += consumePan
            remainingScreenY -= consumePan
        }

        // 2. Scroll the underlying LazyColumn in 1:1 screen space
        if (abs(remainingScreenY) > 0.01f) {
            val listDelta = -remainingScreenY / safeZoom
            val consumedList = listState.dispatchRawDelta(listDelta)
            val unconsumedScreenY = remainingScreenY + consumedList * safeZoom
            panOffsetY += unconsumedScreenY
        }

        // 3. Strictly clamp panOffsetY so it can ONLY be non-zero at the true top of Page 1 or true bottom of the last page
        val minPanBound = if (!listState.canScrollForward) -maxPanY else 0f
        val maxPanBound = if (!listState.canScrollBackward) maxPanY else 0f
        panOffsetY = panOffsetY.coerceIn(minPanBound, maxPanBound)
    }

    fun animateZoomAndPan(
        targetZoom: Float,
        targetPanX: Float = 0f,
        targetPanY: Float = 0f,
        focusScreenY: Float? = null
    ) {
        zoomAnimJob?.cancel()
        flingJob?.cancel()
        zoomAnimJob = coroutineScope.launch {
            val startZoom = zoomScale
            val startPanX = panOffsetX
            val startPanY = panOffsetY
            var prevZoom = startZoom
            val ch = if (containerSize.height > 0) containerSize.height.toFloat() else 1920f
            val centerY = ch / 2f
            animate(0f, 1f, animationSpec = tween(200, easing = FastOutSlowInEasing)) { progress, _ ->
                val newZoom = startZoom + (targetZoom - startZoom) * progress
                val stepRatio = if (prevZoom > 0.01f) newZoom / prevZoom else 1f
                zoomScale = newZoom
                panOffsetX = startPanX + (targetPanX - startPanX) * progress

                if (newZoom <= 1.0f && viewMode == PdfViewMode.CONTINUOUS_FLOW) {
                    panOffsetX = 0f
                    panOffsetY = 0f
                } else if (viewMode == PdfViewMode.CONTINUOUS_FLOW && focusScreenY != null && abs(stepRatio - 1f) > 0.0001f) {
                    val focusY = focusScreenY - centerY
                    val focalShiftY = (focusY - panOffsetY) * (1f - stepRatio)
                    applyFlowVerticalDelta(focalShiftY, newZoom, ch)
                } else {
                    panOffsetY = startPanY + (targetPanY - startPanY) * progress
                }
                prevZoom = newZoom
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
            var lastError: Exception? = null
            for (attempt in 1..2) {
                try {
                    val targetFile: File = when {
                        pdfSource.startsWith("http://") || pdfSource.startsWith("https://") || pdfSource.startsWith("data:") -> {
                            // 1. Check if a COMPLETE file is already in public Downloads
                            var foundFile: File? = null
                            try {
                                val candidateName = resolvedTitle.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                val candidates = listOf(
                                    File(downloadsDir, resolvedTitle),
                                    File(downloadsDir, candidateName),
                                    File(downloadsDir, "$candidateName.pdf")
                                )
                                for (cand in candidates) {
                                    if (cand.exists() && cand.length() > 512 &&
                                        (FileImportHelper.isCompletePdfFile(cand) || FileImportHelper.isDocxZip(cand))
                                    ) {
                                        foundFile = cand
                                        break
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                            if (foundFile != null) {
                                if (DocxToPdfConverter.isWordOrOfficeDocument(foundFile.name, null) || FileImportHelper.isDocxZip(foundFile)) {
                                    DocxToPdfConverter.convertToPdf(context, foundFile, resolvedTitle)
                                } else {
                                    FileImportHelper.sanitizePdfFile(foundFile, context, resolvedTitle)
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
                                    FileImportHelper.sanitizePdfFile(f, context, resolvedTitle)
                                }
                            } else {
                                val uri = Uri.parse(pdfSource)
                                val prepared = FileImportHelper.importAndPrepareDocument(context, uri, resolvedTitle)
                                prepared?.first ?: throw IllegalStateException("PDF file not found on device.")
                            }
                        }
                    }

                    val engine = PdfRenderEngine(targetFile, screenDensity)
                    if (engine.pageCount <= 0) {
                        engine.close()
                        throw IllegalStateException("Document contains no readable pages.")
                    }
                    renderEngine = engine
                    pageCount = engine.pageCount
                    currentPageIndex = 0
                    localPdfFile = targetFile
                    errorMessage = null
                    isDownloading = false
                    lastError = null
                    break
                } catch (e: Exception) {
                    e.printStackTrace()
                    lastError = e
                    if (attempt == 1) {
                        delay(650)
                    }
                }
            }
            if (lastError != null && renderEngine == null) {
                errorMessage = "Document Error: ${lastError.localizedMessage ?: "Invalid or encrypted document format"}"
                isDownloading = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                renderEngine?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

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

    // Adaptive NestedScroll connection:
    // Immediately hides floating ball & chrome on ANY scroll (up or down).
    // Tracks whether the user's scroll direction was upward so the ball only comes back up after upward scrolling stops.
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (zoomScale <= 1.05f) {
                    if (abs(available.y) > 4f && barsVisible && !isOrbMenuOpen) {
                        barsVisible = false
                    }
                    if (available.y < -6f) {
                        lastPdfScrollWasUp = false
                    } else if (available.y > 6f) {
                        lastPdfScrollWasUp = true
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
        // MAIN CONTENT AREA: SWIPEABLE PAGES WITH GPU-PREPPED GRAPHICS
        if (pageCount > 0 && renderEngine != null) {
            val engine = renderEngine!!

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { containerSize = it }
                    .pointerInput(viewMode) {
                        awaitEachGesture {
                            zoomAnimJob?.cancel()
                            flingJob?.cancel()
                            val firstDown = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            zoomAnimJob?.cancel()
                            flingJob?.cancel()
                            val downTime = System.currentTimeMillis()
                            val downPos = firstDown.position
                            var isMultiTouch = false
                            var hasMoved = false
                            val touchSlop = viewConfiguration.touchSlop
                            val velocityTracker = VelocityTracker()
                            velocityTracker.addPosition(firstDown.uptimeMillis, firstDown.position)

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
                                    flingJob?.cancel()
                                    // TWO-FINGER GESTURE: Exact centroid focal-point pinch-to-zoom & pan
                                    isMultiTouch = true
                                    hasMoved = true
                                    isZoomGestureScrolling = true
                                    if (barsVisible && !isOrbMenuOpen) {
                                        barsVisible = false
                                    }

                                    val centroid = event.calculateCentroid(useCurrent = true)
                                    val rawZoom = event.calculateZoom()
                                    val panDelta = event.calculatePan()

                                    if (rawZoom > 0f && rawZoom != 1f) {
                                        val oldZoom = zoomScale
                                        val targetZoom = (oldZoom * rawZoom).coerceIn(0.75f, 5.0f)
                                        val scaleRatio = targetZoom / oldZoom

                                        // Exact centroid focal shift: keeps pinched X and Y spot locked under fingers
                                        val focusX = centroid.x - centerX
                                        val focusY = centroid.y - centerY
                                        val focalShiftX = (focusX - panOffsetX) * (1f - scaleRatio)
                                        val focalShiftY = (focusY - panOffsetY) * (1f - scaleRatio)

                                        zoomScale = targetZoom
                                        val maxPanX = max(0f, (cw * (zoomScale - 1f)) / 2f)
                                        panOffsetX = if (zoomScale <= 1.0f) 0f else (panOffsetX + focalShiftX + panDelta.x).coerceIn(-maxPanX, maxPanX)

                                        if (viewMode == PdfViewMode.CONTINUOUS_FLOW) {
                                            if (zoomScale <= 1.0f) {
                                                panOffsetY = 0f
                                                if (panDelta.y != 0f) {
                                                    listState.dispatchRawDelta(-panDelta.y)
                                                }
                                            } else {
                                                applyFlowVerticalDelta(focalShiftY + panDelta.y, zoomScale, ch)
                                            }
                                        } else {
                                            val maxPanY = max(0f, (ch * (zoomScale - 1f)) / 2f)
                                            panOffsetY = (panOffsetY + focalShiftY + panDelta.y).coerceIn(-maxPanY, maxPanY)
                                        }
                                    } else if (panDelta != Offset.Zero) {
                                        val maxPanX = max(0f, (cw * (zoomScale - 1f)) / 2f)
                                        panOffsetX = (panOffsetX + panDelta.x).coerceIn(-maxPanX, maxPanX)

                                        if (viewMode == PdfViewMode.CONTINUOUS_FLOW) {
                                            applyFlowVerticalDelta(panDelta.y, zoomScale, ch)
                                        } else {
                                            val maxPanY = max(0f, (ch * (zoomScale - 1f)) / 2f)
                                            panOffsetY = (panOffsetY + panDelta.y).coerceIn(-maxPanY, maxPanY)
                                        }
                                    }

                                    // Intercept and consume so underlying pager/list does not jitter during pinch
                                    event.changes.forEach { it.consume() }
                                } else if (count == 1) {
                                    val change = activePointers.first()
                                    if (!isMultiTouch) {
                                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                                        val dist = (change.position - downPos).getDistance()
                                        if (dist > touchSlop) {
                                            hasMoved = true
                                        }

                                        if (zoomScale > 1.05f) {
                                            // ONE FINGER DRAG WHILE ZOOMED IN: Smoothly scroll and pan without multi-touch jump
                                            if (dist > touchSlop || hasMoved) {
                                                val panDelta = event.calculatePan()
                                                if (panDelta != Offset.Zero) {
                                                    isZoomGestureScrolling = true
                                                    if (barsVisible && !isOrbMenuOpen) {
                                                        barsVisible = false
                                                    }
                                                    val maxPanX = max(0f, (cw * (zoomScale - 1f)) / 2f)
                                                    panOffsetX = (panOffsetX + panDelta.x).coerceIn(-maxPanX, maxPanX)

                                                    if (viewMode == PdfViewMode.CONTINUOUS_FLOW) {
                                                        // In Flow mode: Seamless 1:1 list scrolling in exact swipe direction
                                                        if (panDelta.y != 0f) {
                                                            applyFlowVerticalDelta(panDelta.y, zoomScale, ch)
                                                            if (panDelta.y < -6f) {
                                                                lastPdfScrollWasUp = false
                                                            } else if (panDelta.y > 6f) {
                                                                lastPdfScrollWasUp = true
                                                            }
                                                        }
                                                    } else {
                                                        // In Page Swipe mode: Pan inside the current page
                                                        val maxPanY = max(0f, (ch * (zoomScale - 1f)) / 2f)
                                                        panOffsetY = (panOffsetY + panDelta.y).coerceIn(-maxPanY, maxPanY)
                                                        if (panDelta.y < -6f) {
                                                            lastPdfScrollWasUp = false
                                                        } else if (panDelta.y > 6f) {
                                                            lastPdfScrollWasUp = true
                                                        }
                                                    }
                                                }
                                                change.consume()
                                            }
                                        }
                                    } else {
                                        // Finger lifted after 2-finger pinch: consume remaining single-finger frames so page doesn't jump
                                        if (zoomScale > 1.05f) {
                                            change.consume()
                                        }
                                    }
                                    // When zoomScale <= 1.05f and !isMultiTouch, we DO NOT consume!
                                    // Native gestures scroll list or flip pages freely
                                }
                            } while (event.changes.any { it.pressed })

                            // Fling inertia on release in zoomed Flow mode (cancellable on next touch down)
                            if (viewMode == PdfViewMode.CONTINUOUS_FLOW && zoomScale > 1.05f && hasMoved && !isMultiTouch) {
                                val velocity = velocityTracker.calculateVelocity()
                                if (abs(velocity.y) > 95f) {
                                    val totalScreenFlingY = (velocity.y * 0.28f).coerceIn(-ch * 2.4f, ch * 2.4f)
                                    val durationMs = (240 + (abs(velocity.y) / 18f).toInt()).coerceIn(240, 520)
                                    flingJob = coroutineScope.launch {
                                        try {
                                            var prevVal = 0f
                                            animate(
                                                initialValue = 0f,
                                                targetValue = totalScreenFlingY,
                                                animationSpec = tween(durationMs, easing = LinearOutSlowInEasing)
                                            ) { currentVal, _ ->
                                                val stepDelta = currentVal - prevVal
                                                prevVal = currentVal
                                                applyFlowVerticalDelta(stepDelta, zoomScale, ch)
                                            }
                                        } catch (_: Exception) {
                                        } finally {
                                            isZoomGestureScrolling = false
                                        }
                                    }
                                } else {
                                    isZoomGestureScrolling = false
                                }
                            } else {
                                isZoomGestureScrolling = false
                            }

                            // Snap-back bounds checking on finger release
                            val maxPanX = max(0f, (cw * (zoomScale - 1f)) / 2f)
                            val maxPanY = max(0f, (ch * (zoomScale - 1f)) / 2f)
                            val minAllowedPanY = if (viewMode == PdfViewMode.CONTINUOUS_FLOW && listState.canScrollForward) 0f else -maxPanY
                            val maxAllowedPanY = if (viewMode == PdfViewMode.CONTINUOUS_FLOW && listState.canScrollBackward) 0f else maxPanY

                            if (zoomScale < 0.75f) {
                                // Snap back to minimum zoom if pinched out too far
                                animateZoomAndPan(0.75f, 0f, 0f)
                            } else if (zoomScale > 5.0f) {
                                animateZoomAndPan(
                                    5.0f,
                                    panOffsetX.coerceIn(-maxPanX, maxPanX),
                                    panOffsetY.coerceIn(minAllowedPanY, maxAllowedPanY)
                                )
                            } else if (zoomScale > 1.05f) {
                                val clampedX = panOffsetX.coerceIn(-maxPanX, maxPanX)
                                val clampedY = panOffsetY.coerceIn(minAllowedPanY, maxAllowedPanY)
                                if (clampedX != panOffsetX || clampedY != panOffsetY) {
                                    animateZoomAndPan(zoomScale, clampedX, clampedY)
                                }
                            } else if (zoomScale < 0.98f) {
                                // In zoomed out mode (0.75f..0.98f): preserve zoomed out view, keep centered
                                if (panOffsetX != 0f || panOffsetY != 0f) {
                                    animateZoomAndPan(zoomScale, 0f, 0f)
                                }
                            } else {
                                // Around 1.0x zoom: keep pan offsets strictly 0f
                                if (panOffsetX != 0f || panOffsetY != 0f) {
                                    panOffsetX = 0f
                                    panOffsetY = 0f
                                }
                            }

                            // Tap & Double Tap detection (fires only when stationary single touch)
                            if (!isMultiTouch && !hasMoved) {
                                val upTime = System.currentTimeMillis()
                                if (upTime - downTime < 320L) {
                                    if (upTime - lastTapTime < 320L) {
                                        // DOUBLE TAP: If zoomed in OR zoomed out, fix to 1x zoom. If at 1x, zoom into tapped spot (2.25x)
                                        lastTapTime = 0L
                                        if (abs(zoomScale - 1f) > 0.06f) {
                                            // Fix to 1x zoom for both zoomed in and zoomed out modes!
                                            animateZoomAndPan(1f, 0f, 0f)
                                        } else {
                                            val focusX = downPos.x - centerX
                                            val focusY = downPos.y - centerY
                                            val maxPanX2 = (cw * 1.25f) / 2f
                                            val maxPanY2 = (ch * 1.25f) / 2f
                                            val targetPanX = (-focusX * 1.25f).coerceIn(-maxPanX2, maxPanX2)
                                            val targetPanY = (-focusY * 1.25f).coerceIn(-maxPanY2, maxPanY2)
                                            animateZoomAndPan(
                                                targetZoom = 2.25f,
                                                targetPanX = targetPanX,
                                                targetPanY = targetPanY,
                                                focusScreenY = downPos.y
                                            )
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
                    // HORIZONTAL SWIPE PAGER WITH EDGE-TO-EDGE GAME MODE VIEW
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
                        pageSpacing = 10.dp,
                        userScrollEnabled = isNativeScrollEnabled
                    ) { pageIdx ->
                        PdfSinglePageView(
                            pageIndex = pageIdx,
                            engine = engine,
                            colorFilter = paperColorFilter,
                            isSwipeMode = true,
                            isHighZoom = isHighZoomMode,
                            isUserScrolling = isUserScrolling,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 2.dp)
                        )
                    }
                } else {
                    // CONTINUOUS VERTICAL FLOW:
                    // When zoomScale < 1f (e.g. 0.75x), LazyColumn stays 100% full-screen height so there is ZERO
                    // top or bottom viewport gap when scrolling upside/downside, while each page scales to zoomScale width & height.
                    LazyColumn(
                        state = listState,
                        userScrollEnabled = isNativeScrollEnabled,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val flowLayerScale = zoomScale.coerceAtLeast(1f)
                                scaleX = flowLayerScale
                                scaleY = flowLayerScale
                                translationX = if (zoomScale > 1f) panOffsetX else 0f
                                translationY = if (zoomScale > 1f) panOffsetY else 0f
                            },
                        contentPadding = PaddingValues(vertical = 0.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(
                            count = pageCount,
                            key = { it }
                        ) { pageIdx ->
                            PdfSinglePageView(
                                pageIndex = pageIdx,
                                engine = engine,
                                colorFilter = paperColorFilter,
                                isSwipeMode = false,
                                isHighZoom = isHighZoomMode,
                                isUserScrolling = isUserScrolling,
                                modifier = Modifier
                                    .fillMaxWidth(itemWidthFraction)
                                    .padding(horizontal = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // PREMIUM REALISTIC AURA LOADING OVERLAY
        if (isDownloading) {
            val pdfLoadTransition = rememberInfiniteTransition(label = "PdfAuraLoad")
            val orbitAngle by pdfLoadTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3600, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "PdfOrbit"
            )
            val auraBreath by pdfLoadTransition.animateFloat(
                initialValue = 0.88f,
                targetValue = 1.12f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "PdfAuraBreath"
            )
            val effectiveProgress = if (downloadProgress > 0.02f) downloadProgress.coerceIn(0f, 1f) else 0.38f

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xF204070E)),
                contentAlignment = Alignment.Center
            ) {
                // Background Ambient Aura Bloom
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val baseR = minOf(size.width, size.height) * 0.45f * auraBreath
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF00E5FF).copy(alpha = 0.16f),
                                Color(0xFF6366F1).copy(alpha = 0.09f),
                                Color.Transparent
                            ),
                            center = Offset(cx, cy),
                            radius = baseR.coerceAtLeast(10f)
                        ),
                        center = Offset(cx, cy),
                        radius = baseR.coerceAtLeast(10f)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(28.dp)
                        .widthIn(min = 280.dp, max = 360.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF162038).copy(alpha = 0.92f),
                                    Color(0xFF0A0F1D).copy(alpha = 0.97f)
                                )
                            ),
                            RoundedCornerShape(30.dp)
                        )
                        .border(
                            BorderStroke(
                                1.2.dp,
                                Brush.linearGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.38f),
                                        Color(0xFF38BDF8).copy(alpha = 0.32f),
                                        Color(0xFF818CF8).copy(alpha = 0.16f)
                                    )
                                )
                            ),
                            RoundedCornerShape(30.dp)
                        )
                        .padding(horizontal = 28.dp, vertical = 28.dp)
                ) {
                    // 3D Glass Medallion with Orbital Aura Ring
                    Box(
                        modifier = Modifier.size(86.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val r = size.minDimension / 2f
                            // Subtle outer track
                            drawCircle(
                                color = Color.White.copy(alpha = 0.08f),
                                radius = r - 4.dp.toPx(),
                                style = Stroke(width = 2.dp.toPx())
                            )
                            // Rotating dual-arc aura ring
                            drawArc(
                                brush = Brush.sweepGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color(0xFF38BDF8),
                                        Color(0xFFA855F7),
                                        Color.Transparent
                                    )
                                ),
                                startAngle = orbitAngle,
                                sweepAngle = 220f,
                                useCenter = false,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            Color(0xFF1E293B),
                                            Color(0xFF090D16)
                                        )
                                    )
                                )
                                .border(
                                    1.dp,
                                    Brush.linearGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.42f),
                                            Color(0xFF38BDF8).copy(alpha = 0.2f)
                                        )
                                    ),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Preparing High-Clarity Document",
                        color = Color(0xFFF8FAFC),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = resolvedTitle,
                        color = Color(0xFF94A3B8),
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Realistic Progress Track & Percentage
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(effectiveProgress)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color(0xFF38BDF8),
                                            Color(0xFF818CF8),
                                            Color(0xFFC084FC)
                                        )
                                    )
                                )
                        )
                    }

                    if (downloadProgress > 0f) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${(downloadProgress * 100).toInt()}% Synchronized",
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp,
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
                onSelectTheme = { selectedTheme ->
                    paperTheme = selectedTheme
                },
                onResetZoom = {
                    animateZoomAndPan(1f, 0f, 0f)
                },
                onClose = {
                    isOrbMenuOpen = false
                }
            )
        }

        // INTERACTIVE ZOOM LEVEL PILL IN BOTTOM LEFT (tap to reset to 1x)
        AnimatedVisibility(
            visible = pageCount > 0 && !isOrbMenuOpen && abs(zoomScale - 1f) > 0.04f,
            enter = fadeIn(animationSpec = tween(180)) + scaleIn(animationSpec = tween(180), transformOrigin = TransformOrigin(0f, 1f)),
            exit = fadeOut(animationSpec = tween(180)) + scaleOut(animationSpec = tween(180), transformOrigin = TransformOrigin(0f, 1f)),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 18.dp, bottom = 20.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .shadow(10.dp, RoundedCornerShape(22.dp), spotColor = Color(0x6600E5FF))
                    .clickable { animateZoomAndPan(1f, 0f, 0f) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.24f),
                                    Color(0xFF131A2E).copy(alpha = 0.88f),
                                    Color(0xFF090D18).copy(alpha = 0.96f)
                                )
                            )
                        )
                        .border(
                            BorderStroke(1.dp, Brush.linearGradient(listOf(Color.White.copy(0.6f), Color(0xFF00E5FF).copy(0.45f)))),
                            RoundedCornerShape(22.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = "${(zoomScale * 100).roundToInt()}%",
                        color = Color(0xFF00E5FF),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (zoomScale >= 1.55f) {
                        Text(
                            text = "HD",
                            color = Color(0xFF00E676),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Text(
                        text = "· 1x",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // MINIMALIST FLOATING CONTROL BALL (ORB) IN BOTTOM RIGHT CORNER
        // Hides underneath the screen when scrolling down and comes back up when scrolling up
        AnimatedVisibility(
            visible = (barsVisible || isOrbMenuOpen) && pageCount > 0,
            enter = slideInVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                initialOffsetY = { it * 2 }
            ) + fadeIn(animationSpec = tween(200)) + scaleIn(
                animationSpec = tween(200),
                transformOrigin = TransformOrigin(1f, 1f)
            ),
            exit = slideOutVertically(
                animationSpec = tween(220, easing = FastOutSlowInEasing),
                targetOffsetY = { it * 2 }
            ) + fadeOut(animationSpec = tween(180)) + scaleOut(
                animationSpec = tween(180),
                transformOrigin = TransformOrigin(1f, 1f)
            ),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(end = 18.dp, bottom = 18.dp)
        ) {
            LiquidGlassOrbButton(
                onClick = { isOrbMenuOpen = !isOrbMenuOpen },
                isOpen = isOrbMenuOpen,
                pageText = "${currentPageIndex + 1} / $pageCount",
                accentColor = Color(0xFF38BDF8)
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
