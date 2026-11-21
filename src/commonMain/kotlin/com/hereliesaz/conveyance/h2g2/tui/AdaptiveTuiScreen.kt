package com.hereliesaz.conveyance.h2g2.tui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hereliesaz.conveyance.h2g2.H2g2
import com.hereliesaz.conveyance.h2g2.H2g2Chip
import com.hereliesaz.conveyance.h2g2.LocalH2g2Ground
import com.hereliesaz.conveyance.h2g2.StatusKind
import com.hereliesaz.conveyance.h2g2.Text
import com.hereliesaz.conveyance.h2g2.currentH2g2Type
import com.hereliesaz.conveyance.h2g2.headingRole
import com.hereliesaz.conveyance.h2g2.liveRegionPolite
import com.hereliesaz.conveyance.h2g2.minTouchSize
import com.hereliesaz.conveyance.h2g2.onPage
import com.hereliesaz.conveyance.h2g2.screenInsets
import com.hereliesaz.conveyance.h2g2.statusColor
import kotlinx.coroutines.launch

/** Index of the red hue in [H2g2.hues] (the system's destructive/primary colour). */
private const val DESTRUCTIVE_HUE = 6

/**
 * Native projection of an already-running terminal UI.
 *
 * It renders the [TuiSnapshot] the host derived from the running program
 * ([FullScreenTerminalSession.snapshot]): a tab strip, region cards (tables, results, progress,
 * panes, diffs, logs, editors), one card per menu layer with tappable action / checkbox / radio
 * rows, and a prompt card (typed, masked, or YES/NO). Taps go back to the program through the
 * session's `activateTab`, `mouseClick`, `activate`, `confirm` and `sendText`.
 *
 * The screen is a root: it pads itself for the system bars, the display cutout and the keyboard
 * (pass [fullscreen] = the immersive-mode preference). A failed native interaction no longer
 * throws the user into the raw terminal on its own - it explains what happened and *offers* RAW,
 * which they can decline. CLOSE asks before ending the program, like the Back gesture does.
 *
 * RAW is always available: the header's RAW chip calls [onRawTerminal], and the host then shows
 * the raw grid ([H2g2FullScreenTerminalScreen] does this switch itself and is the usual way to get
 * here). When [session] has nothing wrappable this screen *is* the raw terminal.
 *
 * - [onExit]: the user confirmed CLOSE (after [FullScreenTerminalSession.kill]). Leave the screen.
 */
@Composable
fun H2g2AdaptiveTuiScreen(
    session: FullScreenTerminalSession,
    onRawTerminal: () -> Unit,
    onExit: () -> Unit,
    fullscreen: Boolean = false
) {
    val snapshot = session.snapshot
    val scope = rememberCoroutineScope()
    var interactionError by remember { mutableStateOf<String?>(null) }
    var confirmClose by remember { mutableStateOf(false) }

    if (snapshot == null || !snapshot.isWrappable) {
        H2g2FullScreenTerminalScreen(session = session, onExit = onExit, forceRaw = true, fullscreen = fullscreen)
        return
    }

    fun handle(result: TuiInteractionResult) {
        interactionError = if (result.success) null else interactionFailureMessage(result.reason)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(LocalH2g2Ground.current.page)
            .screenInsets(fullscreen)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        WrapperHeader(session, snapshot, onRawTerminal, onRequestClose = { confirmClose = true })
        interactionError?.let { message ->
            InteractionBanner(message, onUseRaw = onRawTerminal, onDismiss = { interactionError = null })
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (snapshot.tabs.isNotEmpty()) {
                TabStrip(snapshot.tabs) { target -> scope.launch { handle(session.activateTab(target)) } }
            }
            snapshot.regions.forEach { region ->
                RegionCard(
                    region = region,
                    mouseAware = snapshot.mouseAware,
                    onMouseRow = { row, column -> handle(session.mouseClick(row, column)) }
                )
            }
            snapshot.layers.forEachIndexed { index, layer ->
                MenuLayer(
                    layer = layer,
                    layerIndex = index,
                    inputOwner = snapshot.activeLayerIndex == index,
                    onChoose = { target -> scope.launch { handle(session.activate(index, target)) } }
                )
            }
            snapshot.prompt?.let { prompt -> PromptCard(session, prompt) }
            if (snapshot.mouseAware) {
                Text(
                    "Mouse-aware terminal UI detected. Contiguous semantic rows can receive native taps; RAW remains available for unmodeled gestures.",
                    color = statusColor(StatusKind.Muted), fontSize = 11.sp
                )
            }
        }
    }

    if (confirmClose) {
        CloseProgramConfirm(session, onDismiss = { confirmClose = false }, onExit = onExit)
    }
}

/**
 * Why a native tap did not take, in words, plus what the user can do about it. [reason] is the
 * controller's own explanation when it gave one.
 */
internal fun interactionFailureMessage(reason: String?): String {
    val detail = reason?.trim()?.takeIf { it.isNotEmpty() }
    val cause = if (detail != null) "That didn't reach the program ($detail)." else "That didn't reach the program."
    return "$cause The raw terminal can send the exact keystrokes."
}

