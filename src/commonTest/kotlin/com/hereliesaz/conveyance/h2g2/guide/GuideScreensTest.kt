package com.hereliesaz.conveyance.h2g2.guide

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import com.hereliesaz.conveyance.h2g2.H2g2Theme
import com.hereliesaz.conveyance.h2g2.components.BackStepState
import com.hereliesaz.conveyance.h2g2.menu.MenuNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Compose smoke tests for the Guide's three public composables, each composed for real inside
 * [H2g2Theme], plus the content model the screens are parameterised on.
 */
@OptIn(ExperimentalTestApi::class)
class GuideScreensTest {

    /** A host's own Guide: nothing to do with Termux, to prove the reader is not tied to the manuscript. */
    private val manual = GuideContent(
        title = "THE MANUAL",
        lede = "Everything the kettle does, more or less.",
        chapters = listOf(
            guideChapter(
                "Part 1", "Boiling", "Water goes in. Use fill before boil.",
                GuideEntry(cmd = "boil", full = "bring to the boil", blurb = "Heats the water. Always fill first."),
                GuideEntry(cmd = "fill", full = "fill the kettle", blurb = "Adds water. Then boil it.")
            ),
            guideChapter("Part 2", "Descaling", "Nothing has been written here yet.")
        )
    )

    /** Taps the character at [index] of this text node, through its real text layout. */
    private fun SemanticsNodeInteraction.clickCharacter(index: Int) {
        val layouts = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        val box = layouts.first().getBoundingBox(index)
        performTouchInput { click(Offset(box.center.x, box.center.y)) }
    }

    // --- content model -------------------------------------------------------------------------

    @Test
    fun guideChapterStampsItsEntriesAndContentFlattensThem() {
        val boil = manual.entries.first()
        assertEquals("Part 1", boil.chapterLabel)
        assertEquals("Boiling", boil.chapterTitle)
        assertEquals("Water goes in. Use fill before boil.", boil.chapterIntro)
        assertEquals("Heats the water.", boil.teaser)
        assertEquals(listOf("boil", "fill"), manual.entries.map { it.cmd })
        assertEquals(setOf("boil", "fill"), manual.commands)
    }

    @Test
    fun theManuscriptShipsWhole() {
        val guide = HitchhikersGuideToTermux
        assertEquals("THE GUIDE", guide.title)
        assertEquals(GuideBook.chapters.size + 1, guide.chapters.size)
        assertEquals(GuideCanonicalConcepts.chapter, guide.chapters.last())
        assertEquals(GuideBook.entries.size + 3, guide.entries.size)
        assertEquals("ls", guide.entries.first().cmd)
        assertTrue("hg2auth" in guide.commands)
        // Chapter labels key the index rows, so they must be unique.
        assertEquals(guide.chapters.size, guide.chapters.map { it.label }.toSet().size)
        assertTrue(guide.entries.all { it.blurb.isNotBlank() && it.chapterIntro.isNotBlank() })
    }

    @Test
    fun runtimeIndexReadsTheHostsTree() {
        val index = GuideRuntimeIndex.fromTree(
            listOf(
                MenuNode(
                    "pkg", "pkg", cap = "packages",
                    children = listOf(
                        MenuNode("pkg.install", "install", value = "pkg install"),
                        MenuNode("pkg.browse", "browse…", emitsToken = false)
                    )
                )
            )
        )
        assertEquals(GuideRuntimeInfo(true, "packages", listOf("install")), index.info("pkg"))
        assertTrue(index.info("pkg  install").available)
        assertFalse(index.info("vi").available)
        assertFalse(GuideRuntimeIndex.Empty.info("pkg").available)
    }

    // --- H2g2GuideLinkedText -------------------------------------------------------------------

    @Test
    fun tappingALinkedCommandReportsThatCommand() = runComposeUiTest {
        val text = "Use chown after chmod."
        val picked = mutableListOf<String>()
        setContent {
            H2g2Theme {
                H2g2GuideLinkedText(
                    text = text,
                    commands = setOf("chmod", "chown"),
                    color = Color.Black,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    onCommandSelected = { picked += it }
                )
            }
        }
        val node = onNodeWithText(text)
        node.assertIsDisplayed()

        node.clickCharacter(text.indexOf("chmod") + 2)
        assertEquals(listOf("chmod"), picked)
        node.clickCharacter(text.indexOf("chown") + 2)
        assertEquals(listOf("chmod", "chown"), picked)
        // Plain prose is not a link.
        node.clickCharacter(text.indexOf("after") + 2)
        assertEquals(listOf("chmod", "chown"), picked)
    }

