package com.homelab.household.app.screens.calendarchoose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.homelab.household.app.components.BentoCard
import com.homelab.household.app.components.HearthScaffold
import com.homelab.household.app.components.HearthSwitch
import com.homelab.household.app.components.HearthTopBar
import com.homelab.household.app.components.PrimaryButton
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.calendar_choose_back
import com.homelab.household.app.resources.calendar_choose_count
import com.homelab.household.app.resources.calendar_choose_done
import com.homelab.household.app.resources.calendar_choose_editable
import com.homelab.household.app.resources.calendar_choose_failed
import com.homelab.household.app.resources.calendar_choose_failed_detail
import com.homelab.household.app.resources.calendar_choose_intro
import com.homelab.household.app.resources.calendar_choose_loading
import com.homelab.household.app.resources.calendar_choose_read_only
import com.homelab.household.app.resources.calendar_choose_read_only_note
import com.homelab.household.app.resources.calendar_choose_row_read_only
import com.homelab.household.app.resources.calendar_choose_save_failed
import com.homelab.household.app.resources.calendar_choose_saving
import com.homelab.household.app.resources.calendar_choose_title
import com.homelab.household.app.resources.calendar_choose_try_again
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.app.theme.PreviewDayNight
import com.homelab.household.domain.model.CalendarSource
import com.homelab.household.presentation.calendarchoose.CalendarChooseStatus
import com.homelab.household.presentation.calendarchoose.CalendarChooseUiState
import org.jetbrains.compose.resources.stringResource

/**
 * The account's calendars, each with a switch (canvas: CalPickA): the ones agents may edit, then the
 * read-only ones with a lock. Rows show only a calendar's name — the hub knows whether a calendar can
 * be written to, not who owns it. While asking the account it shows bare rows (CalPickLoading); when
 * the account doesn't answer, a card that says nothing changed (CalPickFailed).
 *
 * Stateless — [CalendarChooseScreen] owns the ViewModel.
 */
