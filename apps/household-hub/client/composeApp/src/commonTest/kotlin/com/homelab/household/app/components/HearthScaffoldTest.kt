package com.homelab.household.app.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.homelab.household.app.testing.StillTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Screens scroll: the content region moves, header and navigation stay pinned (design notes §2).
 * The content is handed the space the bars and the window leave it, and scrolls however it likes.
 */
@OptIn(ExperimentalTestApi::class)
class HearthScaffoldTest {
    @Test
    fun header_and_bottom_bar_stay_while_the_content_scrolls() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    HearthScaffold(
                        header = { Text("HyggeHub · Household") },
                        bottomBar = { Text("Bottom bar") },
                    ) { padding ->
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(padding)) {
                            repeat(120) { Text("Row $it") }
                        }
                    }
                }
            }

            onNodeWithText("Row 119").performScrollTo().assertIsDisplayed()

            onNodeWithText("Row 0").assertIsNotDisplayed()
            onNodeWithText("HyggeHub · Household").assertIsDisplayed()
            onNodeWithText("Bottom bar").assertIsDisplayed()
        }

    @Test
    fun a_lazy_list_scrolls_inside() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    HearthScaffold(header = { Text("Chats") }) { padding ->
                        LazyColumn(modifier = Modifier.testTag("list"), contentPadding = padding) {
                            items(500) { Text("Chat $it") }
                        }
                    }
                }
            }

            onNodeWithTag("list").performScrollToIndex(499)

            onNodeWithText("Chat 499").assertIsDisplayed()
            onNodeWithText("Chats").assertIsDisplayed()
        }

    @Test
    fun the_content_starts_under_the_header_and_ends_above_the_bottom_bar() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    HearthScaffold(
                        header = { Box(Modifier.fillMaxWidth().height(BAR)) },
                        bottomBar = { Box(Modifier.fillMaxWidth().height(BAR)) },
                        contentWindowInsets = WindowInsets(0.dp),
                    ) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding).testTag("content"))
                    }
                }
            }

            val root = onRoot().getBoundsInRoot()
            val content = onNodeWithTag("content").getBoundsInRoot()

            assertEquals(BAR, content.top)
            assertEquals(root.bottom - BAR, content.bottom)
        }

    @Test
    fun without_bars_the_window_insets_and_the_gutter_reach_the_content() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    HearthScaffold(contentWindowInsets = WindowInsets(top = STATUS_BAR)) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding).testTag("content"))
                    }
                }
            }

            onNodeWithTag("content")
                .assertTopPositionInRootIsEqualTo(STATUS_BAR)
                .assertLeftPositionInRootIsEqualTo(20.dp)
        }

    private companion object {
        val BAR = 56.dp
        val STATUS_BAR = 24.dp
    }
}
