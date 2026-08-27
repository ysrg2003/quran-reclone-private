#!/usr/bin/env python3
"""Validate the current full Quran Word, Husary timing, and source MP3 assets."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[1]
TEXT_PATH = PROJECT / "app/src/main/assets/quran/word_reference_ayahs.json"
TIMING_PATH = PROJECT / "app/src/main/assets/quran/audio_timing/Husary_Murattal.json"
DEFAULT_AUDIO_DIR = PROJECT.parent / "quran-husary-mp3-source"
if not DEFAULT_AUDIO_DIR.is_dir():
    DEFAULT_AUDIO_DIR = PROJECT / "app/src/offline/assets/quran/audio/husary"

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--mp3-dir", type=Path, default=DEFAULT_AUDIO_DIR)
args = parser.parse_args()
AUDIO_DIR = args.mp3_dir

text = json.loads(TEXT_PATH.read_text(encoding="utf-8"))
timing_payload = json.loads(TIMING_PATH.read_text(encoding="utf-8"))
ayahs = {(int(item["surah"]), int(item["ayah"])): item for item in text["ayahs"]}
timing_rows = {
    (int(item["surah"]), int(item["ayah"])): item
    for item in timing_payload["ayahs"]
}

if len(ayahs) != 6236:
    raise SystemExit(f"expected 6236 Quran ayahs, found {len(ayahs)}")
if len(timing_rows) != 6236:
    raise SystemExit(f"expected 6236 timing rows, found {len(timing_rows)}")
if set(timing_rows) != set(ayahs):
    missing = sorted(set(ayahs) - set(timing_rows))[:10]
    extra = sorted(set(timing_rows) - set(ayahs))[:10]
    raise SystemExit(f"timing/text keys differ; missing={missing}; extra={extra}")

missing_audio: list[str] = []
empty_audio: list[str] = []
for surah, ayah in sorted(ayahs):
    path = AUDIO_DIR / f"{surah:03d}{ayah:03d}.mp3"
    if not path.is_file():
        missing_audio.append(path.name)
    elif path.stat().st_size == 0:
        empty_audio.append(path.name)
if missing_audio or empty_audio:
    raise SystemExit(f"audio invalid; missing={missing_audio[:10]}; empty={empty_audio[:10]}")

bad_segments = []
reversed_segments = 0
for key, row in timing_rows.items():
    for segment in row.get("segments", []):
        if len(segment) != 4 or segment[0] >= segment[1] or segment[2] < 0 or segment[3] < 0:
            bad_segments.append((key, segment))
        if len(segment) == 4 and segment[2] > segment[3]:
            reversed_segments += 1
if bad_segments:
    raise SystemExit(f"invalid timing segments: {bad_segments[:5]}")

print(f"quran-ayahs-ok={len(ayahs)}")
print(f"reversed-timing-segments={reversed_segments}")
print(f"timing-rows-ok={len(timing_rows)}")
print(f"audio-files-ok={len(ayahs)}")
print(f"audio-dir={AUDIO_DIR}")
print(f"audio-bytes={sum((AUDIO_DIR / f'{s:03d}{a:03d}.mp3').stat().st_size for s, a in ayahs):,}")
print("full-quran-husary-assets-ok")
