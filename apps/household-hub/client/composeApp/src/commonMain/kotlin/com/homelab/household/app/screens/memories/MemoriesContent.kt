package com.homelab.household.app.screens.memories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.homelab.household.app.components.BentoCard
import com.homelab.household.app.components.ChipVariant
import com.homelab.household.app.components.EmptyState
import com.homelab.household.app.components.EmptyStateAction
import com.homelab.household.app.components.HearthChip
import com.homelab.household.app.components.HearthScaffold
import com.homelab.household.app.components.HearthTopBar
import com.homelab.household.app.components.SkeletonBlock
import com.homelab.household.app.components.SkeletonGroup
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.changes_stored
import com.homelab.household.app.resources.changes_title
import com.homelab.household.app.resources.memories_back
import com.homelab.household.app.resources.memories_band_line
import com.homelab.household.app.resources.memories_empty_line
import com.homelab.household.app.resources.memories_empty_title
import com.homelab.household.app.resources.memories_failed_line
import com.homelab.household.app.resources.memories_failed_title
import com.homelab.household.app.resources.memories_household_note
import com.homelab.household.app.resources.memories_intro
import com.homelab.household.app.resources.memories_offline_line
import com.homelab.household.app.resources.memories_offline_title
import com.homelab.household.app.resources.memories_remove
import com.homelab.household.app.resources.memories_remove_failed
import com.homelab.household.app.resources.memories_remove_unreachable
import com.homelab.household.app.resources.memories_removed
import com.homelab.household.app.resources.memories_tab_household
import com.homelab.household.app.resources.memories_tab_household_plain
import com.homelab.household.app.resources.memories_tab_yours
import com.homelab.household.app.resources.memories_tab_yours_plain
import com.homelab.household.app.resources.memories_tabs
import com.homelab.household.app.resources.memories_title
import com.homelab.household.app.resources.memories_try_again
import com.homelab.household.app.resources.memories_undo
import com.homelab.household.app.resources.memories_undo_failed
import com.homelab.household.app.resources.memories_undo_unreachable
import com.homelab.household.app.resources.memories_until
import com.homelab.household.app.resources.memories_waiting_caption
import com.homelab.household.app.resources.memories_waiting_on
import com.homelab.household.app.resources.memories_waiting_yours
import com.homelab.household.app.resources.memories_withdraw
import com.homelab.household.app.resources.memories_withdrawn
import com.homelab.household.app.resources.memories_you
import com.homelab.household.app.resources.memories_you_asked
import com.homelab.household.app.screens.changes.SentenceBox
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.app.theme.PreviewDayNight
import com.homelab.household.domain.model.MemoryOrigin
import com.homelab.household.presentation.memories.MemoriesStatus
import com.homelab.household.presentation.memories.MemoriesUiState
import com.homelab.household.presentation.memories.MemoryActionFailure
import com.homelab.household.presentation.memories.MemoryRow
import com.homelab.household.presentation.memories.MemoryTab
import com.homelab.household.presentation.memories.WaitingRow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * "What the agents know" (canvas page "Memory (slice 5)", boards MemList, MemHousehold,
 * MemListRemoved, MemEmpty, MemListLoading, MemListOffline). Two tabs; ✕ on your own memories turns
 * the row into "Removed" with Undo in place. A long memory wraps in full, never clamped.
 *
 * Stateless — [MemoriesScreen] owns the ViewModel.
 */
