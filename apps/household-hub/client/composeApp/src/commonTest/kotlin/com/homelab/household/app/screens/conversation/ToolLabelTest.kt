package com.homelab.household.app.screens.conversation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

/**
 * No raw identifier reaches a person (design notes §2): `searxng_search` is "Search the web".
 * One map, keyed by the hub's names, is the only place a tool is put into words.
 */
@OptIn(ExperimentalTestApi::class)
class ToolLabelTest {
    private val hubTools = listOf("calendar_read", "calendar_write", "document_writer", "pdf_reader", "searxng_search")

    @Test
    fun `GIVEN every tool the hub has WHEN it is shown THEN it has words of its own`() =
        runComposeUiTest {
            hubTools.forEach { tool ->
                assertNotEquals(
                    toolLabel("a_tool_nobody_named"),
                    toolLabel(tool),
                    "$tool falls back to the generic words",
                )
            }
        }

    @Test
    fun `GIVEN any tool WHEN it is shown THEN its backend name never is`() =
        runComposeUiTest {
            (hubTools + "a_tool_nobody_named").forEach { tool ->
                val label = toolLabel(tool)
                listOf(label.running, label.done, label.failed).forEach { words ->
                    val shown = getString(words)
                    assertFalse(shown.contains(tool), "\"$shown\" names $tool")
                    assertFalse(shown.contains('_'), "\"$shown\" reads like an identifier")
                }
            }
        }
}
