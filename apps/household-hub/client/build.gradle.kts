import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ktlint) apply false
}

// -------------------------------------------------------------------------------------------
// ktlint, applied to every module.
//
// The rules themselves live in `.editorconfig`, not here — that is the one file IntelliJ, the
// ktlint CLI and this plugin all read, so the IDE formats the way the build checks.
//
// The Compose ruleset is the point of this as much as the formatting is: it is what catches a
// composable missing its `modifier` default, a `modifier` buried behind other optional params,
// or a callback named in the past tense. A plain ktlint would pass those without a word.
// -------------------------------------------------------------------------------------------

// Read out here: the type-safe `libs` accessor belongs to this script, not to the subproject
// scope the block below runs in.
val ktlintVersion = libs.versions.ktlint.asProvider()
val composeRules = libs.compose.rules.ktlint

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<KtlintExtension> {
        version.set(ktlintVersion)
        // The plugin defaults to 1.5.0; the Compose ruleset is built against 1.8.0.
        filter {
            exclude { it.file.path.contains("${File.separator}build${File.separator}") }
        }
    }

    dependencies {
        add("ktlintRuleset", composeRules)
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
