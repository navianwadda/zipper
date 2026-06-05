package com.livetvpro.app.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.livetvpro.app.R
import com.livetvpro.app.data.models.Category

private val BergenSans = FontFamily(Font(R.font.bergen_sans))

private val CardLogoBg = Color(0x80000000)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    spanCount: Int = 3,
    isTvDevice: Boolean = false,
    onCategoryClick: (Category) -> Unit,
    onCategoryInteraction: ((Category, () -> Unit) -> Boolean)? = null
) {
    val filteredCategories by viewModel.filteredCategories.observeAsState(emptyList())
    val isLoading by viewModel.isLoading.observeAsState(false)
    val isEmpty by viewModel.isEmpty.observeAsState(false)
    val primaryColor by viewModel.primaryColorFlow.collectAsState()

    val gridState = rememberLazyGridState()
    val pullToRefreshState = rememberPullToRefreshState()

    if (isTvDevice) {
        LaunchedEffect(filteredCategories) {
            if (filteredCategories.isNotEmpty()) {
                gridState.scrollToItem(0)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = { if (!isTvDevice) viewModel.refresh() },
            state = pullToRefreshState,
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                isLoading && filteredCategories.isEmpty() -> {}

                isEmpty || filteredCategories.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No categories available",
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = BergenSans,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp
                        )
                    }
                }

                else -> {
                    val navBarBottom = WindowInsets.navigationBars
                        .asPaddingValues()
                        .calculateBottomPadding()

                    val bottomPadding = maxOf(
                        navBarBottom
                            + dimensionResource(R.dimen.nav_bottom_margin)
                            + dimensionResource(R.dimen.nav_height),
                        90.dp
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(spanCount),
                        state = gridState,
                        contentPadding = PaddingValues(
                            start = 8.dp,
                            end = 8.dp,
                            top = 8.dp,
                            bottom = bottomPadding
                        ),
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredCategories, key = { it.id }) { category ->
                            CategoryCard(
                                category = category,
                                primaryColor = Color(primaryColor),
                                onClick = {
                                    val navAction: () -> Unit = { onCategoryClick(category) }
                                    val redirected =
                                        onCategoryInteraction?.invoke(category, navAction) ?: false
                                    if (!redirected) navAction()
                                }
                            )
                        }
                    }
                }
            }
        }

        if (isLoading && filteredCategories.isEmpty()) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun CategoryCard(
    category: Category,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    var hasFocus by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (hasFocus) 1.05f else 1f,
        animationSpec = tween(120),
        label = "cardScale"
    )

    Card(
        modifier = Modifier
            .padding(4.dp)
            .fillMaxWidth()
            .wrapContentHeight()
            .scale(scale)
            .onFocusChanged { hasFocus = it.hasFocus }
            .focusable()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
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
                        model = category.iconUrl,
                        contentDescription = "Category icon",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        it.diskCacheStrategy(DiskCacheStrategy.ALL)
                            .error(R.mipmap.ic_launcher_round)
                            .fallback(R.mipmap.ic_launcher_round)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = dimensionResource(R.dimen.player_channel_logo_stroke_width),
                            color = primaryColor,
                            shape = CircleShape
                        )
                )
            }

            Text(
                text = category.name,
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
