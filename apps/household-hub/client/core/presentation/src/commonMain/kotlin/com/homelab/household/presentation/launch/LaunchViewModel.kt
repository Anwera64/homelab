package com.homelab.household.presentation.launch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.household.domain.exception.NotFoundException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.exception.UnexpectedContentTypeException
import com.homelab.household.domain.exception.UpstreamGatewayException
import com.homelab.household.domain.usecase.CheckAuthStatusUseCase
import com.homelab.household.domain.usecase.GetHubHostUseCase
import com.homelab.household.domain.util.runCatchingSafe
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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

    private var autoRetry: Job? = null

    init {
        checkHub()
    }

    /** Also the "Try again" of the offline screen, which cancels the countdown to asking by itself. */
    fun checkHub() {
        autoRetry?.cancel()
        autoRetry = null
        _uiState.update { it.copy(status = HubStatus.Checking, retryInSeconds = null) }

        viewModelScope.launch {
            runCatchingSafe { checkAuthStatusUseCase() }
                .onSuccess { authStatus ->
                    _events.send(if (authStatus.isInitialized) LaunchEvent.GoToSignIn else LaunchEvent.GoToFirstRun)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(status = HubStatus.Unavailable(whyUnavailable(error))) }
                    countDownToAskingAgain()
                }
        }
    }

    private fun whyUnavailable(error: Throwable): HubFailure = when (error) {
        is ServerOfflineException -> HubFailure.NoRoute
        is NotFoundException -> HubFailure.AddressNotFound
        is UpstreamGatewayException -> HubFailure.Upstream(error.statusCode)
        is UnexpectedContentTypeException -> HubFailure.NotJson(error.contentType)
        else -> HubFailure.Unknown
    }

    private fun countDownToAskingAgain() {
        autoRetry = viewModelScope.launch {
            for (left in AUTO_RETRY_SECONDS downTo 1) {
                _uiState.update { it.copy(retryInSeconds = left) }
                delay(1_000)
            }
            autoRetry = null
            checkHub()
        }
    }

    private companion object {
        const val AUTO_RETRY_SECONDS = 10
    }
}
