package com.hereliesaz.conveyance.h2g2.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.hereliesaz.conveyance.h2g2.H2g2
import com.hereliesaz.conveyance.h2g2.H2g2Chip
import com.hereliesaz.conveyance.h2g2.H2g2Surface
import com.hereliesaz.conveyance.h2g2.StatusKind
import com.hereliesaz.conveyance.h2g2.Text
import com.hereliesaz.conveyance.h2g2.liveRegionPolite
import com.hereliesaz.conveyance.h2g2.resources.Res
import com.hereliesaz.conveyance.h2g2.resources.mcp_connect_copied
import com.hereliesaz.conveyance.h2g2.resources.mcp_connect_copy
import com.hereliesaz.conveyance.h2g2.resources.mcp_connect_copy_description
import com.hereliesaz.conveyance.h2g2.resources.mcp_connect_description
import com.hereliesaz.conveyance.h2g2.resources.mcp_connect_title
import com.hereliesaz.conveyance.h2g2.resources.mcp_notifications_denied
import com.hereliesaz.conveyance.h2g2.resources.mcp_notifications_open_settings
import com.hereliesaz.conveyance.h2g2.resources.mcp_option_off
import com.hereliesaz.conveyance.h2g2.resources.mcp_option_on
import com.hereliesaz.conveyance.h2g2.resources.mcp_server_description_off
import com.hereliesaz.conveyance.h2g2.resources.mcp_server_description_on
import com.hereliesaz.conveyance.h2g2.resources.mcp_server_title
import com.hereliesaz.conveyance.h2g2.resources.mcp_shell_description_off
import com.hereliesaz.conveyance.h2g2.resources.mcp_shell_description_on
import com.hereliesaz.conveyance.h2g2.resources.mcp_shell_title
import com.hereliesaz.conveyance.h2g2.resources.mcp_title
import com.hereliesaz.conveyance.h2g2.resources.mcp_token_copied
import com.hereliesaz.conveyance.h2g2.resources.mcp_token_copy
import com.hereliesaz.conveyance.h2g2.resources.mcp_token_copy_description
import com.hereliesaz.conveyance.h2g2.resources.mcp_token_description
import com.hereliesaz.conveyance.h2g2.resources.mcp_token_hidden
import com.hereliesaz.conveyance.h2g2.resources.mcp_token_hidden_description
import com.hereliesaz.conveyance.h2g2.resources.mcp_token_hide
import com.hereliesaz.conveyance.h2g2.resources.mcp_token_show
import com.hereliesaz.conveyance.h2g2.resources.mcp_token_title
import com.hereliesaz.conveyance.h2g2.resources.mcp_token_value_description
import com.hereliesaz.conveyance.h2g2.statusColor
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/** How long the "copied" confirmation stays on screen. */
private const val COPIED_NOTICE_MS = 3_000L

/** The clip label [H2g2McpServerScreen] passes with the pairing token. */
const val MCP_TOKEN_CLIP_LABEL = "HG2Gui MCP pairing token"

/** The clip label [H2g2McpServerScreen] passes with the `adb forward` line. */
const val MCP_CONNECT_CLIP_LABEL = "HG2Gui MCP adb forward"

/** Which of [H2g2McpServerScreen]'s two copy chips was pressed last; drives the "copied" line. */
enum class McpCopied {
    /** The pairing token. */
    Token,

    /** The `adb forward` command line. */
    Connect
}

/**
 * The MCP server as the host knows it. [toString] leaves the token out: it is a secret.
 *
 * @property running true while the server is listening.
 * @property port the loopback port it listens on.
 * @property token the pairing token, or null when there is none yet.
 * @property shellExecEnabled true when MCP clients may run shell commands.
 * @property notificationsDenied true when starting was blocked by a refused notification
 *   permission (the foreground-service notification is the only visible sign a shell is exposed);
 *   the screen then explains why nothing started and offers the system settings page instead of
 *   leaving the ON toggle silently inert.
 */
