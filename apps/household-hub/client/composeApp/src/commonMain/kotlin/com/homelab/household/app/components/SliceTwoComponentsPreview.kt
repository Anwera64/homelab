package com.homelab.household.app.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.homelab.household.app.theme.PreviewDayNight

/** Sample copy lives here, not in strings.xml: none of it ever reaches the app. */
private val PALETTE = listOf("#3C6E4E", "#C05638", "#6B655F", "#7A6672", "#A33B2A")

@PreviewDayNight
@Composable
private fun PinFieldPreview() {
    ComponentPreview {
        PinField(value = "", onValueChange = {
        }, label = "Choose a PIN", helper = "6 digits", modifier = Modifier.fillMaxWidth())
        PinField(value = "482913", onValueChange = {}, label = "Current PIN", modifier = Modifier.fillMaxWidth())
        PinField(
            value = "4829",
            onValueChange = {},
            label = "New PIN again",
            error = "Those two PINs are different",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@PreviewDayNight
@Composable
private fun CodeBoxesPreview() {
    ComponentPreview {
        CodeBoxes(code = "", onCodeChange = {}, contentDescription = "Invite code")
        CodeBoxes(code = "K7M", onCodeChange = {}, contentDescription = "Invite code")
        CodeBoxes(code = "K7M2QP", onCodeChange = {}, contentDescription = "Invite code")
    }
}

@PreviewDayNight
@Composable
private fun CodeCardPreview() {
    ComponentPreview {
        CodeCard(label = "INVITE CODE", code = "K7M2QP", expiry = "Expires in 14:52")
        CodeCard(label = "RESET CODE", code = "P4XN7T", expiry = "Expired")
    }
}

@PreviewDayNight
@Composable
private fun ConsequenceCardsPreview() {
    ComponentPreview {
        ConsequenceCards(
            erasedTitle = "ERASED FOR GOOD",
            erased =
                listOf(
                    "Every conversation, private ones included",
                    "Their space and what agents know about them",
                    "Their calendar connection and documents",
                ),
            staysTitle = "STAYS IN THE HOUSEHOLD",
            stays = listOf("Things they shared keep their name", "Agents they created pass to you"),
        )
    }
}

@PreviewDayNight
@Composable
private fun HearthSwitchPreview() {
    ComponentPreview {
        HearthSwitch(checked = false, onCheckedChange = {}, contentDescription = "Household admin")
        HearthSwitch(checked = true, onCheckedChange = {}, contentDescription = "Household admin")
    }
}

@PreviewDayNight
@Composable
private fun SettingsRowPreview() {
    ComponentPreview {
        SettingsRow(label = "Members", onClick = {}, modifier = Modifier.fillMaxWidth())
        SettingsRow(label = "Change PIN", caption = "Signs out your other devices", onClick = {
        }, modifier = Modifier.fillMaxWidth())
        SettingsRow(
            label = "Delete my account",
            caption = "Not while you are the only admin",
            enabled = false,
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@PreviewDayNight
@Composable
private fun ColourSwatchesPreview() {
    ComponentPreview {
        ColourSwatches(
            swatches = PALETTE,
            selected = PALETTE.first(),
            onSelect = {},
            swatchDescription = { index -> "Colour ${index + 1}" },
        )
        ColourSwatches(
            swatches = PALETTE,
            selected = PALETTE[1],
            onSelect = {},
            swatchDescription = { index -> "Colour ${index + 1}" },
            taken = setOf(PALETTE.first()),
        )
    }
}
