package com.homelab.household.presentation.memories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.DeviceTimeZone
import com.homelab.household.domain.model.ProposalAnswer
import com.homelab.household.domain.usecase.AnswerChangeUseCase
import com.homelab.household.domain.usecase.GetChangesToCheckUseCase
import com.homelab.household.domain.usecase.UndoChangeAnswerUseCase
import com.homelab.household.domain.util.runCatchingSafe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Changes to check, opened from the memory page's band. Each answer saves in place and leaves a done
 * row with Undo, which lasts until the page is left.
 */
@OptIn(ExperimentalTime::class)
class ChangesToCheckViewModel(
    private val getChangesToCheck: GetChangesToCheckUseCase,
    answerChange: AnswerChangeUseCase,
    undoChangeAnswer: UndoChangeAnswerUseCase,
    private val deviceTimeZone: DeviceTimeZone,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChangesToCheckUiState())
    val uiState: StateFlow<ChangesToCheckUiState> = _uiState.asStateFlow()

    private val answers =
        ChangeAnswers(
            scope = viewModelScope,
            answerChange = answerChange,
            undoChangeAnswer = undoChangeAnswer,
            rows = { _uiState.value.toCheck },
            update = { change -> _uiState.update { it.copy(toCheck = change(it.toCheck)) } },
        )

    init {
        load()
    }

    /** Also the "Try again" when the hub didn't answer. */
    fun load() {
        _uiState.update { it.copy(status = MemoriesStatus.Loading) }
        viewModelScope.launch {
            runCatchingSafe { getChangesToCheck() }.fold(
                onSuccess = { changes ->
                    _uiState.update {
                        it.copy(
                            status = MemoriesStatus.Ready,
                            toCheck = changes.toCheck.map { card -> changeRowOf(card, deviceTimeZone, clock) },
                            waiting = changes.waiting.map { card -> changeRowOf(card, deviceTimeZone, clock) },
                        )
                    }
                },
                onFailure = { error ->
                    val status =
                        if (error is ServerOfflineException) MemoriesStatus.Unreachable else MemoriesStatus.Failed
                    _uiState.update { it.copy(status = status) }
                },
            )
        }
    }

    fun onAnswer(
        changeId: String,
        answer: ProposalAnswer,
    ) = answers.answer(changeId, answer)

    fun onUndo(changeId: String) = answers.undo(changeId)
}
