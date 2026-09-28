package com.homelab.household.app.screens.calendarconnect

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.homelab.household.app.components.HearthScaffold
import com.homelab.household.app.components.HearthTextField
import com.homelab.household.app.components.HearthTopBar
import com.homelab.household.app.components.PrimaryButton
import com.homelab.household.app.components.SecondaryButton
import com.homelab.household.app.components.SlowLine
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.a11y_calendar_checking
import com.homelab.household.app.resources.calendar_account_missing
import com.homelab.household.app.resources.calendar_apple_account
import com.homelab.household.app.resources.calendar_apple_instructions
import com.homelab.household.app.resources.calendar_apple_password
import com.homelab.household.app.resources.calendar_apple_password_helper
import com.homelab.household.app.resources.calendar_apple_rejected
import com.homelab.household.app.resources.calendar_apple_rejected_detail
import com.homelab.household.app.resources.calendar_apple_step_one
import com.homelab.household.app.resources.calendar_apple_step_three
import com.homelab.household.app.resources.calendar_apple_step_two
import com.homelab.household.app.resources.calendar_apple_warning
import com.homelab.household.app.resources.calendar_apple_warning_detail
import com.homelab.household.app.resources.calendar_back
import com.homelab.household.app.resources.calendar_checking
import com.homelab.household.app.resources.calendar_connect
import com.homelab.household.app.resources.calendar_hub_unreachable
import com.homelab.household.app.resources.calendar_name
import com.homelab.household.app.resources.calendar_name_placeholder
import com.homelab.household.app.resources.calendar_nothing_saved
import com.homelab.household.app.resources.calendar_other_account
import com.homelab.household.app.resources.calendar_other_password
import com.homelab.household.app.resources.calendar_other_rejected
import com.homelab.household.app.resources.calendar_other_rejected_detail
import com.homelab.household.app.resources.calendar_other_warning
import com.homelab.household.app.resources.calendar_other_warning_detail
import com.homelab.household.app.resources.calendar_password_missing
import com.homelab.household.app.resources.calendar_password_rejected
import com.homelab.household.app.resources.calendar_server
import com.homelab.household.app.resources.calendar_server_missing
import com.homelab.household.app.resources.calendar_server_placeholder
import com.homelab.household.app.resources.calendar_server_preset
import com.homelab.household.app.resources.calendar_try_again
import com.homelab.household.app.resources.calendar_unreachable
import com.homelab.household.app.resources.members_failed
import com.homelab.household.app.screens.calendarprovider.CalendarProviderMark
import com.homelab.household.app.screens.calendarprovider.providerName
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.app.theme.PreviewDayNight
import com.homelab.household.app.util.WaitPhase
import com.homelab.household.app.util.rememberWaitPhase
import com.homelab.household.domain.model.CalendarProvider
import com.homelab.household.presentation.calendarconnect.CalendarConnectStatus
import com.homelab.household.presentation.calendarconnect.CalendarConnectUiState
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The details a password calendar needs (boards "credentials + test" and "auth rejected"). Google
 * signs in instead ([GoogleCalendarSignInContent]). The app-password warning comes before the field it is about, and a refused
 * password swaps it for the steps to make one. Each failure lands under the field it is about.
 *
 * Stateless — [CalendarConnectScreen] owns the ViewModel.
 */
