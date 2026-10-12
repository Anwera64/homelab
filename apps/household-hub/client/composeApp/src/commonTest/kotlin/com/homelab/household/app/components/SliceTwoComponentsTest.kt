package com.homelab.household.app.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.testing.StillTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The pieces the members screens are built from: a PIN, a code, a colour, a switch and a row. */
@OptIn(ExperimentalTestApi::class)
class SliceTwoComponentsTest {
    @Test
    fun a_pin_field_takes_six_digits_and_nothing_else() =
        runComposeUiTest {
            var pin = ""
            setContent {
                StillTheme {
                    PinField(value = pin, onValueChange = { pin = it }, label = "Choose a PIN")
                }
            }

            onSliceTwoComponents { typesIntoTheField("48a29-13") }

            assertEquals("482913", pin)
        }

    @Test
    fun a_pin_field_never_grows_past_six_digits() =
        runComposeUiTest {
            var pin = "482913"
            setContent {
                StillTheme {
                    PinField(value = pin, onValueChange = { pin = it }, label = "Choose a PIN")
                }
            }

            onSliceTwoComponents { typesIntoTheField("7") }

            assertEquals(6, pin.length)
        }

    @Test
    fun code_boxes_upper_case_what_is_typed_and_stop_at_six() =
        runComposeUiTest {
            var code = ""
            setContent {
                StillTheme {
                    CodeBoxes(code = code, onCodeChange = { code = it }, contentDescription = "Invite code")
                }
            }

            onSliceTwoComponents { typesIntoTheControlDescribed("Invite code", "k7m2qp9") }

            assertEquals("K7M2QP", code)
        }

    @Test
    fun code_boxes_draw_a_box_per_character_typed() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    CodeBoxes(code = "K7M", onCodeChange = {}, contentDescription = "Invite code")
                }
            }

            onSliceTwoComponents {
                seesText("K")
                seesText("7")
                seesText("M")
            }
        }

    @Test
    fun a_code_card_shows_the_code_and_how_long_it_lasts() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    CodeCard(label = "INVITE CODE", code = "K7M2QP", expiry = "Expires in 14:52")
                }
            }

            onSliceTwoComponents {
                seesText("K7M2QP")
                seesText("Expires in 14:52")
            }
        }

    @Test
    fun consequence_cards_list_what_goes_and_what_stays() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    ConsequenceCards(
                        erasedTitle = "Erased for good",
                        erased = listOf("Every conversation he's had"),
                        staysTitle = "Stays in the household",
                        stays = listOf("Things he shared keep his name on them"),
                    )
                }
            }

            onSliceTwoComponents {
                seesText("Erased for good")
                seesText("Every conversation he's had")
                seesText("Stays in the household")
                seesText("Things he shared keep his name on them")
            }
        }

    @Test
    fun a_switch_reports_the_value_it_would_become() =
        runComposeUiTest {
            var admin = false
            setContent {
                StillTheme {
                    HearthSwitch(
                        checked = admin,
                        onCheckedChange = { admin = it },
                        contentDescription = "Household admin",
                    )
                }
            }

            onSliceTwoComponents { tapsTheControlDescribed("Household admin") }

            assertEquals(true, admin)
        }

    @Test
    fun a_settings_row_opens_what_it_names() =
        runComposeUiTest {
            var opened = 0
            setContent {
                StillTheme {
                    SettingsRow(label = "Members", onClick = { opened++ })
                }
            }

            onSliceTwoComponents { tapsText("Members") }

            assertEquals(1, opened)
        }

    @Test
    fun a_row_that_cannot_be_opened_says_why_and_does_nothing() =
        runComposeUiTest {
            var opened = 0
            setContent {
                StillTheme {
                    SettingsRow(
                        label = "Delete my account",
                        caption = "Not while you're the only admin",
                        enabled = false,
                        onClick = { opened++ },
                    )
                }
            }

            onSliceTwoComponents {
                seesText("Not while you're the only admin")
                tapsText("Delete my account")
            }

            assertEquals(0, opened)
        }

    @Test
    fun a_taken_colour_cannot_be_chosen() =
        runComposeUiTest {
            var chosen = "#3C6E4E"
            setContent {
                StillTheme {
                    ColourSwatches(
                        swatches = listOf("#3C6E4E", "#C05638"),
                        selected = chosen,
                        taken = setOf("#C05638"),
                        onSelect = { chosen = it },
                        swatchDescription = { index -> "Colour ${index + 1}" },
                    )
                }
            }

            onSliceTwoComponents { tapsTheControlDescribed("Colour 2") }

            assertEquals("#3C6E4E", chosen)
        }

    @Test
    fun a_free_colour_can_be_chosen() =
        runComposeUiTest {
            var chosen = "#3C6E4E"
            setContent {
                StillTheme {
                    ColourSwatches(
                        swatches = listOf("#3C6E4E", "#C05638"),
                        selected = chosen,
                        taken = emptySet(),
                        onSelect = { chosen = it },
                        swatchDescription = { index -> "Colour ${index + 1}" },
                    )
                }
            }

            onSliceTwoComponents { tapsTheControlDescribed("Colour 2") }

            assertEquals("#C05638", chosen)
        }
}
