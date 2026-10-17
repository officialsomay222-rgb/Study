package com.example.ui.offline

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.pdf.LiquidGlassButton
import java.io.File

@Composable
fun OfflineHubScreen(
    onRetry: () -> Unit,
    onTryLoadCache: () -> Unit,
    onOpenPdf: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var cachedPdfs by remember { mutableStateOf<List<File>>(emptyList()) }

    // Load available cached study PDFs and documents
    LaunchedEffect(Unit) {
        val cacheFolder = File(context.cacheDir, "study_pdfs")
        if (cacheFolder.exists()) {
            val files = cacheFolder.listFiles { f -> 
                f.extension.equals("pdf", ignoreCase = true) ||
                f.extension.equals("docx", ignoreCase = true) ||
                f.extension.equals("doc", ignoreCase = true)
            }
            cachedPdfs = files?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090D1A))
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                // Animated Pulsing Beacon
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(110.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .scale(pulseScale)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        Color(0xFF00E5FF).copy(alpha = 0.25f),
                                        Color(0xFF2979FF).copy(alpha = 0.1f),
                                        Color.Transparent
                                    )
                                ),
                                CircleShape
                            )
                    )
                    Surface(
                        shape = CircleShape,
                        color = Color.Transparent,
                        modifier = Modifier
                            .size(68.dp)
                            .shadow(12.dp, CircleShape, spotColor = Color(0xFF00E5FF))
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.2f),
                                            Color(0xFF162038).copy(alpha = 0.85f),
                                            Color(0xFF0D1426)
                                        )
                                    )
                                )
                                .border(
                                    BorderStroke(
                                        1.5.dp,
                                        Brush.linearGradient(
                                            listOf(Color.White.copy(alpha = 0.7f), Color(0xFF00E5FF).copy(alpha = 0.5f))
                                        )
                                    ),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SignalWifiOff,
                                contentDescription = "Offline Mode",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "StudyVerse Offline Hub",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "No active internet connection. You can still read cached study pages or open saved PDFs offline.",
                    color = Color(0xFFB0BEC5),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons in Liquid Glass UI
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LiquidGlassButton(
                        onClick = onTryLoadCache,
                        icon = Icons.Default.Cached,
                        text = "View Cache",
                        accentColor = Color(0xFF00E5FF),
                        isPill = true,
                        modifier = Modifier.weight(1f)
                    )

                    LiquidGlassButton(
                        onClick = onRetry,
                        icon = Icons.Default.Refresh,
                        text = "Reconnect",
                        accentColor = Color(0xFF2979FF),
                        isPill = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Saved Study Materials Section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Saved Offline Documents",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.08f)
                    ) {
                        Text(
                            text = "${cachedPdfs.size} Available",
                            color = Color(0xFF00E5FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (cachedPdfs.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.06f),
                                            Color(0xFF131A2B).copy(alpha = 0.5f)
                                        )
                                    )
                                )
                                .border(
                                    BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = Color(0xFF546E7A),
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No PDFs cached yet",
                                    color = Color(0xFF78909C),
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "PDFs you view online are saved here automatically for offline reading.",
                                    color = Color(0xFF546E7A),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            } else {
                items(cachedPdfs) { file ->
                    val kbSize = file.length() / 1024
                    val sizeLabel = if (kbSize > 1024) "${kbSize / 1024} MB" else "$kbSize KB"

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onOpenPdf(file.absolutePath, file.name)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.1f),
                                            Color(0xFF141C30).copy(alpha = 0.8f)
                                        )
                                    )
                                )
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        Brush.linearGradient(
                                            listOf(
                                                Color.White.copy(alpha = 0.35f),
                                                Color(0xFF00E5FF).copy(alpha = 0.2f),
                                                Color.Transparent
                                            )
                                        )
                                    ),
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF00E5FF).copy(alpha = 0.15f),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = "PDF Icon",
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "$sizeLabel · Available Offline",
                                    color = Color(0xFF90CAF9),
                                    fontSize = 12.sp
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Open Document",
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
