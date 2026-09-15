package com.homelab.household.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.io.File
import javax.imageio.ImageIO
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
    /**
     * The catalog every iOS brand asset lives in. Both sets below hang off it, and so does the
     * cross-check that the launch-screen declaration names sets that are really there.
     */
    private val iosAssetCatalog = File(clientRootDir, "iosApp/HouseholdHub/Assets.xcassets")
    private val iosCanvasColorSet = File(iosAssetCatalog, "HearthCanvas.colorset/Contents.json")

    /** The other half of the brand: the one drawing of the household glyph. */
    private val hearthIconFile = File(
        clientRootDir,
        "composeApp/src/commonMain/kotlin/com/homelab/household/app/icons/HearthIcon.kt"
    )

    private val androidSplashTile = File(clientRootDir, "androidApp/src/main/res/drawable/splash_tile.xml")
    private val androidLauncherGlyph =
        File(clientRootDir, "androidApp/src/main/res/drawable/ic_launcher_foreground.xml")

    private val iosLaunchTileDir = File(iosAssetCatalog, "HearthLaunchTile.imageset")
    private val iosLaunchTileDay = File(iosLaunchTileDir, "hearth_launch_tile.svg")
    private val iosLaunchTileNight = File(iosLaunchTileDir, "hearth_launch_tile_dark.svg")
    private val iosLaunchTileManifest = File(iosLaunchTileDir, "Contents.json")

    private val iosAppIconSet = File(iosAssetCatalog, "AppIcon.appiconset")
    private val iosAppIconManifest = File(iosAppIconSet, "Contents.json")

    /**
     * The icon's vector source. The catalog can only hold the raster, so this is the only copy of
     * the icon artwork that states the glyph as text — and therefore the only one this guard can
     * compare with the others.
     */
    private val iosAppIconSource = File(clientRootDir, "tools/hearth_app_icon.svg")

    /** The XcodeGen spec. `HouseholdHub/Info.plist` is generated from it, and git-ignored. */
    private val iosProjectSpec = File(clientRootDir, "iosApp/project.yml")

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

    // ---------------------------------------------------------------------------------------
    // The iOS launch screen declaration
    // ---------------------------------------------------------------------------------------

    /**
     * Every failure below repeats this, because the obvious place to go and fix a wrong
     * `Info.plist` key is `Info.plist` — and that file is generated by XcodeGen from `project.yml`
     * and git-ignored, so an edit there survives exactly until the next `xcodegen generate`.
     */
    private val fixInTheSpec =
        "Fix it in ${iosProjectSpec.relative()}: HouseholdHub/Info.plist is generated from that " +
            "spec and is git-ignored, so editing the plist is not the fix."

    /** What an empty `UILaunchScreen` costs, said the same way wherever it is found. */
    private val launchScreenIsEmpty =
        "${iosProjectSpec.relative()} declares UILaunchScreen but gives it no keys, so the launch " +
            "screen carries no artwork at all: iOS falls back to a letterboxed compatibility " +
            "window and Compose draws into a small box in the middle of the screen. It needs " +
            "UIColorName, UIImageName and UIImageRespectsSafeAreaInsets. $fixInTheSpec"

    /**
     * The lines of the `UILaunchScreen:` block, found by indentation.
     *
     * This is a deliberately shallow reader and not a YAML parser. There is no YAML library on the
     * `:shared` jvmTest classpath and adding one to read three keys would cost more than it
     * guards — the same trade as the JSON scanner above. It assumes what this file actually is:
     * space-indented block mappings, one `key: value` per line, no tabs, no anchors, no flow
     * mappings and no multi-line scalars, with `UILaunchScreen` appearing once. Every assumption
     * it makes is one it checks, so a project.yml that stops being that shape fails loudly here
     * rather than quietly reading nothing.
     */
    private fun launchScreenLines(): List<String> {
        if (!iosProjectSpec.exists()) {
            fail<Nothing>(
                "The XcodeGen spec is expected at ${iosProjectSpec.relative()}; it does not exist. " +
                    "It is where the iOS launch screen is declared, and HouseholdHub/Info.plist is " +
                    "generated from it."
            )
        }

        val lines = iosProjectSpec.readLines()
        val header = lines.indexOfFirst { Regex("""^\s*UILaunchScreen\s*:""").containsMatchIn(it) }
        if (header < 0) {
            fail<Nothing>(
                "${iosProjectSpec.relative()} declares no UILaunchScreen. Without it iOS runs the " +
                    "app letterboxed and Compose draws into a small box in the middle of the " +
                    "screen. $fixInTheSpec"
            )
        }

        val indent = lines[header].takeWhile { it == ' ' }.length

        // `UILaunchScreen: {}` is the shape this guard exists for. It is legal YAML, it is what a
        // revert of the launch-screen work produces, and to a reader that only looks at
        // indentation it is indistinguishable from a block with no children — so it is named here
        // explicitly rather than left to fall through as "found nothing".
        val inline = lines[header].substringAfter(':').substringBefore('#').trim()
        if (inline.isNotEmpty()) {
            fail<Nothing>(
                if (inline.filterNot { it == ' ' } == "{}") {
                    "$launchScreenIsEmpty (It is written as the empty inline dict " +
                        "`UILaunchScreen: {}`.)"
                } else {
                    "${iosProjectSpec.relative()} writes UILaunchScreen as the inline value " +
                        "`$inline`; this guard reads it as an indented block of keys and cannot " +
                        "see inside a flow mapping. $fixInTheSpec"
                }
            )
        }

        val children = lines.drop(header + 1)
            .takeWhile { line -> line.isBlank() || line.takeWhile { it == ' ' }.length > indent }
            .filter { it.isNotBlank() && !it.trim().startsWith("#") }

        if (children.isEmpty()) fail<Nothing>(launchScreenIsEmpty)
        return children
    }

    /** The block's `key: value` pairs, with any trailing ` # comment` dropped. */
    private fun launchScreenValues(): Map<String, String> =
        launchScreenLines().mapNotNull { line ->
            Regex("""^\s*([A-Za-z0-9_]+)\s*:\s*(.*)$""").find(line)?.let {
                it.groupValues[1] to it.groupValues[2].substringBefore(" #").trim()
            }
        }.toMap()

    private fun declaredNameProblem(
        declared: Map<String, String>,
        key: String,
        expected: String
    ): String? {
        val actual = declared[key]
            ?: return "${iosProjectSpec.relative()}: UILaunchScreen declares no $key; expected " +
                "`$key: $expected`"
        return if (actual == expected) null else {
            "${iosProjectSpec.relative()}: UILaunchScreen declares `$key: $actual` but the asset " +
                "this guard checks is named `$expected`"
        }
    }

    /**
     * The launch screen is declared in `project.yml`, baked into a generated, git-ignored
     * `Info.plist`, and read by iOS from the built bundle — so on a Linux PR nothing looks at it
     * at all. The XCTest that would catch a missing key runs only on a Mac, and the symptom on a
     * device is a blank launch screen, which no assertion anywhere else would notice.
     *
     * The expected asset names are taken from the very directories the colour-set and image-set
     * assertions above open, not written out again here. That is the drift this is really for: a
     * rename that updates the catalog but not the spec (or the reverse) leaves two spellings that
     * each look right on their own, and only a check that reads both can tell.
     */
    @Test
    fun the_ios_launch_screen_declaration_points_at_the_hearth_assets() {
        val declared = launchScreenValues()

        val expectedColorName = iosCanvasColorSet.parentFile.name.removeSuffix(".colorset")
        val expectedImageName = iosLaunchTileDir.name.removeSuffix(".imageset")

        val problems = buildList {
            add(declaredNameProblem(declared, "UIColorName", expectedColorName))
            add(declaredNameProblem(declared, "UIImageName", expectedImageName))

            // Boolean only: YAML spells true several ways and which one the file uses is not a
            // difference worth failing over.
            val respectsSafeArea = declared["UIImageRespectsSafeAreaInsets"]
            if (respectsSafeArea == null) {
                add(
                    "${iosProjectSpec.relative()}: UILaunchScreen declares no " +
                        "UIImageRespectsSafeAreaInsets; expected " +
                        "`UIImageRespectsSafeAreaInsets: true`"
                )
            } else if (respectsSafeArea.lowercase() !in setOf("true", "yes")) {
                add(
                    "${iosProjectSpec.relative()}: UILaunchScreen declares " +
                        "`UIImageRespectsSafeAreaInsets: $respectsSafeArea`, so the launch tile is " +
                        "laid out ignoring the safe area and sits under the notch and the home " +
                        "indicator. Expected true."
                )
            }

            // A name that points at nothing is the other half of a half-finished rename: the spec
            // reads fine on its own, and the asset it names simply is not there.
            declared["UIColorName"]?.let { name ->
                val colorSet = File(iosAssetCatalog, "$name.colorset")
                if (!colorSet.isDirectory) {
                    add(
                        "${iosProjectSpec.relative()}: UIColorName names `$name`, but " +
                            "${colorSet.relative()} does not exist — the launch screen would have " +
                            "no background colour"
                    )
                }
            }
            declared["UIImageName"]?.let { name ->
                val imageSet = File(iosAssetCatalog, "$name.imageset")
                if (!imageSet.isDirectory) {
                    add(
                        "${iosProjectSpec.relative()}: UIImageName names `$name`, but " +
                            "${imageSet.relative()} does not exist — the launch screen would have " +
                            "no artwork"
                    )
                }
            }
        }.filterNotNull()

        assertTrue(
            problems.isEmpty(),
            "The iOS launch screen declaration is wrong (${problems.size}):\n" +
                problems.joinToString("\n") + "\n" + fixInTheSpec
        )
    }

    // ---------------------------------------------------------------------------------------
    // The iOS app icon
    // ---------------------------------------------------------------------------------------

    /** One JSON string value off an entry body, without caring about key order or formatting. */
    private fun jsonString(body: String, key: String): String? =
        Regex("""["]$key["]\s*:\s*"([^"]*)"""").find(body)?.groupValues?.get(1)

    /**
     * The single entry of the app icon set.
     *
     * The set itself is never absent, and that is not a sign of health: actool hard-fails any build
     * where the catalog has no set matching ASSETCATALOG_COMPILER_APPICON_NAME, so an empty stub is
     * the shape that keeps the build alive while the app ships the blank default icon. Everything
     * below therefore checks what the set *says*, not merely that it is there.
     */
    private fun appIconEntry(): String {
        if (!iosAppIconManifest.exists()) {
            fail<Nothing>(
                "${iosAppIconManifest.relative()} does not exist. It is the app icon set manifest; " +
                    "without it actool fails the whole build, because XcodeGen's iOS application " +
                    "preset always sets ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon."
            )
        }

        val entries = entryBodies(
            arrayBody(iosAppIconManifest.readText(), iosAppIconManifest, "images", "app icon set"),
            iosAppIconManifest,
            "an app icon entry"
        )

        return entries.singleOrNull() ?: fail(
            "${iosAppIconManifest.relative()}: the \"images\" array has ${entries.size} entries; a " +
                "modern iOS app icon is one 1024x1024 universal slot that the system downscales " +
                "for every other size."
        )
    }

    /**
     * The PNG the manifest names, resolved from the declared filename rather than from a name
     * written out here — so a manifest pointing at artwork that is not on disk is caught as the
     * half-finished change it is.
     */
    private fun appIconPng(): File {
        val filename = jsonString(appIconEntry(), "filename") ?: fail(
            "${iosAppIconManifest.relative()} declares no \"filename\", so the app icon slot names " +
                "no artwork and the app ships the blank default iOS icon. The set exists only " +
                "because actool refuses to build a catalog without it."
        )

        val png = File(iosAppIconSet, filename)
        if (!png.isFile) {
            fail<Nothing>(
                "${iosAppIconManifest.relative()} names `$filename`, but ${png.relative()} does not " +
                    "exist — the app icon slot points at artwork that is not there, and the app " +
                    "ships the blank default iOS icon."
            )
        }
        return png
    }

    /**
     * The app icon set has to declare a universal iOS slot, and the reason this is a test rather
     * than a glance is the `platform` key.
     *
     * An entry written the old way — `idiom: ios-marketing`, no `platform` — is accepted by Xcode,
     * builds clean, and uploads to App Store Connect without a word of complaint. What it produces
     * is a home screen with no icon at all. Nothing on a Linux PR would otherwise read this file,
     * and the symptom appears only once the app is installed on a device.
     */
    @Test
    fun the_ios_app_icon_declares_one_universal_ios_slot() {
        val entry = appIconEntry()

        val problems = buildList {
            val idiom = jsonString(entry, "idiom")
            val platform = jsonString(entry, "platform")
            if (idiom != "universal" || platform != "ios") {
                add(
                    "${iosAppIconManifest.relative()}: the slot is declared as " +
                        "`idiom: ${idiom ?: "(absent)"}`" +
                        (platform?.let { ", `platform: $it`" } ?: ", with no `platform` key") +
                        "; it must be `idiom: universal` with `platform: ios`. This is the mistake " +
                        "worth catching from Linux: the legacy `ios-marketing` idiom with no " +
                        "`platform` is accepted by Xcode and ships to App Store Connect without " +
                        "complaint, and leaves the home screen with no icon at all."
                )
            }

            val size = jsonString(entry, "size")
            if (size != "1024x1024") {
                add(
                    "${iosAppIconManifest.relative()}: the slot declares " +
                        "`size: ${size ?: "(absent)"}`; a modern iOS app icon is a single " +
                        "1024x1024 image that the system downscales for every other size."
                )
            }

            if (jsonString(entry, "filename") == null) {
                add(
                    "${iosAppIconManifest.relative()}: the slot declares no \"filename\", so it " +
                        "names no artwork and the app ships the blank default iOS icon. The set " +
                        "exists only because actool refuses to build a catalog without it."
                )
            }
        }

        assertTrue(
            problems.isEmpty(),
            "The iOS app icon set is wrong (${problems.size}):\n" + problems.joinToString("\n")
        )
    }

    /** The PNG colour types, by the numbers the IHDR chunk actually stores. */
    private fun colourTypeName(type: Int): String = when (type) {
        0 -> "greyscale"
        2 -> "truecolour, no alpha"
        3 -> "indexed colour"
        4 -> "greyscale with alpha"
        6 -> "truecolour with alpha"
        else -> "unrecognised"
    }

    /**
     * Reads the icon's IHDR chunk straight out of the file.
     *
     * A PNG opens with an 8-byte signature, then the IHDR chunk: 4 bytes of length, the literal
     * `IHDR`, then width and height as big-endian uint32s at offsets 16 and 20, the bit depth at
     * 24 and the colour type at 25. The signature and the chunk name are verified before any of
     * those offsets are trusted, so a file that is not a PNG says so instead of reporting a
     * nonsense size.
     */
    @Test
    fun the_ios_app_icon_png_is_a_1024_square_with_no_alpha_channel() {
        val png = appIconPng()
        val bytes = png.readBytes()

        val signature = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
        if (bytes.size < 26 || !bytes.copyOfRange(0, 8).contentEquals(signature)) {
            fail<Nothing>(
                "${png.relative()} is not a PNG: it does not start with the PNG signature. The app " +
                    "icon slot names it, so whatever it is, it is what would be shipped."
            )
        }
        if (String(bytes, 12, 4, Charsets.US_ASCII) != "IHDR") {
            fail<Nothing>(
                "${png.relative()} is a PNG whose first chunk is not IHDR, so its header cannot be " +
                    "read. Every PNG must open with IHDR."
            )
        }

        fun uint32At(offset: Int): Int = (0 until 4).fold(0) { value, index ->
            (value shl 8) or (bytes[offset + index].toInt() and 0xFF)
        }

        val width = uint32At(16)
        val height = uint32At(20)
        val colourType = bytes[25].toInt() and 0xFF

        val problems = buildList {
            if (width != 1024 || height != 1024) {
                add(
                    "${png.relative()} is ${width}x${height}; the single universal slot must be " +
                        "1024x1024, because every smaller icon iOS shows is downscaled from it."
                )
            }
            if (colourType != 2) {
                add(
                    "${png.relative()} has PNG colour type $colourType (${colourTypeName(colourType)}); " +
                        "it must be 2 (truecolour, no alpha). Apple rejects an app icon carrying an " +
                        "alpha channel outright, and a fully opaque alpha channel is not good " +
                        "enough — the channel's presence is what the check looks at. Caught here it " +
                        "is a re-export; caught at submission it is months later."
                )
            }
        }

        assertTrue(
            problems.isEmpty(),
            "The iOS app icon artwork is wrong (${problems.size}):\n" + problems.joinToString("\n")
        )
    }

    /**
     * The icon is a full-bleed Hearth green square: iOS applies its own mask, so the artwork must
     * carry colour all the way into the corners or the rounded edge shows through as white.
     *
     * The day green in every appearance, on purpose — an app icon is the app's mark, not part of
     * its UI, the same rationale already written into `launcher_colors.xml`. There is no night
     * variant to look for and none is missing.
     *
     * Corners, not the centre: the centre holds the glyph. They are sampled a few pixels in, far
     * outside the glyph but off the outermost row, so an encoder's edge artefact cannot red this.
     */
    @Test
    fun the_ios_app_icon_is_full_bleed_hearth_green() {
        val png = appIconPng()
        val image = ImageIO.read(png) ?: fail(
            "${png.relative()} could not be decoded as an image, so its colours cannot be checked."
        )

        val inset = 8
        val corners = listOf(
            "top-left" to (inset to inset),
            "top-right" to (image.width - 1 - inset to inset),
            "bottom-left" to (inset to image.height - 1 - inset),
            "bottom-right" to (image.width - 1 - inset to image.height - 1 - inset)
        )

        val expected = canonicalHex("DayColors", "primary")
        val drifted = corners.mapNotNull { (name, point) ->
            val (x, y) = point
            val pixel = image.getRGB(x, y)
            val hex = "%02X%02X%02X".format(
                (pixel shr 16) and 0xFF,
                (pixel shr 8) and 0xFF,
                pixel and 0xFF
            )
            if (hex == expected) null else {
                "${png.relative()}: the $name corner is #$hex but DayColors.primary in " +
                    "${hearthColorsFile.relative()} is #$expected"
            }
        }

        assertTrue(
            drifted.isEmpty(),
            "The iOS app icon is not full-bleed Hearth green (${drifted.size}):\n" +
                drifted.joinToString("\n") +
                "\n(By design the app icon carries the day green in every appearance — it is the " +
                "app's mark, not part of its UI — so there is no night variant to add.)"
        )
    }

    /**
     * The house on the app icon is the same house as everywhere else.
     *
     * This is the copy that nearly escaped the net. The catalog can only hold a raster, and the
     * assertions above it check that raster thoroughly — 1024 square, no alpha channel, Hearth
     * green into all four corners — but a PNG cannot be compared with a path string, so none of
     * them can tell whether the glyph on it is the household glyph or some other drawing
     * altogether. Rasterising from a checked-in SVG is what makes the icon answerable to
     * `HearthIcon.Household` like the other four copies, and this is the assertion that collects
     * on it.
     *
     * The background is a `<rect>` here rather than the launch tile's rounded `<path>`, because
     * iOS masks the corners itself and the artwork must run edge to edge underneath that mask.
     */
    @Test
    fun the_ios_app_icon_source_draws_the_canonical_glyph_on_hearth_green() {
        assertTrue(
            iosAppIconSource.exists(),
            "${iosAppIconSource.relative()} does not exist. It is the vector source the committed " +
                "app icon PNG is rasterised from (tools/make_app_icon.sh), and the only copy of " +
                "the icon artwork that spells the glyph out as text — without it the icon is a " +
                "raster nothing can check against ${hearthIconFile.relative()}."
        )

        val icon = parseLaunchTile(iosAppIconSource)
        val problems = buildList {
            addAll(glyphDrift(iosAppIconSource, icon.glyphPaths, canonicalGlyphPaths()))

            val expectedGlyph = canonicalHex("DayColors", "onPrimary")
            icon.glyphStrokes.distinct().filterNot { it == expectedGlyph }.forEach { stroke ->
                add(
                    "${iosAppIconSource.relative()}: the glyph stroke is #$stroke but " +
                        "DayColors.onPrimary in ${hearthColorsFile.relative()} is #$expectedGlyph"
                )
            }

            val expectedGreen = canonicalHex("DayColors", "primary")
            val fills = elements(iosAppIconSource.readText(), "rect")
                .mapNotNull { attributeOf(it, "fill") }
                .map { normaliseHex(it) }
            when {
                fills.isEmpty() -> add(
                    "${iosAppIconSource.relative()} has no filled <rect>. The icon must be " +
                        "full-bleed: iOS applies its own rounded mask, so artwork that stops short " +
                        "of the edge shows white through the corners."
                )
                fills.any { it != expectedGreen } -> add(
                    "${iosAppIconSource.relative()}: the background fill is " +
                        "#${fills.first { it != expectedGreen }} but DayColors.primary in " +
                        "${hearthColorsFile.relative()} is #$expectedGreen"
                )
            }
        }

        assertTrue(
            problems.isEmpty(),
            "The iOS app icon source drifted (${problems.size}):\n" + problems.joinToString("\n") +
                "\n(Regenerate the PNG with tools/make_app_icon.sh after fixing the SVG — the " +
                "raster is committed and does not rebuild itself.)"
        )
    }
}
