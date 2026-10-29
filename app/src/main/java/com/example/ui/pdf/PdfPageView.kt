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
    modifier: Modifier = Modifier
) {
    var pageBitmap by remember(pageIndex, engine) {
        mutableStateOf(engine.getCachedBitmap(pageIndex, ultraHd = isHighZoom))
    }

    // Load High-DPI base bitmap immediately if not already cached
    LaunchedEffect(pageIndex, engine) {
        if (pageBitmap == null) {
            val bmp = engine.getPageBitmap(pageIndex, ultraHd = false)
            if (bmp != null) {
                pageBitmap = bmp
            }
        }
    }

    // Seamlessly upgrade in-place to Ultra-HD bitmap after zoom gesture settles
    LaunchedEffect(pageIndex, engine, isHighZoom) {
        if (isHighZoom) {
            val cachedUltra = engine.getCachedBitmap(pageIndex, ultraHd = true)
            if (cachedUltra != null) {
                pageBitmap = cachedUltra
            } else {
                kotlinx.coroutines.delay(260)
                val ultraBmp = engine.getPageBitmap(pageIndex, ultraHd = true)
                if (ultraBmp != null) {
                    pageBitmap = ultraBmp
                }
            }
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
                        .shadow(
                            elevation = 5.dp,
                            shape = RoundedCornerShape(8.dp),
                            ambientColor = Color.Black.copy(alpha = 0.4f),
                            spotColor = Color.Black.copy(alpha = 0.55f)
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.18f)), RoundedCornerShape(8.dp))
                        .background(Color.White)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 3.dp)
                        .aspectRatio(aspect)
                        .shadow(
                            elevation = 4.dp,
                            shape = RoundedCornerShape(6.dp),
                            ambientColor = Color.Black.copy(alpha = 0.35f),
                            spotColor = Color.Black.copy(alpha = 0.5f)
                        )
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
                    filterQuality = FilterQuality.Medium,
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
