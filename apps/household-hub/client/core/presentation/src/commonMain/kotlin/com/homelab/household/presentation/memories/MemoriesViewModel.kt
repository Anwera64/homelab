package com.homelab.household.presentation.memories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.DeviceTimeZone
import com.homelab.household.domain.model.MemoryItem
import com.homelab.household.domain.usecase.GetMemoryPageUseCase
import com.homelab.household.domain.usecase.RemoveMemoryUseCase
import com.homelab.household.domain.usecase.RestoreMemoryUseCase
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

    /** Coming back from Changes to check: only the band's count is asked for again. */
    fun refreshToCheck() {
        viewModelScope.launch {
            runCatchingSafe { getMemoryPage() }.onSuccess { page -> _uiState.update { it.copy(toCheck = page.toCheck) } }
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

    private fun rowOf(item: MemoryItem): MemoryRow {
        val now = clock.now()
        return MemoryRow(
            item = item,
            age = memoryAge(item.createdAt, now, deviceTimeZone.offsetSecondsAt(now)),
            until = item.expiresAt?.let { localDay(it, deviceTimeZone.offsetSecondsAt(it)) },
        )
    }
}
