package com.azizbek.safetyai.data.repository

import com.azizbek.safetyai.data.local.NotificationDao
import com.azizbek.safetyai.data.local.NotificationEntity
import kotlinx.coroutines.flow.Flow

/** Backs the Notifications screen. Nothing currently inserts into it — see [NotificationEntity]. */
class NotificationRepository(private val dao: NotificationDao) {
    fun observeAll(): Flow<List<NotificationEntity>> = dao.observeAll()
    suspend fun markRead(id: String) = dao.markRead(id)
}
