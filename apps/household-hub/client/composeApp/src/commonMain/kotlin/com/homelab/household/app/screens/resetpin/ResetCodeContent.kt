package com.homelab.household.app.screens.resetpin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.homelab.household.app.components.CodeBoxes
import com.homelab.household.app.components.HearthScaffold
import com.homelab.household.app.components.HearthTopBar
import com.homelab.household.app.components.PrimaryButton
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.invite_code_continue
import com.homelab.household.app.resources.invite_code_incomplete
import com.homelab.household.app.resources.reset_code_back
import com.homelab.household.app.resources.reset_code_detail
import com.homelab.household.app.resources.reset_code_field
import com.homelab.household.app.resources.reset_code_title
import com.homelab.household.app.theme.DayNightPreviews
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.presentation.resetpin.ResetCodeUiState
import org.jetbrains.compose.resources.stringResource

/**
 * The code a housemate read out, or the hub printed. It is not checked here: a reset code is only
 * ever spent together with the new PIN it sets.
 *
 * Stateless — [ResetCodeScreen] owns the ViewModel.
 */
@Composable
fun ResetCodeContent(
    state: ResetCodeUiState,
    onCodeChange: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography

    HearthScaffold(
        modifier = modifier,
        header = { HearthTopBar(onBack = onBack, backDescription = stringResource(Res.string.reset_code_back)) },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth().padding(HearthTheme.spacing.xl)) {
                PrimaryButton(
                    text = stringResource(Res.string.invite_code_continue),
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xxl),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md)) {
                Text(stringResource(Res.string.reset_code_title), style = type.hero, color = colors.textPrimary)
                Text(stringResource(Res.string.reset_code_detail), style = type.body, color = colors.textMuted)
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.lg),
            ) {
                CodeBoxes(
                    code = state.code,
                    onCodeChange = onCodeChange,
                    contentDescription = stringResource(Res.string.reset_code_field),
                )
                if (state.incomplete) {
                    Text(
                        stringResource(Res.string.invite_code_incomplete),
                        style = type.caption,
                        color = colors.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@DayNightPreviews
@Composable
private fun ResetCodeContentPreview(
    @PreviewParameter(ResetCodeUiStateProvider::class) state: ResetCodeUiState,
) {
    HearthTheme {
        ResetCodeContent(state = state, onCodeChange = {}, onContinue = {}, onBack = {})
    }
}
