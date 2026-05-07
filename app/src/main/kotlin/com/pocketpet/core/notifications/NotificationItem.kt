package com.pocketpet.core.notifications

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationItem(
    @PrimaryKey val id: String,
    val packageName: String,
    val appLabel: String,
    val title: String?,
    val text: String?,
    val timestamp: Long,
    val priority: Int,
    val category: String?,
    val isRead: Boolean = false
)
