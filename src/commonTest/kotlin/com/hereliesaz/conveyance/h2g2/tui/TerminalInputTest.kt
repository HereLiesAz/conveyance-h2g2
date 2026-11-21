package com.hereliesaz.conveyance.h2g2.tui

import com.hereliesaz.conveyance.h2g2.terminal.ShellFamily
import com.hereliesaz.conveyance.h2g2.terminal.ShellPresentation
import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The pure parts of the RAW terminal's sticky CTRL/ALT keys and the TUI wrapper's failure copy. */
class TerminalInputTest {

    @Test
    fun ctrlMapsLettersToControlBytesInEitherCase() {
        assertEquals(3, controlCodePoint('c'.code))
        assertEquals(3, controlCodePoint('C'.code))
        assertEquals(1, controlCodePoint('a'.code))
        assertEquals(26, controlCodePoint('z'.code))
    }

    @Test
    fun ctrlMapsTheShellPunctuationKeys() {
        assertEquals(0x1c, controlCodePoint('\\'.code)) // SIGQUIT
        assertEquals(0x1b, controlCodePoint('['.code))
        assertEquals(0, controlCodePoint(' '.code))
        assertEquals(0x7f, controlCodePoint('?'.code))
        assertNull(controlCodePoint('7'.code))
    }

    @Test
    fun plainKeyIsSentUnchanged() {
        assertContentEquals(intArrayOf('x'.code), terminalCodePoints('x'.code, ctrl = false, alt = false))
    }

    @Test
    fun altPrefixesEsc() {
        assertContentEquals(intArrayOf(0x1b, 'b'.code), terminalCodePoints('b'.code, ctrl = false, alt = true))
    }

    @Test
    fun ctrlAndAltCombine() {
        assertContentEquals(intArrayOf(0x1b, 3), terminalCodePoints('c'.code, ctrl = true, alt = true))
    }

    @Test
    fun ctrlOnAKeyWithNoControlByteFallsThrough() {
        assertContentEquals(intArrayOf('7'.code), terminalCodePoints('7'.code, ctrl = true, alt = false))
    }

    @Test
    fun emptyScreenIsDescribedAsEmpty() {
        assertEquals("Terminal. The screen is empty.", terminalScreenDescription(emptyList()))
    }

    @Test
    fun screenDescriptionIsTheVisibleLines() {
        assertEquals("Terminal. one\ntwo", terminalScreenDescription(listOf("one", "two")))
    }

    @Test
    fun screenDescriptionKeepsTheLastLinesOfALongScreen() {
        val lines = (1..100).map { "line $it" }
        val description = terminalScreenDescription(lines)
        assertTrue(description.endsWith("line 100"))
        assertTrue(!description.contains("line 1\n"))
    }

    @Test
    fun interactionFailureOffersRawInsteadOfSwitching() {
        val withReason = interactionFailureMessage("target disappeared")
        assertTrue(withReason.contains("target disappeared"))
        assertTrue(withReason.contains("raw terminal"))
        assertTrue(interactionFailureMessage(null).contains("raw terminal"))
        assertEquals(interactionFailureMessage(null), interactionFailureMessage("  "))
    }

    // ----- the key table: the same expectations the emulator's own key handler is tested against -----

    private val shift = TerminalKeyModifiers.SHIFT
    private val alt = TerminalKeyModifiers.ALT
    private val ctrl = TerminalKeyModifiers.CTRL

    @Test
    fun arrowsHomeAndEndFollowCursorApplicationMode() {
        assertEquals("\u001B[A", terminalKeySequence(TerminalKey.UP))
        assertEquals("\u001B[B", terminalKeySequence(TerminalKey.DOWN))
        assertEquals("\u001B[C", terminalKeySequence(TerminalKey.RIGHT))
        assertEquals("\u001B[D", terminalKeySequence(TerminalKey.LEFT))
        assertEquals("\u001B[H", terminalKeySequence(TerminalKey.HOME))
        assertEquals("\u001B[F", terminalKeySequence(TerminalKey.END))
        assertEquals("\u001BOA", terminalKeySequence(TerminalKey.UP, cursorApplication = true))
        assertEquals("\u001BOH", terminalKeySequence(TerminalKey.HOME, cursorApplication = true))
        assertEquals("\u001BOF", terminalKeySequence(TerminalKey.END, cursorApplication = true))
    }

