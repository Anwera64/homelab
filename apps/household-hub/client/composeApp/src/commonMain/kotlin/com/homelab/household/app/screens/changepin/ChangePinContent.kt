package com.homelab.household.app.screens.changepin

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
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.homelab.household.app.components.BentoCard
import com.homelab.household.app.components.HearthScaffold
import com.homelab.household.app.components.HearthTopBar
import com.homelab.household.app.components.PinField
import com.homelab.household.app.components.PrimaryButton
import com.homelab.household.app.components.SlowLine
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.a11y_change_pin_saving
import com.homelab.household.app.resources.change_pin_again
import com.homelab.household.app.resources.change_pin_again_placeholder
import com.homelab.household.app.resources.change_pin_back
import com.homelab.household.app.resources.change_pin_current
import com.homelab.household.app.resources.change_pin_detail
import com.homelab.household.app.resources.change_pin_locked
import com.homelab.household.app.resources.change_pin_mismatch
import com.homelab.household.app.resources.change_pin_new
import com.homelab.household.app.resources.change_pin_new_helper
import com.homelab.household.app.resources.change_pin_not_six_digits
import com.homelab.household.app.resources.change_pin_saving
import com.homelab.household.app.resources.change_pin_submit
import com.homelab.household.app.resources.change_pin_title
import com.homelab.household.app.resources.change_pin_unreachable
import com.homelab.household.app.resources.change_pin_warning
import com.homelab.household.app.resources.change_pin_warning_detail
import com.homelab.household.app.resources.change_pin_wrong
import com.homelab.household.app.resources.members_failed
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.app.theme.PreviewDayNight
import com.homelab.household.app.util.WaitPhase
import com.homelab.household.app.util.rememberWaitPhase
import com.homelab.household.presentation.changepin.ChangePinStatus
import com.homelab.household.presentation.changepin.ChangePinUiState
import com.homelab.household.presentation.changepin.CurrentPinError
import com.homelab.household.presentation.firstrun.PinError
import org.jetbrains.compose.resources.stringResource

/**
 * Changing your PIN, which signs out every other device: you may be changing it because somebody
 * saw it, so the old sessions have to die.
 *
 * Stateless — [ChangePinScreen] owns the ViewModel.
 */
@Composable
fun ChangePinContent(
    state: ChangePinUiState,
    onCurrentChange: (String) -> Unit,
    onNewChange: (String) -> Unit,
    onAgainChange: (String) -> Unit,
    onChange: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wait = rememberWaitPhase(state.status == ChangePinStatus.Saving)
    val waiting = wait != WaitPhase.Hidden

    val colors = HearthTheme.colors
    val type = HearthTheme.typography

    HearthScaffold(
        modifier = modifier,
        header = { HearthTopBar(onBack = onBack, backDescription = stringResource(Res.string.change_pin_back)) },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth().padding(HearthTheme.spacing.xl)) {
                PrimaryButton(
                    text =
                        stringResource(
                            if (waiting) Res.string.change_pin_saving else Res.string.change_pin_submit,
                        ),
                    onClick = onChange,
                    busy = waiting,
                    busyDescription = stringResource(Res.string.a11y_change_pin_saving),
                    modifier = Modifier.fillMaxWidth(),
                )
                SlowLine(wait)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding),
            verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xxl),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md)) {
                Text(stringResource(Res.string.change_pin_title), style = type.hero, color = colors.textPrimary)
                Text(stringResource(Res.string.change_pin_detail), style = type.body, color = colors.textMuted)
            }

            PinField(
                value = state.current,
                onValueChange = onCurrentChange,
                label = stringResource(Res.string.change_pin_current),
                contentDescription = stringResource(Res.string.change_pin_current),
                error = state.currentError?.let { currentErrorText(it) },
                imeAction = ImeAction.Next,
            )

            PinField(
                value = state.new,
                onValueChange = onNewChange,
                label = stringResource(Res.string.change_pin_new),
                contentDescription = stringResource(Res.string.change_pin_new),
                helper = if (state.newError == null) stringResource(Res.string.change_pin_new_helper) else null,
                error = state.newError?.let { stringResource(Res.string.change_pin_not_six_digits) },
                imeAction = ImeAction.Next,
            )

            PinField(
                value = state.again,
                onValueChange = onAgainChange,
                label = stringResource(Res.string.change_pin_again),
                contentDescription = stringResource(Res.string.change_pin_again),
                placeholder = stringResource(Res.string.change_pin_again_placeholder),
                error = if (state.againMismatch) stringResource(Res.string.change_pin_mismatch) else null,
            )

            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
                    verticalAlignment = Alignment.Top,
                ) {
                    HearthIconImage(
                        icon = HearthIcon.Warning,
                        contentDescription = null,
                        size = HearthTheme.size.iconMd,
                        tint = colors.secondary,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xxs)) {
                        Text(
                            stringResource(Res.string.change_pin_warning),
                            style = type.bodyStrong,
                            color = colors.textPrimary,
                        )
                        Text(
                            stringResource(Res.string.change_pin_warning_detail),
                            style = type.caption,
                            color = colors.textMuted,
                        )
                    }
                }
            }

            failure(state.status)?.let { failure ->
                Text(failure, style = type.caption, color = colors.error)
            }
        }
    }
}

@Composable
private fun currentErrorText(error: CurrentPinError): String =
    when (error) {
        CurrentPinError.NotSixDigits -> stringResource(Res.string.change_pin_not_six_digits)
        is CurrentPinError.Wrong -> stringResource(Res.string.change_pin_wrong, error.attemptsLeft)
    }

@Composable
private fun failure(status: ChangePinStatus): String? =
    when (status) {
        is ChangePinStatus.Locked -> stringResource(Res.string.change_pin_locked, status.secondsLeft)
        ChangePinStatus.Unreachable -> stringResource(Res.string.change_pin_unreachable)
        ChangePinStatus.Failed -> stringResource(Res.string.members_failed)
        else -> null
    }

@PreviewDayNight
@Composable
private fun ChangePinContentPreview(
    @PreviewParameter(ChangePinUiStateProvider::class) state: ChangePinUiState,
) {
    HearthTheme {
        ChangePinContent(
            state = state,
            onCurrentChange = {},
            onNewChange = {},
            onAgainChange = {},
            onChange = {},
            onBack = {},
        )
    }
}
