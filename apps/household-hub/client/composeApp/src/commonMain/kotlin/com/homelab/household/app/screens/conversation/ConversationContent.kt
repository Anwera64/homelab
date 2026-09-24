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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.homelab.household.app.components.AnswerText
import com.homelab.household.app.components.HearthScaffold
import com.homelab.household.app.components.HearthTopBar
import com.homelab.household.app.components.MessageBubble
import com.homelab.household.app.components.MessageComposer
import com.homelab.household.app.components.NotSentReceipt
import com.homelab.household.app.components.SecondaryButton
import com.homelab.household.app.components.SentReceipt
import com.homelab.household.app.components.SlowLine
import com.homelab.household.app.components.StepsFold
import com.homelab.household.app.components.ThinkingDots
import com.homelab.household.app.components.ToolRecordLine
import com.homelab.household.app.components.ToolRunningChip
import com.homelab.household.app.components.TurnStatusLine
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.conversation_back
import com.homelab.household.app.resources.conversation_composer_idle
import com.homelab.household.app.resources.conversation_composer_leave
import com.homelab.household.app.resources.conversation_composer_waiting
import com.homelab.household.app.resources.conversation_failed_line
import com.homelab.household.app.resources.conversation_failed_title
import com.homelab.household.app.resources.conversation_greeting
import com.homelab.household.app.resources.conversation_reconnecting
import com.homelab.household.app.resources.conversation_reconnecting_detail
import com.homelab.household.app.resources.conversation_still_working
import com.homelab.household.app.resources.conversation_still_working_detail
import com.homelab.household.app.resources.conversation_thought
import com.homelab.household.app.resources.conversation_try_again
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.app.theme.LocalHearthMotion
import com.homelab.household.app.theme.PreviewDayNight
import com.homelab.household.app.theme.StillMotion
import com.homelab.household.app.util.WaitPhase
import com.homelab.household.app.util.rememberWaitPhase
import com.homelab.household.domain.model.AnswerPart
import com.homelab.household.domain.model.MessageRole
import com.homelab.household.domain.model.MessageStatus
import com.homelab.household.presentation.chatsession.ChatSessionUiState
import com.homelab.household.presentation.chatsession.TurnState
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.stringResource

