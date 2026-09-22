package com.homelab.household.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class CoroutineSafetyTest {
    private val clientRootDir =
        File(System.getProperty("user.dir")).let { dir ->
            if (dir.name == "shared") dir.parentFile else dir
        }

    private val sourceDirs =
        listOf("core/domain", "core/data", "core/presentation", "shared", "composeApp", "androidApp")
            .map { File(clientRootDir, "$it/src") }
            .filter { it.exists() }

    /** `runCatching` also catches `CancellationException`; `runCatchingSafe` rethrows it. */
    @Test
    fun sources_use_run_catching_safe_instead_of_run_catching() {
        val runCatchingCall = Regex("""\brunCatching\s*\{""")

        val violations =
            sourceDirs.flatMap { dir ->
                dir
                    .walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .flatMap { file ->
                        file.readLines().mapIndexedNotNull { index, line ->
                            if (runCatchingCall.containsMatchIn(line)) "${file.name}:${index + 1}" else null
                        }
                    }.toList()
            }

        assertTrue(
            violations.isEmpty(),
            "runCatching swallows coroutine cancellation; use runCatchingSafe:\n" + violations.joinToString("\n"),
        )
    }
}
