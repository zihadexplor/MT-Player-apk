# UI/UX Overhaul — Phase Plan

Each phase ends with a build, unit tests and lint locally, then a commit and push to
`feat/ui-overhaul`, then an update to [AGENT-HANDOVER.md](AGENT-HANDOVER.md).

| Phase | Content | Done when |
|---|---|---|
| P0 | Audit, docs, handover (this folder). | Docs pushed. |
| P1 | Design system: Ember Night palette, display font, shapes, `Editorial.kt` components, `Greeting.kt` + `GreetingTest`. | `ThemeContrastTest` and `GreetingTest` pass. |
| P2 | Top navigation: `TopLevelBar(overline, title)` with greeting on Home, `HeaderAction` tiles; `DetailBar` back tile; floating `Dock` + mini player join; rail restyle. | All tabs render; touch targets ≥48dp. |
| P3 | Home: remove Shuffle all / Videos tiles, add Jump back in, Yumeiro-style feed rows. | Home matches 01-architecture §4. |
| P4 | Global refresh: section headings, cards, chips, list rows, sheets, Settings groups, Now Playing controls, empty states use the new tokens. | No screen still uses the old look. |
| P5 | Settings > About credit line "Made with love by Tawfik Estiak Zihad". | Visible and tested. |
| P6 | Brand: new launcher icon (adaptive + monochrome + round), splash, notification glyph check, TV banner, README/docs image. | Rendered and reviewed at 48/96/192 px and on light/dark wallpapers. |
| P7 | Tests and CI: update `AppUiTest`, shell checks; run CI on the branch until fully green; review emulator screenshots. | All CI jobs green. |
| P8 | PR Proposal posted in chat. **No PR is opened and nothing is merged** until the owner approves. | Owner says go. |
