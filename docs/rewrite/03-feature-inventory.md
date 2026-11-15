# Feature inventory — 1.x to 2.0

Every user-visible feature found in the 1.x code and APK, and what 2.0 does with it.

| 1.x feature (source) | 2.0 | Notes |
|---|---|---|
| Splash screen (`SplashScreen.kt`) | Replaced | AndroidX SplashScreen API, no artificial delay |
| Home: device music/video counts, recently & most played, quick navigation | Rebuilt | Home shows continue listening/watching, recent, most played, quick shuffle |
| Music: songs, albums, artists, folders, genres + detail pages, play all | Rebuilt | Sort options persisted, fast scroll |
| Library: playlists CRUD, recently/most played, storage breakdown | Rebuilt as "Saved" | Favourites songs + videos, playlists, recent, most played; storage breakdown moved to Settings › Library |
| Fake seeded playlists | **Removed** | Never seeded; legacy empty seeds are not imported |
| Now Playing: artwork, seek, controls, shuffle/repeat, lyrics, queue, sleep timer, speed | Rebuilt | Plus favourite, add to playlist, share-free, artwork tint |
| Mini player | Rebuilt | Adds close button and swipe-down/sideways dismiss |
| Queue: reorder, remove, play next, add to queue | Rebuilt | Drag handle reorder, swipe remove, persisted |
| Sleep timer: duration, finish last song, fade out | Rebuilt | Runs inside the service, survives UI death |
| Crossfade / gapless / auto-pause on disconnect | Rebuilt | Gapless native in ExoPlayer; crossfade dropped (see note) |
| Equalizer, bass boost, virtualizer, presets | Rebuilt | Attached to service audio session; disabled gracefully if unsupported |
| Lyrics: `.lrc`, `.txt`, embedded ID3 USLT | Rebuilt | Synced highlight + tap-to-seek |
| Video: list/grid, folders, favourites, continue watching, hidden folders, sort, rescan | Rebuilt | Grid is a single lazy grid with header spans; layout choice persisted |
| Video player: gestures (brightness, volume, scrub), double-tap seek, lock, speed, aspect, orientation, audio track, subtitle track, external subtitles, info dialog, PiP, resume | Rebuilt | Dedicated activity, pinch zoom, subtitle delay, true PiP |
| Background video audio | Kept as setting | Video continues as audio when leaving the player (not PiP) only if enabled |
| Settings: theme mode, accent, AMOLED, dynamic artwork colour, playback, video gestures, scanning, min duration, history clear, updates, about, crash log | Rebuilt | DataStore-backed; crash log viewer with copy |
| In-app updater | Rebuilt | No token, verified install |
| Crash logger | Rebuilt | Ring buffer, user-visible, path redaction |

Note on crossfade: ExoPlayer has no native crossfade between items; 1.x faked it with a
volume ramp after the transition, which cut the start of each song. 2.0 replaces it with
an optional short fade-in on manual skips only, clearly labelled.
