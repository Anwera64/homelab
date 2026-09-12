package com.homelab.household.app.screens.launch

import androidx.compose.ui.test.ExperimentalTestApi
import com.homelab.household.app.testing.TestApp
import com.homelab.household.app.testing.runScreenTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The launch screen with the real stack under it: ViewModel, use cases, repositories and the
 * Ktor client. Only the hub at the far end is faked.
 */
@OptIn(ExperimentalTestApi::class)
class LaunchScreenTest {

    @Test
    fun a_hub_with_members_sends_the_user_to_sign_in() {
        val hub = FakeLaunchHub()
        hub.respondsWith(initialized = true, members = 2)
        var signIn = 0
        var firstRun = 0

        runScreenTest {
            setContent {
                TestApp(hub.engine) {
                    LaunchScreen(onSignIn = { signIn++ }, onFirstRun = { firstRun++ })
                }
            }
            onLaunch { seesTheHubIsReady(members = 2) }
            waitUntil(timeoutMillis = 5_000) { signIn == 1 }
        }

        assertEquals(1, signIn)
        assertEquals(0, firstRun)
    }
}
