package com.homelab.household.app.screens.changes

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.homelab.household.app.components.BentoCard
import com.homelab.household.app.components.ChipVariant
import com.homelab.household.app.components.HearthChip
import com.homelab.household.app.components.PrimaryButton
import com.homelab.household.app.components.SecondaryButton
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.changes_done_both
import com.homelab.household.app.resources.changes_done_kept
import com.homelab.household.app.resources.changes_done_replaced
import com.homelab.household.app.resources.changes_failed
import com.homelab.household.app.resources.changes_keep_both
import com.homelab.household.app.resources.changes_keep_stored
import com.homelab.household.app.resources.changes_read
import com.homelab.household.app.resources.changes_saving
import com.homelab.household.app.resources.changes_source_owner
import com.homelab.household.app.resources.changes_source_title
import com.homelab.household.app.resources.changes_stored
import com.homelab.household.app.resources.changes_undo_failed
import com.homelab.household.app.resources.changes_use_new
import com.homelab.household.app.resources.memories_tab_household_plain
import com.homelab.household.app.resources.memories_tab_yours_plain
import com.homelab.household.app.resources.memories_undo
import com.homelab.household.app.resources.memories_you
import com.homelab.household.app.screens.memories.ageWords
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.domain.model.MemoryScope
import com.homelab.household.domain.model.ProposalAnswer
import com.homelab.household.presentation.memories.ChangeRow
import com.homelab.household.presentation.memories.ChangeStatus
import com.homelab.household.presentation.memories.LocalTime
import com.homelab.household.presentation.memories.MemoryAge
import org.jetbrains.compose.resources.stringResource

/**
 * One change to check (canvas MemProposals): what's stored beside what an agent read in a chat, and
 * the three answers. [showSource] is off in the chat sheet, where the chat is the one already open.
 * A card the member can't answer has no buttons; a done one becomes a short row with Undo.
 */
@Composable
internal fun ChangeCard(
    row: ChangeRow,
    onAnswer: (ProposalAnswer) -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
    showSource: Boolean = true,
) {
    when (val status = row.status) {
        is ChangeStatus.Done -> {
            ChangeDoneRow(row = row, done = status, onUndo = onUndo, modifier = modifier)
        }

        is ChangeStatus.Withdrawn -> {
            ChangeWithdrawnRow(words = status.message, modifier = modifier)
        }

        else -> {
            ChangeOpenCard(row = row, onAnswer = onAnswer, showSource = showSource, modifier = modifier)
        }
    }
}

@Composable
private fun ChangeOpenCard(
    row: ChangeRow,
    onAnswer: (ProposalAnswer) -> Unit,
    showSource: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val card = row.card
    val stored = card.stored
    BentoCard(modifier = modifier.fillMaxWidth()) {
        HearthChip(
            label =
                stringResource(
                    if (stored.scope == MemoryScope.HOUSEHOLD) {
                        Res.string.memories_tab_household_plain
                    } else {
                        Res.string.memories_tab_yours_plain
                    },
                ),
            variant = if (stored.scope == MemoryScope.HOUSEHOLD) ChipVariant.Neutral else ChipVariant.Primary,
        )
        val who = if (stored.isMine) stringResource(Res.string.memories_you) else stored.speakerName
        SentenceBox(
            label = stringResource(Res.string.changes_stored),
            sentence = stored.content,
            meta = "$who · ${stored.agentName} · ${ageWords(row.storedAge)}",
            container = colors.secondaryContainer,
            ink = colors.onSecondaryContainer,
        )
        SentenceBox(
            label = stringResource(Res.string.changes_read),
            sentence = card.content,
            meta = if (showSource) sourceWords(row) else null,
            container = colors.surfaceAlt,
            ink = colors.textPrimary,
        )
        val status = row.status
        if (status is ChangeStatus.Open && status.failed) {
            Text(
                stringResource(Res.string.changes_failed),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = HearthTheme.typography.caption,
                color = colors.error,
            )
        }
        if (card.canAnswer) {
            Answers(saving = (status as? ChangeStatus.Saving)?.answer, onAnswer = onAnswer)
        }
    }
}

