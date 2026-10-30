package com.example.ui.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt
import kotlinx.coroutines.ensureActive

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
 * Ultra-Smooth On-Demand ("DOM-style") PDF Rendering Engine.
 *
 * - Pre-computes page aspect ratios so LazyColumn items have exact final heights from frame 0
 *   with zero layout jumps during fast scrolling.
 * - Checks coroutineContext.ensureActive() before and inside renderMutex so pages scrolled past
 *   during a fast fling are skipped in 0ms without blocking visible pages.
 * - Calls Bitmap.prepareToDraw() and caches ImageBitmap on Dispatchers.IO so GPU texture upload
 *   never blocks the main UI thread.
 * - Suspends heavy HD/Ultra-HD work while the user is actively scrolling or zooming so CPU/GPU power
 *   is used strictly on demand when settled.
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

    @Volatile
    var isUserActivelyScrolling: Boolean = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val renderMutex = Mutex()

    // Exact width/height aspect ratios per page (prevents any LazyColumn height jump)
    private val pageAspectRatios: FloatArray
    private var defaultAspectRatio: Float = 0.7071f // Standard A4 portrait width/height

    // Cache keys:
    // - pageIndex (0..9,999): Fast scroll preview bitmap
    // - pageIndex + 10,000: Full High-DPI crisp page bitmap
    // - pageIndex + 100,000: Ultra-HD zoomed page bitmap
    private val maxCacheKb = ((Runtime.getRuntime().maxMemory() / 1024) / 4).toInt().coerceIn(32 * 1024, 128 * 1024)
    private val cache = object : LruCache<Int, Bitmap>(maxCacheKb) {
        override fun sizeOf(key: Int, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
        override fun entryRemoved(evicted: Boolean, key: Int, oldValue: Bitmap?, newValue: Bitmap?) {
            if (evicted && newValue == null) {
                imageBitmapCache.remove(key)
            }
        }
    }
    private val imageBitmapCache = LruCache<Int, ImageBitmap>(140)

    init {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val rend = PdfRenderer(descriptor)
            pfd = descriptor
            renderer = rend
            pageCount = rend.pageCount
            pageAspectRatios = FloatArray(pageCount.coerceAtLeast(1))

            // Read Page 0 dimensions immediately so every page has an accurate initial aspect ratio
            if (pageCount > 0) {
                try {
                    val firstPage = rend.openPage(0)
                    try {
                        val w = firstPage.width.coerceAtLeast(1).toFloat()
                        val h = firstPage.height.coerceAtLeast(1).toFloat()
                        defaultAspectRatio = (w / h).coerceIn(0.2f, 5.0f)
                    } finally {
                        try {
                            firstPage.close()
                        } catch (_: Throwable) {
                        }
                    }
                } catch (_: Throwable) {
                }
                for (i in 0 until pageCount) {
                    pageAspectRatios[i] = defaultAspectRatio
                }
                // Pre-warm initial page previews in background
                triggerPrefetchPreview(0)
            }
        } catch (e: Throwable) {
            try {
                descriptor.close()
            } catch (_: Throwable) {
            }
            throw e
        }
    }

    private fun previewKey(pageIndex: Int) = pageIndex
    private fun hdKey(pageIndex: Int) = pageIndex + 10_000
    private fun ultraHdKey(pageIndex: Int) = pageIndex + 100_000

    /**
     * Returns the exact width/height aspect ratio for [pageIndex] in O(1) time.
     */
    fun getPageAspectRatio(pageIndex: Int): Float {
        if (pageIndex !in 0 until pageCount) return defaultAspectRatio
        val ratio = pageAspectRatios[pageIndex]
        return if (ratio > 0.05f) ratio else defaultAspectRatio
    }

    private fun storeBitmapAndPrepareGpu(key: Int, pageIndex: Int, bmp: Bitmap): ImageBitmap {
        if (pageIndex in 0 until pageCount && bmp.height > 0) {
            pageAspectRatios[pageIndex] = (bmp.width.toFloat() / bmp.height.toFloat()).coerceIn(0.2f, 5.0f)
        }
        try {
            bmp.prepareToDraw()
        } catch (_: Throwable) {
        }
        val img = bmp.asImageBitmap()
        cache.put(key, bmp)
        imageBitmapCache.put(key, img)
        return img
    }

    private fun getOrWrapImageBitmap(key: Int): ImageBitmap? {
        val bmp = cache.get(key) ?: return null
        if (bmp.isRecycled) {
            cache.remove(key)
            imageBitmapCache.remove(key)
            return null
        }
        imageBitmapCache.get(key)?.let { return it }
        val img = bmp.asImageBitmap()
        imageBitmapCache.put(key, img)
        return img
    }

    /**
     * Returns the lightweight soft preview ImageBitmap if cached.
     */
    fun getCachedPreviewImage(pageIndex: Int): ImageBitmap? {
        if (isClosed || pageIndex < 0 || pageIndex >= pageCount) return null
        return getOrWrapImageBitmap(previewKey(pageIndex))
    }

    /**
     * Returns the sharp High-DPI (or Ultra-HD) ImageBitmap if cached.
     */
    fun getCachedSharpImage(pageIndex: Int, ultraHd: Boolean = false): ImageBitmap? {
        if (isClosed || pageIndex < 0 || pageIndex >= pageCount) return null
        if (ultraHd) {
            getOrWrapImageBitmap(ultraHdKey(pageIndex))?.let { return it }
        }
        return getOrWrapImageBitmap(hdKey(pageIndex))
    }

    /**
     * Returns the lightweight soft preview bitmap if cached.
     */
    fun getCachedPreviewOnly(pageIndex: Int): Bitmap? {
        if (isClosed || pageIndex < 0 || pageIndex >= pageCount) return null
        val bmp = cache.get(previewKey(pageIndex))
        return if (bmp != null && !bmp.isRecycled) bmp else null
    }

    /**
     * Returns the sharp High-DPI (or Ultra-HD) bitmap if cached.
     */
    fun getCachedSharpBitmap(pageIndex: Int, ultraHd: Boolean = false): Bitmap? {
        if (isClosed || pageIndex < 0 || pageIndex >= pageCount) return null
        if (ultraHd) {
            val ultra = cache.get(ultraHdKey(pageIndex))
            if (ultra != null && !ultra.isRecycled) return ultra
        }
        val hd = cache.get(hdKey(pageIndex))
        return if (hd != null && !hd.isRecycled) hd else null
    }

    /**
     * Renders or retrieves a lightweight low-res preview ImageBitmap on demand for 60/120fps scrolling.
     */
    suspend fun getPreviewImage(pageIndex: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        if (isClosed || pageIndex < 0 || pageIndex >= pageCount) return@withContext null
        getCachedPreviewImage(pageIndex)?.let { return@withContext it }
        coroutineContext.ensureActive()

        renderMutex.withLock {
            coroutineContext.ensureActive()
            if (isClosed) return@withContext null
            getCachedPreviewImage(pageIndex)?.let { return@withContext it }
            val rend = renderer ?: return@withContext null

            try {
                val page = rend.openPage(pageIndex)
                try {
                    val baseWidth = page.width.coerceAtLeast(1)
                    val baseHeight = page.height.coerceAtLeast(1)
                    pageAspectRatios[pageIndex] = (baseWidth.toFloat() / baseHeight.toFloat()).coerceIn(0.2f, 5.0f)
                    val heightOverWidth = baseHeight.toFloat() / baseWidth.toFloat()

                    val previewWidth = 360
                    val previewHeight = (previewWidth * heightOverWidth).roundToInt().coerceIn(80, 820)

                    // PdfRenderer.Page.render strictly requires Bitmap.Config.ARGB_8888
                    val bmp = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    canvas.drawColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    storeBitmapAndPrepareGpu(previewKey(pageIndex), pageIndex, bmp)
                } finally {
                    try {
                        page.close()
                    } catch (_: Throwable) {
                    }
                }
            } catch (oom: OutOfMemoryError) {
                cache.evictAll()
                imageBitmapCache.evictAll()
                null
            } catch (e: Throwable) {
                null
            }
        }
    }

    /**
     * Renders or retrieves the full High-DPI (or Ultra-HD when zoomed) sharp ImageBitmap on demand.
     */
    suspend fun getPageImage(pageIndex: Int, ultraHd: Boolean = false): ImageBitmap? = withContext(Dispatchers.IO) {
        if (isClosed || pageIndex < 0 || pageIndex >= pageCount) return@withContext null
        val targetKey = if (ultraHd) ultraHdKey(pageIndex) else hdKey(pageIndex)
        getOrWrapImageBitmap(targetKey)?.let { return@withContext it }

        // Do not waste CPU/GPU power on HD rendering while user is actively scrolling or zooming
        if (isUserActivelyScrolling) return@withContext null
        coroutineContext.ensureActive()

        renderMutex.withLock {
            coroutineContext.ensureActive()
            if (isClosed || isUserActivelyScrolling) return@withContext null
            getOrWrapImageBitmap(targetKey)?.let { return@withContext it }
            val rend = renderer ?: return@withContext null

            try {
                val page = rend.openPage(pageIndex)
                try {
                    val baseWidth = page.width.coerceAtLeast(1)
                    val baseHeight = page.height.coerceAtLeast(1)
                    pageAspectRatios[pageIndex] = (baseWidth.toFloat() / baseHeight.toFloat()).coerceIn(0.2f, 5.0f)
                    val heightOverWidth = baseHeight.toFloat() / baseWidth.toFloat()

                    val desiredWidth = if (ultraHd) {
                        maxOf((baseWidth * screenDensity * 1.35f).roundToInt(), 1440).coerceIn(1320, 1680)
                    } else {
                        maxOf((baseWidth * screenDensity * 1.02f).roundToInt(), 1020).coerceIn(960, 1220)
                    }

                    val maxAllowedHeight = if (ultraHd) 2240 else 1760
                    val rawHeight = (desiredWidth * heightOverWidth).roundToInt().coerceAtLeast(1)
                    val (targetWidth, targetHeight) = if (rawHeight > maxAllowedHeight) {
                        val clampedH = maxAllowedHeight
                        val clampedW = (clampedH / heightOverWidth).roundToInt().coerceAtLeast(1)
                        Pair(clampedW, clampedH)
                    } else {
                        Pair(desiredWidth, rawHeight)
                    }

                    val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    canvas.drawColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    val img = storeBitmapAndPrepareGpu(targetKey, pageIndex, bmp)
                    if (!ultraHd && !isUserActivelyScrolling) {
                        triggerPrefetchPreview(pageIndex)
                    }
                    img
                } finally {
                    try {
                        page.close()
                    } catch (_: Throwable) {
                    }
                }
            } catch (oom: OutOfMemoryError) {
                cache.evictAll()
                imageBitmapCache.evictAll()
                System.gc()
                if (ultraHd) getCachedSharpImage(pageIndex, ultraHd = false) else renderFallbackPage(pageIndex)
            } catch (e: Throwable) {
                null
            }
        }
    }

    private fun renderFallbackPage(pageIndex: Int): ImageBitmap? {
        if (isClosed) return null
        val rend = renderer ?: return null
        return try {
            val page = rend.openPage(pageIndex)
            try {
                val baseWidth = page.width.coerceAtLeast(1)
                val baseHeight = page.height.coerceAtLeast(1)
                val heightOverWidth = baseHeight.toFloat() / baseWidth.toFloat()
                val targetWidth = 760
                val targetHeight = (targetWidth * heightOverWidth).roundToInt().coerceIn(100, 1400)
                val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.drawColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                storeBitmapAndPrepareGpu(hdKey(pageIndex), pageIndex, bmp)
            } finally {
                try {
                    page.close()
                } catch (_: Throwable) {
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Pre-renders lightweight previews of adjacent pages (+/- 3) when idle so scrolling never hits a blank frame.
     */
    private fun triggerPrefetchPreview(currentIndex: Int) {
        if (isClosed || isUserActivelyScrolling) return
        scope.launch {
            val prefetchIndices = intArrayOf(
                currentIndex,
                currentIndex + 1,
                currentIndex + 2,
                currentIndex - 1,
                currentIndex + 3
            )
            for (idx in prefetchIndices) {
                if (isClosed || isUserActivelyScrolling) break
                if (idx in 0 until pageCount && getCachedPreviewOnly(idx) == null) {
                    if (renderMutex.tryLock()) {
                        try {
                            if (!isClosed && !isUserActivelyScrolling && getCachedPreviewOnly(idx) == null && renderer != null) {
                                val rend = renderer ?: break
                                val page = rend.openPage(idx)
                                try {
                                    val baseWidth = page.width.coerceAtLeast(1)
                                    val baseHeight = page.height.coerceAtLeast(1)
                                    pageAspectRatios[idx] = (baseWidth.toFloat() / baseHeight.toFloat()).coerceIn(0.2f, 5.0f)
                                    val heightOverWidth = baseHeight.toFloat() / baseWidth.toFloat()
                                    val previewWidth = 360
                                    val previewHeight = (previewWidth * heightOverWidth).roundToInt().coerceIn(80, 820)

                                    val bmp = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
                                    val canvas = Canvas(bmp)
                                    canvas.drawColor(Color.WHITE)
                                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                    storeBitmapAndPrepareGpu(previewKey(idx), idx, bmp)
                                } finally {
                                    try {
                                        page.close()
                                    } catch (_: Throwable) {
                                    }
                                }
                            }
                        } catch (_: Throwable) {
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
                imageBitmapCache.evictAll()
            }
        }
    }
}
