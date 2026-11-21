package com.hereliesaz.conveyance.h2g2.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.hereliesaz.conveyance.h2g2.H2g2Theme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Smoke tests for the three standalone screens: each is composed inside [H2g2Theme] with sample
 * data, and what the host hands in (and gets back through its callbacks) is asserted.
 */
@OptIn(ExperimentalTestApi::class)
class ScreensUiTest {

    // ---- Editor ----

    @Test
    fun editorShowsTheFileAndReportsEditsAndSave() = runComposeUiTest {
        var content by mutableStateOf<String?>("echo hi")
        var saved = 0
        var wrap by mutableStateOf(true)
        setContent {
            H2g2Theme {
                H2g2EditorScreen(
                    fileName = "run.sh",
                    content = content,
                    error = null,
                    dirty = true,
                    onContentChange = { content = it },
                    onSave = { saved++ },
                    onBack = {},
                    wrap = wrap,
                    onWrapChange = { wrap = it }
                )
            }
        }
        onNodeWithText("RUN.SH").assertIsDisplayed()
        onNodeWithText("echo hi").assertIsDisplayed()
        onNodeWithContentDescription("Save file").performClick()
        assertEquals(1, saved)
        // The unwrapped layout (a wide field in a sideways scroller) composes too.
        onNodeWithContentDescription("Soft wrap long lines").performClick()
        assertEquals(false, wrap)
        waitForIdle()
        onNodeWithText("echo hi").assertIsDisplayed()
    }

    @Test
    fun editorSaysLoadingUntilContentArrivesAndHidesSave() = runComposeUiTest {
        setContent {
            H2g2Theme {
                H2g2EditorScreen("notes.txt", content = null, error = null, dirty = false, onContentChange = {}, onSave = {}, onBack = {})
            }
        }
        onNodeWithText("LOADING…").assertIsDisplayed()
        assertEquals(0, onAllNodesWithContentDescriptionCount("Save file"))
    }

