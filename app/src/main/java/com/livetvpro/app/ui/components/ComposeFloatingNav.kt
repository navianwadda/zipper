package com.livetvpro.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livetvpro.app.ui.main.NavTab

private val ContainerElevation = 8.dp
private val ItemSize           = 46.dp
private val IconSize           = 22.dp
private val IndicatorSize      = 38.dp
private val ItemWidth          = 64.dp

@Composable
fun ComposeFloatingNav(
    tabs: List<NavTab>,
    currentDestId: Int,
    primaryColor: Color,
    onTabSelected: (Int) -> Unit,
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
                selected     = tab.destId == currentDestId,
                primaryColor = primaryColor,
                onSurface    = MaterialTheme.colorScheme.onSurface,
                onClick      = { onTabSelected(tab.destId) },
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

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .width(ItemWidth)
            .scale(scale)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = onClick,
            )
            .padding(vertical = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(ItemSize)
                .clip(CircleShape)
                .background(if (selected) primaryColor else Color.Transparent),
        ) {
            Icon(
                painter            = painterResource(if (selected) tab.filledIcon else tab.outlineIcon),
                contentDescription = stringResource(tab.labelRes),
                tint               = if (selected) Color.White else onSurface.copy(alpha = 0.55f),
                modifier           = Modifier.size(IconSize),
            )
        }
        Text(
            text      = stringResource(tab.labelRes),
            fontSize  = 10.sp,
            maxLines  = 1,
            textAlign = TextAlign.Center,
            color     = if (selected) primaryColor else onSurface.copy(alpha = 0.55f),
            modifier  = Modifier.width(ItemWidth),
        )
    }
}
