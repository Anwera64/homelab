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
import com.homelab.household.app.resources.memories_age_last_week
import com.homelab.household.app.resources.memories_age_month_year
import com.homelab.household.app.resources.memories_age_today
import com.homelab.household.app.resources.memories_age_yesterday
import com.homelab.household.app.resources.memories_back
import com.homelab.household.app.resources.memories_empty_line
import com.homelab.household.app.resources.memories_empty_title
import com.homelab.household.app.resources.memories_failed_line
import com.homelab.household.app.resources.memories_failed_title
import com.homelab.household.app.resources.memories_household_note
import com.homelab.household.app.resources.memories_intro
import com.homelab.household.app.resources.memories_month_apr
import com.homelab.household.app.resources.memories_month_aug
import com.homelab.household.app.resources.memories_month_dec
import com.homelab.household.app.resources.memories_month_feb
import com.homelab.household.app.resources.memories_month_jan
import com.homelab.household.app.resources.memories_month_jul
import com.homelab.household.app.resources.memories_month_jun
import com.homelab.household.app.resources.memories_month_mar
import com.homelab.household.app.resources.memories_month_may
import com.homelab.household.app.resources.memories_month_nov
import com.homelab.household.app.resources.memories_month_oct
import com.homelab.household.app.resources.memories_month_sep
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
import com.homelab.household.app.resources.memories_you
import com.homelab.household.app.resources.memories_you_asked
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
import com.homelab.household.app.resources.tool_card_weekday_fri
import com.homelab.household.app.resources.tool_card_weekday_mon
import com.homelab.household.app.resources.tool_card_weekday_sat
import com.homelab.household.app.resources.tool_card_weekday_sun
import com.homelab.household.app.resources.tool_card_weekday_thu
import com.homelab.household.app.resources.tool_card_weekday_tue
import com.homelab.household.app.resources.tool_card_weekday_wed
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.app.theme.PreviewDayNight
import com.homelab.household.domain.model.MemoryOrigin
import com.homelab.household.presentation.memories.LocalDay
import com.homelab.household.presentation.memories.MemoriesStatus
import com.homelab.household.presentation.memories.MemoriesUiState
import com.homelab.household.presentation.memories.MemoryActionFailure
import com.homelab.household.presentation.memories.MemoryAge
import com.homelab.household.presentation.memories.MemoryRow
import com.homelab.household.presentation.memories.MemoryTab
import org.jetbrains.compose.resources.StringResource
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
    onRemove: (String) -> Unit,
    onUndo: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
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
                        val rows = if (state.tab == MemoryTab.Yours) state.yours else state.household
                        if (rows.isEmpty()) {
                            EmptyState(
                                icon = HearthIcon.Memory,
                                title = stringResource(Res.string.memories_empty_title),
                                line = stringResource(Res.string.memories_empty_line),
                            )
                        } else {
                            MemoryList(
                                rows = rows,
                                household = state.tab == MemoryTab.Household,
                                onRemove = onRemove,
                                onUndo = onUndo,
                            )
                        }
                    }
                }
            }
        }
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
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
    ) {
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
                    if (item.origin == MemoryOrigin.Tool) {
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
private fun RemoveButton(
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
private fun RemovedRow(
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

@Composable
private fun ageWords(age: MemoryAge): String =
    when (age) {
        MemoryAge.Today -> stringResource(Res.string.memories_age_today)

        MemoryAge.Yesterday -> stringResource(Res.string.memories_age_yesterday)

        is MemoryAge.Weekday -> stringResource(WEEKDAYS[age.isoDay - 1])

        MemoryAge.LastWeek -> stringResource(Res.string.memories_age_last_week)

        is MemoryAge.Month -> stringResource(MONTHS[age.month - 1])

        is MemoryAge.MonthYear -> stringResource(
            Res.string.memories_age_month_year,
            stringResource(MONTHS[age.month - 1]),
            age.year,
        )
    }

private fun MemoryActionFailure.words(): StringResource =
    when (this) {
        MemoryActionFailure.RemoveUnreachable -> Res.string.memories_remove_unreachable
        MemoryActionFailure.RemoveFailed -> Res.string.memories_remove_failed
        MemoryActionFailure.UndoUnreachable -> Res.string.memories_undo_unreachable
        MemoryActionFailure.UndoFailed -> Res.string.memories_undo_failed
    }

private val WEEKDAYS =
    listOf(
        Res.string.tool_card_weekday_mon,
        Res.string.tool_card_weekday_tue,
        Res.string.tool_card_weekday_wed,
        Res.string.tool_card_weekday_thu,
        Res.string.tool_card_weekday_fri,
        Res.string.tool_card_weekday_sat,
        Res.string.tool_card_weekday_sun,
    )

private val MONTHS =
    listOf(
        Res.string.memories_month_jan,
        Res.string.memories_month_feb,
        Res.string.memories_month_mar,
        Res.string.memories_month_apr,
        Res.string.memories_month_may,
        Res.string.memories_month_jun,
        Res.string.memories_month_jul,
        Res.string.memories_month_aug,
        Res.string.memories_month_sep,
        Res.string.memories_month_oct,
        Res.string.memories_month_nov,
        Res.string.memories_month_dec,
    )

private val SHORT_MONTHS =
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
            onRemove = {},
            onUndo = {},
            onRetry = {},
        )
    }
}
