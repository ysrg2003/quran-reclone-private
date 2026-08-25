#!/usr/bin/env python3
"""Audit the local recitation inputs before building pronunciation metadata.

This script is read-only. It reports whether the available Sheikh Alafasy
recordings and quran-align segments provide one segment per Quran word, and
whether a Word-reference ayah can be mapped without changing its display text.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
from collections import Counter


def load_json(path: Path):
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--assets", type=Path, default=Path("/home/ubuntu/quran-memorization-assets"))
    parser.add_argument("--repo", type=Path, default=Path("/home/ubuntu/quran-memorization-online"))
    args = parser.parse_args()

    content = load_json(args.assets / "quran/quran_content.json")
    timings = load_json(args.assets / "timings/Alafasy_128kbps.json")
    word_layout = load_json(args.repo / "app/src/main/assets/quran/word_reference_layout.json")

    ayahs = {(row["surah"], row["ayah"]): row for row in content["ayahs"]}
    timing_rows = {(row["surah"], row["ayah"]): row for row in timings}
    word_keys = []
    for page in word_layout["pages"]:
        for line in page["lines"]:
            word_keys.extend(line.get("ayahKeys", []))
    word_keys = sorted(set(tuple(key) for key in word_keys))

    mismatches = []
    stats = Counter()
    for key, ayah in sorted(ayahs.items()):
        words = ayah.get("words", [])
        row = timing_rows.get(key)
        segments = row.get("segments", []) if row else []
        stats["ayahs"] += 1
        stats["words"] += len(words)
        stats["segments"] += len(segments)
        if row is None:
            stats["missing_timing_rows"] += 1
            mismatches.append((key, len(words), 0, "missing timing row"))
            continue
        if len(words) != len(segments):
            stats["segment_count_mismatch"] += 1
            mismatches.append((key, len(words), len(segments), "word/segment count differs"))
        if row.get("stats", {}).get("deletions", 0) or row.get("stats", {}).get("insertions", 0) or row.get("stats", {}).get("transpositions", 0):
            stats["nonzero_alignment_stats"] += 1

    audio_root = args.assets / "audio/Alafasy_128kbps"
    audio_count = len(list(audio_root.glob("*.mp3"))) if audio_root.is_dir() else 0
    word_reference_ayahs = set(word_keys)

    print(f"content_ayahs={stats['ayahs']}")
    print(f"content_words={stats['words']}")
    print(f"timing_segments={stats['segments']}")
    print(f"timing_rows={len(timing_rows)}")
    print(f"missing_timing_rows={stats['missing_timing_rows']}")
    print(f"segment_count_mismatch={stats['segment_count_mismatch']}")
    print(f"nonzero_alignment_stats={stats['nonzero_alignment_stats']}")
    print(f"audio_mp3_files={audio_count}")
    print(f"word_reference_pages={word_layout.get('pageCount')}")
    print(f"word_reference_lines={word_layout.get('lineCount')}")
    print(f"word_reference_ayah_keys={len(word_reference_ayahs)}")
    print(f"word_reference_missing_content_ayahs={len(word_reference_ayahs - set(ayahs))}")
    print("first_mismatches:")
    for key, words, segments, reason in mismatches[:40]:
        print(f"  {key[0]}:{key[1]} words={words} segments={segments} reason={reason}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
