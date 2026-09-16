package com.hereliesaz.conveyance.h2g2

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.roundToInt

/** A transient non-agent visitor to the terrarium. */
enum class H2g2ServiceVehicleKind {
    MailTruck,
    CourierVan,
    UtilityCart,
}

data class H2g2TerrariumServiceVisit(
    val id: String,
    val serviceName: String,
    val operation: String? = null,
    val target: H2g2TerrariumPosition,
    /** A false visit is retained by the host for history but does not enter the bowl. */
    val active: Boolean,
    val vehicleKind: H2g2ServiceVehicleKind = serviceVehicleKind(serviceName),
    val livery: H2g2ServiceLivery = serviceLivery(serviceName),
)

data class H2g2ServiceLivery(
    val mark: String,
    val longName: String,
)

fun serviceVehicleKind(serviceName: String): H2g2ServiceVehicleKind = when {
    serviceName.contains("git", ignoreCase = true) ||
        serviceName.contains("pull", ignoreCase = true) ||
        serviceName.contains("github", ignoreCase = true) -> H2g2ServiceVehicleKind.MailTruck
    serviceName.contains("deploy", ignoreCase = true) ||
        serviceName.contains("release", ignoreCase = true) -> H2g2ServiceVehicleKind.CourierVan
    else -> H2g2ServiceVehicleKind.UtilityCart
}

/**
 * Deliberately dumb fictional delivery-company names. The same service always gets the same livery.
 * These are not replicas of real postal branding; they are terrarium shorthand for software work.
 */
fun serviceLivery(serviceName: String): H2g2ServiceLivery {
    val hash = positiveServiceHash(serviceName)
    val gitLike = serviceName.contains("git", ignoreCase = true) ||
        serviceName.contains("pull", ignoreCase = true) ||
        serviceName.contains("github", ignoreCase = true)
    if (gitLike) {
        return if (hash % 2 == 0) {
            H2g2ServiceLivery("USPR", "US PULL REQUEST")
        } else {
            H2g2ServiceLivery("GIT", "GIT EXPRESS")
        }
    }
    val generic = listOf(
        H2g2ServiceLivery("API", "API COURIER"),
        H2g2ServiceLivery("RPC", "REMOTE PARCEL CO."),
        H2g2ServiceLivery("NET", "NETWORK EXPRESS"),
        H2g2ServiceLivery("EXT", "EXTERNAL DELIVERY"),
    )
    return generic[hash % generic.size]
}

/**
 * Draws transient service visitors over a terrarium. Each active visit drives in, pauses at its
 * recipient, then drives out. It owns no physics loop and disappears completely after delivery.
 */
@Composable
fun H2g2TerrariumServiceLayer(
    visits: List<H2g2TerrariumServiceVisit>,
    modifier: Modifier = Modifier,
) {
    if (visits.none(H2g2TerrariumServiceVisit::active)) return
    BoxWithConstraints(modifier) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        visits.filter(H2g2TerrariumServiceVisit::active).forEach { visit ->
            ServiceVisitActor(visit, widthPx, heightPx)
        }
    }
}

@Composable
private fun ServiceVisitActor(
    visit: H2g2TerrariumServiceVisit,
    widthPx: Float,
    heightPx: Float,
) {
    val progress = remember(visit.id) { Animatable(-.18f) }
    var visible by remember(visit.id) { mutableStateOf(true) }
    val vehicleWidth = 148.dp
    val vehicleHeight = 82.dp

    LaunchedEffect(visit.id, visit.active) {
        if (!visit.active) {
            visible = false
            return@LaunchedEffect
        }
        visible = true
        progress.snapTo(-.18f)
        val stopX = visit.target.x.coerceIn(.18f, .82f)
        progress.animateTo(stopX, tween(900))
        delay(720)
        progress.animateTo(1.18f, tween(980))
        visible = false
    }

    if (!visible) return
    val x = progress.value * widthPx
    val targetY = (visit.target.y * heightPx).coerceIn(68f, heightPx - 68f)
    Box(
        Modifier
            .offset {
                IntOffset(
                    x = (x - 74f).roundToInt(),
                    y = (targetY - 41f).roundToInt(),
                )
            }
            .size(vehicleWidth, vehicleHeight),
    ) {
        Canvas(Modifier.matchParentSize()) {
            when (visit.vehicleKind) {
                H2g2ServiceVehicleKind.MailTruck -> drawMailTruck(visit)
                H2g2ServiceVehicleKind.CourierVan -> drawCourierVan(visit)
                H2g2ServiceVehicleKind.UtilityCart -> drawUtilityCart(visit)
            }
        }
    }
}

