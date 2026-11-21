package com.hereliesaz.conveyance.h2g2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.backhandler.BackHandler
import com.hereliesaz.conveyance.compose.LocalReducedMotion
import com.hereliesaz.conveyance.h2g2.H2g2.contrastingText

/*
 * HG2Gui's containment grammar, made explicit: a thing that belongs to something else is drawn
 * INSIDE it. A pill does not open a sheet somewhere else on screen - the pill itself grows into a
 * card, keeping its own hue and its own label as the card's header, and the card's options sit
 * inside it. Picking one of those options grows that option, in turn, into a card inside the
 * first card; its siblings, which still belong to the outer card, collapse into thin rods along
 * the outer card's inner edge rather than vanishing. One continuous move, never a fade between
 * two screens - the same tile -> rod -> panel idea HG2Gui's FolderPicker and Files band
 * (ui/files/TileMorph.kt) play at two sizes, generalised here to any depth.
 *
 * The surface follows the depth, so nesting reads from shape alone: the outermost card is a
 * [H2g2Surface.recordTile], one level in is a [H2g2Surface.note], and anything deeper is a
 * [H2g2Surface.well]. A closed nest at every depth is a [H2g2Surface.capsule]. The corner radius
 * morphs continuously between the two (see [NestMorphShape]) on the same clock as the size, so a
 * pill never pops into a rectangle partway through growing.
 *
 * Back closes the innermost open nest first: each open nest registers its own BackHandler, and
 * the deepest one is composed last, so it wins.
 */

/** HG2Gui's own morph length (TileMorph.kt's TILE_MORPH_MS). */
const val NEST_MORPH_MS = 480

/** How thin a closed sibling becomes while another option in the same card is open. */
val NestRodHeight: Dp = 14.dp

private val NEST_GAP = 6.dp
private val NEST_PAD_OPEN = 14.dp
private val NEST_PAD_CLOSED_H = 18.dp
private val NEST_MIN_TOUCH = 48.dp

/** How many nests enclose the current composition point: 0 outside any open nest. */
val LocalNestDepth = compositionLocalOf { 0 }

/** The card surface a nest opens into at [depth] - the shape is how the eye reads depth. */
fun nestSurface(depth: Int): Shape = when (depth) {
    0 -> H2g2Surface.recordTile
    1 -> H2g2Surface.note
    else -> H2g2Surface.well
}

/** The open corner radius at [depth], matching [nestSurface]. */
fun nestRadius(depth: Int): Dp = when (depth) {
    0 -> 26.dp
    1 -> 20.dp
    else -> 18.dp
}

/**
 * A rounded rect whose corner radius is [progress] of the way from a capsule (half the short
 * side) to [openRadius]. Public so a caller drawing its own morphing surface uses the same curve.
 */
class NestMorphShape(private val progress: Float, private val openRadius: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val capsule = size.minDimension / 2f
        val open = with(density) { openRadius.toPx() }.coerceAtMost(capsule)
        val r = capsule + (open - capsule) * progress.coerceIn(0f, 1f)
        return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r, r)))
    }
}

/**
 * Which child of a card is open. One per card: opening a child closes its open sibling, and every
 * other sibling becomes a rod while one is open. Hoist it (or key it) to drive a card from
 * outside; [H2g2Nest] makes one for its own children.
 */
@Stable
class NestGroupState(initial: Any? = null) {
    var openKey: Any? by mutableStateOf(initial)

    fun toggle(key: Any) {
        openKey = if (openKey == key) null else key
    }

    fun close() {
        openKey = null
    }
}

/** Shared by the children of one open card - see [NestGroupState]. */
val LocalNestGroup = compositionLocalOf<NestGroupState?> { null }

/** What an open nest's content can use: its depth and its own child group. */
class NestScope internal constructor(
    columnScope: ColumnScope,
    val depth: Int,
    val group: NestGroupState,
    val hue: Color,
) : ColumnScope by columnScope

