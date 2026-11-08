package com.example.ui.pdf

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Google Drive-Grade PDF Page Renderer with Vector Viewport Tile Overlay:
 * - Renders base page in full High-DPI with [FilterQuality.Medium] mipmap interpolation for crisp 1x reading.
 * - When zoomed in (`zoomScale > 1.12f`) and settled (`zoomViewportRevision > 0`), computes the exact visible
 *   sub-region of the page on screen and rasterizes a native vector [ZoomedPageTile] directly from [android.graphics.pdf.PdfRenderer.Page]
 *   at full screen pixel density — delivering razor-sharp vector text up to 4.0x zoom without allocating giant full-page bitmaps.
 * - Uses strict native page aspect ratio [PdfRenderEngine.getPageAspectRatio] and [ContentScale.FillBounds]
 *   so page height never shifts by even 1px and never creates gaps when unzooming or switching from preview to HD.
 */
@Composable
fun PdfSinglePageView(
    pageIndex: Int,
    engine: PdfRenderEngine,
    colorFilter: ColorFilter? = null,
    isSwipeMode: Boolean = true,
    isHighZoom: Boolean = false,
    isUserScrolling: Boolean = false,
    zoomScaleProvider: () -> Float = { 1f },
    zoomViewportRevision: Int = 0,
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
    var zoomedTile by remember(pageIndex, engine) {
        mutableStateOf<ZoomedPageTile?>(null)
    }
    var pageCoords by remember {
        mutableStateOf<LayoutCoordinates?>(null)
    }

    // Immediately clear zoomed tile when unzoomed back to 1x
    if (zoomViewportRevision == 0 && zoomedTile != null) {
        zoomedTile = null
    }

    val needsBackgroundRender = sharpImage == null || (isHighZoom && !engine.hasCachedUltraHd(pageIndex))

    if (needsBackgroundRender) {
        LaunchedEffect(pageIndex, engine, isHighZoom) {
            // 1. Check synchronous cache first (0ms fast path)
            val immediateTarget = engine.getCachedSharpImage(pageIndex, ultraHd = isHighZoom)
            if (immediateTarget != null) {
                sharpImage = immediateTarget
                return@LaunchedEffect
            }
            if (sharpImage == null) {
                engine.getCachedSharpImage(pageIndex, ultraHd = false)?.let {
                    sharpImage = it
                }
            }

            // 2. Load lightweight preview if no image is available yet
            while (isActive && sharpImage == null && previewImage == null) {
                val cachedPrev = engine.getCachedPreviewImage(pageIndex)
                if (cachedPrev != null) {
                    previewImage = cachedPrev
                    break
                }
                while (isActive && engine.isUserActivelyScrolling) {
                    delay(engine.flingGateDelayMs)
                }
                if (!isActive) break
                val renderedPrev = engine.getPreviewImage(pageIndex)
                if (renderedPrev != null) {
                    previewImage = renderedPrev
                    break
                }
                delay(50L)
            }

            // 3. Wait for continuous stillness (settleDelayMs + stagger) and retry if interrupted by a scroll
            val requiredStillMs = engine.settleDelayMs + (pageIndex % 2) * 45L
            while (isActive) {
                val cachedSharp = engine.getCachedSharpImage(pageIndex, ultraHd = isHighZoom)
                if (cachedSharp != null) {
                    sharpImage = cachedSharp
                    break
                }

                var quietElapsed = 0L
                while (isActive && quietElapsed < requiredStillMs) {
                    if (engine.isUserActivelyScrolling) {
                        quietElapsed = 0L
                        delay(55L)
                    } else {
                        delay(55L)
                        if (!engine.isUserActivelyScrolling) {
                            quietElapsed += 55L
                        } else {
                            quietElapsed = 0L
                        }
                    }
                }

                if (!isActive) break
                val renderedSharp = engine.getPageImage(pageIndex, ultraHd = isHighZoom)
                if (renderedSharp != null) {
                    sharpImage = renderedSharp
                    break
                }
                delay(65L)
            }
        }
    }

    // 4. Google Drive Vector Viewport Tile Renderer:
    // Whenever zoom settles (zoomViewportRevision > 0), rasterize the visible crop directly from PDF vector paths
    if (zoomViewportRevision > 0) {
        LaunchedEffect(pageIndex, engine, zoomViewportRevision) {
            val currentZoom = zoomScaleProvider()
            if (currentZoom <= 1.12f) {
                zoomedTile = null
                return@LaunchedEffect
            }
            val coords = pageCoords
            if (coords == null || !coords.isAttached) return@LaunchedEffect

            val rootSize = coords.findRootCoordinates().size
            val winW = rootSize.width.toFloat()
            val winH = rootSize.height.toFloat()
            if (winW <= 1f || winH <= 1f) return@LaunchedEffect

            val bounds = coords.boundsInWindow()
            if (bounds.width <= 1f || bounds.height <= 1f ||
                bounds.right <= 0f || bounds.bottom <= 0f ||
                bounds.left >= winW || bounds.top >= winH
            ) {
                zoomedTile = null
                return@LaunchedEffect
            }

            val visLeft = ((0f - bounds.left) / bounds.width).coerceIn(0f, 1f)
            val visTop = ((0f - bounds.top) / bounds.height).coerceIn(0f, 1f)
            val visRight = ((winW - bounds.left) / bounds.width).coerceIn(0f, 1f)
            val visBottom = ((winH - bounds.top) / bounds.height).coerceIn(0f, 1f)

            if (visRight - visLeft < 0.03f || visBottom - visTop < 0.03f) {
                zoomedTile = null
                return@LaunchedEffect
            }

            // Add 12% overscan padding around visible viewport so small pans remain inside crisp vector tile
            val padX = (visRight - visLeft) * 0.12f
            val padY = (visBottom - visTop) * 0.12f
            val normLeft = (visLeft - padX).coerceIn(0f, 0.95f)
            val normTop = (visTop - padY).coerceIn(0f, 0.95f)
            val normRight = (visRight + padX).coerceIn(normLeft + 0.05f, 1f)
            val normBottom = (visBottom + padY).coerceIn(normTop + 0.05f, 1f)
            val targetBucket = (currentZoom * 10f).roundToInt()

            val existing = zoomedTile
            if (existing != null &&
                existing.zoomBucket == targetBucket &&
                abs(existing.normLeft - normLeft) < 0.015f &&
                abs(existing.normTop - normTop) < 0.015f &&
                abs(existing.normRight - normRight) < 0.015f &&
                abs(existing.normBottom - normBottom) < 0.015f
            ) {
                return@LaunchedEffect
            }

            while (isActive && engine.isUserActivelyScrolling) {
                delay(45L)
            }
            if (!isActive) return@LaunchedEffect

            val tile = engine.renderZoomedTile(
                pageIndex = pageIndex,
                normLeft = normLeft,
                normTop = normTop,
                normRight = normRight,
                normBottom = normBottom,
                zoomScale = currentZoom
            )
            if (isActive && tile != null && zoomScaleProvider() > 1.12f) {
                zoomedTile = tile
            }
        }
    }

    val displayImage = sharpImage ?: previewImage
    val aspect = engine.getPageAspectRatio(pageIndex)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .onGloballyPositioned { pageCoords = it }
                .background(if (displayImage != null) Color.White else Color(0xFF141928)),
            contentAlignment = Alignment.Center
        ) {
            if (displayImage != null) {
                Image(
                    bitmap = displayImage,
                    contentDescription = "Page ${pageIndex + 1}",
                    contentScale = ContentScale.FillBounds,
                    colorFilter = colorFilter,
                    filterQuality = FilterQuality.Medium,
                    modifier = Modifier.fillMaxSize()
                )
                val activeTile = zoomedTile
                if (activeTile != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val dstLeft = (activeTile.normLeft * size.width).roundToInt()
                        val dstTop = (activeTile.normTop * size.height).roundToInt()
                        val dstRight = (activeTile.normRight * size.width).roundToInt()
                        val dstBottom = (activeTile.normBottom * size.height).roundToInt()
                        val dstW = (dstRight - dstLeft).coerceAtLeast(1)
                        val dstH = (dstBottom - dstTop).coerceAtLeast(1)
                        drawImage(
                            image = activeTile.bitmap,
                            dstOffset = IntOffset(dstLeft, dstTop),
                            dstSize = IntSize(dstW, dstH),
                            colorFilter = colorFilter,
                            filterQuality = FilterQuality.Medium
                        )
                    }
                }
            } else {
                Text(
                    text = "Page ${pageIndex + 1}",
                    color = Color(0xFF64748B),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
