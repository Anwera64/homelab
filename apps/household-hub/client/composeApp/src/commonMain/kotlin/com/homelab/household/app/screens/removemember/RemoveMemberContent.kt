package com.homelab.household.app.screens.removemember

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.homelab.household.app.components.ConsequenceCards
import com.homelab.household.app.components.HearthScaffold
import com.homelab.household.app.components.HearthTextField
import com.homelab.household.app.components.HearthTopBar
import com.homelab.household.app.components.MemberAvatar
import com.homelab.household.app.components.PrimaryButton
import com.homelab.household.app.components.SecondaryButton
import com.homelab.household.app.components.SlowLine
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.a11y_remove_removing
import com.homelab.household.app.resources.members_failed
import com.homelab.household.app.resources.remove_agents_note
import com.homelab.household.app.resources.remove_back
import com.homelab.household.app.resources.remove_confirm_label
import com.homelab.household.app.resources.remove_confirm_mismatch
import com.homelab.household.app.resources.remove_detail
import com.homelab.household.app.resources.remove_erased_calendar
import com.homelab.household.app.resources.remove_erased_chats
import com.homelab.household.app.resources.remove_erased_space
import com.homelab.household.app.resources.remove_erased_title
import com.homelab.household.app.resources.remove_keep
import com.homelab.household.app.resources.remove_removing
import com.homelab.household.app.resources.remove_stays_agents
import com.homelab.household.app.resources.remove_stays_shared
import com.homelab.household.app.resources.remove_stays_title
import com.homelab.household.app.resources.remove_submit
import com.homelab.household.app.resources.remove_title
import com.homelab.household.app.resources.remove_unreachable
import com.homelab.household.app.theme.DayNightPreviews
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.app.util.WaitPhase
import com.homelab.household.app.util.rememberWaitPhase
import com.homelab.household.presentation.removemember.RemoveMemberStatus
import com.homelab.household.presentation.removemember.RemoveMemberUiState
import org.jetbrains.compose.resources.stringResource

/**
 * Removing someone. Friction matches damage, so their name has to be typed out, and both lists say
 * what goes and what stays before the button means anything.
 *
 * Stateless — [RemoveMemberScreen] owns the ViewModel.
 */
@Composable
fun RemoveMemberContent(
    state: RemoveMemberUiState,
    onNameChange: (String) -> Unit,
    onRemove: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val wait = rememberWaitPhase(state.status == RemoveMemberStatus.Removing)
    val waiting = wait != WaitPhase.Hidden

    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    val name = state.member.name

    HearthScaffold(
        modifier = modifier,
        header = { HearthTopBar(onBack = onBack, backDescription = stringResource(Res.string.remove_back)) },
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(HearthTheme.spacing.xl),
                verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md)
            ) {
                PrimaryButton(
                    text = if (waiting) {
                        stringResource(Res.string.remove_removing)
                    } else {
                        stringResource(Res.string.remove_submit, name)
                    },
                    onClick = onRemove,
                    busy = waiting,
                    busyDescription = stringResource(Res.string.a11y_remove_removing, name),
                    modifier = Modifier.fillMaxWidth()
                )
                SecondaryButton(
                    text = stringResource(Res.string.remove_keep),
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth()
                )
                SlowLine(wait)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xxl)
        ) {
            Column(
                modifier = Modifier.padding(top = HearthTheme.spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.lg)
            ) {
                MemberAvatar(
                    name = name,
                    colour = state.member.avatarColor,
                    size = HearthTheme.size.tile,
                    glyph = type.glyphXxl
                )
                Text(
                    stringResource(Res.string.remove_title, name),
                    style = type.title,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center
                )
                Text(
                    stringResource(Res.string.remove_detail),
                    style = type.body,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center
                )
            }

            ConsequenceCards(
                erasedTitle = stringResource(Res.string.remove_erased_title),
                erased = listOf(
                    stringResource(Res.string.remove_erased_chats),
                    stringResource(Res.string.remove_erased_space),
                    stringResource(Res.string.remove_erased_calendar)
                ),
                staysTitle = stringResource(Res.string.remove_stays_title),
                stays = listOf(
                    stringResource(Res.string.remove_stays_shared),
                    stringResource(Res.string.remove_stays_agents)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Text(stringResource(Res.string.remove_agents_note), style = type.caption, color = colors.textMuted)

            HearthTextField(
                value = state.typedName,
                onValueChange = onNameChange,
                label = stringResource(Res.string.remove_confirm_label, name),
                contentDescription = stringResource(Res.string.remove_confirm_label, name),
                error = if (state.nameMismatch) stringResource(Res.string.remove_confirm_mismatch) else null,
                modifier = Modifier.fillMaxWidth()
            )

            failure(state.status)?.let { failure ->
                Text(failure, style = type.caption, color = colors.error)
            }
        }
    }
}

@Composable
private fun failure(status: RemoveMemberStatus): String? = when (status) {
    RemoveMemberStatus.Unreachable -> stringResource(Res.string.remove_unreachable)
    RemoveMemberStatus.Failed -> stringResource(Res.string.members_failed)
    else -> null
}

@DayNightPreviews
@Composable
private fun RemoveMemberContentPreview(
    @PreviewParameter(RemoveMemberUiStateProvider::class) state: RemoveMemberUiState
) {
    HearthTheme {
        RemoveMemberContent(state = state, onNameChange = {}, onRemove = {}, onBack = {})
    }
}
