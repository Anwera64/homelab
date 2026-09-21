package com.homelab.household.data.repository

import app.cash.turbine.test
import com.homelab.household.data.datasource.local.AuthSessionLocalDataSource
import com.homelab.household.data.datasource.local.InMemoryTokenStorage
import com.homelab.household.data.datasource.remote.`interface`.AuthRemoteDataSource
import com.homelab.household.data.dto.AuthStatusDto
import com.homelab.household.data.dto.InvitePreviewReadDto
import com.homelab.household.data.dto.MemberProfileDto
import com.homelab.household.data.dto.TokenResponseDto
import com.homelab.household.data.dto.UserReadDto
import com.homelab.household.data.network.HubConfig
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.exception.UnauthorizedException
import com.homelab.household.domain.exception.UpstreamGatewayException
import com.homelab.household.domain.exception.WrongPinException
import com.homelab.household.domain.model.Member
import com.homelab.household.domain.util.runCatchingSafe
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifyNoMoreCalls
import dev.mokkery.verifySuspend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * What the repository is, now that it does not do the call: it maps DTOs to domain models, decides
 * what is kept on the phone, and is the single-flight around renewal. The hub is a mock — so there
 * is no engine, no HTTP and nothing to race, and `runTest`'s clock is the only clock.
 */
class AuthRepositoryTest {

    private val emmaDto = UserReadDto(
        id = "emma",
        full_name = "Emma",
        is_admin = true,
        is_active = true,
        personal_space_id = "sp-1",
        avatar_color = "#3C6E4E",
        created_at = "2026-09-13T00:00:00Z",
    )

    private fun signedIn(token: String = "jwt-token-123", user: UserReadDto? = emmaDto) =
        TokenResponseDto(access_token = token, token_type = "bearer", user = user)

    private fun repository(
        remote: AuthRemoteDataSource,
        tokens: InMemoryTokenStorage = InMemoryTokenStorage(),
        session: AuthSessionLocalDataSource = AuthSessionLocalDataSource(),
    ) = AuthRepositoryImpl(
        remote = remote,
        tokenStorage = tokens,
        session = session,
        hubConfig = HubConfig(baseUrl = "https://hub.test.local:8443"),
    )

    // ---- signing in --------------------------------------------------------

    @Test
    fun `GIVEN a hub that accepts the PIN WHEN signing in THEN the member comes back and the token is kept`() = runTest {
        // GIVEN
        val remote = mock<AuthRemoteDataSource>()
        everySuspend { remote.login("emma", "482913") } returns signedIn()
        val tokens = InMemoryTokenStorage()

        // WHEN
        val user = repository(remote, tokens).login("emma", "482913")

        // THEN
        assertEquals("emma", user.id)
        assertEquals("Emma", user.fullName)
        assertEquals("jwt-token-123", tokens.getAccessToken())
    }

    @Test
    fun `GIVEN the hub refuses the PIN WHEN signing in THEN the refusal reaches the caller and no token is kept`() = runTest {
        // GIVEN
        val remote = mock<AuthRemoteDataSource>()
        everySuspend { remote.login("emma", "000000") } throws WrongPinException(attemptsLeft = 3)
        val tokens = InMemoryTokenStorage()

        // WHEN
        val thrown = assertFailsWith<WrongPinException> { repository(remote, tokens).login("emma", "000000") }

        // THEN
        assertEquals(3, thrown.attemptsLeft)
        assertNull(tokens.getAccessToken())
    }

    @Test
    fun `GIVEN a hub that hands back a token without saying who signed in WHEN signing in THEN it is a bad answer from upstream`() = runTest {
        // GIVEN
        val remote = mock<AuthRemoteDataSource>()
        everySuspend { remote.login("emma", "482913") } returns signedIn(user = null)

        // WHEN / THEN
        assertFailsWith<UpstreamGatewayException> { repository(remote).login("emma", "482913") }
    }

    @Test
    fun `GIVEN a member who has just signed in WHEN the current member is observed THEN it is them`() = runTest {
        // GIVEN
        val remote = mock<AuthRemoteDataSource>()
        everySuspend { remote.login("emma", "482913") } returns signedIn()
        val repository = repository(remote)

        // WHEN
        repository.login("emma", "482913")

        // THEN
        assertEquals("emma", repository.observeCurrentUser().first()?.id)
    }

