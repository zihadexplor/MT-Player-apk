# MT Player

A modern, offline music & video player for Android, built with Kotlin, Jetpack Compose and Media3 (ExoPlayer).
MT Player ships with a built-in, signature-verified updater that delivers new versions straight from GitHub Releases.

| | |
|---|---|
| **Min Android** | 9 (API 28) |
| **Target / compile** | Android 17 (API 37) — forward compatible with newer releases |
| **Language** | Kotlin 2.4 · Java 17 bytecode |
| **UI** | Jetpack Compose · Material 3 |
| **Playback** | AndroidX Media3 (ExoPlayer + MediaSession) |
| **Build** | Official Gradle 9.8.2 wrapper · Android Gradle Plugin 9.5 |

---

## Features

- **Music** — local library (songs, albums, artists), mini player, full-screen player, background playback with a media notification and lock-screen controls.
- **Video** — local video library with an immersive full-screen player.
- **Equalizer** — system audio-effect equalizer with presets.
- **In-app updates**
  - Automatic background check on app start (at most once per session, snooze for 24 h with *Remind me later*).
  - Update popup with version, size, date and the latest changes.
  - Animated downloader with **Cancel** and **Download in background**; a notification is posted when the download completes.
  - Before installing, the APK is verified: SHA-256 digest, package name, higher `versionCode`, and **same signing certificate** as the installed app.
  - Works with a **private repository** (token) and falls back to the **public** GitHub URL.
  - Manual check & settings under **Settings → App Updates**.

---

## Project structure

```
.
├── app/
│   ├── build.gradle.kts            # app module: SDK levels, signing, BuildConfig, R8
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/java/com/example/
│       │   ├── MainActivity.kt
│       │   ├── data/               # models + MediaStore repository
│       │   ├── player/             # MusicPlayerManager, equalizer
│       │   ├── service/            # PlaybackService (MediaSessionService)
│       │   ├── ui/                 # Compose screens, components, theme, view-models
│       │   └── update/             # in-app updater (see below)
│       └── test/                   # unit + Robolectric screenshot tests
├── gradle/
│   ├── libs.versions.toml          # version catalog (single place for dependency versions)
│   └── wrapper/                    # official Gradle wrapper (checksum-pinned)
├── version.properties              # VERSION_NAME / VERSION_CODE (single source of truth)
├── keystore.properties.example     # template for local release signing
└── .github/
    ├── workflows/ci.yml            # build + test on PRs and feature branches
    ├── workflows/release.yml       # version bump + signed APK + GitHub Release on main
    ├── scripts/                    # compute-version.sh, release-notes.sh
    └── release-signing-cert.sha256 # expected release certificate fingerprint
```

### Updater package (`com.example.update`)

| File | Responsibility |
|---|---|
| `GitHubReleaseClient.kt` | Fetches the latest release. Uses the API with a token first, falls back to the public API. Resolves private asset downloads. |
| `UpdateManager.kt` | Check / snooze / download state machine (uses `DownloadManager`, survives process death). |
| `ApkInstaller.kt` | Verifies the downloaded APK (hash, package, version, signature) and installs it via `PackageInstaller`. |
| `UpdateNotifier.kt`, `UpdateReceivers.kt` | Download-complete notification and install-result handling. |
| `UpdatePreferences.kt` | Persisted settings (auto-check, snooze, pending download). |
| `ui/UpdateDialog.kt`, `ui/UpdateHost.kt`, `ui/UpdateSettingsSection.kt` | Update popup, downloader animation and settings card. |

---

## Requirements

- JDK 17 or newer (CI uses Temurin 21)
- Android SDK with platform **android-37** and recent build-tools
- No need to install Gradle — always use the wrapper (`./gradlew`)

Create `local.properties` (not committed) if Android Studio didn't:

```properties
sdk.dir=/path/to/Android/sdk
```

## Build

```bash
# Debug build (applicationId suffix .debug, installs next to the release app)
./gradlew assembleDebug

# Unit + screenshot tests
./gradlew testDebugUnitTest

# Signed release build (needs the release keystore, see below)
./gradlew assembleRelease
```

Outputs are written to `app/build/outputs/apk/<variant>/`.

---

## Signing — constant release keystore

Android only accepts an update if it is signed with **the same key** as the installed app.
For that reason this project **never** generates a random or debug key for releases:

- CI restores one fixed keystore from repository secrets and fails the build if it is missing.
- After building, CI compares the APK certificate against `.github/release-signing-cert.sha256` and refuses to publish if it differs.
- Locally, a release build without credentials is produced **unsigned** instead of being signed with a throw-away key.

