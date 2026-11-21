package com.hereliesaz.conveyance.h2g2

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.random.Random

/*
 * HG2Gui's motion vocabulary (shared/.../ui/menu/StackEntrance.kt, PillWobble.kt and
 * AzphaltPrimitives.kt's AzphaltMotion), lifted into h2g2 so every app speaking HG2Gui moves the
 * same way instead of each keeping its own copy. Values are HG2Gui's own, unchanged.
 */

object H2g2Motion {
    /** The "page" settle every HG2Gui morph uses: fast out, long gentle landing. */
    val Ease: Easing = CubicBezierEasing(0f, .9f, .1f, 1f)

    /** 1 = normal, 0 = animations off, up to [MAX_DURATION_SCALE] = slower. Set by the host. */
    var durationScale: Float by mutableStateOf(1f)
        private set

    /** True when the person has turned animations off. Pair with conveyance's LocalReducedMotion. */
    val reduced: Boolean get() = durationScale <= 0f

    /** The platform's animator scale. Out-of-range or NaN input falls back to normal speed. */
    fun updateDurationScale(scale: Float) {
        durationScale = if (scale.isNaN() || scale < 0f) 1f else scale.coerceAtMost(MAX_DURATION_SCALE)
    }

    /** [ms] stretched by [durationScale]; wrap every choreographing `delay()` in it. */
    fun scaledDelayMs(ms: Int): Long = scaledDelayMs(ms.toLong())

    /** Long form of [scaledDelayMs]. */
    fun scaledDelayMs(ms: Long): Long = (ms * durationScale).roundToLong()

    // The pill menu's own clocks (HG2Gui's `Azphalt.SLIDE_MS` and friends), unchanged.
    const val SLIDE_MS = 420 / 3
    const val DROP_MS = 420 / 3
    const val SWING_MS = 520 / 3
    const val LIFT_FRACTION = 0.90f

    /**
     * How much of a Cascade/Telescope row's own motion its fade-in takes: fast enough that the
     * parked, pre-swing pill never flashes into view, short enough that the swing itself plays out
     * visibly instead of hiding behind it.
     */
    const val REVEAL_FRACTION = 1f / 6f

    /** A capsule revealed by growing in length ([StackEntrance.Unfold], the perimeter flood). */
    const val UNFOLD_MS = 420

    /**
     * A child surface's fill inside a parent of [parentHue] at [depth]: the same hue pulled toward
     * ink a step per level, so a nested card reads as part of its parent rather than a new object.
     */
    fun innerHue(parentHue: Color, depth: Int): Color =
        lerp(parentHue, H2g2.ink, (INNER_HUE_STEP * depth).coerceAtMost(INNER_HUE_MAX))

    private const val MAX_DURATION_SCALE = 10f
    private const val INNER_HUE_STEP = 0.14f
    private const val INNER_HUE_MAX = 0.5f
}

/** Top-level form of [H2g2Motion.scaledDelayMs], as HG2Gui's call sites spell it. */
fun scaledDelayMs(ms: Long): Long = H2g2Motion.scaledDelayMs(ms)

/** Int overload of [scaledDelayMs] for the many millisecond constants that are Ints. */
fun scaledDelayMs(ms: Int): Long = H2g2Motion.scaledDelayMs(ms)

/**
 * True when the person turned animations off. Recomposes if the setting changes. When it is true,
 * skip entrance and hand-off flourishes outright (show the end state) rather than playing them at
 * zero length; decorative loops should not start at all.
 */
@Composable
fun rememberReducedMotion(): Boolean = H2g2Motion.reduced

// Lottery tickets, not a percentage - see [StackEntrance.roll].
private const val WEIGHT_HOUSE = 4
private const val WEIGHT_COMMON = 3
private const val WEIGHT_OCCASIONAL = 2
private const val WEIGHT_RARE = 1

/**
 * How a stack of pills arrives. Only arrivals vary - every stack still LEAVES by the sweep left,
 * so the departure stays learnable. Resolved once per stack and handed down: a pill that picked
 * for itself could disagree with the row beneath it.
 */
enum class StackEntrance(val weight: Int) {
    Slide(WEIGHT_HOUSE), // the house arrival; deliberately the most common
    Unfold(WEIGHT_COMMON),
    Drop(WEIGHT_COMMON),
    Cascade(WEIGHT_OCCASIONAL),
    Deal(WEIGHT_OCCASIONAL),
    Telescope(WEIGHT_OCCASIONAL),
    Split(WEIGHT_RARE),
    Rally(WEIGHT_RARE),
    Extend(WEIGHT_OCCASIONAL),
    Unroll(WEIGHT_OCCASIONAL),
    Tumble(WEIGHT_OCCASIONAL);

