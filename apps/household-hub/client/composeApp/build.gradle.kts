plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    android {
        namespace = "com.homelab.household.app"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        androidResources.enable = true
    }

    // JVM target exists to run Compose UI tests on the desktop runtime.
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:domain"))
            implementation(project(":core:presentation"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
            implementation(libs.navigation3.ui)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.jetbrains.markdown)
        }
        androidMain.dependencies {
            // The preview renderer is Android- and desktop-only; commonMain keeps
            // ui-tooling-preview, which is where @Preview itself comes from.
            implementation(libs.compose.ui.tooling)
        }
        commonTest.dependencies {
            // The UI tests run the real graph over a faked hub, so they need the DI coordinator.
            implementation(project(":shared"))
            implementation(libs.compose.ui.test)
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        jvmTest.dependencies {
            // Only what it takes to run the common tests on the desktop runtime.
            implementation(compose.desktop.currentOs)
            implementation(libs.junit.jupiter)
            implementation(libs.junit.platform.launcher)
        }
    }
}

compose.resources {
    packageOfResClass = "com.homelab.household.app.resources"
}
