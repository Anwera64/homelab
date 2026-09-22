package com.homelab.household.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The linter's own wiring, guarded the way everything else in this package is.
 *
 * `ktlintCheck` passing proves the code is clean. It does not prove the checker is still the one
 * that was agreed: a `.editorconfig` can be emptied, and the Compose ruleset can fall out of the
 * build while every formatting rule keeps passing — which is the failure that would hurt, because
 * the Compose rules are the half that reads composables rather than whitespace.
 *
 * `tests/ci-workflow.test.js` covers the other half, where the linter has to actually run: the CI
 * job and the pre-commit hook.
 */
class LintSetupTest {
    private val clientRootDir =
        File(System.getProperty("user.dir")).let { dir ->
            if (dir.name == "shared") dir.parentFile else dir
        }

    private val editorConfig = File(clientRootDir, ".editorconfig")
    private val rootBuildFile = File(clientRootDir, "build.gradle.kts")
    private val versionCatalog = File(clientRootDir, "gradle/libs.versions.toml")

    @Test
    fun `GIVEN the client WHEN ktlint runs THEN its rules come from editorconfig`() {
        assertTrue(editorConfig.isFile, "${editorConfig.path} must exist: it is where the rules live")

        val text = editorConfig.readText()
        val required =
            mapOf(
                "ktlint_code_style" to "the style has to be named, or ktlint picks its own default",
                "max_line_length" to "an unset line length is an unenforced one",
            )
        val missing =
            required.filterKeys { key ->
                !Regex("""^\s*$key\s*=""", RegexOption.MULTILINE).containsMatchIn(text)
            }

        assertTrue(
            missing.isEmpty(),
            ".editorconfig must set these:\n" + missing.entries.joinToString("\n") { "  ${it.key} — ${it.value}" },
        )
    }

    @Test
    fun `GIVEN every module WHEN the build configures THEN ktlint is applied with the Compose rules`() {
        val build = rootBuildFile.readText()

        assertTrue(
            build.contains("org.jlleitschuh.gradle.ktlint"),
            "The root build must apply the ktlint plugin to its subprojects",
        )
        assertTrue(
            build.contains("subprojects"),
            "ktlint must be applied to every module, not only the root",
        )
        // Without this dependency ktlint still formats, and every Compose rule silently stops
        // running — a green build that checks half of what it claims to.
        assertTrue(
            build.contains("ktlintRuleset"),
            "The Compose ruleset must be added to the ktlintRuleset configuration",
        )
        assertTrue(
            versionCatalog.readText().contains("io.nlopez.compose.rules:ktlint"),
            "The catalog must declare io.nlopez.compose.rules:ktlint",
        )
    }

    @Test
    fun `GIVEN a rule is switched off WHEN someone reads editorconfig THEN the reason is next to it`() {
        val lines = editorConfig.readLines()
        val switchedOff =
            lines.withIndex().filter { (_, line) ->
                line.trimStart().startsWith("ktlint_") && line.substringAfter('=').trim() == "disabled"
            }

        assertTrue(switchedOff.isNotEmpty(), "Expected some rules to be switched off; update this test if none are")

        // A `disabled` with no comment above it is how a considered exception turns into folklore.
        val undocumented =
            switchedOff
                .filter { (index, _) ->
                    lines
                        .take(index)
                        .lastOrNull { it.isNotBlank() }
                        ?.trimStart()
                        ?.startsWith("#") !=
                        true
                }.map { (index, line) -> "${index + 1}: ${line.trim()}" }

        assertTrue(
            undocumented.isEmpty(),
            "Every disabled rule needs a comment saying why:\n" + undocumented.joinToString("\n"),
        )
    }
}
