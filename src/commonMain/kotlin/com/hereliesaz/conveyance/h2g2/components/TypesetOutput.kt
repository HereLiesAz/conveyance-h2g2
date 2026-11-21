package com.hereliesaz.conveyance.h2g2.components

import com.hereliesaz.conveyance.h2g2.currentH2g2Type
import com.hereliesaz.conveyance.h2g2.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/*
 * "Output is set, not echoed" (HG2Gui_Reading.dc.html): a terminal draws a grid of characters
 * because a printer once did. Where output is genuinely structured - not prose, not a one-off
 * value - it's set on the page instead: hierarchy from weight/size, a two-column grid, rules at
 * 16% ink, figures right-aligned. This covers the one broadly, safely detectable case: a block of
 * `label: value` lines - real examples include HTTP response headers (`curl -I`) and single-field-
 * per-line output like `openssl x509 -noout -subject -issuer`. Multi-value lines packed with 2+
 * spaces between fields, the way `stat`'s default format does, are split into one row per field.
 * Output whose colons don't line-delimit cleanly (`ifconfig`, `dpkg -s`'s wrapped Description
 * field) just doesn't qualify - it falls back to plain monospace text, which is always correct.
 * The spec's other views (a manual page, a file index, a diff) each need real semantic parsing of
 * that specific command's output and are out of scope here - this is the one generic,
 * command-agnostic slice. A Plain text toggle (BufferEntry) always falls back to exactly what the
 * command printed, because a reading can be wrong.
 */

private val KEY_VALUE_LINE = Regex("^([^:\\n]{1,32}):\\s+(.+)$")
// Finds every `label: value` field within one line, where a field ends at a 2+ space gap (the
// next field's start) or end of line - lets a single-line, multi-field record like `stat`'s
// `Size: 2843  Blocks: 8  IO Block: 4096  regular file` become three rows instead of one row
// whose value swallows the rest of the line from the first colon on.
private val KEY_VALUE_FIELD = Regex("([^:\\n]{1,32}):\\s+(\\S.*?)(?=\\s{2,}|$)")

/** Conservative on purpose: every non-blank line must match `label: value` at least once, or
 *  this returns false - prose and code essentially never satisfy that on every line, so a false
 *  positive (typesetting something that wasn't actually a table) is unlikely. */
fun looksLikeKeyValueTable(text: String): Boolean {
    val lines = text.lines().filter { it.isNotBlank() }
    if (lines.size < 3) return false
    return lines.all { KEY_VALUE_LINE.matches(it.trim()) }
}

internal data class KeyValueRow(val label: String, val value: String)

internal fun parseKeyValueTable(text: String): List<KeyValueRow> =
    text.lines().filter { it.isNotBlank() }.flatMap { line ->
        KEY_VALUE_FIELD.findAll(line.trim()).map { m ->
            KeyValueRow(m.groupValues[1].trim(), m.groupValues[2].trim())
        }.toList()
    }

// Label text sits at this alpha over the page: .55 read at only ~3.3:1 on the default ground.
private const val TYPESET_LABEL_ALPHA = .72f
private val TYPESET_LABEL_SIZE = 11.sp

/**
 * One `LABEL ... value` row. The label keeps its natural width and the value takes the rest (right
 * aligned, wrapping), so a long value can no longer squeeze the label to nothing; the two are
 * merged into one accessibility node so a screen reader hears "label, value".
 */
@Composable
private fun TypesetRow(label: String, value: String, ink: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label.uppercase(),
            color = ink.copy(alpha = TYPESET_LABEL_ALPHA),
            style = currentH2g2Type().eyebrow.copy(letterSpacing = 0.18.em)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            color = ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            style = TextStyle(fontFeatureSettings = "tnum"),
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * A block of `label: value` lines set as a two-column table: rules at 16% [ink], the label in
 * small caps on the left, the value right-aligned in tabular figures. A host calls it with text
 * that [looksLikeKeyValueTable] accepted (or lets [H2g2TypesetOutput] decide).
 */
@Composable
fun H2g2KeyValueTable(text: String, ink: Color, modifier: Modifier = Modifier) {
    val rows = remember(text) { parseKeyValueTable(text) }
    val rule = ink.copy(alpha = .16f)
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(rule))
        rows.forEach { row ->
            TypesetRow(row.label, row.value, ink, Modifier.padding(vertical = 9.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(rule))
        }
    }
}

/*
 * D6 (HG2Gui Termux Coverage.dc.html): "TypesetOutput handles the two-column case well, but
 * plenty of real output is neither two columns nor prose: `ps aux` is eleven columns, `ls -l` is
 * nine ... a design that refuses horizontal scrolling needs an answer for data that is genuinely
 * wide." [looksLikeWideTable]/[parseWideTable] cover that: a header line names each column, every
 * data line beneath it packs at least that many whitespace-separated fields AND each field lines
 * up under its header label (left- or right-aligned to it) - `ps aux`, `netstat`. Plain
 * whitespace-separated prose with enough words per line does not qualify. `ls -l` and `df -h` do
 * not either: `ls -l` has no header row at all (only a `total N` line, skipped here), and `df -h`'s
 * "Mounted on" header is two words for one column. Each row reflows as its own labelled-field record ([H2g2WideTableRecords]),
 * "which suits this design better than a table anyway" per the doc's own recommendation, rather
 * than either wrapping into unreadable ribbons or being clipped at the screen edge.
 */

