package com.hereliesaz.conveyance.h2g2.tui

import com.hereliesaz.conveyance.h2g2.terminal.ShellFamily
import com.hereliesaz.conveyance.h2g2.terminal.ShellPresentation
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hereliesaz.conveyance.h2g2.H2g2
import com.hereliesaz.conveyance.h2g2.H2g2Surface
import com.hereliesaz.conveyance.h2g2.Text
import com.hereliesaz.conveyance.h2g2.currentH2g2Type
import com.hereliesaz.conveyance.h2g2.headingRole
import com.hereliesaz.conveyance.h2g2.minTouch
import com.hereliesaz.conveyance.h2g2.minTouchSize

/**
 * Native projection of status that shell prompt frameworks normally encode into decorated text.
 * Drawn on the Ink terminal strip (Yellow-on-Ink), not on the page ground. The git-branch chip is a
 * button, so it has a 48dp hit box around its smaller visible capsule.
 *
 * This overload reads everything from [session]: its [FullScreenTerminalSession.shellPresentation]
 * is the status, picking a branch writes `git switch` through
 * [FullScreenTerminalSession.sendText], and the branch list comes from
 * [FullScreenTerminalSession.gitBranches]. [H2g2FullScreenTerminalScreen] already places it above
 * the grid; call it yourself only to put the strip somewhere else.
 */
@Composable
fun H2g2StructuredShellStatus(session: FullScreenTerminalSession, modifier: Modifier = Modifier) {
    // The open branch chooser belongs to one session and must not carry over to another.
    key(session) {
        H2g2StructuredShellStatus(
            presentation = session.shellPresentation,
            onSendText = session::sendText,
            loadGitBranches = session::gitBranches,
            modifier = modifier
        )
    }
}

/**
 * Native projection of status that shell prompt frameworks normally encode into decorated text:
 * a horizontally scrolling line of small tags for the shell, `user@host`, the working directory,
 * the Git branch (with a dot when the tree is dirty) and the last exit status, each drawn only when
 * [presentation] knows it. Nothing at all is drawn for an unknown shell with no status.
 *
 * Drawn Yellow-on-Ink, so place it on an Ink strip rather than on the page ground. The git-branch
 * chip is a button, so it has a 48dp hit box around its smaller visible capsule; tapping it opens a
 * branch chooser.
 *
 * - [onSendText]: write text to the shell. Picking a branch sends
 *   `git switch -- '<branch>'` and a newline.
 * - [loadGitBranches]: the branches of the repository at the given working directory. Called each
 *   time the chooser opens (and again if the directory or branch changes while it is open); do the
 *   work off the UI thread. An empty list shows "No branch inventory is available for this
 *   directory."
 */
@Composable
fun H2g2StructuredShellStatus(
    presentation: ShellPresentation,
    onSendText: (String) -> Unit,
    modifier: Modifier = Modifier,
    loadGitBranches: suspend (cwd: String?) -> List<String> = { emptyList() }
) {
    if (!presentation.hasStructuredStatus && presentation.shell == ShellFamily.UNKNOWN) return

    var chooseBranch by remember { mutableStateOf(false) }
    val currentLoadGitBranches by rememberUpdatedState(loadGitBranches)
    val branches by produceState<List<String>>(
        initialValue = emptyList(),
        key1 = chooseBranch,
        key2 = presentation.cwd,
        key3 = presentation.gitBranch
    ) {
        value = if (chooseBranch) currentLoadGitBranches(presentation.cwd) else emptyList()
    }

    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (presentation.shell != ShellFamily.UNKNOWN) {
            StatusChip(presentation.shell.name.lowercase())
        }
        val identity = listOfNotNull(presentation.user, presentation.host).joinToString("@").takeIf(String::isNotBlank)
        identity?.let { StatusChip(it) }
        presentation.cwd?.let { StatusChip(it) }
        presentation.gitBranch?.let { branch ->
            StatusChip(
                text = "${if (presentation.gitDirty) "● " else ""}$branch",
                onClick = { chooseBranch = true }
            )
        }
        presentation.lastExitCode?.let { code ->
            StatusChip(if (code == 0) "exit 0" else "exit $code")
        }
    }

    if (chooseBranch) {
        BranchChooser(
            branches = branches,
            currentBranch = presentation.gitBranch,
            onPick = { branch ->
                onSendText("git switch -- ${shellQuote(branch)}\n")
                chooseBranch = false
            },
            onDismiss = { chooseBranch = false }
        )
    }
}

private val CHOOSER_MAX_WIDTH = 480.dp
private const val CHOOSER_MAX_HEIGHT_FRACTION = 0.9f
private val CHOOSER_FALLBACK_MAX_HEIGHT = 560.dp
private const val CHOOSER_TEXT_ALPHA = 0.88f

/**
 * The "Switch Git branch" dialog: a title, one tappable row per branch (the current one in a
 * heavier weight) or a line saying there is no inventory, and a single CLOSE. The panel is the
 * library's dialog panel (Ink, Yellow title - see `H2g2ConfirmDialog`); the list scrolls when it is
 * longer than the screen, and Back / tapping outside closes it.
 */
@Composable
private fun BranchChooser(
    branches: List<String>,
    currentBranch: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val type = currentH2g2Type()
    val textColor = H2g2.white.copy(alpha = CHOOSER_TEXT_ALPHA)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            val panelMaxHeight = if (constraints.hasBoundedHeight) maxHeight * CHOOSER_MAX_HEIGHT_FRACTION else CHOOSER_FALLBACK_MAX_HEIGHT
            Column(
                Modifier
                    .widthIn(max = CHOOSER_MAX_WIDTH)
                    .fillMaxWidth()
                    .heightIn(max = panelMaxHeight)
                    .clip(H2g2Surface.recordTile)
                    .background(H2g2.ink)
                    .padding(22.dp)
            ) {
                Text(
                    "Switch Git branch",
                    color = H2g2.yellow,
                    style = type.capsule.copy(fontSize = 15.sp, lineHeight = 20.sp),
                    modifier = Modifier.headingRole()
                )
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (branches.isEmpty()) {
                        Text(
                            "No branch inventory is available for this directory.",
                            color = textColor,
                            style = type.body.copy(fontSize = 14.sp, lineHeight = 20.sp)
                        )
                    } else {
                        branches.forEach { branch ->
                            Text(
                                branch,
                                color = textColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(H2g2.white.copy(alpha = .08f), RoundedCornerShape(14.dp))
                                    .clickable(role = Role.Button) { onPick(branch) }
                                    .heightIn(min = minTouchSize())
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                fontWeight = if (branch == currentBranch) FontWeight.Black else FontWeight.Medium
                            )
                        }
                    }
                }
                Box(Modifier.align(Alignment.End)) {
                    Text(
                        "CLOSE",
                        color = H2g2.yellow,
                        style = type.chip,
                        modifier = Modifier
                            .clickable(role = Role.Button, onClick = onDismiss)
                            .heightIn(min = minTouchSize())
                            .padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, onClick: (() -> Unit)? = null) {
    Text(
        text,
        color = H2g2.yellow,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            // Gesture first, then the 48dp hit box, then the drawing (see minTouch).
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick).minTouch(minTouchSize()) else Modifier)
            .background(H2g2.white.copy(alpha = .08f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

/** [value] as one single-quoted POSIX shell word. */
internal fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"