private fun DrawScope.drawMailTruck(visit: H2g2TerrariumServiceVisit) {
    val s = min(size.width, size.height)
    val outline = H2g2.ink
    val body = H2g2.white.copy(alpha = .95f)
    val panel = H2g2.hues[H2g2.indexOf(visit.serviceName)]
    val shadow = panel.copy(alpha = .55f)

    drawRoundRect(
        color = outline,
        topLeft = Offset(size.width * .08f, size.height * .24f),
        size = Size(size.width * .77f, size.height * .48f),
        cornerRadius = CornerRadius(s * .055f),
    )
    drawRoundRect(
        color = body,
        topLeft = Offset(size.width * .095f, size.height * .255f),
        size = Size(size.width * .74f, size.height * .445f),
        cornerRadius = CornerRadius(s * .045f),
    )
    val cab = Path().apply {
        moveTo(size.width * .63f, size.height * .255f)
        lineTo(size.width * .79f, size.height * .255f)
        lineTo(size.width * .91f, size.height * .44f)
        lineTo(size.width * .91f, size.height * .69f)
        lineTo(size.width * .63f, size.height * .69f)
        close()
    }
    drawPath(cab, outline)
    val innerCab = Path().apply {
        moveTo(size.width * .65f, size.height * .275f)
        lineTo(size.width * .775f, size.height * .275f)
        lineTo(size.width * .885f, size.height * .45f)
        lineTo(size.width * .885f, size.height * .67f)
        lineTo(size.width * .65f, size.height * .67f)
        close()
    }
    drawPath(innerCab, body)
    drawRect(
        color = panel,
        topLeft = Offset(size.width * .18f, size.height * .36f),
        size = Size(size.width * .37f, size.height * .17f),
    )
    drawRect(
        color = shadow,
        topLeft = Offset(size.width * .18f, size.height * .55f),
        size = Size(size.width * .37f, size.height * .055f),
    )
    drawWindshield(outline, panel)
    drawWheel(Offset(size.width * .26f, size.height * .72f), s)
    drawWheel(Offset(size.width * .74f, size.height * .72f), s)
    drawVehicleMark(visit.livery.mark, Offset(size.width * .365f, size.height * .445f), s)
    drawOperationTag(visit.operation, Offset(size.width * .36f, size.height * .62f), s)
}

private fun DrawScope.drawCourierVan(visit: H2g2TerrariumServiceVisit) {
    val s = min(size.width, size.height)
    val outline = H2g2.ink
    val panel = H2g2.hues[H2g2.indexOf(visit.serviceName)]
    drawRoundRect(
        outline,
        Offset(size.width * .09f, size.height * .3f),
        Size(size.width * .8f, size.height * .4f),
        CornerRadius(s * .1f),
    )
    drawRoundRect(
        panel,
        Offset(size.width * .11f, size.height * .32f),
        Size(size.width * .76f, size.height * .36f),
        CornerRadius(s * .085f),
    )
    drawRect(H2g2.white.copy(alpha = .85f), Offset(size.width * .62f, size.height * .35f), Size(size.width * .18f, size.height * .13f))
    drawWheel(Offset(size.width * .27f, size.height * .71f), s)
    drawWheel(Offset(size.width * .71f, size.height * .71f), s)
    drawVehicleMark(visit.livery.mark, Offset(size.width * .39f, size.height * .5f), s)
}

private fun DrawScope.drawUtilityCart(visit: H2g2TerrariumServiceVisit) {
    val s = min(size.width, size.height)
    val panel = H2g2.hues[H2g2.indexOf(visit.serviceName)]
    val outline = H2g2.ink
    drawRoundRect(outline, Offset(size.width * .18f, size.height * .43f), Size(size.width * .64f, size.height * .25f), CornerRadius(s * .06f))
    drawRoundRect(panel, Offset(size.width * .2f, size.height * .45f), Size(size.width * .6f, size.height * .21f), CornerRadius(s * .045f))
    drawLine(outline, Offset(size.width * .62f, size.height * .45f), Offset(size.width * .72f, size.height * .28f), s * .025f, StrokeCap.Round)
    drawLine(outline, Offset(size.width * .72f, size.height * .28f), Offset(size.width * .82f, size.height * .28f), s * .025f, StrokeCap.Round)
    drawWheel(Offset(size.width * .32f, size.height * .69f), s)
    drawWheel(Offset(size.width * .68f, size.height * .69f), s)
    drawVehicleMark(visit.livery.mark, Offset(size.width * .46f, size.height * .555f), s)
}

