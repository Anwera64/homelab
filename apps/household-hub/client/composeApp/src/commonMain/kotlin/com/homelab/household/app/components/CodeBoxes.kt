package com.homelab.household.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme

/** What a code can be made of, matching the hub: no 0 or O, no 1, I or L. */
private const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

/**
 * A one-time code, one character per box: a mistyped character is obvious in a way it never is in a
 * single field. Typing goes through one hidden field, so the phone keyboard behaves normally.
 */
@Composable
fun CodeBoxes(
    code: String,
    onCodeChange: (String) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    length: Int = 6
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography

    BasicTextField(
        value = code,
        onValueChange = { typed ->
            onCodeChange(typed.uppercase().filter { it in CODE_ALPHABET }.take(length))
        },
        modifier = modifier.semantics { this.contentDescription = contentDescription },
        textStyle = type.codeHero,
        singleLine = true,
        cursorBrush = SolidColor(Color.Transparent),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            imeAction = ImeAction.Done
        ),
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm)) {
                repeat(length) { index ->
                    val character = code.getOrNull(index)
                    Box(
                        modifier = Modifier
                            .width(HearthTheme.size.touchTarget)
                            .height(HearthTheme.size.control)
                            .background(if (character == null) colors.surfaceAlt else colors.surface, HearthShapes.item)
                            .border(
                                width = if (character == null) HearthTheme.size.hairline else HearthTheme.size.emphasis,
                                color = if (character == null) colors.outlineSoft else colors.primary,
                                shape = HearthShapes.item
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (character != null) {
                            Text(character.toString(), style = type.codeHero, color = colors.textPrimary)
                        }
                    }
                }
            }
        }
    )
}