@Composable
fun CalendarChooseContent(
    state: CalendarChooseUiState,
    onBack: () -> Unit,
    onToggle: (key: String) -> Unit,
    onDone: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    val spacing = HearthTheme.spacing

    HearthScaffold(
        modifier = modifier,
        header = { HearthTopBar(onBack = onBack, backDescription = stringResource(Res.string.calendar_choose_back)) },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.xl),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                if (state.status == CalendarChooseStatus.LoadFailed) {
                    PrimaryButton(
                        text = stringResource(Res.string.calendar_choose_try_again),
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    PrimaryButton(
                        text = stringResource(Res.string.calendar_choose_done),
                        onClick = onDone,
                        modifier = Modifier.fillMaxWidth(),
                        busy = state.saving,
                        busyDescription = stringResource(Res.string.calendar_choose_saving),
                    )
                }
                if (state.status == CalendarChooseStatus.Ready) {
                    Text(
                        stringResource(Res.string.calendar_choose_count, state.pickedCount, state.calendars.size),
                        modifier = Modifier.fillMaxWidth(),
                        style = type.caption,
                        color = colors.textMuted,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(spacing.xl),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = spacing.xs),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Text(stringResource(Res.string.calendar_choose_title), style = type.hero, color = colors.textPrimary)
                val intro =
                    if (state.status == CalendarChooseStatus.Loading) {
                        Res.string.calendar_choose_loading
                    } else {
                        Res.string.calendar_choose_intro
                    }
                Text(stringResource(intro), style = type.body, color = colors.textMuted)
            }

            when (state.status) {
                CalendarChooseStatus.Loading -> {
                    Skeleton()
                }

                CalendarChooseStatus.LoadFailed -> {
                    Failed()
                }

                CalendarChooseStatus.Ready -> {
                    if (state.editable.isNotEmpty()) {
                        Group(title = stringResource(Res.string.calendar_choose_editable)) {
                            state.editable.forEachIndexed { index, calendar ->
                                if (index > 0) HorizontalDivider(color = colors.outlineSoft)
                                CalendarRow(calendar = calendar, onToggle = onToggle)
                            }
                        }
                    }
                    if (state.readOnly.isNotEmpty()) {
                        Group(
                            title = stringResource(Res.string.calendar_choose_read_only),
                            note = stringResource(Res.string.calendar_choose_read_only_note),
                        ) {
                            state.readOnly.forEachIndexed { index, calendar ->
                                if (index > 0) HorizontalDivider(color = colors.outlineSoft)
                                CalendarRow(calendar = calendar, onToggle = onToggle)
                            }
                        }
                    }
                    if (state.saveFailed) {
                        Text(
                            stringResource(Res.string.calendar_choose_save_failed),
                            modifier = Modifier.padding(start = spacing.xs),
                            style = type.caption,
                            color = colors.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Group(
    title: String,
    note: String? = null,
    content: @Composable () -> Unit,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm)) {
        Text(
            title.uppercase(),
            modifier = Modifier.padding(start = HearthTheme.spacing.xs),
            style = type.overline,
            color = colors.textMuted,
        )
        BentoCard(modifier = Modifier.fillMaxWidth()) { content() }
        if (note != null) {
            Text(
                note,
                modifier = Modifier.padding(start = HearthTheme.spacing.xs),
                style = type.caption,
                color = colors.textMuted,
            )
        }
    }
}

/** One calendar and its switch. A read-only one carries a lock and says so under its name. */
@Composable
private fun CalendarRow(
    calendar: CalendarSource,
    onToggle: (key: String) -> Unit,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = HearthTheme.size.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
    ) {
        if (!calendar.writable) {
            HearthIconImage(
                icon = HearthIcon.SecretLocked,
                contentDescription = null,
                size = HearthTheme.size.iconSm,
                tint = colors.textMuted,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xxs)) {
            Text(calendar.name, style = type.body, color = colors.textPrimary)
            if (!calendar.writable) {
                Text(
                    stringResource(Res.string.calendar_choose_row_read_only),
                    style = type.caption,
                    color = colors.textMuted,
                )
            }
        }
        HearthSwitch(
            checked = calendar.picked,
            onCheckedChange = { onToggle(calendar.key) },
            contentDescription = calendar.name,
        )
    }
}

/** Bare rows while the account is asked, so the screen doesn't jump once it answers. */
@Composable
private fun Skeleton() {
    val colors = HearthTheme.colors
    BentoCard(modifier = Modifier.fillMaxWidth()) {
        repeat(SKELETON_ROWS) { index ->
            if (index > 0) HorizontalDivider(color = colors.outlineSoft)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = HearthTheme.size.touchTarget),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(HearthTheme.spacing.md)
                        .background(colors.outlineSoft, HearthShapes.pill),
                )
                Box(
                    modifier = Modifier
                        .width(HearthTheme.size.touchTarget)
                        .height(HearthTheme.spacing.xxl + HearthTheme.spacing.xs)
                        .background(colors.outlineSoft, HearthShapes.pill),
                )
            }
        }
    }
}

@Composable
private fun Failed() {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    Surface(modifier = Modifier.fillMaxWidth(), shape = HearthShapes.item, color = colors.errorContainer) {
        Column(
            modifier = Modifier.padding(HearthTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HearthIconImage(
                    icon = HearthIcon.Warning,
                    contentDescription = null,
                    size = HearthTheme.size.iconMd,
                    tint = colors.onErrorContainer,
                )
                Text(
                    stringResource(Res.string.calendar_choose_failed),
                    style = type.bodyStrong,
                    color = colors.onErrorContainer,
                )
            }
            Text(
                stringResource(Res.string.calendar_choose_failed_detail),
                style = type.caption,
                color = colors.onErrorContainer,
            )
        }
    }
}

private const val SKELETON_ROWS = 3

@PreviewDayNight
@Composable
private fun CalendarChooseContentPreview(
    @PreviewParameter(CalendarChooseUiStateProvider::class) state: CalendarChooseUiState,
) {
    HearthTheme {
        CalendarChooseContent(state = state, onBack = {}, onToggle = {}, onDone = {}, onRetry = {})
    }
}
