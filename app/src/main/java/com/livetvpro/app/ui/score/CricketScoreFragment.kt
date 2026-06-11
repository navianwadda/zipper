package com.livetvpro.app.ui.score

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.livetvpro.app.data.local.ThemeManager
import com.livetvpro.app.ui.theme.AppThemeContent
import com.livetvpro.app.utils.NativeListenerManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CricketScoreFragment : Fragment() {

    @Inject lateinit var listenerManager: NativeListenerManager
    @Inject lateinit var themeManager: ThemeManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val url = listenerManager.getCricLiveUrl()
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppThemeContent(themeManager) {
                    ScoreWebScreen(url = url)
                }
            }
        }
    }
}
