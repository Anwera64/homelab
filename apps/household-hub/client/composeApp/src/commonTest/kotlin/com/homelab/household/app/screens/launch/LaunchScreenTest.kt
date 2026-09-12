package com.homelab.household.app.screens.launch

import androidx.compose.ui.test.ExperimentalTestApi
import com.homelab.household.app.testing.TEST_HUB_HOST
import com.homelab.household.app.testing.TestApp
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.presentation.launch.HubStatus
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The launch screen with the real stack under it: ViewModel, use cases, repositories, mappers
 * and the Ktor client. Only the hub at the far end is faked.
 *
 * Navigation is a counted lambda here — where those lambdas take the user is `AppNavHostTest`.
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
            launchScreen(hub, onSignIn = { signIn++ }, onFirstRun = { firstRun++ })

            onLaunch { seesTheHubIsReady(members = 2) }
            waitUntil(timeoutMillis = WAIT_MILLIS) { signIn == 1 }
        }

        assertEquals(1, signIn)
        assertEquals(0, firstRun)
    }

    @Test
    fun a_hub_with_nobody_on_it_sends_the_user_to_first_run() {
        val hub = FakeLaunchHub()
        hub.respondsWith(initialized = false, members = 0)
        var signIn = 0
        var firstRun = 0

        runScreenTest {
            launchScreen(hub, onSignIn = { signIn++ }, onFirstRun = { firstRun++ })

            onLaunch { seesNobodyLivesHereYet() }
            waitUntil(timeoutMillis = WAIT_MILLIS) { firstRun == 1 }
        }

        assertEquals(0, signIn)
        assertEquals(1, firstRun)
    }

    @Test
    fun an_unreachable_hub_keeps_the_user_here_with_something_to_retry() {
        val hub = FakeLaunchHub()
        hub.isOffline()
        var moved = 0

        runScreenTest {
            launchScreen(hub, onSignIn = { moved++ }, onFirstRun = { moved++ })

            onLaunch { seesTheHubIsOffline() }
        }

        assertEquals(0, moved)
    }

    /** The message is the hub's, all the way from `AuthRepositoryImpl` — not one we invented. */
    @Test
    fun a_failing_hub_keeps_its_own_words() {
        val hub = FakeLaunchHub()
        hub.fails(HttpStatusCode.InternalServerError)
        var moved = 0

        runScreenTest {
            launchScreen(hub, onSignIn = { moved++ }, onFirstRun = { moved++ })

            onLaunch {
                seesTheHubSaid("Hub returned HTTP 500: Internal Server Error")
                seesSomethingToRetry()
            }
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

            onLaunch { seesTheHubIsOffline() }

            hub.respondsWith(initialized = true, members = 1)
            onLaunch { tapsTryAgain() }

            onLaunch { seesTheHubIsReady(members = 1) }
            waitUntil(timeoutMillis = WAIT_MILLIS) { signIn == 1 }
        }

        assertEquals(1, signIn)
    }

    /**
     * The preview provider is the list of states launch can be in. Each one is reached the way
     * the app reaches it — through the hub — and has to draw. A new `HubStatus` without a
     * previewed state, or without a hub behaviour that produces it, fails here.
     */
    @Test
    fun every_previewed_state_draws() {
        val states = LaunchUiStateProvider().values.toList()
        assertEquals(5, states.size)

        states.forEach { state ->
            val hub = FakeLaunchHub()
            hub.producing(state.status)

            runScreenTest {
                launchScreen(hub)

                onLaunch {
                    when (val status = state.status) {
                        HubStatus.Checking -> seesItReachingTheHub()
                        is HubStatus.Ready -> seesTheHubIsReady(status.memberCount)
                        HubStatus.FirstRun -> seesNobodyLivesHereYet()
                        HubStatus.Unreachable -> seesTheHubIsOffline()
                        is HubStatus.Failed -> {
                            // The previewed message is the one the hub really sends.
                            seesTheHubSaid(status.message)
                            seesSomethingToRetry()
                        }
                    }
                    seesTheHubAddress(TEST_HUB_HOST)
                }
            }
        }
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
