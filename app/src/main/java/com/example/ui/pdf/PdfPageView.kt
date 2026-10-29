package com.example.ui.pdf

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Google Drive-grade PDF Page renderer with High-DPI base rendering,
 * seamless in-place Ultra-HD upgrade during microscopic zoom, and FilterQuality.High text clarity.
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
    var pageBitmap by remember(pageIndex, engine) {
        mutableStateOf(
            if (isUserScrolling) {
                engine.getCachedPreviewOnly(pageIndex) ?: engine.getCachedBitmap(pageIndex, ultraHd = false)
            } else {
                engine.getCachedBitmap(pageIndex, ultraHd = isHighZoom)
            }
        )
    }
    var wasScrolling by remember(pageIndex, engine) { mutableStateOf(false) }

    // 1. Ensure fast low-res preview is loaded immediately so no page is blank while scrolling
    LaunchedEffect(pageIndex, engine, isUserScrolling) {
        if (isUserScrolling) {
            wasScrolling = true
            val preview = engine.getCachedPreviewOnly(pageIndex) ?: engine.getPreviewBitmap(pageIndex)
            if (preview != null) {
                pageBitmap = preview
            }
        } else if (pageBitmap == null) {
            val preview = engine.getPreviewBitmap(pageIndex)
            if (preview != null && pageBitmap == null) {
                pageBitmap = preview
            }
        }
    }

    // 2. When scrolling stops (or when zoomed in), progressively sharpen text chunk-by-chunk (Google Drive style)
    LaunchedEffect(pageIndex, engine, isUserScrolling, isHighZoom) {
        if (!isUserScrolling) {
            if (wasScrolling || isHighZoom) {
                kotlinx.coroutines.delay(85)
            }
            val shouldForceChunks = wasScrolling
            engine.renderPageInChunks(
                pageIndex = pageIndex,
                ultraHd = isHighZoom,
                totalChunks = 4,
                forceChunkReveal = shouldForceChunks
            ) { chunkBitmap, _ ->
                pageBitmap = chunkBitmap
            }
            wasScrolling = false
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val currentBmp = pageBitmap
        if (currentBmp != null) {
            val imageBitmap = remember(currentBmp) { currentBmp.asImageBitmap() }
            val aspect = (currentBmp.width.toFloat() / currentBmp.height.toFloat().coerceAtLeast(1f)).coerceIn(0.15f, 6f)
            Box(
                modifier = if (isSwipeMode) {
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.18f)), RoundedCornerShape(8.dp))
                        .background(Color.White)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 3.dp)
                        .aspectRatio(aspect)
                        .clip(RoundedCornerShape(6.dp))
                        .border(BorderStroke(0.5.dp, Color.Black.copy(alpha = 0.12f)), RoundedCornerShape(6.dp))
                        .background(Color.White)
                },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Page ${pageIndex + 1}",
                    contentScale = if (isSwipeMode) ContentScale.Fit else ContentScale.FillWidth,
                    colorFilter = colorFilter,
                    filterQuality = if (isUserScrolling) FilterQuality.Low else FilterQuality.Medium,
                    modifier = if (isSwipeMode) Modifier.fillMaxSize() else Modifier.fillMaxWidth().wrapContentHeight()
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp)
                    .padding(horizontal = 4.dp, vertical = 3.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(8.dp))
                    .background(Color(0xFF141928)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF00E5FF),
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Rendering Page ${pageIndex + 1}...",
                        color = Color(0xFF90CAF9),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
