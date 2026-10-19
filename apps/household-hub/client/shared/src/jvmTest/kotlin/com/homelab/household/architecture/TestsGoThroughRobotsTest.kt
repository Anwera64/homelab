package com.homelab.household.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * A UI test reads as what the person does and sees, and its robot is where that is turned into
 * nodes. The robot names every string once, as its resource, and waits for it before asserting —
 * so a test that finds a node itself drifts back to matching literal text and asserting before the
 * hub has answered. See "How a screen is tested" in the client README.
 *
 * So outside a `*Robot.kt`, no client UI test looks a node up: no `onNode…`, `onAllNodes…`,
 * `onRoot`, or wait that names a node. Every `assert…` and `perform…` hangs off one of those, so
 * this keeps every interaction in the robot. A plain `waitUntil { … }` on a fake hub is fine:
 * it waits on the hub, not the screen.
 */
class TestsGoThroughRobotsTest {
    private val clientRootDir =
        File(System.getProperty("user.dir")).let { dir ->
            if (dir.name == "shared") dir.parentFile else dir
        }

    private val appTestDir = File(clientRootDir, "composeApp/src/commonTest/kotlin/com/homelab/household/app")
    private val screensDir = File(appTestDir, "screens")

    /** The screen-agnostic harness every robot is built on. It composes, it doesn't look. */
    private val harness = File(appTestDir, "testing")

    private val nodeLookup =
        Regex(
            """\b(onNode\w*|onAllNodes\w*|onRoot|waitUntilExactlyOneExists|waitUntilAtLeastOneExists|""" +
                """waitUntilDoesNotExist|waitUntilNodeCount)\s*\(""",
        )

    @Test
    fun `GIVEN the screen test sources WHEN node lookups and touches are scanned THEN they happen only through a robot`() {
        assertTrue(appTestDir.exists(), "Test directory must exist at: ${appTestDir.absolutePath}")

        val violations =
            appTestDir
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filterNot { it.startsWith(harness) || it.name.endsWith("Robot.kt") }
                .flatMap { file ->
                    file.readLines().mapIndexedNotNull { index, line ->
                        nodeLookup.find(line)?.let { match ->
                            "${file.toRelativeString(clientRootDir)}:${index + 1} calls ${match.groupValues[1]} " +
                                "itself — move it into the robot beside this test"
                        }
                    }
                }.toList()

        assertTrue(
            violations.isEmpty(),
            "A test looked a node up outside its robot (${violations.size}):\n" + violations.joinToString("\n"),
        )
    }

    @Test
    fun `GIVEN the screen test sources WHEN each is checked for a robot THEN every one has a robot beside it`() {
        assertTrue(screensDir.exists(), "Screens test directory must exist at: ${screensDir.absolutePath}")

        val violations =
            screensDir
                .walkTopDown()
                .filter { it.isFile && it.name.endsWith("ScreenTest.kt") }
                .filterNot { test -> test.parentFile.listFiles().orEmpty().any { it.name.endsWith("Robot.kt") } }
                .map { "${it.toRelativeString(clientRootDir)} has no *Robot.kt in its folder" }
                .toList()

        assertTrue(
            violations.isEmpty(),
            "A screen test has no robot (${violations.size}):\n" + violations.joinToString("\n"),
        )
    }
}
