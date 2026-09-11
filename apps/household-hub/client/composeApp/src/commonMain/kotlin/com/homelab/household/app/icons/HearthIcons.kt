package com.homelab.household.app.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The Hearth icon set, drawn on a 24-unit grid with a 1.5 stroke (1.85 when active),
 * round caps and joins. Path data is copied from the icon sheet; `token` is the sheet's name.
 * Vectors are drawn in black and take their colour from the `Icon` tint.
 */
enum class HearthIcon(val token: String, private val shapes: List<IconShape>) {
    // Navigation
    Household("household", strokes("M3.6 10.4 12 3.8l8.4 6.6", "M5.8 9.2V19a1.4 1.4 0 0 0 1.4 1.4h9.6a1.4 1.4 0 0 0 1.4-1.4V9.2")),
    Schedule("schedule", strokes(rect(3.5, 5.5, 17.0, 15.0, 3.2), "M8.2 3.2v4M15.8 3.2v4M3.5 10.4h17")),
    Chats("chats", strokes("M4 6.6A2.6 2.6 0 0 1 6.6 4h10.8A2.6 2.6 0 0 1 20 6.6v7.6a2.6 2.6 0 0 1-2.6 2.6H9.4l-4.2 3.4a.7.7 0 0 1-1.2-.5Z")),
    MySpace(
        "mySpace",
        strokes(
            "M7 11.6V8.4a2.6 2.6 0 0 1 2.6-2.6h4.8a2.6 2.6 0 0 1 2.6 2.6v3.2",
            "M4 14.4a2 2 0 0 1 4 0v1.8h8v-1.8a2 2 0 0 1 4 0v2.6a1.6 1.6 0 0 1-1.6 1.6H5.6A1.6 1.6 0 0 1 4 17Z",
            "M6.6 18.4v2.2M17.4 18.4v2.2"
        )
    ),
    NewChat("newChat", strokes("M12 5.6v12.8M5.6 12h12.8")),

    // Conversation
    Send("send", strokes("M12 19.2V5.4M6.4 11 12 5.4 17.6 11")),
    Attach("attach", strokes("M17.2 11.2 11.4 17a3.8 3.8 0 0 1-5.4-5.4l6.6-6.6a2.5 2.5 0 0 1 3.6 3.6l-6.5 6.5a1.3 1.3 0 0 1-1.8-1.8l5.8-5.8")),
    Back("back", strokes("M14.4 5.6 8 12l6.4 6.4")),
    AgentSwitch("agentSwitch", strokes("M6.4 9.6 12 15.2l5.6-5.6")),
    Retry("retry", strokes("M18.8 12a6.8 6.8 0 1 1-2-4.8", "M18.6 4.6V8h-3.4")),
    Streaming("streaming", solids(circle(12.0, 12.0, 1.1), circle(6.4, 12.0, 1.1), circle(17.6, 12.0, 1.1))),

    // Privacy — Secret Mode and memory
    SecretLocked("secretLocked", strokes(rect(4.8, 10.4, 14.4, 9.6, 2.8), "M8.4 10.4V7.6a3.6 3.6 0 0 1 7.2 0v2.8")),
    SecretOpen("secretOpen", strokes(rect(4.8, 10.4, 14.4, 9.6, 2.8), "M8.4 10.4V7.6a3.6 3.6 0 0 1 7-1.2")),
    Memory(
        "memory",
        strokes(
            "M6.2 3.6h12.2v16.8H6.2A2.6 2.6 0 0 1 3.6 17.8V6.2a2.6 2.6 0 0 1 2.6-2.6Z",
            "M3.6 17.8a2.6 2.6 0 0 1 2.6-2.6h12.2",
            "M13.4 3.6v6.6l2.2-1.7 2.2 1.7V3.6"
        )
    ),
    Revoke("revoke", strokes(circle(12.0, 12.0, 8.2), "M9.4 9.4 14.6 14.6M14.6 9.4 9.4 14.6")),
    Shared("shared", strokes(circle(9.4, 12.0, 5.8), circle(14.6, 12.0, 5.8))),
    BiometricUnlock(
        "biometricUnlock",
        strokes(
            "M3.6 8V6.2a2.6 2.6 0 0 1 2.6-2.6H8",
            "M16 3.6h1.8a2.6 2.6 0 0 1 2.6 2.6V8",
            "M20.4 16v1.8a2.6 2.6 0 0 1-2.6 2.6H16",
            "M8 20.4H6.2a2.6 2.6 0 0 1-2.6-2.6V16",
            circle(12.0, 10.6, 2.3),
            "M12 12.9v3.5"
        )
    ),

