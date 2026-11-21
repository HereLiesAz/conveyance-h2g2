package com.hereliesaz.conveyance.h2g2.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hereliesaz.conveyance.h2g2.H2g2
import com.hereliesaz.conveyance.h2g2.H2g2Chip
import com.hereliesaz.conveyance.h2g2.H2g2Heading
import com.hereliesaz.conveyance.h2g2.H2g2Surface
import com.hereliesaz.conveyance.h2g2.LocalH2g2Ground
import com.hereliesaz.conveyance.h2g2.StatusKind
import com.hereliesaz.conveyance.h2g2.Text
import com.hereliesaz.conveyance.h2g2.currentH2g2Type
import com.hereliesaz.conveyance.h2g2.headingRole
import com.hereliesaz.conveyance.h2g2.liveRegionPolite
import com.hereliesaz.conveyance.h2g2.maxContentWidth
import com.hereliesaz.conveyance.h2g2.onPage
import com.hereliesaz.conveyance.h2g2.pageBrush
import com.hereliesaz.conveyance.h2g2.rememberReducedMotion
import com.hereliesaz.conveyance.h2g2.resources.Res
import com.hereliesaz.conveyance.h2g2.resources.settings_about_description
import com.hereliesaz.conveyance.h2g2.resources.settings_about_licences
import com.hereliesaz.conveyance.h2g2.resources.settings_about_licences_description
import com.hereliesaz.conveyance.h2g2.resources.settings_about_privacy
import com.hereliesaz.conveyance.h2g2.resources.settings_about_privacy_description
import com.hereliesaz.conveyance.h2g2.resources.settings_about_title
import com.hereliesaz.conveyance.h2g2.resources.settings_ai_button
import com.hereliesaz.conveyance.h2g2.resources.settings_ai_button_description
import com.hereliesaz.conveyance.h2g2.resources.settings_ai_description
import com.hereliesaz.conveyance.h2g2.resources.settings_ai_title
import com.hereliesaz.conveyance.h2g2.resources.settings_back
import com.hereliesaz.conveyance.h2g2.resources.settings_back_description
import com.hereliesaz.conveyance.h2g2.resources.settings_developer_button
import com.hereliesaz.conveyance.h2g2.resources.settings_developer_button_description
import com.hereliesaz.conveyance.h2g2.resources.settings_developer_description
import com.hereliesaz.conveyance.h2g2.resources.settings_developer_title
import com.hereliesaz.conveyance.h2g2.resources.settings_environment_button
import com.hereliesaz.conveyance.h2g2.resources.settings_environment_button_description
import com.hereliesaz.conveyance.h2g2.resources.settings_environment_description
import com.hereliesaz.conveyance.h2g2.resources.settings_environment_title
import com.hereliesaz.conveyance.h2g2.resources.settings_fullscreen_description_off
import com.hereliesaz.conveyance.h2g2.resources.settings_fullscreen_description_on
import com.hereliesaz.conveyance.h2g2.resources.settings_fullscreen_option_fullscreen
import com.hereliesaz.conveyance.h2g2.resources.settings_fullscreen_option_safe_area
import com.hereliesaz.conveyance.h2g2.resources.settings_fullscreen_title
import com.hereliesaz.conveyance.h2g2.resources.settings_history_button
import com.hereliesaz.conveyance.h2g2.resources.settings_history_button_description
import com.hereliesaz.conveyance.h2g2.resources.settings_history_description
import com.hereliesaz.conveyance.h2g2.resources.settings_history_title
import com.hereliesaz.conveyance.h2g2.resources.settings_isolation_button
import com.hereliesaz.conveyance.h2g2.resources.settings_isolation_button_description
import com.hereliesaz.conveyance.h2g2.resources.settings_isolation_description
import com.hereliesaz.conveyance.h2g2.resources.settings_isolation_title
import com.hereliesaz.conveyance.h2g2.resources.settings_motion_button
import com.hereliesaz.conveyance.h2g2.resources.settings_motion_description_off
import com.hereliesaz.conveyance.h2g2.resources.settings_motion_description_on
import com.hereliesaz.conveyance.h2g2.resources.settings_motion_title
import com.hereliesaz.conveyance.h2g2.resources.settings_option_off
import com.hereliesaz.conveyance.h2g2.resources.settings_option_on
import com.hereliesaz.conveyance.h2g2.resources.settings_pty_description
import com.hereliesaz.conveyance.h2g2.resources.settings_pty_option_pipe
import com.hereliesaz.conveyance.h2g2.resources.settings_pty_option_pty
import com.hereliesaz.conveyance.h2g2.resources.settings_pty_title
import com.hereliesaz.conveyance.h2g2.resources.settings_shell_description
import com.hereliesaz.conveyance.h2g2.resources.settings_shell_none
import com.hereliesaz.conveyance.h2g2.resources.settings_shell_title
import com.hereliesaz.conveyance.h2g2.resources.settings_text_size_decrease
import com.hereliesaz.conveyance.h2g2.resources.settings_text_size_description
import com.hereliesaz.conveyance.h2g2.resources.settings_text_size_increase
import com.hereliesaz.conveyance.h2g2.resources.settings_text_size_reset
import com.hereliesaz.conveyance.h2g2.resources.settings_text_size_title
import com.hereliesaz.conveyance.h2g2.resources.settings_title
import com.hereliesaz.conveyance.h2g2.resources.settings_updates_description_off
import com.hereliesaz.conveyance.h2g2.resources.settings_updates_description_on
import com.hereliesaz.conveyance.h2g2.resources.settings_updates_title
import com.hereliesaz.conveyance.h2g2.screenInsets
import com.hereliesaz.conveyance.h2g2.statusColor
import org.jetbrains.compose.resources.stringResource

