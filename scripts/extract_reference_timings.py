#!/usr/bin/env python3
"""Extract all reciter timings from the locked quran-align release."""
from pathlib import Path
import json
import zipfile

project = Path(__file__).resolve().parents[1]
archive = project / "build/quran-align-data/quran-align-data-2016-11-24.zip"
out = project / "app/src/main/assets/timings/Alafasy_128kbps.json"

with zipfile.ZipFile(archive) as bundle:
    data = json.loads(bundle.read("Alafasy_128kbps.json"))

if len(data) != 6236:
    raise SystemExit(f"expected 6236 timing rows, got {len(data)}")
for row in data:
    if not {"surah", "ayah", "segments"}.issubset(row):
        raise SystemExit(f"invalid timing row: {row}")
    for segment in row["segments"]:
        if len(segment) != 4 or segment[0] >= segment[1] or segment[2] < 0 or segment[3] < 0:
            raise SystemExit(f"invalid timing segment in {row['surah']}:{row['ayah']}: {segment}")

out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"wrote {out} with {len(data)} ayahs")
