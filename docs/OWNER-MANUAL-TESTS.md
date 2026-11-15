# Owner manual tests

Only checks that genuinely need your physical devices, accounts or environment are listed here.
Everything that can run on an emulator is automated in CI (see `.github/workflows/ci.yml` and the
`emulator-evidence-*` / `update-compat-evidence` artifacts of each run). Emulator results are not
treated as physical-device verification.

Use the signed release APK from the GitHub Release (not a debug build) unless a step says otherwise.
Mark each line with the device and Android version you used.

## 1. Update on your own phone with real 1.x data

- [ ] On a phone that has MT Player 1.3.0 with your real favourites and playlists, install the
      v2 release APK over it (via the in-app updater of 1.3.0 or by opening the APK).
- [ ] The app opens without asking to reinstall; your favourite songs, favourite videos and
      playlists (with their order) are present; a "Restored … from the previous version" message
      appears once.
- [ ] The 1.x placeholder playlists ("Workout Energy", "Late Night Chill") do not appear unless
      you had added songs to them.

## 2. Audio output hardware

- [ ] Wired headphones: unplugging pauses playback (setting "Pause when headphones disconnect").
- [ ] Bluetooth headset/car: track title and artist appear; play/pause/next/previous buttons work;
      disconnecting pauses.
- [ ] Equalizer, bass boost and surround audibly change the sound on your device (vendor audio
      effects vary; some devices do not support them).
- [ ] An incoming phone call pauses music and it resumes afterwards (audio focus).

## 3. Notification and lock screen on your OEM build

- [ ] Notification appears with artwork, title, artist and working play/pause, next, previous,
      favourite, shuffle/repeat buttons; it updates when the track changes.
- [ ] Lock-screen controls work.
- [ ] Leave music paused for 10 minutes with the screen off; the notification can be swiped away
      and the app does not keep the phone awake.
- [ ] On aggressive battery-saving OEMs (Xiaomi, Samsung, Oppo, Vivo, Huawei): play music, lock
      the phone for 30 minutes; playback continues.

## 4. Real library scale and performance

- [ ] With your full library (thousands of songs, many videos), first scan finishes and scrolling
      Songs, Albums and Videos is smooth.
- [ ] On a low-RAM phone (2–3 GB) the app does not crash or reload repeatedly when switching
      between music, video player and other apps.
- [ ] 30 minutes of video playback does not make the phone noticeably hot compared to other players.

## 5. Video on real hardware

- [ ] Your own 1080p/4K/HEVC/MKV files play; external `.srt`/`.ass`/`.vtt` subtitles load.
- [ ] Brightness gesture changes screen brightness; volume gesture changes media volume.
- [ ] Picture-in-picture: pressing Home while a video plays shows the PiP window with play/pause,
      rewind and forward; closing the PiP window stops the video.
- [ ] Rotation follows the video aspect and the rotate button.

## 6. Android TV (if you have one)

- [ ] The app appears in the TV launcher with its banner; all screens are reachable with the
      D-pad; the remote's play/pause and media keys work in the music and video players.

## 7. Updater against the real repository

- [ ] After the next release after v2, Settings → Check for updates finds it, downloads it, shows
      "Update ready" (verified) and installs it after the system confirmation.

## 8. Repository settings (owner-only)

- [ ] Delete the obsolete `MT_UPDATER_TOKEN` secret from the repository settings and revoke the
      token on GitHub, since 1.x APKs contained it.
- [ ] Confirm the four signing secrets are still present and that you hold an offline backup of
      the release keystore.
