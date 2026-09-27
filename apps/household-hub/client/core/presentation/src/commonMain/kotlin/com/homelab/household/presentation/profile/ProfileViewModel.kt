package com.homelab.household.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.CalendarConnection
import com.homelab.household.domain.usecase.GetCalendarUseCase
import com.homelab.household.domain.usecase.GetCurrentUserUseCase
import com.homelab.household.domain.usecase.ListHouseholdMembersUseCase
import com.homelab.household.domain.usecase.LogoutUseCase
import com.homelab.household.domain.usecase.RemoveCalendarUseCase
import com.homelab.household.domain.util.runCatchingSafe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class ProfileViewModel(
    private val getCurrentUser: GetCurrentUserUseCase,
    private val listHouseholdMembers: ListHouseholdMembersUseCase,
    private val logout: LogoutUseCase,
    private val getCalendar: GetCalendarUseCase,
    private val removeCalendar: RemoveCalendarUseCase,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _events = Channel<ProfileEvent>(Channel.BUFFERED)
    val events: Flow<ProfileEvent> = _events.receiveAsFlow()

    init {
        load()
    }

    /**
     * Also the "Try again" when the hub didn't answer.
     *
     * The two asks are separate because they can fail separately: who you are is kept on this
     * phone and answers offline, while the household has to come from the hub. Showing the member
     * without the household is the offline profile — a name, a colour, and a line saying the hub
     * could not be reached — so the failure of the second must not throw away the first.
     */
    fun load() {
        _uiState.update { it.copy(status = ProfileStatus.Loading) }
        viewModelScope.launch {
            val member =
                runCatchingSafe { getCurrentUser() }
                    .onSuccess { _uiState.update { state -> state.copy(member = it) } }

            runCatchingSafe { listHouseholdMembers() }.fold(
                onSuccess = { household ->
                    // Nothing can promote anyone, so the only admin cannot leave (design notes §4).
                    val soleAdmin = member.getOrNull()?.isAdmin == true && household.count { it.isAdmin } <= 1
                    _uiState.update { it.copy(isSoleAdmin = soleAdmin, status = ProfileStatus.Ready) }
                },
                onFailure = { error -> _uiState.update { it.copy(status = failureOf(error)) } },
            )

            // A member this phone could not name is a failure of its own, whatever the household did.
            member.onFailure { error -> _uiState.update { it.copy(status = failureOf(error)) } }
        }
    }

    /**
     * Asked each time the profile is shown rather than once, so coming back from connecting a
     * calendar shows the new one. A failure leaves the row making no claim: the hub failing is
     * already said once, by [load].
     */
    fun loadCalendar() {
        viewModelScope.launch {
            val row =
                runCatchingSafe { getCalendar() }.fold(
                    onSuccess = { connection -> connection?.let(::rowOf) ?: CalendarRow.None },
                    onFailure = { CalendarRow.Unknown },
                )
            _uiState.update { it.copy(calendar = row) }
        }
    }

    /**
     * The row only changes once the hub has said yes. A failure leaves the calendar connected, since
     * that's still true, and says why.
     */
    fun onDisconnectCalendar() {
        _uiState.update { it.copy(calendarDisconnect = CalendarDisconnect.Disconnecting) }
        viewModelScope.launch {
            runCatchingSafe { removeCalendar() }.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(calendar = CalendarRow.None, calendarDisconnect = CalendarDisconnect.Idle)
                    }
                },
                onFailure = { error ->
                    val failure =
                        when (error) {
                            is ServerOfflineException -> CalendarDisconnect.Unreachable
                            else -> CalendarDisconnect.Failed
                        }
                    _uiState.update { it.copy(calendarDisconnect = failure) }
                },
            )
        }
    }

    private fun rowOf(connection: CalendarConnection) =
        CalendarRow.Connected(
            provider = connection.provider,
            account = connection.account,
            minutesAgo = connection.connectedAt?.let(::minutesSince),
        )

    /** The hub writes its times in UTC, and not always with the zone on the end. */
    private fun minutesSince(stamp: String): Long? {
        val time = stamp.substringAfter('T', missingDelimiterValue = "")
        val zoned = if (time.endsWith('Z') || '+' in time || '-' in time) stamp else "${stamp}Z"
        return try {
            (clock.now() - Instant.parse(zoned)).inWholeMinutes.coerceAtLeast(0)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun failureOf(error: Throwable): ProfileStatus =
        if (error is ServerOfflineException) ProfileStatus.Unreachable else ProfileStatus.Failed

    fun onSignOut() {
        viewModelScope.launch {
            logout()
            _events.send(ProfileEvent.SignedOut)
        }
    }
}
