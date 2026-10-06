package com.homelab.household.app.screens.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.homelab.household.app.components.ActionCard
import com.homelab.household.app.components.CardButtonRow
import com.homelab.household.app.components.DestructiveButton
import com.homelab.household.app.components.HearthCheckboxRow
import com.homelab.household.app.components.HearthDatePickerDialog
import com.homelab.household.app.components.HearthFieldLabel
import com.homelab.household.app.components.HearthPickerField
import com.homelab.household.app.components.HearthSegmented
import com.homelab.household.app.components.HearthTextField
import com.homelab.household.app.components.HearthTimePickerDialog
import com.homelab.household.app.components.PrimaryButton
import com.homelab.household.app.components.SecondaryButton
import com.homelab.household.app.components.ToolRecordLine
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.tool_card_approve
import com.homelab.household.app.resources.tool_card_approve_with_changes
import com.homelab.household.app.resources.tool_card_approved
import com.homelab.household.app.resources.tool_card_cancel
import com.homelab.household.app.resources.tool_card_chip_fri
import com.homelab.household.app.resources.tool_card_chip_mon
import com.homelab.household.app.resources.tool_card_chip_sat
import com.homelab.household.app.resources.tool_card_chip_sun
import com.homelab.household.app.resources.tool_card_chip_thu
import com.homelab.household.app.resources.tool_card_chip_tue
import com.homelab.household.app.resources.tool_card_chip_wed
import com.homelab.household.app.resources.tool_card_day
import com.homelab.household.app.resources.tool_card_day_fri
import com.homelab.household.app.resources.tool_card_day_mon
import com.homelab.household.app.resources.tool_card_day_sat
import com.homelab.household.app.resources.tool_card_day_sun
import com.homelab.household.app.resources.tool_card_day_thu
import com.homelab.household.app.resources.tool_card_day_tue
import com.homelab.household.app.resources.tool_card_day_wed
import com.homelab.household.app.resources.tool_card_decline
import com.homelab.household.app.resources.tool_card_edit
import com.homelab.household.app.resources.tool_card_ends_after
import com.homelab.household.app.resources.tool_card_ends_before_start
import com.homelab.household.app.resources.tool_card_ends_never
import com.homelab.household.app.resources.tool_card_ends_on_date
import com.homelab.household.app.resources.tool_card_field_day
import com.homelab.household.app.resources.tool_card_field_days
import com.homelab.household.app.resources.tool_card_field_empty
import com.homelab.household.app.resources.tool_card_field_ends
import com.homelab.household.app.resources.tool_card_field_last_date
import com.homelab.household.app.resources.tool_card_field_note
import com.homelab.household.app.resources.tool_card_field_repeat
import com.homelab.household.app.resources.tool_card_field_time
import com.homelab.household.app.resources.tool_card_field_title
import com.homelab.household.app.resources.tool_card_field_what
import com.homelab.household.app.resources.tool_card_keep
import com.homelab.household.app.resources.tool_card_month_apr
import com.homelab.household.app.resources.tool_card_month_aug
import com.homelab.household.app.resources.tool_card_month_dec
import com.homelab.household.app.resources.tool_card_month_feb
import com.homelab.household.app.resources.tool_card_month_jan
import com.homelab.household.app.resources.tool_card_month_jul
import com.homelab.household.app.resources.tool_card_month_jun
import com.homelab.household.app.resources.tool_card_month_mar
import com.homelab.household.app.resources.tool_card_month_may
import com.homelab.household.app.resources.tool_card_month_nov
import com.homelab.household.app.resources.tool_card_month_oct
import com.homelab.household.app.resources.tool_card_month_sep
import com.homelab.household.app.resources.tool_card_remove
import com.homelab.household.app.resources.tool_card_repeat_and
import com.homelab.household.app.resources.tool_card_repeat_daily
import com.homelab.household.app.resources.tool_card_repeat_day
import com.homelab.household.app.resources.tool_card_repeat_days
import com.homelab.household.app.resources.tool_card_repeat_last
import com.homelab.household.app.resources.tool_card_repeat_month
import com.homelab.household.app.resources.tool_card_repeat_monthly
import com.homelab.household.app.resources.tool_card_repeat_months
import com.homelab.household.app.resources.tool_card_repeat_no_days
import com.homelab.household.app.resources.tool_card_repeat_none
import com.homelab.household.app.resources.tool_card_repeat_sessions
import com.homelab.household.app.resources.tool_card_repeat_times
import com.homelab.household.app.resources.tool_card_repeat_until
import com.homelab.household.app.resources.tool_card_repeat_week
import com.homelab.household.app.resources.tool_card_repeat_week_on
import com.homelab.household.app.resources.tool_card_repeat_weekly
import com.homelab.household.app.resources.tool_card_repeat_weeks
import com.homelab.household.app.resources.tool_card_repeat_weeks_on
import com.homelab.household.app.resources.tool_card_repeat_year
import com.homelab.household.app.resources.tool_card_repeat_years
import com.homelab.household.app.resources.tool_card_replace
import com.homelab.household.app.resources.tool_card_scope
import com.homelab.household.app.resources.tool_card_scope_following
import com.homelab.household.app.resources.tool_card_scope_following_remove_note
import com.homelab.household.app.resources.tool_card_scope_this
import com.homelab.household.app.resources.tool_card_scope_this_remove_note
import com.homelab.household.app.resources.tool_card_times
import com.homelab.household.app.resources.tool_card_times_fewer
import com.homelab.household.app.resources.tool_card_times_more
import com.homelab.household.app.resources.tool_card_untitled
import com.homelab.household.app.resources.tool_card_weekday_fri
import com.homelab.household.app.resources.tool_card_weekday_mon
import com.homelab.household.app.resources.tool_card_weekday_sat
import com.homelab.household.app.resources.tool_card_weekday_sun
import com.homelab.household.app.resources.tool_card_weekday_thu
import com.homelab.household.app.resources.tool_card_weekday_tue
import com.homelab.household.app.resources.tool_card_weekday_wed
import com.homelab.household.app.resources.tool_card_when
import com.homelab.household.app.resources.tool_card_when_all_day
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.domain.model.AnswerPart
import com.homelab.household.domain.model.EditField
import com.homelab.household.domain.model.EditValues
import com.homelab.household.domain.model.EventDate
import com.homelab.household.domain.model.EventMoment
import com.homelab.household.domain.model.EventRepeat
import com.homelab.household.domain.model.EventScope
import com.homelab.household.domain.model.ProposalDetails
import com.homelab.household.domain.model.ProposalStatus
import com.homelab.household.domain.model.RepeatEnd
import com.homelab.household.domain.model.RepeatEvery
import com.homelab.household.domain.model.TimeOfDay
import com.homelab.household.domain.model.editProposal
import com.homelab.household.domain.model.editableFields
import com.homelab.household.domain.model.offersNoRepeat
import com.homelab.household.domain.model.proposedValues
import com.homelab.household.domain.model.repeatSummary
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A write the agent wants to make, put to the member before it happens (canvas: ToolApproveRemove).
 *
 * What it will do and to what, with Decline on the left and Approve on the right. A removal reads
 * in red and asks "Keep it" or "Remove", so saying yes to losing something is never the easy tap.
 *
 * A write that can be made automatic (adding events, creating notes) also offers "Auto-approve …
 * from now on" (canvas: ToolAutoApprove). Ticking it changes nothing until Approve: then
 * [onApproveAutomatically] is called instead of [onDecide]. Removing and replacing never offer it.
 *
 * An add or a change also offers Edit between them (canvas: ToolEdit): the details open as fields
 * in place, each one changed is marked, and Approve becomes "Approve with changes", which hands
 * [onDecide] only the details that changed. Cancel puts the proposal back as it was. The edit rules
 * are the domain's ([editProposal]); the card only words days and draws the fields and pickers.
 *
 * Once answered while the step's other cards still wait, it shrinks to its record line, so what is
 * left to answer stands out; when the last one is answered the turn carries on and the hub turns
 * each into the step it became.
 */
