"""Inspect selected quran.com-images rows for a comparison exception."""
from __future__ import annotations

import importlib.util
import json
from pathlib import Path

VALIDATOR = Path(__file__).with_name("validate_quran_word_text_against_image_layout.py")
SQL = Path("/home/ubuntu/quran-com-images-reference/sql/02-database.sql")

def load_validator():
    spec = importlib.util.spec_from_file_location("word_validator", VALIDATOR)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module

def main() -> None:
    validator = load_validator()
    sql = SQL.read_text(encoding="utf-8", errors="replace")
    values = validator.load_word_arabic(sql)
    rows = validator.load_words(sql)
    glyph_rows = {}
    for values_body in validator.insert_values(sql, "glyph"):
        for row in validator.parse_sql_rows(values_body):
            if len(row) >= 4 and row[0] is not None:
                glyph_rows[int(row[0])] = row
    selected = {(12, 39), (12, 41), (2, 181), (8, 6), (13, 37)}
    ids = {word_id for word_id, text in values.items() if text in {"يَٰصَٰحِبَىِ", "يَٰصَىٰحِبَىِ", "بَعْدَ", "مَا", "بَعْدَمَا"}}
    print("word_arabic_ids=")
    for word_id in sorted(ids):
        print(word_id, repr(values[word_id]), [(r["word_id"], r["surah"], r["ayah"], r["position"], r["page"], r["glyph_id"]) for r in rows if r["word_arabic_id"] == word_id])
    print("selected_ayah_rows=")
    for row in rows:
        if (row["surah"], row["ayah"]) in selected:
            output = {**row, "text": values[row["word_arabic_id"]]}
            output["glyph_row"] = glyph_rows.get(row["glyph_id"])
            print(json.dumps(output, ensure_ascii=False))

if __name__ == "__main__":
    main()
