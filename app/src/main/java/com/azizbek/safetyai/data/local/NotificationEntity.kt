package com.azizbek.safetyai.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A notification-center entry. Nothing in this build ever inserts one — the
 * only trigger (a completed or failed analysis) doesn't exist yet — but the
 * table, DAO and screen are ready for when [com.azizbek.safetyai.analysis]
 * has a real engine.
 */
@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val message: String,
    val createdAtEpochMs: Long,
    val read: Boolean
)
