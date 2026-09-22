package com.homelab.household.app.screens.launch

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.components.HearthProgressBarTag
import com.homelab.household.app.platform.ExternalApps
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.app_name
import com.homelab.household.app.resources.launch_checking
import com.homelab.household.app.resources.launch_no_route_chip
import com.homelab.household.app.resources.launch_no_route_detail
import com.homelab.household.app.resources.launch_no_route_title
import com.homelab.household.app.resources.launch_not_found_chip
import com.homelab.household.app.resources.launch_not_found_detail
import com.homelab.household.app.resources.launch_not_found_title
import com.homelab.household.app.resources.launch_offline_hint
import com.homelab.household.app.resources.launch_open_tailscale
import com.homelab.household.app.resources.launch_retry
import com.homelab.household.app.resources.launch_retrying_in
import com.homelab.household.app.resources.launch_unknown_chip
import com.homelab.household.app.resources.launch_unknown_detail
import com.homelab.household.app.resources.launch_unknown_title
import com.homelab.household.app.resources.launch_upstream_chip
import com.homelab.household.app.resources.launch_upstream_detail
import com.homelab.household.app.resources.launch_upstream_title
import com.homelab.household.app.resources.launch_web_page_chip
import com.homelab.household.app.resources.launch_web_page_detail
import com.homelab.household.app.resources.launch_web_page_title
import com.homelab.household.app.testing.FakeExternalApps
import com.homelab.household.app.testing.TestApp
import com.homelab.household.data.datasource.local.InMemorySessionStorage
import com.homelab.household.data.datasource.local.StoredSessionLocalDataSource
import com.homelab.household.presentation.launch.HubFailure
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
        seesText(getString(Res.string.app_name))
        seesText(getString(Res.string.launch_checking))
        test.onNode(loading).assertIsDisplayed()
    }

    /**
     * Every reason gets the same offline screen: what went wrong in the app's own words, that it
     * will try again by itself, and the ways back — retry now, or Tailscale.
     */
    suspend fun seesTheHubIsUnavailable(reason: HubFailure) {
        val words = wordsFor(reason)
        seesText(words.title)
        seesText(words.detail)
        seesText(words.chip)
        seesText(getString(Res.string.launch_retrying_in))
        seesText(getString(Res.string.launch_retry))
        seesText(getString(Res.string.launch_open_tailscale))
        seesText(getString(Res.string.launch_offline_hint))
        seesNothingLoading()
    }

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

    private class Words(val title: String, val detail: String, val chip: String)

    private suspend fun wordsFor(reason: HubFailure): Words = when (reason) {
        HubFailure.NoRoute -> Words(
            getString(Res.string.launch_no_route_title),
            getString(Res.string.launch_no_route_detail),
            getString(Res.string.launch_no_route_chip)
        )
        is HubFailure.Upstream -> Words(
            getString(Res.string.launch_upstream_title),
            getString(Res.string.launch_upstream_detail, reason.statusCode),
            getString(Res.string.launch_upstream_chip, reason.statusCode)
        )
        is HubFailure.NotJson -> Words(
            getString(Res.string.launch_web_page_title),
            getString(Res.string.launch_web_page_detail),
            getString(Res.string.launch_web_page_chip)
        )
        HubFailure.AddressNotFound -> Words(
            getString(Res.string.launch_not_found_title),
            getString(Res.string.launch_not_found_detail),
            getString(Res.string.launch_not_found_chip)
        )
        HubFailure.Unknown -> Words(
            getString(Res.string.launch_unknown_title),
            getString(Res.string.launch_unknown_detail),
            getString(Res.string.launch_unknown_chip)
        )
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L

        // The app's own bar, by name. It used to be matched as "some indeterminate progress bar",
        // which stopped being true the moment `TestApp` turned the motion off: with no animation
        // the bar rests at a fixed fraction, which is determinate. The tag is the better question
        // anyway — it asks whether *this* bar is on screen.
        val loading = hasTestTag(HearthProgressBarTag)
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
    sessionStorage: StoredSessionLocalDataSource = InMemorySessionStorage(),
    externalApps: ExternalApps = FakeExternalApps()
) {
    setContent {
        TestApp(hub.engine, sessionStorage = sessionStorage, externalApps = externalApps) {
            LaunchScreen(onSignIn = onSignIn, onFirstRun = onFirstRun)
        }
    }
}
