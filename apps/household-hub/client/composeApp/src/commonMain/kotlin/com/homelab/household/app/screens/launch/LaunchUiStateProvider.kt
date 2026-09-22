package com.homelab.household.app.screens.launch

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.homelab.household.presentation.launch.HubFailure
import com.homelab.household.presentation.launch.HubStatus
import com.homelab.household.presentation.launch.LaunchUiState

/**
 * Every state launch can be in: checking, and unavailable for each [HubFailure], because each
 * reason has its own wording. The previews draw them all, and `LaunchScreenTest` renders them
 * all — so a new status or reason without a state here is a failing test.
 */
class LaunchUiStateProvider : PreviewParameterProvider<LaunchUiState> {
    override val values: Sequence<LaunchUiState> =
        sequenceOf(
            HubStatus.Checking,
            HubStatus.Unavailable(HubFailure.NoRoute),
            HubStatus.Unavailable(HubFailure.Upstream(statusCode = 500)),
            HubStatus.Unavailable(HubFailure.NotJson(contentType = "text/html")),
            HubStatus.Unavailable(HubFailure.AddressNotFound),
            HubStatus.Unavailable(HubFailure.Unknown),
        ).map { status ->
            LaunchUiState(
                hubAddress = "hub.spicy-llama.duckdns.org",
                status = status,
                retryInSeconds = if (status is HubStatus.Unavailable) 8 else null,
            )
        }

    override fun getDisplayName(index: Int): String =
        when (val status = values.elementAt(index).status) {
            HubStatus.Checking -> "Checking"
            is HubStatus.Unavailable -> "Unavailable · ${status.reason}"
        }
}