    // ---- first run ---------------------------------------------------------

    @Test
    fun `GIVEN an empty hub WHEN the first member onboards THEN they come back and the token is kept`() = runTest {
        // GIVEN
        val remote = mock<AuthRemoteDataSource>()
        everySuspend { remote.onboard("Emma", "482913", "#C05638") } returns signedIn(token = "first-token")
        val tokens = InMemoryTokenStorage()

        // WHEN
        val user = repository(remote, tokens).onboard("Emma", "482913", "#C05638")

        // THEN
        assertEquals("emma", user.id)
        assertEquals("first-token", tokens.getAccessToken())
    }

    // ---- joining and PIN resets -------------------------------------------

    @Test
    fun `GIVEN a live invite code WHEN it is redeemed THEN the joiner comes back signed in`() = runTest {
        // GIVEN
        val remote = mock<AuthRemoteDataSource>()
        everySuspend { remote.joinHousehold("482913", "Liam", "112233", "#C05638") } returns signedIn(token = "joined")
        val tokens = InMemoryTokenStorage()

        // WHEN
        val user = repository(remote, tokens).joinHousehold("482913", "Liam", "112233", "#C05638")

        // THEN
        assertEquals("emma", user.id)
        assertEquals("joined", tokens.getAccessToken())
    }

    @Test
    fun `GIVEN a live reset code WHEN it is redeemed THEN the member comes back signed in`() = runTest {
        // GIVEN
        val remote = mock<AuthRemoteDataSource>()
        everySuspend { remote.redeemPinReset("K7M2QP", "998877") } returns signedIn(token = "reset")
        val tokens = InMemoryTokenStorage()

        // WHEN
        val user = repository(remote, tokens).redeemPinReset("K7M2QP", "998877")

        // THEN
        assertEquals("emma", user.id)
        assertEquals("reset", tokens.getAccessToken())
    }

    @Test
    fun `GIVEN an invite the hub knows WHEN it is looked up THEN who invited whom is mapped for the UI`() = runTest {
        // GIVEN
        val remote = mock<AuthRemoteDataSource>()
        everySuspend { remote.lookUpInvite("482913") } returns InvitePreviewReadDto(
            invited_name = "Liam",
            inviter_name = "Emma",
            inviter_avatar_color = "#3C6E4E",
        )

        // WHEN
        val preview = repository(remote).lookUpInvite("482913")

        // THEN
        assertEquals("Liam", preview.invitedName)
        assertEquals("Emma", preview.inviterName)
        assertEquals("#3C6E4E", preview.inviterAvatarColor)
    }

    // ---- reading the household --------------------------------------------

    @Test
    fun `GIVEN the hub lists two profiles WHEN the members are asked for THEN they are mapped to members`() = runTest {
        // GIVEN
        val remote = mock<AuthRemoteDataSource>()
        everySuspend { remote.listMembers() } returns listOf(
            MemberProfileDto(id = "emma", full_name = "Emma", avatar_color = "#3C6E4E"),
            MemberProfileDto(id = "liam", full_name = "Liam", avatar_color = "#C05638"),
        )

        // WHEN
        val members = repository(remote).listMembers()

        // THEN
        assertEquals(listOf(Member("emma", "Emma", "#3C6E4E"), Member("liam", "Liam", "#C05638")), members)
    }

    @Test
    fun `GIVEN the hub reports itself set up with four members WHEN its status is asked for THEN that is what comes back`() = runTest {
        // GIVEN
        val remote = mock<AuthRemoteDataSource>()
        everySuspend { remote.checkStatus() } returns AuthStatusDto(is_initialized = true, member_count = 4)

        // WHEN
        val status = repository(remote).checkStatus()

        // THEN
        assertEquals(true, status.isInitialized)
        assertEquals(4, status.memberCount)
    }

    // ---- who is signed in --------------------------------------------------

    @Test
    fun `GIVEN the signed-in member is already known WHEN they are asked for THEN the hub is not asked at all`() = runTest {
        // GIVEN
        val remote = mock<AuthRemoteDataSource>()
        everySuspend { remote.login("emma", "482913") } returns signedIn()
        val repository = repository(remote)
        repository.login("emma", "482913")

        // WHEN
        val user = repository.getCurrentUser()

        // THEN
        assertEquals("emma", user?.id)
        verifySuspend(VerifyMode.exactly(0)) { remote.fetchCurrentUser() }
    }

