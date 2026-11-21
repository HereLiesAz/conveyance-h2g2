package com.hereliesaz.conveyance.h2g2.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.hereliesaz.conveyance.h2g2.H2g2
import com.hereliesaz.conveyance.h2g2.H2g2Contrast
import com.hereliesaz.conveyance.h2g2.H2g2Theme
import com.hereliesaz.conveyance.h2g2.contrastRatio
import com.hereliesaz.conveyance.h2g2.grounds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Smoke tests: each public composable of this package is really composed inside [H2g2Theme] and
 * something a reader (or a screen reader) would perceive is asserted. They pin that the pieces
 * compose on every target and that [H2g2TypesetOutput] routes each kind of output to its form.
 */
@OptIn(ExperimentalTestApi::class)
class ComponentsUiTest {

    private val keyValue = "Address: 10.0.0.14/24\nGateway: 10.0.0.1\nSignal: -47 dBm"

    private val wide = listOf(
        "USER       PID %CPU %MEM COMMAND",
        "root         1  0.0  0.1 init",
        "app       1234  2.5  1.0 com.example.app"
    ).joinToString("\n")

    private val art = listOf(
        "+--------------------+",
        "|  /\\_/\\    ______   |",
        "| ( o.o )  |      |  |",
        "|  > ^ <   |______|  |",
        "+--------------------+"
    ).joinToString("\n")

    @Test
    fun jobProgressBarReportsItsFillAsAProgressRange() = runComposeUiTest {
        val state = JobProgressBarState()
        lateinit var scope: CoroutineScope
        setContent {
            scope = rememberCoroutineScope()
            H2g2Theme { H2g2JobProgressBar(state, avoidSystemBars = false, description = "Download") }
        }
        onNodeWithContentDescription("Download").assertIsDisplayed()
        onNodeWithContentDescription("Download").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(0f, 0f..1f))
        )
        scope.launch { state.advanceTo(0.5f) }
        waitForIdle()
        assertEquals(0.5f, state.fill.value)
        scope.launch { state.complete() }
        waitForIdle()
        onNodeWithContentDescription("Download").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(1f, 0f..1f))
        )
        // "Holds wherever it actually got to, then drains away."
        scope.launch { state.fail() }
        waitUntil(timeoutMillis = 5_000) { state.fill.value == 0f }
        assertEquals(0f, state.fill.value)
    }

    @Test
    fun progressFillReadsAgainstEveryGround() {
        grounds.forEach { ground ->
            val fill = progressFillOn(ground.page)
            assertTrue(contrastRatio(fill, ground.page) >= H2g2Contrast.NON_TEXT, "fill on ${ground.page}")
        }
        assertEquals(H2g2.yellow, progressFillOn(Color.Black))
        assertEquals(H2g2.ink, progressFillOn(Color.White))
    }

    @Test
    fun keyValueTableSetsLabelsInCapsBesideTheirValues() = runComposeUiTest {
        setContent { H2g2Theme { H2g2KeyValueTable(keyValue, H2g2.ink) } }
        onNodeWithText("GATEWAY").assertIsDisplayed()
        onNodeWithText("10.0.0.1").assertIsDisplayed()
        onNodeWithText("-47 dBm").assertIsDisplayed()
    }

    @Test
    fun wideTableReflowsEachRowAsLabelledFields() = runComposeUiTest {
        setContent { H2g2Theme { H2g2WideTableRecords(wide, H2g2.ink) } }
        onNodeWithText("com.example.app").assertIsDisplayed()
        onNodeWithText("1234").assertIsDisplayed()
        onNodeWithText("init").assertIsDisplayed()
    }

    @Test
    fun asciiArtCanvasDescribesItselfInsteadOfShowingGlyphs() = runComposeUiTest {
        setContent { H2g2Theme { Box(Modifier.width(300.dp)) { H2g2AsciiArtCanvas(art, H2g2.ink) } } }
        onNodeWithContentDescription("ASCII art, 5 rows by 22 columns. Use the plain text toggle to read it.")
            .assertIsDisplayed()
    }

    @Test
    fun typesetOutputRendersATableForKeyValueLines() = runComposeUiTest {
        setContent { H2g2Theme { H2g2TypesetOutput(keyValue, H2g2.ink) } }
        onNodeWithText("ADDRESS").assertIsDisplayed()
        onNodeWithText("10.0.0.14/24").assertIsDisplayed()
    }

    @Test
    fun typesetOutputReflowsAWideTable() = runComposeUiTest {
        setContent { H2g2Theme { H2g2TypesetOutput(wide, H2g2.ink) } }
        onNodeWithText("com.example.app").assertIsDisplayed()
    }

    @Test
    fun typesetOutputTracesArt() = runComposeUiTest {
        assertEquals(OutputKind.ART, classifyOutput(art))
        setContent { H2g2Theme { Box(Modifier.width(300.dp)) { H2g2TypesetOutput(art, H2g2.ink) } } }
        onNodeWithContentDescription("ASCII art", substring = true).assertIsDisplayed()
    }

    @Test
    fun typesetOutputRefusesBinary() = runComposeUiTest {
        setContent { H2g2Theme { H2g2TypesetOutput("ELF\u0001\u0002\u0003\u0000\u0000 not text", H2g2.ink) } }
        onNodeWithText("Binary output - not displayed as text.").assertIsDisplayed()
    }

    @Test
    fun typesetOutputShowsProseAsItIs() = runComposeUiTest {
        setContent { H2g2Theme { H2g2TypesetOutput("hello world", H2g2.ink) } }
        onNodeWithText("hello world").assertIsDisplayed()
    }

    @Test
    fun showRawAlwaysFallsBackToWhatTheCommandPrinted() = runComposeUiTest {
        setContent { H2g2Theme { H2g2TypesetOutput(keyValue, H2g2.ink, showRaw = true) } }
        onNodeWithText(keyValue).assertIsDisplayed()
    }
}