/**
 * Bounds of the Text-size setting. The floor is 85%: below that the pill labels and chip text
 * drop under 10sp on the glass and the touch targets shrink with them, which the old 50% floor
 * allowed with no way back short of finding the stepper again. A stored value from before the
 * change is pulled up to the floor when Settings opens (see [clampFontScalePercent]).
 */
const val FONT_SCALE_MIN_PERCENT = 85

/** The largest Text-size setting [H2g2SettingsScreen]'s stepper offers. */
const val FONT_SCALE_MAX_PERCENT = 200

/** The Text-size setting a host starts from, and what the RESET chip returns to. */
const val FONT_SCALE_DEFAULT_PERCENT = 100
private const val FONT_SCALE_STEP_PERCENT = 10

/** HG2Gui's privacy policy: the default [SettingsState.privacyPolicyUrl]. */
const val PRIVACY_POLICY_URL = "https://github.com/HereLiesAz/HG2Gui/blob/HEAD/docs/PRIVACY_POLICY.md"

/** HG2Gui's source repository, where its licences are listed: the default [SettingsState.sourceUrl]. */
const val SOURCE_URL = "https://github.com/HereLiesAz/HG2Gui"

/**
 * [percent] forced into [FONT_SCALE_MIN_PERCENT]..[FONT_SCALE_MAX_PERCENT]. Public so the host
 * can apply it to the stored value when it reads the preference, before Settings is ever opened.
 */
fun clampFontScalePercent(percent: Int): Int = percent.coerceIn(FONT_SCALE_MIN_PERCENT, FONT_SCALE_MAX_PERCENT)

/** One stepper press: [direction] is -1 or +1 step; the result is always inside the allowed range. */
internal fun steppedFontScalePercent(current: Int, direction: Int): Int =
    clampFontScalePercent(current + direction * FONT_SCALE_STEP_PERCENT)

/**
 * The Text-size setting as a density multiplier, clamped to the range Settings offers (85%..200%).
 * Feed it to `H2g2Theme(scale = ...)`.
 */
