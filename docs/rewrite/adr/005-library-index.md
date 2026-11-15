# ADR-005: In-memory library index from MediaStore

## Status
Accepted

## Context
MediaStore already indexes every local file. Copying metadata into Room doubles storage
and introduces sync bugs. A 10,000-song library is about 3 MB as compact Kotlin objects.

## Decision
`MediaStoreSource` queries audio and video with projection-only columns on
`Dispatchers.IO`. `LibraryRepository` builds an immutable `LibraryIndex` (songs, albums,
artists, genres, folders, videos, video folders) and publishes it as a `StateFlow`.
A `ContentObserver` triggers a debounced refresh. The last index is not persisted;
MediaStore queries on mid-range devices complete in well under a second.
Audio filter: `IS_MUSIC != 0 AND DURATION >= minDuration` (fixes the old `OR` bug).

## Consequences
Single source of truth, no stale cache. Genre lookup uses the `GENRE` column on API 30+
and the genre members table below.
