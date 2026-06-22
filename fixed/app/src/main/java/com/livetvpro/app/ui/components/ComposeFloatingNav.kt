package com.livetvpro.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.livetvpro.app.ui.main.NavTab

private val ContainerElevation = 8.dp
private val ItemSize           = 46.dp
private val IconSize           = 22.dp
private val IndicatorSize      = 38.dp

@Composable
fun ComposeFloatingNav(
    tabs: List<NavTab>,
    currentRoute: String?,
    primaryColor: Color,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .shadow(elevation = ContainerElevation, shape = CircleShape, clip = false)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            FloatingNavItem(
                tab          = tab,
                selected     = tab.route == currentRoute,
                primaryColor = primaryColor,
                onSurface    = MaterialTheme.colorScheme.onSurface,
                onClick      = { onTabSelected(tab.route) },
            )
        }
    }
}

@Composable
private fun FloatingNavItem(
    tab: NavTab,
    selected: Boolean,
    primaryColor: Color,
    onSurface: Color,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(ItemSize)
            .scale(scale)
            .clip(CircleShape)
            .background(if (selected) primaryColor else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = onClick,
            ),
    ) {
        Icon(
            painter            = painterResource(if (selected) tab.filledIcon else tab.outlineIcon),
            contentDescription = stringResource(tab.labelRes),
            tint               = if (selected) Color.White else onSurface.copy(alpha = 0.55f),
            modifier           = Modifier.size(IconSize),
        )
    }
}
