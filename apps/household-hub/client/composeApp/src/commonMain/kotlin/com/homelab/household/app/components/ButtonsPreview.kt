package com.homelab.household.app.components

import androidx.compose.runtime.Composable
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.theme.DayNightPreviews

/** Each button kind, without and then with its icon. */
@DayNightPreviews
@Composable
private fun PrimaryButtonPreview() {
    ComponentPreview {
        PrimaryButton(text = "Create household", onClick = {})
        PrimaryButton(text = "New chat", onClick = {}, icon = HearthIcon.NewChat)
    }
}

@DayNightPreviews
@Composable
private fun SecondaryButtonPreview() {
    ComponentPreview {
        SecondaryButton(text = "Not now", onClick = {})
        SecondaryButton(text = "Try again", onClick = {}, icon = HearthIcon.Retry)
    }
}

@DayNightPreviews
@Composable
private fun DestructiveButtonPreview() {
    ComponentPreview {
        DestructiveButton(text = "Remove Emma", onClick = {})
        DestructiveButton(text = "Revoke access", onClick = {}, icon = HearthIcon.Revoke)
    }
}
