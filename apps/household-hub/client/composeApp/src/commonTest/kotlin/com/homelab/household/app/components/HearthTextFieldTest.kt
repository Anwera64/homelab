package com.homelab.household.app.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.theme.HearthTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/** Errors live under the field that caused them, and nothing typed is ever cleared (design notes §2). */
@OptIn(ExperimentalTestApi::class)
class HearthTextFieldTest {

    @Test
    fun error_sits_below_the_field_and_marks_it_as_an_error() = runComposeUiTest {
        setContent {
            HearthTheme(darkTheme = false) {
                HearthTextField(value = "", onValueChange = {}, label = "Name", error = "Give it a name")
            }
        }

        val field = onNode(hasSetTextAction())
        val error = onNodeWithText("Give it a name").assertIsDisplayed()

        field.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        val fieldBottom = field.getUnclippedBoundsInRoot().bottom
        val errorTop = error.getUnclippedBoundsInRoot().top
        assertTrue(errorTop >= fieldBottom, "Error ($errorTop) should sit below the field ($fieldBottom)")
    }

    @Test
    fun without_an_error_the_field_is_not_marked() = runComposeUiTest {
        setContent {
            HearthTheme(darkTheme = false) {
                HearthTextField(value = "", onValueChange = {}, label = "Name")
            }
        }

        onNode(hasSetTextAction()).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
    }

    @Test
    fun typed_text_survives_an_error_appearing() = runComposeUiTest {
        var text by mutableStateOf("")
        var error by mutableStateOf<String?>(null)
        setContent {
            HearthTheme(darkTheme = false) {
                HearthTextField(value = text, onValueChange = { text = it }, label = "Handle", error = error)
            }
        }

        onNode(hasSetTextAction()).performTextInput("chef-bot")
        error = "This handle is already used"
        waitForIdle()

        onNode(hasSetTextAction()).assertTextContains("chef-bot")
        onNodeWithText("This handle is already used").assertIsDisplayed()
    }
}
