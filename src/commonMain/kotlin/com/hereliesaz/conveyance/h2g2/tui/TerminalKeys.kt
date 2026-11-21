package com.hereliesaz.conveyance.h2g2.tui

import androidx.compose.ui.input.key.Key

/**
 * The non-character keys a terminal understands: the ones the full-screen terminal's key rows and
 * a hardware keyboard send as escape sequences rather than as typed text. Turn one into the bytes
 * to write with [terminalKeySequence].
 */
enum class TerminalKey {
    /** The centre of a directional pad; sends a carriage return. */
    CENTER,
    UP,
    DOWN,
    RIGHT,
    LEFT,
    HOME,
    END,
    F1,
    F2,
    F3,
    F4,
    F5,
    F6,
    F7,
    F8,
    F9,
    F10,
    F11,
    F12,

    /** Sys Request / Print. */
    PRINT_SCREEN,

    /** Pause/Break. */
    BREAK,
    ESCAPE,
    INSERT,

    /** Forward delete (the delete-character key), not backspace. */
    DELETE,
    PAGE_UP,
    PAGE_DOWN,
    BACKSPACE,
    NUM_LOCK,
    SPACE,
    TAB,
    ENTER,
    NUMPAD_ENTER,
    NUMPAD_MULTIPLY,
    NUMPAD_ADD,
    NUMPAD_COMMA,
    NUMPAD_SUBTRACT,
    NUMPAD_DIVIDE,
    NUMPAD_EQUALS
}

/** Modifier bits for [terminalKeySequence]; combine with `or`. */
object TerminalKeyModifiers {
    const val NONE: Int = 0
    const val SHIFT: Int = 1
    const val ALT: Int = 2
    const val CTRL: Int = 4
}

private const val ESC = "\u001B"
private const val CSI_1 = "\u001B[1"
private const val SS3 = "\u001BO"

/**
 * The xterm escape sequence for [key] with [modifiers] (bits of [TerminalKeyModifiers]) held, or
 * null when the key has none in that state and should go through normal text input instead (plain
 * Space, Num Lock outside keypad-application mode).
 *
 * - [cursorApplication]: the terminal is in cursor-keys application mode (DECCKM), so unmodified
 *   arrows, Home and End use the `ESC O` form instead of `ESC [`.
 * - [keypadApplication]: the terminal is in keypad application mode (DECKPAM), which changes what
 *   the numeric keypad's keys send.
 *
 * A host writes the returned string to its PTY. The library's own screens call this for you; it is
 * public for hosts that drive a program themselves (a custom
 * [FullScreenTerminalSession.activate], say).
 */