@Composable
fun CalendarConnectContent(
    state: CalendarConnectUiState,
    onAccountChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onServerChange: (String) -> Unit,
    onCalendarNameChange: (String) -> Unit,
    onConnect: () -> Unit,
    onOpenInstructions: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wait = rememberWaitPhase(state.status == CalendarConnectStatus.Checking)
    val waiting = wait != WaitPhase.Hidden
    val rejected = state.status == CalendarConnectStatus.Rejected
    val words = wordsFor(state.provider)

    val colors = HearthTheme.colors
    val type = HearthTheme.typography

    HearthScaffold(
        modifier = modifier,
        header = {
            HearthTopBar(
                onBack = onBack,
                backDescription = stringResource(Res.string.calendar_back),
                titleContent = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CalendarProviderMark(provider = state.provider)
                        Text(providerName(state.provider), style = type.heading, color = colors.textPrimary)
                    }
                },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(HearthTheme.spacing.xl),
                verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
            ) {
                PrimaryButton(
                    text =
                        stringResource(
                            when {
                                waiting -> Res.string.calendar_checking

                                // A quick answer never shows "Checking…", so the button keeps its label meanwhile.
                                state.status == CalendarConnectStatus.Idle ||
                                    state.status == CalendarConnectStatus.Checking -> Res.string.calendar_connect

                                else -> Res.string.calendar_try_again
                            },
                        ),
                    onClick = onConnect,
                    busy = waiting,
                    busyDescription = stringResource(Res.string.a11y_calendar_checking),
                    modifier = Modifier.fillMaxWidth(),
                )
                SlowLine(wait)
                if (rejected && words.instructions != null) {
                    SecondaryButton(
                        text = stringResource(words.instructions),
                        onClick = onOpenInstructions,
                        icon = HearthIcon.Document,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        stringResource(Res.string.calendar_nothing_saved),
                        modifier = Modifier.fillMaxWidth(),
                        style = type.caption,
                        color = colors.textMuted,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding),
            verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.lg),
        ) {
            if (rejected) {
                CalendarNotice(
                    title = stringResource(words.rejected),
                    detail = stringResource(words.rejectedDetail),
                    icon = HearthIcon.Error,
                    background = colors.errorContainer,
                    ink = colors.onErrorContainer,
                    steps = words.steps.map { stringResource(it) },
                )
            } else {
                CalendarNotice(
                    title = stringResource(words.warning),
                    detail = stringResource(words.warningDetail),
                    icon = HearthIcon.Warning,
                    background = colors.secondaryContainer,
                    ink = colors.onSecondaryContainer,
                )
            }

            HearthTextField(
                value = state.account,
                onValueChange = onAccountChange,
                label = stringResource(words.account),
                contentDescription = stringResource(words.account),
                error = if (state.accountMissing) stringResource(Res.string.calendar_account_missing) else null,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType =
                            if (state.provider == CalendarProvider.OTHER) KeyboardType.Text else KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
            )

            HearthTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = stringResource(words.password),
                contentDescription = stringResource(words.password),
                helper =
                    words.passwordHelper
                        ?.let {
                            stringResource(
                                it,
                            )
                        }?.takeIf { !rejected && !state.passwordMissing },
                error =
                    when {
                        state.passwordMissing -> stringResource(Res.string.calendar_password_missing)
                        rejected -> stringResource(Res.string.calendar_password_rejected)
                        else -> null
                    },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                visualTransformation = PasswordVisualTransformation(),
            )

            val unreachable =
                if (state.status == CalendarConnectStatus.CalendarUnreachable) {
                    stringResource(Res.string.calendar_unreachable)
                } else {
                    null
                }
            val preset = state.presetServer
            if (preset == null) {
                HearthTextField(
                    value = state.server,
                    onValueChange = onServerChange,
                    label = stringResource(Res.string.calendar_server),
                    contentDescription = stringResource(Res.string.calendar_server),
                    placeholder = stringResource(Res.string.calendar_server_placeholder),
                    error =
                        if (state.serverMissing) {
                            stringResource(
                                Res.string.calendar_server_missing,
                            )
                        } else {
                            unreachable
                        },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                )
            } else {
                PresetServer(address = preset, error = unreachable)
            }

            HearthTextField(
                value = state.calendarName,
                onValueChange = onCalendarNameChange,
                label = stringResource(Res.string.calendar_name),
                contentDescription = stringResource(Res.string.calendar_name),
                placeholder = stringResource(Res.string.calendar_name_placeholder),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )

            failure(state.status)?.let { failure ->
                Text(failure, style = type.caption, color = colors.error)
            }
        }
    }
}

