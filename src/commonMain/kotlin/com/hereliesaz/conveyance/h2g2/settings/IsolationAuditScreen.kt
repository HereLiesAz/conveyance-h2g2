package com.hereliesaz.conveyance.h2g2.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import kotlin.math.floor

/**
 * What one isolated run changed and did, as the host's sampler recorded it.
 *
 * @property created sandbox paths that appeared during the run.
 * @property modified sandbox paths whose size or time changed.
 * @property deleted sandbox paths that disappeared.
 * @property telemetry observed runtime activity, one row each, prefixed with its kind:
 *   `authority-attempt:`, `network:`, `process:` / `root-process:`, `file-...:`; anything else is
 *   grouped under OTHER. A `network:` row is `network:<proto>:<local>:<remote>:<state>:<inode>`
 *   with hex `ADDR:PORT` pairs.
 */
data class IsolationAudit(
    val created: List<String>,
    val modified: List<String>,
    val deleted: List<String>,
    val telemetry: List<String> = emptyList()
) {
    /** How many sandbox paths were created, modified or deleted in total. */
    val changedCount: Int get() = created.size + modified.size + deleted.size
}

/** An [audit] with the time its run completed ([timestampMillis], epoch ms; 0 or less = unknown). */
data class SavedIsolationAudit(val timestampMillis: Long, val audit: IsolationAudit)

/**
 * One isolated package's card on [H2g2IsolationAuditScreen].
 *
 * @property packageName the package's name; the card title.
 * @property managerLabel the package manager it came from.
 * @property version its version; blank reads as "installed".
 * @property savedAudit the latest completed run's audit, or null when it has never been run.
 * @property fileCount how many files its sandbox holds.
 * @property bytes their total size.
 */
data class IsolationAuditEntry(
    val packageName: String,
    val managerLabel: String,
    val version: String,
    val savedAudit: SavedIsolationAudit?,
    val fileCount: Int,
    val bytes: Long
) {
    /**
     * Identifies this package's latest run: the key for [IsolationAuditState.ai] and
     * [IsolationAuditUiState.confirmSendFor]. A new run has a new key, so an interpretation of
     * the previous run is not shown under it.
     */
    val auditKey: String get() = "$packageName@${savedAudit?.timestampMillis}"
}

/**
 * The AI's reading of one audit, as the host tracks it.
 *
 * @property result the interpretation (or the failure sentence) to show under AI INTERPRETATION;
 *   null until there is one.
 * @property busy true while the request is in flight: the chip reads ASKING AI… and is disabled.
 */
data class AuditAiState(val result: String? = null, val busy: Boolean = false)

/**
 * What [H2g2IsolationAuditScreen] shows. Reading the sandboxes and the saved audits is the
 * host's work, off the main thread.
 *
 * @property entries one per isolated package; null while they are first being read
 *   ("READING AUDITS…"), empty when there are none.
 * @property refreshing true while a refresh is running: the REFRESH chip is disabled.
 * @property aiReady true when an AI provider is configured and may be asked; the ASK AI chip is
 *   only offered then.
 * @property aiProviderLabel who the summary would be sent to, named in the disclosure dialog.
 * @property ai per-audit AI state, keyed by [IsolationAuditEntry.auditKey].
 */
data class IsolationAuditState(
    val entries: List<IsolationAuditEntry>? = null,
    val refreshing: Boolean = false,
    val aiReady: Boolean = false,
    val aiProviderLabel: String = "Anthropic",
    val ai: Map<String, AuditAiState> = emptyMap()
)

/**
 * [H2g2IsolationAuditScreen]'s own view state, hoisted: keep it in a `rememberSaveable` or
 * `remember { mutableStateOf(IsolationAuditUiState()) }` and replace it with whatever
 * `onUiChange` hands back.
 *
 * @property confirmSendFor the [IsolationAuditEntry.auditKey] whose "send to AI?" disclosure is
 *   up, or null.
 * @property shownRows how many rows each expandable list currently shows, by list key; a list
 *   that is absent shows its first 40.
 */
data class IsolationAuditUiState(
    val confirmSendFor: String? = null,
    val shownRows: Map<String, Int> = emptyMap()
)

