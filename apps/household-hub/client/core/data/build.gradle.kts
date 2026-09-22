plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    // A repository's collaborators are data sources, and these are how they are mocked — a compiler
    // plugin rather than a bytecode rewriter, so the same tests run on iOS. Matches :core:domain.
    alias(libs.plugins.mokkery)
}

val generateBuildConfig =
    tasks.register("generateBuildConfig") {
        val baseUrl =
            providers.gradleProperty("hub.baseUrl").orNull
                ?: throw GradleException(
                    "hub.baseUrl is not set; add it to gradle.properties or pass -Phub.baseUrl=...",
                )
        val outputDir = layout.buildDirectory.dir("generated/source/buildConfig/commonMain/kotlin")
        inputs.property("baseUrl", baseUrl)
        outputs.dir(outputDir)
        doLast {
            val file = outputDir.get().file("com/homelab/household/data/BuildConfig.kt").asFile
            file.parentFile.mkdirs()
            file.writeText(
                """
                package com.homelab.household.data

                object BuildConfig {
                    const val BASE_URL: String = "$baseUrl"
                }
                """.trimIndent(),
            )
        }
    }

kotlin {
    android {
        namespace = "com.homelab.household.data"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()

        withDeviceTestBuilder {}.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            kotlin.srcDir(generateBuildConfig)
            dependencies {
                implementation(project(":core:domain"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.ktor.client.logging)
                implementation(libs.kermit)
                implementation(libs.ktor.client.auth)
                implementation(libs.koin.core)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.turbine)
        }
        jvmTest.dependencies {
            implementation(libs.junit.jupiter)
            implementation(libs.junit.platform.launcher)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.test.ext.junit)
        }
    }
}

/**
 * Mirror of [jvmTest] that forces Ktor's `HttpStatement.execute` to run its block on the engine's
 * dispatcher, as it does unconditionally on every non-JVM target (and will do everywhere in Ktor 4).
 * This makes the flow-context invariant observable on the JVM, guarding against re-introducing an
 * `emit` that crosses the `execute` dispatcher boundary.
 */
val jvmEngineDispatcherTest =
    tasks.register<Test>("jvmEngineDispatcherTest") {
        val jvmTestCompilation = kotlin.jvm().compilations.getByName("test")
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Runs the JVM tests with io.ktor.client.statement.useEngineDispatcher=true."
        testClassesDirs = jvmTestCompilation.output.classesDirs
        classpath = jvmTestCompilation.output.allOutputs + jvmTestCompilation.runtimeDependencyFiles
        useJUnitPlatform()
        systemProperty("io.ktor.client.statement.useEngineDispatcher", "true")
    }

tasks.named("check") {
    dependsOn(jvmEngineDispatcherTest)
}
