package com.example.ui.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
 * Google Drive-grade Ultra-Crisp PDF Rendering Engine.
 *
 * Delivers razor-sharp vector text rasterization:
 * 1. Super-Sampled High-DPI Base Rendering (1800px–2160px width) with true aspect-ratio preservation.
 * 2. Dynamic Microscopic Zoom Re-Rasterization (up to 2880px width when zoomed in) for crystal-clear typography.
 * 3. Proactive Background Prefetching of adjacent pages for zero-latency scrolling.
 * 4. Thread-Safe Mutex & Automatic OOM Fallback.
 */
class PdfRenderEngine(
    val file: File,
    private val screenDensity: Float
) {
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    val pageCount: Int

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val renderMutex = Mutex()

    // Generous memory cache for high-DPI pages (keys: pageIndex for base HD, pageIndex + 100_000 for Ultra-HD zoom)
    private val maxCacheKb = ((Runtime.getRuntime().maxMemory() / 1024) / 5).toInt().coerceIn(32 * 1024, 128 * 1024)
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

    /**
     * Retrieves or rasterizes the page bitmap at Google Drive High-DPI quality,
     * or Ultra-HD quality when [ultraHd] is requested during deep zoom.
     */
    suspend fun getPageBitmap(pageIndex: Int, ultraHd: Boolean = false): Bitmap? = withContext(Dispatchers.IO) {
        if (pageIndex < 0 || pageIndex >= pageCount) return@withContext null

        val cacheKey = if (ultraHd) pageIndex + 100_000 else pageIndex

        // Fast-path cache lookup
        cache.get(cacheKey)?.let { return@withContext it }

        renderMutex.withLock {
            cache.get(cacheKey)?.let { return@withContext it }
            val rend = renderer ?: return@withContext null

            try {
                val page = rend.openPage(pageIndex)

                val baseWidth = page.width.coerceAtLeast(1)
                val baseHeight = page.height.coerceAtLeast(1)
                val aspectRatio = baseHeight.toFloat() / baseWidth.toFloat()

                val desiredWidth = if (ultraHd) {
                    maxOf((baseWidth * screenDensity * 2.1f).roundToInt(), 2560).coerceIn(2048, 2880)
                } else {
                    maxOf((baseWidth * screenDensity * 1.45f).roundToInt(), 1800).coerceIn(1440, 2160)
                }

                // Preserve exact page aspect ratio while keeping max texture size safe for GPU (< 4096px)
                val rawHeight = (desiredWidth * aspectRatio).roundToInt().coerceAtLeast(1)
                val (targetWidth, targetHeight) = if (rawHeight > 3840) {
                    val clampedH = 3840
                    val clampedW = (clampedH / aspectRatio).roundToInt().coerceAtLeast(1)
                    Pair(clampedW, clampedH)
                } else {
                    Pair(desiredWidth, rawHeight)
                }

                val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.drawColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                cache.put(cacheKey, bmp)

                if (!ultraHd) {
                    triggerPrefetch(pageIndex)
                }

                bmp
            } catch (oom: OutOfMemoryError) {
                cache.evictAll()
                System.gc()
                // Fallback to standard HD if Ultra-HD hit memory ceiling
                if (ultraHd) {
                    cache.get(pageIndex)
                } else {
                    renderFallbackPage(pageIndex)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun renderFallbackPage(pageIndex: Int): Bitmap? {
        val rend = renderer ?: return null
        return try {
            val page = rend.openPage(pageIndex)
            val baseWidth = page.width.coerceAtLeast(1)
            val baseHeight = page.height.coerceAtLeast(1)
            val aspectRatio = baseHeight.toFloat() / baseWidth.toFloat()
            val targetWidth = 1200
            val targetHeight = (targetWidth * aspectRatio).roundToInt().coerceIn(100, 2400)
            val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            cache.put(pageIndex, bmp)
            bmp
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Asynchronously pre-renders adjacent pages when engine is idle.
     */
    private fun triggerPrefetch(currentIndex: Int) {
        scope.launch {
            val prefetchIndices = listOf(currentIndex + 1, currentIndex - 1, currentIndex + 2)
            for (idx in prefetchIndices) {
                if (idx in 0 until pageCount && cache.get(idx) == null) {
                    if (renderMutex.tryLock()) {
                        try {
                            if (cache.get(idx) == null && renderer != null) {
                                val rend = renderer ?: break
                                val page = rend.openPage(idx)
                                val baseWidth = page.width.coerceAtLeast(1)
                                val baseHeight = page.height.coerceAtLeast(1)
                                val aspectRatio = baseHeight.toFloat() / baseWidth.toFloat()
                                val desiredWidth = maxOf((baseWidth * screenDensity * 1.45f).roundToInt(), 1800).coerceIn(1440, 2160)
                                val rawHeight = (desiredWidth * aspectRatio).roundToInt().coerceAtLeast(1)
                                val (targetWidth, targetHeight) = if (rawHeight > 3840) {
                                    val clampedH = 3840
                                    val clampedW = (clampedH / aspectRatio).roundToInt().coerceAtLeast(1)
                                    Pair(clampedW, clampedH)
                                } else {
                                    Pair(desiredWidth, rawHeight)
                                }

                                val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                                val canvas = Canvas(bmp)
                                canvas.drawColor(Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                page.close()

                                cache.put(idx, bmp)
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
        try {
            scope.cancel()
            renderer?.close()
            pfd?.close()
            cache.evictAll()
            renderer = null
            pfd = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
