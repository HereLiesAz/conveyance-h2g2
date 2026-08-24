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

Example composable manifest referencing this library, once it has a real template:

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
      { "id": "example", "templateId": "<pending>", "hue": "<host-defined>", "surface": "<host-defined>", "scale": "<host-defined>", "act": "<host-defined>", "jobs": ["<what this element does>"] }
    ]
  },
  "files": {}
}
```

## Status

Scaffold only. `Templates.kt` is an empty registry -- no `templateId` has been designed yet. The concept (port h2g2's own token system) is set; the token/template design work is next.

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
