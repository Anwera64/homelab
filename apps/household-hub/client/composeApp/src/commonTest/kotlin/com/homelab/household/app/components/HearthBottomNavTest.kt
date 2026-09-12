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
import androidx.compose.ui.unit.dp
import com.homelab.household.app.theme.HearthTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class HearthBottomNavTest {

    private val labels = listOf("Household", "Schedule", "Chats", "My Space")

    @Test
    fun shows_four_tabs_and_the_centre_plus() = runComposeUiTest {
        setContent { HearthTheme(darkTheme = false) { HearthBottomNav(NavTab.Household, onSelect = {}, onNewChat = {}) } }

        labels.forEach { onNodeWithText(it).assertIsDisplayed() }
        onNodeWithContentDescription("New chat").assertIsDisplayed()
    }

    @Test
    fun only_the_current_tab_is_selected() = runComposeUiTest {
        setContent { HearthTheme(darkTheme = false) { HearthBottomNav(NavTab.Chats, onSelect = {}, onNewChat = {}) } }

        onNodeWithText("Chats").assertIsSelected()
        listOf("Household", "Schedule", "My Space").forEach { onNodeWithText(it).assertIsNotSelected() }
    }

    @Test
    fun tapping_a_tab_reports_it() = runComposeUiTest {
        var chosen: NavTab? = null
        setContent { HearthTheme(darkTheme = false) { HearthBottomNav(NavTab.Household, onSelect = { chosen = it }, onNewChat = {}) } }

        onNodeWithText("My Space").performClick()

        assertEquals(NavTab.MySpace, chosen)
    }

    @Test
    fun the_plus_starts_a_new_chat() = runComposeUiTest {
        var newChats = 0
        setContent { HearthTheme(darkTheme = false) { HearthBottomNav(NavTab.Household, onSelect = {}, onNewChat = { newChats++ }) } }

        onNodeWithContentDescription("New chat").performClick()

        assertEquals(1, newChats)
    }

    @Test
    fun every_target_is_at_least_44dp() = runComposeUiTest {
        setContent { HearthTheme(darkTheme = false) { HearthBottomNav(NavTab.Household, onSelect = {}, onNewChat = {}) } }

        labels.forEach { onNodeWithText(it).assertHeightIsAtLeast(44.dp).assertWidthIsAtLeast(44.dp) }
        onNodeWithContentDescription("New chat").assertHeightIsAtLeast(44.dp).assertWidthIsAtLeast(44.dp)
    }
}
