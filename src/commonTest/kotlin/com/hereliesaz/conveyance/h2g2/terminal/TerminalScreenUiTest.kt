package com.hereliesaz.conveyance.h2g2.terminal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.hereliesaz.conveyance.h2g2.H2g2Theme
import com.hereliesaz.conveyance.h2g2.Text
import com.hereliesaz.conveyance.h2g2.menu.MenuNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Smoke tests for the terminal package's public composables: each is really composed inside
 * [H2g2Theme] with sample data and something visible is asserted. The screen tests also drive the
 * composer end to end (type, RUN, output card) through the host callbacks, since that wiring is
 * what a host depends on.
 */
@OptIn(ExperimentalTestApi::class)
class TerminalScreenUiTest {

    private val tree = listOf(
        MenuNode(
            id = "disk",
            label = "Disk",
            emitsToken = false,
            children = listOf(MenuNode(id = "files-ls", label = "ls", value = "ls"))
        ),
        MenuNode(id = "git", label = "git", children = listOf(MenuNode(id = "git-status", label = "status")))
    )

    @Test
    fun terminalScreenShowsTabsStatusLineAndComposer() = runComposeUiTest {
        val session = SessionUiState(id = "1", name = "bash", cwd = "/home/zaphod")
        setContent {
            H2g2Theme {
                H2g2TerminalScreen(
                    tree = tree,
                    sessions = listOf(session),
                    activeSessionId = "1",
                    onSessionPick = {},
                    onNewSession = {},
                    onCloseSession = {},
                    fullscreen = true,
                    onOpenSettings = {},
                    onOpenGuide = {},
                    onOpenFiles = {},
                    // The screen re-derives the status line itself through the probe.
                    shellProbe = { _, command ->
                        if (command == ShellStatusProjector.GIT_STATUS_COMMAND) "## main...origin/main\n M README.md" else null
                    },
                    onRun = { _, _, _, _, _, _, _ -> }
                )
            }
        }
        onNodeWithText("BASH").assertExists()
        onNodeWithText("FILES").assertExists()
        onNodeWithText("DISK").assertExists()
        onNodeWithText("/home/zaphod").assertExists()
        onNodeWithContentDescription("Git branch main, uncommitted changes").assertExists()
        onNodeWithText("PICK A CATEGORY").assertExists()
        onNodeWithText("RUN").assertExists()
        onNodeWithText("01 — COMMAND TREE").assertExists()
        onNodeWithContentDescription("Control. Arms the next typed letter as a control key").assertExists()
    }

    @Test
    fun typingAndRunHandsTheLineToTheHostAndShowsItsOutput() = runComposeUiTest {
        val session = SessionUiState(id = "1", name = "bash", cwd = "/home/zaphod")
        val ran = mutableListOf<String>()
        var completionLine: String? = null
        setContent {
            H2g2Theme {
                H2g2TerminalScreen(
                    tree = tree,
                    sessions = listOf(session),
                    activeSessionId = "1",
                    onSessionPick = {},
                    onNewSession = {},
                    onCloseSession = {},
                    fullscreen = true,
                    onOpenSettings = {},
                    onOpenGuide = {},
                    onOpenFiles = {},
                    complete = { request ->
                        completionLine = request.line
                        emptyList()
                    },
                    onRun = { _, line, onOutput, _, onExit, _, _ ->
                        ran += line
                        onOutput("forty-two")
                        onExit(0)
                    }
                )
            }
        }
        onNodeWithContentDescription("Command line").performTextInput("ll /tmp")
        waitForIdle()
        onNodeWithText("READY — PRESS RUN").assertExists()
        onNodeWithText("RUN").performClick()
        waitForIdle()
        // The alias is expanded for the shell, but the card and history keep what the user typed.
        assertEquals(listOf("ls -la /tmp"), ran)
        assertEquals(listOf("ll /tmp"), session.commandHistory)
        assertEquals("", session.inputText)
        val entry = session.buffer.single()
        assertEquals("ll /tmp", entry.command)
        assertEquals("forty-two", entry.output)
        assertEquals(0, entry.exitCode)
        assertTrue(!entry.isRunning && !session.running)
        onNodeWithText("ll /tmp").assertExists()
        onNodeWithText("forty-two").assertExists()
        onNodeWithText("ok").assertExists()
        onNodeWithText("00 — BUFFER").assertExists()
        // The debounced completion request saw the composed line before RUN cleared it.
        assertEquals("ll /tmp", completionLine)
    }

