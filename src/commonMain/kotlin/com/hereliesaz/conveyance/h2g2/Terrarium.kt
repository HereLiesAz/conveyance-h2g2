package com.hereliesaz.conveyance.h2g2

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private const val TERRARIUM_TAU = (PI * 2.0).toFloat()

/** Normalized habitat coordinate. Persist this in the host, not pixel coordinates. */
data class H2g2TerrariumPosition(val x: Float, val y: Float) {
    fun clamped(): H2g2TerrariumPosition = H2g2TerrariumPosition(
        x = x.coerceIn(.05f, .95f),
        y = y.coerceIn(.07f, .93f),
    )
}

data class H2g2TerrariumSubject(
    val node: H2g2WorkflowNode,
    val position: H2g2TerrariumPosition,
    /** First appearance visibly emerges from this parent if the parent exists in the same habitat. */
    val birthParentId: String? = null,
)

enum class H2g2TerrariumRelationshipKind {
    Dependency,
    Spawn,
    Transfer,
    Confer,
}

data class H2g2TerrariumRelationship(
    val from: String,
    val to: String,
    val kind: H2g2TerrariumRelationshipKind = H2g2TerrariumRelationshipKind.Dependency,
    /** True only while the host runtime says the interaction is currently occurring. */
    val active: Boolean = false,
)

/**
 * A workflow as a living fishbowl.
 *
 * Idle motion deliberately does not use a physics engine or per-agent coroutine. One shared phase
 * drives deterministic low-amplitude wander functions for every subject. Static habitat anchors are
 * relaxed only when the subject list changes, so ordinary animation remains O(n), cheap and stable.
 *
 * Dragging is presentation until the drop completes. [onNodeDroppedOn] is the semantic boundary:
 * Haive can turn that gesture into a real dependency/parent-child rewrite, reject it, or stage it
 * for the next workflow revision without this renderer pretending to own orchestration truth.
 */
