# Phase 2 report — full application rebuild

## 1. What was investigated

- Every screen, feature and data store of v1.3.0 (`origin/main`, package `com.example`) from the
  Phase 0 audit and the feature inventory (`03-feature-inventory.md`), re-checked against the
  original source while writing replacements — e.g. the v1 Room entities in
  `app/src/main/java/com/example/data/database/Entities.kt` (DB `melody_player_local.db`, schema v2)
  were used verbatim to build the legacy importer and its test fixture.
- Media3 1.8.0 APIs (MediaLibraryService, `setMediaButtonPreferences`, `CommandButton.Builder(icon)`,
  `onPlaybackResumption`, `DefaultMediaNotificationProvider.Builder`) were verified against the
  published AARs with `javap` before use rather than assumed.
- UI/UX guidance from the mandatory ui-ux-pro-max skill (Compose stack: stable keys in lazy lists,
  `LazyColumn`/`LazyVerticalGrid` over nested scrolling, 48 dp targets, reduced motion).

## 2. What was rebuilt

Nothing from v1 was carried over; the old source tree is deleted.

| Area | New implementation |
|---|---|
| Design system | "Tide" palette (old palette removed), WCAG `Contrast` helpers, dark / light / pure-black schemes, dynamic colour passed through a contrast guard, Manrope type scale, shared components (rows, cards, sheets, dialogs, snackbars, loading/empty/error states) |
| Data | MediaStore source → immutable `LibraryIndex` (grouping + accent-insensitive search), ContentObserver refresh, hidden folders, Room `mtplayer.db` v1 with exported schema and no destructive fallback, DataStore settings |
| Migration | `LegacyImporter`: read-only, one transaction + marker, keeps v1 DB untouched, imports favourites (incl. songs v1 stored in its hidden "favorites" playlist), playlists in order, history, video resume points, hidden folders, theme/player/sleep/EQ settings; skips v1's seeded fake playlists when empty |
| Playback | `PlaybackService` (MediaLibraryService, notification with favourite/repeat/shuffle buttons, metadata updates, sleep timer with fade/finish-song, queue persistence and resumption, audio focus, becoming-noisy, error skip limit, EQ/bass/virtualizer), `PlayerConnection` |
| Video | Dedicated `VideoPlayerActivity` with its own ExoPlayer and MediaSession, gestures (brightness, volume, seek, double-tap, pinch), fit/zoom/fill, orientation by aspect, lock, audio/subtitle tracks, external subtitles via SAF, speed, resume, PiP with remote actions and auto-enter, background-audio hand-off |
| UI | Splash, permission gate, Home, Music (Songs/Albums/Artists/Playlists/Genres/Folders), Videos (grid/list, folders), Saved (favourites, recent, most played, watch history), local Search, detail screens, playlist management with undo, Now Playing (lyrics with sync, queue, sleep, speed), Equalizer, Settings, hidden folders, crash reports, bottom bar / navigation rail ≥ 600 dp (tablets, TV) |
| Mini player | Close button and swipe-to-dismiss stop playback and clear the queue, with snackbar feedback |
| Updater | No embedded token; public Releases API, `SHA256SUMS.txt` check, package name, versionCode and signing-certificate verification before PackageInstaller |
| Identity | New adaptive logo (foreground, background, monochrome), notification icon, splash icon, TV banner; applicationId and signing unchanged |

## 3. Files and commits

`app/src/main/java/com/mtplayer/app/**` (≈ 8.7k lines), `app/src/main/res/**`,
`app/src/main/AndroidManifest.xml`, `app/schemas/**`, `app/src/test/**`.

| Commit | Content |
|---|---|
| `165e03c` | Foundation: design tokens, data layer, playback service, updater |
| `91d4155` | Manifest, resources, logo, components, shell, mini player |
| `ce08d0c` | Screens |
| `2e8948b` | Video player |
| `a8b88ed` | Strings, compile fixes — first successful `assembleDebug` |
| `bf96544` | Unit tests + fixes they exposed |

## 4. Tests executed

Local (sandbox, JDK 21, no emulator): `./gradlew assembleDebug testDebugUnitTest` — build
successful, 25 tests, 0 failures.

| Test class | Covers |
|---|---|
| `ThemeContrastTest` | every text role ≥ 4.5:1 on every surface for dark, light, pure-black and built schemes; light theme text darker than surfaces; contrast guard repairs hostile dynamic schemes |
| `LrcParserTest` | synced/plain/offset/multi-tag/BOM parsing, active-line search |
| `LibraryIndexTest` | sort order, album grouping and artist, genre merging, folders, accent-insensitive multi-term search |
| `UpdateManagerTest` | version comparison, `SHA256SUMS` parsing, SHA-256 |
| `CrashLogTest` | private paths and content URIs are redacted |
| `LegacyImporterTest` | full v1 import, ordering, fake playlist skip, idempotency, v1 DB unchanged |

Bugs found and fixed by these tests: contrast guard checked on-colours against the uncorrected
accent; `Contrast.ensure` failed on mid-tone backgrounds; genre names used an arbitrary spelling;
crash redaction stopped at spaces in file names.

CI result for `bf96544`: GitHub Actions run 43725855199 — build, unit tests, lint (non-blocking) all green.

## 5. Remaining problems

- No emulator or device execution yet: playback, notification, PiP, permissions and process
  death are implemented but unverified.
- Lint not reviewed (CI step is non-blocking).
- Release (R8) build not yet exercised.

## 6. Next

Phase 3: emulator instrumentation in GitHub Actions (UI smoke, MediaSession/notification,
process death, PiP, permissions on API 29/34/35) and a v1 → v2 data migration check on device.
