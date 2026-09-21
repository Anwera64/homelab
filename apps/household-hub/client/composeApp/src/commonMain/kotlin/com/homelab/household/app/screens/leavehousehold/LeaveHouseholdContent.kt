package com.homelab.household.app.screens.leavehousehold

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.homelab.household.app.components.ConsequenceCards
import com.homelab.household.app.components.DestructiveButton
import com.homelab.household.app.components.HearthScaffold
import com.homelab.household.app.components.HearthTopBar
import com.homelab.household.app.components.PinField
import com.homelab.household.app.components.SecondaryButton
import com.homelab.household.app.components.SlowLine
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.a11y_leave_deleting
import com.homelab.household.app.resources.change_pin_not_six_digits
import com.homelab.household.app.resources.leave_back
import com.homelab.household.app.resources.leave_cancel
import com.homelab.household.app.resources.leave_deleting
import com.homelab.household.app.resources.leave_detail
import com.homelab.household.app.resources.leave_erased_calendar
import com.homelab.household.app.resources.leave_erased_chats
import com.homelab.household.app.resources.leave_erased_space
import com.homelab.household.app.resources.leave_erased_title
import com.homelab.household.app.resources.leave_locked
import com.homelab.household.app.resources.leave_pin_label
import com.homelab.household.app.resources.leave_shared_note
import com.homelab.household.app.resources.leave_sole_admin
import com.homelab.household.app.resources.leave_stays_agents
import com.homelab.household.app.resources.leave_stays_shared
import com.homelab.household.app.resources.leave_stays_title
import com.homelab.household.app.resources.leave_submit
import com.homelab.household.app.resources.leave_title
import com.homelab.household.app.resources.leave_unreachable
import com.homelab.household.app.resources.leave_wrong_pin
import com.homelab.household.app.resources.members_failed
import com.homelab.household.app.theme.DayNightPreviews
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.app.util.WaitPhase
import com.homelab.household.app.util.rememberWaitPhase
import com.homelab.household.presentation.leavehousehold.LeaveHouseholdStatus
import com.homelab.household.presentation.leavehousehold.LeaveHouseholdUiState
import org.jetbrains.compose.resources.stringResource

/**
 * Leaving for good, confirmed with your own PIN — the one destructive action only you can take.
 * The filled red button is the exception the canvas draws: everything else destructive is outlined.
 *
 * Stateless — [LeaveHouseholdScreen] owns the ViewModel.
 */
@Composable
fun LeaveHouseholdContent(
    state: LeaveHouseholdUiState,
    onPinChange: (String) -> Unit,
    onLeave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val wait = rememberWaitPhase(state.status == LeaveHouseholdStatus.Leaving)
    val waiting = wait != WaitPhase.Hidden

    val colors = HearthTheme.colors
    val type = HearthTheme.typography

    HearthScaffold(
        modifier = modifier,
        header = { HearthTopBar(onBack = onBack, backDescription = stringResource(Res.string.leave_back)) },
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(HearthTheme.spacing.xl),
                verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md)
            ) {
                DestructiveButton(
                    text = stringResource(if (waiting) Res.string.leave_deleting else Res.string.leave_submit),
                    onClick = onLeave,
                    busy = waiting,
                    busyDescription = stringResource(Res.string.a11y_leave_deleting),
                    modifier = Modifier.fillMaxWidth()
                )
                SecondaryButton(
                    text = stringResource(Res.string.leave_cancel),
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth()
                )
                SlowLine(wait)
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
                Text(stringResource(Res.string.leave_title), style = type.hero, color = colors.textPrimary)
                Text(stringResource(Res.string.leave_detail), style = type.body, color = colors.textMuted)
            }

            ConsequenceCards(
                erasedTitle = stringResource(Res.string.leave_erased_title),
                erased = listOf(
                    stringResource(Res.string.leave_erased_chats),
                    stringResource(Res.string.leave_erased_space),
                    stringResource(Res.string.leave_erased_calendar)
                ),
                staysTitle = stringResource(Res.string.leave_stays_title),
                stays = listOf(
                    stringResource(Res.string.leave_stays_shared),
                    stringResource(Res.string.leave_stays_agents)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Text(stringResource(Res.string.leave_shared_note), style = type.caption, color = colors.textMuted)

            PinField(
                value = state.pin,
                onValueChange = onPinChange,
                label = stringResource(Res.string.leave_pin_label),
                contentDescription = stringResource(Res.string.leave_pin_label),
                error = refusal(state.status),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun refusal(status: LeaveHouseholdStatus): String? = when (status) {
    LeaveHouseholdStatus.NotSixDigits -> stringResource(Res.string.change_pin_not_six_digits)
    is LeaveHouseholdStatus.WrongPin -> stringResource(Res.string.leave_wrong_pin, status.attemptsLeft)
    is LeaveHouseholdStatus.Locked -> stringResource(Res.string.leave_locked, status.secondsLeft)
    LeaveHouseholdStatus.SoleAdmin -> stringResource(Res.string.leave_sole_admin)
    LeaveHouseholdStatus.Unreachable -> stringResource(Res.string.leave_unreachable)
    LeaveHouseholdStatus.Failed -> stringResource(Res.string.members_failed)
    else -> null
}

@DayNightPreviews
@Composable
private fun LeaveHouseholdContentPreview(
    @PreviewParameter(LeaveHouseholdUiStateProvider::class) state: LeaveHouseholdUiState
) {
    HearthTheme {
        LeaveHouseholdContent(state = state, onPinChange = {}, onLeave = {}, onBack = {})
    }
}
