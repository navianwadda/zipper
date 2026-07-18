package com.livetvpro.app.ui.favorites

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.data.models.FavoriteChannel
import com.livetvpro.app.data.models.ListenerConfig
import com.livetvpro.app.ui.player.ChannelListCache
import com.livetvpro.app.ui.player.PlayerActivity
import com.livetvpro.app.utils.DeviceUtils
import com.livetvpro.app.utils.FloatingPlayerHelper
import com.livetvpro.app.utils.NativeListenerManager
import com.livetvpro.app.utils.RedirectCooldownManager
import com.livetvpro.app.utils.RedirectHelper

private const val FAVORITES_CACHE_KEY = "favorites_session"
private const val NUMPAD_RESET_MS = 2000L

@Composable
fun FavoritesRoute(
    listenerManager: NativeListenerManager,
    cooldownManager: RedirectCooldownManager,
    preferencesManager: PreferencesManager,
) {
    val context = LocalContext.current
    val viewModel: FavoritesViewModel = hiltViewModel()

    var lastPageType by remember { mutableStateOf<String?>(null) }
    var lastUniqueId by remember { mutableStateOf<String?>(null) }
    var pendingChannelAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingExternalRedirect by remember { mutableStateOf(false) }

    val redirectLauncher = RedirectHelper.rememberRedirectLauncher(
        cooldownMgr = cooldownManager,
        pageTypeProvider = { lastPageType },
        uniqueIdProvider = { lastUniqueId },
    )

    
    val favorites by viewModel.favorites.observeAsState()
    LaunchedEffect(favorites) {
        refreshFavoritesCache(viewModel, favorites)
        viewModel.onFavoritesChanged(favorites ?: emptyList())
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                RedirectHelper.executePendingActionOnResume(
                    pendingActionProvider = { pendingChannelAction },
                    clearPendingAction = { pendingChannelAction = null },
                    pendingExternalRedirect = pendingExternalRedirect,
                    clearPendingRedirect = { pendingExternalRedirect = false },
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun launchPlayer(channel: Channel, linkIndex: Int) {
        if (DeviceUtils.isTvDevice || DeviceUtils.isTablet) {
            PlayerActivity.startWithChannel(context, channel, linkIndex, channelListCacheKey = FAVORITES_CACHE_KEY)
            return
        }
        val floatingEnabled = preferencesManager.isFloatingPlayerEnabled()
        val hasPermission = FloatingPlayerHelper.hasOverlayPermission(context)

        if (floatingEnabled) {
            if (!hasPermission) {
                Toast.makeText(
                    context,
                    "Overlay permission required for floating player. Opening normally instead.",
                    Toast.LENGTH_LONG
                ).show()
                PlayerActivity.startWithChannel(context, channel, linkIndex, channelListCacheKey = FAVORITES_CACHE_KEY)
                return
            }
            try {
                FloatingPlayerHelper.launchFloatingPlayer(context, channel, linkIndex, channelListCacheKey = FAVORITES_CACHE_KEY)
            } catch (e: Exception) {
                PlayerActivity.startWithChannel(context, channel, linkIndex, channelListCacheKey = FAVORITES_CACHE_KEY)
            }
        } else {
            PlayerActivity.startWithChannel(context, channel, linkIndex, channelListCacheKey = FAVORITES_CACHE_KEY)
        }
    }

    fun handleChannelClick(favorite: FavoriteChannel, linkIndex: Int) {
        val liveChannel = viewModel.getLiveChannel(favorite.id)
        val channelToUse = liveChannel ?: run {
            val resolvedStreamUrl = favorite.streamUrl.ifEmpty {
                favorite.links?.firstOrNull()?.url ?: ""
            }
            Channel(
                id = favorite.id,
                name = favorite.name,
                logoUrl = favorite.logoUrl,
                streamUrl = resolvedStreamUrl,
                categoryId = favorite.categoryId,
                categoryName = favorite.categoryName,
                links = favorite.links
            )
        }

        if (channelToUse.streamUrl.isEmpty() && channelToUse.links.isNullOrEmpty()) {
            Toast.makeText(context, "No stream available for ${favorite.name}", Toast.LENGTH_SHORT).show()
            return
        }

        val playerAction: () -> Unit = { launchPlayer(channelToUse, linkIndex) }

        lastPageType = ListenerConfig.PAGE_FAVORITES
        lastUniqueId = favorite.id
        pendingChannelAction = playerAction

        val result = RedirectHelper.tryRedirect(
            context     = context,
            pageType    = ListenerConfig.PAGE_FAVORITES,
            uniqueId    = favorite.id,
            cooldownMgr = cooldownManager,
            listenerMgr = listenerManager,
            launcher    = redirectLauncher
        )
        if (result == RedirectHelper.RedirectResult.REDIRECTED) {
            if (!listenerManager.isInAppRedirectEnabled()) {
                pendingExternalRedirect = true
            } else {
                pendingChannelAction = null
            }
        } else {
            pendingChannelAction = null
            playerAction()
        }
    }

    val configuration = LocalConfiguration.current
    val spanCount = remember(configuration) {
        context.resources.getInteger(com.livetvpro.app.R.integer.grid_column_count)
    }
    val isTvOrTablet = DeviceUtils.isBigScreenLayout || DeviceUtils.isTablet

    
    var numpadBuffer by remember { mutableStateOf("") }
    val numpadHandler = remember { Handler(Looper.getMainLooper()) }
    val numpadResetRunnable = remember {
        Runnable {
            numpadBuffer = ""
            viewModel.searchFavorites("")
        }
    }
    DisposableEffect(Unit) {
        onDispose { numpadHandler.removeCallbacksAndMessages(null) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (isTvOrTablet) {
                    Modifier
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                            val digit = when (keyEvent.key) {
                                Key.Zero, Key.NumPad0 -> "0"
                                Key.One, Key.NumPad1 -> "1"
                                Key.Two, Key.NumPad2 -> "2"
                                Key.Three, Key.NumPad3 -> "3"
                                Key.Four, Key.NumPad4 -> "4"
                                Key.Five, Key.NumPad5 -> "5"
                                Key.Six, Key.NumPad6 -> "6"
                                Key.Seven, Key.NumPad7 -> "7"
                                Key.Eight, Key.NumPad8 -> "8"
                                Key.Nine, Key.NumPad9 -> "9"
                                else -> null
                            }
                            if (digit != null) {
                                numpadBuffer += digit
                                numpadHandler.removeCallbacksAndMessages(null)
                                numpadHandler.postDelayed(numpadResetRunnable, NUMPAD_RESET_MS)
                                viewModel.searchFavorites(numpadBuffer)
                                return@onKeyEvent true
                            }
                            if (keyEvent.key == Key.Backspace && numpadBuffer.isNotEmpty()) {
                                numpadBuffer = numpadBuffer.dropLast(1)
                                numpadHandler.removeCallbacksAndMessages(null)
                                if (numpadBuffer.isEmpty()) {
                                    viewModel.searchFavorites("")
                                } else {
                                    viewModel.searchFavorites(numpadBuffer)
                                    numpadHandler.postDelayed(numpadResetRunnable, NUMPAD_RESET_MS)
                                }
                                return@onKeyEvent true
                            }
                            false
                        }
                } else Modifier
            )
    ) {
        FavoritesScreen(
            viewModel = viewModel,
            spanCount = spanCount,
            isTvDevice = isTvOrTablet,
            onChannelClick = { favorite, linkIndex -> handleChannelClick(favorite, linkIndex) },
            onRemoveFavorite = { favorite -> viewModel.removeFavorite(favorite.id) },
            onClearAll = { viewModel.clearAll() }
        )
    }
}

private fun refreshFavoritesCache(viewModel: FavoritesViewModel, favorites: List<FavoriteChannel>?) {
    val list = try {
        favorites?.map { fav ->
            val live = viewModel.getLiveChannel(fav.id)
            live ?: Channel(
                id = fav.id,
                name = fav.name,
                logoUrl = fav.logoUrl,
                streamUrl = fav.streamUrl.ifEmpty { fav.links?.firstOrNull()?.url ?: "" },
                categoryId = fav.categoryId,
                categoryName = fav.categoryName,
                links = fav.links
            )
        } ?: emptyList()
    } catch (e: OutOfMemoryError) {
        System.gc()
        emptyList()
    }
    if (list.isNotEmpty()) {
        ChannelListCache.put(FAVORITES_CACHE_KEY, list)
    }
}
