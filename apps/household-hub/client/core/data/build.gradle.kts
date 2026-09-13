plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

val generateBuildConfig = tasks.register("generateBuildConfig") {
    val baseUrl = providers.gradleProperty("hub.baseUrl").orNull
        ?: throw GradleException("hub.baseUrl is not set; add it to gradle.properties or pass -Phub.baseUrl=...")
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
            """.trimIndent()
        )
    }
}

kotlin {
    android {
        namespace = "com.homelab.household.data"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withDeviceTestBuilder {}.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

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
