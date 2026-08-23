#!/usr/bin/env python3
"""Extract page/line/word layout rows from quran.com-images SQL seed data."""
from __future__ import annotations

import json
import re
import sys
from collections import defaultdict
from pathlib import Path


WORD_ROW = re.compile(r"\((\d+),(\d+),(\d+),(\d+),(\d+),(\d+),")
GLYPH_AYAH_ROW = re.compile(r"\((\d+),(\d+),(\d+),(\d+),(\d+)\)")
PAGE_LINE_ROW = re.compile(r"\((\d+),(\d+),(\d+),(\d+),(\d+),'([a-z-]+)'\)")


def insert_values(sql: str, table: str) -> list[str]:
    pattern = re.compile(
        rf"INSERT INTO (?:`nextgen`\.)?`{re.escape(table)}`[^;]*?VALUES\s*(.*?);",
        re.IGNORECASE | re.DOTALL,
    )
    matches = pattern.findall(sql)
    if not matches:
        raise SystemExit(f"missing INSERT for {table}")
    return matches


def main() -> None:
    sql_path = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("/home/ubuntu/quran-com-images-reference/sql/02-database.sql")
    out_path = Path(sys.argv[2]) if len(sys.argv) > 2 else Path("/tmp/quran_image_layout.json")
    sql = sql_path.read_text(encoding="utf-8", errors="replace")

    words = {}
    for values in insert_values(sql, "word"):
        for row in WORD_ROW.finditer(values):
            word_id, glyph_id, page, surah, ayah, position = map(int, row.groups())
            words[glyph_id] = {"page": page, "surah": surah, "ayah": ayah, "position": position, "word_id": word_id}

    glyph_ayah = {}
    for values in insert_values(sql, "glyph_ayah"):
        for row in GLYPH_AYAH_ROW.finditer(values):
            _, glyph_id, surah, ayah, position = map(int, row.groups())
            glyph_ayah[glyph_id] = {"surah": surah, "ayah": ayah, "position": position}

    pages: dict[int, dict[int, dict[str, object]]] = defaultdict(dict)
    unmatched = []
    for values in insert_values(sql, "glyph_page_line"):
        for row in PAGE_LINE_ROW.finditer(values):
            _, glyph_id, page, line, position, line_type = row.groups()
            glyph_id = int(glyph_id)
            page = int(page)
            line = int(line)
            position = int(position)
            record = {"glyph_id": glyph_id, "position": position, "line_type": line_type}
            if glyph_id in words:
                record.update({"surah": words[glyph_id]["surah"], "ayah": words[glyph_id]["ayah"], "word_position": words[glyph_id]["position"]})
            elif glyph_id in glyph_ayah:
                record.update({"surah": glyph_ayah[glyph_id]["surah"], "ayah": glyph_ayah[glyph_id]["ayah"], "word_position": glyph_ayah[glyph_id]["position"]})
            else:
                unmatched.append({"page": page, "line": line, "glyph_id": glyph_id})
            pages[page].setdefault(line, {"line_number": line, "line_type": line_type, "items": []})["items"].append(record)

    normalized_pages = []
    for page in range(1, 605):
        line_rows = []
        for line in sorted(pages[page]):
            row = pages[page][line]
            items = sorted(row["items"], key=lambda item: item["position"])
            row["items"] = items
            row["ayah_keys"] = sorted({(item["surah"], item["ayah"]) for item in items if "surah" in item})
            line_rows.append(row)
        ayah_keys = []
        for row in line_rows:
            for key in row["ayah_keys"]:
                if key not in ayah_keys:
                    ayah_keys.append(key)
        normalized_pages.append({"page": page, "line_count": len(line_rows), "lines": line_rows, "ayah_keys": ayah_keys})

    output = {"page_count": len(normalized_pages), "pages": normalized_pages, "word_glyph_count": len(words), "glyph_ayah_count": len(glyph_ayah), "unmatched_page_glyphs": unmatched}
    out_path.write_text(json.dumps(output, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"pages={len(normalized_pages)}")
    print(f"word-glyphs={len(words)}")
    print(f"glyph-ayah-rows={len(glyph_ayah)}")
    print(f"unmatched-page-glyphs={len(unmatched)}")
    print(f"output={out_path}")


if __name__ == "__main__":
    main()
