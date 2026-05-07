package com.pocketpet.core.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class ModelVariant(val displayName: String, val modelFile: String, val minRamBytes: Long) {
    LITE("Lite (0.8B)", "qwen3.5-0.8b-q4.mnn", 4L * 1024 * 1024 * 1024),
    STANDARD("Standard (2B)", "qwen3.5-2b-q4.mnn", 6L * 1024 * 1024 * 1024),
    PRO("Pro (4B)", "qwen3.5-4b-q4.mnn", 8L * 1024 * 1024 * 1024)
}

@Singleton
class DeviceCapabilityChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    fun recommendedVariant(): ModelVariant {
        val ram = totalRamBytes()
        return when {
            ram >= ModelVariant.PRO.minRamBytes -> ModelVariant.PRO
            ram >= ModelVariant.STANDARD.minRamBytes -> ModelVariant.STANDARD
            else -> ModelVariant.LITE
        }
    }

    fun totalRamBytes(): Long {
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem
    }

    fun availableStorageBytes(): Long {
        val stat = StatFs(context.filesDir.absolutePath)
        return stat.availableBlocksLong * stat.blockSizeLong
    }

    fun hasEnoughStorage(requiredBytes: Long = 800L * 1024 * 1024): Boolean =
        availableStorageBytes() >= requiredBytes

    fun isLowMemory(): Boolean {
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.lowMemory
    }

    fun batteryLevel(context: Context): Int {
        val intent = context.registerReceiver(null,
            android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
    }
}
