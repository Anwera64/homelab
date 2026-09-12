package com.homelab.household.app.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.theme.DefaultSizes
import com.homelab.household.app.theme.HearthTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Primary buttons are never disabled (design notes §2): they have no `enabled` parameter,
 * so a tap always reaches `onClick`, where the screen explains what's missing.
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
}
