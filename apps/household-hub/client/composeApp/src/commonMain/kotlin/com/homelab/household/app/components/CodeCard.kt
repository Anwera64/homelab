package com.homelab.household.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme

/**
 * The one code a screen exists to show — an invite, a PIN reset — with how long it has left. The
 * countdown comes from the hub in seconds, because this phone's clock may not agree with it.
 */
@Composable
fun CodeCard(
    label: String,
    code: String,
    expiry: String,
    modifier: Modifier = Modifier
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, HearthShapes.bento)
            .padding(HearthTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md)
    ) {
        Text(label, style = type.overline, color = colors.textMuted)
        Text(code, style = type.codeHero, color = colors.primary)
        Text(expiry, style = type.caption, color = colors.secondary)
    }
}
