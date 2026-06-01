package com.livetvpro.app.ui.adapters

import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.livetvpro.app.R
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.data.models.Category
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.data.models.EventCategory
import com.livetvpro.app.data.models.EventStatus
import com.livetvpro.app.data.models.FavoriteChannel
import com.livetvpro.app.data.models.LiveEvent
import com.livetvpro.app.data.models.Playlist
import com.livetvpro.app.ui.player.ChannelListCache
import com.livetvpro.app.ui.player.PlayerActivity
import com.livetvpro.app.utils.DeviceUtils
import com.livetvpro.app.utils.FloatingPlayerHelper
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Composable
fun ChannelCard(
    channel: Channel,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .padding(4.dp)
            .fillMaxWidth()
            .clickable { onClick() },
        shape  = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp),
        ) {
            Box {
                @OptIn(ExperimentalGlideComposeApi::class)
                GlideImage(
                    model              = channel.logoUrl.ifEmpty { null },
                    contentDescription = channel.name,
                    contentScale       = ContentScale.Fit,
                    loading            = placeholder(R.mipmap.ic_launcher_round),
                    failure            = placeholder(R.mipmap.ic_launcher_round),
                    modifier           = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(6.dp)),
                )
                if (isFavorite) {
                    Icon(
                        painter            = painterResource(R.drawable.ic_star_filled),
                        contentDescription = null,
                        tint               = Color(0xFFFFD700),
                        modifier           = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(16.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text     = channel.name,
                style    = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun CategoryCard(
    category: Category,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .padding(4.dp)
            .fillMaxWidth()
            .clickable { onClick() },
        shape     = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp),
        ) {
            @OptIn(ExperimentalGlideComposeApi::class)
            GlideImage(
                model              = category.iconUrl?.ifEmpty { null },
                contentDescription = category.name,
                contentScale       = ContentScale.Fit,
                loading            = placeholder(R.mipmap.ic_launcher_round),
                failure            = placeholder(R.mipmap.ic_launcher_round),
                modifier           = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp)),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text     = category.name,
                style    = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun FavoriteCard(
    favorite: FavoriteChannel,
    preferencesManager: PreferencesManager,
    getLiveChannel: (String) -> Channel?,
    onChannelClick: (() -> Unit) -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Card(
        modifier = modifier
            .padding(4.dp)
            .fillMaxWidth()
            .clickable {
                val liveChannel = getLiveChannel(favorite.id)
                val channelToUse = liveChannel ?: Channel(
                    id           = favorite.id,
                    name         = favorite.name,
                    logoUrl      = favorite.logoUrl,
                    streamUrl    = favorite.streamUrl.ifEmpty { favorite.links?.firstOrNull()?.url ?: "" },
                    categoryId   = favorite.categoryId,
                    categoryName = favorite.categoryName,
                    links        = favorite.links,
                )
                val cacheKey = "favorites_${channelToUse.id}"
                val playerAction: () -> Unit = {
                    if (DeviceUtils.isTvDevice) {
                        PlayerActivity.startWithChannel(context, channelToUse, -1, channelListCacheKey = cacheKey)
                    } else {
                        val floatingEnabled = preferencesManager.isFloatingPlayerEnabled()
                        val hasPermission   = FloatingPlayerHelper.hasOverlayPermission(context)
                        if (floatingEnabled && hasPermission) {
                            try {
                                FloatingPlayerHelper.launchFloatingPlayer(context, channelToUse, -1, channelListCacheKey = cacheKey)
                            } catch (e: Exception) {
                                PlayerActivity.startWithChannel(context, channelToUse, -1, channelListCacheKey = cacheKey)
                            }
                        } else {
                            PlayerActivity.startWithChannel(context, channelToUse, -1, channelListCacheKey = cacheKey)
                        }
                    }
                }
                onChannelClick(playerAction)
            },
        shape     = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            @OptIn(ExperimentalGlideComposeApi::class)
            GlideImage(
                model              = favorite.logoUrl.ifEmpty { null },
                contentDescription = favorite.name,
                contentScale       = ContentScale.Fit,
                loading            = placeholder(R.mipmap.ic_launcher_round),
                failure            = placeholder(R.mipmap.ic_launcher_round),
                modifier           = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text     = favorite.name,
                style    = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onFavoriteToggle) {
                Icon(
                    painter            = painterResource(R.drawable.ic_star_filled),
                    contentDescription = "Remove from favorites",
                    tint               = Color(0xFFFFD700),
                    modifier           = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
fun PlaylistItem(
    playlist: Playlist,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        shape     = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter            = painterResource(if (playlist.isFile) R.drawable.ic_folder else R.drawable.ic_network_stream),
                contentDescription = null,
                modifier           = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = playlist.title,
                    style    = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = if (playlist.isFile) playlist.filePath else playlist.url
                if (subtitle.isNotEmpty()) {
                    Text(
                        text     = subtitle,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onEditClick) {
                Icon(
                    painter            = painterResource(R.drawable.ic_settings),
                    contentDescription = "Edit",
                    modifier           = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onDeleteClick) {
                Icon(
                    painter            = painterResource(R.drawable.ic_close),
                    contentDescription = "Delete",
                    tint               = MaterialTheme.colorScheme.error,
                    modifier           = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
fun EventCategoryChip(
    category: EventCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = isSelected,
        onClick  = onClick,
        label    = { Text(category.name) },
        modifier = modifier.padding(end = 4.dp),
    )
}

@Composable
fun LiveEventCard(
    event: LiveEvent,
    preferencesManager: PreferencesManager,
    onInteraction: (() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentTime = System.currentTimeMillis()
    val status = event.getStatus(currentTime)

    Card(
        modifier = modifier
            .padding(4.dp)
            .fillMaxWidth()
            .clickable {
                val playerAction: () -> Unit = {
                    if (event.links.size > 1) {
                        val linkLabels = event.links.map { it.quality }.toTypedArray()
                        android.app.AlertDialog.Builder(context)
                            .setTitle("Multiple Links Available")
                            .setItems(linkLabels) { d, which ->
                                PlayerActivity.startWithEvent(context, event, which)
                                d.dismiss()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    } else {
                        PlayerActivity.startWithEvent(context, event, 0)
                    }
                }
                onInteraction(playerAction)
            },
        shape     = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (status == EventStatus.LIVE) {
                    LiveIndicatorDot()
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text     = when (status) {
                        EventStatus.LIVE     -> "LIVE"
                        EventStatus.UPCOMING -> formatTime(event.startTime)
                        EventStatus.RECENT   -> "ENDED"
                    },
                    style    = MaterialTheme.typography.labelSmall,
                    color    = when (status) {
                        EventStatus.LIVE     -> Color.Red
                        EventStatus.UPCOMING -> MaterialTheme.colorScheme.primary
                        EventStatus.RECENT   -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier            = Modifier.weight(1f),
                ) {
                    @OptIn(ExperimentalGlideComposeApi::class)
                    GlideImage(
                        model              = event.team1Logo.ifEmpty { null },
                        contentDescription = event.team1Name,
                        loading            = placeholder(R.drawable.ic_placeholder_team),
                        failure            = placeholder(R.drawable.ic_placeholder_team),
                        modifier           = Modifier.size(36.dp),
                    )
                    Text(
                        text     = event.team1Name,
                        style    = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text  = "vs",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier            = Modifier.weight(1f),
                ) {
                    @OptIn(ExperimentalGlideComposeApi::class)
                    GlideImage(
                        model              = event.team2Logo.ifEmpty { null },
                        contentDescription = event.team2Name,
                        loading            = placeholder(R.drawable.ic_placeholder_team),
                        failure            = placeholder(R.drawable.ic_placeholder_team),
                        modifier           = Modifier.size(36.dp),
                    )
                    Text(
                        text     = event.team2Name,
                        style    = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (event.league.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text     = event.league,
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LiveIndicatorDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "live_dot")
    val alpha by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 0.2f,
        animationSpec = infiniteRepeatable(
            animation  = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(Color.Red.copy(alpha = alpha), CircleShape),
    )
}

@Composable
fun MarqueeBanner(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier  = modifier.clickable { onClick() },
        shape     = RoundedCornerShape(6.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Text(
            text     = text,
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

private fun formatTime(isoTime: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val display = SimpleDateFormat("hh:mm a", Locale.US)
        display.format(sdf.parse(isoTime)!!)
    } catch (e: Exception) {
        isoTime
    }
}
