package com.homelab.household.data.repository

import com.homelab.household.data.datasource.local.InMemorySessionStorage
import com.homelab.household.data.datasource.remote.`interface`.MembersRemoteDataSource
import com.homelab.household.data.dto.InviteReadDto
import com.homelab.household.data.dto.PinResetReadDto
import com.homelab.household.data.dto.TokenResponseDto
import com.homelab.household.data.dto.UserReadDto
import com.homelab.household.domain.exception.NameTakenException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.exception.SoleAdminException
import com.homelab.household.domain.exception.WrongPinException
import com.homelab.household.domain.model.Invite
import com.homelab.household.domain.model.ResetCode
import com.homelab.household.domain.model.User
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * The members repository maps DTOs to domain models, keeps the new token a changed PIN hands back,
 * and forgets this phone's session once its member has left. Everything else it does is pass a
 * refusal along untouched.
 */
class MembersRepositoryTest {
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

    private fun repository(
        remote: MembersRemoteDataSource,
        tokens: InMemorySessionStorage = InMemorySessionStorage(),
    ) = MembersRepositoryImpl(remote = remote, storage = tokens)

    // ---- listing -----------------------------------------------------------

    @Test
    fun `GIVEN the hub lists one household member WHEN they are asked for THEN the profile is mapped to a user`() =
        runTest {
            // GIVEN
            val remote = mock<MembersRemoteDataSource>()
            everySuspend { remote.listHouseholdMembers() } returns listOf(emmaDto)

            // WHEN
            val members = repository(remote).listHouseholdMembers()

            // THEN
            assertEquals(
                listOf(
                    User(
                        id = "emma",
                        fullName = "Emma",
                        isAdmin = true,
                        isActive = true,
                        personalSpaceId = "sp-1",
                        avatarColor = "#3C6E4E",
                        createdAt = "2026-09-13T00:00:00Z",
                    ),
                ),
                members,
            )
        }

    @Test
    fun `GIVEN the hub cannot be reached WHEN household members are asked for THEN the failure reaches the caller`() =
        runTest {
            // GIVEN
            val remote = mock<MembersRemoteDataSource>()
            everySuspend { remote.listHouseholdMembers() } throws ServerOfflineException()

            // WHEN / THEN
            assertFailsWith<ServerOfflineException> { repository(remote).listHouseholdMembers() }
        }

    // ---- inviting ----------------------------------------------------------

    @Test
    fun `GIVEN the hub issues an invite code WHEN someone is invited THEN it is mapped to an invite`() =
        runTest {
            // GIVEN
            val remote = mock<MembersRemoteDataSource>()
            everySuspend { remote.createInvite("Liam", false) } returns
                InviteReadDto(
                    code = "482913",
                    invited_name = "Liam",
                    is_admin = false,
                    expires_in_seconds = 900,
                )

            // WHEN
            val invite = repository(remote).createInvite("Liam", isAdmin = false)

            // THEN
            assertEquals(Invite(code = "482913", invitedName = "Liam", isAdmin = false, expiresInSeconds = 900), invite)
        }

    @Test
    fun `GIVEN a name the household already has WHEN someone is invited THEN the refusal reaches the caller`() =
        runTest {
            // GIVEN
            val remote = mock<MembersRemoteDataSource>()
            everySuspend { remote.createInvite("Emma", false) } throws NameTakenException()

            // WHEN / THEN
            assertFailsWith<NameTakenException> { repository(remote).createInvite("Emma", isAdmin = false) }
        }

    // ---- approving a forgotten PIN ----------------------------------------

    @Test
    fun `GIVEN the hub issues a reset code WHEN a forgotten PIN is approved THEN it is mapped to a reset code`() =
        runTest {
            // GIVEN
            val remote = mock<MembersRemoteDataSource>()
            everySuspend { remote.approvePinReset("liam", "111111") } returns
                PinResetReadDto(code = "738291", expires_in_seconds = 900)

            // WHEN
            val reset = repository(remote).approvePinReset("liam", "111111")

            // THEN
            assertEquals(ResetCode(code = "738291", expiresInSeconds = 900), reset)
        }

    @Test
    fun `GIVEN the approver's own PIN is wrong WHEN a reset is approved THEN the refusal reaches the caller`() =
        runTest {
            // GIVEN
            val remote = mock<MembersRemoteDataSource>()
            everySuspend { remote.approvePinReset("liam", "000000") } throws WrongPinException(attemptsLeft = 2)

            // WHEN
            val thrown = assertFailsWith<WrongPinException> { repository(remote).approvePinReset("liam", "000000") }

            // THEN
            assertEquals(2, thrown.attemptsLeft)
        }