fun uiScaleFromPercent(percent: Int): Float =
    percent.coerceIn(FONT_SCALE_MIN_PERCENT, FONT_SCALE_MAX_PERCENT) / 100f

/**
 * The shells HG2Gui's Default-shell picker knows, and the pure rules it applies to them. The host
 * decides which are installed (that is a file-system question) and stores the choice; this only
 * names them and says which one the picker should show as chosen.
 */
object ShellPreference {
    /** The shell every install has, and the stored default. */
    const val BASH = "bash"

    /** Offered once the `zsh` package is installed. */
    const val ZSH = "zsh"

    /** Offered once the `fish` package is installed. */
    const val FISH = "fish"

    /**
     * Which of the known shells are present, in picker order (bash first). Empty when none is
     * executable: the picker then shows an explicit "no shell" state instead of offering (and
     * highlighting) a shell that is not installed. Pass the result as [SettingsState.availableShells].
     */
    fun availableFrom(bash: Boolean, zsh: Boolean, fish: Boolean): List<String> = buildList {
        if (bash) add(BASH)
        if (zsh) add(ZSH)
        if (fish) add(FISH)
    }

    /**
     * The shell the picker should show as chosen: the stored one when it is still installed,
     * otherwise the first available shell (what a new session falls back to), or null when
     * nothing is installed. [stored] alone can name a shell that has since been removed.
     */
    fun effectiveSelection(stored: String, available: List<String>): String? =
        stored.takeIf { it in available } ?: available.firstOrNull()
}

/**
 * Whether the app may ask, on its own, whether a newer build exists. On by default (HG2Gui's
 * behaviour before the switch existed); [H2g2SettingsScreen] flips it. Turning it off stops the
 * background check and its notification only; nothing blocks an update the user starts by hand.
 */
enum class UpdateCheckPreference {
    /** No background check. */
    Off,

    /** The app checks for a newer build by itself. */
    On;

    /** True for [On]. */
    val enabled: Boolean get() = this == On

    companion object {
        /** The preference a stored boolean stands for. */
        fun of(enabled: Boolean): UpdateCheckPreference = if (enabled) On else Off
    }
}

/**
 * Everything [H2g2SettingsScreen] shows. The host owns every value (preferences, installed shells,
 * the version string) and hands a new copy in whenever one changes.
 *
 * @property fullscreen the immersive preference: system bars hidden, and not padded for.
 * @property fontScalePercent the Text-size setting, 100 = unscaled. A value outside
 *   [FONT_SCALE_MIN_PERCENT]..[FONT_SCALE_MAX_PERCENT] is reported back clamped as soon as the
 *   screen is shown.
 * @property usePty true when commands run under a pseudo-terminal rather than plain pipes.
 * @property availableShells the shells that can be launched right now, in picker order (see
 *   [ShellPreference.availableFrom]). Empty shows the "no shell" warning.
 * @property shell the stored default shell; the picker highlights
 *   [ShellPreference.effectiveSelection] of it.
 * @property updateCheck the background update-check preference.
 * @property versionName the installed version, shown in the About row; "" when unknown.
 * @property privacyPolicyUrl what the PRIVACY chip opens.
 * @property sourceUrl what the LICENCES chip opens.
 */
data class SettingsState(
    val fullscreen: Boolean = false,
    val fontScalePercent: Int = FONT_SCALE_DEFAULT_PERCENT,
    val usePty: Boolean = false,
    val availableShells: List<String> = listOf(ShellPreference.BASH),
    val shell: String = ShellPreference.BASH,
    val updateCheck: UpdateCheckPreference = UpdateCheckPreference.On,
    val versionName: String = "",
    val privacyPolicyUrl: String = PRIVACY_POLICY_URL,
    val sourceUrl: String = SOURCE_URL,
)

