package com.hereliesaz.conveyance.h2g2

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/*
 * The splash, built from `docs/design/HG2Gui Splash.html`. HG2Gui specified it and never shipped
 * it, so this is the one composable here drawn from a design document rather than from HG2Gui's
 * Kotlin. A flat ink hand pops up as a fist, shows four fingers, then two, then a thumbs-up, and
 * the wordmark wipes in. Four, two: the answer, given by hand. Geometry, cues and curves are the
 * document's own; only the colours follow the ground instead of assuming Mustard.
 */

// Scene lengths in seconds (the design's OM_SCENES); each cue is where its scene starts.
private const val POP_S = 0.6f
private const val FOUR_S = 1.0f
private const val TWO_S = 1.0f
private const val THUMBS_UP_S = 1.3f
private const val CUE_FOUR = POP_S
private const val CUE_TWO = CUE_FOUR + FOUR_S
private const val CUE_THUMBS_UP = CUE_TWO + TWO_S
private const val TOTAL_S = CUE_THUMBS_UP + THUMBS_UP_S
private const val MS_PER_S = 1000

// The hand is drawn in the design's own 200-unit box, shown at 220dp.
private const val VIEW_BOX = 200f
private val HAND_SIZE = 220.dp
private val WORDMARK_GAP = 28.dp

private const val HAND_PIVOT_X = 100f
private const val HAND_PIVOT_Y = 110f
private const val PALM_LEFT = 62f
private const val PALM_WIDTH = 76f
private const val PALM_HEIGHT = 66f
private const val PALM_RADIUS = 26f
private const val DIGIT_WIDTH = 16f
private const val DIGIT_RADIUS = 8f
private const val FINGER_ROOT_OVERLAP = 12f
private const val THUMB_ROOT_OVERLAP = 10f
private const val CURL_SQUASH = 0.86f
private const val THUMB_X = 64f
private const val THUMB_Y = 126f
private const val THUMB_TUCKED_DEG = 95f
private const val THUMB_UP_DEG = -15f
private const val THUMB_TUCKED_LEN = 38f
private const val THUMB_UP_LEN = 56f
private const val BURST_Y = 130f
private const val BURST_RADIUS = 40f

/** One finger: where it stands on the palm's top edge, and how tall it is uncurled. */
private class Finger(val cx: Float, val height: Float)

private val INDEX = Finger(cx = 76f, height = 66f)
private val MIDDLE = Finger(cx = 92f, height = 74f)
private val RING = Finger(cx = 108f, height = 66f)
private val PINKY = Finger(cx = 124f, height = 50f)

private const val BACK_OVERSHOOT = 1.70158f

private fun easeOutBack(t: Float): Float {
    val c3 = BACK_OVERSHOOT + 1f
    return 1f + c3 * (t - 1f).pow(3) + BACK_OVERSHOOT * (t - 1f).pow(2)
}

private fun easeInOutQuad(t: Float): Float = if (t < 0.5f) 2f * t * t else -1f + (4f - 2f * t) * t

@Suppress("MagicNumber") // the standard easeOutElastic constants
private fun easeOutElastic(t: Float): Float = when (t) {
    0f -> 0f
    1f -> 1f
    else -> 2f.pow(-10f * t) * sin((t * 10f - 0.75f) * (2f * PI.toFloat() / 3f)) + 1f
}

/** [from] before [start], [to] after [end], eased between: the design's `animate`. */
private fun span(time: Float, from: Float, to: Float, start: Float, end: Float, ease: (Float) -> Float): Float = when {
    time <= start -> from
    time >= end -> to
    else -> from + (to - from) * ease((time - start) / (end - start))
}

/** Everything the splash draws at one instant. */
private class SplashFrame(time: Float) {
    val scale = span(time, 0.4f, 1f, 0f, 0.4f, ::easeOutBack)

    private val unfurl = span(time, 0f, 1f, CUE_FOUR, CUE_FOUR + 0.35f, ::easeInOutQuad)
    private val foldOuter = span(time, 0f, 1f, CUE_TWO, CUE_TWO + 0.3f, ::easeInOutQuad)
    private val foldInner = span(time, 0f, 1f, CUE_THUMBS_UP, CUE_THUMBS_UP + 0.3f, ::easeInOutQuad)

    /** 1 = curled into the fist, 0 = straight. Index and middle hold until the thumbs-up. */
    val innerCurl = (1f - unfurl + foldInner).coerceIn(0f, 1f)
    val outerCurl = (1f - unfurl + foldOuter).coerceIn(0f, 1f)

    private val thumbUp = span(time, 0f, 1f, CUE_THUMBS_UP, CUE_THUMBS_UP + 0.35f, ::easeInOutQuad)
    val thumbAngle = THUMB_TUCKED_DEG + (THUMB_UP_DEG - THUMB_TUCKED_DEG) * thumbUp
    val thumbLength = THUMB_TUCKED_LEN + (THUMB_UP_LEN - THUMB_TUCKED_LEN) * thumbUp

