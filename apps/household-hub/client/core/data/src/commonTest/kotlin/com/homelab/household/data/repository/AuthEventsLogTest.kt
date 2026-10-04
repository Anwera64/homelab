package com.homelab.household.data.repository

import co.touchlab.kermit.Severity
import com.homelab.household.data.datasource.local.AuthEventsLocalDataSource
import com.homelab.household.data.datasource.local.InMemorySessionStorage
import com.homelab.household.data.datasource.remote.`interface`.AuthRemoteDataSource
import com.homelab.household.data.dto.TokenResponseDto
import com.homelab.household.data.dto.UserReadDto
import com.homelab.household.data.network.HubConfig
import com.homelab.household.data.telemetry.RecordingLogWriter
import com.homelab.household.data.telemetry.RecordingLogWriter.Line
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Signing in and out are logged, and those lines leave the phone. They say that it happened and
 * never who: the collector works out the member from the token the batch arrives with.
 */
class AuthEventsLogTest {
    private val emmaDto =
        UserReadDto(
            id = "emma",
            full_name = "Emma",
            is_admin = true,
            is_active = true,
            personal_space_id = "sp-1",
            avatar_color = "#3C6E4E",
            created_at = "2026-09-13T00:00:00Z",
        )

    private fun signedIn(token: String = "jwt-token-123") =
        TokenResponseDto(access_token = token, token_type = "bearer", user = emmaDto)

    private fun repository(
        remote: AuthRemoteDataSource,
        writer: RecordingLogWriter,
        tokens: InMemorySessionStorage = InMemorySessionStorage(),
    ) = AuthRepositoryImpl(
        remote = remote,
        storage = tokens,
        events = AuthEventsLocalDataSource(),
        hubConfig = HubConfig(baseUrl = "https://hub.test.local:8443"),
        log = writer.logger("Auth"),
    )

    @Test
    fun `GIVEN a hub that accepts the PIN WHEN signing in THEN it is logged once without saying who or with what`() =
        runTest {
            // GIVEN
            val writer = RecordingLogWriter()
            val remote = mock<AuthRemoteDataSource>()
            everySuspend { remote.login("emma", "482913") } returns signedIn()

            // WHEN
            repository(remote, writer).login("emma", "482913")

            // THEN
            assertEquals(listOf(Line(Severity.Info, "Auth", "Signed in")), writer.lines)
        }

    @Test
    fun `GIVEN a new member joining with an invite WHEN the hub accepts THEN it is logged as a sign-in`() =
        runTest {
            // GIVEN
            val writer = RecordingLogWriter()
            val remote = mock<AuthRemoteDataSource>()
            everySuspend { remote.joinHousehold("maple-otter", "Emma", "482913", "#3C6E4E") } returns signedIn()

            // WHEN
            repository(remote, writer).joinHousehold("maple-otter", "Emma", "482913", "#3C6E4E")

            // THEN
            assertEquals(listOf(Line(Severity.Info, "Auth", "Signed in")), writer.lines)
        }

    @Test
    fun `GIVEN a signed-in phone WHEN its token is renewed THEN nothing is logged as a sign-in`() =
        runTest {
            // GIVEN
            val writer = RecordingLogWriter()
            val remote = mock<AuthRemoteDataSource>()
            everySuspend { remote.renew("jwt-token-123") } returns signedIn(token = "jwt-token-456")
            val tokens = InMemorySessionStorage().apply { saveTokens("jwt-token-123") }

            // WHEN
            repository(remote, writer, tokens).refreshToken()

            // THEN
            assertTrue(writer.lines.isEmpty())
        }

    @Test
    fun `GIVEN a signed-in phone WHEN the member signs out THEN it is logged once`() =
        runTest {
            // GIVEN
            val writer = RecordingLogWriter()
            val tokens = InMemorySessionStorage().apply { saveTokens("jwt-token-123") }

            // WHEN
            repository(mock<AuthRemoteDataSource>(), writer, tokens).logout()

            // THEN
            assertEquals(listOf(Line(Severity.Info, "Auth", "Signed out")), writer.lines)
        }

    @Test
    fun `GIVEN the hub stops accepting the token WHEN that is raised THEN it is logged as a warning apart from an ordinary sign-out`() {
        // GIVEN
        val writer = RecordingLogWriter()
        val events = AuthEventsLocalDataSource(log = writer.logger("Auth"))

        // WHEN
        events.raiseSignedOut()

        // THEN
        assertEquals(listOf(Line(Severity.Warn, "Auth", "Signed out by the hub")), writer.lines)
        assertFalse(writer.lines.any { "emma" in it.message.lowercase() })
    }
}
