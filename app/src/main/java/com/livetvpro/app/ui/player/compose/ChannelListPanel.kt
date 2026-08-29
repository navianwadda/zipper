package com.livetvpro.app.ui.player.compose

import android.widget.ImageView
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.text.input.ImeAction
import com.livetvpro.app.R
import com.livetvpro.app.utils.GlideExtensions
import com.livetvpro.app.data.models.Channel

private val RED            = Color(0xFFCC0000)
private val PANEL_BG       = Color(0x990A0A0A)
private val SIDEBAR_BG     = Color(0x99141414)
private val HEADER_BG      = Color(0xAA1A1A1A)
private val BergenSans     = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(R.font.bergen_sans)
)

data class ChannelGroup(val title: String, val channels: List<Channel>)

fun buildChannelGroups(channels: List<Channel>): List<ChannelGroup> {
    val result = mutableListOf<ChannelGroup>()
    result += ChannelGroup("All Channels", channels)
    channels.mapNotNull { it.groupTitle.takeIf { g -> g.isNotBlank() } }
            .distinct()
            .forEach { g -> result += ChannelGroup(g, channels.filter { it.groupTitle == g }) }
    return result
}

@Composable
fun ChannelListPanel(
    visible: Boolean,
    channels: List<Channel>,
    currentChannelId: String,
    onChannelClick: (Channel) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) +
                fadeIn(animationSpec = tween(300)),
        exit  = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(250)) +
                fadeOut(animationSpec = tween(250)),
        modifier = modifier
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isCompact = maxWidth < 500.dp
            val panelWidthFraction = if (isCompact) 0.92f else 0.60f
            val sidebarWidthFraction = if (isCompact) 0.34f else 0.28f

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(panelWidthFraction)
                    .align(Alignment.CenterEnd)
                    .background(PANEL_BG)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                val groups = remember(channels) { buildChannelGroups(channels) }
                var selectedGroupIndex by remember { mutableIntStateOf(0) }
                val selectedGroup = groups.getOrNull(selectedGroupIndex)
                val channelListState = rememberLazyListState()
                val focusManager = LocalFocusManager.current
                val closeFocusRequester = remember { FocusRequester() }
                val firstSidebarFocusRequester = remember { FocusRequester() }
                val searchFocusRequester = remember { FocusRequester() }
                var searchQuery by remember { mutableStateOf("") }
                val isSearching = searchQuery.isNotBlank()
                val keyboardController = LocalSoftwareKeyboardController.current
                val searchInteractionSource = remember { MutableInteractionSource() }
                val isSearchHovered by searchInteractionSource.collectIsHoveredAsState()
                val clearInteractionSource = remember { MutableInteractionSource() }

                val displayedChannels = remember(isSearching, searchQuery, selectedGroup, channels) {
                    if (isSearching) {
                        channels.filter { it.name.contains(searchQuery, ignoreCase = true) }
                    } else {
                        selectedGroup?.channels ?: emptyList()
                    }
                }

                LaunchedEffect(selectedGroupIndex) { channelListState.scrollToItem(0) }
                LaunchedEffect(visible, selectedGroupIndex, isSearching, searchQuery) {
                    if (visible && !isSearching) {
                        val idx = displayedChannels.indexOfFirst { it.id == currentChannelId }
                        if (idx >= 0) channelListState.animateScrollToItem(idx)
                    }
                }

                LaunchedEffect(visible) {
                    if (visible) {
                        runCatching { firstSidebarFocusRequester.requestFocus() }
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(HEADER_BG)
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var closeButtonFocused by remember { mutableStateOf(false) }
                        val closeInteractionSource = remember { MutableInteractionSource() }
                        val isCloseHovered by closeInteractionSource.collectIsHoveredAsState()
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    if (closeButtonFocused || isCloseHovered) Color(0xFF2DB233).copy(alpha = 0.7f) else Color(0xFF2DB233),
                                    RoundedCornerShape(50)
                                )
                                .focusRequester(closeFocusRequester)
                                .onFocusChanged { closeButtonFocused = it.isFocused }
                                .onKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyUp &&
                                        (event.key == Key.DirectionCenter || event.key == Key.Enter)
                                    ) {
                                        onDismiss()
                                        true
                                    } else if (event.type == KeyEventType.KeyDown &&
                                        event.key == Key.DirectionDown
                                    ) {
                                        runCatching { searchFocusRequester.requestFocus() }
                                        true
                                    } else false
                                }
                                .hoverable(interactionSource = closeInteractionSource)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable(
                                    interactionSource = closeInteractionSource,
                                    indication = null,
                                    onClick = onDismiss
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Channels List",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = BergenSans
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                    Row(modifier = Modifier.fillMaxSize()) {

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(sidebarWidthFraction)
                                .background(SIDEBAR_BG)
                        ) {
                            itemsIndexed(groups) { index, group ->
                                GroupSidebarItem(
                                    title = group.title,
                                    isSelected = !isSearching && index == selectedGroupIndex,
                                    channelCount = group.channels.size,
                                    isFirstItem = index == 0,
                                    firstItemFocusRequester = if (index == 0) firstSidebarFocusRequester else null,
                                    onClick = { searchQuery = ""; selectedGroupIndex = index },
                                    onFocusedMoveRight = { focusManager.moveFocus(FocusDirection.Right) },
                                    onFocusedMoveUp = { runCatching { searchFocusRequester.requestFocus() } }
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(Color.White.copy(alpha = 0.08f))
                        )

                        Column(modifier = Modifier.fillMaxSize()) {

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isSearchHovered) Color.White.copy(alpha = 0.05f) else HEADER_BG
                                    )
                                    .hoverable(interactionSource = searchInteractionSource)
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = "Search",
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontFamily = BergenSans
                                    ),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(
                                        onSearch = { keyboardController?.hide() }
                                    ),
                                    modifier = Modifier
                                        .pointerHoverIcon(PointerIcon.Text)
                                        .weight(1f)
                                        .focusRequester(searchFocusRequester)
                                        .onKeyEvent { event ->
                                            if (event.type == KeyEventType.KeyDown &&
                                                event.key == Key.DirectionDown
                                            ) {
                                                runCatching { firstSidebarFocusRequester.requestFocus() }
                                                true
                                            } else if (event.type == KeyEventType.KeyDown &&
                                                event.key == Key.DirectionUp
                                            ) {
                                                runCatching { closeFocusRequester.requestFocus() }
                                                true
                                            } else false
                                        },
                                    decorationBox = { innerTextField ->
                                        Box {
                                            if (searchQuery.isEmpty()) {
                                                Text(
                                                    text = "Search channels",
                                                    color = Color.White.copy(alpha = 0.4f),
                                                    fontSize = 13.sp,
                                                    fontFamily = BergenSans
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                )
                                if (isSearching) {
                                    val isClearHovered by clearInteractionSource.collectIsHoveredAsState()
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Clear search",
                                        tint = if (isClearHovered) Color.White else Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier
                                            .clickable(
                                                interactionSource = clearInteractionSource,
                                                indication = null,
                                                onClick = {
                                                    searchQuery = ""
                                                    keyboardController?.hide()
                                                }
                                            )
                                            .hoverable(interactionSource = clearInteractionSource)
                                            .pointerHoverIcon(PointerIcon.Hand)
                                            .padding(8.dp)
                                            .size(16.dp)
                                    )
                                }
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                            LazyColumn(
                                state = channelListState,
                                modifier = Modifier.fillMaxSize().background(PANEL_BG)
                            ) {
                                if (isSearching && displayedChannels.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "No channels found",
                                                color = Color.White.copy(alpha = 0.5f),
                                                fontSize = 13.sp,
                                                fontFamily = BergenSans
                                            )
                                        }
                                    }
                                }
                                itemsIndexed(displayedChannels) { index, channel ->
                                    ChannelItemRow(
                                        channel = channel,
                                        serialNumber = index + 1,
                                        isPlaying = channel.id == currentChannelId,
                                        onClick = { onChannelClick(channel); onDismiss() },
                                        onFocusedMoveLeft = { focusManager.moveFocus(FocusDirection.Left) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupSidebarItem(
    title: String,
    isSelected: Boolean,
    channelCount: Int,
    isFirstItem: Boolean = false,
    firstItemFocusRequester: FocusRequester? = null,
    onClick: () -> Unit,
    onFocusedMoveRight: () -> Unit = {},
    onFocusedMoveUp: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    var isFocused by remember { mutableStateOf(false) }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isHighlighted = isFocused || isHovered

    val baseModifier = Modifier
        .fillMaxWidth()
        .background(
            when {
                isSelected -> RED.copy(alpha = 0.85f)
                isHighlighted -> Color.White.copy(alpha = 0.08f)
                else -> Color.Transparent
            }
        )
        .then(
            if (firstItemFocusRequester != null)
                Modifier.focusRequester(firstItemFocusRequester)
            else
                Modifier
        )
        .onFocusChanged { isFocused = it.isFocused }
        .onKeyEvent { event ->
            when {
                event.type == KeyEventType.KeyUp &&
                (event.key == Key.DirectionCenter || event.key == Key.Enter) -> {
                    onClick(); true
                }
                event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight -> {
                    onFocusedMoveRight(); true
                }
                isFirstItem &&
                event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp -> {
                    onFocusedMoveUp(); true
                }
                else -> false
            }
        }
        .hoverable(interactionSource = interactionSource)
        .pointerHoverIcon(PointerIcon.Hand)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
        .padding(vertical = 10.dp, horizontal = 8.dp)

    Column(
        modifier = baseModifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            color = if (isSelected || isHighlighted) Color.White else Color.White.copy(alpha = 0.65f),
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontFamily = BergenSans,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = "$channelCount",
            color = if (isSelected) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.4f),
            fontSize = 10.sp,
            fontFamily = BergenSans
        )
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.5.dp)
}

@Composable
private fun ChannelItemRow(
    channel: Channel,
    serialNumber: Int,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onFocusedMoveLeft: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    var isFocused by remember { mutableStateOf(false) }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isHighlighted = isFocused || isHovered

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when {
                    isPlaying -> RED.copy(alpha = 0.85f)
                    isHighlighted -> Color.White.copy(alpha = 0.08f)
                    else -> Color.Transparent
                }
            )
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                when {
                    event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter) -> {
                        onClick(); true
                    }
                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft -> {
                        onFocusedMoveLeft(); true
                    }
                    else -> false
                }
            }
            .hoverable(interactionSource = interactionSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = serialNumber.toString(),
            color = if (isPlaying) Color.White else Color(0xFFB8E44A),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = BergenSans,
            modifier = Modifier.widthIn(min = 30.dp),
            textAlign = TextAlign.Start
        )
        Spacer(Modifier.width(6.dp))
        AndroidView(
            factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
            update = { iv ->
                val appIconRes = com.livetvpro.app.utils.AppIconUtils.currentLauncherRoundIcon(iv.context)
                GlideExtensions.loadImage(iv, channel.logoUrl.takeIf { it.isNotBlank() }, appIconRes, appIconRes, isCircular = true)
            },
            modifier = Modifier.size(32.dp).clip(CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = channel.name,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
            fontFamily = BergenSans,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (isPlaying) {
            Spacer(Modifier.width(4.dp))
            Box(modifier = Modifier.size(6.dp).background(Color.White, RoundedCornerShape(50)))
        }
    }
    HorizontalDivider(
        color = Color.White.copy(alpha = 0.05f),
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 48.dp)
    )
}
