package com.homelab.household.app.screens.conversation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.homelab.household.app.components.AgentCard
import com.homelab.household.app.components.AgentOwnerChip
import com.homelab.household.app.components.SecondaryButton
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.agent_owner_built_in
import com.homelab.household.app.resources.agent_owner_member
import com.homelab.household.app.resources.agent_owner_yours
import com.homelab.household.app.resources.agent_picker_caption
import com.homelab.household.app.resources.agent_picker_failed_line
import com.homelab.household.app.resources.agent_picker_failed_title
import com.homelab.household.app.resources.agent_picker_title
import com.homelab.household.app.resources.conversation_try_again
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.presentation.chatsession.AgentChoice
import com.homelab.household.presentation.chatsession.AgentOwner
import org.jetbrains.compose.resources.stringResource

/**
 * Who a new chat talks to: the catalogue's cards in a sheet that rises from under the thumb, with
 * the agent already chosen ticked.
 *
 * Nothing in it is dimmed — the list is only what can be picked. When the list did not come, the
 * chosen agent is still there and still works, and the sheet says so and offers to ask again
 * rather than blocking the chat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentPickerSheet(
    agents: List<AgentChoice>,
    selectedAgentId: String?,
    failed: Boolean,
    currentAgentName: String,
    onSelect: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    val spacing = HearthTheme.spacing

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = HearthShapes.sheet,
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.outline) },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.xxl).padding(bottom = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(stringResource(Res.string.agent_picker_title), style = type.title, color = colors.textPrimary)
            Text(stringResource(Res.string.agent_picker_caption), style = type.caption, color = colors.textMuted)
        }
        LazyColumn(
            contentPadding =
                PaddingValues(start = spacing.xl, end = spacing.xl, bottom = spacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            items(agents, key = { it.id }) { agent ->
                AgentCard(
                    name = agent.name,
                    avatar = agent.avatar,
                    tagline = agent.tagline,
                    owner = ownerChip(agent.owner),
                    permissions = agent.tools.map { stringResource(toolPermissionLabel(it)) },
                    selected = agent.id == selectedAgentId,
                    onClick = { onSelect(agent.id) },
                )
            }
            if (failed) {
                item(key = "failed") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = HearthShapes.bento,
                        color = colors.surfaceAlt,
                        border = BorderStroke(HearthTheme.size.hairline, colors.outlineSoft),
                    ) {
                        Column(
                            modifier = Modifier.padding(spacing.lg),
                            verticalArrangement = Arrangement.spacedBy(spacing.md),
                        ) {
                            Text(
                                stringResource(Res.string.agent_picker_failed_title),
                                style = type.bodyStrong,
                                color = colors.textPrimary,
                            )
                            Text(
                                stringResource(Res.string.agent_picker_failed_line, currentAgentName),
                                style = type.caption,
                                color = colors.textMuted,
                            )
                            SecondaryButton(
                                text = stringResource(Res.string.conversation_try_again),
                                onClick = onRetry,
                                icon = HearthIcon.Retry,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ownerChip(owner: AgentOwner): AgentOwnerChip? =
    when (owner) {
        AgentOwner.BuiltIn -> AgentOwnerChip.BuiltIn(stringResource(Res.string.agent_owner_built_in))
        AgentOwner.Yours -> AgentOwnerChip.Yours(stringResource(Res.string.agent_owner_yours))
        is AgentOwner.Member -> AgentOwnerChip.Member(stringResource(Res.string.agent_owner_member, owner.firstName))
        AgentOwner.Unknown -> null
    }
