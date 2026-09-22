package com.homelab.household.app.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.a11y_loading
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.theme.DefaultMotion
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.StillMotion
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * A skeleton is a shape standing in for an answer that hasn't arrived. It says nothing, because
 * there is nothing yet to say: the blocks are cleared out of the semantics tree and the group
 * around them carries the one announcement between them.
 *
 * Under `StillMotion` throughout — the breath is an infinite transition, and a composition holding
 * one never goes idle, so a test that mounted the breathing version would time out rather than
 * fail.
 */
@OptIn(ExperimentalTestApi::class)
class SkeletonTest {
    @Test
    fun the_group_announces_the_wait() =
        runComposeUiTest {
            setContent {
                Still {
                    SkeletonGroup {
                        SkeletonBlock()
                        SkeletonCircle()
                    }
                }
            }

            onNodeWithTag(SkeletonGroupTag)
                .assertIsDisplayed()
                .assert(
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.StateDescription,
                        getString(Res.string.a11y_loading),
                    ),
                )
        }

    /**
     * And the blocks say nothing themselves. The failure this catches is a skeleton built out of a
     * blank `Text` or a placeholder label — a screen reader would then read the scaffolding aloud
     * instead of waiting for the answer, three times over.
     */
    @Test
    fun the_blocks_inside_it_are_not_in_the_semantics_tree() =
        runComposeUiTest {
            setContent {
                Still {
                    SkeletonGroup {
                        SkeletonBlock()
                        SkeletonBlock()
                        SkeletonCircle()
                    }
                }
            }

            // `clearAndSetSemantics` empties a node rather than removing it, so the blocks are still
            // there to be counted, and still carry the layout's own `Shape`. What matters is that none
            // of them carries anything a screen reader would say out loud.
            val spoken =
                setOf(
                    SemanticsProperties.Text,
                    SemanticsProperties.ContentDescription,
                    SemanticsProperties.StateDescription,
                    SemanticsProperties.Role,
                ).map { it.name }

            val exposed =
                onNodeWithTag(SkeletonGroupTag, useUnmergedTree = true)
                    .fetchSemanticsNode()
                    .descendants()
                    .flatMap { node -> node.config.map { it.key.name } }
                    .filter { it in spoken }

            assertEquals(emptyList(), exposed, "a skeleton block must say nothing of its own")
        }

    /**
     * A skeleton line is a stand-in for a line of text, and the canvas draws it with the same
     * gentle 4dp corner a line of text would have if it had one — not the fully-rounded pill an
     * earlier version defaulted to, which at 14dp high reads as a lozenge rather than a line.
     */
    @Test
    fun a_skeleton_line_takes_the_skeleton_corner() {
        assertEquals(HearthShapes.skeleton, RoundedCornerShape(4.dp))
        assertNotEquals(HearthShapes.pill, HearthShapes.skeleton)
    }

    /**
     * Six blocks standing in for six code characters breathe a beat apart, so the card reads as one
     * thing arriving rather than six lights blinking together.
     */
    @Test
    fun the_breath_can_be_staggered_across_a_row_of_blocks() {
        assertTrue(DefaultMotion.breatheStagger > Duration.ZERO, "a stagger of zero is no stagger")
        assertTrue(
            DefaultMotion.breatheStagger < DefaultMotion.breathe,
            "a stagger longer than the breath itself would put the last block a whole cycle behind",
        )
        assertEquals(Duration.ZERO, StillMotion.breatheStagger, "nothing is time-gated with motion off")
    }

    /**
     * The whole subtree, not just the group's own children: a label smuggled into a skeleton would
     * sit a level further down, inside the block that drew it.
     */
    private fun SemanticsNode.descendants(): List<SemanticsNode> = children + children.flatMap { it.descendants() }

    @Composable
    private fun Still(content: @Composable () -> Unit) {
        StillTheme {
            content()
        }
    }
}