    // ---- changing a PIN ----------------------------------------------------

    @Test
    fun `GIVEN a PIN change the hub accepts WHEN it is made THEN the fresh token replaces the kept one`() =
        runTest {
            // GIVEN
            val remote = mock<MembersRemoteDataSource>()
            everySuspend { remote.changePin("111111", "222222") } returns
                TokenResponseDto(access_token = "new-token", token_type = "bearer", user = emmaDto)
            val tokens = InMemorySessionStorage().apply { saveTokens("old-token") }

            // WHEN
            repository(remote, tokens).changePin("111111", "222222")

            // THEN
            assertEquals("new-token", tokens.getAccessToken())
        }

    @Test
    fun `GIVEN the wrong current PIN WHEN a change is attempted THEN the old token is left alone`() =
        runTest {
            // GIVEN
            val remote = mock<MembersRemoteDataSource>()
            everySuspend { remote.changePin("000000", "222222") } throws WrongPinException(attemptsLeft = 1)
            val tokens = InMemorySessionStorage().apply { saveTokens("old-token") }

            // WHEN
            val thrown = assertFailsWith<WrongPinException> { repository(remote, tokens).changePin("000000", "222222") }

            // THEN
            assertEquals(1, thrown.attemptsLeft)
            assertEquals("old-token", tokens.getAccessToken())
        }

    // ---- removing and leaving ---------------------------------------------

    @Test
    fun `GIVEN an admin removing someone WHEN the hub agrees THEN the hub is asked and nothing is kept`() =
        runTest {
            // GIVEN
            val remote = mock<MembersRemoteDataSource>()
            everySuspend { remote.removeMember("liam") } returns Unit

            // WHEN
            repository(remote).removeMember("liam")

            // THEN
            verifySuspend(VerifyMode.exactly(1)) { remote.removeMember("liam") }
        }

    @Test
    fun `GIVEN the only admin WHEN someone tries to remove them THEN the refusal reaches the caller`() =
        runTest {
            // GIVEN
            val remote = mock<MembersRemoteDataSource>()
            everySuspend { remote.removeMember("emma") } throws SoleAdminException()

            // WHEN / THEN
            assertFailsWith<SoleAdminException> { repository(remote).removeMember("emma") }
        }

    @Test
    fun `GIVEN a member leaving WHEN they confirm with their PIN THEN the hub is asked`() =
        runTest {
            // GIVEN
            val remote = mock<MembersRemoteDataSource>()
            everySuspend { remote.leaveHousehold("111111") } returns Unit

            // WHEN
            repository(remote).leaveHousehold("111111")

            // THEN
            verifySuspend(VerifyMode.exactly(1)) { remote.leaveHousehold("111111") }
        }

    @Test
    fun `GIVEN a member leaving WHEN the hub agrees THEN the kept token and member are forgotten`() =
        runTest {
            // GIVEN
            val remote = mock<MembersRemoteDataSource>()
            everySuspend { remote.leaveHousehold("111111") } returns Unit
            val tokens =
                InMemorySessionStorage().apply {
                    saveTokens("emma-token")
                    saveUser(emmaDto)
                }

            // WHEN
            repository(remote, tokens).leaveHousehold("111111")

            // THEN
            assertNull(tokens.getAccessToken())
            assertNull(tokens.getUser())
        }

    @Test
    fun `GIVEN a wrong PIN WHEN a member tries to leave THEN the kept session is left alone`() =
        runTest {
            // GIVEN
            val remote = mock<MembersRemoteDataSource>()
            everySuspend { remote.leaveHousehold("000000") } throws WrongPinException(attemptsLeft = 2)
            val tokens =
                InMemorySessionStorage().apply {
                    saveTokens("emma-token")
                    saveUser(emmaDto)
                }

            // WHEN
            assertFailsWith<WrongPinException> { repository(remote, tokens).leaveHousehold("000000") }

            // THEN
            assertEquals("emma-token", tokens.getAccessToken())
            assertEquals(emmaDto, tokens.getUser())
        }

    @Test
    fun `GIVEN the only admin WHEN they try to leave THEN the refusal reaches the caller`() =
        runTest {
            // GIVEN
            val remote = mock<MembersRemoteDataSource>()
            everySuspend { remote.leaveHousehold("111111") } throws SoleAdminException()

            // WHEN / THEN
            assertFailsWith<SoleAdminException> { repository(remote).leaveHousehold("111111") }
        }
}
