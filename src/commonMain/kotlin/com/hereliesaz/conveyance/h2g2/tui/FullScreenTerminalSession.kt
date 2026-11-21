package com.hereliesaz.conveyance.h2g2.tui

import androidx.compose.runtime.Stable
import kotlinx.coroutines.delay

/**
 * What [H2g2FullScreenTerminalScreen], [H2g2AdaptiveTuiScreen] and [H2g2StructuredShellStatus] need
 * from a running full-screen program: a host implements this over its own PTY and terminal
 * emulator. The library never opens a process, parses escape sequences or reads a file; the
 * emulator stays the source of truth and this is the window onto it.
 *
 * **State.** The `val`s are read during composition, so back them with Compose snapshot state
 * (`mutableStateOf`), or compute them in a getter that reads a state the host bumps whenever the
 * emulator changes; the screens then redraw on their own. The same getters are also read outside
 * composition (in click handlers and by [TuiInteractionController]) and must return the *current*
 * value there.
 *
 * **Input.** The functions are called on the UI thread from taps and key presses. Everything the
 * screens send goes through [sendText] unless the host overrides the more specific members.
 *
 * Only the members without a default are required.
 */
@Stable
interface FullScreenTerminalSession {
    /** The command line that was launched. The raw surface's title, and the wrapper's when it has none. */
    val commandLine: String

    /** The program's exit status once it has ended, null while it runs. The screens call `onExit` when it turns non-null. */
    val exitCode: Int?

    /** The visible terminal screen. [TerminalGrid.Empty] until the emulator exists. */
    val grid: TerminalGrid

    /** Cursor-keys application mode (DECCKM): arrows, Home and End send `ESC O x`. */
    val cursorApplicationMode: Boolean get() = false

    /** Keypad application mode (DECKPAM): changes what the numeric keypad sends. */
    val keypadApplicationMode: Boolean get() = false

    /**
     * The shell status strip above the raw grid. The default (an unknown shell with nothing
     * known) draws no strip.
     */
    val shellPresentation: ShellPresentation get() = ShellPresentation()

    /**
     * The host's native projection of the screen, or null when there is none (no emulator yet, or
     * the host does not project). When it [TuiSnapshot.isWrappable], the full-screen terminal shows
     * it through [H2g2AdaptiveTuiScreen] instead of the raw grid, with RAW always one tap away.
     */
    val snapshot: TuiSnapshot? get() = null

    /**
     * The grid area now fits [columns] x [rows] cells of [cellWidthPx] x [cellHeightPx] pixels:
     * resize the PTY and the emulator. Called on first layout and whenever the space or the cell
     * size changes (rotation, keyboard, text size).
     */
    fun updateSize(columns: Int, rows: Int, cellWidthPx: Int, cellHeightPx: Int)

    /** Write [text] to the PTY: typed text, key escape sequences, whole command lines. */
    fun sendText(text: String)

    /** Write one typed code point (already mapped for the sticky CTRL/ALT keys). */
    fun sendCodePoint(codePoint: Int) = sendText(codePointToString(codePoint))

    /** INTR was tapped: send the interrupt byte (Ctrl+C). */
    fun sendInterrupt() = sendText(INTERRUPT)

    /** The user confirmed closing: end the program. `onExit` is called right after. */
    fun kill()

    /**
     * A menu item was tapped: move the program's selection in layer [layerIndex] to [targetIndex]
     * and activate it. The default drives the program with arrow keys through
     * [TuiInteractionController], re-reading [snapshot] after every step.
     */
    suspend fun activate(layerIndex: Int, targetIndex: Int): TuiInteractionResult =
        TuiInteractionController.activate(this, layerIndex, targetIndex)

    /** A tab was tapped: switch the program to tab [targetIndex]. Defaults to [TuiInteractionController.activateTab]. */
    suspend fun activateTab(targetIndex: Int): TuiInteractionResult =
        TuiInteractionController.activateTab(this, targetIndex)

    /**
     * A row of a region was tapped in a mouse-aware program: send a real terminal mouse
     * press/release at zero-based [row], [column]. Encoding a mouse event depends on the tracking
     * mode the program asked for, which only the emulator knows, so the default reports that the
     * tap could not be delivered (the screen then offers RAW).
     */
    fun mouseClick(row: Int, column: Int): TuiInteractionResult =
        TuiInteractionResult(false, "application is not accepting terminal mouse input")

    /** YES or NO was tapped on a confirmation prompt. Defaults to [TuiInteractionController.confirm]. */
    fun confirm(yes: Boolean) = TuiInteractionController.confirm(this, yes)

    /**
     * The branches of the Git repository at [cwd], for the status strip's branch chooser. Called
     * when the chooser opens; do the work off the UI thread. The default has no inventory.
     */
    suspend fun gitBranches(cwd: String?): List<String> = emptyList()
}

/** ETX, what Ctrl+C sends. */
private const val INTERRUPT = "\u0003"

/** Whether a native interaction reached the program; [reason] says why not, in a few words. */
data class TuiInteractionResult(val success: Boolean, val reason: String? = null)

/**
 * Drives a wrapped terminal UI while re-reading semantic state after every generated action.
 * The child application remains authoritative: if expected state cannot be observed, it stops
 * generating input and reports why, so the screen can offer RAW rather than guessing through a
 * changed screen.
 *
 * It only ever touches a [FullScreenTerminalSession] through [FullScreenTerminalSession.snapshot]
 * and [FullScreenTerminalSession.sendText]; a host that knows a better way to reach its program
 * overrides the session's own `activate` / `activateTab` / `confirm` instead.
 */
