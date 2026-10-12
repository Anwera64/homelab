package com.homelab.household.app.screens.conversation

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.tool_read_page_done_named
import com.homelab.household.app.resources.tool_read_page_failed_named
import com.homelab.household.app.resources.tool_reason_blocked
import com.homelab.household.app.resources.tool_reason_search_unavailable
import com.homelab.household.app.resources.tool_search_done
import com.homelab.household.app.resources.tool_search_done_query
import com.homelab.household.app.resources.tool_search_failed
import com.homelab.household.app.resources.tool_search_results
import com.homelab.household.app.resources.tool_step_named
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/** A tool step's line: what it says, what opens under it, and which of its words are links. */
@OptIn(ExperimentalTestApi::class)
class ToolStepLineRobot(
    private val test: ComposeUiTest,
) {
    suspend fun seesTheSearch(
        query: String,
        count: Int,
    ) = seesText(searchLine(query, count))

    suspend fun tapsTheSearch(
        query: String,
        count: Int,
    ) {
        test.onNodeWithText(searchLine(query, count)).performClick()
    }

    suspend fun seesResult(
        title: String,
        site: String,
    ) {
        seesText(title)
        seesText(site)
    }

    fun doesNotSeeResult(title: String) {
        test.onNodeWithText(title).assertDoesNotExist()
    }

    /** The links in the words of a result's title. */
    fun linksInResult(title: String): List<String> = linksIn(title)

    suspend fun seesThePageRead(title: String) = seesText(readTitle(title))

    /** The site, in the part of the line that is never shortened. */
    suspend fun seesThePageSite(
        title: String,
        site: String,
    ) {
        val kept = readKept(title, site)
        waitForOneUnmerged(kept)
        test.onNodeWithText(kept, useUnmergedTree = true).assertIsDisplayed()
    }

    /** The links in the words of a page's line. */
    suspend fun linksInPageRead(title: String): List<String> = linksIn(readTitle(title))

    suspend fun layoutOfPageTitle(title: String): TextLayoutResult = layoutOf(readTitle(title))

    suspend fun layoutOfPageSite(
        title: String,
        site: String,
    ): TextLayoutResult = layoutOf(readKept(title, site))

    suspend fun seesThePageWasBlocked(site: String) {
        val what = getString(Res.string.tool_read_page_failed_named, site)
        seesText(getString(Res.string.tool_step_named, what, getString(Res.string.tool_reason_blocked)))
    }

    suspend fun seesWhyTheSearchServiceIsDown() {
        val what = getString(Res.string.tool_search_failed)
        seesText(getString(Res.string.tool_step_named, what, getString(Res.string.tool_reason_search_unavailable)))
    }

    /** A search saved before summaries existed: tapping it opens nothing. */
    suspend fun tapsTheOldSearch() {
        val line = getString(Res.string.tool_search_done)
        seesText(line)
        test.onNodeWithText(line).performClick()
    }

    /**
     * No " · " detail at all. The separator has no resource of its own, so it's read off the one
     * that joins a line's two halves.
     */
    suspend fun seesNoDetailAfterTheWords() {
        val separator = getString(Res.string.tool_step_named, "", "").trim()
        test.onNodeWithText(separator, substring = true).assertDoesNotExist()
    }

    private suspend fun searchLine(
        query: String,
        count: Int,
    ): String {
        val results = getPluralString(Res.plurals.tool_search_results, count, count)
        return getString(Res.string.tool_search_done_query, query, results)
    }

    private suspend fun readLine(
        title: String,
        site: String,
    ) = getString(Res.string.tool_read_page_done_named, title, site)

    /** The page's line up to the end of its title, which is the part that gives way. */
    private suspend fun readTitle(title: String): String {
        val line = readLine(title, SITE_PLACEHOLDER)
        return line.substring(0, line.indexOf(title) + title.length)
    }

    /** The page's line from the end of its title on, which is the part that is kept. */
    private suspend fun readKept(
        title: String,
        site: String,
    ): String {
        val line = readLine(title, site)
        return line.substring(line.indexOf(title) + title.length)
    }

    private suspend fun layoutOf(text: String): TextLayoutResult {
        waitForOneUnmerged(text)
        val layouts = mutableListOf<TextLayoutResult>()
        test.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            it(layouts)
        }
        return layouts.single()
    }

    private fun waitForOneUnmerged(text: String) {
        test.waitUntil(timeoutMillis = WAIT_MILLIS) {
            test.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().size == 1
        }
    }

    private fun linksIn(text: String): List<String> {
        val shown = test.onNodeWithText(text).fetchSemanticsNode().config[SemanticsProperties.Text].first()
        return shown.getLinkAnnotations(0, shown.length).map { (it.item as LinkAnnotation.Url).url }
    }

    private fun seesText(text: String) {
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(text).assertIsDisplayed()
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
        const val SITE_PLACEHOLDER = "site"
    }
}

@OptIn(ExperimentalTestApi::class)
suspend fun ComposeUiTest.onToolStepLine(block: suspend ToolStepLineRobot.() -> Unit) {
    ToolStepLineRobot(this).block()
}
