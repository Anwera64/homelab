package com.homelab.household.app.screens.calendarchoose

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.homelab.household.domain.model.CalendarSource
import com.homelab.household.presentation.calendarchoose.CalendarChooseStatus
import com.homelab.household.presentation.calendarchoose.CalendarChooseUiState

/** Choosing calendars, in each state worth drawing (canvas: CalPickA, CalPickLoading, CalPickFailed). */
class CalendarChooseUiStateProvider : PreviewParameterProvider<CalendarChooseUiState> {
    private val ready =
        CalendarChooseUiState(
            status = CalendarChooseStatus.Ready,
            calendars =
                listOf(
                    CalendarSource(key = "c1", name = "Home", writable = true, picked = true),
                    CalendarSource(key = "c2", name = "Work", writable = true, picked = true),
                    CalendarSource(key = "c3", name = "Family", writable = true, picked = true),
                    CalendarSource(key = "c4", name = "Holidays in Spain", writable = false, picked = false),
                ),
        )

    override val values =
        sequenceOf(
            ready,
            CalendarChooseUiState(),
            CalendarChooseUiState(status = CalendarChooseStatus.LoadFailed),
            ready.copy(saveFailed = true),
        )
}
