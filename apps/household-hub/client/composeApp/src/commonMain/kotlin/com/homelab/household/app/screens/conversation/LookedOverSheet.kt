package com.homelab.household.app.screens.conversation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.household.app.components.BentoCard
import com.homelab.household.app.components.ChipVariant
import com.homelab.household.app.components.EmptyState
import com.homelab.household.app.components.EmptyStateAction
import com.homelab.household.app.components.HearthChip
import com.homelab.household.app.components.SkeletonBlock
import com.homelab.household.app.components.SkeletonGroup
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.looked_over_all_memories
import com.homelab.household.app.resources.looked_over_caption
import com.homelab.household.app.resources.looked_over_remembered
import com.homelab.household.app.resources.looked_over_title
import com.homelab.household.app.resources.looked_over_to_check
import com.homelab.household.app.resources.memories_failed_line
import com.homelab.household.app.resources.memories_failed_title
import com.homelab.household.app.resources.memories_offline_line
import com.homelab.household.app.resources.memories_offline_title
import com.homelab.household.app.resources.memories_tab_household_plain
import com.homelab.household.app.resources.memories_tab_yours_plain
import com.homelab.household.app.resources.memories_try_again
import com.homelab.household.app.resources.memories_until
import com.homelab.household.app.screens.changes.ChangeCard
import com.homelab.household.app.screens.changes.WaitingHeading
import com.homelab.household.app.screens.changes.words
import com.homelab.household.app.screens.memories.RemoveButton
import com.homelab.household.app.screens.memories.RemovedRow
import com.homelab.household.app.screens.memories.SHORT_MONTHS
import com.homelab.household.app.screens.memories.words
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.app.theme.PreviewDayNight
import com.homelab.household.domain.model.MemoryScope
import com.homelab.household.domain.model.ProposalAnswer
import com.homelab.household.presentation.memories.LookedOverUiState
import com.homelab.household.presentation.memories.LookedOverViewModel
import com.homelab.household.presentation.memories.MemoriesStatus
import com.homelab.household.presentation.memories.MemoryRow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Tags the sheet's list so a test can scroll it to rows not yet drawn. */
internal const val LOOKED_OVER_SHEET_TAG = "looked-over-sheet"

/**
 * "Remembered from this chat" (canvas MemChatSheet), risen from the quiet line. Each opening reads
 * the chat's look-over afresh; closing it hands back to the chat, which recounts its line.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LookedOverSheet(
    sessionId: String,
    onDismiss: () -> Unit,
    onAllMemories: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: LookedOverViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(sessionId) { viewModel.open(sessionId) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = HearthShapes.sheet,
        containerColor = HearthTheme.colors.surface,
        contentColor = HearthTheme.colors.textPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = HearthTheme.colors.outline) },
    ) {
        LookedOverSheetContent(
            state = state,
            onAnswer = viewModel::onAnswer,
            onUndo = viewModel::onUndo,
            onRemove = viewModel::onRemove,
            onRestore = viewModel::onRestore,
            onRetry = viewModel::retry,
            onAllMemories = onAllMemories,
        )
    }
}

/**
 * The sheet's insides: changes to check, answered in place; those waiting for someone else; and what
 * was remembered, each with ✕. Its cards don't name the chat: it is the one already open.
 *
 * Stateless — [LookedOverSheet] owns the ViewModel.
 */
@Composable
fun LookedOverSheetContent(
    state: LookedOverUiState,
    onAnswer: (String, ProposalAnswer) -> Unit,
    onUndo: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRestore: (String) -> Unit,
    onRetry: () -> Unit,
    onAllMemories: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = HearthTheme.spacing
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .testTag(LOOKED_OVER_SHEET_TAG),
        contentPadding = PaddingValues(start = spacing.xl, end = spacing.xl, bottom = spacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        item(key = "heading") { SheetHeading(state = state) }

        when (state.status) {
            MemoriesStatus.Loading -> {
                item(key = "loading") { LoadingCard() }
            }

            MemoriesStatus.Unreachable, MemoriesStatus.Failed -> {
                item(key = "failed") { SheetFailed(unreachable = state.status == MemoriesStatus.Unreachable, onRetry) }
            }

            MemoriesStatus.Ready -> {
                state.failure?.let { failure ->
                    item(key = "failure") {
                        Text(
                            stringResource(failure.words()),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            style = HearthTheme.typography.caption,
                            color = HearthTheme.colors.error,
                        )
                    }
                }
                if (state.toCheck.isNotEmpty()) {
                    item(key = "to-check") {
                        SectionLabel(stringResource(Res.string.looked_over_to_check, state.openCount))
                    }
                    items(state.toCheck, key = { it.card.id }) { row ->
                        ChangeCard(
                            row = row,
                            onAnswer = { answer -> onAnswer(row.card.id, answer) },
                            onUndo = { onUndo(row.card.id) },
                            showSource = false,
                        )
                    }
                }
                state.waitingByAnswerer.forEach { (name, rows) ->
                    item(key = "waiting-$name") { WaitingHeading(name = name) }
                    items(rows, key = { it.card.id }) { row ->
                        ChangeCard(row = row, onAnswer = {}, onUndo = {}, showSource = false)
                    }
                }
                if (state.remembered.isNotEmpty()) {
                    item(key = "remembered") {
                        SectionLabel(stringResource(Res.string.looked_over_remembered, state.rememberedCount))
                    }
                    items(state.remembered, key = { it.item.id }) { row ->
                        if (row.removed) {
                            RemovedRow(row = row, onUndo = { onRestore(row.item.id) })
                        } else {
                            RememberedRow(row = row, onRemove = { onRemove(row.item.id) })
                        }
                    }
                }
                item(key = "all-memories") { AllMemoriesLink(onClick = onAllMemories) }
            }
        }
    }
}

