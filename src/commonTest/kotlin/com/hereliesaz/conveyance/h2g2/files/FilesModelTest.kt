package com.hereliesaz.conveyance.h2g2.files

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import com.hereliesaz.conveyance.h2g2.H2g2
import com.hereliesaz.conveyance.h2g2.H2g2Contrast
import com.hereliesaz.conveyance.h2g2.contrastRatio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The pure pieces under the files screens: paths, sizes, the tile/rod geometry, styled lines. */
class FilesModelTest {

    @Test
    fun parentAndChildPathsRoundTrip() {
        assertEquals("/a/b", vfsParentPath("/a/b/c"))
        assertEquals("/", vfsParentPath("/a"))
        assertEquals("/", vfsParentPath("/"))
        assertEquals("/a/b", vfsParentPath("/a/b/c/"))
        assertEquals("/x", vfsChildPath("/", "x"))
        assertEquals("/a/x", vfsChildPath("/a", "x"))
        assertEquals("/a/x", vfsChildPath("/a/", "x"))
    }

    @Test
    fun fileSizesStepThroughTheUnits() {
        assertEquals("0 B", formatFileSize(0))
        assertEquals("1023 B", formatFileSize(1023))
        assertEquals("1 KB", formatFileSize(1024))
        assertEquals("5 MB", formatFileSize(5L * 1024 * 1024 + 7))
        assertEquals("1.5 GB", formatFileSize(1024L * 1024 * 1024 * 3 / 2))
    }

    @Test
    fun gridTilesAreSquareAndFillTheWidth() {
        val density = Density(1f)
        // 3 columns, 6dp gaps, 312px wide -> (312 - 12) / 3 = 100px tiles.
        val first = gridTileRect(0, 3, 312f, density)
        val last = gridTileRect(5, 3, 312f, density)
        assertEquals(TileRect(0f, 0f, 100f, 100f), first)
        assertEquals(TileRect(212f, 106f, 100f, 100f), last)
        assertEquals(312f, last.x + last.w)
        assertEquals(100f, gridTileSizePx(3, 312f, density))
    }

    @Test
    fun rodsStackDownTheTrailingEdge() {
        val density = Density(1f)
        val a = rodTileRect(0, 312f, 70f, density, startY = 62f)
        val b = rodTileRect(1, 312f, 70f, density, startY = 62f)
        // 14dp wide, pinned to the trailing edge, advancing on y by length + 4dp gap.
        assertEquals(TileRect(298f, 62f, 14f, 70f), a)
        assertEquals(TileRect(298f, 136f, 14f, 70f), b)
    }

    @Test
    fun styledLinesKeepTextOrderAndWeight() {
        val page = Color(0xFFF2C230)
        val line = buildStyledLine(
            listOf(StyledSpan("fun "), StyledSpan("main", hue = 6, bold = true), StyledSpan("()")),
            onPage = H2g2.ink,
            background = page
        )
        assertEquals("fun main()", line.text)
        assertEquals(3, line.spanStyles.size)
        assertEquals(FontWeight.Bold, line.spanStyles[1].item.fontWeight)
        // Plain spans are the page's text colour at 80%; hued ones are pushed to readable contrast.
        assertEquals(H2g2.ink.copy(alpha = .8f), line.spanStyles[0].item.color)
        assertTrue(contrastRatio(line.spanStyles[1].item.color, page) >= H2g2Contrast.TEXT - 0.05f)
    }
}
