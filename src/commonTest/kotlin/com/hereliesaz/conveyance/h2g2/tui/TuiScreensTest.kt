package com.hereliesaz.conveyance.h2g2.tui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.hereliesaz.conveyance.h2g2.H2g2Theme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Smoke tests for the three public composables of the package: each is composed with a small fake
 * session and checked for what it shows and for what it sends back when its controls are tapped.
 */
@OptIn(ExperimentalTestApi::class)
class TuiScreensTest {

    /** A host's session reduced to state the tests can set and a log of what the screens sent. */
    private class FakeSession(
        snapshot: TuiSnapshot? = null,
        grid: TerminalGrid = TerminalGrid.ofText(listOf("hello world", "$ "), cursorRow = 1, cursorColumn = 2),
        override val cursorApplicationMode: Boolean = false,
        presentation: ShellPresentation = ShellPresentation(),
        private val branches: List<String> = emptyList(),
        /** Called for every write, so a test can make the fake program react to keys. */
        private val onWrite: FakeSession.(String) -> Unit = {}
    ) : FullScreenTerminalSession {
        override val commandLine = "vim notes.txt"
        override var exitCode: Int? by mutableStateOf(null)
        override var grid by mutableStateOf(grid)
        override var snapshot by mutableStateOf(snapshot)
        override var shellPresentation by mutableStateOf(presentation)

        val sent = mutableListOf<String>()
        var size: List<Int>? = null
        var killed = false
        var mouse: Pair<Int, Int>? = null

        override fun updateSize(columns: Int, rows: Int, cellWidthPx: Int, cellHeightPx: Int) {
            size = listOf(columns, rows, cellWidthPx, cellHeightPx)
        }

        override fun sendText(text: String) {
            sent += text
            onWrite(text)
        }

        override fun kill() {
            killed = true
        }

        override fun mouseClick(row: Int, column: Int): TuiInteractionResult {
            mouse = row to column
            return TuiInteractionResult(true)
        }

        override suspend fun gitBranches(cwd: String?): List<String> = branches
    }

    private val menu = TuiLayer(
        heading = "Pick a file",
        items = listOf(
            TuiMenuItem(2, "First", selected = true),
            TuiMenuItem(3, "Second", selected = false),
            TuiMenuItem(4, "Third", selected = false)
        ),
        activeIndex = 0
    )

    private fun wrappable(
        layers: List<TuiLayer> = listOf(menu),
        prompt: TuiPrompt? = null,
        regions: List<TuiRegion> = emptyList(),
        tabs: List<TuiTab> = emptyList(),
        mouseAware: Boolean = false,
        title: String? = "File chooser"
    ) = TuiSnapshot(title, layers, prompt, regions, tabs, alternateScreen = true, mouseAware = mouseAware)

    private companion object {
        const val WAIT_MS = 5_000L
    }

    private val isProgressBar = SemanticsMatcher("is a progress bar") {
        it.config.getOrElseNullable(SemanticsProperties.ProgressBarRangeInfo) { null } != null
    }

    // ----- H2g2FullScreenTerminalScreen: the raw surface -----------------------------------------

    @Test
    fun rawSurfaceShowsTheCommandTheGridAndSizesThePty() = runComposeUiTest {
        val session = FakeSession()
        setContent { H2g2Theme { H2g2FullScreenTerminalScreen(session, onExit = {}) } }

        onNodeWithText("vim notes.txt").assertExists()
        // The grid is drawn, so its text is only reachable as the canvas description.
        onNodeWithContentDescription("Terminal. hello world\n$").assertExists()
        onNodeWithContentDescription("Terminal keyboard input").assertExists()
        // Nothing to wrap: no way to a native view is offered.
        onAllNodesWithContentDescription("Switch to native view").assertCountEquals(0)

        waitForIdle()
        val size = assertNotNull(session.size, "the PTY was never told its size")
        assertTrue(size.all { it > 0 }, "columns, rows and cell size must all be positive: $size")
    }

