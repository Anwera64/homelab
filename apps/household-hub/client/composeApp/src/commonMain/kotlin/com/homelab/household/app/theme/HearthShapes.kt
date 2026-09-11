package com.homelab.household.app.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object HearthShapes {
    /** Bento cards. */
    val bento = RoundedCornerShape(24.dp)

    /** Inner elements, list items and text fields. */
    val item = RoundedCornerShape(14.dp)

    /** Full-width actions. */
    val button = RoundedCornerShape(16.dp)

    /** Chips, pills and badges. */
    val pill = RoundedCornerShape(percent = 50)

    internal val material = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = item,
        large = bento,
        extraLarge = bento
    )
}
