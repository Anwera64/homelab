package com.homelab.household.presentation.memories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.DeviceTimeZone
import com.homelab.household.domain.model.ProposalAnswer
import com.homelab.household.domain.usecase.AnswerChangeUseCase
import com.homelab.household.domain.usecase.GetLookedOverUseCase
import com.homelab.household.domain.usecase.RemoveMemoryUseCase
import com.homelab.household.domain.usecase.RestoreMemoryUseCase
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
 * "Remembered from this chat", opened from the quiet line in a chat. Changes are answered in place,
 * as on Changes to check, and each remembered memory has ✕ with Undo, as on the memory page.
 */
@OptIn(ExperimentalTime::class)
class LookedOverViewModel(
    private val getLookedOver: GetLookedOverUseCase,
    answerChange: AnswerChangeUseCase,
    undoChangeAnswer: UndoChangeAnswerUseCase,
    private val removeMemory: RemoveMemoryUseCase,
    private val restoreMemory: RestoreMemoryUseCase,
    private val deviceTimeZone: DeviceTimeZone,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LookedOverUiState())
    val uiState: StateFlow<LookedOverUiState> = _uiState.asStateFlow()

    private var sessionId: String? = null

    private val answers =
        ChangeAnswers(
            scope = viewModelScope,
            answerChange = answerChange,
            undoChangeAnswer = undoChangeAnswer,
            rows = { _uiState.value.toCheck },
            update = { change -> _uiState.update { it.copy(toCheck = change(it.toCheck)) } },
        )

    /** Each opening starts fresh: what was answered or removed last time is read from the hub again. */
    fun open(sessionId: String) {
        this.sessionId = sessionId
        _uiState.value = LookedOverUiState()
        load(sessionId)
    }

    fun retry() {
        sessionId?.let(::load)
    }

    private fun load(sessionId: String) {
        _uiState.update { it.copy(status = MemoriesStatus.Loading) }
        viewModelScope.launch {
            runCatchingSafe { getLookedOver(sessionId) }.fold(
                onSuccess = { sheet ->
                    _uiState.update {
                        it.copy(
                            status = MemoriesStatus.Ready,
                            at = localTime(sheet.at, deviceTimeZone.offsetSecondsAt(sheet.at)),
                            toCheck = sheet.toCheck.map { card -> changeRowOf(card, deviceTimeZone, clock) },
                            waiting = sheet.waiting.map { card -> changeRowOf(card, deviceTimeZone, clock) },
                            remembered = sheet.remembered.map { item -> memoryRowOf(item, deviceTimeZone, clock) },
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

    fun onRemove(memoryId: String) {
        val row = _uiState.value.remembered.firstOrNull { it.item.id == memoryId } ?: return
        if (!row.item.canRemove || row.removed) return
        mark(memoryId, removed = true)
        viewModelScope.launch {
            runCatchingSafe { removeMemory(memoryId) }.onFailure { error ->
                mark(memoryId, removed = false)
                val failure =
                    if (error is ServerOfflineException) {
                        MemoryActionFailure.RemoveUnreachable
                    } else {
                        MemoryActionFailure.RemoveFailed
                    }
                _uiState.update { it.copy(failure = failure) }
            }
        }
    }

    fun onRestore(memoryId: String) {
        if (_uiState.value.remembered.firstOrNull { it.item.id == memoryId }?.removed != true) return
        mark(memoryId, removed = false)
        viewModelScope.launch {
            runCatchingSafe { restoreMemory(memoryId) }.onFailure { error ->
                mark(memoryId, removed = true)
                val failure =
                    if (error is ServerOfflineException) {
                        MemoryActionFailure.UndoUnreachable
                    } else {
                        MemoryActionFailure.UndoFailed
                    }
                _uiState.update { it.copy(failure = failure) }
            }
        }
    }

    /** A tap starts clean: the last failure's line goes as the member acts again. */
    private fun mark(
        memoryId: String,
        removed: Boolean,
    ) = _uiState.update { state ->
        state.copy(
            remembered = state.remembered.map { if (it.item.id == memoryId) it.copy(removed = removed) else it },
            failure = null,
        )
    }
}
