package com.homelab.household.presentation.chatsession

import app.cash.turbine.test
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.ChatMessage
import com.homelab.household.domain.model.ChatStreamEvent
import com.homelab.household.domain.model.ConversationSession
import com.homelab.household.domain.model.MessageRole
import com.homelab.household.domain.model.MessageStatus
import com.homelab.household.domain.usecase.ApproveToolProposalUseCase
import com.homelab.household.domain.usecase.GetSessionUseCase
import com.homelab.household.domain.usecase.RegenerateAnswerUseCase
import com.homelab.household.domain.usecase.StreamChatTurnUseCase
import com.homelab.household.domain.usecase.ToggleSecretModeUseCase
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class ChatSessionViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private val streamChatTurnUseCase = mock<StreamChatTurnUseCase>()
    private val getSessionUseCase = mock<GetSessionUseCase>()
    private val regenerateAnswerUseCase = mock<RegenerateAnswerUseCase>()
    private val approveToolProposalUseCase = mock<ApproveToolProposalUseCase>()
    private val toggleSecretModeUseCase = mock<ToggleSecretModeUseCase>()

    private lateinit var viewModel: ChatSessionViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel =
            ChatSessionViewModel(
                streamChatTurnUseCase = streamChatTurnUseCase,
                getSessionUseCase = getSessionUseCase,
                regenerateAnswerUseCase = regenerateAnswerUseCase,
                approveToolProposalUseCase = approveToolProposalUseCase,
                toggleSecretModeUseCase = toggleSecretModeUseCase,
            )
    }

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

            viewModel.sendMessage("First message")
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
     * two sends". Sending a burst is how that difference shows: a clock-derived id collides here on
     * a platform whose monotonic clock is coarser than the gap between two statements.
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
            repeat(sends) { viewModel.sendMessage("Message $it") }
            advanceUntilIdle()

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

    @Test
    fun trying_again_does_nothing_when_the_turn_did_not_fail() =
        runTest(testDispatcher) {
            loadedSession()

            viewModel.regenerate()
            advanceUntilIdle()

            assertEquals(TurnState.Idle, viewModel.uiState.value.turnState)
            verify(VerifyMode.exactly(0)) { regenerateAnswerUseCase(any(), any()) }
        }
}
