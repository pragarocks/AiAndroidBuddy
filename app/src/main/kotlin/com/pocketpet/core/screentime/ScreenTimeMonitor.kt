package com.pocketpet.core.screentime

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

data class AppUsage(val packageName: String, val totalMinutes: Long)

/**
 * Reads screen/app usage via UsageStatsManager.
 * Requires android.permission.PACKAGE_USAGE_STATS (user must grant in Settings).
 * Returns -1 gracefully if permission is not granted.
 */
@Singleton
class ScreenTimeMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val usageManager by lazy {
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    }

    /** Total screen-on time today in minutes, or -1 if permission denied */
    fun getTodayScreenMinutes(): Long {
        val mgr = usageManager ?: return -1L
        val (start, end) = todayRange()
        return try {
            val stats = mgr.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            if (stats.isNullOrEmpty()) return -1L
            val totalMs = stats.sumOf { it.totalTimeInForeground }
            totalMs / 60_000L
        } catch (_: Exception) { -1L }
    }

    /** Top N apps by foreground time today */
    fun getTopApps(limit: Int = 3): List<AppUsage> {
        val mgr = usageManager ?: return emptyList()
        val (start, end) = todayRange()
        return try {
            mgr.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
                ?.filter { it.totalTimeInForeground > 0 }
                ?.sortedByDescending { it.totalTimeInForeground }
                ?.take(limit)
                ?.map { AppUsage(it.packageName, it.totalTimeInForeground / 60_000L) }
                ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    /** Returns true if the user has granted PACKAGE_USAGE_STATS permission */
    fun hasPermission(): Boolean {
        val mgr = usageManager ?: return false
        val (start, end) = todayRange()
        return try {
            val stats = mgr.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            !stats.isNullOrEmpty()
        } catch (_: Exception) { false }
    }

    private fun todayRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis to System.currentTimeMillis()
    }
}