/** Title + status line on top; CLOSE at the far left and RAW/INTR at the right, never adjacent. */
@Composable
private fun WrapperHeader(
    session: FullScreenTerminalSession,
    snapshot: TuiSnapshot,
    onRawTerminal: () -> Unit,
    onRequestClose: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Text(
            snapshot.title ?: session.commandLine,
            color = LocalH2g2Ground.current.onPage,
            style = currentH2g2Type().heading,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.headingRole()
        )
        Text(
            "LIVE TERMINAL INTERFACE · ${snapshot.profile.name}",
            color = statusColor(StatusKind.Muted),
            style = currentH2g2Type().eyebrow
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            H2g2Chip(
                label = "CLOSE",
                onClick = onRequestClose,
                tint = H2g2.hues[DESTRUCTIVE_HUE],
                contentDescription = "Close program"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                H2g2Chip("RAW", onRawTerminal, contentDescription = "Switch to raw terminal")
                H2g2Chip("INTR", { session.sendInterrupt() }, contentDescription = "Send interrupt")
            }
        }
    }
}

/**
 * Shown when a native interaction failed. A live region (announced without moving focus), it says
 * what happened and offers the raw terminal as a choice rather than switching to it.
 */
@Composable
private fun InteractionBanner(message: String, onUseRaw: () -> Unit, onDismiss: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(H2g2.ink.copy(alpha = .09f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .liveRegionPolite()
    ) {
        Text(message, color = LocalH2g2Ground.current.onPage, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            H2g2Chip("USE RAW TERMINAL", onUseRaw)
            H2g2Chip("DISMISS", onDismiss)
        }
    }
}

@Composable
private fun TabStrip(tabs: List<TuiTab>, onPick: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        tabs.forEachIndexed { index, tab ->
            H2g2Chip(
                label = tab.label,
                onClick = { if (!tab.active) onPick(index) },
                selected = tab.active,
                role = Role.Tab
            )
        }
    }
}

