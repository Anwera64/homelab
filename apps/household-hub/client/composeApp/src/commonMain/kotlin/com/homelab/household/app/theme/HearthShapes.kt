package com.homelab.household.app.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object HearthShapes {
    /** Bento cards. */
    val bento = RoundedCornerShape(24.dp)

    /** The soft tile an icon sits on — the bento radius, so tiles stop inventing their own. */
    val tile = bento

    /** Inner elements, list items and text fields. */
    val item = RoundedCornerShape(16.dp)

    /** Full-width actions. */
    val button = RoundedCornerShape(16.dp)

    /**
     * A block standing in for a line of text that hasn't arrived. Gentler than [item] because a
     * skeleton line is only 14dp tall: at that height a bigger radius reads as a lozenge, and the
     * fully rounded [pill] reads as a chip.
     */
    val skeleton = RoundedCornerShape(4.dp)

    /** Chips, pills and badges. */
    val pill = RoundedCornerShape(percent = 50)

    /** A key on the PIN pad. */
    val key = RoundedCornerShape(20.dp)

    internal val material = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = item,
        large = bento,
        extraLarge = bento
    )
}
