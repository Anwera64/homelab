package com.homelab.household.presentation.di

import com.homelab.household.presentation.launch.LaunchViewModel
import com.homelab.household.presentation.chatsession.ChatSessionViewModel
import com.homelab.household.presentation.dashboard.DashboardViewModel
import com.homelab.household.presentation.firstrun.FirstRunViewModel
import com.homelab.household.presentation.memoryaudit.MemoryAuditViewModel
import com.homelab.household.presentation.pinentry.PinEntryViewModel
import com.homelab.household.presentation.profilepicker.ProfilePickerViewModel
import org.koin.dsl.module

val presentationModule = module {
    factory { ChatSessionViewModel(get(), get(), get(), get()) }
    factory { DashboardViewModel(get(), get(), get(), get(), get()) }
    factory { LaunchViewModel(get(), get(), get()) }
    factory { MemoryAuditViewModel(get(), get(), get(), get()) }
    factory { FirstRunViewModel(get()) }
    factory { ProfilePickerViewModel(get()) }
    factory { params -> PinEntryViewModel(member = params.get(), loginUseCase = get()) }
}
