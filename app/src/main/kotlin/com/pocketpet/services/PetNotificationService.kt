package com.pocketpet.services

import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.pocketpet.core.notifications.NotificationItem
import com.pocketpet.core.notifications.NotificationRepository
import com.pocketpet.core.pet.PetEvent
import com.pocketpet.core.pet.PetStateMachine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PetNotificationService : NotificationListenerService() {

    @Inject lateinit var repository: NotificationRepository
    @Inject lateinit var stateMachine: PetStateMachine

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.isOngoing || sbn.packageName == packageName) return

        val notification = sbn.notification ?: return
        val extras = notification.extras

        val item = NotificationItem(
            id = sbn.key,
            packageName = sbn.packageName,
            appLabel = getAppLabel(sbn.packageName),
            title = extras.getString("android.title"),
            text = extras.getCharSequence("android.text")?.toString(),
            timestamp = sbn.postTime,
            priority = notification.priority,
            category = notification.category
        )

        repository.add(item)
        stateMachine.send(PetEvent.NotificationArrived)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // no-op: we keep notifications in our buffer for context
    }

    private fun getAppLabel(packageName: String): String {
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }
}
