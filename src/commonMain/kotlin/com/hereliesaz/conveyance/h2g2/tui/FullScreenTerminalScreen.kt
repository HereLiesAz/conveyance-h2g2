package com.hereliesaz.conveyance.h2g2.tui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle as ComposeTextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hereliesaz.conveyance.h2g2.H2g2
import com.hereliesaz.conveyance.h2g2.H2g2Chip
import com.hereliesaz.conveyance.h2g2.LocalH2g2Ground
import com.hereliesaz.conveyance.h2g2.LocalH2g2UiScale
import com.hereliesaz.conveyance.h2g2.Text
import com.hereliesaz.conveyance.h2g2.azrienochMonoFontFamily
import com.hereliesaz.conveyance.h2g2.components.H2g2ConfirmDialog
import com.hereliesaz.conveyance.h2g2.onPage
import com.hereliesaz.conveyance.h2g2.screenInsets
import kotlin.math.max
import kotlin.math.min

private val CELL_FONT_SIZE = 14.sp
private const val CURSOR_ALPHA = 0.5f

/** Below this window height (dp) - a landscape phone - the symbol keys share the first row. */
private const val COMPACT_HEIGHT_DP = 480

/** Keeps the screen-reader description of the grid to a size TalkBack can actually read out. */
private const val MAX_DESCRIBED_LINES = 60

private data class ExtraKey(val label: String, val key: TerminalKey, val description: String? = null)

/** A key that types a literal character (shell punctuation that soft keyboards bury). */
private data class SymbolKey(val label: String, val text: String, val description: String)

private val EXTRA_KEYS = listOf(
    ExtraKey("ESC", TerminalKey.ESCAPE, "Escape"),
    ExtraKey("TAB", TerminalKey.TAB, "Tab"),
    ExtraKey("←", TerminalKey.LEFT, "Left arrow"),
    ExtraKey("↑", TerminalKey.UP, "Up arrow"),
    ExtraKey("↓", TerminalKey.DOWN, "Down arrow"),
    ExtraKey("→", TerminalKey.RIGHT, "Right arrow"),
    ExtraKey("HOME", TerminalKey.HOME),
    ExtraKey("END", TerminalKey.END),
    ExtraKey("PGUP", TerminalKey.PAGE_UP, "Page up"),
    ExtraKey("PGDN", TerminalKey.PAGE_DOWN, "Page down"),
    ExtraKey("⌫", TerminalKey.BACKSPACE, "Backspace"),
    ExtraKey("⏎", TerminalKey.ENTER, "Enter")
)

private val SYMBOL_KEYS = listOf(
    SymbolKey("/", "/", "Slash"),
    SymbolKey("|", "|", "Pipe"),
    SymbolKey("~", "~", "Tilde"),
    SymbolKey("-", "-", "Dash"),
    SymbolKey("_", "_", "Underscore"),
    SymbolKey("\\", "\\", "Backslash")
)

/**
 * Full-screen PTY surface. When the live alternate-screen buffer can be modeled confidently as
 * a structured terminal UI, the same entry point dynamically projects it through
 * [H2g2AdaptiveTuiScreen]. Otherwise it renders the raw terminal grid.
 *
 * Both surfaces are screen roots: they pad themselves for the system bars, the display cutout and
 * the keyboard (see `screenInsets`), so the PTY grid is sized from the space that is actually
 * visible. Pass [fullscreen] = the user's immersive-mode preference so hidden bars are not padded.
 *
 * RAW is not a dead end: when the program is also wrappable, the RAW surface offers a "native view"
 * control that clears the override again.
 *
 * The host supplies the terminal through [session] (see [FullScreenTerminalSession]): the grid to
 * draw, the projection to wrap, and where typed text, key sequences, resizes and the close request
 * go. The grid is set in the Mono cut of Azrienoch and drawn cell by cell; the top bar carries
 * CLOSE (which asks first), the command line, NATIVE when a projection is available, and INTR; the
 * key rows under the grid are the keys a soft keyboard lacks, with sticky CTRL and ALT.
 *
 * - [onExit]: the program ended, or the user confirmed CLOSE (after
 *   [FullScreenTerminalSession.kill]). Leave the screen.
 * - [forceRaw]: start on the raw grid even when the program is wrappable.
 */
