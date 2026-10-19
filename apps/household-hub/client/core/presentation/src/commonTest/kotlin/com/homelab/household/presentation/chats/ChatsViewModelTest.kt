package com.homelab.household.presentation.chats

import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.ConversationSession
import com.homelab.household.domain.usecase.ListSessionsUseCase
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Chats list, and the search that runs over it.
 *
 * Search is on the phone for the MVP (design notes §6.16): it filters the list `GET /sessions`
 * already returns in full, matching titles and agent names. That means it works offline over
 * whatever is loaded, and costs the hub nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val listSessions = mock<ListSessionsUseCase>()

    private lateinit var viewModel: ChatsViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ChatsViewModel(listSessions)
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun session(
        id: String,
        title: String,
        agentName: String? = "Home & Life Coordinator",
        isSecret: Boolean = false,
    ) = ConversationSession(
        id = id,
        userId = "u-1",
        title = title,
        isSecret = isSecret,
        agentName = agentName,
        agentAvatar = "🏡",
        lastMessagePreview = "Something was said",
    )

    @Test
    fun `GIVEN a household with two chats WHEN loading THEN the list shows both and reports it is neither loading nor empty`() =
        runTest(testDispatcher) {
            everySuspend { listSessions() } returns
                listOf(session("s-1", "Saturday dinner"), session("s-2", "Groceries for the week"))

            viewModel.load()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf("Saturday dinner", "Groceries for the week"), state.visible.map { it.title })
            assertEquals(false, state.isLoading)
            assertEquals(false, state.isEmpty)
        }

    @Test
    fun `GIVEN a household that has never chatted WHEN loading THEN the state is empty rather than unsearched`() =
        runTest(testDispatcher) {
            everySuspend { listSessions() } returns emptyList()

            viewModel.load()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.isEmpty, "the empty screen has no search and no rows; both arrive with the first chat")
            assertEquals(false, state.hasNoMatches)
        }

    @Test
    fun `GIVEN several chats WHEN searching for a word in a title THEN only the chats with that word in the title show`() =
        runTest(testDispatcher) {
            everySuspend { listSessions() } returns
                listOf(
                    session("s-1", "Saturday dinner"),
                    session("s-2", "Dinner party menu"),
                    session("s-3", "Groceries for the week"),
                )
            viewModel.load()
            advanceUntilIdle()

            viewModel.search("dinner")

            assertEquals(
                listOf("Saturday dinner", "Dinner party menu"),
                viewModel.uiState.value.visible
                    .map { it.title },
                "matching is case-insensitive, as the canvas shows with a lowercase query",
            )
        }

    @Test
    fun `GIVEN chats with different agents WHEN searching for part of an agents name THEN that agents chat shows`() =
        runTest(testDispatcher) {
            everySuspend { listSessions() } returns
                listOf(
                    session("s-1", "Saturday dinner", agentName = "Home & Life Coordinator"),
                    session("s-2", "Jury panel references", agentName = "Academic & Document Researcher"),
                )
            viewModel.load()
            advanceUntilIdle()

            viewModel.search("researcher")

            assertEquals(
                listOf("Jury panel references"),
                viewModel.uiState.value.visible
                    .map { it.title },
            )
        }

    @Test
    fun `GIVEN a secret chat that matches WHEN searching THEN the secret chat is not in the results`() =
        runTest(testDispatcher) {
            everySuspend { listSessions() } returns
                listOf(
                    session("s-1", "Saturday dinner"),
                    session("s-2", "Dinner in secret", isSecret = true),
                )
            viewModel.load()
            advanceUntilIdle()

            viewModel.search("dinner")

            assertEquals(
                listOf("Saturday dinner"),
                viewModel.uiState.value.visible
                    .map { it.title },
                "search never shows secret chats, locked or not (design notes §6.16)",
            )
        }

    @Test
    fun `GIVEN a chat WHEN searching for something that matches nothing THEN it reports no matches rather than an empty household`() =
        runTest(testDispatcher) {
            everySuspend { listSessions() } returns listOf(session("s-1", "Saturday dinner"))
            viewModel.load()
            advanceUntilIdle()

            viewModel.search("boiler")

            val state = viewModel.uiState.value
            assertTrue(state.hasNoMatches, "this draws \"No chats called ...\", not the empty state")
            assertEquals(false, state.isEmpty)
        }

    @Test
    fun `GIVEN an active search WHEN cancelling the search THEN the whole list and an empty query return`() =
        runTest(testDispatcher) {
            everySuspend { listSessions() } returns
                listOf(session("s-1", "Saturday dinner"), session("s-2", "Groceries for the week"))
            viewModel.load()
            advanceUntilIdle()
            viewModel.search("dinner")

            viewModel.cancelSearch()

            assertEquals(2, viewModel.uiState.value.visible.size)
            assertEquals("", viewModel.uiState.value.query)
        }

    @Test
    fun `GIVEN a chat whose agent is gone WHEN searching by its title THEN the chat still shows`() =
        runTest(testDispatcher) {
            // Purging an agent clears the link (slice 7 draws the row properly; this must not crash).
            everySuspend { listSessions() } returns listOf(session("s-1", "Is the 5080 worth it?", agentName = null))
            viewModel.load()
            advanceUntilIdle()

            viewModel.search("5080")

            assertEquals(
                listOf("Is the 5080 worth it?"),
                viewModel.uiState.value.visible
                    .map { it.title },
            )
        }

    @Test
    fun `GIVEN an unreachable hub WHEN loading THEN the error message shows and the list is not reported as empty`() =
        runTest(testDispatcher) {
            everySuspend { listSessions() } throws ServerOfflineException("Can't reach your hub")

            viewModel.load()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("Can't reach your hub", state.errorMessage)
            assertEquals(false, state.isEmpty, "no chats loaded is not the same as no chats existing")
        }

    @Test
    fun `GIVEN a loaded list WHEN loading again THEN the rows are replaced rather than added to`() =
        runTest(testDispatcher) {
            everySuspend { listSessions() } returns listOf(session("s-1", "Saturday dinner"))
            viewModel.load()
            advanceUntilIdle()

            viewModel.load()
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.visible.size)
            assertNull(viewModel.uiState.value.errorMessage)
        }
}