@Composable
fun H2g2SwarmTerrarium(
    subjects: List<H2g2TerrariumSubject>,
    relationships: List<H2g2TerrariumRelationship>,
    modifier: Modifier = Modifier,
    editable: Boolean = false,
    selectedId: String? = null,
    onNodeSelected: (H2g2WorkflowNode) -> Unit = {},
    onNodeMoved: (String, H2g2TerrariumPosition) -> Unit = { _, _ -> },
    onNodeDroppedOn: (String, String) -> Unit = { _, _ -> },
) {
    if (subjects.isEmpty()) return

    val habitat = remember { mutableStateMapOf<String, H2g2TerrariumPosition>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dropTargetId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(subjects) {
        val ids = subjects.mapTo(mutableSetOf()) { it.node.id }
        habitat.keys.toList().filterNot(ids::contains).forEach(habitat::remove)
        val relaxed = relaxTerrariumAnchors(subjects.associate { it.node.id to it.position.clamped() })
        subjects.forEach { subject ->
            if (draggingId != subject.node.id) {
                habitat[subject.node.id] = relaxed.getValue(subject.node.id)
            }
        }
    }

    val ambient = rememberInfiniteTransition(label = "h2g2-terrarium-ambient")
    val phase by ambient.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(18_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "h2g2-terrarium-shared-clock",
    )

    BoxWithConstraints(modifier = modifier) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val density = LocalDensity.current
        val creatureSize = 124.dp
        val creatureSizePx = with(density) { creatureSize.toPx() }
        val halfCreaturePx = creatureSizePx / 2f
        val subjectById = remember(subjects) { subjects.associateBy { it.node.id } }
        val activeContactById = remember(relationships) {
            buildMap<String, H2g2TerrariumRelationship> {
                relationships.filter { it.active && it.kind != H2g2TerrariumRelationshipKind.Dependency }
                    .forEach { relationship ->
                        put(relationship.from, relationship)
                        put(relationship.to, relationship)
                    }
            }
        }

        Canvas(Modifier.matchParentSize()) {
            drawTerrariumBackdrop()
            relationships.forEach { relationship ->
                val fromAnchor = habitat[relationship.from] ?: return@forEach
                val toAnchor = habitat[relationship.to] ?: return@forEach
                val fromSubject = subjectById[relationship.from] ?: return@forEach
                val toSubject = subjectById[relationship.to] ?: return@forEach
                val fromWander = wanderOffset(fromSubject.node.id, phase, widthPx, heightPx)
                val toWander = wanderOffset(toSubject.node.id, phase, widthPx, heightPx)
                val startCenter = Offset(fromAnchor.x * widthPx, fromAnchor.y * heightPx) + fromWander
                val endCenter = Offset(toAnchor.x * widthPx, toAnchor.y * heightPx) + toWander
                val direction = endCenter - startCenter
                val startTerminal = h2g2SwarmGenome(fromSubject.node.id)
                    .terminalAnchorToward(direction) * halfCreaturePx
                val endTerminal = h2g2SwarmGenome(toSubject.node.id)
                    .terminalAnchorToward(-direction) * halfCreaturePx
                val start = startCenter + startTerminal
                val end = endCenter + endTerminal
                drawTerrariumRelationship(relationship, start, end)
            }
        }

        subjects.forEach { subject ->
            val node = subject.node
            val anchor = habitat[node.id] ?: subject.position.clamped()
            val wander = if (draggingId == node.id) Offset.Zero else wanderOffset(node.id, phase, widthPx, heightPx)
            val currentCenter = Offset(anchor.x * widthPx, anchor.y * heightPx) + wander
            val parentCenter = subject.birthParentId
                ?.let(habitat::get)
                ?.let { Offset(it.x * widthPx, it.y * heightPx) }
            val birth = remember(node.id, subject.birthParentId) { Animatable(if (parentCenter == null) 1f else 0f) }
            LaunchedEffect(node.id, subject.birthParentId) {
                if (parentCenter != null && birth.value < 1f) {
                    delay((stableTerrariumHash(node.id).ushr(1) % 180).toLong())
                    birth.animateTo(1f, tween(620))
                }
            }
            val birthCenter = parentCenter?.let { parent -> parent + (currentCenter - parent) * birth.value } ?: currentCenter
            val contact = activeContactById[node.id]
            val contactOtherId = contact?.let { if (it.from == node.id) it.to else it.from }
            val contactOtherAnchor = contactOtherId?.let(habitat::get)
            val contactDirection = contactOtherAnchor?.let { other ->
                Offset(other.x * widthPx, other.y * heightPx) - currentCenter
            }
            val isDropTarget = dropTargetId == node.id
            val isSelected = selectedId == node.id

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (birthCenter.x - halfCreaturePx).roundToInt(),
                            y = (birthCenter.y - halfCreaturePx).roundToInt(),
                        )
                    }
                    .size(creatureSize)
                    .pointerInput(editable, node.id, widthPx, heightPx, subjects) {
                        if (!editable) return@pointerInput
                        detectDragGestures(
                            onDragStart = {
                                draggingId = node.id
                                dropTargetId = null
                            },
                            onDragCancel = {
                                draggingId = null
                                dropTargetId = null
                            },
                            onDragEnd = {
                                val target = dropTargetId
                                val moved = habitat[node.id]
                                if (moved != null) onNodeMoved(node.id, moved)
                                if (target != null && target != node.id) onNodeDroppedOn(node.id, target)
                                draggingId = null
                                dropTargetId = null
                            },
                        ) { change, dragAmount ->
                            change.consume()
                            val current = habitat[node.id] ?: subject.position
                            val next = H2g2TerrariumPosition(
                                x = current.x + dragAmount.x / widthPx,
                                y = current.y + dragAmount.y / heightPx,
                            ).clamped()
                            habitat[node.id] = next
                            val nextPx = Offset(next.x * widthPx, next.y * heightPx)
                            dropTargetId = subjects
                                .asSequence()
                                .filter { it.node.id != node.id }
                                .map { candidate ->
                                    val candidatePosition = habitat[candidate.node.id] ?: candidate.position
                                    val candidatePx = Offset(candidatePosition.x * widthPx, candidatePosition.y * heightPx)
                                    candidate.node.id to hypot(
                                        (candidatePx.x - nextPx.x).toDouble(),
                                        (candidatePx.y - nextPx.y).toDouble(),
                                    ).toFloat()
                                }
                                .filter { (_, distance) -> distance <= creatureSizePx * .72f }
                                .minByOrNull { it.second }
                                ?.first
                        }
                    }
                    .clickable { onNodeSelected(node) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected || isDropTarget) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .padding(5.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (isDropTarget) H2g2.hues[4].copy(alpha = .28f)
                                else H2g2.white.copy(alpha = .12f),
                            ),
                    )
                }
                H2g2SwarmCharacter(
                    identitySeed = node.id,
                    hueSeed = node.hueSeed,
                    active = node.state == H2g2WorkflowState.Active || draggingId == node.id,
                    contactDirection = contactDirection,
                    contactStrength = if (contact != null) 1f else 0f,
                    modifier = Modifier.matchParentSize(),
                )
                BasicText(
                    text = node.label.uppercase(),
                    style = h2g2Type().endCap.copy(
                        color = H2g2.white,
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .clip(RoundedCornerShape(999.dp))
                        .background(H2g2.ink.copy(alpha = .84f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTerrariumBackdrop() {
    drawRect(H2g2.ink.copy(alpha = .08f))
    val grid = 48.dp.toPx()
    var x = 0f
    while (x <= size.width) {
        drawLine(H2g2.white.copy(alpha = .025f), Offset(x, 0f), Offset(x, size.height), 1f)
        x += grid
    }
    var y = 0f
    while (y <= size.height) {
        drawLine(H2g2.white.copy(alpha = .025f), Offset(0f, y), Offset(size.width, y), 1f)
        y += grid
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTerrariumRelationship(
    relationship: H2g2TerrariumRelationship,
    start: Offset,
    end: Offset,
) {
    val color = when (relationship.kind) {
        H2g2TerrariumRelationshipKind.Dependency -> H2g2.white.copy(alpha = .18f)
        H2g2TerrariumRelationshipKind.Spawn -> H2g2.hues[3].copy(alpha = .72f)
        H2g2TerrariumRelationshipKind.Transfer -> H2g2.hues[4].copy(alpha = .88f)
        H2g2TerrariumRelationshipKind.Confer -> H2g2.hues[1].copy(alpha = .88f)
    }
    val active = relationship.active
    drawLine(
        color = color,
        start = start,
        end = end,
        strokeWidth = if (active) 7f else 3f,
        cap = StrokeCap.Round,
        pathEffect = if (relationship.kind == H2g2TerrariumRelationshipKind.Dependency) {
            PathEffect.dashPathEffect(floatArrayOf(9f, 12f))
        } else null,
    )
    if (active) {
        drawCircle(H2g2.white.copy(alpha = .82f), radius = 5f, center = start)
        drawCircle(H2g2.white.copy(alpha = .82f), radius = 5f, center = end)
    }
}

/** One-time anchor relaxation; no continuous collision solver is needed during idle animation. */
private fun relaxTerrariumAnchors(
    input: Map<String, H2g2TerrariumPosition>,
): Map<String, H2g2TerrariumPosition> {
    if (input.size < 2) return input
    val result = input.toMutableMap()
    repeat(4) {
        val snapshot = result.toMap()
        snapshot.forEach { (id, position) ->
            var pushX = 0f
            var pushY = 0f
            snapshot.forEach inner@ { (otherId, other) ->
                if (id == otherId) return@inner
                val dx = position.x - other.x
                val dy = position.y - other.y
                val distanceSq = dx * dx + dy * dy
                val minimum = .13f
                if (distanceSq in .000001f..(minimum * minimum)) {
                    val distance = kotlin.math.sqrt(distanceSq)
                    val force = (minimum - distance) * .18f
                    pushX += dx / distance * force
                    pushY += dy / distance * force
                } else if (distanceSq <= .000001f) {
                    val hash = stableTerrariumHash("$id:$otherId")
                    pushX += if (hash and 1 == 0) .012f else -.012f
                    pushY += if (hash and 2 == 0) .009f else -.009f
                }
            }
            result[id] = H2g2TerrariumPosition(position.x + pushX, position.y + pushY).clamped()
        }
    }
    return result
}

/** Cheap deterministic Lissajous-style wander around a saved anchor. */
private fun wanderOffset(id: String, phase: Float, widthPx: Float, heightPx: Float): Offset {
    val hash = stableTerrariumHash(id)
    val phaseA = ((hash ushr 8) and 0xFF) / 255f * TERRARIUM_TAU
    val phaseB = ((hash ushr 16) and 0xFF) / 255f * TERRARIUM_TAU
    val speedA = 1f + (hash and 3) * .17f
    val speedB = 1f + ((hash ushr 2) and 3) * .13f
    val amplitudeX = widthPx * (.012f + ((hash ushr 4) and 7) * .0014f)
    val amplitudeY = heightPx * (.010f + ((hash ushr 7) and 7) * .0012f)
    val t = phase * TERRARIUM_TAU
    val pause = 0.72f + abs(sin(t * .31f + phaseB)) * .28f
    return Offset(
        x = sin(t * speedA + phaseA) * amplitudeX * pause,
        y = sin(t * speedB + phaseB) * amplitudeY * pause,
    )
}

private fun stableTerrariumHash(value: String): Int {
    var hash = 0x811C9DC5u
    value.encodeToByteArray().forEach { byte ->
        hash = hash xor byte.toUByte().toUInt()
        hash *= 0x01000193u
    }
    return hash.toInt()
}
