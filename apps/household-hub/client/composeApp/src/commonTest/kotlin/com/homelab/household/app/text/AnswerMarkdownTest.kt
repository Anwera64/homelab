package com.homelab.household.app.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.homelab.household.app.text.AnswerBlock.Item
import com.homelab.household.app.text.AnswerBlock.Prose
import com.homelab.household.app.text.AnswerBlock.Row
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An answer's Markdown, turned into the blocks the conversation draws.
 *
 * The model writes Markdown whether asked to or not; these pin down what each piece of it becomes
 * so an answer reads as prose — no stars, no pipes, no hashes — in Hearth's own styles.
 */
class AnswerMarkdownTest {
    private val styles =
        AnswerStyles(
            emphasis = SpanStyle(fontWeight = FontWeight.SemiBold),
            italic = SpanStyle(fontStyle = FontStyle.Italic),
            code = SpanStyle(fontFamily = FontFamily.Monospace),
            muted = SpanStyle(color = Color.Gray),
            link = TextLinkStyles(SpanStyle(color = Color.Blue)),
        )

    private fun AnnotatedString.styledBy(style: SpanStyle): List<String> =
        spanStyles.filter { it.item == style }.map { text.substring(it.start, it.end) }

    /** Each link as the words it covers and the address it opens. */
    private fun AnnotatedString.links(): List<Pair<String, String>> =
        getLinkAnnotations(0, length).map { text.substring(it.start, it.end) to (it.item as LinkAnnotation.Url).url }

    private fun AnswerBlock.plain(): String =
        when (this) {
            is Prose -> text.text
            is Item -> text.text
            is Row -> (listOf(label) + cells).joinToString("\n") { it.text }
        }

    private fun only(markdown: String): Prose = answerBlocks(markdown, styles).single() as Prose

    @Test
    fun `GIVEN a bold phrase WHEN the answer is drawn THEN the stars go and only the phrase is emphasised`() {
        // GIVEN
        val markdown = "**Panel review** at 14:30"

        // WHEN
        val block = only(markdown)

        // THEN
        assertEquals("Panel review at 14:30", block.text.text)
        assertEquals(listOf("Panel review"), block.text.styledBy(styles.emphasis))
    }

    @Test
    fun `GIVEN underscores for bold WHEN the answer is drawn THEN they emphasise like stars`() {
        // GIVEN
        val markdown = "a __good__ day"

        // WHEN
        val block = only(markdown)

        // THEN
        assertEquals("a good day", block.text.text)
        assertEquals(listOf("good"), block.text.styledBy(styles.emphasis))
    }

    @Test
    fun `GIVEN a word in single stars or underscores WHEN the answer is drawn THEN it is italic`() {
        // GIVEN
        val markdown = "a mountain view *and* a _lake_"

        // WHEN
        val block = only(markdown)

        // THEN
        assertEquals("a mountain view and a lake", block.text.text)
        assertEquals(listOf("and", "lake"), block.text.styledBy(styles.italic))
    }

    @Test
    fun `GIVEN inline code WHEN the answer is drawn THEN the backticks go and the code is mono`() {
        // GIVEN
        val markdown = "run `docker ps` first"

        // WHEN
        val block = only(markdown)

        // THEN
        assertEquals("run docker ps first", block.text.text)
        assertEquals(listOf("docker ps"), block.text.styledBy(styles.code))
    }

    @Test
    fun `GIVEN a dash list WHEN the answer is drawn THEN each item is its own bulleted block`() {
        // GIVEN
        val markdown = "- Milk\n- Bread"

        // WHEN
        val blocks = answerBlocks(markdown, styles)

        // THEN
        assertEquals(
            listOf(Item("•", 0, AnnotatedString("Milk"), Gap.None), Item("•", 0, AnnotatedString("Bread"), Gap.Tight)),
            blocks,
        )
    }

    @Test
    fun `GIVEN star bullets padded with spaces WHEN the answer is drawn THEN they become plain bullets`() {
        // GIVEN
        val markdown = "*   Drink a glass of water\n*   Take a short walk"

        // WHEN
        val blocks = answerBlocks(markdown, styles)

        // THEN
        assertEquals(listOf("•", "•"), blocks.map { (it as Item).marker })
        assertEquals(listOf("Drink a glass of water", "Take a short walk"), blocks.map { it.plain() })
    }

    @Test
    fun `GIVEN a numbered list WHEN the answer is drawn THEN each item keeps its number`() {
        // GIVEN
        val markdown = "1. Book the cabin\n2. Pack the car"

        // WHEN
        val blocks = answerBlocks(markdown, styles)

        // THEN
        assertEquals(listOf("1.", "2."), blocks.map { (it as Item).marker })
        assertEquals(listOf("Book the cabin", "Pack the car"), blocks.map { it.plain() })
    }

