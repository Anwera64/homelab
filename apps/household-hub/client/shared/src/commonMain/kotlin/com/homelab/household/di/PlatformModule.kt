package com.homelab.household.di

import org.koin.core.module.Module

/** Binds what differs per platform: the HTTP engine and where auth tokens are kept. */
expect val platformModule: Module
