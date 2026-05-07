package com.pocketpet.core.notifications

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: NotificationItem)

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<NotificationItem>>

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentOnce(limit: Int = 50): List<NotificationItem>

    @Query("DELETE FROM notifications WHERE timestamp < :cutoff")
    suspend fun pruneOlderThan(cutoff: Long)

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    fun unreadCount(): Flow<Int>

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: String)

    @Query("DELETE FROM notifications")
    suspend fun clearAll()
}
