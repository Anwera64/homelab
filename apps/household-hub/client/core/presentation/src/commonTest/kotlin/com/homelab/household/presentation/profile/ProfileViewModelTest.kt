package com.homelab.household.presentation.profile

import app.cash.turbine.test
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.CalendarConnection
import com.homelab.household.domain.model.CalendarProvider
import com.homelab.household.domain.model.HubTool
import com.homelab.household.domain.model.MemoryItem
import com.homelab.household.domain.model.MemoryOrigin
import com.homelab.household.domain.model.MemoryPage
import com.homelab.household.domain.model.MemoryScope
import com.homelab.household.domain.model.ToolAction
import com.homelab.household.domain.model.ToolApproval
import com.homelab.household.domain.model.User
import com.homelab.household.domain.usecase.GetCalendarUseCase
import com.homelab.household.domain.usecase.GetCurrentUserUseCase
import com.homelab.household.domain.usecase.GetMemoryPageUseCase
import com.homelab.household.domain.usecase.GetToolApprovalsUseCase
import com.homelab.household.domain.usecase.ListHouseholdMembersUseCase
import com.homelab.household.domain.usecase.LogoutUseCase
import com.homelab.household.domain.usecase.RemoveCalendarUseCase
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Your own account, and the one thing it cannot do: leave while you are the only admin. */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class ProfileViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val getCurrentUser = mock<GetCurrentUserUseCase>()
    private val listHouseholdMembers = mock<ListHouseholdMembersUseCase>()
    private val logout = mock<LogoutUseCase>()
    private val getCalendar = mock<GetCalendarUseCase>()
    private val removeCalendar = mock<RemoveCalendarUseCase>()
    private val getToolApprovals = mock<GetToolApprovalsUseCase>()
    private val getMemoryPage = mock<GetMemoryPageUseCase>()

    /** Four minutes after the hub last checked the calendar below. */
    private val clock =
        object : Clock {
            override fun now(): Instant = Instant.parse("2026-09-26T20:08:00Z")
        }

    private val icloud =
        CalendarConnection(
            provider = CalendarProvider.APPLE,
            account = "emma@icloud.com",
            server = "https://caldav.icloud.com",
            calendarName = "Default",
            connectedAt = "2026-09-26T20:04:00Z",
        )

    private val emma = User(id = "emma", fullName = "Emma", isAdmin = true, isActive = true)
    private val liam = User(id = "liam", fullName = "Liam", isAdmin = false, isActive = true)
    private val noor = User(id = "noor", fullName = "Noor", isAdmin = true, isActive = true)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        ProfileViewModel(
            getCurrentUser,
            listHouseholdMembers,
            logout,
            getCalendar,
            removeCalendar,
            getToolApprovals,
            getMemoryPage,
            clock,
        )

    @Test
    fun `GIVEN the only admin in the household WHEN the profile loads THEN the member shows and the sole admin flag is set`() =
        runTest(testDispatcher) {
            everySuspend { getCurrentUser() } returns emma
            everySuspend { listHouseholdMembers() } returns listOf(emma, liam)

            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(emma, viewModel.uiState.value.member)
            assertEquals(true, viewModel.uiState.value.isSoleAdmin)
            assertEquals(ProfileStatus.Ready, viewModel.uiState.value.status)
        }

    @Test
    fun `GIVEN an admin with another admin in the household WHEN the profile loads THEN the sole admin flag is not set`() =
        runTest(testDispatcher) {
            everySuspend { getCurrentUser() } returns emma
            everySuspend { listHouseholdMembers() } returns listOf(emma, noor)

            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(false, viewModel.uiState.value.isSoleAdmin)
        }

    @Test
    fun `GIVEN a member who is not an admin WHEN the profile loads THEN the sole admin flag is not set`() =
        runTest(testDispatcher) {
            everySuspend { getCurrentUser() } returns liam
            everySuspend { listHouseholdMembers() } returns listOf(emma, liam)

            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(false, viewModel.uiState.value.isSoleAdmin)
        }

    /**
     * Offline, the member comes off the phone and the household does not. Losing the member with the
     * household is what drew an empty circle where a name and a colour belong.
     */
    @Test
    fun `GIVEN a household that cannot be listed WHEN the profile loads THEN the member is still shown and the status says unreachable`() =
        runTest(testDispatcher) {
            everySuspend { getCurrentUser() } returns emma
            everySuspend { listHouseholdMembers() } throws ServerOfflineException()

            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(emma, viewModel.uiState.value.member)
            assertEquals(ProfileStatus.Unreachable, viewModel.uiState.value.status)
        }

    /** Nobody can be promoted offline either, so the question is left alone rather than guessed. */
    @Test
    fun `GIVEN a household that cannot be listed WHEN the profile loads THEN the sole admin question is left unanswered`() =
        runTest(testDispatcher) {
            everySuspend { getCurrentUser() } returns emma
            everySuspend { listHouseholdMembers() } throws ServerOfflineException()

            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(false, viewModel.uiState.value.isSoleAdmin)
        }

    @Test
    fun `GIVEN an unreachable hub WHEN the profile loads THEN the status says unreachable`() =
        runTest(testDispatcher) {
            everySuspend { getCurrentUser() } throws ServerOfflineException()
            val viewModel = viewModel()
            advanceUntilIdle()
            assertEquals(ProfileStatus.Unreachable, viewModel.uiState.value.status)
        }

    @Test
    fun `GIVEN an unreachable hub that has since come back WHEN loading again THEN the status is ready`() =
        runTest(testDispatcher) {
            everySuspend { getCurrentUser() } throws ServerOfflineException()
            val viewModel = viewModel()
            advanceUntilIdle()
            everySuspend { getCurrentUser() } returns emma
            everySuspend { listHouseholdMembers() } returns listOf(emma, liam)
            viewModel.load()
            advanceUntilIdle()

            assertEquals(ProfileStatus.Ready, viewModel.uiState.value.status)
        }

    @Test
    fun `GIVEN a loaded profile WHEN signing out THEN the token is forgotten and the screen says signed out`() =
        runTest(testDispatcher) {
            everySuspend { getCurrentUser() } returns emma
            everySuspend { listHouseholdMembers() } returns listOf(emma, liam)
            everySuspend { logout() } returns Unit
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.events.test {
                viewModel.onSignOut()
                advanceUntilIdle()

                assertEquals(ProfileEvent.SignedOut, awaitItem())
            }
            verifySuspend(VerifyMode.exactly(1)) { logout() }
        }

    @Test
    fun `GIVEN a connected calendar WHEN the calendar loads THEN the row shows its account and how long since it was checked`() =
        runTest(testDispatcher) {
            everySuspend { getCurrentUser() } returns emma
            everySuspend { listHouseholdMembers() } returns listOf(emma, liam)
            everySuspend { getCalendar() } returns icloud
            val viewModel = viewModel()

            viewModel.loadCalendar()
            advanceUntilIdle()

            assertEquals(
                CalendarRow.Connected(provider = CalendarProvider.APPLE, account = "emma@icloud.com", minutesAgo = 4),
                viewModel.uiState.value.calendar,
            )
        }

    @Test
    fun `GIVEN no calendar WHEN the calendar loads THEN the row offers to connect one`() =
        runTest(testDispatcher) {
            everySuspend { getCurrentUser() } returns emma
            everySuspend { listHouseholdMembers() } returns listOf(emma, liam)
            everySuspend { getCalendar() } returns null
            val viewModel = viewModel()

            viewModel.loadCalendar()
            advanceUntilIdle()

            assertEquals(CalendarRow.None, viewModel.uiState.value.calendar)
        }

    /** Coming back from connecting one is what asks again, so the new calendar shows at once. */
    @Test
    fun `GIVEN a calendar connected since the last check WHEN the calendar loads again THEN the row shows the new calendar`() =
        runTest(testDispatcher) {
            everySuspend { getCurrentUser() } returns emma
            everySuspend { listHouseholdMembers() } returns listOf(emma, liam)
            everySuspend { getCalendar() } returns null
            val viewModel = viewModel()
            viewModel.loadCalendar()
            advanceUntilIdle()

            everySuspend { getCalendar() } returns icloud.copy(connectedAt = "2026-09-26T20:08:00Z")
            viewModel.loadCalendar()
            advanceUntilIdle()

            assertEquals(
                CalendarRow.Connected(provider = CalendarProvider.APPLE, account = "emma@icloud.com", minutesAgo = 0),
                viewModel.uiState.value.calendar,
            )
        }

    @Test
    fun `GIVEN a hub that cannot say about the calendar WHEN the calendar loads THEN the row makes no claim`() =
        runTest(testDispatcher) {
            everySuspend { getCurrentUser() } returns emma
            everySuspend { listHouseholdMembers() } returns listOf(emma, liam)
            everySuspend { getCalendar() } throws ServerOfflineException()
            val viewModel = viewModel()

            viewModel.loadCalendar()
            advanceUntilIdle()

            assertEquals(CalendarRow.Unknown, viewModel.uiState.value.calendar)
        }

    @Test
    fun `GIVEN a connected calendar WHEN it is disconnected THEN the hub removes it and the row offers to connect one`() =
        runTest(testDispatcher) {
            val viewModel = connectedToIcloud()
            everySuspend { removeCalendar() } returns Unit

            viewModel.onDisconnectCalendar()
            advanceUntilIdle()

            verifySuspend(VerifyMode.exactly(1)) { removeCalendar() }
            assertEquals(CalendarRow.None, viewModel.uiState.value.calendar)
            assertEquals(CalendarDisconnect.Idle, viewModel.uiState.value.calendarDisconnect)
        }

    @Test
    fun `GIVEN the hub can't be reached WHEN the calendar is disconnected THEN it stays connected and says so`() =
        runTest(testDispatcher) {
            val viewModel = connectedToIcloud()
            everySuspend { removeCalendar() } throws ServerOfflineException()

            viewModel.onDisconnectCalendar()
            advanceUntilIdle()

            assertEquals(
                CalendarRow.Connected(provider = CalendarProvider.APPLE, account = "emma@icloud.com", minutesAgo = 4),
                viewModel.uiState.value.calendar,
            )
            assertEquals(CalendarDisconnect.Unreachable, viewModel.uiState.value.calendarDisconnect)
        }

    @Test
    fun `GIVEN the hub is still answering WHEN the calendar is being disconnected THEN it shows it's busy`() =
        runTest(testDispatcher) {
            val viewModel = connectedToIcloud()
            val hubAnswers = CompletableDeferred<Unit>()
            everySuspend { removeCalendar() } calls { hubAnswers.await() }

            viewModel.onDisconnectCalendar()
            runCurrent()

            assertEquals(CalendarDisconnect.Disconnecting, viewModel.uiState.value.calendarDisconnect)
            hubAnswers.complete(Unit)
        }

    @Test
    fun `GIVEN Google stopped accepting the sign-in WHEN the calendar is loaded THEN the row asks to sign in again`() =
        runTest(testDispatcher) {
            everySuspend { getCurrentUser() } returns emma
            everySuspend { listHouseholdMembers() } returns listOf(emma, liam)
            everySuspend { getCalendar() } returns
                icloud.copy(provider = CalendarProvider.GOOGLE, account = "emma@gmail.com", needsReconnect = true)
            val viewModel = viewModel()

            viewModel.loadCalendar()
            advanceUntilIdle()

            assertEquals(
                CalendarRow.Connected(
                    provider = CalendarProvider.GOOGLE,
                    account = "emma@gmail.com",
                    minutesAgo = 4,
                    needsSignInAgain = true,
                ),
                viewModel.uiState.value.calendar,
            )
        }

    private suspend fun TestScope.connectedToIcloud(): ProfileViewModel {
        everySuspend { getCurrentUser() } returns emma
        everySuspend { listHouseholdMembers() } returns listOf(emma, liam)
        everySuspend { getCalendar() } returns icloud
        return viewModel().also {
            it.loadCalendar()
            advanceUntilIdle()
        }
    }

    private fun writes(vararg automatic: ToolAction) =
        listOf(ToolAction.Create, ToolAction.Update, ToolAction.Delete).map {
            ToolApproval(HubTool.CALENDAR_WRITE, it, automatic = it in automatic, alwaysAsks = it == ToolAction.Delete)
        } +
            ToolApproval(HubTool.DOCUMENT_WRITER, ToolAction.Create, automatic = ToolAction.Append in automatic)

    @Test
    fun `GIVEN two writes that run without asking WHEN the agents row loads THEN it counts two`() =
        runTest(testDispatcher) {
            everySuspend { getCurrentUser() } returns emma
            everySuspend { listHouseholdMembers() } returns listOf(emma, noor)
            everySuspend { getToolApprovals() } returns writes(ToolAction.Create, ToolAction.Update)
            val viewModel = viewModel()
            assertEquals(null, viewModel.uiState.value.automaticWrites)

            viewModel.loadAutomaticWrites()
            advanceUntilIdle()

            assertEquals(2, viewModel.uiState.value.automaticWrites)
        }

    @Test
    fun `GIVEN no writes that run without asking WHEN the agents row loads THEN it counts none`() =
        runTest(testDispatcher) {
            everySuspend { getCurrentUser() } returns emma
            everySuspend { listHouseholdMembers() } returns listOf(emma, noor)
            everySuspend { getToolApprovals() } returns writes()
            val viewModel = viewModel()
            viewModel.loadAutomaticWrites()
            advanceUntilIdle()
            assertEquals(0, viewModel.uiState.value.automaticWrites)
        }

    @Test
    fun `GIVEN a count of none and a write switched on since WHEN coming back to the profile THEN it counts again`() =
        runTest(testDispatcher) {
            everySuspend { getCurrentUser() } returns emma
            everySuspend { listHouseholdMembers() } returns listOf(emma, noor)
            everySuspend { getToolApprovals() } returns writes()
            val viewModel = viewModel()
            viewModel.loadAutomaticWrites()
            advanceUntilIdle()
            everySuspend { getToolApprovals() } returns writes(ToolAction.Create)
            viewModel.loadAutomaticWrites()
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.automaticWrites)
        }

    @Test
    fun `GIVEN a hub that cannot give the count WHEN the agents row loads THEN it makes no claim`() =
        runTest(testDispatcher) {
            everySuspend { getCurrentUser() } returns emma
            everySuspend { listHouseholdMembers() } returns listOf(emma, noor)
            everySuspend { getToolApprovals() } throws ServerOfflineException("offline")
            val viewModel = viewModel()

            viewModel.loadAutomaticWrites()
            advanceUntilIdle()

            assertEquals(null, viewModel.uiState.value.automaticWrites)
        }

    @Test
    fun `GIVEN memories on the hub WHEN the profile is shown THEN the Memories row counts yours and the household's`() =
        runTest(testDispatcher) {
            everySuspend { getCurrentUser() } returns emma
            everySuspend { listHouseholdMembers() } returns listOf(emma, noor)
            everySuspend { getMemoryPage() } returns
                MemoryPage(yours = List(27) { memoryItem("p$it") }, household = List(14) { memoryItem("h$it") })
            val viewModel = viewModel()
            assertEquals(null, viewModel.uiState.value.memoryCounts)

            viewModel.loadMemoryCounts()
            advanceUntilIdle()

            assertEquals(MemoryCounts(yours = 27, household = 14), viewModel.uiState.value.memoryCounts)
        }

    @Test
    fun `GIVEN changes waiting for the member WHEN the profile is shown THEN the Memories row counts them`() =
        runTest(testDispatcher) {
            everySuspend { getCurrentUser() } returns emma
            everySuspend { listHouseholdMembers() } returns listOf(emma, noor)
            everySuspend { getMemoryPage() } returns
                MemoryPage(yours = emptyList(), household = emptyList(), toCheck = 2)
            val viewModel = viewModel()

            viewModel.loadMemoryCounts()
            advanceUntilIdle()

            assertEquals(MemoryCounts(yours = 0, household = 0, toCheck = 2), viewModel.uiState.value.memoryCounts)
        }

    @Test
    fun `GIVEN the hub can't count memories WHEN the profile is shown THEN the row makes no claim`() =
        runTest(testDispatcher) {
            everySuspend { getCurrentUser() } returns emma
            everySuspend { listHouseholdMembers() } returns listOf(emma, noor)
            everySuspend { getMemoryPage() } throws ServerOfflineException("offline")
            val viewModel = viewModel()

            viewModel.loadMemoryCounts()
            advanceUntilIdle()

            assertEquals(null, viewModel.uiState.value.memoryCounts)
        }

    private fun memoryItem(id: String) =
        MemoryItem(
            id = id,
            content = id,
            scope = MemoryScope.PERSONAL,
            origin = MemoryOrigin.Reflection,
            createdAt = Instant.parse("2026-10-15T09:00:00Z"),
            expiresAt = null,
            speakerId = "emma",
            speakerName = "Emma",
            isMine = true,
            agentName = "Coordinator",
            agentAvatar = "🏡",
            canRemove = true,
        )
}
