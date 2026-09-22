package com.homelab.household.app.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.domain.model.Pin

/**
 * A PIN as every screen asks for one: six digits, masked, on the mono scale. Anything that is not a
 * digit is ignored rather than refused, and the sixth digit is the last one it takes.
 */
@Composable
fun PinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    contentDescription: String? = null,
    helper: String? = null,
    error: String? = null,
    imeAction: ImeAction = ImeAction.Done,
) {
    HearthTextField(
        value = value,
        onValueChange = { typed -> onValueChange(typed.filter { it in '0'..'9' }.take(Pin.LENGTH)) },
        label = label,
        modifier = modifier,
        placeholder = placeholder,
        contentDescription = contentDescription,
        helper = helper,
        error = error,
        textStyle = HearthTheme.typography.monoLg,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = imeAction),
        visualTransformation = PasswordVisualTransformation(),
    )
}
