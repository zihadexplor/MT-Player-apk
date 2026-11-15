# ADR-003: Media3 MediaLibraryService + MediaController for all music playback

## Status
Accepted

## Context
The old app manipulated a static ExoPlayer directly, so no controller connected to the
session and the notification never appeared reliably (Phase 0 §3.1).

## Decision
- `PlaybackService : MediaLibraryService` owns the only music ExoPlayer and MediaSession.
- UI connects through a `MediaController` wrapped in `PlayerConnection`, which exposes
  `StateFlow`s (current item, isPlaying, position, queue, modes).
- The service uses Media3's built-in notification provider with a custom small icon and
  custom session commands (shuffle, repeat, favourite, close).
- `onPlaybackResumption` restores the persisted queue so System UI and headset play
  buttons can resume after process death.
- `onTaskRemoved` calls `pauseAllPlayersAndStopSelf()` only when nothing is playing.
- Audio focus handled by ExoPlayer (`handleAudioFocus = true`), becoming-noisy enabled.

## Alternatives considered
- Custom `NotificationCompat.MediaStyle` + foreground service: more code, more bugs across
  API levels.
- Keep `MediaSessionService`: `MediaLibraryService` adds browse support for Android Auto
  and Bluetooth head units at near-zero cost.

## Consequences
Notification, lock screen, Bluetooth, Wear and Auto all follow standard behaviour. All UI
calls are asynchronous through the controller.