@Composable
fun H2g2FullScreenTerminalScreen(
    session: FullScreenTerminalSession,
    onExit: () -> Unit,
    forceRaw: Boolean = false,
    fullscreen: Boolean = false
) {
    var rawOverride by remember(session) { mutableStateOf(forceRaw) }
    // Registered before choosing a surface: with the effect below the old early return, a session
    // that exited while the adaptive wrapper was showing never reported its exit.
    val exitCode = session.exitCode
    LaunchedEffect(exitCode) { if (exitCode != null) onExit() }

    // Evaluated in RAW too (when overridden): it decides whether "native view" can be offered.
    val wrappable = session.snapshot?.isWrappable == true
    if (wrappable && !rawOverride) {
        H2g2AdaptiveTuiScreen(
            session = session,
            onRawTerminal = { rawOverride = true },
            onExit = onExit,
            fullscreen = fullscreen
        )
    } else {
        RawTerminalSurface(
            session = session,
            fullscreen = fullscreen,
            onNativeView = if (wrappable) ({ rawOverride = false }) else null,
            onExit = onExit
        )
    }
}

/**
 * The "Closing now ends the running program." confirmation, shared by the RAW and wrapped surfaces
 * and worded exactly like a host's Back-gesture confirmation should be, so the visible kill
 * buttons are never a quieter way to lose an unsaved editor or REPL session than Back.
 */
@Composable
internal fun CloseProgramConfirm(session: FullScreenTerminalSession, onDismiss: () -> Unit, onExit: () -> Unit) {
    H2g2ConfirmDialog(
        title = "CLOSE ${session.commandLine.take(MAX_TITLE_COMMAND_CHARS).uppercase()}?",
        message = "Closing now ends the running program.",
        confirmLabel = "CLOSE",
        onConfirm = {
            onDismiss()
            session.kill()
            onExit()
        },
        onDismiss = onDismiss
    )
}

private const val MAX_TITLE_COMMAND_CHARS = 40

