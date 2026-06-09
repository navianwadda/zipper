package com.livetvpro.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@Composable
fun ComposeFloatingNav(
    tabs: List<NavTab>,
    currentDestId: Int,
    primaryColor: Color,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .height(60.dp)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            val selected = tab.destId == currentDestId
            FloatingNavItem(
                tab          = tab,
                selected     = selected,
                primaryColor = primaryColor,
                onSurface    = MaterialTheme.colorScheme.onSurface,
                onClick      = { onTabSelected(tab.destId) },
                modifier     = Modifier.weight(1f),
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
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale  by animateFloatAsState(if (focused && !selected) 1.08f else 1f, tween(120), label = "nav")
    val pillW  by animateDpAsState(if (selected) 40.dp else 0.dp, tween(200), label = "pill")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .scale(scale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = onClick,
            )
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (selected && pillW > 0.dp) {
                Box(
                    modifier = Modifier
                        .size(width = pillW, height = 28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(primaryColor.copy(alpha = 0.14f))
                )
            }
            Icon(
                painter            = painterResource(if (selected) tab.filledIcon else tab.outlineIcon),
                contentDescription = stringResource(tab.labelRes),
                tint               = if (selected) primaryColor else onSurface.copy(alpha = 0.55f),
                modifier           = Modifier.size(22.dp),
            )
        }
        Text(
            text       = stringResource(tab.labelRes),
            color      = if (selected) primaryColor else onSurface.copy(alpha = 0.55f),
            fontSize   = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontFamily = BergenSans,
            maxLines   = 1,
        )
    }
}
