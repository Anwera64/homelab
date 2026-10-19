package com.homelab.household.domain.usecase

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.exception.WrongPinException
import com.homelab.household.domain.model.Invite
import com.homelab.household.domain.model.InvitePreview
import com.homelab.household.domain.model.ResetCode
import com.homelab.household.domain.model.User
import com.homelab.household.domain.repository.AuthRepository
import com.homelab.household.domain.repository.MembersRepository
import com.homelab.household.domain.usecase.impl.ApprovePinResetUseCaseImpl
import com.homelab.household.domain.usecase.impl.ChangePinUseCaseImpl
import com.homelab.household.domain.usecase.impl.CreateInviteUseCaseImpl
import com.homelab.household.domain.usecase.impl.JoinHouseholdUseCaseImpl
import com.homelab.household.domain.usecase.impl.LeaveHouseholdUseCaseImpl
import com.homelab.household.domain.usecase.impl.ListHouseholdMembersUseCaseImpl
import com.homelab.household.domain.usecase.impl.LookUpInviteUseCaseImpl
import com.homelab.household.domain.usecase.impl.RedeemPinResetUseCaseImpl
import com.homelab.household.domain.usecase.impl.RemoveMemberUseCaseImpl
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MembersUseCasesTest {
    private val authRepo = mock<AuthRepository>()
    private val membersRepo = mock<MembersRepository>()

    private val lookUpInviteUseCase = LookUpInviteUseCaseImpl(authRepo)
    private val joinHouseholdUseCase = JoinHouseholdUseCaseImpl(authRepo)
    private val redeemPinResetUseCase = RedeemPinResetUseCaseImpl(authRepo)
    private val listHouseholdMembersUseCase = ListHouseholdMembersUseCaseImpl(membersRepo)
    private val createInviteUseCase = CreateInviteUseCaseImpl(membersRepo)
    private val approvePinResetUseCase = ApprovePinResetUseCaseImpl(membersRepo)
    private val changePinUseCase = ChangePinUseCaseImpl(membersRepo)
    private val removeMemberUseCase = RemoveMemberUseCaseImpl(membersRepo)
    private val leaveHouseholdUseCase = LeaveHouseholdUseCaseImpl(membersRepo)

    private val emma =
        User(
            id = "emma",
            fullName = "Emma",
            isAdmin = true,
            isActive = true,
            personalSpaceId = "space-1",
            avatarColor = "#3C6E4E",
        )

    @Test
    fun `GIVEN a valid invite code WHEN the invite is looked up THEN the preview from the hub comes back`() =
        runTest {
            val preview = InvitePreview(invitedName = "Liam", inviterName = "Emma", inviterAvatarColor = "#3C6E4E")
            everySuspend { authRepo.lookUpInvite("482913") } returns preview

            assertEquals(preview, lookUpInviteUseCase("482913"))
        }

    @Test
    fun `GIVEN a blank invite code WHEN the invite is looked up THEN it is refused and the hub is never asked`() =
        runTest {
            assertFailsWith<ValidationException> { lookUpInviteUseCase("") }
            verifySuspend(VerifyMode.exactly(0)) { authRepo.lookUpInvite(any()) }
        }

    @Test
    fun `GIVEN a name with stray spaces WHEN joining a household THEN the hub gets the trimmed name with the PIN and colour`() =
        runTest {
            everySuspend { authRepo.joinHousehold("482913", "Liam", "111111", "#C05638") } returns emma

            val result = joinHouseholdUseCase("482913", "  Liam ", "111111", "#C05638")

            assertEquals(emma, result)
        }

    @Test
    fun `GIVEN a blank code or a blank name or a short PIN WHEN joining a household THEN each is refused and the hub is never asked`() =
        runTest {
            assertFailsWith<ValidationException> { joinHouseholdUseCase("", "Liam", "111111", "#C05638") }
            assertFailsWith<ValidationException> { joinHouseholdUseCase("482913", "   ", "111111", "#C05638") }
            assertFailsWith<ValidationException> { joinHouseholdUseCase("482913", "Liam", "1111", "#C05638") }

            verifySuspend(VerifyMode.exactly(0)) { authRepo.joinHousehold(any(), any(), any(), any()) }
        }

    @Test
    fun `GIVEN a valid reset code and a new PIN WHEN the reset is redeemed THEN the member is signed in`() =
        runTest {
            everySuspend { authRepo.redeemPinReset("482913", "111111") } returns emma

            assertEquals(emma, redeemPinResetUseCase("482913", "111111"))
        }

    @Test
    fun `GIVEN a blank code or a short PIN WHEN a reset is redeemed THEN each is refused and the hub is never asked`() =
        runTest {
            assertFailsWith<ValidationException> { redeemPinResetUseCase("", "111111") }
            assertFailsWith<ValidationException> { redeemPinResetUseCase("482913", "11") }

            verifySuspend(VerifyMode.exactly(0)) { authRepo.redeemPinReset(any(), any()) }
        }

    @Test
    fun `GIVEN a household with one member WHEN its members are listed THEN that member comes back`() =
        runTest {
            everySuspend { membersRepo.listHouseholdMembers() } returns listOf(emma)

            assertEquals(listOf(emma), listHouseholdMembersUseCase())
        }

    @Test
    fun `GIVEN a name with stray spaces WHEN an invite is created THEN the hub gets the trimmed name and admin flag`() =
        runTest {
            val invite = Invite(code = "482913", invitedName = "Liam", isAdmin = false, expiresInSeconds = 900)
            everySuspend { membersRepo.createInvite("Liam", false) } returns invite

            assertEquals(invite, createInviteUseCase("  Liam ", false))
        }

    @Test
    fun `GIVEN a blank invited name WHEN an invite is created THEN it is refused and the hub is never asked`() =
        runTest {
            assertFailsWith<ValidationException> { createInviteUseCase("   ", false) }
            verifySuspend(VerifyMode.exactly(0)) { membersRepo.createInvite(any(), any()) }
        }

    @Test
    fun `GIVEN an approver with their own PIN WHEN a PIN reset is approved THEN the hub gets that PIN and returns the reset code`() =
        runTest {
            val resetCode = ResetCode(code = "738291", expiresInSeconds = 900)
            everySuspend { membersRepo.approvePinReset("liam", "111111") } returns resetCode

            assertEquals(resetCode, approvePinResetUseCase("liam", "111111"))
        }

    @Test
    fun `GIVEN the hub rejecting the approver PIN WHEN a PIN reset is approved THEN the wrong PIN reaches the caller`() =
        runTest {
            everySuspend { membersRepo.approvePinReset("liam", "000000") } throws WrongPinException(attemptsLeft = 2)

            assertFailsWith<WrongPinException> { approvePinResetUseCase("liam", "000000") }
        }

    @Test
    fun `GIVEN a blank member or a short PIN WHEN a PIN reset is approved THEN each is refused and the hub is never asked`() =
        runTest {
            assertFailsWith<ValidationException> { approvePinResetUseCase("", "111111") }
            assertFailsWith<ValidationException> { approvePinResetUseCase("liam", "11") }

            verifySuspend(VerifyMode.exactly(0)) { membersRepo.approvePinReset(any(), any()) }
        }

    @Test
    fun `GIVEN an old and a new PIN WHEN the PIN is changed THEN the hub gets both once`() =
        runTest {
            everySuspend { membersRepo.changePin("111111", "222222") } returns Unit

            changePinUseCase("111111", "222222")

            verifySuspend(VerifyMode.exactly(1)) { membersRepo.changePin("111111", "222222") }
        }

    @Test
    fun `GIVEN a short old PIN or a short new PIN WHEN the PIN is changed THEN each is refused and the hub is never asked`() =
        runTest {
            assertFailsWith<ValidationException> { changePinUseCase("11", "222222") }
            assertFailsWith<ValidationException> { changePinUseCase("111111", "22") }

            verifySuspend(VerifyMode.exactly(0)) { membersRepo.changePin(any(), any()) }
        }

    @Test
    fun `GIVEN a member in the household WHEN they are removed THEN the hub is asked once`() =
        runTest {
            everySuspend { membersRepo.removeMember("liam") } returns Unit

            removeMemberUseCase("liam")

            verifySuspend(VerifyMode.exactly(1)) { membersRepo.removeMember("liam") }
        }

    @Test
    fun `GIVEN a blank member id WHEN a member is removed THEN it is refused and the hub is never asked`() =
        runTest {
            assertFailsWith<ValidationException> { removeMemberUseCase("") }
            verifySuspend(VerifyMode.exactly(0)) { membersRepo.removeMember(any()) }
        }

    @Test
    fun `GIVEN a member with their own PIN WHEN they leave the household THEN the hub gets that PIN once`() =
        runTest {
            everySuspend { membersRepo.leaveHousehold("111111") } returns Unit

            leaveHouseholdUseCase("111111")

            verifySuspend(VerifyMode.exactly(1)) { membersRepo.leaveHousehold("111111") }
        }

    @Test
    fun `GIVEN a short PIN WHEN leaving the household THEN it is refused and the hub is never asked`() =
        runTest {
            assertFailsWith<ValidationException> { leaveHouseholdUseCase("11") }
            verifySuspend(VerifyMode.exactly(0)) { membersRepo.leaveHousehold(any()) }
        }
}
