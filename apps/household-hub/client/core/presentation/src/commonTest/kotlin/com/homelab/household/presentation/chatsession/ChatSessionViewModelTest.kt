package com.homelab.household.presentation.chatsession

import app.cash.turbine.test
import com.homelab.household.domain.exception.ApprovalPendingException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.AgentPersonality
import com.homelab.household.domain.model.AnswerPart
import com.homelab.household.domain.model.ChatMessage
import com.homelab.household.domain.model.ChatStreamEvent
import com.homelab.household.domain.model.ConversationSession
import com.homelab.household.domain.model.EventMoment
import com.homelab.household.domain.model.EventScope
import com.homelab.household.domain.model.HubTool
import com.homelab.household.domain.model.LookedOverLine
import com.homelab.household.domain.model.MessageRole
import com.homelab.household.domain.model.MessageStatus
import com.homelab.household.domain.model.ProposalDetails
import com.homelab.household.domain.model.ProposalStatus
import com.homelab.household.domain.model.ToolAction
import com.homelab.household.domain.model.ToolApproval
import com.homelab.household.domain.model.ToolFailureReason
import com.homelab.household.domain.model.ToolSource
import com.homelab.household.domain.model.ToolSummary
import com.homelab.household.domain.model.User
import com.homelab.household.domain.usecase.CreateSessionUseCase
import com.homelab.household.domain.usecase.DecideToolProposalUseCase
import com.homelab.household.domain.usecase.GetAgentUseCase
import com.homelab.household.domain.usecase.GetCurrentUserUseCase
import com.homelab.household.domain.usecase.GetSessionUseCase
import com.homelab.household.domain.usecase.ListAgentsUseCase
import com.homelab.household.domain.usecase.ListHouseholdMembersUseCase
import com.homelab.household.domain.usecase.RegenerateAnswerUseCase
import com.homelab.household.domain.usecase.ResumeTurnUseCase
import com.homelab.household.domain.usecase.SetToolApprovalUseCase
import com.homelab.household.domain.usecase.StreamChatTurnUseCase
import com.homelab.household.domain.usecase.ToggleSecretModeUseCase
import com.homelab.household.presentation.memories.LocalTime
import com.homelab.household.presentation.memories.MemoryAge
import com.homelab.household.presentation.whenagentsact.WhenAgentsActViewModel
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TestTimeSource
import kotlin.time.TimeSource

