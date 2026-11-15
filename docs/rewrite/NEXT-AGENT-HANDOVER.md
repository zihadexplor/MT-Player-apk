# Next-agent handover — MT Player 2 rewrite

Keep this file current. Update it in every push so work can resume immediately after a crash
or usage limit. Read it first, then `docs/rewrite/00-phase0-audit.md`, `02-architecture.md`,
`03-feature-inventory.md` and the ADRs in `docs/rewrite/adr/`.

## Ground rules (from the owner)

- Full rewrite from scratch, not patches. Strictly local/offline media. The only network use is
  the opt-in updater (public GitHub Releases API, no token).
- Keep `applicationId = com.aistudio.mtplayer.kmzv`, signing certificate
  (`.github/release-signing-cert.sha256`), and user data (favourites, playlists, history).
- Never expose credentials. Signing secrets live only in GitHub secrets
  (`MT_KEYSTORE_BASE64`, `MT_KEYSTORE_PASSWORD`, `MT_KEY_ALIAS`, `MT_KEY_PASSWORD`).
  `MT_UPDATER_TOKEN` is obsolete and must be removed from `release.yml`.
- Work on branch `rewrite/v2`. Commit and push after every meaningful change. Do not wait for
  owner approval between phases. At the end: open PR `rewrite/v2 -> main`, CI green, merge
  (merging to `main` triggers the signed release via `release.yml`).
- Commit trailer: `Co-authored-by: Perplexity <noreply@perplexity.ai>`. PR/GitHub comments end
  with `<sub>Created with [Perplexity](https://www.perplexity.ai/computer/tasks/773a18ab-4d94-4f08-9ec7-eb3c4231591b)</sub>`.
- WIP commits that do not compile yet use `[skip ci]` in the message to avoid wasting CI runs.
- Do not claim physical-device verification from emulator results. Owner-only tests go in
  `docs/OWNER-MANUAL-TESTS.md`.

## Environment (sandbox)

- JDK 21 at `~/jdk`, Android SDK at `~/android-sdk` (platforms 36 / 36.1, build-tools 36.0.0).
  `local.properties` (git-ignored) has `sdk.dir`.
- Build: `ANDROID_HOME=~/android-sdk JAVA_HOME=~/jdk PATH=~/jdk/bin:$PATH ./gradlew --no-daemon assembleDebug testDebugUnitTest` (~5 min, 2 CPUs).
- TLS interception: if Gradle shows PKIX errors, import `/usr/local/share/ca-certificates/*.crt`
  into the JDK with `keytool -importcert -cacerts -storepass changeit -noprompt`.
- No KVM locally: emulator tests run only in GitHub Actions.

## Phase status

| Phase | Scope | Status |
|---|---|---|
| 0 | Audit, environment, executable wrapper | Done (`f2a08de`) |
| 1 | Research, architecture, feature inventory, ADRs 001–011 | Done (`c641d5f`) |
| 2 | Foundation: design system, data layer, DB + legacy import, playback core, updater, app shell, logo | In progress |
| 3 | Music experience: now playing, queue, lyrics, sleep timer, equalizer, notification verification | Planned |
| 4 | Video: dedicated activity, gestures, subtitles, PiP, resume, background audio | Planned |
| 5 | Library, favourites, playlists, history, search screens | Planned |
| 6 | Settings, theme polish, contrast tests | Planned |
| 7 | Hardening: R8, memory, ANR, crash log, TV, permissions | Planned |
| 8 | Tests (unit, Robolectric, emulator CI), evidence, docs, PR + merge | Planned |

Phase 2 is large; screens may land across phases 2–6 as listed above.

## Current state of `rewrite/v2`

The old `com.example` source tree is deleted. New code lives in `app/src/main/java/com/mtplayer/app`
(namespace `com.mtplayer.app`). **The app does not compile yet**: AndroidManifest, resources and
UI layer are still missing.

Written so far (Phase 2):

- `designsystem/theme/` — `Color.kt` ("Tide" palette, contrast-checked), `Contrast.kt` (WCAG
  helpers + `ensure`), `Theme.kt` (dark/light schemes, pure-black, dynamic colour guarded,
  `MtTheme`, `ThemeMode`), `Type.kt` (Manrope variable font, `res/font/manrope.ttf`).
