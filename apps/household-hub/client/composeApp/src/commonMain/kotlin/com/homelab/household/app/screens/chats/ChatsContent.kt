package com.homelab.household.app.screens.chats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.homelab.household.app.components.ChatRow
import com.homelab.household.app.components.EmptyState
import com.homelab.household.app.components.EmptyStateAction
import com.homelab.household.app.components.HearthProgressBar
import com.homelab.household.app.components.HearthProgressBarWidth
import com.homelab.household.app.components.HearthScaffold
import com.homelab.household.app.components.HearthTextField
import com.homelab.household.app.components.SecondaryButton
import com.homelab.household.app.components.SkeletonBlock
import com.homelab.household.app.components.SkeletonGroup
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.chats_empty_action
import com.homelab.household.app.resources.chats_empty_line
import com.homelab.household.app.resources.chats_empty_title
import com.homelab.household.app.resources.chats_no_matches_line
import com.homelab.household.app.resources.chats_no_matches_title
import com.homelab.household.app.resources.chats_retry
import com.homelab.household.app.resources.chats_search_cancel
import com.homelab.household.app.resources.chats_search_count
import com.homelab.household.app.resources.chats_search_footnote
import com.homelab.household.app.resources.chats_search_placeholder
import com.homelab.household.app.resources.chats_title
import com.homelab.household.app.resources.chats_unreachable
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.app.theme.PreviewDayNight
import com.homelab.household.presentation.chats.ChatsUiState
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Every conversation, newest first, with a search over them.
 *
 * Three empty-looking states that are not the same thing, and the screen says which: a household
 * that has never chatted gets the one empty state with a button on it, a query that found nothing
 * says so in the query's own words, and a hub that cannot be reached says that instead of
 * pretending there is nothing here.
 *
 * Stateless — [ChatsScreen] owns the ViewModel.
 */
@Composable
fun ChatsContent(
    state: ChatsUiState,
    onSearch: (String) -> Unit,
    onCancelSearch: () -> Unit,
    onOpen: (String) -> Unit,
    onNewChat: () -> Unit,
    onRetry: () -> Unit,
    tabs: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    val searching = state.query.isNotBlank()

    HearthScaffold(
        modifier = modifier,
        bottomBar = tabs,
        header = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        // Chats has no Material 3 top bar to inset it, so the header does it
                        // itself; otherwise the title sits under the status bar.
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                        ),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = HearthTheme.spacing.xl, vertical = HearthTheme.spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.chats_title),
                        style = type.heading,
                        color = colors.textPrimary,
                    )
                }
                // Both arrive with the first chat: an empty list has nothing to search.
                if (!state.isEmpty) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = HearthTheme.spacing.xl,
                                    end = HearthTheme.spacing.xl,
                                    bottom = HearthTheme.spacing.md,
                                ),
                        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HearthTextField(
                            value = state.query,
                            onValueChange = onSearch,
                            label = null,
                            leadingIcon = HearthIcon.Search,
                            placeholder = stringResource(Res.string.chats_search_placeholder),
                            modifier = Modifier.weight(1f),
                        )
                        if (searching) {
                            SecondaryButton(
                                text = stringResource(Res.string.chats_search_cancel),
                                onClick = onCancelSearch,
                            )
                        }
                    }
                }
                if (state.isLoading && state.visible.isNotEmpty()) {
                    HearthProgressBar(width = HearthProgressBarWidth.FullBleed)
                }
                HorizontalDivider(thickness = HearthTheme.size.hairline, color = colors.outlineSoft)
            }
        },
    ) { padding ->
        // Anything that stands in for the list — the empty screen, a query that matched nothing, a
        // hub that cannot be reached — sits in the middle of the space the list would have used.
        // A list, and the blocks that stand in for one while it arrives, start at the top.
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when {
                state.errorMessage != null -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(HearthTheme.spacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.lg),
                    ) {
                        Text(
                            text = stringResource(Res.string.chats_unreachable),
                            style = type.body,
                            color = colors.textMuted,
                        )
                        SecondaryButton(text = stringResource(Res.string.chats_retry), onClick = onRetry)
                    }
                }

                state.isLoading && state.visible.isEmpty() -> {
                    // Arriving empty: chrome is already drawn, so the rows breathe where they land.
                    SkeletonGroup(modifier = Modifier.align(Alignment.TopCenter).padding(HearthTheme.spacing.xl)) {
                        repeat(4) { SkeletonBlock(modifier = Modifier.fillMaxWidth()) }
                    }
                }

                state.isEmpty -> {
                    EmptyState(
                        icon = HearthIcon.Chats,
                        title = stringResource(Res.string.chats_empty_title),
                        line = stringResource(Res.string.chats_empty_line),
                        action =
                            EmptyStateAction(
                                label = stringResource(Res.string.chats_empty_action),
                                onClick = onNewChat,
                            ),
                    )
                }

                state.hasNoMatches -> {
                    EmptyState(
                        icon = HearthIcon.Search,
                        title = stringResource(Res.string.chats_no_matches_title, state.query),
                        line = stringResource(Res.string.chats_no_matches_line),
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().align(Alignment.TopCenter),
                        contentPadding =
                            androidx.compose.foundation.layout
                                .PaddingValues(HearthTheme.spacing.xl),
                    ) {
                        if (searching) {
                            item {
                                Text(
                                    text =
                                        pluralStringResource(
                                            Res.plurals.chats_search_count,
                                            state.visible.size,
                                            state.visible.size,
                                        ),
                                    style = type.labelStrong,
                                    color = colors.textMuted,
                                    modifier = Modifier.padding(bottom = HearthTheme.spacing.sm),
                                )
                            }
                        }
                        items(state.visible, key = { it.id }) { session ->
                            ChatRow(
                                title = session.title,
                                timestamp = session.updatedAt.orEmpty(),
                                preview = session.lastMessagePreview,
                                agentAvatar = session.agentAvatar,
                                onClick = { onOpen(session.id) },
                            )
                        }
                        if (searching) {
                            // Says plainly what search cannot do, so a miss isn't a lost chat.
                            item {
                                Text(
                                    text = stringResource(Res.string.chats_search_footnote),
                                    style = type.caption,
                                    color = colors.textMuted,
                                    modifier = Modifier.padding(top = HearthTheme.spacing.lg),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@PreviewDayNight
@Composable
private fun ChatsContentPreview(
    @PreviewParameter(ChatsUiStateProvider::class) state: ChatsUiState,
) {
    HearthTheme {
        ChatsContent(
            state = state,
            onSearch = {},
            onCancelSearch = {},
            onOpen = {},
            onNewChat = {},
            onRetry = {},
            tabs = {},
        )
    }
}
