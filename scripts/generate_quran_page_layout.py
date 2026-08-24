from __future__ import annotations

import json
import os
import re
from collections import defaultdict
from pathlib import Path

# Quran-PNG/quran.com-images is the primary reference. The old offline checkout
# is deliberately not modified and is no longer the default input.
SQL = Path(os.environ.get("QURAN_IMAGES_SQL", "/home/ubuntu/quran-source-cache/quran.com-images/sql/02-database.sql"))
OUTPUT = Path(os.environ.get("QURAN_LAYOUT_OUTPUT", "/home/ubuntu/quran-memorization-assets/quran/quran_page_layout.json"))


def payloads(text: str, table: str) -> list[str]:
    pattern = rf"INSERT INTO\s+(?:`?nextgen`?\.)?`?{table}`?\s*.*?VALUES\s*(.*?);"
    return [match.group(1) for match in re.finditer(pattern, text, re.IGNORECASE | re.DOTALL)]


text = SQL.read_text(encoding="utf-8", errors="replace")
word_rows = re.findall(
    r"\((\d+),(\d+),(\d+),(\d+),(\d+),(\d+),(\d+),(?:NULL|\d+),(?:NULL|\d+),(?:NULL|\d+)\)",
    "\n".join(payloads(text, "word")),
)
glyph_rows = re.findall(
    r"\((\d+),'([^']+)',(\d+),(\d+),(?:NULL|\d+),(?:NULL|\d+),(?:NULL|'[^']*')\)",
    "\n".join(payloads(text, "glyph")),
)
line_rows = re.findall(
    r"\((\d+),(\d+),(\d+),(\d+),(\d+),'([^']+)'\)",
    "\n".join(payloads(text, "glyph_page_line")),
)
glyph_by_id = {int(row[0]): {"fontFile": row[1], "glyphCode": int(row[2]), "pageNumber": int(row[3])} for row in glyph_rows}

words_by_glyph = {
    int(row[1]): {"surah": int(row[3]), "ayah": int(row[4]), "word": int(row[5])}
    for row in word_rows
}
pages: dict[int, dict[int, dict[str, object]]] = defaultdict(dict)
missing_word_glyphs: list[int] = []
layout_word_keys: list[str] = []
for row in line_rows:
    _, glyph_id_text, page_text, line_text, position_text, line_type = row
    glyph_id = int(glyph_id_text)
    page = int(page_text)
    line = int(line_text)
    position = int(position_text)
    line_entry = pages[page].setdefault(line, {"number": line, "type": line_type, "items": []})
    if line_entry["type"] != line_type:
        raise ValueError(f"line type changed for page={page}, line={line}")
    source_glyph = glyph_by_id.get(glyph_id, {})
    word = words_by_glyph.get(glyph_id)
    if word is None:
        line_entry["items"].append({"position": position, "type": line_type, "glyphId": glyph_id, "glyphCode": source_glyph.get("glyphCode"), "fontFile": source_glyph.get("fontFile")})
        continue
    key = f"{word['surah']}:{word['ayah']}:{word['word']}"
    line_entry["items"].append({"position": position, "type": "word", "wordKey": key, "glyphId": glyph_id, "glyphCode": source_glyph.get("glyphCode"), "fontFile": source_glyph.get("fontFile")})
    layout_word_keys.append(key)
    
if len(word_rows) != 77430:
    raise ValueError(f"unexpected word row count: {len(word_rows)}")
if len(set(layout_word_keys)) != len(layout_word_keys):
    raise ValueError("a word appears more than once in page layout")
if len(layout_word_keys) != len(word_rows):
    raise ValueError(f"layout word count {len(layout_word_keys)} != source word count {len(word_rows)}")

page_output = []
for page in range(1, 605):
    lines_output = []
    for line in sorted(pages[page]):
        entry = pages[page][line]
        # Page.pm orders glyphs by position DESC for RTL. Keep the lossless
        # source items so future renderers cannot silently discard geometry.
        items = sorted(entry["items"], key=lambda item: item["position"], reverse=True)
        words = [item["wordKey"] for item in items if item["type"] == "word"]
        markers = [
            {"position": item["position"], "type": item["type"], "glyphId": item.get("glyphId"), "glyphCode": item.get("glyphCode"), "fontFile": item.get("fontFile")}
            for item in items
            if item["type"] != "word"
        ]
        line_output = {"number": entry["number"], "type": entry["type"], "wordKeys": words, "items": items}
        if markers:
            line_output["markers"] = markers
        lines_output.append(line_output)
    page_output.append({"page": page, "lines": lines_output})

OUTPUT.parent.mkdir(parents=True, exist_ok=True)
OUTPUT.write_text(
    json.dumps(
        {
            "schemaVersion": 3,
            "source": "GovarJabbar/Quran-PNG via quran.com-images glyph_page_line + glyph + word tables",
            "rtlSourceOrder": "line items sorted by position DESC as in quran.com-images/lib/Quran/DB.pm",
            "sourceSql": "quran.com-images/sql/02-database.sql",
            "pageCount": 604,
            "wordCount": len(layout_word_keys),
            "pages": page_output,
        },
        ensure_ascii=False,
        separators=(",", ":"),
    )
    + "\n",
    encoding="utf-8",
)
print(f"wrote {OUTPUT} bytes={OUTPUT.stat().st_size:,} pages={len(page_output)} words={len(layout_word_keys)}")
print("page2", json.dumps(page_output[1], ensure_ascii=False))
