package com.homelab.household.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * A modifier chain is read top to bottom, one call per line, because the order is the meaning:
 * `.padding().background()` and `.background().padding()` draw different things, and a chain
 * squeezed onto one line hides which came first.
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
    fun `GIVEN a modifier chain WHEN it has more than one call THEN each call is on its own line`() {
        assertTrue(sources.isNotEmpty(), "Expected Kotlin sources under ${clientRootDir.absolutePath}")

        val violations =
            sources.flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (oneLineChainCalls(line) >= 2) {
                        "${file.toRelativeString(clientRootDir)}:${index + 1}: ${line.trim()}"
                    } else {
                        null
                    }
                }
            }

        assertTrue(
            violations.isEmpty(),
            "Break these modifier chains one call per line (${violations.size}):\n" + violations.joinToString("\n"),
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
            "Start these modifier chains on the assignment line (${violations.size}):\n" + violations.joinToString(
                "\n",
            ),
        )
    }

    @Test
    fun `GIVEN sample lines WHEN counted THEN only chained calls on one line count`() {
        assertTrue(oneLineChainCalls("modifier = Modifier.matchParentSize().clip(HearthShapes.button),") == 2)
        assertTrue(oneLineChainCalls("Canvas(modifier = modifier.size(a.b).rotate(angle)) {") == 2)
        assertTrue(oneLineChainCalls("modifier.fillMaxWidth().padding(") == 2)
        assertTrue(oneLineChainCalls("Row(modifier = modifier.semantics { x = y }.fillMaxWidth())") == 2)
        assertTrue(oneLineChainCalls("Column(modifier = Modifier.weight(1f)) {") == 1)
        assertTrue(oneLineChainCalls("modifier: Modifier = Modifier,") == 0)
        assertTrue(oneLineChainCalls("            Modifier") == 0)
        assertTrue(oneLineChainCalls("                .clip(HearthShapes.item)") == 0)
        assertTrue(oneLineChainCalls("private fun Modifier.busySemantics(") == 1)
        assertTrue(oneLineChainCalls("// Modifier.a().b() in a comment") == 0)
        assertTrue(oneLineChainCalls("import androidx.compose.ui.Modifier") == 0)
    }

    /**
     * The most calls chained onto a `Modifier`/`modifier` receiver within this one line. Arguments
     * and trailing lambdas are skipped with their brackets balanced, so `.padding(a.b)` is one call.
     */
    private fun oneLineChainCalls(line: String): Int {
        val trimmed = line.trimStart()
        if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) return 0
        if (trimmed.startsWith("import ") || trimmed.startsWith("package ")) return 0

        return receiver.findAll(line).maxOfOrNull { match -> callsAfter(line, match.range.last + 1) } ?: 0
    }

    private fun callsAfter(
        line: String,
        start: Int,
    ): Int {
        var i = start
        var calls = 0
        while (i < line.length && line[i] == '.') {
            val name = identifier.matchAt(line, i + 1) ?: break
            i = name.range.last + 1
            while (true) {
                // A trailing lambda may sit a space away: `.semantics { ... }`.
                val bracket = (i until line.length).firstOrNull { line[it] != ' ' } ?: break
                if (line[bracket] == '(' || line[bracket] == '{') {
                    i = skipBalanced(line, bracket)
                } else {
                    break
                }
            }
            calls++
        }
        return calls
    }

    /** Index just past the bracket opened at [open], or the end of the line if it never closes. */
    private fun skipBalanced(
        line: String,
        open: Int,
    ): Int {
        var depth = 0
        var i = open
        while (i < line.length) {
            when (line[i]) {
                '(', '{', '[' -> depth++
                ')', '}', ']' -> if (--depth == 0) return i + 1
            }
            i++
        }
        return line.length
    }

    private companion object {
        val receiver = Regex("""(?<![\w.])[Mm]odifier\b""")
        val identifier = Regex("""[A-Za-z_]\w*""")
    }
}
