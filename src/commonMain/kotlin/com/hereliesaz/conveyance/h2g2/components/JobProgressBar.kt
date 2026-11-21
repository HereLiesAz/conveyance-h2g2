package com.hereliesaz.conveyance.h2g2.components

import com.hereliesaz.conveyance.h2g2.H2g2
import com.hereliesaz.conveyance.h2g2.H2g2Contrast
import com.hereliesaz.conveyance.h2g2.LocalH2g2Ground
import com.hereliesaz.conveyance.h2g2.onPage
import com.hereliesaz.conveyance.h2g2.contrastRatio
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/*
 * "job"/"fail" from the house Motion Sheet - a long-running task's own progress readout: a thin
 * bar filling as real work completes (job), or filling partway and giving up (fail) - "the joke
 * is entirely in the pause: it commits, gets a third of the way, and stops long enough for you to
 * notice it has stopped." First real consumer: DistroManager.bootstrap()'s own download, whose
 * progress this app only ever showed as a wall of scrolling "Downloaded: XMB / YMB" text lines
 * before this.
 *
 * Unlike the spec's own demo (a fixed, indeterminate 2.6s linear fill), this tracks REAL progress
 * - [advanceTo] is driven by the caller's own actual byte count, so [fail] stalls wherever the
 * job genuinely got to, not a scripted 34%. Held long enough to register as stopped, not merely
 * paused, before draining away - never resets instantly.
 */

private const val GROW_MS = 300
private const val STALL_HOLD_MS = 480L
private const val DRAIN_MS = 200
private val BAR_HEIGHT = 4.dp

/**
 * The state behind [H2g2JobProgressBar]. A host creates one with `remember { JobProgressBarState() }`,
 * hands it to the bar, and drives it from a coroutine as the real job reports: [advanceTo] for each
 * progress update, then [complete] or [fail] when the job ends.
 */
class JobProgressBarState {
    /** The bar's current fill, 0..1. Read by [H2g2JobProgressBar]; hosts normally only call the functions below. */
    val fill = Animatable(0f)

    /** Advances toward real progress - smoothed, not snapped, so a burst of fast updates (a
     *  download's own chunked progress callbacks) doesn't read as a stutter. */
    suspend fun advanceTo(fraction: Float) {
        fill.animateTo(fraction.coerceIn(0f, 1f), tween(GROW_MS, easing = LinearEasing))
    }

    /** The job finished cleanly - fills the rest of the way, whatever fraction it was actually at. */
    suspend fun complete() {
        fill.animateTo(1f, tween(GROW_MS, easing = LinearEasing))
    }

    /** "The joke is entirely in the pause": holds wherever it actually got to, then drains away. */
    suspend fun fail() {
        delay(STALL_HOLD_MS)
        fill.animateTo(0f, tween(DRAIN_MS, easing = LinearEasing))
    }
}

/**
 * The fill colour for a progress bar drawn on [page]: the first of Ink, Yellow and White that reads
 * at the non-text threshold (3:1) against it, so the bar is Ink on the light grounds and Yellow on
 * the dark ones instead of the old always-Yellow fill (1.11:1 on Mustard).
 */
internal fun progressFillOn(page: Color): Color {
    val candidates = listOf(H2g2.ink, H2g2.yellow, H2g2.white)
    return candidates.firstOrNull { contrastRatio(it, page) >= H2g2Contrast.NON_TEXT }
        ?: candidates.maxByOrNull { contrastRatio(it, page) }!!
}

/**
 * The bar itself. It reports its fill to accessibility as a progress range, and by default pads
 * itself clear of the status bar and display cutout ([avoidSystemBars]) because callers place it as
 * an overlay at the very top of the window, outside any screen's own inset handling; pass false if
 * the parent already pads it.
 *
 * A host places it over its content and drives [state] ([JobProgressBarState.advanceTo],
 * [JobProgressBarState.complete], [JobProgressBarState.fail]) from the job's own callbacks;
 * [description] is what a screen reader calls it.
 */
@Composable
fun H2g2JobProgressBar(
    state: JobProgressBarState,
    modifier: Modifier = Modifier,
    avoidSystemBars: Boolean = true,
    description: String = "Progress"
) {
    val ground = LocalH2g2Ground.current
    val onPage = ground.onPage
    val fraction = state.fill.value.coerceIn(0f, 1f)
    val label = description
    Box(
        modifier
            .then(
                if (avoidSystemBars) {
                    Modifier.windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                } else {
                    Modifier
                }
            )
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            }
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .clip(RoundedCornerShape(percent = 50))
            .background(onPage.copy(alpha = TRACK_ALPHA))
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .clip(RoundedCornerShape(percent = 50))
                .background(progressFillOn(ground.page))
        )
    }
}

private const val TRACK_ALPHA = 0.18f
