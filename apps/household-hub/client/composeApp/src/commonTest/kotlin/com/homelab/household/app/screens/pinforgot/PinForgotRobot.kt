package com.homelab.household.app.screens.pinforgot

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.components.SKELETON_GROUP_TAG
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.forgot_alone_title
import com.homelab.household.app.resources.forgot_ask
import com.homelab.household.app.resources.forgot_have_code
import com.homelab.household.app.resources.forgot_title
import com.homelab.household.app.testing.FakeMembersHub
import com.homelab.household.app.testing.TestApp
import com.homelab.household.data.datasource.local.InMemorySessionStorage
import com.homelab.household.domain.model.Member
import org.jetbrains.compose.resources.getString

/** Everything the forgotten-PIN screen says and does, named as the resources it reads. */
@OptIn(ExperimentalTestApi::class)
class PinForgotRobot(
    private val test: ComposeUiTest,
) {
    suspend fun seesTheTitle() = seesText(getString(Res.string.forgot_title))

    /** The housemate who can vouch is named once the hub has said who lives here. */
    suspend fun seesToAsk(name: String) = seesText(getString(Res.string.forgot_ask, name))

    /** The hub's own reset is the backstop, and it does not depend on the hub answering. */
    suspend fun seesTheFallback() = seesText(getString(Res.string.forgot_alone_title))

    fun seesItLoading() {
        test.onNodeWithTag(SKELETON_GROUP_TAG).assertIsDisplayed()
    }

    suspend fun tapsHaveACode() {
        test.onNodeWithText(getString(Res.string.forgot_have_code)).performClick()
    }

    private fun seesText(text: String) {
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(text).assertIsDisplayed()
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}

@OptIn(ExperimentalTestApi::class)
suspend fun ComposeUiTest.onPinForgot(block: suspend PinForgotRobot.() -> Unit) {
    PinForgotRobot(this).block()
}

/** The forgotten-PIN screen for [member], signed in, over a hub that answers however the test says. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.pinForgotScreen(
    hub: FakeMembersHub,
    member: Member,
    onHaveCode: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    setContent {
        TestApp(hub.engine, sessionStorage = InMemorySessionStorage().apply { saveTokens("signed-in-token") }) {
            PinForgotScreen(member = member, onBack = onBack, onHaveCode = onHaveCode)
        }
    }
}