    @Test
    fun `GIVEN bullets under a numbered item WHEN the answer is drawn THEN they sit one step deeper`() {
        // GIVEN
        val markdown = "1. **What’s the age range?**\n   - **Toddlers?** Beach wins."

        // WHEN
        val blocks = answerBlocks(markdown, styles)

        // THEN
        val (question, answer) = blocks.map { it as Item }
        assertEquals("1." to 0, question.marker to question.depth)
        assertEquals("What’s the age range?", question.text.text)
        assertEquals("•" to 1, answer.marker to answer.depth)
        assertEquals("Toddlers? Beach wins.", answer.text.text)
        assertEquals(Gap.Tight, answer.gap)
    }

    @Test
    fun `GIVEN a list after a paragraph WHEN the answer is drawn THEN it starts a block away`() {
        // GIVEN
        val markdown = "**Pros:**\n- Water play"

        // WHEN
        val blocks = answerBlocks(markdown, styles)

        // THEN
        assertEquals(listOf(Gap.None, Gap.Block), blocks.map { it.gap })
    }

    @Test
    fun `GIVEN a heading WHEN the answer is drawn THEN it is an emphasised line with no hashes`() {
        // GIVEN
        val markdown = "## Plan"

        // WHEN
        val block = only(markdown)

        // THEN
        assertEquals("Plan", block.text.text)
        assertEquals(listOf("Plan"), block.text.styledBy(styles.emphasis))
    }

    @Test
    fun `GIVEN a heading with bold inside WHEN the answer is drawn THEN the whole line is emphasised once`() {
        // GIVEN
        val markdown = "### 1. **Beach: The Classic Relaxation & Play**"

        // WHEN
        val block = only(markdown)

        // THEN
        assertEquals("1. Beach: The Classic Relaxation & Play", block.text.text)
        assertEquals(listOf("1. Beach: The Classic Relaxation & Play"), block.text.styledBy(styles.emphasis))
    }

    @Test
    fun `GIVEN two paragraphs WHEN the answer is drawn THEN they are two blocks a block apart`() {
        // GIVEN
        val markdown = "Tomorrow is light.\n\nNothing in the evening."

        // WHEN
        val blocks = answerBlocks(markdown, styles)

        // THEN
        assertEquals(listOf("Tomorrow is light.", "Nothing in the evening."), blocks.map { it.plain() })
        assertEquals(listOf(Gap.None, Gap.Block), blocks.map { it.gap })
    }

    @Test
    fun `GIVEN a heading after a paragraph WHEN the answer is drawn THEN it gets a heading's gap`() {
        // GIVEN
        val markdown = "Here’s the plan:\n\n### Saturday"

        // WHEN
        val blocks = answerBlocks(markdown, styles)

        // THEN
        assertEquals(listOf(Gap.None, Gap.Heading), blocks.map { it.gap })
    }

    @Test
    fun `GIVEN a divider WHEN the answer is drawn THEN no dashes show and the next block gets a section's gap`() {
        // GIVEN
        val markdown = "…the slower pace.\n\n---\n\n### 2. Mountains"

        // WHEN
        val blocks = answerBlocks(markdown, styles)

        // THEN
        assertEquals(listOf("…the slower pace.", "2. Mountains"), blocks.map { it.plain() })
        assertEquals(listOf(Gap.None, Gap.Section), blocks.map { it.gap })
    }

    @Test
    fun `GIVEN a link WHEN the answer is drawn THEN only its words show`() {
        // GIVEN
        val markdown = "see [the forecast](https://met.no/x) first"

        // WHEN
        val block = only(markdown)

        // THEN
        assertEquals("see the forecast first", block.text.text)
    }

    @Test
    fun `GIVEN an image WHEN the answer is drawn THEN only its alt text shows`() {
        // GIVEN
        val markdown = "![Map of the trail](https://example.org/map.png)"

        // WHEN
        val block = only(markdown)

        // THEN
        assertEquals("Map of the trail", block.text.text)
    }

    @Test
    fun `GIVEN a link WHEN the answer is drawn THEN its words open the address`() {
        // GIVEN
        val markdown = "see [the forecast](https://met.no/x) first"

        // WHEN
        val block = only(markdown)

        // THEN
        assertEquals(listOf("the forecast" to "https://met.no/x"), block.text.links())
    }

    @Test
    fun `GIVEN web and mail links WHEN the answer is drawn THEN http and mailto open too`() {
        // GIVEN
        val markdown = "the [NAS](http://nas.local) or [write](MAILTO:a@b.no)"

        // WHEN
        val block = only(markdown)

        // THEN
        assertEquals(listOf("NAS" to "http://nas.local", "write" to "MAILTO:a@b.no"), block.text.links())
    }

