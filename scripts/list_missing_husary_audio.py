"""Print curl config lines for missing canonical Husary ayah files."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CANONICAL = ROOT / "app/src/main/assets/quran/word_reference_ayahs.json"
DEST = ROOT.parent / "quran-husary-mp3-source"
BASE = "https://audio-cdn.tarteel.ai/quran/husary"
rows = json.loads(CANONICAL.read_text(encoding="utf-8"))["ayahs"]
seen: set[str] = set()
for row in rows:
    name = f"{int(row['surah']):03d}{int(row['ayah']):03d}.mp3"
    if name in seen:
        continue
    seen.add(name)
    target = DEST / name
    if not target.is_file() or target.stat().st_size <= 0:
        print(f'url = "{BASE}/{name}"')
