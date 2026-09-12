package com.homelab.household.presentation.viewmodel.memoryaudit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.household.domain.model.MemoryScope
import com.homelab.household.domain.usecase.AuditMemoriesUseCase
import com.homelab.household.domain.usecase.RevokeMemoryUseCase
import com.homelab.household.domain.usecase.RevokeMilestoneUseCase
import com.homelab.household.domain.usecase.UpdateMemoryUseCase
import com.homelab.household.presentation.viewmodel.memoryaudit.model.MemoryAuditUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MemoryAuditViewModel(
    private val auditMemoriesUseCase: AuditMemoriesUseCase,
    private val deleteMemoryUseCase: RevokeMemoryUseCase,
    private val updateMemoryUseCase: UpdateMemoryUseCase,
    private val revokeMilestoneUseCase: RevokeMilestoneUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoryAuditUiState())
    val uiState: StateFlow<MemoryAuditUiState> = _uiState.asStateFlow()

    fun loadAudit(scope: MemoryScope? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, selectedScope = scope, errorMessage = null) }
            try {
                val memories = auditMemoriesUseCase(scope)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        memories = memories
                    )
                }
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load memories"
                    )
                }
            }
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            try {
                deleteMemoryUseCase(memoryId)
                loadAudit(_uiState.value.selectedScope)
            } catch (e: Throwable) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to revoke memory") }
            }
        }
    }

    fun revokeMilestone(milestoneId: String) {
        viewModelScope.launch {
            try {
                revokeMilestoneUseCase(milestoneId)
                _uiState.update { state ->
                    state.copy(userMilestones = state.userMilestones.filterNot { it.id == milestoneId })
                }
            } catch (e: Throwable) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to revoke milestone") }
            }
        }
    }
}