    @Test
    fun aFailedSaveIsABannerOverTheTextNotAReplacementForIt() = runComposeUiTest {
        var dismissed = 0
        setContent {
            H2g2Theme {
                H2g2EditorScreen(
                    "notes.txt", content = "my edits", error = "Could not save", dirty = true,
                    onContentChange = {}, onSave = {}, onBack = {}, onDismissError = { dismissed++ }
                )
            }
        }
        onNodeWithText("Could not save").assertIsDisplayed()
        onNodeWithText("my edits").assertIsDisplayed()
        onNodeWithText("DISMISS").performClick()
        assertEquals(1, dismissed)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.onAllNodesWithContentDescriptionCount(label: String): Int =
        onAllNodes(androidx.compose.ui.test.hasContentDescription(label)).fetchSemanticsNodes().size

    // ---- Store ----

    private fun listing(id: String, kind: String = "code", installed: Boolean = false, trust: String = "") = AzpListing(
        id = id, name = "Pkg $id", author = "ada", description = "Does $id things",
        version = "1.2", kind = kind, installed = installed, trust = trust
    )

    @Test
    fun storeStartsWithTheIdleHintAndSearchesWithTheTypedQuery() = runComposeUiTest {
        var searched: Pair<String, String>? = null
        setContent {
            H2g2Theme {
                H2g2AzpStoreScreen(
                    fullscreen = false, results = emptyList(), busy = false, installingId = null,
                    onSearch = { q, k -> searched = q to k }, onInstall = {}, onBack = {}
                )
            }
        }
        onNodeWithText("STORE").assertIsDisplayed()
        onNodeWithText("Search the registry by name", substring = true).assertIsDisplayed()
        onNodeWithContentDescription("Search packages").performTextInput(" git ")
        onNodeWithText("SEARCH").performClick()
        assertEquals("git" to "all", searched)
        waitForIdle()
        // Searched, nothing came back, no error: the empty answer is said in words.
        onNodeWithText("No packages found", substring = true).assertIsDisplayed()
    }

    @Test
    fun storeListsInstalledAboveResultsAndInstallsAnInertKindDirectly() = runComposeUiTest {
        var installedPick: AzpListing? = null
        val code = listing("b")
        setContent {
            H2g2Theme {
                H2g2AzpStoreScreen(
                    fullscreen = false, results = listOf(code), busy = false, installingId = null,
                    onSearch = { _, _ -> }, onInstall = { installedPick = it }, onBack = {},
                    onUninstall = {}, installed = listOf(listing("a", installed = true, trust = "TRUSTED"))
                )
            }
        }
        onNode(androidx.compose.ui.test.hasText("INSTALLED") and androidx.compose.ui.test.isHeading()).assertIsDisplayed()
        onNodeWithText("REGISTRY").assertIsDisplayed()
        onNodeWithText("Pkg a").assertIsDisplayed()
        onNodeWithText("✓ TRUSTED SIGNER").assertIsDisplayed()
        onNodeWithContentDescription("Pkg a is installed").assertIsNotEnabled()
        onNodeWithContentDescription("Install Pkg b").performClick()
        assertEquals(code, installedPick)
    }

    @Test
    fun aSkillIsOnlyInstalledAfterTheStoreAsks() = runComposeUiTest {
        var installedPick: AzpListing? = null
        val skill = listing("s", kind = "skill")
        setContent {
            H2g2Theme {
                H2g2AzpStoreScreen(
                    fullscreen = false, results = listOf(skill), busy = false, installingId = null,
                    onSearch = { _, _ -> }, onInstall = { installedPick = it }, onBack = {}
                )
            }
        }
        onNodeWithContentDescription("Install Pkg s").performClick()
        waitForIdle()
        assertNull(installedPick)
        onNodeWithText("Install unverified skill?", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun removingAsksFirst() = runComposeUiTest {
        var removed: AzpListing? = null
        setContent {
            H2g2Theme {
                H2g2AzpStoreScreen(
                    fullscreen = false, results = emptyList(), busy = false, installingId = null,
                    onSearch = { _, _ -> }, onInstall = {}, onBack = {},
                    onUninstall = { removed = it }, installed = listOf(listing("a", installed = true)),
                    error = "Registry unreachable", installError = "bad signature"
                )
            }
        }
        onNodeWithText("Registry unreachable").assertIsDisplayed()
        onNodeWithText("Install failed: bad signature").assertIsDisplayed()
        onNodeWithContentDescription("Remove Pkg a").performClick()
        waitForIdle()
        assertNull(removed)
        onNodeWithText("REMOVE PKG A?").assertIsDisplayed()
    }

    // ---- AI chat ----

    @Test
    fun chatWithoutAKeyOnlyOffersSettings() = runComposeUiTest {
        var opened = 0
        setContent {
            H2g2Theme {
                H2g2AiChatScreen(
                    fullscreen = false, messages = emptyList(), apiKeyConfigured = false, busy = false,
                    onAsk = {}, onUseCommand = {}, onOpenSettings = { opened++ }, onBack = {}
                )
            }
        }
        onNodeWithText("Choose an AI provider", substring = true).assertIsDisplayed()
        onNodeWithContentDescription("Open AI settings").performClick()
        assertEquals(1, opened)
        assertEquals(0, onAllNodesWithContentDescriptionCount("Ask the AI for a command"))
    }

    @Test
    fun chatSendsTheTrimmedQuestionAndClearsTheField() = runComposeUiTest {
        var asked: String? = null
        setContent {
            H2g2Theme {
                H2g2AiChatScreen(
                    fullscreen = false, messages = emptyList(), apiKeyConfigured = true, busy = false,
                    onAsk = { asked = it }, onUseCommand = {}, onOpenSettings = {}, onBack = {}
                )
            }
        }
        onNodeWithText("Describe what you want to do and the AI suggests", substring = true).assertIsDisplayed()
        onNodeWithContentDescription("Ask the AI for a command").performTextInput("  list files ")
        onNodeWithText("SEND").performClick()
        assertEquals("list files", asked)
        waitForIdle()
        onNodeWithText("Describe what you want to do").assertIsDisplayed()
    }

    @Test
    fun chatShowsBubblesIntentPartsAndHandsTheCommandBack() = runComposeUiTest {
        var used: String? = null
        val supplied = AiIntent(AiIntentKind.AUTHORITY_REQUEST, "hg2auth root id", "root", listOf("Requests elevated authority."))
        val messages = listOf(
            AiMessage(fromUser = true, text = "who am i as root"),
            AiMessage(fromUser = false, text = "Try this.", command = "hg2auth root id", parts = listOf("id" to "prints the user"), intent = supplied),
            AiMessage(fromUser = false, text = "Or install it.", command = "pkg install git")
        )
        setContent {
            H2g2Theme {
                H2g2AiChatScreen(
                    fullscreen = false, messages = messages, apiKeyConfigured = true, busy = true,
                    onAsk = {}, onUseCommand = { used = it }, onOpenSettings = {}, onBack = {},
                    // The host's own analysis, consulted only where a message carries no intent.
                    analyzeIntent = { command ->
                        AiIntent(AiIntentKind.PACKAGE_MUTATION, command, consequences = listOf("Changes installed package state."))
                    }
                )
            }
        }
        onNodeWithText("who am i as root").assertIsDisplayed()
        onNodeWithText("YOU").assertIsDisplayed()
        onNodeWithText("AUTHORITY REQUEST · ROOT AUTHORITY").assertIsDisplayed()
        onNodeWithText("• Requests elevated authority.").assertIsDisplayed()
        onNodeWithText("WHAT EACH PART DOES").assertIsDisplayed()
        onNodeWithText("prints the user").assertIsDisplayed()
        onNodeWithText("PACKAGE MUTATION · APP AUTHORITY").assertIsDisplayed()
        onNodeWithText("Thinking…").assertIsDisplayed()
        onAllNodes(androidx.compose.ui.test.hasContentDescription("Use this command in the terminal"))[0].performClick()
        assertEquals("hg2auth root id", used)
    }

    @Test
    fun withoutAnAnalysisNoAuthorityPanelIsDrawn() = runComposeUiTest {
        setContent {
            H2g2Theme {
                H2g2AiChatScreen(
                    fullscreen = false,
                    messages = listOf(AiMessage(fromUser = false, text = "Try this.", command = "ls -la")),
                    apiKeyConfigured = true, busy = false,
                    onAsk = {}, onUseCommand = {}, onOpenSettings = {}, onBack = {}
                )
            }
        }
        onNodeWithText("Try this.").assertIsDisplayed()
        assertEquals(0, onAllNodes(androidx.compose.ui.test.hasText("AUTHORITY", substring = true)).fetchSemanticsNodes().size)
    }
}
