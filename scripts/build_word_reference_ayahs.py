#!/usr/bin/env python3
"""Build per-ayah/per-word Quran records from the supplied Word layout.

The `text` fields are copied from the Word-derived layout after removing only
layout-only standalone rub-al-hizb symbols and joining three LibreOffice
line-break artifacts that split a single Word token. Ayah boundaries come from
Word's printed Arabic ayah numbers, not from legacy content word counts.
Comparison fields are imported only as speech hints and never as canonical text.
"""
from __future__ import annotations

import argparse
import json
import re
import unicodedata
from pathlib import Path

ARABIC_MARKS = re.compile(r"[\u0610-\u061a\u064b-\u065f\u06d6-\u06ed\u0670]")
ARABIC_DIGITS = set("٠١٢٣٤٥٦٧٨٩")
RUB = "۞"


def is_ayah_number(token: str) -> bool:
    return bool(token) and all(char in ARABIC_DIGITS for char in token)


def arabic_number(token: str) -> int:
    return int(token.translate(str.maketrans("٠١٢٣٤٥٦٧٨٩", "0123456789")))


def merge_layout_tokens(tokens: list[str]) -> tuple[list[str], int]:
    """Join only the three known LibreOffice split-token artifacts."""
    merged: list[str] = []
    joined = 0
    index = 0
    while index < len(tokens):
        current = tokens[index]
        if index + 1 < len(tokens):
            pair = (current, tokens[index + 1])
            if pair == ("و", "َلَهُ"):
                merged.append("وَ" + "لَهُ")
                index += 2
                joined += 1
                continue
            if pair == ("ٱ", "لذِّكۡرَ"):
                merged.append("ٱ" + "لذِّكۡرَ")
                index += 2
                joined += 1
                continue
            if pair == ("و", "َٱلسَّمَآءِ"):
                merged.append("وَ" + "ٱلسَّمَآءِ")
                index += 2
                joined += 1
                continue
        merged.append(current)
        index += 1
    return merged, joined


def comparison_hint(value: str) -> str:
    """Build a comparison-only Arabic hint from the same Word token."""
    output: list[str] = []
    chars = list(unicodedata.normalize("NFC", value))
    index = 0
    while index < len(chars):
        char = chars[index]
        if char == "و" and index + 1 < len(chars) and chars[index + 1] == "ْ" and index + 2 < len(chars) and chars[index + 2] == "ل":
            index += 1
            continue
        if char == "ا" and index + 1 < len(chars) and chars[index + 1] in {"ْ", "ۡ", "۟"}:
            index += 1
            continue
        if char == "و" and index + 1 < len(chars) and chars[index + 1] == "ٰ":
            output.append("ا")
            index += 2
            continue
        if char == "ى" and index + 1 < len(chars) and chars[index + 1] == "ٰ":
            output.append("ى")
            index += 2
            continue
        if char == "ٰ":
            output.append("ا")
            index += 1
            continue
        if char == "۟":
            if output and output[-1] in "ابتثجحخدذرزسشصضطظعغفقكلمنهوي":
                output.pop()
            index += 1
            continue
        if ARABIC_MARKS.fullmatch(char):
            index += 1
            continue
        output.append({"ٱ": "ا", "آ": "ا", "أ": "ا", "إ": "ا", "ؤ": "و", "ئ": "ي", "ء": ""}.get(char, char))
        index += 1
    return "".join(output)


