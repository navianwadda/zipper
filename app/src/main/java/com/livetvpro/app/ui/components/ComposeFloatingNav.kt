package com.livetvpro.app.ui.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livetvpro.app.R
import com.livetvpro.app.ui.main.NavTab

private val BergenSans = FontFamily(Font(R.font.bergen_sans))

private val NavIconSize   = 24.dp
private val NavButtonSize = 48.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ComposeFloatingNav(
    tabs: List<NavTab>,
    currentDestId: Int,
    primaryColor: Color,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
   
   
    HorizontalFloatingToolbar(
        expanded  = true,
        modifier  = modifier,
        colors    = FloatingToolbarDefaults.standardFloatingToolbarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor   = MaterialTheme.colorScheme.onSurface,
        ),
       
        contentPadding        = FloatingToolbarDefaults.ContentPadding,
       
        shape                 = FloatingToolbarDefaults.ContainerShape,
       
        expandedShadowElevation   = FloatingToolbarDefaults.ContainerExpandedElevation,
        collapsedShadowElevation  = FloatingToolbarDefaults.ContainerCollapsedElevation,
        content = {
            tabs.forEach { tab ->
                val selected = tab.destId == currentDestId
                FloatingNavItem(
                    tab          = tab,
                    selected     = selected,
                    primaryColor = primaryColor,
                    onSurface    = MaterialTheme.colorScheme.onSurface,
                    onClick      = { onTabSelected(tab.destId) },
                )
            }
        },
    )
}

@Composable
private fun FloatingNavItem(
    tab: NavTab,
    selected: Boolean,
    primaryColor: Color,
    onSurface: Color,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    Box(contentAlignment = Alignment.Center) {
       
        IconButton(
            onClick           = onClick,
            modifier          = Modifier
                .size(NavButtonSize)
                .scale(if (focused && !selected) 1.08f else 1f)
                .onFocusChanged { focused = it.isFocused },
            interactionSource = remember { MutableInteractionSource() },
            colors            = IconButtonDefaults.iconButtonColors(
                containerColor         = if (selected) primaryColor.copy(alpha = 0.14f) else Color.Transparent,
                contentColor           = if (selected) primaryColor else onSurface.copy(alpha = 0.55f),
            ),
        ) {
            Icon(
                painter            = painterResource(if (selected) tab.filledIcon else tab.outlineIcon),
                contentDescription = stringResource(tab.labelRes),
                modifier           = Modifier.size(NavIconSize),
            )
        }
    }
}
