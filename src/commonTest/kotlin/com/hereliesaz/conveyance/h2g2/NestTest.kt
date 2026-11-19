package com.hereliesaz.conveyance.h2g2

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The containment grammar: a pill opens into a card holding its options; an option opens into a
 * card inside that card; while it is open its siblings are rods (spoken by name, not shown); a
 * leaf option acts instead of opening.
 */
@OptIn(ExperimentalTestApi::class)
class NestTest {
    @Test
    fun pillOpensIntoCardAndOptionOpensInsideIt() = runComposeUiTest {
        var picked = ""
        setContent {
            H2g2Nest(label = "Hive", hue = Color(0xFFE8B730)) {
                Nest(label = "Roles") { Option("Queen", onPick = { picked = "queen" }) }
                Option("Rename", onPick = { picked = "rename" })
            }
        }
        onNodeWithText("ROLES").assertDoesNotExist()
        onNodeWithText("HIVE").performClick()
        onNodeWithText("ROLES").assertIsDisplayed()
        onNodeWithText("RENAME").assertIsDisplayed()

        onNodeWithText("ROLES").performClick()
        onNodeWithText("QUEEN").assertIsDisplayed()
        // The sibling collapses to a rod: no visible label, still named for assistive tech.
        onNodeWithText("RENAME").assertDoesNotExist()
        onNodeWithContentDescription("Rename").assertExists()

        onNodeWithText("QUEEN").performClick()
        assertEquals("queen", picked)
    }

    @Test
    fun surfaceFollowsDepth() {
        assertEquals(H2g2Surface.recordTile, nestSurface(0))
        assertEquals(H2g2Surface.note, nestSurface(1))
        assertEquals(H2g2Surface.well, nestSurface(2))
        assertEquals(H2g2Surface.well, nestSurface(5))
    }

    @Test
    fun innerHueDarkensPerLevelAndCaps() {
        val base = Color(0xFFE8B730)
        assertEquals(base, H2g2Motion.innerHue(base, 0))
        assertTrue(H2g2Motion.innerHue(base, 1) != base)
        assertEquals(H2g2Motion.innerHue(base, 9), H2g2Motion.innerHue(base, 20))
    }

    @Test
    fun staggerIsCapped() {
        assertEquals(0L, staggerDelayMs(0, 10, 90))
        assertTrue(staggerDelayMs(29, 30, 90) <= STAGGER_TOTAL_CAP_MS)
        assertEquals(90L, staggerDelayMs(1, 3, 90))
        assertEquals(45, stackInterval(90, 7))
    }
}