    @Test
    fun `GIVEN a link to a script or a file WHEN the answer is drawn THEN its words show but open nothing`() {
        // GIVEN
        val markdown = "[click](javascript:alert(1)) or [this](file:///etc/passwd) or [that](/relative)"

        // WHEN
        val block = only(markdown)

        // THEN
        assertEquals("click or this or that", block.text.text)
        assertEquals(emptyList(), block.text.links())
    }

    @Test
    fun `GIVEN an address in angle brackets WHEN the answer is drawn THEN the brackets go and it opens`() {
        // GIVEN
        val markdown = "see <https://met.no> first"

        // WHEN
        val block = only(markdown)

        // THEN
        assertEquals("see https://met.no first", block.text.text)
        assertEquals(listOf("https://met.no" to "https://met.no"), block.text.links())
    }

    @Test
    fun `GIVEN a bare address in the prose WHEN the answer is drawn THEN it opens`() {
        // GIVEN
        val markdown = "see https://met.no/x today"

        // WHEN
        val block = only(markdown)

        // THEN
        assertEquals("see https://met.no/x today", block.text.text)
        assertEquals(listOf("https://met.no/x" to "https://met.no/x"), block.text.links())
    }

    @Test
    fun `GIVEN a bare www address WHEN the answer is drawn THEN it opens over https`() {
        // GIVEN
        val markdown = "try www.met.no today"

        // WHEN
        val block = only(markdown)

        // THEN
        assertEquals("try www.met.no today", block.text.text)
        assertEquals(listOf("www.met.no" to "https://www.met.no"), block.text.links())
    }

    @Test
    fun `GIVEN a mail address in angle brackets WHEN the answer is drawn THEN the brackets go and it opens mail`() {
        // GIVEN
        val markdown = "write to <a@b.no> today"

        // WHEN
        val block = only(markdown)

        // THEN
        assertEquals("write to a@b.no today", block.text.text)
        assertEquals(listOf("a@b.no" to "mailto:a@b.no"), block.text.links())
    }

    @Test
    fun `GIVEN a bare mailto address WHEN the answer is drawn THEN all of it opens mail`() {
        // GIVEN
        val markdown = "write to mailto:anton.haushalt@example.com today"

        // WHEN
        val block = only(markdown)

        // THEN
        assertEquals("write to mailto:anton.haushalt@example.com today", block.text.text)
        assertEquals(
            listOf("mailto:anton.haushalt@example.com" to "mailto:anton.haushalt@example.com"),
            block.text.links(),
        )
    }

    @Test
    fun `GIVEN a bare mail address WHEN the answer is drawn THEN it opens mail`() {
        // GIVEN
        val markdown = "write to anton@example.com."

        // WHEN
        val block = only(markdown)

        // THEN
        assertEquals(listOf("anton@example.com" to "mailto:anton@example.com"), block.text.links())
    }

    @Test
    fun `GIVEN a mail address in code WHEN the answer is drawn THEN it stays code and opens nothing`() {
        // GIVEN
        val markdown = "set `admin@nas.local` as the sender"

        // WHEN
        val block = only(markdown)

        // THEN
        assertEquals(emptyList(), block.text.links())
    }

    @Test
    fun `GIVEN a mail link with its address as the words WHEN the answer is drawn THEN it opens once`() {
        // GIVEN
        val markdown = "[a@b.no](mailto:a@b.no)"

        // WHEN
        val block = only(markdown)

        // THEN
        assertEquals(listOf("a@b.no" to "mailto:a@b.no"), block.text.links())
    }

    @Test
    fun `GIVEN a link inside bold WHEN the answer is drawn THEN it is both emphasised and opens`() {
        // GIVEN
        val markdown = "**read [the forecast](https://met.no/x)**"

        // WHEN
        val block = only(markdown)

        // THEN
        assertEquals(listOf("read the forecast"), block.text.styledBy(styles.emphasis))
        assertEquals(listOf("the forecast" to "https://met.no/x"), block.text.links())
    }

    @Test
    fun `GIVEN an image WHEN the answer is drawn THEN its alt text opens nothing`() {
        // GIVEN
        val markdown = "![Map of the trail](https://example.org/map.png)"

        // WHEN
        val block = only(markdown)

        // THEN
        assertEquals(emptyList(), block.text.links())
    }

    @Test
    fun `GIVEN bold that has not closed yet WHEN the answer is drawn mid-stream THEN the stars stay literal`() {
        // GIVEN
        val markdown = "**Best F"

        // WHEN
        val block = only(markdown)

        // THEN
        assertEquals("**Best F", block.text.text)
        assertTrue(block.text.spanStyles.isEmpty())
    }