/**
 * The Isolation audit: for each isolated package, the latest completed run's persisted sandbox
 * diff plus the runtime activity observed while it ran, grouped as AUTHORITY / NETWORK / PROCESS /
 * FILES / OTHER, and (when an AI provider is set up) a chip to have the summary interpreted.
 *
 * - [state] is the data; [ui] is the view state (open disclosure, expanded lists) and
 *   [onUiChange] replaces it.
 * - [onRefresh] re-reads the audits; set [IsolationAuditState.refreshing] while it runs.
 * - [onAskAi] fires once the user has read the disclosure ([aiDisclosureText]) and pressed SEND.
 *   It receives the entry and the exact prompt that disclosure describes
 *   ([auditInterpretationPrompt]): send that and nothing else, mark the audit
 *   [busy][AuditAiState.busy] meanwhile, then put the answer - or a sentence saying it failed -
 *   in [AuditAiState.result]. It is not called again while that audit is busy.
 * - [formatTimestamp] renders a capture time (epoch ms, always positive). The default is a plain
 *   UTC `YYYY-MM-DD HH:MM:SS UTC`; pass the platform's own medium date-time format to match
 *   HG2Gui, which shows the device's locale and zone.
 * - [onBack] is the BACK chip; [fullscreen] is the immersive preference.
 */
