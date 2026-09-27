package com.homelab.household.app.screens.conversation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.domain.model.ToolAction
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * No raw identifier reaches a person (design notes §2): `searxng_search` is "Search the web".
 * One map, keyed by the hub's names and a write's action, is the only place a tool is put into
 * words, so a removal can never read as "Added" (canvas: tools-map).
 */
@OptIn(ExperimentalTestApi::class)
class ToolLabelTest {
    private val hubTools =
        listOf(
            "calendar_read",
            "calendar_write",
            "document_writer",
            "pdf_reader",
            "searxng_search",
            "read_page",
            "lookup_sources",
        )

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
    fun `GIVEN any tool and any action WHEN it is shown THEN its backend name never is`() =
        runComposeUiTest {
            (hubTools + "a_tool_nobody_named").forEach { tool ->
                (ToolAction.entries + null).forEach { action ->
                    val label = toolLabel(tool, action)
                    listOfNotNull(label.running, label.done, label.failed, label.card, label.declined, label.permission)
                        .forEach { words ->
                            val shown = getString(words)
                            assertFalse(shown.contains(tool), "\"$shown\" names $tool")
                            assertFalse(shown.contains('_'), "\"$shown\" reads like an identifier")
                        }
                }
            }
        }

    @Test
    fun `GIVEN a calendar removal WHEN it is shown THEN it reads as a removal everywhere`() =
        runComposeUiTest {
            val label = toolLabel("calendar_write", ToolAction.Delete)

            assertEquals("Removing from your calendar…", getString(label.running))
            assertEquals("Removed from your calendar", getString(label.done))
            assertEquals("Couldn’t remove it from your calendar", getString(label.failed))
            assertEquals("Remove from your calendar", label.card?.let { getString(it) })
            assertEquals("Not removed", label.declined?.let { getString(it) })
            assertEquals("Remove events", getString(label.permission))
            assertEquals(HearthIcon.CalendarRemove, label.icon)
        }

    @Test
    fun `GIVEN a calendar add WHEN it is shown THEN it reads as adding`() =
        runComposeUiTest {
            val label = toolLabel("calendar_write", ToolAction.Create)

            assertEquals("Adding to your calendar…", getString(label.running))
            assertEquals("Added to your calendar", getString(label.done))
            assertEquals("Couldn’t add it to your calendar", getString(label.failed))
            assertEquals("Add to your calendar", label.card?.let { getString(it) })
            assertEquals("Not added", label.declined?.let { getString(it) })
            assertEquals("Add events", getString(label.permission))
            assertEquals(HearthIcon.CalendarAdd, label.icon)
        }

    @Test
    fun `GIVEN a calendar change WHEN it is shown THEN it reads as changing`() =
        runComposeUiTest {
            val label = toolLabel("calendar_write", ToolAction.Update)

            assertEquals("Changing your calendar…", getString(label.running))
            assertEquals("Changed your calendar", getString(label.done))
            assertEquals("Couldn’t change your calendar", getString(label.failed))
            assertEquals("Change in your calendar", label.card?.let { getString(it) })
            assertEquals("Not changed", label.declined?.let { getString(it) })
            assertEquals("Change events", getString(label.permission))
        }

    @Test
    fun `GIVEN each note action WHEN it is shown THEN it says what it did to the note`() =
        runComposeUiTest {
            val created = toolLabel("document_writer", ToolAction.Create)
            val added = toolLabel("document_writer", ToolAction.Append)
            val replaced = toolLabel("document_writer", ToolAction.Replace)

            assertEquals("Created a note", getString(created.done))
            assertEquals("Create notes", getString(created.permission))
            assertEquals("Added to a note", getString(added.done))
            assertEquals("Add to notes", getString(added.permission))
            assertEquals("Replaced a note", getString(replaced.done))
            assertEquals("Replace notes", getString(replaced.permission))
            assertEquals("Not replaced", replaced.declined?.let { getString(it) })
        }

