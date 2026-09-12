package com.homelab.household.presentation.launch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.household.domain.exception.NotFoundException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.exception.UnexpectedContentTypeException
import com.homelab.household.domain.exception.UpstreamGatewayException
import com.homelab.household.domain.usecase.CheckAuthStatusUseCase
import com.homelab.household.domain.usecase.GetHubHostUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LaunchViewModel(
    private val checkAuthStatusUseCase: CheckAuthStatusUseCase,
    private val getHubHostUseCase: GetHubHostUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(LaunchUiState(hubAddress = getHubHostUseCase()))
    val uiState: StateFlow<LaunchUiState> = _uiState.asStateFlow()

    private val _events = Channel<LaunchEvent>(Channel.BUFFERED)
    val events: Flow<LaunchEvent> = _events.receiveAsFlow()

    init {
        checkHub()
    }

    /** Also the "Try again" of the offline screen. */
    fun checkHub() {
        _uiState.update { it.copy(status = HubStatus.Checking) }

        viewModelScope.launch {
            val status = runCatching { checkAuthStatusUseCase() }
                .fold(
                    onSuccess = { authStatus ->
                        if (authStatus.isInitialized) {
                            HubStatus.Ready(memberCount = authStatus.memberCount)
                        } else {
                            HubStatus.FirstRun
                        }
                    },
                    onFailure = { error ->
                        when (error) {
                            is ServerOfflineException -> HubStatus.Unreachable
                            is NotFoundException -> HubStatus.Failed(HubFailure.AddressNotFound)
                            is UpstreamGatewayException -> HubStatus.Failed(HubFailure.Upstream(error.statusCode))
                            is UnexpectedContentTypeException -> HubStatus.Failed(HubFailure.NotJson(error.contentType))
                            else -> HubStatus.Failed(HubFailure.Unknown)
                        }
                    }
                )

            _uiState.update { it.copy(status = status) }

            when (status) {
                is HubStatus.Ready -> _events.send(LaunchEvent.GoToSignIn)
                HubStatus.FirstRun -> _events.send(LaunchEvent.GoToFirstRun)
                else -> Unit
            }
        }
    }
}
