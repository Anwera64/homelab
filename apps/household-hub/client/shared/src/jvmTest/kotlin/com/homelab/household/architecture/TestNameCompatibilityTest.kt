package com.homelab.household.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Tests here are named `` `GIVEN ... WHEN ... THEN ...` ``, and a backticked name is a real method
 * name — so it has to survive every target the client builds for. Two of them are fussier than the
 * JVM, and neither can be seen by a JVM build, which is what this guard is for.
 *
 * **Kotlin/Native** rejects some characters the JVM accepts. A comma cost a full red CI run:
 * `SpaceRepositoryTest.kt:95 Name contains illegal characters: ","`, from
 * `:core:data:compileTestKotlinIosSimulatorArm64`. `commonTest` compiles for both, so `jvmTest`
 * passing says nothing about it. Apostrophes and hyphens are fine — that same compilation reported
 * this one error and nothing else, with apostrophes present in the same file.
 *
 * **Android instrumented tests** reject spaces outright: a method name containing one is not legal
 * dex below `minSdkVersion 30` and this client is `minSdk 26`, so D8 fails the *build*
 * (`Space characters in SimpleName ... are not allowed prior to DEX version 040`). Those source
 * sets use `GIVEN_..._WHEN_..._THEN_...` instead, and are checked here for the opposite thing.
 */
class TestNameCompatibilityTest {
    private val clientRootDir =
        File(System.getProperty("user.dir")).let { dir ->
            if (dir.name == "shared") dir.parentFile else dir
        }

    private val modules = listOf("core/domain", "core/data", "core/presentation", "shared", "composeApp", "androidApp")

    /** Rejected by Kotlin/Native, by the JVM, or by dex. The comma is the one that actually bit. */
    private val illegalInAnyTarget = charArrayOf(',', '.', ';', '[', ']', '/', '<', '>', ':', '\\')

    private val backtickedName = Regex("""\bfun\s+`([^`]+)`""")

    @Test
    fun `GIVEN a backticked test name WHEN it is read THEN it uses no character a target rejects`() {
        val violations =
            sourceFiles { it !in instrumentedSourceSets }.flatMap { file ->
                file.namedTests().mapNotNull { (line, name) ->
                    val bad = name.filter { it in illegalInAnyTarget }.toSortedSet()
                    if (bad.isEmpty()) {
                        null
                    } else {
                        "${file.name}:$line uses ${bad.joinToString(" ") { "'$it'" }} in `$name`"
                    }
                }
            }

        assertTrue(
            violations.isEmpty(),
            "A test name contains a character some target rejects — Kotlin/Native fails the build " +
                "on these and a JVM run will not tell you:\n" + violations.joinToString("\n"),
        )
    }

    @Test
    fun `GIVEN an Android instrumented test WHEN its name is read THEN it has no spaces`() {
        val violations =
            sourceFiles { it in instrumentedSourceSets }.flatMap { file ->
                file
                    .namedTests()
                    .filter { (_, name) -> ' ' in name }
                    .map { (line, _) -> "${file.name}:$line is backticked with spaces" }
            }

        assertTrue(
            violations.isEmpty(),
            "An Android instrumented test cannot have a space in its method name below minSdk 30; " +
                "use GIVEN_..._WHEN_..._THEN_... there:\n" + violations.joinToString("\n"),
        )
    }

    /** The structure lives in the name (client AGENTS.md), so every test reads GIVEN, WHEN, THEN in that order. */
    @Test
    fun `GIVEN a client test WHEN its name is read THEN it says GIVEN WHEN THEN`() {
        val spaced = Regex("""^GIVEN .+ WHEN .+ THEN .+$""")
        val underscored = Regex("""^GIVEN_.+_WHEN_.+_THEN_.+$""")
        val violations =
            sourceFiles { true }.flatMap { file ->
                val instrumented = file.sourceSet() in instrumentedSourceSets
                file
                    .testFunctions()
                    .filterNot { (_, name) -> (if (instrumented) underscored else spaced).matches(name) }
                    .map { (line, name) -> "${file.name}:$line `$name`" }
            }

        assertTrue(
            violations.isEmpty(),
            "Name tests `GIVEN <situation> WHEN <action> THEN <outcome>` (GIVEN_x_WHEN_y_THEN_z in " +
                "Android instrumented tests):\n" + violations.joinToString("\n"),
        )
    }

    /** The name carries GIVEN, WHEN and THEN, so the body doesn't repeat them as comments. */
    @Test
    fun `GIVEN a client test WHEN its body is read THEN it has no GIVEN WHEN THEN comments`() {
        val sectionComment = Regex("""^\s*//\s*(GIVEN|WHEN|THEN)\b""")
        val violations =
            sourceFiles { true }.flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (sectionComment.containsMatchIn(line)) "${file.name}:${index + 1}" else null
                }
            }

        assertTrue(
            violations.isEmpty(),
            "Drop the // GIVEN, // WHEN and // THEN comments; the test name carries them:\n" +
                violations.joinToString("\n"),
        )
    }

    private val instrumentedSourceSets = setOf("androidDeviceTest", "androidTest")

    private val funName = Regex("""\bfun\s+(?:`([^`]+)`|(\w+))""")

    /** Each `@Test` paired with the name of the next function declared after it. */
    private fun File.testFunctions(): List<Pair<Int, String>> {
        val lines = readLines()
        return lines.indices
            .filter { lines[it].trim() == "@Test" }
            .mapNotNull { at ->
                (at + 1 until lines.size).firstNotNullOfOrNull { index ->
                    funName.find(lines[index])?.let { match ->
                        (index + 1) to match.groupValues[1].ifEmpty { match.groupValues[2] }
                    }
                }
            }
    }

    /** The source set a file sits in, such as `commonTest` or `androidTest`. */
    private fun File.sourceSet(): String =
        generateSequence(this) { it.parentFile }
            .first { it.parentFile?.name == "src" }
            .name

    private fun sourceFiles(sourceSet: (String) -> Boolean): List<File> =
        modules
            .map { File(clientRootDir, "$it/src") }
            .filter { it.exists() }
            .flatMap { src ->
                (src.listFiles() ?: emptyArray())
                    .filter { it.isDirectory && it.name.contains("est") && sourceSet(it.name) }
                    .flatMap { it.walkTopDown().filter { f -> f.isFile && f.extension == "kt" }.toList() }
            }

    private fun File.namedTests(): List<Pair<Int, String>> =
        readLines().mapIndexedNotNull { index, line ->
            backtickedName.find(line)?.let { (index + 1) to it.groupValues[1] }
        }
}
