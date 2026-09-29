package com.homelab.household.app.screens.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.homelab.household.app.components.ActionCard
import com.homelab.household.app.components.CardButtonRow
import com.homelab.household.app.components.DestructiveButton
import com.homelab.household.app.components.HearthCheckboxRow
import com.homelab.household.app.components.PrimaryButton
import com.homelab.household.app.components.SecondaryButton
import com.homelab.household.app.components.ToolRecordLine
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.tool_card_approve
import com.homelab.household.app.resources.tool_card_approved
import com.homelab.household.app.resources.tool_card_day_fri
import com.homelab.household.app.resources.tool_card_day_mon
import com.homelab.household.app.resources.tool_card_day_sat
import com.homelab.household.app.resources.tool_card_day_sun
import com.homelab.household.app.resources.tool_card_day_thu
import com.homelab.household.app.resources.tool_card_day_tue
import com.homelab.household.app.resources.tool_card_day_wed
import com.homelab.household.app.resources.tool_card_decline
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
import com.homelab.household.app.resources.tool_card_replace
import com.homelab.household.app.resources.tool_card_untitled
import com.homelab.household.app.resources.tool_card_when
import com.homelab.household.app.resources.tool_card_when_all_day
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.domain.model.AnswerPart
import com.homelab.household.domain.model.ProposalStatus
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
 * Once answered while the step's other cards still wait, it shrinks to its record line, so what is
 * left to answer stands out; when the last one is answered the turn carries on and the hub turns
 * each into the step it became.
 */
@Composable
fun ToolApprovalCard(
    card: AnswerPart.Proposal,
    onDecide: (toolCallId: String, approved: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onApproveAutomatically: (toolCallId: String) -> Unit = { onDecide(it, true) },
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
    onDecide: (toolCallId: String, approved: Boolean) -> Unit,
    onApproveAutomatically: (toolCallId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    val ask = cardAsk(card.action)
    var automatic by rememberSaveable(card.toolCallId) { mutableStateOf(false) }
    val tint = if (ask == CardAsk.Approve) colors.textPrimary else colors.error

    ActionCard(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HearthIconImage(icon = label.icon, contentDescription = null, size = HearthTheme.size.iconSm, tint = tint)
            Text(
                text = stringResource(label.card ?: label.running),
                style = type.labelStrong,
                color = tint,
            )
        }
        // Under the title's words rather than its icon, as the canvas lines them up.
        Column(
            modifier = Modifier.padding(start = HearthTheme.size.iconSm + HearthTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xs),
        ) {
            Text(text = name, style = type.bodyStrong, color = colors.textPrimary)
            val caption = details.whenAt?.let { whenText(it) } ?: details.preview
            if (caption != null) {
                Text(text = caption, style = type.caption, color = colors.textMuted)
            }
        }
        val automaticAsk = label.automaticAsk
        if (automaticAsk != null && ask == CardAsk.Approve) {
            HearthCheckboxRow(
                text = stringResource(automaticAsk),
                checked = automatic,
                onCheckedChange = { automatic = it },
            )
        }
        CardButtonRow {
            val no = if (ask == CardAsk.Approve) Res.string.tool_card_decline else Res.string.tool_card_keep
            SecondaryButton(
                text = stringResource(no),
                onClick = { onDecide(card.toolCallId, false) },
                modifier = Modifier.weight(1f),
            )
            when (ask) {
                CardAsk.Approve -> {
                    PrimaryButton(
                        text = stringResource(Res.string.tool_card_approve),
                        onClick = {
                            if (automatic) onApproveAutomatically(card.toolCallId) else onDecide(card.toolCallId, true)
                        },
                        modifier = Modifier.weight(1f),
                        lifted = false,
                    )
                }

                CardAsk.Remove, CardAsk.Replace -> {
                    DestructiveButton(
                        text =
                            stringResource(
                                if (ask ==
                                    CardAsk.Remove
                                ) {
                                    Res.string.tool_card_remove
                                } else {
                                    Res.string.tool_card_replace
                                },
                            ),
                        onClick = { onDecide(card.toolCallId, true) },
                        modifier = Modifier.weight(1f),
                    )
                }
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
