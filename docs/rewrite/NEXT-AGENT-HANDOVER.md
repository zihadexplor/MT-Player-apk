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
  `MT_UPDATER_TOKEN` is obsolete; release.yml no longer references it.
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
| 2 | Full rebuild: design system, data, DB + legacy import, playback, updater, shell, all screens, video player, logo, unit tests | Done (`bf96544`, report `04-phase2-report.md`) |
| 3 | Emulator CI (API 29/34/35): instrumentation tests (UI, MediaSession/notification, PiP, resume), shell checks (permission gate, UI playback via taps, process-death queue restore, theme screenshots, logcat), install-over-v1.3.0 release-signed data migration | In progress: update-compat job green; emulator jobs found a real crash (UPSERT on API 29) fixed in `86281f1`, re-running |
| 4 | Review and fix pass per feature from emulator evidence (music, video, library, settings, TV/rail layout) | Planned |
| 5 | Hardening: lint clean + blocking (done), StrictMode log-only in debug (done), memory/ANR review, low-RAM paths | In progress |
| 6 | Docs (README done, OWNER-MANUAL-TESTS done, phase reports), PR `rewrite/v2 -> main`, CI green, merge | Planned |

The phase table was re-scoped after Phase 1: screens originally split across phases 3–6 were
built together in Phase 2 because they share one design system and navigation shell.

## Current state of `rewrite/v2`

The old `com.example` tree is deleted. All code is new in `app/src/main/java/com/mtplayer/app`
(namespace `com.mtplayer.app`, applicationId unchanged). `assembleDebug` and `testDebugUnitTest`
pass locally.

- `designsystem/theme` — Tide palette, `Contrast` (WCAG; `ensure` tries both directions),
  `Theme` (dark/light/pure-black, guarded dynamic colour, `resolveDark`, `MtTheme`), `Type`.
- `designsystem/components` — Artwork, States (loading/message), Feedback (Messenger snackbar,
  dialogs, action/choice sheets), Rows (MediaRow, VideoCard, TileCard, PlayingBars…).
- `core`, `data` (MediaStore → `LibraryIndex`, Room `mtplayer.db` v1 with exported schema in
  `app/schemas`, `LegacyImporter`, DataStore settings, lyrics), `playback` (MediaLibraryService +
  notification + sleep timer + queue persistence), `update` (token-free, SHA-256 + signer check).
- `feature/shell` — MainActivity (splash, edge-to-edge, audio VIEW intents), AppRoot (bottom bar /
  rail ≥600dp, mini player with close + swipe dismiss, permission gate), Navigation, MediaActions
  (song/video sheets, add-to-playlist, favourites with undo).
- `feature/*` — Home, Music (6 tabs), Videos (grid/list, folders), Saved, Search, detail screens,
  Settings (+ hidden folders, crash reports), UpdatePrompt, Equalizer, Now Playing (lyrics, queue,
  sleep, speed).
- `video/` — VideoPlayerActivity (own ExoPlayer + MediaSession id `mt_video`, resume, PiP with
  remote actions/auto-enter, background audio hand-off, subtitles via SAF) and VideoPlayerScreen
  (gestures, fit modes, lock, track/speed sheets).
- Tests in `app/src/test`: ThemeContrastTest, LrcParserTest, LibraryIndexTest (Robolectric),
  UpdateManagerTest, CrashLogTest, LegacyImporterTest (Robolectric, synthetic v1 DB).

## Exact next steps

1. Check the latest CI run: `gh run list --branch rewrite/v2 --limit 3`, then
   `gh run view <id> --json jobs -q '.jobs[] | "\(.name) \(.status) \(.conclusion)"'`.
   On failure: `gh run view <id> --log-failed`, and `gh run download <id> -n emulator-evidence-api<N>`
   (summary.txt, screenshots, uiautomator XML, dumpsys media_session/notification, logcat,
   instrumentation results). Fix and push. A non-`[skip ci]` push cancels the running CI.
2. When all CI jobs are green, write `docs/rewrite/05-phase3-report.md` with the evidence.
3. Phase 4 review pass, Phase 5 memory/ANR review, Phase 6 PR + merge.

## CI findings so far (Phase 3)

- Run 43726937809: failed in build only because Maven Central returned HTTP 429 (transient).
- Run 43727256725: build/unit/lint green; **update-compat green** (v1.3.0 → 2.0.0 release-signed
  `adb install -r`, v1 favourites/playlists/progress imported, v1 DB untouched, idempotent).
  Emulator jobs failed:
  - API 29: `INSERT ... ON CONFLICT DO UPDATE` unsupported by SQLite 3.22 (Android 9/10) → crash
    on every play. Fixed: insert-or-ignore + update in a transaction; history is best-effort.
  - `PlaybackSessionTest` line 88 asserted `COMMAND_SEEK_TO_NEXT_MEDIA_ITEM` on the last item (test
    bug). Notification checks before it (title, ≥3 actions, MediaStyle session, update on track
    change, stays while paused) passed on API 29 and 35.
  - API 35: PiP entered, `ui.inPip` asserted before `onPictureInPictureModeChanged` (race in test).
  - API 34: "Pixel Launcher isn't responding" system dialog covered the permission gate; the script
    now dismisses foreign ANR dialogs with "Wait" (screenshot kept as `*-system-anr-dialog.png`).
  - API 35 shell checks passed: session PLAYING with metadata, notification posted, queue restored
    after force-stop, no crash/ANR in logcat.

## Latest commit

See `git log -1` on `rewrite/v2`. This file is updated in the same commit as the work it describes.