/**
 * A pill that opens into a card containing [content].
 *
 * Inside an open [H2g2Nest], call this again for each option: it registers with the enclosing
 * card's [NestGroupState] under [key], so opening it closes its open sibling and collapses the rest
 * into rods. Outside any nest, [expanded]/[onExpandedChange] drive it directly (both null = it keeps
 * its own state).
 *
 * @param label the pill's text, kept as the open card's header.
 * @param hue the pill's fill and the card's fill; text is whichever of ink/white reads on it.
 * @param key this option's identity in the enclosing card; defaults to [label].
 * @param onPick for a leaf with no [content]: what a tap does. A nest with content opens instead.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun H2g2Nest(
    label: String,
    hue: Color,
    modifier: Modifier = Modifier,
    key: Any = label,
    expanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    caption: String? = null,
    onPick: (() -> Unit)? = null,
    content: (@Composable NestScope.() -> Unit)? = null,
) {
    val parentGroup = LocalNestGroup.current
    val depth = LocalNestDepth.current
    var ownOpen by remember { mutableStateOf(false) }

    val isOpen = when {
        content == null -> false
        expanded != null -> expanded
        parentGroup != null -> parentGroup.openKey == key
        else -> ownOpen
    }
    val isRod = !isOpen && parentGroup?.openKey != null && parentGroup.openKey != key
    val setOpen: (Boolean) -> Unit = { open ->
        when {
            onExpandedChange != null -> onExpandedChange(open)
            parentGroup != null && expanded == null -> if (open) parentGroup.openKey = key else if (parentGroup.openKey == key) parentGroup.close()
            else -> ownOpen = open
        }
    }

    val reduced = LocalReducedMotion.current
    val morph = remember { Animatable(if (isOpen) 1f else 0f) }
    LaunchedEffect(isOpen, reduced) {
        val target = if (isOpen) 1f else 0f
        if (reduced) morph.snapTo(target) else morph.animateTo(target, tween(NEST_MORPH_MS, easing = H2g2Motion.Ease))
    }
    val spec = tween<Dp>(if (reduced) 0 else NEST_MORPH_MS, easing = H2g2Motion.Ease)
    val rodHeight by animateDpAsState(if (isRod) NestRodHeight else NEST_MIN_TOUCH, spec)

    val textColor = hue.contrastingText()
    val type = currentH2g2Type()
    val childGroup = remember(key) { NestGroupState() }

    if (isOpen) BackHandler(enabled = true) {
        // The innermost open child closes first; only then does this one.
        if (childGroup.openKey != null) childGroup.close() else setOpen(false)
    }

    Column(
        modifier
            .then(if (isOpen) Modifier.fillMaxWidth() else Modifier.wrapContentWidth(Alignment.Start))
            .clip(NestMorphShape(morph.value, nestRadius(depth)))
            .background(hue)
            .then(
                if (isRod) Modifier.height(rodHeight).semantics { contentDescription = label }
                else Modifier.defaultMinSize(minHeight = rodHeight)
            )
            .semantics { stateDescription = if (isOpen) "Open" else if (content != null) "Closed" else "" }
            .clickable(
                role = Role.Button,
                onClickLabel = when {
                    content == null -> label
                    isOpen -> "Close $label"
                    else -> "Open $label"
                },
            ) {
                when {
                    content != null -> setOpen(!isOpen)
                    else -> onPick?.invoke()
                }
            },
    ) {
        if (!isRod) {
            Box(
                Modifier.padding(
                    if (isOpen) PaddingValues(start = NEST_PAD_OPEN, end = NEST_PAD_OPEN, top = NEST_PAD_OPEN)
                    else PaddingValues(horizontal = NEST_PAD_CLOSED_H, vertical = 12.dp),
                ),
            ) {
                Column {
                    BasicText(label.uppercase(), style = type.capsule.copy(color = textColor), maxLines = 1)
                    if (caption != null && isOpen) {
                        BasicText(caption, style = type.endCap.copy(color = textColor), modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        if (content != null) {
            AnimatedVisibility(
                visible = isOpen,
                enter = if (reduced) fadeIn(tween(0)) else expandIn(tween(NEST_MORPH_MS, easing = H2g2Motion.Ease), expandFrom = Alignment.TopStart) + fadeIn(tween(NEST_MORPH_MS / 2, delayMillis = NEST_MORPH_MS / 3)),
                exit = if (reduced) fadeOut(tween(0)) else shrinkOut(tween(NEST_MORPH_MS, easing = H2g2Motion.Ease), shrinkTowards = Alignment.TopStart) + fadeOut(tween(NEST_MORPH_MS / 3)),
            ) {
                CompositionLocalProvider(LocalNestDepth provides depth + 1, LocalNestGroup provides childGroup) {
                    Column(
                        Modifier.fillMaxWidth().padding(NEST_PAD_OPEN),
                        verticalArrangement = Arrangement.spacedBy(NEST_GAP),
                    ) {
                        NestScope(this, depth + 1, childGroup, hue).content()
                    }
                }
            }
        }
    }
}

/**
 * A plain option inside an open [H2g2Nest] that does something instead of opening: a capsule of
 * the card's own [hue] shifted toward ink, so it reads as part of the card rather than a new
 * object. Collapses to a rod like any sibling while another option in the same card is open.
 */
@Composable
fun NestScope.Option(label: String, onPick: () -> Unit, modifier: Modifier = Modifier, key: Any = label) {
    H2g2Nest(label = label, hue = H2g2Motion.innerHue(hue, depth), modifier = modifier, key = key, onPick = onPick)
}

/** A child nest of an open card, tinted for its depth. */
@Composable
fun NestScope.Nest(
    label: String,
    modifier: Modifier = Modifier,
    key: Any = label,
    caption: String? = null,
    content: @Composable NestScope.() -> Unit,
) {
    H2g2Nest(label = label, hue = H2g2Motion.innerHue(hue, depth), modifier = modifier, key = key, caption = caption, content = content)
}

