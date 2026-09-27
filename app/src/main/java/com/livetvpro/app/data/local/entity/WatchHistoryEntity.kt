package com.livetvpro.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val logoUrl: String,
    val streamUrl: String,
    val categoryId: String,
    val categoryName: String,
    val groupTitle: String,
    val linksJson: String?,
    val linkIndex: Int = -1,
    val watchedAt: Long = System.currentTimeMillis()
)
