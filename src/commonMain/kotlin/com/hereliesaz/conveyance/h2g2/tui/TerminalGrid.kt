package com.hereliesaz.conveyance.h2g2.tui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * How one run of terminal cells is painted. Colours are already resolved: the host looks indexed
 * colours up in its emulator's palette before building the grid. [inverse] swaps [foreground] and
 * [background] at draw time, exactly as the SGR attribute does.
 */
@Immutable
data class TerminalCellStyle(
    val foreground: Color = DefaultForeground,
    val background: Color = DefaultBackground,
    val bold: Boolean = false,
    val underline: Boolean = false,
    val inverse: Boolean = false
) {
    companion object {
        /** White: the stock terminal foreground. */
        val DefaultForeground = Color(0xFFFFFFFF)

        /** Black: the stock terminal background. */
        val DefaultBackground = Color(0xFF000000)

        /** Plain text in the stock colours. */
        val Default = TerminalCellStyle()
    }
}

/**
 * One grid cluster: a base character plus any zero-width combiners that ride on it, drawn stretched
 * to [width] cells (2 for a wide CJK/emoji character, otherwise 1). The second cell of a wide
 * character is not a cell of its own. A blank is `" "`.
 */
@Immutable
data class TerminalCell(
    val text: String,
    val style: TerminalCellStyle = TerminalCellStyle.Default,
    val width: Int = 1
)

/** One row of the terminal screen, left to right. Columns past the last cell are blank. */
@Immutable
data class TerminalLine(val cells: List<TerminalCell>) {
    /** The row's text, one cluster per cell, as a screen reader should hear it. */
    val text: String get() = cells.joinToString("") { it.text }

    companion object {
        /**
         * A row of single-width cells in one [style], one per code point of [text]. Enough for
         * plain ASCII output; build [TerminalCell]s yourself for wide characters and combiners.
         */
        fun of(text: String, style: TerminalCellStyle = TerminalCellStyle.Default): TerminalLine {
            val cells = ArrayList<TerminalCell>(text.length)
            var index = 0
            while (index < text.length) {
                val count = charCount(codePointAt(text, index))
                cells += TerminalCell(text.substring(index, index + count), style)
                index += count
            }
            return TerminalLine(cells)
        }
    }
}

/**
 * The visible terminal screen as plain data: what [H2g2FullScreenTerminalScreen] draws.
 *
 * The host rebuilds it from its emulator whenever the screen changes and exposes it through
 * [FullScreenTerminalSession.grid].
 *
 * - [lines]: the screen rows, top to bottom.
 * - [columns]: the emulator's column count; cells past it are not drawn.
 * - [defaultBackground]: the emulator's background colour. The grid is filled with it and runs in
 *   this colour paint no rectangle of their own.
 * - [cursorRow] / [cursorColumn]: the cursor cell, zero-based; out of range hides it.
 * - [cursorVisible]: the emulator's own cursor-visibility state (it blinks, or the program hid it).
 * - [cursorColor]: drawn at half opacity over the cursor cell.
 */
@Immutable
data class TerminalGrid(
    val lines: List<TerminalLine>,
    val columns: Int = lines.maxOfOrNull { line -> line.cells.sumOf { it.width.coerceIn(1, 2) } } ?: 0,
    val defaultBackground: Color = TerminalCellStyle.DefaultBackground,
    val cursorRow: Int = -1,
    val cursorColumn: Int = -1,
    val cursorVisible: Boolean = true,
    val cursorColor: Color = TerminalCellStyle.DefaultForeground
) {
    /** The emulator's row count. */
    val rows: Int get() = lines.size

    companion object {
        /** No screen yet (the emulator has not started): nothing is drawn. */
        val Empty = TerminalGrid(emptyList())

        /** A grid of plain single-width text rows, for previews, tests and simple hosts. */
        fun ofText(
            lines: List<String>,
            cursorRow: Int = -1,
            cursorColumn: Int = -1
        ): TerminalGrid = TerminalGrid(
            lines = lines.map { TerminalLine.of(it) },
            cursorRow = cursorRow,
            cursorColumn = cursorColumn
        )
    }
}

private const val MIN_HIGH_SURROGATE = 0xD800
private const val MAX_HIGH_SURROGATE = 0xDBFF
private const val MIN_LOW_SURROGATE = 0xDC00
private const val MAX_LOW_SURROGATE = 0xDFFF
private const val MIN_SUPPLEMENTARY_CODE_POINT = 0x10000
private const val SURROGATE_SHIFT = 10

/** The Unicode code point starting at [index] of [text] (a surrogate pair is one code point). */
internal fun codePointAt(text: String, index: Int): Int {
    val high = text[index].code
    if (high in MIN_HIGH_SURROGATE..MAX_HIGH_SURROGATE && index + 1 < text.length) {
        val low = text[index + 1].code
        if (low in MIN_LOW_SURROGATE..MAX_LOW_SURROGATE) {
            return ((high - MIN_HIGH_SURROGATE) shl SURROGATE_SHIFT) + (low - MIN_LOW_SURROGATE) + MIN_SUPPLEMENTARY_CODE_POINT
        }
    }
    return high
}

/** How many UTF-16 chars [codePoint] takes: 2 above the basic plane, otherwise 1. */
internal fun charCount(codePoint: Int): Int = if (codePoint >= MIN_SUPPLEMENTARY_CODE_POINT) 2 else 1

/** [codePoint] as a string (a surrogate pair above the basic plane). */
internal fun codePointToString(codePoint: Int): String =
    if (codePoint >= MIN_SUPPLEMENTARY_CODE_POINT) {
        val offset = codePoint - MIN_SUPPLEMENTARY_CODE_POINT
        charArrayOf(
            (MIN_HIGH_SURROGATE + (offset shr SURROGATE_SHIFT)).toChar(),
            (MIN_LOW_SURROGATE + (offset and ((1 shl SURROGATE_SHIFT) - 1))).toChar()
        ).concatToString()
    } else {
        codePoint.toChar().toString()
    }
