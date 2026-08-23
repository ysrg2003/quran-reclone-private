#!/usr/bin/env bash
set -euo pipefail

OUT_DIR="${1:-app/src/main/assets/audio/Alafasy_128kbps}"
JOBS="${JOBS:-8}"
mkdir -p "$OUT_DIR"

# Number of ayahs in the 114 surahs, in canonical order.
COUNTS=(7 286 200 176 120 165 206 75 129 109 123 111 43 52 99 128 111 110 98 135 112 78 118 64 77 227 93 88 69 60 34 30 73 54 45 83 182 88 75 85 54 53 89 59 37 35 38 29 18 45 60 49 62 55 78 96 29 22 24 13 14 11 11 18 12 12 30 52 52 44 28 28 20 56 40 31 50 40 46 42 29 19 36 25 22 17 19 26 30 20 15 21 11 8 8 19 5 8 8 11 11 8 3 9 5 4 7 3 6 3 5 4 5 6)

download_one() {
  local code="$1"
  local target="$OUT_DIR/${code}.mp3"
  if [[ -s "$target" ]]; then
    return 0
  fi
  curl -fL --retry 4 --retry-delay 1 --connect-timeout 20 --max-time 180 \
    "https://everyayah.com/data/Alafasy_128kbps/${code}.mp3" \
    -o "$target.tmp"
  test -s "$target.tmp"
  mv "$target.tmp" "$target"
}
export OUT_DIR
export -f download_one

codes_file="$(mktemp)"
trap 'rm -f "$codes_file"' EXIT
for ((surah=1; surah<=114; surah++)); do
  count="${COUNTS[$((surah-1))]}"
  for ((ayah=1; ayah<=count; ayah++)); do
    printf '%03d%03d\n' "$surah" "$ayah"
  done
done > "$codes_file"

xargs -P "$JOBS" -n 1 bash -c 'download_one "$0"' < "$codes_file"
count="$(find "$OUT_DIR" -maxdepth 1 -name '*.mp3' -type f | wc -l)"
test "$count" -eq 6236
echo "downloaded $count ayah files into $OUT_DIR"
