package com.pocketpet.core.notifications

sealed interface NotificationAction {
    data class Dismiss(val notificationKey: String) : NotificationAction
    data class Reply(val notificationKey: String, val text: String) : NotificationAction
    data class Open(val notificationKey: String) : NotificationAction
    data class Snooze(val notificationKey: String, val durationMs: Long) : NotificationAction
}
