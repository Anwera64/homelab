package com.homelab.household.app.screens.resetpin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.homelab.household.app.components.HearthScaffold
import com.homelab.household.app.components.PinField
import com.homelab.household.app.components.PrimaryButton
import com.homelab.household.app.components.SlowLine
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.a11y_new_pin_setting
import com.homelab.household.app.resources.change_pin_mismatch
import com.homelab.household.app.resources.change_pin_not_six_digits
import com.homelab.household.app.resources.invite_code_locked
import com.homelab.household.app.resources.invite_code_unreachable
import com.homelab.household.app.resources.members_failed
import com.homelab.household.app.resources.new_pin_again
import com.homelab.household.app.resources.new_pin_detail
import com.homelab.household.app.resources.new_pin_label
import com.homelab.household.app.resources.new_pin_setting
import com.homelab.household.app.resources.new_pin_submit
import com.homelab.household.app.resources.new_pin_title
import com.homelab.household.app.resources.reset_code_invalid
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.app.theme.PreviewDayNight
import com.homelab.household.app.util.WaitPhase
import com.homelab.household.app.util.rememberWaitPhase
import com.homelab.household.presentation.resetpin.NewPinStatus
import com.homelab.household.presentation.resetpin.NewPinUiState
import org.jetbrains.compose.resources.stringResource

/**
 * The PIN the reset code sets, typed twice so a slip cannot lock anyone out again.
 *
 * Stateless — [NewPinScreen] owns the ViewModel.
 */
@Composable
fun NewPinContent(
    state: NewPinUiState,
    onPinChange: (String) -> Unit,
    onAgainChange: (String) -> Unit,
    onSetPin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wait = rememberWaitPhase(state.status == NewPinStatus.Setting)
    val waiting = wait != WaitPhase.Hidden

    val colors = HearthTheme.colors
    val type = HearthTheme.typography

    HearthScaffold(
        modifier = modifier,
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth().padding(HearthTheme.spacing.xl)) {
                PrimaryButton(
                    text = stringResource(if (waiting) Res.string.new_pin_setting else Res.string.new_pin_submit),
                    onClick = onSetPin,
                    busy = waiting,
                    busyDescription = stringResource(Res.string.a11y_new_pin_setting),
                    modifier = Modifier.fillMaxWidth(),
                )
                SlowLine(wait)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xxl),
        ) {
            Column(
                modifier = Modifier.padding(top = HearthTheme.spacing.huge),
                verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
            ) {
                Text(stringResource(Res.string.new_pin_title), style = type.hero, color = colors.textPrimary)
                Text(stringResource(Res.string.new_pin_detail), style = type.body, color = colors.textMuted)
            }

            PinField(
                value = state.pin,
                onValueChange = onPinChange,
                label = stringResource(Res.string.new_pin_label),
                contentDescription = stringResource(Res.string.new_pin_label),
                error = state.pinError?.let { stringResource(Res.string.change_pin_not_six_digits) },
                imeAction = ImeAction.Next,
            )

            PinField(
                value = state.again,
                onValueChange = onAgainChange,
                label = stringResource(Res.string.new_pin_again),
                contentDescription = stringResource(Res.string.new_pin_again),
                error = if (state.againMismatch) stringResource(Res.string.change_pin_mismatch) else null,
            )

            failure(state.status)?.let { failure ->
                Text(failure, style = type.caption, color = colors.error)
            }
        }
    }
}

@Composable
private fun failure(status: NewPinStatus): String? =
    when (status) {
        NewPinStatus.Invalid -> stringResource(Res.string.reset_code_invalid)
        is NewPinStatus.Locked -> stringResource(Res.string.invite_code_locked, status.secondsLeft)
        NewPinStatus.Unreachable -> stringResource(Res.string.invite_code_unreachable)
        NewPinStatus.Failed -> stringResource(Res.string.members_failed)
        else -> null
    }

@PreviewDayNight
@Composable
private fun NewPinContentPreview(
    @PreviewParameter(NewPinUiStateProvider::class) state: NewPinUiState,
) {
    HearthTheme {
        NewPinContent(state = state, onPinChange = {}, onAgainChange = {}, onSetPin = {})
    }
}
