package com.homelab.household.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * A modifier chain is read top to bottom, one call per line, because the order is the meaning:
 * `.padding().background()` and `.background().padding()` draw different things, and a chain
 * squeezed onto one line hides which came first. That includes the call after a multi-line one:
 * `).padding(...)` tucks a call behind the closing bracket of the one before it.
 *
 * A single call (`Modifier.fillMaxWidth()`) stays inline; it has no order to read.
 *
 * ktlint can only force this for every call chain in the codebase (well over a thousand that are
 * not modifiers), so the rule lives here, scoped to chains that start at `Modifier` or `modifier`.
 */
class ModifierChainTest {
    private val clientRootDir =
        File(System.getProperty("user.dir")).let { dir ->
            if (dir.name == "shared") dir.parentFile else dir
        }

    private val sources by lazy {
        clientRootDir
            .walkTopDown()
            .onEnter { it.name != "build" && !it.name.startsWith(".") }
            .filter { it.isFile && it.extension == "kt" && "${File.separator}src${File.separator}" in it.path }
            // Its own samples are chains on one line, on purpose.
            .filterNot { it.name == "ModifierChainTest.kt" }
            .toList()
    }

    @Test
    fun `GIVEN a modifier chain WHEN it has more than one call THEN each call starts its own line`() {
        assertTrue(sources.isNotEmpty(), "Expected Kotlin sources under ${clientRootDir.absolutePath}")

        val violations =
            sources.flatMap { file ->
                val text = file.readText()
                val lines = text.lines()
                callsNotStartingTheirLine(text).map { offset ->
                    val line = text.substring(0, offset).count { it == '\n' }
                    "${file.toRelativeString(clientRootDir)}:${line + 1}: ${lines[line].trim()}"
                }
            }

        assertTrue(
            violations.isEmpty(),
            "Put each call of these modifier chains on its own line (${violations.size}):\n" +
                violations.joinToString("\n"),
        )
    }

    /**
     * The chain starts where it is assigned: `modifier = Modifier` and then its calls below. Wrapping
     * after the `=` spends a line on nothing and pushes the whole chain one indent further right.
     * ktlint's `multiline-expression-wrapping` asks for exactly that wrap, which is why
     * `.editorconfig` turns it off.
     */
    @Test
    fun `GIVEN a modifier chain WHEN it is assigned THEN it starts on the assignment line`() {
        val wrappedAfterEquals = Regex("""=\s*$""")
        val bareReceiver = Regex("""^\s*[Mm]odifier\s*$""")

        val violations =
            sources.flatMap { file ->
                file.readLines().zipWithNext().mapIndexedNotNull { index, (line, next) ->
                    if (wrappedAfterEquals.containsMatchIn(line) && bareReceiver.matches(next)) {
                        "${file.toRelativeString(clientRootDir)}:${index + 1}: ${line.trim()} ${next.trim()}"
                    } else {
                        null
                    }
                }
            }

        assertTrue(
            violations.isEmpty(),
            "Start these modifier chains on the assignment line (${violations.size}):\n" +
                violations.joinToString("\n"),
        )
    }

    @Test
    fun `GIVEN sample chains WHEN scanned THEN only calls not starting their line are reported`() {
        fun misplaced(text: String) = callsNotStartingTheirLine(text).size

        assertEquals(2, misplaced("modifier = Modifier.matchParentSize().clip(HearthShapes.button),"))
        assertEquals(2, misplaced("Canvas(modifier = modifier.size(a.b).rotate(angle)) {"))
        assertEquals(2, misplaced("Row(modifier = modifier.semantics { x = y }.fillMaxWidth())"))
        assertEquals(1, misplaced("modifier = Modifier\n  .clickable(\n    onClick = go,\n  ).padding(sm),"))
        assertEquals(1, misplaced("modifier = Modifier\n  .semantics {\n    x = y\n  }.padding(sm),"))
        assertEquals(0, misplaced("modifier = Modifier\n  .clickable(\n    onClick = go,\n  )\n  .padding(sm),"))
        assertEquals(0, misplaced("modifier = Modifier\n    .size(a.b)\n    .clip(shape),"))
        assertEquals(1, misplaced("modifier = Modifier\n  .size(a)\n  // why\n  .clip(\n    s,\n  ).padding(sm),"))
        assertEquals(0, misplaced("Column(modifier = Modifier.weight(1f)) {"))
        assertEquals(0, misplaced("modifier: Modifier = Modifier,"))
        assertEquals(0, misplaced("private fun Modifier.busySemantics(\n    busy: Boolean,\n): Modifier {"))
        assertEquals(0, misplaced("// Modifier.a().b() in a comment"))
        assertEquals(0, misplaced("import androidx.compose.ui.Modifier"))
        assertEquals(0, misplaced("Text(\"Modifier.a().b()\")"))
    }