    @Test
    fun keyRowKeysSendTheirEscapeSequences() = runComposeUiTest {
        val session = FakeSession()
        setContent { H2g2Theme { H2g2FullScreenTerminalScreen(session, onExit = {}) } }

        onNodeWithContentDescription("Tab").performClick()
        onNodeWithContentDescription("Escape").performClick()
        onNodeWithContentDescription("Up arrow").performClick()
        onNodeWithContentDescription("Backspace").performClick()
        onNodeWithContentDescription("Enter").performClick()
        onNodeWithContentDescription("Slash").performClick()
        onNodeWithContentDescription("Send interrupt").performClick()

        assertEquals(listOf("\t", "\u001B", "\u001B[A", "\u007F", "\r", "/", "\u0003"), session.sent)
    }

    @Test
    fun arrowsFollowCursorApplicationMode() = runComposeUiTest {
        val session = FakeSession(cursorApplicationMode = true)
        setContent { H2g2Theme { H2g2FullScreenTerminalScreen(session, onExit = {}) } }

        onNodeWithContentDescription("Up arrow").performClick()
        assertEquals(listOf("\u001BOA"), session.sent)
    }

    @Test
    fun stickyCtrlAndAltApplyToTheNextKeyOnly() = runComposeUiTest {
        val session = FakeSession()
        setContent { H2g2Theme { H2g2FullScreenTerminalScreen(session, onExit = {}) } }

        // Ctrl+\ is SIGQUIT's byte; the modifier is spent by it.
        onNodeWithContentDescription("Control modifier").performClick()
        onNodeWithContentDescription("Backslash").performClick()
        onNodeWithContentDescription("Backslash").performClick()
        // Alt prefixes ESC to a typed character, and turns a navigation key into its xterm form.
        onNodeWithContentDescription("Alt modifier").performClick()
        onNodeWithContentDescription("Dash").performClick()
        onNodeWithContentDescription("Control modifier").performClick()
        onNodeWithContentDescription("Left arrow").performClick()
        onNodeWithContentDescription("Left arrow").performClick()

        assertEquals(listOf("\u001C", "\\", "\u001B", "-", "\u001B[1;5D", "\u001B[D"), session.sent)
    }

    @Test
    fun closeAsksBeforeEndingTheProgram() = runComposeUiTest {
        val session = FakeSession()
        var exits = 0
        setContent { H2g2Theme { H2g2FullScreenTerminalScreen(session, onExit = { exits++ }) } }

        onNodeWithContentDescription("Close program").performClick()
        onNodeWithText("CLOSE VIM NOTES.TXT?").assertExists()
        onNodeWithText("Closing now ends the running program.").assertExists()
        assertEquals(false, session.killed)

        // The dialog's own CLOSE, not the top bar's chip (which is described as "Close program").
        onNode(hasText("CLOSE") and hasClickAction() and !hasContentDescription("Close program")).performClick()
        assertTrue(session.killed)
        assertEquals(1, exits)
        onAllNodesWithText("Closing now ends the running program.").assertCountEquals(0)
    }

    @Test
    fun aProgramThatEndsReportsItsExit() = runComposeUiTest {
        val session = FakeSession()
        var exits = 0
        setContent { H2g2Theme { H2g2FullScreenTerminalScreen(session, onExit = { exits++ }) } }
        assertEquals(0, exits)

        session.exitCode = 0
        waitForIdle()
        assertEquals(1, exits)
    }

    // ----- H2g2FullScreenTerminalScreen <-> H2g2AdaptiveTuiScreen: the RAW fallback ---------------

