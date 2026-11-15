# ADR-011: Testing strategy

## Status
Accepted

## Decision
- JVM unit tests: parsers, queue logic, contrast, updater verification, version compare.
- Robolectric: Room DAOs, schema, legacy import from a real v2 database fixture,
  Compose screen interactions, theme rendering in light/dark.
- Instrumented tests on GitHub Actions emulators (API 29, 34, 35/36): playback service +
  notification presence, controller commands, process-death restore, PiP entry, permissions
  flow, upgrade install over the v1.3.0 APK where signing allows (debug key differs, so the
  upgrade test uses a debug build of 1.3.0 built from its tag).
- Physical-device-only checks are listed in `docs/OWNER-MANUAL-TESTS.md`.
