#!/usr/bin/env python3
"""Audit the Quran data contract before/after opening-basmalah migration.

This tool is intentionally read-only. It compares quran_content.json against
quran.com-images SQL and quran-align timings, and writes a JSON report.
"""
from __future__ import annotations

import ast
import json
import re
import sys
import unicodedata
from collections import defaultdict
from pathlib import Path
from typing import Any

BASMALA = ["بِسْمِ", "ٱللَّهِ", "ٱلرَّحْمَٰنِ", "ٱلرَّحِيمِ"]
BASMALA_MATCH = ["بسم", "الله", "الرحمن", "الرحيم"]
MUQATTAAT_SOURCE = {
    "الٓمٓ", "الٓمٓصٓ", "الٓر", "الٓمٓر", "كٓهيعٓصٓ", "طه",
    "طسٓمٓ", "طسٓ", "يسٓ", "صٓ", "حمٓ", "عٓسٓقٓ", "قٓ", "نٓ",
}
MUQATTAAT_MATCH = {
    "الم", "المص", "الر", "المر", "كهيعص", "طه", "طسم", "طس",
    "يس", "ص", "حم", "عسق", "ق", "ن",
}

MARKS = re.compile(r"[\u0610-\u061a\u064b-\u065f\u06d6-\u06ed]")
WHITESPACE = re.compile(r"\s+")

def norm(value: str) -> str:
    value = unicodedata.normalize("NFC", value or "")
    value = MARKS.sub("", value).replace("ـ", "").replace("ٰ", "")
    value = value.replace("ٱ", "ا").replace("آ", "ا").replace("أ", "ا").replace("إ", "ا")
    value = value.replace("ؤ", "و").replace("ئ", "ي").replace("ء", "")
    return WHITESPACE.sub(" ", value).strip()

def parse_sql_tuples(sql: str, table: str) -> list[tuple[Any, ...]]:
    """Parse every MySQL INSERT tuple without executing untrusted SQL."""
    pattern = re.compile(rf"INSERT INTO `{re.escape(table)}` VALUES (.*?);", re.S)
    matches = list(pattern.finditer(sql))
    if not matches:
        raise RuntimeError(f"INSERT for `{table}` not found")
    tuples: list[tuple[Any, ...]] = []
    for match in matches:
        body = match.group(1)
        current: list[str] = []
        token: list[str] = []
        in_string = False
        escaped = False
        depth = 0
        for char in body:
            if in_string:
                token.append(char)
                if escaped:
                    escaped = False
                elif char == "\\":
                    escaped = True
                elif char == "'":
                    in_string = False
                continue
            if char == "'":
                in_string = True
                token.append(char)
            elif char == "(":
                depth += 1
                if depth == 1:
                    current = []
                    token = []
                else:
                    token.append(char)
            elif char == ")":
                if depth == 1:
                    if token or current:
                        current.append("".join(token).strip())
                    values: list[Any] = []
                    for raw in current:
                        if raw.upper() == "NULL":
                            values.append(None)
                        else:
                            try:
                                values.append(ast.literal_eval(raw))
                            except (ValueError, SyntaxError):
                                values.append(raw)
                    tuples.append(tuple(values))
                    current = []
                    token = []
                    depth = 0
                else:
                    token.append(char)
                    depth -= 1
            elif char == "," and depth == 1:
                current.append("".join(token).strip())
                token = []
            elif depth >= 1:
                token.append(char)
    return tuples

def load_reference(sql_path: Path) -> dict[tuple[int, int], list[str]]:
    sql = sql_path.read_text(encoding="utf-8", errors="replace")
    words = parse_sql_tuples(sql, "word")
    arabic_rows = parse_sql_tuples(sql, "word_arabic")
    arabic_by_id: dict[int, str] = {}
    for row in arabic_rows:
        if len(row) >= 2 and isinstance(row[0], int):
            arabic_by_id[int(row[0])] = str(row[1])
    result: dict[tuple[int, int], list[str]] = defaultdict(list)
    for row in words:
        if len(row) < 7:
            continue
        surah, ayah, position, word_arabic_id = int(row[3]), int(row[4]), int(row[5]), int(row[6])
        result[(surah, ayah)].append((position, arabic_by_id.get(word_arabic_id, "")))
    return {key: [text for _, text in sorted(values)] for key, values in result.items()}

