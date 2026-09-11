package com.homelab.household.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class CleanArchitectureBoundaryTest {

    private val clientRootDir = File(System.getProperty("user.dir")).let { dir ->
        if (dir.name == "shared") dir.parentFile else dir
    }

    private val domainDir = File(clientRootDir, "core/domain/src/commonMain/kotlin")
    private val dataDir = File(clientRootDir, "core/data/src/commonMain/kotlin")
    private val presentationDir = File(clientRootDir, "core/presentation/src/commonMain/kotlin")
    private val uiDir = File(clientRootDir, "composeApp/src/commonMain/kotlin")

    private val commonMainDirs = listOf("core/domain", "core/data", "core/presentation", "shared", "composeApp")
        .map { File(clientRootDir, "$it/src/commonMain/kotlin") }
        .filter { it.exists() }

    @Test
    fun domain_layer_has_zero_external_or_outer_layer_dependencies() {
        assertTrue(domainDir.exists(), "Domain directory must exist at: ${domainDir.absolutePath}")

        val violations = importViolations(
            domainDir,
            listOf(
                "com.homelab.household.data",
                "com.homelab.household.presentation",
                "com.homelab.household.di",
                "io.ktor",
                "kotlinx.serialization",
                "androidx",
                "org.koin"
            )
        )

        assertTrue(
            violations.isEmpty(),
            "Clean Architecture Violation in :core:domain:\n" + violations.joinToString("\n")
        )
    }

    @Test
    fun presentation_layer_depends_only_on_domain_and_never_on_data() {
        assertTrue(presentationDir.exists(), "Presentation directory must exist at: ${presentationDir.absolutePath}")

        val violations = importViolations(presentationDir, listOf("com.homelab.household.data"))

        assertTrue(
            violations.isEmpty(),
            "Clean Architecture Violation in :core:presentation:\n" + violations.joinToString("\n")
        )
    }

    @Test
    fun data_layer_depends_only_on_domain_and_never_on_presentation() {
        assertTrue(dataDir.exists(), "Data directory must exist at: ${dataDir.absolutePath}")

        val violations = importViolations(dataDir, listOf("com.homelab.household.presentation"))

        assertTrue(
            violations.isEmpty(),
            "Clean Architecture Violation in :core:data:\n" + violations.joinToString("\n")
        )
    }

    @Test
    fun common_main_has_no_platform_imports() {
        val violations = commonMainDirs.flatMap {
            importViolations(
                it,
                listOf(
                    "java.",
                    "javax.",
                    "android.",
                    "io.ktor.client.engine.cio",
                    "io.ktor.client.engine.okhttp",
                    "io.ktor.client.engine.darwin"
                )
            )
        }

        assertTrue(
            violations.isEmpty(),
            "Platform API in commonMain (must stay iOS-compatible):\n" + violations.joinToString("\n")
        )
    }

    @Test
    fun ui_layer_imports_only_presentation_and_domain() {
        assertTrue(uiDir.exists(), "UI directory must exist at: ${uiDir.absolutePath}")

        val violations = importViolations(
            uiDir,
            listOf(
                "com.homelab.household.data",
                "com.homelab.household.di",
                "com.homelab.household.sdk",
                "io.ktor"
            )
        )

        assertTrue(
            violations.isEmpty(),
            "Clean Architecture Violation in :composeApp:\n" + violations.joinToString("\n")
        )
    }

    @Test
    fun module_dependencies_point_inward() {
        val allowedProjectDependencies = mapOf(
            "core/domain" to emptySet(),
            "core/data" to setOf(":core:domain"),
            "core/presentation" to setOf(":core:domain"),
            "composeApp" to setOf(":core:domain", ":core:presentation")
        )

        val violations = allowedProjectDependencies.flatMap { (module, allowed) ->
            val buildFile = File(clientRootDir, "$module/build.gradle.kts")
            assertTrue(buildFile.exists(), "Build file must exist at: ${buildFile.absolutePath}")
            projectDependencies(buildFile)
                .filterNot { it in allowed }
                .map { "$module depends on $it (allowed: ${allowed.ifEmpty { setOf("none") }})" }
        }

        assertTrue(
            violations.isEmpty(),
            "Module dependency points outward:\n" + violations.joinToString("\n")
        )
    }

    private fun projectDependencies(buildFile: File): Set<String> =
        Regex("""project\("(:[^"]+)"\)""")
            .findAll(buildFile.readText())
            .map { it.groupValues[1] }
            .toSet()

    private fun importViolations(sourceDir: File, forbiddenImports: List<String>): List<String> =
        sourceDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    val trimmed = line.trim()
                    forbiddenImports.firstOrNull { trimmed.startsWith("import $it") }
                        ?.let { "${file.name}:${index + 1} imports forbidden dependency '$it'" }
                }
            }
            .toList()
}
