package com.homelab.household.app.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.testing.StillTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class HearthBottomNavTest {
    @Test
    fun shows_four_tabs_and_the_centre_plus() =
        runComposeUiTest {
            setContent { StillTheme { HearthBottomNav(NavTab.Household, onSelect = {}, onNewChat = {}) } }

            onHearthBottomNav {
                NavTab.entries.forEach { seesTab(it) }
                seesTheNewChatButton()
            }
        }

    @Test
    fun only_the_current_tab_is_selected() =
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
    fun tapping_a_tab_reports_it() =
        runComposeUiTest {
            var chosen: NavTab? = null
            setContent { StillTheme { HearthBottomNav(NavTab.Household, onSelect = { chosen = it }, onNewChat = {}) } }

            onHearthBottomNav { tapsTab(NavTab.MySpace) }

            assertEquals(NavTab.MySpace, chosen)
        }

    @Test
    fun the_plus_starts_a_new_chat() =
        runComposeUiTest {
            var newChats = 0
            setContent { StillTheme { HearthBottomNav(NavTab.Household, onSelect = {}, onNewChat = { newChats++ }) } }

            onHearthBottomNav { tapsTheNewChatButton() }

            assertEquals(1, newChats)
        }

    @Test
    fun every_target_is_at_least_44dp() =
        runComposeUiTest {
            setContent { StillTheme { HearthBottomNav(NavTab.Household, onSelect = {}, onNewChat = {}) } }

            onHearthBottomNav {
                NavTab.entries.forEach { seesTabBigEnoughToHit(it) }
                seesTheNewChatButtonBigEnoughToHit()
            }
        }
}
