package com.livetvpro.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.data.models.ChannelLink
import com.livetvpro.app.data.models.LiveEvent
import com.livetvpro.app.ui.player.FloatingPlayerActivity
import com.livetvpro.app.ui.player.FloatingPlayerService
import com.livetvpro.app.utils.DeviceUtils
import java.util.UUID
import com.livetvpro.app.ui.player.ChannelListCache

object FloatingPlayerHelper {

    private val createdInstances = mutableListOf<String>()
    private val eventToInstanceMap = mutableMapOf<String, String>()

    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun requestOverlayPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (e: Exception) {

            }
        }
    }

    fun launchFloatingPlayer(context: Context, channel: Channel, linkIndex: Int = 0, eventId: String? = null, isSports: Boolean = false, channelList: ArrayList<Channel>? = null, channelListCacheKey: String? = null) {
        if (DeviceUtils.isTvDevice) {
            val key = channelListCacheKey ?: channelList?.let { list ->
                val k = channel.categoryId.takeIf { it.isNotEmpty() } ?: channel.id
                ChannelListCache.put(k, list)
                k
            }
            FloatingPlayerActivity.startWithChannel(context, channel, linkIndex, categoryId = channel.categoryId.takeIf { it.isNotEmpty() }, isSports = isSports, channelListCacheKey = key)
            return
        }

        if (!hasOverlayPermission(context)) return

        val resolvedChannel = if (channel.links.isNullOrEmpty()) {
            if (channel.streamUrl.isBlank()) return
            channel.copy(links = listOf(parseLinkFromStreamUrl(channel.streamUrl)))
        } else {
            channel
        }

        val actualEventId = eventId ?: resolvedChannel.id
        val existingInstanceId = eventToInstanceMap[actualEventId]

        if (existingInstanceId != null && FloatingPlayerManager.hasPlayer(existingInstanceId)) {
            updateFloatingPlayer(context, existingInstanceId, resolvedChannel, linkIndex, channelList, channelListCacheKey)
        } else if (FloatingPlayerManager.canAddNewPlayer()) {
            createNewFloatingPlayer(context, channel = resolvedChannel, linkIndex = linkIndex, eventId = actualEventId, channelList = channelList, channelListCacheKey = channelListCacheKey, isSports = isSports)
        } else {
            val lastId = FloatingPlayerManager.getLastPlayerId()
            if (lastId != null) {
                val oldEventKey = eventToInstanceMap.entries.find { it.value == lastId }?.key
                if (oldEventKey != null) eventToInstanceMap.remove(oldEventKey)
                eventToInstanceMap[actualEventId] = lastId
                updateFloatingPlayer(context, lastId, resolvedChannel, linkIndex, channelList, channelListCacheKey)
            }
        }
    }

    fun launchFloatingPlayerWithEvent(context: Context, channel: Channel, event: LiveEvent, linkIndex: Int = 0) {
        if (!hasOverlayPermission(context)) return
        if (DeviceUtils.isTvDevice) {
            FloatingPlayerActivity.startWithChannel(context, channel, linkIndex)
            return
        }

        if (channel.links.isNullOrEmpty()) return

        val existingInstanceId = eventToInstanceMap[event.id]

        if (existingInstanceId != null && FloatingPlayerManager.hasPlayer(existingInstanceId)) {
            updateFloatingPlayerWithEvent(context, existingInstanceId, channel, event, linkIndex)
        } else if (FloatingPlayerManager.canAddNewPlayer()) {
            createNewFloatingPlayer(context, channel = channel, event = event, linkIndex = linkIndex, eventId = event.id)
        } else {
            val lastId = FloatingPlayerManager.getLastPlayerId()
            if (lastId != null) {
                val oldEventKey = eventToInstanceMap.entries.find { it.value == lastId }?.key
                if (oldEventKey != null) eventToInstanceMap.remove(oldEventKey)
                eventToInstanceMap[event.id] = lastId
                updateFloatingPlayerWithEvent(context, lastId, channel, event, linkIndex)
            }
        }
    }

    fun launchFloatingPlayerWithNetworkStream(
        context: Context,
        streamUrl: String,
        cookie: String = "",
        referer: String = "",
        origin: String = "",
        drmLicense: String = "",
        userAgent: String = "Default",
        drmScheme: String = "clearkey",
        streamName: String = "Network Stream",
        xForwardedFor: String = ""
    ): String? {
        if (DeviceUtils.isTvDevice) {
            FloatingPlayerActivity.startWithNetworkStream(context, streamUrl, cookie, referer, origin, drmLicense, userAgent, drmScheme, streamName)
            return null
        }

        if (!hasOverlayPermission(context)) return null
        if (streamUrl.isBlank()) return null
        if (!FloatingPlayerManager.canAddNewPlayer()) {
            val lastId = FloatingPlayerManager.getLastPlayerId()
            if (lastId != null) {
                FloatingPlayerService.updateFloatingPlayerWithNetworkStream(
                    context = context,
                    instanceId = lastId,
                    streamUrl = streamUrl,
                    cookie = cookie,
                    referer = referer,
                    origin = origin,
                    drmLicense = drmLicense,
                    userAgent = userAgent,
                    drmScheme = drmScheme,
                    streamName = streamName,
                    xForwardedFor = xForwardedFor
                )
                return lastId
            }
            return null
        }

        val instanceId = UUID.randomUUID().toString()
        FloatingPlayerManager.addPlayer(instanceId, streamName, "network_stream")

        return try {
            val started = FloatingPlayerService.startFloatingPlayerWithNetworkStream(
                context = context,
                instanceId = instanceId,
                streamUrl = streamUrl,
                cookie = cookie,
                referer = referer,
                origin = origin,
                drmLicense = drmLicense,
                userAgent = userAgent,
                drmScheme = drmScheme,
                streamName = streamName,
                xForwardedFor = xForwardedFor
            )
            if (started) {
                createdInstances.add(instanceId)
                instanceId
            } else {
                FloatingPlayerManager.removePlayer(instanceId)
                null
            }
        } catch (e: Exception) {
            FloatingPlayerManager.removePlayer(instanceId)
            null
        }
    }

    private fun updateFloatingPlayer(context: Context, instanceId: String, channel: Channel, linkIndex: Int, channelList: ArrayList<Channel>? = null, channelListCacheKey: String? = null) {
        try {
            val resolvedChannel = if (channel.links.isNullOrEmpty()) {
                if (channel.streamUrl.isBlank()) return
                channel.copy(links = listOf(parseLinkFromStreamUrl(channel.streamUrl)))
            } else {
                channel
            }
            val key = channelListCacheKey ?: channelList?.let { list ->
                val k = resolvedChannel.categoryId.takeIf { it.isNotEmpty() } ?: resolvedChannel.id
                ChannelListCache.put(k, list)
                k
            }
            FloatingPlayerService.updateFloatingPlayer(context, instanceId, resolvedChannel, linkIndex, key)
        } catch (e: Exception) {

        }
    }

    private fun updateFloatingPlayerWithEvent(context: Context, instanceId: String, channel: Channel, event: LiveEvent, linkIndex: Int) {
        try {
            val resolvedChannel = if (channel.links.isNullOrEmpty()) {
                if (channel.streamUrl.isBlank()) return
                channel.copy(links = listOf(parseLinkFromStreamUrl(channel.streamUrl)))
            } else {
                channel
            }
            FloatingPlayerService.updateFloatingPlayerWithEvent(context, instanceId, resolvedChannel, event, linkIndex)
        } catch (e: Exception) {

        }
    }

    fun createNewFloatingPlayer(
        context: Context,
        channel: Channel? = null,
        event: LiveEvent? = null,
        linkIndex: Int = 0,
        eventId: String? = null,
        channelList: ArrayList<Channel>? = null,
        channelListCacheKey: String? = null,
        isSports: Boolean = false
    ): String? {
        if (!FloatingPlayerManager.canAddNewPlayer()) return null
        if (!hasOverlayPermission(context)) {
            requestOverlayPermission(context)
            return null
        }
        if (channel == null && event == null) return null

        val instanceId = UUID.randomUUID().toString()
        val contentName = channel?.name ?: event?.title ?: "Unknown"
        val contentType = if (channel != null) "channel" else "event"
        val actualEventId = eventId ?: channel?.id ?: event?.id ?: ""

        FloatingPlayerManager.addPlayer(instanceId, contentName, contentType)

        if (actualEventId.isNotEmpty()) {
            eventToInstanceMap[actualEventId] = instanceId
        }

        return try {
            val key = channelListCacheKey ?: channelList?.let { list ->
            val k = channel?.categoryId?.takeIf { it.isNotEmpty() } ?: channel?.id ?: ""
            if (k.isNotEmpty()) ChannelListCache.put(k, list)
            k.takeIf { it.isNotEmpty() }
        }
        val started = FloatingPlayerService.startFloatingPlayer(context, instanceId, channel, event, linkIndex, key, isSports)
            if (started) {
                createdInstances.add(instanceId)
                instanceId
            } else {
                FloatingPlayerManager.removePlayer(instanceId)
                eventToInstanceMap.remove(actualEventId)
                null
            }
        } catch (e: Exception) {
            FloatingPlayerManager.removePlayer(instanceId)
            eventToInstanceMap.remove(actualEventId)
            null
        }
    }

    fun closeFloatingPlayer(context: Context, instanceId: String) {
        if (!FloatingPlayerManager.hasPlayer(instanceId)) return
        val eventId = eventToInstanceMap.entries.find { it.value == instanceId }?.key
        if (eventId != null) eventToInstanceMap.remove(eventId)
        FloatingPlayerService.stopFloatingPlayer(context, instanceId)
        FloatingPlayerManager.removePlayer(instanceId)
        createdInstances.remove(instanceId)
    }

    fun closeAllFloatingPlayers(context: Context) {
        val instanceIds = FloatingPlayerManager.getAllPlayerIds()
        instanceIds.forEach { FloatingPlayerService.stopFloatingPlayer(context, it) }
        FloatingPlayerManager.clearAll()
        createdInstances.clear()
        eventToInstanceMap.clear()
    }

    fun hasFloatingPlayers(): Boolean = FloatingPlayerManager.hasAnyPlayers()

    fun getFloatingPlayerCount(): Int = FloatingPlayerManager.getActivePlayerCount()

    fun canCreateMore(): Boolean = FloatingPlayerManager.canAddNewPlayer()

    fun getActivePlayers(): List<FloatingPlayerManager.PlayerMetadata> = FloatingPlayerManager.getAllPlayerMetadata()

    fun hasFloatingPlayerForEvent(eventId: String): Boolean {
        val instanceId = eventToInstanceMap[eventId]
        return instanceId != null && FloatingPlayerManager.hasPlayer(instanceId)
    }

    fun getInstanceIdForEvent(eventId: String): String? = eventToInstanceMap[eventId]

    private fun parseLinkFromStreamUrl(streamUrl: String): ChannelLink {
        val pipeIndex = streamUrl.indexOf('|')
        if (pipeIndex == -1) {
            return ChannelLink(quality = "Default", url = streamUrl)
        }
        val url = streamUrl.substring(0, pipeIndex).trim()
        val rawParams = streamUrl.substring(pipeIndex + 1)

        var cookie: String? = null
        var referer: String? = null
        var origin: String? = null
        var userAgent: String? = null
        var xForwardedFor: String? = null
        var drmScheme: String? = null
        var drmLicenseUrl: String? = null
        val extraHeaders = mutableMapOf<String, String>()

        for (segment in rawParams.split("|")) {
            val trimmed = segment.trim()
            if (trimmed.isEmpty()) continue
            val eq = trimmed.indexOf('=')
            if (eq == -1) continue
            val key = trimmed.substring(0, eq).trim()
            val value = trimmed.substring(eq + 1).trim()
            if (key.isEmpty() || value.isEmpty()) continue

            when (key.lowercase()) {
                "drmscheme" -> drmScheme = value
                "drmlicense" -> drmLicenseUrl = value
                "cookie" -> cookie = value
                "referer", "referrer" -> referer = value
                "origin" -> origin = value
                "user-agent", "useragent" -> userAgent = value
                "x-forwarded-for" -> xForwardedFor = value
                "x-requested-with" -> extraHeaders["X-Requested-With"] = value
                "authorization" -> extraHeaders["Authorization"] = value
                "host" -> extraHeaders["Host"] = value
                else -> {
                    if (key.startsWith("x-", ignoreCase = true) ||
                        key.startsWith("sec-", ignoreCase = true) ||
                        key.equals("accept", ignoreCase = true) ||
                        key.equals("range", ignoreCase = true)) {
                        extraHeaders[key] = value
                    }
                }
            }
        }

        return ChannelLink(
            quality = "Default",
            url = url,
            cookie = cookie,
            referer = referer,
            origin = origin,
            userAgent = userAgent,
            xForwardedFor = xForwardedFor,
            drmScheme = drmScheme,
            drmLicenseUrl = drmLicenseUrl
        )
    }
}
