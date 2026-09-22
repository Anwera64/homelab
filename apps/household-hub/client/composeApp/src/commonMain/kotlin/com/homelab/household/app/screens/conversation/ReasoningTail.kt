package com.homelab.household.app.screens.conversation

/**
 * The newest words of a model thinking, about three lines of them at the reading width.
 *
 * Cut here rather than by the text box, because a text box keeps its first lines and drops the
 * rest — backwards for thinking, where the newest thought is at the end. The cut lands on a whole
 * word and says it was made. Paragraph breaks are collapsed: three lines are too few to spend on
 * blank ones.
 */
fun reasoningTail(
    reasoning: String,
    maxChars: Int = DEFAULT_TAIL_CHARS,
): String {
    val text = reasoning.replace(whitespace, " ").trim()
    if (text.length <= maxChars) return text

    val end = text.takeLast(maxChars)
    val wordStart = end.indexOf(' ')
    val kept = if (wordStart < 0) end else end.substring(wordStart + 1)
    return "…$kept"
}

private val whitespace = Regex("\\s+")

/** About three lines of the caption style at the reading width. */
private const val DEFAULT_TAIL_CHARS = 140