@Suppress("CyclomaticComplexMethod", "LongMethod")
fun terminalKeySequence(
    key: TerminalKey,
    modifiers: Int = TerminalKeyModifiers.NONE,
    cursorApplication: Boolean = false,
    keypadApplication: Boolean = false
): String? = when (key) {
    TerminalKey.CENTER -> "\r"

    TerminalKey.UP -> cursorKey('A', modifiers, cursorApplication)
    TerminalKey.DOWN -> cursorKey('B', modifiers, cursorApplication)
    TerminalKey.RIGHT -> cursorKey('C', modifiers, cursorApplication)
    TerminalKey.LEFT -> cursorKey('D', modifiers, cursorApplication)
    TerminalKey.HOME -> cursorKey('H', modifiers, cursorApplication)
    TerminalKey.END -> cursorKey('F', modifiers, cursorApplication)

    // An xterm can send function keys F1 to F4 in two modes: vt100 compatible or not. This is the
    // xterm form (ESC O P ...), which is what terminfo's xterm entries describe.
    TerminalKey.F1 -> if (modifiers == 0) "${SS3}P" else transformForModifiers(CSI_1, modifiers, 'P')
    TerminalKey.F2 -> if (modifiers == 0) "${SS3}Q" else transformForModifiers(CSI_1, modifiers, 'Q')
    TerminalKey.F3 -> if (modifiers == 0) "${SS3}R" else transformForModifiers(CSI_1, modifiers, 'R')
    TerminalKey.F4 -> if (modifiers == 0) "${SS3}S" else transformForModifiers(CSI_1, modifiers, 'S')
    TerminalKey.F5 -> transformForModifiers("$ESC[15", modifiers, '~')
    TerminalKey.F6 -> transformForModifiers("$ESC[17", modifiers, '~')
    TerminalKey.F7 -> transformForModifiers("$ESC[18", modifiers, '~')
    TerminalKey.F8 -> transformForModifiers("$ESC[19", modifiers, '~')
    TerminalKey.F9 -> transformForModifiers("$ESC[20", modifiers, '~')
    TerminalKey.F10 -> transformForModifiers("$ESC[21", modifiers, '~')
    TerminalKey.F11 -> transformForModifiers("$ESC[23", modifiers, '~')
    TerminalKey.F12 -> transformForModifiers("$ESC[24", modifiers, '~')

    TerminalKey.PRINT_SCREEN -> "$ESC[32~"
    TerminalKey.BREAK -> "$ESC[34~"

    TerminalKey.ESCAPE -> ESC

    TerminalKey.INSERT -> transformForModifiers("$ESC[2", modifiers, '~')
    TerminalKey.DELETE -> transformForModifiers("$ESC[3", modifiers, '~')

    TerminalKey.PAGE_UP -> transformForModifiers("$ESC[5", modifiers, '~')
    TerminalKey.PAGE_DOWN -> transformForModifiers("$ESC[6", modifiers, '~')
    TerminalKey.BACKSPACE -> {
        val prefix = if (modifiers and TerminalKeyModifiers.ALT == 0) "" else ESC
        // Just do what xterm and gnome-terminal does:
        prefix + if (modifiers and TerminalKeyModifiers.CTRL == 0) "\u007F" else "\u0008"
    }
    TerminalKey.NUM_LOCK -> if (keypadApplication) "${SS3}P" else null
    TerminalKey.SPACE ->
        // If ctrl is not down, return null so that it goes through normal input processing (which
        // may e.g. cause a combining accent to be written):
        if (modifiers and TerminalKeyModifiers.CTRL == 0) null else "\u0000"
    TerminalKey.TAB ->
        // This is back-tab when shifted:
        if (modifiers and TerminalKeyModifiers.SHIFT == 0) "\u0009" else "$ESC[Z"
    TerminalKey.ENTER -> if (modifiers and TerminalKeyModifiers.ALT == 0) "\r" else "$ESC\r"

    TerminalKey.NUMPAD_ENTER -> if (keypadApplication) transformForModifiers(SS3, modifiers, 'M') else "\n"
    TerminalKey.NUMPAD_MULTIPLY -> if (keypadApplication) transformForModifiers(SS3, modifiers, 'j') else "*"
    TerminalKey.NUMPAD_ADD -> if (keypadApplication) transformForModifiers(SS3, modifiers, 'k') else "+"
    TerminalKey.NUMPAD_COMMA -> ","
    TerminalKey.NUMPAD_SUBTRACT -> if (keypadApplication) transformForModifiers(SS3, modifiers, 'm') else "-"
    TerminalKey.NUMPAD_DIVIDE -> if (keypadApplication) transformForModifiers(SS3, modifiers, 'o') else "/"
    TerminalKey.NUMPAD_EQUALS -> if (keypadApplication) transformForModifiers(SS3, modifiers, 'X') else "="
}

/** Arrows, Home and End: `ESC O x` / `ESC [ x` bare, `ESC [ 1 ; m x` with a modifier held. */
private fun cursorKey(final: Char, modifiers: Int, cursorApplication: Boolean): String =
    if (modifiers == 0) (if (cursorApplication) "$SS3$final" else "$ESC[$final") else transformForModifiers(CSI_1, modifiers, final)

/**
 * xterm's modifier parameter: 2 = Shift, 3 = Alt, 4 = Shift+Alt, 5 = Ctrl, 6 = Shift+Ctrl,
 * 7 = Alt+Ctrl, 8 = all three. The [TerminalKeyModifiers] bits are chosen so that it is simply
 * the mask plus one.
 */
private fun transformForModifiers(start: String, modifiers: Int, lastChar: Char): String {
    val mask = modifiers and (TerminalKeyModifiers.SHIFT or TerminalKeyModifiers.ALT or TerminalKeyModifiers.CTRL)
    if (mask == 0) return start + lastChar
    return start + ";" + (mask + 1) + lastChar
}

private const val ESC_CODE_POINT = 0x1b

/** The control byte for Ctrl+[codePoint] (`Ctrl+C` = 3, `Ctrl+[` = ESC, ...), or null if it has none. */
internal fun controlCodePoint(codePoint: Int): Int? = when (codePoint) {
    in 'a'.code..'z'.code -> codePoint - 'a'.code + 1
    in 'A'.code..'Z'.code -> codePoint - 'A'.code + 1
    '@'.code, ' '.code -> 0
    '['.code -> ESC_CODE_POINT
    '\\'.code -> 0x1c
    ']'.code -> 0x1d
    '^'.code -> 0x1e
    '_'.code -> 0x1f
    '?'.code -> 0x7f
    else -> null
}

/**
 * What to write to the PTY for one typed character with the sticky modifiers applied: Ctrl maps it
 * to its control byte when it has one (otherwise the character goes through unchanged), Alt prefixes
 * ESC - the convention every terminal program expects for Meta.
 */