    @Test
    fun linkedTextOffersEachCommandAsANamedAccessibilityAction() = runComposeUiTest {
        val text = "Use chown after chmod, then chown again."
        val picked = mutableListOf<String>()
        setContent {
            H2g2Theme {
                H2g2GuideLinkedText(
                    text = text,
                    commands = setOf("chmod", "chown"),
                    color = Color.Black,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    onCommandSelected = { picked += it }
                )
            }
        }
        val actions = onNodeWithText(text).fetchSemanticsNode().config
            .getOrNull(SemanticsActions.CustomActions).orEmpty()
        assertEquals(listOf("Add chown to command", "Add chmod to command"), actions.map { it.label })
        runOnIdle { actions.last().action() }
        assertEquals(listOf("chmod"), picked)
    }

    // --- H2g2GuideReaderScreen -----------------------------------------------------------------

    @Test
    fun readerShowsTheManuscriptIndexByDefault() = runComposeUiTest {
        val backStep = BackStepState()
        var closed = 0
        setContent {
            H2g2Theme {
                H2g2GuideReaderScreen(fullscreen = false, onBack = { closed++ }, backStep = backStep)
            }
        }
        onNodeWithText("THE GUIDE").assertIsDisplayed()
        onNodeWithText("${HitchhikersGuideToTermux.entries.size} ENTRIES").assertIsDisplayed()
        onNodeWithText("STANDARD INPUT").assertIsDisplayed()
        onNodeWithText("ls").assertIsDisplayed()
        assertTrue(backStep.canStepBack)

        onNodeWithContentDescription("Back").performClick()
        assertEquals(1, closed)
    }

    @Test
    fun readerOpensAHostsEntryAndHandsItsCommandsBack() = runComposeUiTest {
        val backStep = BackStepState()
        val picked = mutableListOf<String>()
        var closed = 0
        setContent {
            H2g2Theme {
                H2g2GuideReaderScreen(
                    fullscreen = false,
                    onBack = { closed++ },
                    onCommandSelected = { picked += it },
                    backStep = backStep,
                    runtimeIndex = GuideRuntimeIndex.fromTree(
                        listOf(MenuNode("boil", "boil", cap = "hot", children = listOf(MenuNode("boil.fast", "--fast"))))
                    ),
                    content = manual
                )
            }
        }
        onNodeWithText("THE MANUAL").assertIsDisplayed()
        onNodeWithText("Everything the kettle does, more or less.").assertIsDisplayed()
        onNodeWithText("2 ENTRIES").assertIsDisplayed()
        // The chapter with no entries shows its intro instead.
        onNodeWithText("Nothing has been written here yet.").assertIsDisplayed()

        onNodeWithText("boil").performClick()
        onNodeWithText("ENTRY 1 OF 2").assertIsDisplayed()
        onNodeWithText("STANDS FOR “BRING TO THE BOIL”").assertIsDisplayed()
        onNodeWithText("available · hot").assertIsDisplayed()

        // The heading is the command itself.
        onNodeWithText("BOIL", useUnmergedTree = true).performClick()
        assertEquals(listOf("boil"), picked)

        // A live-option chip adds the root plus the option.
        onNodeWithContentDescription("Add boil --fast to command").performClick()
        assertEquals(listOf("boil", "boil --fast"), picked)

        // A command named in the prose is a link to the same path, and the Guide stays open.
        val blurb = "Heats the water. Always fill first."
        onNodeWithText(blurb).clickCharacter(blurb.indexOf("fill") + 1)
        assertEquals(listOf("boil", "boil --fast", "fill"), picked)
        onNodeWithText("ENTRY 1 OF 2").assertIsDisplayed()

        onNodeWithContentDescription("Next entry").performClick()
        onNodeWithText("ENTRY 2 OF 2").assertIsDisplayed()
        onNodeWithText("Not in this build").assertIsDisplayed()

        // System back steps entry -> index -> out.
        runOnIdle { backStep.stepBack() }
        onNodeWithText("THE MANUAL").assertIsDisplayed()
        runOnIdle { backStep.stepBack() }
        assertEquals(1, closed)
    }

