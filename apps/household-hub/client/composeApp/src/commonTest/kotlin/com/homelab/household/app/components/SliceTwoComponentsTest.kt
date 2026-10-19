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
    fun `GIVEN an empty PIN field WHEN digits and letters are typed THEN only the digits are kept`() =
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
    fun `GIVEN a PIN field with six digits WHEN another digit is typed THEN it still has six digits`() =
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
    fun `GIVEN empty code boxes WHEN a long lower-case code is typed THEN six upper-case characters are kept`() =
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
    fun `GIVEN code boxes holding three characters WHEN they are shown THEN each character appears`() =
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
    fun `GIVEN a code card with a code and an expiry WHEN it is shown THEN both are visible`() =
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
    fun `GIVEN consequence cards with erased and kept items WHEN they are shown THEN both lists are visible`() =
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
    fun `GIVEN a switch that is off WHEN it is tapped THEN it reports on`() =
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
    fun `GIVEN a settings row WHEN it is tapped THEN it opens once`() =
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
    fun `GIVEN a disabled settings row with a caption WHEN it is tapped THEN the caption shows and nothing opens`() =
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
    fun `GIVEN colour swatches with one taken WHEN the taken swatch is tapped THEN the choice does not change`() =
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
    fun `GIVEN colour swatches with none taken WHEN another swatch is tapped THEN that colour is chosen`() =
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
