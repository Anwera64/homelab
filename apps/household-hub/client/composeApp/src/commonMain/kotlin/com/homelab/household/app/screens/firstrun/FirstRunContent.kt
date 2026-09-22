package com.homelab.household.app.screens.firstrun

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.homelab.household.app.components.ColourSwatches
import com.homelab.household.app.components.HearthScaffold
import com.homelab.household.app.components.HearthTextField
import com.homelab.household.app.components.PinField
import com.homelab.household.app.components.PrimaryButton
import com.homelab.household.app.components.SecondaryButton
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.first_run_colour_helper
import com.homelab.household.app.resources.first_run_colour_label
import com.homelab.household.app.resources.first_run_colour_swatch
import com.homelab.household.app.resources.first_run_create
import com.homelab.household.app.resources.first_run_create_detail
import com.homelab.household.app.resources.first_run_detail
import com.homelab.household.app.resources.first_run_failed_already_set_up
import com.homelab.household.app.resources.first_run_failed_unknown
import com.homelab.household.app.resources.first_run_failed_unreachable
import com.homelab.household.app.resources.first_run_name_helper
import com.homelab.household.app.resources.first_run_name_label
import com.homelab.household.app.resources.first_run_name_missing
import com.homelab.household.app.resources.first_run_name_too_long
import com.homelab.household.app.resources.first_run_overline
import com.homelab.household.app.resources.first_run_pin_helper
import com.homelab.household.app.resources.first_run_pin_label
import com.homelab.household.app.resources.first_run_pin_not_six_digits
import com.homelab.household.app.resources.first_run_sign_in_instead
import com.homelab.household.app.resources.first_run_title
import com.homelab.household.app.resources.join_name_taken
import com.homelab.household.app.theme.DayNightPreviews
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.app.util.hexColor
import com.homelab.household.domain.model.MemberName
import com.homelab.household.presentation.firstrun.AvatarPalette
import com.homelab.household.presentation.firstrun.FirstRunFailure
import com.homelab.household.presentation.firstrun.FirstRunUiState
import com.homelab.household.presentation.firstrun.NameError
import com.homelab.household.presentation.firstrun.PinError
import org.jetbrains.compose.resources.stringResource

/**
 * First run: name, PIN and colour, with "Create household" pinned under the form. The form
 * scrolls; the button never dims, and every error sits under what caused it.
 *
 * Stateless — [FirstRunScreen] owns the ViewModel.
 */
@Composable
fun FirstRunContent(
    state: FirstRunUiState,
    onNameChange: (String) -> Unit,
    onPinChange: (String) -> Unit,
    onColourSelect: (String) -> Unit,
    onCreate: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    val focusManager = LocalFocusManager.current

    HearthScaffold(
        modifier = modifier,
        gutter = HearthTheme.spacing.xxl,
        bottomBar = { CreateFooter(state = state, onCreate = onCreate, onSignIn = onSignIn) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    // iOS's numeric keypad (the PIN field's NumberPassword type) has no return key,
                    // so tapping away from the fields is the only way to dismiss it there. A tap that
                    // lands on a real control (a field, a swatch, the footer button) is consumed by
                    // that control first and never reaches this gesture.
                    .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }
                    .verticalScroll(rememberScrollState())
                    .padding(padding),
            verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xxl),
        ) {
            Column(
                modifier = Modifier.padding(top = HearthTheme.spacing.huge),
                verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
            ) {
                Text(stringResource(Res.string.first_run_overline), style = type.overline, color = colors.textMuted)
                Text(stringResource(Res.string.first_run_title), style = type.hero, color = colors.textPrimary)
                Text(stringResource(Res.string.first_run_detail), style = type.body, color = colors.textMuted)
            }

            HearthTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = stringResource(Res.string.first_run_name_label),
                helper = if (state.nameError == null) stringResource(Res.string.first_run_name_helper) else null,
                error = state.nameError?.let { nameErrorText(it) },
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next,
                    ),
            )

            PinField(
                value = state.pin,
                onValueChange = onPinChange,
                label = stringResource(Res.string.first_run_pin_label),
                helper = if (state.pinError == null) stringResource(Res.string.first_run_pin_helper) else null,
                error = state.pinError?.let { pinErrorText(it) },
            )

            ColourPicker(selected = state.colour, onSelect = onColourSelect)
        }
    }
}

@Composable
private fun ColourPicker(
    selected: String,
    onSelect: (String) -> Unit,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    val count = AvatarPalette.swatches.size

    Column(verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md)) {
        Text(stringResource(Res.string.first_run_colour_label), style = type.labelStrong, color = colors.textMuted)
        ColourSwatches(
            swatches = AvatarPalette.swatches,
            selected = selected,
            onSelect = onSelect,
            swatchDescription = { index -> stringResource(Res.string.first_run_colour_swatch, index + 1, count) },
        )
        Text(stringResource(Res.string.first_run_colour_helper), style = type.caption, color = colors.textMuted)
    }
}

@Composable
private fun CreateFooter(
    state: FirstRunUiState,
    onCreate: () -> Unit,
    onSignIn: () -> Unit,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.canvas)
                // A bar pads its own insets: the footer sits above the gesture bar and the keyboard.
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                ).padding(horizontal = HearthTheme.spacing.xxl, vertical = HearthTheme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
    ) {
        PrimaryButton(
            text = stringResource(Res.string.first_run_create),
            onClick = onCreate,
            modifier = Modifier.fillMaxWidth(),
        )

        val failure = state.failure
        if (failure == null) {
            Text(
                text = stringResource(Res.string.first_run_create_detail),
                style = type.caption,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm)) {
                HearthIconImage(
                    icon = HearthIcon.Error,
                    contentDescription = null,
                    size = HearthTheme.size.iconSm,
                    tint = colors.error,
                    modifier = Modifier.padding(top = HearthTheme.spacing.xxs),
                )
                Text(failureText(failure), style = type.caption, color = colors.error)
            }
            if (failure == FirstRunFailure.AlreadySetUp) {
                SecondaryButton(
                    text = stringResource(Res.string.first_run_sign_in_instead),
                    onClick = onSignIn,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun nameErrorText(error: NameError): String =
    when (error) {
        NameError.Missing -> stringResource(Res.string.first_run_name_missing)

        NameError.TooLong -> stringResource(Res.string.first_run_name_too_long, MemberName.MAX_LENGTH)

        // First run has nobody to clash with, so the hub never reports a taken name here.
        NameError.Taken -> stringResource(Res.string.join_name_taken)
    }

@Composable
private fun pinErrorText(error: PinError): String =
    when (error) {
        PinError.NotSixDigits -> stringResource(Res.string.first_run_pin_not_six_digits)
    }

@Composable
private fun failureText(failure: FirstRunFailure): String =
    when (failure) {
        FirstRunFailure.Unreachable -> stringResource(Res.string.first_run_failed_unreachable)
        FirstRunFailure.AlreadySetUp -> stringResource(Res.string.first_run_failed_already_set_up)
        FirstRunFailure.Unknown -> stringResource(Res.string.first_run_failed_unknown)
    }

@DayNightPreviews
@Composable
private fun FirstRunContentPreview(
    @PreviewParameter(FirstRunUiStateProvider::class) state: FirstRunUiState,
) {
    HearthTheme {
        FirstRunContent(
            state = state,
            onNameChange = {},
            onPinChange = {},
            onColourSelect = {},
            onCreate = {},
            onSignIn = {},
        )
    }
}
