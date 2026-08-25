#!/usr/bin/env python3
"""Validate the generated Word-canonical Quran content asset."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

ARABIC_DIGITS = set("٠١٢٣٤٥٦٧٨٩")
RUB = "۞"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--reference", type=Path, default=Path("app/src/main/assets/quran/word_reference_ayahs.json"))
    args = parser.parse_args()
    root = json.loads(args.reference.read_text(encoding="utf-8"))
    rows = root.get("ayahs", [])
    errors: list[str] = []
    if root.get("schemaVersion") != 2:
        errors.append(f"schemaVersion must be 2, got {root.get('schemaVersion')}")
    if root.get("source") != "UthmanicHafs_V20.doc via word_reference_layout.json":
        errors.append("source does not identify the supplied Word reference")
    if root.get("ayahCount") != 6236 or len(rows) != 6236:
        errors.append(f"ayah count mismatch: metadata={root.get('ayahCount')} rows={len(rows)}")
    if root.get("wordCount") != 77430:
        errors.append(f"word count metadata mismatch: {root.get('wordCount')}")
    if root.get("removedStandaloneRubAlHizbSymbols") != 199:
        errors.append(f"standalone rub count mismatch: {root.get('removedStandaloneRubAlHizbSymbols')}")
    if root.get("removedAyahNumberTokens") != 6236:
        errors.append(f"ayah number count mismatch: {root.get('removedAyahNumberTokens')}")
    if root.get("joinedKnownLibreOfficeSplitTokens") != 3:
        errors.append(f"known split join count mismatch: {root.get('joinedKnownLibreOfficeSplitTokens')}")

    expected_keys = [(surah, ayah) for surah in range(1, 115) for ayah in []]
    seen: set[tuple[int, int]] = set()
    previous: tuple[int, int] | None = None
    total_words = 0
    for row in rows:
        key = (row.get("surah"), row.get("ayah"))
        if key in seen:
            errors.append(f"duplicate ayah {key[0]}:{key[1]}")
        seen.add(key)
        if previous is not None and not (key[0] > previous[0] or (key[0] == previous[0] and key[1] > previous[1])):
            errors.append(f"ayah order is not strictly increasing at {key}")
        previous = key
        words = row.get("words", [])
        if not words:
            errors.append(f"empty Word word list at {key[0]}:{key[1]}")
        if row.get("text") != " ".join(word.get("text", "") for word in words):
            errors.append(f"ayah text is not the word sequence at {key[0]}:{key[1]}")
        for index, word in enumerate(words):
            total_words += 1
            text = word.get("text", "")
            if word.get("index") != index:
                errors.append(f"word index mismatch at {key[0]}:{key[1]}:{index}")
            if not text:
                errors.append(f"empty Word token at {key[0]}:{key[1]}:{index}")
            if text == RUB or all(char in ARABIC_DIGITS for char in text):
                errors.append(f"layout-only token leaked into QuranWord at {key[0]}:{key[1]}:{index}: {text!r}")
            if RUB in text:
                errors.append(f"rub symbol embedded in QuranWord at {key[0]}:{key[1]}:{index}")
    if len({row["surah"] for row in rows}) != 114:
        errors.append("surah count is not 114")
    if total_words != 77430:
        errors.append(f"word count mismatch: {total_words}")
    if len(root.get("legacySegmentationDeltas", [])) != 6:
        errors.append(f"legacy segmentation delta count must be 6, got {len(root.get('legacySegmentationDeltas', []))}")

    by_key = {(row["surah"], row["ayah"]): row for row in rows}
    exact_targets = {
        (1, 1): "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ",
        (1, 2): "ٱلۡحَمۡدُ لِلَّهِ رَبِّ ٱلۡعَٰلَمِينَ",
        (1, 4): "مَٰلِكِ يَوۡمِ ٱلدِّينِ",
        (2, 1): "الٓمٓ",
        (2, 6): "إِنَّ ٱلَّذِينَ كَفَرُواْ سَوَآءٌ عَلَيۡهِمۡ ءَأَنذَرۡتَهُمۡ أَمۡ لَمۡ تُنذِرۡهُمۡ لَا يُؤۡمِنُونَ",
        (2, 14): "وَإِذَا لَقُواْ ٱلَّذِينَ ءَامَنُواْ قَالُوٓاْ ءَامَنَّا وَإِذَا خَلَوۡاْ إِلَىٰ شَيَٰطِينِهِمۡ قَالُوٓاْ إِنَّا مَعَكُمۡ إِنَّمَا نَحۡنُ مُسۡتَهۡزِءُونَ",
        (2, 16): "أُوْلَٰٓئِكَ ٱلَّذِينَ ٱشۡتَرَوُاْ ٱلضَّلَٰلَةَ بِٱلۡهُدَىٰ فَمَا رَبِحَت تِّجَٰرَتُهُمۡ وَمَا كَانُواْ مُهۡتَدِينَ",
        (68, 51): "وَإِن يَكَادُ ٱلَّذِينَ كَفَرُواْ لَيُزۡلِقُونَكَ بِأَبۡصَٰرِهِمۡ لَمَّا سَمِعُواْ ٱلذِّكۡرَ وَيَقُولُونَ إِنَّهُۥ لَمَجۡنُونٞ",
        (86, 1): "وَٱلسَّمَآءِ وَٱلطَّارِقِ",
    }
    for key, expected in exact_targets.items():
        actual = by_key.get(key, {}).get("text")
        if actual != expected:
            errors.append(f"exact Word text mismatch at {key[0]}:{key[1]}: {actual!r}")
    if "ۡ" not in by_key[(1, 1)]["text"]:
        errors.append("U+06E1 dotless-head sukun is missing from 1:1")
    for key in [(2, 181), (8, 6), (13, 37)]:
        row = by_key[key]
        if "بَعۡدَ" not in [word["text"] for word in row["words"]] or "مَا" not in [word["text"] for word in row["words"]]:
            errors.append(f"Word segmentation bَعۡدَ/مَا missing at {key[0]}:{key[1]}")
    if [word["text"] for word in by_key[(15, 7)]["words"]][0] != "لَّوۡمَا":
        errors.append("Word segmentation لَّوۡمَا missing at 15:7")
    if [word["text"] for word in by_key[(27, 20)]["words"]][3] != "مَالِيَ":
        errors.append("Word segmentation مَالِيَ missing at 27:20")
    if [word["text"] for word in by_key[(36, 22)]["words"]][0] != "وَمَالِيَ":
        errors.append("Word segmentation وَمَالِيَ missing at 36:22")

    if errors:
        for error in errors[:100]:
            print(f"ERROR: {error}")
        print(f"word-reference-content=FAIL errors={len(errors)}")
        return 1
    print("word-reference-content=PASS")
    print("ayahs=6236")
    print("surahs=114")
    print(f"words={total_words}")
    print("u06e1_checked=1")
    print("exact_targets_checked=9")
    print("known_split_joins=3")
    print("legacy_segmentation_deltas=6")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