/**
 * HG2Gui's Settings screen: layout mode, text size, PTY, default shell, the doors to the
 * Environment / History / Isolation audit / AI / MCP screens, the update-check switch, the motion
 * note and About. It holds no state and touches no platform API: every value comes from [state]
 * and every tap is a callback.
 *
 * - [onFullscreenChange], [onFontScalePercentChange], [onUsePtyChange], [onShellChange] and
 *   [onUpdateCheckChange] carry the new value; store it and pass back an updated [state].
 * - the `onOpen...` callbacks navigate. [onOpenMotionSettings] should open the system
 *   accessibility settings (the screen only reports the motion state, read from
 *   [rememberReducedMotion]); [onOpenUrl] receives [SettingsState.privacyPolicyUrl] or
 *   [SettingsState.sourceUrl] and should do nothing when no browser can take it.
 * - [onBack] is the BACK chip.
 */
@Suppress("LongParameterList")
@Composable
fun H2g2SettingsScreen(
    state: SettingsState,
    onFullscreenChange: (Boolean) -> Unit,
    onFontScalePercentChange: (Int) -> Unit,
    onUsePtyChange: (Boolean) -> Unit,
    onShellChange: (String) -> Unit,
    onUpdateCheckChange: (UpdateCheckPreference) -> Unit,
    onOpenMcpServer: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenEnvironment: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenIsolationAudit: () -> Unit,
    onOpenMotionSettings: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit
) {
    val fullscreen = state.fullscreen
    val fontScalePercent = state.fontScalePercent
    val usePty = state.usePty
    val availableShells = state.availableShells
    val shownShell = ShellPreference.effectiveSelection(state.shell, availableShells)
    val autoUpdateCheck = state.updateCheck.enabled

    // A value saved before the 85% floor existed (down to 50%) would leave the UI at an unusable
    // size with the "-" button disabled; lift it to the floor as soon as Settings is shown.
    LaunchedEffect(fontScalePercent) {
        val clamped = clampFontScalePercent(fontScalePercent)
        if (clamped != fontScalePercent) onFontScalePercentChange(clamped)
    }

    SettingsPage(title = stringResource(Res.string.settings_title), fullscreen = fullscreen, onBack = onBack) {
        val fullscreenTitle = stringResource(Res.string.settings_fullscreen_title)
        SettingRow(
            title = fullscreenTitle,
            description = if (fullscreen)
                stringResource(Res.string.settings_fullscreen_description_on)
            else
                stringResource(Res.string.settings_fullscreen_description_off)
        ) {
            SegmentedToggle(
                groupLabel = fullscreenTitle,
                leftLabel = stringResource(Res.string.settings_fullscreen_option_safe_area),
                rightLabel = stringResource(Res.string.settings_fullscreen_option_fullscreen),
                rightSelected = fullscreen,
                onSelectRight = onFullscreenChange
            )
        }

        SettingRow(
            title = stringResource(Res.string.settings_text_size_title),
            description = stringResource(Res.string.settings_text_size_description)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Stepper(
                    value = fontScalePercent,
                    min = FONT_SCALE_MIN_PERCENT,
                    max = FONT_SCALE_MAX_PERCENT,
                    label = { "$it%" },
                    decreaseDescription = stringResource(Res.string.settings_text_size_decrease),
                    increaseDescription = stringResource(Res.string.settings_text_size_increase),
                    onChange = { direction -> onFontScalePercentChange(steppedFontScalePercent(fontScalePercent, direction)) }
                )
                if (fontScalePercent != FONT_SCALE_DEFAULT_PERCENT) {
                    H2g2Chip(
                        label = stringResource(Res.string.settings_text_size_reset),
                        onClick = { onFontScalePercentChange(FONT_SCALE_DEFAULT_PERCENT) }
                    )
                }
            }
        }

        val ptyTitle = stringResource(Res.string.settings_pty_title)
        SettingRow(title = ptyTitle, description = stringResource(Res.string.settings_pty_description)) {
            SegmentedToggle(
                groupLabel = ptyTitle,
                leftLabel = stringResource(Res.string.settings_pty_option_pipe),
                rightLabel = stringResource(Res.string.settings_pty_option_pty),
                rightSelected = usePty,
                onSelectRight = onUsePtyChange
            )
        }

        val shellTitle = stringResource(Res.string.settings_shell_title)
        SettingRow(title = shellTitle, description = stringResource(Res.string.settings_shell_description)) {
            if (availableShells.isEmpty()) {
                // No shell is executable: say so rather than offering one that cannot start.
                Text(
                    stringResource(Res.string.settings_shell_none),
                    color = statusColor(StatusKind.Warning),
                    style = settingDescriptionStyle(),
                    modifier = Modifier.liveRegionPolite()
                )
            } else {
                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    availableShells.forEach { shell ->
                        H2g2Chip(
                            label = shell,
                            onClick = { onShellChange(shell) },
                            selected = shell == shownShell,
                            role = Role.RadioButton,
                            contentDescription = "$shellTitle: $shell"
                        )
                    }
                }
            }
        }

        SettingRow(
            title = stringResource(Res.string.settings_environment_title),
            description = stringResource(Res.string.settings_environment_description)
        ) {
            H2g2Chip(
                label = stringResource(Res.string.settings_environment_button),
                onClick = onOpenEnvironment,
                contentDescription = stringResource(Res.string.settings_environment_button_description)
            )
        }

        SettingRow(
            title = stringResource(Res.string.settings_history_title),
            description = stringResource(Res.string.settings_history_description)
        ) {
            H2g2Chip(
                label = stringResource(Res.string.settings_history_button),
                onClick = onOpenHistory,
                contentDescription = stringResource(Res.string.settings_history_button_description)
            )
        }

        SettingRow(
            title = stringResource(Res.string.settings_isolation_title),
            description = stringResource(Res.string.settings_isolation_description)
        ) {
            H2g2Chip(
                label = stringResource(Res.string.settings_isolation_button),
                onClick = onOpenIsolationAudit,
                contentDescription = stringResource(Res.string.settings_isolation_button_description)
            )
        }

        SettingRow(
            title = stringResource(Res.string.settings_ai_title),
            description = stringResource(Res.string.settings_ai_description)
        ) {
            H2g2Chip(
                label = stringResource(Res.string.settings_ai_button),
                onClick = onOpenAiSettings,
                contentDescription = stringResource(Res.string.settings_ai_button_description)
            )
        }

        SettingRow(
            title = stringResource(Res.string.settings_developer_title),
            description = stringResource(Res.string.settings_developer_description)
        ) {
            H2g2Chip(
                label = stringResource(Res.string.settings_developer_button),
                onClick = onOpenMcpServer,
                contentDescription = stringResource(Res.string.settings_developer_button_description)
            )
        }

        val updatesTitle = stringResource(Res.string.settings_updates_title)
        SettingRow(
            title = updatesTitle,
            description = if (autoUpdateCheck)
                stringResource(Res.string.settings_updates_description_on)
            else
                stringResource(Res.string.settings_updates_description_off)
        ) {
            SegmentedToggle(
                groupLabel = updatesTitle,
                leftLabel = stringResource(Res.string.settings_option_off),
                rightLabel = stringResource(Res.string.settings_option_on),
                rightSelected = autoUpdateCheck,
                onSelectRight = { enabled -> onUpdateCheckChange(UpdateCheckPreference.of(enabled)) }
            )
        }

        // HG2Gui has no in-app motion switch of its own: it follows Android's animator scale
        // (see H2g2Motion), so this row reports the state and points at the system setting.
        SettingRow(
            title = stringResource(Res.string.settings_motion_title),
            description = if (rememberReducedMotion())
                stringResource(Res.string.settings_motion_description_off)
            else
                stringResource(Res.string.settings_motion_description_on)
        ) {
            H2g2Chip(
                label = stringResource(Res.string.settings_motion_button),
                onClick = onOpenMotionSettings
            )
        }

        SettingRow(
            title = stringResource(Res.string.settings_about_title),
            description = stringResource(Res.string.settings_about_description, state.versionName)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                H2g2Chip(
                    label = stringResource(Res.string.settings_about_privacy),
                    onClick = { onOpenUrl(state.privacyPolicyUrl) },
                    contentDescription = stringResource(Res.string.settings_about_privacy_description)
                )
                H2g2Chip(
                    label = stringResource(Res.string.settings_about_licences),
                    onClick = { onOpenUrl(state.sourceUrl) },
                    contentDescription = stringResource(Res.string.settings_about_licences_description)
                )
            }
        }
    }
}

