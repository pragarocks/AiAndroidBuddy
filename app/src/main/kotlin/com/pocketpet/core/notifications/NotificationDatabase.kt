package com.pocketpet.core.notifications

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [NotificationItem::class], version = 1, exportSchema = false)
abstract class NotificationDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao
}
