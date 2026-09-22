package com.homelab.household.app.screens.invitecreate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.homelab.household.app.components.BentoCard
import com.homelab.household.app.components.CodeCard
import com.homelab.household.app.components.HearthScaffold
import com.homelab.household.app.components.HearthSwitch
import com.homelab.household.app.components.HearthTextField
import com.homelab.household.app.components.HearthTopBar
import com.homelab.household.app.components.PrimaryButton
import com.homelab.household.app.components.SecondaryButton
import com.homelab.household.app.components.SlowLine
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.a11y_invite_create_making
import com.homelab.household.app.resources.a11y_invite_create_remaking
import com.homelab.household.app.resources.invite_create_admin_caption
import com.homelab.household.app.resources.invite_create_admin_label
import com.homelab.household.app.resources.invite_create_back
import com.homelab.household.app.resources.invite_create_code_label
import com.homelab.household.app.resources.invite_create_copy
import com.homelab.household.app.resources.invite_create_expired
import com.homelab.household.app.resources.invite_create_expires
import com.homelab.household.app.resources.invite_create_footnote
import com.homelab.household.app.resources.invite_create_instruction
import com.homelab.household.app.resources.invite_create_making
import com.homelab.household.app.resources.invite_create_name_helper
import com.homelab.household.app.resources.invite_create_name_label
import com.homelab.household.app.resources.invite_create_name_missing
import com.homelab.household.app.resources.invite_create_new_code
import com.homelab.household.app.resources.invite_create_title
import com.homelab.household.app.resources.join_name_taken
import com.homelab.household.app.resources.join_name_too_long
import com.homelab.household.app.resources.members_failed
import com.homelab.household.app.resources.members_unreachable
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.app.theme.PreviewDayNight
import com.homelab.household.app.util.WaitPhase
import com.homelab.household.app.util.asCountdown
import com.homelab.household.app.util.rememberWaitPhase
import com.homelab.household.presentation.firstrun.NameError
import com.homelab.household.presentation.invitecreate.InviteCreateStatus
import com.homelab.household.presentation.invitecreate.InviteCreateUiState
import org.jetbrains.compose.resources.stringResource

/**
 * The admin names who is joining and reads out a code. They never invent a credential: the joiner
 * picks their own PIN when they redeem it.
 *
 * Stateless — [InviteCreateScreen] owns the ViewModel.
 */
@Composable
fun InviteCreateContent(
    state: InviteCreateUiState,
    onNameChange: (String) -> Unit,
    onAdminChange: (Boolean) -> Unit,
    onCreate: () -> Unit,
    onNewCode: () -> Unit,
    onCopy: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wait = rememberWaitPhase(state.status == InviteCreateStatus.Creating)
    val waiting = wait != WaitPhase.Hidden

    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    val invite = state.invite

    HearthScaffold(
        modifier = modifier,
        header = {
            HearthTopBar(
                onBack = onBack,
                backDescription = stringResource(Res.string.invite_create_back),
                title = stringResource(Res.string.invite_create_title),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding),
            verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xxl),
        ) {
            HearthTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = stringResource(Res.string.invite_create_name_label),
                contentDescription = stringResource(Res.string.invite_create_name_label),
                helper = if (state.nameError == null) stringResource(Res.string.invite_create_name_helper) else null,
                error = state.nameError?.let { nameErrorText(it) },
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                modifier = Modifier.padding(top = HearthTheme.spacing.lg),
            )

            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xxs),
                    ) {
                        Text(
                            stringResource(Res.string.invite_create_admin_label),
                            style = type.bodyStrong,
                            color = colors.textPrimary,
                        )
                        Text(
                            stringResource(Res.string.invite_create_admin_caption),
                            style = type.caption,
                            color = colors.textMuted,
                        )
                    }
                    HearthSwitch(
                        checked = state.isAdmin,
                        onCheckedChange = onAdminChange,
                        contentDescription = stringResource(Res.string.invite_create_admin_label),
                    )
                }
            }

            if (invite == null) {
                PrimaryButton(
                    text =
                        stringResource(
                            if (waiting) Res.string.invite_create_making else Res.string.invite_create_new_code,
                        ),
                    onClick = onCreate,
                    busy = waiting,
                    busyDescription = stringResource(Res.string.a11y_invite_create_making),
                    modifier = Modifier.fillMaxWidth(),
                )
                SlowLine(wait)
            } else {
                CodeCard(
                    label = stringResource(Res.string.invite_create_code_label),
                    code = invite.code,
                    expiry =
                        if (state.status is InviteCreateStatus.Expired) {
                            stringResource(Res.string.invite_create_expired)
                        } else {
                            stringResource(Res.string.invite_create_expires, state.secondsLeft.asCountdown())
                        },
                    loading = waiting,
                )
                Text(
                    text = stringResource(Res.string.invite_create_instruction, invite.invitedName),
                    style = type.caption,
                    color = colors.textMuted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md)) {
                    SecondaryButton(
                        text = stringResource(Res.string.invite_create_copy),
                        onClick = onCopy,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text =
                            stringResource(
                                if (waiting) Res.string.invite_create_making else Res.string.invite_create_new_code,
                            ),
                        onClick = onNewCode,
                        icon = HearthIcon.Retry,
                        busy = waiting,
                        busyDescription = stringResource(Res.string.a11y_invite_create_remaking),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            failure(state.status)?.let { failure ->
                Text(failure, style = type.caption, color = colors.error)
            }

            Text(
                text = stringResource(Res.string.invite_create_footnote),
                style = type.caption,
                color = colors.textMuted,
                modifier = Modifier.padding(bottom = HearthTheme.spacing.xxl),
            )
        }
    }
}

@Composable
private fun nameErrorText(error: NameError): String =
    when (error) {
        NameError.Missing -> stringResource(Res.string.invite_create_name_missing)
        NameError.TooLong -> stringResource(Res.string.join_name_too_long)
        NameError.Taken -> stringResource(Res.string.join_name_taken)
    }

@Composable
private fun failure(status: InviteCreateStatus): String? =
    when (status) {
        InviteCreateStatus.Unreachable -> stringResource(Res.string.members_unreachable)
        InviteCreateStatus.Failed -> stringResource(Res.string.members_failed)
        else -> null
    }

@PreviewDayNight
@Composable
private fun InviteCreateContentPreview(
    @PreviewParameter(InviteCreateUiStateProvider::class) state: InviteCreateUiState,
) {
    HearthTheme {
        InviteCreateContent(
            state = state,
            onNameChange = {},
            onAdminChange = {},
            onCreate = {},
            onNewCode = {},
            onCopy = {},
            onBack = {},
        )
    }
}