@Composable
fun MemoriesContent(
    state: MemoriesUiState,
    onBack: () -> Unit,
    onTab: (MemoryTab) -> Unit,
    onChangesToCheck: () -> Unit,
    onRemove: (String) -> Unit,
    onUndo: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onWithdraw: (String) -> Unit = {},
    onUndoWithdraw: (String) -> Unit = {},
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    val ready = state.status == MemoriesStatus.Ready

    HearthScaffold(
        modifier = modifier,
        header = { HearthTopBar(onBack = onBack, backDescription = stringResource(Res.string.memories_back)) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.lg),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = HearthTheme.spacing.xs),
                verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm),
            ) {
                Text(stringResource(Res.string.memories_title), style = type.title, color = colors.textPrimary)
                Text(stringResource(Res.string.memories_intro), style = type.body, color = colors.textMuted)
            }

            if (ready && state.toCheck > 0) {
                ChangesBand(count = state.toCheck, onClick = onChangesToCheck)
            }

            MemoryTabs(
                chosen = state.tab,
                yours =
                    if (ready) {
                        stringResource(Res.string.memories_tab_yours, state.yoursCount)
                    } else {
                        stringResource(Res.string.memories_tab_yours_plain)
                    },
                household =
                    if (ready) {
                        stringResource(Res.string.memories_tab_household, state.householdCount)
                    } else {
                        stringResource(Res.string.memories_tab_household_plain)
                    },
                onTab = onTab,
            )

            state.failure?.let { failure ->
                Text(
                    stringResource(failure.words()),
                    modifier = Modifier
                        .padding(horizontal = HearthTheme.spacing.xs)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    style = type.caption,
                    color = colors.error,
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when (state.status) {
                    MemoriesStatus.Loading -> {
                        LoadingRows()
                    }

                    MemoriesStatus.Unreachable -> {
                        EmptyState(
                            icon = HearthIcon.HubOffline,
                            title = stringResource(Res.string.memories_offline_title),
                            line = stringResource(Res.string.memories_offline_line),
                            action = EmptyStateAction(stringResource(Res.string.memories_try_again), onRetry),
                        )
                    }

                    MemoriesStatus.Failed -> {
                        EmptyState(
                            icon = HearthIcon.Error,
                            title = stringResource(Res.string.memories_failed_title),
                            line = stringResource(Res.string.memories_failed_line),
                            action = EmptyStateAction(stringResource(Res.string.memories_try_again), onRetry),
                        )
                    }

                    MemoriesStatus.Ready -> {
                        val household = state.tab == MemoryTab.Household
                        val rows = if (household) state.household else state.yours
                        val waiting = if (household) state.waiting else emptyList()
                        if (rows.isEmpty() && waiting.isEmpty()) {
                            EmptyState(
                                icon = HearthIcon.Memory,
                                title = stringResource(Res.string.memories_empty_title),
                                line = stringResource(Res.string.memories_empty_line),
                            )
                        } else {
                            MemoryList(
                                rows = rows,
                                household = household,
                                onRemove = onRemove,
                                onUndo = onUndo,
                                waiting = waiting,
                                onWithdraw = onWithdraw,
                                onUndoWithdraw = onUndoWithdraw,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "2 changes to check" at the top of the page (board MemList); a tap opens Changes to check. */
@Composable
private fun ChangesBand(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HearthTheme.size.touchTarget)
            .clip(HearthShapes.item)
            .background(colors.secondaryContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(HearthTheme.spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xxs)) {
            Text(
                pluralStringResource(Res.plurals.changes_title, count, count),
                style = HearthTheme.typography.bodyStrong,
                color = colors.onSecondaryContainer,
            )
            Text(
                stringResource(Res.string.memories_band_line),
                style = HearthTheme.typography.caption,
                color = colors.onSecondaryContainer,
            )
        }
        HearthIconImage(
            icon = HearthIcon.ChevronRight,
            contentDescription = null,
            size = HearthTheme.size.iconMd,
            tint = colors.onSecondaryContainer,
        )
    }
}

/** Yours and Household, side by side in a raised tray; the chosen one is filled (board MemList). */
@Composable
private fun MemoryTabs(
    chosen: MemoryTab,
    yours: String,
    household: String,
    onTab: (MemoryTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val label = stringResource(Res.string.memories_tabs)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(HearthShapes.item)
            .background(colors.surface)
            .padding(HearthTheme.spacing.xs)
            .semantics { contentDescription = label }
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xs),
    ) {
        listOf(MemoryTab.Yours to yours, MemoryTab.Household to household).forEach { (tab, words) ->
            val selected = tab == chosen
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = HearthTheme.size.touchTarget)
                    .clip(HearthShapes.button)
                    .background(if (selected) colors.primary else colors.surface)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onTab(tab) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = words,
                    style = if (selected) HearthTheme.typography.labelStrong else HearthTheme.typography.caption,
                    color = if (selected) colors.onPrimary else colors.textMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun MemoryList(
    rows: List<MemoryRow>,
    household: Boolean,
    onRemove: (String) -> Unit,
    onUndo: (String) -> Unit,
    modifier: Modifier = Modifier,
    waiting: List<WaitingRow> = emptyList(),
    onWithdraw: (String) -> Unit = {},
    onUndoWithdraw: (String) -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
    ) {
        // First: each waits on someone, and the memory it waits on is drawn in it rather than below.
        items(waiting, key = { "waiting-${it.change.id}" }) { card ->
            if (card.withdrawn) {
                WithdrawnRow(content = card.change.content, onUndo = { onUndoWithdraw(card.change.id) })
            } else {
                WaitingCard(card = card, onWithdraw = { onWithdraw(card.change.id) })
            }
        }
        items(rows, key = { it.item.id }) { row ->
            if (row.removed) {
                RemovedRow(row = row, onUndo = { onUndo(row.item.id) })
            } else {
                MemoryCard(row = row, household = household, onRemove = { onRemove(row.item.id) })
            }
        }
        if (household) {
            item(key = "secret-note") { SecretModeNote(modifier = Modifier.padding(bottom = HearthTheme.spacing.xxl)) }
        }
    }
}

/** The sentence, then who · which agent · when, and its chips. ✕ only on your own. */
@Composable
private fun MemoryCard(
    row: MemoryRow,
    household: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    val item = row.item
    BentoCard(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md)) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm),
            ) {
                Text(item.content, style = type.body, color = colors.textPrimary)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xs),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    val age = ageWords(row.age)
                    if (household) {
                        SpeakerInitial(name = item.speakerName, mine = item.isMine)
                        val who = if (item.isMine) stringResource(Res.string.memories_you) else item.speakerName
                        Text("$who · ${item.agentName} · $age", style = type.monoSm, color = colors.textMuted)
                    } else {
                        Text(
                            "${item.agentAvatar} ${item.agentName} · $age",
                            style = type.monoSm,
                            color = colors.textMuted,
                        )
                    }
                    row.until?.let { until ->
                        HearthChip(
                            label = stringResource(
                                Res.string.memories_until,
                                until.day,
                                stringResource(
                                    SHORT_MONTHS[
                                        until.month -
                                            1,
                                    ],
                                ),
                            ),
                            variant = ChipVariant.Attention,
                            icon = HearthIcon.Schedule,
                        )
                    }
                    // Asked for by whoever said it: only yours read "You asked".
                    if (item.isMine && item.origin == MemoryOrigin.Tool) {
                        HearthChip(label = stringResource(Res.string.memories_you_asked), variant = ChipVariant.Primary)
                    }
                }
            }
            if (item.canRemove) {
                RemoveButton(content = item.content, onClick = onRemove)
            }
        }
    }
}