- `core/` — `CrashLog` (local only, path redaction), `DeviceClass` (low-RAM/TV), `Format`, `Permissions`.
- `data/media/` — `Models`, `MediaStoreSource` (IS_MUSIC != 0 AND duration filter), `LibraryIndex`
  (grouping + accent-insensitive search), `LibraryRepository` (state, ContentObserver, hidden folders).
- `data/db/` — Room `mtplayer.db` v1 (`Entities`, `Daos`, `MtDatabase`, exportSchema, no destructive
  fallback) and `LegacyImporter` (read-only import of `melody_player_local.db` + `mt_player_video_prefs`,
  skips seeded fake playlists, one transaction + marker, never deletes old DB).
- `data/prefs/SettingsStore` (DataStore), `data/repo/` (`UserDataRepository`, `Lyrics` + `LrcParser`,
  `LyricsRepository`: linked SAF file, sidecar on API ≤ 29, embedded USLT/Vorbis/MP4).
- `playback/` — `PlaybackService` (MediaLibraryService, notification buttons favourite/repeat/shuffle,
  sleep timer with fade/finish-song, queue persistence + `onPlaybackResumption`, error skip limit,
  audio effects, browse tree), `PlayerConnection` (MediaController, connects on process start),
  `MediaItems`, `AudioEffects`.
- `update/` — `UpdateManager` (public API, SHA256SUMS.txt check, package/versionCode/signer check,
  PackageInstaller), `InstallResultReceiver`.
- `ui/image/VideoFrameFetcher` (Coil fetcher for MediaStore video thumbnails), `MTApp`, `AppContainer`.

## Exact next steps

1. Write `AndroidManifest.xml` (MTApp, MainActivity singleTask + splash theme, VideoPlayerActivity
   with PiP/configChanges, PlaybackService foregroundServiceType=mediaPlayback with
   MediaLibraryService + MediaSessionService intent filters, `androidx.media3.session.MediaButtonReceiver`,
   InstallResultReceiver not exported, leanback launcher + banner, touchscreen not required,
   permissions: READ_MEDIA_AUDIO/VIDEO, READ_MEDIA_VISUAL_USER_SELECTED, READ_EXTERNAL_STORAGE
   maxSdk 32, POST_NOTIFICATIONS, FOREGROUND_SERVICE(_MEDIA_PLAYBACK), WAKE_LOCK, INTERNET,
   REQUEST_INSTALL_PACKAGES).
2. Resources: `values/strings.xml` (all strings referenced: app_name, notification_channel_playback,
   action_favorite/unfavorite/repeat/shuffle, tab_songs, tab_albums, favorites, import_done …),
   `values/themes.xml` + `values-night`, splash theme, adaptive launcher icon (new logo: orange→violet
   squircle, play triangle + waveform) with monochrome layer, `ic_notification`, TV banner,
   `xml/backup_rules.xml`, `xml/data_extraction_rules.xml`, `proguard-rules.pro` for new package.
3. UI layer: `designsystem/components` (MtSnackbarHost, MtDialog, MtBottomSheet, Artwork, list rows,
   empty/loading/error states), `feature/shell/MainActivity` + navigation, mini player with close and
   swipe-dismiss, Home/Music/Videos/Saved tabs, Search, Settings, detail screens, Now Playing.
4. `video/VideoPlayerActivity`.
5. Build locally, fix compile errors, add unit tests (Contrast, LrcParser, LibraryIndex, UpdateManager
   helpers, LegacyImporter with Robolectric, Room schema), push without `[skip ci]`, write
   `docs/rewrite/04-phase2-report.md`.
6. CI: Gradle cache, emulator job (reactivecircus/android-emulator-runner, API 29/34/35), update test
   from v1.3.0. Remove `MT_UPDATER_TOKEN` from `release.yml`. Update README.

## Latest commit

See `git log -1` on `rewrite/v2`. This file is updated in the same commit as the work it describes.
