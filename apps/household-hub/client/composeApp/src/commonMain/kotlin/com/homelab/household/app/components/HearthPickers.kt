package com.homelab.household.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.field_picker_cancel
import com.homelab.household.app.resources.field_picker_ok
import com.homelab.household.app.resources.field_picker_time_title
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme
import org.jetbrains.compose.resources.stringResource

/**
 * A field that is picked rather than typed: it looks like [HearthTextField], shows [value], and a
 * tap anywhere on it calls [onClick], which opens a picker. [changed] marks it the same way.
 * [tag] names the tappable field for tests.
 */
@Composable
fun HearthPickerField(
    value: String,
    label: String,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    changed: Boolean = false,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm)) {
        HearthFieldLabel(label = label, changed = changed)
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = HearthTheme.size.touchTarget)
                    .clip(HearthShapes.item)
                    .background(colors.surface, HearthShapes.item)
                    .border(
                        width = if (changed) HearthTheme.size.changed else HearthTheme.size.hairline,
                        color = if (changed) colors.primary else colors.outline,
                        shape = HearthShapes.item,
                    ).clickable(role = Role.Button, onClick = onClick)
                    .testTag(tag)
                    .padding(horizontal = HearthTheme.spacing.lg, vertical = HearthTheme.spacing.md),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(text = value, style = type.body, color = colors.textPrimary)
        }
    }
}

/**
 * Material's date picker, opened on [initialEpochDay] (days since 1970-01-01). OK hands back the day
 * picked, counted the same way; Cancel or a tap outside changes nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HearthDatePickerDialog(
    initialEpochDay: Long,
    onPick: (epochDay: Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialEpochDay * MILLIS_PER_DAY)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onPick(it.floorDiv(MILLIS_PER_DAY)) } ?: onDismiss() },
                modifier = Modifier.testTag(PICKER_CONFIRM_TAG),
            ) { Text(stringResource(Res.string.field_picker_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(PICKER_DISMISS_TAG)) {
                Text(stringResource(Res.string.field_picker_cancel))
            }
        },
    ) {
        DatePicker(state = state)
    }
}

/** Material's clock, on a 24-hour dial, opened on [initialHour]:[initialMinute]. OK hands back the time picked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HearthTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onPick: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)
    TimePickerDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(Res.string.field_picker_time_title)) },
        confirmButton = {
            TextButton(
                onClick = { onPick(state.hour, state.minute) },
                modifier = Modifier.testTag(PICKER_CONFIRM_TAG),
            ) { Text(stringResource(Res.string.field_picker_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(PICKER_DISMISS_TAG)) {
                Text(stringResource(Res.string.field_picker_cancel))
            }
        },
    ) {
        TimePicker(state = state)
    }
}

const val PICKER_CONFIRM_TAG = "picker_confirm"
const val PICKER_DISMISS_TAG = "picker_dismiss"
private const val MILLIS_PER_DAY = 86_400_000L
