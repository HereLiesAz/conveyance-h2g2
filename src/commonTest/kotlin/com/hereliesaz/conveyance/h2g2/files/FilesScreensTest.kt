package com.hereliesaz.conveyance.h2g2.files

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.hereliesaz.conveyance.h2g2.H2g2Theme
import com.hereliesaz.conveyance.h2g2.components.BackStepState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * An in-memory directory tree standing in for the host's filesystem: the screens only ever see it
 * through the same callbacks a real host passes.
 *
 *     /Docs/report.txt
 *     /Docs/Sub/deep.txt
 *     /Photos/a.png  b.png  c.png
 *     /notes.txt
 *     /readme.md
 */
private class FakeTree {
    private val dirs = mutableSetOf("/", "/Docs", "/Docs/Sub", "/Photos")
    private val files = mutableMapOf(
        "/Docs/report.txt" to "quarterly figures",
        "/Docs/Sub/deep.txt" to "deep",
        "/Photos/a.png" to "",
        "/Photos/b.png" to "",
        "/Photos/c.png" to "",
        "/notes.txt" to "remember the towel",
        "/readme.md" to "# Read me\n- first point"
    )
    val trashed = mutableListOf<VfsTrashEntry>()
    val deleteCalls = mutableListOf<String>()

    private fun entry(path: String) = VfsEntry(
        name = path.substringAfterLast('/'),
        path = path,
        isDirectory = path in dirs,
        sizeBytes = files[path]?.length?.toLong() ?: 0L,
        isImage = path.endsWith(".png")
    )

    fun list(path: String): List<VfsEntry> {
        val dir = path.trimEnd('/').ifEmpty { "/" }
        return (dirs + files.keys).filter { it != "/" && vfsParentPath(it) == dir }.map(::entry)
    }

    fun search(query: String): List<VfsSearchResult> =
        (dirs + files.keys).filter { it != "/" && query.lowercase() in it.substringAfterLast('/').lowercase() }
            .map { VfsSearchResult(entry(it), vfsParentPath(it)) }

    fun stats() = StorageStats(
        totalBytes = files.values.sumOf { it.length.toLong() },
        byCategory = listOf(StorageCategoryStat("Documents", 40), StorageCategoryStat("Images", 0)),
        largest = files.keys.sortedByDescending { files[it]!!.length }.take(3).map(::entry)
    )

    fun preview(path: String): FilePreview {
        val text = files[path] ?: return FilePreview(text = null)
        return FilePreview(text = text, isMarkdown = path.endsWith(".md"))
    }

    fun delete(path: String): Boolean {
        deleteCalls += path
        val gone = files.remove(path) ?: return false
        trashed += VfsTrashEntry("t${trashed.size}", path, path.substringAfterLast('/'), false, gone.length.toLong(), 0L)
        return true
    }

    fun createFolder(parent: String, name: String): Boolean = dirs.add(vfsChildPath(parent, name))
}

@OptIn(ExperimentalTestApi::class)
class FilesScreensTest {

    private fun ComposeUiTest.showFiles(
        tree: FakeTree,
        backStep: BackStepState = BackStepState(),
        opened: MutableList<String> = mutableListOf(),
        onBack: () -> Unit = {}
    ) {
        setContent {
            H2g2Theme {
                H2g2FilesScreen(
                    fullscreen = false,
                    nowMillis = 1_000_000L,
                    listDir = { tree.list(it) },
                    search = { tree.search(it) },
                    storageStats = { tree.stats() },
                    previewFile = { tree.preview(it) },
                    onOpenFile = { opened += it },
                    onCreateFolder = { parent, name -> tree.createFolder(parent, name) },
                    onCreateFile = { _, _ -> true },
                    onDelete = { tree.delete(it) },
                    trash = TrashActions(
                        items = { tree.trashed.toList() },
                        restore = { true },
                        purge = { true },
                        empty = { 0 }
                    ),
                    deviceStorage = DeviceStorageState(active = false, granted = false, onToggle = {}),
                    onRename = { _, _ -> true },
                    onMove = { _, _ -> true },
                    onCopy = { _, _ -> true },
                    onShare = {},
                    onShareMultiple = {},
                    onBack = onBack,
                    backStep = backStep
                )
            }
        }
        waitForIdle()
    }

    // ----- H2g2FilesScreen -----------------------------------------------------------------

