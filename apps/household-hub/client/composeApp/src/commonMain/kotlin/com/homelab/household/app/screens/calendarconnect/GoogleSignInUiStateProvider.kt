package com.homelab.household.app.screens.calendarconnect

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.homelab.household.domain.model.CalendarSignInFailure
import com.homelab.household.presentation.googlesignin.GoogleSignInStatus
import com.homelab.household.presentation.googlesignin.GoogleSignInUiState

/** Signing in with Google, in each state worth drawing. `GoogleCalendarSignInScreenTest` renders them all. */
class GoogleSignInUiStateProvider : PreviewParameterProvider<GoogleSignInUiState> {
    private val named =
        listOf(
            "Ready" to GoogleSignInUiState(),
            "In the browser" to GoogleSignInUiState(GoogleSignInStatus.InBrowser),
            "Calendar not shared" to GoogleSignInUiState(GoogleSignInStatus.Failed(CalendarSignInFailure.DENIED)),
            "Took too long" to GoogleSignInUiState(GoogleSignInStatus.Failed(CalendarSignInFailure.EXPIRED)),
            "Hub couldn't reach Google" to
                GoogleSignInUiState(GoogleSignInStatus.Failed(CalendarSignInFailure.UNREACHABLE)),
            "Hub unreachable" to GoogleSignInUiState(GoogleSignInStatus.HubUnreachable),
            "Hub not set up for Google" to GoogleSignInUiState(GoogleSignInStatus.Unavailable),
        )

    override val values: Sequence<GoogleSignInUiState> = named.map { it.second }.asSequence()

    override fun getDisplayName(index: Int): String = named[index].first
}
