package com.homelab.household.app.screens.calendarconnect

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.homelab.household.app.components.HearthScaffold
import com.homelab.household.app.components.HearthTopBar
import com.homelab.household.app.components.PrimaryButton
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.a11y_google_signin_waiting
import com.homelab.household.app.resources.calendar_back
import com.homelab.household.app.resources.calendar_hub_unreachable
import com.homelab.household.app.resources.calendar_nothing_saved
import com.homelab.household.app.resources.google_signin_again
import com.homelab.household.app.resources.google_signin_button
import com.homelab.household.app.resources.google_signin_denied
import com.homelab.household.app.resources.google_signin_denied_detail
import com.homelab.household.app.resources.google_signin_detail
import com.homelab.household.app.resources.google_signin_expired
import com.homelab.household.app.resources.google_signin_expired_detail
import com.homelab.household.app.resources.google_signin_failed
import com.homelab.household.app.resources.google_signin_failed_detail
import com.homelab.household.app.resources.google_signin_note
import com.homelab.household.app.resources.google_signin_rejected
import com.homelab.household.app.resources.google_signin_rejected_detail
import com.homelab.household.app.resources.google_signin_step_one
import com.homelab.household.app.resources.google_signin_step_three
import com.homelab.household.app.resources.google_signin_step_two
import com.homelab.household.app.resources.google_signin_steps
import com.homelab.household.app.resources.google_signin_title
import com.homelab.household.app.resources.google_signin_unavailable
import com.homelab.household.app.resources.google_signin_unavailable_detail
import com.homelab.household.app.resources.google_signin_unreachable
import com.homelab.household.app.resources.google_signin_unreachable_detail
import com.homelab.household.app.resources.google_signin_waiting
import com.homelab.household.app.screens.calendarprovider.CalendarProviderMark
import com.homelab.household.app.screens.calendarprovider.providerName
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.app.theme.PreviewDayNight
import com.homelab.household.domain.model.CalendarProvider
import com.homelab.household.domain.model.CalendarSignInFailure
import com.homelab.household.presentation.googlesignin.GoogleSignInStatus
import com.homelab.household.presentation.googlesignin.GoogleSignInUiState
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Connecting a Google calendar (boards "Google sign-in" and "Google sign-in refused"). There is no
 * password: the button opens Google's page in the browser, and the steps say what happens there,
 * including the calendar box Google leaves unticked. A sign-in that comes back without a calendar
 * swaps the introduction for what went wrong.
 *
 * Stateless — [GoogleCalendarSignInScreen] owns the ViewModel.
 */
@Composable
fun GoogleCalendarSignInContent(
    state: GoogleSignInUiState,
    onSignIn: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    val problem = problemOf(state.status)

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
                        CalendarProviderMark(provider = CalendarProvider.GOOGLE)
                        Text(providerName(CalendarProvider.GOOGLE), style = type.heading, color = colors.textPrimary)
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
                                state.status == GoogleSignInStatus.InBrowser -> Res.string.google_signin_waiting
                                problem != null -> Res.string.google_signin_again
                                else -> Res.string.google_signin_button
                            },
                        ),
                    onClick = onSignIn,
                    busy = state.busy,
                    busyDescription = stringResource(Res.string.a11y_google_signin_waiting),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(Res.string.calendar_nothing_saved),
                    modifier = Modifier.fillMaxWidth(),
                    style = type.caption,
                    color = colors.textMuted,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding),
            verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xl),
        ) {
            if (problem == null) {
                Column(verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm)) {
                    Text(stringResource(Res.string.google_signin_title), style = type.hero, color = colors.textPrimary)
                    Text(stringResource(Res.string.google_signin_detail), style = type.body, color = colors.textMuted)
                }
            } else {
                CalendarNotice(
                    title = stringResource(problem.first),
                    detail = stringResource(problem.second),
                    icon = HearthIcon.Error,
                    background = colors.errorContainer,
                    ink = colors.onErrorContainer,
                )
            }

            BrowserSteps()

            Row(
                horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
                verticalAlignment = Alignment.Top,
            ) {
                HearthIconImage(
                    icon = HearthIcon.SecretLocked,
                    contentDescription = null,
                    size = HearthTheme.size.iconMd,
                    tint = colors.textMuted,
                )
                Text(stringResource(Res.string.google_signin_note), style = type.caption, color = colors.textMuted)
            }
        }
    }
}

/** What the member will do on Google's page, before they get there. */
@Composable
private fun BrowserSteps(modifier: Modifier = Modifier) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    val steps =
        listOf(
            Res.string.google_signin_step_one,
            Res.string.google_signin_step_two,
            Res.string.google_signin_step_three,
        )

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(colors.surfaceAlt, HearthShapes.bento)
                .border(HearthTheme.size.hairline, colors.outlineSoft, HearthShapes.bento)
                .padding(HearthTheme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
    ) {
        Text(
            stringResource(Res.string.google_signin_steps).uppercase(),
            style = type.overline,
            color = colors.textMuted,
        )
        steps.forEachIndexed { index, step ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier.size(HearthTheme.size.iconLg).background(colors.surface, HearthShapes.pill),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${index + 1}", style = type.mono, color = colors.textPrimary)
                }
                Text(stringResource(step), style = type.body, color = colors.textPrimary)
            }
        }
    }
}

/** What went wrong, as a title and what to do about it, or null when nothing did. */
private fun problemOf(status: GoogleSignInStatus): Pair<StringResource, StringResource>? =
    when (status) {
        is GoogleSignInStatus.Failed -> {
            when (status.failure) {
                CalendarSignInFailure.DENIED -> {
                    Res.string.google_signin_denied to
                        Res.string.google_signin_denied_detail
                }

                CalendarSignInFailure.EXPIRED -> {
                    Res.string.google_signin_expired to
                        Res.string.google_signin_expired_detail
                }

                CalendarSignInFailure.REJECTED -> {
                    Res.string.google_signin_rejected to
                        Res.string.google_signin_rejected_detail
                }

                CalendarSignInFailure.UNREACHABLE -> {
                    Res.string.google_signin_unreachable to Res.string.google_signin_unreachable_detail
                }

                CalendarSignInFailure.FAILED -> {
                    Res.string.google_signin_failed to
                        Res.string.google_signin_failed_detail
                }
            }
        }

        GoogleSignInStatus.HubUnreachable -> {
            Res.string.calendar_hub_unreachable to Res.string.google_signin_failed_detail
        }

        GoogleSignInStatus.Unavailable -> {
            Res.string.google_signin_unavailable to Res.string.google_signin_unavailable_detail
        }

        GoogleSignInStatus.Idle, GoogleSignInStatus.Starting, GoogleSignInStatus.InBrowser -> {
            null
        }
    }

@PreviewDayNight
@Composable
private fun GoogleCalendarSignInContentPreview(
    @PreviewParameter(GoogleSignInUiStateProvider::class) state: GoogleSignInUiState,
) {
    HearthTheme {
        GoogleCalendarSignInContent(state = state, onSignIn = {}, onBack = {})
    }
}
