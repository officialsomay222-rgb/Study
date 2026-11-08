package com.example.util

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.Immutable

/**
 * Hardware-aware adaptive performance profiler.
 * Automatically tunes PDF rendering resolutions, vector zoom-tile dimensions, settle debounce timings,
 * and UI visual effect complexity according to the device's actual RAM and CPU capabilities.
 */
@Immutable
class DevicePerformanceProfile private constructor(
    val tier: Tier,
    val totalRamMb: Long,
    val cpuCores: Int,
    val previewWidth: Int,
    val maxPreviewHeight: Int,
    val hdWidth: Int,
    val maxHdHeight: Int,
    val ultraHdWidth: Int,
    val maxUltraHdHeight: Int,
    val zoomTileWidth: Int,
    val maxZoomTileHeight: Int,
    val settleDelayMs: Long,
    val flingGateDelayMs: Long,
    val initialPrewarmPreviewCount: Int,
    val auraParticleCount: Int,
    val gyroRingSegments: Int,
    val waveStepDivisor: Float,
    val useLightweightShadows: Boolean
) {
    enum class Tier {
        LOW_END,   // <= 4.5 GB RAM or low-RAM flag (e.g. Samsung Galaxy F13, budget A/M/F series)
        MID_RANGE, // 4.5 GB .. 6.5 GB RAM
        HIGH_END   // > 6.5 GB RAM
    }

    companion object {
        @Volatile
        private var cachedInstance: DevicePerformanceProfile? = null

        fun get(context: Context): DevicePerformanceProfile {
            cachedInstance?.let { return it }
            synchronized(this) {
                cachedInstance?.let { return it }
                val appContext = context.applicationContext ?: context
                val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val memInfo = ActivityManager.MemoryInfo()
                try {
                    am?.getMemoryInfo(memInfo)
                } catch (_: Throwable) {
                }
                val totalRamMb = if (memInfo.totalMem > 0L) {
                    memInfo.totalMem / (1024L * 1024L)
                } else {
                    3800L
                }
                val isLowRam = am?.isLowRamDevice == true
                val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
                val maxHeapMb = (Runtime.getRuntime().maxMemory() / (1024L * 1024L)).coerceAtLeast(64L)

                val tier = when {
                    isLowRam || totalRamMb <= 4600L || cores <= 4 || maxHeapMb <= 256L -> Tier.LOW_END
                    totalRamMb <= 6800L || maxHeapMb <= 384L -> Tier.MID_RANGE
                    else -> Tier.HIGH_END
                }

                val profile = when (tier) {
                    Tier.LOW_END -> DevicePerformanceProfile(
                        tier = Tier.LOW_END,
                        totalRamMb = totalRamMb,
                        cpuCores = cores,
                        previewWidth = 240,
                        maxPreviewHeight = 560,
                        hdWidth = 960,
                        maxHdHeight = 1580,
                        ultraHdWidth = 1280,
                        maxUltraHdHeight = 1960,
                        zoomTileWidth = 1080,
                        maxZoomTileHeight = 1620,
                        settleDelayMs = 220L,
                        flingGateDelayMs = 90L,
                        initialPrewarmPreviewCount = 10,
                        auraParticleCount = 8,
                        gyroRingSegments = 16,
                        waveStepDivisor = 12f,
                        useLightweightShadows = true
                    )
                    Tier.MID_RANGE -> DevicePerformanceProfile(
                        tier = Tier.MID_RANGE,
                        totalRamMb = totalRamMb,
                        cpuCores = cores,
                        previewWidth = 280,
                        maxPreviewHeight = 640,
                        hdWidth = 1080,
                        maxHdHeight = 1780,
                        ultraHdWidth = 1440,
                        maxUltraHdHeight = 2200,
                        zoomTileWidth = 1240,
                        maxZoomTileHeight = 1860,
                        settleDelayMs = 180L,
                        flingGateDelayMs = 70L,
                        initialPrewarmPreviewCount = 10,
                        auraParticleCount = 12,
                        gyroRingSegments = 22,
                        waveStepDivisor = 18f,
                        useLightweightShadows = false
                    )
                    Tier.HIGH_END -> DevicePerformanceProfile(
                        tier = Tier.HIGH_END,
                        totalRamMb = totalRamMb,
                        cpuCores = cores,
                        previewWidth = 340,
                        maxPreviewHeight = 760,
                        hdWidth = 1240,
                        maxHdHeight = 2040,
                        ultraHdWidth = 1680,
                        maxUltraHdHeight = 2560,
                        zoomTileWidth = 1440,
                        maxZoomTileHeight = 2160,
                        settleDelayMs = 140L,
                        flingGateDelayMs = 50L,
                        initialPrewarmPreviewCount = 12,
                        auraParticleCount = 18,
                        gyroRingSegments = 30,
                        waveStepDivisor = 24f,
                        useLightweightShadows = false
                    )
                }
                cachedInstance = profile
                return profile
            }
        }
    }
}