    @Test
    fun filesScreenShowsTheRootListing() = runComposeUiTest {
        var closed = false
        showFiles(FakeTree(), onBack = { closed = true })
        onNodeWithText("4 THINGS HERE").assertIsDisplayed()
        onNodeWithText("FOLDERS · 2").assertIsDisplayed()
        // Folders are tiles labelled in capitals; files keep their real case.
        onNodeWithText("DOCS").assertIsDisplayed()
        onNodeWithText("PHOTOS").assertIsDisplayed()
        onNodeWithText("notes.txt").assertIsDisplayed()
        onNodeWithText("readme.md").assertIsDisplayed()
        onNodeWithText("ROOT: SANDBOX").assertIsDisplayed()
        onNodeWithText("‹ CLOSE").performClick()
        assertTrue(closed)
    }

    @Test
    fun openingAFolderTileShowsItsChildrenInsideIt() = runComposeUiTest {
        val backStep = BackStepState()
        showFiles(FakeTree(), backStep)
        onNodeWithText("report.txt").assertDoesNotExist()
        assertFalse(backStep.canStepBack)

        onNodeWithText("DOCS").performClick()
        waitForIdle()
        // The folder's own children, nested under it: a subfolder tile and a file row.
        onNodeWithText("report.txt").assertIsDisplayed()
        onNodeWithText("SUB").assertIsDisplayed()
        onNodeWithText("2 THINGS HERE").assertIsDisplayed()
        // Its sibling is a rod now: too thin for a label, so it is spoken instead.
        onNodeWithText("PHOTOS").assertDoesNotExist()
        onNodeWithContentDescription("Folder Photos").assertExists()
        onNodeWithText("‹ UP").assertIsDisplayed()

        // One level deeper, then back out a level at a time through the back-step contract.
        onNodeWithText("SUB").performClick()
        waitForIdle()
        onNodeWithText("deep.txt").assertIsDisplayed()
        assertTrue(backStep.canStepBack)
        backStep.stepBack()
        waitForIdle()
        onNodeWithText("deep.txt").assertDoesNotExist()
        onNodeWithText("report.txt").assertIsDisplayed()
        backStep.stepBack()
        waitForIdle()
        onNodeWithText("report.txt").assertDoesNotExist()
        onNodeWithText("PHOTOS").assertIsDisplayed()
        assertFalse(backStep.canStepBack)
    }

    @Test
    fun tappingAFilePreviewsItInPlaceAndMarkdownIsRendered() = runComposeUiTest {
        val opened = mutableListOf<String>()
        showFiles(FakeTree(), opened = opened)
        onNodeWithText("notes.txt").performClick()
        waitForIdle()
        onNodeWithText("remember the towel").assertIsDisplayed()
        onNodeWithText("OPEN IN EDITOR").performClick()
        assertEquals(listOf("/notes.txt"), opened)

        // One preview at a time; a .md file is rendered, not shown as source.
        onNodeWithText("readme.md").performClick()
        waitForIdle()
        onNodeWithText("remember the towel").assertDoesNotExist()
        onNodeWithText("Read me").assertIsDisplayed()
        onNodeWithText("· first point").assertIsDisplayed()
    }

    @Test
    fun aFolderOfImagesBecomesAPhotoGrid() = runComposeUiTest {
        val opened = mutableListOf<String>()
        showFiles(FakeTree(), opened = opened)
        onNodeWithText("PHOTOS").performClick()
        waitForIdle()
        onNodeWithText("3 PHOTOS").assertIsDisplayed()
        onNodeWithText("b.png").performClick()
        assertEquals(listOf("/Photos/b.png"), opened)
    }

    @Test
    fun trashingAFileAsksFirstThenCallsTheHost() = runComposeUiTest {
        val tree = FakeTree()
        showFiles(tree)
        onNodeWithContentDescription("Move notes.txt to the trash").performClick()
        waitForIdle()
        onNodeWithText("DELETE NOTES.TXT?").assertIsDisplayed()
        assertTrue(tree.deleteCalls.isEmpty())
        onNodeWithText("DELETE").performClick()
        waitForIdle()
        assertEquals(listOf("/notes.txt"), tree.deleteCalls)
        onNodeWithText("notes.txt").assertDoesNotExist()
        onNodeWithText("3 THINGS HERE").assertIsDisplayed()
    }

