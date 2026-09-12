package com.homelab.household.app.screens.launch

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.homelab.household.presentation.launch.HubStatus
import com.homelab.household.presentation.launch.LaunchUiState

/**
 * Every state launch can be in, one per [HubStatus]. The previews draw them all, and
 * `LaunchContentTest` renders them all — so a new status without a state here is a failing test.
 */
class LaunchUiStateProvider : PreviewParameterProvider<LaunchUiState> {

    override val values: Sequence<LaunchUiState> = sequenceOf(
        HubStatus.Checking,
        HubStatus.Ready(memberCount = 2),
        HubStatus.FirstRun,
        HubStatus.Unreachable,
        HubStatus.Failed(message = "Unexpected status 500")
    ).map { LaunchUiState(hubAddress = "hub.spicy-llama.duckdns.org", status = it) }

    override fun getDisplayName(index: Int): String =
        when (val status = values.elementAt(index).status) {
            HubStatus.Checking -> "Checking"
            is HubStatus.Ready -> "Ready · ${status.memberCount} members"
            HubStatus.FirstRun -> "First run"
            HubStatus.Unreachable -> "Unreachable"
            is HubStatus.Failed -> "Failed"
        }
}
