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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

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

    private val emma = User(
        id = "emma",
        fullName = "Emma",
        isAdmin = true,
        isActive = true,
        personalSpaceId = "space-1",
        avatarColor = "#3C6E4E"
    )

    @Test
    fun looking_up_an_invite_asks_the_hub_for_the_preview() = runTest {
        val preview = InvitePreview(invitedName = "Liam", inviterName = "Emma", inviterAvatarColor = "#3C6E4E")
        everySuspend { authRepo.lookUpInvite("482913") } returns preview

        assertEquals(preview, lookUpInviteUseCase("482913"))
    }

    @Test
    fun a_blank_invite_code_never_reaches_the_hub() = runTest {
        assertFailsWith<ValidationException> { lookUpInviteUseCase("") }
        verifySuspend(VerifyMode.exactly(0)) { authRepo.lookUpInvite(any()) }
    }

    @Test
    fun joining_a_household_sends_the_trimmed_name_the_pin_and_the_colour() = runTest {
        everySuspend { authRepo.joinHousehold("482913", "Liam", "111111", "#C05638") } returns emma

        val result = joinHouseholdUseCase("482913", "  Liam ", "111111", "#C05638")

        assertEquals(emma, result)
    }

    @Test
    fun joining_needs_a_code_a_name_and_a_six_digit_pin() = runTest {
        assertFailsWith<ValidationException> { joinHouseholdUseCase("", "Liam", "111111", "#C05638") }
        assertFailsWith<ValidationException> { joinHouseholdUseCase("482913", "   ", "111111", "#C05638") }
        assertFailsWith<ValidationException> { joinHouseholdUseCase("482913", "Liam", "1111", "#C05638") }

        verifySuspend(VerifyMode.exactly(0)) { authRepo.joinHousehold(any(), any(), any(), any()) }
    }

    @Test
    fun redeeming_a_pin_reset_signs_the_member_in() = runTest {
        everySuspend { authRepo.redeemPinReset("482913", "111111") } returns emma

        assertEquals(emma, redeemPinResetUseCase("482913", "111111"))
    }

    @Test
    fun redeeming_a_pin_reset_needs_a_code_and_a_six_digit_pin() = runTest {
        assertFailsWith<ValidationException> { redeemPinResetUseCase("", "111111") }
        assertFailsWith<ValidationException> { redeemPinResetUseCase("482913", "11") }

        verifySuspend(VerifyMode.exactly(0)) { authRepo.redeemPinReset(any(), any()) }
    }

    @Test
    fun listing_household_members_asks_the_hub() = runTest {
        everySuspend { membersRepo.listHouseholdMembers() } returns listOf(emma)

        assertEquals(listOf(emma), listHouseholdMembersUseCase())
    }

    @Test
    fun creating_an_invite_sends_the_trimmed_name_and_admin_flag() = runTest {
        val invite = Invite(code = "482913", invitedName = "Liam", isAdmin = false, expiresInSeconds = 900)
        everySuspend { membersRepo.createInvite("Liam", false) } returns invite

        assertEquals(invite, createInviteUseCase("  Liam ", false))
    }

    @Test
    fun a_blank_invited_name_never_reaches_the_hub() = runTest {
        assertFailsWith<ValidationException> { createInviteUseCase("   ", false) }
        verifySuspend(VerifyMode.exactly(0)) { membersRepo.createInvite(any(), any()) }
    }

    @Test
    fun approving_a_pin_reset_confirms_with_the_approvers_own_pin() = runTest {
        val resetCode = ResetCode(code = "738291", expiresInSeconds = 900)
        everySuspend { membersRepo.approvePinReset("liam", "111111") } returns resetCode

        assertEquals(resetCode, approvePinResetUseCase("liam", "111111"))
    }

    @Test
    fun a_wrong_approver_pin_reaches_the_caller_as_it_came_from_the_hub() = runTest {
        everySuspend { membersRepo.approvePinReset("liam", "000000") } throws WrongPinException(attemptsLeft = 2)

        assertFailsWith<WrongPinException> { approvePinResetUseCase("liam", "000000") }
    }

    @Test
    fun approving_needs_a_member_id_and_a_six_digit_pin() = runTest {
        assertFailsWith<ValidationException> { approvePinResetUseCase("", "111111") }
        assertFailsWith<ValidationException> { approvePinResetUseCase("liam", "11") }

        verifySuspend(VerifyMode.exactly(0)) { membersRepo.approvePinReset(any(), any()) }
    }

    @Test
    fun changing_a_pin_sends_both_pins() = runTest {
        everySuspend { membersRepo.changePin("111111", "222222") } returns Unit

        changePinUseCase("111111", "222222")

        verifySuspend(VerifyMode.exactly(1)) { membersRepo.changePin("111111", "222222") }
    }

    @Test
    fun changing_a_pin_needs_two_six_digit_pins() = runTest {
        assertFailsWith<ValidationException> { changePinUseCase("11", "222222") }
        assertFailsWith<ValidationException> { changePinUseCase("111111", "22") }

        verifySuspend(VerifyMode.exactly(0)) { membersRepo.changePin(any(), any()) }
    }

    @Test
    fun removing_a_member_asks_the_hub() = runTest {
        everySuspend { membersRepo.removeMember("liam") } returns Unit

        removeMemberUseCase("liam")

        verifySuspend(VerifyMode.exactly(1)) { membersRepo.removeMember("liam") }
    }

    @Test
    fun a_blank_member_id_never_reaches_the_hub_when_removing() = runTest {
        assertFailsWith<ValidationException> { removeMemberUseCase("") }
        verifySuspend(VerifyMode.exactly(0)) { membersRepo.removeMember(any()) }
    }

    @Test
    fun leaving_the_household_confirms_with_the_members_own_pin() = runTest {
        everySuspend { membersRepo.leaveHousehold("111111") } returns Unit

        leaveHouseholdUseCase("111111")

        verifySuspend(VerifyMode.exactly(1)) { membersRepo.leaveHousehold("111111") }
    }

    @Test
    fun leaving_needs_a_six_digit_pin() = runTest {
        assertFailsWith<ValidationException> { leaveHouseholdUseCase("11") }
        verifySuspend(VerifyMode.exactly(0)) { membersRepo.leaveHousehold(any()) }
    }
}
