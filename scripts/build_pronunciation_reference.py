#!/usr/bin/env python3
"""Build speech-only pronunciation metadata from Word-canonical Quran words.

The supplied Word-derived asset is the only source for canonical word spelling.
Legacy quran_content is read only to establish the old audio ordinal length in
six documented segmentation-delta ayahs; it never supplies displayed text or
spoken forms.
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

ARABIC_MARKS = re.compile(r"[\u0610-\u061a\u064b-\u065f\u06d6-\u06ed\u0670]")
SILENT_MARKER = "۟"
SMALL_ALIF = "ٰ"
FATHA = "َ"
DAMMA = "ُ"
KASRA = "ِ"
BASE_LETTERS = "ابتثجحخدذرزسشصضطظعغفقكلمنهوي"
MUQATTAAT_SPOKEN = {
    "الٓمٓ": ["الف", "لام", "ميم"],
    "الٓمٓصٓ": ["الف", "لام", "ميم", "صاد"],
    "الٓر": ["الف", "لام", "را"],
    "الٓمٓر": ["الف", "لام", "ميم", "را"],
    "كٓهيعٓصٓ": ["كاف", "ها", "يا", "عين", "صاد"],
    "طه": ["طا", "ها"],
    "طسٓمٓ": ["طا", "سين", "ميم"],
    "طسٓ": ["طا", "سين"],
    "يسٓ": ["يا", "سين"],
    "صٓ": ["صاد"],
    "حمٓ": ["حا", "ميم"],
    "عٓسٓقٓ": ["عين", "سين", "قاف"],
    "قٓ": ["قاف"],
    "نٓ": ["نون"],
}

# Each list describes canonical Word indices -> legacy quran-align word indices.
# Duplicated indices mean two Word words share one legacy merged audio token;
# multiple indices mean one Word word spans several legacy split tokens.
SPLIT_CANONICAL_TO_LEGACY = {
    (2, 181): (2, 2),
    (8, 6): (3, 3),
    (13, 37): (7, 7),
}
MERGED_CANONICAL_TO_LEGACY = {
    (15, 7): (0, (0, 1)),
    (27, 20): (3, (3, 4)),
    (36, 22): (0, (0, 1)),
}


def normalize_spoken_with_madd(value: str) -> tuple[str, list[int]]:
    output: list[str] = []
    madd_positions: list[int] = []
    last_haraka = ""
    chars = list(value)
    index = 0
    while index < len(chars):
        char = chars[index]
        if char in {FATHA, DAMMA, KASRA}:
            last_haraka = char
            index += 1
            continue
        # Word's final alif followed by sukun is silent in forms such as
        # كَفَرُواْ; do not remove an ordinary pronounced alif or dagger alif.
        if char == "ا" and index + 1 < len(chars) and chars[index + 1] in {"ْ", "ۡ", SILENT_MARKER}:
            index += 1
            last_haraka = ""
            continue
        if char == SILENT_MARKER:
            if output and output[-1] in BASE_LETTERS:
                removed = len(output) - 1
                output.pop()
                madd_positions = [
                    position - 1 if position > removed else position
                    for position in madd_positions
                    if position != removed
                ]
            index += 1
            last_haraka = ""
            continue
        if char == "و" and index + 1 < len(chars) and chars[index + 1] == SMALL_ALIF:
            output.append("ا")
            madd_positions.append(len(output) - 1)
            index += 2
            last_haraka = ""
            continue
        if char == "ى" and index + 1 < len(chars) and chars[index + 1] == SMALL_ALIF:
            output.append("ى")
            madd_positions.append(len(output) - 1)
            index += 2
            last_haraka = ""
            continue
        if char == SMALL_ALIF:
            output.append("ا")
            madd_positions.append(len(output) - 1)
            index += 1
            last_haraka = ""
            continue
        if ARABIC_MARKS.fullmatch(char):
            index += 1
            continue
        replacement = {
            "ٱ": "ا", "آ": "ا", "أ": "ا", "إ": "ا",
            "ؤ": "و", "ئ": "ي", "ء": "",
        }.get(char, char)
        if replacement:
            output.append(replacement)
            if (
                (char == "ا" and last_haraka == FATHA)
                or (char == "و" and last_haraka == DAMMA)
                or (char == "ي" and last_haraka == KASRA)
            ):
                madd_positions.append(len(output) - 1)
        if char not in "ًٌٍّْٓـ":
            last_haraka = ""
        index += 1
    return re.sub(r"\s+", " ", "".join(output)).strip(), sorted(set(madd_positions))


def legacy_groups(key: tuple[int, int], canonical_count: int, legacy_count: int) -> list[list[int]]:
    if key in SPLIT_CANONICAL_TO_LEGACY:
        first, second = SPLIT_CANONICAL_TO_LEGACY[key]
        groups = [[i] for i in range(first)] + [[second], [second]]
        groups.extend([[i] for i in range(second + 1, legacy_count)])
    elif key in MERGED_CANONICAL_TO_LEGACY:
        canonical_index, merged = MERGED_CANONICAL_TO_LEGACY[key]
        groups = [[i] for i in range(canonical_index)] + [list(merged)]
        groups.extend([[i] for i in range(merged[-1] + 1, legacy_count)])
    else:
        groups = [[i] for i in range(canonical_count)]
    if len(groups) != canonical_count:
        raise ValueError(f"legacy map length mismatch at {key}: {len(groups)} != {canonical_count}")
    if any(any(i < 0 or i >= legacy_count for i in group) for group in groups):
        raise ValueError(f"legacy map out of range at {key}")
    return groups


def load(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--word-reference", type=Path, default=Path("app/src/main/assets/quran/word_reference_ayahs.json"))
    parser.add_argument("--legacy-content", type=Path, default=Path("/home/ubuntu/quran-memorization-assets/quran/quran_content.json"))
    parser.add_argument("--timings", type=Path, default=Path("/home/ubuntu/quran-memorization-assets/timings/Alafasy_128kbps.json"))
    parser.add_argument("--out", type=Path, default=Path("app/src/main/assets/quran/pronunciation_reference.json"))
    args = parser.parse_args()

    word_root = load(args.word_reference)
    legacy = load(args.legacy_content)
    timing_rows = {(row["surah"], row["ayah"]): row for row in load(args.timings)}
    legacy_by_key = {(row["surah"], row["ayah"]): row for row in legacy["ayahs"]}
    output_ayahs = []
    total_words = 0
    safe_segment_links = 0
    mapped_delta_keys = set()

    for source in word_root["ayahs"]:
        key = (source["surah"], source["ayah"])
        legacy_row = legacy_by_key[key]
        source_words = source["words"]
        legacy_words = legacy_row.get("words", [])
        groups = legacy_groups(key, len(source_words), len(legacy_words))
        if len(source_words) != len(legacy_words):
            mapped_delta_keys.add(key)
        segments = timing_rows.get(key, {}).get("segments", [])
        words = []
        for index, word in enumerate(source_words):
            spoken, madd_positions = normalize_spoken_with_madd(word["text"])
            spoken_tokens = MUQATTAAT_SPOKEN.get(word["text"], [spoken])
            item = {
                "index": index,
                "spoken": " ".join(spoken_tokens),
                "spokenTokens": spoken_tokens,
                "maddPositions": madd_positions,
                "legacyWordIndices": groups[index],
            }
            segment_indices = [i for i in groups[index] if i < len(segments)]
            if segment_indices:
                first = segments[min(segment_indices)]
                last = segments[max(segment_indices)]
                item.update({
                    "audioStartMs": first[2],
                    "audioEndMs": last[3],
                    "audioWordStart": first[0],
                    "audioWordEnd": last[1],
                })
                safe_segment_links += 1
            words.append(item)
        total_words += len(words)
        output_ayahs.append({
            "surah": source["surah"],
            "ayah": source["ayah"],
            "audioFile": f"{source['surah']:03d}{source['ayah']:03d}.mp3",
            "audioSegmentCount": len(segments),
            "spoken": " ".join(item["spoken"] for item in words),
            "words": words,
        })

    expected_delta_keys = set(SPLIT_CANONICAL_TO_LEGACY) | set(MERGED_CANONICAL_TO_LEGACY)
    if mapped_delta_keys != expected_delta_keys:
        raise SystemExit(f"segmentation map keys mismatch: {sorted(mapped_delta_keys ^ expected_delta_keys)}")
    payload = {
        "schemaVersion": 2,
        "source": "Word-canonical text with Alafasy/quran-align timings as a separate audio layer",
        "canonicalTextSource": "UthmanicHafs_V20.doc via word_reference_ayahs.json",
        "audioDirectory": "audio/Alafasy_128kbps",
        "purpose": "speech comparison and audio timing only; never used to render or rewrite Quran text",
        "pronunciationPolicy": "spoken forms and valid madd-carrier positions are derived from canonical Word words; legacy audio indices are explicit reconciliation metadata",
        "ayahCount": len(output_ayahs),
        "wordCount": total_words,
        "safeOrdinalAudioSegmentLinks": safe_segment_links,
        "legacySegmentationReconciliation": {
            "mappedAyahs": [{"surah": key[0], "ayah": key[1]} for key in sorted(mapped_delta_keys)],
            "policy": "split Word words share a legacy segment; merged Word words span the listed legacy segments",
        },
        "ayahs": output_ayahs,
    }
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    print(f"wrote={args.out}")
    print(f"ayahs={len(output_ayahs)}")
    print(f"words={total_words}")
    print(f"mapped_segmentation_deltas={len(mapped_delta_keys)}")
    print(f"safe_audio_links={safe_segment_links}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