    companion object {
        private var last: StackEntrance? = null

        // Unroll and Tumble are a root-stack flourish: a wide staggered wave reads across a whole
        // screen of rows, but a child band is a handful of pills, where it reads as Cascade.
        private val CHILD_BAND_POOL = entries.filterNot { it == Unroll || it == Tumble }

        /** Excludes whatever ran last, so the variation is perceptible. Shared across root and
         *  child-band rolls on purpose: a band opening still spends a turn. */
        fun roll(random: Random = Random.Default): StackEntrance = rollFrom(entries, random)

        /** The same lottery, restricted to what a child band can use. */
        fun rollForChildBand(random: Random = Random.Default): StackEntrance = rollFrom(CHILD_BAND_POOL, random)

        private fun rollFrom(candidates: List<StackEntrance>, random: Random): StackEntrance {
            val pool = candidates.filter { it != last }
            var n = random.nextInt(pool.sumOf { it.weight })
            for (e in pool) {
                if (n < e.weight) {
                    last = e
                    return e
                }
                n -= e.weight
            }
            return pool.last().also { last = it }
        }
    }
}

private const val ROW_CAP = 6

/** Per-row interval, halved past six rows so a tall stack never runs long. */
fun stackInterval(base: Int, rowCount: Int): Int = if (rowCount > ROW_CAP) base / 2 else base

/** Most time, in ms, a whole stack may spend waiting for its stagger. */
const val STAGGER_TOTAL_CAP_MS = 600

/**
 * The start delay for row [index] of [rowCount], [perRowMs] apart, the whole wait capped at
 * [STAGGER_TOTAL_CAP_MS]: past that the gap shrinks so the last row still starts within the cap.
 * Wrap in [H2g2Motion.scaledDelayMs] before `delay()`.
 */
fun staggerDelayMs(index: Int, rowCount: Int, perRowMs: Int): Long {
    if (index <= 0 || rowCount <= 1) return 0L
    val gaps = rowCount - 1
    val perRow = if (perRowMs * gaps > STAGGER_TOTAL_CAP_MS) STAGGER_TOTAL_CAP_MS / gaps else perRowMs
    return (index.coerceAtMost(gaps) * perRow).toLong()
}

/*
 * A stack that moves as one sways: it is a pile, and the higher a pill sits the further it swings,
 * hinged on its visible right end, damping out in three diminishing swings. Every shared-clock
 * stack move - arrive, leave, host drop - never a single pill alone, never a loop.
 */
private const val BASE_AMPLITUDE_DEG = 0.9f
private const val AMPLITUDE_PER_ROW_DEG = 0.7f
private const val MAX_AMPLITUDE_DEG = 3.4f
private const val SWING_1_FRACTION = 0.30f
private const val SWING_2_FRACTION = 0.58f
private const val SWING_2_SCALE = 0.55f
private const val SWING_3_FRACTION = 0.82f
private const val SWING_3_SCALE = 0.24f

/** Degrees. Grows with height in the pile, capped so the top row never flails. */
fun wobbleAmplitude(row: Int): Float = min(BASE_AMPLITUDE_DEG + row * AMPLITUDE_PER_ROW_DEG, MAX_AMPLITUDE_DEG)

/**
 * Out, back, again smaller, settled. Runs over the duration of the move it accompanies. Additive:
 * animates its receiver from 0 back to 0, so callers sum it onto the rotation the pill already
 * holds (`rotationZ = turn.value + sway.value`). [amplitudeScale] is 2 for [StackEntrance.Rally].
 */
suspend fun Animatable<Float, AnimationVector1D>.wobble(row: Int, durationMs: Int, amplitudeScale: Float = 1f) {
    val a = wobbleAmplitude(row) * amplitudeScale
    snapTo(0f)
    if (H2g2Motion.reduced) return
    animateTo(0f, keyframes {
        durationMillis = durationMs
        0f at 0 using LinearEasing
        a at (durationMs * SWING_1_FRACTION).toInt() using LinearEasing
        (-a * SWING_2_SCALE) at (durationMs * SWING_2_FRACTION).toInt() using LinearEasing
        (a * SWING_3_SCALE) at (durationMs * SWING_3_FRACTION).toInt() using LinearEasing
        0f at durationMs
    })
}
