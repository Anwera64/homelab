package com.homelab.household.app.screens.invitecode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.homelab.household.app.components.CodeBoxes
import com.homelab.household.app.components.HearthScaffold
import com.homelab.household.app.components.HearthTopBar
import com.homelab.household.app.components.PrimaryButton
import com.homelab.household.app.components.SecondaryButton
import com.homelab.household.app.components.SlowLine
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.a11y_invite_code_checking
import com.homelab.household.app.resources.invite_code_back
import com.homelab.household.app.resources.invite_code_checking
import com.homelab.household.app.resources.invite_code_continue
import com.homelab.household.app.resources.invite_code_detail
import com.homelab.household.app.resources.invite_code_failed
import com.homelab.household.app.resources.invite_code_field
import com.homelab.household.app.resources.invite_code_incomplete
import com.homelab.household.app.resources.invite_code_invalid
import com.homelab.household.app.resources.invite_code_locked
import com.homelab.household.app.resources.invite_code_note
import com.homelab.household.app.resources.invite_code_paste
import com.homelab.household.app.resources.invite_code_title
import com.homelab.household.app.resources.invite_code_unreachable
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.app.theme.PreviewDayNight
import com.homelab.household.app.util.WaitPhase
import com.homelab.household.app.util.rememberWaitPhase
import com.homelab.household.presentation.invitecode.InviteCodeStatus
import com.homelab.household.presentation.invitecode.InviteCodeUiState
import org.jetbrains.compose.resources.stringResource

/**
 * The way into a household you are not on the picker of: six boxes rather than one field, so a
 * mistyped character is obvious, and Paste for a code that arrived over chat.
 *
 * Stateless — [InviteCodeScreen] owns the ViewModel.
 */
@Composable
fun InviteCodeContent(
    state: InviteCodeUiState,
    onCodeChange: (String) -> Unit,
    onPaste: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wait = rememberWaitPhase(state.status == InviteCodeStatus.Checking)
    val waiting = wait != WaitPhase.Hidden

    val colors = HearthTheme.colors
    val type = HearthTheme.typography

    HearthScaffold(
        modifier = modifier,
        header = { HearthTopBar(onBack = onBack, backDescription = stringResource(Res.string.invite_code_back)) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xxl),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md)) {
                Text(stringResource(Res.string.invite_code_title), style = type.hero, color = colors.textPrimary)
                Text(stringResource(Res.string.invite_code_detail), style = type.body, color = colors.textMuted)
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.lg),
            ) {
                CodeBoxes(
                    code = state.code,
                    onCodeChange = onCodeChange,
                    contentDescription = stringResource(Res.string.invite_code_field),
                )
                SecondaryButton(text = stringResource(Res.string.invite_code_paste), onClick = onPaste)
                refusal(state.status)?.let { refusal ->
                    Text(refusal, style = type.caption, color = colors.error, textAlign = TextAlign.Center)
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.lg, Alignment.Bottom),
            ) {
                Text(
                    text = stringResource(Res.string.invite_code_note),
                    style = type.caption,
                    color = colors.textMuted,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceAlt, HearthShapes.item)
                            .padding(HearthTheme.spacing.lg),
                )
                Column(
                    modifier = Modifier.padding(bottom = HearthTheme.spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm),
                ) {
                    PrimaryButton(
                        text =
                            stringResource(
                                if (waiting) Res.string.invite_code_checking else Res.string.invite_code_continue,
                            ),
                        onClick = onContinue,
                        busy = waiting,
                        busyDescription = stringResource(Res.string.a11y_invite_code_checking),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SlowLine(wait)
                }
            }
        }
    }
}

/** What the hub said about the code, under the boxes where the problem is. */
@Composable
private fun refusal(status: InviteCodeStatus): String? =
    when (status) {
        InviteCodeStatus.Idle, InviteCodeStatus.Checking -> null
        InviteCodeStatus.Incomplete -> stringResource(Res.string.invite_code_incomplete)
        InviteCodeStatus.Invalid -> stringResource(Res.string.invite_code_invalid)
        is InviteCodeStatus.Locked -> stringResource(Res.string.invite_code_locked, status.secondsLeft)
        InviteCodeStatus.Unreachable -> stringResource(Res.string.invite_code_unreachable)
        InviteCodeStatus.Failed -> stringResource(Res.string.invite_code_failed)
    }

@PreviewDayNight
@Composable
private fun InviteCodeContentPreview(
    @PreviewParameter(InviteCodeUiStateProvider::class) state: InviteCodeUiState,
) {
    HearthTheme {
        InviteCodeContent(state = state, onCodeChange = {}, onPaste = {}, onContinue = {}, onBack = {})
    }
}
