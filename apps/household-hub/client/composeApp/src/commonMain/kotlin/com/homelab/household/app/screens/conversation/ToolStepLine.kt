package com.homelab.household.app.screens.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import com.homelab.household.app.components.ToolRecordLine
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.tool_read_page_done_named
import com.homelab.household.app.resources.tool_read_page_failed_named
import com.homelab.household.app.resources.tool_results_hide
import com.homelab.household.app.resources.tool_results_show
import com.homelab.household.app.resources.tool_search_done_query
import com.homelab.household.app.resources.tool_search_results
import com.homelab.household.app.resources.tool_step_named
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.domain.model.AnswerPart
import com.homelab.household.domain.model.ToolSource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One tool step in an answer, drawn from what it says about itself ([toolStep], #40).
 *
 * A search opens in place to what it found, each result a link; a page read names its page, the
 * title a link. A failed step keeps its tool's icon, in red, and says why when that is known.
 */
@Composable
fun ToolStepLine(
    part: AnswerPart.ToolDone,
    modifier: Modifier = Modifier,
) {
    ToolStepLine(toolStep(part), modifier)
}

@Composable
fun ToolStepLine(
    part: AnswerPart.ToolFailed,
    modifier: Modifier = Modifier,
) {
    ToolStepLine(toolStep(part), modifier)
}

@Composable
private fun ToolStepLine(
    step: ToolStep,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val iconTint = if (step.failed) colors.error else colors.textMuted
    val text = stepText(step)

    if (step.results.isEmpty()) {
        ToolRecordLine(icon = step.icon, text = text, modifier = modifier, iconTint = iconTint)
        return
    }

    // A search is the one step that opens: to what it found, in place, like the steps fold does.
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xs)) {
        ToolRecordLine(
            icon = step.icon,
            text = text,
            iconTint = iconTint,
            onClick = { open = !open },
            onClickLabel = stringResource(if (open) Res.string.tool_results_hide else Res.string.tool_results_show),
            trailing = {
                HearthIconImage(
                    icon = HearthIcon.ChevronRight,
                    contentDescription = null,
                    size = HearthTheme.size.iconSm,
                    tint = colors.textMuted,
                    modifier = Modifier.rotate(if (open) -90f else 90f),
                )
            },
        )
        if (open) Results(step.results)
    }
}

/** What a search found: each title a link, its site under it, lined up with the line's words. */
@Composable
private fun Results(results: List<ToolSource>) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    val indent = HearthTheme.spacing.xs + HearthTheme.size.iconSm + HearthTheme.spacing.sm
    Column(
        modifier = Modifier.padding(start = indent),
        verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm),
    ) {
        results.forEach { result ->
            Column(verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xxs)) {
                Text(text = linked(result.title.ifBlank { hostOf(result.url) }, result.url), style = type.label)
                Text(text = hostOf(result.url), style = type.monoSm, color = colors.textMuted)
            }
        }
    }
}

@Composable
private fun stepText(step: ToolStep): AnnotatedString =
    when (val words = step.words) {
        is StepWords.Plain -> {
            AnnotatedString(stringResource(words.words))
        }

        is StepWords.Searched -> {
            val results = pluralStringResource(Res.plurals.tool_search_results, words.count, words.count)
            AnnotatedString(stringResource(Res.string.tool_search_done_query, words.query, results))
        }

        is StepWords.Read -> {
            val line = stringResource(Res.string.tool_read_page_done_named, words.title, words.host)
            linkedWithin(line, words.title, words.url)
        }

        is StepWords.Named -> {
            AnnotatedString(stringResource(Res.string.tool_step_named, stringResource(words.words), words.name))
        }

        is StepWords.CouldNotRead -> {
            failure(
                stringResource(Res.string.tool_read_page_failed_named, words.host),
                words.reason?.let { stringResource(it) },
            )
        }

        is StepWords.Failed -> {
            failure(stringResource(words.words), words.reason?.let { stringResource(it) })
        }
    }

/** What failed, in red; why, when it is known, stays muted after it. */
@Composable
private fun failure(
    what: String,
    why: String?,
): AnnotatedString {
    val colors = HearthTheme.colors
    if (why == null) return buildAnnotatedString { withStyle(SpanStyle(color = colors.error)) { append(what) } }
    val line = stringResource(Res.string.tool_step_named, what, why)
    val start = line.indexOf(what).coerceAtLeast(0)
    return buildAnnotatedString {
        append(line)
        addStyle(SpanStyle(color = colors.error), start, start + what.length)
    }
}

@Composable
private fun linked(
    text: String,
    url: String,
): AnnotatedString = linkedWithin(text, text, url)

/** [line] with [title] inside it made a link to [url]. */
@Composable
private fun linkedWithin(
    line: String,
    title: String,
    url: String,
): AnnotatedString {
    val styles = TextLinkStyles(SpanStyle(color = HearthTheme.colors.primary))
    val start = line.indexOf(title)
    return buildAnnotatedString {
        if (start < 0) {
            append(line)
            return@buildAnnotatedString
        }
        append(line.substring(0, start))
        withLink(LinkAnnotation.Url(url, styles)) { append(title) }
        append(line.substring(start + title.length))
    }
}
