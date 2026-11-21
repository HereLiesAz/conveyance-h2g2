package com.hereliesaz.conveyance.h2g2.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hereliesaz.conveyance.h2g2.H2g2
import com.hereliesaz.conveyance.h2g2.H2g2Chip
import com.hereliesaz.conveyance.h2g2.H2g2Heading
import com.hereliesaz.conveyance.h2g2.LocalH2g2Ground
import com.hereliesaz.conveyance.h2g2.StatusKind
import com.hereliesaz.conveyance.h2g2.Text
import com.hereliesaz.conveyance.h2g2.components.H2g2ConfirmDialog
import com.hereliesaz.conveyance.h2g2.liveRegionPolite
import com.hereliesaz.conveyance.h2g2.onPage
import com.hereliesaz.conveyance.h2g2.pageBrush
import com.hereliesaz.conveyance.h2g2.screenInsets
import com.hereliesaz.conveyance.h2g2.statusColor

private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60
private const val HOURS_PER_DAY = 24
private const val MILLIS_PER_SECOND = 1000

/**
 * One command in the persistent, cross-session log [H2g2HistoryScreen] searches.
 *
 * @property timestampMs when it was run, epoch milliseconds.
 * @property sessionLabel the tab it was run in.
 * @property command the command line as typed.
 */
data class CommandHistoryEntry(val timestampMs: Long, val sessionLabel: String, val command: String)

/**
 * What [H2g2HistoryScreen] shows. Filtering is the host's: [entries] is already the result of
 * searching the log for [query].
 *
 * @property entries the matching entries, in the order to list them.
 * @property query the search box's text.
 * @property nowMillis the current time, epoch milliseconds; each row's age is measured from it.
 * @property loading true while the log is still being read; shows "Loading" rather than flashing
 *   "No commands recorded yet." before the list appears.
 * @property confirmClear true while the "Clear history?" dialog is up.
 */
data class HistoryState(
    val entries: List<CommandHistoryEntry> = emptyList(),
    val query: String = "",
    val nowMillis: Long = 0L,
    val loading: Boolean = false,
    val confirmClear: Boolean = false
)

/**
 * W5 (docs/HG2Gui Termux Coverage.dc.html): "no cross-session scrollback/history search" - every
 * tab's own up/down-arrow recall is in-memory and per-session; this searches the host's
 * persistent, cross-session log instead. Selecting a result hands the command text back to the
 * caller (dropped into the active tab's input, same as arrow recall already does) rather than
 * re-running it directly - the user should see what they're about to run before it runs, same as
 * recalling it with the up arrow already works today.
 *
 * The log can hold secrets typed on a command line, so the screen is also where it is erased:
 * each row has its own DELETE and the header has CLEAR (behind a [H2g2ConfirmDialog]).
 *
 * - [onQueryChange] carries the search box's new text; filter and pass back new [state].
 * - [onSelect] receives the chosen command's text; [onDelete] removes one entry from the log.
 * - [onConfirmClearChange] opens (`true`) and closes (`false`) the clear confirmation - mirror it
 *   into [HistoryState.confirmClear]; [onClear] erases the entire log once the user confirmed.
 * - [onBack] is the BACK chip; [fullscreen] is the immersive preference.
 */
@Suppress("LongParameterList") // Every param is a genuinely distinct value the caller must
// supply - the search box, the results list, and the back pill below all need their own slice
// of it, so bundling would just move the same count into a wrapper class.
@Composable
fun H2g2HistoryScreen(
    state: HistoryState,
    onQueryChange: (String) -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (CommandHistoryEntry) -> Unit,
    onConfirmClearChange: (Boolean) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    fullscreen: Boolean,
    modifier: Modifier = Modifier
) {
    val entries = state.entries
    val query = state.query
    val muted = statusColor(StatusKind.Muted)

    Column(
        modifier
            .fillMaxSize()
            .background(LocalH2g2Ground.current.pageBrush())
            .screenInsets(fullscreen)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            H2g2Chip("‹ Back", onClick = onBack, contentDescription = "Back to settings")
            if (entries.isNotEmpty() || query.isNotBlank()) {
                H2g2Chip(
                    "Clear history", onClick = { onConfirmClearChange(true) },
                    tint = statusColor(StatusKind.Error),
                    contentDescription = "Clear all command history"
                )
            }
        }

        H2g2Heading("History", Modifier.padding(horizontal = 20.dp, vertical = 6.dp))

        HistorySearchBox(query, onQueryChange, resultCount = entries.size)

        if (state.loading) {
            Text(
                "Loading history…",
                color = muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp).liveRegionPolite()
            )
        } else if (entries.isEmpty()) {
            Text(
                if (query.isBlank()) "No commands recorded yet." else "No matches for “$query”.",
                color = muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp)) {
            // Index in the key: the same command can be logged twice in one millisecond, and a
            // duplicate LazyColumn key crashes.
            itemsIndexed(entries, key = { index, it -> "$index:${it.timestampMs}:${it.command}" }) { _, entry ->
                HistoryRow(entry, state.nowMillis, muted, onClick = { onSelect(entry.command) }, onDelete = { onDelete(entry) })
            }
        }
    }

    if (state.confirmClear) {
        H2g2ConfirmDialog(
            title = "Clear history?",
            message = "Erases every recorded command from every tab. Commands you already ran are " +
                "not affected, but they can no longer be searched or recalled from here.",
            confirmLabel = "CLEAR",
            onConfirm = { onConfirmClearChange(false); onClear() },
            onDismiss = { onConfirmClearChange(false) }
        )
    }
}

@Composable
private fun HistorySearchBox(query: String, onQueryChange: (String) -> Unit, resultCount: Int) {
    val onPage = LocalH2g2Ground.current.onPage
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(H2g2.ink.copy(alpha = .10f))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        if (query.isEmpty()) {
            Text(
                "SEARCH $resultCount COMMANDS", color = statusColor(StatusKind.Muted),
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = onPage, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            cursorBrush = SolidColor(onPage),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search command history" }
        )
    }
}

@Composable
private fun HistoryRow(
    entry: CommandHistoryEntry,
    nowMillis: Long,
    muted: Color,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val onPage = LocalH2g2Ground.current.onPage
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClickLabel = "Put in the command line", onClick = onClick)
                .padding(vertical = 10.dp)
        ) {
            Text(
                entry.command, color = onPage,
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 3, overflow = TextOverflow.Ellipsis
            )
            Text(
                "${entry.sessionLabel} · ${relativeTime(nowMillis - entry.timestampMs)}",
                color = muted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        H2g2Chip(
            "Delete", onClick = onDelete,
            contentDescription = "Delete ${entry.command} from history"
        )
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(onPage.copy(alpha = .12f)))
}

internal fun relativeTime(agoMs: Long): String {
    val seconds = (agoMs / MILLIS_PER_SECOND).coerceAtLeast(0)
    val minutes = seconds / SECONDS_PER_MINUTE
    val hours = minutes / MINUTES_PER_HOUR
    val days = hours / HOURS_PER_DAY
    return when {
        seconds < SECONDS_PER_MINUTE -> "just now"
        minutes < MINUTES_PER_HOUR -> "${minutes}m ago"
        hours < HOURS_PER_DAY -> "${hours}h ago"
        else -> "${days}d ago"
    }
}
