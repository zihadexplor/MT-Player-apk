#!/usr/bin/env bash
# Emulator test suite. Run inside reactivecircus/android-emulator-runner (one script, because the
# runner executes each line of its `script:` separately). Evidence is written to
# build/emulator-evidence/api<level>/ and uploaded as a workflow artifact.
set -euo pipefail
API=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
OUT="build/emulator-evidence/api$API"
mkdir -p "$OUT"
APK=app/build/outputs/apk/debug/app-debug.apk
PKG=com.aistudio.mtplayer.kmzv.debug
MAIN="$PKG/com.mtplayer.app.feature.shell.MainActivity"
UI=.github/scripts/ui.py

log() { echo "== $*" | tee -a "$OUT/summary.txt"; }
dump() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true; adb pull /sdcard/ui.xml "$OUT/$1.xml" >/dev/null; adb exec-out screencap -p > "$OUT/$1.png"; }
tap() { local xy; xy=$(python3 "$UI" tap "$OUT/$1.xml" "$2") || { log "FAIL: '$2' not found in $1"; return 1; }; adb shell input tap $xy; }
fail() { log "FAIL: $*"; exit 1; }

log "API $API"
adb logcat -c || true

# --- Test media ------------------------------------------------------------------------------
adb shell mkdir -p /sdcard/Music /sdcard/Movies
adb push build/test-media/mt_ci_tone_one.wav build/test-media/mt_ci_tone_two.wav /sdcard/Music/ >/dev/null
adb push build/test-media/mt_ci_clip.mp4 /sdcard/Movies/ >/dev/null
for f in /sdcard/Music/mt_ci_tone_one.wav /sdcard/Music/mt_ci_tone_two.wav /sdcard/Movies/mt_ci_clip.mp4; do
  adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file://$f" >/dev/null 2>&1 || true
done
adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1 || true
for i in $(seq 1 30); do
  if adb shell content query --uri content://media/external/audio/media --projection title | grep -q mt_ci_tone_two \
     && adb shell content query --uri content://media/external/video/media --projection title | grep -q mt_ci_clip; then break; fi
  sleep 2
done
adb shell content query --uri content://media/external/audio/media --projection title:duration > "$OUT/mediastore-audio.txt" || true
adb shell content query --uri content://media/external/video/media --projection title:duration > "$OUT/mediastore-video.txt" || true
grep -q mt_ci_tone_one "$OUT/mediastore-audio.txt" || fail "test audio not indexed"
log "media indexed"

# --- 1. Fresh install without permissions: the permission gate must be shown ------------------
adb uninstall "$PKG" >/dev/null 2>&1 || true
adb install "$APK" >/dev/null
adb shell am start -W -n "$MAIN" >/dev/null
sleep 6
dump first-launch
if python3 "$UI" has "$OUT/first-launch.xml" "Allow access to your media" || grep -q permissioncontroller "$OUT/first-launch.xml"; then
  log "PASS permission gate / system dialog shown on fresh install"
else
  fail "no permission gate on fresh install"
fi
adb shell am force-stop "$PKG"

# --- 2. Instrumentation tests (Compose UI, MediaSession + notification, video + PiP) -----------
set +e
./gradlew --no-configuration-cache connectedDebugAndroidTest
RC=$?
set -e
mkdir -p "$OUT/instrumentation"
cp -r app/build/outputs/androidTest-results/connected/* "$OUT/instrumentation/" 2>/dev/null || true
[ $RC -eq 0 ] && log "PASS instrumentation tests" || log "FAIL instrumentation tests (rc=$RC)"

# --- 3. Play from the UI, check session + notification, kill the app, check resume -------------
adb install -r -g "$APK" >/dev/null
[ "$API" -ge 33 ] && adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true
adb shell am start -W -n "$MAIN" >/dev/null
sleep 6
dump home
tap home "Music"
sleep 3
dump music
tap music "mt_ci_tone_one"
sleep 5
dump playing
adb shell dumpsys media_session > "$OUT/media_session.txt"
adb shell dumpsys notification --noredact > "$OUT/notification.txt" 2>/dev/null || adb shell dumpsys notification > "$OUT/notification.txt"
grep -q "$PKG" "$OUT/media_session.txt" || fail "no media session for $PKG"
grep -q "mt_ci_tone_one" "$OUT/media_session.txt" || fail "session metadata missing title"
grep -Eq "PlaybackState \{state=(3|PLAYING)" "$OUT/media_session.txt" && log "PASS session is PLAYING with metadata" || fail "session not playing"
grep -q "pkg=$PKG" "$OUT/notification.txt" && log "PASS media notification posted" || fail "no notification"
sleep 4  # let the debounced queue persistence run
adb shell am force-stop "$PKG"
sleep 2
adb shell am start -W -n "$MAIN" >/dev/null
sleep 6
dump after-kill
if python3 "$UI" has "$OUT/after-kill.xml" "mt_ci_tone_one" && python3 "$UI" has "$OUT/after-kill.xml" "Stop and close player"; then
  log "PASS queue restored after app was killed (mini player shows last song)"
else
  fail "queue not restored after kill"
fi

# --- 4. Theme: switch to light mode in system settings, app must follow -------------------------
adb shell cmd uimode night yes >/dev/null 2>&1 || true
sleep 3; dump night
adb shell cmd uimode night no >/dev/null 2>&1 || true
sleep 3; dump day
log "theme screenshots captured (night.png / day.png)"

adb logcat -d > "$OUT/logcat.txt" || true
if grep -E "FATAL EXCEPTION|ANR in $PKG" "$OUT/logcat.txt" | grep -q .; then fail "crash or ANR in logcat"; fi
log "PASS no crash / ANR in logcat"
[ $RC -eq 0 ] || exit $RC
