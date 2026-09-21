package com.homelab.household.app.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.a11y_working
import com.homelab.household.app.theme.DefaultSizes
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.app.theme.LocalHearthMotion
import com.homelab.household.app.theme.StillMotion
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Primary buttons are never disabled (design notes §2): they have no `enabled` parameter, so a tap
 * always reaches `onClick`, where the screen explains what's missing. A button that is *already
 * working* is the one exception, and it still isn't dimmed — it keeps its colour and swallows the
 * second tap.
 *
 * Every busy case runs under `StillMotion`: the bar along the button's bottom edge animates
 * endlessly otherwise, and an endlessly-animating tree never goes idle for the test to look at.
 */
@OptIn(ExperimentalTestApi::class)
class ButtonsTest {

    @Test
    fun primary_button_always_reaches_on_click() = assertAlwaysClickable { onClick ->
        PrimaryButton(text = "Create", onClick = onClick)
    }

    @Test
    fun secondary_button_always_reaches_on_click() = assertAlwaysClickable { onClick ->
        SecondaryButton(text = "Create", onClick = onClick)
    }

    @Test
    fun destructive_button_always_reaches_on_click() = assertAlwaysClickable { onClick ->
        DestructiveButton(text = "Create", onClick = onClick)
    }

    /**
     * A button that is already working swallows the tap rather than sending a second one. This is
     * the button's own guard; every ViewModel guards re-entry too, and both are cheap.
     */
    @Test
    fun a_busy_primary_button_swallows_the_tap() = assertBusySwallowsTheTap { busy, onClick ->
        PrimaryButton(text = "Creating…", onClick = onClick, busy = busy)
    }

    @Test
    fun a_busy_secondary_button_swallows_the_tap() = assertBusySwallowsTheTap { busy, onClick ->
        SecondaryButton(text = "Creating…", onClick = onClick, busy = busy)
    }

    @Test
    fun a_busy_destructive_button_swallows_the_tap() = assertBusySwallowsTheTap { busy, onClick ->
        DestructiveButton(text = "Creating…", onClick = onClick, busy = busy)
    }

    /**
     * Nothing is ever dimmed while it works (design notes §2): a working button keeps its colour,
     * stays enabled to the accessibility tree, and says what it is doing instead. The bar along its
     * bottom edge is the only thing that changes.
     */
    @Test
    fun a_busy_button_is_not_dimmed_but_says_it_is_working() = runComposeUiTest {
        setContent {
            HearthTheme(darkTheme = false) {
                CompositionLocalProvider(LocalHearthMotion provides StillMotion) {
                    PrimaryButton(text = "Creating…", onClick = {}, busy = true)
                }
            }
        }

        onNodeWithText("Creating…")
            .assertIsEnabled()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    getString(Res.string.a11y_working)
                )
            )
        onNodeWithTag(HearthProgressBarTag).assertIsDisplayed()
    }

    /** And no bar when it isn't working — the affordance only exists during the wait. */
    @Test
    fun a_button_at_rest_carries_no_bar() = runComposeUiTest {
        setContent {
            HearthTheme(darkTheme = false) {
                CompositionLocalProvider(LocalHearthMotion provides StillMotion) {
                    PrimaryButton(text = "Create", onClick = {})
                }
            }
        }

        onNodeWithTag(HearthProgressBarTag).assertDoesNotExist()
    }

    private fun assertAlwaysClickable(button: @Composable (onClick: () -> Unit) -> Unit) = runComposeUiTest {
        var clicks = 0
        setContent { HearthTheme(darkTheme = false) { button { clicks++ } } }

        onNodeWithText("Create")
            .assertIsEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHeightIsAtLeast(DefaultSizes.touchTarget)
            .performClick()

        assertEquals(1, clicks)
    }

    private fun assertBusySwallowsTheTap(
        button: @Composable (busy: Boolean, onClick: () -> Unit) -> Unit
    ) = runComposeUiTest {
        var clicks = 0
        setContent {
            HearthTheme(darkTheme = false) {
                CompositionLocalProvider(LocalHearthMotion provides StillMotion) {
                    button(true) { clicks++ }
                }
            }
        }

        onNodeWithText("Creating…").performClick()
        assertEquals(0, clicks)
    }
}
