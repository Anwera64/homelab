package com.homelab.household.app.screens.changes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.homelab.household.app.components.BentoCard
import com.homelab.household.app.components.EmptyState
import com.homelab.household.app.components.EmptyStateAction
import com.homelab.household.app.components.HearthScaffold
import com.homelab.household.app.components.HearthTopBar
import com.homelab.household.app.components.SkeletonBlock
import com.homelab.household.app.components.SkeletonGroup
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.changes_back
import com.homelab.household.app.resources.changes_intro
import com.homelab.household.app.resources.changes_none_line
import com.homelab.household.app.resources.changes_none_title
import com.homelab.household.app.resources.changes_title
import com.homelab.household.app.resources.changes_waiting_for
import com.homelab.household.app.resources.changes_waiting_line
import com.homelab.household.app.resources.memories_failed_line
import com.homelab.household.app.resources.memories_failed_title
import com.homelab.household.app.resources.memories_offline_line
import com.homelab.household.app.resources.memories_offline_title
import com.homelab.household.app.resources.memories_try_again
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.app.theme.PreviewDayNight
import com.homelab.household.domain.model.ProposalAnswer
import com.homelab.household.presentation.memories.ChangesToCheckUiState
import com.homelab.household.presentation.memories.MemoriesStatus
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Changes to check (canvas MemProposals, MemProposalsLiam, MemProposalsSaving, MemProposalsFailed,
 * MemProposalsDone, MemProposalsLong): your cards to answer, then the ones from your chats waiting for
 * someone else, read-only. Opened from the band on the memory page.
 *
 * Stateless — [ChangesToCheckScreen] owns the ViewModel.
 */
@Composable
fun ChangesToCheckContent(
    state: ChangesToCheckUiState,
    onBack: () -> Unit,
    onAnswer: (String, ProposalAnswer) -> Unit,
    onUndo: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HearthScaffold(
        modifier = modifier,
        header = { HearthTopBar(onBack = onBack, backDescription = stringResource(Res.string.changes_back)) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (state.status) {
                MemoriesStatus.Loading -> {
                    LoadingCards()
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
                    ChangeList(state = state, onAnswer = onAnswer, onUndo = onUndo)
                }
            }
        }
    }
}

@Composable
private fun ChangeList(
    state: ChangesToCheckUiState,
    onAnswer: (String, ProposalAnswer) -> Unit,
    onUndo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
    ) {
        item(key = "heading") { Heading(openCount = state.openCount) }
        items(state.toCheck, key = { it.card.id }) { row ->
            ChangeCard(
                row = row,
                onAnswer = { answer -> onAnswer(row.card.id, answer) },
                onUndo = { onUndo(row.card.id) },
            )
        }
        state.waitingByAnswerer.forEach { (name, rows) ->
            item(key = "waiting-$name") { WaitingHeading(name = name) }
            items(rows, key = { it.card.id }) { row -> ChangeCard(row = row, onAnswer = {}, onUndo = {}) }
        }
    }
}

/** "2 changes to check", or "Nothing left to check" once every card is answered. */
@Composable
private fun Heading(
    openCount: Int,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    Column(
        modifier = modifier.padding(horizontal = HearthTheme.spacing.xs, vertical = HearthTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm),
    ) {
        if (openCount == 0) {
            Text(stringResource(Res.string.changes_none_title), style = type.title, color = colors.textPrimary)
            Text(stringResource(Res.string.changes_none_line), style = type.body, color = colors.textMuted)
        } else {
            Text(
                pluralStringResource(Res.plurals.changes_title, openCount, openCount),
                style = type.title,
                color = colors.textPrimary,
            )
            Text(stringResource(Res.string.changes_intro), style = type.body, color = colors.textMuted)
        }
    }
}

/** "Waiting for Liam": only the person who said the stored memory chooses. */
@Composable
internal fun WaitingHeading(
    name: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = HearthTheme.spacing.xs, vertical = HearthTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xs),
    ) {
        Text(
            stringResource(Res.string.changes_waiting_for, name).uppercase(),
            style = HearthTheme.typography.overline,
            color = HearthTheme.colors.textMuted,
        )
        Text(
            stringResource(Res.string.changes_waiting_line, name),
            style = HearthTheme.typography.caption,
            color = HearthTheme.colors.textMuted,
        )
    }
}

@Composable
private fun LoadingCards(modifier: Modifier = Modifier) {
    SkeletonGroup(modifier = modifier.fillMaxWidth()) {
        repeat(LOADING_CARDS) { position ->
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                SkeletonBlock(modifier = Modifier.fillMaxWidth(), position = position)
                SkeletonBlock(modifier = Modifier.fillMaxWidth(), position = position)
                SkeletonBlock(widthFraction = 0.4f, position = position)
            }
        }
    }
}

private const val LOADING_CARDS = 2

@PreviewDayNight
@Composable
private fun ChangesToCheckContentPreview(
    @PreviewParameter(ChangesToCheckUiStateProvider::class) state: ChangesToCheckUiState,
) {
    HearthTheme {
        ChangesToCheckContent(state = state, onBack = {}, onAnswer = { _, _ -> }, onUndo = {}, onRetry = {})
    }
}

