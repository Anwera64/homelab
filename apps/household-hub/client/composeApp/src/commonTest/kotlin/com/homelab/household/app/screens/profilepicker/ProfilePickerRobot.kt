package com.homelab.household.app.screens.profilepicker

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.picker_hint
import com.homelab.household.app.resources.picker_invite_code
import com.homelab.household.app.resources.picker_member_count
import com.homelab.household.app.resources.picker_retry
import com.homelab.household.app.resources.picker_title
import com.homelab.household.app.resources.picker_unreachable
import com.homelab.household.app.testing.FakeSignInHub
import com.homelab.household.app.testing.TestApp
import com.homelab.household.domain.model.Member
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/** Everything "Who's here?" says and does, named as the resources the screen reads. */
@OptIn(ExperimentalTestApi::class)
class ProfilePickerRobot(
    private val test: ComposeUiTest,
) {
    suspend fun seesTheHousehold(vararg names: String) {
        seesText(getString(Res.string.picker_title))
        seesText(getPluralString(Res.plurals.picker_member_count, names.size, names.size))
        names.forEach { seesText(it) }
        seesText(getString(Res.string.picker_hint))
    }

    fun tapsTheFaceOf(name: String) {
        test.onNodeWithText(name).performClick()
    }

    suspend fun tapsInviteCode() {
        test.onNodeWithText(getString(Res.string.picker_invite_code)).performClick()
    }

    suspend fun seesTheHubDidNotAnswer() {
        seesText(getString(Res.string.picker_unreachable))
        seesText(getString(Res.string.picker_retry))
    }

    suspend fun tapsTryAgain() {
        test.onNodeWithText(getString(Res.string.picker_retry)).performClick()
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
suspend fun ComposeUiTest.onProfilePicker(block: suspend ProfilePickerRobot.() -> Unit) {
    ProfilePickerRobot(this).block()
}

/** "Who's here?" over a hub that answers however the test says. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.profilePickerScreen(
    hub: FakeSignInHub,
    onSelectMember: (Member) -> Unit = {},
    onInviteCode: () -> Unit = {},
) {
    setContent {
        TestApp(hub.engine) {
            ProfilePickerScreen(onSelectMember = onSelectMember, onInviteCode = onInviteCode)
        }
    }
}
