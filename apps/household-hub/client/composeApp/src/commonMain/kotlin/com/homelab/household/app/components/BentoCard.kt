package com.homelab.household.app.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme

/** The 24dp surface card every dashboard is built from, with an optional small-caps label. */
@Composable
fun BentoCard(
    modifier: Modifier = Modifier,
    label: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = HearthTheme.colors
    Surface(
        modifier = modifier,
        shape = HearthShapes.bento,
        color = colors.surface,
        contentColor = colors.textPrimary
    ) {
        Column(
            modifier = Modifier.padding(HearthTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md)
        ) {
            if (label != null) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, letterSpacing = 1.15.sp),
                    color = colors.textMuted
                )
            }
            content()
        }
    }
}
