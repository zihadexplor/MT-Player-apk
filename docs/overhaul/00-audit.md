# UI/UX Overhaul — Phase 0 Audit

Branch: `feat/ui-overhaul` (from `main` at `1d9a8fb`, v2.0.0).
Brief: the "Master Engineering & UI/UX Overhaul Prompt" (preserved verbatim in
[AGENT-HANDOVER.md](AGENT-HANDOVER.md)).

## 1. Repository audit (MT Player)

| Branch | State vs `main` | Notes |
|---|---|---|
| `main` | — | v2.0.0 rewrite merged (PR #5, `b7bb8bf`), released as tag `v2.0.0`. |
| `rewrite/v2` | 0 ahead | Fully merged. Kept for history. |
| `feat/official-gradle-updater-ci`, `fix/insets-crash-nav-thumbnails`, `fix/release-signing` | 0 ahead | Pre-rewrite (v1.x) work, all merged. |
| `zihadexplor-patch-1`, `zihadexplor-patch-2` | 1 ahead each | README-only web edits from Nov 4 (v1 era). Not relevant to this overhaul; left untouched. |

Documentation read before acting: `docs/DEVELOPMENT.md`, `docs/OWNER-MANUAL-TESTS.md`,
`docs/rewrite/00..06`, `docs/rewrite/NEXT-AGENT-HANDOVER.md`, ADRs 001–011.

Current UI (v2.0.0) that this overhaul changes:

- **Top bar** (`feature/common/Common.kt` `TopLevelBar`): Material 3 `TopAppBar`, single title
  (`app_name` on Home), plain search and settings icon buttons.
- **Home** (`feature/home/HomeScreen.kt`): a "Shuffle all" quick card and a "Videos" quick card,
  then Continue watching (video row), Recently played, Playlists, Most played (list), Recently
  added (list), Rescan.
- **Bottom navigation** (`feature/shell/AppRoot.kt`): stock `NavigationBar` with the mini player
  stacked on top; `NavigationRail` at ≥600dp (tablets and TV).
- **Design system** (`designsystem/theme`): "Tide" palette (cool ink neutrals, Ember orange
  primary, Iris, Lagoon), Manrope variable font, `Contrast` WCAG guard with `ThemeContrastTest`.
- **Launcher icon**: adaptive vector, ember disc with a play glyph on dark ink; TV banner vector.
- **Settings > About**: only a Version row.

Data already available locally (no new storage needed):

- Last song: `PlaybackService.restoreQueue()` restores the saved queue at service start, so
  `PlayerUiState.current` holds the last song and its position once the app connects.
- Last video: `AppViewModel.continueWatching` (from `video_progress`, ordered by last watched).
- Recently played / most played songs (`play_history`), playlists, library index (songs and
  videos with `dateAddedSec`).

## 2. Reference audit (Yumeiro, `siamform/yumeiro`)

Licence: **GPL-3.0-or-later**, plus `TRADEMARKS.md` reserving the Yumeiro name, logo and icon.

Decision (ADR-012): **study and reimplement, do not copy.** No Yumeiro source file, string,
font file taken from its repo, image or logo is copied into MT Player. Copying GPL code would force
MT Player under the GPL, and the trademark file forbids reusing its brand. Patterns, layout
structure and interaction ideas are not copyrightable and are reimplemented in MT Player's own
code, palette and brand.

Yumeiro design language observed (screenshots in `fastlane/.../phoneScreenshots`, code in
`ui/components`, `ui/theme`, `ui/screens/home`):

| Pattern | Yumeiro | MT Player adaptation |
|---|---|---|
| Top bar | Small accent overline ("夢色 · YUMEIRO") above a large display title. Home title is a time-based greeting: 5–11 morning, 12–16 afternoon, 17–21 evening, else night. Header actions are separate 40dp rounded-square tiles (14dp corners) with a faint fill and hairline border. | Same structure. Overline "MT PLAYER" on Home and short verbs on tabs (LISTEN, WATCH, YOUR COLLECTION). Greeting from string resources, refreshed on the hour. 48dp touch targets. |
| Section headings | Overline (accent, bold caps) + section title in a wide display face; trailing "See all" text action. | Same, with MT Player strings. |
| Continue / Jump back in | Two-up cards at the top of Home: "Music · last played" and "Video · last watched", artwork, accent round play button, title/subtitle, thin progress bar. | Same, fed only from local data (last queue song, last watched video). |
| Feed rows | Horizontal rows of 120dp square cards with a small type badge; 16dp gutter, 12dp gaps, 28dp space above sections. | Same for Recently played, Most played, New on this device, Playlists; video cards for more in-progress videos. |
| Bottom navigation | Floating rounded dock (inset from edges), surface fill, hairline border, soft shadow; active tab = accent pill behind the icon + accent label; mini player joins the dock. | Same structure in MT Player tokens; rail kept for ≥600dp and TV. |
| Typography | Unbounded (display) + Manrope (text). | Manrope already bundled; add a wide display face for titles (Unbounded, SIL OFL 1.1, fetched from the upstream `google/fonts` repository, not from Yumeiro). |
| Palette | Near-black warm canvas with a gold accent (#F5C46B). | MT Player keeps its own Ember identity: warmer ink neutrals and an amber-to-ember accent. |
| Logo | Gold crescent moon with a play glyph on dark ink — the mark tells the name ("dream colour", night). | New MT Player mark: own concept and palette (see ADR-015). |

Not adopted (out of scope or online-only in Yumeiro): extensions, streaming, YouTube, Spaces grid,
mood sections, inbox, kana overlines, kanji watermarks.

## 3. Competitor scan (for scale and positioning)

- Musicolet leads "offline" recommendations because it has no internet permission and is ad-free
  ([SoundMavericks](https://soundmavericks.com/best-android-music-players/),
  [bestappsforandroid](https://bestappsforandroid.com/best-music-player-apps-for-android/)).
- Small, fast open-source players (Symphony 4.8 MB, Auxio 10.6 MB, Retro Music 9.2 MB) are praised
  for opening instantly ([FileCobra](https://www.filecobra.com/guides/best-android-music-players/)).
- Pulsar and Retro Music are picked for a polished, modern Material look
  ([unstore.io](https://unstore.io/discover/best-offline-music-player-mp3-alternatives/)).
- For video, VLC is the default for format coverage, MX Player for gestures and subtitles, Nova
  for local libraries and Android TV
  ([enveu](https://www.enveu.com/blog/best-android-video-players-for-ott-streaming-apps)).

Implication for MT Player: the redesign must look premium (Pulsar/Retro level) while staying
small and fast (Symphony/Auxio level) and fully offline (Musicolet). So: vector-only brand
assets, one extra font at most, no new libraries, no new permissions, lazy lists with stable keys
and content types, and nothing on Home that does I/O on the main thread.

## 4. UI/UX skill output (ui-ux-pro-max, run first)

`search.py "music video player entertainment dark editorial" --design-system` recommended a
"Dark Mode (OLED)" style for entertainment apps: deep dark surfaces, low white emission,
4.5:1 text contrast, visible focus states, reduced motion respected, no emoji icons. Its generic
palette (indigo + green) is not used — MT Player keeps its Ember brand. Rules applied: 48dp touch
targets, visible D-pad focus, contrast verified by `ThemeContrastTest`, motion ≤300ms.

## 5. Environment

JDK 21 at `~/jdk`, Android SDK at `~/android-sdk`; no KVM locally, so emulator tests run only in
CI (API 29/34/35). Build and test commands are in [AGENT-HANDOVER.md](AGENT-HANDOVER.md).
