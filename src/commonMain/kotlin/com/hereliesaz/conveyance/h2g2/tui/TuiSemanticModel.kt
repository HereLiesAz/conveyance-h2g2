package com.hereliesaz.conveyance.h2g2.tui

/**
 * Toolkit-neutral description of a terminal UI screen: what [H2g2AdaptiveTuiScreen] renders.
 *
 * The library only draws this model. Deriving it from a live terminal (reading the emulator's rows
 * and recognising menus, tabs, tables and prompts in them) is the host's job; a host hands the
 * current projection to the screen through [FullScreenTerminalSession.snapshot].
 *
 * - [title]: the screen's own title, shown as the heading (the command line is used when null).
 * - [layers]: the menus on screen, outermost first.
 * - [prompt]: the question the program is waiting on, if any.
 * - [regions]: tables, result lists, progress, panes, diffs, logs and editors.
 * - [tabs]: the tab strip, if one was recognised.
 * - [alternateScreen]: whether the program is on the alternate screen buffer. Only an
 *   alternate-screen program is ever wrapped.
 * - [mouseAware]: whether the program has enabled terminal mouse tracking, so taps on region rows
 *   can be delivered as terminal mouse clicks.
 * - [profile]: which toolkit the projection was derived for; shown in the status line.
 */
data class TuiSnapshot(
    val title: String?,
    val layers: List<TuiLayer>,
    val prompt: TuiPrompt?,
    val regions: List<TuiRegion> = emptyList(),
    val tabs: List<TuiTab> = emptyList(),
    val alternateScreen: Boolean,
    val mouseAware: Boolean = false,
    val profile: TuiAdapterProfile = TuiAdapterProfile.GENERIC
) {
    /** Whether there is enough structure here to show natively rather than as the raw grid. */
    val isWrappable: Boolean
        get() = alternateScreen && (
            layers.any { it.items.size >= 2 } || prompt != null || regions.isNotEmpty() || tabs.isNotEmpty()
        )

    /** The layer that owns input: the innermost one whose selection is observable, or null. */
    val activeLayerIndex: Int?
        get() = layers.indexOfLast { it.activeIndex != null }.takeIf { it >= 0 }
}

/** The terminal UI toolkit a [TuiSnapshot] was derived for. */
enum class TuiAdapterProfile { GENERIC, RICH, TEXTUAL, INK, RATATUI, COOPERATIVE }

/**
 * One menu on screen. [activeIndex] is the highlighted item, or null when the selection cannot be
 * observed (the layer is then shown but not tappable). [depth] indents nested menus; a [modal]
 * layer is drawn heavier; [scrollable] marks a menu that continues off screen.
 */
data class TuiLayer(
    val heading: String?,
    val items: List<TuiMenuItem>,
    val activeIndex: Int?,
    val depth: Int = 0,
    val modal: Boolean = false,
    val scrollable: Boolean = false
)

/**
 * One row of a [TuiLayer]. [row] is its terminal row; [checked] is the state of a
 * [TuiControlKind.CHECKBOX] or [TuiControlKind.RADIO] item and null for a plain action.
 */
data class TuiMenuItem(
    val row: Int,
    val label: String,
    val selected: Boolean,
    val enabled: Boolean = true,
    val control: TuiControlKind = TuiControlKind.ACTION,
    val checked: Boolean? = null
)

/** What kind of control a [TuiMenuItem] is. */
enum class TuiControlKind { ACTION, CHECKBOX, RADIO }

/** The question a terminal program is waiting on, at terminal row [row]. */
data class TuiPrompt(
    val row: Int,
    val label: String,
    val kind: TuiPromptKind
)

/** How a [TuiPrompt] is answered: typed text, masked text, or yes/no. */
enum class TuiPromptKind { TEXT, PASSWORD, CONFIRMATION, UNKNOWN }

/** One tab of a recognised tab strip on terminal row [row]. */
data class TuiTab(val label: String, val active: Boolean, val row: Int)

/**
 * A block of the screen with a recognised purpose, covering terminal rows [startRow]..[endRow].
 * [lines] is its text, one entry per shown row; [progress] (0..1) draws a progress bar under it.
 */
data class TuiRegion(
    val kind: TuiRegionKind,
    val startRow: Int,
    val endRow: Int,
    val lines: List<String>,
    val progress: Float? = null
)

/** What a [TuiRegion] holds; its name is the region card's eyebrow. */
enum class TuiRegionKind { TABLE, RESULTS, PROGRESS, STATUS, PANE, DIFF, LOG, EDITOR }