/**
 * The frame every settings-style screen shares (Settings, AI settings, MCP server): the ground
 * page, system-bar / cutout / keyboard insets, a column capped at a readable width, a back chip
 * with a real 48dp hit box, an [H2g2Heading] title and a vertical scroll. Scrolling is part of
 * the contract - at a large Text-size setting, in landscape, or with the keyboard up, the last row
 * must stay reachable.
 */
@Composable
internal fun SettingsPage(
    title: String,
    fullscreen: Boolean,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(LocalH2g2Ground.current.pageBrush())
            .screenInsets(fullscreen)
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .maxContentWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                H2g2Chip(
                    label = stringResource(Res.string.settings_back),
                    onClick = onBack,
                    contentDescription = stringResource(Res.string.settings_back_description)
                )
            }
            H2g2Heading(title, Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp))
            content()
        }
    }
}

/** Body text of a setting's explanation: the type scale's body face at caption size. */
@Composable
internal fun settingDescriptionStyle(): TextStyle =
    currentH2g2Type().body.copy(fontSize = 13.sp, lineHeight = 18.sp)

@Composable
internal fun SettingRow(title: String, description: String, control: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(
            title.uppercase(),
            color = LocalH2g2Ground.current.onPage,
            style = currentH2g2Type().capsule,
            modifier = Modifier.headingRole()
        )
        Text(
            description,
            color = statusColor(StatusKind.Muted),
            style = settingDescriptionStyle(),
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
        )
        control()
    }
}

