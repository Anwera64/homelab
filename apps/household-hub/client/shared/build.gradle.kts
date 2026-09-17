import java.net.HttpURLConnection
import java.net.URI

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    android {
        namespace = "com.homelab.household.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            api(project(":core:data"))
            api(project(":core:presentation"))
            implementation(libs.koin.core)
            implementation(libs.ktor.client.core)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.koin.android)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.koin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.client.logging)
            implementation(libs.turbine)
        }
        jvmTest.dependencies {
            implementation(libs.junit.jupiter)
            implementation(libs.junit.platform.launcher)
        }
    }
}

// -------------------------------------------------------------------------------------------
// SSE fixture server for the iOS Darwin/NSURLSession streaming proofs.
//
// Kotlin/Native has no in-process `ServerSocket`, so the iOS SSE tests talk to an external
// Python process (`client/tools/sse_fixture.py`). These tasks start it before the simulator
// test tasks and stop it afterwards — `finalizedBy` so a failing test still cleans up, because
// a leaked server holding the fixed port breaks every later run.
// -------------------------------------------------------------------------------------------

/** Fixed on purpose. Must match `SseFixture.PORT` in `shared/src/iosTest`. */
val sseFixturePort = 8749

/** Must match `SseFixture.GAP_MILLIS`. */
val sseFixtureGapMillis = 400

/** Must match `SseFixture.PAUSE_SECONDS`; longer than NSURLSession's 60s default. */
val sseFixturePauseSeconds = 75

val sseFixtureScript: File = rootProject.layout.projectDirectory.file("tools/sse_fixture.py").asFile
val sseFixtureDir: Provider<Directory> = layout.buildDirectory.dir("sse-fixture")

val startSseFixture = tasks.register("startSseFixture") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Starts the external SSE fixture server used by the iOS streaming proofs."
    outputs.upToDateWhen { false }

    val script = sseFixtureScript
    val port = sseFixturePort
    val gapMillis = sseFixtureGapMillis
    val pauseSeconds = sseFixturePauseSeconds
    val workDir = sseFixtureDir

    doLast {
        val dir = workDir.get().asFile.apply { mkdirs() }
        val pidFile = File(dir, "fixture.pid")
        val logFile = File(dir, "fixture.log")
        pidFile.delete()
        logFile.delete()

        val process = ProcessBuilder(
            "/usr/bin/python3",
            script.absolutePath,
            "--port", port.toString(),
            "--gap-millis", gapMillis.toString(),
            "--pause-seconds", pauseSeconds.toString(),
            "--pid-file", pidFile.absolutePath
        ).redirectErrorStream(true).redirectOutput(logFile).start()

        val deadline = System.currentTimeMillis() + 20_000
        var ready = false
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive) break
            ready = runCatching {
                val connection = URI("http://127.0.0.1:$port/fixture/health")
                    .toURL().openConnection() as HttpURLConnection
                connection.connectTimeout = 500
                connection.readTimeout = 500
                try {
                    connection.responseCode == 200
                } finally {
                    connection.disconnect()
                }
            }.getOrDefault(false)
            if (ready) break
            Thread.sleep(100)
        }

        if (!ready) {
            process.destroyForcibly()
            throw GradleException(
                "The SSE fixture server never became ready on port $port.\n" +
                    "----- ${logFile.absolutePath} -----\n" +
                    logFile.takeIf { it.exists() }?.readText().orEmpty().ifBlank { "(no output)" }
            )
        }
        logger.lifecycle("SSE fixture ready on http://127.0.0.1:$port (pid ${pidFile.readText().trim()})")
    }
}

val stopSseFixture = tasks.register("stopSseFixture") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Stops the external SSE fixture server."
    outputs.upToDateWhen { false }

    val workDir = sseFixtureDir

    doLast {
        val pidFile = File(workDir.get().asFile, "fixture.pid")
        if (!pidFile.exists()) {
            logger.info("No SSE fixture pid file; nothing to stop.")
            return@doLast
        }
        val pid = pidFile.readText().trim()
        logger.lifecycle("Stopping SSE fixture (pid $pid)")
        ProcessBuilder("/bin/kill", "-TERM", pid).start().waitFor()
        val deadline = System.currentTimeMillis() + 5_000
        while (pidFile.exists() && System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
        }
        if (pidFile.exists()) {
            ProcessBuilder("/bin/kill", "-KILL", pid).start().waitFor()
            pidFile.delete()
        }
    }
}

/**
 * The long-pause proof costs more than [sseFixturePauseSeconds] seconds of wall clock, so it
 * gets its own opt-in task (`:shared:iosSimulatorArm64SlowSseLongPauseTest`) and is filtered
 * out of the ordinary `iosSimulatorArm64Test`.
 */
val longPauseProofClass = "com.homelab.household.network.DarwinSseLongPauseTest"
val slowLongPauseTaskName = "iosSimulatorArm64SlowSseLongPauseTest"

// Creates the `iosSimulatorArm64SlowSseLongPauseTest` task off the same test binary.
kotlin.iosSimulatorArm64().testRuns.create("slowSseLongPause")

tasks.withType<AbstractTestTask>().configureEach {
    when (name) {
        "iosSimulatorArm64Test" ->
            filter.excludeTestsMatching(longPauseProofClass)
        slowLongPauseTaskName -> {
            description =
                "SLOW (>${sseFixturePauseSeconds}s): proves a Darwin SSE stream survives a pause " +
                    "longer than NSURLSession's 60s default timeoutIntervalForRequest."
            filter.includeTestsMatching(longPauseProofClass)
            // `allTests` (and therefore `check` and `build`) aggregates every test task on the
            // target, so without this the opt-in proof would cost every ordinary build more than
            // a minute. It runs only when someone asks for it by name.
            onlyIf("only runs when named explicitly, because it costs over a minute") { task ->
                val requested = task.project.gradle.startParameter.taskNames
                    .any { it.substringAfterLast(':') == slowLongPauseTaskName }
                if (!requested) {
                    task.logger.lifecycle(
                        "Skipping $slowLongPauseTaskName (>${sseFixturePauseSeconds}s). " +
                            "Run `./gradlew :shared:$slowLongPauseTaskName` to execute it."
                    )
                }
                requested
            }
        }
    }
}

tasks.matching {
    it.name == "iosSimulatorArm64Test" || it.name == slowLongPauseTaskName
}.configureEach {
    dependsOn(startSseFixture)
    finalizedBy(stopSseFixture)
}