    @Test
    fun modifiedArrowsUseTheXtermModifierParameter() {
        assertEquals("\u001B[1;2A", terminalKeySequence(TerminalKey.UP, shift))
        assertEquals("\u001B[1;3A", terminalKeySequence(TerminalKey.UP, alt))
        assertEquals("\u001B[1;4A", terminalKeySequence(TerminalKey.UP, shift or alt))
        assertEquals("\u001B[1;5D", terminalKeySequence(TerminalKey.LEFT, ctrl))
        assertEquals("\u001B[1;6B", terminalKeySequence(TerminalKey.DOWN, shift or ctrl))
        assertEquals("\u001B[1;7C", terminalKeySequence(TerminalKey.RIGHT, alt or ctrl))
        assertEquals("\u001B[1;8C", terminalKeySequence(TerminalKey.RIGHT, shift or alt or ctrl))
        // A modifier wins over application mode.
        assertEquals("\u001B[1;2H", terminalKeySequence(TerminalKey.HOME, shift, cursorApplication = true))
        assertEquals("\u001B[1;2F", terminalKeySequence(TerminalKey.END, shift))
    }

    @Test
    fun functionKeys() {
        assertEquals("\u001BOP", terminalKeySequence(TerminalKey.F1))
        assertEquals("\u001BOS", terminalKeySequence(TerminalKey.F4))
        assertEquals("\u001B[15~", terminalKeySequence(TerminalKey.F5))
        assertEquals("\u001B[21~", terminalKeySequence(TerminalKey.F10))
        assertEquals("\u001B[24~", terminalKeySequence(TerminalKey.F12))
        assertEquals("\u001B[1;2P", terminalKeySequence(TerminalKey.F1, shift))
        assertEquals("\u001B[15;2~", terminalKeySequence(TerminalKey.F5, shift))
        assertEquals("\u001B[24;2~", terminalKeySequence(TerminalKey.F12, shift))
    }

    @Test
    fun editingAndPagingKeys() {
        assertEquals("\u001B", terminalKeySequence(TerminalKey.ESCAPE))
        assertEquals("\r", terminalKeySequence(TerminalKey.ENTER))
        assertEquals("\u001B\r", terminalKeySequence(TerminalKey.ENTER, alt))
        // Ctrl+Enter has no sequence of its own: it is the plain key.
        assertEquals("\r", terminalKeySequence(TerminalKey.ENTER, ctrl))
        assertEquals("\u007F", terminalKeySequence(TerminalKey.BACKSPACE))
        assertEquals("\u0008", terminalKeySequence(TerminalKey.BACKSPACE, ctrl))
        assertEquals("\u001B\u007F", terminalKeySequence(TerminalKey.BACKSPACE, alt))
        assertEquals("\t", terminalKeySequence(TerminalKey.TAB))
        assertEquals("\u001B[Z", terminalKeySequence(TerminalKey.TAB, shift))
        assertEquals("\u001B[5~", terminalKeySequence(TerminalKey.PAGE_UP))
        assertEquals("\u001B[6~", terminalKeySequence(TerminalKey.PAGE_DOWN))
        assertEquals("\u001B[2~", terminalKeySequence(TerminalKey.INSERT))
        assertEquals("\u001B[3~", terminalKeySequence(TerminalKey.DELETE))
        assertEquals("\u001B[3;5~", terminalKeySequence(TerminalKey.DELETE, ctrl))
    }

    @Test
    fun plainSpaceGoesThroughTextInput() {
        assertNull(terminalKeySequence(TerminalKey.SPACE))
        assertEquals("\u0000", terminalKeySequence(TerminalKey.SPACE, ctrl))
    }

    @Test
    fun keypadFollowsKeypadApplicationMode() {
        assertEquals("\n", terminalKeySequence(TerminalKey.NUMPAD_ENTER))
        assertEquals("\u001BOM", terminalKeySequence(TerminalKey.NUMPAD_ENTER, keypadApplication = true))
        assertEquals("+", terminalKeySequence(TerminalKey.NUMPAD_ADD))
        assertEquals("\u001BOk", terminalKeySequence(TerminalKey.NUMPAD_ADD, keypadApplication = true))
        assertNull(terminalKeySequence(TerminalKey.NUM_LOCK))
        assertEquals("\u001BOP", terminalKeySequence(TerminalKey.NUM_LOCK, keypadApplication = true))
    }

    // ----- hardware keys -----

    private fun hardware(key: Key, shift: Boolean = false, ctrl: Boolean = false, alt: Boolean = false, cursorApp: Boolean = false) =
        specialKeySequence(key, shift, ctrl, alt, cursorApp, keypadApplication = false)

    @Test
    fun hardwareCtrlLetterIsTheControlByte() {
        assertEquals("\u0003", hardware(Key.C, ctrl = true))
        assertEquals("\u0001", hardware(Key.A, ctrl = true))
        assertEquals("\u001A", hardware(Key.Z, ctrl = true))
        assertEquals("\u001b\u0003", hardware(Key.C, ctrl = true, alt = true))
    }

    @Test
    fun hardwareLettersWithoutCtrlAreLeftToTextInput() {
        assertNull(hardware(Key.C))
        assertNull(hardware(Key.C, shift = true))
        assertNull(hardware(Key.Spacebar))
    }