@Composable
internal fun RemoveButton(
    content: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(Res.string.memories_remove, content)
    Box(
        modifier = modifier
            .size(HearthTheme.size.touchTarget)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        HearthIconImage(
            icon = HearthIcon.Revoke,
            contentDescription = null,
            size = HearthTheme.size.iconMd,
            tint = HearthTheme.colors.textMuted,
        )
    }
}

/** The row after ✕ (board MemListRemoved): quiet, with Undo, until the page is left. */
@Composable
internal fun RemovedRow(
    row: MemoryRow,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
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
            Text(stringResource(Res.string.memories_removed), style = type.labelStrong, color = colors.textMuted)
            Text(row.item.content, style = type.body, color = colors.textMuted)
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

/**
 * A sentence you asked to remember over someone else's memory, waiting on them (board MemWaitingB):
 * what's stored, and yours beside it, not used yet. ✕ takes it back.
 */
@Composable
private fun WaitingCard(
    card: WaitingRow,
    onWithdraw: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    val change = card.change
    val stored = card.stored.item
    BentoCard(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HearthIconImage(
                    icon = HearthIcon.Waiting,
                    contentDescription = null,
                    size = HearthTheme.size.iconSm,
                    tint = colors.textMuted,
                )
                Text(
                    stringResource(Res.string.memories_waiting_on, change.answererName).uppercase(),
                    style = type.overline,
                    color = colors.textMuted,
                )
            }
            WithdrawButton(content = change.content, onClick = onWithdraw)
        }
        SentenceBox(
            label = stringResource(Res.string.changes_stored),
            sentence = stored.content,
            meta = "${stored.speakerName} · ${stored.agentName} · ${ageWords(card.stored.age)}",
            container = colors.secondaryContainer,
            ink = colors.onSecondaryContainer,
        )
        SentenceBox(
            label = stringResource(Res.string.memories_waiting_yours),
            sentence = change.content,
            meta = "${change.agentName} · ${ageWords(card.age)}",
            container = colors.surfaceAlt,
            ink = colors.textPrimary,
        )
        Text(
            stringResource(Res.string.memories_waiting_caption, change.answererName),
            style = type.caption,
            color = colors.textMuted,
        )
    }
}

