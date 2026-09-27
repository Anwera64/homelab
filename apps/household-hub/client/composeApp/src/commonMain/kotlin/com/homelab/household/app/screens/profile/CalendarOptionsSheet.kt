package com.homelab.household.app.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import com.homelab.household.app.components.DestructiveButton
import com.homelab.household.app.components.SecondaryButton
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.calendar_disconnect_busy
import com.homelab.household.app.resources.calendar_disconnect_failed
import com.homelab.household.app.resources.calendar_disconnect_keep
import com.homelab.household.app.resources.calendar_disconnect_stays
import com.homelab.household.app.resources.calendar_disconnect_stops_reading
import com.homelab.household.app.resources.calendar_disconnect_stops_telling
import com.homelab.household.app.resources.calendar_disconnect_submit
import com.homelab.household.app.resources.calendar_disconnect_title
import com.homelab.household.app.resources.calendar_disconnect_unreachable
import com.homelab.household.app.resources.calendar_sheet_change
import com.homelab.household.app.resources.calendar_sheet_change_caption
import com.homelab.household.app.resources.calendar_sheet_disconnect
import com.homelab.household.app.resources.calendar_sheet_disconnect_caption
import com.homelab.household.app.screens.calendarprovider.CalendarProviderMark
import com.homelab.household.app.screens.calendarprovider.providerName
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.app.theme.PreviewDayNight
import com.homelab.household.domain.model.CalendarProvider
import com.homelab.household.presentation.profile.CalendarDisconnect
import com.homelab.household.presentation.profile.CalendarRow
import org.jetbrains.compose.resources.stringResource

/** Which half of the calendar sheet is showing. Disconnecting asks in the same sheet it was offered in. */
enum class CalendarSheetStep { Options, ConfirmDisconnect }

/**
 * What can be done to the connected calendar: change it, or disconnect it after saying what that
 * costs. The row on Profile only changes once the hub has agreed, so a failure keeps the sheet open
 * on the question with the reason under it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarOptionsSheet(
    row: CalendarRow.Connected,
    step: CalendarSheetStep,
    disconnect: CalendarDisconnect,
    onChange: () -> Unit,
    onAskToDisconnect: () -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = HearthTheme.colors

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = HearthShapes.sheet,
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.outline) },
    ) {
        when (step) {
            CalendarSheetStep.Options -> {
                OptionsStep(row = row, onChange = onChange, onAskToDisconnect = onAskToDisconnect)
            }

            CalendarSheetStep.ConfirmDisconnect -> {
                ConfirmStep(row = row, disconnect = disconnect, onDisconnect = onDisconnect, onKeep = onDismiss)
            }
        }
    }
}

@Composable
private fun OptionsStep(
    row: CalendarRow.Connected,
    onChange: () -> Unit,
    onAskToDisconnect: () -> Unit,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    val spacing = HearthTheme.spacing

    Column(modifier = Modifier.padding(start = spacing.lg, end = spacing.lg, bottom = spacing.xxxl)) {
        Row(
            modifier = Modifier.padding(start = spacing.sm, end = spacing.sm, bottom = spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(HearthTheme.size.swatch).background(colors.canvas, HearthShapes.item),
                contentAlignment = Alignment.Center,
            ) {
                CalendarProviderMark(provider = row.provider)
            }
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(providerName(row.provider), style = type.bodyStrong, color = colors.textPrimary)
                Text(row.account, style = type.monoSm, color = colors.textMuted, maxLines = 1)
            }
        }
        SheetAction(
            icon = HearthIcon.AgentSwap,
            label = stringResource(Res.string.calendar_sheet_change),
            caption = stringResource(Res.string.calendar_sheet_change_caption),
            tint = colors.primary,
            labelColor = colors.textPrimary,
            onClick = onChange,
        )
        HorizontalDivider(color = colors.outlineSoft)
        SheetAction(
            icon = HearthIcon.Revoke,
            label = stringResource(Res.string.calendar_sheet_disconnect),
            caption = stringResource(Res.string.calendar_sheet_disconnect_caption),
            tint = colors.error,
            labelColor = colors.error,
            onClick = onAskToDisconnect,
        )
    }
}

@Composable
private fun SheetAction(
    icon: HearthIcon,
    label: String,
    caption: String,
    tint: Color,
    labelColor: Color,
    onClick: () -> Unit,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    val spacing = HearthTheme.spacing

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = HearthTheme.size.control)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HearthIconImage(icon = icon, contentDescription = null, size = HearthTheme.size.iconMd, tint = tint)
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(label, style = type.body, color = labelColor)
            Text(caption, style = type.caption, color = colors.textMuted)
        }
    }
}

@Composable
private fun ConfirmStep(
    row: CalendarRow.Connected,
    disconnect: CalendarDisconnect,
    onDisconnect: () -> Unit,
    onKeep: () -> Unit,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    val spacing = HearthTheme.spacing

    Column(
        modifier = Modifier.padding(start = spacing.xxl, end = spacing.xxl, bottom = spacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Text(
            stringResource(Res.string.calendar_disconnect_title, providerName(row.provider)),
            style = type.title,
            color = colors.textPrimary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(
                "· " + stringResource(Res.string.calendar_disconnect_stops_reading),
                style = type.label,
                color = colors.error,
            )
            Text(
                "· " + stringResource(Res.string.calendar_disconnect_stops_telling),
                style = type.label,
                color = colors.error,
            )
            Text(
                "· " + stringResource(Res.string.calendar_disconnect_stays),
                style = type.label,
                color = colors.textPrimary,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            DestructiveButton(
                text = stringResource(Res.string.calendar_disconnect_submit),
                onClick = onDisconnect,
                busy = disconnect == CalendarDisconnect.Disconnecting,
                busyDescription = stringResource(Res.string.calendar_disconnect_busy),
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(
                text = stringResource(Res.string.calendar_disconnect_keep),
                onClick = onKeep,
                modifier = Modifier.fillMaxWidth(),
            )
            failure(disconnect)?.let { failure ->
                Text(failure, style = type.caption, color = colors.error)
            }
        }
    }
}

private val previewRow =
    CalendarRow.Connected(provider = CalendarProvider.APPLE, account = "emma@icloud.com", minutesAgo = 4)

@PreviewDayNight
@Composable
private fun OptionsStepPreview() {
    HearthTheme {
        Surface(color = HearthTheme.colors.surface) {
            OptionsStep(row = previewRow, onChange = {}, onAskToDisconnect = {})
        }
    }
}

@PreviewDayNight
@Composable
private fun ConfirmStepUnreachablePreview() {
    HearthTheme {
        Surface(color = HearthTheme.colors.surface) {
            ConfirmStep(row = previewRow, disconnect = CalendarDisconnect.Unreachable, onDisconnect = {}, onKeep = {})
        }
    }
}

@Composable
private fun failure(disconnect: CalendarDisconnect): String? =
    when (disconnect) {
        CalendarDisconnect.Unreachable -> stringResource(Res.string.calendar_disconnect_unreachable)
        CalendarDisconnect.Failed -> stringResource(Res.string.calendar_disconnect_failed)
        CalendarDisconnect.Idle, CalendarDisconnect.Disconnecting -> null
    }
