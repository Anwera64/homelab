package com.homelab.household.architecture

import com.homelab.household.data.network.templateRequestPath
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * `templateRequestPath` keeps only the path words it knows and turns everything else into `{id}`,
 * because the line it feeds is sent off the phone and an invite code looks like any other word.
 * The cost of that is a list to keep up: a new endpoint whose words are not on it would be logged
 * as `/api/v1/{id}`, which is safe and useless. This reads every hub path the data sources name
 * and fails until its fixed words are on the list.
 */
class RequestPathWordsTest {
    private val clientRootDir =
        File(System.getProperty("user.dir")).let { dir ->
            if (dir.name == "shared") dir.parentFile else dir
        }

    private val remoteDir =
        File(clientRootDir, "core/data/src/commonMain/kotlin/com/homelab/household/data/datasource/remote")

    private val hubPath = Regex(""""(?:\$\{?baseUrl}?)?(/api/[^"]*)"""")

    @Test
    fun `GIVEN every hub path a data source names WHEN it is templated THEN its fixed words are all kept`() {
        // GIVEN
        val paths =
            remoteDir
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file -> hubPath.findAll(file.readText()).map { it.groupValues[1].substringBefore('?') } }
                .toSet()

        // WHEN
        val unknownWords =
            paths
                .flatMap { path -> path.split('/').filter { it.isNotEmpty() && '$' !in it } }
                .toSet()
                .filter { word -> templateRequestPath("/$word") != "/$word" }

        // THEN
        assertTrue(paths.size > 20, "Expected to find the hub's paths under ${remoteDir.absolutePath}, found $paths")
        assertTrue(
            unknownWords.isEmpty(),
            "These path words would be logged as {id}; add them to fixedSegments in RequestFailureLog.kt: $unknownWords",
        )
    }
}
