package com.example.ui.pdf

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Ultra-Fast Single-Quad PDF Page Renderer optimized for budget & mid-range GPUs (e.g. Samsung F13 / Exynos 850):
 * - Skips creating a [LaunchedEffect] coroutine entirely when the page's sharp bitmap is already in cache.
 * - Uses strict native page aspect ratio [PdfRenderEngine.getPageAspectRatio] and [ContentScale.FillBounds]
 *   so page height never shifts by even 1px and never creates gaps when unzooming or switching from preview to HD.
 * - Defers uncached bitmap allocation & GPU texture uploads while [PdfRenderEngine.isUserActivelyScrolling] is true,
 *   so fast swipe-flings glide at 60/120fps with zero mid-fling GPU stalls.
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
                // Wait while user is actively flinging so pages flying past never upload GPU textures mid-fling
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
            // so visible pages ALWAYS reach crisp HD / Ultra-HD once the user stops scrolling.
            val requiredStillMs = engine.settleDelayMs + (pageIndex % 2) * 55L
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
                        delay(65L)
                    } else {
                        delay(65L)
                        if (!engine.isUserActivelyScrolling) {
                            quietElapsed += 65L
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
                delay(75L)
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
                .background(if (displayImage != null) Color.White else Color(0xFF141928)),
            contentAlignment = Alignment.Center
        ) {
            if (displayImage != null) {
                Image(
                    bitmap = displayImage,
                    contentDescription = "Page ${pageIndex + 1}",
                    contentScale = ContentScale.FillBounds,
                    colorFilter = colorFilter,
                    filterQuality = FilterQuality.Low,
                    modifier = Modifier.fillMaxSize()
                )
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
