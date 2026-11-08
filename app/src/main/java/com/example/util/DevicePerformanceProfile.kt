package com.example.util

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.Immutable

/**
 * Hardware-aware adaptive performance profiler.
 * Automatically tunes PDF rendering resolutions, settle debounce timings, memory cache budgets,
 * and UI visual effect complexity according to the device's actual RAM and CPU capabilities
 * (e.g., budget 3-4GB Cortex-A55 phones like Samsung Galaxy F13 vs mid-range vs flagship devices).
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
                        previewWidth = 224,
                        maxPreviewHeight = 520,
                        hdWidth = 680,
                        maxHdHeight = 1120,
                        ultraHdWidth = 1000,
                        maxUltraHdHeight = 1540,
                        settleDelayMs = 260L,
                        flingGateDelayMs = 95L,
                        initialPrewarmPreviewCount = 10,
                        auraParticleCount = 10,
                        gyroRingSegments = 18,
                        waveStepDivisor = 14f,
                        useLightweightShadows = true
                    )
                    Tier.MID_RANGE -> DevicePerformanceProfile(
                        tier = Tier.MID_RANGE,
                        totalRamMb = totalRamMb,
                        cpuCores = cores,
                        previewWidth = 280,
                        maxPreviewHeight = 640,
                        hdWidth = 880,
                        maxHdHeight = 1420,
                        ultraHdWidth = 1280,
                        maxUltraHdHeight = 1900,
                        settleDelayMs = 200L,
                        flingGateDelayMs = 75L,
                        initialPrewarmPreviewCount = 10,
                        auraParticleCount = 16,
                        gyroRingSegments = 24,
                        waveStepDivisor = 20f,
                        useLightweightShadows = false
                    )
                    Tier.HIGH_END -> DevicePerformanceProfile(
                        tier = Tier.HIGH_END,
                        totalRamMb = totalRamMb,
                        cpuCores = cores,
                        previewWidth = 340,
                        maxPreviewHeight = 760,
                        hdWidth = 1080,
                        maxHdHeight = 1720,
                        ultraHdWidth = 1520,
                        maxUltraHdHeight = 2240,
                        settleDelayMs = 150L,
                        flingGateDelayMs = 55L,
                        initialPrewarmPreviewCount = 12,
                        auraParticleCount = 24,
                        gyroRingSegments = 36,
                        waveStepDivisor = 28f,
                        useLightweightShadows = false
                    )
                }
                cachedInstance = profile
                return profile
            }
        }
    }
}
