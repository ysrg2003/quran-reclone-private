#!/usr/bin/env python3
"""Validate that every canonical Quran word accepts its source matchText form."""
from __future__ import annotations

import json
import re
import sys
import unicodedata
from pathlib import Path

MARKS = re.compile(r"[\u0610-\u061a\u064b-\u065f\u06d6-\u06ed]")
WHITESPACE = re.compile(r"\s+")
SMALL_ALIF = "ٰ"
EXPECTED_WORDS = 77878


def normalize_for_match(value: str) -> str:
    return WHITESPACE.sub(" ", MARKS.sub("", unicodedata.normalize("NFC", value)).replace("ـ", "").replace(SMALL_ALIF, "").replace("ٱ", "ا").replace("آ", "ا")).strip()


def normalize_with_small_alif(value: str) -> str:
    value = MARKS.sub("", unicodedata.normalize("NFC", value)).replace("ـ", "")
    return WHITESPACE.sub(" ", value.replace(SMALL_ALIF, "ا").replace("ٱ", "ا").replace("آ", "ا").replace("أ", "ا").replace("إ", "ا").replace("ؤ", "و").replace("ئ", "ي").replace("ء", "")).strip()


def equivalent(first: str, second: str) -> bool:
    observed = normalize_for_match(second)
    first_forms = {normalize_for_match(first), normalize_with_small_alif(first)}
    second_forms = {normalize_for_match(second), normalize_with_small_alif(second)}
    return bool(first_forms & second_forms) or observed in first_forms


def main() -> None:
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("/tmp/quran_content_remote.json")
    payload = json.loads(path.read_text(encoding="utf-8"))
    ayahs = payload["ayahs"]
    words = [word for ayah in ayahs for word in ayah["words"]]
    keys = [(ayah["surah"], ayah["ayah"]) for ayah in ayahs]
    failures = [(word["text"], word["matchText"]) for word in words if not equivalent(word["text"], word["matchText"])]
    if len(ayahs) != 6236:
        raise SystemExit(f"expected 6236 ayahs, found {len(ayahs)}")
    if len(words) != EXPECTED_WORDS:
        raise SystemExit(f"expected {EXPECTED_WORDS} words, found {len(words)}")
    if len(set(keys)) != len(keys):
        raise SystemExit("duplicate surah/ayah key")
    if failures:
        raise SystemExit(f"source text/matchText equivalence failures={failures[:10]}")
    print(f"full-quran-ayahs={len(ayahs)}")
    print(f"full-quran-words={len(words)}")
    print("all-text-matchText-pairs-accepted=true")
    print("full-quran-matcher-source-check-ok")


if __name__ == "__main__":
    main()
