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

enum class NavTab(
    val label: StringResource,
    val icon: HearthIcon,
) {
    Household(Res.string.tab_household, HearthIcon.Household),
    Schedule(Res.string.tab_schedule, HearthIcon.Schedule),
    Chats(Res.string.tab_chats, HearthIcon.Chats),
    MySpace(Res.string.tab_my_space, HearthIcon.MySpace),
}

/** Four tabs around the raised + — the only way a chat is started. */
@Composable
fun HearthBottomNav(
    selected: NavTab,
    onSelect: (NavTab) -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    Column(modifier = modifier.fillMaxWidth().background(colors.surface)) {
        HorizontalDivider(thickness = HearthTheme.size.hairline, color = colors.outlineSoft)
        Row(
            modifier =
                Modifier.fillMaxWidth().padding(
                    start = HearthTheme.spacing.sm,
                    top = HearthTheme.spacing.md,
                    end = HearthTheme.spacing.sm,
                    bottom = HearthTheme.spacing.lg,
                ),
            verticalAlignment = Alignment.Bottom,
        ) {
            NavItem(NavTab.Household, selected, onSelect)
            NavItem(NavTab.Schedule, selected, onSelect)
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    modifier =
                        Modifier
                            .offset(y = -HearthTheme.spacing.xxl)
                            .size(HearthTheme.size.control)
                            .shadow(
                                elevation = HearthTheme.size.raised,
                                shape = CircleShape,
                                ambientColor = colors.primary.copy(alpha = 0.30f),
                                spotColor = colors.primary.copy(alpha = 0.30f),
                            ).clip(CircleShape)
                            .background(colors.primary)
                            .clickable(role = Role.Button, onClick = onNewChat),
                    contentAlignment = Alignment.Center,
                ) {
                    HearthIconImage(
                        icon = HearthIcon.NewChat,
                        contentDescription = stringResource(Res.string.new_chat),
                        active = true,
                        size = HearthTheme.size.iconLg,
                        tint = colors.onPrimary,
                    )
                }
            }
            NavItem(NavTab.Chats, selected, onSelect)
            NavItem(NavTab.MySpace, selected, onSelect)
        }
    }
}

@Composable
private fun RowScope.NavItem(
    tab: NavTab,
    selected: NavTab,
    onSelect: (NavTab) -> Unit,
) {
    val isSelected = tab == selected
    val tint = if (isSelected) HearthTheme.colors.primary else HearthTheme.colors.textMuted
    Column(
        modifier =
            Modifier
                .weight(1f)
                .selectable(selected = isSelected, role = Role.Tab) { onSelect(tab) }
                .heightIn(min = HearthTheme.size.touchTarget)
                .padding(vertical = HearthTheme.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xs, Alignment.Bottom),
    ) {
        HearthIconImage(
            icon = tab.icon,
            contentDescription = null,
            active = isSelected,
            size = HearthTheme.size.iconLg,
            tint = tint,
        )
        // Selection reads through colour, not weight: a label that also thickens shifts the
        // whole row by a hair as you move between tabs.
        Text(
            text = stringResource(tab.label),
            style = HearthTheme.typography.micro,
            color = tint,
        )
    }
}