    @Test
    fun readerConfirmsATappedCommand() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent {
            H2g2Theme {
                H2g2GuideReaderScreen(fullscreen = false, onBack = {}, backStep = BackStepState(), content = manual)
            }
        }
        mainClock.advanceTimeBy(100)
        onNodeWithText("fill").performClick()
        // Past the whole wipe (capped at GUIDE_WIPE_TOTAL_CAP_MS).
        mainClock.advanceTimeBy(GUIDE_WIPE_TOTAL_CAP_MS + 200)
        onNodeWithText("FILL", useUnmergedTree = true).performClick()
        mainClock.advanceTimeBy(100)
        onNodeWithText("Added “fill” to input").assertIsDisplayed()
        mainClock.advanceTimeBy(GUIDE_CONFIRMATION_MS + 200)
        onNodeWithText("Added “fill” to input").assertDoesNotExist()
    }

    @Test
    fun readerShowsProductionNotesOnlyWhenAsked() = runComposeUiTest {
        val content = GuideContent(
            chapters = listOf(
                guideChapter(
                    "Part 1", "Boiling", "Intro.",
                    GuideEntry("boil", "bring to the boil", "Heats.", animation = "A kettle, whistling.", note = "See above.")
                )
            )
        )
        var notes by mutableStateOf(false)
        setContent {
            H2g2Theme {
                H2g2GuideReaderScreen(
                    fullscreen = false, onBack = {}, backStep = BackStepState(),
                    showProductionNotes = notes, content = content
                )
            }
        }
        onNodeWithText("boil").performClick()
        onNodeWithText("See above.").assertIsDisplayed()
        onNodeWithText("ANIMATION CANDIDATE").assertDoesNotExist()
        notes = true
        onNodeWithText("ANIMATION CANDIDATE").assertIsDisplayed()
        onNodeWithText("A kettle, whistling.").assertIsDisplayed()
    }

    // --- H2g2CommandGuideScreen ----------------------------------------------------------------

    @Test
    fun commandGuideShowsTheTreeAndOpensTheReader() = runComposeUiTest {
        val backStep = BackStepState()
        val appended = mutableListOf<String>()
        val selected = mutableListOf<Pair<List<String>, Boolean>>()
        var closed = 0
        setContent {
            H2g2Theme {
                H2g2CommandGuideScreen(
                    tree = listOf(MenuNode("boil", "boil"), MenuNode("fill", "fill")),
                    fullscreen = false,
                    onCommandSelected = { tokens, complete -> selected += tokens to complete },
                    onBack = { closed++ },
                    backStep = backStep,
                    onCommandAppended = { appended += it },
                    content = manual
                )
            }
        }
        onNodeWithText("GUIDE").assertIsDisplayed()
        onNodeWithText("Pick a command to drop it into the input — nothing here runs on its own.").assertIsDisplayed()
        assertFalse(backStep.canStepBack)
        onNodeWithContentDescription("Back to the terminal").performClick()
        assertEquals(1, closed)

        onNodeWithContentDescription("Open the Guide: command reference and walkthroughs").performClick()
        onNodeWithText("THE MANUAL").assertIsDisplayed()
        assertTrue(backStep.canStepBack)

        // The reader's facts come from the same tree; a reader tap appends and never completes.
        onNodeWithText("boil").performClick()
        onNodeWithText("available").assertIsDisplayed()
        onNodeWithText("BOIL", useUnmergedTree = true).performClick()
        assertEquals(listOf("boil"), appended)
        assertTrue(selected.isEmpty())

        // Back out of the reader lands on the tree again, not out of the screen.
        runOnIdle { backStep.stepBack() }
        runOnIdle { backStep.stepBack() }
        onNodeWithText("Pick a command to drop it into the input — nothing here runs on its own.").assertIsDisplayed()
        assertEquals(1, closed)
    }

    @Test
    fun commandGuideReportsAReaderTapAsIncompleteWithoutAnAppendCallback() = runComposeUiTest {
        val selected = mutableListOf<Pair<List<String>, Boolean>>()
        setContent {
            H2g2Theme {
                H2g2CommandGuideScreen(
                    tree = emptyList(),
                    fullscreen = false,
                    onCommandSelected = { tokens, complete -> selected += tokens to complete },
                    onBack = {},
                    backStep = BackStepState()
                )
            }
        }
        onNodeWithContentDescription("Open the Guide: command reference and walkthroughs").performClick()
        onNodeWithText("THE GUIDE").assertIsDisplayed()
        onNodeWithText("ls").performClick()
        onNodeWithText("LS", useUnmergedTree = true).performClick()
        assertEquals(listOf(listOf("ls") to false), selected)
    }
}
