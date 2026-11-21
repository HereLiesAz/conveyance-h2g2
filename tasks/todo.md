# Port HG2Gui's design into conveyance-h2g2

Source: `HereLiesAz/HG2Gui` at `0d8edf6`. Scope agreed with Az: every UI/UX feature as a composable a
host can call, the screens' design included; Azrienoch bundled; design specs plus the Guide art
direction as documents.

## Plan

- [x] Foundation: tokens brought up to HG2Gui's current values, Azrienoch, theme root, primitives
- [x] Documents: `docs/design`, `docs/brand`, `docs/art-direction`
- [ ] Pill menu: the breadcrumb pill stack with its entrances, trail, host drop and run transition
- [ ] Files: explorer with tile/rod morph and open/close transitions, folder picker, path picker, storage, markdown preview
- [ ] Guide: reader, command guide, linked text
- [ ] Components: confirm dialog, job progress bar, typeset output, ASCII art, editor, store, AI chat, back-step, composer keys
- [ ] Terminal: terminal screen, fluid workspace, shell status, full-screen terminal, adaptive TUI
- [ ] Settings family: settings, AI settings, MCP server, environment, history
- [ ] README and `docs/README.md` index; version bump
- [ ] Verify: desktop tests, Android/JS/Wasm compile, a rendered desktop gallery

## Porting rules (every unit follows these)

Package root `com.hereliesaz.conveyance.h2g2`; one subpackage per surface (`menu`, `files`, `guide`,
`terminal`, `screens`, `components`). Entry-point composables take an `H2g2` prefix; models keep
their names. Behaviour, timings, geometry and comments carry over unchanged. App logic (shell, VFS,
MCP, AI clients, preferences) never comes along: it becomes parameters and callbacks.

| HG2Gui | conveyance-h2g2 |
| --- | --- |
| `Azphalt.Ink` / `.White` / `.Yellow` | `H2g2.ink` / `.white` / `.yellow` |
| `Azphalt.hues` / `.caps` / `.hueNames` / `.hueOf` / `.groupings` | `H2g2.*` |
| `Azphalt.Ground`, `.grounds`, `.randomGround` | `Ground`, `grounds`, `randomGround` |
| `Azphalt.currentGround` | `LocalH2g2Ground.current` (read in composition, pass down) |
| `Azphalt.rerollGround()` / `.canRerollGround` | `LocalH2g2GroundState.current?.reroll()` / `?.canReroll` |
| `pageBrush()`, `onPage`, `contrastingText()` | same names (`import ...H2g2.contrastingText`) |
| `Azphalt.SLIDE_MS` `DROP_MS` `SWING_MS` `LIFT_FRACTION` `REVEAL_FRACTION` `UNFOLD_MS` | `H2g2Motion.*` |
| `Azphalt.PAGE_EASE` | `H2g2Motion.Ease` |
| `AzphaltMotion`, `scaledDelayMs`, `rememberReducedMotion` | `H2g2Motion`, same top-level functions |
| `AzphaltDimens` / `AzphaltContrast` | `H2g2Dimens` / `H2g2Contrast` |
| `AzphaltChip` / `AzphaltHeading` | `H2g2Chip` / `H2g2Heading` |
| `AzphaltSurface.recordTile` / `.note` / `.capsule` | `H2g2Surface.*` (plus `.well`) |
| `LocalAzphaltType.current` | `currentH2g2Type()` |
| `LocalAzphaltUiScale` | `LocalH2g2UiScale` |
| `HG2GuiTheme` | `H2g2Theme` |
| `azrienochFontFamily()` / `azrienochMonoFontFamily()` | same |
| `androidx.compose.material3.Text` | the internal `Text` in `Text.kt` (same arguments; no import inside the root package) |
| `material3.LocalContentColor` | `LocalH2g2ContentColor` |
| `hg2gui.shared.generated.resources.*` | `com.hereliesaz.conveyance.h2g2.resources.*` (strings already copied) |
| `StackEntrance`, `staggerDelayMs`, `stackInterval`, `wobble` | already in `Motion.kt` |
| `PillBreakFree.kt` / `PillPerimeterReveal.kt` / slot fling | `BreakFree.kt` / `PerimeterReveal.kt` / `SlotReel.kt` |

No Material dependency. Other Material widgets (`AlertDialog`, `OutlinedTextField`,
`LinearProgressIndicator`) are rebuilt from foundation in the h2g2 idiom.

## Review

(filled in when done)
