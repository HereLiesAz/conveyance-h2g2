package com.hereliesaz.conveyance.h2g2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hereliesaz.conveyance.Act
import com.hereliesaz.conveyance.compose.Offer
import com.hereliesaz.conveyance.h2g2.H2g2.contrastingText

/**
 * What a `kind: "composable"` `.azp` package's `elements[]` entry (azphalt `spec/composable.md`)
 * supplies once a host has resolved it against this library's [Templates.registry] and built the
 * live [Act] the element performs. The manifest's own `hue`/`surface`/`scale` strings pass
 * straight through as [hueSeed]/[surface]/[scale]; `templateId` is the registry lookup key and
 * isn't repeated here. [subtitle] is optional -- most templates render [label] alone; the ones
 * that use a second line say so.
 */
data class ComposableRequest(
    val act: Act,
    /** Hashed via [H2g2.hueOf] -- typically the manifest element's own `id`, or a subject id. */
    val hueSeed: String,
    val surface: String,
    val scale: String,
    val label: String,
    val subtitle: String? = null,
)

/**
 * The h2g2 composable-set's template registry -- what a `templateId` resolves against once this
 * artifact is linked at build time. A host looks a `templateId` up here and calls the matching
 * function with the manifest's declared token values; nothing arrives that this artifact didn't
 * already ship.
 */
object Templates {
    val registry: Map<String, @Composable (ComposableRequest) -> Unit> = mapOf(
        "h2g2.tile.record" to { request -> RecordTile(request) },
        "h2g2.tile.note" to { request -> NoteTile(request) },
        "h2g2.pill.action" to { request -> ActionPill(request) },
    )
}

/**
 * A [H2g2Surface.recordTile]-shaped element: the larger of h2g2's two soft rectangular surfaces,
 * colored by [ComposableRequest.hueSeed] via [H2g2.hueOf], offering [ComposableRequest.act].
 * Renders [ComposableRequest.subtitle] as a second, smaller line below [ComposableRequest.label]
 * when present -- a title plus a detail line, the record tile's two-line form.
 */
@Composable
fun RecordTile(request: ComposableRequest) {
    val hue = H2g2.hues[H2g2.hueOf(request.hueSeed)]
    val textColor = hue.contrastingText()
    Offer(act = request.act, modifier = Modifier.wrapContentSize()) {
        Box(
            modifier = Modifier
                .clip(H2g2Surface.recordTile)
                .background(hue)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (request.subtitle == null) {
                BasicText(
                    text = request.label,
                    style = h2g2Type().step(request.scale).copy(color = textColor),
                )
            } else {
                Column {
                    BasicText(
                        text = request.label,
                        style = h2g2Type().step(request.scale).copy(color = textColor),
                    )
                    BasicText(
                        text = request.subtitle,
                        style = h2g2Type().body.copy(color = textColor),
                    )
                }
            }
        }
    }
}

/**
 * A [H2g2Surface.note]-shaped element: h2g2's other soft rectangular surface -- smaller, meant
 * for a secondary/annotation block rather than a primary record. Unlike [RecordTile],
 * [ComposableRequest.subtitle] renders *above* [ComposableRequest.label] in the `eyebrow` step --
 * a note is usually labeled ("Reminder", "Note to self") before it's read, not captioned after.
 */
@Composable
fun NoteTile(request: ComposableRequest) {
    val hue = H2g2.hues[H2g2.hueOf(request.hueSeed)]
    val textColor = hue.contrastingText()
    Offer(act = request.act, modifier = Modifier.wrapContentSize()) {
        Box(
            modifier = Modifier
                .clip(H2g2Surface.note)
                .background(hue)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Column {
                if (request.subtitle != null) {
                    BasicText(text = request.subtitle, style = h2g2Type().eyebrow.copy(color = textColor))
                }
                BasicText(
                    text = request.label,
                    style = h2g2Type().step(request.scale).copy(color = textColor),
                )
            }
        }
    }
}

/**
 * A [H2g2Surface.capsule]-shaped element: h2g2's default surface, used for nearly everything --
 * buttons, chips, pills -- colored by [ComposableRequest.hueSeed] via [H2g2.hueOf], offering
 * [ComposableRequest.act].
 */
@Composable
fun ActionPill(request: ComposableRequest) {
    val hue = H2g2.hues[H2g2.hueOf(request.hueSeed)]
    Offer(act = request.act, modifier = Modifier.wrapContentSize()) {
        Box(
            modifier = Modifier
                .clip(H2g2Surface.capsule)
                .background(hue)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = request.label,
                style = h2g2Type().step(request.scale).copy(color = hue.contrastingText()),
            )
        }
    }
}
