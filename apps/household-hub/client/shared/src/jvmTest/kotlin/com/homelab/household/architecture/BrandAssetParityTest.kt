package com.homelab.household.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.roundToInt

/**
 * The brand artwork exists in more than one place and cannot be generated from one source.
 * `HearthColors.kt` is what Compose draws, but the Android window has already been painted
 * before Compose runs — that colour lives in `res/values/colors.xml` — and iOS paints its
 * launch screen from an asset catalog that no Kotlin ever reads. Each copy is hand-typed
 * because the alternative (an SVG → Android-vector → asset-catalog generator) is a larger
 * program than the handful of hex strings it would deduplicate.
 *
 * So the copies stay hand-typed and this guard makes divergence impossible to merge. It is
 * also the *only* thing that looks at the iOS asset catalog on CI: the `jvm-tests` job runs on
 * a Linux runner where no Xcode exists to notice that a colour set drifted, or vanished.
 *
 * The comparison is deliberately loose about spelling and tight about value: hex is normalised
 * (uppercase, no `#`, no alpha prefix) and the JSON is read structurally, so reformatting a
 * file, reordering its attributes or pretty-printing it never turns this red. Only a changed
 * colour does.
 *
 * Note on parsing: `kotlinx.serialization.json` is *not* on the `:shared` jvmTest compile
 * classpath — `:core:data` declares it as `implementation`, so it does not leak through the
 * `api(project(":core:data"))` in `shared/build.gradle.kts`. Adding a dependency for one test
 * is not worth it, so the asset catalog is read with a small string-aware scanner below. It is
 * structural (brace matching, not line matching), which is all the looseness this needs.
 */
class BrandAssetParityTest {

    private val clientRootDir = File(System.getProperty("user.dir")).let { dir ->
        if (dir.name == "shared") dir.parentFile else dir
    }

    /** The one file that decides what Hearth looks like. Every other copy answers to it. */
    private val hearthColorsFile = File(
        clientRootDir,
        "composeApp/src/commonMain/kotlin/com/homelab/household/app/theme/HearthColors.kt"
    )

    private val androidDayColors = File(clientRootDir, "androidApp/src/main/res/values/colors.xml")
    private val androidNightColors = File(clientRootDir, "androidApp/src/main/res/values-night/colors.xml")
    private val androidLauncherColors = File(clientRootDir, "androidApp/src/main/res/values/launcher_colors.xml")
    private val iosCanvasColorSet = File(
        clientRootDir,
        "iosApp/HouseholdHub/Assets.xcassets/HearthCanvas.colorset/Contents.json"
    )

    private fun File.relative(): String = toRelativeString(clientRootDir)

    // ---------------------------------------------------------------------------------------
    // The canonical palette
    // ---------------------------------------------------------------------------------------

    /**
     * Reads a token out of `val DayColors = HearthColors(...)`.
     *
     * If the shape ever changes, this fails and says so. That matters more than it sounds: a
     * lenient reader that returned `null` here would make every assertion below compare nothing
     * to nothing and pass, and a guard that passes because it stopped reading its own source of
     * truth is worse than no guard at all.
     */
    private fun canonicalHex(palette: String, token: String): String {
        if (!hearthColorsFile.exists()) {
            fail<Nothing>(
                "The canonical Hearth palette is expected at ${hearthColorsFile.relative()}; " +
                    "it does not exist. Every colour asserted here is read from it, so this guard " +
                    "cannot run without it."
            )
        }

        val source = hearthColorsFile.readText()
        val declaration = Regex("""val\s+$palette\s*=\s*HearthColors\s*\(([\s\S]*?)\n\s*\)""")
            .find(source)
            ?: fail(
                "${hearthColorsFile.relative()} no longer declares `val $palette = HearthColors(...)` " +
                    "— this guard reads it as the source of truth for every other copy of the brand " +
                    "colours (Android colors.xml, the launcher colours, the iOS asset catalog). " +
                    "Either restore that shape or teach this test the new one."
            )

        val body = declaration.groupValues[1]
        val entry = Regex("""\b$token\s*=\s*Color\s*\(\s*(0[xX][0-9A-Fa-f]{6,8})\s*\)""").find(body)
            ?: fail(
                "`$palette` in ${hearthColorsFile.relative()} no longer has a " +
                    "`$token = Color(0xAARRGGBB)` entry — this guard reads it as the source of truth " +
                    "for the copies of that colour in Android resources and the iOS asset catalog."
            )

        return normaliseHex(entry.groupValues[1])
    }

