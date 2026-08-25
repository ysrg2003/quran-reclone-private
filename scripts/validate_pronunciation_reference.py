#!/usr/bin/env python3
"""Validate the generated speech-only pronunciation reference."""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

MARKS = re.compile(r"[\u0610-\u061a\u064b-\u065f\u06d6-\u06ed\u0670]")
MUQATTAAT_EXPECTED = {
    (2, 1): ["الف", "لام", "ميم"],
    (19, 1): ["كاف", "ها", "يا", "عين", "صاد"],
}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--reference", type=Path, default=Path("app/src/main/assets/quran/pronunciation_reference.json"))
    args = parser.parse_args()
    root = json.loads(args.reference.read_text(encoding="utf-8"))
    rows = root["ayahs"]
    errors: list[str] = []
    if root.get("schemaVersion") != 2:
        errors.append("schemaVersion must be 2")
    if root.get("canonicalTextSource") != "UthmanicHafs_V20.doc via word_reference_ayahs.json":
        errors.append("canonicalTextSource must identify the Word reference")
    if root.get("legacySegmentationReconciliation", {}).get("mappedAyahs") != [
        {"surah": 2, "ayah": 181}, {"surah": 8, "ayah": 6}, {"surah": 13, "ayah": 37},
        {"surah": 15, "ayah": 7}, {"surah": 27, "ayah": 20}, {"surah": 36, "ayah": 22},
    ]:
        errors.append("legacy segmentation reconciliation keys are incomplete or out of order")
    if root.get("ayahCount") != 6236 or len(rows) != 6236:
        errors.append(f"ayah count mismatch: metadata={root.get('ayahCount')} rows={len(rows)}")
    if root.get("wordCount") != 77430:
        errors.append(f"word count metadata mismatch: {root.get('wordCount')}")
    seen = set()
    total_words = 0
    for row in rows:
        key = (row["surah"], row["ayah"])
        if key in seen:
            errors.append(f"duplicate ayah {key[0]}:{key[1]}")
        seen.add(key)
        if not row["spoken"] or MARKS.search(row["spoken"]):
            errors.append(f"invalid ayah spoken text at {key[0]}:{key[1]}")
        words = row["words"]
        if len(words) == 0:
            errors.append(f"empty word list at {key[0]}:{key[1]}")
        if row["spoken"] != " ".join(word["spoken"] for word in words):
            errors.append(f"ayah spoken text is not the word sequence at {key[0]}:{key[1]}")
        for index, word in enumerate(words):
            total_words += 1
            if word["index"] != index:
                errors.append(f"word index mismatch at {key[0]}:{key[1]}:{index}")
            if not word["spoken"] or MARKS.search(word["spoken"]):
                errors.append(f"invalid word spoken text at {key[0]}:{key[1]}:{index}")
            if not isinstance(word["spokenTokens"], list) or not word["spokenTokens"]:
                errors.append(f"missing spokenTokens at {key[0]}:{key[1]}:{index}")
            if any(position < 0 or position >= len(word["spoken"]) or word["spoken"][position] not in "اويى" for position in word["maddPositions"]):
                errors.append(f"invalid madd position at {key[0]}:{key[1]}:{index}")
            if not isinstance(word.get("legacyWordIndices"), list) or not word["legacyWordIndices"]:
                errors.append(f"missing legacyWordIndices at {key[0]}:{key[1]}:{index}")
    if total_words != 77430:
        errors.append(f"word count mismatch: {total_words}")
    by_key = {(row["surah"], row["ayah"]): row for row in rows}
    for key, expected in MUQATTAAT_EXPECTED.items():
        row = by_key.get(key)
        actual = row["words"][0]["spokenTokens"] if row else None
        if actual != expected:
            errors.append(f"muqattaat mismatch at {key[0]}:{key[1]}: {actual}")
    targets = {
        (2, 6): "كفرو",
        (2, 14): "لقو",
        (2, 16): "اشترو",
    }
    for key, expected in targets.items():
        row = by_key[key]
        if not any(word["spoken"] == expected for word in row["words"]):
            errors.append(f"target pronunciation {expected} missing at {key[0]}:{key[1]}")
    if errors:
        for error in errors[:80]:
            print(f"ERROR: {error}")
        print(f"pronunciation-reference=FAIL errors={len(errors)}")
        return 1
    print("pronunciation-reference=PASS")
    print(f"ayahs={len(rows)}")
    print(f"words={total_words}")
    print(f"muqattaat_checked={len(MUQATTAAT_EXPECTED)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