| GitHub secret | Value |
|---|---|
| `MT_KEYSTORE_BASE64` | `base64 -w0 mtplayer-release.jks` |
| `MT_KEYSTORE_PASSWORD` | keystore password |
| `MT_KEY_ALIAS` | key alias (`mtplayer`) |
| `MT_KEY_PASSWORD` | key password |
| `MT_UPDATER_TOKEN` | *optional* — read-only token for a private repo (see below) |

For local release builds copy `keystore.properties.example` to `keystore.properties` (git-ignored) and fill it in,
or export `MT_KEYSTORE_PATH`, `MT_KEYSTORE_PASSWORD`, `MT_KEY_ALIAS`, `MT_KEY_PASSWORD`.

> **Keep an offline backup of the keystore and its passwords.** If the key is lost, existing users can never receive an update again.
> GitHub secrets cannot be read back once saved.

---

## Versioning & releases

`version.properties` is the single source of truth:

```properties
VERSION_NAME=1.0.0
VERSION_CODE=1
```

Every push to `main` runs **Release** (`.github/workflows/release.yml`):

1. **Compute version** — if you bumped `VERSION_NAME` yourself it is used; otherwise it is bumped automatically from
   [Conventional Commits](https://www.conventionalcommits.org/) since the last tag:
   `feat!:` / `BREAKING CHANGE` → major, `feat:` → minor, anything else → patch.
   `VERSION_CODE` always increases.
2. **Build** the release APK, signed with the constant keystore, and verify the certificate.
3. **Commit** the bumped `version.properties` back to `main` (`[skip ci]`).
4. **Publish** a GitHub Release `vX.Y.Z` with:
   - `release-MT-Player-vX.Y.Z.apk` (the APK itself, not zipped)
   - `SHA256SUMS.txt`
   - Source code `.zip` and `.tar.gz` (added automatically by GitHub)
   - Release notes grouped from the commits since the previous release.

Changes that only touch Markdown/docs do not create a release. A release can also be started manually from
**Actions → Release → Run workflow** with a forced bump type (`patch`, `minor`, `major`).

**CI** (`.github/workflows/ci.yml`) runs on pull requests and non-main branches: wrapper validation, debug build, unit tests, lint and a version dry-run.

### Commit message examples

```
feat(player): add playback speed control      -> minor
fix(equalizer): keep preset after track change -> patch
feat!: drop Android 9 support                 -> major
```

---

## How the updater works

```
App start ──► background check (once per session, skipped if snoozed)
                │
                ├─ token present?  ──► api.github.com (authenticated)  ──┐
                │                      on failure ▼                       │
                └──────────────────► api.github.com (public)  ───────────┤
                                                                          ▼
                              newer versionCode/version?  ──► Update popup
                                                                │
                         Remind me later (24 h) ◄───────────────┤
                                                                ▼
                         Download (DownloadManager) ── Cancel / Download in background
                                                                │  notification when done
                                                                ▼
                 verify SHA-256 + package + versionCode + signing certificate
                                                                ▼
                                       PackageInstaller (system confirmation)
```

- The release notes contain a hidden `<!-- versionCode: N -->` marker that the app reads to compare versions.
- If the user has not yet allowed "Install unknown apps" for MT Player, the app opens the system screen once.

### Going private

When the repository becomes private, the updater needs a token to read releases:

1. Create a **fine-grained personal access token** — Repository access: *only* `MT-Player-apk`; Permissions: **Contents: Read-only**. Nothing else.
2. Save it as the repository secret **`MT_UPDATER_TOKEN`**.
3. Push to `main`; the next release embeds the token and keeps updating normally.

Without the secret the app simply uses the public URL, so it keeps working while the repo is public.

> **Security note:** a token inside an APK can be extracted by anyone who has the APK (it is only lightly obfuscated).
> Use the narrowest token possible (single repo, read-only contents) and rotate it if it leaks.

---

## Permissions

Only what the features need:

| Permission | Why |
|---|---|
| `READ_MEDIA_AUDIO`, `READ_MEDIA_VIDEO` | Show local music and videos (Android 13+) |
| `READ_EXTERNAL_STORAGE` (`maxSdkVersion=32`) | Same, on Android 9 – 12 only |
| `POST_NOTIFICATIONS` | Playback controls and "update downloaded" notification (Android 13+) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK` | Background playback |
| `MODIFY_AUDIO_SETTINGS` | Equalizer |
| `INTERNET` | Checking and downloading updates |
| `REQUEST_INSTALL_PACKAGES` | Installing a downloaded update (user confirms every install) |

Hardening: cleartext HTTP disabled, update files excluded from backups, only the receiver that needs it is exported, R8 shrinking and obfuscation enabled for release.

---

## Contributing

1. Branch from `main` (`feat/...`, `fix/...`).
2. Use Conventional Commit messages — they drive the version bump and release notes.
3. Open a pull request; CI must pass.
4. Merging to `main` publishes a new release automatically.