    /**
     * `#F5F2EB`, `0xFFF5F2EB` and `f5f2eb` are the same colour written three ways, and which one
     * a file uses is that file's convention, not a difference worth failing over. Alpha is
     * dropped: nothing in the brand set is translucent, and the Android and iOS spellings carry
     * it differently (`0xFF` prefix vs. a separate `"alpha"` component).
     */
    private fun normaliseHex(raw: String): String {
        val digits = raw.trim().removePrefix("#").removePrefix("0x").removePrefix("0X").uppercase()
        return if (digits.length == 8) digits.substring(2) else digits
    }

    // ---------------------------------------------------------------------------------------
    // Android colour resources
    // ---------------------------------------------------------------------------------------

    /**
     * `<color name="…">#RRGGBB</color>`, read without caring where the attribute sits on the
     * element or how the file is indented.
     */
    private fun androidColor(file: File, name: String): String {
        if (!file.exists()) {
            fail<Nothing>(
                "${file.relative()} does not exist; it mirrors the Hearth palette for the Android " +
                    "window and splash, which are painted before Compose draws anything."
            )
        }
        val match = Regex("""<color\b[^>]*\bname\s*=\s*"$name"[^>]*>([^<]*)</color>""")
            .find(file.readText())
            ?: fail(
                "${file.relative()} has no <color name=\"$name\">. It mirrors the Hearth palette, " +
                    "so removing the name silently un-guards that colour."
            )
        return normaliseHex(match.groupValues[1])
    }

    private fun mismatches(
        file: File,
        palette: String,
        pairs: List<Triple<String, String, String>>
    ): List<String> = pairs.mapNotNull { (resourceName, token, actual) ->
        val expected = canonicalHex(palette, token)
        if (actual == expected) null else {
            "${file.relative()}: $resourceName is #$actual but $palette.$token in " +
                "${hearthColorsFile.relative()} is #$expected"
        }
    }

    /**
     * The window background and the splash tile are drawn by the system from these, so a drift
     * here shows up as a flash of the wrong colour at launch — the one moment no screenshot test
     * is watching.
     */
    @Test
    fun android_day_resources_mirror_the_day_palette() {
        val drifted = mismatches(
            androidDayColors,
            "DayColors",
            listOf(
                Triple("hearth_canvas", "canvas", androidColor(androidDayColors, "hearth_canvas")),
                Triple("hearth_primary", "primary", androidColor(androidDayColors, "hearth_primary")),
                Triple("hearth_on_primary", "onPrimary", androidColor(androidDayColors, "hearth_on_primary"))
            )
        )

        assertTrue(
            drifted.isEmpty(),
            "Android day colours drifted from DayColors (${drifted.size}):\n" + drifted.joinToString("\n")
        )
    }

    @Test
    fun android_night_resources_mirror_the_night_palette() {
        val drifted = mismatches(
            androidNightColors,
            "NightColors",
            listOf(
                Triple("hearth_canvas", "canvas", androidColor(androidNightColors, "hearth_canvas")),
                Triple("hearth_primary", "primary", androidColor(androidNightColors, "hearth_primary")),
                Triple("hearth_on_primary", "onPrimary", androidColor(androidNightColors, "hearth_on_primary"))
            )
        )

        assertTrue(
            drifted.isEmpty(),
            "Android night colours drifted from NightColors (${drifted.size}):\n" + drifted.joinToString("\n")
        )
    }