@Composable
private fun RawTerminalSurface(
    session: FullScreenTerminalSession,
    fullscreen: Boolean,
    onNativeView: (() -> Unit)?,
    onExit: () -> Unit
) {
    val measurer = rememberTextMeasurer()
    val focusRequester = remember { FocusRequester() }
    var ctrlArmed by remember { mutableStateOf(false) }
    var altArmed by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    var columns by remember { mutableStateOf(0) }
    var rows by remember { mutableStateOf(0) }

    // Keyed on everything that changes a glyph's pixel size: the Text-size setting and the system
    // font scale are both applied in place (the host handles those config changes itself), so
    // an unkeyed measurement would keep positioning the grid with the old cell.
    val density = LocalDensity.current
    val uiScale = LocalH2g2UiScale.current
    val family = azrienochMonoFontFamily()
    // The Mono cut of Azrienoch has one width for every ASCII glyph, so the cell is that width. Each cluster
    // is still drawn on its own and scaled to its cell (see GlyphCache), which also fits glyphs the font
    // lacks and the system draws instead. The digit zero sets the width.
    val cellMetrics = remember(measurer, family, density.density, density.fontScale, uiScale) {
        measurer.measure("0", ComposeTextStyle(fontFamily = family, fontSize = CELL_FONT_SIZE))
    }
    val glyphs = remember(measurer, family, density.density, density.fontScale, uiScale) {
        GlyphCache(measurer, family)
    }
    val cellWidthPx = cellMetrics.size.width.coerceAtLeast(1)
    val cellHeightPx = cellMetrics.size.height.coerceAtLeast(1)

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val ground = LocalH2g2Ground.current
    // The chrome (top bar, key rows) sits on the page ground so the system-bar icons - which follow
    // the ground - stay legible; only the terminal itself is Ink. Insets are applied to this root,
    // so the grid below is measured *after* the bars and the keyboard have taken their share and
    // the PTY is never told it has rows that are hidden behind them.
    Column(
        Modifier
            .fillMaxSize()
            .background(ground.page)
            .screenInsets(fullscreen)
    ) {
        TerminalTopBar(session, onNativeView, onRequestClose = { confirmClose = true })
        Column(Modifier.weight(1f).fillMaxWidth().background(H2g2.ink)) {
            H2g2StructuredShellStatus(session)
            TerminalGridSurface(
                session = session,
                glyphs = glyphs,
                focusRequester = focusRequester,
                ctrlArmed = ctrlArmed,
                altArmed = altArmed,
                onModifiersConsumed = {
                    ctrlArmed = false
                    altArmed = false
                },
                cellWidthPx = cellWidthPx,
                cellHeightPx = cellHeightPx,
                columns = columns,
                rows = rows,
                onGridSized = { c, r ->
                    columns = c
                    rows = r
                    session.updateSize(c, r, cellWidthPx, cellHeightPx)
                },
                onNativeView = onNativeView,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
        TerminalExtraKeys(
            session = session,
            ctrlArmed = ctrlArmed,
            altArmed = altArmed,
            onCtrlToggle = { ctrlArmed = !ctrlArmed },
            onAltToggle = { altArmed = !altArmed },
            onModifiersConsumed = {
                ctrlArmed = false
                altArmed = false
            }
        )
    }

    if (confirmClose) {
        CloseProgramConfirm(session, onDismiss = { confirmClose = false }, onExit = onExit)
    }
}

/**
 * CLOSE sits at the far left and INTR at the right, with the command name between them, so the
 * destructive key is never the neighbour of the one used mid-program. Every control has a 48dp hit
 * box (see `H2g2Chip`).
 */
@Composable
private fun TerminalTopBar(
    session: FullScreenTerminalSession,
    onNativeView: (() -> Unit)?,
    onRequestClose: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        H2g2Chip(
            label = "CLOSE",
            onClick = onRequestClose,
            tint = H2g2.hues[CLOSE_HUE],
            contentDescription = "Close program"
        )
        Text(
            session.commandLine,
            color = LocalH2g2Ground.current.onPage,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        if (onNativeView != null) {
            H2g2Chip(
                label = "NATIVE",
                onClick = onNativeView,
                contentDescription = "Switch to native view"
            )
        }
        H2g2Chip(
            label = "INTR",
            onClick = { session.sendInterrupt() },
            contentDescription = "Send interrupt"
        )
    }
}

/** Index of the red hue in [H2g2.hues] (the system's destructive/primary colour). */
private const val CLOSE_HUE = 6

@Suppress("LongParameterList")
@Composable
private fun TerminalGridSurface(
    session: FullScreenTerminalSession,
    glyphs: GlyphCache,
    focusRequester: FocusRequester,
    ctrlArmed: Boolean,
    altArmed: Boolean,
    onModifiersConsumed: () -> Unit,
    cellWidthPx: Int,
    cellHeightPx: Int,
    columns: Int,
    rows: Int,
    onGridSized: (Int, Int) -> Unit,
    onNativeView: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var gridPx by remember { mutableStateOf(IntSize.Zero) }
    val currentOnGridSized by rememberUpdatedState(onGridSized)
    // The grid is re-derived when its pixel size changes (rotation, keyboard open/close) *and*
    // when the cell size does (Text-size or font-scale change) - the latter changes no layout size.
    LaunchedEffect(gridPx, cellWidthPx, cellHeightPx) {
        if (gridPx == IntSize.Zero) return@LaunchedEffect
        val newColumns = max(1, gridPx.width / cellWidthPx)
        val newRows = max(1, gridPx.height / cellHeightPx)
        if (newColumns != columns || newRows != rows) currentOnGridSized(newColumns, newRows)
    }
    Box(
        modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Show keyboard"
            ) { focusRequester.requestFocus() }
            .onSizeChanged { size -> gridPx = size }
    ) {
        // Read here, in composition, so a new screen from the host redraws the canvas.
        val grid = session.grid
        // The grid is drawn, not composed, so a screen reader would find nothing here. The
        // description (rebuilt only when accessibility asks for it) is the visible text; the
        // actions are the two things a non-visual user needs most.
        Canvas(
            Modifier.fillMaxSize().semantics {
                contentDescription = terminalScreenDescription(visibleTerminalLines(grid))
                customActions = buildList {
                    if (onNativeView != null) {
                        add(CustomAccessibilityAction("Switch to native view") { onNativeView(); true })
                    }
                    add(CustomAccessibilityAction("Send interrupt") { session.sendInterrupt(); true })
                }
            }
        ) {
            drawTerminal(grid, GridMetrics(columns, rows, cellWidthPx.toFloat(), cellHeightPx.toFloat()), glyphs)
        }

        BasicTextField(
            value = input,
            onValueChange = { new ->
                val added = new.text.removePrefix(input.text)
                sendTypedText(session, added, ctrlArmed, altArmed, onModifiersConsumed)
                input = TextFieldValue("")
            },
            textStyle = ComposeTextStyle(color = Color.Transparent),
            modifier = Modifier
                .size(1.dp)
                .alpha(0f)
                .focusRequester(focusRequester)
                .semantics { contentDescription = "Terminal keyboard input" }
                .onPreviewKeyEvent { event ->
                    handleSpecialKey(session, event, session.cursorApplicationMode, session.keypadApplicationMode)
                }
        )
    }
}

/**
 * The extra keys a soft keyboard lacks: modifiers (CTRL, ALT - sticky, armed for the next key),
 * navigation keys and shell punctuation. In a landscape phone the punctuation joins the first row
 * so the key area stays one row high.
 */
@Composable
private fun TerminalExtraKeys(
    session: FullScreenTerminalSession,
    ctrlArmed: Boolean,
    altArmed: Boolean,
    onCtrlToggle: () -> Unit,
    onAltToggle: () -> Unit,
    onModifiersConsumed: () -> Unit
) {
    val windowHeightDp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val compact = windowHeightDp < COMPACT_HEIGHT_DP.dp
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(KEY_SPACING),
            verticalAlignment = Alignment.CenterVertically
        ) {
            H2g2Chip("CTRL", onCtrlToggle, selected = ctrlArmed, contentDescription = "Control modifier")
            H2g2Chip("ALT", onAltToggle, selected = altArmed, contentDescription = "Alt modifier")
            for (key in EXTRA_KEYS) {
                H2g2Chip(
                    label = key.label,
                    onClick = {
                        sendExtraKey(session, key, ctrlArmed, altArmed)
                        if (ctrlArmed || altArmed) onModifiersConsumed()
                    },
                    contentDescription = key.description
                )
            }
            if (compact) SymbolKeys(session, ctrlArmed, altArmed, onModifiersConsumed)
        }
        if (!compact) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(KEY_SPACING),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SymbolKeys(session, ctrlArmed, altArmed, onModifiersConsumed)
            }
        }
    }
}

