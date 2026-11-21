package com.hereliesaz.conveyance.h2g2.files

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.hereliesaz.conveyance.h2g2.H2g2
import com.hereliesaz.conveyance.h2g2.H2g2PerimeterReveal
import com.hereliesaz.conveyance.h2g2.PerimeterRevealState

/*
 * "The pill becomes the page": in HG2Gui the Files pill (and the Select File/Folder pill) runs the
 * screen's full perimeter edge by edge and floods it, and the file manager is what the flood
 * uncovers; closing plays the same beats backwards. That choreography is H2g2PerimeterReveal. What
 * lived in HG2Gui's activity around it - one PerimeterRevealState per screen, the open()/close()
 * calls with the measured screen size, and the guard that cancels whichever of the two is still
 * running before the other starts - is this composable, with the state it kept hoisted to a single
 * [visible] flag.
 */

// Matches the fallback H2g2PerimeterReveal's own render code uses when a reveal's origin rect
// hasn't been reported yet.
private val REVEAL_DEFAULT_BASE_WIDTH = 64.dp
private val REVEAL_MIN_THICKNESS = 34.dp

/**
 * The file manager's transitional open and close. Put [H2g2FilesScreen] (or
 * [H2g2PathPickerScreen]) in [content] and flip [visible]: on `true` a [hue]-coloured pill
 * stretches, breaks free and falls from [origin], the frame runs the perimeter of this
 * composable's bounds and floods it, and [content] wipes on; on `false` the same plays in reverse,
 * [content] leaves the composition, and [onClosed] is called. A tap during either skips to its
 * end, and with animations off (`H2g2Motion.reduced`) both are instant.
 *
 * - [origin]: where the thing that opened it sits (the tapped pill's bounds), in this
 *   composable's own coordinates - it fills its parent, so normally the root's. `Rect.Zero` (the
 *   default) starts from the bottom-left corner.
 * - [hue]: the frame's colour. The default is the Files pill's own (the hue of `"/"`).
 *
 * Flipping [visible] back while a run is still playing cancels that run before the other starts,
 * so the two never race the same animation. It draws nothing and takes no input while closed.
 * A host that needs the animation state itself (to gate other UI on `active`, say) can use
 * [H2g2PerimeterReveal] with its own [PerimeterRevealState] instead.
 */
@Composable
fun H2g2FilesReveal(
    visible: Boolean,
    modifier: Modifier = Modifier,
    origin: Rect = Rect.Zero,
    hue: Color = H2g2.hues[H2g2.hueOf("/")],
    onClosed: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val state = remember { PerimeterRevealState() }
    val density = LocalDensity.current
    val currentOrigin by rememberUpdatedState(origin)
    val currentOnClosed by rememberUpdatedState(onClosed)
    // The reveal frame runs the perimeter of this Box, so open()/close() are sized from its real
    // measured size.
    BoxWithConstraints(modifier.fillMaxSize()) {
        val fullWidthPx = with(density) { maxWidth.toPx() }
        val fullHeightPx = with(density) { maxHeight.toPx() }
        val defaultBaseWidthPx = with(density) { REVEAL_DEFAULT_BASE_WIDTH.toPx() }
        val minThicknessPx = with(density) { REVEAL_MIN_THICKNESS.toPx() }
        // Keyed on [visible] alone: a flip cancels whichever of open()/close() is still driving
        // the state's Animatables before the other one starts, instead of both racing them.
        LaunchedEffect(visible) {
            if (visible) {
                state.active = true
                state.origin = currentOrigin
                state.open(fullWidthPx, fullHeightPx, defaultBaseWidthPx, minThicknessPx)
            } else if (state.active) {
                state.close(fullWidthPx, fullHeightPx, defaultBaseWidthPx, minThicknessPx)
                currentOnClosed()
            }
        }
        H2g2PerimeterReveal(state = state, hue = hue, content = content)
    }
}
