package com.livetvpro.app.ui.favorites

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.livetvpro.app.R
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.data.models.FavoriteChannel
import com.livetvpro.app.data.models.ListenerConfig
import com.livetvpro.app.databinding.FragmentFavoritesBinding
import com.livetvpro.app.ui.adapters.FavoriteAdapter
import com.livetvpro.app.utils.DeviceUtils
import com.livetvpro.app.utils.NativeListenerManager
import com.livetvpro.app.utils.RedirectCooldownManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class FavoritesFragment : Fragment() {
    private var _binding: FragmentFavoritesBinding? = null
    private var pendingChannelAction: (() -> Unit)? = null
    private val binding get() = _binding!!
    private val viewModel: FavoritesViewModel by viewModels()
    private lateinit var favoriteAdapter: FavoriteAdapter

    @Inject lateinit var preferencesManager: PreferencesManager
    @Inject lateinit var listenerManager: NativeListenerManager
    @Inject lateinit var cooldownManager: RedirectCooldownManager

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        val columnCount = resources.getInteger(R.integer.grid_column_count)
        (binding.recyclerViewFavorites.layoutManager as? GridLayoutManager)?.spanCount = columnCount
    }

    override fun onResume() {
        super.onResume()
        pendingChannelAction?.invoke()
        pendingChannelAction = null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupButtons()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        favoriteAdapter = FavoriteAdapter(
            preferencesManager = preferencesManager,
            onChannelClick = { favorite, playerAction ->
                pendingChannelAction = playerAction
                val redirected = cooldownManager.tryFire(ListenerConfig.PAGE_FAVORITES, favorite.id) {
                    listenerManager.onPageInteraction(
                        pageType = ListenerConfig.PAGE_FAVORITES,
                        uniqueId = favorite.id
                    )
                }
                if (!redirected) {
                    pendingChannelAction?.invoke()
                    pendingChannelAction = null
                }
                redirected
            },
            onFavoriteToggle = { favChannel ->
                showRemoveConfirmation(favChannel)
            },
            getLiveChannel = { channelId ->
                viewModel.getLiveChannel(channelId)
            }
        )
        val columnCount = resources.getInteger(R.integer.grid_column_count)
        binding.recyclerViewFavorites.apply {
            layoutManager = GridLayoutManager(context, columnCount)
            adapter = favoriteAdapter
            itemAnimator = null
        }

        if (DeviceUtils.isTvDevice) {
            binding.recyclerViewFavorites.setOnKeyListener { _, keyCode, event ->
                if (event.action != android.view.KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                if (keyCode == android.view.KeyEvent.KEYCODE_MENU ||
                    keyCode == android.view.KeyEvent.KEYCODE_BUTTON_Y) {
                    val focused = binding.recyclerViewFavorites.focusedChild
                    val holder = binding.recyclerViewFavorites.findContainingViewHolder(focused ?: return@setOnKeyListener false)
                    val pos = holder?.adapterPosition ?: return@setOnKeyListener false
                    val item = favoriteAdapter.currentList.getOrNull(pos) ?: return@setOnKeyListener false
                    showRemoveConfirmation(item)
                    return@setOnKeyListener true
                }
                false
            }
        }
    }

    private fun setupButtons() {
        binding.clearAllButton.setOnClickListener {
            showClearAllDialog()
        }
        if (DeviceUtils.isTvDevice) {
            binding.clearAllButton.setOnKeyListener { _, keyCode, event ->
                if (event.action != android.view.KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                    android.view.KeyEvent.KEYCODE_ENTER,
                    android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        showClearAllDialog()
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun showRemoveConfirmation(favorite: FavoriteChannel) {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Remove Favorite")
            .setMessage("Remove '${favorite.name}' from your favorites list?")
            .setPositiveButton("Remove") { _, _ ->
                viewModel.removeFavorite(favorite.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.requestFocus()
    }

    private fun showClearAllDialog() {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Clear All Favorites")
            .setMessage("This will remove all channels from your list.")
            .setPositiveButton("Clear All") { _, _ ->
                viewModel.clearAll()
            }
            .setNegativeButton("Cancel", null)
            .show()
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.requestFocus()
    }

    private fun observeViewModel() {
        viewModel.favorites.observe(viewLifecycleOwner) { favorites ->
            favoriteAdapter.submitList(favorites)
            val isEmpty = favorites.isEmpty()
            binding.emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.recyclerViewFavorites.visibility = if (isEmpty) View.GONE else View.VISIBLE
            binding.clearAllButton.visibility = if (isEmpty) View.GONE else View.VISIBLE
            if (DeviceUtils.isTvDevice && favorites.isNotEmpty()) {
                binding.recyclerViewFavorites.post {
                    binding.recyclerViewFavorites
                        .findViewHolderForAdapterPosition(0)
                        ?.itemView
                        ?.requestFocus()
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val layoutManager = _binding?.recyclerViewFavorites?.layoutManager as? GridLayoutManager
        layoutManager?.onSaveInstanceState()?.let { outState.putParcelable("rv_fav_state", it) }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        savedInstanceState?.getParcelable<android.os.Parcelable>("rv_fav_state")?.let {
            binding.recyclerViewFavorites.layoutManager?.onRestoreInstanceState(it)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