private val WORD = Regex("\\S+")

// A 2-column block already reads fine as KeyValueTable above; this is specifically the "beyond
// two columns" case the doc calls out, so a header naming fewer than 3 fields isn't wide enough
// to bother reflowing.
private const val MIN_WIDE_TABLE_COLUMNS = 3

/** Conservative like [looksLikeKeyValueTable]: every data line has to carry at least as many
 *  whitespace-separated fields as the header names, or this reports false and the raw text
 *  fallback (always correct) is what renders instead. */
fun looksLikeWideTable(text: String): Boolean {
    val lines = wideTableLines(text)
    if (lines.size < 3) return false
    val header = WORD.findAll(lines[0]).toList()
    if (header.size < MIN_WIDE_TABLE_COLUMNS || LS_MODE_COLUMN.containsMatchIn(lines[0])) return false
    return lines.drop(1).all { row ->
        val tokens = WORD.findAll(row).toList()
        tokens.size >= header.size && header.indices.all { i ->
            // The last column takes the rest of the line, so its position is free; every other
            // field must share its header label's left edge or its right edge.
            i == header.lastIndex ||
                tokens[i].range.first == header[i].range.first ||
                tokens[i].range.last == header[i].range.last
        }
    }
}

// `ls -l` prints "total 48" before its rows. That line isn't a header and, left in, it was taken
// for one (and `ls -l` has no real header at all).
private val LS_TOTAL_LINE = Regex("""^total\s+\d+\s*$""")

// A permissions column ("drwxr-xr-x", "-rw-r--r--") as the first token marks an `ls -l` row.
private val LS_MODE_COLUMN = Regex("""^\s*[-dlbcps][-rwxsStT]{9}[.+@]?\s""")

private fun wideTableLines(text: String): List<String> =
    text.lines().filter { it.isNotBlank() }.let { all ->
        if (all.isNotEmpty() && LS_TOTAL_LINE.matches(all[0])) all.drop(1) else all
    }

internal data class WideTableRow(val fields: List<Pair<String, String>>)

/** Slices every data line at the header's own column start offsets - the same visual alignment
 *  `ps`/`ls`/`df`/`netstat` already lean on to be readable as a fixed terminal grid in the first
 *  place. A column's start is pulled left to whichever is earliest between the header's own label
 *  and every data row's own token in that slot, since a right-aligned numeric column (`ps aux`'s
 *  PID, VSZ, ...) pads its header label further right than a narrower value needs - anchoring on
 *  the header alone would clip a wider value's leading digits. The last column always takes the
 *  rest of its line, so a field that legitimately contains spaces (`ps aux`'s COMMAND) survives
 *  intact instead of being cut at its own first space. */
internal fun parseWideTable(text: String): List<WideTableRow> {
    val lines = wideTableLines(text)
    val headerMatches = WORD.findAll(lines[0]).toList()
    val labels = headerMatches.map { it.value }
    val dataLines = lines.drop(1)
    val starts = headerMatches.map { it.range.first }.toMutableList()
    dataLines.forEach { row ->
        val rowTokens = WORD.findAll(row).toList()
        for (i in labels.indices) {
            if (i < rowTokens.size) starts[i] = minOf(starts[i], rowTokens[i].range.first)
        }
    }
    return dataLines.map { row ->
        val fields = labels.indices.map { i ->
            val from = starts[i].coerceIn(0, row.length)
            val to = if (i + 1 < starts.size) starts[i + 1].coerceIn(from, row.length) else row.length
            labels[i] to row.substring(from, to).trim()
        }
        WideTableRow(fields)
    }
}

// Mirrors OutputLines' own OUTPUT_WIPE_STAGGER_CAP rule: a very wide command (`ps aux` on a busy
// system, a large `netstat`) can have far more rows than are worth laying out as individual
// labelled-field records - the rest still exists in the raw/plain-text fallback, just not
// reflowed one full record at a time.
private const val WIDE_TABLE_ROW_CAP = 40

/**
 * A wide, header-led table (`ps aux`, `netstat`) reflowed so each row is its own record of
 * labelled fields instead of a grid that needs sideways scrolling. A host calls it with text that
 * [looksLikeWideTable] accepted (or lets [H2g2TypesetOutput] decide). Only the first 40 rows are
 * laid out; a closing line says how many were left to the plain text.
 */
