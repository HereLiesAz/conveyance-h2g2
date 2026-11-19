package com.hereliesaz.conveyance.h2g2

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt

/*
 * HG2Gui's slot-reel scroll (ui/menu/PillMenu.kt rememberStackScroll / SlotFlingBehavior): a stack
 * of pills positioned by absolute animated translationY - so the "fly up from a shared anchor"
 * entrance works - cannot sit in a LazyColumn, so it scrolls by a drag-tracked offset instead,
 * and a fling lands on a row boundary like a reel slowing to a stop. Content follows the finger.
 */

/**
 * The scroll of one stack. Add [offsetPx] to each row's resting lift (row R rests at
 * -pitch*R; it sits on the anchor when offsetPx == pitch*R). [alignedRow] is the row currently on
 * the anchor once the person has scrolled at all, -1 before: a cosmetic "primed" marker only,
 * never a pick.
 */
class SlotReelScroll internal constructor(val modifier: Modifier, val offsetPx: Float, val alignedRow: Int)

/**
 * @param rowMin the row closest to the anchor (0 for a root stack).
 * @param rowMax the furthest row; both ends stop dead, no bounce.
 * @param resetKey a new stack (a different visit) starts unscrolled.
 */
@Composable
fun rememberSlotReelScroll(rowPitch: Dp, rowMin: Int, rowMax: Int, resetKey: Any? = Unit): SlotReelScroll {
    val pitchPx = with(LocalDensity.current) { rowPitch.toPx() }
    var offsetPx by remember(resetKey) { mutableStateOf(pitchPx * rowMin) }
    var hasScrolled by remember(resetKey) { mutableStateOf(false) }
    val minOffsetPx = pitchPx * rowMin
    val maxOffsetPx = pitchPx * rowMax
    val scrollState = rememberScrollableState { delta ->
        hasScrolled = true
        val next = (offsetPx + delta).coerceIn(minOffsetPx, maxOffsetPx)
        val consumed = next - offsetPx
        offsetPx = next
        consumed
    }
    val fling = rememberSlotFlingBehavior(pitchPx) { offsetPx }
    val modifier = Modifier.scrollable(orientation = Orientation.Vertical, state = scrollState, flingBehavior = fling)
    val aligned = if (hasScrolled && pitchPx > 0f) (offsetPx / pitchPx).roundToInt() else -1
    return SlotReelScroll(modifier, offsetPx, aligned)
}

/**
 * A fling's natural resting point, rounded to the nearest row: a hard flick barely moves, a gentle
 * release snaps almost at once - "less effect the faster it goes" from one formula. The motion to
 * that target is a critically damped spring; the decay curve only picks where it heads.
 */
@Composable
fun rememberSlotFlingBehavior(pitchPx: Float, currentOffsetPx: () -> Float): FlingBehavior {
    val decay = rememberSplineBasedDecay<Float>()
    return remember(pitchPx, decay) { SlotFlingBehavior(pitchPx, currentOffsetPx, decay) }
}

private class SlotFlingBehavior(
    private val pitchPx: Float,
    private val currentOffsetPx: () -> Float,
    private val decay: DecayAnimationSpec<Float>,
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        if (pitchPx <= 0f) return initialVelocity
        val current = currentOffsetPx()
        val snapped = ((current + decay.calculateTargetValue(0f, initialVelocity)) / pitchPx).roundToInt() * pitchPx
        var traveled = 0f
        AnimationState(initialValue = 0f, initialVelocity = initialVelocity).animateTo(
            targetValue = snapped - current,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        ) {
            scrollBy(value - traveled)
            traveled = value
        }
        return 0f
    }
}