/** A labelled box: the stored memory in the accent's container, what was read on a neutral one. */
@Composable
internal fun SentenceBox(
    label: String,
    sentence: String,
    meta: String?,
    container: Color,
    ink: Color,
    modifier: Modifier = Modifier,
) {
    val type = HearthTheme.typography
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(HearthShapes.item)
            .background(container)
            .padding(HearthTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xs),
    ) {
        Text(label.uppercase(), style = type.overline, color = ink.copy(alpha = MUTED_ALPHA))
        Text(sentence, style = type.body, color = ink)
        if (meta != null) {
            Text(meta, style = type.monoSm, color = ink.copy(alpha = MUTED_ALPHA))
        }
    }
}

/** Keep stored · Keep both · Use new; Use new is the filled one. The tapped one says "Saving…". */
@Composable
private fun Answers(
    saving: ProposalAnswer?,
    onAnswer: (ProposalAnswer) -> Unit,
    modifier: Modifier = Modifier,
) {
    val savingWords = stringResource(Res.string.changes_saving)
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm)) {
        listOf(
            ProposalAnswer.KeepStored to Res.string.changes_keep_stored,
            ProposalAnswer.KeepBoth to Res.string.changes_keep_both,
        ).forEach { (answer, words) ->
            SecondaryButton(
                text = if (saving == answer) savingWords else stringResource(words),
                onClick = { onAnswer(answer) },
                modifier = Modifier.weight(1f),
                busy = saving == answer,
                compact = true,
            )
        }
        PrimaryButton(
            text = if (saving == ProposalAnswer.UseNew) savingWords else stringResource(Res.string.changes_use_new),
            onClick = { onAnswer(ProposalAnswer.UseNew) },
            modifier = Modifier.weight(1f),
            busy = saving == ProposalAnswer.UseNew,
            lifted = false,
            compact = true,
        )
    }
}

/** After an answer: one quiet line saying what happened, with Undo, until the page is left. */
@Composable
private fun ChangeDoneRow(
    row: ChangeRow,
    done: ChangeStatus.Done,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    val words =
        when (done.answer) {
            ProposalAnswer.UseNew -> stringResource(Res.string.changes_done_replaced, row.card.stored.content)
            ProposalAnswer.KeepStored -> stringResource(Res.string.changes_done_kept, row.card.content)
            ProposalAnswer.KeepBoth -> stringResource(Res.string.changes_done_both, row.card.stored.content)
        }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(HearthShapes.bento)
            .background(colors.surfaceAlt)
            .border(HearthTheme.size.hairline, colors.outlineSoft, HearthShapes.bento)
            .padding(HearthTheme.spacing.lg)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xs)) {
            Text(words, style = type.body, color = colors.textMuted)
            if (done.undoFailed) {
                Text(stringResource(Res.string.changes_undo_failed), style = type.caption, color = colors.error)
            }
        }
        Box(
            modifier = Modifier
                .heightIn(min = HearthTheme.size.touchTarget)
                .widthIn(min = HearthTheme.size.touchTarget)
                .clip(HearthShapes.button)
                .clickable(role = Role.Button, onClick = onUndo)
                .padding(horizontal = HearthTheme.spacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(Res.string.memories_undo), style = type.labelStrong, color = colors.primary)
        }
    }
}

/** A late answer to a sentence its asker took back: the hub's words, quiet, with nothing left to tap. */
@Composable
private fun ChangeWithdrawnRow(
    words: String,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    Text(
        words,
        modifier = modifier
            .fillMaxWidth()
            .clip(HearthShapes.bento)
            .background(colors.surfaceAlt)
            .border(HearthTheme.size.hairline, colors.outlineSoft, HearthShapes.bento)
            .padding(HearthTheme.spacing.lg)
            .semantics { liveRegion = LiveRegionMode.Polite },
        style = HearthTheme.typography.body,
        color = colors.textMuted,
    )
}

/** "“Dinner ideas” · today 10:42" in your own chat; someone else's shows only "Liam’s chat · yesterday". */
@Composable
private fun sourceWords(row: ChangeRow): String {
    val card = row.card
    val chat =
        card.sourceTitle?.let { stringResource(Res.string.changes_source_title, it) }
            ?: stringResource(Res.string.changes_source_owner, card.sourceOwnerName)
    val age = ageWords(row.readAge)
    return if (row.readAge == MemoryAge.Today) "$chat · $age ${row.readTime.words()}" else "$chat · $age"
}

/** 10:42, on the 24-hour clock the rest of the app uses. */
internal fun LocalTime.words(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

private const val MUTED_ALPHA = 0.72f
