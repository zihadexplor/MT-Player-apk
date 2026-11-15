# ADR-010: Device-class aware behaviour

## Status
Accepted

## Decision
`DeviceClass` is computed once from `ActivityManager.isLowRamDevice`, memory class and
`UiModeManager` (TV):
- Low-RAM: image cache 12 % heap, 256 px artwork decode in lists, no blur/animated
  backgrounds, smaller ExoPlayer back buffer.
- TV: leanback launcher entry and banner, `touchscreen` not required, D-pad focus
  indication on all clickable components, video opens in landscape.
- `onTrimMemory` clears image memory cache and lyric/subtitle caches.