const val CONVERSATION_TRANSCRIPT_TAG = "ConversationTranscript"

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
    onSelectAgent: (String) -> Unit = {},
    onRetryAgents: () -> Unit = {},
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    val transcript = rememberLazyListState()

    // Sent, and not a word back yet — the hub loading a model, or the model thinking before it
    // writes. It obeys the same four beats as every other wait (design notes §6.21), so a hub that
    // answers quickly draws none of it.
    val thinking = state.turnState == TurnState.Streaming && state.streamingMessage.isNullOrEmpty()
    val thinkingPhase = rememberWaitPhase(thinking)

    // "Slow" is timed on silence alone. A model visibly thinking or using a tool is busy, not
    // slow, and a cold load is the one wait where nothing at all comes back.
    val slowPhase = rememberWaitPhase(state.isSilent)

    val latestQuestionId = state.messages.lastOrNull { it.role == MessageRole.USER }?.id

    // How many messages the list was last laid out around, so that "were you at the bottom?" can
    // be asked about what was there before this change.
    var seenCount by remember { mutableIntStateOf(0) }

    // Counted from the state rather than read off the layout, because the first pass runs before
    // there is any layout to read and the answer is already on screen by then.
    val turnIsDrawn = state.streamingMessage != null || state.turnState == TurnState.Failed
    val itemCount = state.messages.size + if (turnIsDrawn) 1 else 0

    // An answer arrives faster than anyone reads it, and it arrives at the bottom: without this the
    // words land below the fold and the screen sits still while the agent talks.
    // Thinking, a tool and the steps grow the answer too, before a single word of it arrives.
    LaunchedEffect(
        itemCount,
        state.streamingMessage,
        state.turnState,
        state.isThinking,
        state.activeTool,
        state.parts,
    ) {
        if (itemCount == 0) return@LaunchedEffect

        // Were you at the end of what was already there? Asked against the count from before this
        // change, which is the whole point: the answer is the same whether or not the list has
        // been measured around the new words yet. Asking instead whether the list can still
        // scroll forward, as this used to, reads the layout after the answer is already in it,
        // so it always says yes: the screen followed nothing, and a chat you have been having
        // for weeks opened on its first message.
        //
        // Nothing visible yet means nothing has been scrolled away from, so it follows: that is
        // how a conversation opens on its newest message.
        val lastVisible =
            transcript.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index
        val atEnd = lastVisible == null || lastVisible >= seenCount - 1
        seenCount = itemCount

        // Scroll up to re-read something and the answer keeps arriving without dragging you back
        // down; return to the bottom and it picks the thread up again. Forcing it would make a
        // long answer impossible to read back while it is still being written.
        if (!atEnd) return@LaunchedEffect

        // Wait until the list has actually measured these items. Opening a conversation delivers
        // the whole transcript in one go, and a scroll asked for before there is any layout lands
        // against a height of nothing.
        snapshotFlow { transcript.layoutInfo.totalItemsCount }.first { it >= itemCount }

        // The last item is the answer itself and grows as it arrives, so this asks for its end
        // rather than its start; the offset is clamped to however tall it has become.
        transcript.scrollToItem(itemCount - 1, Int.MAX_VALUE)
    }

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
                            // It refuses a send while an answer is written, so it says so: keeping
                            // its idle words while refusing read as a composer that was broken.
                            TurnState.Streaming, TurnState.Reconnecting -> {
                                stringResource(Res.string.conversation_composer_waiting)
                            }

                            TurnState.StillWorking -> {
                                stringResource(Res.string.conversation_composer_leave)
                            }

                            else -> {
                                stringResource(Res.string.conversation_composer_idle, state.agentName)
                            }
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
                var picking by rememberSaveable { mutableStateOf(false) }
                AgentAvatarButton(
                    avatar = state.agentAvatar,
                    onChangeAgent = if (state.canChangeAgent) ({ picking = true }) else null,
                )
                if (picking && state.canChangeAgent) {
                    AgentPickerSheet(
                        agents = state.agents,
                        selectedAgentId = state.selectedAgentId,
                        failed = state.agentsFailed,
                        currentAgentName = state.agentName,
                        onSelect = { agentId ->
                            onSelectAgent(agentId)
                            picking = false
                        },
                        onRetry = onRetryAgents,
                        onDismiss = { picking = false },
                    )
                }
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
            state = transcript,
            modifier = Modifier.fillMaxSize().padding(padding).testTag(CONVERSATION_TRANSCRIPT_TAG),
            contentPadding = PaddingValues(HearthTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.lg),
        ) {
            items(state.messages, key = { it.id }) { message ->
                val undelivered =
                    message.role == MessageRole.USER &&
                        (message.status == MessageStatus.FAILED_OFFLINE || message.status == MessageStatus.FAILED_ERROR)
                // The receipt belongs to the newest question only: under every one it is noise.
                val receipted = message.id == latestQuestionId && message.status == MessageStatus.SENT

                if (message.role == MessageRole.ASSISTANT) {
                    // Finished, so its steps fold away and it reads as the answer.
                    Answer(parts = message.parts, written = message.content, folded = true)
                } else {
                    MessageBubble(
                        content = message.content,
                        fromMe = message.role == MessageRole.USER,
                        status =
                            if (receipted) {
                                { SentReceipt() }
                            } else if (undelivered) {
                                // Only this case re-sends: the question itself never landed.
                                { NotSentReceipt(onRetry = { onRetry(message.content) }) }
                            } else {
                                null
                            },
                    )
                }
            }

            // The answer being written, with whatever has become of it sitting where it stopped.
            if (state.streamingMessage != null || state.turnState == TurnState.Failed) {
                item {
                    // Nothing folded while it is being written: each step shows as it happens.
                    Answer(parts = state.parts, written = state.streamingMessage.orEmpty(), folded = false) {
                        val tool = state.activeTool
                        when {
                            // In the answer's place, because that is where the eye is waiting.
                            tool != null -> ToolRunningChip(stringResource(toolLabel(tool).running))

                            thinking -> Thinking(thinkingPhase, slowPhase)

                            else -> TurnStatus(state, onTryAgain)
                        }
                    }
                }
            }
        }
    }
}

