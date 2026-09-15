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

    /** The other half of the brand: the one drawing of the household glyph. */
    private val hearthIconFile = File(
        clientRootDir,
        "composeApp/src/commonMain/kotlin/com/homelab/household/app/icons/HearthIcon.kt"
    )

    private val androidSplashTile = File(clientRootDir, "androidApp/src/main/res/drawable/splash_tile.xml")
    private val androidLauncherGlyph =
        File(clientRootDir, "androidApp/src/main/res/drawable/ic_launcher_foreground.xml")

    private val iosLaunchTileDir =
        File(clientRootDir, "iosApp/HouseholdHub/Assets.xcassets/HearthLaunchTile.imageset")
    private val iosLaunchTileDay = File(iosLaunchTileDir, "hearth_launch_tile.svg")
    private val iosLaunchTileNight = File(iosLaunchTileDir, "hearth_launch_tile_dark.svg")
    private val iosLaunchTileManifest = File(iosLaunchTileDir, "Contents.json")

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
     * `#F5F2EB`, `0xFFF5F2EB`, `f5f2eb` and the SVG shorthand `#FFF` are the same colours written
     * four ways, and which one a file uses is that file's convention, not a difference worth
     * failing over. Alpha is dropped: nothing in the brand set is translucent, and the spellings
     * carry it differently (`0xFF` prefix vs. a separate `"alpha"` component vs. an SVG attribute).
     */
    private fun normaliseHex(raw: String): String {
        val digits = raw.trim().removePrefix("#").removePrefix("0x").removePrefix("0X").uppercase()
        return when (digits.length) {
            8 -> digits.substring(2)
            3 -> digits.map { "$it$it" }.joinToString("")
            else -> digits
        }
    }

    /**
     * SVG and Android vector path data are the same little language, and both let a reformatter
     * move whitespace and commas around freely: `M3.6 10.4 12 3.8l8.4 6.6` and
     * `M3.6,10.4 12,3.8 l8.4,6.6` are one path written twice. Separators collapse to a single
     * space and every command letter gets one in front of it, which makes those two spellings
     * identical without going anywhere near parsing the grammar.
     *
     * Case is deliberately preserved: `l` and `L` are relative and absolute, a real difference.
     * Because the same transform is applied to both sides it can only ever make more spellings
     * equal — it can never turn a changed coordinate green.
     */
    private fun normalisePath(raw: String): String = raw
        .replace(Regex("""(?=[MmLlHhVvCcSsQqTtAaZz])"""), " ")
        .replace(Regex("""[\s,]+"""), " ")
        .trim()

    /** `<path …>` / `<color …>` style elements, without caring how they are wrapped or indented. */
    private fun elements(markup: String, tag: String): List<String> =
        Regex("""<$tag\b[^>]*>""").findAll(markup).map { it.value }.toList()

    /**
     * One attribute off an element. Exact-name matching on purpose: `stroke` must not be answered
     * by `stroke-width`, and `fill` must not be answered by `fill-rule`.
     */
    private fun attributeOf(element: String, name: String): String? =
        Regex("""\b${Regex.escape(name)}\s*=\s*"([^"]*)"""").find(element)?.groupValues?.get(1)

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
     * Returns the substring inside the brackets of `"<key>": [ … ]`, found by matching brackets
     * rather than by reading lines, so pretty-printed and minified catalogs both work.
     *
     * One reader serves both catalog shapes on purpose: a colour set keeps its entries under
     * `"colors"` and an image set under `"images"`, and past that one key the two files are the
     * same thing — a flat array of appearance-tagged entries.
     */
    private fun arrayBody(json: String, file: File, key: String, kind: String): String {
        val found = Regex("""["]$key["]\s*:\s*\[""").find(json)
            ?: fail(
                "${file.relative()} has no \"$key\" array. An Xcode $kind is a JSON object with a " +
                    "\"$key\" array of appearance entries; this file is not one."
            )
        return sliceBalanced(json, found.range.last, file, "the \"$key\" array")
    }

    /** Each `{ … }` directly inside an entry array. */
    private fun entryBodies(arrayBody: String, file: File, entryLabel: String): List<String> {
        val bodies = mutableListOf<String>()
        var index = 0
        while (index < arrayBody.length) {
            if (arrayBody[index] == '{') {
                val body = sliceBalanced(arrayBody, index, file, entryLabel)
                bodies += body
                index += body.length + 2
            } else {
                index++
            }
        }
        return bodies
    }

    /**
     * Xcode tags a dark-mode entry with
     * `"appearances": [{"appearance": "luminosity", "value": "dark"}]`, and an entry with no
     * `"appearances"` at all is the universal one it falls back to. Colour sets and image sets
     * spell this identically, so both readers ask the same question here.
     *
     * Both halves of the tag are required. `"value": "dark"` alone also appears under other
     * appearance axes, and matching it loosely would let an entry tagged on the wrong axis pass
     * as the dark one.
     */
    private fun hasDarkAppearance(entryBody: String): Boolean {
        val appearances = Regex(""""appearances"\s*:\s*\[([\s\S]*?)]""")
            .find(entryBody)?.groupValues?.get(1) ?: return false
        return Regex(""""appearance"\s*:\s*"luminosity"""").containsMatchIn(appearances) &&
            Regex(""""value"\s*:\s*"dark"""").containsMatchIn(appearances)
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
        return entryBodies(arrayBody(json, file, "colors", "colour set"), file, "a colour entry").map { body ->
            val isDark = hasDarkAppearance(body)

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

    // ---------------------------------------------------------------------------------------
    // The household glyph
    // ---------------------------------------------------------------------------------------

    /**
     * The two path strings of `HearthIcon.Household`, which is the glyph every other copy is a
     * copy of.
     *
     * The regex holds `strokes(...)` to parenthesis-free contents on purpose. Other icons in the
     * set build their shapes from `rect(...)` and `circle(...)` helpers, and if `Household` ever
     * became one of those, its drawing would no longer be two literal paths that a drawable can
     * mirror — so this must fail loudly rather than quietly find nothing.
     */
    private fun canonicalGlyphPaths(): List<String> {
        if (!hearthIconFile.exists()) {
            fail<Nothing>(
                "The canonical household glyph is expected at ${hearthIconFile.relative()}; it does " +
                    "not exist. Every copy of the glyph asserted here is read from it, so this guard " +
                    "cannot run without it."
            )
        }

        val declaration =
            Regex("""Household\s*\(\s*"household"\s*,\s*strokes\s*\(([^()]*)\)""").find(hearthIconFile.readText())
                ?: fail(
                    "${hearthIconFile.relative()} no longer declares " +
                        "`Household(\"household\", strokes(\"…\", \"…\"))` — this guard reads those two " +
                        "path strings as the source of truth for the glyph in splash_tile.xml, " +
                        "ic_launcher_foreground.xml and the iOS launch tile SVGs. Either restore that " +
                        "shape or teach this test the new one."
                )

        val literals = Regex(""""([^"]*)"""").findAll(declaration.groupValues[1])
            .map { it.groupValues[1] }
            .toList()

        if (literals.size != 2) {
            fail<Nothing>(
                "`HearthIcon.Household` in ${hearthIconFile.relative()} is built from " +
                    "${literals.size} path string(s); this guard expects the two the drawables and " +
                    "SVGs copy. Adding or removing a stroke means every copy needs the same change, " +
                    "which is what this failure is asking for."
            )
        }

        return literals.map { normalisePath(it) }
    }

    /**
     * The stroked paths of an Android vector drawable, in document order.
     *
     * Stroked, not "the last two": `splash_tile.xml` also draws the rounded-square tile body, and
     * that path is a `fillColor` with no stroke. Selecting on `strokeColor` says what the rule
     * actually means — the glyph is the drawing made of strokes — where counting from the end
     * would quietly pick up the wrong path the day someone adds a shape below the group.
     */
    private fun androidStrokedPaths(file: File): List<String> {
        if (!file.exists()) {
            fail<Nothing>(
                "${file.relative()} does not exist; it carries the hand-copied household glyph " +
                    "from ${hearthIconFile.relative()}."
            )
        }

        val stroked = elements(file.readText(), "path")
            .filter { attributeOf(it, "android:strokeColor") != null }

        if (stroked.isEmpty()) {
            fail<Nothing>(
                "${file.relative()} has no <path> with an android:strokeColor. The household glyph " +
                    "is drawn as stroked paths, and this guard finds it by that; a glyph converted " +
                    "to fills would leave the copy unguarded."
            )
        }

        return stroked.map { element ->
            attributeOf(element, "android:pathData") ?: fail(
                "${file.relative()} has a stroked <path> with no android:pathData, so there is " +
                    "nothing to compare with the canonical glyph."
            )
        }.map { normalisePath(it) }
    }

    private fun glyphDrift(file: File, actual: List<String>, canonical: List<String>): List<String> {
        if (actual.size != canonical.size) {
            return listOf(
                "${file.relative()} draws ${actual.size} glyph path(s) but " +
                    "HearthIcon.Household has ${canonical.size}"
            )
        }
        return actual.zip(canonical).mapIndexedNotNull { index, (drawn, expected) ->
            if (drawn == expected) null else {
                "${file.relative()}: glyph path ${index + 1} is\n    $drawn\nbut " +
                    "HearthIcon.Household in ${hearthIconFile.relative()} draws\n    $expected"
            }
        }
    }

    /**
     * The splash is drawn by the system from this file, not by Compose, so the glyph on it is a
     * hand-typed copy. A copy that drifts is a launch animation that morphs into a slightly
     * different house.
     */
    @Test
    fun the_android_splash_tile_draws_the_canonical_glyph() {
        val drifted = glyphDrift(
            androidSplashTile,
            androidStrokedPaths(androidSplashTile),
            canonicalGlyphPaths()
        )

        assertTrue(
            drifted.isEmpty(),
            "The splash tile drifted from HearthIcon.Household (${drifted.size}):\n" +
                drifted.joinToString("\n")
        )
    }

    /** The same drawing again, this time as the launcher's foreground and monochrome layer. */
    @Test
    fun the_launcher_foreground_draws_the_canonical_glyph() {
        val drifted = glyphDrift(
            androidLauncherGlyph,
            androidStrokedPaths(androidLauncherGlyph),
            canonicalGlyphPaths()
        )

        assertTrue(
            drifted.isEmpty(),
            "The launcher foreground drifted from HearthIcon.Household (${drifted.size}):\n" +
                drifted.joinToString("\n")
        )
    }

    // ---------------------------------------------------------------------------------------
    // The iOS launch tile
    // ---------------------------------------------------------------------------------------

    /** An SVG launch tile reduced to the three things this guard compares. */
    private data class LaunchTile(
        val glyphPaths: List<String>,
        val glyphStrokes: List<String>,
        val tileFills: List<String>
    )

    /**
     * Splits a launch-tile SVG into its glyph and its tile body by the same rule as the Android
     * drawables: a `stroke` is the glyph, a bare `fill` is the tile underneath it. The alternative
     * — assuming the glyph is whatever comes last — would silently start comparing the wrong
     * element as soon as the artwork gained a shape.
     */
    private fun parseLaunchTile(file: File): LaunchTile {
        val paths = elements(file.readText(), "path")
        val strokeOf = { element: String -> attributeOf(element, "stroke")?.takeIf { it != "none" } }

        val stroked = paths.filter { strokeOf(it) != null }
        val filled = paths.filter { strokeOf(it) == null && attributeOf(it, "fill") != null }

        return LaunchTile(
            glyphPaths = stroked.map { element ->
                normalisePath(
                    attributeOf(element, "d") ?: fail(
                        "${file.relative()} has a stroked <path> with no `d`, so there is nothing " +
                            "to compare with the canonical glyph."
                    )
                )
            },
            glyphStrokes = stroked.map { normaliseHex(strokeOf(it)!!) },
            tileFills = filled.map { normaliseHex(attributeOf(it, "fill")!!) }
        )
    }

    /**
     * Asserts the tile exists, and says what it is for if it does not. Both SVGs are checked in
     * one place so a report names every missing one rather than the first.
     */
    private fun missingTile(file: File, which: String): String? =
        if (file.exists()) null else {
            "${file.relative()} does not exist. It is the $which iOS launch tile — the artwork " +
                "UIKit draws before any Kotlin runs — and is expected to be an SVG holding a " +
                "filled rounded-square tile body plus the two stroked household glyph paths from " +
                "${hearthIconFile.relative()}."
        }

    /**
     * The iOS launch tile is the only copy of the glyph that no compiler, lint or simulator ever
     * looks at on CI: `jvm-tests` runs on Linux. If it drifts, the first thing that notices is the
     * App Store screenshot.
     */
    @Test
    fun the_ios_launch_tiles_draw_the_canonical_glyph() {
        val missing = listOfNotNull(
            missingTile(iosLaunchTileDay, "day"),
            missingTile(iosLaunchTileNight, "night")
        )
        assertTrue(
            missing.isEmpty(),
            "The iOS launch tile artwork is missing (${missing.size}):\n" + missing.joinToString("\n")
        )

        val canonical = canonicalGlyphPaths()
        val drifted = listOf(iosLaunchTileDay, iosLaunchTileNight).flatMap { file ->
            glyphDrift(file, parseLaunchTile(file).glyphPaths, canonical)
        }

        assertTrue(
            drifted.isEmpty(),
            "The iOS launch tiles drifted from HearthIcon.Household (${drifted.size}):\n" +
                drifted.joinToString("\n")
        )
    }

    /**
     * The launch tile is the one asset whose day and night versions differ in *both* colours, and
     * the night one is not the day one with a darker background: it is a dark glyph on a light
     * green tile, because `NightColors.onPrimary` is the near-black canvas. Writing white-on-green
     * for night is the obvious mistake, it looks fine in a light-mode simulator, and this is the
     * assertion that catches it.
     */
    @Test
    fun the_ios_launch_tiles_invert_between_the_day_and_night_palettes() {
        val missing = listOfNotNull(
            missingTile(iosLaunchTileDay, "day"),
            missingTile(iosLaunchTileNight, "night")
        )
        assertTrue(
            missing.isEmpty(),
            "The iOS launch tile artwork is missing (${missing.size}):\n" + missing.joinToString("\n")
        )

        val drifted = listOf(
            iosLaunchTileDay to "DayColors",
            iosLaunchTileNight to "NightColors"
        ).flatMap { (file, palette) ->
            val tile = parseLaunchTile(file)
            val expectedTile = canonicalHex(palette, "primary")
            val expectedGlyph = canonicalHex(palette, "onPrimary")

            buildList {
                if (tile.tileFills.size != 1) {
                    add(
                        "${file.relative()} should have exactly one filled, unstroked <path> — the " +
                            "rounded-square tile body — but it has ${tile.tileFills.size}"
                    )
                } else if (tile.tileFills.single() != expectedTile) {
                    add(
                        "${file.relative()}: the tile body fill is #${tile.tileFills.single()} but " +
                            "$palette.primary in ${hearthColorsFile.relative()} is #$expectedTile"
                    )
                }

                tile.glyphStrokes.distinct().filterNot { it == expectedGlyph }.forEach { stroke ->
                    add(
                        "${file.relative()}: the glyph stroke is #$stroke but $palette.onPrimary in " +
                            "${hearthColorsFile.relative()} is #$expectedGlyph"
                    )
                }
            }
        }

        assertTrue(
            drifted.isEmpty(),
            "The iOS launch tiles drifted from the Hearth palette (${drifted.size}):\n" +
                drifted.joinToString("\n") +
                "\n(The night tile inverts: NightColors.onPrimary is the near-black canvas, so it " +
                "is a dark glyph on a light green tile — not the day artwork with a darker background.)"
        )
    }

    /**
     * The manifest is the half of the imageset that no eye ever checks. The artwork is obvious
     * when it is wrong; a manifest that names a file that is not there, or forgets which entry is
     * the dark one, produces a launch screen that is simply blank or simply light — and it does
     * that only on a device, at a moment no test is watching.
     *
     * `jvm-tests` runs on Linux, so this guard is the only thing that reads it on CI at all: there
     * is no actool to object, and the XCTest that would notice runs only on a Mac.
     */
    @Test
    fun the_ios_launch_tile_manifest_names_both_svgs_and_keeps_them_vector() {
        assertTrue(
            iosLaunchTileManifest.exists(),
            "${iosLaunchTileManifest.relative()} does not exist. It is the imageset manifest that " +
                "tells Xcode which SVG is the day tile and which is the dark one; without it the " +
                "two files next to it are not an asset at all and nothing can reference them."
        )

        val json = iosLaunchTileManifest.readText()
        val entries = entryBodies(
            arrayBody(json, iosLaunchTileManifest, "images", "image set"),
            iosLaunchTileManifest,
            "an image entry"
        )

        val problems = buildList {
            if (entries.size != 2) {
                add(
                    "${iosLaunchTileManifest.relative()}: the \"images\" array has ${entries.size} " +
                        "entr${if (entries.size == 1) "y" else "ies"}; expected two — the universal " +
                        "one and the dark one"
                )
            }

            val light = entries.filterNot { hasDarkAppearance(it) }
            val dark = entries.filter { hasDarkAppearance(it) }

            // The expected names are taken from the same File objects the artwork assertions use,
            // so the manifest and the tiles cannot be checked against two different spellings.
            add(filenameProblem(light, "universal", iosLaunchTileDay.name))
            add(filenameProblem(dark, "dark", iosLaunchTileNight.name))

            val properties = Regex("""["]properties["]\s*:\s*\{""").find(json)?.let {
                sliceBalanced(json, it.range.last, iosLaunchTileManifest, "the \"properties\" object")
            }
            val preservesVector = properties != null &&
                Regex(""""preserves-vector-representation"\s*:\s*true""").containsMatchIn(properties)
            if (!preservesVector) {
                add(
                    "${iosLaunchTileManifest.relative()}: \"properties\" does not set " +
                        "\"preserves-vector-representation\": true. Without it Xcode rasterises the " +
                        "SVG at build time and the launch screen loses the vector scaling that is " +
                        "the whole reason the tile ships as an SVG."
                )
            }
        }.filterNotNull()

        assertTrue(
            problems.isEmpty(),
            "The iOS launch tile manifest is wrong (${problems.size}):\n" + problems.joinToString("\n")
        )
    }

    /**
     * Checks that exactly one entry carries the given appearance and that it names the expected
     * file. Returns null when it is right, so the caller can collect every problem at once rather
     * than reporting the first.
     */
    private fun filenameProblem(entries: List<String>, which: String, expected: String): String? {
        if (entries.size != 1) {
            return "${iosLaunchTileManifest.relative()}: expected exactly one $which entry in " +
                "\"images\" but found ${entries.size}" +
                if (which == "dark") {
                    " — the dark entry is the one tagged " +
                        "\"appearances\": [{\"appearance\": \"luminosity\", \"value\": \"dark\"}], and " +
                        "without it a night-mode launch shows the day tile"
                } else {
                    " — the universal entry is the one with no \"appearances\", and it is what iOS " +
                        "falls back to"
                }
        }
        val filename = Regex(""""filename"\s*:\s*"([^"]*)"""")
            .find(entries.single())?.groupValues?.get(1)
            ?: return "${iosLaunchTileManifest.relative()}: the $which entry has no \"filename\", " +
                "so it names no artwork at all"
        return if (filename == expected) null else {
            "${iosLaunchTileManifest.relative()}: the $which entry names \"$filename\" but the " +
                "$which tile this guard checks is \"$expected\""
        }
    }
}
