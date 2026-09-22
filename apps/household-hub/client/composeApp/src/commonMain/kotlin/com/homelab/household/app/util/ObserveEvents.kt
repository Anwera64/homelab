package com.homelab.household.app.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow

/**
 * Collects a ViewModel's one-shot events while the screen is at least STARTED.
 *
 * One-shot means one-shot: the events come from a [kotlinx.coroutines.channels.Channel], so a
 * recomposition never replays them and a backgrounded screen never navigates behind the user's
 * back — it picks the events up when it comes back.
 */
@Composable
fun <T> ObserveEvents(
    events: Flow<T>,
    onEvent: (T) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnEvent by rememberUpdatedState(onEvent)

    LaunchedEffect(events, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            events.collect { currentOnEvent(it) }
        }
    }
}
