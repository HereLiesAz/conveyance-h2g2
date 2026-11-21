package com.hereliesaz.conveyance.h2g2

import androidx.compose.ui.graphics.Color

/**
 * The h2g2 hue system: a fixed, ordered palette plus a deterministic hash from any identifier to
 * one of its fourteen entries.
 *
 * This exists for [com.hereliesaz.conveyance.Job.Identify], not [com.hereliesaz.conveyance.Channel.Hue]
 * -- it tells two elements apart by giving each a consistent, non-random color, the same way
 * Android colors a contact's initial by hashing their name. It carries no rank, no state, no
 * urgency; a template built from it should not also claim [com.hereliesaz.conveyance.Meaning.SemanticRank]
 * for the same variation. Ported from HG2Gui's own `Azphalt.hues`/`hueOf` (h2g2's origin), values
 * unchanged.
 */
object H2g2 {
    val hues: List<Color> = listOf(
        Color(0xFF6B4FBB), Color(0xFF2FA9C4), Color(0xFF1F9E86), Color(0xFF5AAE34),
        Color(0xFFD9A21C), Color(0xFFD9762A), Color(0xFFC6392F), Color(0xFFB03A6E),
        Color(0xFF8E4FA8), Color(0xFF2E6FB7),
        Color(0xFF8A9296), Color(0xFF4E7D6E), Color(0xFFC9A45C), Color(0xFF8C6E4E),
    )

    /** A darker end-cap/accent shade of each [hues] entry, index-aligned. */
    val caps: List<Color> = listOf(
        Color(0xFF4B3489), Color(0xFF1F7B90), Color(0xFF137060), Color(0xFF3E7D22),
        Color(0xFFA2760F), Color(0xFFA6551A), Color(0xFF93251D), Color(0xFF7F2A4D),
        Color(0xFF653578), Color(0xFF1E4E85),
        Color(0xFF616A6E), Color(0xFF365A4E), Color(0xFF96762F), Color(0xFF634B31),
    )

    val hueNames: List<String> = listOf(
        "violet", "cyan", "teal", "green", "amber", "orange", "red", "magenta", "purple", "blue",
        "gray", "sage", "tan", "brown",
    )

    val ink: Color = Color(0xFF1E1A17)
    val white: Color = Color(0xFFFFFFFF)

    /**
     * The design system's `--yellow-bright`: the text/accent colour on [ink] (a selected pill, a
     * trail crumb, a badge). Distinct from Mustard's page colour (#E8C81E), which is a ground.
     */
    val yellow: Color = Color(0xFFF0D42A)

    /**
     * A coordinated multi-hue combination pulled from a single film scene, for a screen that needs
     * more than one hue to cohere (a header plus its pills) -- the style guide's "02b - Reference
     * groupings". Picking freehand is the thing this replaces.
     */
    data class Grouping(val headerBg: Color, val hues: List<Color>)

    val groupings: Map<String, Grouping> = mapOf(
        "Vogon stage" to Grouping(Color(0xFF8B6420), listOf(Color(0xFFC6392F), Color(0xFF2E6FB7), Color(0xFF0F2447))),
        "Oolon Colluphid" to Grouping(Color(0xFF8B3350), listOf(Color(0xFF2E6FB7), Color(0xFFD9A21C), Color(0xFFC9A45C))),
        "Babel fish" to Grouping(Color(0xFF8B0021), listOf(Color(0xFFB03A6E), Color(0xFFD9762A), Color(0xFF8C6E4E))),
        "Improbability party" to Grouping(Color(0xFF123A52), listOf(Color(0xFF5AAE34), Color(0xFFC6392F), Color(0xFF8E4FA8))),
        "Improbability den" to Grouping(Color(0xFF4E7D6E), listOf(Color(0xFF8C6E4E), Color(0xFF8A9296), Color(0xFF365A4E))),
        "Hyperspace" to Grouping(Color(0xFF163A63), listOf(Color(0xFF8A9296), Color(0xFFC9A45C), Color(0xFFC6392F))),
        "Point-of-View Gun" to Grouping(Color(0xFFB03A6E), listOf(Color(0xFFC6392F), Color(0xFF5AAE34), Color(0xFF8B6420))),
    )

    /** Deterministically maps any identifier to a [hues] index -- same id, same hue, every time. */
    fun hueOf(id: String): Int {
        // `mod`, not `%`: Int.MIN_VALUE has no positive two's-complement negation
        // (-Int.MIN_VALUE overflows back to itself), so a naive "negate if negative" can still
        // hand `%` a negative dividend and throw IndexOutOfBoundsException. `mod` always returns
        // a non-negative result for a positive divisor, sidestepping that entirely -- the same
        // fix already applied to LiquidHue.of and BacteriumHue.of this session.
        return id.hashCode().mod(hues.size)
    }

    /**
     * Resolves a manifest `hue` value to a [hues] index -- an exact [hueNames] match first (so
     * `"violet"` really renders violet, matching azphalt `spec/composable.md`'s own example of a
     * `hue` value being "one of a host's fourteen named hues"), falling back to [hueOf]'s hash
     * for anything else (an element id, a subject key, or any string that isn't one of the 14
     * names). Templates in this library resolve `hueSeed` through this, not [hueOf] directly, so
     * an author who happens to know and name a real hue gets it, while an author who just wants
     * "some consistent color for this id" still gets that.
     */
    fun indexOf(seed: String): Int {
        val named = hueNames.indexOf(seed)
        return if (named >= 0) named else hueOf(seed)
    }

    /**
     * [ink] or [white], whichever has the higher WCAG contrast ratio against this colour. Chosen
     * by measurement, as HG2Gui does: a fixed luminance cutoff (this used to be 0.35) sits well
     * above the true break-even of about 0.20, so cyan, green, gray, orange and teal got white at
     * roughly 3:1 where ink gives better than 5:1. Not for a pill's selected state (ink + [yellow]),
     * which is a fixed signal rather than text on an arbitrary background.
     */
    fun Color.contrastingText(): Color =
        if (contrastRatio(this, ink) >= contrastRatio(this, white)) ink else white
}
