package com.homelab.household.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.invite_create_making_new
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme
import org.jetbrains.compose.resources.stringResource

/**
 * The one code a screen exists to show — an invite, a PIN reset — with how long it has left. The
 * countdown comes from the hub in seconds, because this phone's clock may not agree with it.
 *
 * While [loading] the card keeps its frame and its label and replaces the code with blocks
 * (design notes §6.21, pattern 4). It has to: the code on screen is being replaced, and leaving it
 * there invites someone to read out six characters that are about to stop working.
 */
@Composable
fun CodeCard(
    label: String,
    code: String,
    expiry: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(colors.surface, HearthShapes.bento)
                .padding(HearthTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
    ) {
        Text(label, style = type.overline, color = colors.textMuted)
        if (loading) {
            ArrivingCode()
        } else {
            Text(code, style = type.codeHero, color = colors.primary)
        }
        Text(
            text = if (loading) stringResource(Res.string.invite_create_making_new) else expiry,
            style = type.caption,
            color = if (loading) colors.textMuted else colors.secondary,
        )
    }
}

/**
 * The code itself, arriving. One block per character at the code box's width, each a beat behind
 * the last, so six of them read as one code landing rather than six lights blinking together.
 */
@Composable
private fun ArrivingCode() {
    SkeletonGroup(verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.none)) {
        Row(horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm)) {
            repeat(CODE_LENGTH) { position ->
                SkeletonBlock(
                    modifier = Modifier.width(HearthTheme.size.iconLg),
                    height = HearthTheme.spacing.xxl,
                    position = position,
                )
            }
        }
    }
}

/** Six characters, as the hub mints them and `CodeBoxes` draws them. */
private const val CODE_LENGTH = 6
