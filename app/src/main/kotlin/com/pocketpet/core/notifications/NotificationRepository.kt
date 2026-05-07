package com.pocketpet.core.notifications

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor(
    private val dao: NotificationDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _newNotification = MutableSharedFlow<NotificationItem>(replay = 0)
    val newNotification: SharedFlow<NotificationItem> = _newNotification.asSharedFlow()

    val recentNotifications: Flow<List<NotificationItem>> = dao.getRecent(50)
    val unreadCount: Flow<Int> = dao.unreadCount()

    fun add(item: NotificationItem) {
        scope.launch {
            dao.insert(item)
            _newNotification.emit(item)
            pruneIfNeeded()
        }
    }

    suspend fun getRecentOnce(limit: Int = 20): List<NotificationItem> =
        dao.getRecentOnce(limit)

    suspend fun markRead(id: String) = dao.markRead(id)

    private suspend fun pruneIfNeeded() {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        dao.pruneOlderThan(cutoff)
    }
}
