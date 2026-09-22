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
    fun the_list_loads_and_reports_what_it_found() =
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
    fun a_household_that_has_never_chatted_is_empty_rather_than_unsearched() =
        runTest(testDispatcher) {
            everySuspend { listSessions() } returns emptyList()

            viewModel.load()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.isEmpty, "the empty screen has no search and no rows; both arrive with the first chat")
            assertEquals(false, state.hasNoMatches)
        }

    @Test
    fun typing_filters_the_list_by_title() =
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
    fun typing_also_finds_a_chat_by_its_agents_name() =
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
    fun a_secret_chat_is_never_a_search_result() =
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
    fun a_query_that_matches_nothing_is_not_the_same_as_having_no_chats() =
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
    fun cancelling_the_search_brings_the_whole_list_back() =
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
    fun a_chat_whose_agent_is_gone_still_lists_and_still_searches_by_title() =
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
    fun an_unreachable_hub_says_so_rather_than_looking_like_an_empty_household() =
        runTest(testDispatcher) {
            everySuspend { listSessions() } throws ServerOfflineException("Can't reach your hub")

            viewModel.load()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("Can't reach your hub", state.errorMessage)
            assertEquals(false, state.isEmpty, "no chats loaded is not the same as no chats existing")
        }

    @Test
    fun loading_again_replaces_the_rows_rather_than_adding_to_them() =
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
