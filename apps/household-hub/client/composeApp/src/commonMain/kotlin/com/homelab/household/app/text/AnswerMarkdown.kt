package com.homelab.household.app.text

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser

/** The looks an answer's Markdown can take on; built from the theme, or fixed in a test. */
@Immutable
data class AnswerStyles(
    val emphasis: SpanStyle,
    val italic: SpanStyle,
    val code: SpanStyle,
    val muted: SpanStyle,
    val link: TextLinkStyles,
)

/** The space above a block, named for why it is there; the screen turns each into a spacing step. */
enum class Gap { None, Tight, Block, Heading, Section }

/** One piece of an answer, drawn as its own text. */
sealed interface AnswerBlock {
    val gap: Gap

    /** A paragraph, heading, quote or code block: one run of styled text. */
    data class Prose(
        val text: AnnotatedString,
        override val gap: Gap,
    ) : AnswerBlock

    /** A list item: its marker hangs in a column of its own, [depth] steps in. */
    data class Item(
        val marker: String,
        val depth: Int,
        val text: AnnotatedString,
        override val gap: Gap,
    ) : AnswerBlock

    /** A table row as a phone can read it: the first cell as a label, then `Header: value` lines. */
    data class Row(
        val label: AnnotatedString,
        val cells: List<AnnotatedString>,
        override val gap: Gap,
    ) : AnswerBlock
}

/**
 * An answer's Markdown as the blocks the conversation draws.
 *
 * A subset only: emphasis, code, lists, headings, quotes, tables, dividers and links. A link opens
 * when it goes to the web or to mail — written as `[words](address)`, in angle brackets or bare —
 * and otherwise keeps just its words; images keep their alt text. Anything else — HTML, a marker
 * that has not closed yet — shows as the model wrote it, so nothing the model said is ever lost.
 */
fun answerBlocks(
    markdown: String,
    styles: AnswerStyles,
): List<AnswerBlock> {
    val root = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(markdown)
    return BlockWalker(markdown, styles).walk(root)
}

private val HEADINGS =
    setOf(
        MarkdownElementTypes.ATX_1,
        MarkdownElementTypes.ATX_2,
        MarkdownElementTypes.ATX_3,
        MarkdownElementTypes.ATX_4,
        MarkdownElementTypes.ATX_5,
        MarkdownElementTypes.ATX_6,
        MarkdownElementTypes.SETEXT_1,
        MarkdownElementTypes.SETEXT_2,
    )

private val HEADING_CONTENT = setOf(MarkdownTokenTypes.ATX_CONTENT, MarkdownTokenTypes.SETEXT_CONTENT)

private val LINKS =
    setOf(
        MarkdownElementTypes.INLINE_LINK,
        MarkdownElementTypes.FULL_REFERENCE_LINK,
        MarkdownElementTypes.SHORT_REFERENCE_LINK,
    )

private val LISTS = setOf(MarkdownElementTypes.UNORDERED_LIST, MarkdownElementTypes.ORDERED_LIST)

private val SPACE = setOf(MarkdownTokenTypes.EOL, MarkdownTokenTypes.WHITE_SPACE)

/** The only kinds of address a tap may open: the web and mail, never a script or a file. */
private val OPENABLE = setOf("https", "http", "mailto")

