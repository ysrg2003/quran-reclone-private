#!/usr/bin/env python3
"""Build immutable offline Quran assets from a Tanzil pipe-delimited export.

The source text is never rewritten. The derived JSON keeps the original ayah text
and adds word indexes plus a conservative matching form for local recognition.
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

ARABIC_DIACRITICS = re.compile(r"[\u0610-\u061a\u064b-\u065f\u0670\u06d6-\u06ed]")
WHITESPACE = re.compile(r"\s+")


def normalize_for_match(text: str) -> str:
    """Create a matching-only form; the canonical text remains unchanged."""
    text = ARABIC_DIACRITICS.sub("", text)
    text = text.replace("ٱ", "ا").replace("ٰ", "ا")
    return WHITESPACE.sub(" ", text).strip()


def parse_source(source: Path) -> list[dict]:
    ayahs: list[dict] = []
    for line_number, raw in enumerate(source.read_text(encoding="utf-8").splitlines(), 1):
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        parts = raw.split("|", 2)
        if len(parts) != 3:
            raise ValueError(f"line {line_number}: expected surah|ayah|text")
        surah, ayah, text = parts
        if not surah.isdigit() or not ayah.isdigit():
            raise ValueError(f"line {line_number}: invalid key {surah}|{ayah}")
        words = text.split(" ")
        if any(not word for word in words):
            raise ValueError(f"line {line_number}: repeated spaces in canonical text")
        ayahs.append(
            {
                "surah": int(surah),
                "ayah": int(ayah),
                "text": text,
                "matchText": normalize_for_match(text),
                "words": [
                    {
                        "index": index,
                        "text": word,
                        "matchText": normalize_for_match(word),
                    }
                    for index, word in enumerate(words)
                ],
            }
        )
    return ayahs


def validate(ayahs: list[dict]) -> None:
    if not ayahs:
        raise ValueError("No ayahs were parsed")
    surahs = sorted({item["surah"] for item in ayahs})
    if surahs != list(range(1, 115)):
        raise ValueError(f"Expected surahs 1..114, got {surahs[:3]}..{surahs[-3:]}")
    keys = [(item["surah"], item["ayah"]) for item in ayahs]
    if len(keys) != len(set(keys)):
        raise ValueError("Duplicate surah/ayah key")
    for surah in surahs:
        numbers = [item["ayah"] for item in ayahs if item["surah"] == surah]
        if numbers != list(range(1, len(numbers) + 1)):
            raise ValueError(f"Surah {surah} has non-contiguous ayah numbering")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    ayahs = parse_source(args.source)
    validate(ayahs)
    payload = {
        "schemaVersion": 1,
        "source": "Tanzil Quran Text Version 1.1",
        "sourceUrl": "https://tanzil.net/",
        "license": "Creative Commons Attribution 3.0",
        "canonicalTextUnchanged": True,
        "ayahCount": len(ayahs),
        "surahCount": 114,
        "ayahs": ayahs,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(payload, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    print(json.dumps({"ayahCount": len(ayahs), "surahCount": 114, "output": str(args.output)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
