# Architecture Decision Records

| ADR | Title | Status |
|---|---|---|
| [001](001-rewrite-in-place.md) | Rewrite in place, keep application id and signing | Accepted |
| [002](002-single-module-manual-di.md) | Single module, package-by-feature, manual DI | Accepted |
| [003](003-media3-session-controller.md) | Media3 MediaLibraryService + MediaController for all music playback | Accepted |
| [004](004-dedicated-video-activity.md) | Dedicated singleTask VideoPlayerActivity with its own session | Accepted |
| [005](005-library-index.md) | In-memory library index from MediaStore, no metadata cache table | Accepted |
| [006](006-new-database-legacy-import.md) | New Room database with explicit migrations and one-time legacy import | Accepted |
| [007](007-settings-datastore.md) | Settings in DataStore Preferences | Accepted |
| [008](008-design-system.md) | New design system "Tide": tokens, contrast-checked themes, dynamic colour opt-in | Accepted |
| [009](009-updater-no-token.md) | Keep the updater, remove embedded token | Accepted |
| [010](010-device-classes.md) | Device-class aware behaviour (low-RAM, TV) | Accepted |
| [011](011-testing-strategy.md) | Testing strategy: JVM + Robolectric + emulator on CI | Accepted |
