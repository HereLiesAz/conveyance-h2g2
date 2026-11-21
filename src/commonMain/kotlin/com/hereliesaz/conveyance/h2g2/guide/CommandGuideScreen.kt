package com.hereliesaz.conveyance.h2g2.guide

import com.hereliesaz.conveyance.h2g2.H2g2
import com.hereliesaz.conveyance.h2g2.H2g2Chip
import com.hereliesaz.conveyance.h2g2.H2g2Heading
import com.hereliesaz.conveyance.h2g2.LocalH2g2Ground
import com.hereliesaz.conveyance.h2g2.Text
import com.hereliesaz.conveyance.h2g2.pageBrush
import com.hereliesaz.conveyance.h2g2.screenInsets
import com.hereliesaz.conveyance.h2g2.statusColor
import com.hereliesaz.conveyance.h2g2.StatusKind
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hereliesaz.conveyance.h2g2.components.BackStepState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hereliesaz.conveyance.h2g2.menu.MenuNode
import com.hereliesaz.conveyance.h2g2.menu.PillMenuPlacement
import com.hereliesaz.conveyance.h2g2.menu.H2g2PillMenu

/*
 * The Guide and the command tree are both command-entry surfaces. The tree exposes the runtime
 * structurally; the Guide exposes the same world through explanation and narrative. Picking a
 * command in either surface writes it into the terminal input for review instead of executing it.
 *
 * A command tapped in the *reader* never leaves the Guide: it is appended to whatever the user is
 * composing ([onCommandAppended]) and the reader shows its own "added to input" confirmation, so
 * reading and composing can interleave. Only a finished pick from the pill tree below returns to
 * the terminal.
 */
/**
 * The command guide: the host's command [tree] as a pill menu, with a "The Guide" chip that opens
 * [H2g2GuideReaderScreen] over it (the reader's live facts are indexed from the same [tree]).
 * Fills its parent and paints the current ground; call it inside `H2g2Theme` as a whole screen.
 *
 * @param tree the commands on offer, as fed to `H2g2PillMenu`.
 * @param fullscreen passed to `screenInsets`: true when the host draws edge to edge.
 * @param onBack "Back" on the tree: the host returns to its terminal.
 * @param backStep while the reader is open it reports its own back step here; on the tree it
 *   reports none, so the host's system-back handler closes the screen.
 * @param content what the reader reads. Defaults to [HitchhikersGuideToTermux].
 * @param showProductionNotes forwarded to the reader (dev builds only; see there).
 */
@Composable
fun H2g2CommandGuideScreen(
    tree: List<MenuNode>,
    fullscreen: Boolean,
    /** tokens picked so far; complete = a whole command was chosen (terminal pill). */
    onCommandSelected: (tokens: List<String>, complete: Boolean) -> Unit,
    onBack: () -> Unit,
    backStep: BackStepState,
    modifier: Modifier = Modifier,
    /**
     * A command was tapped in the reader: append it to the composition, stay on the Guide. When
     * null it is reported through [onCommandSelected] as an incomplete single-token pick - never
     * as complete, which would close the Guide.
     */
    onCommandAppended: ((command: String) -> Unit)? = null,
    content: GuideContent = HitchhikersGuideToTermux,
    /** Which corner the pill stack grows from; see [PillMenuPlacement]. */
    pillPlacement: PillMenuPlacement = PillMenuPlacement.BottomRight,
    showProductionNotes: Boolean = false
) {
    var readingGuide by remember { mutableStateOf(false) }
    val runtimeIndex = remember(tree) { GuideRuntimeIndex.fromTree(tree) }

    if (readingGuide) {
        H2g2GuideReaderScreen(
            fullscreen = fullscreen,
            onBack = { readingGuide = false },
            onCommandSelected = { command ->
                if (onCommandAppended != null) onCommandAppended(command) else onCommandSelected(listOf(command), false)
            },
            backStep = backStep,
            runtimeIndex = runtimeIndex,
            modifier = modifier,
            showProductionNotes = showProductionNotes,
            content = content
        )
        return
    }

    SideEffect {
        backStep.canStepBack = false
        backStep.stepBack = {}
    }

    Column(
        modifier
            .fillMaxSize()
            .background(LocalH2g2Ground.current.pageBrush())
            .screenInsets(fullscreen, ime = false)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            H2g2Chip("‹ Back", onClick = onBack, contentDescription = "Back to the terminal")
            H2g2Chip(
                "The Guide", onClick = { readingGuide = true },
                tint = H2g2.hues[6],
                contentDescription = "Open the Guide: command reference and walkthroughs"
            )
        }

        H2g2Heading("Guide", Modifier.padding(horizontal = 20.dp, vertical = 6.dp))

        Text(
            "Pick a command to drop it into the input — nothing here runs on its own.",
            color = statusColor(StatusKind.Muted),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp)
        )

        H2g2PillMenu(
            roots = tree,
            modifier = Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 12.dp),
            placement = pillPlacement,
            onRun = { picked, isTerminal -> onCommandSelected(picked, isTerminal) }
        )
    }
}