object TuiInteractionController {
    private const val ACKNOWLEDGE_MS = 45L
    private const val NAVIGATE_MS = 35L

    /** Moves the selection of layer [layerIndex] to [targetIndex], then presses Enter (or Space on a toggle). */
    suspend fun activate(session: FullScreenTerminalSession, layerIndex: Int, targetIndex: Int): TuiInteractionResult {
        val navigated = navigateTo(session, layerIndex, targetIndex)
        if (!navigated.success) return navigated
        val before = session.snapshot ?: return TuiInteractionResult(false, "terminal state unavailable")
        val item = before.layers.getOrNull(layerIndex)?.items?.getOrNull(targetIndex)
            ?: return TuiInteractionResult(false, "target disappeared")
        // An action is entered; a checkbox or radio is toggled with Space. Space has no escape
        // sequence of its own (see terminalKeySequence), so it is written as the character it is.
        if (item.control == TuiControlKind.ACTION) sendKey(session, TerminalKey.ENTER) else session.sendText(" ")
        delay(ACKNOWLEDGE_MS)
        val after = session.snapshot ?: return TuiInteractionResult(true)

        if (item.control != TuiControlKind.ACTION) {
            val updated = after.layers.getOrNull(layerIndex)?.items?.getOrNull(targetIndex)
                ?: return TuiInteractionResult(false, "control disappeared after toggle")
            if (item.checked != null && updated.checked == item.checked) {
                return TuiInteractionResult(false, "application did not acknowledge toggle")
            }
            return TuiInteractionResult(true)
        }

        if (after == before) return TuiInteractionResult(false, "application did not acknowledge selection")
        return TuiInteractionResult(true)
    }

    /** Walks the selection of layer [layerIndex] to [targetIndex] with the arrow keys, one observed step at a time. */
    suspend fun navigateTo(session: FullScreenTerminalSession, layerIndex: Int, targetIndex: Int): TuiInteractionResult {
        val snapshot = session.snapshot ?: return TuiInteractionResult(false, "terminal state unavailable")
        var layer = snapshot.layers.getOrNull(layerIndex) ?: return TuiInteractionResult(false, "menu layer disappeared")
        var current = layer.activeIndex ?: return TuiInteractionResult(false, "menu selection is not observable")
        if (targetIndex !in layer.items.indices) return TuiInteractionResult(false, "target is no longer present")

        val targetLabel = layer.items[targetIndex].label
        var guard = 0
        while (current != targetIndex && guard++ < layer.items.size + 4) {
            val direction = if (targetIndex > current) TerminalKey.DOWN else TerminalKey.UP
            sendKey(session, direction)
            delay(NAVIGATE_MS)
            val next = session.snapshot ?: return TuiInteractionResult(false, "screen became unreadable while navigating")
            val nextLayer = next.layers.getOrNull(layerIndex) ?: return TuiInteractionResult(false, "menu structure changed while navigating")
            val observed = nextLayer.activeIndex ?: return TuiInteractionResult(false, "selection stopped being observable")
            if (observed == current) return TuiInteractionResult(false, "application did not acknowledge navigation")
            if (targetIndex !in nextLayer.items.indices || nextLayer.items[targetIndex].label != targetLabel) {
                return TuiInteractionResult(false, "menu reordered while navigating")
            }
            layer = nextLayer
            current = observed
        }
        return if (current == targetIndex) TuiInteractionResult(true) else TuiInteractionResult(false, "target could not be reconciled")
    }

    /** Walks the active tab to [targetIndex] with the left/right arrow keys, one observed step at a time. */
    suspend fun activateTab(session: FullScreenTerminalSession, targetIndex: Int): TuiInteractionResult {
        val snapshot = session.snapshot ?: return TuiInteractionResult(false, "terminal state unavailable")
        var current = snapshot.tabs.indexOfFirst { it.active }.takeIf { it >= 0 }
            ?: return TuiInteractionResult(false, "active tab is not observable")
        if (targetIndex !in snapshot.tabs.indices) return TuiInteractionResult(false, "tab disappeared")
        val targetLabel = snapshot.tabs[targetIndex].label
        var guard = 0
        while (current != targetIndex && guard++ < snapshot.tabs.size + 2) {
            val key = if (targetIndex > current) TerminalKey.RIGHT else TerminalKey.LEFT
            sendKey(session, key)
            delay(NAVIGATE_MS)
            val next = session.snapshot ?: return TuiInteractionResult(false, "screen became unreadable while switching tabs")
            if (targetIndex !in next.tabs.indices || next.tabs[targetIndex].label != targetLabel) {
                return TuiInteractionResult(false, "tabs changed while navigating")
            }
            val observed = next.tabs.indexOfFirst { it.active }.takeIf { it >= 0 }
                ?: return TuiInteractionResult(false, "active tab stopped being observable")
            if (observed == current) return TuiInteractionResult(false, "application did not acknowledge tab navigation")
            current = observed
        }
        return if (current == targetIndex) TuiInteractionResult(true) else TuiInteractionResult(false, "target tab could not be reconciled")
    }

    /** Answers a yes/no prompt: `y` or `n`, then Enter. */
    fun confirm(session: FullScreenTerminalSession, yes: Boolean) {
        session.sendText(if (yes) "y" else "n")
        sendKey(session, TerminalKey.ENTER)
    }

    internal fun sendKey(session: FullScreenTerminalSession, key: TerminalKey) {
        val code = terminalKeySequence(key, TerminalKeyModifiers.NONE, session.cursorApplicationMode, session.keypadApplicationMode)
        if (code != null) session.sendText(code)
    }
}
