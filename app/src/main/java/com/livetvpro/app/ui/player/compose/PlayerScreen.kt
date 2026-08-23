package com.livetvpro.app.ui.player.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
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
import com.livetvpro.app.ui.player.dialogs.LinkSelectionDialog
import com.livetvpro.app.ui.player.dialogs.toLinkItem

private val BergenSans = FontFamily(Font(R.font.bergen_sans))
private val CardLogoBg  = Color(0x80000000)

sealed class RelatedContentState {
    object Hidden  : RelatedContentState()
    object Loading : RelatedContentState()
    data class Channels(val items: List<Channel>)   : RelatedContentState()
    data class Events(val items: List<LiveEvent>)   : RelatedContentState()
}

sealed class NowPlayingState {
    object Hidden : NowPlayingState()
    data class ChannelInfo(
        val channel: Channel,
        val isFavorite: Boolean,
    ) : NowPlayingState()
    data class EventInfo(
        val title: String,
        val team1Name: String,
        val team1Logo: String,
        val team2Name: String,
        val team2Logo: String,
        val sourceName: String,
        val startTime: String = "",
        val endTime: String? = null,
        val isLive: Boolean = false,
    ) : NowPlayingState()
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
    onChannelClick: (Channel, Int) -> Unit,
    onEventClick: (LiveEvent, Int) -> Unit,
    onMessageBannerClick: () -> Unit,
    nowPlaying: NowPlayingState = NowPlayingState.Hidden,
    onToggleFavorite: (Channel) -> Unit = {},
    spanCount: Int = 3,
    eventSpanCount: Int = 2,
    modifier: Modifier = Modifier,
    linksRowState: LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
) {
    var linkDialogChannel by remember { mutableStateOf<Channel?>(null) }
    var linkDialogEvent by remember { mutableStateOf<LiveEvent?>(null) }

    val handleChannelClick: (Channel) -> Unit = { channel ->
        if ((channel.links?.size ?: 0) > 1) {
            linkDialogChannel = channel
        } else {
            onChannelClick(channel, 0)
        }
    }
    val handleEventClick: (LiveEvent) -> Unit = { event ->
        if (event.links.size > 1) {
            linkDialogEvent = event
        } else {
            onEventClick(event, 0)
        }
    }

    if (linkDialogChannel != null) {
        val channel = linkDialogChannel!!
        LinkSelectionDialog(
            links = channel.links.orEmpty().map { it.toLinkItem() },
            onLinkSelected = { _, index ->
                onChannelClick(channel, index)
                linkDialogChannel = null
            },
            onDismiss = { linkDialogChannel = null },
        )
    }
    if (linkDialogEvent != null) {
        val event = linkDialogEvent!!
        LinkSelectionDialog(
            links = event.links.map { it.toLinkItem() },
            onLinkSelected = { _, index ->
                onEventClick(event, index)
                linkDialogEvent = null
            },
            onDismiss = { linkDialogEvent = null },
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(
                if (isLandscape) Color.Transparent
                else MaterialTheme.colorScheme.surface
            )
    ) {
        if (links.size > 1) {
            LinksRow(
                links         = links,
                selectedIndex = selectedLinkIndex,
                onLinkClick   = onLinkClick,
                state         = linksRowState,
                background    = if (isLandscape) Color.Transparent
                                else MaterialTheme.colorScheme.surface,
            )
        }
        if (!isLandscape && messageBanner.isNotBlank()) {
            com.livetvpro.app.ui.components.MarqueeMessageBanner(
                text = messageBanner,
                onClick = onMessageBannerClick,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(
                    if (!isLandscape && nowPlaying !is NowPlayingState.Hidden)
                        Modifier
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(nowPlayingCardBackground())
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    else
                        Modifier.background(
                            if (isLandscape) Color.Transparent
                            else MaterialTheme.colorScheme.surface
                        )
                ),
        ) {
            if (!isLandscape) {
                NowPlayingSection(
                    nowPlaying = nowPlaying,
                    onToggleFavorite = onToggleFavorite,
                )
                if (nowPlaying !is NowPlayingState.Hidden) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (relatedContentState) {
                    is RelatedContentState.Hidden   -> Unit
                    is RelatedContentState.Loading  -> RelatedLoadingRow(modifier = Modifier.fillMaxSize())
                    is RelatedContentState.Channels -> RelatedChannelsGrid(
                        channels       = relatedContentState.items,
                        spanCount      = spanCount,
                        onChannelClick = handleChannelClick,
                        modifier       = Modifier.fillMaxSize(),
                    )
                    is RelatedContentState.Events   -> RelatedEventsGrid(
                        events       = relatedContentState.items,
                        spanCount    = eventSpanCount,
                        onEventClick = handleEventClick,
                        modifier     = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun LinksRow(
    links: List<LiveEventLink>,
    selectedIndex: Int,
    onLinkClick: (LiveEventLink, Int) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    background: Color = MaterialTheme.colorScheme.surface,
) {
    AnimatedVisibility(
        visible  = links.size > 1,
        enter    = fadeIn(),
        exit     = fadeOut(),
        modifier = modifier,
    ) {
        LazyRow(
            state                  = state,
            contentPadding      = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(background),
        ) {
            itemsIndexed(links) { index, link ->
                LinkChip(
                    label      = link.quality,
                    isSelected = index == selectedIndex,
                    onClick    = { onLinkClick(link, index) },
                )
            }
        }
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
        contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier              = modifier.fillMaxWidth(),
    ) {
        itemsIndexed(links) { index, link ->
            LinkChip(
                label      = link.quality,
                isSelected = index == selectedIndex,
                onClick    = { onLinkClick(link, index) },
            )
        }
    }
}

@Composable
private fun LinkChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
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
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        if (isSelected) {
            Icon(
                imageVector    = Icons.Default.Check,
                contentDescription = null,
                tint           = contentColor,
                modifier       = Modifier.size(14.dp),
            )
        }
        Text(
            text       = label,
            color      = contentColor,
            fontFamily = BergenSans,
            fontSize   = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines   = 1,
        )
    }
}

@Composable
private fun RelatedLoadingRow(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        CircularProgressIndicator(
            modifier    = Modifier.size(20.dp),
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
        columns               = GridCells.Fixed(spanCount),
        contentPadding        = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalArrangement   = Arrangement.spacedBy(0.dp),
        modifier = modifier.fillMaxSize(),
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
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue  = if (focused) 1.05f else 1f,
        animationSpec = tween(150),
        label        = "scale",
    )
    Card(
        onClick    = onClick,
        interactionSource = interactionSource,
        colors     = CardDefaults.cardColors(),
        shape      = RoundedCornerShape(12.dp),
        elevation  = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier   = Modifier
            .padding(4.dp)
            .fillMaxWidth()
            .wrapContentHeight()
            .scale(scale),
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(CardLogoBg),
                ) {
                    val appIconRes = com.livetvpro.app.utils.AppIconUtils.currentLauncherRoundIcon(
                        androidx.compose.ui.platform.LocalContext.current
                    )
                    GlideImage(
                        model              = channel.logoUrl.takeIf { it.isNotBlank() },
                        contentDescription = channel.name,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize(),
                    ) {
                        it.diskCacheStrategy(DiskCacheStrategy.ALL)
                            .placeholder(appIconRes)
                            .error(appIconRes)
                            .fallback(appIconRes)
                    }
                }
            }
            Text(
                text       = channel.name,
                fontFamily = BergenSans,
                fontWeight = FontWeight.Bold,
                fontSize   = 13.sp,
                color      = MaterialTheme.colorScheme.onSurface,
                textAlign  = TextAlign.Center,
                maxLines   = 1,
                overflow   = TextOverflow.Clip,
                modifier   = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .basicMarquee(iterations = Int.MAX_VALUE),
            )
        }
    }
}

@Composable
private fun NowPlayingSection(
    nowPlaying: NowPlayingState,
    onToggleFavorite: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (nowPlaying) {
        is NowPlayingState.Hidden -> Unit
        is NowPlayingState.ChannelInfo -> NowPlayingChannelCard(
            channel = nowPlaying.channel,
            isFavorite = nowPlaying.isFavorite,
            onToggleFavorite = { onToggleFavorite(nowPlaying.channel) },
            modifier = modifier,
        )
        is NowPlayingState.EventInfo -> NowPlayingEventCard(
            info = nowPlaying,
            modifier = modifier,
        )
    }
}

@Composable
private fun nowPlayingCardBackground(): Color {
    return MaterialTheme.colorScheme.surfaceVariant
        .copy(alpha = 0.35f)
        .compositeOver(MaterialTheme.colorScheme.background)
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun NowPlayingTeamLogo(
    url: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(CardLogoBg),
    ) {
        val appIconRes = com.livetvpro.app.utils.AppIconUtils.currentLauncherRoundIcon(
            androidx.compose.ui.platform.LocalContext.current
        )
        GlideImage(
            model = url.takeIf { it.isNotBlank() },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        ) {
            it.diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .override(96, 96)
                .placeholder(appIconRes)
                .error(appIconRes)
                .fallback(appIconRes)
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun NowPlayingChannelCard(
    channel: Channel,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = "NOW PLAYING",
            color = MaterialTheme.colorScheme.primary,
            fontFamily = BergenSans,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            NowPlayingTeamLogo(url = channel.logoUrl, modifier = Modifier.size(30.dp))
            Text(
                text = channel.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = BergenSans,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
                    .basicMarquee(iterations = Int.MAX_VALUE),
            )
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                Icon(
                    painter = painterResource(
                        if (isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
                    ),
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (isFavorite) MaterialTheme.colorScheme.tertiary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun NowPlayingEventCard(
    info: NowPlayingState.EventInfo,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = "NOW PLAYING",
            color = MaterialTheme.colorScheme.primary,
            fontFamily = BergenSans,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
        )
        if (info.title.isNotBlank()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = info.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = BergenSans,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                NowPlayingTeamLogo(url = info.team1Logo, modifier = Modifier.size(26.dp))
                Text(
                    text = info.team1Name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = BergenSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = "VS",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = BergenSans,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = info.team2Name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = BergenSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
                NowPlayingTeamLogo(url = info.team2Logo, modifier = Modifier.size(26.dp))
            }
            if (info.sourceName.isNotBlank()) {
                VerticalDivider(
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .height(34.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                )
                NowPlayingEventStatus(info = info)
            }
        }
    }
}

@Composable
private fun NowPlayingEventStatus(
    info: NowPlayingState.EventInfo,
    modifier: Modifier = Modifier,
) {
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(info.startTime, info.endTime, info.isLive) {
        while (true) {
            currentTime = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }

    val startMillis = remember(info.startTime) { parseEventTimestamp(info.startTime) }
    val endMillis = remember(info.endTime) { info.endTime?.let { parseEventTimestamp(it) } }

    val status = when {
        endMillis != null && currentTime in startMillis..endMillis -> EventStatus.LIVE
        info.isLive && currentTime >= startMillis -> EventStatus.LIVE
        startMillis > 0 && currentTime < startMillis -> EventStatus.UPCOMING
        endMillis != null && currentTime > endMillis -> EventStatus.ENDED
        startMillis > 0 && currentTime > startMillis -> EventStatus.ENDED
        else -> EventStatus.LIVE
    }

    val liveRed = Color(0xFFE53935)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Text(
            text = info.sourceName,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = BergenSans,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = when (status) {
                EventStatus.LIVE -> "Live Now"
                EventStatus.UPCOMING -> "Starts in"
                EventStatus.ENDED -> "Ended"
            },
            color = when (status) {
                EventStatus.LIVE -> MaterialTheme.colorScheme.primary
                EventStatus.UPCOMING -> MaterialTheme.colorScheme.primary
                EventStatus.ENDED -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontFamily = BergenSans,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
        )
        when (status) {
            EventStatus.LIVE -> if (startMillis > 0) {
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = formatElapsed(currentTime - startMillis),
                    color = liveRed,
                    fontFamily = BergenSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                )
            }
            EventStatus.UPCOMING -> {
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = formatCountdown(startMillis - currentTime),
                    color = liveRed,
                    fontFamily = BergenSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                )
            }
            EventStatus.ENDED -> Unit
        }
    }
}

private enum class EventStatus { LIVE, UPCOMING, ENDED }

private fun parseEventTimestamp(timeString: String): Long {
    if (timeString.isBlank()) return 0L
    return try {
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.parse(timeString)?.time ?: 0L
    } catch (e: Exception) {
        0L
    }
}

private fun formatElapsed(elapsedMs: Long): String {
    val elapsed = elapsedMs.coerceAtLeast(0L)
    val h = (elapsed / 1000 / 3600).toInt()
    val m = ((elapsed / 1000 / 60) % 60).toInt()
    val s = ((elapsed / 1000) % 60).toInt()
    return String.format("%02d:%02d:%02d", h, m, s)
}

private fun formatCountdown(remainingMs: Long): String {
    val diff = remainingMs.coerceAtLeast(0L)
    val days = (diff / (1000 * 60 * 60 * 24)).toInt()
    val hours = ((diff / (1000 * 60 * 60)) % 24).toInt()
    val minutes = ((diff / (1000 * 60)) % 60).toInt()
    val seconds = ((diff / 1000) % 60).toInt()
    return when {
        days > 0 -> String.format("%dd %02dh %02dm", days, hours, minutes)
        hours > 0 -> String.format("%02dh %02dm %02ds", hours, minutes, seconds)
        minutes > 0 -> String.format("%02dm %02ds", minutes, seconds)
        else -> String.format("%02ds", seconds)
    }
}

@Composable
private fun RelatedEventsGrid(
    events: List<LiveEvent>,
    spanCount: Int,
    onEventClick: (LiveEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (events.isEmpty()) return
    LazyVerticalGrid(
        columns        = GridCells.Fixed(spanCount),
        contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp),
        modifier       = modifier.fillMaxSize(),
    ) {
        items(events, key = { it.id }) { event ->
            com.livetvpro.app.ui.live.LiveEventCard(
                event   = event,
                onClick = { onEventClick(event) },
            )
        }
    }
}
