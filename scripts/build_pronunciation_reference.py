#!/usr/bin/env python3
"""Build the app's audio-backed pronunciation reference.

The visual Quran source remains UthmanicHafs_V20.doc. This separate asset is
only for speech comparison and audio provenance. It keeps the pronunciation
spelling, the complete ayah pronunciation sentence, and the available aligned
Sheikh Alafasy timing segment when a safe ordinal mapping exists. It never
rewrites the visual/reference Quran text.
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

ARABIC_MARKS = re.compile(r"[\u0610-\u061a\u064b-\u065f\u06d6-\u06ed]")
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


def normalize_spoken_with_madd(value: str) -> tuple[str, list[int]]:
    """Return comparison spelling plus positions of valid long-vowel carriers.

    Positions are measured in the normalized word without whitespace. A long
    vowel is recorded only for alif after fatha, waw after damma, ya after
    kasra, or an explicit small-alif carrier. The silent round-zero marker
    removes its preceding carrier from comparison spelling.
    """
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
            # Alif maqsurah with dagger alif is one pronounced long-vowel
            # carrier and remains ى in ordinary Arabic speech spelling.
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
    spoken = re.sub(r"\s+", " ", "".join(output)).strip()
    valid_positions = sorted(set(position for position in madd_positions if 0 <= position < len(spoken)))
    return spoken, valid_positions


def load(path: Path):
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--assets", type=Path, default=Path("/home/ubuntu/quran-memorization-assets"))
    parser.add_argument("--out", type=Path, default=Path("app/src/main/assets/quran/pronunciation_reference.json"))
    args = parser.parse_args()

    content = load(args.assets / "quran/quran_content.json")
    timing_rows = {
        (row["surah"], row["ayah"]): row
        for row in load(args.assets / "timings/Alafasy_128kbps.json")
    }
    output_ayahs = []
    total_words = 0
    safe_segment_links = 0
    for source in content["ayahs"]:
        key = (source["surah"], source["ayah"])
        timing = timing_rows.get(key, {})
        segments = timing.get("segments", [])
        source_words = source.get("words", [])
        words = []
        for index, word in enumerate(source_words):
            spoken, madd_positions = normalize_spoken_with_madd(word["text"])
            spoken_tokens = MUQATTAAT_SPOKEN.get(word["text"], [spoken])
            item = {
                "index": index,
                "spoken": " ".join(spoken_tokens),
                "spokenTokens": spoken_tokens,
                "maddPositions": madd_positions,
            }
            # A segment is attached only when its ordinal relationship is
            # unambiguous. TimingWordMapper remains authoritative for playback
            # in Android when quran-align has gaps/merges.
            if len(segments) == len(source_words) and index < len(segments):
                segment = segments[index]
                item["audioStartMs"] = segment[2]
                item["audioEndMs"] = segment[3]
                item["audioWordStart"] = segment[0]
                item["audioWordEnd"] = segment[1]
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

    args.out.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "schemaVersion": 1,
        "source": "Alafasy_128kbps ayah recordings with quran-align timings",
        "audioDirectory": "audio/Alafasy_128kbps",
        "purpose": "speech comparison only; never used to render or rewrite Quran text",
        "pronunciationPolicy": "Arabic comparison spelling derived from canonical Uthmanic word text; only recorded valid madd-carrier positions may be elongated; ayah sentence retained for contextual matching",
        "ayahCount": len(output_ayahs),
        "wordCount": total_words,
        "safeOrdinalAudioSegmentLinks": safe_segment_links,
        "ayahs": output_ayahs,
    }
    with args.out.open("w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, separators=(",", ":"))
        handle.write("\n")
    print(f"wrote={args.out}")
    print(f"ayahs={len(output_ayahs)}")
    print(f"words={total_words}")
    print(f"safe_ordinal_audio_segment_links={safe_segment_links}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