    @Test
    fun `GIVEN no token kept on this phone WHEN the signed-in member is asked for THEN nobody is signed in and the hub is not asked`() = runTest {
        // GIVEN
        val remote = mock<AuthRemoteDataSource>(MockMode.autofill)

        // WHEN
        val user = repository(remote).getCurrentUser()

        // THEN
        assertNull(user)
        verifyNoMoreCalls(remote)
    }

    @Test
    fun `GIVEN a token kept from last time WHEN the signed-in member is asked for THEN the hub is asked once and the answer is kept`() = runTest {
        // GIVEN
        val remote = mock<AuthRemoteDataSource>()
        everySuspend { remote.fetchCurrentUser() } returns emmaDto
        val repository = repository(remote, InMemoryTokenStorage().apply { saveTokens("token-from-last-time") })

        // WHEN
        val first = repository.getCurrentUser()
        val second = repository.getCurrentUser()

        // THEN
        assertEquals("emma", first?.id)
        assertEquals("emma", second?.id)
        verifySuspend(VerifyMode.exactly(1)) { remote.fetchCurrentUser() }
    }

    @Test
    fun `GIVEN a kept token the hub will not answer for WHEN the signed-in member is asked for THEN nobody is signed in rather than an error`() = runTest {
        // GIVEN
        val remote = mock<AuthRemoteDataSource>()
        everySuspend { remote.fetchCurrentUser() } throws ServerOfflineException()
        val repository = repository(remote, InMemoryTokenStorage().apply { saveTokens("token-from-last-time") })

        // WHEN
        val user = repository.getCurrentUser()

        // THEN
        assertNull(user)
    }

    @Test
    fun `GIVEN a token kept on this phone WHEN a stored session is asked about THEN it says yes without asking the hub`() = runTest {
        // GIVEN
        val remote = mock<AuthRemoteDataSource>(MockMode.autofill)
        val tokens = InMemoryTokenStorage()
        val repository = repository(remote, tokens)

        // WHEN
        val beforeSignIn = repository.hasStoredSession()
        tokens.saveTokens("token-from-last-time")
        val afterSignIn = repository.hasStoredSession()

        // THEN
        assertEquals(false, beforeSignIn)
        assertEquals(true, afterSignIn)
        verifyNoMoreCalls(remote)
    }

    @Test
    fun `GIVEN a signed-in member WHEN they sign out THEN the token and who they were are both forgotten`() = runTest {
        // GIVEN
        val remote = mock<AuthRemoteDataSource>()
        everySuspend { remote.login("emma", "482913") } returns signedIn()
        val tokens = InMemoryTokenStorage()
        val repository = repository(remote, tokens)
        repository.login("emma", "482913")

        // WHEN
        repository.logout()

        // THEN
        assertNull(tokens.getAccessToken())
        assertNull(repository.observeCurrentUser().first())
    }

