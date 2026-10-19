package com.homelab.household.app.icons

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Names, grid and stroke rules come from the Hearth icon sheet (design notes §3). */
class HearthIconsTest {
    private val sheetTokens =
        setOf(
            // Navigation
            "household",
            "schedule",
            "chats",
            "mySpace",
            "newChat",
            // Conversation
            "send",
            "attach",
            "back",
            "agentSwap",
            "retry",
            "streaming",
            // Privacy
            "secretLocked",
            "secretOpen",
            "memory",
            "revoke",
            "shared",
            "biometricUnlock",
            // Tools
            "calendarAdd",
            "search",
            "document",
            "toolGeneric",
            // Hub status and feedback
            "hubOnline",
            "hubOffline",
            "synced",
            "warning",
            "error",
            // Profile and settings
            "profile",
            "settings",
            "signOut",
            "chevronRight",
            // Sign in: the PIN pad's delete key, drawn on the PIN artboard rather than the sheet
            "delete",
            // The receipt under a question the hub has, drawn on the Chat Turn States canvas
            "sent",
            // The calendar row's options, drawn on the Calendar & offline canvas
            "more",
            // Removing an event, drawn on the Tools page's approval board
            "calendarRemove",
            // A write the member declined, on its record line: the Tools page's "approved and declined" board
            "declined",
            // A calendar step fixed since it failed, on its fix card: the Tools page's ToolFixedCard board
            "calendarFixed",
            // How often an event repeats, on its approval card: the Tools page's "Repeating events" row
            "repeat",
            // A sentence waiting on another member's answer: the Memory page's MemWaitingB board
            "waiting",
        )

    private val solidShapeCounts =
        mapOf(
            "streaming" to 3,
            "more" to 3,
            "hubOnline" to 1,
            "hubOffline" to 1,
            "warning" to 1,
            "error" to 1,
        )

    @Test
    fun `GIVEN the icon sheet WHEN the icon set is read THEN it holds exactly the sheet's icons`() {
        assertEquals(sheetTokens, HearthIcon.entries.map { it.token }.toSet())
        assertEquals(sheetTokens.size, HearthIcon.entries.size)
        assertTrue(HearthIcon.entries.none { it.token == "voice" }, "voice was removed")
    }

    @Test
    fun `GIVEN every icon WHEN its vector is built THEN it sits on the 24 unit grid`() {
        HearthIcon.entries.forEach { icon ->
            val vector = icon.vector()
            assertEquals(24.dp, vector.defaultWidth, icon.token)
            assertEquals(24.dp, vector.defaultHeight, icon.token)
            assertEquals(24f, vector.viewportWidth, icon.token)
            assertEquals(24f, vector.viewportHeight, icon.token)
        }
    }

    @Test
    fun `GIVEN every icon WHEN its resting and active vectors are built THEN strokes are one and a half wide at rest and one point eight five when active with round caps and joins`() {
        HearthIcon.entries.forEach { icon ->
            listOf(false to 1.5f, true to 1.85f).forEach { (active, width) ->
                val paths = icon.vector(active).paths()
                assertTrue(paths.isNotEmpty(), "${icon.token} draws nothing")
                paths.filter { it.stroke != null }.forEach { path ->
                    assertEquals(width, path.strokeLineWidth, "${icon.token} active=$active")
                    assertEquals(StrokeCap.Round, path.strokeLineCap, icon.token)
                    assertEquals(StrokeJoin.Round, path.strokeLineJoin, icon.token)
                    assertNull(path.fill, "${icon.token} stroked path must not be filled")
                }
            }
        }
    }

    @Test
    fun `GIVEN every icon WHEN its vector is built THEN its solid shapes are filled and not stroked`() {
        HearthIcon.entries.forEach { icon ->
            val solids = icon.vector().paths().filter { it.fill != null }
            assertEquals(solidShapeCounts[icon.token] ?: 0, solids.size, "${icon.token} solid shapes")
            solids.forEach { assertNull(it.stroke, "${icon.token} solid shape must not be stroked") }
        }
    }

    @Test
    fun `GIVEN every icon WHEN its vector is built THEN every path has drawing commands`() {
        HearthIcon.entries.forEach { icon ->
            icon.vector().paths().forEach { path ->
                assertTrue(path.pathData.isNotEmpty(), "${icon.token} has an empty path")
            }
        }
    }

    @Test
    fun `GIVEN an icon WHEN its vector is asked for twice THEN the same vector comes back for each variant`() {
        assertSame(HearthIcon.Household.vector(), HearthIcon.Household.vector())
        assertSame(HearthIcon.Household.vector(active = true), HearthIcon.Household.vector(active = true))
    }

    private fun ImageVector.paths(): List<VectorPath> = root.paths()

    private fun VectorGroup.paths(): List<VectorPath> =
        flatMap { node ->
            when (node) {
                is VectorPath -> listOf(node)
                is VectorGroup -> node.paths()
            }
        }
}
