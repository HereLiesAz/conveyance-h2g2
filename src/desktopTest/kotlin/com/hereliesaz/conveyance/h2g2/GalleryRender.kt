package com.hereliesaz.conveyance.h2g2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import com.hereliesaz.conveyance.h2g2.components.ConfirmTone
import com.hereliesaz.conveyance.h2g2.components.H2g2ConfirmDialog
import com.hereliesaz.conveyance.h2g2.menu.H2g2PillMenu
import com.hereliesaz.conveyance.h2g2.menu.MenuNode
import com.hereliesaz.conveyance.h2g2.menu.PillMenuPlacement
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the library's surfaces off-screen to PNGs under `build/gallery/`, at chosen moments of
 * their animations. Not an assertion of pixels: it proves each surface composes, lays out and
 * draws on a real Skia canvas with the bundled typeface, and leaves frames a person can look at.
 */
class GalleryRender {
    private val out = File("build/gallery").apply { mkdirs() }

    /** Renders [content] at each of [atMs] on one scene clock, so animations advance between frames. */
    private fun render(name: String, vararg atMs: Long, width: Int = 390, height: Int = 844, content: @Composable () -> Unit) {
        val density = 2f
        ImageComposeScene((width * density).toInt(), (height * density).toInt(), Density(density)) { content() }.use { scene ->
            // Step the clock in display-frame increments: a single jump would starve LaunchedEffects.
            var t = 0L
            for (target in atMs) {
                while (t < target) {
                    scene.render(t * NANOS_PER_MS)
                    t += FRAME_MS
                }
                val image = scene.render(target * NANOS_PER_MS)
                val bytes = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
                val file = File(out, "${name}_${target.toString().padStart(5, '0')}.png")
                file.writeBytes(bytes)
                assertTrue(bytes.size > MIN_PNG_BYTES, "$file is suspiciously small")
            }
        }
    }

    private val mustard = GroundState(grounds.first())
    private val navy = GroundState(grounds.first { it.name == "Navy" })

    @Test
    fun splash() = render("splash", 0, 300, 900, 1900, 2900, 3900) {
        H2g2Theme(mustard) { H2g2Splash() }
    }

    @Test
    fun splashOnNavy() = render("splash_navy", 3900) {
        H2g2Theme(navy) { H2g2Splash() }
    }

    @Test
    fun primitives() = render("primitives", 500, height = 420) {
        H2g2Theme(mustard) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                H2g2Heading("Primitives")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    H2g2Chip("Run", onClick = {})
                    H2g2Chip("Grid", onClick = {}, selected = true)
                    H2g2Chip("List", onClick = {}, selected = false)
                    H2g2Chip("Delete", onClick = {}, tint = H2g2.hues[6])
                }
                for (kind in StatusKind.entries) Text(kind.name.lowercase(), color = statusColor(kind), style = currentH2g2Type().body)
            }
        }
    }

    private val tree = listOf(
        MenuNode("pkg", "Package management", cap = "12", children = listOf(
            MenuNode("install", "install", children = listOf(MenuNode("vim", "vim"), MenuNode("git", "git"))),
            MenuNode("remove", "remove"), MenuNode("update", "update"), MenuNode("search", "search"),
        )),
        MenuNode("files", "Files", cap = "dir"), MenuNode("net", "Network"), MenuNode("git", "Git", cap = "7"),
        MenuNode("proc", "Processes"), MenuNode("guide", "The Guide"), MenuNode("ssh", "Remote shells"),
    )

    @Test
    fun pillMenu() = render("pillmenu", 0, 120, 400, 1200) {
        H2g2Theme(mustard) { Box(Modifier.fillMaxSize()) { H2g2PillMenu(roots = tree, modifier = Modifier.fillMaxSize()) } }
    }

    @Test
    fun pillMenuPlacements() {
        for (placement in PillMenuPlacement.entries) {
            render("pillmenu_${placement.name}", 120, 1200, height = 420) {
                H2g2Theme(mustard) { H2g2PillMenu(roots = tree, modifier = Modifier.fillMaxSize(), placement = placement) }
            }
        }
    }

    @Test
    fun confirmDialog() = render("confirm", 600) {
        H2g2Theme(navy) {
            H2g2ConfirmDialog(
                title = "Remove 3 files?",
                message = "This cannot be undone.",
                confirmLabel = "Remove",
                tone = ConfirmTone.Elevated,
                badge = "RUNS AS ROOT",
                onConfirm = {},
                onDismiss = {},
            )
        }
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
        const val FRAME_MS = 16L
        const val MIN_PNG_BYTES = 2_000
    }
}
