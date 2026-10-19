package com.homelab.household.app.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.testing.StillTheme
import kotlin.test.Test

/** Errors live under the field that caused them, and nothing typed is ever cleared (design notes §2). */
@OptIn(ExperimentalTestApi::class)
class HearthTextFieldTest {
    @Test
    fun `GIVEN a text field with an error WHEN it is shown THEN the error sits below the field and the field is marked as an error`() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    HearthTextField(value = "", onValueChange = {}, label = "Name", error = "Give it a name")
                }
            }

            onHearthTextField { seesTheErrorBelowTheField("Give it a name") }
        }

    @Test
    fun `GIVEN a text field without an error WHEN it is shown THEN the field is not marked as an error`() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    HearthTextField(value = "", onValueChange = {}, label = "Name")
                }
            }

            onHearthTextField { seesTheFieldNotMarkedAsAnError() }
        }

    @Test
    fun `GIVEN a text field with typed text WHEN an error appears THEN the typed text stays and the error shows`() =
        runComposeUiTest {
            var text by mutableStateOf("")
            var error by mutableStateOf<String?>(null)
            setContent {
                StillTheme {
                    HearthTextField(value = text, onValueChange = { text = it }, label = "Handle", error = error)
                }
            }

            onHearthTextField { typesIntoTheField("chef-bot") }
            error = "This handle is already used"
            waitForIdle()

            onHearthTextField {
                seesTheFieldContaining("chef-bot")
                seesText("This handle is already used")
            }
        }
}