/** "Remembered from this chat", and when it was looked over. */
@Composable
private fun SheetHeading(
    state: LookedOverUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = HearthTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xs),
    ) {
        Text(
            stringResource(Res.string.looked_over_title),
            style = HearthTheme.typography.title,
            color = HearthTheme.colors.textPrimary,
        )
        state.at?.let { at ->
            Text(
                stringResource(Res.string.looked_over_caption, at.words()),
                style = HearthTheme.typography.caption,
                color = HearthTheme.colors.textMuted,
            )
        }
    }
}

@Composable
private fun SectionLabel(
    words: String,
    modifier: Modifier = Modifier,
) {
    Text(
        words.uppercase(),
        modifier = modifier.padding(horizontal = HearthTheme.spacing.sm, vertical = HearthTheme.spacing.xs),
        style = HearthTheme.typography.overline,
        color = HearthTheme.colors.textMuted,
    )
}

/** A memory this chat added: the sentence, whose it is, its "until" day, and ✕ on your own. */
@Composable
private fun RememberedRow(
    row: MemoryRow,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = row.item
    BentoCard(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm)) {
                Text(item.content, style = HearthTheme.typography.body, color = HearthTheme.colors.textPrimary)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xs),
                ) {
                    val household = item.scope == MemoryScope.HOUSEHOLD
                    HearthChip(
                        label =
                            stringResource(
                                if (household) {
                                    Res.string.memories_tab_household_plain
                                } else {
                                    Res.string.memories_tab_yours_plain
                                },
                            ),
                        variant = if (household) ChipVariant.Neutral else ChipVariant.Primary,
                    )
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
                }
            }
            if (item.canRemove) {
                RemoveButton(content = item.content, onClick = onRemove)
            }
        }
    }
}

/** The way to the whole memory page, at the sheet's foot. */
@Composable
private fun AllMemoriesLink(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HearthTheme.size.touchTarget)
            .clip(HearthShapes.button)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(Res.string.looked_over_all_memories),
            style = HearthTheme.typography.labelStrong,
            color = HearthTheme.colors.primary,
        )
    }
}

@Composable
private fun SheetFailed(
    unreachable: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        modifier = modifier,
        icon = if (unreachable) HearthIcon.HubOffline else HearthIcon.Error,
        title = stringResource(
            if (unreachable) Res.string.memories_offline_title else Res.string.memories_failed_title,
        ),
        line = stringResource(if (unreachable) Res.string.memories_offline_line else Res.string.memories_failed_line),
        action = EmptyStateAction(stringResource(Res.string.memories_try_again), onRetry),
    )
}

@Composable
private fun LoadingCard(modifier: Modifier = Modifier) {
    SkeletonGroup(modifier = modifier.fillMaxWidth()) {
        BentoCard(modifier = Modifier.fillMaxWidth()) {
            SkeletonBlock(modifier = Modifier.fillMaxWidth(), position = 0)
            SkeletonBlock(widthFraction = 0.4f, position = 0)
        }
    }
}

@PreviewDayNight
@Composable
private fun LookedOverSheetContentPreview(
    @PreviewParameter(LookedOverUiStateProvider::class) state: LookedOverUiState,
) {
    HearthTheme {
        LookedOverSheetContent(
            state = state,
            onAnswer = { _, _ -> },
            onUndo = {},
            onRemove = {},
            onRestore = {},
            onRetry = {},
            onAllMemories = {},
        )
    }
}
