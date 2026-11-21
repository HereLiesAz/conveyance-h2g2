package com.hereliesaz.conveyance.h2g2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.hereliesaz.conveyance.h2g2.resources.Res
import com.hereliesaz.conveyance.h2g2.resources.azrienoch_black
import com.hereliesaz.conveyance.h2g2.resources.azrienoch_extrabold
import com.hereliesaz.conveyance.h2g2.resources.azrienoch_mono_bold
import com.hereliesaz.conveyance.h2g2.resources.azrienoch_mono_regular
import com.hereliesaz.conveyance.h2g2.resources.azrienoch_regular
import com.hereliesaz.conveyance.h2g2.resources.azrienoch_semibold
import org.jetbrains.compose.resources.Font

/**
 * Azrienoch (hereliesaz/azrienoch, SIL OFL 1.1; licence in `third_party/azrienoch/`), a variable
 * font derived from Jost and HG2Gui's typeface. The four weights are static instances of its
 * variable build at wght 400/600/800/900, wdth 100, SERF 0, GRAD 0, so they behave the same on
 * every Android version (variable-font axes need API 26). Glyphs Azrienoch lacks fall back to the
 * system font per character.
 */
@Composable
fun azrienochFontFamily(): FontFamily = FontFamily(
    Font(Res.font.azrienoch_regular, FontWeight.Normal),
    Font(Res.font.azrienoch_black, FontWeight.Black),
    Font(Res.font.azrienoch_semibold, FontWeight.SemiBold),
    Font(Res.font.azrienoch_extrabold, FontWeight.ExtraBold),
)

/**
 * Azrienoch at its Mono width stop (`wdth` 0): every ASCII glyph is the same width. HG2Gui sets
 * its full-screen terminal grid in it. Static instances at wght 400 and 800.
 */
@Composable
fun azrienochMonoFontFamily(): FontFamily = FontFamily(
    Font(Res.font.azrienoch_mono_regular, FontWeight.Normal),
    Font(Res.font.azrienoch_mono_bold, FontWeight.Bold),
)

/**
 * The h2g2 type scale. One family drives every step: weight, size, line-height and tracking tell
 * them apart, not the font. Everything is uppercase except [body], the only lowercase in the
 * system; the call site uppercases its own string, since a [TextStyle] cannot.
 *
 * [hero] .. [micro] run large to small. [heading], [pill], [pillCap] and [chip] are the working
 * sizes for screen titles, command-stack pills, pill badges and [H2g2Chip]. Nothing here is below
 * 11sp: that is the floor for text a person is expected to read.
 *
 * Read it with [currentH2g2Type] inside a composable rather than hard-coding `fontSize` literals.
 */
data class H2g2Type(
    val hero: TextStyle,
    val section: TextStyle,
    val heading: TextStyle,
    val lead: TextStyle,
    val body: TextStyle,
    val capsule: TextStyle,
    val pill: TextStyle,
    val pillCap: TextStyle,
    val chip: TextStyle,
    val eyebrow: TextStyle,
    val endCap: TextStyle,
    val micro: TextStyle,
)

/**
 * The scale built on [family]. Not composable, so it cannot load the bundled typeface itself: the
 * default is the platform font. [rememberH2g2Type] and [currentH2g2Type] are the Azrienoch forms.
 */
@Suppress("LongMethod")
fun h2g2Type(family: FontFamily = FontFamily.Default): H2g2Type = H2g2Type(
    hero = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Black,
        fontSize = 58.sp, lineHeight = 49.sp, letterSpacing = (-0.03).em,
    ),
    section = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Black,
        fontSize = 40.sp, lineHeight = 40.sp, letterSpacing = (-0.02).em,
    ),
    // A screen title. Large enough to read as the heading of the page (the 11sp [eyebrow] cannot),
    // small enough to share a row with a BACK chip on a 360dp phone. Pair with H2g2Heading.
    heading = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Black,
        fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.01).em,
    ),
    lead = TextStyle(
        fontFamily = family, fontWeight = FontWeight.SemiBold,
        fontSize = 25.sp, lineHeight = 32.sp,
    ),
    // The only lowercase step.
    body = TextStyle(
        fontFamily = family, fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp, lineHeight = 25.5.sp,
    ),
    capsule = TextStyle(
        fontFamily = family, fontWeight = FontWeight.ExtraBold,
        fontSize = 15.sp, lineHeight = 18.sp, letterSpacing = 0.09.em,
    ),
    // The label of a pill in the command stack. 13sp keeps most labels whole in the stack's
    // right-anchored geometry while clearing Android's 12sp readability guidance.
    pill = TextStyle(
        fontFamily = family, fontWeight = FontWeight.ExtraBold,
        fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.09.em,
    ),
    // The little badge at a pill's right end (a count, a type tag).
    pillCap = TextStyle(
        fontFamily = family, fontWeight = FontWeight.ExtraBold,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.06.em,
    ),
    // Label of H2g2Chip, the one shared small button. Every chip takes this size.
    chip = TextStyle(
        fontFamily = family, fontWeight = FontWeight.ExtraBold,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.09.em,
    ),
    eyebrow = TextStyle(
        fontFamily = family, fontWeight = FontWeight.ExtraBold,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.28.em,
    ),
    endCap = TextStyle(
        fontFamily = family, fontWeight = FontWeight.ExtraBold,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.10.em,
    ),
    // Was 9sp; nothing below 11sp is legible enough to ship, so it is now [endCap]'s size with
    // wider tracking.
    micro = TextStyle(
        fontFamily = family, fontWeight = FontWeight.ExtraBold,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.18.em,
    ),
)

/** The scale set in the bundled Azrienoch. */
@Composable
fun rememberH2g2Type(): H2g2Type {
    val family = azrienochFontFamily()
    return remember(family) { h2g2Type(family) }
}

/**
 * A host's own [H2g2Type], when it wants the scale in a different family. Null (the default) means
 * "Azrienoch": [currentH2g2Type] resolves that. [H2g2Theme] provides its `type` argument here.
 */
val LocalH2g2Type = staticCompositionLocalOf<H2g2Type?> { null }

/**
 * The active type scale: whatever [LocalH2g2Type] holds, otherwise the bundled Azrienoch. This is
 * what every composable in the library reads, so text is Azrienoch with or without an [H2g2Theme].
 */
@Composable
fun currentH2g2Type(): H2g2Type = LocalH2g2Type.current ?: rememberH2g2Type()

/** Looks up a step by the composable manifest's `scale` string (azphalt `spec/composable.md`). */
fun H2g2Type.step(name: String): TextStyle = when (name) {
    "hero" -> hero
    "section" -> section
    "heading" -> heading
    "lead" -> lead
    "body" -> body
    "capsule" -> capsule
    "pill" -> pill
    "pillCap" -> pillCap
    "chip" -> chip
    "eyebrow" -> eyebrow
    "endCap" -> endCap
    "micro" -> micro
    else -> body
}