def load(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--layout", type=Path, default=Path("app/src/main/assets/quran/word_reference_layout.json"))
    parser.add_argument("--content", type=Path, default=Path("/home/ubuntu/quran-memorization-assets/quran/quran_content.json"))
    parser.add_argument("--out", type=Path, default=Path("app/src/main/assets/quran/word_reference_ayahs.json"))
    args = parser.parse_args()

    layout = load(args.layout)
    comparison = load(args.content)
    comparison_ayahs = comparison["ayahs"]
    expected_keys = [(item["surah"], item["ayah"]) for item in comparison_ayahs]
    comparison_by_key = {(item["surah"], item["ayah"]): item for item in comparison_ayahs}

    extracted: dict[tuple[int, int], list[str]] = {}
    current_tokens: list[str] = []
    key_index = 0
    rub_count = 0
    number_count = 0
    joined_count = 0

    for page in layout["pages"]:
        for line in page["lines"]:
            if line.get("type") != "ayah":
                continue
            raw_tokens = line.get("text", "").split()
            tokens, joined = merge_layout_tokens(raw_tokens)
            joined_count += joined
            for token in tokens:
                if token == RUB:
                    rub_count += 1
                    continue
                if is_ayah_number(token):
                    if key_index >= len(expected_keys):
                        raise SystemExit(f"too many ayah numbers at page {page['page']}")
                    if not current_tokens:
                        raise SystemExit(f"empty Word ayah before number {token} at page {page['page']}")
                    key = expected_keys[key_index]
                    printed_number = arabic_number(token)
                    if printed_number != key[1]:
                        raise SystemExit(
                            f"Word ayah number mismatch at page {page['page']}: printed {printed_number}, expected {key}"
                        )
                    extracted[key] = current_tokens
                    current_tokens = []
                    key_index += 1
                    number_count += 1
                else:
                    current_tokens.append(token)

    if current_tokens:
        raise SystemExit(f"unterminated final Word ayah tokens: {current_tokens[:5]}")
    if key_index != len(expected_keys):
        raise SystemExit(f"Word ayah count mismatch: parsed {key_index}, expected {len(expected_keys)}")
    if len(extracted) != len(expected_keys):
        raise SystemExit(f"duplicate/missing Word ayahs: parsed unique {len(extracted)}")
    if joined_count != 3:
        raise SystemExit(f"expected exactly 3 known Word split joins, got {joined_count}")

    output_ayahs: list[dict] = []
    total_words = 0
    length_deltas: list[dict] = []
    for key in expected_keys:
        visual_words = extracted[key]
        source = comparison_by_key[key]
        source_words = source.get("words", [])
        if len(visual_words) != len(source_words):
            length_deltas.append({
                "surah": key[0],
                "ayah": key[1],
                "wordCount": len(visual_words),
                "comparisonWordCount": len(source_words),
            })
        words = []
        for index, visual in enumerate(visual_words):
            words.append({"index": index, "text": visual, "matchText": comparison_hint(visual)})
        output_ayahs.append({
            "surah": key[0],
            "ayah": key[1],
            "text": " ".join(word["text"] for word in words),
            "matchText": " ".join(word["matchText"] for word in words if word["matchText"]),
            "words": words,
        })
        total_words += len(words)

    payload = {
        "schemaVersion": 2,
        "source": "UthmanicHafs_V20.doc via word_reference_layout.json",
        "comparisonSource": "quran_content.json matchText only; never used for canonical text or ayah boundaries",
        "purpose": "canonical Quran text for display and ayah/word evaluation; every text field is copied from Word",
        "ayahCount": len(output_ayahs),
        "wordCount": total_words,
        "removedStandaloneRubAlHizbSymbols": rub_count,
        "removedAyahNumberTokens": number_count,
        "joinedKnownLibreOfficeSplitTokens": joined_count,
        "wordSegmentationPolicy": "Word segmentation is canonical; six known legacy ordinal segmentation deltas are retained and mapped separately for audio",
        "legacySegmentationDeltas": length_deltas,
        "ayahs": output_ayahs,
    }
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    print(f"wrote={args.out}")
    print(f"ayahs={len(output_ayahs)}")
    print(f"words={total_words}")
    print(f"rub_symbols_removed={rub_count}")
    print(f"ayah_numbers_removed={number_count}")
    print(f"known_split_joins={joined_count}")
    print(f"legacy_segmentation_deltas={len(length_deltas)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