    @Test
    fun creatingAFolderAddsATile() = runComposeUiTest {
        showFiles(FakeTree())
        onNodeWithText("NEW FOLDER").performClick()
        waitForIdle()
        onNodeWithContentDescription("Name for the new folder").performTextInput("Maps")
        onNodeWithText("OK").performClick()
        waitForIdle()
        onNodeWithText("MAPS").assertIsDisplayed()
        onNodeWithText("FOLDERS · 3").assertIsDisplayed()
    }

    @Test
    fun selectModeOffersTheBatchActionsAndStepsBackOut() = runComposeUiTest {
        val backStep = BackStepState()
        showFiles(FakeTree(), backStep)
        onNodeWithText("SELECT").performClick()
        waitForIdle()
        onNodeWithText("notes.txt").performClick()
        waitForIdle()
        onNodeWithText("‹ 1 SELECTED").assertIsDisplayed()
        onNodeWithText("MOVE").assertIsDisplayed()
        onNodeWithText("COPY").assertIsDisplayed()

        // MOVE hands over to the destination picker, which is the same screen family.
        onNodeWithText("MOVE").performClick()
        waitForIdle()
        onNodeWithText("MOVE TO…").assertIsDisplayed()
        onNodeWithText("USE THIS FOLDER").assertIsDisplayed()
        backStep.stepBack()
        waitForIdle()
        onNodeWithText("‹ 1 SELECTED").assertIsDisplayed()
        backStep.stepBack()
        waitForIdle()
        onNodeWithText("SELECT").assertIsDisplayed()
    }

    @Test
    fun searchFindsEntriesAndTheStorageChipOpensTheReport() = runComposeUiTest {
        showFiles(FakeTree())
        onNodeWithText("SEARCH").performClick()
        waitForIdle()
        onNodeWithText("TYPE TO SEARCH").assertIsDisplayed()
        onNodeWithContentDescription("Search files").performTextInput("deep")
        waitForIdle()
        onNodeWithText("deep.txt").assertIsDisplayed()
        onNodeWithText("/Docs/Sub").assertIsDisplayed()
        onNodeWithContentDescription("Close search").performClick()
        waitForIdle()

        onNodeWithText("STORAGE ›").performClick()
        waitForIdle()
        onNodeWithText("USED IN THIS SANDBOX").assertIsDisplayed()
        onNodeWithText("‹ BACK").performClick()
        waitForIdle()
        onNodeWithText("4 THINGS HERE").assertIsDisplayed()
    }

    // ----- H2g2FolderPicker ----------------------------------------------------------------

    @Test
    fun folderPickerOpensATileThenGoesInsideIt() = runComposeUiTest {
        val tree = FakeTree()
        var picked: String? = null
        val backStep = BackStepState()
        setContent {
            H2g2Theme {
                H2g2FolderPicker(
                    title = "Copy to…",
                    listDir = { tree.list(it) },
                    onConfirm = { picked = it },
                    onCancel = {},
                    backStep = backStep
                )
            }
        }
        waitForIdle()
        onNodeWithText("COPY TO…").assertIsDisplayed()
        // Folders only: the root's files are not destinations.
        onNodeWithText("DOCS").assertIsDisplayed()
        onNodeWithText("PHOTOS").assertIsDisplayed()
        onNodeWithText("notes.txt").assertDoesNotExist()

        // First tap opens the tile into a panel; its sibling becomes a rod.
        onNodeWithText("DOCS").performClick()
        waitForIdle()
        onNodeWithText("TAP TO GO INSIDE").assertIsDisplayed()
        onNodeWithContentDescription("Folder Photos").assertExists()
        // Second tap descends: the folder's own subfolders are the new grid.
        onNodeWithText("DOCS").performClick()
        waitForIdle()
        onNodeWithText("SUB").assertIsDisplayed()
        assertTrue(backStep.canStepBack)

        onNodeWithText("USE THIS FOLDER").performClick()
        assertEquals("/Docs", picked)
    }

    // ----- H2g2PathPickerScreen ------------------------------------------------------------

