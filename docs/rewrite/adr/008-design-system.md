# ADR-008: Design system "Tide"

## Status
Accepted

## Context
The old palette (Sakura Rose `#E8799B`, Midnight/Obsidian/Daylight) must be removed and
light theme had unreadable white text from hard-coded colours.

## Decision
- New tokens: deep ink neutrals, "Ember" orange primary, "Iris" violet secondary,
  "Lagoon" teal tertiary. Separate hand-tuned light and dark schemes plus a pure-black
  option. Every on-colour pair is verified ≥ 4.5:1 by a unit test.
- No raw colours outside `designsystem/theme`. Video overlay uses a dedicated
  `ScrimTokens` set that is always light-on-dark by design (video is always dark).
- Optional Material You dynamic colour (API 31+) and optional artwork-tinted player
  background; both pass through the same contrast guard that adjusts tone until 4.5:1.
- Typography: bundled Manrope variable font (OFL), tabular numerals for times.
- One feedback system: `MtSnackbarHost` for transient messages, `MtDialog` and
  `MtBottomSheet` for decisions. No raw `Toast`.
- Inputs from UI/UX Pro Max: "Music Streaming" dark audio palette direction, 4.5:1 text
  contrast, 44–48 dp targets, theme-based colours only.
