package com.homelab.household.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.new_chat
import com.homelab.household.app.resources.tab_chats
import com.homelab.household.app.resources.tab_household
import com.homelab.household.app.resources.tab_my_space
import com.homelab.household.app.resources.tab_schedule
import com.homelab.household.app.theme.HearthTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

enum class NavTab(val label: StringResource, val icon: HearthIcon) {
    Household(Res.string.tab_household, HearthIcon.Household),
    Schedule(Res.string.tab_schedule, HearthIcon.Schedule),
    Chats(Res.string.tab_chats, HearthIcon.Chats),
    MySpace(Res.string.tab_my_space, HearthIcon.MySpace)
}

/** Four tabs around the raised + — the only way a chat is started. */
@Composable
fun HearthBottomNav(
    selected: NavTab,
    onSelect: (NavTab) -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HearthTheme.colors
    Column(modifier = modifier.fillMaxWidth().background(colors.surface)) {
        HorizontalDivider(thickness = 1.dp, color = colors.outlineSoft)
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, top = 10.dp, end = 6.dp, bottom = 14.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            NavItem(NavTab.Household, selected, onSelect)
            NavItem(NavTab.Schedule, selected, onSelect)
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .offset(y = (-24).dp)
                        .size(52.dp)
                        .shadow(
                            elevation = 10.dp,
                            shape = CircleShape,
                            ambientColor = colors.primary.copy(alpha = 0.30f),
                            spotColor = colors.primary.copy(alpha = 0.30f)
                        )
                        .clip(CircleShape)
                        .background(colors.primary)
                        .clickable(role = Role.Button, onClick = onNewChat),
                    contentAlignment = Alignment.Center
                ) {
                    HearthIconImage(
                        icon = HearthIcon.NewChat,
                        contentDescription = stringResource(Res.string.new_chat),
                        active = true,
                        size = 24.dp,
                        tint = colors.onPrimary
                    )
                }
            }
            NavItem(NavTab.Chats, selected, onSelect)
            NavItem(NavTab.MySpace, selected, onSelect)
        }
    }
}

@Composable
private fun RowScope.NavItem(tab: NavTab, selected: NavTab, onSelect: (NavTab) -> Unit) {
    val isSelected = tab == selected
    val tint = if (isSelected) HearthTheme.colors.primary else HearthTheme.colors.textMuted
    Column(
        modifier = Modifier
            .weight(1f)
            .selectable(selected = isSelected, role = Role.Tab) { onSelect(tab) }
            .heightIn(min = 48.dp)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.Bottom)
    ) {
        HearthIconImage(icon = tab.icon, contentDescription = null, active = isSelected, size = 23.dp, tint = tint)
        Text(
            text = stringResource(tab.label),
            fontFamily = HearthTheme.fonts.inter,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = tint
        )
    }
}
