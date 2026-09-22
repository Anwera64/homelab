package com.homelab.household.app.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.homelab.household.app.theme.PreviewDayNight

/** The field's states. With an error the helper is left out, as callers hide it then. */
@PreviewDayNight
@Composable
private fun HearthTextFieldEmptyPreview() {
    ComponentPreview {
        HearthTextField(
            value = "",
            onValueChange = {},
            label = "Household name",
            placeholder = "The Soares home",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@PreviewDayNight
@Composable
private fun HearthTextFieldHelperPreview() {
    ComponentPreview {
        HearthTextField(
            value = "",
            onValueChange = {},
            label = "Your name",
            placeholder = "Emma",
            helper = "This is how the household sees you",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@PreviewDayNight
@Composable
private fun HearthTextFieldErrorPreview() {
    ComponentPreview {
        HearthTextField(
            value = "K7M2",
            onValueChange = {},
            label = "Invite code",
            placeholder = "K7M2QP",
            error = "Invite codes are 6 characters",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@PreviewDayNight
@Composable
private fun HearthTextFieldFilledPreview() {
    ComponentPreview {
        HearthTextField(
            value = "Emma",
            onValueChange = {},
            label = "Your name",
            placeholder = "Emma",
            helper = "This is how the household sees you",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
