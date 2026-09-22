package com.homelab.household.presentation.chatsession

import app.cash.turbine.test
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.ChatMessage
import com.homelab.household.domain.model.ChatStreamEvent
import com.homelab.household.domain.model.ConversationSession
import com.homelab.household.domain.model.MessageRole
import com.homelab.household.domain.model.MessageStatus
import com.homelab.household.domain.usecase.ApproveToolProposalUseCase
import com.homelab.household.domain.usecase.CreateSessionUseCase
import com.homelab.household.domain.usecase.GetSessionUseCase
import com.homelab.household.domain.usecase.ListAgentsUseCase
import com.homelab.household.domain.usecase.RegenerateAnswerUseCase
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlin.time.TimeSource

@OptIn(ExperimentalCoroutinesApi::class)
class ChatSessionViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private val streamChatTurnUseCase = mock<StreamChatTurnUseCase>()
    private val getSessionUseCase = mock<GetSessionUseCase>()
    private val regenerateAnswerUseCase = mock<RegenerateAnswerUseCase>()
    private val listAgentsUseCase = mock<ListAgentsUseCase>(MockMode.autofill)
    private val createSessionUseCase = mock<CreateSessionUseCase>(MockMode.autofill)
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
                listAgentsUseCase = listAgentsUseCase,
                createSessionUseCase = createSessionUseCase,
                regenerateAnswerUseCase = regenerateAnswerUseCase,
                approveToolProposalUseCase = approveToolProposalUseCase,
                toggleSecretModeUseCase = toggleSecretModeUseCase,
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
            approveToolProposalUseCase = approveToolProposalUseCase,
            toggleSecretModeUseCase = toggleSecretModeUseCase,
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
    fun reasoning_gathers_while_the_model_thinks() =
        runTest(testDispatcher) {
            loadedSession()
            every { streamChatTurnUseCase("s-1", any(), false, any()) } returns
                flowOf(ChatStreamEvent.Reasoning("Okay, the user "), ChatStreamEvent.Reasoning("wants tomorrow."))

            viewModel.sendMessage("What is on tomorrow?")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("Okay, the user wants tomorrow.", state.reasoning)
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
    fun a_tool_that_finishes_leaves_a_record_and_one_that_fails_says_so() =
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
                listOf(TurnRecord.ToolDone("calendar_read"), TurnRecord.ToolFailed("searxng_search")),
                state.trail,
            )
        }

    /**
     * How long it thought, measured from the first thought to the first word and summed across a
     * tool — one record, at the top of the trail, however many times it stopped to think.
     */
    @Test
    fun thinking_folds_into_one_timed_record_at_the_top_of_the_trail() =
        runTest(testDispatcher) {
            val clock = TestTimeSource()
            viewModel = viewModelWith(clock)
            loadedSession()
            every { streamChatTurnUseCase("s-1", any(), false, any()) } returns
                flow {
                    emit(ChatStreamEvent.Reasoning("I need the calendar."))
                    clock += 3.seconds
                    emit(ChatStreamEvent.ToolExecuting("calendar_read"))
                    emit(ChatStreamEvent.ToolResult("calendar_read", success = true))
                    emit(ChatStreamEvent.Reasoning("Two events."))
                    clock += 1.seconds
                    emit(ChatStreamEvent.Delta("Two things."))
                }

            viewModel.sendMessage("What is on tomorrow?")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf(TurnRecord.Thought(4), TurnRecord.ToolDone("calendar_read")), state.trail)
            assertEquals("", state.reasoning, "once the answer begins, the thinking steps aside")
        }

    @Test
    fun the_trail_stays_with_its_answer_once_the_turn_is_done() =
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
            assertEquals(listOf(TurnRecord.ToolDone("calendar_read")), state.trails["m-2"])
            assertTrue(state.trail.isEmpty())
            assertNull(state.activeTool)
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
            assertEquals("", state.reasoning)
            assertTrue(state.trail.isEmpty())
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
}