private class BlockWalker(
    private val src: String,
    private val styles: AnswerStyles,
) {
    private val blocks = mutableListOf<AnswerBlock>()
    private var afterDivider = false
    private var inList = false
    private var emphasised = false

    fun walk(root: ASTNode): List<AnswerBlock> {
        root.children.forEach(::block)
        return blocks
    }

    private fun add(
        wanted: Gap,
        make: (Gap) -> AnswerBlock,
    ) {
        val gap =
            when {
                blocks.isEmpty() -> Gap.None
                afterDivider -> Gap.Section
                else -> wanted
            }
        blocks += make(gap)
        afterDivider = false
    }

    private fun block(node: ASTNode) {
        if (node.type !in LISTS) inList = false
        when (node.type) {
            MarkdownElementTypes.PARAGRAPH -> prose(Gap.Block, text { inline(node) })
            in HEADINGS -> heading(node)
            in LISTS -> list(node, depth = 0)
            MarkdownElementTypes.BLOCK_QUOTE -> quote(node)
            MarkdownElementTypes.CODE_FENCE -> code(fenceText(node))
            MarkdownElementTypes.CODE_BLOCK -> code(codeBlockText(node))
            GFMElementTypes.TABLE -> table(node)
            MarkdownTokenTypes.HORIZONTAL_RULE -> afterDivider = blocks.isNotEmpty()
            in SPACE -> Unit
            else -> prose(Gap.Block, AnnotatedString(node.source().trim()))
        }
    }

    private fun prose(
        gap: Gap,
        text: AnnotatedString,
    ) {
        if (text.isNotEmpty()) add(gap) { AnswerBlock.Prose(text, it) }
    }

    private fun heading(node: ASTNode) {
        val content = node.children.firstOrNull { it.type in HEADING_CONTENT } ?: return
        prose(Gap.Heading, text { emphasis { inline(content) } })
    }

    private fun quote(node: ASTNode) {
        val paragraphs = node.children.filter { it.type == MarkdownElementTypes.PARAGRAPH }
        prose(
            Gap.Block,
            text {
                withStyle(styles.muted) {
                    paragraphs.forEachIndexed { index, paragraph ->
                        if (index > 0) append("\n")
                        inline(paragraph)
                    }
                }
            },
        )
    }

    private fun code(source: String) = prose(Gap.Block, text { withStyle(styles.code) { append(source) } })

    private fun list(
        node: ASTNode,
        depth: Int,
    ) {
        node.children.filter { it.type == MarkdownElementTypes.LIST_ITEM }.forEach { item(it, depth) }
    }

    private fun item(
        node: ASTNode,
        depth: Int,
    ) {
        val number = node.children.firstOrNull { it.type == MarkdownTokenTypes.LIST_NUMBER }
        val marker = number?.source()?.trim() ?: "•"
        val paragraphs = node.children.filter { it.type == MarkdownElementTypes.PARAGRAPH }
        val content =
            text {
                paragraphs.forEachIndexed { index, paragraph ->
                    if (index > 0) append("\n")
                    inline(paragraph)
                }
            }
        add(if (inList) Gap.Tight else Gap.Block) { AnswerBlock.Item(marker, depth, content, it) }
        inList = true
        node.children.filter { it.type in LISTS }.forEach { list(it, depth + 1) }
    }

    private fun table(node: ASTNode) {
        val header = node.children.firstOrNull { it.type == GFMElementTypes.HEADER }
        val names = header?.cells()?.map { text { inline(it) }.text } ?: emptyList()
        node.children.filter { it.type == GFMElementTypes.ROW }.forEach { row ->
            val cells = row.cells()
            if (cells.isEmpty()) return@forEach
            val label = text { emphasis { inline(cells.first()) } }
            val lines =
                cells.drop(1).mapIndexed { index, cell ->
                    val value = text { inline(cell) }
                    val name = names.getOrNull(index + 1)
                    buildAnnotatedString {
                        if (!name.isNullOrEmpty()) {
                            withStyle(styles.muted) { append("$name:") }
                            append(" ")
                        }
                        append(value)
                    }
                }
            add(Gap.Block) { AnswerBlock.Row(label, lines, it) }
        }
    }

    private fun ASTNode.cells() = children.filter { it.type == GFMTokenTypes.CELL }

    private fun AnnotatedString.Builder.inline(node: ASTNode) {
        when (node.type) {
            MarkdownElementTypes.STRONG -> {
                emphasis { inner(node) }
            }

            MarkdownElementTypes.EMPH -> {
                withStyle(styles.italic) { inner(node) }
            }

            MarkdownElementTypes.CODE_SPAN -> {
                withStyle(styles.code) { append(node.source().trim('`').trim()) }
            }

            MarkdownElementTypes.INLINE_LINK -> {
                val destination = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_DESTINATION }
                val address = destination?.source()?.removePrefix("<")?.removeSuffix(">")
                link(address) { node.linkText()?.let { linkText(it) } }
            }

            in LINKS -> {
                node.linkText()?.let { linkText(it) }
            }

            MarkdownElementTypes.IMAGE -> {
                node.children
                    .firstOrNull { it.type in LINKS }
                    ?.linkText()
                    ?.let { linkText(it) }
            }

            MarkdownElementTypes.AUTOLINK -> {
                val address = node.source().removePrefix("<").removeSuffix(">")
                link(address) { append(address) }
            }

            GFMTokenTypes.GFM_AUTOLINK -> {
                val address = node.source()
                link(if (address.startsWith("www.")) "https://$address" else address) { append(address) }
            }

            MarkdownTokenTypes.BLOCK_QUOTE -> {
                Unit
            }

            MarkdownTokenTypes.EOL, MarkdownTokenTypes.HARD_LINE_BREAK -> {
                append("\n")
            }

            else -> {
                if (node.children.isEmpty()) append(node.source()) else node.children.forEach { inline(it) }
            }
        }
    }

    /** What sits between an emphasis node's own markers: `**`, `__`, `*` or `_` at each end. */
    private fun AnnotatedString.Builder.inner(node: ASTNode) {
        node.children
            .dropWhile { it.type == MarkdownTokenTypes.EMPH }
            .dropLastWhile { it.type == MarkdownTokenTypes.EMPH }
            .forEach { inline(it) }
    }

    private fun AnnotatedString.Builder.linkText(node: ASTNode) {
        node.children
            .dropWhile { it.type == MarkdownTokenTypes.LBRACKET }
            .dropLastWhile { it.type == MarkdownTokenTypes.RBRACKET }
            .forEach { inline(it) }
    }

    /** [words] that open [address] when tapped, if it is one a tap may open; just the words if not. */
    private fun AnnotatedString.Builder.link(
        address: String?,
        words: AnnotatedString.Builder.() -> Unit,
    ) {
        val scheme = address?.substringBefore(':', missingDelimiterValue = "")?.lowercase()
        if (address == null || scheme !in OPENABLE) return words()
        withLink(LinkAnnotation.Url(address, styles.link)) { words() }
    }

    private fun ASTNode.linkText(): ASTNode? = children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }

    /** Bold inside something already bold — a heading, a table's label — adds nothing. */
    private fun AnnotatedString.Builder.emphasis(content: AnnotatedString.Builder.() -> Unit) {
        if (emphasised) return content()
        emphasised = true
        withStyle(styles.emphasis) { content() }
        emphasised = false
    }

    private fun fenceText(node: ASTNode): String =
        node.children
            .dropWhile { it.type != MarkdownTokenTypes.EOL }
            .drop(1)
            .takeWhile { it.type != MarkdownTokenTypes.CODE_FENCE_END }
            .joinToString("") { if (it.type == MarkdownTokenTypes.EOL) "\n" else it.source() }
            .trimEnd('\n')

    private fun codeBlockText(node: ASTNode): String =
        node.children
            .joinToString("") { if (it.type == MarkdownTokenTypes.EOL) "\n" else it.source() }
            .lines()
            .joinToString("\n") { it.removePrefix("    ") }
            .trimEnd('\n')

    private fun ASTNode.source(): String = getTextInNode(src).toString()

    private fun text(build: AnnotatedString.Builder.() -> Unit): AnnotatedString = buildAnnotatedString(build).trimmed()
}

private fun AnnotatedString.trimmed(): AnnotatedString {
    val start = text.indexOfFirst { !it.isWhitespace() }
    if (start < 0) return AnnotatedString("")
    val end = text.indexOfLast { !it.isWhitespace() } + 1
    return subSequence(start, end)
}
