plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// One version for both apps (gradle.properties). 1.2.3 -> 10203: each part two digits, so a later
// version always gets a larger code. iOS uses the same number as its build number.
fun appVersionCode(version: String): Int {
    val parts = version.split(".").map { it.toInt() }
    require(parts.size == 3 && parts.all { it in 0..99 }) {
        "app.version must be X.Y.Z with each part 0-99, not $version"
    }
    val (major, minor, patch) = parts
    return major * 10000 + minor * 100 + patch
}

val appVersion = providers.gradleProperty("app.version").get()

fun gradleProperty(name: String): String = providers.gradleProperty(name).get()

android {
    namespace = "com.homelab.household"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "com.homelab.household"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        versionCode = appVersionCode(appVersion)
        versionName = appVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // CI signs the household's app with the release key; locally, without it, a release build
    // stays unsigned and debug builds are unaffected.
    signingConfigs {
        create("release") {
            System.getenv("HUB_KEYSTORE_FILE")?.let { storeFile = file(it) }
            storePassword = System.getenv("HUB_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("HUB_KEY_ALIAS")
            keyPassword = System.getenv("HUB_KEY_PASSWORD")
        }
    }

    // Debug builds are the dev app: their own ID, so they install beside the household's, and
    // the dev hub. Release builds are the household's.
    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "HyggeHub Dev")
            buildConfigField("String", "HUB_URL", "\"${gradleProperty("hub.devUrl")}\"")
            buildConfigField("String", "TELEMETRY_URL", "\"${gradleProperty("telemetry.devUrl")}\"")
        }
        getByName("release") {
            resValue("string", "app_name", "HyggeHub")
            buildConfigField("String", "HUB_URL", "\"${gradleProperty("hub.prodUrl")}\"")
            buildConfigField("String", "TELEMETRY_URL", "\"${gradleProperty("telemetry.prodUrl")}\"")
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.koin.android)
    // Renders the commonMain @Preview functions in Android Studio.
    debugImplementation(libs.compose.ui.tooling)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.ktor.client.core)
    androidTestImplementation(libs.kotlinx.coroutines.core)
}
