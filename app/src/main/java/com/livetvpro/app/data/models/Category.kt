package com.livetvpro.app.data.models

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class Category(
    val id: String = "",
    val name: String = "",
    val slug: String = "",
    val iconUrl: String? = null,
    val m3uUrl: String? = null,
    val order: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = ""
) : Parcelable

@Parcelize
data class Channel(
    val id: String = "",
    val name: String = "",
    val logoUrl: String = "",
    val streamUrl: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val groupTitle: String = "",
    val links: List<ChannelLink>? = null,
    val team1Logo: String = "",
    val team2Logo: String = "",
    val isLive: Boolean = false,
    val startTime: String = "",
    val endTime: String = "",
    val createdAt: String = "",
    val updatedAt: String = ""
) : Parcelable

@Parcelize
data class ChannelLink(
    @SerializedName(value = "quality", alternate = ["label", "name"])
    val quality: String = "",
    val url: String = "",
    val cookie: String? = null,
    val referer: String? = null,
    val origin: String? = null,
    val userAgent: String? = null,
    val drmScheme: String? = null,
    val drmLicenseUrl: String? = null
) : Parcelable

@Parcelize
data class FavoriteChannel(
    val id: String = "",
    val name: String = "",
    val logoUrl: String = "",
    val streamUrl: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val links: List<ChannelLink>? = null
) : Parcelable

@Parcelize
data class LiveEvent(
    val id: String = "",
    val category: String = "",
    val league: String = "",
    val leagueLogo: String = "",
    val team1Name: String = "",
    val team1Logo: String = "",
    val team2Name: String = "",
    val team2Logo: String = "",
    val startTime: String = "",
    val endTime: String? = null,
    val isLive: Boolean = false,
    val links: List<LiveEventLink> = emptyList(),
    val title: String = "",
    val description: String = "",
    val wrapper: String = "",
    val eventCategoryId: String = "",
    val eventCategoryName: String = "",
    val createdAt: String = "",
    val updatedAt: String = ""
) : Parcelable {
    fun getStatus(currentTime: Long): EventStatus {
        val startTimestamp = parseTimestamp(startTime)
        val endTimestamp = endTime?.let { parseTimestamp(it) }

        return when {
            endTimestamp != null && currentTime >= startTimestamp && currentTime <= endTimestamp -> EventStatus.LIVE
            isLive && currentTime >= startTimestamp -> EventStatus.LIVE
            currentTime < startTimestamp -> EventStatus.UPCOMING
            endTimestamp != null && currentTime > endTimestamp -> EventStatus.RECENT
            currentTime > startTimestamp -> EventStatus.RECENT
            else -> EventStatus.UPCOMING
        }
    }

    private fun parseTimestamp(timeString: String): Long {
        return try {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.parse(timeString)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}

@Parcelize
data class LiveEventLink(
    @SerializedName(value = "quality", alternate = ["label", "name"])
    val quality: String = "",
    val url: String = "",
    val cookie: String? = null,
    val referer: String? = null,
    val origin: String? = null,
    val userAgent: String? = null,
    val drmScheme: String? = null,
    val drmLicenseUrl: String? = null
) : Parcelable

@Parcelize
data class EventCategory(
    val id: String = "",
    val name: String = "",
    val slug: String = "",
    val logoUrl: String = "",
    val order: Int = 0,
    val isDefault: Boolean = false,
    val createdAt: String = "",
    val updatedAt: String = ""
) : Parcelable

enum class EventStatus {
    LIVE, UPCOMING, RECENT
}


data class NewExternalEventRow(
    @SerializedName("event_title")    val eventTitle: String = "",
    @SerializedName("event_id")       val eventId: String = "",
    @SerializedName("event_cat")      val eventCat: String = "",
    @SerializedName("event_name")     val eventName: String = "",
    @SerializedName("event_time")     val eventTime: String = "",
    @SerializedName("event_end_time") val eventEndTime: String? = null,
    @SerializedName("channel_title")  val channelTitle: String = "",
    @SerializedName("stream_url")     val streamUrl: String = "",
    @SerializedName("keyid")          val keyId: String? = null,
    @SerializedName("key")            val key: String? = null,
    @SerializedName("headers")        val headers: String? = null,
    @SerializedName("referer")        val referer: String? = null,
    @SerializedName("origin")         val origin: String? = null,
    @SerializedName("is_hot")         val isHot: Boolean = false,
    @SerializedName("event_type")     val eventType: String? = null,
    @SerializedName("team_a")         val teamA: String = "",
    @SerializedName("team_b")         val teamB: String = "",
    @SerializedName("team_a_logo")    val teamALogo: String = "",
    @SerializedName("team_b_logo")    val teamBLogo: String = ""
)

fun List<NewExternalEventRow>.toGroupedLiveEvents(): List<LiveEvent> {
    return groupBy { it.eventId }
        .map { (_, rows) ->
            val first = rows.first()

            val links = rows.map { row ->
                LiveEventLink(
                    quality       = row.channelTitle,
                    url           = row.streamUrl,
                    referer       = row.referer,
                    origin        = row.origin,
                    drmScheme     = if (row.hasDrm()) "clearkey" else null,
                    drmLicenseUrl = if (row.hasDrm()) "${row.keyId}:${row.key}" else null
                )
            }

            LiveEvent(
                id                = first.eventId,
                category          = first.eventCat,
                league            = first.eventCat,
                leagueLogo        = "",
                team1Name         = first.teamA.ifBlank { first.eventTitle },
                team1Logo         = first.teamALogo,
                team2Name         = first.teamB.ifBlank { first.eventTitle },
                team2Logo         = first.teamBLogo,
                startTime         = first.eventTime.toIso8601Utc() ?: first.eventTime,
                endTime           = first.eventEndTime?.toIso8601Utc()
                                    ?: first.eventTime.toIso8601UtcPlusHours(3),
                isLive            = false,
                links             = links,
                title             = first.eventName,
                description       = "",
                wrapper           = if (first.isHot) "Hot" else "",
                eventCategoryId   = first.eventCat,
                eventCategoryName = first.eventCat
            )
        }
}

private fun NewExternalEventRow.hasDrm(): Boolean {
    return !keyId.isNullOrBlank() && keyId != "0"
        && !key.isNullOrBlank() && key != "0"
}

private fun String.toIso8601Utc(): String? {
    return try {
        val inputFmt = java.text.SimpleDateFormat(
            "yyyy-MM-dd HH:mm", java.util.Locale.US
        ).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
            isLenient = false
        }
        val outputFmt = java.text.SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US
        ).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        val parsed = inputFmt.parse(this) ?: return null
        outputFmt.format(parsed)
    } catch (e: Exception) {
        null
    }
}

private fun String.toIso8601UtcPlusHours(hours: Int): String? {
    return try {
        val inputFmt = java.text.SimpleDateFormat(
            "yyyy-MM-dd HH:mm", java.util.Locale.US
        ).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
            isLenient = false
        }
        val outputFmt = java.text.SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US
        ).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        val parsed = inputFmt.parse(this) ?: return null
        outputFmt.format(java.util.Date(parsed.time + hours * 3_600_000L))
    } catch (e: Exception) {
        null
    }
}

