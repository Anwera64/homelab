package com.homelab.household.app.screens.conversation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.homelab.household.domain.model.ProposalAnswer
import kotlin.test.Test
import kotlin.test.assertEquals

/** "Remembered from this chat": the sheet the quiet line opens. */
@OptIn(ExperimentalTestApi::class)
class LookedOverSheetTest {
    private val sheet = LookedOverUiStateProvider().values.first()

    @Test
    fun `GIVEN a looked-over chat WHEN the sheet opens THEN it shows what to check what waits for Liam and what was remembered`() =
        runComposeUiTest {
            lookedOverSheetContent(sheet)

            onLookedOverSheet {
                seesTheTitle(time = "10:52")
                seesToCheck(count = 1)
                sees("Eats fish again since September.")
                seesNoChatNamed("Dinner ideas")
                seesWaitingFor("Liam")
                seesRemembered(count = 2)
                sees("Studio Jury on Friday 17 October, 09:00.")
            }
        }

    @Test
    fun `GIVEN the sheet WHEN Use new X and All memories are tapped THEN each reaches its callback`() =
        runComposeUiTest {
            val answers = mutableListOf<Pair<String, ProposalAnswer>>()
            val removed = mutableListOf<String>()
            var allMemories = 0
            lookedOverSheetContent(
                sheet,
                onAnswer = { id, answer -> answers += id to answer },
                onRemove = { removed += it },
                onAllMemories = { allMemories++ },
            )

            onLookedOverSheet {
                usesNew()
                removes("Studio Jury on Friday 17 October, 09:00.")
                opensAllMemories()
            }

            assertEquals(listOf("c1" to ProposalAnswer.UseNew), answers)
            assertEquals(listOf("r1"), removed)
            assertEquals(1, allMemories)
        }

    @Test
    fun `GIVEN a sheet taller than the screen WHEN it draws THEN every row is laid out at once so its height never shifts`() =
        runComposeUiTest {
            lookedOverSheetContent(sheet, height = 300.dp)

            onLookedOverSheet { laysOutEveryRowAtOnce() }
        }

    @Test
    fun every_previewed_state_draws() {
        LookedOverUiStateProvider().values.forEach { state ->
            runComposeUiTest {
                lookedOverSheetContent(state)
                waitForIdle()
            }
        }
    }
}
