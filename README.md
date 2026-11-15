# MT Player

An offline music and video player for Android phones, tablets and Android TV, built with
Kotlin, Jetpack Compose (Material 3) and AndroidX Media3. Everything stays on the device:
there is no streaming, online search, account or analytics. The only network request is the
optional update check against this repository's public GitHub Releases.

| | |
|---|---|
| Min Android | 9 (API 28) |
| Target / compile | API 36 |
| Language | Kotlin · Java 17 bytecode |
| UI | Jetpack Compose · Material 3 · dark-first theme with light, system and pure-black modes |
| Playback | Media3 ExoPlayer · MediaLibraryService · MediaSession |
| Storage | Room (`mtplayer.db`) · DataStore |
| Build | Gradle wrapper · Android Gradle Plugin · R8 for release |

Version 2 is a complete rewrite of 1.x. It keeps the same application id and signing key, so it
installs as an update and imports 1.x favourites, playlists, history, video resume points,
hidden folders and settings (read-only; the old database is never modified or deleted).

## Features

- **Music**: songs, albums, artists, genres, folders and playlists; now playing with synced or
  plain lyrics (embedded tags, `.lrc` next to the file, or a file you pick); queue editing;
  shuffle and repeat; sleep timer with fade-out or finish-song; playback speed; equalizer, bass
  boost and surround; background playback with a media notification, lock-screen and Bluetooth
  controls; resume after the app is closed.
- **Video**: grid or list, folders, continue watching; dedicated player with brightness,
  volume and seek gestures, double-tap seek, pinch zoom, fit / crop / stretch, rotation lock,
  audio and subtitle tracks, external subtitle files, speed, control lock, picture-in-picture
  and optional background audio.
- **Library**: favourites, recently played, most played, watch history, local search (accent-
  and case-insensitive), hidden folders, minimum audio length filter.
- **Adaptive layout**: bottom navigation on phones, navigation rail on tablets and TV, D-pad
  and media-key support.
- **Updater** (optional): verifies SHA-256, package name, version and signing certificate
  before handing the APK to the system installer. No credentials are embedded.

## Project structure

```
app/src/main/java/com/mtplayer/app/
├── core/            crash log (local only, paths redacted), device class, formatting, permissions
├── data/            MediaStore source + LibraryIndex, Room DB + legacy importer, settings, lyrics
├── designsystem/    theme (palette, contrast guard, typography) and shared components
├── feature/         shell (activity, navigation, mini player), home, music, videos, saved,
│                    search, detail, now playing, equalizer, settings
├── playback/        PlaybackService (MediaLibraryService), PlayerConnection, audio effects
├── update/          UpdateManager, install result receiver
├── ui/image/        video thumbnail fetcher for Coil
└── video/           VideoPlayerActivity + VideoPlayerScreen
app/src/test/        JVM + Robolectric unit tests
app/src/androidTest/ instrumentation tests (run on emulators in CI)
app/schemas/         exported Room schemas (migration fixtures)
docs/rewrite/        audit, research, architecture, ADRs, phase reports, handover
docs/OWNER-MANUAL-TESTS.md  checks that need a physical device
```

## Build

Requirements: JDK 17+ (CI uses Temurin 21) and an Android SDK with platform 36. Always use the
wrapper. Create `local.properties` with `sdk.dir=/path/to/Android/sdk` if Android Studio did not.

```bash
./gradlew assembleDebug          # debug build, applicationId suffix .debug (installs side by side)
./gradlew testDebugUnitTest      # unit + Robolectric tests
./gradlew connectedDebugAndroidTest   # instrumentation tests on a connected device/emulator
./gradlew assembleRelease        # R8 release build (unsigned without the release keystore)
```

## Signing

Android only accepts an update signed with the same key as the installed app, so releases are
always signed with one constant keystore stored in GitHub secrets. CI verifies the certificate
against `.github/release-signing-cert.sha256` and refuses to publish anything else. Without the
credentials a local release build is left unsigned instead of being signed with a throw-away key.

| GitHub secret | Value |
|---|---|
| `MT_KEYSTORE_BASE64` | base64 of the release keystore |
| `MT_KEYSTORE_PASSWORD` | keystore password |
| `MT_KEY_ALIAS` | key alias |
| `MT_KEY_PASSWORD` | key password |

For local release builds copy `keystore.properties.example` to `keystore.properties`
(git-ignored) or export `MT_KEYSTORE_PATH`, `MT_KEYSTORE_PASSWORD`, `MT_KEY_ALIAS`,
`MT_KEY_PASSWORD`. Keep an offline backup of the keystore; if it is lost, existing users can
never be updated again.

The old `MT_UPDATER_TOKEN` secret is no longer used and can be deleted from the repository
settings: a token inside an APK can be extracted by anyone.

## CI and releases

- **CI** (`.github/workflows/ci.yml`, pull requests and non-main branches): wrapper validation,
  debug build, unit tests, lint, then emulator jobs on API 29, 34 and 35 (instrumentation tests,
  permission gate, MediaSession and notification checks via `dumpsys`, queue restore after the
  app is killed) and an update test that installs the published v1.3.0 release, adds 1.x data,
  installs the new release-signed build over it and verifies the import. Screenshots, UI dumps
  and logs are uploaded as artifacts.
- **Release** (`.github/workflows/release.yml`, every push to `main`): computes the version
  from `version.properties` or Conventional Commits, builds and verifies the signed APK, and
  publishes `release-MT-Player-vX.Y.Z.apk` with `SHA256SUMS.txt` and release notes. The R8
  mapping file is kept as a workflow artifact.

## Permissions

| Permission | Why |
|---|---|
| `READ_MEDIA_AUDIO`, `READ_MEDIA_VIDEO`, `READ_MEDIA_VISUAL_USER_SELECTED` | Read local music and videos (Android 13+, partial access on 14+) |
| `READ_EXTERNAL_STORAGE` (max SDK 32) | Same on Android 9–12 |
| `POST_NOTIFICATIONS` | Playback notification (Android 13+) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK` | Background playback |
| `MODIFY_AUDIO_SETTINGS` | Equalizer and effects |
| `INTERNET`, `REQUEST_INSTALL_PACKAGES` | Optional update check and install (the user confirms each install) |

## Contributing

Branch from `main`, use Conventional Commit messages (they drive versioning and release notes),
open a pull request and wait for CI. Merging to `main` publishes a release.