@Composable
private fun RegionCard(
    region: TuiRegion,
    mouseAware: Boolean,
    onMouseRow: (row: Int, column: Int) -> Unit
) {
    val contiguous = region.endRow - region.startRow + 1 == region.lines.size
    val muted = statusColor(StatusKind.Muted)
    Column(Modifier.fillMaxWidth().background(H2g2.ink.copy(alpha = .07f), RoundedCornerShape(20.dp)).padding(12.dp)) {
        Text(region.kind.name, color = muted, fontWeight = FontWeight.Black, style = currentH2g2Type().eyebrow)
        region.lines.forEachIndexed { index, line ->
            val firstContentColumn = line.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
            Text(
                line,
                color = LocalH2g2Ground.current.onPage,
                fontSize = 11.sp,
                modifier = if (mouseAware && contiguous) {
                    Modifier.fillMaxWidth().clickable { onMouseRow(region.startRow + index, firstContentColumn) }
                } else {
                    Modifier.fillMaxWidth()
                }
            )
        }
        region.progress?.let { progress ->
            ProgressBar(progress = progress, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}

private val PROGRESS_HEIGHT = 4.dp
private val PROGRESS_GAP = 4.dp
private const val PROGRESS_TRACK_ALPHA = .14f

/**
 * A determinate progress bar, 4dp tall with round ends: the done part in the system's red (its
 * primary), a gap, then the remaining track as an Ink wash, with a stop dot at the far end so the
 * bar's full extent is visible even at zero. Exposed to accessibility as a progress bar.
 */
@Composable
private fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val fraction = progress.coerceIn(0f, 1f)
    val fill = H2g2.hues[DESTRUCTIVE_HUE]
    val track = H2g2.ink.copy(alpha = PROGRESS_TRACK_ALPHA)
    Row(
        modifier
            .height(PROGRESS_HEIGHT)
            .semantics(mergeDescendants = true) { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (fraction > 0f) {
            Box(Modifier.weight(fraction).fillMaxHeight().background(fill, CircleShape))
        }
        if (fraction < 1f) {
            Box(
                Modifier
                    .weight(1f - fraction)
                    .fillMaxHeight()
                    .padding(start = if (fraction > 0f) PROGRESS_GAP else 0.dp)
                    .background(track, CircleShape),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(Modifier.size(PROGRESS_HEIGHT).background(fill, CircleShape))
            }
        }
    }
}

@Composable
private fun MenuLayer(layer: TuiLayer, layerIndex: Int, inputOwner: Boolean, onChoose: (Int) -> Unit) {
    val muted = statusColor(StatusKind.Muted)
    val warning = statusColor(StatusKind.Warning)
    val eyebrow = currentH2g2Type().eyebrow
    val onPage = LocalH2g2Ground.current.onPage
    Column(
        Modifier.fillMaxWidth().padding(start = (layer.depth * 12).dp)
            .background(H2g2.ink.copy(alpha = if (layer.modal) .14f else .07f), RoundedCornerShape(if (layer.modal) 20.dp else 24.dp))
            .padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                layer.heading ?: "MENU ${layerIndex + 1}",
                modifier = Modifier.weight(1f),
                color = if (inputOwner) onPage else muted,
                style = eyebrow
            )
            if (layer.modal) Text("MODAL", color = if (inputOwner) warning else muted, style = eyebrow)
            if (layer.scrollable) Text(" SCROLL", color = muted, style = eyebrow)
        }
        layer.items.forEachIndexed { index, item ->
            val active = layer.activeIndex == index
            val highlighted = active && inputOwner
            val prefix = when (item.control) {
                TuiControlKind.CHECKBOX -> if (item.checked == true) "☑ " else "☐ "
                TuiControlKind.RADIO -> if (item.checked == true) "◉ " else "○ "
                TuiControlKind.ACTION -> ""
            }
            val itemRole = when (item.control) {
                TuiControlKind.CHECKBOX -> Role.Checkbox
                TuiControlKind.RADIO -> Role.RadioButton
                TuiControlKind.ACTION -> Role.Button
            }
            val textColor = when {
                highlighted -> H2g2.yellow
                inputOwner -> onPage
                else -> muted
            }
            Box(
                Modifier.fillMaxWidth().background(if (highlighted) H2g2.ink else H2g2.ink.copy(alpha = .08f), RoundedCornerShape(18.dp))
                    .clickable(enabled = item.enabled && layer.activeIndex != null && inputOwner, role = itemRole) { onChoose(index) }
                    .semantics {
                        if (item.checked != null) stateDescription = if (item.checked == true) "checked" else "not checked"
                        if (highlighted) selected = true
                    }
                    .heightIn(min = minTouchSize())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The ballot glyphs are decoration for the eye; the state is spoken via the
                    // role and stateDescription above, so keep TalkBack from reading their names.
                    if (prefix.isNotEmpty()) {
                        Text(
                            prefix,
                            color = textColor,
                            fontSize = 14.sp,
                            modifier = Modifier.clearAndSetSemantics { }
                        )
                    }
                    Text(
                        item.label,
                        color = textColor,
                        fontWeight = if (highlighted) FontWeight.Black else FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
        if (layer.activeIndex == null) {
            Text("Selection state is not observable; use RAW for this layer.", color = muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PromptCard(session: FullScreenTerminalSession, prompt: TuiPrompt) {
    val onPage = LocalH2g2Ground.current.onPage
    if (prompt.kind == TuiPromptKind.CONFIRMATION) {
        Column(Modifier.fillMaxWidth().background(H2g2.ink.copy(alpha = .08f), RoundedCornerShape(22.dp)).padding(14.dp)) {
            Text(prompt.label, color = onPage, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Row(Modifier.align(Alignment.End).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                H2g2Chip("NO", { session.confirm(false) }, contentDescription = "Answer no")
                H2g2Chip("YES", { session.confirm(true) }, contentDescription = "Answer yes")
            }
        }
        return
    }
    var value by remember(prompt.row) { mutableStateOf("") }
    fun submit() {
        session.sendText(value)
        TuiInteractionController.sendKey(session, TerminalKey.ENTER)
        value = ""
    }
    Column(Modifier.fillMaxWidth().background(H2g2.ink.copy(alpha = .08f), RoundedCornerShape(22.dp)).padding(14.dp)) {
        Text(prompt.label, color = onPage, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        // The field says what its text and outline are, in the page's own text colour, so it reads
        // on every ground (Yellow-on-Ink defaults would vanish on the Mustard ground).
        OutlinedField(
            value = value,
            onValueChange = { value = it },
            password = prompt.kind == TuiPromptKind.PASSWORD,
            color = onPage,
            onDone = { submit() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .semantics { contentDescription = prompt.label }
        )
        H2g2Chip(
            label = "SUBMIT",
            onClick = { submit() },
            modifier = Modifier.align(Alignment.End).padding(top = 6.dp)
        )
    }
}

private val FIELD_MIN_HEIGHT = 56.dp
private val FIELD_SHAPE = RoundedCornerShape(4.dp)
private val FIELD_BORDER = 1.dp
private val FIELD_FOCUSED_BORDER = 2.dp
private const val FIELD_UNFOCUSED_BORDER_ALPHA = .6f

/**
 * A single-line outlined text field: a 56dp box with a thin outline in [color] (dimmed until it has
 * focus, heavier once it does), text and cursor in [color]. [password] masks the text and asks for
 * a password keyboard; the keyboard's Done key calls [onDone].
 */
@Suppress("LongParameterList")
@Composable
private fun OutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    password: Boolean,
    color: Color,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = currentH2g2Type().body.copy(color = color, fontSize = 16.sp),
        cursorBrush = SolidColor(color),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else KeyboardType.Text,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        interactionSource = interaction,
        modifier = modifier,
        decorationBox = { innerTextField ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = FIELD_MIN_HEIGHT)
                    .border(
                        width = if (focused) FIELD_FOCUSED_BORDER else FIELD_BORDER,
                        color = if (focused) color else color.copy(alpha = FIELD_UNFOCUSED_BORDER_ALPHA),
                        shape = FIELD_SHAPE
                    )
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                innerTextField()
            }
        }
    )
}
