package com.hereliesaz.conveyance.h2g2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hereliesaz.conveyance.h2g2.H2g2.contrastingText
import kotlinx.coroutines.delay

/** Presentation-only state for a subject in an h2g2 workflow map. */
enum class H2g2WorkflowState {
    Pending,
    Ready,
    Active,
    Gate,
    Blocked,
    Complete,
    Failed,
}

/**
 * A generic subject in a process/mind-map. The host owns semantics; this library only renders the
 * route. [hueSeed] is identity, never state or rank.
 */
data class H2g2WorkflowNode(
    val id: String,
    val label: String,
    val subtitle: String? = null,
    val hueSeed: String = id,
    val state: H2g2WorkflowState = H2g2WorkflowState.Pending,
    val injected: Boolean = false,
    val detail: String? = null,
)

data class H2g2WorkflowEdge(
    val from: String,
    val to: String,
    val label: String? = null,
)

/** A topological rank. Multiple nodes in one band are rendered as a visible fork. */
data class H2g2WorkflowBand(
    val nodes: List<H2g2WorkflowNode>,
)

private val WorkflowEase = CubicBezierEasing(0f, .9f, .1f, 1f)
private val BandHeight = 122.dp
private val NodeInset = 10.dp

/**
 * A flat vector workflow/mind-map for h2g2 applications.
 *
 * The default view is intentionally not a stack of record tiles. Subjects float as identity-hued
 * vector lozenges on the Ground and are connected by thick hand-drawn-feeling cubic routes.
 * Forks, joins, gates and interruptions are spatial. Selecting a subject transforms that same
 * subject in place and reveals its detail beneath it; no replacement inspector is required.
 *
 * Feed this component topological [bands] plus explicit [edges]. That keeps the primitive generic
 * enough for build pipelines, approval processes, dependency maps, story maps, or any other DAG.
 */
@Composable
fun H2g2WorkflowMap(
    bands: List<H2g2WorkflowBand>,
    edges: List<H2g2WorkflowEdge>,
    selectedId: String? = null,
    onNodeSelected: (H2g2WorkflowNode) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (bands.isEmpty()) return

    val routeProgress = remember(bands, edges) { Animatable(0f) }
    LaunchedEffect(bands, edges) {
        routeProgress.snapTo(0f)
        routeProgress.animateTo(1f, tween(520, easing = WorkflowEase))
    }

    val nodeLocations = remember(bands) {
        buildMap {
            bands.forEachIndexed { bandIndex, band ->
                band.nodes.forEachIndexed { nodeIndex, node ->
                    put(node.id, Triple(bandIndex, nodeIndex, band.nodes.size))
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(BandHeight * bands.size),
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val density = androidx.compose.ui.platform.LocalDensity.current
        val bandHeightPx = with(density) { BandHeight.toPx() }

        Canvas(Modifier.fillMaxSize()) {
            edges.forEachIndexed { edgeIndex, edge ->
                val from = nodeLocations[edge.from] ?: return@forEachIndexed
                val to = nodeLocations[edge.to] ?: return@forEachIndexed
                val fromX = widthPx * ((from.second + 0.5f) / from.third)
                val toX = widthPx * ((to.second + 0.5f) / to.third)
                val fromY = bandHeightPx * (from.first + 0.58f)
                val toY = bandHeightPx * (to.first + 0.34f)
                val midY = (fromY + toY) / 2f
                val path = Path().apply {
                    moveTo(fromX, fromY)
                    cubicTo(fromX, midY, toX, midY, toX, toY)
                }
                val source = bands[from.first].nodes[from.second]
                val routeColor = H2g2.caps[H2g2.indexOf(source.hueSeed)]
                val stagger = (edgeIndex * 0.06f).coerceAtMost(.42f)
                val p = ((routeProgress.value - stagger) / (1f - stagger)).coerceIn(0f, 1f)
                if (p > 0f) {
                    drawPath(
                        path = path,
                        color = routeColor,
                        style = Stroke(width = 7.dp.toPx() * p, cap = StrokeCap.Round),
                    )
                    drawCircle(
                        color = routeColor,
                        radius = 4.dp.toPx() * p,
                        center = androidx.compose.ui.geometry.Offset(toX, toY),
                    )
                }
            }
        }

        Column(Modifier.fillMaxSize()) {
            bands.forEachIndexed { bandIndex, band ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(BandHeight),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    band.nodes.forEachIndexed { nodeIndex, node ->
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            H2g2WorkflowSubject(
                                node = node,
                                selected = node.id == selectedId,
                                arrivalDelayMs = 70L * bandIndex + 34L * nodeIndex,
                                onClick = { onNodeSelected(node) },
                                modifier = Modifier.padding(horizontal = NodeInset),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun H2g2WorkflowSubject(
    node: H2g2WorkflowNode,
    selected: Boolean,
    arrivalDelayMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hueIndex = H2g2.indexOf(node.hueSeed)
    val hue = H2g2.hues[hueIndex]
    val cap = H2g2.caps[hueIndex]
    val text = hue.contrastingText()
    val type = h2g2Type()
    val arrival = remember(node.id) { Animatable(0f) }
    val activeScale by animateFloatAsState(
        targetValue = when {
            selected -> 1.12f
            node.state == H2g2WorkflowState.Active -> 1.06f
            else -> 1f
        },
        animationSpec = tween(240, easing = WorkflowEase),
        label = "h2g2-workflow-subject-scale",
    )

    LaunchedEffect(node.id) {
        delay(arrivalDelayMs)
        arrival.animateTo(1f, tween(320, easing = WorkflowEase))
    }

    Column(
        modifier = modifier
            .widthIn(min = 96.dp, max = 240.dp)
            .graphicsLayer {
                alpha = arrival.value
                translationY = (1f - arrival.value) * 42f
                rotationZ = (1f - arrival.value) * if (hueIndex % 2 == 0) -4f else 4f
                scaleX = activeScale
                scaleY = activeScale
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val selectedBackground = if (selected) H2g2.ink else hue
        val selectedText = if (selected) H2g2.hues[4] else text
        val radius = when (node.state) {
            H2g2WorkflowState.Gate -> 8.dp
            H2g2WorkflowState.Failed -> 16.dp
            else -> 999.dp
        }

        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(radius))
                .background(selectedBackground)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                text = node.label.uppercase(),
                style = type.capsule.copy(color = selectedText, textAlign = TextAlign.Center),
            )
            if (node.subtitle != null) {
                BasicText(
                    text = node.subtitle,
                    style = type.endCap.copy(color = selectedText, textAlign = TextAlign.Center),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(H2g2Surface.capsule)
                        .background(if (selected) H2g2.hues[4] else cap)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    BasicText(
                        text = node.state.name.uppercase(),
                        style = type.endCap.copy(color = if (selected) H2g2.ink else cap.contrastingText()),
                    )
                }
                if (node.injected) {
                    BasicText(
                        text = "AUTO",
                        style = type.endCap.copy(color = selectedText),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = selected && node.detail != null,
            enter = expandVertically(tween(220, easing = WorkflowEase)) + fadeIn(tween(120)),
            exit = shrinkVertically(tween(190, easing = WorkflowEase)) + fadeOut(tween(90)),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 7.dp)
                    .clip(H2g2Surface.note)
                    .background(H2g2.ink)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                BasicText(
                    text = node.detail.orEmpty(),
                    style = type.body.copy(color = H2g2.white, textAlign = TextAlign.Center),
                )
            }
        }
    }
}
