package com.homelab.household.presentation.calendarchoose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.household.domain.model.CalendarSource
import com.homelab.household.domain.usecase.GetCalendarSourcesUseCase
import com.homelab.household.domain.usecase.PickCalendarsUseCase
import com.homelab.household.domain.util.runCatchingSafe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Which of the account's calendars agents read (#252). Switches change only the screen; Done saves
 * them all at once, and says nothing to the hub when nothing changed.
 */
class CalendarChooseViewModel(
    private val getCalendars: GetCalendarSourcesUseCase,
    private val pickCalendars: PickCalendarsUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CalendarChooseUiState())
    val uiState: StateFlow<CalendarChooseUiState> = _uiState.asStateFlow()

    private val _events = Channel<CalendarChooseEvent>(Channel.BUFFERED)
    val events: Flow<CalendarChooseEvent> = _events.receiveAsFlow()

    /** As the hub keeps them, to tell whether Done has anything to save. */
    private var kept: List<CalendarSource> = emptyList()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(status = CalendarChooseStatus.Loading) }
        viewModelScope.launch {
            runCatchingSafe { getCalendars() }.fold(
                onSuccess = { calendars ->
                    kept = calendars
                    _uiState.update { it.copy(status = CalendarChooseStatus.Ready, calendars = calendars) }
                },
                onFailure = { _uiState.update { it.copy(status = CalendarChooseStatus.LoadFailed) } },
            )
        }
    }

    fun onToggle(key: String) {
        _uiState.update { state ->
            state.copy(
                calendars = state.calendars.map { if (it.key == key) it.copy(picked = !it.picked) else it },
                saveFailed = false,
            )
        }
    }

    fun onDone() {
        val state = _uiState.value
        if (state.saving) return
        if (state.calendars == kept) {
            _events.trySend(CalendarChooseEvent.Done)
            return
        }
        _uiState.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            runCatchingSafe { pickCalendars(state.calendars.filter { it.picked }.map { it.key }) }.fold(
                onSuccess = { saved ->
                    kept = saved
                    _uiState.update { it.copy(saving = false, calendars = saved) }
                    _events.send(CalendarChooseEvent.Done)
                },
                onFailure = { _uiState.update { it.copy(saving = false, saveFailed = true) } },
            )
        }
    }
}