data class McpServerState(
    val running: Boolean = false,
    val port: Int = 0,
    val token: String? = null,
    val shellExecEnabled: Boolean = false,
    val notificationsDenied: Boolean = false
) {
    override fun toString(): String =
        "McpServerState(running=$running, port=$port, token=${if (token.isNullOrEmpty()) "<none>" else "<hidden>"}, " +
            "shellExecEnabled=$shellExecEnabled, notificationsDenied=$notificationsDenied)"
}

/**
 * [H2g2McpServerScreen]'s own view state, hoisted: keep it in `remember { mutableStateOf(McpServerUiState()) }`
 * and replace it with whatever `onUiChange` hands back.
 *
 * @property tokenRevealed true while the token is shown in clear rather than dotted out.
 * @property copied which copy was just made; the screen clears it again after three seconds.
 */
data class McpServerUiState(
    val tokenRevealed: Boolean = false,
    val copied: McpCopied? = null
)

/**
 * Server on/off, the shell-exec toggle (gated on enable - the actual authentication happens in
 * the host, which owns the platform prompt; this screen only ever asks for it via
 * [onRequestShellExec] and shows whatever the host decides), and a tap-to-reveal, copyable pairing
 * token with the matching `adb forward` line. Reached from Settings, not the terminal's own pill
 * stack - this is device configuration, not something to run.
 *
 * - [state] is the server; [ui] is the reveal/copied view state and [onUiChange] replaces it.
 * - [onStart] / [onStop] and [onRequestShellExec] / [onDisableShellExec] are the two toggles.
 * - [onCopy] puts `text` on the clipboard under `label` ([MCP_TOKEN_CLIP_LABEL] or
 *   [MCP_CONNECT_CLIP_LABEL]); `sensitive` asks the platform not to show the value in a clipboard
 *   preview, which matters for the pairing token (on Android 13+, set the clip description's
 *   `android.content.extra.IS_SENSITIVE` extra).
 * - [onOpenNotificationSettings] opens the app's page in system settings; it is only offered
 *   while [McpServerState.notificationsDenied] and the server is off.
 */
