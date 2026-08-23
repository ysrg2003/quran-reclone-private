#!/usr/bin/env python3
"""Validate full Quran text, quran-align timings, and EveryAyah audio filenames."""
from pathlib import Path
import json

project = Path(__file__).resolve().parents[1]
text_path = project / "app/src/main/assets/quran/quran_content.json"
timing_path = project / "app/src/main/assets/timings/Alafasy_128kbps.json"
audio_dir = project / "app/src/main/assets/audio/Alafasy_128kbps"

text = json.loads(text_path.read_text(encoding="utf-8"))
ayahs = {(item["surah"], item["ayah"]): item for item in text["ayahs"]}
timings = json.loads(timing_path.read_text(encoding="utf-8"))
timing_keys = {(item["surah"], item["ayah"]): item for item in timings}

if len(ayahs) != 6236:
    raise SystemExit(f"expected 6236 Quran ayahs, found {len(ayahs)}")
if len(timing_keys) != 6236:
    raise SystemExit(f"expected 6236 timing rows, found {len(timing_keys)}")
timing_key_set = set(timing_keys)
if timing_key_set != set(ayahs):
    missing = sorted(set(ayahs) - timing_key_set)[:10]
    extra = sorted(timing_key_set - set(ayahs))[:10]
    raise SystemExit(f"timing/text keys differ; missing={missing}; extra={extra}")

missing_audio = []
empty_audio = []
for surah, ayah in sorted(ayahs):
    path = audio_dir / f"{surah:03d}{ayah:03d}.mp3"
    if not path.exists():
        missing_audio.append(str(path.name))
    elif path.stat().st_size == 0:
        empty_audio.append(str(path.name))
if missing_audio or empty_audio:
    raise SystemExit(f"audio invalid; missing={missing_audio[:10]}; empty={empty_audio[:10]}")

bad_segments = []
reversed_segments = 0
for key, row in timing_keys.items():
    for segment in row.get("segments", []):
        if len(segment) != 4 or segment[0] >= segment[1] or segment[2] < 0 or segment[3] < 0:
            bad_segments.append((key, segment))
        if len(segment) == 4 and segment[2] > segment[3]:
            reversed_segments += 1
if bad_segments:
    raise SystemExit(f"invalid timing segments: {bad_segments[:5]}")

print(f"quran-ayahs-ok={len(ayahs)}")
print(f"reversed-timing-segments-clamped-at-runtime={reversed_segments}")
print(f"timing-rows-ok={len(timing_keys)}")
print(f"audio-files-ok={len(ayahs)}")
print(f"audio-bytes={sum((audio_dir / f'{s:03d}{a:03d}.mp3').stat().st_size for s, a in ayahs):,}")
print("full-quran-assets-ok")
