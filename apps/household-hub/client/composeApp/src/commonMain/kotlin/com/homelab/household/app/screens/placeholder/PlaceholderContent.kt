package com.homelab.household.app.screens.placeholder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.sign_in_detail
import com.homelab.household.app.resources.sign_in_title
import com.homelab.household.app.theme.HearthTheme
import org.jetbrains.compose.resources.stringResource

/**
 * A destination that navigation already reaches and a later slice still has to build.
 * Replace the entry in `AppNavHost` with the real screen when that slice lands.
 */
@Composable
fun PlaceholderContent(
    title: String,
    detail: String,
    modifier: Modifier = Modifier
) {
    val colors = HearthTheme.colors

    Column(
        modifier = modifier.fillMaxSize().background(colors.canvas).padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterVertically)
    ) {
        Text(
            text = title,
            fontFamily = HearthTheme.fonts.outfit,
            fontSize = 27.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            textAlign = TextAlign.Center
        )
        Text(
            text = detail,
            fontFamily = HearthTheme.fonts.inter,
            fontSize = 14.5.sp,
            lineHeight = 22.sp,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 290.dp)
        )
    }
}

@Preview
@Composable
private fun PlaceholderContentPreview() {
    HearthTheme(darkTheme = false) {
        PlaceholderContent(
            title = stringResource(Res.string.sign_in_title),
            detail = stringResource(Res.string.sign_in_detail)
        )
    }
}
