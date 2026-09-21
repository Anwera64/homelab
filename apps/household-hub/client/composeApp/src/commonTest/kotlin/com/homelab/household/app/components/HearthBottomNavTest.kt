package com.homelab.household.app.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.new_chat
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.theme.DefaultSizes
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class HearthBottomNavTest {

    private suspend fun labelOf(tab: NavTab) = getString(tab.label)

    @Test
    fun shows_four_tabs_and_the_centre_plus() = runComposeUiTest {
        setContent { StillTheme { HearthBottomNav(NavTab.Household, onSelect = {}, onNewChat = {}) } }

        NavTab.entries.forEach { onNodeWithText(labelOf(it)).assertIsDisplayed() }
        onNodeWithContentDescription(getString(Res.string.new_chat)).assertIsDisplayed()
    }

    @Test
    fun only_the_current_tab_is_selected() = runComposeUiTest {
        setContent { StillTheme { HearthBottomNav(NavTab.Chats, onSelect = {}, onNewChat = {}) } }

        onNodeWithText(labelOf(NavTab.Chats)).assertIsSelected()
        NavTab.entries.filterNot { it == NavTab.Chats }
            .forEach { onNodeWithText(labelOf(it)).assertIsNotSelected() }
    }

    @Test
    fun tapping_a_tab_reports_it() = runComposeUiTest {
        var chosen: NavTab? = null
        setContent { StillTheme { HearthBottomNav(NavTab.Household, onSelect = { chosen = it }, onNewChat = {}) } }

        onNodeWithText(labelOf(NavTab.MySpace)).performClick()

        assertEquals(NavTab.MySpace, chosen)
    }

    @Test
    fun the_plus_starts_a_new_chat() = runComposeUiTest {
        var newChats = 0
        setContent { StillTheme { HearthBottomNav(NavTab.Household, onSelect = {}, onNewChat = { newChats++ }) } }

        onNodeWithContentDescription(getString(Res.string.new_chat)).performClick()

        assertEquals(1, newChats)
    }

    @Test
    fun every_target_is_at_least_44dp() = runComposeUiTest {
        setContent { StillTheme { HearthBottomNav(NavTab.Household, onSelect = {}, onNewChat = {}) } }

        NavTab.entries.forEach {
            onNodeWithText(labelOf(it))
                .assertHeightIsAtLeast(DefaultSizes.touchTarget)
                .assertWidthIsAtLeast(DefaultSizes.touchTarget)
        }
        onNodeWithContentDescription(getString(Res.string.new_chat))
            .assertHeightIsAtLeast(DefaultSizes.touchTarget)
            .assertWidthIsAtLeast(DefaultSizes.touchTarget)
    }
}
