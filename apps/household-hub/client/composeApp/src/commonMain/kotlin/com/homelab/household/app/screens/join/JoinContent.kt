package com.homelab.household.app.screens.join

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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.foundation.text.KeyboardOptions
import com.homelab.household.app.components.ColourSwatches
import com.homelab.household.app.components.HearthChip
import com.homelab.household.app.components.HearthScaffold
import com.homelab.household.app.components.HearthTextField
import com.homelab.household.app.components.MemberAvatar
import com.homelab.household.app.components.PinField
import com.homelab.household.app.components.PrimaryButton
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.join_colour_helper
import com.homelab.household.app.resources.join_colour_label
import com.homelab.household.app.resources.join_colour_swatch
import com.homelab.household.app.resources.join_detail
import com.homelab.household.app.resources.join_expired
import com.homelab.household.app.resources.join_invited_by
import com.homelab.household.app.resources.join_name_helper
import com.homelab.household.app.resources.join_name_label
import com.homelab.household.app.resources.join_name_missing
import com.homelab.household.app.resources.join_name_taken
import com.homelab.household.app.resources.join_name_too_long
import com.homelab.household.app.resources.join_pin_helper
import com.homelab.household.app.resources.join_pin_label
import com.homelab.household.app.resources.join_pin_not_six_digits
import com.homelab.household.app.resources.join_privacy
import com.homelab.household.app.resources.join_submit
import com.homelab.household.app.resources.join_title
import com.homelab.household.app.resources.members_failed
import com.homelab.household.app.resources.members_unreachable
import com.homelab.household.app.theme.DayNightPreviews
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.presentation.firstrun.AvatarPalette
import com.homelab.household.presentation.firstrun.NameError
import com.homelab.household.presentation.firstrun.PinError
import com.homelab.household.presentation.join.JoinStatus
import com.homelab.household.presentation.join.JoinUiState
import org.jetbrains.compose.resources.stringResource

/**
 * Where the joiner picks their own PIN. The admin never invents a credential: the name comes from
 * the invite and can be corrected, and a colour somebody here already wears is not on offer.
 *
 * Stateless — [JoinScreen] owns the ViewModel.
 */
@Composable
fun JoinContent(
    state: JoinUiState,
    onNameChange: (String) -> Unit,
    onPinChange: (String) -> Unit,
    onColourSelect: (String) -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    val inviter = state.preview.inviterName
    val count = AvatarPalette.swatches.size

    HearthScaffold(
        modifier = modifier,
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(HearthTheme.spacing.xl),
                verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md)
            ) {
                PrimaryButton(
                    text = stringResource(Res.string.join_submit),
                    onClick = onJoin,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(Res.string.join_privacy, inviter),
                    style = type.caption,
                    color = colors.textMuted
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding),
            verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xxl)
        ) {
            Column(
                modifier = Modifier.padding(top = HearthTheme.spacing.xl),
                verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MemberAvatar(
                        name = inviter,
                        colour = state.preview.inviterAvatarColor,
                        size = HearthTheme.spacing.xxxl,
                        glyph = type.glyphMd
                    )
                    Text(stringResource(Res.string.join_invited_by, inviter), style = type.label, color = colors.textMuted)
                }
                Text(stringResource(Res.string.join_title), style = type.hero, color = colors.textPrimary)
                Text(stringResource(Res.string.join_detail, inviter), style = type.body, color = colors.textMuted)
            }

            HearthTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = stringResource(Res.string.join_name_label),
                contentDescription = stringResource(Res.string.join_name_label),
                helper = if (state.nameError == null) stringResource(Res.string.join_name_helper, inviter) else null,
                error = state.nameError?.let { nameErrorText(it) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                )
            )

            PinField(
                value = state.pin,
                onValueChange = onPinChange,
                label = stringResource(Res.string.join_pin_label),
                contentDescription = stringResource(Res.string.join_pin_label),
                helper = if (state.pinError == null) stringResource(Res.string.join_pin_helper) else null,
                error = state.pinError?.let { pinErrorText(it) }
            )

            Column(verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md)) {
                Text(stringResource(Res.string.join_colour_label), style = type.labelStrong, color = colors.textMuted)
                ColourSwatches(
                    swatches = AvatarPalette.swatches,
                    selected = state.colour,
                    taken = state.takenColours,
                    onSelect = onColourSelect,
                    swatchDescription = { index -> stringResource(Res.string.join_colour_swatch, index + 1, count) }
                )
                Text(stringResource(Res.string.join_colour_helper), style = type.caption, color = colors.textMuted)
            }

            refusal(state.status)?.let { refusal ->
                Text(refusal, style = type.caption, color = colors.error)
            }
        }
    }
}

@Composable
private fun nameErrorText(error: NameError): String = when (error) {
    NameError.Missing -> stringResource(Res.string.join_name_missing)
    NameError.TooLong -> stringResource(Res.string.join_name_too_long)
    NameError.Taken -> stringResource(Res.string.join_name_taken)
}

@Composable
private fun pinErrorText(error: PinError): String = when (error) {
    PinError.NotSixDigits -> stringResource(Res.string.join_pin_not_six_digits)
}

@Composable
private fun refusal(status: JoinStatus): String? = when (status) {
    JoinStatus.Idle, JoinStatus.Joining -> null
    JoinStatus.Expired -> stringResource(Res.string.join_expired)
    JoinStatus.Unreachable -> stringResource(Res.string.members_unreachable)
    JoinStatus.Failed -> stringResource(Res.string.members_failed)
}

@DayNightPreviews
@Composable
private fun JoinContentPreview(
    @PreviewParameter(JoinUiStateProvider::class) state: JoinUiState
) {
    HearthTheme {
        JoinContent(state = state, onNameChange = {}, onPinChange = {}, onColourSelect = {}, onJoin = {})
    }
}
