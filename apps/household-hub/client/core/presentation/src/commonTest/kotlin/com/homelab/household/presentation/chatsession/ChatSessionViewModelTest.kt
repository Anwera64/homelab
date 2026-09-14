package com.homelab.household.presentation.chatsession

import app.cash.turbine.test
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.ChatMessage
import com.homelab.household.domain.model.ChatStreamEvent
import com.homelab.household.domain.model.ConversationSession
import com.homelab.household.domain.model.MessageRole
import com.homelab.household.domain.usecase.ApproveToolProposalUseCase
import com.homelab.household.domain.usecase.GetSessionUseCase
import com.homelab.household.domain.usecase.StreamChatTurnUseCase
import com.homelab.household.domain.usecase.ToggleSecretModeUseCase
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ChatSessionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val streamChatTurnUseCase = mock<StreamChatTurnUseCase>()
    private val getSessionUseCase = mock<GetSessionUseCase>()
    private val approveToolProposalUseCase = mock<ApproveToolProposalUseCase>()
    private val toggleSecretModeUseCase = mock<ToggleSecretModeUseCase>()

    private lateinit var viewModel: ChatSessionViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ChatSessionViewModel(
            streamChatTurnUseCase = streamChatTurnUseCase,
            getSessionUseCase = getSessionUseCase,
            approveToolProposalUseCase = approveToolProposalUseCase,
            toggleSecretModeUseCase = toggleSecretModeUseCase
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun load_session_populates_state_with_session_and_messages() = runTest(testDispatcher) {
        val session = ConversationSession(id = "s-1", userId = "u-1", title = "Chat")
        val messages = listOf(
            ChatMessage(id = "m-1", sessionId = "s-1", role = MessageRole.USER, content = "Hi"),
            ChatMessage(id = "m-2", sessionId = "s-1", role = MessageRole.ASSISTANT, content = "Hello!")
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
    fun send_message_streams_deltas_and_completes() = runTest(testDispatcher) {
        val session = ConversationSession(id = "s-1", userId = "u-1")
        everySuspend { getSessionUseCase("s-1") } returns Pair(session, emptyList())
        viewModel.loadSession("s-1")
        advanceUntilIdle()

        every { streamChatTurnUseCase("s-1", "How are you?", false) } returns flow {
            emit(ChatStreamEvent.Delta("I'm "))
            emit(ChatStreamEvent.Delta("doing great!"))
            emit(
                ChatStreamEvent.Done(
                    messageId = "m-done",
                    assistantContent = "I'm doing great!",
                    agentName = "Assistant"
                )
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
    fun tool_approval_proposal_is_stored_in_state() = runTest(testDispatcher) {
        val session = ConversationSession(id = "s-1", userId = "u-1")
        everySuspend { getSessionUseCase("s-1") } returns Pair(session, emptyList())
        viewModel.loadSession("s-1")
        advanceUntilIdle()

        val proposal = ChatStreamEvent.ToolApprovalProposal(
            tool = "database_wipe",
            message = "Allow wiping db?"
        )
        every { streamChatTurnUseCase("s-1", "Wipe it", false) } returns flowOf(proposal)

        viewModel.sendMessage("Wipe it")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.pendingToolProposal)
        assertEquals("database_wipe", state.pendingToolProposal?.tool)
    }

    @Test
    fun approve_tool_dispatches_usecase_and_clears_proposal() = runTest(testDispatcher) {
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
    fun server_offline_sets_error_message_in_state() = runTest(testDispatcher) {
        everySuspend { getSessionUseCase("s-offline") } throws ServerOfflineException("Server offline")

        viewModel.loadSession("s-offline")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Server offline", state.errorMessage)
        assertEquals(false, state.isLoading)
    }

    @Test
    fun send_message_generates_unique_ids_and_sets_status_to_sent_on_done() = runTest(testDispatcher) {
        val session = ConversationSession(id = "s-1", userId = "u-1")
        everySuspend { getSessionUseCase("s-1") } returns Pair(session, emptyList())
        viewModel.loadSession("s-1")
        advanceUntilIdle()

        every { streamChatTurnUseCase("s-1", any(), false) } returns flow {
            emit(ChatStreamEvent.Done(messageId = "m-done", assistantContent = "Hi back", agentName = "Assistant"))
        }

        viewModel.sendMessage("First message")
        viewModel.sendMessage("Second message")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val userMessages = state.messages.filter { it.role == MessageRole.USER }
        assertEquals(2, userMessages.size)
        assertTrue(userMessages[0].id != userMessages[1].id, "Message IDs must be unique")
        assertEquals(com.homelab.household.domain.model.MessageStatus.SENT, userMessages[0].status)
        assertEquals(com.homelab.household.domain.model.MessageStatus.SENT, userMessages[1].status)
    }

    /**
     * The ids only have to be unique, and "unique" cannot mean "the clock happened to tick between
     * two sends". Sending a burst is how that difference shows: a clock-derived id collides here on
     * a platform whose monotonic clock is coarser than the gap between two statements.
     */
    @Test
    fun a_burst_of_sends_gives_every_message_its_own_id() = runTest(testDispatcher) {
        val session = ConversationSession(id = "s-1", userId = "u-1")
        everySuspend { getSessionUseCase("s-1") } returns Pair(session, emptyList())
        viewModel.loadSession("s-1")
        advanceUntilIdle()

        every { streamChatTurnUseCase("s-1", any(), false) } returns flow {
            emit(ChatStreamEvent.Done(messageId = "m-done", assistantContent = "Hi back", agentName = "Assistant"))
        }

        val sends = 50
        repeat(sends) { viewModel.sendMessage("Message $it") }
        advanceUntilIdle()

        val ids = viewModel.uiState.value.messages
            .filter { it.role == MessageRole.USER }
            .map { it.id }
        assertEquals(sends, ids.size)
        assertEquals(sends, ids.toSet().size, "every send needs its own id; got ${sends - ids.toSet().size} collisions")
    }

    @Test
    fun send_message_on_offline_failure_updates_user_message_status_to_failed_offline() = runTest(testDispatcher) {
        val session = ConversationSession(id = "s-1", userId = "u-1")
        everySuspend { getSessionUseCase("s-1") } returns Pair(session, emptyList())
        viewModel.loadSession("s-1")
        advanceUntilIdle()

        every { streamChatTurnUseCase("s-1", "Offline test", false) } returns flow {
            throw ServerOfflineException("Network unavailable")
        }

        viewModel.sendMessage("Offline test")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val userMessage = state.messages.firstOrNull { it.role == MessageRole.USER }
        assertNotNull(userMessage)
        assertEquals(com.homelab.household.domain.model.MessageStatus.FAILED_OFFLINE, userMessage?.status)
        assertEquals("Network unavailable", state.errorMessage)
    }

    @Test
    fun send_message_on_general_failure_updates_user_message_status_to_failed_error() = runTest(testDispatcher) {
        val session = ConversationSession(id = "s-1", userId = "u-1")
        everySuspend { getSessionUseCase("s-1") } returns Pair(session, emptyList())
        viewModel.loadSession("s-1")
        advanceUntilIdle()

        every { streamChatTurnUseCase("s-1", "General test", false) } returns flow {
            throw RuntimeException("Internal error")
        }

        viewModel.sendMessage("General test")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val userMessage = state.messages.firstOrNull { it.role == MessageRole.USER }
        assertNotNull(userMessage)
        assertEquals(com.homelab.household.domain.model.MessageStatus.FAILED_ERROR, userMessage?.status)
        assertEquals("Internal error", state.errorMessage)
    }
}
