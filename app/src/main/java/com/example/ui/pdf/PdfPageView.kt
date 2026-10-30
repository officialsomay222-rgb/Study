package com.example.ui.pdf

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val SwipePageShape = RoundedCornerShape(6.dp)
private val FlowPageShape = RoundedCornerShape(4.dp)

/**
 * Google Drive-grade On-Demand PDF Page Renderer:
 * - Uses exact pre-computed page aspect ratio from frame 0 so LazyColumn never experiences layout height jumps.
 * - Uses GPU-prepped ImageBitmaps directly from PdfRenderEngine (zero UI-thread bitmap conversion).
 * - Suspends HD/Ultra-HD rendering while scrolling so CPU/GPU power is used strictly on demand when settled,
 *   then reveals crisp text in 4 smooth progressive bands.
 */
@Composable
fun PdfSinglePageView(
    pageIndex: Int,
    engine: PdfRenderEngine,
    colorFilter: ColorFilter? = null,
    isSwipeMode: Boolean = true,
    isHighZoom: Boolean = false,
    isUserScrolling: Boolean = false,
    modifier: Modifier = Modifier
) {
    var previewImage by remember(pageIndex, engine) {
        mutableStateOf(engine.getCachedPreviewImage(pageIndex))
    }
    var sharpImage by remember(pageIndex, engine) {
        mutableStateOf(
            engine.getCachedSharpImage(pageIndex, ultraHd = isHighZoom)
                ?: engine.getCachedSharpImage(pageIndex, ultraHd = false)
        )
    }
    // 0 = soft preview (while scrolling), 1..3 = progressive horizontal chunks, 4 = 100% sharp
    var revealedChunks by remember(pageIndex, engine) {
        mutableIntStateOf(if (sharpImage != null && !isUserScrolling) 4 else 0)
    }

    LaunchedEffect(pageIndex, engine, isUserScrolling, isHighZoom) {
        // Step 1: Ensure fast preview is loaded immediately if nothing is cached yet
        if (previewImage == null) {
            val cachedPrev = engine.getCachedPreviewImage(pageIndex)
            if (cachedPrev != null) {
                previewImage = cachedPrev
            } else if (sharpImage == null || isUserScrolling) {
                val renderedPrev = engine.getPreviewImage(pageIndex)
                if (renderedPrev != null) {
                    previewImage = renderedPrev
                }
            }
        }

        // Step 2: While actively scrolling, use lightweight preview only (0% HD render power overhead)
        if (isUserScrolling) {
            if (previewImage != null) {
                revealedChunks = 0
            }
            return@LaunchedEffect
        }

        // Step 3: Scrolling stopped -> check if sharp image is already cached; otherwise debounce 110ms
        // so quick back-to-back scroll flicks never waste CPU/GPU power
        var targetSharp = engine.getCachedSharpImage(pageIndex, ultraHd = isHighZoom)
        if (targetSharp == null) {
            delay(110)
            if (isUserScrolling) return@LaunchedEffect
            targetSharp = engine.getPageImage(pageIndex, ultraHd = isHighZoom)
        }

        if (targetSharp != null) {
            val needChunkReveal = revealedChunks < 4 && previewImage != null
            sharpImage = targetSharp
            if (needChunkReveal) {
                for (chunk in 1..4) {
                    revealedChunks = chunk
                    if (chunk < 4) {
                        delay(18)
                    }
                }
            } else {
                revealedChunks = 4
            }
        }
    }

    val currentSharp = sharpImage
    val currentPreview = previewImage
    val primaryImage = when {
        isUserScrolling && currentPreview != null -> currentPreview
        currentSharp != null && (revealedChunks >= 4 || currentPreview == null) -> currentSharp
        currentPreview != null -> currentPreview
        else -> currentSharp
    }

    val aspect = if (primaryImage != null && primaryImage.height > 0) {
        (primaryImage.width.toFloat() / primaryImage.height.toFloat()).coerceIn(0.2f, 5.0f)
    } else {
        engine.getPageAspectRatio(pageIndex)
    }
    val pageShape = if (isSwipeMode) SwipePageShape else FlowPageShape

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .clip(pageShape)
                .border(
                    BorderStroke(
                        0.5.dp,
                        if (isSwipeMode) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.10f)
                    ),
                    pageShape
                )
                .background(if (primaryImage != null) Color.White else Color(0xFF141928)),
            contentAlignment = Alignment.Center
        ) {
            if (primaryImage != null) {
                val showPartialSharpOverlay = !isUserScrolling &&
                    revealedChunks in 1..3 &&
                    currentSharp != null &&
                    primaryImage === currentPreview

                Image(
                    bitmap = primaryImage,
                    contentDescription = "Page ${pageIndex + 1}",
                    contentScale = ContentScale.FillWidth,
                    colorFilter = colorFilter,
                    filterQuality = if (isUserScrolling || primaryImage === currentPreview) FilterQuality.Low else FilterQuality.Medium,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (showPartialSharpOverlay && currentSharp != null) {
                                Modifier.drawWithContent {
                                    drawContent()
                                    val clipBottom = size.height * (revealedChunks / 4f)
                                    clipRect(
                                        left = 0f,
                                        top = 0f,
                                        right = size.width,
                                        bottom = clipBottom
                                    ) {
                                        drawImage(
                                            image = currentSharp,
                                            dstOffset = IntOffset.Zero,
                                            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                                            colorFilter = colorFilter,
                                            filterQuality = FilterQuality.Medium
                                        )
                                    }
                                }
                            } else {
                                Modifier
                            }
                        )
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF00E5FF),
                        strokeWidth = 2.8.dp,
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Page ${pageIndex + 1}",
                        color = Color(0xFF90CAF9),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
