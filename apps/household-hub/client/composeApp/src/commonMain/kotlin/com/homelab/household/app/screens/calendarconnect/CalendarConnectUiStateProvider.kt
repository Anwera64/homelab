package com.homelab.household.app.screens.calendarconnect

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.homelab.household.domain.model.CalendarProvider
import com.homelab.household.presentation.calendarconnect.CalendarConnectStatus
import com.homelab.household.presentation.calendarconnect.CalendarConnectUiState

/** Connecting a calendar, in each state worth drawing. `CalendarConnectScreenTest` renders them all. */
class CalendarConnectUiStateProvider : PreviewParameterProvider<CalendarConnectUiState> {
    private val apple =
        CalendarConnectUiState(
            provider = CalendarProvider.APPLE,
            account = "emma@icloud.com",
            password = "abcd-efgh-ijkl-mnop",
        )
    private val google =
        CalendarConnectUiState(
            provider = CalendarProvider.GOOGLE,
            account = "emma.larsson@gmail.com",
            password = "abcd efgh ijkl mnop",
        )

    private val named =
        listOf(
            "Apple, empty" to CalendarConnectUiState(provider = CalendarProvider.APPLE),
            "Apple, checking" to apple.copy(status = CalendarConnectStatus.Checking),
            "Apple, rejected" to apple.copy(password = "my-apple-id", status = CalendarConnectStatus.Rejected),
            "Google, filled in" to google,
            "Google, rejected" to google.copy(status = CalendarConnectStatus.Rejected),
            "Other, nothing typed yet" to
                CalendarConnectUiState(
                    provider = CalendarProvider.OTHER,
                    accountMissing = true,
                    passwordMissing = true,
                    serverMissing = true,
                ),
            "Other, server unreachable" to
                CalendarConnectUiState(
                    provider = CalendarProvider.OTHER,
                    account = "emma",
                    password = "secret",
                    server = "https://cloud.example.com/remote.php/dav",
                    status = CalendarConnectStatus.CalendarUnreachable,
                ),
            "Hub unreachable" to apple.copy(status = CalendarConnectStatus.HubUnreachable),
        )

    override val values: Sequence<CalendarConnectUiState> = named.map { it.second }.asSequence()

    override fun getDisplayName(index: Int): String = named[index].first
}
