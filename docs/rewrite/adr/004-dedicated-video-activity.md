# ADR-004: Dedicated singleTask VideoPlayerActivity with its own session

## Status
Accepted

## Context
The old video player was a Compose overlay inside `MainActivity`; PiP shrank the whole app,
and orientation changes affected the library UI.

## Decision
`VideoPlayerActivity` (`singleTask`, `supportsPictureInPicture`, `resizeableActivity`,
handles its own config changes). It owns a video ExoPlayer and a `MediaSession` for
PiP/headset controls. PiP: `setAutoEnterEnabled` on API 31+, `onUserLeaveHint` fallback on
API 28–30, source-rect hint from the video surface, play/pause remote action, controls
hidden in PiP. Orientation follows the video aspect ratio by default with a user override.
Starting a video pauses music via audio focus; the music session is untouched.

## Alternatives considered
- Run video through the music service: complex surface handover, mixes notification types.

## Consequences
Clean PiP, independent lifecycle, simpler state restoration via `SavedStateHandle` and
the per-video resume table.
