package com.homelab.household.presentation.memories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.DeviceTimeZone
import com.homelab.household.domain.model.MemoryItem
import com.homelab.household.domain.model.WaitingChange
import com.homelab.household.domain.usecase.GetMemoryPageUseCase
import com.homelab.household.domain.usecase.RemoveMemoryUseCase
import com.homelab.household.domain.usecase.RestoreChangeUseCase
import com.homelab.household.domain.usecase.RestoreMemoryUseCase
import com.homelab.household.domain.usecase.WithdrawChangeUseCase
import com.homelab.household.domain.util.runCatchingSafe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * "What the agents know": your memories and the household's. ✕ is one tap and turns the row into
 * "Removed" with Undo at once; the hub's answer only matters if it says no. Removed rows are this
 * page's own memory, so they are gone once the page loads again.
 */
@OptIn(ExperimentalTime::class)
class MemoriesViewModel(
    private val getMemoryPage: GetMemoryPageUseCase,
    private val removeMemory: RemoveMemoryUseCase,
    private val restoreMemory: RestoreMemoryUseCase,
    private val withdrawChange: WithdrawChangeUseCase,
    private val restoreChange: RestoreChangeUseCase,
    private val deviceTimeZone: DeviceTimeZone,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MemoriesUiState())
    val uiState: StateFlow<MemoriesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** Also the "Try again" when the hub didn't answer. */
    fun load() {
        _uiState.update { it.copy(status = MemoriesStatus.Loading) }
        viewModelScope.launch {
            runCatchingSafe { getMemoryPage() }.fold(
                onSuccess = { page ->
                    _uiState.update {
                        it.copy(
                            status = MemoriesStatus.Ready,
                            yours = page.yours.map(::rowOf),
                            household = page.household.map(::rowOf),
                            toCheck = page.toCheck,
                            waiting = page.waiting.map(::waitingOf),
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

    /**
     * Coming back from Changes to check: the band recounts and the lists pick up what an answer added
     * or retired, without the loading rows.
     */
    fun refresh() {
        viewModelScope.launch {
            runCatchingSafe { getMemoryPage() }.onSuccess { page ->
                _uiState.update {
                    it.copy(
                        yours = page.yours.map(::rowOf),
                        household = page.household.map(::rowOf),
                        toCheck = page.toCheck,
                        waiting = page.waiting.map(::waitingOf),
                    )
                }
            }
        }
    }

    fun onTab(tab: MemoryTab) = _uiState.update { it.copy(tab = tab) }

    fun onRemove(memoryId: String) {
        val row = rowOf(memoryId) ?: return
        if (!row.item.canRemove || row.removed) return
        mark(memoryId, removed = true, clearFailure = true)
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

    fun onUndo(memoryId: String) {
        if (rowOf(memoryId)?.removed != true) return
        mark(memoryId, removed = false, clearFailure = true)
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

    /** ✕ on a waiting card: it turns into "Withdrawn · Undo" at once, as a removed row does. */
    fun onWithdraw(changeId: String) {
        if (waitingOf(changeId)?.withdrawn != false) return
        markWithdrawn(changeId, withdrawn = true, clearFailure = true)
        viewModelScope.launch {
            runCatchingSafe { withdrawChange(changeId) }.onFailure { error ->
                markWithdrawn(changeId, withdrawn = false)
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

    fun onUndoWithdraw(changeId: String) {
        if (waitingOf(changeId)?.withdrawn != true) return
        markWithdrawn(changeId, withdrawn = false, clearFailure = true)
        viewModelScope.launch {
            runCatchingSafe { restoreChange(changeId) }.onFailure { error ->
                markWithdrawn(changeId, withdrawn = true)
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

    private fun waitingOf(changeId: String): WaitingRow? =
        _uiState.value.waiting.firstOrNull {
            it.change.id == changeId
        }

    private fun markWithdrawn(
        changeId: String,
        withdrawn: Boolean,
        clearFailure: Boolean = false,
    ) = _uiState.update { state ->
        state.copy(
            waiting = state.waiting.map { if (it.change.id == changeId) it.copy(withdrawn = withdrawn) else it },
            failure = if (clearFailure) null else state.failure,
        )
    }

    private fun waitingOf(change: WaitingChange): WaitingRow {
        val now = clock.now()
        return WaitingRow(
            change = change,
            age = memoryAge(change.createdAt, now, deviceTimeZone.offsetSecondsAt(now)),
            stored = rowOf(change.stored),
        )
    }

    private fun rowOf(memoryId: String): MemoryRow? =
        _uiState.value.let { state -> (state.yours + state.household).firstOrNull { it.item.id == memoryId } }

    /** A tap starts clean: the last failure's line goes as the member acts again. */
    private fun mark(
        memoryId: String,
        removed: Boolean,
        clearFailure: Boolean = false,
    ) = _uiState.update { state ->
        fun List<MemoryRow>.marked() = map { if (it.item.id == memoryId) it.copy(removed = removed) else it }
        state.copy(
            yours = state.yours.marked(),
            household = state.household.marked(),
            failure = if (clearFailure) null else state.failure,
        )
    }

    private fun rowOf(item: MemoryItem): MemoryRow = memoryRowOf(item, deviceTimeZone, clock)
}
