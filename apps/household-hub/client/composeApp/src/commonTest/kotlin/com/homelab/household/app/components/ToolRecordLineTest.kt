package com.homelab.household.app.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.testing.StillTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** Tools leave a trail: every read leaves a quiet record above the answer (design notes §2). */
@OptIn(ExperimentalTestApi::class)
class ToolRecordLineTest {
    @Test
    fun `GIVEN a tool record line WHEN it is shown THEN the record text is visible`() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    ToolRecordLine(icon = HearthIcon.Schedule, text = "Checked your calendar")
                }
            }

            onToolRecordLine { seesRecord("Checked your calendar") }
        }

    @Test
    fun `GIVEN a tool record line without a click handler WHEN it is shown THEN it cannot be opened`() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    ToolRecordLine(icon = HearthIcon.Schedule, text = "Checked your calendar")
                }
            }

            onToolRecordLine { doesNotSeeItCanBeOpened("Checked your calendar") }
        }

    @Test
    fun `GIVEN a tool record line with a click handler WHEN it is tapped THEN it opens once`() =
        runComposeUiTest {
            var opened = 0
            setContent {
                StillTheme {
                    ToolRecordLine(icon = HearthIcon.Search, text = "Searched the web", onClick = { opened++ })
                }
            }

            onToolRecordLine { tapsRecordThatCanBeOpened("Searched the web") }
            assertEquals(1, opened)
        }
}
