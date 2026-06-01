package com.livetvpro.app.ui.favorites

import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.livetvpro.app.R
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.data.models.FavoriteChannel
import com.livetvpro.app.data.models.ListenerConfig
import com.livetvpro.app.ui.adapters.FavoriteCard
import com.livetvpro.app.utils.NativeListenerManager
import com.livetvpro.app.utils.RedirectCooldownManager
import com.livetvpro.app.utils.RedirectHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class FavoritesFragment : Fragment() {

    private val viewModel: FavoritesViewModel by viewModels()

    @Inject lateinit var preferencesManager: PreferencesManager
    @Inject lateinit var listenerManager: NativeListenerManager
    @Inject lateinit var cooldownManager: RedirectCooldownManager

    private var pendingChannelAction: (() -> Unit)? = null
    private var pendingExternalRedirect: Boolean = false
    private var lastPageType: String? = null
    private var lastUniqueId: String? = null

    private lateinit var redirectLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        redirectLauncher = RedirectHelper.registerLauncher(
            fragment         = this,
            cooldownMgr      = cooldownManager,
            pageTypeProvider = { lastPageType },
            uniqueIdProvider = { lastUniqueId }
        )
    }

    override fun onResume() {
        super.onResume()
        RedirectHelper.executePendingActionOnResume(
            pendingActionProvider   = { pendingChannelAction },
            clearPendingAction      = { pendingChannelAction = null },
            pendingExternalRedirect = pendingExternalRedirect,
            clearPendingRedirect    = { pendingExternalRedirect = false }
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            MaterialTheme {
                FavoritesScreen(
                    viewModel            = viewModel,
                    preferencesManager   = preferencesManager,
                    onChannelClick       = { favorite, playerAction -> handleChannelClick(favorite, playerAction) },
                    onFavoriteToggle     = { viewModel.removeFavorite(it.id) },
                    onClearAll           = { showClearAllDialog() },
                )
            }
        }
    }

    private fun handleChannelClick(favorite: FavoriteChannel, playerAction: () -> Unit) {
        pendingChannelAction = playerAction
        lastPageType = ListenerConfig.PAGE_FAVORITES
        lastUniqueId = favorite.id
        val result = RedirectHelper.tryRedirect(
            fragment    = this,
            pageType    = ListenerConfig.PAGE_FAVORITES,
            uniqueId    = favorite.id,
            cooldownMgr = cooldownManager,
            listenerMgr = listenerManager,
            launcher    = redirectLauncher
        )
        when (result) {
            RedirectHelper.RedirectResult.REDIRECTED -> {
                if (!listenerManager.isInAppRedirectEnabled()) pendingExternalRedirect = true
                else pendingChannelAction = null
            }
            else -> {
                pendingChannelAction?.invoke()
                pendingChannelAction = null
            }
        }
    }

    private fun showClearAllDialog() {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Clear All Favorites")
            .setMessage("This will remove all channels from your list.")
            .setPositiveButton("Clear All") { _, _ -> viewModel.clearAll() }
            .setNegativeButton("Cancel", null)
            .show()
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.requestFocus()
    }
}

@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    preferencesManager: PreferencesManager,
    onChannelClick: (FavoriteChannel, () -> Unit) -> Unit,
    onFavoriteToggle: (FavoriteChannel) -> Unit,
    onClearAll: () -> Unit,
) {
    val favorites by viewModel.favorites.observeAsState(emptyList())

    val configuration = LocalConfiguration.current
    val columns = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 4 else 2

    if (favorites.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text  = "No favorites available",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        ConstraintLayout(modifier = Modifier.fillMaxSize()) {
            val (grid, btn) = createRefs()

            LazyVerticalGrid(
                columns  = GridCells.Fixed(columns),
                modifier = Modifier
                    .constrainAs(grid) {
                        top.linkTo(parent.top)
                        bottom.linkTo(btn.top)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                        height = Dimension.fillToConstraints
                    }
                    .padding(4.dp),
            ) {
                items(favorites) { favorite ->
                    FavoriteCard(
                        favorite           = favorite,
                        preferencesManager = preferencesManager,
                        getLiveChannel     = { viewModel.getLiveChannel(it) },
                        onChannelClick     = { action -> onChannelClick(favorite, action) },
                        onFavoriteToggle   = { onFavoriteToggle(favorite) },
                    )
                }
            }

            Button(
                onClick  = onClearAll,
                modifier = Modifier
                    .constrainAs(btn) {
                        bottom.linkTo(parent.bottom, margin = 8.dp)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                    }
                    .padding(bottom = 8.dp),
            ) {
                Text(text = "Clear All")
            }
        }
    }
}