    // Tools — agent actions
    CalendarAdd(
        "calendarAdd",
        strokes(
            "M19.4 11.6V8.6a2.8 2.8 0 0 0-2.8-2.8H5.8A2.8 2.8 0 0 0 3 8.6v9.2a2.8 2.8 0 0 0 2.8 2.8h6",
            "M8.2 3.4v4.4M15 3.4v4.4M3 11.4h16.4",
            "M17.6 14.6v6M14.6 17.6h6"
        )
    ),
    Search("search", strokes(circle(10.8, 10.8, 6.4), "M15.6 15.6l4.8 4.8")),
    Document(
        "document",
        strokes(
            "M13.2 3.2H7.4A2.4 2.4 0 0 0 5 5.6v12.8a2.4 2.4 0 0 0 2.4 2.4h9.2a2.4 2.4 0 0 0 2.4-2.4V9.2Z",
            "M13.2 3.2v4.4a1.6 1.6 0 0 0 1.6 1.6h4.2",
            "M8.8 13.8h6.4M8.8 16.8h4"
        )
    ),
    ToolGeneric(
        "toolGeneric",
        strokes("M14.8 4.4a4.6 4.6 0 0 0-6 6l-5.2 5.2a1.6 1.6 0 0 0 0 2.3l1.5 1.5a1.6 1.6 0 0 0 2.3 0l5.2-5.2a4.6 4.6 0 0 0 6-6l-2.8 2.8-2.6-.7-.7-2.6Z")
    ),

    // Hub status and feedback
    HubOnline("hubOnline", strokes("M5.2 12.8a9.4 9.4 0 0 1 13.6 0M8.4 16a5 5 0 0 1 7.2 0") + solids(circle(12.0, 19.2, 1.3))),
    HubOffline(
        "hubOffline",
        strokes("M5.2 12.8a9.4 9.4 0 0 1 4.2-2.5M14.8 10.5a9.4 9.4 0 0 1 4 2.3", "M8.4 16a5 5 0 0 1 2-1.2") +
            solids(circle(12.0, 19.2, 1.3)) +
            strokes("M3.8 3.8 20.2 20.2")
    ),
    Synced("synced", strokes(circle(12.0, 12.0, 8.2), "M8.2 12.2l2.7 2.7 5-5.4")),
    Warning(
        "warning",
        strokes("M10.9 5.4a1.3 1.3 0 0 1 2.2 0l7.5 13.1a1.3 1.3 0 0 1-1.1 2H4.5a1.3 1.3 0 0 1-1.1-2Z", "M12 10.2v3.8") +
            solids(circle(12.0, 17.2, 1.0))
    ),
    Error("error", strokes(circle(12.0, 12.0, 8.2), "M12 8v4.4") + solids(circle(12.0, 15.8, 1.0))),

    // Profile and settings
    Profile("profile", strokes(circle(12.0, 8.6, 4.0), "M4.8 20.2a7.2 7.2 0 0 1 14.4 0")),
    Settings("settings", strokes("M3.6 7.8h8.6M17.4 7.8h3M3.6 16.2h3M11.8 16.2h8.6", circle(14.8, 7.8, 2.4), circle(9.0, 16.2, 2.4))),
    SignOut(
        "signOut",
        strokes(
            "M14.4 7.6V5.6a2 2 0 0 0-2-2H6.4a2 2 0 0 0-2 2v12.8a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2v-2",
            "M10.6 12h9M16.4 8.8 19.6 12l-3.2 3.2"
        )
    ),
    ChevronRight("chevronRight", strokes("M9.6 5.6 16 12l-6.4 6.4"));

    private val restingVector: ImageVector by lazy { build(strokeWidth = 1.5f) }
    private val activeVector: ImageVector by lazy { build(strokeWidth = 1.85f) }

    fun vector(active: Boolean = false): ImageVector = if (active) activeVector else restingVector

    private fun build(strokeWidth: Float): ImageVector =
        ImageVector.Builder(
            name = token,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            shapes.forEach { shape ->
                when (shape) {
                    is IconShape.Stroke -> addPath(
                        pathData = addPathNodes(shape.pathData),
                        stroke = ink,
                        strokeLineWidth = strokeWidth,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round
                    )
                    is IconShape.Solid -> addPath(
                        pathData = addPathNodes(shape.pathData),
                        fill = ink
                    )
                }
            }
        }.build()
}

sealed interface IconShape {
    val pathData: String

    data class Stroke(override val pathData: String) : IconShape
    data class Solid(override val pathData: String) : IconShape
}

private val ink = SolidColor(Color.Black)

private fun strokes(vararg pathData: String): List<IconShape> = pathData.map { IconShape.Stroke(it) }

private fun solids(vararg pathData: String): List<IconShape> = pathData.map { IconShape.Solid(it) }

/** SVG `<circle>` as two arcs. */
private fun circle(cx: Double, cy: Double, r: Double): String =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0Z"

/** SVG `<rect rx>` as a rounded-rectangle path. */
private fun rect(x: Double, y: Double, w: Double, h: Double, rx: Double): String =
    "M${x + rx} ${y}H${x + w - rx}A$rx $rx 0 0 1 ${x + w} ${y + rx}V${y + h - rx}" +
        "A$rx $rx 0 0 1 ${x + w - rx} ${y + h}H${x + rx}A$rx $rx 0 0 1 $x ${y + h - rx}" +
        "V${y + rx}A$rx $rx 0 0 1 ${x + rx} ${y}Z"
