package com.livetvpro.app.ui.player.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.livetvpro.app.R
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.data.models.LiveEvent
import com.livetvpro.app.data.models.LiveEventLink
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val BergenSans = FontFamily(Font(R.font.bergen_sans))

sealed class RelatedContentState {
    object Hidden : RelatedContentState()
    object Loading : RelatedContentState()
    data class Channels(val items: List<Channel>) : RelatedContentState()
    data class Events(val items: List<LiveEvent>) : RelatedContentState()
}

@Composable
fun PlayerScreen(
    isLandscape: Boolean,
    relatedContentState: RelatedContentState,
    links: List<LiveEventLink>,
    selectedLinkIndex: Int,
    messageBanner: String,
    messageBannerUrl: String,
    onLinkClick: (LiveEventLink, Int) -> Unit,
    onChannelClick: (Channel) -> Unit,
    onEventClick: (LiveEvent, Int) -> Unit,
    onMessageBannerClick: () -> Unit,
    spanCount: Int = 3,
    eventSpanCount: Int = 2,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        if (!isLandscape) {
            LinksRow(
                links = links,
                selectedIndex = selectedLinkIndex,
                onLinkClick = onLinkClick,
            )
        }
        if (!isLandscape && messageBanner.isNotBlank()) {
            MessageBanner(
                message = messageBanner,
                onClick = onMessageBannerClick,
            )
        }
        when (relatedContentState) {
            is RelatedContentState.Hidden -> Unit
            is RelatedContentState.Loading -> RelatedLoadingRow()
            is RelatedContentState.Channels -> RelatedChannelsGrid(
                channels = relatedContentState.items,
                spanCount = spanCount,
                onChannelClick = onChannelClick,
            )
            is RelatedContentState.Events -> RelatedEventsGrid(
                events = relatedContentState.items,
                spanCount = eventSpanCount,
                onEventClick = onEventClick,
            )
        }
    }
}

@Composable
private fun LinksRow(
    links: List<LiveEventLink>,
    selectedIndex: Int,
    onLinkClick: (LiveEventLink, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = links.size > 1,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            itemsIndexed(links) { index, link ->
                LinkChip(
                    label = link.quality,
                    isSelected = index == selectedIndex,
                    onClick = { onLinkClick(link, index) },
                )
            }
        }
    }
}

@Composable
private fun LinkChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        isSelected -> MaterialTheme.colorScheme.primary
        focused    -> MaterialTheme.colorScheme.primaryContainer
        else       -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        else       -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = label,
            color = contentColor,
            fontFamily = BergenSans,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}
@Composable
fun LandscapeLinksRow(
    links: List<LiveEventLink>,
    selectedIndex: Int,
    onLinkClick: (LiveEventLink, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (links.size <= 1) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        itemsIndexed(links) { index, link ->
            LinkChip(
                label = link.quality,
                isSelected = index == selectedIndex,
                onClick = { onLinkClick(link, index) },
            )
        }
    }
}

@Composable
private fun MessageBanner(
    message: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontFamily = BergenSans,
            fontSize = 16.sp,
            maxLines = 1,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 5.dp)
                .basicMarquee(),
        )
    }
}

@Composable
private fun RelatedLoadingRow(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
        )
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun RelatedChannelsGrid(
    channels: List<Channel>,
    spanCount: Int,
    onChannelClick: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (channels.isEmpty()) return
    LazyVerticalGrid(
        columns = GridCells.Fixed(spanCount),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
    ) {
        items(channels, key = { it.id }) { channel ->
            RelatedChannelCard(
                channel = channel,
                onClick = { onChannelClick(channel) },
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun RelatedChannelCard(
    channel: Channel,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (focused) 1.05f else 1f,
        animationSpec = tween(150),
        label = "scale",
    )
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color(0x80000000)),
            ) {
                GlideImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                ) {
                    it.diskCacheStrategy(DiskCacheStrategy.ALL)
                        .placeholder(R.mipmap.ic_launcher_round)
                        .error(R.mipmap.ic_launcher_round)
                }
            }
            Text(
                text = channel.name,
                fontFamily = BergenSans,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun RelatedEventsGrid(
    events: List<LiveEvent>,
    spanCount: Int,
    onEventClick: (LiveEvent, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (events.isEmpty()) return
    LazyVerticalGrid(
        columns = GridCells.Fixed(spanCount),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
    ) {
        items(events, key = { it.id }) { event ->
            RelatedEventCard(
                event = event,
                onClick = { onEventClick(event, 0) },
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun RelatedEventCard(
    event: LiveEvent,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (focused) 1.05f else 1f,
        animationSpec = tween(150),
        label = "scale",
    )
    val isLive = remember(event) {
        try {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
            val start = fmt.parse(event.startTime)?.time ?: 0L
            val end = if (event.endTime.isNotEmpty()) fmt.parse(event.endTime)?.time ?: Long.MAX_VALUE
                      else Long.MAX_VALUE
            System.currentTimeMillis() in start..end
        } catch (_: Exception) {
            event.isLive
        }
    }

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val teamNames = event.title.split(" vs ", ignoreCase = true)
                val team1 = teamNames.getOrNull(0)?.trim() ?: ""
                val team2 = teamNames.getOrNull(1)?.trim() ?: ""

                TeamLogoColumn(
                    logoUrl = event.team1Logo.ifEmpty { event.leagueLogo },
                    name = team1,
                )
                Text(
                    text = "vs",
                    fontFamily = BergenSans,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                TeamLogoColumn(
                    logoUrl = event.team2Logo.ifEmpty { event.leagueLogo },
                    name = team2,
                )
            }

            Spacer(Modifier.height(4.dp))
            if (isLive) {
                LivePulseDot()
            } else {
                EventTimeText(startTime = event.startTime)
            }
            Text(
                text = event.league,
                fontFamily = BergenSans,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun TeamLogoColumn(logoUrl: String, name: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(56.dp),
    ) {
        GlideImage(
            model = logoUrl,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape),
        ) {
            it.diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.mipmap.ic_launcher_round)
                .error(R.mipmap.ic_launcher_round)
        }
        Text(
            text = name,
            fontFamily = BergenSans,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LivePulseDot(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "alpha",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(0xFFE53935).copy(alpha = alpha)),
        )
        Text(
            text = "LIVE",
            fontFamily = BergenSans,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE53935),
        )
    }
}

@Composable
private fun EventTimeText(startTime: String, modifier: Modifier = Modifier) {
    val (timeStr, dateStr) = remember(startTime) {
        try {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
            val date = fmt.parse(startTime)
            val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date)
            val day = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date)
            time to day
        } catch (_: Exception) {
            startTime to ""
        }
    }
    Column(modifier = modifier) {
        Text(
            text = timeStr,
            fontFamily = BergenSans,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        if (dateStr.isNotBlank()) {
            Text(
                text = dateStr,
                fontFamily = BergenSans,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}
