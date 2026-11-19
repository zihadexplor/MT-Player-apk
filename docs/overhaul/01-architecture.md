# UI/UX Overhaul — Architecture and Decisions

Scope is exactly the six items of the brief: greeting top bar, Home "Jump back in" (Shuffle all
and Videos tiles removed), Yumeiro-style feed, new logo, global UI refresh, and the Settings >
About credit line. No new features, permissions, libraries, network access or storage.

## 1. Constraints (non-functional requirements)

| Area | Requirement | How it is met |
|---|---|---|
| Identity | Same `applicationId` (`com.aistudio.mtplayer.kmzv`), signing key and app name; installs as an update. | No Gradle identity changes. Launcher icon resources keep their names (`ic_launcher*`, `tv_banner`). |
| Offline | Local only. | Home reads only Room, DataStore, the library index and the player state. |
| Scale (200k MAU) | Smooth on low-RAM phones through flagships and Android TV; no regressions in startup or scrolling. | Vector-only brand assets; one display font (~100 KB); Lazy lists with stable keys and `contentType`; live progress read in draw phase only for the one current card; no new background work. |
| Accessibility | Dark-first, readable light theme, WCAG AA 4.5:1 text, 48dp touch targets, visible focus for D-pad/keyboard, TalkBack labels. | Palette checked by `ThemeContrastTest`; every new clickable has `Role`, a label and a ≥48dp target; focus ring on dock and header tiles. |
| Responsive | Phones <380dp, normal phones, tablets, TV. | Compact title style under 380dp; Continue cards stay two-up down to 320dp; ≥600dp uses a navigation rail (also TV); content max-width unchanged. |
| Data safety | Never wipe or overwrite user data. | No schema or settings migration in this overhaul. |

## 2. Design system "Ember Night" (replaces Tide neutrals, keeps Ember brand)

Tokens live in `designsystem/theme`. Components never use raw colours.

- **Neutrals:** warm ink (dark) and warm paper (light) instead of the cool blue-grey ink.
- **Accent:** Ember stays the primary (brand continuity with v1/v2), tuned toward amber so it
  matches the new logo gradient (amber `#FFB45E` → ember `#FF6A3D`). Iris and Lagoon remain as
  secondary/tertiary for chips and states; secondary containers (tonal buttons) use warm sand
  `#F2E2D5` (light) / clay `#3A2C27` (dark) so nothing reads as the old lavender.
- **Type:** wide display face (Unbounded, SIL OFL 1.1) for page titles and section titles only;
  Manrope for everything read. Scale: page title 26/32, compact 22/28, section 17/24, overline
  11/16 bold caps tracking 0.08em.
- **Shapes:** cards 20dp, artwork 14dp, header tiles 14dp, dock 28dp, chips full pill.
- **Spacing:** 16dp gutter, 12dp row gap, 28dp above a section, 8dp rhythm.
- **Motion:** press scale 0.94 spring, colour changes 200ms, no motion that loops.

New shared components (`designsystem/components/Editorial.kt`):

- `Overline` — accent caps label.
- `HeaderAction` — 40dp rounded-square tile inside a 48dp target, faint fill and hairline border,
  press scale, focus ring.
- `SectionHeading` — overline + display title + optional "See all".
- `ContinueCard` — the Jump back in card (art 1.25:1, accent round play button, eyebrow, title,
  subtitle, progress bar).
- `SquareCard` — 120dp feed card with a type badge (song / video / playlist).

Shell components (`feature/shell`): `Dock` (floating bottom navigation that the mini player joins)
and a restyled rail; `feature/common`: `TopLevelBar(overline, title)` built from the components
above; `DetailBar` uses a `HeaderAction` back tile.

## 3. Greeting (top bar)

`designsystem/components/Greeting.kt` (pure Kotlin part unit-tested):

