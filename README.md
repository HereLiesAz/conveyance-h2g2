# conveyance-h2g2

A composable-set library for [Conveyance](https://github.com/HereLiesAz/Conveyance): the h2g2 design system -- hues, grounds, the Azrienoch type scale, motion, and every screen and component of HG2Gui as a callable composable.

## What this is

Per [azphalt's `spec/composable.md`](https://github.com/HereLiesAz/azphalt/blob/main/spec/composable.md),
a `kind: "composable"` `.azp` package is a **pure header**: it names this artifact's Gradle
coordinates (`library.group` / `library.artifact`) and selects a `templateId`, `hue`,
`surface`, `scale`, and `act` from it. It carries no code of its own. This repository *is* the
artifact a composable package's `library` block points at -- the `.azp` package itself is
authored and published separately, wherever its author chooses; this repo does not need to hold
one.

Example composable manifest referencing this library:

```jsonc
{
  "azphalt": "0.1",
  "id": "com.hereliesaz.azphalt.example",
  "name": "Example",
  "version": "1.0.0",
  "kind": "composable",
  "license": "Apache-2.0",
  "compat": ">=0.1",
  "composable": {
    "library": { "group": "com.hereliesaz.conveyance", "artifact": "conveyance-h2g2", "version": "0.1.0" },
    "elements": [
      { "id": "confirm-record", "templateId": "h2g2.tile.record", "hue": "confirm-record", "surface": "recordTile", "scale": "lead", "act": "create", "jobs": ["confirms a destructive action"] }
    ]
  },
  "files": {}
}
```

## What's here

Ported from HG2Gui's own Azphalt style system (h2g2's origin), values unchanged unless noted:

- **`H2g2`** (`H2g2.kt`) -- the 14-entry hue/cap palette and `hueOf(id)`, a deterministic hash
  from any identifier to a hue. This is [`Job.Identify`](https://github.com/HereLiesAz/Conveyance)
  territory, not `Channel.Hue` -- it tells elements apart, it doesn't rank them.
- **`Ground`/`GroundState`** (`Grounds.kt`) -- the six weighted background themes. `pageBrush()`
  is the flat page HG2Gui paints; `foldBrush()` is the printed spread with its seam at the style
  guide's 100 degrees. Adapted from a process-wide singleton (h2g2's own app-level pattern) to a
  `GroundState` a host instantiates and scopes itself, since a library shouldn't impose global
  mutable state on its consumer; components read the active one from `LocalH2g2Ground`.
- **`H2g2Type`** (`Type.kt`) -- the twelve-step type scale (`hero` through `micro`, plus the
  working sizes `heading`, `pill`, `pillCap`, `chip`), nothing below 11sp. Set in **Azrienoch**,
  HG2Gui's typeface, which this library bundles (six cuts, SIL OFL 1.1, licence in
  `third_party/azrienoch/`): `azrienochFontFamily()`, `azrienochMonoFontFamily()`, and
  `currentH2g2Type()`, which every composable here reads. `h2g2Type(family)` builds the same scale
  on any other family.
- **`H2g2Surface`** (`Surfaces.kt`) -- **four** shapes: `recordTile`/`note`, from the source-code
  `AzphaltSurface` object, plus `well` (18dp) -- a shape that object doesn't name, but the actual
  design mockups (`docs/HG2Gui Surfaces.dc.html`) use consistently: an ink-background container
  for raw/monospace output, nested inside a `recordTile` or standing on its own. Everything else
  is the fully-rounded `capsule`. `note` is not a lesser shape despite one design doc
  (`docs/DESIGN.md`, which is scoped to the pill menu specifically) suggesting otherwise -- the
  file explorer's own `folderTileShape(isOpen, width)` picks between all three of
  `recordTile`/`note`/`capsule` by state and size, treating them as equally deliberate.
- **`H2g2Page`** (`Page.kt`) -- the one-line convenience for painting a `Ground` behind a whole
  screen. Deliberately **not** a `Templates.registry` entry: every composable manifest element
  requires a non-empty `act` (azphalt `spec/composable.md`), and a page background isn't an
  actionable control -- `Ground`/`GroundState`/`Modifier.ground` (`Grounds.kt`) are already a
  complete, directly callable API; this just wraps the common case.
