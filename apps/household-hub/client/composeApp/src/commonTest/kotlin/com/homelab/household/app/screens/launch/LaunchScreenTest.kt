package com.homelab.household.app.screens.launch

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.homelab.household.app.testing.FakeExternalApps
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.testing.TEST_HUB_HOST
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.presentation.launch.HubFailure
import com.homelab.household.presentation.launch.HubStatus
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The launch screen with the real stack under it: ViewModel, use cases, repositories, mappers
 * and the Ktor client. Only the hub at the far end is faked.
 *
 * Navigation is a counted lambda here — where those lambdas take the user is `AppNavHostTest`.
 * Because the lambdas don't navigate, the screen stays put after it calls one, which is how these
 * tests see what launch draws once the hub has answered.
 */
@OptIn(ExperimentalTestApi::class)
class LaunchScreenTest {
    /** No "your hub is ready" on the way: the splash is all there is until the user moves on. */
    @Test
    fun `GIVEN a hub with members WHEN the launch screen opens THEN it sends the user to sign in and not to first run`() {
        val hub = FakeLaunchHub()
        hub.respondsWith(initialized = true, members = 2)
        var signIn = 0
        var firstRun = 0

        runScreenTest {
            launchScreen(hub, onSignIn = { signIn++ }, onFirstRun = { firstRun++ })

            waitUntil(timeoutMillis = WAIT_MILLIS) { signIn == 1 }
            onLaunch { seesItReachingTheHub() }
        }

        assertEquals(1, signIn)
        assertEquals(0, firstRun)
    }

    @Test
    fun `GIVEN a hub with nobody on it WHEN the launch screen opens THEN it sends the user to first run and not to sign in`() {
        val hub = FakeLaunchHub()
        hub.respondsWith(initialized = false, members = 0)
        var signIn = 0
        var firstRun = 0

        runScreenTest {
            launchScreen(hub, onSignIn = { signIn++ }, onFirstRun = { firstRun++ })

            waitUntil(timeoutMillis = WAIT_MILLIS) { firstRun == 1 }
            onLaunch { seesItReachingTheHub() }
        }

        assertEquals(0, signIn)
        assertEquals(1, firstRun)
    }

    @Test
    fun `GIVEN an offline hub WHEN open Tailscale is tapped THEN Tailscale is opened once`() {
        val hub = FakeLaunchHub()
        hub.isOffline()
        val apps = FakeExternalApps()

        runScreenTest {
            launchScreen(hub, externalApps = apps)

            onLaunch {
                seesTheHubIsUnavailable(HubFailure.NoRoute)
                tapsOpenTailscale()
            }
        }

        assertEquals(1, apps.tailscaleOpened)
    }

    @Test
    fun `GIVEN an unreachable hub WHEN the launch screen opens THEN it says the hub is unavailable and the user goes nowhere`() {
        val hub = FakeLaunchHub()
        hub.isOffline()
        var moved = 0

        runScreenTest {
            launchScreen(hub, onSignIn = { moved++ }, onFirstRun = { moved++ })

            onLaunch { seesTheHubIsUnavailable(HubFailure.NoRoute) }
        }

        assertEquals(0, moved)
    }

    /** A real 500 from the hub, said in the app's own words rather than the hub's — and retried. */
    @Test
    fun `GIVEN a hub failing with a server error WHEN the launch screen opens THEN it says the hub answered 500 and the user goes nowhere`() {
        val hub = FakeLaunchHub()
        hub.fails(HttpStatusCode.InternalServerError)
        var moved = 0

        runScreenTest {
            launchScreen(hub, onSignIn = { moved++ }, onFirstRun = { moved++ })

            onLaunch { seesTheHubIsUnavailable(HubFailure.Upstream(statusCode = 500)) }
        }

        assertEquals(0, moved)
    }

    @Test
    fun `GIVEN something answering with a web page WHEN the launch screen opens THEN it says the answer was not JSON and the user goes nowhere`() {
        val hub = FakeLaunchHub()
        hub.answersWithHtml()
        var moved = 0

        runScreenTest {
            launchScreen(hub, onSignIn = { moved++ }, onFirstRun = { moved++ })

            onLaunch { seesTheHubIsUnavailable(HubFailure.NotJson(contentType = "text/html")) }
        }

        assertEquals(0, moved)
    }

    @Test
    fun `GIVEN a hub that never answers WHEN the launch screen opens THEN it says it is reaching the hub and names its address`() {
        val hub = FakeLaunchHub()
        hub.neverAnswers()

        runScreenTest {
            launchScreen(hub)

            onLaunch {
                seesItReachingTheHub()
                seesTheHubAddress(TEST_HUB_HOST)
            }
        }
    }

    @Test
    fun `GIVEN an offline hub that comes back WHEN try again is tapped THEN the hub is asked again and the user is sent to sign in`() {
        val hub = FakeLaunchHub()
        hub.isOffline()
        var signIn = 0

        runScreenTest {
            launchScreen(hub, onSignIn = { signIn++ })

            onLaunch { seesTheHubIsUnavailable(HubFailure.NoRoute) }

            hub.respondsWith(initialized = true, members = 1)
            onLaunch { tapsTryAgain() }

            waitUntil(timeoutMillis = WAIT_MILLIS) { signIn == 1 }
        }

        assertEquals(1, signIn)
    }

    /**
     * The preview provider is the list of states launch can be in. Each one is reached the way
     * the app reaches it — through the hub — and has to draw. A new `HubStatus` or `HubFailure`
     * without a previewed state, or without a hub behaviour that produces it, fails here.
     */
    @Test
    fun `GIVEN every previewed state WHEN launch is reached through the hub THEN each state draws with the hub address`() {
        val states = LaunchUiStateProvider().values.toList()
        assertEquals(6, states.size)

        states.forEach { state ->
            val hub = FakeLaunchHub()
            hub.producing(state.status)

            runScreenTest {
                launchScreen(hub)

                onLaunch {
                    when (val status = state.status) {
                        HubStatus.Checking -> seesItReachingTheHub()

                        // The previewed reason is one the hub can really produce.
                        is HubStatus.Unavailable -> seesTheHubIsUnavailable(status.reason)
                    }
                    seesTheHubAddress(TEST_HUB_HOST)
                }
            }
        }
    }

    /** A short phone or a large font: the offline screen scrolls rather than clipping its buttons. */
    @Test
    fun `GIVEN the offline screen in a short window WHEN it is shown THEN it scrolls to the offline hint`() =
        runComposeUiTest {
            val offline = LaunchUiStateProvider().values.first { it.status is HubStatus.Unavailable }
            setContent {
                StillTheme {
                    Box(Modifier.size(width = 360.dp, height = 320.dp)) {
                        LaunchContent(state = offline, onRetry = {}, onOpenTailscale = {})
                    }
                }
            }

            onLaunch { scrollsToTheOfflineHint() }
        }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
