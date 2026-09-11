package com.homelab.household.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme

/**
 * Labelled field whose error sits directly under it, with a red edge on the field.
 * It is stateless and never touches [value]: nothing typed is ever cleared (design notes §2).
 */
@Composable
fun HearthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    helper: String? = null,
    error: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    textStyle: TextStyle? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    val colors = HearthTheme.colors
    val inter = HearthTheme.fonts.inter
    val fieldStyle = textStyle ?: TextStyle(fontFamily = inter, fontSize = 15.sp, lineHeight = 22.sp)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, fontFamily = inter, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.textMuted)

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { if (error != null) error(error) },
            textStyle = fieldStyle.copy(color = colors.textPrimary),
            singleLine = singleLine,
            minLines = minLines,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            cursorBrush = SolidColor(colors.primary),
            decorationBox = { innerField ->
                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .background(colors.surface, HearthShapes.item)
                        .border(
                            width = if (error != null) 1.5.dp else 1.dp,
                            color = if (error != null) colors.error else colors.outline,
                            shape = HearthShapes.item
                        )
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = fieldStyle, color = colors.textMuted)
                    }
                    innerField()
                }
            }
        )

        if (helper != null) {
            Text(helper, fontFamily = inter, fontSize = 11.5.sp, color = colors.textMuted)
        }
        if (error != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                HearthIconImage(
                    icon = HearthIcon.Error,
                    contentDescription = null,
                    size = 14.dp,
                    tint = colors.error,
                    modifier = Modifier.padding(top = 1.dp)
                )
                Text(error, fontFamily = inter, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.error)
            }
        }
    }
}
