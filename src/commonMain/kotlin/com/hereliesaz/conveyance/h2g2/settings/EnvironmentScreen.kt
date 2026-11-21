package com.hereliesaz.conveyance.h2g2.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.hereliesaz.conveyance.h2g2.H2g2Chip
import com.hereliesaz.conveyance.h2g2.H2g2Heading
import com.hereliesaz.conveyance.h2g2.LocalH2g2Ground
import com.hereliesaz.conveyance.h2g2.StatusKind
import com.hereliesaz.conveyance.h2g2.Text
import com.hereliesaz.conveyance.h2g2.headingRole
import com.hereliesaz.conveyance.h2g2.liveRegionPolite
import com.hereliesaz.conveyance.h2g2.onPage
import com.hereliesaz.conveyance.h2g2.pageBrush
import com.hereliesaz.conveyance.h2g2.screenInsets
import com.hereliesaz.conveyance.h2g2.statusColor

/**
 * What [H2g2EnvironmentScreen] shows.
 *
 * @property installed false when there is no bootstrap, so no environment exists to show.
 * @property env the variables every command runs with. `PATH` is split on `:` into its own list;
 *   the rest are listed sorted by name.
 * @property loading true while the prefix is still being probed: the BACK chip and title show,
 *   the body says so.
 */
data class EnvironmentState(
    val installed: Boolean = true,
    val env: Map<String, String> = emptyMap(),
    val loading: Boolean = false
)

/**
 * W4 (docs/design/HG2Gui Termux Coverage.dc.html): "no environment/PATH visibility or editing" - a
 * read-only view of the same bootstrap env every command in the app actually runs under
 * (the map the host builds each session from), so at least the "visibility" half of that gap has
 * an honest answer.
 *
 * Deliberately read-only: making this editable would mean feeding user-edited values back into
 * the host's own env-construction path, which per-command already reconstructs it rather than
 * reading any mutable app-level store - wiring an edit UI to that safely is a separate, larger
 * change than closing the visibility gap this screen closes.
 *
 * The host passes the environment in [state]; [onBack] is the BACK chip and [fullscreen] is the
 * immersive preference (see `Modifier.screenInsets`).
 */
@Composable
fun H2g2EnvironmentScreen(
    state: EnvironmentState,
    onBack: () -> Unit,
    fullscreen: Boolean,
    modifier: Modifier = Modifier
) {
    val env = state.env
    val muted = statusColor(StatusKind.Muted)
    val onPage = LocalH2g2Ground.current.onPage
    Column(
        modifier
            .fillMaxSize()
            .background(LocalH2g2Ground.current.pageBrush())
            .screenInsets(fullscreen, ime = false)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            H2g2Chip("‹ Back", onClick = onBack, contentDescription = "Back to settings")
        }

        H2g2Heading("Environment", Modifier.padding(horizontal = 20.dp, vertical = 6.dp))

        if (state.loading) {
            Text(
                "Reading the environment…",
                color = muted,
                fontSize = 12.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp).liveRegionPolite()
            )
            return@Column
        }

        if (!state.installed) {
            Text(
                "No Termux bootstrap is installed, so there is no environment for a command to " +
                    "run under yet - install it first (the pill menu's own setup step).",
                color = onPage,
                fontSize = 12.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp)
            )
            return@Column
        }

        Text(
            "What every command in this app actually runs with - the same env ShellSession " +
                "builds fresh for each bootstrap-tier session. Press and hold a value to copy it.",
            color = muted,
            fontSize = 11.sp, lineHeight = 15.sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp)
        )

        val pathDirs = env["PATH"]?.split(":")?.filter { it.isNotBlank() }.orEmpty()
        val otherVars = env.entries.filter { it.key != "PATH" }.sortedBy { it.key }

        // SelectionContainer: values are long paths and tokens people want to copy a piece of.
        SelectionContainer(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 20.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Text(
                        "PATH (${pathDirs.size})",
                        color = muted,
                        fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.18.em,
                        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp).headingRole()
                    )
                }
                items(pathDirs) { dir -> EnvRow(label = null, value = dir) }

                item {
                    Text(
                        "VARIABLES (${otherVars.size})",
                        color = muted,
                        fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.18.em,
                        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp).headingRole()
                    )
                }
                items(otherVars) { (key, value) -> EnvRow(label = key, value = value) }
            }
        }
    }
}

@Composable
private fun EnvRow(label: String?, value: String) {
    val onPage = LocalH2g2Ground.current.onPage
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        if (label != null) {
            Text(
                label, color = onPage,
                fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.01.em
            )
        }
        Text(
            value, color = onPage,
            fontSize = 12.sp, lineHeight = 16.sp,
            modifier = Modifier.padding(top = if (label != null) 2.dp else 0.dp)
        )
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(onPage.copy(alpha = .12f)))
}