    @Test
    fun `GIVEN a write that isn't an add WHEN it is shown THEN nothing about it says added`() =
        runComposeUiTest {
            val notAdds =
                listOf(
                    toolLabel("calendar_write", ToolAction.Update),
                    toolLabel("calendar_write", ToolAction.Delete),
                    toolLabel("document_writer", ToolAction.Replace),
                    // Saved before the hub said which action a write was: it could have been anything.
                    toolLabel("calendar_write"),
                    toolLabel("document_writer"),
                )
            notAdds.forEach { label ->
                listOfNotNull(label.running, label.done, label.failed, label.card, label.declined).forEach { words ->
                    val shown = getString(words)
                    assertFalse(shown.contains("add", ignoreCase = true), "\"$shown\" reads as an add")
                }
            }
        }

    @Test
    fun `GIVEN a write saved without its action WHEN it is shown THEN it says the calendar was updated`() =
        runComposeUiTest {
            assertEquals("Updated your calendar", getString(toolLabel("calendar_write").done))
        }

    @Test
    fun `GIVEN an action a tool doesn't have WHEN it is shown THEN it reads as the tool with no action`() =
        runComposeUiTest {
            assertEquals(toolLabel("calendar_write"), toolLabel("calendar_write", ToolAction.Append))
            assertEquals(toolLabel("calendar_read"), toolLabel("calendar_read", ToolAction.Delete))
        }

    @Test
    fun `GIVEN a tool that only looks WHEN it is shown THEN it has no card and nothing to decline`() =
        runComposeUiTest {
            listOf("calendar_read", "searxng_search", "read_page", "lookup_sources", "pdf_reader").forEach { tool ->
                assertNull(toolLabel(tool).card, tool)
                assertNull(toolLabel(tool).declined, tool)
            }
        }

    @Test
    fun `GIVEN the agent picker WHEN it shows what an agent may do THEN it uses the words of the settings screen`() =
        runComposeUiTest {
            assertEquals("Read your calendar", getString(toolPermissionLabel("calendar_read")))
            assertEquals("Add events", getString(toolPermissionLabel("calendar_write")))
            assertEquals("Read PDFs", getString(toolPermissionLabel("pdf_reader")))
            assertEquals("Create notes", getString(toolPermissionLabel("document_writer")))
        }

    @Test
    fun `GIVEN every tool the hub has WHEN its permission is shown THEN it has words of its own`() =
        runComposeUiTest {
            hubTools.forEach { tool ->
                assertNotEquals(
                    toolPermissionLabel("a_tool_nobody_named"),
                    toolPermissionLabel(tool),
                    "$tool falls back to the generic permission words",
                )
            }
        }

    @Test
    fun `GIVEN the web search tool WHEN its permission is shown THEN it reads Search the web`() =
        runComposeUiTest {
            assertEquals("Search the web", getString(toolPermissionLabel("searxng_search")))
        }

    @Test
    fun `GIVEN a tool nobody named WHEN its permission is shown THEN the raw name never appears`() =
        runComposeUiTest {
            val shown = getString(toolPermissionLabel("a_tool_nobody_named"))
            assertFalse(shown.contains("a_tool_nobody_named"), "\"$shown\" names the raw tool")
            assertFalse(shown.contains('_'), "\"$shown\" reads like an identifier")
        }

    @Test
    fun `GIVEN a page read while researching WHEN it is shown THEN it reads like reading a page`() =
        runComposeUiTest {
            val label = toolLabel("read_page")
            assertEquals("Reading a page…", getString(label.running))
            assertEquals("Read a page", getString(label.done))
            assertEquals("Couldn’t read a page", getString(label.failed))
        }

    @Test
    fun `GIVEN a look through what was read WHEN it is shown THEN it reads like looking through the sources`() =
        runComposeUiTest {
            val label = toolLabel("lookup_sources")
            assertEquals("Looking through the sources…", getString(label.running))
            assertEquals("Looked through the sources", getString(label.done))
            assertEquals("Couldn’t look through the sources", getString(label.failed))
        }

    @Test
    fun `GIVEN the research tools WHEN their permission is shown THEN it is searching the web they come with`() =
        runComposeUiTest {
            assertEquals("Search the web", getString(toolPermissionLabel("read_page")))
            assertEquals("Search the web", getString(toolPermissionLabel("lookup_sources")))
        }
}
