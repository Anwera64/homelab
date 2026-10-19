package com.homelab.household.app.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.testing.StillTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class HearthBottomNavTest {
    @Test
    fun `GIVEN the bottom navigation WHEN it is shown THEN every tab and the new chat button are visible`() =
        runComposeUiTest {
            setContent { StillTheme { HearthBottomNav(NavTab.Household, onSelect = {}, onNewChat = {}) } }

            onHearthBottomNav {
                NavTab.entries.forEach { seesTab(it) }
                seesTheNewChatButton()
            }
        }

    @Test
    fun `GIVEN the bottom navigation on the chats tab WHEN it is shown THEN only the chats tab is selected`() =
        runComposeUiTest {
            setContent { StillTheme { HearthBottomNav(NavTab.Chats, onSelect = {}, onNewChat = {}) } }

            onHearthBottomNav {
                seesTabSelected(NavTab.Chats)
                NavTab.entries
                    .filterNot { it == NavTab.Chats }
                    .forEach { seesTabNotSelected(it) }
            }
        }

    @Test
    fun `GIVEN the bottom navigation on the household tab WHEN another tab is tapped THEN that tab is reported`() =
        runComposeUiTest {
            var chosen: NavTab? = null
            setContent { StillTheme { HearthBottomNav(NavTab.Household, onSelect = { chosen = it }, onNewChat = {}) } }

            onHearthBottomNav { tapsTab(NavTab.MySpace) }

            assertEquals(NavTab.MySpace, chosen)
        }

    @Test
    fun `GIVEN the bottom navigation WHEN the plus is tapped THEN a new chat is started`() =
        runComposeUiTest {
            var newChats = 0
            setContent { StillTheme { HearthBottomNav(NavTab.Household, onSelect = {}, onNewChat = { newChats++ }) } }

            onHearthBottomNav { tapsTheNewChatButton() }

            assertEquals(1, newChats)
        }

    @Test
    fun `GIVEN the bottom navigation WHEN it is shown THEN every tab and the new chat button are big enough to hit`() =
        runComposeUiTest {
            setContent { StillTheme { HearthBottomNav(NavTab.Household, onSelect = {}, onNewChat = {}) } }

            onHearthBottomNav {
                NavTab.entries.forEach { seesTabBigEnoughToHit(it) }
                seesTheNewChatButtonBigEnoughToHit()
            }
        }
}