@Composable
fun H2g2WideTableRecords(text: String, ink: Color, modifier: Modifier = Modifier) {
    val rows = remember(text) { parseWideTable(text) }
    val rule = ink.copy(alpha = .16f)
    Column(modifier.fillMaxWidth()) {
        rows.take(WIDE_TABLE_ROW_CAP).forEach { row ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(rule))
            row.fields.forEach { (label, value) ->
                if (value.isNotBlank()) {
                    TypesetRow(label, value, ink, Modifier.padding(vertical = 4.dp))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(rule))
        if (rows.size > WIDE_TABLE_ROW_CAP) {
            Text(
                "and ${rows.size - WIDE_TABLE_ROW_CAP} more rows - see PLAIN TEXT for the rest",
                color = ink.copy(alpha = TYPESET_LABEL_ALPHA),
                fontSize = TYPESET_LABEL_SIZE,
                modifier = Modifier.padding(top = 9.dp)
            )
        }
    }
}

// D6: "non-UTF8 bytes are not text at all ... binary content should be refused with an
// explanation rather than rendered." By the time output reaches here it has already gone through
// a UTF-8 decode - real binary content shows up as either the Unicode replacement character
// (invalid byte sequences) or a run of C0 control bytes that were never valid printable text to
// begin with (a compiled binary's own header, mostly). A real terminal transcript essentially
// never contains either, so a low, conservative ratio is enough to tell the two apart without
// false-flagging an ordinary command's output.
private const val BINARY_SUSPICIOUS_RATIO = 0.01

/**
 * Whether [text] is decoded binary rather than text: more than 1% of it is the Unicode replacement
 * character or C0 control bytes other than tab and line breaks. Hosts use it (or [classifyOutput])
 * to refuse to render such output as text.
 */
fun looksLikeBinary(text: String): Boolean {
    if (text.isEmpty()) return false
    val suspicious = text.count { c -> c == '\uFFFD' || (c.code < 0x20 && c != '\t' && c != '\n' && c != '\r') }
    return suspicious.toDouble() / text.length > BINARY_SUSPICIOUS_RATIO
}

/** The mutually-exclusive readings BufferEntry can typeset one command's output as - checked in
 *  this exact priority order (binary first, since garbled bytes can spuriously satisfy the art/
 *  table/wide-table heuristics on their own noise; wide-table last, since it's the least specific
 *  shape) and collapsed into a single classification here instead of a chain of independent
 *  booleans, so a caller only ever has to make (and pay the branching cost of) this one decision. */
enum class OutputKind { BINARY, ART, TABLE, WIDE_TABLE, PLAIN }

/**
 * Classifies one block of command [output] into the single [OutputKind] it should be typeset as.
 * Pure and cheap enough to call inside `remember(output)`; [H2g2TypesetOutput] does exactly that.
 */
fun classifyOutput(output: String): OutputKind = when {
    looksLikeBinary(output) -> OutputKind.BINARY
    looksLikeAsciiArt(output) -> OutputKind.ART
    looksLikeKeyValueTable(output) -> OutputKind.TABLE
    looksLikeWideTable(output) -> OutputKind.WIDE_TABLE
    else -> OutputKind.PLAIN
}

// The plain-text reading sits at this alpha over the page - the same value HG2Gui's terminal gives
// an output line that carries no ANSI hue of its own (ANSI_PLAIN_ALPHA there).
private const val PLAIN_OUTPUT_ALPHA = .8f

/**
 * One block of command output, set in whichever reading fits it. [classifyOutput] picks the form
 * and this renders it the way HG2Gui's terminal card does:
 *
 * - binary: the notice "Binary output - not displayed as text." in italics at 72% [ink];
 * - ASCII art: [H2g2AsciiArtCanvas] at 80% [ink];
 * - `label: value` lines: [H2g2KeyValueTable];
 * - a wide header-led table: [H2g2WideTableRecords];
 * - anything else: the text itself, in the body face at 12sp/17sp and 80% [ink].
 *
 * A host passes the raw [text] and the colour it should read in (normally the ground's `onPage`).
 * "A reading can be wrong", so [showRaw] is the escape hatch a PLAIN TEXT toggle drives: true
 * always shows exactly what the command printed, whatever it was classified as.
 */
@Composable
fun H2g2TypesetOutput(text: String, ink: Color, modifier: Modifier = Modifier, showRaw: Boolean = false) {
    val kind = remember(text) { classifyOutput(text) }
    val type = currentH2g2Type()
    when {
        kind == OutputKind.BINARY && !showRaw -> Text(
            "Binary output - not displayed as text.",
            modifier = modifier,
            style = type.body.copy(
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = ink.copy(alpha = .72f),
                fontStyle = FontStyle.Italic
            )
        )
        kind == OutputKind.ART && !showRaw -> H2g2AsciiArtCanvas(text, ink.copy(alpha = .8f), modifier)
        kind == OutputKind.TABLE && !showRaw -> H2g2KeyValueTable(text, ink, modifier)
        kind == OutputKind.WIDE_TABLE && !showRaw -> H2g2WideTableRecords(text, ink, modifier)
        else -> Text(
            text,
            modifier = modifier,
            style = type.body.copy(
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = ink.copy(alpha = PLAIN_OUTPUT_ALPHA)
            )
        )
    }
}
