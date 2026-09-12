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

    private val sheetTokens = setOf(
        // Navigation
        "household", "schedule", "chats", "mySpace", "newChat",
        // Conversation
        "send", "attach", "back", "agentSwitch", "retry", "streaming",
        // Privacy
        "secretLocked", "secretOpen", "memory", "revoke", "shared", "biometricUnlock",
        // Tools
        "calendarAdd", "search", "document", "toolGeneric",
        // Hub status and feedback
        "hubOnline", "hubOffline", "synced", "warning", "error",
        // Profile and settings
        "profile", "settings", "signOut", "chevronRight"
    )

    private val solidShapeCounts = mapOf(
        "streaming" to 3,
        "hubOnline" to 1,
        "hubOffline" to 1,
        "warning" to 1,
        "error" to 1
    )

    @Test
    fun the_set_is_exactly_the_icon_sheet() {
        assertEquals(sheetTokens, HearthIcon.entries.map { it.token }.toSet())
        assertEquals(sheetTokens.size, HearthIcon.entries.size)
        assertTrue(HearthIcon.entries.none { it.token == "voice" }, "voice was removed")
    }

    @Test
    fun every_icon_sits_on_the_24_unit_grid() {
        HearthIcon.entries.forEach { icon ->
            val vector = icon.vector()
            assertEquals(24.dp, vector.defaultWidth, icon.token)
            assertEquals(24.dp, vector.defaultHeight, icon.token)
            assertEquals(24f, vector.viewportWidth, icon.token)
            assertEquals(24f, vector.viewportHeight, icon.token)
        }
    }

    @Test
    fun strokes_are_1_5_with_round_caps_and_joins_and_1_85_when_active() {
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
    fun solid_shapes_are_filled_not_stroked() {
        HearthIcon.entries.forEach { icon ->
            val solids = icon.vector().paths().filter { it.fill != null }
            assertEquals(solidShapeCounts[icon.token] ?: 0, solids.size, "${icon.token} solid shapes")
            solids.forEach { assertNull(it.stroke, "${icon.token} solid shape must not be stroked") }
        }
    }

    @Test
    fun every_path_has_drawing_commands() {
        HearthIcon.entries.forEach { icon ->
            icon.vector().paths().forEach { path ->
                assertTrue(path.pathData.isNotEmpty(), "${icon.token} has an empty path")
            }
        }
    }

    @Test
    fun vectors_are_built_once_per_variant() {
        assertSame(HearthIcon.Household.vector(), HearthIcon.Household.vector())
        assertSame(HearthIcon.Household.vector(active = true), HearthIcon.Household.vector(active = true))
    }

    private fun ImageVector.paths(): List<VectorPath> = root.paths()

    private fun VectorGroup.paths(): List<VectorPath> = flatMap { node ->
        when (node) {
            is VectorPath -> listOf(node)
            is VectorGroup -> node.paths()
        }
    }
}
