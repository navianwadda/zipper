package com.livetvpro.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.data.models.ChannelLink

@Entity(
    tableName = "channels",
    indices = [
        Index("categoryId"),
        Index("groupTitle"),
        Index(value = ["categoryId", "name"])
    ]
)
data class ChannelEntity(
    @PrimaryKey val id: String,
    val position: Int = 0,
    val name: String,
    val logoUrl: String,
    val streamUrl: String,
    val categoryId: String,
    val categoryName: String,
    val groupTitle: String,
    val linksJson: String?,
    val team1Logo: String,
    val team2Logo: String,
    val isLive: Boolean,
    val startTime: String,
    val endTime: String,
    val createdAt: String,
    val updatedAt: String
) {
    fun toChannel(links: List<ChannelLink>?): Channel = Channel(
        id           = id,
        name         = name,
        logoUrl      = logoUrl,
        streamUrl    = streamUrl,
        categoryId   = categoryId,
        categoryName = categoryName,
        groupTitle   = groupTitle,
        links        = links,
        team1Logo    = team1Logo,
        team2Logo    = team2Logo,
        isLive       = isLive,
        startTime    = startTime,
        endTime      = endTime,
        createdAt    = createdAt,
        updatedAt    = updatedAt,
        position     = position
    )
}

fun Channel.toEntity(): ChannelEntity {
    val gson = Gson()
    return ChannelEntity(
        id           = id,
        position     = position,
        name         = name,
        logoUrl      = logoUrl,
        streamUrl    = streamUrl,
        categoryId   = categoryId,
        categoryName = categoryName,
        groupTitle   = groupTitle,
        linksJson    = if (links != null) gson.toJson(links) else null,
        team1Logo    = team1Logo,
        team2Logo    = team2Logo,
        isLive       = isLive,
        startTime    = startTime,
        endTime      = endTime,
        createdAt    = createdAt,
        updatedAt    = updatedAt
    )
}