@Composable
private fun WithdrawButton(
    content: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(Res.string.memories_withdraw, content)
    Box(
        modifier = modifier
            .size(HearthTheme.size.touchTarget)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        HearthIconImage(
            icon = HearthIcon.Revoke,
            contentDescription = null,
            size = HearthTheme.size.iconMd,
            tint = HearthTheme.colors.textMuted,
        )
    }
}

/** The card after ✕ (board MemWaitingWithdrawn): the removed row's look, with Undo. */
@Composable
private fun WithdrawnRow(
    content: String,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
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
            Text(stringResource(Res.string.memories_withdrawn), style = type.labelStrong, color = colors.textMuted)
            Text(content, style = type.body, color = colors.textMuted)
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

/** Who said a household memory: their initial on a small circle, yours in the accent's container. */
@Composable
private fun SpeakerInitial(
    name: String,
    mine: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    Box(
        modifier = modifier
            .size(HearthTheme.size.iconMd)
            .background(if (mine) colors.primaryContainer else colors.tertiaryContainer, CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().firstOrNull()?.uppercase().orEmpty(),
            style = HearthTheme.typography.glyphXs,
            color = if (mine) colors.onPrimaryContainer else colors.onTertiaryContainer,
        )
    }
}

@Composable
private fun SecretModeNote(modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), shape = HearthShapes.item, color = HearthTheme.colors.surfaceAlt) {
        Row(
            modifier = Modifier.padding(HearthTheme.spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
        ) {
            HearthIconImage(
                icon = HearthIcon.SecretLocked,
                contentDescription = null,
                size = HearthTheme.size.iconMd,
                tint = HearthTheme.colors.textMuted,
            )
            Text(
                stringResource(Res.string.memories_household_note),
                style = HearthTheme.typography.caption,
                color = HearthTheme.colors.textMuted,
            )
        }
    }
}

/** Arriving (board MemListLoading): rows breathe where the cards will land. */
@Composable
private fun LoadingRows(modifier: Modifier = Modifier) {
    SkeletonGroup(modifier = modifier.fillMaxWidth()) {
        repeat(LOADING_ROWS) { position ->
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                SkeletonBlock(modifier = Modifier.fillMaxWidth(), position = position)
                SkeletonBlock(widthFraction = 0.4f, position = position)
            }
        }
    }
}

private const val LOADING_ROWS = 3

internal fun MemoryActionFailure.words(): StringResource =
    when (this) {
        MemoryActionFailure.RemoveUnreachable -> Res.string.memories_remove_unreachable
        MemoryActionFailure.RemoveFailed -> Res.string.memories_remove_failed
        MemoryActionFailure.UndoUnreachable -> Res.string.memories_undo_unreachable
        MemoryActionFailure.UndoFailed -> Res.string.memories_undo_failed
    }

@PreviewDayNight
@Composable
private fun MemoriesContentPreview(
    @PreviewParameter(MemoriesUiStateProvider::class) state: MemoriesUiState,
) {
    HearthTheme {
        MemoriesContent(
            state = state,
            onBack = {},
            onTab = {},
            onChangesToCheck = {},
            onRemove = {},
            onUndo = {},
            onRetry = {},
        )
    }
}
