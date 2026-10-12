package com.example.ui.pdf

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.min

@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    text: String? = null,
    contentDescription: String? = text ?: "Action Button",
    enabled: Boolean = true,
    accentColor: Color = Color(0xFF00E5FF),
    isPill: Boolean = false
) {
    val alphaMultiplier = if (enabled) 1f else 0.4f
    val shape = if (isPill) RoundedCornerShape(24.dp) else CircleShape

    Surface(
        onClick = { if (enabled) onClick() },
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = shape,
                ambientColor = accentColor.copy(alpha = 0.25f * alphaMultiplier),
                spotColor = accentColor.copy(alpha = 0.35f * alphaMultiplier)
            ),
        shape = shape,
        color = Color.Transparent,
        enabled = enabled
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.22f * alphaMultiplier),
                            Color(0xFF1E2640).copy(alpha = 0.65f * alphaMultiplier),
                            Color(0xFF0D1322).copy(alpha = 0.88f * alphaMultiplier)
                        )
                    )
                )
                .border(
                    BorderStroke(
                        1.2.dp,
                        Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.7f * alphaMultiplier),
                                accentColor.copy(alpha = 0.45f * alphaMultiplier),
                                Color.White.copy(alpha = 0.15f * alphaMultiplier)
                            )
                        )
                    ),
                    shape = shape
                )
                .padding(
                    horizontal = if (text != null && icon != null) 16.dp else if (text != null) 14.dp else 11.dp,
                    vertical = 11.dp
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = contentDescription,
                        tint = Color.White.copy(alpha = alphaMultiplier),
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (text != null) {
                    if (icon != null) Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = text,
                        color = Color.White.copy(alpha = alphaMultiplier),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun PdfViewerScreen(
    pdfSource: String, // Remote URL or local file path
    title: String? = null,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var localPdfFile by remember { mutableStateOf<File?>(null) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var isDownloading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    var currentPageIndex by remember { mutableStateOf(0) }
    var renderedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRenderingPage by remember { mutableStateOf(false) }

    // Zoom & Pan state
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    val resolvedTitle = remember(pdfSource, title) {
        if (!title.isNullOrBlank()) title
        else {
            val fileName = pdfSource.substringAfterLast("/").substringBefore("?")
            if (fileName.endsWith(".pdf", ignoreCase = true)) fileName else "$fileName.pdf"
        }
    }

    BackHandler {
        onClose()
    }

    // Prepare or download PDF
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
                        // Download with progress
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
                    // Local file path
                    val f = File(pdfSource)
                    if (f.exists()) {
                        localPdfFile = f
                        isDownloading = false
                    } else {
                        errorMessage = "PDF file not found on device."
                        isDownloading = false
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Failed to load PDF: ${e.localizedMessage ?: "Unknown error"}"
                isDownloading = false
            }
        }
    }

    // Initialize PdfRenderer when file is available
    LaunchedEffect(localPdfFile) {
        val file = localPdfFile ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                pfd?.close()
                pdfRenderer?.close()

                val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(descriptor)
                pfd = descriptor
                pdfRenderer = renderer
                pageCount = renderer.pageCount
                currentPageIndex = 0
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Invalid or corrupted PDF file."
            }
        }
    }

    // Render current page
    LaunchedEffect(currentPageIndex, pageCount, pdfRenderer) {
        val renderer = pdfRenderer ?: return@LaunchedEffect
        if (pageCount <= 0 || currentPageIndex < 0 || currentPageIndex >= pageCount) return@LaunchedEffect

        isRenderingPage = true
        withContext(Dispatchers.IO) {
            try {
                val page = renderer.openPage(currentPageIndex)
                val displayMetrics = context.resources.displayMetrics
                val targetDensity = (displayMetrics.densityDpi / 160f).coerceIn(1.5f, 2.5f)
                val width = (page.width * targetDensity).toInt()
                val height = (page.height * targetDensity).toInt()

                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                canvas.drawColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                renderedBitmap = bmp
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isRenderingPage = false
            }
        }
    }

    // Cleanup when leaving
    DisposableEffect(Unit) {
        onDispose {
            try {
                pdfRenderer?.close()
                pfd?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0D18))
    ) {
        // PDF Canvas Viewer Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.8f, 5f)
                        if (scale > 1f) {
                            val maxOffset = 500f * scale
                            offsetX = (offsetX + pan.x * scale).coerceIn(-maxOffset, maxOffset)
                            offsetY = (offsetY + pan.y * scale).coerceIn(-maxOffset, maxOffset)
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.2f) {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                scale = 2.2f
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            val bmp = renderedBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "PDF Page ${currentPageIndex + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 70.dp, bottom = 90.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        }
                )
            } else if (!isDownloading && errorMessage == null) {
                CircularProgressIndicator(
                    color = Color(0xFF00E5FF),
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        // Loading or Downloading Progress Overlay
        if (isDownloading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC090C19)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(32.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF1B2236).copy(alpha = 0.85f),
                                    Color(0xFF0E1322).copy(alpha = 0.95f)
                                )
                            ),
                            RoundedCornerShape(24.dp)
                        )
                        .border(
                            BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            RoundedCornerShape(24.dp)
                        )
                        .padding(28.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { if (downloadProgress > 0) downloadProgress else 0.5f },
                        color = Color(0xFF00E5FF),
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Loading Study PDF...",
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

        // Error Banner
        if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xDD090C19)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Cannot Open Document",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = Color(0xFFB0BEC5),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    LiquidGlassButton(
                        onClick = onClose,
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        text = "Go Back",
                        accentColor = Color(0xFF2979FF)
                    )
                }
            }
        }

        // FLOATING TOP BAR - LIQUID GLASS UI
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Close / Back Liquid Glass Button
            LiquidGlassButton(
                onClick = onClose,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to StudyVerse",
                accentColor = Color(0xFF00E5FF)
            )

            // Title Pill in Liquid Glass
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .shadow(6.dp, RoundedCornerShape(20.dp), spotColor = Color(0x3300E5FF))
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.18f),
                                    Color(0xFF131A2D).copy(alpha = 0.85f)
                                )
                            )
                        )
                        .border(
                            BorderStroke(
                                1.dp,
                                Brush.linearGradient(
                                    listOf(Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0.1f))
                                )
                            ),
                            RoundedCornerShape(20.dp)
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
                // Share Liquid Glass Button
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
                            Toast.makeText(context, "Document not ready to share", Toast.LENGTH_SHORT).show()
                        }
                    },
                    icon = Icons.Default.Share,
                    contentDescription = "Share PDF",
                    accentColor = Color(0xFF7C4DFF)
                )

                // Save to Downloads Liquid Glass Button
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
                                Toast.makeText(
                                    context,
                                    "Saved to Downloads: ${target.name}",
                                    Toast.LENGTH_LONG
                                ).show()
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
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
                                Toast.makeText(context, "Downloading $resolvedTitle to Downloads...", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },
                    icon = Icons.Default.FileDownload,
                    contentDescription = "Save to Downloads",
                    accentColor = Color(0xFF00E676)
                )
            }
        }

        // FLOATING BOTTOM TOOLBAR - LIQUID GLASS UI
        if (pageCount > 0) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .align(Alignment.BottomCenter)
                    .shadow(16.dp, RoundedCornerShape(28.dp), spotColor = Color(0x6600E5FF))
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.25f),
                                    Color(0xFF182038).copy(alpha = 0.75f),
                                    Color(0xFF0B1020).copy(alpha = 0.95f)
                                )
                            )
                        )
                        .border(
                            BorderStroke(
                                1.2.dp,
                                Brush.linearGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.75f),
                                        Color(0xFF00E5FF).copy(alpha = 0.45f),
                                        Color.White.copy(alpha = 0.2f)
                                    )
                                )
                            ),
                            RoundedCornerShape(28.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Previous Page Button
                        LiquidGlassButton(
                            onClick = {
                                if (currentPageIndex > 0) {
                                    currentPageIndex--
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            },
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous Page",
                            enabled = currentPageIndex > 0,
                            accentColor = Color(0xFF2979FF)
                        )

                        // Page Indicator Pill
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = "${currentPageIndex + 1} / $pageCount",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        // Next Page Button
                        LiquidGlassButton(
                            onClick = {
                                if (currentPageIndex < pageCount - 1) {
                                    currentPageIndex++
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            },
                            icon = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next Page",
                            enabled = currentPageIndex < pageCount - 1,
                            accentColor = Color(0xFF2979FF)
                        )

                        // Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(24.dp)
                                .background(Color.White.copy(alpha = 0.25f))
                        )

                        // Zoom Out (-)
                        LiquidGlassButton(
                            onClick = {
                                scale = max(1f, scale - 0.4f)
                                if (scale == 1f) {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            },
                            icon = Icons.Default.ZoomOut,
                            contentDescription = "Zoom Out",
                            enabled = scale > 1f,
                            accentColor = Color(0xFF00E5FF)
                        )

                        // Reset Zoom / Fit
                        LiquidGlassButton(
                            onClick = {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            },
                            text = if (scale > 1.05f) "${(scale * 100).toInt()}%" else "1x",
                            contentDescription = "Reset Zoom",
                            accentColor = Color(0xFF00E5FF),
                            isPill = true
                        )

                        // Zoom In (+)
                        LiquidGlassButton(
                            onClick = {
                                scale = min(4.5f, scale + 0.4f)
                            },
                            icon = Icons.Default.ZoomIn,
                            contentDescription = "Zoom In",
                            enabled = scale < 4.5f,
                            accentColor = Color(0xFF00E5FF)
                        )
                    }
                }
            }
        }
    }
}