internal fun terminalCodePoints(codePoint: Int, ctrl: Boolean, alt: Boolean): IntArray {
    val base = if (ctrl) controlCodePoint(codePoint) ?: codePoint else codePoint
    return if (alt) intArrayOf(ESC_CODE_POINT, base) else intArrayOf(base)
}

private val LETTER_KEYS = listOf(
    Key.A, Key.B, Key.C, Key.D, Key.E, Key.F, Key.G, Key.H, Key.I, Key.J, Key.K, Key.L, Key.M,
    Key.N, Key.O, Key.P, Key.Q, Key.R, Key.S, Key.T, Key.U, Key.V, Key.W, Key.X, Key.Y, Key.Z
)

/**
 * The [TerminalKey] a hardware [key] stands for, or null for a key that types text. Back maps to
 * Escape, as it does on a terminal. A platform that lacks a key reports it as [Key.Unknown]; that
 * is ruled out first so an unrecognised key never passes for one of those.
 *
 * The numeric keypad's digits and dot are deliberately left to normal text input: what they mean
 * depends on Num Lock, which a key event does not carry here.
 */
@Suppress("CyclomaticComplexMethod")
internal fun terminalKeyFor(key: Key): TerminalKey? = when (key) {
    Key.Unknown -> null
    Key.DirectionCenter -> TerminalKey.CENTER
    Key.DirectionUp -> TerminalKey.UP
    Key.DirectionDown -> TerminalKey.DOWN
    Key.DirectionRight -> TerminalKey.RIGHT
    Key.DirectionLeft -> TerminalKey.LEFT
    Key.MoveHome -> TerminalKey.HOME
    Key.MoveEnd -> TerminalKey.END
    Key.F1 -> TerminalKey.F1
    Key.F2 -> TerminalKey.F2
    Key.F3 -> TerminalKey.F3
    Key.F4 -> TerminalKey.F4
    Key.F5 -> TerminalKey.F5
    Key.F6 -> TerminalKey.F6
    Key.F7 -> TerminalKey.F7
    Key.F8 -> TerminalKey.F8
    Key.F9 -> TerminalKey.F9
    Key.F10 -> TerminalKey.F10
    Key.F11 -> TerminalKey.F11
    Key.F12 -> TerminalKey.F12
    Key.PrintScreen -> TerminalKey.PRINT_SCREEN
    Key.Break -> TerminalKey.BREAK
    Key.Escape, Key.Back -> TerminalKey.ESCAPE
    Key.Insert -> TerminalKey.INSERT
    Key.Delete -> TerminalKey.DELETE
    Key.PageUp -> TerminalKey.PAGE_UP
    Key.PageDown -> TerminalKey.PAGE_DOWN
    Key.Backspace -> TerminalKey.BACKSPACE
    Key.NumLock -> TerminalKey.NUM_LOCK
    Key.Spacebar -> TerminalKey.SPACE
    Key.Tab -> TerminalKey.TAB
    Key.Enter -> TerminalKey.ENTER
    Key.NumPadEnter -> TerminalKey.NUMPAD_ENTER
    Key.NumPadMultiply -> TerminalKey.NUMPAD_MULTIPLY
    Key.NumPadAdd -> TerminalKey.NUMPAD_ADD
    Key.NumPadComma -> TerminalKey.NUMPAD_COMMA
    Key.NumPadSubtract -> TerminalKey.NUMPAD_SUBTRACT
    Key.NumPadDivide -> TerminalKey.NUMPAD_DIVIDE
    Key.NumPadEquals -> TerminalKey.NUMPAD_EQUALS
    else -> null
}

/**
 * What a hardware key-down writes to the PTY, or null to let the key through to text input.
 */
@Suppress("LongParameterList")
internal fun specialKeySequence(
    key: Key,
    shift: Boolean,
    ctrl: Boolean,
    alt: Boolean,
    cursorApplication: Boolean,
    keypadApplication: Boolean
): String? {
    // Hardware Ctrl+letter: the key table has no mapping for it and the hidden text field turns it
    // into a clipboard shortcut rather than a character, so send the control byte directly
    // (Alt adds the usual ESC prefix).
    if (ctrl && key != Key.Unknown) {
        val letter = LETTER_KEYS.indexOf(key)
        if (letter >= 0) {
            val control = (letter + 1).toChar().toString()
            return if (alt) "\u001b$control" else control
        }
    }
    val terminalKey = terminalKeyFor(key) ?: return null
    var mods = 0
    if (shift) mods = mods or TerminalKeyModifiers.SHIFT
    if (ctrl) mods = mods or TerminalKeyModifiers.CTRL
    if (alt) mods = mods or TerminalKeyModifiers.ALT
    return terminalKeySequence(terminalKey, mods, cursorApplication, keypadApplication)
}
