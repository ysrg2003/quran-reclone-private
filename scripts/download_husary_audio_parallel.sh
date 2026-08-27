#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/app/src/offline/assets/quran/audio/husary"
mkdir -p "$DEST"
CONFIG="$(mktemp)"
trap 'rm -f "$CONFIG"' EXIT
python3 "$ROOT/scripts/list_missing_husary_audio.py" > "$CONFIG"
COUNT=$(wc -l < "$CONFIG")
if [ "$COUNT" -eq 0 ]; then
  echo "missing=0"
  exit 0
fi
printf 'missing=%s\n' "$COUNT"
curl --fail --location --retry 3 --retry-all-errors --connect-timeout 20 --max-time 180 \
  --parallel --parallel-immediate --parallel-max 64 \
  --output-dir "$DEST" --remote-name-all --config "$CONFIG"
printf 'downloaded_missing=done\n'