def load_timings(path: Path | None) -> dict[tuple[int, int], list[dict[str, Any]]]:
    if path is None or not path.exists():
        return {}
    payload = json.loads(path.read_text(encoding="utf-8"))
    result: dict[tuple[int, int], list[dict[str, Any]]] = {}
    # quran-align releases have appeared in both keyed and nested forms.
    if isinstance(payload, dict):
        candidates = payload.get("audio", payload.get("timings", payload))
    else:
        candidates = payload
    if isinstance(candidates, dict):
        for key, value in candidates.items():
            if isinstance(value, dict) and "segments" in value:
                segments = value["segments"]
            else:
                segments = value
            match = re.match(r"^(\d+)[/:](\d+)$", str(key))
            if match and isinstance(segments, list):
                result[(int(match.group(1)), int(match.group(2)))] = segments
    elif isinstance(candidates, list):
        for item in candidates:
            if not isinstance(item, dict):
                continue
            surah = item.get("surah", item.get("sura", item.get("sura_number")))
            ayah = item.get("ayah", item.get("ayah_number"))
            segments = item.get("segments", item.get("timings"))
            if surah is not None and ayah is not None and isinstance(segments, list):
                result[(int(surah), int(ayah))] = segments
    return result

def segment_bounds(segments: list[Any], word_count: int) -> dict[str, Any]:
    starts: list[int] = []
    ends: list[int] = []
    malformed = 0
    for segment in segments:
        if isinstance(segment, dict):
            start = segment.get("wordStart", segment.get("word_start", segment.get("start_word")))
            end = segment.get("wordEnd", segment.get("word_end", segment.get("end_word")))
        elif isinstance(segment, list) and len(segment) >= 2:
            start, end = segment[0], segment[1]
        else:
            malformed += 1
            continue
        if start is None or end is None:
            malformed += 1
            continue
        try:
            starts.append(int(start)); ends.append(int(end))
        except (TypeError, ValueError):
            malformed += 1
    invalid = [value for value in starts + ends if value < 0 or value > word_count]
    return {
        "segmentCount": len(segments),
        "minWordStart": min(starts) if starts else None,
        "maxWordEnd": max(ends) if ends else None,
        "wordCount": word_count,
        "outOfBounds": sorted(set(invalid)),
        "malformedSegments": malformed,
        "withinBounds": not invalid and malformed == 0,
    }

