# Azrienoch

The app's typeface. Source: https://github.com/HereLiesAz/azrienoch (tag `1.2`, version 1.2.0, commit `672a5575ff869e761597b32f0e35c73911c16ec1`), SIL Open
Font License 1.1 (`OFL.txt`). Azrienoch is a Modified Version of Jost and includes outlines derived from Arimo. The 1.2.0 build retains a
`wdth` axis that runs from 0 (Mono, every ASCII glyph 620 units wide) to 100.

`src/commonMain/composeResources/font/azrienoch_{regular,semibold,extrabold,black}.ttf` are static
instances of `fonts/variable/Azrienoch-VF.ttf` made with `fontTools.varLib.instancer` (fontTools 4.63.0, `updateFontNames=False`) at `wght` 400 / 600 /
800 / 900, `wdth` 100, `SERF` 0, `GRAD` 0.

Characters used in the app's strings that Azrienoch still lacks: `▸` and `✓`. They render in the system font.

`azrienoch_mono_regular.ttf` and `azrienoch_mono_bold.ttf` are the Mono cut (`wdth` 0) at `wght` 400 and 800, used by the full-screen terminal grid.

Source TTF SHA-256: `1932c8156fb65aee5c0ca05cb229ec78ee4f487240034c84595b359fdb8f46b4`.
The import verifies this digest before instantiating every axis, verifies each static weight and checks equal printable ASCII advances in both mono cuts. The OFL remains unchanged.
