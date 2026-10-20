package com.homelab.household.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The dev and prod apps (#134): one version for both platforms, and two apps that install side by
 * side, each talking to its own hub.
 *
 * Debug builds are the dev app — `.dev` app ID, "HyggeHub Dev", the terracotta icon, the dev hub —
 * and release builds are the household's. Both platforms read the same version, `app.version` in
 * gradle.properties; Xcode cannot read that file, so `project.yml` repeats it and this test keeps
 * the two equal, along with the build number derived from it.
 */
class AppVariantsTest {
    private val clientRootDir =
        File(System.getProperty("user.dir")).let { dir ->
            if (dir.name == "shared") dir.parentFile else dir
        }

    private fun read(path: String): String = File(clientRootDir, path).readText().replace("\r\n", "\n")

    private val properties: Map<String, String> by lazy {
        read("gradle.properties")
            .lines()
            .filter { it.isNotBlank() && !it.trimStart().startsWith("#") && it.contains("=") }
            .associate { it.substringBefore("=").trim() to it.substringAfter("=").trim() }
    }

    private val androidBuild by lazy { read("androidApp/build.gradle.kts") }
    private val iosProject by lazy { read("iosApp/project.yml") }

    /** The build file's line that puts a gradle.properties address into BuildConfig. */
    private fun urlField(
        field: String,
        property: String,
    ): String = """buildConfigField("String", "$field", "\"${'$'}{gradleProperty("$property")}\"")"""

    /** The text of a block opened by [header], up to its matching close brace. */
    private fun block(
        text: String,
        header: String,
    ): String {
        val start = text.indexOf(header)
        assertTrue(start >= 0, "\"$header\" must be in the build file")
        var depth = 0
        for (i in text.indices.drop(start)) {
            when (text[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return text.substring(start, i + 1)
            }
        }
        error("\"$header\" never closes")
    }

    /** The settings of one XcodeGen configuration of the app target, as key to value. */
    private fun iosConfig(name: String): Map<String, String> {
        val section =
            Regex("""^        $name:\n((?:          [^\n]*\n)+)""", RegexOption.MULTILINE).find(iosProject)
        assertTrue(section != null, "project.yml must have a $name configuration for the app target")
        return section!!
            .groupValues[1]
            .lines()
            .filter { it.contains(":") }
            .associate { it.substringBefore(":").trim() to it.substringAfter(":").trim().trim('"') }
    }

    @Test
    fun `GIVEN gradle properties WHEN the apps build THEN one version and a hub and collector per app come from there`() {
        assertTrue(
            Regex("""^\d+\.\d+\.\d+$""").matches(properties["app.version"].orEmpty()),
            "app.version must be X.Y.Z",
        )
        assertEquals("https://hub-dev.spicy-llama.duckdns.org", properties["hub.devUrl"])
        assertEquals("https://hub.spicy-llama.duckdns.org", properties["hub.prodUrl"])
        assertEquals("https://telemetry-dev.spicy-llama.duckdns.org", properties["telemetry.devUrl"])
        assertEquals("https://telemetry.spicy-llama.duckdns.org", properties["telemetry.prodUrl"])
        assertFalse("hub.baseUrl" in properties, "one address per app, not one for both")
        // The SDK's own default, for tests and tools that start the graph without asking: dev.
        val data = read("core/data/build.gradle.kts")
        assertTrue(data.contains("""gradleProperty("hub.devUrl")"""))
        assertTrue(data.contains("""gradleProperty("telemetry.devUrl")"""))
    }

    @Test
    fun `GIVEN the Android build WHEN it reads the version THEN the name is app version and the code counts every part`() {
        assertTrue(androidBuild.contains("""providers.gradleProperty("app.version").get()"""))
        assertTrue(androidBuild.contains("versionName = appVersion"))
        assertTrue(androidBuild.contains("versionCode = appVersionCode(appVersion)"))
        // 1.2.3 -> 10203: each part two digits, so a later version always has a larger code.
        val function = block(androidBuild, "fun appVersionCode(")
        assertTrue(function.contains("major * 10000 + minor * 100 + patch"))
        assertTrue(function.contains("it in 0..99"), "a part above 99 would overlap the next one")
    }

    @Test
    fun `GIVEN a debug Android build WHEN it is installed THEN it is the dev app beside the household's`() {
        val debug = block(androidBuild, "getByName(\"debug\")")
        assertTrue(debug.contains("""applicationIdSuffix = ".dev""""))
        assertTrue(debug.contains("""versionNameSuffix = "-dev""""))
        assertTrue(debug.contains("""resValue("string", "app_name", "HyggeHub Dev")"""))
        assertTrue(debug.contains(urlField("HUB_URL", "hub.devUrl")))
        assertTrue(debug.contains(urlField("TELEMETRY_URL", "telemetry.devUrl")))

        val release = block(androidBuild, "getByName(\"release\")")
        assertTrue(release.contains("""resValue("string", "app_name", "HyggeHub")"""))
        assertTrue(release.contains(urlField("HUB_URL", "hub.prodUrl")))
        assertTrue(release.contains(urlField("TELEMETRY_URL", "telemetry.prodUrl")))
        assertTrue(release.contains("signingConfig = signingConfigs.getByName(\"release\")"))

        assertTrue(read("androidApp/src/main/AndroidManifest.xml").contains("""android:label="@string/app_name""""))
        val application = read("androidApp/src/main/kotlin/com/homelab/household/HouseholdHubApplication.kt")
        assertTrue(application.contains("baseUrl = BuildConfig.HUB_URL"))
        assertTrue(application.contains("endpoint = BuildConfig.TELEMETRY_URL"))
        assertFalse(application.contains("DEFAULT_BASE_URL"), "the app picks its hub, not the SDK default")
    }

    @Test
    fun `GIVEN a release Android build WHEN CI signs it THEN the key comes from the environment and never the repo`() {
        val signing = block(androidBuild, "signingConfigs {")
        for (name in listOf("HUB_KEYSTORE_FILE", "HUB_KEYSTORE_PASSWORD", "HUB_KEY_ALIAS", "HUB_KEY_PASSWORD")) {
            assertTrue(signing.contains("""System.getenv("$name")"""), "the release key reads $name")
        }
        assertFalse(Regex("""storePassword\s*=\s*"""").containsMatchIn(signing), "no password in the build file")
    }

    @Test
    fun `GIVEN the dev Android app WHEN the launcher draws it THEN its background is Hearth's tertiary`() {
        val main = read("androidApp/src/main/res/values/launcher_colors.xml")
        val debug = read("androidApp/src/debug/res/values/launcher_colors.xml")
        assertTrue(
            main.contains("""<color name="launcher_background">#3C6E4E</color>"""),
            "the household's stays primary",
        )
        assertTrue(debug.contains("""<color name="launcher_background">#C05638</color>"""), "the dev app's is tertiary")
    }

    @Test
    fun `GIVEN the iOS project WHEN each configuration builds THEN Debug is the dev app and Release the household's`() {
        val debug = iosConfig("Debug")
        assertEquals("com.homelab.household.dev", debug["PRODUCT_BUNDLE_IDENTIFIER"])
        assertEquals("HyggeHub Dev", debug["APP_DISPLAY_NAME"])
        assertEquals(properties["hub.devUrl"], debug["HUB_BASE_URL"])
        assertEquals(properties["telemetry.devUrl"], debug["TELEMETRY_BASE_URL"])
        assertEquals("AppIcon-Dev", debug["ASSETCATALOG_COMPILER_APPICON_NAME"])

        val release = iosConfig("Release")
        assertEquals("com.homelab.household", release["PRODUCT_BUNDLE_IDENTIFIER"])
        assertEquals("HyggeHub", release["APP_DISPLAY_NAME"])
        assertEquals(properties["hub.prodUrl"], release["HUB_BASE_URL"])
        assertEquals(properties["telemetry.prodUrl"], release["TELEMETRY_BASE_URL"])
        assertEquals("AppIcon", release["ASSETCATALOG_COMPILER_APPICON_NAME"])

        for (line in listOf(
            "CFBundleDisplayName: $(APP_DISPLAY_NAME)",
            "HubBaseUrl: $(HUB_BASE_URL)",
            "TelemetryBaseUrl: $(TELEMETRY_BASE_URL)",
        )) {
            assertTrue(iosProject.contains(line), "Info.plist must carry $line")
        }
        val controller = read("iosApp/src/iosMain/kotlin/com/homelab/household/MainViewController.kt")
        assertTrue(controller.contains("objectForInfoDictionaryKey(key)"), "the addresses come from Info.plist")
        assertTrue(controller.contains("""baseUrl = infoString("HubBaseUrl")"""), "the hub is HubBaseUrl")
        assertTrue(
            controller.contains("""endpoint = infoString("TelemetryBaseUrl")"""),
            "the collector is TelemetryBaseUrl",
        )
        assertFalse(controller.contains("DEFAULT_BASE_URL"), "the app picks its hub, not the SDK default")
    }

    @Test
    fun `GIVEN a TestFlight upload WHEN App Store Connect asks about encryption THEN the plist answers no`() {
        // Only the HTTPS built into iOS (Ktor's Darwin engine), which Apple counts as exempt.
        assertTrue(iosProject.contains("ITSAppUsesNonExemptEncryption: false"))
    }

    @Test
    fun `GIVEN the iOS project WHEN it names a version THEN it is app version and its build number`() {
        val version = properties["app.version"].orEmpty()
        val (major, minor, patch) = version.split(".").map { it.toInt() }
        assertTrue(iosProject.contains("""MARKETING_VERSION: "$version""""), "project.yml must say $version")
        assertTrue(
            iosProject.contains("CURRENT_PROJECT_VERSION: ${major * 10000 + minor * 100 + patch}"),
            "the build number is the Android version code",
        )
    }

    @Test
    fun `GIVEN the dev iOS app WHEN the home screen draws it THEN it has an icon of its own`() {
        val set = File(clientRootDir, "iosApp/HouseholdHub/Assets.xcassets/AppIcon-Dev.appiconset")
        assertTrue(File(set, "Contents.json").readText().contains("\"filename\" : \"AppIcon-Dev-1024.png\""))
        assertTrue(File(set, "AppIcon-Dev-1024.png").isFile, "the dev icon must be committed")
    }
}
