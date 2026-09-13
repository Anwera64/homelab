package com.homelab.household.app.screens.launch

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.homelab.household.presentation.launch.HubFailure
import com.homelab.household.presentation.launch.HubStatus
import com.homelab.household.presentation.launch.LaunchUiState

/**
 * Every state launch can be in, one per [HubStatus]. The previews draw them all, and
 * `LaunchScreenTest` renders them all — so a new status without a state here is a failing test.
 */
class LaunchUiStateProvider : PreviewParameterProvider<LaunchUiState> {

    override val values: Sequence<LaunchUiState> = sequenceOf(
        HubStatus.Checking,
        HubStatus.Ready(memberCount = 2),
        HubStatus.FirstRun,
        HubStatus.Unreachable,
        HubStatus.Failed(HubFailure.Upstream(statusCode = 500))
    ).map { status ->
        LaunchUiState(
            hubAddress = "hub.spicy-llama.duckdns.org",
            status = status,
            retryInSeconds = if (status is HubStatus.Unreachable) 8 else null
        )
    }

    override fun getDisplayName(index: Int): String =
        when (val status = values.elementAt(index).status) {
            HubStatus.Checking -> "Checking"
            is HubStatus.Ready -> "Ready · ${status.memberCount} members"
            HubStatus.FirstRun -> "First run"
            HubStatus.Unreachable -> "Unreachable"
            is HubStatus.Failed -> "Failed"
        }
}
