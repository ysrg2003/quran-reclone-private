"""Compare app ayah words with quran.com-images word tokens.

Quran-PNG is the visual reference; quran.com-images SQL is its machine-readable
word/glyph source. Categories are kept explicit: raw equality, conservative
Arabic equivalence, separate basmala presentation, segmentation, and true
text differences. No source text is silently rewritten.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

EXPECTED_AYAHS = 6236
EXPECTED_PAGES = 604
EXPECTED_REFERENCE_WORD_ROWS = 77430
EXPECTED_WORD_ARABIC_VALUES = 18994
BASMALA_NORMALIZED = ["بسم", "الله", "الرحمان", "الرحيم"]
SPOT_CHECK_KEYS = [(1, 1), (1, 2), (2, 1), (2, 2), (12, 39), (12, 41),
                   (18, 54), (18, 55), (18, 56), (18, 57), (18, 58), (18, 59),
                   (18, 60), (18, 61),
                   (112, 1), (112, 2), (112, 3), (112, 4), (113, 1), (113, 2),
                   (113, 3), (113, 4), (114, 1), (114, 2), (114, 3), (114, 4),
                   (114, 5), (114, 6)]
MARKS = re.compile(r"[\u0610-\u061a\u064b-\u065f\u06d6-\u06ed]")
WHITESPACE = re.compile(r"\s+")
SMALL_ALIF = "ٰ"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--content", type=Path, default=Path("/tmp/quran_content_remote.json"))
    parser.add_argument("--sql", type=Path, default=Path("/home/ubuntu/quran-com-images-reference/sql/02-database.sql"))
    parser.add_argument("--layout", type=Path, default=Path("/tmp/quran_image_layout_full.json"))
    parser.add_argument("--report", type=Path, default=Path("/tmp/quran_word_text_comparison.json"))
    parser.add_argument("--self-check", action="store_true")
    return parser.parse_args()


def insert_values(sql: str, table: str) -> list[str]:
    """Extract VALUES bodies while recognizing SQL quotes and semicolons."""
    header = re.compile(rf"INSERT INTO\s+`(?:nextgen`\.)?{re.escape(table)}`\s+VALUES\s*", re.I)
    bodies: list[str] = []
    for match in header.finditer(sql):
        index = match.end()
        start = index
        quoted = False
        while index < len(sql):
            char = sql[index]
            if char == "'":
                if quoted and index > 0 and sql[index - 1] == "\\":
                    index += 1
                    continue
                if quoted and index + 1 < len(sql) and sql[index + 1] == "'":
                    index += 2
                    continue
                quoted = not quoted
            elif char == ";" and not quoted:
                bodies.append(sql[start:index])
                break
            index += 1
        else:
            raise ValueError(f"unterminated INSERT for {table}")
    if not bodies:
        raise ValueError(f"missing INSERT for {table}")
    return bodies


def parse_sql_value(value: str) -> str | None:
    value = value.strip()
    if value.upper() == "NULL":
        return None
    if len(value) >= 2 and value[0] == "'" and value[-1] == "'":
        value = value[1:-1]
        return value.replace("\\'", "'").replace("''", "'")
    return value


def parse_sql_rows(values: str) -> list[list[str | None]]:
    """Parse comma-separated SQL tuples without splitting quoted commas."""
    rows: list[list[str | None]] = []
    row: list[str | None] | None = None
    token_start = 0
    index = 0
    quoted = False
    while index < len(values):
        char = values[index]
        if char == "'":
            if quoted and index > 0 and values[index - 1] == "\\":
                index += 1
                continue
            if quoted and index + 1 < len(values) and values[index + 1] == "'":
                index += 2
                continue
            quoted = not quoted
        elif not quoted and char == "(":
            if row is not None:
                raise ValueError("nested SQL tuple")
            row = []
            token_start = index + 1
        elif not quoted and char == "," and row is not None:
            row.append(parse_sql_value(values[token_start:index]))
            token_start = index + 1
        elif not quoted and char == ")" and row is not None:
            row.append(parse_sql_value(values[token_start:index]))
            rows.append(row)
            row = None
        index += 1
    if quoted or row is not None:
        raise ValueError("malformed SQL VALUES body")
    return rows


def load_word_arabic(sql: str) -> dict[int, str]:
    result: dict[int, str] = {}
    for values in insert_values(sql, "word_arabic"):
        for row in parse_sql_rows(values):
            if len(row) != 2 or row[0] is None or row[1] is None:
                raise ValueError(f"invalid word_arabic row: {row}")
            result[int(row[0])] = row[1]
    return result


def load_words(sql: str) -> list[dict[str, int]]:
    result: list[dict[str, int]] = []
    for values in insert_values(sql, "word"):
        for row in parse_sql_rows(values):
            if len(row) < 7 or any(row[index] is None for index in range(7)):
                raise ValueError(f"invalid word row: {row}")
            word_id, glyph_id, page, surah, ayah, position, word_arabic_id = (int(row[index]) for index in range(7))
            result.append({"word_id": word_id, "glyph_id": glyph_id, "page": page, "surah": surah,
                           "ayah": ayah, "position": position, "word_arabic_id": word_arabic_id})
    result.sort(key=lambda row: (row["surah"], row["ayah"], row["position"], row["word_id"]))
    return result


def normalize_for_match(value: str) -> str:
    """Same conservative matcher as scripts/validate_full_quran_matching.py."""
    value = MARKS.sub("", unicodedata.normalize("NFC", value).replace("ـ", ""))
    return WHITESPACE.sub(" ", value.replace(SMALL_ALIF, "").replace("ٱ", "ا").replace("آ", "ا")).strip()


def normalize_with_small_alif(value: str) -> str:
    """Broader orthographic form used only for equivalence classification."""
    value = MARKS.sub("", unicodedata.normalize("NFC", value).replace("ـ", ""))
    return WHITESPACE.sub(" ", value.replace(SMALL_ALIF, "ا").replace("ٱ", "ا").replace("آ", "ا")
                          .replace("أ", "ا").replace("إ", "ا").replace("ؤ", "و")
                          .replace("ئ", "ي").replace("ء", "")).strip()


def equivalent_token(first: str, second: str) -> bool:
    return bool({normalize_for_match(first), normalize_with_small_alif(first)} &
                {normalize_for_match(second), normalize_with_small_alif(second)})


def sequence_equivalent(first: list[str], second: list[str]) -> bool:
    return len(first) == len(second) and all(equivalent_token(a, b) for a, b in zip(first, second))


def sequence_signature(tokens: list[str]) -> str:
    return "".join(normalize_with_small_alif(token) for token in tokens)


def first_difference(left: list[str], right: list[str]) -> dict[str, Any] | None:
    differences = all_differences(left, right)
    return differences[0] if differences else None


def all_differences(left: list[str], right: list[str]) -> list[dict[str, Any]]:
    """Return every raw token difference, including trailing segmentation tokens."""
    differences: list[dict[str, Any]] = []
    limit = min(len(left), len(right))
    for index in range(limit):
        if left[index] != right[index]:
            differences.append({"index": index, "app": left[index], "reference": right[index]})
    if len(left) > limit:
        differences.append({"index": limit, "app": left[limit:], "reference": None})
    elif len(right) > limit:
        differences.append({"index": limit, "app": None, "reference": right[limit:]})
    return differences


def key(row: dict[str, Any]) -> tuple[int, int]:
    return int(row["surah"]), int(row["ayah"])


def load_layout_page_map(layout: dict[str, Any]) -> tuple[dict[tuple[int, int], int], dict[int, list[tuple[int, int]]]]:
    page_for_ayah: dict[tuple[int, int], int] = {}
    ayahs_by_page: dict[int, list[tuple[int, int]]] = {}
    for page_entry in layout.get("pages", []):
        page = int(page_entry["page"])
        page_keys = [(int(pair[0]), int(pair[1])) for pair in page_entry.get("ayah_keys", [])]
        ayahs_by_page[page] = page_keys
        for current_key in page_keys:
            if current_key in page_for_ayah and page_for_ayah[current_key] != page:
                raise ValueError(f"ayah {current_key} occurs on multiple pages")
            page_for_ayah[current_key] = page
    return page_for_ayah, ayahs_by_page


def validate_reference_structure(rows: list[dict[str, int]], word_arabic: dict[int, str],
                                page_for_ayah: dict[tuple[int, int], int],
                                expected_keys: set[tuple[int, int]]) -> dict[str, Any]:
    if len(rows) != EXPECTED_REFERENCE_WORD_ROWS:
        raise ValueError(f"reference word row count={len(rows)}, expected={EXPECTED_REFERENCE_WORD_ROWS}")
    if len(word_arabic) != EXPECTED_WORD_ARABIC_VALUES:
        raise ValueError(f"word_arabic count={len(word_arabic)}, expected={EXPECTED_WORD_ARABIC_VALUES}")
    if len({row["word_id"] for row in rows}) != len(rows):
        raise ValueError("duplicate reference word_id")
    if len({row["glyph_id"] for row in rows}) != len(rows):
        raise ValueError("duplicate reference glyph_id")
    by_key: dict[tuple[int, int], list[dict[str, int]]] = defaultdict(list)
    page_counts: Counter[int] = Counter()
    page_errors: list[dict[str, Any]] = []
    for row in rows:
        current_key = key(row)
        if current_key not in expected_keys:
            raise ValueError(f"reference word has unknown ayah {current_key}")
        if row["word_arabic_id"] not in word_arabic:
            raise ValueError(f"missing word_arabic_id={row['word_arabic_id']}")
        expected_page = page_for_ayah.get(current_key)
        if expected_page is None:
            raise ValueError(f"layout has no page for {current_key}")
        page_counts[row["page"]] += 1
        if row["page"] != expected_page:
            page_errors.append({"key": current_key, "row_page": row["page"], "layout_page": expected_page})
        by_key[current_key].append(row)
    if page_errors:
        raise ValueError(f"reference word/page disagreements={page_errors[:3]}")
    if set(by_key) != expected_keys:
        raise ValueError(f"reference ayah key coverage mismatch missing={sorted(expected_keys-set(by_key))[:3]}")
    position_errors = []
    for current_key, grouped in by_key.items():
        positions = [row["position"] for row in sorted(grouped, key=lambda item: item["position"])]
        if positions != list(range(1, len(positions) + 1)):
            position_errors.append((current_key, positions))
    if position_errors:
        raise ValueError(f"non-contiguous word positions={position_errors[:3]}")
    if set(page_counts) != set(range(1, EXPECTED_PAGES + 1)):
        raise ValueError("reference rows do not cover pages 1..604")
    return {"page_word_counts": {str(page): count for page, count in sorted(page_counts.items())}}


def run_self_check() -> int:
    parsed = load_word_arabic("INSERT INTO `word_arabic` VALUES (1,'أَلْف'),(2,'x''y'),(3,'a,b; c');")
    assert parsed == {1: "أَلْف", 2: "x'y", 3: "a,b; c"}, parsed
    assert equivalent_token("الاخرة", "الءاخرة")
    assert equivalent_token("ابرهم", "ابرهۦم")
    assert sequence_signature(["بَعْدَ", "مَا"]) == sequence_signature(["بَعْدَمَا"])
    print("word-comparison-self-check=PASS")
    return 0


def main() -> int:
    args = parse_args()
    if args.self_check:
        return run_self_check()
    content = json.loads(args.content.read_text(encoding="utf-8"))
    content_ayahs = content.get("ayahs")
    if not isinstance(content_ayahs, list):
        print("ERROR: content JSON has no ayahs list", file=sys.stderr)
        return 2
    app_words: dict[tuple[int, int], list[str]] = {}
    for ayah in content_ayahs:
        current_key = (int(ayah["surah"]), int(ayah["ayah"]))
        if current_key in app_words:
            raise ValueError(f"duplicate app ayah key={current_key}")
        app_words[current_key] = [str(word["text"]) for word in ayah.get("words", [])]
    if len(app_words) != EXPECTED_AYAHS:
        raise ValueError(f"app ayah count={len(app_words)}, expected={EXPECTED_AYAHS}")
    sql = args.sql.read_text(encoding="utf-8", errors="replace")
    word_arabic, word_rows = load_word_arabic(sql), load_words(sql)
    layout = json.loads(args.layout.read_text(encoding="utf-8"))
    if len(layout.get("pages", [])) != EXPECTED_PAGES:
        raise ValueError(f"layout page count={len(layout.get('pages', []))}, expected={EXPECTED_PAGES}")
    page_for_ayah, ayahs_by_page = load_layout_page_map(layout)
    structure = validate_reference_structure(word_rows, word_arabic, page_for_ayah, set(app_words))
    reference_words: dict[tuple[int, int], list[str]] = defaultdict(list)
    for row in word_rows:
        reference_words[key(row)].append(word_arabic[row["word_arabic_id"]])

    counts: Counter[str] = Counter()
    true_mismatches: list[dict[str, Any]] = []
    basmala_cases: list[dict[str, Any]] = []
    normalized_cases: list[dict[str, Any]] = []
    segmentation_cases: list[dict[str, Any]] = []
    token_differences: list[dict[str, Any]] = []
    mismatch_pages: Counter[int] = Counter()
    expected_opening_basmala_keys = [f"{surah}:1" for surah in list(range(2, 9)) + list(range(10, 115))]
    opening_basmala_presentations = [
        {"surah": int(key.split(":")[0]), "ayah": 1, "countAsAyah": False}
        for key in expected_opening_basmala_keys
    ]
    unresolved_length_delta = 0
    spot_checks: dict[str, dict[str, Any]] = {}
    for current_key in sorted(app_words):
        app, reference, page = app_words[current_key], reference_words[current_key], page_for_ayah[current_key]
        full_exact = app == reference
        full_normalized = sequence_equivalent(app, reference)
        full_segmented = sequence_signature(app) == sequence_signature(reference)
        if full_exact:
            classification = "exact"
        elif full_normalized:
            classification = "normalized_only"
        elif full_segmented:
            classification = "segmentation_only"
        else:
            classification = "true_text_mismatch"
        counts[classification] += 1
        if current_key in SPOT_CHECK_KEYS:
            spot_checks[f"{current_key[0]}:{current_key[1]}"] = {
                "page": page, "classification": classification, "app_raw": app,
                "reference_raw": reference, "app_word_count": len(app),
                "reference_word_count": len(reference)}
        if classification == "normalized_only":
            differences = all_differences(app, reference)
            normalized_cases.append({"surah": current_key[0], "ayah": current_key[1], "page": page,
                                     "app_raw": app, "reference_raw": reference,
                                     "app_match_normalized": [normalize_for_match(t) for t in app],
                                     "reference_match_normalized": [normalize_for_match(t) for t in reference],
                                     "difference": differences[0] if differences else None,
                                     "all_differences": differences,
                                     "classification": classification})
            token_differences.extend({"surah": current_key[0], "ayah": current_key[1], "page": page,
                                      "scope": "ayah", "classification": classification, **difference}
                                     for difference in differences)
        if "segmentation" in classification:
            differences = all_differences(app, reference)
            segmentation_cases.append({"surah": current_key[0], "ayah": current_key[1], "page": page,
                                       "classification": classification, "app_raw": app,
                                       "reference_raw": reference, "all_differences": differences})
            token_differences.extend({"surah": current_key[0], "ayah": current_key[1], "page": page,
                                      "scope": "ayah", "classification": classification, **difference}
                                     for difference in differences)
        if classification == "true_text_mismatch":
            differences = all_differences(app, reference)
            token_differences.extend({"surah": current_key[0], "ayah": current_key[1], "page": page,
                                      "scope": "ayah", "classification": classification, **difference}
                                     for difference in differences)
            unresolved_length_delta += len(app) - len(reference)
            mismatch_pages[page] += 1
            true_mismatches.append({"surah": current_key[0], "ayah": current_key[1], "page": page,
                                    "app_raw": app, "reference_raw": reference,
                                    "app_match_normalized": [normalize_for_match(t) for t in app],
                                    "reference_match_normalized": [normalize_for_match(t) for t in reference],
                                    "difference": first_difference(app, reference),
                                    "classification": classification})
    report = {
        "provenance": {"visual_reference": "GovarJabbar/Quran-PNG pages 001.png..604.png",
                        "machine_reference": "quran/quran.com-images sql/02-database.sql word + word_arabic",
                        "comparison_scope": "all 6236 app ayahs and all 604 layout pages",
                        "pixel_or_glyph_claim": False},
        "content_ayah_count": len(app_words), "app_word_count": sum(map(len, app_words.values())),
        "reference_word_row_count": len(word_rows), "reference_word_arabic_count": len(word_arabic),
        "app_reference_word_delta": sum(map(len, app_words.values())) - len(word_rows),
        "word_delta_explanation": {
            "opening_basmala_presentations": len(opening_basmala_presentations),
            "opening_basmala_words_in_ayahs": 0,
            "segmentation_cases": len(segmentation_cases),
            "unresolved_length_delta": unresolved_length_delta,
            "unexplained_delta": (sum(map(len, app_words.values())) - len(word_rows)) - unresolved_length_delta},
        "reference_ayahs_with_word_rows": len(reference_words), "layout_page_count": len(ayahs_by_page),
        "layout_page_word_counts": structure["page_word_counts"], "classification_counts": dict(counts),
        "true_text_mismatch_count": len(true_mismatches), "true_text_mismatches": true_mismatches,
        "raw_token_difference_count": len(token_differences), "raw_token_differences": token_differences,
        "expected_basmala_cases": [], "opening_basmala_presentations": opening_basmala_presentations,
        "normalized_only_cases": normalized_cases, "segmentation_cases": segmentation_cases,
        "spot_checks": spot_checks,
        "true_text_mismatch_page_counts": {str(p): n for p, n in sorted(mismatch_pages.items())}}
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"app-ayahs={len(app_words)}")
    print(f"app-words={report['app_word_count']}")
    print(f"reference-word-rows={len(word_rows)}")
    print(f"reference-word-arabic-values={len(word_arabic)}")
    print(f"reference-ayahs-with-word-rows={len(reference_words)}/{len(app_words)}")
    print(f"layout-pages={len(ayahs_by_page)}")
    print(f"app-reference-word-delta={report['app_reference_word_delta']}")
    print(f"classification-counts={dict(counts)}")
    print(f"report={args.report}")
    if true_mismatches:
        print(f"true-text-mismatches={len(true_mismatches)}", file=sys.stderr)
        for row in true_mismatches[:20]:
            print(f"MISMATCH {row['surah']}:{row['ayah']} page={row['page']} diff={row['difference']}", file=sys.stderr)
        return 1
    print("ayah-word-text-comparison=PASS_WITH_EXPLICIT_PRESENTATION_AND_SEGMENTATION_CATEGORIES")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