    @Test
    fun `GIVEN the hub has stopped accepting this phone WHEN the sign-out is raised THEN it is announced and who was signed in is forgotten`() = runTest {
        // GIVEN
        val remote = mock<AuthRemoteDataSource>()
        everySuspend { remote.login("emma", "482913") } returns signedIn()
        val session = AuthSessionLocalDataSource()
        val repository = repository(remote, session = session)
        repository.login("emma", "482913")

        // WHEN
        repository.observeSignedOut().test {
            session.raiseSignedOut()

            // THEN
            awaitItem()
            assertNull(repository.observeCurrentUser().first())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- renewing ----------------------------------------------------------

    @Test
    fun `GIVEN a token kept on this phone WHEN it is renewed THEN the kept one is handed to the hub and the fresh one replaces it`() = runTest {
        // GIVEN
        val remote = mock<AuthRemoteDataSource>()
        everySuspend { remote.renew("kept-token") } returns signedIn(token = "fresh-token")
        val tokens = InMemoryTokenStorage().apply { saveTokens("kept-token") }
        val repository = repository(remote, tokens)

        // WHEN
        val fresh = repository.refreshToken()

        // THEN
        assertEquals("fresh-token", fresh)
        assertEquals("fresh-token", tokens.getAccessToken())
        assertEquals("emma", repository.observeCurrentUser().first()?.id)
    }

    /**
     * The renewal is held open until all five callers have asked, which is the only way to test a
     * single-flight at all: if the hub answers without suspending, caller one finishes before caller
     * two starts and there is no contention to coalesce. The version of this test that went through
     * MockEngine never said so — it passed because the engine happened to suspend, not because the
     * overlap was arranged. Here the gate arranges it, and the test fails if the guard is removed.
     */
    @Test
    fun `GIVEN five callers asking to renew at once WHEN they all ask THEN the hub is asked once and all five get the same token`() = runTest {
        // GIVEN
        val hubIsAnswering = CompletableDeferred<Unit>()
        val remote = mock<AuthRemoteDataSource>()
        everySuspend { remote.renew("kept-token") } calls {
            hubIsAnswering.await()
            signedIn(token = "fresh-token")
        }
        val tokens = InMemoryTokenStorage().apply { saveTokens("kept-token") }
        val repository = repository(remote, tokens)

        // WHEN
        val callers = (1..5).map { async { repository.refreshToken() } }
        runCurrent()
        hubIsAnswering.complete(Unit)
        val results = callers.awaitAll()

        // THEN
        assertEquals(List(5) { "fresh-token" }, results)
        verifySuspend(VerifyMode.exactly(1)) { remote.renew("kept-token") }
        assertEquals("fresh-token", tokens.getAccessToken())
    }

    @Test
    fun `GIVEN a renewal the hub refuses WHEN five callers asked together THEN the refusal reaches every one of them`() = runTest {
        // GIVEN
        val hubIsAnswering = CompletableDeferred<Unit>()
        val remote = mock<AuthRemoteDataSource>()
        everySuspend { remote.renew("revoked-token") } calls {
            hubIsAnswering.await()
            throw UnauthorizedException("no longer accepted")
        }
        val repository = repository(remote, InMemoryTokenStorage().apply { saveTokens("revoked-token") })

        // WHEN
        val callers = (1..5).map { async { runCatchingSafe { repository.refreshToken() } } }
        runCurrent()
        hubIsAnswering.complete(Unit)
        val outcomes = callers.awaitAll()

        // THEN
        assertEquals(5, outcomes.count { it.exceptionOrNull() is UnauthorizedException })
        verifySuspend(VerifyMode.exactly(1)) { remote.renew("revoked-token") }
    }

    @Test
    fun `GIVEN a renewal that has already finished WHEN another is asked for THEN the hub is asked again rather than handed the old answer`() = runTest {
        // GIVEN
        val remote = mock<AuthRemoteDataSource>()
        everySuspend { remote.renew(any()) } returns signedIn(token = "fresh-token")
        val repository = repository(remote, InMemoryTokenStorage().apply { saveTokens("kept-token") })

        // WHEN
        repository.refreshToken()
        repository.refreshToken()

        // THEN — the single flight lasts one renewal, not forever.
        verifySuspend(VerifyMode.exactly(2)) { remote.renew(any()) }
    }

    @Test
    fun `GIVEN the hub cannot be reached WHEN a token is renewed THEN the kept token is left alone`() = runTest {
        // GIVEN
        val remote = mock<AuthRemoteDataSource>()
        everySuspend { remote.renew("kept-token") } throws ServerOfflineException()
        val tokens = InMemoryTokenStorage().apply { saveTokens("kept-token") }

        // WHEN
        assertFailsWith<ServerOfflineException> { repository(remote, tokens).refreshToken() }

        // THEN
        assertEquals("kept-token", tokens.getAccessToken())
    }

    @Test
    fun `GIVEN nobody signed in on this phone WHEN a token is renewed THEN it is unauthorized without asking the hub`() = runTest {
        // GIVEN
        val remote = mock<AuthRemoteDataSource>(MockMode.autofill)

        // WHEN
        assertFailsWith<UnauthorizedException> { repository(remote).refreshToken() }

        // THEN
        verifyNoMoreCalls(remote)
    }

    // ---- where the hub is --------------------------------------------------

    @Test
    fun `GIVEN a hub address with a scheme and a port WHEN the host is asked for THEN only the host and port come back`() = runTest {
        // GIVEN
        val remote = mock<AuthRemoteDataSource>(MockMode.autofill)

        // WHEN
        val host = repository(remote).getHubHost()

        // THEN
        assertEquals("hub.test.local:8443", host)
    }
}