    /**
     * The launcher icon keeps the day green in every theme on purpose — an app icon is the app's
     * mark, not part of its UI — so `values-night` has no launcher colours and that absence is
     * correct, not a gap this guard is asking anyone to fill. What it does check is that the one
     * palette the icon *does* mirror is still the day one.
     */
    @Test
    fun the_launcher_icon_keeps_the_day_green() {
        val drifted = mismatches(
            androidLauncherColors,
            "DayColors",
            listOf(
                Triple(
                    "launcher_background",
                    "primary",
                    androidColor(androidLauncherColors, "launcher_background")
                ),
                Triple(
                    "launcher_glyph",
                    "onPrimary",
                    androidColor(androidLauncherColors, "launcher_glyph")
                )
            )
        )

        assertTrue(
            drifted.isEmpty(),
            "The launcher icon no longer carries the day Hearth green (${drifted.size}):\n" +
                drifted.joinToString("\n") +
                "\n(By design the launcher icon has no night variant — it mirrors DayColors in " +
                "every theme. Fix the day values; do not add a values-night copy.)"
        )
    }

    // ---------------------------------------------------------------------------------------
    // The iOS asset catalog
    // ---------------------------------------------------------------------------------------

    /** One entry of an asset-catalog `"colors"` array, reduced to what this guard cares about. */
    private data class ColorSetEntry(val isDark: Boolean, val hex: String)

    /**
     * Returns the substring inside the brackets of `"colors": [ … ]`, found by matching brackets
     * rather than by reading lines, so pretty-printed and minified catalogs both work.
     */
    private fun colorsArrayBody(json: String, file: File): String {
        val key = Regex(""""colors"\s*:\s*\[""").find(json)
            ?: fail(
                "${file.relative()} has no \"colors\" array. An Xcode colour set is a JSON object " +
                    "with a \"colors\" array of appearance entries; this file is not one."
            )
        return sliceBalanced(json, key.range.last, file, "the \"colors\" array")
    }

    /** Each `{ … }` directly inside the colours array. */
    private fun entryBodies(arrayBody: String, file: File): List<String> {
        val bodies = mutableListOf<String>()
        var index = 0
        while (index < arrayBody.length) {
            if (arrayBody[index] == '{') {
                val body = sliceBalanced(arrayBody, index, file, "a colour entry")
                bodies += body
                index += body.length + 2
            } else {
                index++
            }
        }
        return bodies
    }