private val KEY_SPACING = 2.dp

@Composable
private fun SymbolKeys(
    session: FullScreenTerminalSession,
    ctrlArmed: Boolean,
    altArmed: Boolean,
    onModifiersConsumed: () -> Unit
) {
    for (key in SYMBOL_KEYS) {
        H2g2Chip(
            label = key.label,
            onClick = { sendTypedText(session, key.text, ctrlArmed, altArmed, onModifiersConsumed) },
            contentDescription = key.description
        )
    }
}

private fun sendExtraKey(session: FullScreenTerminalSession, key: ExtraKey, ctrl: Boolean, alt: Boolean) {
    var mods = 0
    if (ctrl) mods = mods or TerminalKeyModifiers.CTRL
    if (alt) mods = mods or TerminalKeyModifiers.ALT
    // A modified code the terminfo has no entry for (Ctrl+Enter, say) falls back to the plain key
    // rather than swallowing the tap.
    val code = terminalKeySequence(key.key, mods, session.cursorApplicationMode, session.keypadApplicationMode)
        ?: terminalKeySequence(key.key, 0, session.cursorApplicationMode, session.keypadApplicationMode)
    if (code != null) session.sendText(code)
}

private fun sendTypedText(
    session: FullScreenTerminalSession,
    added: String,
    ctrlArmed: Boolean,
    altArmed: Boolean,
    onModifiersConsumed: () -> Unit
) {
    var i = 0
    while (i < added.length) {
        val codePoint = codePointAt(added, i)
        for (point in terminalCodePoints(codePoint, ctrlArmed, altArmed)) session.sendCodePoint(point)
        i += charCount(codePoint)
    }
    if (added.isNotEmpty() && (ctrlArmed || altArmed)) onModifiersConsumed()
}

