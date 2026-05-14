package com.pocketpet.core.nudge

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules all periodic and one-time health nudge WorkRequests.
 * Called once from PetOverlayService.onCreate().
 *
 * Schedule:
 *   - Water nudge:     every 90 minutes
 *   - Posture nudge:   every 45 minutes
 *   - Eye break nudge: every 60 minutes
 *   - Step nudge:      every 2 hours
 *   - Sleep nudge:     once daily at 23:00
 */
@Singleton
class NudgeScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    fun scheduleAll() {
        schedulePeriodicNudge(
            uniqueName = "nudge_water",
            type = NudgeType.WATER,
            intervalMinutes = 90L
        )
        schedulePeriodicNudge(
            uniqueName = "nudge_posture",
            type = NudgeType.POSTURE,
            intervalMinutes = 45L,
            initialDelayMinutes = 20L   // Don't fire immediately on first launch
        )
        schedulePeriodicNudge(
            uniqueName = "nudge_eyes",
            type = NudgeType.EYES,
            intervalMinutes = 60L,
            initialDelayMinutes = 40L
        )
        schedulePeriodicNudge(
            uniqueName = "nudge_step",
            type = NudgeType.STEP,
            intervalMinutes = 120L,
            initialDelayMinutes = 60L
        )
        // Check if user has been in same app too long (runs every 30 min, self-suppresses if not applicable)
        schedulePeriodicNudge(
            uniqueName = "nudge_app_time",
            type = NudgeType.APP_TIME,
            intervalMinutes = 30L,
            initialDelayMinutes = 30L
        )
        scheduleSleepNudge()
    }

    fun cancel() {
        workManager.cancelUniqueWork("nudge_water")
        workManager.cancelUniqueWork("nudge_posture")
        workManager.cancelUniqueWork("nudge_eyes")
        workManager.cancelUniqueWork("nudge_step")
        workManager.cancelUniqueWork("nudge_app_time")
        workManager.cancelUniqueWork("nudge_sleep")
    }

    private fun schedulePeriodicNudge(
        uniqueName: String,
        type: NudgeType,
        intervalMinutes: Long,
        initialDelayMinutes: Long = 0L
    ) {
        val request = PeriodicWorkRequestBuilder<NudgeWorker>(intervalMinutes, TimeUnit.MINUTES)
            .setInputData(workDataOf(NudgeWorker.KEY_NUDGE_TYPE to type.name))
            .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            uniqueName,
            ExistingPeriodicWorkPolicy.KEEP, // Don't restart if already scheduled
            request
        )
    }

    /** Fire the sleep nudge at 23:00 each night */
    private fun scheduleSleepNudge() {
        val delayMs = millisUntil(hour = 23, minute = 0)
        val request = OneTimeWorkRequestBuilder<NudgeWorker>()
            .setInputData(workDataOf(NudgeWorker.KEY_NUDGE_TYPE to NudgeType.SLEEP.name))
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniqueWork(
            "nudge_sleep",
            ExistingWorkPolicy.REPLACE, // Re-schedule if time has passed
            request
        )
    }

    private fun millisUntil(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.before(now)) target.add(Calendar.DAY_OF_YEAR, 1)
        return (target.timeInMillis - now.timeInMillis).coerceAtLeast(60_000L)
    }
}
