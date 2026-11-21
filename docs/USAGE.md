# Using conveyance-h2g2

How to call each surface from a host app. Signatures here are abridged; the KDoc on each composable
is complete. Everything lives under `com.hereliesaz.conveyance.h2g2`.

The rule that shapes every API below: **the library draws and moves; the host does the work.** A
shell, a filesystem, an AI client, a preference store never came along from HG2Gui. They arrive as
parameters and callbacks, and most have a default that does nothing, so a screen renders before it
is wired.

- [Theme, ground, type](#theme-ground-type)
- [Primitives](#primitives)
- [Pill menu](#pill-menu)
- [File manager](#file-manager)
- [The Guide](#the-guide)
- [Terminal](#terminal)
- [Full-screen terminal and Adaptive TUI](#full-screen-terminal-and-adaptive-tui)
- [Components](#components)
- [Editor, store, AI chat](#editor-store-ai-chat)
- [Settings family](#settings-family)
- [Splash](#splash)
- [Motion](#motion)
- [System Back](#system-back)
- [Seeing it without a device](#seeing-it-without-a-device)

## Theme, ground, type

~~~kotlin
val ground = remember { GroundState() }          // rolls one of six grounds; Mustard is weighted 6x

H2g2Theme(groundState = ground, scale = 1f) {
    // everything here reads the ground, its text colour, Azrienoch and the UI scale
}
~~~

`H2g2Theme` provides `LocalH2g2Ground`, `LocalH2g2GroundState`, `LocalH2g2ContentColor`,
`LocalH2g2Type` and `LocalH2g2UiScale`, and paints the ground behind its content. On Android it also
follows the system animator scale ("Remove animations") and tints the window and system-bar icons to
the ground. Call `window.applyH2g2Ground(ground.current)` in `onCreate`, before `setContent`, so a
launch on a dark ground does not flash light.

Every composable also works **outside** a theme: Mustard, Azrienoch, scale 1.

| Need | Call |
| --- | --- |
| The active ground | `LocalH2g2Ground.current` (`page`, `onPage`, `pageBrush()`, `foldBrush()`) |
| Reroll it (at most twice per `GroundState`) | `groundState.reroll()` |
| Several roots agreeing on one ground | Hoist one `GroundState` above them all |
| The type scale | `currentH2g2Type()` (`hero` … `micro`, plus `heading`, `pill`, `pillCap`, `chip`) |
| The scale in another family | `H2g2Theme(type = h2g2Type(myFamily))` |
| The typeface itself | `azrienochFontFamily()`, `azrienochMonoFontFamily()` |
| A hue for an id | `H2g2.hues[H2g2.hueOf(id)]`; its darker end-cap mate is `H2g2.caps[...]` |
| Text colour on any fill | `fill.contrastingText()` (`import ...H2g2.contrastingText`) |

Text is uppercase everywhere except `body`. A `TextStyle` cannot uppercase; the call site does.

## Primitives

`Primitives.kt`, root package.

~~~kotlin
H2g2Heading("Storage")                                   // screen title, exposed as a heading
H2g2Chip("Run", onClick = ::run)                         // ink fill, yellow text
H2g2Chip("Grid", onClick = ::grid, selected = isGrid)    // filled when true, outlined wash when false
H2g2Chip("Delete", onClick = ::delete, tint = H2g2.hues[6])

Text("exit 127", color = statusColor(StatusKind.Error))  // >= 4.5:1 on the current ground

Modifier.clickable { }.minTouch(minTouchSize())          // 48dp hit box, drawn size unchanged
Modifier.screenInsets()                                  // once per screen root: bars, cutout, keyboard
Modifier.maxContentWidth()                               // 640dp column on wide windows
~~~

Minimums the primitives enforce and new UI should keep: text at least 11sp, touch targets at least
48dp, text contrast at least 4.5:1 (3:1 for large text and borders), and a word or semantic for
anything carried only by colour or a glyph. `color.readableOn(background)` nudges any colour to pass.

## Pill menu

`menu.H2g2PillMenu` is the breadcrumb pill stack.

~~~kotlin
val tree = listOf(
    MenuNode("pkg", "Package management", cap = "12", children = listOf(
        MenuNode("install", "install", children = listOf(MenuNode("vim", "vim"))),
        MenuNode("remove", "remove"),
    )),
    MenuNode("files", "Files", resolveChildren = { listDirectoryAsNodes("/") }), // resolved on open
)

H2g2PillMenu(
    roots = tree,
    placement = PillMenuPlacement.BottomRight,
    onRun = { tokens, isTerminal -> line = tokens.joinToString(" "); if (isTerminal) run(line) },
)
~~~

A pick drops its pill to the anchor row as the host; its children cascade from it; each further pick
settles into the shingled trail beside the host. Tapping a crumb returns there. `onRun` fires on
every pick with the tokens so far; `isTerminal` is true when nothing is left to drill into.

`MenuNode` fields worth knowing:

| Field | Effect |
| --- | --- |
| `cap` | The badge at the pill's visible end (a count, a type tag). |
| `resolveChildren` | Children fetched when the node is opened (a directory, a PATH scan). |
| `value` | The token contributed, when it differs from the label (a file's full path). |
| `emitsToken = false` | A purely navigational node; contributes nothing to the line. |
| `wizardId` | Picking it calls `onWizard(id)` instead of `onRun`. |
| `settleBeforeWizard` | `onWizard` waits until the crumb has landed and reported its rect via `onCrumbPositioned`. |

### Placements

`PillMenuPlacement` is the house stack mirrored into a corner. Motion, hinges and the scroll all
mirror with it; labels stay upright; taps land where the pills are drawn.

| Placement | Reads as |
| --- | --- |
| `BottomRight` | The house stack: right-anchored, bleeding off the left, growing **up** from the bottom. |
| `TopRight` | For the top of a screen: the host rises to the top row and stacks fall **downward**. |
| `BottomLeft` | Left-anchored, bleeding off the right, growing up; label and cap at the left end. |
| `TopLeft` | Left-anchored, growing downward. |

`H2g2TerminalScreen` and `H2g2CommandGuideScreen` take the same choice as `pillPlacement`.

`PillMetrics` (`Height`, `RowPitch`, `TouchTarget`, `MaxReferenceWidth`) is public so a layout that
hosts the stack can size its region from the same numbers.

## File manager

`files.H2g2FilesScreen`. Folders are tiles; tapping one morphs it open into a card while its
siblings collapse to rods; levels nest. The host supplies every byte of I/O.

~~~kotlin
val backStep = remember { BackStepState() }

H2g2FilesScreen(
    fullscreen = false,
    nowMillis = Clock.System.now().toEpochMilliseconds(),
    listDir = { path -> fs.list(path).map { VfsEntry(it.name, it.path, it.isDirectory, it.size) } },
    search = { query -> fs.search(query) },
    storageStats = { fs.stats() },
    previewFile = { path -> FilePreview(text = fs.readText(path), isMarkdown = path.endsWith(".md")) },
    onOpenFile = ::openInEditor,
    onCreateFolder = { parent, name -> fs.mkdir(vfsChildPath(parent, name)) },
    onCreateFile = { parent, name -> fs.touch(vfsChildPath(parent, name)) },
    onDelete = { path -> fs.trash(path) },
    trash = TrashActions(items = fs::trashed, restore = fs::restore, purge = fs::purge, empty = fs::emptyTrash),
    deviceStorage = DeviceStorageState(active = false, granted = false, onToggle = ::askForStorage),
    onRename = fs::rename, onMove = fs::move, onCopy = fs::copy,
    onShare = ::share, onShareMultiple = ::shareAll,
    onBack = ::closeFiles,
    backStep = backStep,
)
~~~

The suspend callbacks run from the screen's own scope; return `false` and the screen says the
action failed. `FilePreview.styled` takes `StyledSpan` lines from a highlighter when you have one.

The open and close transition (a picked control runs the screen's perimeter and floods into the
page) is separate, so any screen can use it:

~~~kotlin
H2g2FilesReveal(visible = filesOpen, origin = filesButtonRect, onClosed = { /* gone */ }) {
    H2g2FilesScreen(/* … */)
}
~~~

Also here: `H2g2FolderPicker`, `H2g2PathPickerScreen` (both take just `listDir` and result
callbacks), `H2g2StorageScreen` (by type, largest, trash), and `H2g2MarkdownPreview(source)`.

## The Guide

~~~kotlin
H2g2GuideReaderScreen(
    fullscreen = false,
    onBack = ::closeGuide,
    onCommandSelected = { command -> line += command },   // a command tapped in the prose
    backStep = backStep,
    runtimeIndex = GuideRuntimeIndex.fromTree(tree),      // marks which entries exist in your menu
    content = HitchhikersGuideToTermux,                   // the default; pass your own GuideContent
)
~~~

Write your own book with `GuideContent(chapters = listOf(guideChapter(label, title, intro, GuideEntry(…))))`.
Chapter labels must be unique within one `GuideContent`. Without a `runtimeIndex` every entry reads
"Not in this build".

`H2g2CommandGuideScreen` puts the pill stack above the reader. `H2g2GuideLinkedText(text, commands,
…, onCommandSelected)` is the tappable-command prose on its own.

## Terminal

`terminal.H2g2TerminalScreen` is the command-composition screen: session tabs, shell status line,
output cards, the pill stack, the composer and RUN.

~~~kotlin
val session = remember { SessionUiState(id = "1", name = "bash", cwd = "/home/me") }

H2g2TerminalScreen(
    tree = tree,
    sessions = listOf(session),
    activeSessionId = session.id,
    onSessionPick = {}, onNewSession = {}, onCloseSession = {},
    fullscreen = false,
    onOpenSettings = ::openSettings, onOpenGuide = ::openGuide, onOpenFiles = ::openFiles,
    complete = { request -> shell.complete(request.line, request.cursor) },
    onRun = { sessionId, line, onOutput, onNeedInput, onExit, onStderr, onStyledOutput ->
        shell.run(line, stdout = onOutput, stderr = onStderr, prompt = onNeedInput)
        onExit(shell.lastExitCode)
    },
)
~~~

The screen owns the buffer: it creates a card per run and fills it as `onOutput` reports. Output is
classified and typeset for reading (key-value table, reflowed wide table, ASCII art drawn as a
picture, a notice for binary) with a chip to see the raw text; override `classifyOutput` or
`typesetOutput` to change that.

| Parameter | Default | Supply it to get |
| --- | --- | --- |
| `complete` | no candidates | The Complete pill and completion picks. |
| `shellProbe` | `NoShellProbe` | The git branch/dirty chip in the status line. |
| `isRealCommand` | `{ false }` | Aliases not expanded when an installed command has the name. |
| `onInterrupt` | no-op | INTR on a running card. |
| `onFilesButtonPositioned`, `onCrumbPositioned` | no-op | Root rects to anchor a reveal to. |

Usable on their own: `H2g2FluidTerminalWorkspace` (the buffer/stack layout that adapts to window
size), `H2g2ShellStatusLine`, `H2g2LiveStatusStrip`, `H2g2ComposerKeys`. The pure models came too:
`ShellAliases`, `ChainOperator`, `applyCompletion`, `CompletionSelectionPolicy`, `ShellPromptParser`,
`WorkflowFlow`, `SshFlow`.

## Full-screen terminal and Adaptive TUI

For programs that take over the screen. The host implements one interface over its own PTY and
emulator; back its properties with snapshot state so the screen recomposes.

~~~kotlin
class MySession(private val pty: Pty) : FullScreenTerminalSession {
    override val commandLine = "vim notes.txt"
    override var exitCode: Int? by mutableStateOf(null)
    override var grid: TerminalGrid by mutableStateOf(TerminalGrid.Empty)   // set on every emulator update
    override fun updateSize(columns: Int, rows: Int, cellWidthPx: Int, cellHeightPx: Int) = pty.resize(columns, rows)
    override fun sendText(text: String) = pty.write(text)
    override fun kill() = pty.kill()
}

H2g2FullScreenTerminalScreen(session = session, onExit = ::closeTerminal)
~~~

The grid is set in Azrienoch Mono with each glyph stretched to its cell. The key rows, sticky
CTRL/ALT, hardware-key mapping (`terminalKeySequence`) and the close confirmation are built in.

`H2g2AdaptiveTuiScreen` draws menus, tabs, lists, tables, prompts and progress **projected from** a
running terminal program. Provide the projection by overriding `snapshot: TuiSnapshot?` on the
session; the detection engine is yours. The RAW chip always returns to the plain grid, and the
screen falls back to it by itself when nothing is wrappable. `mouseClick` reports failure unless you
override it, since encoding a click needs your emulator's tracking mode.

## Components

~~~kotlin
H2g2ConfirmDialog(
    title = "Remove 3 files?", message = "This cannot be undone.",
    confirmLabel = "Remove", onConfirm = ::remove, onDismiss = ::cancel,
    tone = ConfirmTone.Elevated, badge = "RUNS AS ROOT",          // outlined, badged: authority prompts
)

val progress = remember { JobProgressBarState() }
H2g2JobProgressBar(progress)          // from a coroutine: advanceTo(0.4f), complete(), fail()

H2g2TypesetOutput(text = output, ink = LocalH2g2Ground.current.onPage)
~~~

`H2g2TypesetOutput` classifies with `classifyOutput` and draws `H2g2KeyValueTable`,
`H2g2WideTableRecords` or `H2g2AsciiArtCanvas`; each is callable directly. `ConfirmPrompts.parse`
splits a shell's yes/no prompt into a dialog's parts.

## Editor, store, AI chat

`screens` package. All three are fully hoisted: state in, events out.

| Composable | The host supplies |
| --- | --- |
| `H2g2EditorScreen` | `fileName`, `content`, `dirty`, `error`; `onContentChange`, `onSave`, `onBack`, wrap toggle. |
| `H2g2AzpStoreScreen` | `results`, `installed`, `busy`, `installingId`; `onSearch`, `onInstall`, `onUninstall`. |
| `H2g2AiChatScreen` | `messages`, `busy`, `apiKeyConfigured`; `onAsk`, `onUseCommand`, `onOpenSettings`; optionally `analyzeIntent` to label what a suggested command would touch. |

## Settings family

`settings` package: `H2g2SettingsScreen`, `H2g2AiSettingsScreen`, `H2g2McpServerScreen`,
`H2g2EnvironmentScreen`, `H2g2HistoryScreen`, `H2g2IsolationAuditScreen`. Each takes an immutable
state object and callbacks; nothing reads or writes a preference.

~~~kotlin
H2g2SettingsScreen(
    state = SettingsState(/* fullscreen, fontScalePercent, shell, … */),
    onFullscreenChange = prefs::setFullscreen,
    onFontScalePercentChange = prefs::setFontScale,     // feed uiScaleFromPercent(it) to H2g2Theme(scale = …)
    onUsePtyChange = prefs::setUsePty,
    onShellChange = prefs::setShell,
    onUpdateCheckChange = prefs::setUpdateCheck,
    onOpenMcpServer = {}, onOpenAiSettings = {}, onOpenEnvironment = {}, onOpenHistory = {},
    onOpenIsolationAudit = {}, onOpenMotionSettings = ::openSystemAccessibility,
    onOpenUrl = ::openUrl, onBack = ::closeSettings,
)
~~~

Secrets: `AiSettingsDraft` holds the key while it is being edited. Keep it in plain `remember`, not
a saveable. `toString()` on the settings states masks secrets, but do not log them.

The isolation audit shows timestamps as UTC text by default; pass `formatTimestamp` for a localized
form.

## Splash

~~~kotlin
var ready by remember { mutableStateOf(false) }
if (!ready) H2g2Splash(onFinished = { ready = true }) else Home()
~~~

A hand pops up as a fist, shows four fingers, then two, then a thumbs-up while the wordmark wipes
in. About four seconds, once. `wordmark = null` draws the hand alone. With animations off it shows
the last frame and finishes at once.

## Motion

Everything animated reads `H2g2Motion`. On Android `H2g2Theme` keeps `durationScale` in step with the
system; elsewhere call `H2g2Motion.updateDurationScale(scale)` yourself if you have a preference.

- `rememberReducedMotion()`: true when animations are off. Skip a flourish outright; do not play it
  at zero length.
- `scaledDelayMs(ms)`: wrap every `delay()` that choreographs motion.
- `StackEntrance.roll()`, `staggerDelayMs`, `wobble`: the stack-arrival vocabulary.
- `H2g2PerimeterReveal` + `PerimeterRevealState`, `BreakFreeState`: the run transition.
- `H2g2Nest`: a pill that grows into a card holding its options, nested to any depth.

## System Back

Screens with internal levels (files, guide, pickers) do not install a back handler. They report
through a `BackStepState` you pass in, and you own the single handler:

~~~kotlin
val backStep = remember { BackStepState() }
BackHandler { if (backStep.canStepBack) backStep.stepBack() else closeScreen() }
~~~

Call `backStep.reset()` when you swap the screen that was using it.

## Seeing it without a device

`./gradlew desktopTest --tests '*GalleryRender*' --tests '*ScreensGallery*'` draws the surfaces
off-screen and writes PNGs to `build/gallery/`, at chosen moments of their animations and after
scripted taps. It is the quickest way to see what a change did.
