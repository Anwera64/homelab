package com.homelab.household.app.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme

// Buttons deliberately have no `enabled` parameter: a primary action is never dimmed,
// it explains what's missing when tapped (design notes §2).

private val ButtonMinHeight = 52.dp
private val ButtonPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp)

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: HearthIcon? = null) {
    val colors = HearthTheme.colors
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = ButtonMinHeight)
            .shadow(
                elevation = 10.dp,
                shape = HearthShapes.button,
                ambientColor = colors.primary.copy(alpha = 0.30f),
                spotColor = colors.primary.copy(alpha = 0.30f)
            ),
        shape = HearthShapes.button,
        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
        contentPadding = ButtonPadding
    ) {
        ButtonContent(text, icon)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: HearthIcon? = null) {
    OutlinedActionButton(text, onClick, modifier, icon, HearthTheme.colors.outline, HearthTheme.colors.textMuted)
}

/** Outlined in error red rather than filled: destructive, but never the page's purpose. */
@Composable
fun DestructiveButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: HearthIcon? = null) {
    OutlinedActionButton(text, onClick, modifier, icon, HearthTheme.colors.error, HearthTheme.colors.error)
}

@Composable
private fun OutlinedActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    icon: HearthIcon?,
    borderColor: Color,
    contentColor: Color
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = ButtonMinHeight),
        shape = HearthShapes.button,
        border = BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
        contentPadding = ButtonPadding
    ) {
        ButtonContent(text, icon)
    }
}

@Composable
private fun RowScope.ButtonContent(text: String, icon: HearthIcon?) {
    if (icon != null) {
        HearthIconImage(icon = icon, contentDescription = null, active = true, size = 19.dp)
        Spacer(Modifier.width(9.dp))
    }
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = HearthTheme.fonts.inter)
}
