package com.homelab.household.app.screens.memories

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.changes_title
import com.homelab.household.app.resources.memories_band_line
import com.homelab.household.app.resources.memories_empty_title
import com.homelab.household.app.resources.memories_household_note
import com.homelab.household.app.resources.memories_offline_title
import com.homelab.household.app.resources.memories_remove
import com.homelab.household.app.resources.memories_remove_unreachable
import com.homelab.household.app.resources.memories_removed
import com.homelab.household.app.resources.memories_tab_household
import com.homelab.household.app.resources.memories_tab_yours
import com.homelab.household.app.resources.memories_title
import com.homelab.household.app.resources.memories_try_again
import com.homelab.household.app.resources.memories_undo
import com.homelab.household.app.resources.memories_until
import com.homelab.household.app.resources.memories_you_asked
import com.homelab.household.app.resources.tool_card_month_oct
import com.homelab.household.app.testing.FakeMembersHub
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.testing.TestApp
import com.homelab.household.data.datasource.local.InMemorySessionStorage
import com.homelab.household.presentation.memories.MemoriesUiState
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import kotlin.test.assertEquals

/** Everything the memory page says and does, named as the resources it reads. */
@OptIn(ExperimentalTestApi::class)
class MemoriesRobot(
    private val test: ComposeUiTest,
) {
    suspend fun seesTheTitle() = seesText(getString(Res.string.memories_title))

    suspend fun seesTheTabs(
        yours: Int,
        household: Int,
    ) {
        seesText(getString(Res.string.memories_tab_yours, yours))
        seesText(getString(Res.string.memories_tab_household, household))
    }

    suspend fun seesTheBand(count: Int) {
        seesText(getPluralString(Res.plurals.changes_title, count, count))
        seesText(getString(Res.string.memories_band_line))
    }

    suspend fun seesNoBand() =
        test.waitUntilDoesNotExist(hasText(getString(Res.string.memories_band_line)), timeoutMillis = WAIT_MILLIS)

    suspend fun opensTheBand() = tapsText(getString(Res.string.memories_band_line))

    suspend fun opensHousehold(count: Int) = tapsText(getString(Res.string.memories_tab_household, count))

    fun seesMemory(content: String) {
        test.waitUntilExactlyOneExists(hasText(content), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(content).performScrollTo().assertIsDisplayed()
    }

    fun doesNotSee(content: String) = test.waitUntilDoesNotExist(hasText(content), timeoutMillis = WAIT_MILLIS)

    /** Its "who · agent · when" line names the agent that noted it. */
    fun seesItWasNotedBy(line: String) = seesSubstring(line)

    suspend fun seesUntil(day: Int) =
        seesInList(getString(Res.string.memories_until, day, getString(Res.string.tool_card_month_oct)))

    suspend fun seesYouAsked() = seesInList(getString(Res.string.memories_you_asked))

    suspend fun canRemove(content: String) {
        test.waitUntilExactlyOneExists(removeButton(content), timeoutMillis = WAIT_MILLIS)
    }

    suspend fun cannotRemove(content: String) {
        assertEquals(0, test.onAllNodes(removeButton(content)).fetchSemanticsNodes().size)
    }

    suspend fun removes(content: String) {
        test.waitUntilExactlyOneExists(removeButton(content), timeoutMillis = WAIT_MILLIS)
        test.onNode(removeButton(content)).performScrollTo().performClick()
    }

    suspend fun seesItRemoved() = seesInList(getString(Res.string.memories_removed))

    suspend fun undoes() = tapsText(getString(Res.string.memories_undo))

    suspend fun seesNothingRemoved() =
        test.waitUntilDoesNotExist(hasText(getString(Res.string.memories_removed)), timeoutMillis = WAIT_MILLIS)

    suspend fun seesTheRemovalFailed() = seesText(getString(Res.string.memories_remove_unreachable))

    suspend fun seesTheSecretModeNote() = seesInList(getString(Res.string.memories_household_note))

    suspend fun seesNothingRemembered() = seesText(getString(Res.string.memories_empty_title))

    suspend fun seesTheHubCannotBeReached() {
        seesText(getString(Res.string.memories_offline_title))
        seesText(getString(Res.string.memories_try_again))
    }

    private suspend fun removeButton(content: String) =
        hasContentDescription(getString(Res.string.memories_remove, content))

    private fun seesText(text: String) {
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(text).assertIsDisplayed()
    }

    /** A row of the list, scrolled into view first. */
    private fun seesInList(text: String) {
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }

    private fun seesSubstring(text: String) {
        test.waitUntilAtLeastOneExists(hasText(text, substring = true), timeoutMillis = WAIT_MILLIS)
    }

    private fun tapsText(text: String) {
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(text).performClick()
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}

@OptIn(ExperimentalTestApi::class)
suspend fun ComposeUiTest.onMemories(block: suspend MemoriesRobot.() -> Unit) {
    MemoriesRobot(this).block()
}

/** The memory page for a signed-in member, over a hub that answers however the test says. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.memoriesScreen(
    hub: FakeMembersHub,
    onBack: () -> Unit = {},
    onChangesToCheck: () -> Unit = {},
) {
    setContent {
        TestApp(hub.engine, sessionStorage = InMemorySessionStorage().apply { saveTokens("signed-in-token") }) {
            MemoriesScreen(onBack = onBack, onChangesToCheck = onChangesToCheck)
        }
    }
}

/** The memory page drawn from [state] alone. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.memoriesContent(state: MemoriesUiState) {
    setContent {
        StillTheme {
            MemoriesContent(
                state = state,
                onBack = {},
                onTab = {},
                onChangesToCheck = {},
                onRemove = {},
                onUndo = {},
                onRetry = {},
            )
        }
    }
}
