package com.livetvpro.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_channels")
data class FavoriteChannelEntity(
    @PrimaryKey
    val id: String,
    val addedAt: Long = System.currentTimeMillis()
)
