package com.homelab.household.presentation.di

import com.homelab.household.presentation.changepin.ChangePinViewModel
import com.homelab.household.presentation.chats.ChatsViewModel
import com.homelab.household.presentation.chatsession.ChatSessionViewModel
import com.homelab.household.presentation.dashboard.DashboardViewModel
import com.homelab.household.presentation.firstrun.FirstRunViewModel
import com.homelab.household.presentation.invitecode.InviteCodeViewModel
import com.homelab.household.presentation.invitecreate.InviteCreateViewModel
import com.homelab.household.presentation.join.JoinViewModel
import com.homelab.household.presentation.launch.LaunchViewModel
import com.homelab.household.presentation.leavehousehold.LeaveHouseholdViewModel
import com.homelab.household.presentation.members.MembersViewModel
import com.homelab.household.presentation.memoryaudit.MemoryAuditViewModel
import com.homelab.household.presentation.pinapprove.PinApproveViewModel
import com.homelab.household.presentation.pinentry.PinEntryViewModel
import com.homelab.household.presentation.pinforgot.PinForgotViewModel
import com.homelab.household.presentation.profile.ProfileViewModel
import com.homelab.household.presentation.profilepicker.ProfilePickerViewModel
import com.homelab.household.presentation.removemember.RemoveMemberViewModel
import com.homelab.household.presentation.resetpin.NewPinViewModel
import com.homelab.household.presentation.resetpin.ResetCodeViewModel
import org.koin.dsl.module

val presentationModule =
    module {
        factory { ChatSessionViewModel(get(), get(), get(), get(), get()) }
        factory { ChatsViewModel(get()) }
        factory { DashboardViewModel(get(), get(), get(), get(), get()) }
        factory { LaunchViewModel(get(), get()) }
        factory { MemoryAuditViewModel(get(), get(), get(), get()) }
        factory { FirstRunViewModel(get()) }
        factory { ProfilePickerViewModel(get()) }
        factory { InviteCodeViewModel(get()) }
        factory { ProfileViewModel(get(), get(), get()) }
        factory { MembersViewModel(get(), get()) }
        factory { InviteCreateViewModel(get()) }
        factory { params -> PinApproveViewModel(member = params.get(), approvePinReset = get()) }
        factory { params -> PinForgotViewModel(member = params.get(), listMembers = get()) }
        factory { ResetCodeViewModel() }
        factory { params -> NewPinViewModel(code = params.get(), redeemPinReset = get()) }
        factory { ChangePinViewModel(get()) }
        factory { params -> RemoveMemberViewModel(member = params.get(), removeMember = get()) }
        factory { LeaveHouseholdViewModel(get()) }
        factory { params ->
            JoinViewModel(preview = params.get(), code = params.get(), joinHousehold = get(), listMembers = get())
        }
        factory { params -> PinEntryViewModel(member = params.get(), loginUseCase = get()) }
    }
