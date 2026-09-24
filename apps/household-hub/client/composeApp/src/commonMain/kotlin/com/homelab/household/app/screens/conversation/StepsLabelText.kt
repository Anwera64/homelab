package com.homelab.household.app.screens.conversation

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.conversation_steps
import com.homelab.household.app.resources.steps_label_and
import com.homelab.household.app.resources.steps_label_failed
import com.homelab.household.app.resources.steps_label_more
import com.homelab.household.app.resources.steps_phrase_added
import com.homelab.household.app.resources.steps_phrase_added_plain
import com.homelab.household.app.resources.steps_phrase_checked_calendar
import com.homelab.household.app.resources.steps_phrase_read
import com.homelab.household.app.resources.steps_phrase_searched
import com.homelab.household.app.resources.steps_phrase_searched_times
import com.homelab.household.app.resources.steps_phrase_searched_twice
import com.homelab.household.app.theme.HearthTheme
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A fold's label in words: "Searched the web, read 2 pages · 1 failed", what failed in red.
 * [count] is how many steps the fold holds, said instead when nothing in it is worth naming.
 */
@Composable
fun stepsLabelText(
    label: StepsLabel,
    count: Int,
): AnnotatedString {
    val error = HearthTheme.colors.error
    val only = label.phrases.singleOrNull()
    if (only is StepPhrase.Failed) {
        return buildAnnotatedString {
            withStyle(SpanStyle(color = error)) { append(stringResource(toolLabel(only.tool).failed)) }
        }
    }
    if (label.phrases.isEmpty()) {
        return AnnotatedString(
            pluralStringResource(Res.plurals.conversation_steps, count, count),
        )
    }

    var said = phrase(label.phrases.first())
    label.phrases.drop(1).forEach { said = stringResource(Res.string.steps_label_and, said, phrase(it)) }
    if (label.more > 0) said = stringResource(Res.string.steps_label_more, said, label.more)
    said = said.replaceFirstChar { it.uppercase() }

    return buildAnnotatedString {
        append(said)
        if (label.failed > 0) {
            append(SEPARATOR)
            withStyle(SpanStyle(color = error)) { append(stringResource(Res.string.steps_label_failed, label.failed)) }
        }
    }
}

@Composable
private fun phrase(phrase: StepPhrase): String =
    when (phrase) {
        is StepPhrase.Searched -> {
            when (phrase.times) {
                1 -> stringResource(Res.string.steps_phrase_searched)
                2 -> stringResource(Res.string.steps_phrase_searched_twice)
                else -> stringResource(Res.string.steps_phrase_searched_times, phrase.times)
            }
        }

        is StepPhrase.Read -> {
            pluralStringResource(Res.plurals.steps_phrase_read, phrase.pages, phrase.pages)
        }

        StepPhrase.CheckedCalendar -> {
            stringResource(Res.string.steps_phrase_checked_calendar)
        }

        is StepPhrase.Added -> {
            phrase.title?.let { stringResource(Res.string.steps_phrase_added, it) }
                ?: stringResource(Res.string.steps_phrase_added_plain)
        }

        is StepPhrase.Did -> {
            stringResource(toolLabel(phrase.tool).done).replaceFirstChar { it.lowercase() }
        }

        is StepPhrase.Failed -> {
            stringResource(toolLabel(phrase.tool).failed).replaceFirstChar { it.lowercase() }
        }
    }

// The same separator the step lines use between what happened and more about it.
private const val SEPARATOR = " · "
