package com.homelab.household.app.screens.launch

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.launch_offline_hint
import com.homelab.household.app.testing.FakeExternalApps
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.testing.TEST_HUB_HOST
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.presentation.launch.HubFailure
import com.homelab.household.presentation.launch.HubStatus
import io.ktor.http.HttpStatusCode
import org.jetbrains.compose.resources.getString
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
    fun a_hub_with_members_sends_the_user_to_sign_in_from_the_splash() {
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
    fun a_hub_with_nobody_on_it_sends_the_user_to_first_run_from_the_splash() {
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
    fun open_tailscale_hands_the_user_over_to_tailscale() {
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
    fun an_unreachable_hub_keeps_the_user_here_with_something_to_retry() {
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
    fun a_failing_hub_says_what_it_answered() {
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
    fun something_answering_with_a_web_page_says_so() {
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
    fun it_names_the_hub_it_is_reaching_while_it_waits() {
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
    fun trying_again_asks_the_hub_a_second_time() {
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
    fun every_previewed_state_draws() {
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
    fun the_offline_screen_scrolls_when_it_does_not_fit() = runComposeUiTest {
        val offline = LaunchUiStateProvider().values.first { it.status is HubStatus.Unavailable }
        setContent {
            StillTheme {
                Box(Modifier.size(width = 360.dp, height = 320.dp)) {
                    LaunchContent(state = offline, onRetry = {}, onOpenTailscale = {})
                }
            }
        }

        onNodeWithText(getString(Res.string.launch_offline_hint)).performScrollTo().assertIsDisplayed()
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