    @Test
    fun pathPickerDescendsSelectsAndStepsBack() = runComposeUiTest {
        val tree = FakeTree()
        var file: String? = null
        var folder: String? = null
        val backStep = BackStepState()
        setContent {
            H2g2Theme {
                H2g2PathPickerScreen(
                    startPath = "/Docs",
                    listDir = { tree.list(it) },
                    onSelectFile = { file = it },
                    onSelectFolder = { folder = it },
                    onCancel = {},
                    backStep = backStep
                )
            }
        }
        waitForIdle()
        // Folders first with a trailing slash, files in their real case, and a way up.
        onNodeWithText("Sub/").assertIsDisplayed()
        onNodeWithText("report.txt").assertIsDisplayed()
        onNodeWithText("‹ UP").assertIsDisplayed()
        assertFalse(backStep.canStepBack)

        onNodeWithText("Sub/").performClick()
        waitForIdle()
        onNodeWithText("deep.txt").assertIsDisplayed()
        assertTrue(backStep.canStepBack)
        onNodeWithText("SELECT THIS FOLDER").performClick()
        assertEquals("/Docs/Sub", folder)

        backStep.stepBack()
        waitForIdle()
        onNodeWithText("report.txt").performClick()
        assertEquals("/Docs/report.txt", file)
    }

    // ----- H2g2StorageScreen ---------------------------------------------------------------

    @Test
    fun storageScreenCountsThenShowsTheBreakdownLargestAndTrash() = runComposeUiTest {
        val tree = FakeTree()
        var stats by mutableStateOf<StorageStats?>(null)
        val deleted = mutableListOf<String>()
        val restored = mutableListOf<String>()
        val trashItem = VfsTrashEntry("t0", "/old.txt", "old.txt", false, 2048L, deletedAtMillis = 0L)
        setContent {
            H2g2Theme {
                H2g2StorageScreen(
                    stats = stats,
                    onDelete = { deleted += it },
                    trash = TrashPanelState(items = listOf(trashItem), onRestore = { restored += it.name }),
                    onBack = {},
                    fullscreen = false
                )
            }
        }
        waitForIdle()
        onNodeWithText("COUNTING…").assertIsDisplayed()

        stats = tree.stats()
        waitForIdle()
        onNodeWithText("USED IN THIS SANDBOX").assertIsDisplayed()
        // Only categories that hold something are listed.
        onNodeWithText("DOCUMENTS").assertIsDisplayed()
        onNodeWithText("IMAGES").assertDoesNotExist()

        onNodeWithText("LARGEST").performClick()
        waitForIdle()
        onNodeWithContentDescription("Move readme.md to the trash").performClick()
        waitForIdle()
        onNodeWithText("DELETE").performClick()
        waitForIdle()
        assertEquals(listOf("/readme.md"), deleted)

        onNodeWithText("TRASH (1)").performClick()
        waitForIdle()
        onNodeWithText("old.txt").assertIsDisplayed()
        onNodeWithText("2 KB · purged 30 days after it was deleted").assertIsDisplayed()
        onNodeWithText("RESTORE").performClick()
        assertEquals(listOf("old.txt"), restored)
    }

    // ----- H2g2MarkdownPreview -------------------------------------------------------------

    @Test
    fun markdownPreviewRendersInsteadOfShowingSource() = runComposeUiTest {
        setContent {
            H2g2Theme {
                H2g2MarkdownPreview("# Title\n\nSome **bold** and `code` text.\n- one\n```\nval x = 1\n```")
            }
        }
        waitForIdle()
        onNodeWithText("Title").assertIsDisplayed()
        onNodeWithText("Some bold and code text.").assertIsDisplayed()
        onNodeWithText("· one").assertIsDisplayed()
        onNodeWithText("val x = 1").assertIsDisplayed()
        onNodeWithText("# Title").assertDoesNotExist()
    }

    // ----- H2g2FilesReveal -----------------------------------------------------------------

    @Test
    fun revealOpensOntoItsContentAndClosesItAway() = runComposeUiTest {
        var visible by mutableStateOf(false)
        var closedCalls = 0
        val tree = FakeTree()
        setContent {
            H2g2Theme {
                H2g2FilesReveal(visible = visible, onClosed = { closedCalls++ }) {
                    H2g2FolderPicker(title = "Pick", listDir = { tree.list(it) }, onConfirm = {}, onCancel = {})
                }
            }
        }
        waitForIdle()
        onNodeWithText("USE THIS FOLDER").assertDoesNotExist()
        assertEquals(0, closedCalls)

        visible = true
        waitForIdle()
        onNodeWithText("USE THIS FOLDER").assertIsDisplayed()
        onNodeWithText("DOCS").assertIsDisplayed()

        visible = false
        waitForIdle()
        onNodeWithText("USE THIS FOLDER").assertDoesNotExist()
        assertEquals(1, closedCalls)
    }
}
