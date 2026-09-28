package com.homelab.household.presentation.calendarconnect

import com.homelab.household.domain.model.CalendarProvider

/**
 * The details one provider needs. Apple's server is fixed, so only [CalendarProvider.OTHER] asks for
 * [server]. Google never comes here: it signs in instead.
 */
data class CalendarConnectUiState(
    val provider: CalendarProvider,
    val account: String = "",
    val password: String = "",
    val server: String = "",
    val calendarName: String = "",
    val accountMissing: Boolean = false,
    val passwordMissing: Boolean = false,
    val serverMissing: Boolean = false,
    val status: CalendarConnectStatus = CalendarConnectStatus.Idle,
) {
    /** The address the hub will be given, shown read-only, or null when the member types it. */
    val presetServer: String? get() = provider.presetServer()
}
