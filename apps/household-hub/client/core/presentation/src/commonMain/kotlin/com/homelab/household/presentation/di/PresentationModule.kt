package com.homelab.household.presentation.di

import com.homelab.household.presentation.viewmodel.auth.AuthViewModel
import com.homelab.household.presentation.viewmodel.chatsession.ChatSessionViewModel
import com.homelab.household.presentation.viewmodel.dashboard.DashboardViewModel
import com.homelab.household.presentation.viewmodel.memoryaudit.MemoryAuditViewModel
import org.koin.dsl.module

val presentationModule = module {
    factory { ChatSessionViewModel(get(), get(), get(), get()) }
    factory { DashboardViewModel(get(), get(), get(), get(), get()) }
    factory { AuthViewModel(get(), get(), get(), get()) }
    factory { MemoryAuditViewModel(get(), get(), get(), get()) }
}
