package com.homelab.household.app.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

/**
 * Base for tests that compose a screen.
 *
 * Two things the desktop test runtime doesn't give a screen on its own:
 * - a Main dispatcher, which lifecycle-aware collection ([com.homelab.household.app.util.ObserveEvents]) hops to;
 * - a clean Koin context — `KoinApplication` starts the *global* one and leaves it behind when
 *   the composition goes away, so the next test would resolve the previous test's fakes.
 *
 * Stateless content tests need neither.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class ScreenTest {

    @BeforeTest
    fun installTestEnvironment() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        stopKoin()
    }

    @AfterTest
    fun removeTestEnvironment() {
        stopKoin()
        Dispatchers.resetMain()
    }
}