/** The non-blank visible rows of the terminal screen, top to bottom, with trailing blanks trimmed. */
private fun visibleTerminalLines(grid: TerminalGrid): List<String> =
    grid.lines.mapNotNull { line -> line.text.trimEnd().takeIf { it.isNotEmpty() } }

/** Spoken description of the drawn grid: the visible lines, capped to the last [MAX_DESCRIBED_LINES]. */
internal fun terminalScreenDescription(lines: List<String>): String =
    if (lines.isEmpty()) {
        "Terminal. The screen is empty."
    } else {
        "Terminal. " + lines.takeLast(MAX_DESCRIBED_LINES).joinToString("\n")
    }

private fun handleSpecialKey(session: FullScreenTerminalSession, event: KeyEvent, cursorApp: Boolean, keypadApp: Boolean): Boolean {
    val code = if (event.type == KeyEventType.KeyDown) {
        specialKeySequence(event.key, event.isShiftPressed, event.isCtrlPressed, event.isAltPressed, cursorApp, keypadApp)
    } else {
        null
    }
    if (code == null) return false
    session.sendText(code)
    return true
}

private class GridMetrics(val columns: Int, val rows: Int, val cellWidth: Float, val cellHeight: Float)

private class RenderContext(
    val metrics: GridMetrics,
    val screenRows: Int,
    val screenCols: Int,
    val defaultBg: Color,
    val glyphs: GlyphCache
)

private fun DrawScope.drawTerminal(grid: TerminalGrid, metrics: GridMetrics, glyphs: GlyphCache) {
    // No screen yet: the host's emulator has not started.
    if (grid.lines.isEmpty() && grid.columns == 0) return
    val ctx = RenderContext(
        metrics = metrics,
        screenRows = min(metrics.rows, grid.rows),
        screenCols = min(metrics.columns, grid.columns),
        defaultBg = grid.defaultBackground,
        glyphs = glyphs
    )

    drawRect(ctx.defaultBg, size = Size(metrics.columns * metrics.cellWidth, metrics.rows * metrics.cellHeight))
    for (row in 0 until ctx.screenRows) {
        drawRow(grid.lines.getOrNull(row), row, ctx)
    }
    drawCursor(grid, ctx)
}

/** Paints one row as runs of consecutive cells that share a style. */
private fun DrawScope.drawRow(line: TerminalLine?, row: Int, ctx: RenderContext) {
    val cells = line?.cells ?: return
    var index = 0
    var col = 0
    while (index < cells.size && col < ctx.screenCols) {
        val style = cells[index].style
        var runEnd = index
        var endCol = col
        while (runEnd < cells.size && endCol < ctx.screenCols && cells[runEnd].style == style) {
            endCol += cells[runEnd].width.coerceIn(1, 2)
            runEnd++
        }
        drawRun(cells, RowSpan(row, col, min(endCol, ctx.screenCols), index, runEnd), style, ctx)
        index = runEnd
        col = endCol
    }
}

