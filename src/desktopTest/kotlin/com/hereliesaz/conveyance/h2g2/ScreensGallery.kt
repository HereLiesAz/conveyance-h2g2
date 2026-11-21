package com.hereliesaz.conveyance.h2g2

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.hereliesaz.conveyance.h2g2.components.BackStepState
import com.hereliesaz.conveyance.h2g2.files.DeviceStorageState
import com.hereliesaz.conveyance.h2g2.files.FilePreview
import com.hereliesaz.conveyance.h2g2.files.H2g2FilesScreen
import com.hereliesaz.conveyance.h2g2.files.StorageStats
import com.hereliesaz.conveyance.h2g2.files.TrashActions
import com.hereliesaz.conveyance.h2g2.files.VfsEntry
import com.hereliesaz.conveyance.h2g2.guide.H2g2GuideReaderScreen
import com.hereliesaz.conveyance.h2g2.menu.MenuNode
import com.hereliesaz.conveyance.h2g2.menu.PillMenuPlacement
import com.hereliesaz.conveyance.h2g2.terminal.H2g2TerminalScreen
import com.hereliesaz.conveyance.h2g2.terminal.SessionUiState
import com.hereliesaz.conveyance.h2g2.terminal.TerminalHistoryEntry
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * The ported screens drawn off-screen, with real pointer input where a transition needs a tap:
 * frames land in `build/gallery/`. See [GalleryRender] for what this does and does not prove.
 */
class ScreensGallery {
    private val out = File("build/gallery").apply { mkdirs() }
    private val density = 2f

    /** A scripted scene: [step] advances the clock frame by frame, [tap] presses and releases, [shot] saves a frame. */
    private inner class Script(val scene: ImageComposeScene, val name: String) {
        var t = 0L
        fun step(ms: Long) {
            val end = t + ms
            while (t < end) {
                scene.render(t * NANOS_PER_MS)
                t += FRAME_MS
            }
        }

        fun tap(xDp: Float, yDp: Float) {
            val at = Offset(xDp * density, yDp * density)
            scene.sendPointerEvent(PointerEventType.Move, at)
            scene.sendPointerEvent(PointerEventType.Press, at)
            step(FRAME_MS * 2)
            scene.sendPointerEvent(PointerEventType.Release, at)
        }

        fun shot(label: String) {
            val bytes = scene.render(t * NANOS_PER_MS).encodeToData(EncodedImageFormat.PNG)!!.bytes
            File(out, "${name}_$label.png").writeBytes(bytes)
        }
    }

    private fun script(name: String, width: Int = 390, height: Int = 844, content: @Composable () -> Unit, run: Script.() -> Unit) {
        ImageComposeScene((width * density).toInt(), (height * density).toInt(), Density(density)) { content() }
            .use { Script(it, name).run() }
    }

    private val mustard = GroundState(grounds.first())

    private val dirs = setOf("/", "/Docs", "/Docs/Drafts", "/Photos", "/Music", "/Projects")
    private val files = mapOf(
        "/Docs/report.txt" to 2_400L, "/Docs/towel.md" to 900L, "/Docs/Drafts/ch1.txt" to 12_000L,
        "/Photos/a.png" to 240_000L, "/Photos/b.png" to 310_000L, "/Music/theme.ogg" to 4_200_000L,
        "/Projects/build.gradle.kts" to 3_100L, "/notes.txt" to 420L, "/readme.md" to 1_800L,
    )

    private fun list(path: String): List<VfsEntry> {
        val dir = path.trimEnd('/').ifEmpty { "/" }
        return (dirs + files.keys).filter { it != "/" && it.substringBeforeLast('/').ifEmpty { "/" } == dir }.map {
            VfsEntry(it.substringAfterLast('/'), it, it in dirs, files[it] ?: 0L, isImage = it.endsWith(".png"))
        }
    }

    @Test
    fun filesOpenAndClose() = script("files", content = {
        H2g2Theme(mustard) {
            H2g2FilesScreen(
                fullscreen = false,
                nowMillis = 1_000_000L,
                listDir = { list(it) },
                search = { emptyList() },
                storageStats = { StorageStats(totalBytes = files.values.sum(), byCategory = emptyList(), largest = emptyList()) },
                previewFile = { FilePreview(text = "Don't panic.") },
                onOpenFile = {},
                onCreateFolder = { _, _ -> true },
                onCreateFile = { _, _ -> true },
                onDelete = { true },
                trash = TrashActions(items = { emptyList() }, restore = { true }, purge = { true }, empty = { 0 }),
                deviceStorage = DeviceStorageState(active = false, granted = false, onToggle = {}),
                onRename = { _, _ -> true },
                onMove = { _, _ -> true },
                onCopy = { _, _ -> true },
                onShare = {},
                onShareMultiple = {},
                onBack = {},
                backStep = remember { BackStepState() },
            )
        }
    }) {
        step(1500); shot("0_rest")
        tap(TILE_X, TILE_Y)
        step(120); shot("1_open_120")
        step(120); shot("2_open_240")
        step(160); shot("3_open_400")
        step(900); shot("4_opened")
    }

    @Test
    fun guideReader() = script("guide", content = {
        H2g2Theme(mustard) {
            H2g2GuideReaderScreen(fullscreen = false, onBack = {}, backStep = remember { BackStepState() })
        }
    }) {
        step(1500); shot("0_index")
        tap(GUIDE_ROW_X, GUIDE_ROW_Y)
        step(200); shot("1_opening")
        step(1500); shot("2_entry")
    }

    private val tree = listOf(
        MenuNode("pkg", "Package management", cap = "12", children = listOf(MenuNode("install", "install"), MenuNode("remove", "remove"))),
        MenuNode("files", "Files", cap = "dir"), MenuNode("net", "Network"), MenuNode("git", "Git", cap = "7"),
    )

    private fun terminal(name: String, placement: PillMenuPlacement) = script(name, content = {
        val session = remember {
            SessionUiState(id = "1", name = "bash", cwd = "/home/zaphod").apply {
                buffer = listOf(
                    TerminalHistoryEntry(command = "uname -a", output = "Linux heart-of-gold 6.18 aarch64 Android", exitCode = 0),
                    TerminalHistoryEntry(
                        command = "id",
                        output = "User: zaphod\nGroups: presidents, hitchhikers\nShell: /bin/bash\nHome: /home/zaphod",
                        exitCode = 0,
                    ),
                )
            }
        }
        H2g2Theme(mustard) {
            H2g2TerminalScreen(
                tree = tree, sessions = listOf(session), activeSessionId = "1",
                onSessionPick = {}, onNewSession = {}, onCloseSession = {}, fullscreen = false,
                onOpenSettings = {}, onOpenGuide = {}, onOpenFiles = {}, onCopy = {}, onShare = {},
                onInterrupt = {}, onCopied = {}, pillPlacement = placement,
                modifier = Modifier.fillMaxSize(),
                onRun = { _, _, _, _, _, _, _ -> },
            )
        }
    }) {
        step(2000); shot("rest")
    }

    @Test
    fun terminalScreen() = terminal("terminal", PillMenuPlacement.BottomRight)

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
        const val FRAME_MS = 16L
        const val TILE_X = 100f
        const val TILE_Y = 330f
        const val GUIDE_ROW_X = 195f
        const val GUIDE_ROW_Y = 420f
    }
}
