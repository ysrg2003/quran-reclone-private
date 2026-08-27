#!/usr/bin/env python3
"""Build word timing data for Mahmoud Khalil Al-Husary from QUL.

QUL's recitation 20 is the ayah-by-ayah Murattal Hafs Husary package. The
endpoint returns [zero_based_word_start, exclusive_word_end, start_ms, end_ms]
segments. This builder rejects merged, missing, duplicated, or malformed word
ranges instead of silently assigning approximate durations.
"""
from __future__ import annotations

import argparse
import json
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

QUL_ENDPOINT = "https://qul.tarteel.ai/api/v1/audio/ayah_segments/20"
HUSARY_AUDIO_BASE = "https://audio-cdn.tarteel.ai/quran/husary"


def load_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def fetch(chapter: int, retries: int = 3) -> dict:
    query = urlencode({"surah": chapter, "from": 1, "per_page": 1000})
    url = f"{QUL_ENDPOINT}?{query}"
    last_error: Exception | None = None
    for attempt in range(retries):
        try:
            request = Request(url, headers={"User-Agent": "quran-memorization-build/1.0"})
            with urlopen(request, timeout=60) as response:
                return json.load(response)
        except (HTTPError, URLError, TimeoutError) as error:
            last_error = error
            if attempt + 1 < retries:
                time.sleep(2 ** attempt)
    raise RuntimeError(f"QUL request failed for surah {chapter}: {last_error}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--canonical", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--cache-dir", type=Path, default=None)
    parser.add_argument("--workers", type=int, default=6)
    args = parser.parse_args()
    if args.workers < 1 or args.workers > 12:
        raise ValueError("workers must be between 1 and 12")
    if args.cache_dir:
        args.cache_dir.mkdir(parents=True, exist_ok=True)

    canonical_payload = load_json(args.canonical)
    canonical = {
        (int(row["surah"]), int(row["ayah"])): row
        for row in canonical_payload["ayahs"]
    }
    all_segments: dict[tuple[int, int], dict] = {}

    def load_or_fetch(chapter: int) -> tuple[int, dict]:
        cache_file = args.cache_dir / f"{chapter:03d}.json" if args.cache_dir else None
        if cache_file and cache_file.is_file():
            return chapter, load_json(cache_file)
        payload = fetch(chapter)
        if cache_file:
            cache_file.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
        return chapter, payload

    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = [pool.submit(load_or_fetch, chapter) for chapter in range(1, 115)]
        fetched = {}
        for future in as_completed(futures):
            chapter, payload = future.result()
            fetched[chapter] = payload
            print(f"fetched surah={chapter} ayahs={len(payload.get('segments', {}))}", file=sys.stderr)

    for chapter in range(1, 115):
        rows = fetched[chapter].get("segments")
        if not isinstance(rows, dict):
            raise ValueError(f"surah {chapter}: response has no segments object")
        for verse_key, row in rows.items():
            key = tuple(map(int, verse_key.split(":")))
            if key in all_segments:
                raise ValueError(f"duplicate QUL ayah {verse_key}")
            all_segments[key] = row

    if set(all_segments) != set(canonical):
        missing = sorted(set(canonical) - set(all_segments))
        extra = sorted(set(all_segments) - set(canonical))
        raise ValueError(f"QUL/canonical key mismatch missing={missing[:5]} extra={extra[:5]}")

    output_ayahs = []
    totals = {"ayahs": 0, "words": 0, "segments": 0, "ayahMarkerSegments": 0}
    for key in sorted(canonical):
        canonical_words = canonical[key]["words"]
        source_segments = all_segments[key].get("segments")
        if not isinstance(source_segments, list):
            raise ValueError(f"{key}: segments is not a list")
        normalized = []
        non_word_segments = []
        raw_source_segments = []
        for segment in source_segments:
            if not isinstance(segment, list) or len(segment) != 4:
                raise ValueError(f"{key}: malformed segment {segment!r}")
            start_word, end_word, start_ms, end_ms = map(int, segment)
            if end_word != start_word + 1:
                raise ValueError(f"{key}: merged segment {segment!r}")
            if end_ms <= start_ms:
                raise ValueError(f"{key}: non-positive segment {segment!r}")
            # A segment beyond the canonical range is an ayah marker or an
            # insertion/repetition event. Preserve it for audit, but never
            # shift it onto a canonical word.
            clean_segment = [start_word, end_word, start_ms, end_ms]
            raw_source_segments.append(clean_segment)
            if start_word < 0 or start_word >= len(canonical_words):
                non_word_segments.append(clean_segment)
                continue
            normalized.append(clean_segment)

        # Check coverage: every canonical word must have at least one segment.
        # Repeated segments for the same word are allowed (reciter repetition).
        actual_indexes = {s[0] for s in normalized}
        expected_indexes = set(range(len(canonical_words)))
        if not expected_indexes.issubset(actual_indexes):
            missing = sorted(expected_indexes - actual_indexes)
            raise ValueError(f"{key}: missing word indexes {missing[:8]}")
        if any(normalized[i][2] > normalized[i + 1][2] for i in range(len(normalized) - 1)):
            raise ValueError(f"{key}: segment start times are not ordered")
        for segment in normalized:
            if segment[3] <= segment[2]:
                raise ValueError(f"{key}: non-positive segment {segment!r}")

        # Keep the first source occurrence as the canonical word window. Any
        # later occurrence is an inserted/repeated recitation event and is
        # retained in sourceSegments for audit, never shifted onto a later
        # canonical word by ordinal indexing.
        primary_by_word: dict[int, list[int]] = {}
        for segment in normalized:
            primary_by_word.setdefault(segment[0], segment)
        canonical_segments = [primary_by_word[i] for i in sorted(primary_by_word)]
        repeated_indexes = sorted(i for i, count in {
            word_index: sum(1 for segment in normalized if segment[0] == word_index)
            for word_index in actual_indexes
        }.items() if count > 1)
        surah, ayah = key
        row = {
            "surah": surah,
            "ayah": ayah,
            "audioUrl": f"{HUSARY_AUDIO_BASE}/{surah:03d}{ayah:03d}.mp3",
            "segments": canonical_segments,
            "sourceSegments": raw_source_segments,
            "sourceSegmentCount": len(source_segments),
            "nonWordSegments": non_word_segments,
            "wordCount": len(canonical_words),
            "quality": {
                "quranUniversalLibraryWordSegments": len(normalized),
                "canonicalWordSegments": len(canonical_segments),
                "repeatedWordIndexes": repeated_indexes,
            },
            "wordSources": ["qul-word-segment"] * len(canonical_segments),
        }
        if non_word_segments:
            totals["ayahMarkerSegments"] += len(non_word_segments)
        output_ayahs.append(row)
        totals["ayahs"] += 1
        totals["words"] += len(canonical_segments)
        totals["segments"] += len(raw_source_segments)

    result = {
        "schemaVersion": 2,
        "reciter": "Husary_Murattal",
        "source": "Quranic Universal Library recitation 112 / QUL recitation id 20",
        "sourceUrl": "https://qul.tarteel.ai/resources/recitation/112",
        "segmentsEndpoint": QUL_ENDPOINT,
        "canonicalText": "app/src/main/assets/quran/word_reference_ayahs.json",
        "purpose": "precomputed one-word timing windows for Mahmoud Khalil Al-Husary ayah audio; not Quran text",
        "runtimePolicy": "runtime reads one QUL [wordStart,wordEnd,startMs,endMs] segment per canonical Word token; it never splits or estimates audio",
        "splitPolicy": "QUL segments are accepted only when every canonical word has at least one one-word segment; the first occurrence becomes the canonical window, later repeated occurrences remain in sourceSegments as audit events, and any out-of-range segment (marker/insertion/repetition) remains in nonWordSegments instead of being shifted onto a canonical word",
        "audioBaseUrl": HUSARY_AUDIO_BASE,
        "stats": totals,
        "ayahs": output_ayahs,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(json.dumps({"output": str(args.output), **totals}, ensure_ascii=False))


if __name__ == "__main__":
    main()