/**
 * A model working before its first word: "Thinking…", and the slow line only if it has said
 * nothing at all for eight seconds. Never its thoughts — they stream faster than anyone reads.
 */
@Composable
private fun Thinking(
    phase: WaitPhase,
    slowPhase: WaitPhase,
) {
    Column(verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm)) {
        if (phase != WaitPhase.Hidden) ThinkingDots()
        SlowLine(slowPhase)
    }
}

/**
 * An answer, drawn in the order it happened: its words, and between them the steps it took on
 * the way — each stretch of thinking and each tool, one quiet line each (design notes §6.5).
 *
 * The steps used to be drawn above the whole answer, so a tool used halfway through looked as if
 * it came first and the words either side of it ran together (#33). A [folded] answer — a finished
 * one — keeps each run of steps to a single "N steps" line where it happened.
 *
 * [written] is the answer as plain text, drawn as it always was when there are no parts to draw
 * it from: an answer saved before the hub kept them, or one whose words have not started yet.
 * [status] sits where the answer stopped.
 */
@Composable
private fun Answer(
    parts: List<AnswerPart>,
    written: String,
    folded: Boolean,
    status: (@Composable () -> Unit)? = null,
) {
    val runs = parts.runs(written)
    Column(verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm)) {
        runs.forEachIndexed { index, run ->
            // Keyed by place, so each fold remembers its own open or closed.
            key(index) {
                when (run) {
                    is AnswerRun.Words -> {
                        AnswerText(content = run.text)
                    }

                    is AnswerRun.Steps -> {
                        if (folded) {
                            StepsFold(count = run.steps.size) { Steps(run.steps) }
                        } else {
                            Steps(run.steps)
                        }
                    }
                }
            }
        }
        status?.invoke()
    }
}

/** A stretch of an answer: its words, or the steps it took between them. */
private sealed interface AnswerRun {
    data class Words(
        val text: String,
    ) : AnswerRun

    data class Steps(
        val steps: List<AnswerPart>,
    ) : AnswerRun
}

/**
 * The parts grouped into stretches. With no words among them, [written] stands in for the words:
 * a message saved before parts were kept, or a live answer whose parts are all steps so far.
 */
private fun List<AnswerPart>.runs(written: String): List<AnswerRun> {
    val runs = mutableListOf<AnswerRun>()
    var steps = mutableListOf<AnswerPart>()
    for (part in this) {
        if (part is AnswerPart.Text) {
            if (steps.isNotEmpty()) runs += AnswerRun.Steps(steps)
            steps = mutableListOf()
            if (part.content.isNotBlank()) runs += AnswerRun.Words(part.content.trim())
        } else {
            steps += part
        }
    }
    if (steps.isNotEmpty()) runs += AnswerRun.Steps(steps)
    if (runs.none { it is AnswerRun.Words } && written.isNotBlank()) runs += AnswerRun.Words(written)
    return runs
}

/** Each step on a line of its own, in the order it ran. */
@Composable
private fun Steps(steps: List<AnswerPart>) {
    Column(verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xs)) {
        steps.forEach { step ->
            when (step) {
                is AnswerPart.Thought -> {
                    ToolRecordLine(
                        icon = HearthIcon.Streaming,
                        text = stringResource(Res.string.conversation_thought, step.seconds),
                    )
                }

                is AnswerPart.ToolDone -> {
                    val label = toolLabel(step.tool)
                    ToolRecordLine(icon = label.icon, text = stringResource(label.done))
                }

                is AnswerPart.ToolFailed -> {
                    ToolRecordLine(
                        icon = HearthIcon.Error,
                        text = stringResource(toolLabel(step.tool).failed),
                        tint = HearthTheme.colors.error,
                    )
                }

                is AnswerPart.Text -> {}
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

/**
 * Motion off, because a preview draws one frame: with it on, every wait is still inside its hold
 * and the thinking states draw an empty bubble. `ThinkingDotsPreview` shows the dots moving.
 */
@PreviewDayNight
@Composable
private fun ConversationContentPreview(
    @PreviewParameter(ConversationUiStateProvider::class) state: ChatSessionUiState,
) {
    HearthTheme {
        CompositionLocalProvider(LocalHearthMotion provides StillMotion) {
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
}