/** Columns [col] until [end] of [row], holding cells [firstCell] until [lastCell]. */
private class RowSpan(val row: Int, val col: Int, val end: Int, val firstCell: Int, val lastCell: Int)

private fun DrawScope.drawRun(cells: List<TerminalCell>, span: RowSpan, style: TerminalCellStyle, ctx: RenderContext) {
    var fg = style.foreground
    var bg = style.background
    if (style.inverse) { val swap = fg; fg = bg; bg = swap }

    val x = span.col * ctx.metrics.cellWidth
    val y = span.row * ctx.metrics.cellHeight
    val runWidth = (span.end - span.col) * ctx.metrics.cellWidth
    if (bg != ctx.defaultBg) {
        drawRect(bg, topLeft = Offset(x, y), size = Size(runWidth, ctx.metrics.cellHeight))
    }

    drawGlyphs(cells, span, fg, style.bold, ctx)
    if (style.underline) {
        drawLine(fg, Offset(x, y + ctx.metrics.cellHeight - 1), Offset(x + runWidth, y + ctx.metrics.cellHeight - 1))
    }
}

/**
 * Draws the run one grid cluster at a time, each stretched horizontally to its cell width (two cells
 * for a wide character). This is what makes glyphs the font lacks, which the system draws in some
 * other face, still fit a terminal grid. Each [TerminalCell] is one cluster: a base character with
 * its zero-width combiners, the second cell of a wide character omitted.
 */
private fun DrawScope.drawGlyphs(cells: List<TerminalCell>, span: RowSpan, color: Color, bold: Boolean, ctx: RenderContext) {
    val cellWidth = ctx.metrics.cellWidth
    val y = span.row * ctx.metrics.cellHeight
    var col = span.col
    for (index in span.firstCell until span.lastCell) {
        val cell = cells[index]
        val width = cell.width.coerceIn(1, 2)
        if (cell.text.isNotEmpty() && cell.text != " ") {
            val layout = ctx.glyphs.layout(cell.text, bold)
            val natural = layout.size.width.coerceAtLeast(1)
            val x = col * cellWidth
            withTransform({ scale(width * cellWidth / natural, 1f, pivot = Offset(x, y)) }) {
                drawText(layout, color = color, topLeft = Offset(x, y))
            }
        }
        col += width
    }
}

/**
 * Measured layouts of single grid clusters, kept for the life of the screen (the TextMeasurer's own
 * cache holds eight entries, far too few for a full grid redrawn every frame). Keyed by the
 * cluster and its weight; colour is applied at draw time so it does not multiply the entries.
 * Rebuilt by the caller whenever the font size, density or font scale changes.
 */
private class GlyphCache(private val measurer: TextMeasurer, private val family: FontFamily) {
    private val layouts = HashMap<String, TextLayoutResult>()

    fun layout(cluster: String, bold: Boolean): TextLayoutResult =
        layouts.getOrPut(if (bold) "$cluster\u0000b" else cluster) {
            measurer.measure(
                cluster,
                ComposeTextStyle(
                    fontFamily = family,
                    fontSize = CELL_FONT_SIZE,
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
                )
            )
        }
}

private fun DrawScope.drawCursor(grid: TerminalGrid, ctx: RenderContext) {
    val cursorRow = grid.cursorRow
    val cursorCol = grid.cursorColumn
    if (grid.cursorVisible && cursorRow in 0 until ctx.screenRows && cursorCol in 0 until ctx.screenCols) {
        drawRect(
            grid.cursorColor.copy(alpha = CURSOR_ALPHA),
            topLeft = Offset(cursorCol * ctx.metrics.cellWidth, cursorRow * ctx.metrics.cellHeight),
            size = Size(ctx.metrics.cellWidth, ctx.metrics.cellHeight)
        )
    }
}
