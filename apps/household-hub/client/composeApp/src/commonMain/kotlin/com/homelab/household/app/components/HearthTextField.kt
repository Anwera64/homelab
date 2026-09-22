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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
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
    contentDescription: String? = null,
    helper: String? = null,
    error: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    textStyle: TextStyle? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    val fieldStyle = textStyle ?: type.bodyLarge

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm)) {
        Text(label, style = type.labelStrong, color = colors.textMuted)

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics {
                        if (error != null) error(error)
                        if (contentDescription != null) this.contentDescription = contentDescription
                    },
            textStyle = fieldStyle.copy(color = colors.textPrimary),
            singleLine = singleLine,
            minLines = minLines,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            cursorBrush = SolidColor(colors.primary),
            decorationBox = { innerField ->
                Box(
                    modifier =
                        Modifier
                            .heightIn(min = HearthTheme.size.touchTarget)
                            .background(colors.surface, HearthShapes.item)
                            .border(
                                width = if (error != null) HearthTheme.size.emphasis else HearthTheme.size.hairline,
                                color = if (error != null) colors.error else colors.outline,
                                shape = HearthShapes.item,
                            ).padding(horizontal = HearthTheme.spacing.lg, vertical = HearthTheme.spacing.md),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = fieldStyle, color = colors.textMuted)
                    }
                    innerField()
                }
            },
        )

        if (helper != null) {
            Text(helper, style = type.caption, color = colors.textMuted)
        }
        if (error != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm),
                verticalAlignment = Alignment.Top,
            ) {
                HearthIconImage(
                    icon = HearthIcon.Error,
                    contentDescription = null,
                    size = HearthTheme.size.iconSm,
                    tint = colors.error,
                    modifier = Modifier.padding(top = HearthTheme.spacing.xxs),
                )
                Text(error, style = type.caption, color = colors.error)
            }
        }
    }
}