@Suppress("LongParameterList")
@Composable
fun H2g2IsolationAuditScreen(
    state: IsolationAuditState,
    ui: IsolationAuditUiState,
    onUiChange: (IsolationAuditUiState) -> Unit,
    onRefresh: () -> Unit,
    onAskAi: (entry: IsolationAuditEntry, prompt: String) -> Unit,
    onBack: () -> Unit,
    fullscreen: Boolean,
    modifier: Modifier = Modifier,
    formatTimestamp: (Long) -> String = ::utcTimestamp
) {
    val refreshing = state.refreshing
    val onPage = LocalH2g2Ground.current.onPage
    val muted = statusColor(StatusKind.Muted)
    Column(
        modifier
            .fillMaxSize()
            .background(LocalH2g2Ground.current.pageBrush())
            .screenInsets(fullscreen, ime = false)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            H2g2Chip("‹ BACK", onBack, contentDescription = "Back")
            H2g2Chip(
                label = if (refreshing) "REFRESHING…" else "REFRESH",
                onClick = onRefresh,
                enabled = !refreshing,
                contentDescription = if (refreshing) "Refreshing audits" else "Refresh audits"
            )
        }
        H2g2Heading("Isolation audit")
        Text(
            "Latest completed isolated run per package: persisted sandbox diff plus observed runtime activity. " +
                "Runtime observation is sampled and is not syscall-complete.",
            color = muted,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )

        when (val current = state.entries) {
            null -> Text("READING AUDITS…", color = muted, fontSize = 12.sp, modifier = Modifier.liveRegionPolite())
            emptyList<IsolationAuditEntry>() -> Text("No isolated packages.", color = onPage, modifier = Modifier.liveRegionPolite())
            else -> current.forEach { entry ->
                AuditCard(entry, state, ui, onUiChange, onAskAi, formatTimestamp)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Suppress("LongParameterList")
@Composable
private fun AuditCard(
    entry: IsolationAuditEntry,
    state: IsolationAuditState,
    ui: IsolationAuditUiState,
    onUiChange: (IsolationAuditUiState) -> Unit,
    onAskAi: (IsolationAuditEntry, String) -> Unit,
    formatTimestamp: (Long) -> String
) {
    val aiReady = state.aiReady
    val key = entry.auditKey
    val ai = state.ai[key] ?: AuditAiState()
    val aiResult = ai.result
    val aiBusy = ai.busy
    val confirmSend = ui.confirmSendFor == key
    val onPage = LocalH2g2Ground.current.onPage
    val muted = statusColor(StatusKind.Muted)

    Column(
        Modifier.fillMaxWidth().background(H2g2.ink.copy(alpha = .09f), RoundedCornerShape(22.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(entry.packageName, color = onPage, fontWeight = FontWeight.Black, fontSize = 17.sp)
        Text(
            "${entry.managerLabel} · ${entry.version.ifBlank { "installed" }} · " +
                "${entry.fileCount} sandbox files · ${formatBytes(entry.bytes)}",
            color = muted,
            fontSize = 11.sp
        )

        val saved = entry.savedAudit
        if (saved == null) {
            Text("No completed-run audit yet. Run this isolated package, then refresh.", color = muted, fontSize = 12.sp)
        } else {
            SectionLabel("CAPTURED ${capturedLabel(saved.timestampMillis, formatTimestamp)}")
            SandboxDiff(entry.packageName, saved.audit, ui, onUiChange)
            RuntimeTelemetry(entry.packageName, saved.audit.telemetry, ui, onUiChange)

            if (aiReady) {
                H2g2Chip(
                    label = if (aiBusy) "ASKING AI…" else "ASK AI · SEND SUMMARY",
                    onClick = { onUiChange(ui.copy(confirmSendFor = key)) },
                    enabled = !aiBusy,
                    contentDescription = if (aiBusy) "Asking the AI" else "Ask the AI to interpret this audit; shows what will be sent first"
                )
            }
            aiResult?.let { result ->
                SectionLabel("AI INTERPRETATION")
                Text(result, color = onPage, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.liveRegionPolite())
            }
        }
    }

    if (confirmSend && aiReady) {
        H2g2ConfirmDialog(
            title = "SEND AUDIT SUMMARY TO AI?",
            message = aiDisclosureText(state.aiProviderLabel),
            confirmLabel = "SEND",
            onConfirm = {
                onUiChange(ui.copy(confirmSendFor = null))
                if (aiBusy) return@H2g2ConfirmDialog
                // The prompt carries a placeholder view of the sandbox, not its real absolute
                // path - the device's on-disk layout (user id, package dir) isn't the AI's business.
                onAskAi(entry, auditInterpretationPrompt(entry))
            },
            onDismiss = { onUiChange(ui.copy(confirmSendFor = null)) }
        )
    }
}

/** A small caps label above a block of audit rows. Muted, but legible (see [StatusKind.Muted]). */
@Composable
private fun SectionLabel(text: String) {
    Text(text, color = statusColor(StatusKind.Muted), fontWeight = FontWeight.Black, fontSize = 11.sp)
}

@Composable
private fun SandboxDiff(
    packageName: String,
    audit: IsolationAudit,
    ui: IsolationAuditUiState,
    onUiChange: (IsolationAuditUiState) -> Unit
) {
    SectionLabel("SANDBOX DIFF · +${audit.created.size}  ~${audit.modified.size}  −${audit.deleted.size}")
    if (audit.changedCount == 0) {
        Text("No sandbox filesystem changes observed.", color = statusColor(StatusKind.Muted), fontSize = 12.sp)
        return
    }
    AuditRows("$packageName/created", "+", audit.created, ui, onUiChange)
    AuditRows("$packageName/modified", "~", audit.modified, ui, onUiChange)
    AuditRows("$packageName/deleted", "−", audit.deleted, ui, onUiChange)
}

@Composable
private fun RuntimeTelemetry(
    packageName: String,
    telemetry: List<String>,
    ui: IsolationAuditUiState,
    onUiChange: (IsolationAuditUiState) -> Unit
) {
    SectionLabel("RUNTIME · ${telemetry.size} OBSERVATIONS")
    if (telemetry.isEmpty()) {
        Text("No sampled runtime activity recorded.", color = statusColor(StatusKind.Muted), fontSize = 12.sp)
        return
    }
    val groups = telemetry.groupBy(::telemetryKind)
    listOf("AUTHORITY", "NETWORK", "PROCESS", "FILES", "OTHER").forEach { kind ->
        val rows = groups[kind].orEmpty()
        if (rows.isNotEmpty()) {
            SectionLabel(kind)
            ExpandableRows("$packageName/telemetry/$kind", rows.map(::telemetryDetail), ui, onUiChange)
        }
    }
}

@Composable
private fun AuditRows(
    stateKey: String,
    marker: String,
    paths: List<String>,
    ui: IsolationAuditUiState,
    onUiChange: (IsolationAuditUiState) -> Unit
) {
    ExpandableRows(stateKey, paths.map { "$marker $it" }, ui, onUiChange)
}

/**
 * [rows] as text, the first [MAX_ROWS_PER_SECTION] at first and [MAX_ROWS_PER_SECTION] more per tap
 * of the "… N MORE" chip (a sandbox can hold tens of thousands of files, so it opens in steps rather
 * than composing them all at once). Once past the first page a SHOW LESS chip folds it back.
 * [stateKey] must be unique per list on the screen; the count lives in
 * [IsolationAuditUiState.shownRows] under it, so it survives for as long as the host keeps that.
 */
@Composable
private fun ExpandableRows(
    stateKey: String,
    rows: List<String>,
    ui: IsolationAuditUiState,
    onUiChange: (IsolationAuditUiState) -> Unit
) {
    val shown = ui.shownRows[stateKey] ?: MAX_ROWS_PER_SECTION
    val setShown = { count: Int -> onUiChange(ui.copy(shownRows = ui.shownRows + (stateKey to count))) }
    val onPage = LocalH2g2Ground.current.onPage
    rows.take(shown).forEach { row ->
        Text(row, color = onPage, fontSize = 11.sp, lineHeight = 15.sp)
    }
    val remaining = rows.size - shown
    if (remaining > 0) {
        H2g2Chip(
            label = "… $remaining MORE",
            onClick = { setShown(nextVisibleCount(shown, rows.size, MAX_ROWS_PER_SECTION)) },
            contentDescription = "Show more rows, $remaining remaining"
        )
    } else if (shown > MAX_ROWS_PER_SECTION) {
        H2g2Chip("SHOW LESS", { setShown(MAX_ROWS_PER_SECTION) }, contentDescription = "Show fewer rows")
    }
}

/** How many rows to show after one "more" tap: [current] plus [step], never past [total]. */
internal fun nextVisibleCount(current: Int, total: Int, step: Int): Int =
    minOf(total, current + step).coerceAtLeast(current)

/**
 * What the confirmation shows before the audit summary leaves the device. It describes exactly what
 * [auditInterpretationPrompt] builds - keep the two in step (the caps are the same constant).
 */
fun aiDisclosureText(providerLabel: String = "Anthropic"): String =
    "This sends a text summary of this package's latest run to $providerLabel, using your API key: " +
        "the package name and version; the sandbox file count and size; how many files were created, " +
        "modified and deleted; the file names (never the folders) of up to $AI_MAX_ROWS in each of those groups, " +
        "leaving out any whose path mentions a password, secret, token, key or similar; how many runtime " +
        "events of each kind were observed; and the protocol and remote port (never the address) of up to " +
        "$AI_MAX_ROWS network connections. Full paths, command lines and addresses stay on this device."

/**
 * The question sent to the AI for [entry]: the redacted summary [aiDisclosureText] promises, and
 * nothing more. [H2g2IsolationAuditScreen] hands the result to its `onAskAi`.
 */
fun auditInterpretationPrompt(entry: IsolationAuditEntry): String {
    val audit = entry.savedAudit?.audit ?: return "No audit exists."
    fun safeRows(rows: List<String>): List<String> = rows
        .filterNot { SENSITIVE_WORDS.containsMatchIn(it) }
        .take(AI_MAX_ROWS)
    return buildString {
        appendLine("Interpret this HG2Gui isolated-package audit. Give a concise risk/behavior summary. Do not propose elevated commands unless explicitly necessary, and remember the runtime sampler is best-effort rather than syscall-complete.")
        appendLine("Package: ${entry.packageName} ${entry.version}")
        appendLine("Sandbox: ${entry.fileCount} files, ${formatBytes(entry.bytes)}")
        appendLine("Created ${audit.created.size}, modified ${audit.modified.size}, deleted ${audit.deleted.size}.")
        safeRows(audit.created).forEach { appendLine("created: ${it.substringAfterLast('/')}") }
        safeRows(audit.modified).forEach { appendLine("modified: ${it.substringAfterLast('/')}") }
        safeRows(audit.deleted).forEach { appendLine("deleted: ${it.substringAfterLast('/')}") }
        // Raw telemetry rows carry full command lines, file paths and peer addresses - only
        // per-category counts, plus network peers with the address redacted, leave the device.
        audit.telemetry.groupingBy(::telemetryKind).eachCount().toList().sortedBy { it.first }.forEach { (kind, count) ->
            appendLine("observed $kind events: $count")
        }
        audit.telemetry.filter { telemetryKind(it) == "NETWORK" }
            .mapNotNull(::redactedNetworkRow)
            .distinct()
            .take(AI_MAX_ROWS)
            .forEach { appendLine("network: $it") }
    }
}

internal fun telemetryKind(line: String): String = when {
    line.startsWith("authority-attempt:") -> "AUTHORITY"
    line.startsWith("network:") -> "NETWORK"
    line.startsWith("process:") || line.startsWith("root-process:") -> "PROCESS"
    line.startsWith("file-") -> "FILES"
    else -> "OTHER"
}

/**
 * `network:<proto>:<local>:<remote>:<state>:<inode>` (from /proc/net, hex `ADDR:PORT` pairs)
 * reduced to `<proto> remote port <n>` - the peer's address is withheld, only its port (which
 * says what kind of service was contacted) is kept. Null for a row that doesn't parse.
 */
internal fun redactedNetworkRow(line: String): String? {
    val fields = line.removePrefix("network:").split(':')
    val proto = fields.firstOrNull()?.takeIf { it.matches(Regex("[a-z0-9]+")) }
    val remotePort = fields.getOrNull(4)?.toIntOrNull(16)
    return if (proto == null || remotePort == null) null else "$proto remote port $remotePort"
}

private fun telemetryDetail(line: String): String = line.substringAfter(':', line)

private fun capturedLabel(timestampMillis: Long, formatTimestamp: (Long) -> String): String =
    if (timestampMillis <= 0L) "UNKNOWN TIME" else formatTimestamp(timestampMillis)

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * [timestampMillis] (epoch ms) as `YYYY-MM-DD HH:MM:SS UTC`: the platform-neutral default for
 * [H2g2IsolationAuditScreen]'s `formatTimestamp`. Common code has no locale or time-zone
 * database, so this is always the proleptic Gregorian calendar in UTC.
 */
fun utcTimestamp(timestampMillis: Long): String {
    val days = floorDiv(timestampMillis, MILLIS_PER_DAY)
    val secondOfDay = ((timestampMillis - days * MILLIS_PER_DAY) / 1000L).toInt()
    // Days since 1970-01-01 to a civil date (Howard Hinnant's algorithm).
    val z = days + 719_468L
    val era = floorDiv(z, 146_097L)
    val dayOfEra = z - era * 146_097L
    val yearOfEra = (dayOfEra - dayOfEra / 1460L + dayOfEra / 36_524L - dayOfEra / 146_096L) / 365L
    val dayOfYear = dayOfEra - (365L * yearOfEra + yearOfEra / 4L - yearOfEra / 100L)
    val mp = (5L * dayOfYear + 2L) / 153L
    val day = dayOfYear - (153L * mp + 2L) / 5L + 1L
    val month = if (mp < 10L) mp + 3L else mp - 9L
    val year = yearOfEra + era * 400L + if (month <= 2L) 1L else 0L
    fun two(n: Long) = n.toString().padStart(2, '0')
    val h = secondOfDay / 3600
    val m = secondOfDay % 3600 / 60
    val s = secondOfDay % 60
    return "$year-${two(month)}-${two(day)} ${two(h.toLong())}:${two(m.toLong())}:${two(s.toLong())} UTC"
}

private fun floorDiv(a: Long, b: Long): Long {
    val q = a / b
    return if ((a % b != 0L) && ((a < 0L) != (b < 0L))) q - 1L else q
}

/** [value] with exactly one decimal, rounded half up - what `"%.1f".format(value)` prints. */
private fun oneDecimal(value: Double): String {
    val tenths = floor(value * 10.0 + 0.5).toLong()
    return "${tenths / 10L}.${tenths % 10L}"
}

internal fun formatBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${oneDecimal(bytes / 1024.0)} KiB"
    bytes < 1024L * 1024L * 1024L -> "${oneDecimal(bytes / (1024.0 * 1024.0))} MiB"
    else -> "${oneDecimal(bytes / (1024.0 * 1024.0 * 1024.0))} GiB"
}

private val SENSITIVE_WORDS = Regex(
    "(password|passwd|secret|token|api[_-]?key|authorization|bearer|credential|private[_-]?key)",
    RegexOption.IGNORE_CASE
)
private const val MAX_ROWS_PER_SECTION = 40

/** Cap on each list of rows that goes to the AI - and that [aiDisclosureText] promises. */
const val AI_MAX_ROWS = 24
