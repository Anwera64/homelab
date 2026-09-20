package com.homelab.household.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Inside `:core:data`, the rule `CleanArchitectureBoundaryTest` cannot see: **a repository never
 * does the call itself**. It orchestrates data sources — remote or local — and maps what they
 * return. A repository holding an `HttpClient` is the shape this module was reworked away from, and
 * it is what made every repository test a wire test with Ktor's own coroutines underneath it.
 *
 * [stillToConvert] is the shrinking baseline for that rework: each slice deletes its own entry, and
 * the list goes with the last one. Nothing is ever added to it.
 */
class DataLayerBoundaryTest {

    private val clientRootDir = File(System.getProperty("user.dir")).let { dir ->
        if (dir.name == "shared") dir.parentFile else dir
    }

    private val dataDir = File(clientRootDir, "core/data/src/commonMain/kotlin/com/homelab/household/data")
    private val repositoryDir = File(dataDir, "repository")
    private val dataSourceDir = File(dataDir, "datasource")

    /**
     * What has not been reworked onto data sources yet. Shrinks to empty; never grows.
     *
     * Slice 1 recorded this list by running the rules with no exemptions at all, which named these
     * eight and nothing else — `ServerStatusRepositoryImpl` was already right, which is where the
     * shape came from.
     */
    private val stillToConvert = setOf(
        "AgentRepositoryImpl.kt",       // slice 4
        "GossipRepositoryImpl.kt",      // slice 4
        "MemoryRepositoryImpl.kt",      // slice 4
        "ServerHealthMonitor.kt",       // slice 5, becomes ServerStatusRemoteDataSource
        "SessionRepositoryImpl.kt",     // slice 7
        "SpaceRepositoryImpl.kt",       // slice 4
    )

    @Test
    fun a_repository_never_holds_an_http_client() {
        assertTrue(repositoryDir.exists(), "Repository directory must exist at: ${repositoryDir.absolutePath}")

        val violations = repositoryDir.kotlinFiles()
            .filterNot { it.name in stillToConvert }
            .filter { file -> file.readLines().any { it.trim().startsWith("import io.ktor") } }
            .map { "${it.name} imports Ktor; it should take a data source instead" }

        assertTrue(
            violations.isEmpty(),
            "A repository is doing the call itself:\n" + violations.joinToString("\n")
        )
    }

    @Test
    fun only_data_sources_and_the_network_package_reach_the_hub() {
        // `di` builds the one HttpClient the data sources share — that is wiring, not a call.
        val allowed = setOf("datasource", "network", "di")

        val violations = dataDir.kotlinFiles()
            .filterNot { it.name in stillToConvert }
            .filterNot { it.relativeTo(dataDir).invariantSeparatorsPath.substringBefore('/') in allowed }
            .filter { file -> file.readLines().any { it.trim().startsWith("import io.ktor.client") } }
            .map { "${it.relativeTo(dataDir).invariantSeparatorsPath} holds a Ktor client outside datasource/ and network/" }

        assertTrue(
            violations.isEmpty(),
            "Ktor has leaked out of the data sources:\n" + violations.joinToString("\n")
        )
    }

    /**
     * DTOs below the repository, domain models above it. A data source speaks `dto/` and throws
     * domain exceptions; the repository is where `mapper/` turns one into the other.
     *
     * `ServerStatusRemoteDataSource` is the stated exception: `checkHealth()` folds an HTTP failure
     * into `ServerStatus.Offline` rather than throwing, because that is what the launch screen reads,
     * and a DTO for "offline" would only be that sealed type with another name.
     */
    @Test
    fun a_data_source_returns_dtos_not_domain_models() {
        if (!dataSourceDir.exists()) return

        val exempt = setOf("ServerStatusRemoteDataSource.kt", "KtorServerStatusRemoteDataSource.kt")

        val violations = dataSourceDir.kotlinFiles()
            .filterNot { it.name in exempt }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (line.trim().startsWith("import com.homelab.household.domain.model")) {
                        "${file.name}:${index + 1} returns a domain model; map it in the repository"
                    } else {
                        null
                    }
                }
            }

        assertTrue(
            violations.isEmpty(),
            "A data source is speaking domain models:\n" + violations.joinToString("\n")
        )
    }

    private fun File.kotlinFiles(): List<File> =
        walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
}
