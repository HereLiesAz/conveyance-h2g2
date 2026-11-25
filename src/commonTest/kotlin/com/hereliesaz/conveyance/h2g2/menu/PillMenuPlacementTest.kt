package com.hereliesaz.conveyance.h2g2.menu

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.hereliesaz.conveyance.h2g2.H2g2Theme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every placement is the house stack seen through one mirrored layer. These prove the part a
 * picture cannot: that a tap lands on the pill where it is drawn, and that the picked path comes
 * back the same in all four corners.
 */
@OptIn(ExperimentalTestApi::class)
class PillMenuPlacementTest {
    private val tree = listOf(
        MenuNode("pkg", "pkg", children = listOf(MenuNode("install", "install"), MenuNode("remove", "remove"))),
        MenuNode("git", "git"),
    )

    @Test
    fun `a pick drills to the same tokens in every placement`() {
        for (placement in PillMenuPlacement.entries) {
            runComposeUiTest {
                val runs = mutableListOf<Pair<List<String>, Boolean>>()
                setContent {
                    H2g2Theme {
                        H2g2PillMenu(
                            roots = tree,
                            modifier = Modifier.fillMaxSize(),
                            placement = placement,
                            onRun = { tokens, terminal -> runs += tokens to terminal },
                        )
                    }
                }
                mainClock.advanceTimeBy(SETTLE_MS)
                onNodeWithText("PKG").performClick()
                mainClock.advanceTimeBy(SETTLE_MS)
                onNodeWithText("INSTALL").performClick()
                mainClock.advanceTimeBy(SETTLE_MS)
                waitForIdle()
                assertEquals(listOf("install") to true, runs.last(), "placement $placement")
            }
        }
    }

    @Test
    fun `top placements rest in the upper half and bottom placements in the lower`() {
        for (placement in PillMenuPlacement.entries) {
            runComposeUiTest {
                setContent { H2g2Theme { H2g2PillMenu(roots = tree, modifier = Modifier.fillMaxSize(), placement = placement) } }
                mainClock.advanceTimeBy(SETTLE_MS)
                waitForIdle()
                val height = onRoot().getBoundsInRoot().bottom
                val pill = onNodeWithText("GIT").getBoundsInRoot()
                val centre = (pill.top + pill.bottom) / 2
                val inTopHalf = centre < height / 2
                assertTrue(inTopHalf == placement.mirrorY, "placement $placement: pill centre $centre of $height")
                assertTrue(pill.bottom > 0.dp && pill.top < height, "placement $placement: pill is on screen")
            }
        }
    }

    @Test
    fun standalonePillPreservesHostSemanticsAndInput() = runComposeUiTest {
        var clicks = 0
        setContent {
            H2g2Theme {
                Pill(
                    label = "story", cap = "3", hue = 0, selected = true,
                    stretch = false, onClick = { clicks++ },
                    fillOverride = Color.White, textOverride = Color.Black,
                    semanticRole = Role.Tab, spokenLabel = "Story: 3", spokenState = "Tab 1 of 2",
                )
            }
        }
        val control = onNodeWithContentDescription("Story: 3")
        control.assertIsSelected()
        assertEquals(Role.Tab, control.fetchSemanticsNode().config[SemanticsProperties.Role])
        assertEquals("Tab 1 of 2", control.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        assertTrue(control.getBoundsInRoot().height >= 48.dp)
        control.performClick()
        runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun standaloneDisabledPillExposesDisabledState() = runComposeUiTest {
        setContent {
            H2g2Theme {
                Pill(label = "stop", cap = null, hue = 0, selected = false, stretch = false,
                    enabled = false, spokenLabel = "Stop", onClick = { error("Disabled pill clicked") })
            }
        }
        onNodeWithContentDescription("Stop").assertIsNotEnabled()
    }

    private companion object {
        // Longer than any entrance, leave or cascade, so each step starts from a settled stack.
        const val SETTLE_MS = 3_000L
    }
}