@OptIn(ExperimentalCoroutinesApi::class)
class ChatSessionViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private val streamChatTurnUseCase = mock<StreamChatTurnUseCase>()
    private val getSessionUseCase = mock<GetSessionUseCase>()
    private val regenerateAnswerUseCase = mock<RegenerateAnswerUseCase>()
    private val resumeTurnUseCase = mock<ResumeTurnUseCase>()
    private val listAgentsUseCase = mock<ListAgentsUseCase>(MockMode.autofill)
    private val createSessionUseCase = mock<CreateSessionUseCase>(MockMode.autofill)
    private val decideToolProposalUseCase = mock<DecideToolProposalUseCase>()
    private val toggleSecretModeUseCase = mock<ToggleSecretModeUseCase>()
    private val getAgentUseCase = mock<GetAgentUseCase>(MockMode.autofill)
    private val getCurrentUserUseCase = mock<GetCurrentUserUseCase>(MockMode.autofill)
    private val listHouseholdMembersUseCase = mock<ListHouseholdMembersUseCase>(MockMode.autofill)
    private val setToolApprovalUseCase = mock<SetToolApprovalUseCase>(MockMode.autofill)

    private lateinit var viewModel: ChatSessionViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel =
            ChatSessionViewModel(
                streamChatTurnUseCase = streamChatTurnUseCase,
                getSessionUseCase = getSessionUseCase,
                listAgentsUseCase = listAgentsUseCase,
                createSessionUseCase = createSessionUseCase,
                regenerateAnswerUseCase = regenerateAnswerUseCase,
                resumeTurnUseCase = resumeTurnUseCase,
                decideToolProposalUseCase = decideToolProposalUseCase,
                toggleSecretModeUseCase = toggleSecretModeUseCase,
                getAgentUseCase = getAgentUseCase,
                getCurrentUserUseCase = getCurrentUserUseCase,
                listHouseholdMembersUseCase = listHouseholdMembersUseCase,
                setToolApprovalUseCase = setToolApprovalUseCase,
                deviceTimeZone = { ONE_HOUR },
            )
    }

    /** The same wiring, with clocks the test sets by hand. */
    private fun viewModelWith(
        timeSource: TimeSource = TimeSource.Monotonic,
        clock: Clock = Clock.System,
    ) = ChatSessionViewModel(
        streamChatTurnUseCase = streamChatTurnUseCase,
        getSessionUseCase = getSessionUseCase,
        listAgentsUseCase = listAgentsUseCase,
        createSessionUseCase = createSessionUseCase,
        regenerateAnswerUseCase = regenerateAnswerUseCase,
        resumeTurnUseCase = resumeTurnUseCase,
        decideToolProposalUseCase = decideToolProposalUseCase,
        toggleSecretModeUseCase = toggleSecretModeUseCase,
        getAgentUseCase = getAgentUseCase,
        getCurrentUserUseCase = getCurrentUserUseCase,
        listHouseholdMembersUseCase = listHouseholdMembersUseCase,
        setToolApprovalUseCase = setToolApprovalUseCase,
        deviceTimeZone = { ONE_HOUR },
        timeSource = timeSource,
        clock = clock,
    )

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `GIVEN a session with two messages WHEN it is loaded THEN the session and both messages show without an error`() =
        runTest(testDispatcher) {
            val session = ConversationSession(id = "s-1", userId = "u-1", title = "Chat")
            val messages =
                listOf(
                    ChatMessage(id = "m-1", sessionId = "s-1", role = MessageRole.USER, content = "Hi"),
                    ChatMessage(id = "m-2", sessionId = "s-1", role = MessageRole.ASSISTANT, content = "Hello!"),
                )
            everySuspend { getSessionUseCase("s-1") } returns Pair(session, messages)

            viewModel.loadSession("s-1")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("s-1", state.session?.id)
            assertEquals(2, state.messages.size)
            assertEquals(false, state.isLoading)
            assertNull(state.errorMessage)
        }

    @Test
    fun `GIVEN an opened chat WHEN it is first shown THEN nothing more is fetched`() =
        runTest(testDispatcher) {
            val session = ConversationSession(id = "s-1", userId = "u-1")
            everySuspend { getSessionUseCase("s-1") } returns Pair(session, emptyList())
            viewModel.open("s-1")
            viewModel.onShown()
            advanceUntilIdle()

            verifySuspend(VerifyMode.exactly(1)) { getSessionUseCase("s-1") }
        }

    @Test
    fun `GIVEN a looked-over chat WHEN it is shown again after answering elsewhere THEN its line is fetched again`() =
        runTest(testDispatcher) {
            val line =
                LookedOverLine(
                    throughMessageId = "m-1",
                    at = kotlin.time.Instant.parse("2026-10-16T10:52:00Z"),
                    remembered = 2,
                    toCheck = 1,
                )
            val messages = listOf(ChatMessage(id = "m-1", sessionId = "s-1", role = MessageRole.USER, content = "Hi"))
            val session = ConversationSession(id = "s-1", userId = "u-1", lookedOver = line)
            everySuspend { getSessionUseCase("s-1") } returns Pair(session, messages)
            viewModel.open("s-1")
            viewModel.onShown()
            advanceUntilIdle()

            val answered = line.copy(toCheck = 0)
            everySuspend { getSessionUseCase("s-1") } returns Pair(session.copy(lookedOver = answered), messages)
            viewModel.onShown()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(answered, state.session?.lookedOver)
            assertEquals(false, state.isLoading)
            assertEquals(messages, state.messages)
        }

    @Test
    fun `GIVEN a chat that was looked over WHEN its sheet closes THEN only the line is fetched again`() =
        runTest(testDispatcher) {
            val line =
                LookedOverLine(
                    throughMessageId = "m-1",
                    at = kotlin.time.Instant.parse("2026-10-16T10:52:00Z"),
                    remembered = 2,
                    toCheck = 1,
                )
            val messages = listOf(ChatMessage(id = "m-1", sessionId = "s-1", role = MessageRole.USER, content = "Hi"))
            val session = ConversationSession(id = "s-1", userId = "u-1", lookedOver = line)
            everySuspend { getSessionUseCase("s-1") } returns Pair(session, messages)
            viewModel.loadSession("s-1")
            advanceUntilIdle()
            assertEquals(line, viewModel.uiState.value.session?.lookedOver)
            assertEquals(LocalTime(hour = 11, minute = 52), viewModel.uiState.value.lookedOverAt)

            val answered = line.copy(
                remembered = 3,
                toCheck = 0,
                at = kotlin.time.Instant.parse("2026-10-16T12:05:00Z"),
            )
            everySuspend { getSessionUseCase("s-1") } returns Pair(session.copy(lookedOver = answered), messages)
            viewModel.refreshLookedOver()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(answered, state.session?.lookedOver)
            assertEquals(LocalTime(hour = 13, minute = 5), state.lookedOverAt)
            assertEquals(false, state.isLoading)
            assertEquals(1, state.messages.size)
        }

    @Test
    fun `GIVEN a loaded chat WHEN a message is sent and the answer streams in THEN the streaming message clears and the question and the answer are listed`() =
        runTest(testDispatcher) {
            val session = ConversationSession(id = "s-1", userId = "u-1")
            everySuspend { getSessionUseCase("s-1") } returns Pair(session, emptyList())
            viewModel.loadSession("s-1")
            advanceUntilIdle()

            every { streamChatTurnUseCase("s-1", "How are you?", false, any()) } returns
                flow {
                    emit(ChatStreamEvent.Delta("I'm "))
                    emit(ChatStreamEvent.Delta("doing great!"))
                    emit(
                        ChatStreamEvent.Done(
                            messageId = "m-done",
                            assistantContent = "I'm doing great!",
                            agentName = "Assistant",
                        ),
                    )
                }

            viewModel.sendMessage("How are you?")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNull(state.streamingMessage)
            assertEquals(2, state.messages.size) // User message + Assistant completed message
            assertEquals("How are you?", state.messages[0].content)
            assertEquals("I'm doing great!", state.messages[1].content)
        }

    // ---- approval cards (slice 4, PR 3) -----------------------------------

    private val dinner =
        AnswerPart.Proposal(
            toolCallId = "c1",
            tool = "calendar_write",
            action = ToolAction.Create,
            details =
                ProposalDetails.CalendarEvent(
                    title = "Dinner together",
                    start = EventMoment(2026, 10, 3, hour = 20, minute = 30),
                    end = null,
                    allDay = false,
                ),
        )
    private val flowers =
        dinner.copy(
            toolCallId = "c2",
            details = ProposalDetails.CalendarEvent("Buy flowers", null, null, allDay = false),
        )
    private val question =
        ChatMessage(id = "m-1", sessionId = "s-1", role = MessageRole.USER, content = "Dinner Saturday?")

    private fun pausedAnswer(vararg cards: AnswerPart.Proposal) =
        ChatMessage(
            id = "m-2",
            sessionId = "s-1",
            role = MessageRole.ASSISTANT,
            content = "I can add it now.",
            parts = listOf(AnswerPart.Text("I can add it now.")) + cards,
        )

    /** A chat whose newest answer is paused on [cards], opened the way the hub reports it. */
    private fun TestScope.openPaused(vararg cards: AnswerPart.Proposal) {
        val session = ConversationSession(id = "s-1", userId = "u-1", awaitingApproval = true)
        everySuspend { getSessionUseCase("s-1") } returns Pair(session, listOf(question, pausedAnswer(*cards)))
        viewModel.loadSession("s-1")
        advanceUntilIdle()
    }

    @Test
    fun `GIVEN a turn that asks before a write WHEN it pauses THEN the answer stays with its card and the turn waits`() =
        runTest(testDispatcher) {
            everySuspend { getSessionUseCase("s-1") } returns
                Pair(ConversationSession(id = "s-1", userId = "u-1"), emptyList())
            viewModel.loadSession("s-1")
            advanceUntilIdle()
            every { streamChatTurnUseCase("s-1", "Dinner Saturday?", false, any()) } returns
                flowOf(
                    ChatStreamEvent.Accepted,
                    ChatStreamEvent.Delta("I can add it now."),
                    ChatStreamEvent.ToolApprovalProposal("c1", "calendar_write", ToolAction.Create, dinner.details),
                    ChatStreamEvent.AwaitingApproval(
                        messageId = "m-2",
                        assistantContent = "I can add it now.",
                        parts = pausedAnswer(dinner).parts,
                    ),
                )

            viewModel.sendMessage("Dinner Saturday?")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(TurnState.AwaitingApproval, state.turnState)
            assertNull(state.streamingMessage)
            assertEquals(pausedAnswer(dinner).parts, state.messages.last().parts)
            assertEquals(listOf(dinner), state.pendingProposals)
        }

    @Test
    fun `GIVEN a chat opened while a card waits WHEN it loads THEN the turn waits on the card`() =
        runTest(testDispatcher) {
            openPaused(dinner)

            val state = viewModel.uiState.value
            assertEquals(TurnState.AwaitingApproval, state.turnState)
            assertEquals(listOf(dinner), state.pendingProposals)
        }

    @Test
    fun `GIVEN a waiting card WHEN Send is tapped THEN nothing is sent and the text stays and the hold line shows`() =
        runTest(testDispatcher) {
            openPaused(dinner)
            viewModel.composerTextChanged("Also lunch?")

            viewModel.sendMessage("Also lunch?")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            verify(VerifyMode.not) { streamChatTurnUseCase(any(), any(), any(), any()) }
            assertEquals("Also lunch?", state.composerText)
            assertTrue(state.holdingForCard)
            assertEquals(2, state.messages.size)
        }

    @Test
    fun `GIVEN a waiting card WHEN it is approved THEN the same answer carries on to its end`() =
        runTest(testDispatcher) {
            openPaused(dinner)
            viewModel.sendMessage("Also lunch?")
            val done =
                listOf(
                    AnswerPart.Text("I can add it now."),
                    AnswerPart.ToolDone(
                        "calendar_write",
                        ToolSummary(action = ToolAction.Create, title = "Dinner together"),
                    ),
                    AnswerPart.Text("Done."),
                )
            every { decideToolProposalUseCase("s-1", "c1", true, null) } returns
                flowOf(
                    ChatStreamEvent.Accepted,
                    ChatStreamEvent.ToolExecuting("calendar_write", action = ToolAction.Create),
                    ChatStreamEvent.ToolResult(
                        "calendar_write",
                        success = true,
                        summary =
                            done[1].let {
                                (it as AnswerPart.ToolDone).summary
                            },
                    ),
                    ChatStreamEvent.Delta("Done."),
                    ChatStreamEvent.Done(
                        messageId = "m-2",
                        assistantContent = "I can add it now.\n\nDone.",
                        parts = done,
                    ),
                )

            viewModel.decide("c1", approved = true)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(TurnState.Idle, state.turnState)
            assertFalse(state.holdingForCard)
            assertEquals(listOf("m-1", "m-2"), state.messages.map { it.id })
            assertEquals(done, state.messages.last().parts)
            assertTrue(state.pendingProposals.isEmpty())
        }

    @Test
    fun `GIVEN a card being answered WHEN the write runs THEN it takes the card's place in the answer`() =
        runTest(testDispatcher) {
            openPaused(dinner)
            every { decideToolProposalUseCase("s-1", "c1", false, null) } returns
                flow {
                    emit(ChatStreamEvent.Accepted)
                    emit(
                        ChatStreamEvent.ToolDeclined(
                            "calendar_write",
                            ToolSummary(action = ToolAction.Create, title = "Dinner together"),
                        ),
                    )
                    awaitCancellation()
                }

            viewModel.decide("c1", approved = false)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(TurnState.Streaming, state.turnState)
            assertEquals(listOf("m-1"), state.messages.map { it.id })
            assertEquals(
                listOf(
                    AnswerPart.Text("I can add it now."),
                    AnswerPart.Declined(
                        "calendar_write",
                        ToolSummary(action = ToolAction.Create, title = "Dinner together"),
                    ),
                ),
                state.parts,
            )
        }

    @Test
    fun `GIVEN a card approved with changes WHEN it is sent THEN the edit reaches the hub and the card shows it`() =
        runTest(testDispatcher) {
            openPaused(dinner, flowers)
            val later = EventMoment(2026, 10, 3, hour = 21, minute = 0)
            val edit =
                ProposalDetails.CalendarEvent(
                    title = "Dinner at Nonna's",
                    start = later,
                    end = null,
                    allDay = false,
                )
            every { decideToolProposalUseCase("s-1", "c1", true, edit) } returns
                flow {
                    emit(ChatStreamEvent.Accepted)
                    awaitCancellation()
                }

            viewModel.decide("c1", approved = true, edited = edit)
            advanceUntilIdle()

            verify { decideToolProposalUseCase("s-1", "c1", true, edit) }
            val card =
                viewModel.uiState.value.parts
                    .filterIsInstance<AnswerPart.Proposal>()
                    .first()
            assertEquals(ProposalStatus.Approved, card.status)
            assertEquals(
                ProposalDetails.CalendarEvent(title = "Dinner at Nonna's", start = later, end = null, allDay = false),
                card.details,
            )
        }

    @Test
    fun `GIVEN two cards WHEN the first is answered THEN the turn still waits on the second`() =
        runTest(testDispatcher) {
            openPaused(dinner, flowers)
            val afterFirst = pausedAnswer(dinner.copy(status = ProposalStatus.Approved), flowers)
            every { decideToolProposalUseCase("s-1", "c1", true, null) } returns
                flowOf(
                    ChatStreamEvent.Accepted,
                    ChatStreamEvent.AwaitingApproval(
                        messageId = "m-2",
                        assistantContent = afterFirst.content,
                        parts = afterFirst.parts,
                    ),
                )

            viewModel.decide("c1", approved = true)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(TurnState.AwaitingApproval, state.turnState)
            assertEquals(listOf(flowers), state.pendingProposals)
            assertEquals(listOf("m-1", "m-2"), state.messages.map { it.id })
        }

    @Test
    fun `GIVEN the hub holding a card this phone has not seen WHEN a message is sent THEN it is held and not failed`() =
        runTest(testDispatcher) {
            everySuspend { getSessionUseCase("s-1") } returns
                Pair(ConversationSession(id = "s-1", userId = "u-1"), listOf(question))
            viewModel.loadSession("s-1")
            advanceUntilIdle()
            every { streamChatTurnUseCase("s-1", "Also lunch?", false, any()) } returns
                flow { throw ApprovalPendingException() }
            // Read again, the chat shows the card another phone left waiting.
            everySuspend { getSessionUseCase("s-1") } returns
                Pair(
                    ConversationSession(id = "s-1", userId = "u-1", awaitingApproval = true),
                    listOf(question, pausedAnswer(dinner)),
                )

            viewModel.sendMessage("Also lunch?")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("Also lunch?", state.composerText)
            assertTrue(state.holdingForCard)
            assertNull(state.errorMessage)
            assertEquals(TurnState.AwaitingApproval, state.turnState)
            assertEquals(listOf("m-1", "m-2"), state.messages.map { it.id })
        }

    // ---- auto-approve from the card (slice 4, PR 5) -------------------------

    private val removal =
        dinner.copy(
            toolCallId = "c3",
            action = ToolAction.Delete,
            details = ProposalDetails.CalendarEvent(title = "Print shop", start = null, end = null, allDay = false),
        )

    /** The rest of an approved turn: the write runs, and the answer ends. */
    private fun approvedTurn(toolCallId: String) {
        every { decideToolProposalUseCase("s-1", toolCallId, true, null) } returns
            flowOf(
                ChatStreamEvent.Accepted,
                ChatStreamEvent.Done(messageId = "m-2", assistantContent = "Done."),
            )
    }

    /** The hub saves the setting. Stubbed, not autofilled: Kotlin/Native can't return a null list. */
    private fun settingsSave() {
        everySuspend { setToolApprovalUseCase(any(), any(), any()) } returns emptyList()
    }

    @Test
    fun `GIVEN the box ticked WHEN the card is approved THEN adding events is made automatic before the turn carries on`() =
        runTest(testDispatcher) {
            openPaused(dinner)
            approvedTurn("c1")
            settingsSave()

            viewModel.approveAutomatically("c1")
            advanceUntilIdle()

            verifySuspend { setToolApprovalUseCase("calendar_write", ToolAction.Create, true) }
            verify { decideToolProposalUseCase("s-1", "c1", true, null) }
            val state = viewModel.uiState.value
            assertEquals(AutomaticWrite("calendar_write", ToolAction.Create), state.madeAutomatic)
            assertEquals(TurnState.Idle, state.turnState)
        }

    /** The hub's settings, kept the way the hub keeps them: one list, whoever changes it. */
    private class HubSettings {
        var approvals =
            listOf(
                ToolApproval(HubTool.CALENDAR_WRITE, ToolAction.Create, automatic = false),
                ToolApproval(HubTool.CALENDAR_WRITE, ToolAction.Update, automatic = false),
                ToolApproval(HubTool.CALENDAR_WRITE, ToolAction.Delete, automatic = false, alwaysAsks = true),
            )

        fun set(
            tool: String,
            action: ToolAction,
            automatic: Boolean,
        ): List<ToolApproval> {
            approvals =
                approvals.map { if (it.tool == tool && it.action == action) it.copy(automatic = automatic) else it }
            return approvals
        }
    }

    @Test
    fun `GIVEN the card's box ticked WHEN the card is approved THEN the When agents act switch for adding events shows on`() =
        runTest(testDispatcher) {
            val hub = HubSettings()
            everySuspend { setToolApprovalUseCase(any(), any(), any()) } calls
                { (tool: String, action: ToolAction, auto: Boolean) ->
                    hub.set(tool, action, auto)
                }
            openPaused(dinner)
            approvedTurn("c1")

            viewModel.approveAutomatically("c1")
            advanceUntilIdle()

            val settings = WhenAgentsActViewModel({ hub.approvals }, setToolApprovalUseCase)
            advanceUntilIdle()
            val addEvents = {
                settings.uiState.value.groups
                    .first()
                    .approvals
                    .first { it.action == ToolAction.Create }
            }
            assertTrue(addEvents().automatic)
        }

    @Test
    fun `GIVEN adding events made automatic from the card WHEN the When agents act switch is turned off THEN both show the same setting`() =
        runTest(testDispatcher) {
            val hub = HubSettings()
            everySuspend { setToolApprovalUseCase(any(), any(), any()) } calls
                { (tool: String, action: ToolAction, auto: Boolean) ->
                    hub.set(tool, action, auto)
                }
            openPaused(dinner)
            approvedTurn("c1")
            viewModel.approveAutomatically("c1")
            advanceUntilIdle()
            val settings = WhenAgentsActViewModel({ hub.approvals }, setToolApprovalUseCase)
            advanceUntilIdle()
            val addEvents = {
                settings.uiState.value.groups
                    .first()
                    .approvals
                    .first { it.action == ToolAction.Create }
            }

            settings.onToggle(HubTool.CALENDAR_WRITE, ToolAction.Create, automatic = false)
            advanceUntilIdle()

            assertFalse(addEvents().automatic)
            assertFalse(hub.approvals.first { it.action == ToolAction.Create }.automatic)
        }

    @Test
    fun `GIVEN a write just made automatic WHEN Undo is tapped THEN it asks again and the line goes`() =
        runTest(testDispatcher) {
            openPaused(dinner)
            approvedTurn("c1")
            settingsSave()
            viewModel.approveAutomatically("c1")
            advanceUntilIdle()

            viewModel.undoAutomatic()
            advanceUntilIdle()

            verifySuspend { setToolApprovalUseCase("calendar_write", ToolAction.Create, false) }
            assertNull(viewModel.uiState.value.madeAutomatic)
        }

    @Test
    fun `GIVEN the setting can't be saved WHEN the ticked card is approved THEN the write is still approved`() =
        runTest(testDispatcher) {
            openPaused(dinner)
            approvedTurn("c1")
            everySuspend { setToolApprovalUseCase(any(), any(), any()) } throws ServerOfflineException("offline")

            viewModel.approveAutomatically("c1")
            advanceUntilIdle()

            verify { decideToolProposalUseCase("s-1", "c1", true, null) }
            assertNull(viewModel.uiState.value.madeAutomatic)
        }

    @Test
    fun `GIVEN a removal WHEN it is approved automatically THEN nothing is made automatic`() =
        runTest(testDispatcher) {
            openPaused(removal)
            approvedTurn("c3")

            viewModel.approveAutomatically("c3")
            advanceUntilIdle()

            verifySuspend(VerifyMode.not) { setToolApprovalUseCase(any(), any(), any()) }
            verify { decideToolProposalUseCase("s-1", "c3", true, null) }
            assertNull(viewModel.uiState.value.madeAutomatic)
        }

    @Test
    fun `GIVEN a write made automatic WHEN the next message is sent THEN its Undo line goes with the answer`() =
        runTest(testDispatcher) {
            openPaused(dinner)
            approvedTurn("c1")
            settingsSave()
            viewModel.approveAutomatically("c1")
            advanceUntilIdle()
            assertNotNull(viewModel.uiState.value.madeAutomatic)
            every { streamChatTurnUseCase("s-1", "Thanks", false, any()) } returns flow { awaitCancellation() }

            viewModel.sendMessage("Thanks")
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.madeAutomatic)
        }

    @Test
    fun `GIVEN an add made automatic WHEN it runs without a card THEN its step says it was automatic`() =
        runTest(testDispatcher) {
            everySuspend { getSessionUseCase("s-1") } returns
                Pair(ConversationSession(id = "s-1", userId = "u-1"), emptyList())
            viewModel.loadSession("s-1")
            advanceUntilIdle()
            val added = ToolSummary(action = ToolAction.Create, title = "Print shop cutoff")
            every { streamChatTurnUseCase("s-1", "Add the cutoff", false, any()) } returns
                flow {
                    emit(ChatStreamEvent.Accepted)
                    emit(
                        ChatStreamEvent.ToolResult("calendar_write", success = true, summary = added, automatic = true),
                    )
                    awaitCancellation()
                }

            viewModel.sendMessage("Add the cutoff")
            advanceUntilIdle()

            assertEquals(
                listOf(AnswerPart.ToolDone("calendar_write", added, automatic = true)),
                viewModel.uiState.value.parts,
            )
        }

    @Test
    fun `GIVEN an offline hub WHEN a session is loaded THEN the error message shows and loading stops`() =
        runTest(testDispatcher) {
            everySuspend { getSessionUseCase("s-offline") } throws ServerOfflineException("Server offline")

            viewModel.loadSession("s-offline")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("Server offline", state.errorMessage)
            assertEquals(false, state.isLoading)
        }

    @Test
    fun `GIVEN a first message already answered WHEN a second message is sent THEN each question has its own id and is marked sent`() =
        runTest(testDispatcher) {
            val session = ConversationSession(id = "s-1", userId = "u-1")
            everySuspend { getSessionUseCase("s-1") } returns Pair(session, emptyList())
            viewModel.loadSession("s-1")
            advanceUntilIdle()

            every { streamChatTurnUseCase("s-1", any(), false, any()) } returns
                flow {
                    emit(
                        ChatStreamEvent.Done(
                            messageId = "m-done",
                            assistantContent = "Hi back",
                            agentName = "Assistant",
                        ),
                    )
                }

            // Each turn is let finish: the composer will not take a second message while one
            // is being answered, which is the point of `canSend`.
            viewModel.sendMessage("First message")
            advanceUntilIdle()
            viewModel.sendMessage("Second message")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            val userMessages = state.messages.filter { it.role == MessageRole.USER }
            assertEquals(2, userMessages.size)
            assertTrue(userMessages[0].id != userMessages[1].id, "Message IDs must be unique")
            assertEquals(MessageStatus.SENT, userMessages[0].status)
            assertEquals(MessageStatus.SENT, userMessages[1].status)
        }

    /**
     * The ids only have to be unique, and "unique" cannot mean "the clock happened to tick between
     * two sends". A clock-derived id collides on a platform whose monotonic clock is coarser than
     * the gap between two sends, and fifty in quick succession is how that shows.
     *
     * Each one waits for the last to be answered, because the composer does not take a message
     * while a turn is live — but the turns still land back to back, which is the timing that
     * matters here.
     */
    @Test
    fun `GIVEN a loaded chat WHEN fifty messages are sent one after another THEN every message has its own id`() =
        runTest(testDispatcher) {
            val session = ConversationSession(id = "s-1", userId = "u-1")
            everySuspend { getSessionUseCase("s-1") } returns Pair(session, emptyList())
            viewModel.loadSession("s-1")
            advanceUntilIdle()

            every { streamChatTurnUseCase("s-1", any(), false, any()) } returns
                flow {
                    emit(
                        ChatStreamEvent.Done(
                            messageId = "m-done",
                            assistantContent = "Hi back",
                            agentName = "Assistant",
                        ),
                    )
                }

            val sends = 50
            repeat(sends) {
                viewModel.sendMessage("Message $it")
                advanceUntilIdle()
            }

            val ids =
                viewModel.uiState.value.messages
                    .filter { it.role == MessageRole.USER }
                    .map { it.id }
            assertEquals(sends, ids.size)
            assertEquals(
                sends,
                ids.toSet().size,
                "every send needs its own id; got ${sends - ids.toSet().size} collisions",
            )
        }

    @Test
    fun `GIVEN an offline hub WHEN a message is sent THEN the question is marked failed offline and the error message shows`() =
        runTest(testDispatcher) {
            val session = ConversationSession(id = "s-1", userId = "u-1")
            everySuspend { getSessionUseCase("s-1") } returns Pair(session, emptyList())
            viewModel.loadSession("s-1")
            advanceUntilIdle()

            every { streamChatTurnUseCase("s-1", "Offline test", false, any()) } returns
                flow {
                    throw ServerOfflineException("Network unavailable")
                }

            viewModel.sendMessage("Offline test")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            val userMessage = state.messages.firstOrNull { it.role == MessageRole.USER }
            assertNotNull(userMessage)
            assertEquals(MessageStatus.FAILED_OFFLINE, userMessage?.status)
            assertEquals("Network unavailable", state.errorMessage)
        }

    @Test
    fun `GIVEN a hub that fails unexpectedly WHEN a message is sent THEN the question is marked failed with an error and the error message shows`() =
        runTest(testDispatcher) {
            val session = ConversationSession(id = "s-1", userId = "u-1")
            everySuspend { getSessionUseCase("s-1") } returns Pair(session, emptyList())
            viewModel.loadSession("s-1")
            advanceUntilIdle()

            every { streamChatTurnUseCase("s-1", "General test", false, any()) } returns
                flow {
                    throw RuntimeException("Internal error")
                }

            viewModel.sendMessage("General test")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            val userMessage = state.messages.firstOrNull { it.role == MessageRole.USER }
            assertNotNull(userMessage)
            assertEquals(MessageStatus.FAILED_ERROR, userMessage?.status)
            assertEquals("Internal error", state.errorMessage)
        }

    // ---- which end the turn reached ----------------------------------------

    private fun TestScope.loadedSession(): ConversationSession {
        val session = ConversationSession(id = "s-1", userId = "u-1")
        everySuspend { getSessionUseCase("s-1") } returns Pair(session, emptyList())
        viewModel.loadSession("s-1")
        advanceUntilIdle()
        return session
    }

    @Test
    fun `GIVEN a hub that fails before the first word WHEN a message is sent THEN the question is blamed and the composer is freed`() =
        runTest(testDispatcher) {
            loadedSession()
            every { streamChatTurnUseCase("s-1", any(), false, any()) } returns
                flow { throw ServerOfflineException("Network unavailable") }

            viewModel.sendMessage("Did this arrive?")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(MessageStatus.FAILED_OFFLINE, state.messages.first { it.role == MessageRole.USER }.status)
            assertEquals(TurnState.Idle, state.turnState)
            assertTrue(state.canSend, "nothing was delivered, so sending again is the right move")
        }

    @Test
    fun `GIVEN a stream that drops mid answer WHEN a message is sent THEN the words so far are kept and the answer is blamed`() =
        runTest(testDispatcher) {
            loadedSession()
            every { streamChatTurnUseCase("s-1", any(), false, any()) } returns
                flowOf(
                    ChatStreamEvent.Delta("Three things, in order of how much "),
                    ChatStreamEvent.Delta("they'll bite."),
                    ChatStreamEvent.Reconnecting,
                )

            viewModel.sendMessage("What's left before Friday?")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("Three things, in order of how much they'll bite.", state.streamingMessage)
            assertEquals(TurnState.Reconnecting, state.turnState)
            assertEquals(
                MessageStatus.SENDING,
                state.messages.first { it.role == MessageRole.USER }.status,
                "the question arrived; only the answer is interrupted",
            )
            assertFalse(state.canSend, "a second send would answer 409, so the composer says so instead")
        }

    @Test
    fun `GIVEN a stream that dropped and waited past the active wait WHEN a message is sent THEN the turn is still working rather than failed`() =
        runTest(testDispatcher) {
            loadedSession()
            every { streamChatTurnUseCase("s-1", any(), false, any()) } returns
                flowOf(
                    ChatStreamEvent.Delta("Three things"),
                    ChatStreamEvent.Reconnecting,
                    ChatStreamEvent.StillWorking,
                )

            viewModel.sendMessage("What's left before Friday?")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(TurnState.StillWorking, state.turnState)
            assertEquals("Three things", state.streamingMessage)
            assertNull(state.errorMessage, "giving up on waiting is not the answer failing")
            assertFalse(state.canSend)
        }

    @Test
    fun `GIVEN a turn the hub is no longer running WHEN a message is sent THEN the turn is failed and the composer is free`() =
        runTest(testDispatcher) {
            loadedSession()
            every { streamChatTurnUseCase("s-1", any(), false, any()) } returns
                flowOf(
                    ChatStreamEvent.Delta("Plan"),
                    ChatStreamEvent.Reconnecting,
                    ChatStreamEvent.TurnFailed,
                )

            viewModel.sendMessage("Plan meals for the week")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(TurnState.Failed, state.turnState)
            assertTrue(state.canSend, "the turn is over, so the composer is free again")
        }

    // ---- coming back to a turn --------------------------------------------

    @Test
    fun `GIVEN an answer left still working while the phone was locked WHEN the app comes back THEN it carries on from the last word`() =
        runTest(testDispatcher) {
            loadedSession()
            every { streamChatTurnUseCase("s-1", any(), false, any()) } returns
                flowOf(
                    ChatStreamEvent.Accepted,
                    ChatStreamEvent.Delta("Hel"),
                    ChatStreamEvent.Reconnecting,
                    ChatStreamEvent.StillWorking,
                )
            every { resumeTurnUseCase("s-1", any()) } returns
                flowOf(ChatStreamEvent.Reconnecting, ChatStreamEvent.Delta("lo"))
            viewModel.sendMessage("Say hello")
            advanceUntilIdle()

            viewModel.onForeground()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("Hello", state.streamingMessage)
            assertEquals(TurnState.Streaming, state.turnState)
        }

    @Test
    fun `GIVEN an answer resumed after the phone was locked WHEN it finishes THEN it lands as the answer`() =
        runTest(testDispatcher) {
            loadedSession()
            every { streamChatTurnUseCase("s-1", any(), false, any()) } returns
                flowOf(
                    ChatStreamEvent.Delta("Hel"),
                    ChatStreamEvent.Reconnecting,
                    ChatStreamEvent.StillWorking,
                )
            every { resumeTurnUseCase("s-1", any()) } returns
                flowOf(
                    ChatStreamEvent.Delta("lo"),
                    ChatStreamEvent.Done(messageId = "m2", assistantContent = "Hello", agentName = "Assistant"),
                )
            viewModel.sendMessage("Say hello")
            advanceUntilIdle()

            viewModel.onForeground()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("Hello", state.messages.last { it.role == MessageRole.ASSISTANT }.content)
            assertEquals(MessageStatus.SENT, state.messages.first { it.role == MessageRole.USER }.status)
            assertNull(state.streamingMessage)
            assertEquals(TurnState.Idle, state.turnState)
        }

    @Test
    fun `GIVEN a turn still reconnecting in the background WHEN the app comes back THEN it asks right away instead of waiting out the backoff`() =
        runTest(testDispatcher) {
            loadedSession()
            every { streamChatTurnUseCase("s-1", any(), false, any()) } returns
                flow {
                    emit(ChatStreamEvent.Delta("Hel"))
                    emit(ChatStreamEvent.Reconnecting)
                    awaitCancellation()
                }
            every { resumeTurnUseCase("s-1", any()) } returns flowOf(ChatStreamEvent.Delta("lo"))
            viewModel.sendMessage("Say hello")
            advanceUntilIdle()

            viewModel.onForeground()
            advanceUntilIdle()

            assertEquals("Hello", viewModel.uiState.value.streamingMessage)
        }

    @Test
    fun `GIVEN nothing being answered WHEN the app comes back THEN nothing is asked of the hub`() =
        runTest(testDispatcher) {
            loadedSession()

            viewModel.onForeground()
            advanceUntilIdle()

            verify(VerifyMode.exactly(0)) { resumeTurnUseCase(any(), any()) }
        }

    // ---- opening a conversation mid-turn --------------------------------------

    private fun question(id: String = "m1") =
        ChatMessage(
            id = id,
            sessionId = "s-1",
            role = MessageRole.USER,
            content = "Plan the week",
            status = MessageStatus.SENT,
        )

    private fun answer(id: String = "m2") =
        ChatMessage(
            id = id,
            sessionId = "s-1",
            role = MessageRole.ASSISTANT,
            content = "Here's the week",
            status = MessageStatus.SENT,
        )

    @Test
    fun `GIVEN a question still being answered WHEN the conversation is opened THEN the answer streams in`() =
        runTest(testDispatcher) {
            val session = ConversationSession(id = "s-1", userId = "u-1", turnRunning = true)
            everySuspend { getSessionUseCase("s-1") } returns Pair(session, listOf(question()))
            every { resumeTurnUseCase("s-1", any()) } returns
                flowOf(ChatStreamEvent.Reconnecting, ChatStreamEvent.Delta("Monday"))

            viewModel.loadSession("s-1")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("Monday", state.streamingMessage)
            assertEquals(TurnState.Streaming, state.turnState)
            assertFalse(state.canSend)
        }

    @Test
    fun `GIVEN a question still being answered WHEN the conversation is opened and the answer finishes THEN it lands`() =
        runTest(testDispatcher) {
            val session = ConversationSession(id = "s-1", userId = "u-1", turnRunning = true)
            everySuspend { getSessionUseCase("s-1") } returns Pair(session, listOf(question()))
            every { resumeTurnUseCase("s-1", any()) } returns
                flowOf(
                    ChatStreamEvent.Delta("Monday"),
                    ChatStreamEvent.Done(messageId = "m2", assistantContent = "Monday", agentName = "Assistant"),
                )

            viewModel.loadSession("s-1")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("Monday", state.messages.last().content)
            assertNull(state.streamingMessage)
            assertEquals(TurnState.Idle, state.turnState)
        }

    @Test
    fun `GIVEN a question whose answer never came WHEN the conversation is opened THEN it offers to try again`() =
        runTest(testDispatcher) {
            val session = ConversationSession(id = "s-1", userId = "u-1", turnRunning = false)
            everySuspend { getSessionUseCase("s-1") } returns Pair(session, listOf(question()))

            viewModel.loadSession("s-1")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(TurnState.Failed, state.turnState)
            assertTrue(state.canSend)
            verify(VerifyMode.exactly(0)) { resumeTurnUseCase(any(), any()) }
        }

    @Test
    fun `GIVEN a conversation that ends in an answer WHEN it is opened THEN nothing is waited for`() =
        runTest(testDispatcher) {
            val session = ConversationSession(id = "s-1", userId = "u-1")
            everySuspend { getSessionUseCase("s-1") } returns Pair(session, listOf(question(), answer()))

            viewModel.loadSession("s-1")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(TurnState.Idle, state.turnState)
            assertNull(state.streamingMessage)
            verify(VerifyMode.exactly(0)) { resumeTurnUseCase(any(), any()) }
        }

    // ---- what the agent says that is not the answer ------------------------

    @Test
    fun `GIVEN a hub that has taken the question WHEN a message is sent THEN the question is marked sent at once`() =
        runTest(testDispatcher) {
            loadedSession()
            every { streamChatTurnUseCase("s-1", any(), false, any()) } returns flowOf(ChatStreamEvent.Accepted)

            viewModel.sendMessage("What is on tomorrow?")
            advanceUntilIdle()

            // Not at the end of the answer, which may be a minute away: the receipt says so now.
            assertEquals(
                MessageStatus.SENT,
                viewModel.uiState.value.messages
                    .single()
                    .status,
            )
        }

    @Test
    fun `GIVEN a model that has only reasoned so far WHEN a message is sent THEN the model is marked as thinking and not silent`() =
        runTest(testDispatcher) {
            loadedSession()
            every { streamChatTurnUseCase("s-1", any(), false, any()) } returns
                flowOf(ChatStreamEvent.Reasoning("Okay, the user "), ChatStreamEvent.Reasoning("wants tomorrow."))

            viewModel.sendMessage("What is on tomorrow?")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.isThinking)
            assertFalse(state.isSilent, "a model visibly thinking is not a silent hub")
        }

    @Test
    fun `GIVEN a tool that is running WHEN a message is sent THEN the tool is shown while it runs`() =
        runTest(testDispatcher) {
            loadedSession()
            every { streamChatTurnUseCase("s-1", any(), false, any()) } returns
                flowOf(ChatStreamEvent.ToolExecuting("calendar_read"))

            viewModel.sendMessage("What is on tomorrow?")
            advanceUntilIdle()

            assertEquals("calendar_read", viewModel.uiState.value.activeTool)
        }

    @Test
    fun `GIVEN a write running WHEN shown THEN its action is shown with it and goes when it is done`() =
        runTest(testDispatcher) {
            loadedSession()
            every { streamChatTurnUseCase("s-1", any(), false, any()) } returns
                flowOf(ChatStreamEvent.ToolExecuting("calendar_write", action = ToolAction.Delete))

            viewModel.sendMessage("Cancel the print shop thing")
            advanceUntilIdle()

            assertEquals("calendar_write", viewModel.uiState.value.activeTool)
            assertEquals(ToolAction.Delete, viewModel.uiState.value.activeToolAction)
        }

    @Test
    fun `GIVEN a write that finished WHEN the next tool runs THEN no action is left over from it`() =
        runTest(testDispatcher) {
            loadedSession()
            every { streamChatTurnUseCase("s-1", any(), false, any()) } returns
                flowOf(
                    ChatStreamEvent.ToolExecuting("calendar_write", action = ToolAction.Delete),
                    ChatStreamEvent.ToolResult("calendar_write", success = true),
                    ChatStreamEvent.ToolExecuting("calendar_read"),
                )

            viewModel.sendMessage("Cancel it and tell me what's left")
            advanceUntilIdle()

            assertEquals("calendar_read", viewModel.uiState.value.activeTool)
            assertNull(viewModel.uiState.value.activeToolAction)
        }

    @Test
    fun `GIVEN a tool that finishes and one that fails WHEN they run THEN each leaves its part and the failure says so`() =
        runTest(testDispatcher) {
            loadedSession()
            every { streamChatTurnUseCase("s-1", any(), false, any()) } returns
                flowOf(
                    ChatStreamEvent.ToolExecuting("calendar_read"),
                    ChatStreamEvent.ToolResult("calendar_read", success = true),
                    ChatStreamEvent.ToolExecuting("searxng_search"),
                    ChatStreamEvent.ToolResult("searxng_search", success = false, error = "offline"),
                )

            viewModel.sendMessage("What is on tomorrow?")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNull(state.activeTool)
            assertEquals(
                listOf(AnswerPart.ToolDone("calendar_read"), AnswerPart.ToolFailed("searxng_search")),
                state.parts,
            )
        }

    /** The live line shows the query, the results and why a step failed, as the reopened chat will (#40). */
    @Test
    fun `GIVEN tool results with summaries WHEN they arrive THEN each part carries its summary`() =
        runTest(testDispatcher) {
            loadedSession()
            val menu = ToolSource("Menu", "https://lapubilla.cat/")
            val found = ToolSummary(query = "dinner", count = 1, sources = listOf(menu))
            val scmp = ToolSource("", "https://scmp.com/")
            val blocked = ToolSummary(reason = ToolFailureReason.Blocked, sources = listOf(scmp))
            every { streamChatTurnUseCase("s-1", any(), false, any()) } returns
                flowOf(
                    ChatStreamEvent.ToolExecuting("searxng_search"),
                    ChatStreamEvent.ToolResult("searxng_search", success = true, summary = found),
                    ChatStreamEvent.ToolExecuting("read_page"),
                    ChatStreamEvent.ToolResult("read_page", success = false, summary = blocked),
                )

            viewModel.sendMessage("Dinner near Gràcia?")
            advanceUntilIdle()

            assertEquals(
                listOf(AnswerPart.ToolDone("searxng_search", found), AnswerPart.ToolFailed("read_page", blocked)),
                viewModel.uiState.value.parts,
            )
        }

    /** Issue #33: the tool used to be drawn above the whole answer, and the two stretches ran together. */
    @Test
    fun `GIVEN text then a tool then more text WHEN it streams THEN the parts keep that order`() =
        runTest(testDispatcher) {
            loadedSession()
            every { streamChatTurnUseCase("s-1", any(), false, any()) } returns
                flowOf(
                    ChatStreamEvent.Delta("Let me "),
                    ChatStreamEvent.Delta("check."),
                    ChatStreamEvent.ToolExecuting("web_search"),
                    ChatStreamEvent.ToolResult("web_search", success = true),
                    ChatStreamEvent.Delta("It stays "),
                    ChatStreamEvent.Delta("dry."),
                )

            viewModel.sendMessage("Will Saturday stay dry?")
            advanceUntilIdle()

            assertEquals(
                listOf(
                    AnswerPart.Text("Let me check."),
                    AnswerPart.ToolDone("web_search"),
                    AnswerPart.Text("It stays dry."),
                ),
                viewModel.uiState.value.parts,
            )
        }

    /** Timed from the first thought of each stretch to whatever ends it: a tool, a word, the end. */
    @Test
    fun `GIVEN thinking before each stretch WHEN it streams THEN each stretch has its own thought where it happened`() =
        runTest(testDispatcher) {
            val clock = TestTimeSource()
            viewModel = viewModelWith(clock)
            loadedSession()
            every { streamChatTurnUseCase("s-1", any(), false, any()) } returns
                flow {
                    emit(ChatStreamEvent.Reasoning("I need the calendar."))
                    clock += 3.seconds
                    emit(ChatStreamEvent.ToolExecuting("calendar_read"))
                    clock += 20.seconds // the tool running is not thinking
                    emit(ChatStreamEvent.ToolResult("calendar_read", success = true))
                    emit(ChatStreamEvent.Reasoning("Two events."))
                    clock += 1.seconds
                    emit(ChatStreamEvent.Delta("Two things."))
                }

            viewModel.sendMessage("What is on tomorrow?")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(
                listOf(
                    AnswerPart.Thought(3),
                    AnswerPart.ToolDone("calendar_read"),
                    AnswerPart.Thought(1),
                    AnswerPart.Text("Two things."),
                ),
                state.parts,
            )
            assertFalse(state.isThinking, "once the answer begins, the thinking steps aside")
        }

    /** The chip goes when the tool is done; something has to say the answer is still coming. */
    @Test
    fun `GIVEN a tool that finished after the first words WHEN the next words have not come THEN the turn is waiting for them`() =
        runTest(testDispatcher) {
            loadedSession()
            every { streamChatTurnUseCase("s-1", any(), false, any()) } returns
                flow {
                    emit(ChatStreamEvent.Delta("Let me check."))
                    emit(ChatStreamEvent.ToolExecuting("web_search"))
                    emit(ChatStreamEvent.ToolResult("web_search", success = true))
                    awaitCancellation()
                }

            viewModel.sendMessage("Will Saturday stay dry?")
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isWaitingForWords)
        }

    @Test
    fun `GIVEN a turn waiting after a tool WHEN the next words arrive THEN it is no longer waiting`() =
        runTest(testDispatcher) {
            loadedSession()
            every { streamChatTurnUseCase("s-1", any(), false, any()) } returns
                flow {
                    emit(ChatStreamEvent.Delta("Let me check."))
                    emit(ChatStreamEvent.ToolExecuting("web_search"))
                    emit(ChatStreamEvent.ToolResult("web_search", success = true))
                    emit(ChatStreamEvent.Delta("It stays dry."))
                    awaitCancellation()
                }

            viewModel.sendMessage("Will Saturday stay dry?")
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isWaitingForWords)
        }

    @Test
    fun `GIVEN a finished answer WHEN the hub sends no parts THEN the message keeps the parts it streamed`() =
        runTest(testDispatcher) {
            loadedSession()
            every { streamChatTurnUseCase("s-1", any(), false, any()) } returns
                flowOf(
                    ChatStreamEvent.ToolExecuting("calendar_read"),
                    ChatStreamEvent.ToolResult("calendar_read", success = true),
                    ChatStreamEvent.Delta("A light day."),
                    ChatStreamEvent.Done(messageId = "m-2", assistantContent = "A light day."),
                )

            viewModel.sendMessage("What is on tomorrow?")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(
                listOf(AnswerPart.ToolDone("calendar_read"), AnswerPart.Text("A light day.")),
                state.messages.single { it.id == "m-2" }.parts,
            )
            assertTrue(state.parts.isEmpty())
            assertNull(state.activeTool)
        }

    @Test
    fun `GIVEN a finished answer WHEN the hub sends its parts THEN the message keeps the hub's`() =
        runTest(testDispatcher) {
            loadedSession()
            val saved =
                listOf(AnswerPart.Thought(2), AnswerPart.ToolDone("calendar_read"), AnswerPart.Text("A light day."))
            every { streamChatTurnUseCase("s-1", any(), false, any()) } returns
                flowOf(
                    ChatStreamEvent.ToolExecuting("calendar_read"),
                    ChatStreamEvent.ToolResult("calendar_read", success = true),
                    ChatStreamEvent.Delta("A light day."),
                    ChatStreamEvent.Done(messageId = "m-2", assistantContent = "A light day.", parts = saved),
                )

            viewModel.sendMessage("What is on tomorrow?")
            advanceUntilIdle()

            assertEquals(
                saved,
                viewModel.uiState.value.messages
                    .single { it.id == "m-2" }
                    .parts,
            )
        }

    @Test
    fun `GIVEN an answer resumed after a lock WHEN more text and a tool arrive THEN they carry on after the parts already shown`() =
        runTest(testDispatcher) {
            loadedSession()
            every { streamChatTurnUseCase("s-1", any(), false, any()) } returns
                flowOf(
                    ChatStreamEvent.Accepted,
                    ChatStreamEvent.Delta("Let me "),
                    ChatStreamEvent.Reconnecting,
                    ChatStreamEvent.StillWorking,
                )
            every { resumeTurnUseCase("s-1", any()) } returns
                flowOf(
                    ChatStreamEvent.Delta("check."),
                    ChatStreamEvent.ToolExecuting("web_search"),
                    ChatStreamEvent.ToolResult("web_search", success = true),
                    ChatStreamEvent.Delta("Dry."),
                )
            viewModel.sendMessage("Will Saturday stay dry?")
            advanceUntilIdle()

            viewModel.onForeground()
            advanceUntilIdle()

            assertEquals(
                listOf(AnswerPart.Text("Let me check."), AnswerPart.ToolDone("web_search"), AnswerPart.Text("Dry.")),
                viewModel.uiState.value.parts,
            )
        }

    @Test
    fun `GIVEN a failed first turn WHEN a second message is sent THEN nothing is left over from the last turn`() =
        runTest(testDispatcher) {
            loadedSession()
            every { streamChatTurnUseCase("s-1", "First", false, any()) } returns
                flowOf(
                    ChatStreamEvent.Reasoning("Hmm."),
                    ChatStreamEvent.ToolExecuting("calendar_read"),
                    ChatStreamEvent.ToolResult("calendar_read", success = false),
                    ChatStreamEvent.TurnFailed,
                )
            every { streamChatTurnUseCase("s-1", "Second", false, any()) } returns flowOf(ChatStreamEvent.Accepted)
            viewModel.sendMessage("First")
            advanceUntilIdle()

            viewModel.sendMessage("Second")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isThinking)
            assertTrue(state.parts.isEmpty())
            assertTrue(state.isSilent, "a turn that has heard nothing yet is silent")
        }

    /**
     * The turn that answered nothing at all, and then would not let go.
     *
     * A stream that finished without saying how left the composer believing a turn was still
     * running, so it refused every later message — silently, since a send it will not take clears
     * nothing and says nothing. Two messages typed, neither sent, and an empty bubble on screen.
     */
    @Test
    fun `GIVEN a turn that ends without a word WHEN a message is sent THEN the turn is failed and the composer is free`() =
        runTest(testDispatcher) {
            loadedSession()
            every { streamChatTurnUseCase("s-1", any(), false, any()) } returns flowOf(ChatStreamEvent.TurnFailed)

            viewModel.sendMessage("What's left before Friday?")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(TurnState.Failed, state.turnState)
            assertTrue(state.canSend, "a turn that is over must not go on blocking the composer")
        }

    @Test
    fun `GIVEN a failed turn that has ended WHEN a second message is sent THEN both questions are in the chat`() =
        runTest(testDispatcher) {
            loadedSession()
            every { streamChatTurnUseCase("s-1", any(), false, any()) } returns flowOf(ChatStreamEvent.TurnFailed)
            viewModel.sendMessage("The first one")
            advanceUntilIdle()

            viewModel.sendMessage("And the second")
            advanceUntilIdle()

            val asked =
                viewModel.uiState.value.messages
                    .filter { it.role == MessageRole.USER }
                    .map { it.content }
            assertEquals(listOf("The first one", "And the second"), asked)
        }

    @Test
    fun `GIVEN an answer already on screen WHEN a message is sent THEN the recovery is told which answer is on screen`() =
        runTest(testDispatcher) {
            val session = ConversationSession(id = "s-1", userId = "u-1")
            everySuspend { getSessionUseCase("s-1") } returns
                Pair(
                    session,
                    listOf(
                        ChatMessage(id = "m-1", sessionId = "s-1", role = MessageRole.USER, content = "Last week"),
                        ChatMessage(id = "m-2", sessionId = "s-1", role = MessageRole.ASSISTANT, content = "An answer"),
                    ),
                )
            viewModel.loadSession("s-1")
            advanceUntilIdle()

            every { streamChatTurnUseCase("s-1", any(), false, "m-2") } returns flowOf(ChatStreamEvent.Delta("New"))

            viewModel.sendMessage("And this week?")
            advanceUntilIdle()

            // Without "m-2" the stubbing would not match and nothing would stream.
            assertEquals("New", viewModel.uiState.value.streamingMessage)
        }

    @Test
    fun `GIVEN a failed turn WHEN trying again THEN a fresh answer arrives without a second copy of the question`() =
        runTest(testDispatcher) {
            loadedSession()
            every { streamChatTurnUseCase("s-1", any(), false, any()) } returns
                flowOf(ChatStreamEvent.Delta("Plan"), ChatStreamEvent.TurnFailed)
            viewModel.sendMessage("Plan meals for the week")
            advanceUntilIdle()

            every { regenerateAnswerUseCase("s-1", any()) } returns
                flowOf(
                    ChatStreamEvent.Delta("Here is the week."),
                    ChatStreamEvent.Done(messageId = "m-9", assistantContent = "Here is the week."),
                )

            viewModel.regenerate()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(TurnState.Idle, state.turnState)
            assertEquals(
                1,
                state.messages.count { it.role == MessageRole.USER },
                "regenerating must not add a second copy of the question",
            )
            assertEquals("Here is the week.", state.messages.last { it.role == MessageRole.ASSISTANT }.content)
        }

    // ---- what happens to what you typed ------------------------------------

    @Test
    fun `GIVEN a typed message WHEN it is sent THEN it leaves the composer`() =
        runTest(testDispatcher) {
            loadedSession()
            every { streamChatTurnUseCase("s-1", any(), false, any()) } returns
                flowOf(ChatStreamEvent.Done(messageId = "m-1", assistantContent = "Hello back"))

            viewModel.composerTextChanged("Hi")
            viewModel.sendMessage("Hi")
            advanceUntilIdle()

            assertEquals("", viewModel.uiState.value.composerText)
        }

    @Test
    fun `GIVEN a new chat where the hub refuses to create one WHEN a typed message is sent THEN the text stays in the composer and an error shows`() =
        runTest(testDispatcher) {
            // A brand new chat, and the hub refuses to create one. Nothing typed is ever cleared
            // (design notes §2): losing the message is worse than the failure that caused it.
            everySuspend { listAgentsUseCase() } returns emptyList()

            viewModel.open(null)
            advanceUntilIdle()
            viewModel.composerTextChanged("Hi")
            viewModel.sendMessage("Hi")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("Hi", state.composerText)
            assertNotNull(state.errorMessage, "a send that went nowhere has to say so")
        }

    @Test
    fun `GIVEN a turn that did not fail WHEN trying again THEN nothing is asked of the hub`() =
        runTest(testDispatcher) {
            loadedSession()

            viewModel.regenerate()
            advanceUntilIdle()

            assertEquals(TurnState.Idle, viewModel.uiState.value.turnState)
            verify(VerifyMode.exactly(0)) { regenerateAnswerUseCase(any(), any()) }
        }

    // ---- choosing who a new chat talks to ----------------------------------

    private val coordinator =
        AgentPersonality(
            id = "agent-coord",
            slug = "assistant",
            name = "Home Coordinator",
            description = "Schedules, meals, keeping the week straight.",
            avatar = "🏡",
            systemPrompt = "",
            isBuiltin = true,
        )

    private val researcher =
        AgentPersonality(
            id = "agent-research",
            slug = "researcher",
            name = "Academic Researcher",
            description = "Papers, references, reading long PDFs properly.",
            avatar = "📚",
            systemPrompt = "",
            toolPermissions = listOf("searxng_search", "pdf_reader"),
            isBuiltin = true,
        )

    private val scout =
        AgentPersonality(
            id = "agent-scout",
            slug = "scout",
            name = "Hardware Scout",
            avatar = "🔧",
            systemPrompt = "",
            ownerId = "user-liam",
        )

    private fun TestScope.newChatWithAgents(membersListed: Boolean = true) {
        everySuspend { listAgentsUseCase() } returns listOf(coordinator, researcher, scout)
        everySuspend { getCurrentUserUseCase() } returns
            User(id = "user-emma", fullName = "Emma Doyle", isAdmin = true, isActive = true)
        if (membersListed) {
            everySuspend { listHouseholdMembersUseCase() } returns
                listOf(User(id = "user-liam", fullName = "Liam Doyle", isAdmin = false, isActive = true))
        } else {
            everySuspend { listHouseholdMembersUseCase() } throws RuntimeException("members down")
        }
        viewModel.open(null)
        advanceUntilIdle()
    }

    @Test
    fun `GIVEN the hub lists agents WHEN a new chat opens THEN every agent is offered and the Coordinator is chosen`() =
        runTest(testDispatcher) {
            newChatWithAgents()

            val state = viewModel.uiState.value
            assertEquals(listOf("agent-coord", "agent-research", "agent-scout"), state.agents.map { it.id })
            assertEquals(AgentOwner.Member("Liam"), state.agents.last().owner)
            assertEquals("agent-coord", state.selectedAgentId)
            assertEquals("Home Coordinator", state.agentName)
            assertFalse(state.agentsFailed)
            assertTrue(state.canChangeAgent)
        }

    @Test
    fun `GIVEN the members cannot be listed WHEN a new chat opens THEN the agents are still offered`() =
        runTest(testDispatcher) {
            newChatWithAgents(membersListed = false)

            val state = viewModel.uiState.value
            assertEquals(3, state.agents.size)
            assertEquals(AgentOwner.Unknown, state.agents.last().owner)
            assertFalse(state.agentsFailed)
        }

    @Test
    fun `GIVEN the agent list fails WHEN a new chat opens THEN the Coordinator still greets and the list is marked failed`() =
        runTest(testDispatcher) {
            everySuspend { listAgentsUseCase() } throws RuntimeException("agents down")
            everySuspend { getAgentUseCase("assistant") } returns coordinator

            viewModel.open(null)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.agentsFailed)
            assertEquals("agent-coord", state.selectedAgentId)
            assertEquals("Home Coordinator", state.agentName)
            assertEquals(listOf("agent-coord"), state.agents.map { it.id })
            assertNull(state.errorMessage, "a missing list must not block the chat")
        }

    @Test
    fun `GIVEN a new chat WHEN another agent is picked THEN the greeting becomes theirs`() =
        runTest(testDispatcher) {
            newChatWithAgents()

            viewModel.selectAgent("agent-research")

            val state = viewModel.uiState.value
            assertEquals("agent-research", state.selectedAgentId)
            assertEquals("Academic Researcher", state.agentName)
            assertEquals("📚", state.agentAvatar)
            assertEquals("Papers, references, reading long PDFs properly.", state.agentTagline)
        }

    /** Back from a screen it opened, such as connecting a calendar: the chat is where it was left. */
    @Test
    fun `GIVEN a new chat under way WHEN the screen opens it again THEN it is kept and not started over`() =
        runTest(testDispatcher) {
            newChatWithAgents()
            viewModel.selectAgent("agent-research")

            viewModel.open(null)
            advanceUntilIdle()

            assertEquals("agent-research", viewModel.uiState.value.selectedAgentId)
            verifySuspend(VerifyMode.exactly(1)) { listAgentsUseCase() }
        }

    @Test
    fun `GIVEN an opened chat WHEN the screen opens it again THEN it is not fetched again`() =
        runTest(testDispatcher) {
            val session = ConversationSession(id = "s-1", userId = "u-1")
            everySuspend { getSessionUseCase("s-1") } returns Pair(session, emptyList())

            viewModel.open("s-1")
            advanceUntilIdle()
            viewModel.open("s-1")
            advanceUntilIdle()

            verifySuspend(VerifyMode.exactly(1)) { getSessionUseCase("s-1") }
        }

    /** A calendar connected from this chat: its fix cards are redrawn from what the hub now says. */
    @Test
    fun `GIVEN an opened chat WHEN it is refreshed THEN it is fetched again`() =
        runTest(testDispatcher) {
            val session = ConversationSession(id = "s-1", userId = "u-1")
            everySuspend { getSessionUseCase("s-1") } returns Pair(session, emptyList())
            viewModel.open("s-1")
            advanceUntilIdle()

            viewModel.refresh()
            advanceUntilIdle()

            verifySuspend(VerifyMode.exactly(2)) { getSessionUseCase("s-1") }
        }

    @Test
    fun `GIVEN a new chat nobody has spoken in WHEN it is refreshed THEN nothing is fetched`() =
        runTest(testDispatcher) {
            newChatWithAgents()

            viewModel.refresh()
            advanceUntilIdle()

            verifySuspend(VerifyMode.exactly(0)) { getSessionUseCase(any()) }
        }

    @Test
    fun `GIVEN a picked agent WHEN the first message is sent THEN the chat is created with that agent`() =
        runTest(testDispatcher) {
            newChatWithAgents()
            everySuspend { createSessionUseCase(any(), any(), any()) } returns
                ConversationSession(id = "s-new", userId = "user-emma", agentId = "agent-research")
            every { streamChatTurnUseCase("s-new", "Hi", false, any()) } returns
                flowOf(ChatStreamEvent.Done(messageId = "m-1", assistantContent = "Hello"))

            viewModel.selectAgent("agent-research")
            viewModel.sendMessage("Hi")
            advanceUntilIdle()

            verifySuspend { createSessionUseCase("agent-research", any(), any()) }
            assertFalse(viewModel.uiState.value.canChangeAgent)
        }

    @Test
    fun `GIVEN a chat that has started WHEN another agent is picked THEN nothing changes`() =
        runTest(testDispatcher) {
            loadedSession()
            val before = viewModel.uiState.value

            viewModel.selectAgent("agent-research")

            assertEquals(before, viewModel.uiState.value)
        }

    @Test
    fun `GIVEN the list failed WHEN asked again THEN the agents arrive and the choice is kept`() =
        runTest(testDispatcher) {
            everySuspend { listAgentsUseCase() } throws RuntimeException("agents down")
            everySuspend { getAgentUseCase("assistant") } returns coordinator
            viewModel.open(null)
            advanceUntilIdle()

            everySuspend { listAgentsUseCase() } returns listOf(coordinator, researcher, scout)
            viewModel.retryAgents()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.agentsFailed)
            assertEquals(3, state.agents.size)
            assertEquals("agent-coord", state.selectedAgentId)
        }

    // ---- which dates of a repeating event (repeating events) ---------------

    private val gymMove =
        dinner.copy(
            toolCallId = "c4",
            action = ToolAction.Update,
            details =
                ProposalDetails.CalendarEvent(
                    title = "Gym",
                    start = EventMoment(2026, 10, 6, hour = 8, minute = 0),
                    end = null,
                    allDay = false,
                    scope = EventScope.OnlyThis,
                ),
        )
    private val gymMoveOnwards =
        (gymMove.details as ProposalDetails.CalendarEvent).copy(
            scope = EventScope.ThisAndFollowing,
        )

    @Test
    fun `GIVEN the member picked this and following WHEN the card is approved THEN the hub gets the pick`() =
        runTest(testDispatcher) {
            openPaused(gymMove)
            every { decideToolProposalUseCase("s-1", "c4", true, gymMoveOnwards) } returns
                flowOf(ChatStreamEvent.Accepted, ChatStreamEvent.Done(messageId = "m-2", assistantContent = "Moved."))

            viewModel.decide("c4", approved = true, edited = gymMoveOnwards)
            advanceUntilIdle()

            verify { decideToolProposalUseCase("s-1", "c4", true, gymMoveOnwards) }
        }

    @Test
    fun `GIVEN the member picked this and following WHEN the ticked card is approved THEN the pick goes with it`() =
        runTest(testDispatcher) {
            openPaused(gymMove)
            settingsSave()
            every { decideToolProposalUseCase("s-1", "c4", true, gymMoveOnwards) } returns
                flowOf(ChatStreamEvent.Accepted, ChatStreamEvent.Done(messageId = "m-2", assistantContent = "Moved."))

            viewModel.approveAutomatically("c4", edited = gymMoveOnwards)
            advanceUntilIdle()

            verifySuspend { setToolApprovalUseCase("calendar_write", ToolAction.Update, true) }
            verify { decideToolProposalUseCase("s-1", "c4", true, gymMoveOnwards) }
        }

    @Test
    fun `GIVEN a memory stored months ago WHEN its age is asked THEN it is counted in months`() {
        val fixed =
            object : Clock {
                override fun now() = Instant.parse("2026-10-18T09:00:00Z")
            }
        val viewModel = viewModelWith(clock = fixed)

        assertEquals(MemoryAge.Month(3), viewModel.memoryAge(Instant.parse("2026-03-04T10:00:00Z")))
    }

    @Test
    fun `GIVEN a memory stored late on the 17th in UTC WHEN its age is asked THEN it is today in the phones own days`() {
        val fixed =
            object : Clock {
                override fun now() = Instant.parse("2026-10-18T09:00:00Z")
            }
        val viewModel = viewModelWith(clock = fixed)

        // 23:30Z on the 17th is already the 18th an hour ahead of UTC.
        assertEquals(MemoryAge.Today, viewModel.memoryAge(Instant.parse("2026-10-17T23:30:00Z")))
    }

    private companion object {
        /** The phone is an hour ahead of UTC, so 10:52Z reads 11:52. */
        const val ONE_HOUR = 3_600
    }
}
