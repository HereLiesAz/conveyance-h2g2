package com.hereliesaz.conveyance.h2g2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * The root of an h2g2 screen: HG2Gui's `HG2GuiTheme`, without the Material layer it carried only
 * for `Text`. Provides, for everything beneath it:
 *
 * - [LocalH2g2Ground] and [LocalH2g2GroundState] from [groundState], recomposing on a reroll;
 * - [LocalH2g2ContentColor], the ground's own text colour, so uncoloured text reads on every
 *   ground rather than assuming the light Mustard one;
 * - [LocalH2g2Type] ([type], or the bundled Azrienoch when null) and [LocalH2g2UiScale];
 * - a root background painted with the ground, so the first composed frame, a loading screen and
 *   any area a screen forgets to fill all show the ground;
 * - on Android: the system animator scale into [H2g2Motion] (live), and a window background and
 *   status/navigation icon tint matched to the ground instead of the system light/dark theme.
 *
 * [scale] is a Text-size setting (1 = 100%): it multiplies the whole UI's density, while font
 * scaling stays the system's. Every component in this library also works outside this theme, on
 * Mustard, in Azrienoch, at scale 1.
 *
 * [groundState] defaults to one remembered here; pass a longer-lived one (process-wide, say) when
 * several roots must agree on the ground, as HG2Gui's separate editor Activity does.
 */
@Composable
fun H2g2Theme(
    groundState: GroundState = remember { GroundState() },
    scale: Float = 1f,
    type: H2g2Type? = null,
    content: @Composable () -> Unit,
) {
    val ground = groundState.current
    H2g2PlatformEffects(ground)
    val base = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(base.density * scale, base.fontScale),
        LocalH2g2Ground provides ground,
        LocalH2g2GroundState provides groundState,
        LocalH2g2ContentColor provides ground.onPage,
        LocalH2g2Type provides (type ?: rememberH2g2Type()),
        LocalH2g2UiScale provides scale,
    ) {
        Box(Modifier.fillMaxSize().background(ground.pageBrush())) {
            content()
        }
    }
}

/**
 * What only a platform can do for [H2g2Theme]. Android feeds the animator scale to [H2g2Motion]
 * and paints the window; the other targets have nothing to feed, so motion stays at normal speed
 * unless the host calls [H2g2Motion.updateDurationScale] itself.
 */
@Composable
internal expect fun H2g2PlatformEffects(ground: Ground)