    /**
     * Offsets of the `.` of every call, in a chain of two or more on a `Modifier`/`modifier`
     * receiver, that has something other than indentation before it on its line. Arguments and
     * lambdas are skipped with their brackets balanced, so `.padding(a.b)` is one call.
     */
    private fun callsNotStartingTheirLine(text: String): List<Int> =
        receiver
            .findAll(text)
            .filterNot { inCommentOrString(text, it.range.first) }
            .flatMap { match ->
                val dots = chainDots(text, match.range.last + 1)
                if (dots.size < 2) emptyList() else dots.filterNot { startsItsLine(text, it) }
            }.toList()

    private fun chainDots(
        text: String,
        start: Int,
    ): List<Int> {
        val dots = mutableListOf<Int>()
        var i = skipWhitespace(text, start)
        while (i < text.length && text[i] == '.') {
            val name = identifier.matchAt(text, i + 1) ?: break
            dots += i
            i = name.range.last + 1
            while (true) {
                // A trailing lambda may sit a space away: `.semantics { ... }`.
                var bracket = i
                while (bracket < text.length && text[bracket] == ' ') bracket++
                if (bracket < text.length && (text[bracket] == '(' || text[bracket] == '{')) {
                    i = skipBalanced(text, bracket)
                } else {
                    break
                }
            }
            i = skipWhitespace(text, i)
        }
        return dots
    }

    /** Skips whitespace and `//` comments, which a chain may carry between its calls. */
    private fun skipWhitespace(
        text: String,
        start: Int,
    ): Int {
        var i = start
        while (i < text.length) {
            when {
                text[i].isWhitespace() -> i++
                text.startsWith("//", i) -> i = text.indexOf('\n', i).let { if (it < 0) text.length else it }
                else -> return i
            }
        }
        return i
    }

    private fun startsItsLine(
        text: String,
        offset: Int,
    ): Boolean = text.substring(text.lastIndexOf('\n', offset - 1) + 1, offset).isBlank()

    /** A receiver on a comment or import line, or inside a string literal, is not code. */
    private fun inCommentOrString(
        text: String,
        offset: Int,
    ): Boolean {
        val before = text.substring(text.lastIndexOf('\n', offset - 1) + 1, offset)
        val trimmed = before.trimStart()
        return trimmed.startsWith("//") ||
            trimmed.startsWith("*") ||
            trimmed.startsWith("/*") ||
            trimmed.startsWith("import ") ||
            before.count { it == '"' } % 2 == 1
    }

    /** Index just past the bracket opened at [open], skipping string literals inside it. */
    private fun skipBalanced(
        text: String,
        open: Int,
    ): Int {
        var depth = 0
        var i = open
        while (i < text.length) {
            when (text[i]) {
                '"' -> {
                    i++
                    while (i < text.length && text[i] != '"') {
                        if (text[i] == '\\') i++
                        i++
                    }
                }

                '(', '{', '[' -> {
                    depth++
                }

                ')', '}', ']' -> {
                    if (--depth == 0) return i + 1
                }
            }
            i++
        }
        return text.length
    }

    private companion object {
        val receiver = Regex("""(?<![\w.])[Mm]odifier\b""")
        val identifier = Regex("""[A-Za-z_]\w*""")
    }
}
