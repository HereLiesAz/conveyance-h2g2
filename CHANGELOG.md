# Changelog

Versions are `major.minor.patch.build` from `version.properties`. JitPack release tags are listed
where one exists.

## 0.1.0 — the HG2Gui port

HG2Gui's design (at `0d8edf6`) as callable composables. Not yet tagged; resolve `main-SNAPSHOT`.

### Added

- **Theme and type.** `H2g2Theme`; bundled Azrienoch (`azrienochFontFamily()`,
  `azrienochMonoFontFamily()`, `currentH2g2Type()`, `rememberH2g2Type()`); `LocalH2g2Ground`,
  `LocalH2g2GroundState`, `LocalH2g2ContentColor`, `LocalH2g2Type`, `LocalH2g2UiScale`. On Android,
  `Window.applyH2g2Ground` and a live system animator scale.
- **Primitives.** `H2g2Chip`, `H2g2Heading`, `statusColor` / `StatusKind`, `readableOn`,
  `contrastRatio`, `Modifier.minTouch`, `screenInsets`, `maxContentWidth`, `H2g2Dimens`,
  `H2g2Contrast`.
- **`menu`.** `H2g2PillMenu`, `MenuNode`, `PillMetrics`, and `PillMenuPlacement` (`BottomRight`,
  `TopRight`, `BottomLeft`, `TopLeft`).
- **`files`.** `H2g2FilesScreen`, `H2g2FilesReveal`, `H2g2FolderPicker`, `H2g2PathPickerScreen`,
  `H2g2StorageScreen`, `H2g2MarkdownPreview`.
- **`guide`.** `H2g2GuideReaderScreen`, `H2g2CommandGuideScreen`, `H2g2GuideLinkedText`,
  `GuideContent`, `HitchhikersGuideToTermux`.
- **`terminal`.** `H2g2TerminalScreen`, `H2g2FluidTerminalWorkspace`, `H2g2ShellStatusLine`,
  `H2g2LiveStatusStrip`, `H2g2ComposerKeys`, and the completion, alias, chaining and prompt models.
- **`tui`.** `H2g2FullScreenTerminalScreen`, `H2g2AdaptiveTuiScreen`, `H2g2StructuredShellStatus`,
  `FullScreenTerminalSession`, `TerminalGrid`, `terminalKeySequence`.
- **`components`.** `H2g2ConfirmDialog`, `H2g2JobProgressBar`, `H2g2TypesetOutput`,
  `H2g2KeyValueTable`, `H2g2WideTableRecords`, `H2g2AsciiArtCanvas`, `StyledSpan`, `BackStepState`.
- **`screens`.** `H2g2EditorScreen`, `H2g2AzpStoreScreen`, `H2g2AiChatScreen`.
- **`settings`.** `H2g2SettingsScreen`, `H2g2AiSettingsScreen`, `H2g2McpServerScreen`,
  `H2g2EnvironmentScreen`, `H2g2HistoryScreen`, `H2g2IsolationAuditScreen`.
- **`H2g2Splash`**, built from `docs/design/HG2Gui Splash.html`, which HG2Gui specified and never
  shipped.
- **Tokens.** `H2g2.yellow`, `H2g2.groupings`, `Ground.foldBrush()`, the pill menu's clocks on
  `H2g2Motion`, top-level `scaledDelayMs` and `rememberReducedMotion`.
- **Documents.** `docs/design`, `docs/brand`, `docs/art-direction`, `docs/USAGE.md`.

### Changed under existing callers

- `Ground.pageBrush()` is a flat fill, as HG2Gui paints it. The crease is `Ground.foldBrush()`, now
  at the style guide's 100 degrees.
- `Color.contrastingText()` picks ink or white by measured contrast, not a 0.35 luminance cutoff.
  Cyan, teal, green, gray and orange fills now take ink text.
- `H2g2Type` has twelve steps, not eight: `heading`, `pill`, `pillCap` and `chip` are new, so a
  positional constructor call no longer compiles. `capsule`, `eyebrow`, `endCap` and `micro` took
  HG2Gui's current sizes; nothing is below 11sp.
- Templates, `H2g2Nest`, the workflow map and the terrarium read `currentH2g2Type()`, so they render
  in Azrienoch instead of the platform font.
- `H2g2Surface`'s shapes are typed `RoundedCornerShape`.
- A new dependency, `compose.components.resources`, carries the typeface and strings.

### Departures from HG2Gui

- No global ground: `GroundState` and `LocalH2g2Ground` replace `Azphalt.currentGround`.
- No Material. Text goes through an internal wrapper over foundation; the Adaptive TUI's progress
  bar, text field and branch chooser were rebuilt from foundation.
- Engines stay with the host, as parameters with inert defaults: the completion bridge, the alias
  probe, AI intent analysis, the TUI parser, the PTY.
- The Adaptive TUI's checkbox and radio toggle sends a space. HG2Gui's sent nothing and always
  reported that the application did not acknowledge it.
- Isolation-audit timestamps default to UTC text; `formatTimestamp` restores a localized form.
- `H2g2FilesReveal`: turning `visible` back on during a close replays the open. HG2Gui ignored it.
- Screens do not install a system back handler; they report through `BackStepState`.

### Not ported

The command, authority and package-lifecycle menu trees. They are HG2Gui's data, not its design.

## 0.5.1 and earlier (JitPack tags)

Hues, grounds, the eight-step type scale, surfaces, the `Templates` registry, `H2g2Nest`, the motion
vocabulary, the perimeter reveal and break-free, the workflow map and the swarm terrarium.
