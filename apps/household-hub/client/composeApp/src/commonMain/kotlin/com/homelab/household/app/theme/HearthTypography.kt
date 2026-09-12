package com.homelab.household.app.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The type scale (design notes §3), taken from the proof — artboard `0 · The type scale` on the
 * design canvas. Fifteen roles, seven glyph steps, three modifiers, and nothing else.
 *
 * A role carries everything: family, size, weight, line height and tracking. A screen picks one —
 * `HearthTheme.typography.body` — and writes no size and no weight of its own. Before this the
 * canvas drew 28 font sizes in 124 combinations and the components invented their own on top;
 * `DesignSystemTokenTest` now fails the build on a raw `sp` or a hand-applied `FontWeight`
 * anywhere in `composeApp/commonMain` outside this package.
 *
 * [material] is built from the same roles, so Material's own components — a `Button`'s label, a
 * `Dialog`'s title — land on the scale without every call site restating it.
 */
@Immutable
data class HearthTypography(
    /** A full-screen title: onboarding, launch, a full-page empty state. */
    val hero: TextStyle,
    /** A screen or dialog title inside the app's chrome. */
    val title: TextStyle,
    /** App bar, card heading, section heading. */
    val heading: TextStyle,
    /** Lead prose: the briefing card, the paragraph a screen opens with. */
    val bodyLarge: TextStyle,
    /** The default. If you are unsure, it is this one. */
    val body: TextStyle,
    /** A list row's own name, and every button label. */
    val bodyStrong: TextStyle,
    /** The second line of a row, under its name. */
    val label: TextStyle,
    /** Chips, badges, field labels, a toggle's ON and OFF. */
    val labelStrong: TextStyle,
    /** Helper text under a field, and any supporting line. */
    val caption: TextStyle,
    /** The eyebrow over a card's contents. The string itself carries the capitals. */
    val overline: TextStyle,
    /** Bottom-nav labels and counts. Selection reads through colour, not weight. */
    val micro: TextStyle,
    /** Dense metadata: a status line, a latency, a timestamp in a corner. */
    val monoSm: TextStyle,
    /** Times down a schedule, agent handles, tool records. */
    val mono: TextStyle,
    /** A credential you read back: an app password, a masked PIN. */
    val monoLg: TextStyle,
    /** The one code a screen exists to show: an invite, a digit box. */
    val codeHero: TextStyle,
    /** An emoji or an avatar's initial, sized by the circle it sits in — 20dp. */
    val glyphXs: TextStyle,
    /** 32dp. */
    val glyphSm: TextStyle,
    /** 40dp. */
    val glyphMd: TextStyle,
    /** 48dp — [HearthSizes.touchTarget]. */
    val glyphLg: TextStyle,
    /** 56dp — [HearthSizes.control]. */
    val glyphXl: TextStyle,
    /** 64dp — [HearthSizes.tile]. */
    val glyphXxl: TextStyle,
    /** 80dp and up — [HearthSizes.tileHero]. */
    val glyphHero: TextStyle,
    /** The same roles, wired into Material's slots. */
    val material: Typography
)

internal fun hearthTypography(fonts: HearthFonts): HearthTypography {
    fun outfit(size: Int, lineHeight: Int, tracking: Double, weight: FontWeight = FontWeight.SemiBold) =
        TextStyle(
            fontFamily = fonts.outfit,
            fontWeight = weight,
            fontSize = size.sp,
            lineHeight = lineHeight.sp,
            letterSpacing = tracking.em
        )

    fun inter(size: Int, lineHeight: Int, weight: FontWeight, tracking: Double? = null) = TextStyle(
        fontFamily = fonts.inter,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = tracking?.em ?: TextStyle.Default.letterSpacing
    )

    fun mono(size: Int, lineHeight: Int, tracking: Double? = null) = TextStyle(
        fontFamily = fonts.jetBrainsMono,
        fontWeight = FontWeight.Medium,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = tracking?.em ?: TextStyle.Default.letterSpacing
    )

    // A glyph sits on its own cap height so the circle around it does the centring.
    fun glyph(size: Int) = TextStyle(
        fontFamily = fonts.outfit,
        fontWeight = FontWeight.SemiBold,
        fontSize = size.sp,
        lineHeight = 1f.em
    )

    val hero = outfit(size = 28, lineHeight = 32, tracking = -0.015)
    val title = outfit(size = 21, lineHeight = 25, tracking = -0.012)
    val heading = outfit(size = 17, lineHeight = 22, tracking = -0.01)

    val bodyLarge = inter(size = 15, lineHeight = 23, weight = FontWeight.Normal)
    val body = inter(size = 14, lineHeight = 21, weight = FontWeight.Normal)
    val bodyStrong = inter(size = 14, lineHeight = 19, weight = FontWeight.SemiBold)
    val label = inter(size = 13, lineHeight = 18, weight = FontWeight.Medium)
    val labelStrong = inter(size = 12, lineHeight = 16, weight = FontWeight.SemiBold)
    val caption = inter(size = 12, lineHeight = 17, weight = FontWeight.Normal)
    val overline = inter(size = 11, lineHeight = 13, weight = FontWeight.SemiBold, tracking = 0.11)
    val micro = inter(size = 10, lineHeight = 12, weight = FontWeight.SemiBold)

    val monoSm = mono(size = 10, lineHeight = 14)
    val monoRegular = mono(size = 12, lineHeight = 17)
    val monoLg = mono(size = 15, lineHeight = 21, tracking = 0.06)
    val codeHero = mono(size = 24, lineHeight = 26, tracking = 0.1)

    return HearthTypography(
        hero = hero,
        title = title,
        heading = heading,
        bodyLarge = bodyLarge,
        body = body,
        bodyStrong = bodyStrong,
        label = label,
        labelStrong = labelStrong,
        caption = caption,
        overline = overline,
        micro = micro,
        monoSm = monoSm,
        mono = monoRegular,
        monoLg = monoLg,
        codeHero = codeHero,
        glyphXs = glyph(10),
        glyphSm = glyph(14),
        glyphMd = glyph(18),
        glyphLg = glyph(22),
        glyphXl = glyph(26),
        glyphXxl = glyph(30),
        glyphHero = glyph(36),
        material = Typography(
            // Three display slots, one hero: the app has one size of full-screen title.
            displayLarge = hero,
            displayMedium = hero,
            displaySmall = hero,
            headlineLarge = title,
            headlineMedium = title,
            headlineSmall = heading,
            titleLarge = heading,
            titleMedium = bodyStrong,
            titleSmall = label,
            bodyLarge = bodyLarge,
            bodyMedium = body,
            bodySmall = caption,
            // Material puts a button's label in labelLarge.
            labelLarge = bodyStrong,
            labelMedium = labelStrong,
            labelSmall = micro
        )
    )
}