/**
 * Two mutually exclusive options as radio chips. The chosen one is filled and the other outlined
 * (shape and weight, not hue alone), and each is announced as "<group>, <option>, radio button,
 * selected / not selected".
 */
@Composable
internal fun SegmentedToggle(
    groupLabel: String,
    leftLabel: String,
    rightLabel: String,
    rightSelected: Boolean,
    onSelectRight: (Boolean) -> Unit
) {
    Row(
        Modifier.selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(leftLabel to false, rightLabel to true).forEach { (label, value) ->
            H2g2Chip(
                label = label,
                onClick = { onSelectRight(value) },
                selected = value == rightSelected,
                role = Role.RadioButton,
                contentDescription = "$groupLabel: $label"
            )
        }
    }
}

/**
 * "-" [value] "+" with spoken labels for both glyph buttons. [onChange] receives the direction
 * (-1 or +1); the caller applies its own step and range. The value is a polite live region, so a
 * screen reader announces each new percentage.
 */
@Composable
private fun Stepper(
    value: Int,
    min: Int,
    max: Int,
    label: (Int) -> String,
    decreaseDescription: String,
    increaseDescription: String,
    onChange: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        H2g2Chip(
            label = "−",
            onClick = { onChange(-1) },
            enabled = value > min,
            contentDescription = decreaseDescription
        )
        Box(
            Modifier
                .liveRegionPolite()
                .clip(H2g2Surface.capsule)
                .background(H2g2.ink)
                .padding(horizontal = 18.dp, vertical = 6.dp)
        ) {
            Text(label(value), color = H2g2.yellow, style = currentH2g2Type().chip)
        }
        H2g2Chip(
            label = "+",
            onClick = { onChange(1) },
            enabled = value < max,
            contentDescription = increaseDescription
        )
    }
}