    @Test
    fun hardwareNavigationKeysCarryTheirModifiers() {
        assertEquals("\u001B[A", hardware(Key.DirectionUp))
        assertEquals("\u001BOA", hardware(Key.DirectionUp, cursorApp = true))
        assertEquals("\u001B[1;2A", hardware(Key.DirectionUp, shift = true))
        assertEquals("\u001B[1;5C", hardware(Key.DirectionRight, ctrl = true))
        assertEquals("\u001B", hardware(Key.Escape))
        assertEquals("\r", hardware(Key.Enter))
        assertEquals("\u007F", hardware(Key.Backspace))
        assertEquals("\u001B[Z", hardware(Key.Tab, shift = true))
        assertEquals("\u0000", hardware(Key.Spacebar, ctrl = true))
    }

    @Test
    fun anUnknownHardwareKeyIsNeverMistakenForATerminalKey() {
        assertNull(terminalKeyFor(Key.Unknown))
        assertNull(hardware(Key.Unknown))
        assertNull(hardware(Key.Unknown, ctrl = true))
    }

    // ----- models -----

    @Test
    fun codePointsSurviveTheRoundTrip() {
        val emoji = "😀"
        assertEquals(0x1F600, codePointAt(emoji, 0))
        assertEquals(2, charCount(0x1F600))
        assertEquals(emoji, codePointToString(0x1F600))
        assertEquals("a", codePointToString('a'.code))
        assertEquals(1, charCount('a'.code))
        // A lone surrogate is passed through as itself rather than read past the end.
        assertEquals(0xD83D, codePointAt("\uD83D", 0))
    }

    @Test
    fun aTextLineIsOneCellPerCodePoint() {
        val line = TerminalLine.of("a😀b")
        assertEquals(listOf("a", "😀", "b"), line.cells.map { it.text })
        assertEquals("a😀b", line.text)
    }

    @Test
    fun aGridMeasuresItsOwnColumnsCountingWideCells() {
        val grid = TerminalGrid(
            listOf(
                TerminalLine.of("abc"),
                TerminalLine(listOf(TerminalCell("中", width = 2), TerminalCell("文", width = 2), TerminalCell("!")))
            )
        )
        assertEquals(5, grid.columns)
        assertEquals(2, grid.rows)
        assertEquals(0, TerminalGrid.Empty.columns)
    }

    @Test
    fun onlyAnAlternateScreenWithStructureIsWrappable() {
        val menu = TuiLayer(
            heading = "Pick",
            items = listOf(TuiMenuItem(1, "One", selected = true), TuiMenuItem(2, "Two", selected = false)),
            activeIndex = 0
        )
        assertTrue(TuiSnapshot(null, listOf(menu), prompt = null, alternateScreen = true).isWrappable)
        assertFalse(TuiSnapshot(null, listOf(menu), prompt = null, alternateScreen = false).isWrappable)
        assertFalse(TuiSnapshot("title", emptyList(), prompt = null, alternateScreen = true).isWrappable)
        assertTrue(TuiSnapshot(null, emptyList(), TuiPrompt(3, "Name:", TuiPromptKind.TEXT), alternateScreen = true).isWrappable)
        // A single-item "menu" is not enough structure on its own.
        assertFalse(TuiSnapshot(null, listOf(menu.copy(items = menu.items.take(1))), prompt = null, alternateScreen = true).isWrappable)
    }

    @Test
    fun theInnermostObservableLayerOwnsInput() {
        val item = TuiMenuItem(1, "One", selected = false)
        val layers = listOf(
            TuiLayer("outer", listOf(item, item), activeIndex = 0),
            TuiLayer("inner", listOf(item, item), activeIndex = 1, depth = 1),
            TuiLayer("opaque", listOf(item, item), activeIndex = null, depth = 2)
        )
        assertEquals(1, TuiSnapshot(null, layers, prompt = null, alternateScreen = true).activeLayerIndex)
        assertNull(TuiSnapshot(null, layers.takeLast(1), prompt = null, alternateScreen = true).activeLayerIndex)
    }

    @Test
    fun shellStatusKnowsWhenItHasSomethingToShow() {
        assertFalse(ShellPresentation().hasStructuredStatus)
        assertFalse(ShellPresentation(shell = ShellFamily.BASH).hasStructuredStatus)
        assertTrue(ShellPresentation(cwd = "/tmp").hasStructuredStatus)
        assertTrue(ShellPresentation(lastExitCode = 0).hasStructuredStatus)
    }

    @Test
    fun branchNamesAreQuotedAsOneShellWord() {
        assertEquals("'main'", shellQuote("main"))
        assertEquals("'it'\\''s'", shellQuote("it's"))
        assertEquals("'a b; rm -rf /'", shellQuote("a b; rm -rf /"))
    }
}
