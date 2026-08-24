# conveyance-h2g2

A composable-set library for [Conveyance](https://github.com/HereLiesAz/Conveyance): The h2g2 style system -- hues, ground-rotation surfaces, and the 8-step Jost type scale -- ported from HG2Gui.

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
  "license": "MIT",
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
- **`Ground`/`GroundState`** (`Grounds.kt`) -- the six weighted background themes and their
  fold-crease gradient. Adapted from a process-wide singleton (h2g2's own app-level pattern) to a
  `GroundState` a host instantiates and scopes itself, since a library shouldn't impose global
  mutable state on its consumer.
- **`H2g2Type`/`h2g2Type()`** (`Type.kt`) -- the eight-step type scale (`hero` through `micro`).
  Defaults to `FontFamily.Default`; the original uses Jost specifically, but bundling that
  typeface's font files is a per-host asset decision this library doesn't make for you -- pass
  your own Jost `FontFamily` to `h2g2Type()` for the exact original look.
- **`H2g2Surface`** (`Surfaces.kt`) -- the three-shape vocabulary: `recordTile`, `note`, `capsule`
  (nearly everything ends up `capsule`).
- **`Templates`** (`Templates.kt`) -- the `templateId` registry. Two templates so far:
  `h2g2.tile.record` (a `recordTile`-shaped element) and `h2g2.pill.action` (a `capsule`-shaped
  one) -- both `Offer`-backed, hue-colored via `hueOf`, type-set via `h2g2Type().step(scale)`.

## Status

A first real slice, not a finished set. Two templates exist; h2g2's actual visual range is much
wider than that (ground backgrounds aren't wired into a template yet, `note` has none, and there's
only one label-only layout per shape). More templates, and Jost as the actual default rather than
an opt-in, are the natural next additions.

## Using it

```kotlin
repositories {
    maven("https://jitpack.io")
}
dependencies {
    implementation("com.github.HereLiesAz:conveyance-h2g2:main-SNAPSHOT")
}
```

Resolved via [JitPack](https://jitpack.io) directly from this repository -- `conveyance-core` and
`conveyance-compose` both apply `maven-publish`, which is all JitPack needs, so there is no
separate publish step to configure. Conveyance itself has no tagged release yet, so this artifact
and its upstream dependency on Conveyance both pin to `main-SNAPSHOT` for now; switch both to a
real tag once one exists.