    @Test
    fun aWrappableProgramIsProjectedAndRawIsOneTapAway() = runComposeUiTest {
        val session = FakeSession(snapshot = wrappable())
        setContent { H2g2Theme { H2g2FullScreenTerminalScreen(session, onExit = {}) } }

        // The projection, not the grid.
        onNodeWithText("File chooser").assertExists()
        onNodeWithText("LIVE TERMINAL INTERFACE · GENERIC").assertExists()
        onNodeWithText("Second").assertExists()
        onAllNodesWithContentDescription("Terminal. hello world\n$").assertCountEquals(0)

        // RAW shows the raw grid, and offers the way back.
        onNodeWithContentDescription("Switch to raw terminal").performClick()
        onNodeWithContentDescription("Terminal. hello world\n$").assertExists()
        onNodeWithText("vim notes.txt").assertExists()
        onAllNodesWithText("File chooser").assertCountEquals(0)

        onNodeWithContentDescription("Switch to native view").performClick()
        onNodeWithText("File chooser").assertExists()
        onAllNodesWithContentDescription("Terminal. hello world\n$").assertCountEquals(0)
    }

    @Test
    fun forceRawStartsOnTheGridEvenWhenWrappable() = runComposeUiTest {
        val session = FakeSession(snapshot = wrappable())
        setContent { H2g2Theme { H2g2FullScreenTerminalScreen(session, onExit = {}, forceRaw = true) } }

        onNodeWithContentDescription("Terminal. hello world\n$").assertExists()
        onNodeWithContentDescription("Switch to native view").assertExists()
    }

    @Test
    fun theAdaptiveScreenIsTheRawTerminalWhenThereIsNothingToWrap() = runComposeUiTest {
        val session = FakeSession(snapshot = null)
        setContent { H2g2Theme { H2g2AdaptiveTuiScreen(session, onRawTerminal = {}, onExit = {}) } }

        onNodeWithContentDescription("Terminal. hello world\n$").assertExists()
        onNodeWithContentDescription("Tab").performClick()
        assertEquals(listOf("\t"), session.sent)
    }

    // ----- H2g2AdaptiveTuiScreen ----------------------------------------------------------------

    @Test
    fun tappingAMenuItemWalksTheSelectionThereAndEntersIt() = runComposeUiTest {
        // A program that moves its highlight on arrow keys and leaves the menu on Enter.
        val session = FakeSession(snapshot = wrappable()) { key ->
            val current = snapshot ?: return@FakeSession
            val layer = current.layers.first()
            val active = layer.activeIndex ?: 0
            snapshot = when (key) {
                "\u001B[B" -> current.copy(layers = listOf(layer.copy(activeIndex = active + 1)))
                "\u001B[A" -> current.copy(layers = listOf(layer.copy(activeIndex = active - 1)))
                "\r" -> current.copy(title = "Opened")
                else -> current
            }
        }
        var raw = 0
        setContent { H2g2Theme { H2g2AdaptiveTuiScreen(session, onRawTerminal = { raw++ }, onExit = {}) } }

        onNodeWithText("Third").performClick()
        // The controller pauses between keys to let the program redraw.
        waitUntil(timeoutMillis = WAIT_MS) { session.sent.size == 3 }
        waitForIdle()

        assertEquals(listOf("\u001B[B", "\u001B[B", "\r"), session.sent)
        onNodeWithText("Opened").assertExists()
        onAllNodesWithText("USE RAW TERMINAL").assertCountEquals(0)
        assertEquals(0, raw)
    }

