/*
 * The iOS application module — the twin of `:androidApp`.
 *
 * Like `:androidApp`, this is the only module on its platform allowed to depend on **both**
 * `:composeApp` (the UI) and `:shared` (the DI coordinator): composition of the two belongs to the
 * app, not to either half. Everything Xcode needs lives here too — `project.yml` and the Swift
 * sources next to this file — so the Kotlin framework and the app that hosts it stay in one place.
 *
 * The framework is static on purpose: it is linked straight into the app binary, so there is no
 * dynamic framework to embed or sign, and `embedAndSignAppleFrameworkForXcode` (the script phase
 * `project.yml` installs) reduces to a build-and-copy.
 */

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "HouseholdHubKit"
            isStatic = true
        }
    }

    sourceSets {
        iosMain.dependencies {
            implementation(project(":composeApp"))
            implementation(project(":shared"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
        }
    }
}
