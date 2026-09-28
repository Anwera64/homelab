package com.homelab.household.app.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.homelab.household.app.components.BentoCard
import com.homelab.household.app.components.ChipVariant
import com.homelab.household.app.components.DestructiveButton
import com.homelab.household.app.components.HearthChip
import com.homelab.household.app.components.HearthScaffold
import com.homelab.household.app.components.HearthTopBar
import com.homelab.household.app.components.MemberAvatar
import com.homelab.household.app.components.SecondaryButton
import com.homelab.household.app.components.SettingsRow
import com.homelab.household.app.components.SkeletonBlock
import com.homelab.household.app.components.SkeletonCircle
import com.homelab.household.app.components.SkeletonGroup
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.members_failed
import com.homelab.household.app.resources.members_unreachable
import com.homelab.household.app.resources.profile_admin
import com.homelab.household.app.resources.profile_back
import com.homelab.household.app.resources.profile_calendar
import com.homelab.household.app.resources.profile_calendar_checked_days
import com.homelab.household.app.resources.profile_calendar_checked_hours
import com.homelab.household.app.resources.profile_calendar_checked_minutes
import com.homelab.household.app.resources.profile_calendar_checked_now
import com.homelab.household.app.resources.profile_calendar_connect
import com.homelab.household.app.resources.profile_calendar_connect_caption
import com.homelab.household.app.resources.profile_calendar_note
import com.homelab.household.app.resources.profile_calendar_options
import com.homelab.household.app.resources.profile_calendar_sign_in_again
import com.homelab.household.app.resources.profile_calendar_sign_in_needed
import com.homelab.household.app.resources.profile_calendar_sign_in_needed_detail
import com.homelab.household.app.resources.profile_change_pin
import com.homelab.household.app.resources.profile_delete_account
import com.homelab.household.app.resources.profile_delete_blocked
import com.homelab.household.app.resources.profile_members
import com.homelab.household.app.resources.profile_sign_out
import com.homelab.household.app.screens.calendarprovider.CalendarProviderMark
import com.homelab.household.app.screens.calendarprovider.providerName
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.app.theme.PreviewDayNight
import com.homelab.household.presentation.profile.CalendarRow
import com.homelab.household.presentation.profile.ProfileStatus
import com.homelab.household.presentation.profile.ProfileUiState
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The colour a member wears when the hub hasn't said which. */
private const val DEFAULT_COLOUR = "#3C6E4E"

/**
 * Your own account. Everything a later slice adds — memory, agents, calendar, appearance — lands
 * here; for now it holds what slice 2 backs, and states the one limit the admin cannot work around.
 *
 * Stateless — [ProfileScreen] owns the ViewModel.
 */
