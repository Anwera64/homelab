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

    /**
     * `java.`/`javax.` compile happily on the JVM and only break when someone finally builds for
     * iOS, which is what makes a fast JVM-side rule worth having. `platform.` (the Kotlin/Native
     * Apple frameworks) is the opposite: it does not resolve on the JVM at all, so the compiler
     * rejects it here before this test can. It is listed anyway to state the intent in one place —
     * but do not try to prove it by adding such an import, because the build fails first.
     */
    @Test
    fun common_main_has_no_platform_imports() {
        val violations = commonMainDirs.flatMap {
            importViolations(
                it,
                listOf(
                    "java.",
                    "javax.",
                    "android.",
                    "platform.",
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

    /**
     * Use case implementations are named in exactly one place: the Koin module that binds them.
     * Every other file depends on the `fun interface` protocol in
     * `com.homelab.household.domain.usecase`, so a ViewModel's collaborators stay mockable by a
     * compile-time mocking library on iOS.
     */
    @Test
    fun use_case_implementations_are_named_only_by_the_di_module() {
        val outsideDomainDirs = commonMainDirs.filterNot { it == domainDir }
        assertTrue(outsideDomainDirs.isNotEmpty(), "No commonMain directories found outside :core:domain")

        val violations = outsideDomainDirs
            .flatMap { importViolations(it, listOf("com.homelab.household.domain.usecase.impl")) }
            .filterNot { it.startsWith("DomainModule.kt:") }

        assertTrue(
            violations.isEmpty(),
            "Use case implementation imported outside :core:domain (depend on the protocol instead, " +
                "only DomainModule.kt may name an implementation):\n" + violations.joinToString("\n")
        )
    }

    /**
     * Production dependencies only. A test source set may depend outward — the full-stack UI
     * tests in `:composeApp` wire the real graph from `:shared` — without the app itself being
     * able to reach past `:core:presentation`.
     */
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
            "Production module dependency points outward:\n" + violations.joinToString("\n")
        )
    }

    /**
     * The `project(":x")` references a build file declares for its production source sets.
     *
     * Skips test declarations in both styles the client uses: a `<name>Test.dependencies { }`
     * block in the multiplatform source-set DSL, and a `*[tT]est*Implementation(...)` line in a
     * plain `dependencies { }` block.
     */
    private fun projectDependencies(buildFile: File): Set<String> {
        val projectReference = Regex("""project\("(:[^"]+)"\)""")
        val testSourceSet = Regex("""\w*[tT]est\w*\.dependencies\s*\{""")
        val testConfiguration = Regex("""^\w*[tT]est\w*\s*\(""")

        val dependencies = mutableSetOf<String>()
        var depth = 0
        var testBlockDepth: Int? = null

        buildFile.forEachLine { line ->
            if (testBlockDepth == null && testSourceSet.containsMatchIn(line)) {
                testBlockDepth = depth
            }

            val isProduction = testBlockDepth == null && !testConfiguration.containsMatchIn(line.trim())
            if (isProduction) {
                projectReference.findAll(line).forEach { dependencies += it.groupValues[1] }
            }

            depth += line.count { it == '{' } - line.count { it == '}' }
            testBlockDepth?.let { if (depth <= it) testBlockDepth = null }
        }

        return dependencies
    }

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
