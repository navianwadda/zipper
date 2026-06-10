package com.livetvpro.app.ui.favorites

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
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
import com.livetvpro.app.data.models.FavoriteChannel

private val BergenSans = FontFamily(Font(R.font.bergen_sans))
private val CardLogoBg = Color(0x80000000)

@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    spanCount: Int = 3,
    isTvDevice: Boolean = false,
    onChannelClick: (FavoriteChannel) -> Unit,
    onRemoveFavorite: (FavoriteChannel) -> Unit,
    onClearAll: () -> Unit
) {
    val favorites by viewModel.favorites.observeAsState(emptyList())

    var showClearAllDialog by remember { mutableStateOf(false) }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = {
                Text(
                    text = "Clear All Favorites",
                    fontFamily = BergenSans
                )
            },
            text = {
                Text(
                    text = "This will remove all channels from your list.",
                    fontFamily = BergenSans
                )
            },
            confirmButton = {
                Button(onClick = {
                    onClearAll()
                    showClearAllDialog = false
                }) {
                    Text("Clear All", fontFamily = BergenSans)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel", fontFamily = BergenSans)
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No favorites yet",
                    fontFamily = BergenSans,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val gridState = rememberLazyGridState()

            val navBarBottom = WindowInsets.navigationBars
                .asPaddingValues()
                .calculateBottomPadding()

            val bottomPadding = maxOf(
                navBarBottom
                    + dimensionResource(R.dimen.nav_bottom_margin)
                    + dimensionResource(R.dimen.nav_height)
                    + 52.dp,
                90.dp
            )

            Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(spanCount),
                    state = gridState,
                    contentPadding = PaddingValues(
                        start = 4.dp,
                        top = 4.dp,
                        end = 4.dp,
                        bottom = bottomPadding
                    ),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = favorites,
                        key = { it.id }
                    ) { favorite ->
                        FavoriteCard(
                            favorite = favorite,
                            isTvDevice = isTvDevice,
                            onClick = { onChannelClick(favorite) },
                            onRemove = { onRemoveFavorite(favorite) }
                        )
                    }
                }

                Button(
                    onClick = { showClearAllDialog = true },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = navBarBottom + dimensionResource(R.dimen.nav_bottom_margin) + dimensionResource(R.dimen.nav_height) + 4.dp)
                ) {
                    Text("Clear All", fontFamily = BergenSans)
                }
            }
        }
    }
}

@Composable
private fun FavoriteCard(
    favorite: FavoriteChannel,
    isTvDevice: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    var hasFocus by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (hasFocus) 1.05f else 1f,
        animationSpec = tween(120),
        label = "cardScale"
    )

    Card(
        onClick = onClick,
        modifier = Modifier
            .padding(4.dp)
            .fillMaxWidth()
            .wrapContentHeight()
            .scale(scale)
            .onFocusChanged { hasFocus = it.hasFocus }
            .focusable()
            .pointerInput(onRemove) {
                detectTapGestures(onLongPress = { onRemove() })
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(CardLogoBg)
                ) {
                    @OptIn(ExperimentalGlideComposeApi::class)
                    GlideImage(
                        model = favorite.logoUrl.takeIf { it.isNotBlank() },
                        contentDescription = "Channel logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        it.diskCacheStrategy(DiskCacheStrategy.ALL)
                            .placeholder(R.mipmap.ic_launcher_round)
                            .error(R.mipmap.ic_launcher_round)
                            .fallback(R.mipmap.ic_launcher_round)
                    }
                }

                if (!isTvDevice) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier
                            .size(28.dp)
                            .align(Alignment.TopEnd)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = "Remove from favorites",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Text(
                text = favorite.name,
                fontFamily = BergenSans,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .basicMarquee(iterations = Int.MAX_VALUE)
            )
        }
    }
}
