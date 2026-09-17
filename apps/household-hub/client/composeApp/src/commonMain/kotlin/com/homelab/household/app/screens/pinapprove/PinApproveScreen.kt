package com.homelab.household.app.screens.pinapprove

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.household.domain.model.Member
import com.homelab.household.presentation.pinapprove.PinApproveViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Vouching for a housemate: your own PIN, then the code you read out to them. */
@Composable
fun PinApproveScreen(
    member: Member,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: PinApproveViewModel = koinViewModel(parameters = { parametersOf(member) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    PinApproveContent(
        state = state,
        onPinChange = viewModel::onPinChange,
        onApprove = viewModel::approve,
        onBack = onBack,
        modifier = modifier
    )
}
