# Documentation

- [`USAGE.md`](USAGE.md): how to call each surface from a host app.
- [`../CHANGELOG.md`](../CHANGELOG.md): what changed, including under existing callers.
- [`../README.md`](../README.md): what the library is and how to depend on it.

Everything below is specification, not instruction.

# The documents HG2Gui was built on

Copied from [HereLiesAz/HG2Gui](https://github.com/HereLiesAz/HG2Gui) at `0d8edf6`, unedited. They are
the specification this library implements. Where a document and the Kotlin disagree, the Kotlin is what
shipped; the document is why.

The `.html` files are self-contained: open them in a browser.

## `design/` — the system

| Document | What it settles |
| --- | --- |
| `DESIGN.md` | The written rules: pill anchoring, host selection, trail, motion, dedicated surfaces, scale, colour and geometry, accessibility, shared primitives and minimums. |
| `VISION.md` | What HG2Gui is for, and what it refuses to be. |
| `USER_GUIDE.md` | The behaviour as a person meets it, screen by screen. |
| `HG2Gui Style Guide.html` | Grounds, the fourteen hues and their caps, reference groupings, type scale, surfaces. |
| `HG2Gui Menu Style Guide.html` | The pill menu: anchoring, width variation, overhang, trail. |
| `HG2Gui Motion Sheet.html` / `.dc.html` | Every clock and curve. |
| `HG2Gui Stack Entrances.dc.html` | The eleven ways a stack arrives, and their odds. |
| `HG2Gui Run Transition.html` / `.dc.html` | Stretch, break free, fly, thud, fall; the perimeter run and flood. |
| `HG2Gui File Manager.html`, `… (standalone).html` | The explorer: tiles, rods, the open and close morphs. |
| `HG2Gui Reading.html`, `HG2Gui Guide.html` | The Guide's reading surface. |
| `HG2Gui Guide Motion.html` / `.dc.html` | How the Guide moves. |
| `HG2Gui Splash.html` | The splash: a hand that counts four, then two, then approves. HG2Gui specified it and never built it; `H2g2Splash` here is built from this file. |
| `HG2Gui Implementation Audit.dc.html` | The design checked against the code, gap by gap. |
| `HG2Gui Termux Coverage.dc.html` | Which commands the menus cover. |
| `LICENSE_JOST.md` | Jost's licence. Azrienoch, the bundled typeface, derives from Jost; its own licence is in `third_party/azrienoch/`. |

## `brand/`

The logo and banner, as SVG, PNG and WebP.

## `art-direction/` — the Guide's illustrated entries

| Document | What it settles |
| --- | --- |
| `00_READ_ME_FIRST.md` | The visual target: the flat, institutional calm of the 2005 film's Guide graphics. |
| `HG2Gui_animation_style_lock_prompt_v2.md` | The 1:1, single-background, no-chrome style lock. |
| `HG2Gui_character_consistency_and_frames_plan.md` | Reference board first, then shared boundary frames, so a character survives the cut. |
| `HG2Gui_animation_5s_cut_sheet_v2.md` | All 35 entries cut into five-second shots. |
| `guide-tangents.md` | Editorial and animation rules for the tangents. |
| `references/` | Eight frames from the film, and the mustard character sheet. |

Not copied: the Guide manuscript, the 1,758 per-clip production packets, the generated frames, the test
clips and the layered artwork. They are content and production output, about 200 MB of it, and they
stay in HG2Gui.
