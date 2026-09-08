package com.homelab.household.di

import com.homelab.household.data.di.dataModule
import com.homelab.household.presentation.di.presentationModule

val appModules = listOf(
    dataModule,
    domainModule,
    presentationModule
)
