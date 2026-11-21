package com.hereliesaz.conveyance.h2g2.files

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.hereliesaz.conveyance.h2g2.H2g2
import com.hereliesaz.conveyance.h2g2.readableOn

/*
 * The styled-line builder a highlighted file preview is drawn with - HG2Gui keeps these beside
 * its terminal output (ui/TerminalScreen.kt), which is where a preview's [StyledSpan]s come from
 * there too. Only the two pure pieces the file preview needs travelled here. [background] has no
 * default: the ground is a CompositionLocal in this library, so the caller reads it in composition
 * and passes the page colour in.
 */

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