    @Test
    fun typesetSlotDrawsClassifiedOutput() = runComposeUiTest {
        val session = SessionUiState(id = "1", name = "bash", cwd = "/")
        session.buffer = listOf(TerminalHistoryEntry(command = "uname -a", output = "os: linux", exitCode = 0))
        setContent {
            H2g2Theme {
                H2g2TerminalScreen(
                    tree = tree,
                    sessions = listOf(session),
                    activeSessionId = "1",
                    onSessionPick = {},
                    onNewSession = {},
                    onCloseSession = {},
                    fullscreen = true,
                    onOpenSettings = {},
                    onOpenGuide = {},
                    onOpenFiles = {},
                    classifyOutput = { TerminalOutputKind.TABLE },
                    typesetOutput = { kind, text, _ -> Text("slot ${kind.name}: $text") },
                    onRun = { _, _, _, _, _, _, _ -> }
                )
            }
        }
        onNodeWithText("slot TABLE: os: linux").assertExists()
        onNodeWithText("TAP FOR COPY · RE-RUN · SHARE").assertExists()
    }

    @Test
    fun aPendingQuestionTurnsRunIntoSend() = runComposeUiTest {
        val session = SessionUiState(id = "1", name = "bash", cwd = "/")
        var answer: String? = null
        setContent {
            H2g2Theme {
                H2g2TerminalScreen(
                    tree = tree,
                    sessions = listOf(session),
                    activeSessionId = "1",
                    onSessionPick = {},
                    onNewSession = {},
                    onCloseSession = {},
                    fullscreen = true,
                    onOpenSettings = {},
                    onOpenGuide = {},
                    onOpenFiles = {},
                    onRun = { _, _, _, onNeedInput, onExit, _, _ ->
                        answer = onNeedInput("Workflow name?")
                        onExit(0)
                    }
                )
            }
        }
        onNodeWithContentDescription("Command line").performTextInput("deploy")
        onNodeWithText("RUN").performClick()
        waitForIdle()
        onNodeWithText("WORKFLOW NAME?").assertExists()
        assertNull(answer)
        onNodeWithContentDescription("Command line").performTextInput("nightly")
        onNodeWithText("SEND").performClick()
        waitForIdle()
        assertEquals("nightly", answer)
        assertNull(session.pendingPrompt)
    }

    @Test
    fun fluidWorkspaceShowsBothRegionsOnceThereIsOutput() = runComposeUiTest {
        var hasBuffer by mutableStateOf(false)
        setContent {
            H2g2Theme {
                H2g2FluidTerminalWorkspace(
                    hasBuffer = hasBuffer,
                    estimatedPillRows = 3,
                    modifier = Modifier.size(360.dp, 600.dp),
                    bufferContent = { Text("the output") },
                    commandTreeContent = { modifier -> Box(modifier) { Text("the pills") } }
                )
            }
        }
        onNodeWithText("the pills").assertExists()
        onNodeWithText("01 — COMMAND TREE").assertExists()
        onNodeWithText("the output").assertDoesNotExist()
        hasBuffer = true
        waitForIdle()
        onNodeWithText("the output").assertExists()
        onNodeWithText("00 — BUFFER").assertExists()
        onNodeWithText("the pills").assertExists()
    }

    @Test
    fun shellStatusLineShowsBranchIdentityAndFailedExit() = runComposeUiTest {
        setContent {
            H2g2Theme {
                H2g2ShellStatusLine(
                    cwd = "/srv/app",
                    presentation = ShellPresentation(
                        cwd = "/srv/app",
                        user = "ford",
                        host = "betelgeuse",
                        gitBranch = "main",
                        lastExitCode = 2
                    )
                )
            }
        }
        onNodeWithText("/srv/app").assertExists()
        onNodeWithText("GIT main").assertExists()
        onNodeWithText("ford@betelgeuse").assertExists()
        onNodeWithText("EXIT 2").assertExists()
    }

    @Test
    fun liveStatusStripShowsTheProgressFrame() = runComposeUiTest {
        setContent { H2g2Theme { H2g2LiveStatusStrip("42% [Working]") } }
        onNodeWithText("LIVE").assertExists()
        onNodeWithText("42% [Working]").assertExists()
        onNodeWithContentDescription("Live status, 40 percent").assertExists()
    }

    @Test
    fun composerKeysReportTheirNameAndShowTheArmedOne() = runComposeUiTest {
        var armed by mutableStateOf<ComposerModifier?>(null)
        val clicked = mutableListOf<String>()
        setContent {
            H2g2Theme {
                H2g2ComposerKeys(
                    armed = armed,
                    onKeyClick = { key ->
                        clicked += key
                        if (key == "ctrl") armed = ComposerModifier.Ctrl
                    }
                )
            }
        }
        onNodeWithText("TAB").assertExists()
        onNodeWithText("CTRL").performClick()
        onNodeWithText("ESC").performClick()
        waitForIdle()
        assertEquals(listOf("ctrl", "esc"), clicked)
        onNodeWithContentDescription("Control. Arms the next typed letter as a control key").assertIsSelected()
    }
}