```kotlin
enum class DayPart { MORNING, AFTERNOON, EVENING, NIGHT }
fun dayPartFor(hour: Int): DayPart  // 5..11, 12..16, 17..21, else night (Yumeiro ranges)
@Composable fun rememberGreeting(): String  // string resource; re-evaluates at the next hour boundary
```

The composable schedules one `delay()` until the next full hour (not a per-second ticker), and
re-reads the clock when the app returns to the foreground, so a long session or a resume after
midnight shows the right greeting.

## 4. Home feed ("Jump back in" + Yumeiro-style rows)

Order, top to bottom (each section hides when empty; the Continue pair is always shown):

1. **Jump back in** (overline "PICK UP WHERE YOU LEFT OFF") — two-up:
   - Music · last played: `PlayerUiState.current` when it is a song (restored queue), else the
     most recent `play_history` song. Play button toggles play/pause for the current song or
     starts the last song; tapping the card opens Now Playing (or plays). Live progress for the
     current song.
   - Video · last watched: first `continueWatching` entry with its saved progress; tap resumes in
     the video player. Empty state opens the Videos tab.
2. **Keep watching** — remaining in-progress videos (row of video cards), if more than one.
3. **Recently played** — square song cards.
4. **Your playlists** — square playlist cards.
5. **Most played** — square song cards.
6. **New on this device** — newest songs and videos mixed by date added.
7. Rescan footer.

Removed: the "Shuffle all" and "Videos" quick cards (shuffle remains on the Music tab and in
collections; Videos remains a tab).

## 5. Architecture Decision Records

### ADR-012 — Reimplement Yumeiro's design language, never copy its code or assets
- **Status:** Accepted.
- **Context:** The brief asks to adopt Yumeiro's design language. Yumeiro is GPL-3.0 with a
  trademark policy on its name, logo and icon. MT Player is not GPL.
- **Decision:** Study layouts, spacing, hierarchy and interactions; write all code from scratch in
  MT Player's architecture; use MT Player's palette, strings and logo. Fonts are taken from the
  upstream OFL source, with their licence files.
- **Consequences:** Similar feel, no licence contamination, no brand confusion. Some details
  differ on purpose (no kana, no gold).

### ADR-013 — Greeting computed on device from the hour, refreshed on the hour boundary
- **Status:** Accepted.
- **Alternatives:** per-minute ticker (wasteful), static at composition (goes stale).
- **Decision:** pure `dayPartFor(hour)` plus a composable that waits until the next hour and
  re-reads on resume. Strings are resources so they can be translated.

### ADR-014 — "Jump back in" uses existing local state, no new storage
- **Status:** Accepted.
- **Decision:** last song from the restored player queue (falls back to play history), last video
  from `video_progress`. No new tables or preferences, so no migration risk and no data loss.

### ADR-015 — New brand mark, vector-only
- **Status:** Accepted.
- **Decision:** A new MT Player mark drawn as Android vectors (adaptive foreground, background,
  monochrome for themed icons, splash, TV banner) in the Ember Night palette. The mark combines
  the play glyph (video) with sound bars (music) so it says "media player" the way Yumeiro's moon
  says "night/dream". Raster PNGs are generated only for docs and the README.
- **Consequences:** Crisp at every density, tiny APK cost, works with Android 13 themed icons.

## 6. Testing strategy (delta)

- Unit: `GreetingTest` (every hour 0–23 and boundaries), existing `ThemeContrastTest` must pass on
  the new palette (it guards light/dark/pure-black readability).
- UI (emulator CI, API 29/34/35): `AppUiTest` updated — Home shows the greeting and the "Jump back
  in" section, no "Shuffle all" card, Settings > About shows the credit line; tab navigation via
  the dock labels.
- Shell checks and screenshots in `.github/scripts/emulator-tests.sh` keep running; screenshots
  are reviewed for contrast and clipping.
- Physical-device checks stay in `docs/OWNER-MANUAL-TESTS.md` (emulator results are never reported
  as device verification).
