package com.homelab.household.app.testing

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.core.context.stopKoin

/**
 * Runs one composition of a screen.
 *
 * Two things a screen needs that the test runtime doesn't give it:
 * - a Main dispatcher, which lifecycle-aware collection
 *   ([com.homelab.household.app.util.ObserveEvents]) hops to via `repeatOnLifecycle`;
 * - a Koin context that isn't the last composition's. `KoinApplication` starts the *global*
 *   context and deliberately leaves it behind when the composition goes away, so without
 *   stopping it here a second composition silently resolves the first one's fakes.
 *
 * It is a function rather than a base class so a single test can compose more than once.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
fun runScreenTest(block: suspend ComposeUiTest.() -> Unit) {
    Dispatchers.setMain(Dispatchers.Unconfined)
    stopKoin()
    try {
        runComposeUiTest { block() }
    } finally {
        stopKoin()
        Dispatchers.resetMain()
    }
}