    /**
     * From the opening bracket/brace at [start], returns everything up to its partner, ignoring
     * brackets that sit inside strings.
     */
    private fun sliceBalanced(text: String, start: Int, file: File, what: String): String {
        var depth = 0
        var inString = false
        var escaped = false
        var index = start
        while (index < text.length) {
            val character = text[index]
            when {
                escaped -> escaped = false
                inString && character == '\\' -> escaped = true
                character == '"' -> inString = !inString
                inString -> Unit
                character == '[' || character == '{' -> depth++
                character == ']' || character == '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start + 1, index)
                }
            }
            index++
        }
        return fail(
            "${file.relative()} is not valid JSON: $what is never closed. This guard is the only " +
                "check on the iOS asset catalog that runs on CI, so a catalog it cannot read is a " +
                "catalog nobody is checking."
        )
    }

    /**
     * Asset catalogs write a channel as `"0xF5"`, as `"245"` or as a `"0.961"` fraction depending
     * on which Xcode dialog wrote it. All three mean the same byte, so all three are accepted and
     * reduced to two hex digits.
     */
    private fun componentToHex(raw: String, channel: String, file: File): String {
        val value = raw.trim().trim('"')
        val byte = when {
            value.startsWith("0x") || value.startsWith("0X") ->
                value.substring(2).toIntOrNull(16)
            value.contains('.') ->
                value.toDoubleOrNull()?.let { (it * 255.0).roundToInt() }
            else -> value.toIntOrNull()
        }
        return byte?.takeIf { it in 0..255 }?.let { "%02X".format(it) }
            ?: fail(
                "${file.relative()} has an unreadable \"$channel\" component: \"$value\". A colour " +
                    "component is a hex byte (\"0xF5\"), a decimal byte (\"245\") or a fraction " +
                    "(\"0.961\")."
            )
    }

    private fun colorSetEntries(file: File): List<ColorSetEntry> {
        val json = file.readText()
        return entryBodies(colorsArrayBody(json, file), file).map { body ->
            val appearances = Regex(""""appearances"\s*:\s*\[([\s\S]*?)]""").find(body)?.groupValues?.get(1)
            val isDark = appearances != null &&
                Regex(""""value"\s*:\s*"dark"""").containsMatchIn(appearances)

            val channels = listOf("red", "green", "blue").map { channel ->
                val match = Regex(""""$channel"\s*:\s*("[^"]*"|[0-9.]+)""").find(body)
                    ?: fail<MatchResult>(
                        "${file.relative()} has a colour entry with no \"$channel\" component. " +
                            "Every entry needs red, green and blue so it can be compared with the " +
                            "canonical palette in ${hearthColorsFile.relative()}."
                    )
                componentToHex(match.groupValues[1], channel, file)
            }
            ColorSetEntry(isDark, channels.joinToString(""))
        }
    }

    /**
     * iOS paints its launch background from this colour set, before any Kotlin runs. It is also
     * the copy nothing else can catch: CI builds the JVM tests on Linux, so if this file drifts,
     * goes missing or loses its dark appearance, the first person to notice is a user watching
     * the app open onto the wrong colour.
     */
    @Test
    fun the_ios_canvas_colour_set_carries_both_hearth_canvases() {
        assertTrue(
            iosCanvasColorSet.exists(),
            "The iOS launch background lives in ${iosCanvasColorSet.relative()}; it does not exist. " +
                "It is the asset-catalog copy of HearthColors.kt's canvas — the colour iOS paints " +
                "before Compose draws — and it is expected to be a colour set with a universal " +
                "entry matching DayColors.canvas and a `luminosity: dark` entry matching " +
                "NightColors.canvas."
        )

        val entries = colorSetEntries(iosCanvasColorSet)
        val light = entries.filterNot { it.isDark }
        val dark = entries.filter { it.isDark }

        assertTrue(
            light.size == 1,
            "${iosCanvasColorSet.relative()} should have exactly one entry with no \"appearances\" " +
                "— the one iOS uses in light mode and as the fallback — but it has ${light.size}."
        )
        assertTrue(
            dark.size == 1,
            "${iosCanvasColorSet.relative()} should have exactly one entry with " +
                "\"appearances\": [{\"appearance\": \"luminosity\", \"value\": \"dark\"}] — the one " +
                "iOS uses in dark mode — but it has ${dark.size}. Without it, a night-mode launch " +
                "flashes the day canvas."
        )

        val expectedDay = canonicalHex("DayColors", "canvas")
        val expectedNight = canonicalHex("NightColors", "canvas")
        val drifted = buildList {
            light.singleOrNull()?.let {
                if (it.hex != expectedDay) {
                    add(
                        "${iosCanvasColorSet.relative()}: the universal entry is #${it.hex} but " +
                            "DayColors.canvas in ${hearthColorsFile.relative()} is #$expectedDay"
                    )
                }
            }
            dark.singleOrNull()?.let {
                if (it.hex != expectedNight) {
                    add(
                        "${iosCanvasColorSet.relative()}: the dark entry is #${it.hex} but " +
                            "NightColors.canvas in ${hearthColorsFile.relative()} is #$expectedNight"
                    )
                }
            }
        }

        assertTrue(
            drifted.isEmpty(),
            "The iOS canvas colour set drifted from HearthColors.kt (${drifted.size}):\n" +
                drifted.joinToString("\n")
        )
    }
}
