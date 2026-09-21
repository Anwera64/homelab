package com.homelab.household.app.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.testing.StillTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The pieces the members screens are built from: a PIN, a code, a colour, a switch and a row. */
@OptIn(ExperimentalTestApi::class)
class SliceTwoComponentsTest {

    @Test
    fun a_pin_field_takes_six_digits_and_nothing_else() = runComposeUiTest {
        var pin = ""
        setContent {
            StillTheme {
                PinField(value = pin, onValueChange = { pin = it }, label = "Choose a PIN")
            }
        }

        onNode(hasSetTextAction()).performTextInput("48a29-13")

        assertEquals("482913", pin)
    }

    @Test
    fun a_pin_field_never_grows_past_six_digits() = runComposeUiTest {
        var pin = "482913"
        setContent {
            StillTheme {
                PinField(value = pin, onValueChange = { pin = it }, label = "Choose a PIN")
            }
        }

        onNode(hasSetTextAction()).performTextInput("7")

        assertEquals(6, pin.length)
    }

    @Test
    fun code_boxes_upper_case_what_is_typed_and_stop_at_six() = runComposeUiTest {
        var code = ""
        setContent {
            StillTheme {
                CodeBoxes(code = code, onCodeChange = { code = it }, contentDescription = "Invite code")
            }
        }

        onNodeWithContentDescription("Invite code").performTextInput("k7m2qp9")

        assertEquals("K7M2QP", code)
    }

    @Test
    fun code_boxes_draw_a_box_per_character_typed() = runComposeUiTest {
        setContent {
            StillTheme {
                CodeBoxes(code = "K7M", onCodeChange = {}, contentDescription = "Invite code")
            }
        }

        onNodeWithText("K").assertIsDisplayed()
        onNodeWithText("7").assertIsDisplayed()
        onNodeWithText("M").assertIsDisplayed()
    }

    @Test
    fun a_code_card_shows_the_code_and_how_long_it_lasts() = runComposeUiTest {
        setContent {
            StillTheme {
                CodeCard(label = "INVITE CODE", code = "K7M2QP", expiry = "Expires in 14:52")
            }
        }

        onNodeWithText("K7M2QP").assertIsDisplayed()
        onNodeWithText("Expires in 14:52").assertIsDisplayed()
    }

    @Test
    fun consequence_cards_list_what_goes_and_what_stays() = runComposeUiTest {
        setContent {
            StillTheme {
                ConsequenceCards(
                    erasedTitle = "Erased for good",
                    erased = listOf("Every conversation he's had"),
                    staysTitle = "Stays in the household",
                    stays = listOf("Things he shared keep his name on them")
                )
            }
        }

        onNodeWithText("Erased for good").assertIsDisplayed()
        onNodeWithText("Every conversation he's had").assertIsDisplayed()
        onNodeWithText("Stays in the household").assertIsDisplayed()
        onNodeWithText("Things he shared keep his name on them").assertIsDisplayed()
    }

    @Test
    fun a_switch_reports_the_value_it_would_become() = runComposeUiTest {
        var admin = false
        setContent {
            StillTheme {
                HearthSwitch(checked = admin, onCheckedChange = { admin = it }, contentDescription = "Household admin")
            }
        }

        onNodeWithContentDescription("Household admin").performClick()

        assertEquals(true, admin)
    }

    @Test
    fun a_settings_row_opens_what_it_names() = runComposeUiTest {
        var opened = 0
        setContent {
            StillTheme {
                SettingsRow(label = "Members", onClick = { opened++ })
            }
        }

        onNodeWithText("Members").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun a_row_that_cannot_be_opened_says_why_and_does_nothing() = runComposeUiTest {
        var opened = 0
        setContent {
            StillTheme {
                SettingsRow(
                    label = "Delete my account",
                    caption = "Not while you're the only admin",
                    enabled = false,
                    onClick = { opened++ }
                )
            }
        }

        onNodeWithText("Not while you're the only admin").assertIsDisplayed()
        onNodeWithText("Delete my account").performClick()

        assertEquals(0, opened)
    }

    @Test
    fun a_taken_colour_cannot_be_chosen() = runComposeUiTest {
        var chosen = "#3C6E4E"
        setContent {
            StillTheme {
                ColourSwatches(
                    swatches = listOf("#3C6E4E", "#C05638"),
                    selected = chosen,
                    taken = setOf("#C05638"),
                    onSelect = { chosen = it },
                    swatchDescription = { index -> "Colour ${index + 1}" }
                )
            }
        }

        onNodeWithContentDescription("Colour 2").performClick()

        assertEquals("#3C6E4E", chosen)
    }

    @Test
    fun a_free_colour_can_be_chosen() = runComposeUiTest {
        var chosen = "#3C6E4E"
        setContent {
            StillTheme {
                ColourSwatches(
                    swatches = listOf("#3C6E4E", "#C05638"),
                    selected = chosen,
                    taken = emptySet(),
                    onSelect = { chosen = it },
                    swatchDescription = { index -> "Colour ${index + 1}" }
                )
            }
        }

        onNodeWithContentDescription("Colour 2").performClick()

        assertEquals("#C05638", chosen)
    }
}