    private val burst = span(time, 0f, 1f, 0.15f, 0.55f, ::easeOutElastic)
    val burstScale = 0.4f + burst * 2.3f
    val burstAlpha = ((1f - burst) * 0.3f).coerceIn(0f, 1f)

    val wordmark = span(time, 0f, 1f, CUE_THUMBS_UP + 0.5f, CUE_THUMBS_UP + 0.9f, ::easeInOutQuad)
}

/**
 * The h2g2 splash: an ink hand pops up as a fist, counts four, then two, then gives a thumbs-up
 * while [wordmark] wipes in beneath it. It runs once, about four seconds, then calls [onFinished];
 * the host swaps in its first screen there.
 *
 * It fills its bounds with [ground]'s fold and draws in that ground's own text colour, so it reads
 * on every ground. With animations turned off it shows the final frame and finishes at once.
 *
 * ~~~kotlin
 * var ready by remember { mutableStateOf(false) }
 * if (!ready) H2g2Splash(onFinished = { ready = true }) else Home()
 * ~~~
 *
 * @param wordmark the name wiped in under the hand, upper-cased; null draws the hand alone.
 * @param ground the ground to draw on; defaults to the one [H2g2Theme] provides.
 */
@Composable
fun H2g2Splash(
    modifier: Modifier = Modifier,
    wordmark: String? = "HG2Gui",
    ground: Ground = LocalH2g2Ground.current,
    onFinished: () -> Unit = {},
) {
    val reduced = rememberReducedMotion()
    val finished by rememberUpdatedState(onFinished)
    val clock = remember { Animatable(0f) }
    LaunchedEffect(reduced) {
        if (reduced) {
            clock.snapTo(TOTAL_S)
        } else {
            clock.animateTo(TOTAL_S, tween((TOTAL_S * MS_PER_S).toInt(), easing = LinearEasing))
        }
        finished()
    }

    val ink = ground.onPage
    val type = currentH2g2Type()
    Column(
        modifier
            .fillMaxSize()
            .background(ground.foldBrush())
            .semantics(mergeDescendants = true) { if (wordmark != null) contentDescription = wordmark },
        verticalArrangement = Arrangement.spacedBy(WORDMARK_GAP, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(HAND_SIZE)) {
            val frame = SplashFrame(clock.value)
            scale(size.minDimension / VIEW_BOX, pivot = Offset.Zero) {
                drawCircle(
                    color = ground.foldLight,
                    radius = BURST_RADIUS * frame.burstScale,
                    center = Offset(HAND_PIVOT_X, BURST_Y),
                    alpha = frame.burstAlpha,
                )
                drawHand(frame, ink)
            }
        }
        if (wordmark != null) {
            BasicText(
                text = wordmark.uppercase(),
                // The capsule step is the design's own 800/15px; only the tracking is the splash's.
                style = type.capsule.copy(color = ink, letterSpacing = 0.32.em),
                modifier = Modifier.drawWithContent {
                    // Wiped in from the left edge, never faded.
                    val shown = SplashFrame(clock.value).wordmark
                    clipRect(right = size.width * shown) { this@drawWithContent.drawContent() }
                },
            )
        }
    }
}

private fun DrawScope.drawHand(frame: SplashFrame, ink: Color) {
    scale(frame.scale, pivot = Offset(HAND_PIVOT_X, HAND_PIVOT_Y)) {
        drawRoundRect(
            color = ink,
            topLeft = Offset(PALM_LEFT, HAND_PIVOT_Y),
            size = Size(PALM_WIDTH, PALM_HEIGHT),
            cornerRadius = CornerRadius(PALM_RADIUS),
        )
        drawFinger(INDEX, frame.innerCurl, ink)
        drawFinger(MIDDLE, frame.innerCurl, ink)
        drawFinger(RING, frame.outerCurl, ink)
        drawFinger(PINKY, frame.outerCurl, ink)
        translate(THUMB_X, THUMB_Y) {
            rotate(frame.thumbAngle, pivot = Offset.Zero) {
                drawDigit(frame.thumbLength, THUMB_ROOT_OVERLAP, ink)
            }
        }
    }
}

/** A finger curls by squashing toward its root on the palm's top edge, as the design does. */
private fun DrawScope.drawFinger(finger: Finger, curl: Float, ink: Color) {
    translate(finger.cx, HAND_PIVOT_Y) {
        scale(scaleX = 1f, scaleY = 1f - curl * CURL_SQUASH, pivot = Offset.Zero) {
            drawDigit(finger.height, FINGER_ROOT_OVERLAP, ink)
        }
    }
}

/** A capsule standing [length] above the origin, sunk [overlap] below it into the palm. */
private fun DrawScope.drawDigit(length: Float, overlap: Float, ink: Color) {
    drawRoundRect(
        color = ink,
        topLeft = Offset(-DIGIT_WIDTH / 2f, -length),
        size = Size(DIGIT_WIDTH, length + overlap),
        cornerRadius = CornerRadius(DIGIT_RADIUS),
    )
}
