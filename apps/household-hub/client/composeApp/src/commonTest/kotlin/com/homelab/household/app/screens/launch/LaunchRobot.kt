package com.homelab.household.app.screens.launch

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.launch_checking
import com.homelab.household.app.resources.launch_failed_address_not_found
import com.homelab.household.app.resources.launch_failed_not_json
import com.homelab.household.app.resources.launch_failed_title
import com.homelab.household.app.resources.launch_failed_unknown
import com.homelab.household.app.resources.launch_failed_upstream
import com.homelab.household.app.resources.launch_first_run_title
import com.homelab.household.app.resources.launch_member_count
import com.homelab.household.app.resources.launch_no_route
import com.homelab.household.app.resources.launch_open_tailscale
import com.homelab.household.app.resources.launch_ready
import com.homelab.household.app.resources.launch_retry
import com.homelab.household.app.resources.launch_retrying_in
import com.homelab.household.app.resources.launch_unreachable_detail
import com.homelab.household.app.resources.launch_unreachable_title
import com.homelab.household.app.platform.ExternalApps
import com.homelab.household.app.testing.FakeExternalApps
import com.homelab.household.app.testing.TestApp
import com.homelab.household.data.local.InMemoryTokenStorage
import com.homelab.household.data.local.TokenStorage
import com.homelab.household.presentation.launch.HubFailure
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/**
 * Everything the launch screen says and does, named once — as the resource the screen reads, not
 * as a second copy of the text, so changing the copy in `strings.xml` can't quietly pass.
 *
 * Each assertion waits for its text first: the hub answers on its own coroutine, so `waitForIdle`
 * — which only waits for Compose — can run before the answer has arrived.
 */
@OptIn(ExperimentalTestApi::class)
class LaunchRobot(private val test: ComposeUiTest) {

    /** Checking has no end it can show, so the bar says "busy" rather than a percentage. */
    suspend fun seesItReachingTheHub() {
        seesText(getString(Res.string.launch_checking))
        test.onNode(loading).assertIsDisplayed()
    }

    suspend fun seesTheHubIsReady(members: Int) {
        seesText(getString(Res.string.launch_ready))
        seesText(getPluralString(Res.plurals.launch_member_count, members, members))
    }

    suspend fun seesNobodyLivesHereYet() = seesText(getString(Res.string.launch_first_run_title))

    /**
     * Unreachable, not merely failed: nothing answered, so the screen says there's no route, that
     * it will try again by itself, and offers Tailscale as the likely way back.
     */
    suspend fun seesTheHubIsOffline() {
        seesText(getString(Res.string.launch_unreachable_title))
        seesText(getString(Res.string.launch_unreachable_detail))
        seesText(getString(Res.string.launch_no_route))
        seesText(getString(Res.string.launch_retrying_in))
        seesSomethingToRetry()
        seesText(getString(Res.string.launch_open_tailscale))
        seesNothingLoading()
    }

    suspend fun seesTheHubFailed(reason: HubFailure) {
        seesText(getString(Res.string.launch_failed_title))
        seesText(
            when (reason) {
                HubFailure.AddressNotFound -> getString(Res.string.launch_failed_address_not_found)
                is HubFailure.Upstream -> getString(Res.string.launch_failed_upstream, reason.statusCode)
                is HubFailure.NotJson -> getString(Res.string.launch_failed_not_json, reason.contentType)
                HubFailure.Unknown -> getString(Res.string.launch_failed_unknown)
            }
        )
        seesSomethingToRetry()
        seesNothingLoading()
    }

    suspend fun seesSomethingToRetry() = seesText(getString(Res.string.launch_retry))

    /** A hub that already gave its answer isn't still being waited on. */
    fun seesNothingLoading() {
        test.onNode(loading).assertDoesNotExist()
    }

    fun seesTheHubAddress(address: String) = seesText(address)

    suspend fun tapsTryAgain() {
        test.onNodeWithText(getString(Res.string.launch_retry)).performClick()
    }

    suspend fun tapsOpenTailscale() {
        test.onNodeWithText(getString(Res.string.launch_open_tailscale)).performClick()
    }

    private fun seesText(text: String) {
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(text).assertIsDisplayed()
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L

        val loading = SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo,
            ProgressBarRangeInfo.Indeterminate
        )
    }
}

@OptIn(ExperimentalTestApi::class)
suspend fun ComposeUiTest.onLaunch(block: suspend LaunchRobot.() -> Unit) {
    LaunchRobot(this).block()
}

/** The launch screen over a hub that answers however the test says. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.launchScreen(
    hub: FakeLaunchHub,
    onSignIn: () -> Unit = {},
    onFirstRun: () -> Unit = {},
    onSignedIn: () -> Unit = {},
    tokenStorage: TokenStorage = InMemoryTokenStorage(),
    externalApps: ExternalApps = FakeExternalApps()
) {
    setContent {
        TestApp(hub.engine, tokenStorage = tokenStorage, externalApps = externalApps) {
            LaunchScreen(onSignIn = onSignIn, onFirstRun = onFirstRun, onSignedIn = onSignedIn)
        }
    }
}
