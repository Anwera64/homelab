package com.homelab.household.app.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import com.homelab.household.app.text.AnswerBlock
import com.homelab.household.app.text.AnswerStyles
import com.homelab.household.app.text.Gap
import com.homelab.household.app.text.answerBlocks
import com.homelab.household.app.theme.HearthSpacing
import com.homelab.household.app.theme.HearthTheme

/**
 * An answer, its Markdown drawn as prose in Hearth's own styles.
 *
 * One text per block, so the space between blocks comes off the spacing scale: a paragraph's gap,
 * a wider one above a heading, wider still where the model drew a divider. Bold is the emphasis
 * weight, code the mono family, and a table's rows read as a label with its values under it —
 * a phone is too narrow for columns.
 */
@Composable
fun AnswerText(
    content: String,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    val spacing = HearthTheme.spacing
    val styles =
        remember(type, colors) {
            AnswerStyles(
                emphasis = SpanStyle(fontWeight = type.bodyStrong.fontWeight),
                italic = SpanStyle(fontStyle = FontStyle.Italic),
                code = SpanStyle(fontFamily = type.mono.fontFamily, fontWeight = type.mono.fontWeight),
                muted = SpanStyle(color = colors.textMuted),
            )
        }
    val blocks = remember(content, styles) { answerBlocks(content, styles) }
    val indent = spacing.xl

    Column(modifier = modifier) {
        blocks.forEach { block ->
            val above = Modifier.padding(top = spacing.above(block.gap))
            when (block) {
                is AnswerBlock.Prose -> {
                    Text(text = block.text, modifier = above, style = type.bodyLarge, color = colors.textPrimary)
                }

                is AnswerBlock.Item -> {
                    Row(modifier = above.padding(start = indent * block.depth)) {
                        Text(
                            text = block.marker,
                            modifier = Modifier.widthIn(min = indent),
                            style = type.bodyLarge,
                            color = colors.textPrimary,
                        )
                        Text(text = block.text, style = type.bodyLarge, color = colors.textPrimary)
                    }
                }

                is AnswerBlock.Row -> {
                    Column(modifier = above, verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                        Text(text = block.label, style = type.bodyLarge, color = colors.textPrimary)
                        block.cells.forEach { cell ->
                            Text(
                                text = cell,
                                modifier = Modifier.padding(start = indent),
                                style = type.bodyLarge,
                                color = colors.textPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun HearthSpacing.above(gap: Gap): Dp =
    when (gap) {
        Gap.None -> none
        Gap.Tight -> xs
        Gap.Block -> md
        Gap.Heading -> xl
        Gap.Section -> xxxl
    }
