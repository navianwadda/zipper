package com.livetvpro.app.ui.home

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.livetvpro.app.R
import com.livetvpro.app.SearchableFragment
import com.livetvpro.app.data.local.ThemeManager
import com.livetvpro.app.data.models.ListenerConfig
import com.livetvpro.app.ui.theme.AppThemeContent
import com.livetvpro.app.utils.DeviceUtils
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
    @Inject lateinit var themeManager: ThemeManager

    private lateinit var redirectLauncher: ActivityResultLauncher<Intent>
    private var lastPageType: String? = null
    private var lastUniqueId: String? = null
    private var pendingNavAction: (() -> Unit)? = null
    private var pendingExternalRedirect: Boolean = false

    override fun refreshData() {
        viewModel.refresh()
    }

    override fun onSearchQuery(query: String) {
        viewModel.searchCategories(query)
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val configuration = LocalConfiguration.current
            val context = LocalContext.current
            val spanCount = remember(configuration) {
                context.resources.getInteger(R.integer.grid_column_count)
            }
            AppThemeContent(themeManager) {
                HomeScreen(
                    viewModel = viewModel,
                    spanCount = spanCount,
                    isTvDevice = DeviceUtils.isTvDevice || DeviceUtils.isTablet,
                    onCategoryClick = { category ->
                        val bundle = bundleOf(
                            "categoryId" to category.id,
                            "categoryName" to category.name
                        )
                        findNavController().navigate(R.id.action_home_to_category, bundle)
                    },
                    onCategoryInteraction = { category, navAction ->
                        lastPageType = ListenerConfig.PAGE_HOME
                        lastUniqueId = category.id
                        pendingNavAction = navAction

                        val result = RedirectHelper.tryRedirect(
                            fragment    = this@HomeFragment,
                            pageType    = ListenerConfig.PAGE_HOME,
                            uniqueId    = category.id,
                            cooldownMgr = cooldownManager,
                            listenerMgr = listenerManager,
                            launcher    = redirectLauncher
                        )
                        if (result == RedirectHelper.RedirectResult.REDIRECTED) {
                            if (!listenerManager.isInAppRedirectEnabled()) {
                                pendingExternalRedirect = true
                            } else {
                                pendingNavAction = null
                            }
                        } else {
                            pendingNavAction = null
                        }
                        result == RedirectHelper.RedirectResult.REDIRECTED
                    }
                )
            }
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
}