    @Test
    fun aTapTheProgramIgnoresOffersRawInsteadOfSwitching() = runComposeUiTest {
        // This program never reacts, so the selection is never seen to move.
        val session = FakeSession(snapshot = wrappable())
        var raw = 0
        setContent { H2g2Theme { H2g2AdaptiveTuiScreen(session, onRawTerminal = { raw++ }, onExit = {}) } }

        onNodeWithText("Second").performClick()
        waitUntil(timeoutMillis = WAIT_MS) { onAllNodesWithText("USE RAW TERMINAL").fetchSemanticsNodes().isNotEmpty() }

        onNodeWithText(
            "That didn't reach the program (application did not acknowledge navigation). " +
                "The raw terminal can send the exact keystrokes."
        ).assertExists()
        // It is an offer: nothing switched on its own.
        assertEquals(0, raw)

        onNodeWithText("DISMISS").performClick()
        onAllNodesWithText("USE RAW TERMINAL").assertCountEquals(0)

        onNodeWithText("Second").performClick()
        waitUntil(timeoutMillis = WAIT_MS) { onAllNodesWithText("USE RAW TERMINAL").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("USE RAW TERMINAL").performClick()
        assertEquals(1, raw)
    }

    @Test
    fun aLayerWhoseSelectionCannotBeSeenIsShownButNotTappable() = runComposeUiTest {
        val session = FakeSession(snapshot = wrappable(layers = listOf(menu.copy(heading = null, activeIndex = null))))
        setContent { H2g2Theme { H2g2AdaptiveTuiScreen(session, onRawTerminal = {}, onExit = {}) } }

        onNodeWithText("MENU 1").assertExists()
        onNodeWithText("Selection state is not observable; use RAW for this layer.").assertExists()
        onNodeWithText("Second").performClick()
        waitForIdle()
        assertEquals(emptyList(), session.sent)
    }

    @Test
    fun tabsRegionsAndProgressAreRendered() = runComposeUiTest {
        val session = FakeSession(
            snapshot = wrappable(
                layers = emptyList(),
                tabs = listOf(TuiTab("Files", active = true, row = 0), TuiTab("Logs", active = false, row = 0)),
                regions = listOf(
                    TuiRegion(TuiRegionKind.TABLE, 2, 3, listOf("| name | size |", "| a.txt | 12 |")),
                    TuiRegion(TuiRegionKind.PROGRESS, 5, 5, listOf("Copying 40%"), progress = 0.4f)
                ),
                mouseAware = true
            )
        ) { key ->
            // Right arrow moves the active tab.
            val current = snapshot ?: return@FakeSession
            if (key == "\u001B[C") {
                snapshot = current.copy(tabs = current.tabs.mapIndexed { index, tab -> tab.copy(active = index == 1) })
            }
        }
        setContent { H2g2Theme { H2g2AdaptiveTuiScreen(session, onRawTerminal = {}, onExit = {}) } }

        onNodeWithText("TABLE").assertExists()
        onNodeWithText("PROGRESS").assertExists()
        onNodeWithText("Copying 40%").assertExists()
        onNode(isProgressBar).assertExists()
        val range = onNode(isProgressBar).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(ProgressBarRangeInfo(0.4f, 0f..1f), range)
        onNodeWithText(
            "Mouse-aware terminal UI detected. Contiguous semantic rows can receive native taps; " +
                "RAW remains available for unmodeled gestures."
        ).assertExists()

        // A row of a contiguous region in a mouse-aware program is a terminal mouse click at its
        // first non-blank column.
        onNodeWithText("| a.txt | 12 |").performClick()
        assertEquals(3 to 0, session.mouse)

        // Chip labels are drawn upper-cased.
        onNodeWithText("LOGS").performClick()
        waitUntil(timeoutMillis = WAIT_MS) { session.sent.isNotEmpty() }
        waitForIdle()
        assertEquals(listOf("\u001B[C"), session.sent)
    }

    @Test
    fun aConfirmationPromptAnswersWithYesOrNo() = runComposeUiTest {
        val session = FakeSession(
            snapshot = wrappable(layers = emptyList(), prompt = TuiPrompt(7, "Overwrite? [y/n]", TuiPromptKind.CONFIRMATION))
        )
        setContent { H2g2Theme { H2g2AdaptiveTuiScreen(session, onRawTerminal = {}, onExit = {}) } }

        onNodeWithText("Overwrite? [y/n]").assertExists()
        onNodeWithContentDescription("Answer yes").performClick()
        onNodeWithContentDescription("Answer no").performClick()
        assertEquals(listOf("y", "\r", "n", "\r"), session.sent)
    }

    @Test
    fun aTextPromptSendsWhatWasTypedThenEnter() = runComposeUiTest {
        val session = FakeSession(
            snapshot = wrappable(layers = emptyList(), prompt = TuiPrompt(7, "File name:", TuiPromptKind.TEXT))
        )
        setContent { H2g2Theme { H2g2AdaptiveTuiScreen(session, onRawTerminal = {}, onExit = {}) } }

        onNode(hasSetTextAction()).performTextInput("notes.txt")
        onNodeWithText("SUBMIT").performClick()
        assertEquals(listOf("notes.txt", "\r"), session.sent)
    }

    @Test
    fun theAdaptiveScreensCloseAsksToo() = runComposeUiTest {
        val session = FakeSession(snapshot = wrappable())
        var exits = 0
        setContent { H2g2Theme { H2g2AdaptiveTuiScreen(session, onRawTerminal = {}, onExit = { exits++ }) } }

        onNodeWithContentDescription("Send interrupt").performClick()
        assertEquals(listOf("\u0003"), session.sent)

        onNodeWithContentDescription("Close program").performClick()
        onNodeWithText("Closing now ends the running program.").assertExists()
        onNodeWithText("CANCEL").performClick()
        assertEquals(false, session.killed)
        assertEquals(0, exits)
    }

    // ----- H2g2StructuredShellStatus ------------------------------------------------------------

    private val status = ShellPresentation(
        shell = ShellFamily.BASH,
        cwd = "~/src",
        user = "az",
        host = "box",
        gitBranch = "main",
        gitDirty = true,
        lastExitCode = 1
    )

    @Test
    fun shellStatusShowsOneTagPerKnownField() = runComposeUiTest {
        setContent { H2g2Theme { H2g2StructuredShellStatus(status, onSendText = {}) } }

        onNodeWithText("bash").assertExists()
        onNodeWithText("az@box").assertExists()
        onNodeWithText("~/src").assertExists()
        onNodeWithText("● main").assertExists()
        onNodeWithText("exit 1").assertExists()
    }

    @Test
    fun shellStatusDrawsNothingWhenNothingIsKnown() = runComposeUiTest {
        setContent { H2g2Theme { H2g2StructuredShellStatus(ShellPresentation(), onSendText = {}) } }
        onAllNodes(hasText("", substring = true)).assertCountEquals(0)
    }

    @Test
    fun pickingABranchSwitchesToIt() = runComposeUiTest {
        val sent = mutableListOf<String>()
        setContent {
            H2g2Theme {
                H2g2StructuredShellStatus(
                    presentation = status,
                    onSendText = { sent += it },
                    loadGitBranches = { cwd -> if (cwd == "~/src") listOf("main", "it's-a-branch") else emptyList() }
                )
            }
        }

        onNodeWithText("● main").performClick()
        waitForIdle()
        onNodeWithText("Switch Git branch").assertExists()
        onNodeWithText("it's-a-branch").performClick()

        assertEquals(listOf("git switch -- 'it'\\''s-a-branch'\n"), sent)
        onAllNodesWithText("Switch Git branch").assertCountEquals(0)
    }

    @Test
    fun theBranchChooserSaysWhenThereIsNoInventory() = runComposeUiTest {
        setContent { H2g2Theme { H2g2StructuredShellStatus(status, onSendText = {}) } }

        onNodeWithText("● main").performClick()
        waitForIdle()
        onNodeWithText("No branch inventory is available for this directory.").assertExists()
        onNodeWithText("CLOSE").performClick()
        onAllNodesWithText("Switch Git branch").assertCountEquals(0)
    }

    @Test
    fun theRawSurfaceCarriesTheSessionsShellStatus() = runComposeUiTest {
        val session = FakeSession(presentation = status, branches = listOf("main", "dev"))
        setContent { H2g2Theme { H2g2FullScreenTerminalScreen(session, onExit = {}) } }

        onNodeWithText("az@box").assertExists()
        onNodeWithText("● main").performClick()
        waitForIdle()
        onNodeWithText("dev").performClick()
        assertEquals(listOf("git switch -- 'dev'\n"), session.sent)
    }
}
