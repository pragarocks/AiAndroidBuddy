package com.pocketpet.core.nudge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pocketpet.R
import com.pocketpet.core.screentime.ScreenTimeMonitor
import com.pocketpet.services.PetBrainService
import com.pocketpet.ui.main.MainActivity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar

/**
 * WorkManager worker that fires health nudges.
 *
 * Logic:
 *  1. If the user has been in a single app > 30 min → APP_TIME nudge
 *  2. Else if total screen time today > 3h → SCREEN_TIME nudge
 *  3. Else → pick a nudge based on time-of-day
 *
 * Delivery:
 *  - Sends ACTION_SHOW_NUDGE to PetBrainService (pet speaks + shows bubble)
 *  - Also posts a system notification so the user sees it even if they dismiss the pet
 */
@HiltWorker
class NudgeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val screenTimeMonitor: ScreenTimeMonitor
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_NUDGE_TYPE = "nudge_type"
        private const val NOTIF_CHANNEL_ID  = "pocketpet_nudge"
        private const val NOTIF_CHANNEL_NAME = "PocketPet Reminders"
        private const val NOTIF_ID_BASE = 3000
    }

    override suspend fun doWork(): Result {
        val nudgeType = resolveNudgeType()
        val message   = NudgeMessages.random(nudgeType)
        val fullText  = "${message.emoji} ${message.text}"

        // 1. Wake PetBrainService → pet speaks + bubble
        try {
            applicationContext.startForegroundService(
                Intent(applicationContext, PetBrainService::class.java).apply {
                    action = PetBrainService.ACTION_SHOW_NUDGE
                    putExtra(PetBrainService.EXTRA_NUDGE_TEXT, fullText)
                }
            )
        } catch (_: Exception) {}

        // 2. Post a system notification (visible even if pet overlay is hidden)
        postNotification(fullText, nudgeType)

        return Result.success()
    }

    // ── Nudge type resolution ─────────────────────────────────────────────────

    private fun resolveNudgeType(): NudgeType {
        // Explicit type from scheduler takes priority
        val inputType = inputData.getString(KEY_NUDGE_TYPE)
        if (inputType != null) {
            // But override with APP_TIME if user has been in same app too long
            val appMinutes = getCurrentForegroundAppMinutes()
            if (appMinutes >= 30) return NudgeType.APP_TIME
            return NudgeType.valueOf(inputType)
        }

        // Auto-resolve based on usage
        val screenMinutes = screenTimeMonitor.getTodayScreenMinutes()
        val appMinutes    = getCurrentForegroundAppMinutes()

        return when {
            appMinutes    >= 30  -> NudgeType.APP_TIME
            screenMinutes >= 180 -> NudgeType.SCREEN_TIME
            else                 -> NudgeMessages.typeForHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
        }
    }

    /**
     * Returns how many minutes the user has been continuously in the currently
     * active foreground app (approximated from the last UsageEvent).
     * Returns 0 if UsageStats permission is not granted.
     */
    private fun getCurrentForegroundAppMinutes(): Long {
        val mgr = applicationContext.getSystemService(Context.USAGE_STATS_SERVICE)
                as? UsageStatsManager ?: return 0L
        val end   = System.currentTimeMillis()
        val start = end - 60 * 60 * 1000L  // look back 1 hour

        return try {
            val events = mgr.queryEvents(start, end)
            val event  = UsageEvents.Event()
            var lastResumeMs = 0L
            var lastPkg      = ""

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    lastPkg      = event.packageName ?: ""
                    lastResumeMs = event.timeStamp
                }
            }
            if (lastPkg.isEmpty() || lastResumeMs == 0L) return 0L
            (end - lastResumeMs) / 60_000L
        } catch (_: Exception) { 0L }
    }

    // ── System notification ────────────────────────────────────────────────────

    private fun postNotification(text: String, type: NudgeType) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                as? NotificationManager ?: return

        // Ensure channel exists (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID, NOTIF_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Friendly wellness reminders from your PocketPet" }
            nm.createNotificationChannel(channel)
        }

        val tapIntent = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif = NotificationCompat.Builder(applicationContext, NOTIF_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pet_notification)
            .setContentTitle(nudgeTitle(type))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        nm.notify(NOTIF_ID_BASE + type.ordinal, notif)
    }

    private fun nudgeTitle(type: NudgeType) = when (type) {
        NudgeType.WATER       -> "Hydration reminder"
        NudgeType.POSTURE     -> "Posture check"
        NudgeType.EYES        -> "Eye break time"
        NudgeType.STEP        -> "Move a little!"
        NudgeType.SLEEP       -> "Time to wind down"
        NudgeType.SCREEN_TIME -> "Screen time alert"
        NudgeType.APP_TIME    -> "Long app session"
    }
}