@Composable
fun ProfileContent(
    state: ProfileUiState,
    onBack: () -> Unit,
    onMembers: () -> Unit,
    onChangePin: () -> Unit,
    onLeave: () -> Unit,
    onSignOut: () -> Unit,
    onCalendar: () -> Unit,
    modifier: Modifier = Modifier,
    onDisconnectCalendar: () -> Unit = {},
    onCalendarSignInAgain: () -> Unit = {},
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    val member = state.member
    var sheet by rememberSaveable { mutableStateOf<CalendarSheetStep?>(null) }
    val calendar = state.calendar

    if (calendar is CalendarRow.Connected) {
        sheet?.let { step ->
            CalendarOptionsSheet(
                row = calendar,
                step = step,
                disconnect = state.calendarDisconnect,
                onChange = {
                    sheet = null
                    onCalendar()
                },
                onAskToDisconnect = { sheet = CalendarSheetStep.ConfirmDisconnect },
                onDisconnect = onDisconnectCalendar,
                onDismiss = { sheet = null },
            )
        }
    }
    // Disconnected (or not known any more): there is nothing left for the sheet to act on.
    LaunchedEffect(calendar) {
        if (calendar !is CalendarRow.Connected) sheet = null
    }

    HearthScaffold(
        modifier = modifier,
        header = { HearthTopBar(onBack = onBack, backDescription = stringResource(Res.string.profile_back)) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding),
            verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xxl),
        ) {
            // Only the header depends on the hub. Everything below it is this screen's own copy,
            // so it draws from the first frame rather than waiting to be told (§6.21, pattern 2).
            if (member == null) {
                ArrivingHeader()
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MemberAvatar(
                        name = member.fullName,
                        colour = member.avatarColor ?: DEFAULT_COLOUR,
                        size = HearthTheme.size.tile,
                        glyph = type.glyphXxl,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm)) {
                        Text(member.fullName, style = type.title, color = colors.textPrimary)
                        if (member.isAdmin) {
                            HearthChip(label = stringResource(Res.string.profile_admin), variant = ChipVariant.Primary)
                        }
                    }
                }
            }

            CalendarSection(
                row = calendar,
                onCalendar = onCalendar,
                onCalendarOptions = { sheet = CalendarSheetStep.Options },
                onSignInAgain = onCalendarSignInAgain,
            )

            BentoCard(modifier = Modifier.fillMaxWidth()) {
                SettingsRow(
                    label = stringResource(Res.string.profile_members),
                    icon = HearthIcon.Shared,
                    onClick = onMembers,
                )
                HorizontalDivider(color = colors.outlineSoft)
                SettingsRow(
                    label = stringResource(Res.string.profile_change_pin),
                    icon = HearthIcon.SecretLocked,
                    onClick = onChangePin,
                )
                HorizontalDivider(color = colors.outlineSoft)
                // Dimming is for what cannot be opened, and the caption says why (design notes §2).
                SettingsRow(
                    label = stringResource(Res.string.profile_delete_account),
                    icon = HearthIcon.Delete,
                    caption = if (state.isSoleAdmin) stringResource(Res.string.profile_delete_blocked) else null,
                    enabled = !state.isSoleAdmin,
                    onClick = onLeave,
                )
            }

            failure(state.status)?.let { failure ->
                Text(failure, style = type.caption, color = colors.error)
            }

            DestructiveButton(
                text = stringResource(Res.string.profile_sign_out),
                onClick = onSignOut,
                icon = HearthIcon.SignOut,
                modifier = Modifier.fillMaxWidth().padding(bottom = HearthTheme.spacing.xxl),
            )
        }
    }
}

/**
 * Your name and whether you are the admin, before the hub has said either. Drawn at the real
 * header's geometry — the same avatar circle, a line for the name and a shorter one for the chip —
 * so the header does not jump when the answer lands.
 */
@Composable
private fun ArrivingHeader() {
    SkeletonGroup {
        Row(
            horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonCircle(size = HearthTheme.size.tile)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm),
            ) {
                SkeletonBlock(widthFraction = NAME_WIDTH, height = HearthTheme.spacing.xl)
                SkeletonBlock(widthFraction = CHIP_WIDTH, height = HearthTheme.spacing.lg)
            }
        }
    }
}

/**
 * One calendar per member. Connected, it names the provider and the account and when the hub last
 * reached it; its options change it (replacing it rather than adding a second) or disconnect it.
 */