    @Test
    fun `GIVEN plain prose WHEN the answer is drawn THEN it passes through untouched`() {
        // GIVEN
        val markdown = "Yes — nothing after 18:00 on Thursday. It’s the last clear evening before your jury."

        // WHEN
        val block = only(markdown)

        // THEN
        assertEquals(markdown, block.text.text)
        assertTrue(block.text.spanStyles.isEmpty())
    }

    @Test
    fun `GIVEN a line break inside a paragraph WHEN the answer is drawn THEN the break is kept`() {
        // GIVEN
        val markdown = "Panel review 14:30\nPrint shop until 18:00"

        // WHEN
        val block = only(markdown)

        // THEN
        assertEquals("Panel review 14:30\nPrint shop until 18:00", block.text.text)
    }

    @Test
    fun `GIVEN a table WHEN the answer is drawn THEN each row becomes a label with its cells under it`() {
        // GIVEN
        val markdown =
            "| Factor | Beach | Mountains |\n| :--- | :--- | :--- |\n" +
                "| **Crowds** | Often High | Generally Lower |\n| Dining | Casual | Lodge-based |"

        // WHEN
        val blocks = answerBlocks(markdown, styles)

        // THEN
        val (crowds, dining) = blocks.map { it as Row }
        assertEquals("Crowds", crowds.label.text)
        assertTrue("Crowds" in crowds.label.styledBy(styles.emphasis))
        assertEquals(listOf("Beach: Often High", "Mountains: Generally Lower"), crowds.cells.map { it.text })
        assertEquals(listOf("Beach:"), crowds.cells[0].styledBy(styles.muted))
        assertEquals(listOf("Dining" to "Beach: Casual"), listOf(dining.label.text to dining.cells[0].text))
        assertEquals(listOf(Gap.None, Gap.Block), blocks.map { it.gap })
    }

    @Test
    fun `GIVEN a compact table with bold headers after a paragraph WHEN the answer is drawn THEN it still becomes rows`() {
        // GIVEN
        val markdown =
            "Here's a high-level timeline of the key phases:\n\n" +
                "| **Date / Period** | **Event** |\n|---|---|\n" +
                "| **Early April 1989** | Student gatherings begin. |\n" +
                "| **Mid-April 1989** | Students begin a **hunger strike**. |"

        // WHEN
        val blocks = answerBlocks(markdown, styles)

        // THEN
        val rows = blocks.filterIsInstance<Row>()
        assertEquals(listOf("Early April 1989", "Mid-April 1989"), rows.map { it.label.text })
        assertEquals("Event: Student gatherings begin.", rows[0].cells.single().text)
        assertEquals("Event: Students begin a hunger strike.", rows[1].cells.single().text)
        assertTrue(blocks.none { "|" in it.plain() })
    }

    @Test
    fun `GIVEN a fenced code block WHEN the answer is drawn THEN the code shows as written in mono`() {
        // GIVEN
        val markdown = "```bash\ndocker restart <container_name_or_id>\n```"

        // WHEN
        val block = only(markdown)

        // THEN
        assertEquals("docker restart <container_name_or_id>", block.text.text)
        assertEquals(listOf("docker restart <container_name_or_id>"), block.text.styledBy(styles.code))
    }

    @Test
    fun `GIVEN a quote WHEN the answer is drawn THEN it is its own muted paragraph`() {
        // GIVEN
        val markdown = "> Check the weather first."

        // WHEN
        val block = only(markdown)

        // THEN
        assertEquals("Check the weather first.", block.text.text)
        assertEquals(listOf("Check the weather first."), block.text.styledBy(styles.muted))
    }

    @Test
    fun `GIVEN inline HTML WHEN the answer is drawn THEN it shows as the model wrote it`() {
        // GIVEN
        val markdown = "line one<br>line two"

        // WHEN
        val block = only(markdown)

        // THEN
        assertEquals("line one<br>line two", block.text.text)
    }

    @Test
    fun `GIVEN a real answer from the household model WHEN it is drawn THEN no Markdown is left in sight`() {
        // GIVEN
        val markdown = BEACH_OR_MOUNTAINS

        // WHEN
        val blocks = answerBlocks(markdown, styles)

        // THEN
        val shown = blocks.joinToString("\n") { it.plain() }
        listOf("**", "###", "|", "---", ":---").forEach { marker ->
            assertTrue(marker !in shown, "\"$marker\" is still showing")
        }
        assertEquals(7, blocks.count { it is Row })
        assertTrue(blocks.first().plain().startsWith("Choosing between the beach"))
        assertEquals(Gap.None, blocks.first().gap)
    }
}
