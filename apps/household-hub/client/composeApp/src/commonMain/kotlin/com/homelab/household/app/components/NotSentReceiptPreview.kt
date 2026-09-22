package com.homelab.household.app.components

import androidx.compose.runtime.Composable
import com.homelab.household.app.theme.PreviewDayNight

/** The line under a question the hub never got. */
@PreviewDayNight
@Composable
private fun NotSentReceiptPreview() {
    ComponentPreview {
        NotSentReceipt(onRetry = {})
    }
}