/** Apple's fixed address: shown, never typed. */
@Composable
private fun PresetServer(
    address: String,
    error: String?,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm)) {
        Text(stringResource(Res.string.calendar_server), style = type.labelStrong, color = colors.textMuted)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = HearthTheme.size.touchTarget)
                    .background(colors.surfaceAlt, HearthShapes.item)
                    .border(
                        width = if (error != null) HearthTheme.size.emphasis else HearthTheme.size.hairline,
                        color = if (error != null) colors.error else colors.outlineSoft,
                        shape = HearthShapes.item,
                    ).padding(horizontal = HearthTheme.spacing.lg, vertical = HearthTheme.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(address, modifier = Modifier.weight(1f), style = type.mono, color = colors.textMuted)
            Text(
                stringResource(Res.string.calendar_server_preset),
                modifier =
                    Modifier
                        .background(colors.outlineSoft, HearthShapes.pill)
                        .padding(horizontal = HearthTheme.spacing.sm, vertical = HearthTheme.spacing.xs),
                style = type.monoSm,
                color = colors.textMuted,
            )
        }
        if (error != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm),
                verticalAlignment = Alignment.Top,
            ) {
                HearthIconImage(
                    icon = HearthIcon.Error,
                    contentDescription = null,
                    size = HearthTheme.size.iconSm,
                    tint = colors.error,
                    modifier = Modifier.padding(top = HearthTheme.spacing.xxs),
                )
                Text(error, style = type.caption, color = colors.error)
            }
        }
    }
}

/** Everything a provider words its own way. */
private class ProviderWords(
    val warning: StringResource,
    val warningDetail: StringResource,
    val account: StringResource,
    val password: StringResource,
    val passwordHelper: StringResource?,
    val rejected: StringResource,
    val rejectedDetail: StringResource,
    val steps: List<StringResource>,
    val instructions: StringResource?,
)

private fun wordsFor(provider: CalendarProvider): ProviderWords =
    when (provider) {
        CalendarProvider.APPLE -> {
            ProviderWords(
                warning = Res.string.calendar_apple_warning,
                warningDetail = Res.string.calendar_apple_warning_detail,
                account = Res.string.calendar_apple_account,
                password = Res.string.calendar_apple_password,
                passwordHelper = Res.string.calendar_apple_password_helper,
                rejected = Res.string.calendar_apple_rejected,
                rejectedDetail = Res.string.calendar_apple_rejected_detail,
                steps =
                    listOf(
                        Res.string.calendar_apple_step_one,
                        Res.string.calendar_apple_step_two,
                        Res.string.calendar_apple_step_three,
                    ),
                instructions = Res.string.calendar_apple_instructions,
            )
        }

        // Google never opens this screen: it signs in instead (GoogleCalendarSignInContent).
        CalendarProvider.GOOGLE, CalendarProvider.OTHER -> {
            ProviderWords(
                warning = Res.string.calendar_other_warning,
                warningDetail = Res.string.calendar_other_warning_detail,
                account = Res.string.calendar_other_account,
                password = Res.string.calendar_other_password,
                passwordHelper = null,
                rejected = Res.string.calendar_other_rejected,
                rejectedDetail = Res.string.calendar_other_rejected_detail,
                steps = emptyList(),
                instructions = null,
            )
        }
    }

@Composable
private fun failure(status: CalendarConnectStatus): String? =
    when (status) {
        CalendarConnectStatus.HubUnreachable -> stringResource(Res.string.calendar_hub_unreachable)
        CalendarConnectStatus.Failed -> stringResource(Res.string.members_failed)
        else -> null
    }

@PreviewDayNight
@Composable
private fun CalendarConnectContentPreview(
    @PreviewParameter(CalendarConnectUiStateProvider::class) state: CalendarConnectUiState,
) {
    HearthTheme {
        CalendarConnectContent(
            state = state,
            onAccountChange = {},
            onPasswordChange = {},
            onServerChange = {},
            onCalendarNameChange = {},
            onConnect = {},
            onOpenInstructions = {},
            onBack = {},
        )
    }
}
