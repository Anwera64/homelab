package com.homelab.household.presentation.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.household.domain.model.ConversationSession
import com.homelab.household.domain.usecase.ListSessionsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Chats tab: the conversations, and the search that runs over them.
 *
 * Search is on-device for the MVP. The list the hub returns is already the whole list, so
 * filtering it here costs nothing, works offline over whatever is loaded, and needed no backend
 * work at all — which is why the screen's footnote says plainly what it cannot do, so a miss is
 * not mistaken for a lost chat. Full-text search comes after the MVP.
 */
class ChatsViewModel(
    private val listSessionsUseCase: ListSessionsUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatsUiState())
    val uiState: StateFlow<ChatsUiState> = _uiState.asStateFlow()

    private var all: List<ConversationSession> = emptyList()

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                all = listSessionsUseCase()
                _uiState.update {
                    it.copy(isLoading = false, loaded = true, total = all.size, visible = matching(it.query))
                }
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load chats")
                }
            }
        }
    }

    fun search(query: String) {
        _uiState.update { it.copy(query = query, visible = matching(query)) }
    }

    fun cancelSearch() = search("")

    /**
     * Secret chats are never a search result, locked or not — a title you can find is a title you
     * have read. Off a blank query the list is whole, secret rows included, because that is the
     * list itself rather than a search of it.
     */
    private fun matching(query: String): List<ConversationSession> {
        if (query.isBlank()) return all
        return all.filter { session ->
            !session.isSecret &&
                (
                    session.title.contains(query, ignoreCase = true) ||
                        session.agentName?.contains(query, ignoreCase = true) == true
                )
        }
    }
}
