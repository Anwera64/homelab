package com.homelab.household.app.screens.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.homelab.household.app.components.HearthScaffold
import com.homelab.household.app.components.HearthTopBar
import com.homelab.household.app.components.MessageBubble
import com.homelab.household.app.components.MessageComposer
import com.homelab.household.app.components.SecondaryButton
import com.homelab.household.app.components.TurnStatusLine
import com.homelab.household.app.components.TurnStatusTone
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.conversation_back
import com.homelab.household.app.resources.conversation_composer_idle
import com.homelab.household.app.resources.conversation_composer_leave
import com.homelab.household.app.resources.conversation_composer_waiting
import com.homelab.household.app.resources.conversation_failed_line
import com.homelab.household.app.resources.conversation_failed_title
import com.homelab.household.app.resources.conversation_greeting
import com.homelab.household.app.resources.conversation_not_sent
import com.homelab.household.app.resources.conversation_reconnecting
import com.homelab.household.app.resources.conversation_reconnecting_detail
import com.homelab.household.app.resources.conversation_retry
import com.homelab.household.app.resources.conversation_still_working
import com.homelab.household.app.resources.conversation_still_working_detail
import com.homelab.household.app.resources.conversation_try_again
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.app.theme.PreviewDayNight
import com.homelab.household.domain.model.MessageRole
import com.homelab.household.domain.model.MessageStatus
import com.homelab.household.presentation.chatsession.ChatSessionUiState
import com.homelab.household.presentation.chatsession.TurnState
import org.jetbrains.compose.resources.stringResource

/**
 * A conversation, and a new one — the same screen.
 *
 * New Chat is this with no messages in it: the agent's greeting where the transcript will be, and
 * the same header and composer. Nothing is created on the hub until the first message is sent.
 *
 * The composer is what reports the turn's state. It is never dimmed (design notes §2): while an
 * answer is being written it keeps its colour and its placeholder says what it is waiting for,
 * because a send at that moment would answer 409 and failing at it would be worse than saying so.
 *
 * Stateless — [ConversationScreen] owns the ViewModel.
 */
@Composable
fun ConversationContent(
    state: ChatSessionUiState,
    onComposerTextChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onRetry: (String) -> Unit,
    onTryAgain: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    memberName: String = "",
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography

    HearthScaffold(
        modifier = modifier,
        header = {
            HearthTopBar(
                onBack = onBack,
                backDescription = stringResource(Res.string.conversation_back),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.agentAvatar.isNotEmpty()) {
                        Text(text = state.agentAvatar, style = type.glyphSm)
                    }
                    // No chevron: a conversation is bound to one agent, so there is nothing to open.
                    Text(text = state.agentName, style = type.heading, color = colors.textPrimary)
                }
            }
        },
        bottomBar = {
            Column {
                // Under the composer, where the problem is — and the message it refused is still
                // sitting in the field above, so it can simply be sent again.
                val failure = state.errorMessage
                if (failure != null) {
                    Text(
                        text = failure,
                        style = type.caption,
                        color = colors.error,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = HearthTheme.spacing.xl,
                                    end = HearthTheme.spacing.xl,
                                    bottom = HearthTheme.spacing.sm,
                                ),
                    )
                }
                MessageComposer(
                    value = state.composerText,
                    onValueChange = onComposerTextChange,
                    onSend = {
                        if (state.canSend && state.composerText.isNotBlank()) {
                            onSend(state.composerText)
                        }
                    },
                    placeholder =
                        when (state.turnState) {
                            TurnState.Reconnecting -> stringResource(Res.string.conversation_composer_waiting)
                            TurnState.StillWorking -> stringResource(Res.string.conversation_composer_leave)
                            else -> stringResource(Res.string.conversation_composer_idle, state.agentName)
                        },
                )
            }
        },
    ) { padding ->
        if (state.isNew) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(HearthTheme.spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md, Alignment.CenterVertically),
            ) {
                Text(text = state.agentAvatar, style = type.glyphXl)
                Text(
                    text = stringResource(Res.string.conversation_greeting, memberName),
                    style = type.hero,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = state.agentTagline,
                    style = type.body,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = HearthTheme.size.readingWidth),
                )
            }
            return@HearthScaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(HearthTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.lg),
        ) {
            items(state.messages, key = { it.id }) { message ->
                val undelivered =
                    message.role == MessageRole.USER &&
                        (message.status == MessageStatus.FAILED_OFFLINE || message.status == MessageStatus.FAILED_ERROR)

                MessageBubble(
                    content = message.content,
                    fromMe = message.role == MessageRole.USER,
                    status =
                        if (undelivered) {
                            {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TurnStatusLine(
                                        label = stringResource(Res.string.conversation_not_sent),
                                        detail = null,
                                        tone = TurnStatusTone.Wrong,
                                    )
                                    // Only this case re-sends: the question itself never landed.
                                    SecondaryButton(
                                        text = stringResource(Res.string.conversation_retry),
                                        onClick = { onRetry(message.content) },
                                    )
                                }
                            }
                        } else {
                            null
                        },
                )
            }

            // The answer being written, with whatever has become of it sitting where it stopped.
            if (state.streamingMessage != null || state.turnState == TurnState.Failed) {
                item {
                    MessageBubble(
                        content = state.streamingMessage.orEmpty(),
                        fromMe = false,
                        status = { TurnStatus(state, onTryAgain) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TurnStatus(
    state: ChatSessionUiState,
    onTryAgain: () -> Unit,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography

    when (state.turnState) {
        TurnState.Reconnecting -> {
            TurnStatusLine(
                label = stringResource(Res.string.conversation_reconnecting),
                detail = stringResource(Res.string.conversation_reconnecting_detail),
            )
        }

        TurnState.StillWorking -> {
            TurnStatusLine(
                label = stringResource(Res.string.conversation_still_working),
                detail = stringResource(Res.string.conversation_still_working_detail),
            )
        }

        TurnState.Failed -> {
            Column(verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm)) {
                Text(
                    text = stringResource(Res.string.conversation_failed_title),
                    style = type.bodyStrong,
                    color = colors.error,
                )
                Text(
                    text = stringResource(Res.string.conversation_failed_line),
                    style = type.caption,
                    color = colors.textMuted,
                )
                // Never re-asks: the question is on the hub, only the answer is missing.
                SecondaryButton(
                    text = stringResource(Res.string.conversation_try_again),
                    onClick = onTryAgain,
                )
            }
        }

        TurnState.Idle, TurnState.Streaming -> {
            Unit
        }
    }
}

@PreviewDayNight
@Composable
private fun ConversationContentPreview(
    @PreviewParameter(ConversationUiStateProvider::class) state: ChatSessionUiState,
) {
    HearthTheme {
        ConversationContent(
            state = state,
            onComposerTextChange = {},
            onSend = {},
            onRetry = {},
            onTryAgain = {},
            onBack = {},
            memberName = "Emma",
        )
    }
}