@Composable
fun ToolApprovalCard(
    card: AnswerPart.Proposal,
    onDecide: (toolCallId: String, approved: Boolean, edited: ProposalDetails?) -> Unit,
    modifier: Modifier = Modifier,
    onApproveAutomatically: (
        toolCallId: String,
        edited: ProposalDetails?,
    ) -> Unit = { id, edited -> onDecide(id, true, edited) },
) {
    val label = toolLabel(card.tool, card.action)
    val details = approvalCardDetails(card)
    val name = details.title ?: stringResource(Res.string.tool_card_untitled)

    when (card.status) {
        ProposalStatus.Approved -> {
            ToolRecordLine(
                icon = label.icon,
                text = stringResource(Res.string.tool_card_approved, name),
                modifier = modifier,
            )
        }

        ProposalStatus.Declined -> {
            val declined = label.declined?.let { stringResource(it) }
            ToolRecordLine(
                icon = HearthIcon.Declined,
                text = if (declined != null) "$declined$SEPARATOR$name" else name,
                modifier = modifier,
            )
        }

        ProposalStatus.Pending -> {
            PendingCard(card, label, name, details, onDecide, onApproveAutomatically, modifier)
        }
    }
}

@Composable
private fun PendingCard(
    card: AnswerPart.Proposal,
    label: ToolLabel,
    name: String,
    details: ApprovalCardDetails,
    onDecide: (toolCallId: String, approved: Boolean, edited: ProposalDetails?) -> Unit,
    onApproveAutomatically: (toolCallId: String, edited: ProposalDetails?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val ask = cardAsk(card.action)
    var automatic by rememberSaveable(card.toolCallId) { mutableStateOf(false) }
    val tint = if (ask == CardAsk.Approve) colors.textPrimary else colors.error
    var editing by rememberSaveable(card.toolCallId) { mutableStateOf(false) }
    var scope by rememberSaveable(card.toolCallId) { mutableStateOf(details.scope) }
    // This and following can take a new rule where one date can't, so the pick decides the fields.
    val fields = remember(card, scope) { editableFields(card, scope) }
    // Shown on the card and in its Edit view alike: the pick is the same either way.
    val whichDates: @Composable () -> Unit = {
        scope?.let { chosen ->
            ScopeSwitch(chosen = chosen, onChoose = { scope = it })
            if (ask == CardAsk.Remove) {
                val note =
                    if (chosen == EventScope.OnlyThis) {
                        Res.string.tool_card_scope_this_remove_note
                    } else {
                        Res.string.tool_card_scope_following_remove_note
                    }
                Text(text = stringResource(note), style = HearthTheme.typography.caption, color = colors.textMuted)
            }
        }
    }

    ActionCard(modifier = modifier) {
        CardTitle(label = label, tint = tint)
        if (editing) {
            EditingCard(
                card = card,
                fields = fields,
                scope = scope,
                onCancel = { editing = false },
                onApprove = { edited -> onDecide(card.toolCallId, true, decidedEdit(card, edited, scope)) },
                whichDates = whichDates,
            )
        } else {
            // Under the title's words rather than its icon, as the canvas lines them up.
            Column(
                modifier = Modifier.padding(start = HearthTheme.size.iconSm + HearthTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xs),
            ) {
                Text(text = name, style = HearthTheme.typography.bodyStrong, color = colors.textPrimary)
                val caption = details.whenAt?.let { whenText(it) } ?: details.preview
                if (caption != null) {
                    Text(text = caption, style = HearthTheme.typography.caption, color = colors.textMuted)
                }
                details.repeat?.let { RepeatLine(it) }
            }
            whichDates()
            val edited = decidedEdit(card, edit = null, chosen = scope)
            val automaticAsk = label.automaticAsk
            if (automaticAsk != null && ask == CardAsk.Approve) {
                HearthCheckboxRow(
                    text = stringResource(automaticAsk),
                    checked = automatic,
                    onCheckedChange = { automatic = it },
                )
            }
            // Three buttons share the width only when Edit is one of them.
            val compact = fields.isNotEmpty()
            CardButtonRow {
                val no = if (ask == CardAsk.Approve) Res.string.tool_card_decline else Res.string.tool_card_keep
                SecondaryButton(
                    text = stringResource(no),
                    onClick = { onDecide(card.toolCallId, false, null) },
                    modifier = Modifier.weight(1f),
                    compact = compact,
                )
                if (fields.isNotEmpty()) {
                    SecondaryButton(
                        text = stringResource(Res.string.tool_card_edit),
                        onClick = { editing = true },
                        modifier = Modifier.weight(1f),
                        compact = true,
                    )
                }
                when (ask) {
                    CardAsk.Approve -> {
                        PrimaryButton(
                            text = stringResource(Res.string.tool_card_approve),
                            onClick = {
                                if (automatic) {
                                    onApproveAutomatically(card.toolCallId, edited)
                                } else {
                                    onDecide(card.toolCallId, true, edited)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            lifted = false,
                            compact = compact,
                        )
                    }

                    CardAsk.Remove, CardAsk.Replace -> {
                        val yes =
                            if (ask ==
                                CardAsk.Remove
                            ) {
                                Res.string.tool_card_remove
                            } else {
                                Res.string.tool_card_replace
                            }
                        DestructiveButton(
                            text = stringResource(yes),
                            onClick = { onDecide(card.toolCallId, true, edited) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardTitle(
    label: ToolLabel,
    tint: Color,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HearthIconImage(icon = label.icon, contentDescription = null, size = HearthTheme.size.iconSm, tint = tint)
        Text(
            text = stringResource(label.card ?: label.running),
            style = HearthTheme.typography.labelStrong,
            color = tint,
        )
    }
}

/**
 * The card's details as fields (canvas: ToolEdit), Day and Time side by side. The title and a note's
 * words are typed; the day and the time are picked from Material's date picker and clock, so they
 * can't be written wrong. What is entered survives a rotation. A typed field says what's wrong with
 * it once Approve was tapped, not while it is being typed.
 */
@Composable
private fun EditingCard(
    card: AnswerPart.Proposal,
    fields: List<EditField>,
    scope: EventScope?,
    onCancel: () -> Unit,
    onApprove: (edited: ProposalDetails?) -> Unit,
    whichDates: @Composable () -> Unit = {},
) {
    val proposed = remember(card) { proposedValues(card) }
    // Each null until the member changes it; primitives, so they can be saved.
    var title by rememberSaveable(card.toolCallId) { mutableStateOf<String?>(null) }
    var words by rememberSaveable(card.toolCallId) { mutableStateOf<String?>(null) }
    var epochDay by rememberSaveable(card.toolCallId) { mutableStateOf<Long?>(null) }
    var minuteOfDay by rememberSaveable(card.toolCallId) { mutableStateOf<Int?>(null) }
    var tried by rememberSaveable(card.toolCallId) { mutableStateOf(false) }
    var picking by rememberSaveable(card.toolCallId) { mutableStateOf<EditField?>(null) }
    // How it repeats: the pick's name, or "" for None; the days as bits, Monday the lowest.
    var repeatPick by rememberSaveable(card.toolCallId) { mutableStateOf<String?>(null) }
    var dayBits by rememberSaveable(card.toolCallId) { mutableStateOf<Int?>(null) }
    var endsPick by rememberSaveable(card.toolCallId) { mutableStateOf<String?>(null) }
    var lastEpochDay by rememberSaveable(card.toolCallId) { mutableStateOf<Long?>(null) }
    var times by rememberSaveable(card.toolCallId) { mutableStateOf<Int?>(null) }
    val values =
        EditValues(
            title = title ?: proposed.title,
            words = words ?: proposed.words,
            day = epochDay?.let(EventDate::fromEpochDay) ?: proposed.day,
            time = minuteOfDay?.let { TimeOfDay(it / MINUTES_PER_HOUR, it % MINUTES_PER_HOUR) } ?: proposed.time,
            repeat =
                when (val pick = repeatPick) {
                    null -> proposed.repeat
                    NO_REPEAT -> null
                    else -> RepeatEvery.valueOf(pick)
                },
            weekdays = dayBits?.let(::daysOf) ?: proposed.weekdays,
            ends = endsPick?.let(RepeatEnd::valueOf) ?: proposed.ends,
            lastDate = lastEpochDay?.let(EventDate::fromEpochDay) ?: proposed.lastDate,
            times = times ?: proposed.times,
        )
    // A pick can carry others with it (Weekly ticks the start's day), so every part is kept.
    val keep: (EditValues) -> Unit = { next ->
        repeatPick = next.repeat?.name ?: NO_REPEAT
        dayBits = bitsOf(next.weekdays)
        endsPick = next.ends.name
        lastEpochDay = next.lastDate?.toEpochDay()
        times = next.times
    }
    val edit = editProposal(card, values, scope)
    val invalid = if (tried) edit.invalid else emptySet()

    Column(verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md)) {
        if (EditField.What in fields) {
            HearthTextField(
                value = values.title,
                onValueChange = { title = it },
                label = stringResource(fieldLabel(card, EditField.What)),
                error = if (EditField.What in invalid) stringResource(Res.string.tool_card_field_empty) else null,
                textStyle = HearthTheme.typography.body,
                changed = EditField.What in edit.changed,
            )
        }
        if (EditField.Words in fields) {
            HearthTextField(
                value = values.words,
                onValueChange = { words = it },
                label = stringResource(fieldLabel(card, EditField.Words)),
                error = if (EditField.Words in invalid) stringResource(Res.string.tool_card_field_empty) else null,
                singleLine = false,
                minLines = WORDS_LINES,
                textStyle = HearthTheme.typography.body,
                changed = EditField.Words in edit.changed,
            )
        }
        val day = values.day.takeIf { EditField.Day in fields }
        val time = values.time.takeIf { EditField.Time in fields }
        if (day != null || time != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm)) {
                if (day != null) {
                    HearthPickerField(
                        value = dayText(day),
                        label = stringResource(Res.string.tool_card_field_day),
                        onClick = { picking = EditField.Day },
                        tag = DAY_FIELD_TAG,
                        modifier = Modifier.weight(1f),
                        changed = EditField.Day in edit.changed,
                    )
                }
                if (time != null) {
                    HearthPickerField(
                        value = "${time.hour.pad()}:${time.minute.pad()}",
                        label = stringResource(Res.string.tool_card_field_time),
                        onClick = { picking = EditField.Time },
                        tag = TIME_FIELD_TAG,
                        modifier = Modifier.weight(1f),
                        changed = EditField.Time in edit.changed,
                    )
                }
            }
        }
    }
    whichDates()
    if (EditField.Repeat in fields) {
        RepeatFields(
            values = values,
            offersNone = offersNoRepeat(card),
            changed = edit.changed,
            invalid = invalid,
            onChange = keep,
            onPickLastDate = { picking = EditField.Ends },
        )
    }
    val day = values.day
    val time = values.time
    val lastDate = values.lastDate
    if (picking == EditField.Ends && lastDate != null) {
        HearthDatePickerDialog(
            initialEpochDay = lastDate.toEpochDay(),
            onPick = {
                lastEpochDay = it
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
    if (picking == EditField.Day && day != null) {
        HearthDatePickerDialog(
            initialEpochDay = day.toEpochDay(),
            onPick = {
                epochDay = it
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
    if (picking == EditField.Time && time != null) {
        HearthTimePickerDialog(
            initialHour = time.hour,
            initialMinute = time.minute,
            onPick = { hour, minute ->
                minuteOfDay = hour * MINUTES_PER_HOUR + minute
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
    CardButtonRow {
        SecondaryButton(
            text = stringResource(Res.string.tool_card_cancel),
            // The entered values are this composable's, so leaving the edit view forgets them.
            onClick = onCancel,
            // Hugging its word, so "Approve with changes" keeps to one line beside it.
            compact = true,
        )
        val anyChanged = edit.changed.isNotEmpty()
        val approve = if (anyChanged) Res.string.tool_card_approve_with_changes else Res.string.tool_card_approve
        PrimaryButton(
            text = stringResource(approve),
            onClick = {
                tried = true
                if (edit.invalid.isEmpty()) onApprove(edit.edited)
            },
            modifier = Modifier.weight(1f),
            lifted = false,
            compact = true,
        )
    }
}

/**
 * How the event repeats and when that ends (canvas: RepeatEdit, RepeatEndsPicker, RepeatEndsAfter):
 * Repeat, the days for Weekly, Ends with its last date or count, and a caption counting it out.
 * [onChange] gets the fields as they are after a pick; the domain's picks fill in what goes with it.
 */
@Composable
private fun RepeatFields(
    values: EditValues,
    offersNone: Boolean,
    changed: Set<EditField>,
    invalid: Set<EditField>,
    onChange: (EditValues) -> Unit,
    onPickLastDate: () -> Unit,
) {
    val colors = HearthTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md)) {
        val repeatLabel = stringResource(Res.string.tool_card_field_repeat)
        Column(verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm)) {
            HearthFieldLabel(label = repeatLabel, changed = EditField.Repeat in changed)
            val options =
                buildList {
                    if (offersNone) add(null to stringResource(Res.string.tool_card_repeat_none))
                    add(RepeatEvery.Day to stringResource(Res.string.tool_card_repeat_daily))
                    add(RepeatEvery.Week to stringResource(Res.string.tool_card_repeat_weekly))
                    add(RepeatEvery.Month to stringResource(Res.string.tool_card_repeat_monthly))
                }
            HearthSegmented(
                options = options,
                chosen = values.repeat,
                onChoose = { onChange(values.pickRepeat(it)) },
                label = repeatLabel,
            )
        }
        if (values.repeat == RepeatEvery.Week) {
            Column(verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm)) {
                val daysLabel = stringResource(Res.string.tool_card_field_days)
                HearthFieldLabel(label = daysLabel)
                DayChips(
                    ticked = values.weekdays,
                    onToggle = { day ->
                        val days = if (day in values.weekdays) values.weekdays - day else values.weekdays + day
                        onChange(values.copy(weekdays = days))
                    },
                    label = daysLabel,
                )
                if (EditField.Repeat in invalid) {
                    Text(
                        text = stringResource(Res.string.tool_card_repeat_no_days),
                        style = HearthTheme.typography.caption,
                        color = colors.error,
                    )
                }
            }
        }
        if (values.repeat == null) return@Column
        val endsLabel = stringResource(Res.string.tool_card_field_ends)
        Column(verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm)) {
            HearthFieldLabel(label = endsLabel, changed = EditField.Ends in changed)
            HearthSegmented(
                options =
                    listOf(
                        RepeatEnd.Never to stringResource(Res.string.tool_card_ends_never),
                        RepeatEnd.OnDate to stringResource(Res.string.tool_card_ends_on_date),
                        RepeatEnd.After to stringResource(Res.string.tool_card_ends_after),
                    ),
                chosen = values.ends,
                onChoose = { onChange(values.pickEnds(it)) },
                label = endsLabel,
            )
        }
        val lastDate = values.lastDate
        if (values.ends == RepeatEnd.OnDate && lastDate != null) {
            HearthPickerField(
                value = dayText(lastDate),
                label = stringResource(Res.string.tool_card_field_last_date),
                onClick = onPickLastDate,
                tag = LAST_DATE_FIELD_TAG,
            )
        }
        if (values.ends == RepeatEnd.After) {
            TimesStepper(
                times = values.times ?: EditValues.DEFAULT_TIMES,
                onStep = { onChange(values.copy(times = it)) },
            )
        }
        if (EditField.Ends in invalid) {
            Text(
                text = stringResource(Res.string.tool_card_ends_before_start),
                style = HearthTheme.typography.caption,
                color = colors.error,
            )
        }
        repeatSummary(values)?.let { summary ->
            val last = dayText(summary.last)
            val caption =
                if (values.ends == RepeatEnd.After) {
                    stringResource(Res.string.tool_card_repeat_last, last)
                } else {
                    pluralStringResource(
                        Res.plurals.tool_card_repeat_sessions,
                        summary.sessions,
                        summary.sessions,
                        last,
                    )
                }
            Text(text = caption, style = HearthTheme.typography.caption, color = colors.textMuted)
        }
    }
}

/** M to S, each ticked or not; a screen reader hears the whole day's name. */
@Composable
private fun DayChips(
    ticked: Set<Int>,
    onToggle: (Int) -> Unit,
    label: String,
) {
    val colors = HearthTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        DAY_CHIPS.forEachIndexed { day, (letter, name) ->
            val on = day in ticked
            val spoken = stringResource(name)
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = HearthTheme.spacing.xxs)
                        .heightIn(min = HearthTheme.size.touchTarget)
                        .clip(HearthShapes.button)
                        .background(if (on) colors.primary else colors.surface)
                        .border(
                            HearthTheme.size.hairline,
                            if (on) colors.primary else colors.outline,
                            HearthShapes.button,
                        ).toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle(day) })
                        .semantics { contentDescription = spoken },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(letter),
                    style = HearthTheme.typography.labelStrong,
                    color = if (on) colors.onPrimary else colors.textMuted,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
    }
}

/** "10 times" between One fewer and One more, kept to what a rule can count. */
@Composable
private fun TimesStepper(
    times: Int,
    onStep: (Int) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(
            glyph = "\u2212",
            spoken = stringResource(Res.string.tool_card_times_fewer),
            enabled = times > EditValues.TIMES.first,
            onClick = { onStep(times - 1) },
        )
        Text(
            text = pluralStringResource(Res.plurals.tool_card_times, times, times),
            style = HearthTheme.typography.bodyStrong,
            color = HearthTheme.colors.textPrimary,
        )
        StepButton(
            glyph = "+",
            spoken = stringResource(Res.string.tool_card_times_more),
            enabled = times < EditValues.TIMES.last,
            onClick = { onStep(times + 1) },
        )
    }
}

@Composable
private fun StepButton(
    glyph: String,
    spoken: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = HearthTheme.colors
    Box(
        modifier =
            Modifier
                .size(HearthTheme.size.touchTarget)
                .clip(HearthShapes.item)
                .background(colors.surface)
                .border(HearthTheme.size.hairline, colors.outline, HearthShapes.item)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = HearthTheme.typography.bodyStrong,
            color = if (enabled) colors.textPrimary else colors.outline,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

private fun daysOf(bits: Int): Set<Int> = (0 until DAYS_IN_WEEK).filter { bits shr it and 1 == 1 }.toSet()

private fun bitsOf(days: Set<Int>): Int = days.fold(0) { bits, day -> bits or (1 shl day) }

private fun fieldLabel(
    card: AnswerPart.Proposal,
    field: EditField,
): StringResource =
    when (field) {
        EditField.What -> {
            val note = card.details is ProposalDetails.Note
            if (note) Res.string.tool_card_field_title else Res.string.tool_card_field_what
        }

        EditField.Day -> {
            Res.string.tool_card_field_day
        }

        EditField.Time -> {
            Res.string.tool_card_field_time
        }

        EditField.Words -> {
            Res.string.tool_card_field_note
        }

        EditField.Repeat -> {
            Res.string.tool_card_field_repeat
        }

        EditField.Ends -> {
            Res.string.tool_card_field_ends
        }
    }

/** "Sat 3 Oct": the day as the card writes it, which is also what the Day field shows. */
@Composable
private fun dayText(date: EventDate): String =
    stringResource(
        Res.string.tool_card_day,
        stringResource(WEEKDAYS[EventMoment(date.year, date.month, date.day).weekday]),
        date.day,
        stringResource(MONTHS[date.month - 1]),
    )

private fun Int.pad() = toString().padStart(2, '0')

/** "Every Tue and Thu until 24 Dec", under the first date (canvas: RepeatAdd). */
@Composable
private fun RepeatLine(repeat: EventRepeat) {
    val colors = HearthTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HearthIconImage(
            icon = HearthIcon.Repeat,
            contentDescription = null,
            size = HearthTheme.size.iconSm,
            tint = colors.textMuted,
        )
        Text(text = repeatText(repeat), style = HearthTheme.typography.caption, color = colors.textMuted)
    }
}

@Composable
private fun repeatText(repeat: EventRepeat): String {
    val often = oftenText(repeat)
    val until = repeat.until
    val count = repeat.count
    return when {
        until != null -> {
            val month = stringResource(MONTHS[until.month - 1])
            stringResource(Res.string.tool_card_repeat_until, often, until.day, month)
        }

        count != null -> {
            stringResource(Res.string.tool_card_repeat_times, often, count)
        }

        else -> {
            often
        }
    }
}

/** "Every day", "Every 2 weeks on Mon and Fri": how often, without when it ends. */
@Composable
private fun oftenText(repeat: EventRepeat): String {
    val n = repeat.interval
    val (one, many) =
        when (repeat.every) {
            RepeatEvery.Day -> Res.string.tool_card_repeat_day to Res.string.tool_card_repeat_days
            RepeatEvery.Week -> Res.string.tool_card_repeat_week to Res.string.tool_card_repeat_weeks
            RepeatEvery.Month -> Res.string.tool_card_repeat_month to Res.string.tool_card_repeat_months
            RepeatEvery.Year -> Res.string.tool_card_repeat_year to Res.string.tool_card_repeat_years
        }
    val days = if (repeat.every == RepeatEvery.Week) dayList(repeat.weekdays) else null
    return when {
        days != null && n == 1 -> stringResource(Res.string.tool_card_repeat_week_on, days)
        days != null -> stringResource(Res.string.tool_card_repeat_weeks_on, n, days)
        n == 1 -> stringResource(one)
        else -> stringResource(many, n)
    }
}

/** "Tue", "Tue and Thu", "Mon, Wed and Fri"; null for none. */
@Composable
private fun dayList(weekdays: List<Int>): String? {
    val names = weekdays.map { stringResource(WEEKDAYS[it]) }
    if (names.isEmpty()) return null
    if (names.size == 1) return names.single()
    return stringResource(Res.string.tool_card_repeat_and, names.dropLast(1).joinToString(", "), names.last())
}

/**
 * Only this date, or it and every later one (canvas: RepeatRemoveA). Starts on what the agent
 * understood; the member's pick goes with Approve or Remove.
 */
@Composable
private fun ScopeSwitch(
    chosen: EventScope,
    onChoose: (EventScope) -> Unit,
) {
    HearthSegmented(
        options =
            listOf(
                EventScope.OnlyThis to stringResource(Res.string.tool_card_scope_this),
                EventScope.ThisAndFollowing to stringResource(Res.string.tool_card_scope_following),
            ),
        chosen = chosen,
        onChoose = onChoose,
        label = stringResource(Res.string.tool_card_scope),
    )
}

@Composable
private fun whenText(moment: CardWhen): String {
    val day = stringResource(WEEKDAYS[moment.weekday])
    val month = stringResource(MONTHS[moment.month - 1])
    return moment.time?.let { stringResource(Res.string.tool_card_when, day, moment.day, month, it) }
        ?: stringResource(Res.string.tool_card_when_all_day, day, moment.day, month)
}

private val WEEKDAYS =
    listOf(
        Res.string.tool_card_day_mon,
        Res.string.tool_card_day_tue,
        Res.string.tool_card_day_wed,
        Res.string.tool_card_day_thu,
        Res.string.tool_card_day_fri,
        Res.string.tool_card_day_sat,
        Res.string.tool_card_day_sun,
    )

private val MONTHS =
    listOf(
        Res.string.tool_card_month_jan,
        Res.string.tool_card_month_feb,
        Res.string.tool_card_month_mar,
        Res.string.tool_card_month_apr,
        Res.string.tool_card_month_may,
        Res.string.tool_card_month_jun,
        Res.string.tool_card_month_jul,
        Res.string.tool_card_month_aug,
        Res.string.tool_card_month_sep,
        Res.string.tool_card_month_oct,
        Res.string.tool_card_month_nov,
        Res.string.tool_card_month_dec,
    )

private val DAY_CHIPS =
    listOf(
        Res.string.tool_card_chip_mon to Res.string.tool_card_weekday_mon,
        Res.string.tool_card_chip_tue to Res.string.tool_card_weekday_tue,
        Res.string.tool_card_chip_wed to Res.string.tool_card_weekday_wed,
        Res.string.tool_card_chip_thu to Res.string.tool_card_weekday_thu,
        Res.string.tool_card_chip_fri to Res.string.tool_card_weekday_fri,
        Res.string.tool_card_chip_sat to Res.string.tool_card_weekday_sat,
        Res.string.tool_card_chip_sun to Res.string.tool_card_weekday_sun,
    )

private const val SEPARATOR = " · "

/** What Repeat's None is saved as, since a null pick means "as proposed". */
private const val NO_REPEAT = ""
private const val DAYS_IN_WEEK = 7

/** How tall a note's words start, so a few lines of it show without scrolling. */
private const val WORDS_LINES = 3
private const val MINUTES_PER_HOUR = 60

const val DAY_FIELD_TAG = "approval_card_day"
const val TIME_FIELD_TAG = "approval_card_time"
const val LAST_DATE_FIELD_TAG = "approval_card_last_date"
