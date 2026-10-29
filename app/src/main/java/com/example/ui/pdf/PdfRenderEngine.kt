package com.example.ui.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * Modes of document interaction in the PDF viewer.
 */
enum class PdfViewMode {
    PAGE_SWIPE,      // Horizontal swipe pager (eBook style)
    CONTINUOUS_FLOW  // Vertical continuous stream (Google Drive style)
}

/**
 * Eye-care visual themes for page display.
 */
enum class PaperTheme {
    CLEAN_WHITE,     // Crisp daylight white paper
    WARM_SEPIA,      // Soft eye-care amber tint
    DARK_MODE        // High-contrast inverted dark reading
}

/**
 * Google Drive-grade Progressive Chunk PDF Rendering Engine.
 *
 * Designed for 120fps butter-smooth scrolling even on low-end devices:
 * 1. Instant Low-Res Preview (RGB_565) while actively scrolling so scroll frames never drop.
 * 2. Progressive Chunk-by-Chunk (4 horizontal bands) High-DPI sharpening as soon as scrolling settles.
 * 3. Dynamic Ultra-HD Zoom upgrade when zoomed in.
 * 4. Crash-safe lifecycle with atomic close guard and automatic OOM recovery.
 */
class PdfRenderEngine(
    val file: File,
    private val screenDensity: Float
) {
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    val pageCount: Int

    @Volatile
    private var isClosed = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val renderMutex = Mutex()

    // Cache keys:
    // - pageIndex (0..9,999): Fast scroll preview bitmap (RGB_565)
    // - pageIndex + 10,000: Full High-DPI crisp page bitmap
    // - pageIndex + 100,000: Ultra-HD zoomed page bitmap
    private val maxCacheKb = ((Runtime.getRuntime().maxMemory() / 1024) / 5).toInt().coerceIn(24 * 1024, 96 * 1024)
    private val cache = object : LruCache<Int, Bitmap>(maxCacheKb) {
        override fun sizeOf(key: Int, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }

    init {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        pfd = descriptor
        val rend = PdfRenderer(descriptor)
        renderer = rend
        pageCount = rend.pageCount
    }

    private fun previewKey(pageIndex: Int) = pageIndex
    private fun hdKey(pageIndex: Int) = pageIndex + 10_000
    private fun ultraHdKey(pageIndex: Int) = pageIndex + 100_000

    /**
     * Synchronous fast-path lookup from memory cache.
     * Returns the highest clarity bitmap currently cached for [pageIndex], or the preview bitmap if only preview is ready.
     */
    fun getCachedBitmap(pageIndex: Int, ultraHd: Boolean = false): Bitmap? {
        if (isClosed || pageIndex < 0 || pageIndex >= pageCount) return null
        if (ultraHd) {
            cache.get(ultraHdKey(pageIndex))?.let { return it }
        }
        cache.get(hdKey(pageIndex))?.let { return it }
        return cache.get(previewKey(pageIndex))
    }

    /**
     * Returns the lightweight soft preview bitmap if cached (used during active scroll for 120fps Google Drive feel).
     */
    fun getCachedPreviewOnly(pageIndex: Int): Bitmap? {
        if (isClosed || pageIndex < 0 || pageIndex >= pageCount) return null
        return cache.get(previewKey(pageIndex))
    }

    /**
     * Returns true if the sharp High-DPI (or Ultra-HD when [ultraHd] is true) bitmap is already cached.
     */
    fun isSharpBitmapCached(pageIndex: Int, ultraHd: Boolean = false): Boolean {
        if (isClosed || pageIndex < 0 || pageIndex >= pageCount) return false
        return if (ultraHd) {
            cache.get(ultraHdKey(pageIndex)) != null
        } else {
            cache.get(hdKey(pageIndex)) != null
        }
    }

    /**
     * Renders or retrieves a lightweight low-res preview bitmap (RGB_565) in <8ms for zero-lag fast scrolling.
     */
    suspend fun getPreviewBitmap(pageIndex: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (isClosed || pageIndex < 0 || pageIndex >= pageCount) return@withContext null
        cache.get(previewKey(pageIndex))?.let { return@withContext it }

        renderMutex.withLock {
            if (isClosed) return@withContext null
            cache.get(previewKey(pageIndex))?.let { return@withContext it }
            val rend = renderer ?: return@withContext null

            try {
                val page = rend.openPage(pageIndex)
                val baseWidth = page.width.coerceAtLeast(1)
                val baseHeight = page.height.coerceAtLeast(1)
                val aspectRatio = baseHeight.toFloat() / baseWidth.toFloat()

                // Soft lightweight preview (fast rasterization during scroll)
                val previewWidth = 540
                val previewHeight = (previewWidth * aspectRatio).roundToInt().coerceIn(80, 1100)

                val bmp = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.RGB_565)
                val canvas = Canvas(bmp)
                canvas.drawColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                cache.put(previewKey(pageIndex), bmp)
                bmp
            } catch (oom: OutOfMemoryError) {
                cache.evictAll()
                null
            } catch (_: Throwable) {
                null
            }
        }
    }

    /**
     * Google Drive-style progressive chunk-by-chunk sharpener.
     * Starts from the soft preview and sharpens the page in 4 horizontal chunks (top to bottom)
     * after the user stops scrolling, emitting each intermediate sharpened state via [onChunkReady].
     */
    suspend fun renderPageInChunks(
        pageIndex: Int,
        ultraHd: Boolean = false,
        totalChunks: Int = 4,
        forceChunkReveal: Boolean = false,
        onChunkReady: (Bitmap, Int) -> Unit
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (isClosed || pageIndex < 0 || pageIndex >= pageCount) return@withContext null

        val targetKey = if (ultraHd) ultraHdKey(pageIndex) else hdKey(pageIndex)
        if (!forceChunkReveal) {
            cache.get(targetKey)?.let { cached ->
                onChunkReady(cached, totalChunks)
                return@withContext cached
            }
        }

        // Ensure we have a base preview first so we can composite sharp chunks onto it seamlessly
        val basePreview = getPreviewBitmap(pageIndex)

        // Render the full sharp page bitmap inside the mutex, then reveal or stream chunks
        val sharpFullBitmap: Bitmap? = cache.get(targetKey) ?: renderMutex.withLock {
            if (isClosed) return@withLock null
            cache.get(targetKey)?.let { return@withLock it }
            val rend = renderer ?: return@withLock null

            try {
                val page = rend.openPage(pageIndex)
                val baseWidth = page.width.coerceAtLeast(1)
                val baseHeight = page.height.coerceAtLeast(1)
                val aspectRatio = baseHeight.toFloat() / baseWidth.toFloat()

                val desiredWidth = if (ultraHd) {
                    maxOf((baseWidth * screenDensity * 1.65f).roundToInt(), 1800).coerceIn(1560, 2100)
                } else {
                    maxOf((baseWidth * screenDensity * 1.18f).roundToInt(), 1280).coerceIn(1120, 1480)
                }

                val maxAllowedHeight = if (ultraHd) 2900 else 2200
                val rawHeight = (desiredWidth * aspectRatio).roundToInt().coerceAtLeast(1)
                val (targetWidth, targetHeight) = if (rawHeight > maxAllowedHeight) {
                    val clampedH = maxAllowedHeight
                    val clampedW = (clampedH / aspectRatio).roundToInt().coerceAtLeast(1)
                    Pair(clampedW, clampedH)
                } else {
                    Pair(desiredWidth, rawHeight)
                }

                // RGB_565 uses 50% less memory than ARGB_8888 while keeping opaque white-paper text 100% crisp
                val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.RGB_565)
                val canvas = Canvas(bmp)
                canvas.drawColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                cache.put(targetKey, bmp)
                if (!ultraHd) {
                    triggerPrefetchPreview(pageIndex)
                }
                bmp
            } catch (oom: OutOfMemoryError) {
                cache.evictAll()
                System.gc()
                if (ultraHd) cache.get(hdKey(pageIndex)) else renderFallbackPage(pageIndex)
            } catch (_: Throwable) {
                null
            }
        }

        if (sharpFullBitmap == null) return@withContext null

        // Progressive chunk-by-chunk reveal from top to bottom (Google Drive style)
        if (basePreview != null && basePreview !== sharpFullBitmap && totalChunks > 1) {
            try {
                val w = sharpFullBitmap.width
                val h = sharpFullBitmap.height
                val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                val fullDst = Rect(0, 0, w, h)

                for (chunk in 1 until totalChunks) {
                    currentCoroutineContext().ensureActive()
                    if (isClosed) return@withContext sharpFullBitmap

                    val chunkBottom = (h * chunk) / totalChunks
                    val stepBmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
                    val stepCanvas = Canvas(stepBmp)
                    // Draw soft preview as background, then overlay sharpened top chunks [0..chunkBottom]
                    stepCanvas.drawBitmap(basePreview, null, fullDst, paint)
                    val sharpRect = Rect(0, 0, w, chunkBottom)
                    stepCanvas.drawBitmap(sharpFullBitmap, sharpRect, sharpRect, null)

                    onChunkReady(stepBmp, chunk)
                    delay(28)
                }
            } catch (_: OutOfMemoryError) {
                // Skip intermediate chunk allocation if memory is tight and jump straight to sharp bitmap
            }
        }

        currentCoroutineContext().ensureActive()
        onChunkReady(sharpFullBitmap, totalChunks)
        sharpFullBitmap
    }

    /**
     * Direct full-page render helper (maintained for backward compatibility and tests).
     */
    suspend fun getPageBitmap(pageIndex: Int, ultraHd: Boolean = false): Bitmap? = withContext(Dispatchers.IO) {
        if (isClosed || pageIndex < 0 || pageIndex >= pageCount) return@withContext null
        val targetKey = if (ultraHd) ultraHdKey(pageIndex) else hdKey(pageIndex)
        cache.get(targetKey)?.let { return@withContext it }

        var result: Bitmap? = null
        renderPageInChunks(pageIndex = pageIndex, ultraHd = ultraHd, totalChunks = 1) { bmp, _ ->
            result = bmp
        }
        result
    }

    private fun renderFallbackPage(pageIndex: Int): Bitmap? {
        if (isClosed) return null
        val rend = renderer ?: return null
        return try {
            val page = rend.openPage(pageIndex)
            val baseWidth = page.width.coerceAtLeast(1)
            val baseHeight = page.height.coerceAtLeast(1)
            val aspectRatio = baseHeight.toFloat() / baseWidth.toFloat()
            val targetWidth = 960
            val targetHeight = (targetWidth * aspectRatio).roundToInt().coerceIn(100, 1800)
            val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.RGB_565)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            cache.put(hdKey(pageIndex), bmp)
            bmp
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Pre-renders lightweight previews of adjacent pages when idle so fast scrolling never hits a blank placeholder.
     */
    private fun triggerPrefetchPreview(currentIndex: Int) {
        if (isClosed) return
        scope.launch {
            val prefetchIndices = listOf(currentIndex + 1, currentIndex - 1, currentIndex + 2)
            for (idx in prefetchIndices) {
                if (isClosed) break
                if (idx in 0 until pageCount && cache.get(previewKey(idx)) == null && cache.get(hdKey(idx)) == null) {
                    if (renderMutex.tryLock()) {
                        try {
                            if (!isClosed && cache.get(previewKey(idx)) == null && renderer != null) {
                                val rend = renderer ?: break
                                val page = rend.openPage(idx)
                                val baseWidth = page.width.coerceAtLeast(1)
                                val baseHeight = page.height.coerceAtLeast(1)
                                val aspectRatio = baseHeight.toFloat() / baseWidth.toFloat()
                                val previewWidth = 620
                                val previewHeight = (previewWidth * aspectRatio).roundToInt().coerceIn(80, 1200)

                                val bmp = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.RGB_565)
                                val canvas = Canvas(bmp)
                                canvas.drawColor(Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                page.close()

                                cache.put(previewKey(idx), bmp)
                            }
                        } catch (_: Throwable) {
                            // Non-critical prefetch error
                        } finally {
                            renderMutex.unlock()
                        }
                    }
                }
            }
        }
    }

    fun close() {
        if (isClosed) return
        isClosed = true
        try {
            scope.cancel()
        } catch (_: Throwable) {
        }
        // Safely close PdfRenderer and ParcelFileDescriptor after any active render lock finishes
        CoroutineScope(Dispatchers.IO).launch {
            renderMutex.withLock {
                try {
                    renderer?.close()
                } catch (_: Throwable) {
                }
                try {
                    pfd?.close()
                } catch (_: Throwable) {
                }
                renderer = null
                pfd = null
                cache.evictAll()
            }
        }
    }
}
