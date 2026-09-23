package com.homelab.household.app.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme

/** Whose agent it is, already in words — the chip beside the name. */
sealed interface AgentOwnerChip {
    /** Ships with the hub; carries the lock, since nobody in the house can change it. */
    data class BuiltIn(
        val label: String,
    ) : AgentOwnerChip

    data class Yours(
        val label: String,
    ) : AgentOwnerChip

    data class Member(
        val label: String,
    ) : AgentOwnerChip
}

/**
 * An agent as the catalogue draws it: avatar, name, whose it is, what it is for, and what it is
 * allowed to do — the permissions are the part that says which agent can search the web.
 *
 * Every string arrives already in words, so the card never sees a backend identifier. [selected]
 * draws the primary edge and the tick the picker uses for the agent already chosen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgentCard(
    name: String,
    avatar: String,
    tagline: String,
    owner: AgentOwnerChip?,
    permissions: List<String>,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    val spacing = HearthTheme.spacing
    val size = HearthTheme.size

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().semantics { this.selected = selected },
        shape = HearthShapes.bento,
        color = if (selected) colors.surface else colors.surfaceAlt,
        border =
            if (selected) {
                BorderStroke(size.emphasis, colors.primary)
            } else {
                BorderStroke(size.hairline, colors.outlineSoft)
            },
    ) {
        Row(
            modifier = Modifier.padding(spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(spacing.lg),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(size.touchTarget)
                        .clip(CircleShape)
                        .background(colors.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = avatar, style = type.glyphLg)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = name, style = type.bodyStrong, color = colors.textPrimary)
                    when (owner) {
                        is AgentOwnerChip.BuiltIn -> HearthChip(owner.label, icon = HearthIcon.SecretLocked)
                        is AgentOwnerChip.Yours -> HearthChip(owner.label, variant = ChipVariant.Primary)
                        is AgentOwnerChip.Member -> HearthChip(owner.label)
                        null -> Unit
                    }
                }
                if (tagline.isNotEmpty()) {
                    Text(text = tagline, style = type.caption, color = colors.textMuted)
                }
                if (permissions.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.padding(top = spacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        permissions.forEach { HearthChip(it, variant = ChipVariant.Secondary) }
                    }
                }
            }
            if (selected) {
                HearthIconImage(
                    icon = HearthIcon.Sent,
                    contentDescription = null,
                    size = size.iconMd,
                    tint = colors.primary,
                )
            }
        }
    }
}
