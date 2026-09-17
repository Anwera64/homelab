package com.homelab.household.app.screens.pinapprove

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.homelab.household.app.components.BentoCard
import com.homelab.household.app.components.CodeCard
import com.homelab.household.app.components.HearthScaffold
import com.homelab.household.app.components.HearthTopBar
import com.homelab.household.app.components.MemberAvatar
import com.homelab.household.app.components.PinField
import com.homelab.household.app.components.PrimaryButton
import com.homelab.household.app.components.SecondaryButton
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.approve_agree_access
import com.homelab.household.app.resources.approve_agree_label
import com.homelab.household.app.resources.approve_agree_limit
import com.homelab.household.app.resources.approve_back
import com.homelab.household.app.resources.approve_cancel
import com.homelab.household.app.resources.approve_code_instruction
import com.homelab.household.app.resources.approve_code_label
import com.homelab.household.app.resources.approve_detail
import com.homelab.household.app.resources.approve_generate
import com.homelab.household.app.resources.approve_locked
import com.homelab.household.app.resources.approve_pin_label
import com.homelab.household.app.resources.approve_title
import com.homelab.household.app.resources.approve_unreachable
import com.homelab.household.app.resources.approve_wrong_pin
import com.homelab.household.app.resources.invite_create_expires
import com.homelab.household.app.resources.members_failed
import com.homelab.household.app.theme.DayNightPreviews
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.app.util.asCountdown
import com.homelab.household.presentation.pinapprove.PinApproveStatus
import com.homelab.household.presentation.pinapprove.PinApproveUiState
import org.jetbrains.compose.resources.stringResource

/**
 * Vouching for someone who forgot their PIN. You hand back their door key, you do not copy it:
 * they get into their own account, and you get nothing of theirs.
 *
 * Stateless — [PinApproveScreen] owns the ViewModel.
 */
@Composable
fun PinApproveContent(
    state: PinApproveUiState,
    onPinChange: (String) -> Unit,
    onApprove: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    val approved = state.status as? PinApproveStatus.Approved

    HearthScaffold(
        modifier = modifier,
        header = { HearthTopBar(onBack = onBack, backDescription = stringResource(Res.string.approve_back)) },
        bottomBar = {
            if (approved == null) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(HearthTheme.spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md)
                ) {
                    PrimaryButton(
                        text = stringResource(Res.string.approve_generate),
                        onClick = onApprove,
                        modifier = Modifier.fillMaxWidth()
                    )
                    SecondaryButton(
                        text = stringResource(Res.string.approve_cancel),
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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
                    name = state.member.name,
                    colour = state.member.avatarColor,
                    size = HearthTheme.size.tile,
                    glyph = type.glyphXxl
                )
                Text(
                    stringResource(Res.string.approve_title, state.member.name),
                    style = type.title,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center
                )
                Text(
                    stringResource(Res.string.approve_detail),
                    style = type.body,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center
                )
            }

            if (approved == null) {
                BentoCard(modifier = Modifier.fillMaxWidth(), label = stringResource(Res.string.approve_agree_label)) {
                    Agreement(icon = HearthIcon.SecretOpen, text = stringResource(Res.string.approve_agree_access))
                    Agreement(icon = HearthIcon.SecretLocked, text = stringResource(Res.string.approve_agree_limit))
                }

                PinField(
                    value = state.pin,
                    onValueChange = onPinChange,
                    label = stringResource(Res.string.approve_pin_label),
                    contentDescription = stringResource(Res.string.approve_pin_label),
                    error = refusal(state.status),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                CodeCard(
                    label = stringResource(Res.string.approve_code_label),
                    code = approved.code,
                    expiry = stringResource(Res.string.invite_create_expires, state.secondsLeft.asCountdown())
                )
                Text(
                    stringResource(Res.string.approve_code_instruction, state.member.name),
                    style = type.caption,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun Agreement(icon: HearthIcon, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
        verticalAlignment = Alignment.Top
    ) {
        HearthIconImage(
            icon = icon,
            contentDescription = null,
            size = HearthTheme.size.iconMd,
            tint = HearthTheme.colors.textMuted
        )
        Text(text, style = HearthTheme.typography.label, color = HearthTheme.colors.textMuted)
    }
}

@Composable
private fun refusal(status: PinApproveStatus): String? = when (status) {
    is PinApproveStatus.WrongPin -> stringResource(Res.string.approve_wrong_pin, status.attemptsLeft)
    is PinApproveStatus.Locked -> stringResource(Res.string.approve_locked, status.secondsLeft)
    PinApproveStatus.Unreachable -> stringResource(Res.string.approve_unreachable)
    PinApproveStatus.Failed -> stringResource(Res.string.members_failed)
    else -> null
}

@DayNightPreviews
@Composable
private fun PinApproveContentPreview(
    @PreviewParameter(PinApproveUiStateProvider::class) state: PinApproveUiState
) {
    HearthTheme {
        PinApproveContent(state = state, onPinChange = {}, onApprove = {}, onBack = {})
    }
}
