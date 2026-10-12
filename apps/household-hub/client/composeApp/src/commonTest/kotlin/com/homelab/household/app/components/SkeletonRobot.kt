package com.homelab.household.app.components

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.a11y_loading
import org.jetbrains.compose.resources.getString
import kotlin.test.assertEquals

/** A skeleton as a person (or a screen reader) meets it: one announcement for the group, silence from the blocks. */
@OptIn(ExperimentalTestApi::class)
class SkeletonRobot(
    private val test: ComposeUiTest,
) {
    /** The group is on screen and announces that something is loading. */
    suspend fun seesTheGroupAnnouncingTheWait() {
        waitForTheGroup()
        test
            .onNodeWithTag(SKELETON_GROUP_TAG)
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    getString(Res.string.a11y_loading),
                ),
            )
    }

    /**
     * `clearAndSetSemantics` empties a node rather than removing it, so the blocks are still
     * there to be counted, and still carry the layout's own `Shape`. What matters is that none
     * of them carries anything a screen reader would say out loud.
     */
    fun hearsNothingFromTheBlocks() {
        waitForTheGroup()
        val spoken =
            setOf(
                SemanticsProperties.Text,
                SemanticsProperties.ContentDescription,
                SemanticsProperties.StateDescription,
                SemanticsProperties.Role,
            ).map { it.name }

        val exposed =
            test
                .onNodeWithTag(SKELETON_GROUP_TAG, useUnmergedTree = true)
                .fetchSemanticsNode()
                .descendants()
                .flatMap { node -> node.config.map { it.key.name } }
                .filter { it in spoken }

        assertEquals(emptyList(), exposed, "a skeleton block must say nothing of its own")
    }

    private fun waitForTheGroup() {
        test.waitUntilExactlyOneExists(hasTestTag(SKELETON_GROUP_TAG), timeoutMillis = WAIT_MILLIS)
    }

    /**
     * The whole subtree, not just the group's own children: a label smuggled into a skeleton would
     * sit a level further down, inside the block that drew it.
     */
    private fun SemanticsNode.descendants(): List<SemanticsNode> = children + children.flatMap { it.descendants() }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}

@OptIn(ExperimentalTestApi::class)
suspend fun ComposeUiTest.onSkeleton(block: suspend SkeletonRobot.() -> Unit) {
    SkeletonRobot(this).block()
}
