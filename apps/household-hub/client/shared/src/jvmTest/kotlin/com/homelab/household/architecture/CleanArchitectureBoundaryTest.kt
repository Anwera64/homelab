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

    @Test
    fun domain_layer_has_zero_external_or_outer_layer_dependencies() {
        assertTrue(domainDir.exists(), "Domain directory must exist at: ${domainDir.absolutePath}")
        
        val forbiddenImports = listOf(
            "com.homelab.household.data",
            "com.homelab.household.presentation",
            "com.homelab.household.di",
            "io.ktor",
            "kotlinx.serialization",
            "androidx",
            "org.koin"
        )

        val violations = mutableListOf<String>()

        domainDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val lines = file.readLines()
                lines.forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("import ")) {
                        for (forbidden in forbiddenImports) {
                            if (trimmed.startsWith("import $forbidden")) {
                                violations.add("${file.name}:${index + 1} imports forbidden dependency '$forbidden'")
                            }
                        }
                    }
                }
            }

        assertTrue(
            violations.isEmpty(),
            "Clean Architecture Violation in :core:domain:\n" + violations.joinToString("\n")
        )
    }

    @Test
    fun presentation_layer_depends_only_on_domain_and_never_on_data() {
        assertTrue(presentationDir.exists(), "Presentation directory must exist at: ${presentationDir.absolutePath}")

        val forbiddenImports = listOf(
            "com.homelab.household.data"
        )

        val violations = mutableListOf<String>()

        presentationDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val lines = file.readLines()
                lines.forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("import ")) {
                        for (forbidden in forbiddenImports) {
                            if (trimmed.startsWith("import $forbidden")) {
                                violations.add("${file.name}:${index + 1} imports forbidden data layer '$forbidden'")
                            }
                        }
                    }
                }
            }

        assertTrue(
            violations.isEmpty(),
            "Clean Architecture Violation in :core:presentation:\n" + violations.joinToString("\n")
        )
    }

    @Test
    fun data_layer_depends_only_on_domain_and_never_on_presentation() {
        assertTrue(dataDir.exists(), "Data directory must exist at: ${dataDir.absolutePath}")

        val forbiddenImports = listOf(
            "com.homelab.household.presentation"
        )

        val violations = mutableListOf<String>()

        dataDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val lines = file.readLines()
                lines.forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("import ")) {
                        for (forbidden in forbiddenImports) {
                            if (trimmed.startsWith("import $forbidden")) {
                                violations.add("${file.name}:${index + 1} imports forbidden presentation layer '$forbidden'")
                            }
                        }
                    }
                }
            }

        assertTrue(
            violations.isEmpty(),
            "Clean Architecture Violation in :core:data:\n" + violations.joinToString("\n")
        )
    }
}
