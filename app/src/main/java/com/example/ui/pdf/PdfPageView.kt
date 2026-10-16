package com.example.ui.pdf

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Individual PDF Page renderer component with compressed graphics pipeline,
 * instantaneous cache hit rendering, and elegant fallback loading states.
 */
@Composable
fun PdfSinglePageView(
    pageIndex: Int,
    engine: PdfRenderEngine,
    colorFilter: ColorFilter? = null,
    isSwipeMode: Boolean = true,
    modifier: Modifier = Modifier
) {
    var pageBitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pageIndex, engine) {
        val bmp = engine.getPageBitmap(pageIndex)
        pageBitmap = bmp
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = pageBitmap,
            animationSpec = tween(150),
            label = "pageCrossfade"
        ) { bmp ->
            if (bmp != null) {
                Box(
                    modifier = if (isSwipeMode) {
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp, vertical = 8.dp)
                            .shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(10.dp),
                                ambientColor = Color.Black.copy(alpha = 0.45f),
                                spotColor = Color.Black.copy(alpha = 0.65f)
                            )
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(10.dp),
                                ambientColor = Color.Black.copy(alpha = 0.45f),
                                spotColor = Color.Black.copy(alpha = 0.65f)
                            )
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Page ${pageIndex + 1}",
                        contentScale = if (isSwipeMode) ContentScale.Fit else ContentScale.FillWidth,
                        colorFilter = colorFilter,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(480.dp)
                        .clip(RoundedCornerShape(10.dp))
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
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Loading Page ${pageIndex + 1}...",
                            color = Color(0xFF90CAF9),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
