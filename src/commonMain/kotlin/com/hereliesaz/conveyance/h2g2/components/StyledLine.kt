package com.hereliesaz.conveyance.h2g2.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.hereliesaz.conveyance.h2g2.H2g2
import com.hereliesaz.conveyance.h2g2.readableOn

/*
 * One line of coloured terminal text, and how it is drawn. HG2Gui keeps these beside its terminal
 * output; the terminal's cards and the file manager's highlighted preview both draw with them, so
 * they live here once. [background] has no default: the ground is a CompositionLocal in this
 * library, so the caller reads it in composition and passes the page colour in.
 */

/**
 * A run of text in one style: [hue] is an index into [H2g2.hues] (null for the plain ink), [bold]
 * is the ANSI bold attribute. A host's ANSI parser or syntax highlighter produces lists of these,
 * one list per line.
 */
data class StyledSpan(val text: String, val hue: Int? = null, val bold: Boolean = false)

private const val ANSI_PLAIN_ALPHA = .8f

internal fun StyledSpan.inkColor(onPage: Color, background: Color): Color =
    hue?.let { H2g2.hues[it].readableOn(background) } ?: onPage.copy(alpha = ANSI_PLAIN_ALPHA)

/**
 * One output line as styled text. ANSI hues are pill colours, tuned to sit behind labels, not to be
 * text on the ground: each is pushed to a readable contrast against [background] (the ground's
 * page; cards pass their own painted surface).
 */
internal fun buildStyledLine(
    spans: List<StyledSpan>,
    onPage: Color,
    background: Color
): AnnotatedString = buildAnnotatedString {
    spans.forEach { span ->
        withStyle(SpanStyle(color = span.inkColor(onPage, background), fontWeight = if (span.bold) FontWeight.Bold else null)) {
            append(span.text)
        }
    }
}
