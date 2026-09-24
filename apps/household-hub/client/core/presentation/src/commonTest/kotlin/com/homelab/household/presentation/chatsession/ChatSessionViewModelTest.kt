package com.homelab.household.presentation.chatsession

import app.cash.turbine.test
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.AgentPersonality
import com.homelab.household.domain.model.AnswerPart
import com.homelab.household.domain.model.ChatMessage
import com.homelab.household.domain.model.ChatStreamEvent
import com.homelab.household.domain.model.ConversationSession
import com.homelab.household.domain.model.MessageRole
import com.homelab.household.domain.model.MessageStatus
import com.homelab.household.domain.model.User
import com.homelab.household.domain.usecase.ApproveToolProposalUseCase
import com.homelab.household.domain.usecase.CreateSessionUseCase
import com.homelab.household.domain.usecase.GetAgentUseCase
import com.homelab.household.domain.usecase.GetCurrentUserUseCase
import com.homelab.household.domain.usecase.GetSessionUseCase
import com.homelab.household.domain.usecase.ListAgentsUseCase
import com.homelab.household.domain.usecase.ListHouseholdMembersUseCase
import com.homelab.household.domain.usecase.RegenerateAnswerUseCase
import com.homelab.household.domain.usecase.ResumeTurnUseCase
import com.homelab.household.domain.usecase.StreamChatTurnUseCase
import com.homelab.household.domain.usecase.ToggleSecretModeUseCase
import dev.mokkery.MockMode
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
import kotlin.time.Duration.Companion.seconds
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
    private val approveToolProposalUseCase = mock<ApproveToolProposalUseCase>()
    private val toggleSecretModeUseCase = mock<ToggleSecretModeUseCase>()
    private val getAgentUseCase = mock<GetAgentUseCase>(MockMode.autofill)
    private val getCurrentUserUseCase = mock<GetCurrentUserUseCase>(MockMode.autofill)
    private val listHouseholdMembersUseCase = mock<ListHouseholdMembersUseCase>(MockMode.autofill)

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
                approveToolProposalUseCase = approveToolProposalUseCase,
                toggleSecretModeUseCase = toggleSecretModeUseCase,
                getAgentUseCase = getAgentUseCase,
                getCurrentUserUseCase = getCurrentUserUseCase,
                listHouseholdMembersUseCase = listHouseholdMembersUseCase,
            )
    }

    /** The same wiring, with a clock the test moves by hand. */
    private fun viewModelWith(timeSource: TimeSource) =
        ChatSessionViewModel(
            streamChatTurnUseCase = streamChatTurnUseCase,
            getSessionUseCase = getSessionUseCase,
            listAgentsUseCase = listAgentsUseCase,
            createSessionUseCase = createSessionUseCase,
            regenerateAnswerUseCase = regenerateAnswerUseCase,
            resumeTurnUseCase = resumeTurnUseCase,
            approveToolProposalUseCase = approveToolProposalUseCase,
            toggleSecretModeUseCase = toggleSecretModeUseCase,
            getAgentUseCase = getAgentUseCase,
            getCurrentUserUseCase = getCurrentUserUseCase,
            listHouseholdMembersUseCase = listHouseholdMembersUseCase,
            timeSource = timeSource,
        )

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun load_session_populates_state_with_session_and_messages() =
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
    fun send_message_streams_deltas_and_completes() =
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

    @Test
    fun tool_approval_proposal_is_stored_in_state() =
        runTest(testDispatcher) {
            val session = ConversationSession(id = "s-1", userId = "u-1")
            everySuspend { getSessionUseCase("s-1") } returns Pair(session, emptyList())
            viewModel.loadSession("s-1")
            advanceUntilIdle()

            val proposal =
                ChatStreamEvent.ToolApprovalProposal(
                    tool = "database_wipe",
                    message = "Allow wiping db?",
                )
            every { streamChatTurnUseCase("s-1", "Wipe it", false, any()) } returns flowOf(proposal)

            viewModel.sendMessage("Wipe it")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNotNull(state.pendingToolProposal)
            assertEquals("database_wipe", state.pendingToolProposal?.tool)
        }

    @Test
    fun approve_tool_dispatches_usecase_and_clears_proposal() =
        runTest(testDispatcher) {
            val session = ConversationSession(id = "s-1", userId = "u-1")
            everySuspend { getSessionUseCase("s-1") } returns Pair(session, emptyList())
            viewModel.loadSession("s-1")
            advanceUntilIdle()

            everySuspend { approveToolProposalUseCase("s-1", "tc-1", true, null) } returns true

            viewModel.approveTool("tc-1", approved = true)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNull(state.pendingToolProposal)
        }

    @Test
    fun server_offline_sets_error_message_in_state() =
        runTest(testDispatcher) {
            everySuspend { getSessionUseCase("s-offline") } throws ServerOfflineException("Server offline")

            viewModel.loadSession("s-offline")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("Server offline", state.errorMessage)
            assertEquals(false, state.isLoading)
        }

    @Test
    fun send_message_generates_unique_ids_and_sets_status_to_sent_on_done() =
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
    fun a_burst_of_sends_gives_every_message_its_own_id() =
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
    fun send_message_on_offline_failure_updates_user_message_status_to_failed_offline() =
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
    fun send_message_on_general_failure_updates_user_message_status_to_failed_error() =
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
    fun a_failure_before_the_first_word_blames_the_question_and_frees_the_composer() =
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
    fun a_stream_that_drops_mid_answer_keeps_the_words_and_blames_the_answer() =
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
    fun past_the_active_wait_the_turn_is_still_working_rather_than_failed() =
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
    fun a_turn_the_hub_is_no_longer_running_is_failed_and_offers_another_go() =
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
            // GIVEN
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

            // WHEN
            viewModel.onForeground()
            advanceUntilIdle()

            // THEN
            val state = viewModel.uiState.value
            assertEquals("Hello", state.streamingMessage)
            assertEquals(TurnState.Streaming, state.turnState)
        }

    @Test
    fun `GIVEN an answer resumed after the phone was locked WHEN it finishes THEN it lands as the answer`() =
        runTest(testDispatcher) {
            // GIVEN
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

            // WHEN
            viewModel.onForeground()
            advanceUntilIdle()

            // THEN
            val state = viewModel.uiState.value
            assertEquals("Hello", state.messages.last { it.role == MessageRole.ASSISTANT }.content)
            assertEquals(MessageStatus.SENT, state.messages.first { it.role == MessageRole.USER }.status)
            assertNull(state.streamingMessage)
            assertEquals(TurnState.Idle, state.turnState)
        }

    @Test
    fun `GIVEN a turn still reconnecting in the background WHEN the app comes back THEN it asks right away instead of waiting out the backoff`() =
        runTest(testDispatcher) {
            // GIVEN — the old wait never ends on its own, as a backoff delay would not for a while.
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

            // WHEN
            viewModel.onForeground()
            advanceUntilIdle()

            // THEN
            assertEquals("Hello", viewModel.uiState.value.streamingMessage)
        }

    @Test
    fun `GIVEN nothing being answered WHEN the app comes back THEN nothing is asked of the hub`() =
        runTest(testDispatcher) {
            // GIVEN
            loadedSession()

            // WHEN
            viewModel.onForeground()
            advanceUntilIdle()

            // THEN
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
            // GIVEN
            val session = ConversationSession(id = "s-1", userId = "u-1", turnRunning = true)
            everySuspend { getSessionUseCase("s-1") } returns Pair(session, listOf(question()))
            every { resumeTurnUseCase("s-1", any()) } returns
                flowOf(ChatStreamEvent.Reconnecting, ChatStreamEvent.Delta("Monday"))

            // WHEN
            viewModel.loadSession("s-1")
            advanceUntilIdle()

            // THEN
            val state = viewModel.uiState.value
            assertEquals("Monday", state.streamingMessage)
            assertEquals(TurnState.Streaming, state.turnState)
            assertFalse(state.canSend)
        }

    @Test
    fun `GIVEN a question still being answered WHEN the conversation is opened and the answer finishes THEN it lands`() =
        runTest(testDispatcher) {
            // GIVEN
            val session = ConversationSession(id = "s-1", userId = "u-1", turnRunning = true)
            everySuspend { getSessionUseCase("s-1") } returns Pair(session, listOf(question()))
            every { resumeTurnUseCase("s-1", any()) } returns
                flowOf(
                    ChatStreamEvent.Delta("Monday"),
                    ChatStreamEvent.Done(messageId = "m2", assistantContent = "Monday", agentName = "Assistant"),
                )

            // WHEN
            viewModel.loadSession("s-1")
            advanceUntilIdle()

            // THEN
            val state = viewModel.uiState.value
            assertEquals("Monday", state.messages.last().content)
            assertNull(state.streamingMessage)
            assertEquals(TurnState.Idle, state.turnState)
        }

    @Test
    fun `GIVEN a question whose answer never came WHEN the conversation is opened THEN it offers to try again`() =
        runTest(testDispatcher) {
            // GIVEN
            val session = ConversationSession(id = "s-1", userId = "u-1", turnRunning = false)
            everySuspend { getSessionUseCase("s-1") } returns Pair(session, listOf(question()))

            // WHEN
            viewModel.loadSession("s-1")
            advanceUntilIdle()

            // THEN
            val state = viewModel.uiState.value
            assertEquals(TurnState.Failed, state.turnState)
            assertTrue(state.canSend)
            verify(VerifyMode.exactly(0)) { resumeTurnUseCase(any(), any()) }
        }

    @Test
    fun `GIVEN a conversation that ends in an answer WHEN it is opened THEN nothing is waited for`() =
        runTest(testDispatcher) {
            // GIVEN
            val session = ConversationSession(id = "s-1", userId = "u-1")
            everySuspend { getSessionUseCase("s-1") } returns Pair(session, listOf(question(), answer()))

            // WHEN
            viewModel.loadSession("s-1")
            advanceUntilIdle()

            // THEN
            val state = viewModel.uiState.value
            assertEquals(TurnState.Idle, state.turnState)
            assertNull(state.streamingMessage)
            verify(VerifyMode.exactly(0)) { resumeTurnUseCase(any(), any()) }
        }

    // ---- what the agent says that is not the answer ------------------------

    @Test
    fun the_question_is_marked_sent_the_moment_the_hub_has_it() =
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
    fun reasoning_marks_the_model_as_thinking() =
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
    fun a_tool_is_shown_while_it_runs() =
        runTest(testDispatcher) {
            loadedSession()
            every { streamChatTurnUseCase("s-1", any(), false, any()) } returns
                flowOf(ChatStreamEvent.ToolExecuting("calendar_read"))

            viewModel.sendMessage("What is on tomorrow?")
            advanceUntilIdle()

            assertEquals("calendar_read", viewModel.uiState.value.activeTool)
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
    fun a_new_turn_starts_with_nothing_left_over_from_the_last() =
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
    fun a_turn_that_ends_without_a_word_frees_the_composer_rather_than_holding_it() =
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
    fun a_second_message_is_taken_once_a_failed_turn_has_ended() =
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
    fun the_recovery_is_told_which_answer_is_already_on_screen() =
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
    fun trying_again_asks_for_a_fresh_answer_without_asking_the_question_again() =
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
    fun a_message_that_was_taken_leaves_the_composer() =
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
    fun a_message_that_could_not_be_sent_stays_in_the_composer() =
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
    fun trying_again_does_nothing_when_the_turn_did_not_fail() =
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
}
