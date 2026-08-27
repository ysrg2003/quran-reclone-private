"""Verify that the first-render preview is an exact subset of canonical Word JSON."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CANONICAL = ROOT / "app/src/main/assets/quran/word_reference_ayahs.json"
LAYOUT = ROOT / "app/src/main/assets/quran/word_reference_layout.json"
PREVIEW = ROOT / "app/src/main/assets/quran/word_reference_preview.json"
PREVIEW_LAYOUT = ROOT / "app/src/main/assets/quran/word_reference_preview_layout.json"

canonical = json.loads(CANONICAL.read_text(encoding="utf-8"))
layout = json.loads(LAYOUT.read_text(encoding="utf-8"))
preview = json.loads(PREVIEW.read_text(encoding="utf-8"))
preview_layout = json.loads(PREVIEW_LAYOUT.read_text(encoding="utf-8"))

pages = sorted(preview_layout["previewPages"])
assert pages == [1, 2, 3, 4, 5], pages
expected_keys = []
seen = set()
for page in layout["pages"]:
    if page["page"] not in pages:
        continue
    for line in page["lines"]:
        for key in line.get("ayahKeys", []):
            if key not in seen:
                seen.add(key)
                expected_keys.append(key)

canonical_by_key = {f'{row["surah"]}:{row["ayah"]}': row for row in canonical["ayahs"]}
preview_by_key = {f'{row["surah"]}:{row["ayah"]}': row for row in preview["ayahs"]}
assert list(preview_by_key) == expected_keys
assert len(preview_by_key) == 36
for key in expected_keys:
    assert preview_by_key[key] == canonical_by_key[key], key

preview_pages_by_number = {page["page"]: page for page in preview_layout["pages"]}
canonical_pages_by_number = {page["page"]: page for page in layout["pages"]}
for page in pages:
    assert preview_pages_by_number[page] == canonical_pages_by_number[page], page

print(f"word_reference_preview_exact_subset True pages={pages} ayahs={len(expected_keys)}")
