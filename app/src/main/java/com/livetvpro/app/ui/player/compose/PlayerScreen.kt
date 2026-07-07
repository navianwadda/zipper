package com.livetvpro.app.ui.player.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.livetvpro.app.R
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.data.models.LiveEvent
import com.livetvpro.app.data.models.LiveEventLink

private val BergenSans = FontFamily(Font(R.font.bergen_sans))
private val CardLogoBg  = Color(0x80000000)

sealed class RelatedContentState {
    object Hidden  : RelatedContentState()
    object Loading : RelatedContentState()
    data class Channels(val items: List<Channel>)   : RelatedContentState()
    data class Events(val items: List<LiveEvent>)   : RelatedContentState()
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
    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp.dp
    val screenWidthDp  = configuration.screenWidthDp.dp
    val availableHeight = if (isLandscape) 0.dp else {
        val videoHeight  = screenWidthDp * 9f / 16f
        val chromeHeight = (if (links.size > 1) 56.dp else 0.dp) +
            (if (messageBanner.isNotBlank()) 44.dp else 0.dp)
        (screenHeightDp - videoHeight - chromeHeight).coerceAtLeast(0.dp)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
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
                background    = if (isLandscape) Color.Transparent
                                else MaterialTheme.colorScheme.surface,
            )
        }
        if (!isLandscape && messageBanner.isNotBlank()) {
            MessageBanner(
                message = messageBanner,
                onClick = onMessageBannerClick,
            )
        }
        when (relatedContentState) {
            is RelatedContentState.Hidden   -> Unit
            is RelatedContentState.Loading  -> RelatedLoadingRow()
            is RelatedContentState.Channels -> RelatedChannelsGrid(
                channels    = relatedContentState.items,
                spanCount   = spanCount,
                onChannelClick = onChannelClick,
                background  = if (isLandscape) Color.Transparent
                              else MaterialTheme.colorScheme.surface,
                availableHeight = availableHeight,
            )
            is RelatedContentState.Events   -> RelatedEventsGrid(
                events      = relatedContentState.items,
                spanCount   = eventSpanCount,
                onEventClick = onEventClick,
                availableHeight = availableHeight,
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
    background: Color = MaterialTheme.colorScheme.surface,
) {
    AnimatedVisibility(
        visible  = links.size > 1,
        enter    = fadeIn(),
        exit     = fadeOut(),
        modifier = modifier,
    ) {
        LazyRow(
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
        verticalAlignment     = Alignment.CenterVertically,
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
private fun MessageBanner(
    message: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bannerShape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 6.dp)
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.16f), bannerShape)
            .border(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f), bannerShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(
            text     = message,
            modifier = Modifier
                .fillMaxWidth()
                .basicMarquee(iterations = Int.MAX_VALUE, velocity = 60.dp),
            style      = MaterialTheme.typography.bodyMedium,
            fontFamily = BergenSans,
            color      = MaterialTheme.colorScheme.onSurface,
            maxLines   = 1,
            overflow   = TextOverflow.Clip,
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
    background: Color = MaterialTheme.colorScheme.surface,
    availableHeight: Dp = 0.dp,
) {
    if (channels.isEmpty()) return
    val naturalCellHeight = 128.dp
    val rows = (channels.size + spanCount - 1) / spanCount
    val stretchedCellHeight = if (availableHeight > 0.dp && rows > 0) {
        val computed = availableHeight / rows
        if (computed > naturalCellHeight) computed else null
    } else null
    LazyVerticalGrid(
        columns               = GridCells.Fixed(spanCount),
        contentPadding        = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalArrangement   = Arrangement.spacedBy(0.dp),
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(background),
    ) {
        items(channels, key = { it.id }) { channel ->
            RelatedChannelCard(
                channel    = channel,
                onClick    = { onChannelClick(channel) },
                cellHeight = stretchedCellHeight,
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun RelatedChannelCard(
    channel: Channel,
    onClick: () -> Unit,
    cellHeight: Dp? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue  = if (focused) 1.05f else 1f,
        animationSpec = tween(150),
        label        = "scale",
    )
    Card(
        onClick    = onClick,
        colors     = CardDefaults.cardColors(),
        shape      = RoundedCornerShape(12.dp),
        elevation  = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier   = Modifier
            .padding(4.dp)
            .fillMaxWidth()
            .then(if (cellHeight != null) Modifier.height(cellHeight) else Modifier.wrapContentHeight())
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().fillMaxHeight().padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (cellHeight != null) Modifier.weight(1f) else Modifier.aspectRatio(1f))
                    .padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(CardLogoBg),
                ) {
                    GlideImage(
                        model              = channel.logoUrl.takeIf { it.isNotBlank() },
                        contentDescription = channel.name,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize(),
                    ) {
                        it.diskCacheStrategy(DiskCacheStrategy.ALL)
                            .placeholder(R.mipmap.ic_launcher_round)
                            .error(R.mipmap.ic_launcher_round)
                            .fallback(R.mipmap.ic_launcher_round)
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
private fun RelatedEventsGrid(
    events: List<LiveEvent>,
    spanCount: Int,
    onEventClick: (LiveEvent, Int) -> Unit,
    modifier: Modifier = Modifier,
    availableHeight: Dp = 0.dp,
) {
    if (events.isEmpty()) return
    val naturalCardHeight = 145.dp
    val rows = (events.size + spanCount - 1) / spanCount
    val stretchedCardHeight = if (availableHeight > 0.dp && rows > 0) {
        val computed = availableHeight / rows
        if (computed > naturalCardHeight) computed else null
    } else null
    LazyVerticalGrid(
        columns        = GridCells.Fixed(spanCount),
        contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp),
        modifier       = modifier.fillMaxWidth().wrapContentHeight(),
    ) {
        items(events, key = { it.id }) { event ->
            com.livetvpro.app.ui.live.LiveEventCard(
                event         = event,
                onClick       = { onEventClick(event, 0) },
                heightOverride = stretchedCardHeight,
            )
        }
    }
}

