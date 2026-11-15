# ADR-006: New Room database with explicit migrations and one-time legacy import

## Status
Accepted

## Context
The old database `melody_player_local.db` (v2) mixes settings, fake seeded playlists and
user data, and uses `fallbackToDestructiveMigration()`.

## Decision
- New database `mtplayer.db`, version 1, schemas exported to `app/schemas` and checked in.
  No destructive fallback. Future changes ship explicit `Migration`s tested with
  `MigrationTestHelper`.
- `LegacyImporter` opens the old file read-only with plain `SQLiteDatabase`, copies
  favourite songs, favourite videos, user playlists (skipping the seeded
  `favorites_default`, `workout_mix`, `chill_vibes` ids when empty), playlist entries,
  listening/watch history, video resume positions and hidden folders, and theme mode.
  Runs inside one transaction, marks completion in DataStore, never deletes the old file.
- Track identity stays the MediaStore `_ID` (same as 1.x), so imported ids match.

## Consequences
Users keep their data. Old file stays as a backup and can be removed in a later release.