private fun DrawScope.drawWindshield(outline: Color, glass: Color) {
    val p = Path().apply {
        moveTo(size.width * .675f, size.height * .31f)
        lineTo(size.width * .77f, size.height * .31f)
        lineTo(size.width * .855f, size.height * .445f)
        lineTo(size.width * .675f, size.height * .445f)
        close()
    }
    drawPath(p, outline)
    drawPath(p, glass.copy(alpha = .35f), style = Stroke(width = 3f))
}

private fun DrawScope.drawWheel(center: Offset, s: Float) {
    drawCircle(H2g2.ink, s * .095f, center)
    drawCircle(H2g2.white.copy(alpha = .7f), s * .042f, center)
}

/** Tiny block-letter marks without a text-layout dependency. */
private fun DrawScope.drawVehicleMark(mark: String, center: Offset, s: Float) {
    val textWidth = s * .36f
    val glyph = s * .018f
    val usable = mark.take(4)
    val startX = center.x - textWidth * .5f
    usable.forEachIndexed { index, character ->
        val x = startX + index * textWidth / 4f
        drawGlyph(character, Offset(x, center.y), s * .075f, glyph)
    }
}

private fun DrawScope.drawGlyph(character: Char, origin: Offset, h: Float, stroke: Float) {
    // Seven-segment-ish lettering is intentionally crude: these are toy terrarium trucks.
    val w = h * .58f
    val paths = when (character.uppercaseChar()) {
        'U' -> listOf(0 to 3, 3 to 4, 4 to 1)
        'S' -> listOf(2 to 0, 0 to 3, 3 to 4, 4 to 1, 1 to 2)
        'P' -> listOf(3 to 0, 0 to 2, 2 to 5, 5 to 3, 3 to 4)
        'R' -> listOf(3 to 0, 0 to 2, 2 to 5, 5 to 3, 3 to 4, 3 to 1)
        'G' -> listOf(2 to 0, 0 to 3, 3 to 4, 4 to 1, 1 to 5)
        'I' -> listOf(0 to 2, 6 to 7, 4 to 1)
        'T' -> listOf(0 to 2, 6 to 7, 7 to 4)
        'A' -> listOf(4 to 3, 3 to 0, 0 to 2, 2 to 1, 3 to 5)
        'E' -> listOf(2 to 0, 0 to 3, 3 to 4, 3 to 5, 4 to 1)
        'X' -> listOf(0 to 1, 2 to 4)
        'N' -> listOf(4 to 3, 3 to 0, 0 to 1, 1 to 2)
        else -> listOf(0 to 2, 2 to 1, 1 to 4, 4 to 3, 3 to 0)
    }
    val points = listOf(
        Offset(origin.x, origin.y - h * .5f),
        Offset(origin.x + w, origin.y + h * .5f),
        Offset(origin.x + w, origin.y - h * .5f),
        Offset(origin.x, origin.y),
        Offset(origin.x, origin.y + h * .5f),
        Offset(origin.x + w, origin.y),
        Offset(origin.x + w * .5f, origin.y - h * .5f),
        Offset(origin.x + w * .5f, origin.y + h * .5f),
    )
    paths.forEach { (a, b) -> drawLine(H2g2.ink, points[a], points[b], stroke, StrokeCap.Round) }
}

private fun DrawScope.drawOperationTag(operation: String?, center: Offset, s: Float) {
    if (operation.isNullOrBlank()) return
    val dots = ((positiveServiceHash(operation) % 4) + 1)
    repeat(dots) { index ->
        drawCircle(
            color = H2g2.ink.copy(alpha = .65f),
            radius = s * .012f,
            center = center + Offset((index - (dots - 1) / 2f) * s * .04f, 0f),
        )
    }
}

private fun positiveServiceHash(value: String): Int {
    var hash = 0x811C9DC5.toInt()
    value.forEach { c ->
        hash = hash xor c.code
        hash *= 0x01000193
    }
    return hash and Int.MAX_VALUE
}
