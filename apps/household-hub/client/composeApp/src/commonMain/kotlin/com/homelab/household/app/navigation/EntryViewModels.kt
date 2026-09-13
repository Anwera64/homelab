package com.homelab.household.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * Gives a destination its own ViewModels, cleared when it leaves the composition. Without it every
 * screen shares one store for the life of the activity, and a PIN pad opened again would still
 * hold the last visit's digits and lock.
 */
@Composable
internal fun WithEntryViewModels(content: @Composable () -> Unit) {
    val owner = remember { EntryViewModelStoreOwner() }
    DisposableEffect(owner) {
        onDispose { owner.viewModelStore.clear() }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}

private class EntryViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}