- **`Templates`** (`Templates.kt`) -- the `templateId` registry. Six templates:
  `h2g2.tile.record` (a `recordTile`-shaped element, one line or a title+`subtitle` two-line
  form), `h2g2.tile.record.detail` (a `recordTile` with a nested `well` holding
  `detailLines` -- the raw-output-panel pattern from the mockups), `h2g2.tile.record.capped` (a
  `recordTile` with `endCapText` riding the right end), `h2g2.tile.note` (a `note`-shaped
  element, an `eyebrow`-step `subtitle` label *above* the body line -- a note is labeled before
  it's read, not captioned after), `h2g2.pill.action` (a plain `capsule`-shaped element), and
  `h2g2.pill.capped` (a `capsule` with `endCapText`) -- all `Offer`-backed, hue-colored via
  `indexOf`, type-set via `h2g2Type().step(scale)`. The two `.capped` templates are the first to
  use `H2g2.caps` and the `endCap` type step, both previously ported but unused -- `docs/DESIGN.md`
  §2's own "label and end-cap sit together at the right end," read for a natural-width tile/pill
  rather than the pill menu's own anchored, overhung ones, with the "darker mate on the end-cap"
  coloring from §7 ("Unchanged from Azphalt") layered on top. Every one of them clips to
  `request.surfaceShape(<its own default shape>)`, so a manifest element's `surface` string really
  does select the shape (`H2g2Surface.byName`); a `surface` this vocabulary doesn't have leaves
  that template on the shape its name implies rather than collapsing everything to `capsule`.

### The surfaces: HG2Gui's UI, callable

Every HG2Gui screen and component, as a composable with its animations built in. App logic never
came along: a shell, a filesystem, an AI client or a preference store is a parameter or a callback.
Wrap a screen in `H2g2Theme` for the rolled ground, the content colour, the UI scale and (on
Android) the system animator scale and window tint; every piece also works outside it, on Mustard.

~~~kotlin
H2g2Theme {
    H2g2PillMenu(
        roots = listOf(MenuNode("pkg", "Package management", children = listOf(MenuNode("install", "install")))),
        placement = PillMenuPlacement.TopRight, // top of the screen; the stack falls downward
        onRun = { tokens, complete -> if (complete) run(tokens) },
    )
}
~~~

| Package | What it holds |
| --- | --- |
| root | `H2g2Theme`; `H2g2Chip`, `H2g2Heading`, `statusColor`, `readableOn`, `contrastRatio`, `Modifier.minTouch`, `screenInsets`, `maxContentWidth` (`Primitives.kt`); `H2g2Splash`, the hand that counts four, then two, then approves. |
| `menu` | `H2g2PillMenu`: the breadcrumb pill stack. Right-anchored pills, the eleven-way entrance lottery, the host drop, the child cascade, the shingled trail, slot-reel scrolling. `PillMenuPlacement` mirrors it into any corner: `BottomRight` (the house stack), `TopRight` (top of the screen, growing downward), `BottomLeft`, `TopLeft`. |
| `files` | `H2g2FilesScreen`: folder tiles that morph open into cards while their siblings collapse to rods, nested levels, selection, in-place preview. `H2g2FilesReveal` (the perimeter-run open and close), `H2g2FolderPicker`, `H2g2PathPickerScreen`, `H2g2StorageScreen`, `H2g2MarkdownPreview`. |
| `guide` | `H2g2GuideReaderScreen`, `H2g2CommandGuideScreen`, `H2g2GuideLinkedText` (prose whose commands are tappable). Takes any `GuideContent`; `HitchhikersGuideToTermux` is the default. |
| `terminal` | `H2g2TerminalScreen`: session tabs, shell status line, output cards, the pill stack, the composer and RUN. `H2g2FluidTerminalWorkspace`, `H2g2ShellStatusLine`, `H2g2LiveStatusStrip`, `H2g2ComposerKeys`, and the completion, alias and chaining models. |
| `tui` | `H2g2FullScreenTerminalScreen` (the Azrienoch Mono grid, key rows, gestures) and `H2g2AdaptiveTuiScreen` (menus, tabs, lists, tables and prompts projected from a running terminal app, with the RAW fallback). |
| `components` | `H2g2ConfirmDialog`, `H2g2JobProgressBar`, `H2g2TypesetOutput` with `H2g2KeyValueTable`, `H2g2WideTableRecords`, `H2g2AsciiArtCanvas`; `StyledSpan`, `BackStepState`. |
| `screens` | `H2g2EditorScreen`, `H2g2AzpStoreScreen`, `H2g2AiChatScreen`. |
| `settings` | `H2g2SettingsScreen`, `H2g2AiSettingsScreen`, `H2g2McpServerScreen`, `H2g2EnvironmentScreen`, `H2g2HistoryScreen`, `H2g2IsolationAuditScreen`. |

No Material dependency: the screens' text goes through an internal wrapper over foundation.

The documents all of this was built from are in [`docs/`](docs/README.md).

### Motion: containment and the pill choreography

HG2Gui's motion, lifted here so every app that speaks HG2Gui moves the same way.

- `Nest.kt` — the containment grammar. `H2g2Nest` is a pill that grows into a card holding its options; `NestScope.Nest` opens a child card *inside* that card, and while it is open its siblings collapse into rods (spoken by name). The surface follows depth (`recordTile` → `note` → `well`), the corner radius morphs on the same clock as the size (`NestMorphShape`), and Back closes the innermost open card first.
- `Motion.kt` — `H2g2Motion` (the page ease, animator-scale `durationScale`, `innerHue` for a child's fill), `StackEntrance` arrivals, `staggerDelayMs`/`stackInterval`, and the additive `wobble` sway.
- `BreakFree.kt` — the run transition's beats 1–3: stretch, break free, fly, thud, fall (and their reverse).
- `PerimeterReveal.kt` — `H2g2PerimeterReveal`: a picked pill runs the screen's perimeter edge by edge and floods into the page it opens. Tap skips; reduced motion shows the end state.
- `SlotReel.kt` — `rememberSlotReelScroll`: a stack positioned by animated offsets scrolls by drag and a fling lands on a row boundary.

~~~kotlin
H2g2Nest(label = "Hive", hue = hue) {
    Nest("Roles") { Option("Queen") { pick("queen") } }
    Option("Rename") { rename() }
}
~~~

### The workflow/terrarium subsystem

The same package also holds a considerably larger body of work than the style tokens above: a
renderer for a process graph, and an artificial-life reading of that same graph. None of it is
`Templates.registry` material -- these are directly-called composables and plain state classes, not
`.azp` manifest templates -- but it is most of what is actually in `commonMain`:

- **`Workflow.kt`** -- the workflow vocabulary and its default renderer. `H2g2WorkflowNode`/
  `H2g2WorkflowEdge`/`H2g2WorkflowBand` (a band is one topological rank; several nodes in a band
  render as a visible fork) plus `H2g2WorkflowState` (Pending/Ready/Active/Gate/Blocked/Complete/
  Failed) and `H2g2WorkflowMotion`, fourteen named motion personalities a host may assign
  explicitly or let `motionSeed` pick deterministically. `H2g2WorkflowMap` renders subjects as
  identity-hued vector lozenges floating on the `Ground`, connected by thick cubic routes, with
  forks/joins/gates spatial rather than iconographic; a node with `progress` fills from the bottom
  in its own identity color instead of growing a detached progress indicator, a selected subject
  transforms in place and reveals its detail beneath it instead of opening an inspector, and a
  branch inherits a damped 22%-per-generation share of its superior's motion so relatedness is
  visible without an extra highlight.
- **`SemanticWorkflowMap.kt`** -- `H2g2SemanticWorkflowMap`, the same graph under deliberately
  discrete navigation. There is no free camera: `H2g2WorkflowZoomLevel` is exactly
  Overview/Band/Node, `H2g2WorkflowViewportState` (via `rememberH2g2WorkflowViewportState`) owns
  which one is current, and a pinch or a selection moves exactly one semantic level -- one gesture
  can never skip two. Overview renders one summary subject per band; system Back retreats a level.
- **`SwarmSimulation.kt`** -- `H2g2SwarmWorld`, a cheap artificial-life simulation with no
  rendering in it at all. One shared world step, but each creature owns an independent decision
  cadence (~4-9 Hz), personality (`H2g2SwarmPersonality`), RNG stream and behavior state machine
  (`H2g2SwarmBehavior`: Wander/Rest/Investigate/Avoid/Approach/Confer/Transfer/Follow/
  PrepareSpawn/Birth/Working/Blocked/Startled/Recover), plus pairwise `H2g2SwarmContact`s.
  Callers drive it from one render clock and interpolate `H2g2SwarmAgentSnapshot`s at frame rate;
  large stalls are clamped so a backgrounded tab doesn't explode the bowl.
- **`SwarmCharacter.kt`** -- the procedural creatures themselves. `h2g2SwarmGenome(identitySeed)`
  derives a stable body plan (sides, proportions, eye/limb counts, antennae, one of eight
  `H2g2SwarmLocomotion` gaits) from an identity string, identically on every target; color is
  deliberately *not* part of the genome, so two agents stay distinguishable in monochrome.
  `H2g2SwarmCharacter` draws it as low-poly 3D projected into a Compose `Canvas` with real
  discrete light/mid/shadow cel shading (no gradients), hued via `H2g2.indexOf`.
  `structuralSignature()` is the genome's regression-test/cache key.
- **`SwarmAdornment.kt`** -- how a dependency is taught visually.
  `H2g2DependencyManifestation` is Synapse (keep the graph edge visible), Tool (carried) or
  Clothing (worn); `h2g2SwarmAdornment` picks a deterministic `H2g2SwarmAdornmentKind`
  (Probe/Wrench/Clipboard/Lantern/Satchel/Vest/Goggles/Helmet/Cape/Belt) for the latter two, so a
  dense workflow turns into things creatures carry rather than edge spaghetti.
  `H2g2SwarmAdornmentLayer` is the cel-shaded overlay, sharing the creature's own canvas -- no
  meshes, images or physics objects.
- **`Terrarium.kt`** -- `H2g2SwarmTerrarium`, the composable that puts the two halves together: a
  workflow rendered as a living habitat. `H2g2TerrariumSubject` pairs a workflow node with a
  normalized `H2g2TerrariumPosition` (persist that, never pixels), `H2g2TerrariumRelationship`
  carries Dependency/Spawn/Transfer/Confer. Compose supplies one display-frame clock and renders
  `H2g2SwarmWorld` snapshots rather than running AI or physics per frame. Dragging is presentation
  only until the drop lands: `onNodeDroppedOn` is the semantic boundary where a host rewrites,
  stages, or rejects. The orchestrator is host-supplied artwork by contract -- `orchestratorContent`
  is *required* when any subject is an `Orchestrator`, and the composable throws rather than
  procedurally approximating it.
- **`TerrariumServiceActor.kt`** -- `H2g2TerrariumServiceVisit`/`H2g2TerrariumServiceLayer`: a
  transient non-agent visitor. Every external service, whichever one it is, arrives as the same
  fictional USPR mail truck with the same enter/stop/leave choreography -- no per-service skin, no
  RNG, no persistent identity, no simulation loop. The truck is a visual verb ("something external
  arrived"), deliberately not another entity a person has to learn to recognize.

## Status

Tracks HG2Gui at `0d8edf6`: the tokens, the typeface, every screen and shared component, and the
splash its design files specify. Not ported, because they are HG2Gui's data rather than its design:
the command, authority and package-lifecycle menu trees, and everything that does work (shell,
PTY, VFS, MCP, AI clients, preferences). A host supplies those.

Verified by `./gradlew desktopTest` (unit tests, compose UI tests that tap through each screen, and
`GalleryRender`/`ScreensGallery`, which draw the surfaces off-screen to `build/gallery/` at chosen
moments of their animations). Android, JS and Wasm are compiled, not run.

The templates cover h2g2's full surface vocabulary (all four shapes have a template) and, per shape,
more than one act/layout pairing where a real pattern from the source app justified it.

An adversarial audit found and this repo has since fixed two real defects: every template's
`Offer` was missing `Modifier.tell(owesTell, weight).clickable { engage() }` -- every element
rendered but was inert, since nothing ever engaged the act on tap (matches
`conveyance-demo/.../Gallery.kt`'s own wiring now); and `hueSeed` resolution went straight to
`H2g2.hueOf`'s hash, so a manifest author naming a real hue (`"hue": "violet"`) never actually got
violet -- `H2g2.indexOf` now checks `hueNames` for an exact match first, falling back to the hash
for anything else.

A later audit found a third: `ComposableRequest.surface` was documented as selecting the shape via
`H2g2Surface.byName()`, but every template hardcoded its own shape literal and never read the field,
leaving `byName` with zero callers -- a manifest field that did nothing. Every template now resolves
through `request.surfaceShape(default)`, with the template's previous hardcoded literal as that
default so no existing caller's rendering changes. All three fixes are pinned by
`src/commonTest/.../TemplatesTest.kt`, which composes each registry template for real and injects a
click to prove the act is actually engaged -- the inert-template defect is invisible to any test
that only inspects values, since a template missing `.clickable { engage() }` still renders
perfectly.

## Using it

```kotlin
repositories {
    maven("https://jitpack.io")
}
dependencies {
    implementation("com.github.HereLiesAz:conveyance-h2g2:0.5.1")
}
```

Resolved via [JitPack](https://jitpack.io) directly from this repository -- `conveyance-core` and
`conveyance-compose` both apply `maven-publish`, which is all JitPack needs, so there is no
separate publish step to configure.

This artifact pins its own dependency on Conveyance to the matching `0.5.0` tag, not to
`main-SNAPSHOT`: a floating snapshot makes KMP metadata non-reproducible and can resolve stale
per-target publications through JitPack. Consumers can take `main-SNAPSHOT` instead to track `main`.
