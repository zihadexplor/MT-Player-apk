#!/usr/bin/env bash
# Real update test: install the published v1.3.0 release, give it user data, then install the
# new release-signed build over it with `adb install -r` and verify the data was imported.
# Needs a google_apis (userdebug) emulator image for `adb root`, the v1.3.0 APK in build/v1/
# and the new signed APK at $NEW_APK. Evidence goes to build/emulator-evidence/update/.
set -euo pipefail
OUT=build/emulator-evidence/update
mkdir -p "$OUT"
PKG=com.aistudio.mtplayer.kmzv
OLD_APK=$(ls build/v1/*.apk | head -1)
NEW_APK=${NEW_APK:?}
DATA=/data/data/$PKG/databases
log() { echo "== $*" | tee -a "$OUT/summary.txt"; }
fail() { log "FAIL: $*"; exit 1; }
sql() { adb shell "sqlite3 $1 \"$2\""; }

adb root >/dev/null; sleep 3; adb wait-for-device
adb uninstall "$PKG" >/dev/null 2>&1 || true
adb install "$OLD_APK" >/dev/null
adb shell dumpsys package "$PKG" | grep -E "versionCode|versionName" | head -2 | tee -a "$OUT/summary.txt"
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 10
adb shell am force-stop "$PKG"

if adb shell ls "$DATA/melody_player_local.db" >/dev/null 2>&1; then
  log "v1.3.0 created its database"
else
  fail "v1.3.0 did not create melody_player_local.db"
fi
# User data as v1 stores it (ids are MediaStore ids kept as TEXT).
sql "$DATA/melody_player_local.db" "INSERT OR REPLACE INTO favorite_songs VALUES('101', 1000); INSERT OR REPLACE INTO favorite_songs VALUES('102', 2000); INSERT OR REPLACE INTO favorite_videos VALUES('501', 3000); INSERT OR REPLACE INTO custom_playlists VALUES('u_ci','CI Road trip', 5); INSERT OR REPLACE INTO playlist_song_cross_ref VALUES('u_ci','102',0,5); INSERT OR REPLACE INTO playlist_song_cross_ref VALUES('u_ci','101',1,5); INSERT OR REPLACE INTO last_video_playback_position VALUES('501',30000,60000,6);"
sql "$DATA/melody_player_local.db" "SELECT 'v1 favourites', COUNT(*) FROM favorite_songs; SELECT 'v1 playlists', id, name FROM custom_playlists;" | tee "$OUT/v1-data.txt"
OLD_SUM=$(adb shell md5sum "$DATA/melody_player_local.db" | awk '{print $1}')

# The update itself: same package, same signing key, higher versionCode.
adb install -r "$NEW_APK" 2>&1 | tee "$OUT/install.txt"
grep -q Success "$OUT/install.txt" || fail "update install rejected"
adb shell dumpsys package "$PKG" | grep -E "versionCode|versionName" | head -2 | tee -a "$OUT/summary.txt"
log "PASS v2 installed over v1.3.0 as an update"

adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 10
adb exec-out screencap -p > "$OUT/after-update.png"
sql "$DATA/mtplayer.db" "SELECT 'favourites', media_type, media_id FROM favorites ORDER BY media_type, media_id; SELECT 'playlist', name FROM playlists; SELECT 'items', song_id, position FROM playlist_items ORDER BY position; SELECT 'progress', video_id, position_ms FROM video_progress; SELECT 'marker', key FROM meta;" | tee "$OUT/v2-data.txt"

grep -q "favourites|song|101" "$OUT/v2-data.txt" || fail "favourite song 101 missing"
grep -q "favourites|song|102" "$OUT/v2-data.txt" || fail "favourite song 102 missing"
grep -q "favourites|video|501" "$OUT/v2-data.txt" || fail "favourite video missing"
grep -q "playlist|CI Road trip" "$OUT/v2-data.txt" || fail "playlist missing"
grep -q "items|102|0" "$OUT/v2-data.txt" && grep -q "items|101|1" "$OUT/v2-data.txt" || fail "playlist order wrong"
grep -q "progress|501|30000" "$OUT/v2-data.txt" || fail "video resume point missing"
grep -q "marker|legacy_import" "$OUT/v2-data.txt" || fail "import marker missing"
grep -Eq "playlist\|(Workout Energy|Late Night Chill|Favorites)" "$OUT/v2-data.txt" && fail "v1 placeholder playlists were imported"
NEW_SUM=$(adb shell md5sum "$DATA/melody_player_local.db" | awk '{print $1}')
[ "$OLD_SUM" = "$NEW_SUM" ] || fail "v1 database was modified"
log "PASS favourites, playlist order, resume point imported; placeholders skipped; v1 DB untouched"

# Second launch must not duplicate anything.
adb shell am force-stop "$PKG"; adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 6
COUNT=$(sql "$DATA/mtplayer.db" "SELECT COUNT(*) FROM favorites;" | tr -d '\r')
[ "$COUNT" = "3" ] || fail "favourites duplicated on relaunch ($COUNT)"
log "PASS import is idempotent"
adb logcat -d | grep -E "FATAL EXCEPTION" -A20 > "$OUT/crashes.txt" || true
[ -s "$OUT/crashes.txt" ] && fail "crash after update (see crashes.txt)"
log "PASS no crash after update"
