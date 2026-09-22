package com.homelab.household.app.screens.pinforgot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.household.domain.model.Member
import com.homelab.household.presentation.pinforgot.PinForgotViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** How to get back in when the six digits have gone. */
@Composable
fun PinForgotScreen(
    member: Member,
    onBack: () -> Unit,
    onHaveCode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: PinForgotViewModel = koinViewModel(parameters = { parametersOf(member) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    PinForgotContent(state = state, onHaveCode = onHaveCode, onBack = onBack, modifier = modifier)
}
