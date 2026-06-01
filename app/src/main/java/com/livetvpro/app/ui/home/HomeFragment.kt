package com.livetvpro.app.ui.home

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.livetvpro.app.R
import com.livetvpro.app.SearchableFragment
import com.livetvpro.app.data.models.Category
import com.livetvpro.app.data.models.ListenerConfig
import com.livetvpro.app.ui.adapters.CategoryCard
import com.livetvpro.app.utils.NativeListenerManager
import com.livetvpro.app.utils.RedirectCooldownManager
import com.livetvpro.app.utils.RedirectHelper
import com.livetvpro.app.utils.Refreshable
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment(), SearchableFragment, Refreshable {

    private val viewModel: HomeViewModel by viewModels()

    @Inject lateinit var listenerManager: NativeListenerManager
    @Inject lateinit var cooldownManager: RedirectCooldownManager

    private var pendingNavAction: (() -> Unit)? = null
    private var pendingExternalRedirect: Boolean = false
    private var lastPageType: String? = null
    private var lastUniqueId: String? = null

    private lateinit var redirectLauncher: ActivityResultLauncher<Intent>

    override fun onSearchQuery(query: String) { viewModel.searchCategories(query) }
    override fun refreshData() { viewModel.refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        redirectLauncher = RedirectHelper.registerLauncher(
            fragment         = this,
            cooldownMgr      = cooldownManager,
            pageTypeProvider = { lastPageType },
            uniqueIdProvider = { lastUniqueId }
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
                HomeScreen(
                    viewModel = viewModel,
                    onCategoryClick = { category -> handleCategoryClick(category) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        RedirectHelper.executePendingActionOnResume(
            pendingActionProvider  = { pendingNavAction },
            clearPendingAction     = { pendingNavAction = null },
            pendingExternalRedirect = pendingExternalRedirect,
            clearPendingRedirect   = { pendingExternalRedirect = false }
        )
    }

    private fun handleCategoryClick(category: Category) {
        val bundle = bundleOf("categoryId" to category.id, "categoryName" to category.name)
        lastPageType   = ListenerConfig.PAGE_HOME
        lastUniqueId   = category.id
        pendingNavAction = { findNavController().navigate(R.id.action_home_to_category, bundle) }

        val result = RedirectHelper.tryRedirect(
            fragment    = this,
            pageType    = ListenerConfig.PAGE_HOME,
            uniqueId    = category.id,
            cooldownMgr = cooldownManager,
            listenerMgr = listenerManager,
            launcher    = redirectLauncher
        )
        when (result) {
            RedirectHelper.RedirectResult.REDIRECTED -> {
                if (!listenerManager.isInAppRedirectEnabled()) pendingExternalRedirect = true
                else pendingNavAction = null
            }
            RedirectHelper.RedirectResult.NOT_REDIRECTED -> {
                pendingNavAction?.invoke()
                pendingNavAction = null
            }
            else -> pendingNavAction = null
        }
    }
}

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onCategoryClick: (Category) -> Unit,
) {
    val categories by viewModel.filteredCategories.observeAsState(emptyList())
    val isLoading  by viewModel.isLoading.observeAsState(false)

    val configuration = LocalConfiguration.current
    val columns = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 4 else 2

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            categories.isEmpty() -> {
                Text(
                    text     = "No categories available",
                    modifier = Modifier.align(Alignment.Center),
                    style    = MaterialTheme.typography.bodyLarge,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                LazyVerticalGrid(
                    columns          = GridCells.Fixed(columns),
                    modifier         = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .padding(bottom = 90.dp),
                ) {
                    items(categories) { category ->
                        CategoryCard(
                            category = category,
                            onClick  = { onCategoryClick(category) },
                        )
                    }
                }
            }
        }
    }
}