def main() -> None:
    if len(sys.argv) < 3:
        raise SystemExit("usage: audit_quran_ayah_contract.py SOURCE_JSON REFERENCE_SQL [TIMINGS_JSON] [REPORT_JSON]")
    source_path = Path(sys.argv[1])
    sql_path = Path(sys.argv[2])
    timing_path = Path(sys.argv[3]) if len(sys.argv) > 3 else None
    report_path = Path(sys.argv[4]) if len(sys.argv) > 4 else None
    payload = json.loads(source_path.read_text(encoding="utf-8"))
    source_ayahs = payload["ayahs"]
    source_by_key = {(int(item["surah"]), int(item["ayah"])): item for item in source_ayahs}
    reference = load_reference(sql_path)
    timings = load_timings(timing_path)

    first_ayahs: list[dict[str, Any]] = []
    opening_basmala_keys: list[str] = []
    source_basmala_count = 0
    ref_basmala_count = 0
    mismatches: list[dict[str, Any]] = []
    muqattaat: list[dict[str, Any]] = []
    timing_audit: list[dict[str, Any]] = []
    for surah in range(1, 115):
        key = (surah, 1)
        item = source_by_key.get(key)
        ref_words = reference.get(key, [])
        source_words = [str(word.get("text", "")) for word in (item or {}).get("words", [])]
        source_match_words = [str(word.get("matchText", "")) for word in (item or {}).get("words", [])]
        has_basmala = source_words[:4] == BASMALA or [norm(x) for x in source_words[:4]] == [norm(x) for x in BASMALA]
        if has_basmala:
            opening_basmala_keys.append(f"{surah}:1")
            source_basmala_count += 1
        ref_has_basmala = [norm(x) for x in ref_words[:4]] == [norm(x) for x in BASMALA]
        if ref_has_basmala:
            ref_basmala_count += 1
        expected_opening = surah not in (1, 9)
        first_ayahs.append({
            "surah": surah,
            "ayah": 1,
            "sourceText": (item or {}).get("text"),
            "sourceMatchText": (item or {}).get("matchText"),
            "sourceWords": source_words,
            "sourceMatchWords": source_match_words,
            "sourceWordCount": len(source_words),
            "referenceWords": ref_words,
            "referenceWordCount": len(ref_words),
            "hasStandardBasmalaPrefix": has_basmala,
            "referenceHasBasmalaPrefix": ref_has_basmala,
            "expectedSeparateOpeningBasmala": expected_opening,
        })
        if item is None:
            mismatches.append({"key": f"{surah}:1", "reason": "missing_source_ayah"})
        if source_words[:len(ref_words)] != ref_words and not (has_basmala and source_words[4:] == ref_words):
            mismatches.append({"key": f"{surah}:1", "reason": "first_ayah_word_sequence_diff", "source": source_words, "reference": ref_words})
        if item:
            for word in item.get("words", []):
                text = str(word.get("text", ""))
                match_text = str(word.get("matchText", ""))
                if text in MUQATTAAT_SOURCE or match_text in MUQATTAAT_MATCH:
                    muqattaat.append({
                        "surah": surah,
                        "ayah": 1,
                        "sourceText": text,
                        "sourceMatchText": match_text,
                        "referenceTokenCandidates": [token for token in ref_words if token in MUQATTAAT_SOURCE or token in MUQATTAAT_MATCH],
                        "sourceWordIndex": word.get("index"),
                    })
        if key in timings:
            timing_audit.append({"surah": surah, "ayah": 1, "timing": segment_bounds(timings[key], len(source_words))})

    for item in source_ayahs:
        key = (int(item["surah"]), int(item["ayah"]))
        source_words = [str(word.get("text", "")) for word in item.get("words", [])]
        if key in reference and source_words != reference[key] and not (key[1] == 1 and source_words[4:] == reference[key]):
            mismatches.append({"key": f"{key[0]}:{key[1]}", "reason": "word_sequence_diff", "source": source_words, "reference": reference[key]})
        if key in timings:
            timing_audit.append({"surah": key[0], "ayah": key[1], "timing": segment_bounds(timings[key], len(source_words))})

    report = {
        "source": str(source_path),
        "reference": str(sql_path),
        "timings": str(timing_path) if timing_path else None,
        "assetVersion": payload.get("assetVersion"),
        "canonicalTextUnchanged": payload.get("canonicalTextUnchanged"),
        "counts": {
            "sourceAyahs": len(source_ayahs),
            "sourceWords": sum(len(item.get("words", [])) for item in source_ayahs),
            "referenceAyahs": len(reference),
            "referenceWords": sum(len(words) for words in reference.values()),
            "sourceOpeningBasmalaCount": source_basmala_count,
            "referenceBasmalaAyahCount": ref_basmala_count,
            "openingBasmalaKeys": len(opening_basmala_keys),
            "wordDifference": sum(len(item.get("words", [])) for item in source_ayahs) - sum(len(words) for words in reference.values()),
        },
        "openingBasmalaKeys": opening_basmala_keys,
        "firstAyahs": first_ayahs,
        "muqattaatOccurrences": muqattaat,
        "timingAudit": timing_audit,
        "mismatches": mismatches,
        "summary": {
            "sourceOpeningBasmalaExcludedFromExpectedAyahs": all(k not in opening_basmala_keys for k in [f"{s}:1" for s in range(2, 9)] + [f"{s}:1" for s in range(10, 115)]),
            "presentationOpeningBasmalaCoverage": len([s for s in range(1, 115) if s not in (1, 9)]),
            "surah9HasNoOpeningBasmala": "9:1" not in opening_basmala_keys,
            "fatiha1HasReferenceBasmala": [norm(x) for x in reference.get((1, 1), [])[:4]] == [norm(x) for x in BASMALA],
            "timingOutOfBoundsCount": sum(not row["timing"]["withinBounds"] for row in timing_audit),
            "timingAuditedAyahCount": len(timing_audit),
            "mismatchCount": len(mismatches),
        },
    }
    encoded = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if report_path:
        report_path.write_text(encoded, encoding="utf-8")
    else:
        print(encoded)

if __name__ == "__main__":
    main()
