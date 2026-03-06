package com.livetvpro.app.ui.home

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.livetvpro.app.R
import com.livetvpro.app.SearchableFragment
import com.livetvpro.app.data.models.ListenerConfig
import com.livetvpro.app.databinding.FragmentHomeBinding
import com.livetvpro.app.ui.adapters.CategoryAdapter
import com.livetvpro.app.utils.RedirectHelper
import com.livetvpro.app.utils.NativeListenerManager
import com.livetvpro.app.utils.RedirectCooldownManager
import com.livetvpro.app.utils.RetryHandler
import com.livetvpro.app.utils.Refreshable
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment(), SearchableFragment, Refreshable {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()
    private lateinit var categoryAdapter: CategoryAdapter

    @Inject lateinit var listenerManager: NativeListenerManager
    @Inject lateinit var cooldownManager: RedirectCooldownManager

    private var pendingNavAction: (() -> Unit)? = null
    private var pendingExternalRedirect: Boolean = false

    private lateinit var redirectLauncher: ActivityResultLauncher<Intent>
    private var lastPageType: String? = null
    private var lastUniqueId: String? = null

    override fun onSearchQuery(query: String) { viewModel.searchCategories(query) }
    override fun refreshData() { viewModel.refresh() }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        val columnCount = resources.getInteger(R.integer.grid_column_count)
        (binding.recyclerViewCategories.layoutManager as? GridLayoutManager)?.spanCount = columnCount
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        redirectLauncher = RedirectHelper.registerLauncher(
            fragment = this,
            cooldownMgr = cooldownManager,
            pageTypeProvider = { lastPageType },
            uniqueIdProvider = { lastUniqueId }
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupRetryHandling()
        if (com.livetvpro.app.utils.DeviceUtils.isTvDevice) {
            binding.swipeRefresh.isEnabled = false
        }
    }

    override fun onResume() {
        super.onResume()
        RedirectHelper.executePendingActionOnResume(
            pendingActionProvider = { pendingNavAction },
            clearPendingAction = { pendingNavAction = null },
            pendingExternalRedirect = pendingExternalRedirect,
            clearPendingRedirect = { pendingExternalRedirect = false }
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val layoutManager = _binding?.recyclerViewCategories?.layoutManager as? GridLayoutManager
        layoutManager?.onSaveInstanceState()?.let { outState.putParcelable("rv_home_state", it) }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        savedInstanceState?.getParcelable<android.os.Parcelable>("rv_home_state")?.let {
            binding.recyclerViewCategories.layoutManager?.onRestoreInstanceState(it)
        }
    }

    private fun setupRecyclerView() {
        categoryAdapter = CategoryAdapter { category ->
            val bundle = bundleOf(
                "categoryId" to category.id,
                "categoryName" to category.name
            )
            lastPageType = ListenerConfig.PAGE_HOME
            lastUniqueId = category.id
            pendingNavAction = { findNavController().navigate(R.id.action_home_to_category, bundle) }

            val redirected = RedirectHelper.tryRedirect(
                fragment    = this@HomeFragment,
                pageType    = ListenerConfig.PAGE_HOME,
                uniqueId    = category.id,
                cooldownMgr = cooldownManager,
                listenerMgr = listenerManager,
                launcher    = redirectLauncher
            )
            if (redirected) {
                if (!listenerManager.isInAppRedirectEnabled()) {
                    pendingExternalRedirect = true
                } else {
                    pendingNavAction = null
                }
            } else {
                pendingNavAction?.invoke()
                pendingNavAction = null
            }
        }
        val columnCount = resources.getInteger(R.integer.grid_column_count)
        binding.recyclerViewCategories.apply {
            layoutManager = GridLayoutManager(context, columnCount)
            adapter = categoryAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupRetryHandling() {
        RetryHandler.setupGlobal(
            lifecycleOwner = viewLifecycleOwner,
            viewModel = viewModel,
            activity = requireActivity() as androidx.appcompat.app.AppCompatActivity,
            contentView = binding.swipeRefresh,
            swipeRefresh = binding.swipeRefresh,
            progressBar = binding.progressBar,
            emptyView = binding.emptyView
        )
        viewModel.filteredCategories.observe(viewLifecycleOwner) { categories ->
            categoryAdapter.submitList(categories)
            if (viewModel.isLoading.value != true && viewModel.error.value == null) {
                binding.emptyView.visibility = if (categories.isEmpty()) View.VISIBLE else View.GONE
                binding.recyclerViewCategories.visibility = if (categories.isEmpty()) View.GONE else View.VISIBLE
            }
            if (com.livetvpro.app.utils.DeviceUtils.isTvDevice && categories.isNotEmpty()) {
                binding.recyclerViewCategories.post {
                    binding.recyclerViewCategories
                        .findViewHolderForAdapterPosition(0)
                        ?.itemView
                        ?.requestFocus()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