@Suppress("LongParameterList")
@Composable
fun H2g2McpServerScreen(
    fullscreen: Boolean,
    state: McpServerState,
    ui: McpServerUiState,
    onUiChange: (McpServerUiState) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRequestShellExec: () -> Unit,
    onDisableShellExec: () -> Unit,
    onCopy: (label: String, text: String, sensitive: Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onBack: () -> Unit
) {
    val running = state.running
    val port = state.port
    val shellExecEnabled = state.shellExecEnabled
    val tokenRevealed = ui.tokenRevealed
    val copied = ui.copied
    val connectCommand = "adb forward tcp:$port tcp:$port"
    val latestUi by rememberUpdatedState(ui)
    val latestOnUiChange by rememberUpdatedState(onUiChange)

    // The confirmation is transient; clearing it also lets the same copy be announced again.
    LaunchedEffect(copied) {
        if (copied != null) {
            delay(COPIED_NOTICE_MS)
            latestOnUiChange(latestUi.copy(copied = null))
        }
    }

    SettingsPage(title = stringResource(Res.string.mcp_title), fullscreen = fullscreen, onBack = onBack) {
        val serverTitle = stringResource(Res.string.mcp_server_title)
        SettingRow(
            title = serverTitle,
            // MCP-7: loopback-only means no other device on the network can reach this - it does
            // not mean no other app can. Any app on this device with plain INTERNET permission
            // can open the same socket; the token below is what actually stands between it and
            // this app's sandboxed files, not the network boundary.
            description = if (running)
                stringResource(Res.string.mcp_server_description_on, port.toString())
            else
                stringResource(Res.string.mcp_server_description_off)
        ) {
            SegmentedToggle(
                groupLabel = serverTitle,
                leftLabel = stringResource(Res.string.mcp_option_off),
                rightLabel = stringResource(Res.string.mcp_option_on),
                rightSelected = running
            ) { wantOn ->
                if (wantOn) onStart() else onStop()
            }
            if (state.notificationsDenied && !running) {
                Text(
                    stringResource(Res.string.mcp_notifications_denied),
                    color = statusColor(StatusKind.Error),
                    style = settingDescriptionStyle(),
                    modifier = Modifier.padding(top = 8.dp).liveRegionPolite()
                )
                H2g2Chip(
                    label = stringResource(Res.string.mcp_notifications_open_settings),
                    onClick = onOpenNotificationSettings
                )
            }
        }

        if (running) {
            SettingRow(
                title = stringResource(Res.string.mcp_token_title),
                description = stringResource(Res.string.mcp_token_description)
            ) {
                val tokenText = state.token.orEmpty()
                CodeCapsule(
                    text = if (tokenRevealed) tokenText else stringResource(Res.string.mcp_token_hidden),
                    description = if (tokenRevealed) {
                        stringResource(Res.string.mcp_token_value_description, tokenText)
                    } else {
                        stringResource(Res.string.mcp_token_hidden_description)
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    H2g2Chip(
                        label = stringResource(if (tokenRevealed) Res.string.mcp_token_hide else Res.string.mcp_token_show),
                        onClick = { onUiChange(ui.copy(tokenRevealed = !tokenRevealed)) }
                    )
                    H2g2Chip(
                        label = stringResource(Res.string.mcp_token_copy),
                        onClick = {
                            onCopy(MCP_TOKEN_CLIP_LABEL, tokenText, true)
                            onUiChange(ui.copy(copied = McpCopied.Token))
                        },
                        enabled = tokenText.isNotEmpty(),
                        contentDescription = stringResource(Res.string.mcp_token_copy_description)
                    )
                }
                CopiedNotice(
                    when (copied) {
                        McpCopied.Token -> stringResource(Res.string.mcp_token_copied)
                        McpCopied.Connect -> stringResource(Res.string.mcp_connect_copied)
                        null -> null
                    }
                )
            }

            SettingRow(
                title = stringResource(Res.string.mcp_connect_title),
                description = stringResource(Res.string.mcp_connect_description)
            ) {
                CodeCapsule(text = connectCommand, description = connectCommand)
                H2g2Chip(
                    label = stringResource(Res.string.mcp_connect_copy),
                    onClick = {
                        onCopy(MCP_CONNECT_CLIP_LABEL, connectCommand, false)
                        onUiChange(ui.copy(copied = McpCopied.Connect))
                    },
                    contentDescription = stringResource(Res.string.mcp_connect_copy_description)
                )
            }

            val shellTitle = stringResource(Res.string.mcp_shell_title)
            SettingRow(
                title = shellTitle,
                description = if (shellExecEnabled)
                    stringResource(Res.string.mcp_shell_description_on)
                else
                    stringResource(Res.string.mcp_shell_description_off)
            ) {
                SegmentedToggle(
                    groupLabel = shellTitle,
                    leftLabel = stringResource(Res.string.mcp_option_off),
                    rightLabel = stringResource(Res.string.mcp_option_on),
                    rightSelected = shellExecEnabled
                ) { wantOn ->
                    if (wantOn) onRequestShellExec() else onDisableShellExec()
                }
            }
        }
    }
}

/** A monospace value on an ink panel (a token, a command line). [description] is what a screen reader speaks. */
@Composable
private fun CodeCapsule(text: String, description: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(H2g2Surface.note)
            .background(H2g2.ink)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clearAndSetSemantics { contentDescription = description }
    ) {
        Text(
            text,
            color = H2g2.yellow,
            style = settingDescriptionStyle().copy(fontFamily = FontFamily.Monospace)
        )
    }
}

/** The transient "copied" line; empty when [message] is null. A polite live region so it is announced. */
@Composable
private fun CopiedNotice(message: String?) {
    Box(Modifier.liveRegionPolite()) {
        if (message != null) {
            Text(
                message,
                color = statusColor(StatusKind.Success),
                style = settingDescriptionStyle(),
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
