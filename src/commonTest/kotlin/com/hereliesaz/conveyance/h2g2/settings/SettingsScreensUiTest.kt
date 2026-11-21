package com.hereliesaz.conveyance.h2g2.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.hereliesaz.conveyance.h2g2.H2g2Theme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Smoke tests for the six settings-family screens: each is composed with sample state inside
 * [H2g2Theme], its title and a couple of controls are found, and one control is clicked to check
 * the callback carries the right value. They pin the wiring (a screen that renders but whose chips
 * call nothing would still look right), not the pixels.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsScreensUiTest {

    // ----- Settings --------------------------------------------------------------------------

    private class SettingsCalls {
        var fullscreen: Boolean? = null
        var fontScale: Int? = null
        var pty: Boolean? = null
        var shell: String? = null
        var updateCheck: UpdateCheckPreference? = null
        var opened: String? = null
        var url: String? = null
        var back = 0
    }

    private fun settings(state: SettingsState, calls: SettingsCalls, body: androidx.compose.ui.test.ComposeUiTest.() -> Unit) =
        runComposeUiTest {
            setContent {
                H2g2Theme {
                    H2g2SettingsScreen(
                        state = state,
                        onFullscreenChange = { calls.fullscreen = it },
                        onFontScalePercentChange = { calls.fontScale = it },
                        onUsePtyChange = { calls.pty = it },
                        onShellChange = { calls.shell = it },
                        onUpdateCheckChange = { calls.updateCheck = it },
                        onOpenMcpServer = { calls.opened = "mcp" },
                        onOpenAiSettings = { calls.opened = "ai" },
                        onOpenEnvironment = { calls.opened = "environment" },
                        onOpenHistory = { calls.opened = "history" },
                        onOpenIsolationAudit = { calls.opened = "isolation" },
                        onOpenMotionSettings = { calls.opened = "motion" },
                        onOpenUrl = { calls.url = it },
                        onBack = { calls.back++ }
                    )
                }
            }
            body()
        }

    @Test
    fun settingsShowsItsTitleAndReportsEachControl() {
        val calls = SettingsCalls()
        val state = SettingsState(
            fontScalePercent = 120,
            availableShells = listOf(ShellPreference.BASH, ShellPreference.ZSH),
            shell = ShellPreference.BASH,
            versionName = "9.9.9"
        )
        settings(state, calls) {
            onNodeWithText("SETTINGS").assertExists()
            onNodeWithText("120%").assertExists()
            onNodeWithContentDescription("Default shell: bash", substring = true, ignoreCase = true).assertExists()

            onNodeWithContentDescription("Fullscreen: Fullscreen").performClick()
            assertEquals(true, calls.fullscreen)

            onNodeWithContentDescription("Increase text size").performClick()
            assertEquals(130, calls.fontScale)
            onNodeWithContentDescription("Decrease text size").performClick()
            assertEquals(110, calls.fontScale)
            onNodeWithText("RESET TO DEFAULT").performClick()
            assertEquals(FONT_SCALE_DEFAULT_PERCENT, calls.fontScale)

            onNodeWithText("ZSH").performScrollTo().performClick()
            assertEquals("zsh", calls.shell)

            onNodeWithContentDescription("Search history").performScrollTo().performClick()
            assertEquals("history", calls.opened)

            onNodeWithContentDescription("Update check: Off").performScrollTo().performClick()
            assertEquals(UpdateCheckPreference.Off, calls.updateCheck)

            onNodeWithContentDescription("Open the privacy policy").performScrollTo().performClick()
            assertEquals(PRIVACY_POLICY_URL, calls.url)

            onNodeWithContentDescription("Back").performScrollTo().performClick()
            assertEquals(1, calls.back)
        }
    }

    @Test
    fun settingsLiftsALegacyTextSizeToTheFloorAndDisablesMinusThere() {
        val calls = SettingsCalls()
        settings(SettingsState(fontScalePercent = 50), calls) {
            waitForIdle()
            assertEquals(FONT_SCALE_MIN_PERCENT, calls.fontScale)
        }
        settings(SettingsState(fontScalePercent = FONT_SCALE_MIN_PERCENT), SettingsCalls()) {
            onNodeWithContentDescription("Decrease text size").assertIsNotEnabled()
        }
    }

    @Test
    fun settingsHighlightsAnInstalledShellWhenTheStoredOneIsGone() {
        settings(SettingsState(availableShells = listOf("bash"), shell = "fish"), SettingsCalls()) {
            onNodeWithText("BASH").assertIsSelected()
        }
    }

    // ----- AI settings -----------------------------------------------------------------------

    private val savedAi = AiSettingsState(
        config = AiConfig(AiProviders.OpenAi, model = "gpt-5-mini"),
        apiKeys = mapOf(AiProviders.OpenAi to "sk-open-123")
    )

    @Test
    fun aiSettingsShowsTheProvidersAndSavesTheTrimmedChoice() = runComposeUiTest {
        var savedConfig: AiConfig? = null
        var savedKey: String? = "unset"
        var backs = 0
        setContent {
            H2g2Theme {
                var draft by remember { mutableStateOf(AiSettingsDraft.of(savedAi)) }
                H2g2AiSettingsScreen(
                    fullscreen = false,
                    state = savedAi,
                    draft = draft,
                    onDraftChange = { draft = it },
                    onSave = { config, key -> savedConfig = config; savedKey = key },
                    onBack = { backs++ }
                )
            }
        }
        onNodeWithText("AI SETTINGS").assertExists()
        onNodeWithText("OPENAI").assertIsSelected()
        onNodeWithText("ANTHROPIC").assertExists()
        // The saved key is never drawn until the user asks for it.
        onNodeWithText("sk-open-123").assertDoesNotExist()
        onNodeWithText("•••• Key set — tap to edit").assertExists()

        onNodeWithContentDescription("Model").performTextInput(" ")
        onNodeWithText("SAVE").performScrollTo().performClick()
        assertEquals(AiConfig(AiProviders.OpenAi, "gpt-5-mini", ""), savedConfig)
        assertEquals("sk-open-123", savedKey)

        // Nothing but whitespace changed, so Back leaves without asking.
        onNodeWithContentDescription("Back").performScrollTo().performClick()
        assertEquals(1, backs)
    }

    @Test
    fun aiSettingsSwitchesProviderAndAsksBeforeDiscarding() = runComposeUiTest {
        var saved: Pair<AiConfig, String?>? = null
        var backs = 0
        var latest: AiSettingsDraft? = null
        setContent {
            H2g2Theme {
                var draft by remember { mutableStateOf(AiSettingsDraft.of(savedAi)) }
                latest = draft
                H2g2AiSettingsScreen(
                    fullscreen = false,
                    state = savedAi,
                    draft = draft,
                    onDraftChange = { draft = it },
                    onSave = { config, key -> saved = config to key },
                    onBack = { backs++ }
                )
            }
        }
        // Ollama takes no key and has an editable endpoint.
        onNodeWithText("OLLAMA (LOCAL)").performClick()
        waitForIdle()
        assertEquals(AiProviders.Ollama, latest?.provider)
        onNodeWithContentDescription("Endpoint", substring = true, ignoreCase = true).assertExists()
        onNodeWithText("•••• Key set — tap to edit").assertDoesNotExist()

        onNodeWithText("SAVE").performScrollTo().performClick()
        assertEquals(AiConfig(AiProviders.Ollama, "", "") to null, saved)

        onNodeWithContentDescription("Back").performScrollTo().performClick()
        assertEquals(0, backs)
        onNodeWithText("DISCARD CHANGES?").assertExists()
        onNodeWithText("KEEP EDITING").performClick()
        waitForIdle()
        assertEquals(0, backs)
        assertFalse(latest!!.confirmDiscard)

        onNodeWithContentDescription("Back").performScrollTo().performClick()
        onNodeWithText("DISCARD").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun aiSettingsRevealsTheKeyOnlyOnRequestAndReportsAFailedSave() = runComposeUiTest {
        var latest: AiSettingsDraft? = null
        setContent {
            H2g2Theme {
                var draft by remember { mutableStateOf(AiSettingsDraft.of(savedAi)) }
                latest = draft
                H2g2AiSettingsScreen(
                    fullscreen = false,
                    state = savedAi.copy(saveFailed = true),
                    draft = draft,
                    onDraftChange = { draft = it },
                    onSave = { _, _ -> },
                    onBack = {}
                )
            }
        }
        onNodeWithText("SHOW KEY").performScrollTo().performClick()
        waitForIdle()
        assertTrue(latest!!.showKey)
        onNodeWithText("sk-open-123").assertExists()
        onNodeWithText("HIDE KEY").assertExists()
        onNodeWithText("could not", substring = true, ignoreCase = true).assertExists()
    }

    // ----- MCP server ------------------------------------------------------------------------

    @Test
    fun mcpServerOffOffersOnlyTheSwitchAndStartsOnRequest() = runComposeUiTest {
        var started = 0
        var settingsOpened = 0
        setContent {
            H2g2Theme {
                H2g2McpServerScreen(
                    fullscreen = false,
                    state = McpServerState(running = false, port = 8765, notificationsDenied = true),
                    ui = McpServerUiState(),
                    onUiChange = {},
                    onStart = { started++ },
                    onStop = {},
                    onRequestShellExec = {},
                    onDisableShellExec = {},
                    onCopy = { _, _, _ -> },
                    onOpenNotificationSettings = { settingsOpened++ },
                    onBack = {}
                )
            }
        }
        onNodeWithText("MCP SERVER").assertExists()
        onNodeWithText("COPY TOKEN").assertDoesNotExist()
        onNodeWithContentDescription("Server: On").performClick()
        assertEquals(1, started)
        onNodeWithText("OPEN APP SETTINGS").performClick()
        assertEquals(1, settingsOpened)
    }

    @Test
    fun mcpServerRunningHidesTheTokenCopiesItAsSensitiveAndGatesShellExec() = runComposeUiTest {
        val copies = mutableListOf<Triple<String, String, Boolean>>()
        var shellRequested = 0
        var stopped = 0
        var latest = McpServerUiState()
        setContent {
            H2g2Theme {
                var ui by remember { mutableStateOf(McpServerUiState()) }
                latest = ui
                H2g2McpServerScreen(
                    fullscreen = false,
                    state = McpServerState(running = true, port = 8765, token = "tok-abc"),
                    ui = ui,
                    onUiChange = { ui = it },
                    onStart = {},
                    onStop = { stopped++ },
                    onRequestShellExec = { shellRequested++ },
                    onDisableShellExec = {},
                    onCopy = { label, text, sensitive -> copies += Triple(label, text, sensitive) },
                    onOpenNotificationSettings = {},
                    onBack = {}
                )
            }
        }
        onNodeWithContentDescription("Pairing token, hidden").assertExists()
        onNodeWithContentDescription("Pairing token: tok-abc").assertDoesNotExist()

        onNodeWithText("SHOW TOKEN").performClick()
        waitForIdle()
        assertTrue(latest.tokenRevealed)
        onNodeWithContentDescription("Pairing token: tok-abc").assertExists()

        onNodeWithContentDescription("Copy the pairing token to the clipboard").performClick()
        waitForIdle()
        assertEquals(Triple(MCP_TOKEN_CLIP_LABEL, "tok-abc", true), copies.last())
        assertEquals(McpCopied.Token, latest.copied)
        onNodeWithText("Pairing token copied to the clipboard.").assertExists()

        onNodeWithContentDescription("Copy the adb forward command to the clipboard").performScrollTo().performClick()
        assertEquals(Triple(MCP_CONNECT_CLIP_LABEL, "adb forward tcp:8765 tcp:8765", false), copies.last())

        onNodeWithContentDescription("Shell execution: On").performScrollTo().performClick()
        assertEquals(1, shellRequested)
        onNodeWithContentDescription("Server: Off").performScrollTo().performClick()
        assertEquals(1, stopped)

        // The "copied" line is transient: it clears itself after three seconds.
        mainClock.advanceTimeBy(3_500L)
        waitForIdle()
        assertNull(latest.copied)
    }

    // ----- Environment -----------------------------------------------------------------------

    @Test
    fun environmentListsPathAndVariablesAndGoesBack() = runComposeUiTest {
        var backs = 0
        setContent {
            H2g2Theme {
                H2g2EnvironmentScreen(
                    state = EnvironmentState(
                        env = mapOf("PATH" to "/usr/bin:/bin", "HOME" to "/home/az", "TERM" to "xterm-256color")
                    ),
                    onBack = { backs++ },
                    fullscreen = false
                )
            }
        }
        onNodeWithText("ENVIRONMENT").assertExists()
        onNodeWithText("PATH (2)").assertExists()
        onNodeWithText("/usr/bin").assertExists()
        onNodeWithText("VARIABLES (2)").assertExists()
        onNodeWithText("HOME").assertExists()
        onNodeWithText("xterm-256color").assertExists()
        onNodeWithContentDescription("Back to settings").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun environmentSaysSoWhenLoadingOrNotInstalled() {
        runComposeUiTest {
            setContent { H2g2Theme { H2g2EnvironmentScreen(EnvironmentState(loading = true), onBack = {}, fullscreen = false) } }
            onNodeWithText("Reading the environment…").assertExists()
            onNodeWithText("PATH (0)").assertDoesNotExist()
        }
        runComposeUiTest {
            setContent { H2g2Theme { H2g2EnvironmentScreen(EnvironmentState(installed = false), onBack = {}, fullscreen = false) } }
            onNodeWithText("No Termux bootstrap is installed", substring = true).assertExists()
        }
    }

    // ----- History ---------------------------------------------------------------------------

    private val entries = listOf(
        CommandHistoryEntry(timestampMs = 1_000_000L, sessionLabel = "tab 1", command = "ls -la"),
        CommandHistoryEntry(timestampMs = 400_000L, sessionLabel = "tab 2", command = "git status")
    )

    @Test
    fun historyListsCommandsAndHandsBackSelectionDeletionAndSearch() = runComposeUiTest {
        var selected: String? = null
        var deleted: CommandHistoryEntry? = null
        var query: String? = null
        var backs = 0
        setContent {
            H2g2Theme {
                H2g2HistoryScreen(
                    state = HistoryState(entries = entries, nowMillis = 1_000_000L + 5 * 60_000L),
                    onQueryChange = { query = it },
                    onSelect = { selected = it },
                    onDelete = { deleted = it },
                    onConfirmClearChange = {},
                    onClear = {},
                    onBack = { backs++ },
                    fullscreen = false
                )
            }
        }
        onNodeWithText("HISTORY").assertExists()
        onNodeWithText("SEARCH 2 COMMANDS").assertExists()
        onNodeWithText("tab 1 · 5m ago").assertExists()

        onNodeWithText("git status").performClick()
        assertEquals("git status", selected)
        onNodeWithContentDescription("Delete ls -la from history").performClick()
        assertEquals(entries[0], deleted)
        onNodeWithContentDescription("Search command history").performTextInput("git")
        assertEquals("git", query)
        onNodeWithContentDescription("Back to settings").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun historyClearsOnlyAfterTheConfirmation() = runComposeUiTest {
        var cleared = 0
        var confirming = false
        setContent {
            H2g2Theme {
                var state by remember { mutableStateOf(HistoryState(entries = entries, nowMillis = 2_000_000L)) }
                confirming = state.confirmClear
                H2g2HistoryScreen(
                    state = state,
                    onQueryChange = {},
                    onSelect = {},
                    onDelete = {},
                    onConfirmClearChange = { state = state.copy(confirmClear = it) },
                    onClear = { cleared++ },
                    onBack = {},
                    fullscreen = false
                )
            }
        }
        onNodeWithContentDescription("Clear all command history").performClick()
        waitForIdle()
        assertTrue(confirming)
        assertEquals(0, cleared)
        onNodeWithText("CLEAR HISTORY?").assertExists()
        onNodeWithText("CLEAR").performClick()
        waitForIdle()
        assertEquals(1, cleared)
        assertFalse(confirming)
    }

    @Test
    fun historyDistinguishesLoadingEmptyAndNoMatches() {
        fun show(state: HistoryState, check: androidx.compose.ui.test.ComposeUiTest.() -> Unit) = runComposeUiTest {
            setContent {
                H2g2Theme {
                    H2g2HistoryScreen(state, {}, {}, {}, {}, {}, {}, fullscreen = false)
                }
            }
            check()
        }
        show(HistoryState(loading = true)) {
            onNodeWithText("Loading history…").assertExists()
            onNodeWithText("No commands recorded yet.").assertDoesNotExist()
        }
        show(HistoryState()) {
            onNodeWithText("No commands recorded yet.").assertExists()
            onNodeWithContentDescription("Clear all command history").assertDoesNotExist()
        }
        show(HistoryState(query = "zzz")) { onNodeWithText("No matches for “zzz”.").assertExists() }
    }

    // ----- Isolation audit -------------------------------------------------------------------

    private val audited = IsolationAuditEntry(
        packageName = "demo-tool",
        managerLabel = "pkg",
        version = "1.2",
        savedAudit = SavedIsolationAudit(
            timestampMillis = 951_827_696_000L,
            audit = IsolationAudit(
                created = (1..45).map { "/sandbox/out/file$it" },
                modified = listOf("/sandbox/etc/conf"),
                deleted = emptyList(),
                telemetry = listOf(
                    "authority-attempt:su -c id",
                    "network:tcp:0100007F:1F90:0A01020B:01BB:01:123",
                    "process:sh -c build",
                    "file-write:/sandbox/out/file1"
                )
            )
        ),
        fileCount = 46,
        bytes = 2048L
    )

    @Test
    fun isolationAuditGroupsWhatWasObservedAndRefreshes() = runComposeUiTest {
        var refreshed = 0
        var backs = 0
        var latest = IsolationAuditUiState()
        setContent {
            H2g2Theme {
                var ui by remember { mutableStateOf(IsolationAuditUiState()) }
                latest = ui
                H2g2IsolationAuditScreen(
                    state = IsolationAuditState(entries = listOf(audited)),
                    ui = ui,
                    onUiChange = { ui = it },
                    onRefresh = { refreshed++ },
                    onAskAi = { _, _ -> },
                    onBack = { backs++ },
                    fullscreen = false
                )
            }
        }
        onNodeWithText("ISOLATION AUDIT").assertExists()
        onNodeWithText("demo-tool").assertExists()
        onNodeWithText("pkg · 1.2 · 46 sandbox files · 2.0 KiB").assertExists()
        onNodeWithText("CAPTURED 2000-02-29 12:34:56 UTC").assertExists()
        onNodeWithText("SANDBOX DIFF · +45  ~1  −0").assertExists()
        onNodeWithText("RUNTIME · 4 OBSERVATIONS").assertExists()
        listOf("AUTHORITY", "NETWORK", "PROCESS", "FILES").forEach { onNodeWithText(it).assertExists() }
        onNodeWithText("OTHER").assertDoesNotExist()
        onNodeWithText("su -c id").assertExists()
        // No AI provider is set up, so nothing offers to send anything.
        onNodeWithText("ASK AI · SEND SUMMARY").assertDoesNotExist()

        // 45 created paths: 40 shown, the rest behind the chip.
        onNodeWithText("+ /sandbox/out/file45").assertDoesNotExist()
        onNodeWithText("… 5 MORE").performScrollTo().performClick()
        waitForIdle()
        assertEquals(45, latest.shownRows["demo-tool/created"])
        onNodeWithText("+ /sandbox/out/file45").assertExists()
        onNodeWithText("SHOW LESS").performScrollTo().performClick()
        waitForIdle()
        assertEquals(40, latest.shownRows["demo-tool/created"])

        onNodeWithContentDescription("Refresh audits").performScrollTo().performClick()
        assertEquals(1, refreshed)
        onNodeWithContentDescription("Back").performScrollTo().performClick()
        assertEquals(1, backs)
    }

    @Test
    fun isolationAuditSendsTheRedactedSummaryOnlyAfterTheDisclosure() = runComposeUiTest {
        val asked = mutableListOf<Pair<IsolationAuditEntry, String>>()
        setContent {
            H2g2Theme {
                var ui by remember { mutableStateOf(IsolationAuditUiState()) }
                H2g2IsolationAuditScreen(
                    state = IsolationAuditState(entries = listOf(audited), aiReady = true, aiProviderLabel = "Gemini"),
                    ui = ui,
                    onUiChange = { ui = it },
                    onRefresh = {},
                    onAskAi = { entry, prompt -> asked += entry to prompt },
                    onBack = {},
                    fullscreen = false
                )
            }
        }
        onNodeWithText("ASK AI · SEND SUMMARY").performScrollTo().performClick()
        assertTrue(asked.isEmpty())
        onNodeWithText("SEND AUDIT SUMMARY TO AI?").assertExists()
        onNodeWithText("to Gemini, using your API key", substring = true).assertExists()
        onNodeWithText("SEND").performClick()
        waitForIdle()
        assertEquals(1, asked.size)
        assertEquals(audited, asked[0].first)
        assertEquals(auditInterpretationPrompt(audited), asked[0].second)
        assertFalse(asked[0].second.contains("0A01020B"))
        onNodeWithText("SEND AUDIT SUMMARY TO AI?").assertDoesNotExist()
    }

    @Test
    fun isolationAuditShowsBusyResultAndTheEmptyStates() {
        fun show(state: IsolationAuditState, check: androidx.compose.ui.test.ComposeUiTest.() -> Unit) = runComposeUiTest {
            setContent {
                H2g2Theme {
                    H2g2IsolationAuditScreen(state, IsolationAuditUiState(), {}, {}, { _, _ -> }, {}, fullscreen = false)
                }
            }
            check()
        }
        show(IsolationAuditState(entries = null, refreshing = true)) {
            onNodeWithText("READING AUDITS…").assertExists()
            onNodeWithContentDescription("Refreshing audits").assertIsNotEnabled()
        }
        show(IsolationAuditState(entries = emptyList())) { onNodeWithText("No isolated packages.").assertExists() }
        show(IsolationAuditState(entries = listOf(audited.copy(savedAudit = null, version = "")))) {
            onNodeWithText("pkg · installed · 46 sandbox files · 2.0 KiB").assertExists()
            onNodeWithText("No completed-run audit yet. Run this isolated package, then refresh.").assertExists()
        }
        show(
            IsolationAuditState(
                entries = listOf(audited),
                aiReady = true,
                ai = mapOf(audited.auditKey to AuditAiState(result = "Looks routine.", busy = true))
            )
        ) {
            onNodeWithContentDescription("Asking the AI").assertIsNotEnabled()
            onNodeWithText("AI INTERPRETATION").assertExists()
            onNodeWithText("Looks routine.").assertExists()
        }
    }
}
