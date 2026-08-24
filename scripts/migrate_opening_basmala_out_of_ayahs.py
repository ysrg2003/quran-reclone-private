#!/usr/bin/env python3
"""Migrate opening basmala out of non-Fatiha/non-Tawbah first ayahs.

The script is conservative: it only removes the exact four-word prefix from
surah 2-8 and 10-114 ayah 1, then verifies every remaining word against the
quran.com-images reference before writing output.
"""
from __future__ import annotations

import copy
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from audit_quran_ayah_contract import BASMALA, BASMALA_MATCH, load_reference, norm  # noqa: E402

EXPECTED_AYAHS = 6236
EXPECTED_WORDS = 77430
EXPECTED_OPENINGS = 112
SEPARATE_SURAH_NUMBERS = tuple(list(range(2, 9)) + list(range(10, 115)))


def fail(message: str) -> None:
    raise SystemExit(f"migration aborted: {message}")


def main() -> None:
    if len(sys.argv) < 4:
        raise SystemExit("usage: migrate_opening_basmala_out_of_ayahs.py INPUT_JSON OUTPUT_JSON REFERENCE_SQL [ASSET_VERSION]")
    input_path = Path(sys.argv[1])
    output_path = Path(sys.argv[2])
    reference_path = Path(sys.argv[3])
    asset_version = sys.argv[4] if len(sys.argv) > 4 else "2026.08.24.3"
    payload = json.loads(input_path.read_text(encoding="utf-8"))
    original = copy.deepcopy(payload)
    ayahs = payload.get("ayahs")
    if not isinstance(ayahs, list) or len(ayahs) != EXPECTED_AYAHS:
        fail(f"expected {EXPECTED_AYAHS} ayahs")
    reference = load_reference(reference_path)
    opening_count = 0
    removed_words = 0
    for item in ayahs:
        key = (int(item["surah"]), int(item["ayah"]))
        words = item.get("words")
        if not isinstance(words, list):
            fail(f"{key}: words is not an array")
        # Ensure the pre-migration record itself is internally consistent.
        if item.get("text") != " ".join(str(word["text"]) for word in words):
            fail(f"{key}: text does not equal its word sequence before migration")
        if item.get("matchText") != " ".join(str(word["matchText"]) for word in words):
            fail(f"{key}: matchText does not equal its word sequence before migration")
        if key[1] == 1 and key[0] in SEPARATE_SURAH_NUMBERS:
            if len(words) < 5:
                fail(f"{key}: expected basmala plus at least one ayah word")
            actual_text = [str(word["text"]) for word in words[:4]]
            actual_match = [str(word["matchText"]) for word in words[:4]]
            if [norm(value) for value in actual_text] != [norm(value) for value in BASMALA] or [norm(value) for value in actual_match] != [norm(value) for value in BASMALA_MATCH]:
                fail(f"{key}: opening prefix is not the exact four-word basmala")
            remaining_text = [str(word["text"]) for word in words[4:]]
            if [norm(value) for value in remaining_text] != [norm(value) for value in reference.get(key, [])]:
                fail(f"{key}: remaining words do not already match the reference")
            words = words[4:]
            removed_words += 4
            opening_count += 1
            item["text"] = " ".join(str(word["text"]) for word in words)
            item["matchText"] = " ".join(str(word["matchText"]) for word in words)
            item["words"] = words
        # Reindex only the canonical word positions; no new tokens are created.
        for index, word in enumerate(item["words"]):
            word["index"] = index
        if key not in reference:
            fail(f"{key}: missing from reference")
        source_text = [str(word["text"]) for word in item["words"]]
        reference_text = reference[key]
        if source_text != reference_text:
            fail(f"{key}: final text differs from reference")
    if opening_count != EXPECTED_OPENINGS or removed_words != 448:
        fail(f"expected {EXPECTED_OPENINGS} openings/448 removed words, got {opening_count}/{removed_words}")
    if len(ayahs) != EXPECTED_AYAHS:
        fail("ayah count changed")
    final_words = sum(len(item["words"]) for item in ayahs)
    if final_words != EXPECTED_WORDS:
        fail(f"expected {EXPECTED_WORDS} final words, got {final_words}")
    if any(item["surah"] == 9 and item["ayah"] == 1 and item["words"][:4] == BASMALA for item in ayahs):
        fail("surah 9 unexpectedly gained an opening basmala")
    if original["ayahs"][0] != payload["ayahs"][0]:
        fail("Fatiha 1:1 changed; it must remain the basmala ayah")

    payload["assetVersion"] = asset_version
    payload["schemaVersion"] = max(int(payload.get("schemaVersion", 1)), 2)
    payload["canonicalTextUnchanged"] = False
    payload["ayahCount"] = EXPECTED_AYAHS
    payload["surahCount"] = 114
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    print(json.dumps({
        "input": str(input_path),
        "output": str(output_path),
        "assetVersion": asset_version,
        "ayahs": len(ayahs),
        "words": final_words,
        "openingBasmalaSeparated": opening_count,
        "removedWords": removed_words,
        "fatiha1Preserved": original["ayahs"][0] == payload["ayahs"][0],
        "surah9HasNoOpeningBasmala": not any(item["surah"] == 9 and item["ayah"] == 1 and item["words"][:4] == BASMALA for item in ayahs),
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
