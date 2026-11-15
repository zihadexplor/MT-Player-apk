# Phase 1 — Competitor and platform research

## References studied

| App | Why it matters | Patterns adopted |
|---|---|---|
| ArchiveTune (inspiration only, not copied) | Material 3 expressive look, album-art driven colour, many player styles | Artwork-tinted player background, large rounded artwork, generous motion. We do **not** adopt online sources, accounts, or plugin packages. Source: <https://github.com/rukamori/ArchiveTune> |
| Gramophone | "Follows Android's standard strictly", Media3 + MediaStore, synced LRC lyrics | MediaStore as the single index, standard `MediaSessionService`, LRC parsing. Source: <https://github.com/FoedusProgramme/Gramophone> |
| Auxio | Offline, fast, reliable library; playback state persistence; gapless; headset autoplay | Persist full queue + index + position; rational defaults; ease of use over edge cases. Source: <https://github.com/OxygenCobalt/Auxio> |
| Next Player | Leading open-source Media3 video player | Left = brightness, right = volume, horizontal seek, pinch zoom, PiP, external subtitles + subtitle delay, speed control. Source: <https://github.com/anilbeesetti/nextplayer> |
| VLC / MX Player (closed reference) | Mass-market expectations | Lock controls, aspect cycling, resume prompt, audio/subtitle track pickers. |

## Platform rules that shaped the design

- Media3 `MediaSessionService` creates and updates the media notification automatically
  once the player has media items; the UI must talk to the session through a
  `MediaController`. `pauseAllPlayersAndStopSelf()` is the supported way to stop on task
  removal. Manifest needs `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` and
  `foregroundServiceType="mediaPlayback"`. On API 33+ System UI renders the notification
  from the session itself. Source: <https://developer.android.com/media/media3/session/background-playback>
- Picture-in-picture: use `setAutoEnterEnabled(true)` on Android 12+, call
  `enterPictureInPictureMode()` from `onUserLeaveHint()` on Android 11 and lower, keep
  `setSourceRectHint` updated, use a dedicated `singleTask` playback activity and handle
  `onNewIntent()`. An active media session supplies play/pause in the PiP menu.
  Source: <https://developer.android.com/develop/ui/views/picture-in-picture>

## What makes the best local players loved

1. **It just plays.** Notification, lock screen, Bluetooth and headset buttons always work.
2. **Nothing is lost.** Queue, position, shuffle order, and favourites survive process death,
   reboots and updates.
3. **Fast library.** Opening the app shows the last library instantly, then refreshes.
4. **Readable everywhere.** Strong contrast in light and dark, big touch targets.
5. **No fake content.** Empty states explain what to do instead of seeded demo data.
6. **Video gestures that feel native** and a PiP window that only shows the video.

## Where MT Player goes further

- One app for music **and** video with a shared, consistent design system.
- Mini player that can be dismissed (button and swipe) and restores on next launch.
- Unified "Saved" hub: favourite songs, favourite videos, playlists, recent, most played,
  continue watching.
- Device-class aware defaults (low-RAM: smaller image cache, no blur; TV: D-pad focus,
  leanback launcher).
- A safe updater with signature verification and no embedded credentials.
