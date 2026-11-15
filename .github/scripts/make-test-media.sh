#!/usr/bin/env bash
# Generates small, license-free test media for emulator tests: two 40 s WAV tones and a 20 s
# H.264/AAC clip. Output: build/test-media/
set -euo pipefail
OUT=build/test-media
mkdir -p "$OUT"
python3 - "$OUT" <<'PY'
import math, struct, sys, wave
out = sys.argv[1]
for name, hz in (("mt_ci_tone_one", 440.0), ("mt_ci_tone_two", 660.0)):
    with wave.open(f"{out}/{name}.wav", "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(8000)
        w.writeframes(b"".join(struct.pack("<h", int(6000 * math.sin(2 * math.pi * hz * i / 8000))) for i in range(8000 * 40)))
PY
if ! command -v ffmpeg >/dev/null; then
  sudo apt-get update -qq && sudo apt-get install -y -qq ffmpeg >/dev/null
fi
ffmpeg -loglevel error -y -f lavfi -i testsrc=duration=20:size=640x360:rate=24 \
  -f lavfi -i sine=frequency=500:duration=20 -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest \
  "$OUT/mt_ci_clip.mp4"
ls -l "$OUT"
