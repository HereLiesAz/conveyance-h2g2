package com.hereliesaz.conveyance.h2g2

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * h2g2's three-shape surface vocabulary. Two soft rectangular surfaces, [recordTile] and [note];
 * everything else is the fully-rounded [capsule]. There is deliberately no sharp-corner "card"
 * shape -- almost every surface in practice resolves to [capsule]; [recordTile]/[note] are
 * reserved for the two containers that need a rectangular-ish (but still heavily rounded) shape
 * instead of a pill. Ported from HG2Gui's own `AzphaltSurface`, values unchanged.
 */
object H2g2Surface {
    val recordTile: Shape = RoundedCornerShape(26.dp)
    val note: Shape = RoundedCornerShape(20.dp)
    val capsule: Shape = RoundedCornerShape(percent = 50)

    /** Looks up a shape by the composable manifest's `surface` string (azphalt `spec/composable.md`). */
    fun byName(name: String): Shape = when (name) {
        "recordTile" -> recordTile
        "note" -> note
        "capsule" -> capsule
        else -> capsule
    }
}
