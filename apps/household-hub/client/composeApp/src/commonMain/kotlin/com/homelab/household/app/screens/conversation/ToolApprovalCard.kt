package com.homelab.household.app.screens.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.homelab.household.app.components.ActionCard
import com.homelab.household.app.components.CardButtonRow
import com.homelab.household.app.components.DestructiveButton
import com.homelab.household.app.components.HearthCheckboxRow
import com.homelab.household.app.components.HearthDatePickerDialog
import com.homelab.household.app.components.HearthPickerField
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
import com.homelab.household.app.resources.tool_card_field_day
import com.homelab.household.app.resources.tool_card_field_empty
import com.homelab.household.app.resources.tool_card_field_note
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
import com.homelab.household.app.resources.tool_card_repeat_day
import com.homelab.household.app.resources.tool_card_repeat_days
import com.homelab.household.app.resources.tool_card_repeat_month
import com.homelab.household.app.resources.tool_card_repeat_months
import com.homelab.household.app.resources.tool_card_repeat_times
import com.homelab.household.app.resources.tool_card_repeat_until
import com.homelab.household.app.resources.tool_card_repeat_week
import com.homelab.household.app.resources.tool_card_repeat_week_on
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
import com.homelab.household.app.resources.tool_card_untitled
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
import com.homelab.household.domain.model.RepeatEvery
import com.homelab.household.domain.model.TimeOfDay
import com.homelab.household.domain.model.editProposal
import com.homelab.household.domain.model.editableFields
import com.homelab.household.domain.model.proposedValues
import org.jetbrains.compose.resources.StringResource
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
    val fields = remember(card) { editableFields(card) }
    var editing by rememberSaveable(card.toolCallId) { mutableStateOf(false) }
    var scope by rememberSaveable(card.toolCallId) { mutableStateOf(details.scope) }

    ActionCard(modifier = modifier) {
        CardTitle(label = label, tint = tint)
        if (editing) {
            EditingCard(
                card = card,
                fields = fields,
                onCancel = { editing = false },
                onApprove = { edited -> onDecide(card.toolCallId, true, decidedEdit(card, edited, scope)) },
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
    onCancel: () -> Unit,
    onApprove: (edited: ProposalDetails?) -> Unit,
) {
    val proposed = remember(card) { proposedValues(card) }
    // Each null until the member changes it; primitives, so they can be saved.
    var title by rememberSaveable(card.toolCallId) { mutableStateOf<String?>(null) }
    var words by rememberSaveable(card.toolCallId) { mutableStateOf<String?>(null) }
    var epochDay by rememberSaveable(card.toolCallId) { mutableStateOf<Long?>(null) }
    var minuteOfDay by rememberSaveable(card.toolCallId) { mutableStateOf<Int?>(null) }
    var tried by rememberSaveable(card.toolCallId) { mutableStateOf(false) }
    var picking by rememberSaveable(card.toolCallId) { mutableStateOf<EditField?>(null) }
    val values =
        EditValues(
            title = title ?: proposed.title,
            words = words ?: proposed.words,
            day = epochDay?.let(EventDate::fromEpochDay) ?: proposed.day,
            time = minuteOfDay?.let { TimeOfDay(it / MINUTES_PER_HOUR, it % MINUTES_PER_HOUR) } ?: proposed.time,
        )
    val edit = editProposal(card, values)
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
    val day = values.day
    val time = values.time
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
    val colors = HearthTheme.colors
    val label = stringResource(Res.string.tool_card_scope)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(HearthShapes.item)
                .background(colors.canvas)
                .border(HearthTheme.size.hairline, colors.outline, HearthShapes.item)
                .padding(HearthTheme.spacing.xs)
                .semantics { contentDescription = label }
                .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xs),
    ) {
        listOf(
            EventScope.OnlyThis to Res.string.tool_card_scope_this,
            EventScope.ThisAndFollowing to Res.string.tool_card_scope_following,
        ).forEach { (option, words) ->
            val selected = option == chosen
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = HearthTheme.size.touchTarget - HearthTheme.spacing.xs)
                        .clip(HearthShapes.button)
                        .background(if (selected) colors.surface else colors.canvas)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onChoose(option) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(words),
                    style = HearthTheme.typography.label,
                    color = if (selected) colors.textPrimary else colors.textMuted,
                )
            }
        }
    }
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

private const val SEPARATOR = " · "

/** How tall a note's words start, so a few lines of it show without scrolling. */
private const val WORDS_LINES = 3
private const val MINUTES_PER_HOUR = 60

const val DAY_FIELD_TAG = "approval_card_day"
const val TIME_FIELD_TAG = "approval_card_time"