@Composable
private fun CalendarSection(
    row: CalendarRow,
    onCalendar: () -> Unit,
    onCalendarOptions: () -> Unit,
    onSignInAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm)) {
        Text(
            stringResource(Res.string.profile_calendar).uppercase(),
            modifier = Modifier.padding(start = HearthTheme.spacing.xs),
            style = type.overline,
            color = colors.textMuted,
        )
        when (row) {
            is CalendarRow.Connected -> {
                ConnectedCalendar(row = row, onOptions = onCalendarOptions, onSignInAgain = onSignInAgain)
                Text(
                    stringResource(Res.string.profile_calendar_note),
                    modifier = Modifier.padding(start = HearthTheme.spacing.xs),
                    style = type.caption,
                    color = colors.textMuted,
                )
            }

            CalendarRow.Loading -> {
                // The card at a row's height and nothing in it, so the page does not jump when the
                // answer lands. The header already carries this screen's one skeleton.
                BentoCard(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().heightIn(min = HearthTheme.size.touchTarget))
                }
            }

            CalendarRow.None, CalendarRow.Unknown -> {
                BentoCard(modifier = Modifier.fillMaxWidth()) {
                    SettingsRow(
                        label = stringResource(Res.string.profile_calendar_connect),
                        icon = HearthIcon.CalendarAdd,
                        // Unknown makes no claim either way: connecting one replaces whatever is there.
                        caption =
                            if (row == CalendarRow.None) {
                                stringResource(Res.string.profile_calendar_connect_caption)
                            } else {
                                null
                            },
                        onClick = onCalendar,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectedCalendar(
    row: CalendarRow.Connected,
    onOptions: () -> Unit,
    onSignInAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography

    // The whole card opens the options, as it opened Change before; the button is the visible cue
    // and the name a screen reader hears.
    Surface(
        onClick = onOptions,
        modifier = modifier.fillMaxWidth(),
        shape = HearthShapes.bento,
        color = colors.surface,
        contentColor = colors.textPrimary,
    ) {
        Column(
            modifier = Modifier.padding(HearthTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.lg),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(HearthTheme.size.swatch).background(colors.canvas, HearthShapes.item),
                    contentAlignment = Alignment.Center,
                ) {
                    CalendarProviderMark(provider = row.provider)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xs),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(providerName(row.provider), style = type.bodyStrong, color = colors.textPrimary)
                        if (row.needsSignInAgain) {
                            HearthIconImage(
                                icon = HearthIcon.Error,
                                contentDescription = null,
                                size = HearthTheme.size.iconSm,
                                tint = colors.error,
                            )
                        }
                    }
                    Text(row.account, style = type.monoSm, color = colors.textMuted, maxLines = 1)
                    if (row.needsSignInAgain) {
                        Text(
                            stringResource(Res.string.profile_calendar_sign_in_needed),
                            style = type.caption,
                            color = colors.error,
                        )
                    } else {
                        row.minutesAgo?.let { minutes ->
                            Text(checkedAgo(minutes), style = type.caption, color = colors.textMuted)
                        }
                    }
                }
                IconButton(
                    onClick = onOptions,
                    modifier = Modifier.size(HearthTheme.size.touchTarget).background(colors.canvas, HearthShapes.item),
                ) {
                    HearthIconImage(
                        icon = HearthIcon.More,
                        contentDescription = stringResource(Res.string.profile_calendar_options),
                        size = HearthTheme.size.iconMd,
                        tint = colors.textMuted,
                    )
                }
            }
            // Google let go of the sign-in: the events are still there, so the row stays and asks for a new one.
            if (row.needsSignInAgain) {
                Text(
                    stringResource(Res.string.profile_calendar_sign_in_needed_detail),
                    style = type.caption,
                    color = colors.textMuted,
                )
                SecondaryButton(
                    text = stringResource(Res.string.profile_calendar_sign_in_again),
                    onClick = onSignInAgain,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun checkedAgo(minutes: Long): String {
    val count = minutes.toInt()
    return when {
        minutes < 1 -> {
            stringResource(Res.string.profile_calendar_checked_now)
        }

        minutes < MINUTES_PER_HOUR -> {
            pluralStringResource(Res.plurals.profile_calendar_checked_minutes, count, count)
        }

        minutes < MINUTES_PER_DAY -> {
            val hours = (minutes / MINUTES_PER_HOUR).toInt()
            pluralStringResource(Res.plurals.profile_calendar_checked_hours, hours, hours)
        }

        else -> {
            val days = (minutes / MINUTES_PER_DAY).toInt()
            pluralStringResource(Res.plurals.profile_calendar_checked_days, days, days)
        }
    }
}

private const val MINUTES_PER_HOUR = 60L
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

/** How much of the row the name and the admin chip fill before either has arrived. */
private const val NAME_WIDTH = 0.6f
private const val CHIP_WIDTH = 0.3f

@Composable
private fun failure(status: ProfileStatus): String? =
    when (status) {
        ProfileStatus.Loading, ProfileStatus.Ready -> null
        ProfileStatus.Unreachable -> stringResource(Res.string.members_unreachable)
        ProfileStatus.Failed -> stringResource(Res.string.members_failed)
    }

@PreviewDayNight
@Composable
private fun ProfileContentPreview(
    @PreviewParameter(ProfileUiStateProvider::class) state: ProfileUiState,
) {
    HearthTheme {
        ProfileContent(
            state = state,
            onBack = {},
            onMembers = {},
            onChangePin = {},
            onLeave = {},
            onSignOut = {},
            onCalendar = {},
        )
    }
}
