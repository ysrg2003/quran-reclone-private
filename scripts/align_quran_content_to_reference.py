"""Align every eligible application Quran token to quran.com-images word_arabic.

The four opening basmala tokens that are intentionally represented as a separate
visual line in quran.com-images remain in the application ayah. All other app
word objects are aligned to the reference token sequence, including raw
orthographic and diacritic differences and the three verified compound-token
cases. The script emits an audit report and never edits files implicitly.
"""
from __future__ import annotations

import argparse
import importlib.util
import json
from pathlib import Path
from typing import Any

from validate_quran_word_text_against_image_layout import (
    BASMALA_NORMALIZED,
    equivalent_token,
    load_word_arabic,
    load_words,
    normalize_for_match,
    normalize_with_small_alif,
    sequence_signature,
)
from validate_full_quran_matching import equivalent as source_match_equivalent


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--content", type=Path, required=True)
    parser.add_argument("--sql", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--audit", type=Path, required=True)
    return parser.parse_args()


def reference_tokens(sql: str) -> dict[tuple[int, int], list[str]]:
    values = load_word_arabic(sql)
    result: dict[tuple[int, int], list[str]] = {}
    for row in load_words(sql):
        result.setdefault((row["surah"], row["ayah"]), []).append(values[row["word_arabic_id"]])
    return result


def is_basmala_prefix(surah: int, ayah: int, words: list[dict[str, Any]]) -> bool:
    return surah > 1 and ayah == 1 and len(words) >= 4 and [
        normalize_with_small_alif(str(word["text"])) for word in words[:4]
    ] == BASMALA_NORMALIZED


def split_app_segments(app_words: list[dict[str, Any]], reference: list[str]) -> list[list[dict[str, Any]]]:
    """Map one or more app word objects to each reference token by signature."""
    segments: list[list[dict[str, Any]]] = []
    cursor = 0
    for token in reference:
        match: list[dict[str, Any]] | None = None
        for end in range(cursor + 1, len(app_words) + 1):
            candidate = app_words[cursor:end]
            if sequence_signature([str(item["text"]) for item in candidate]) == sequence_signature([token]):
                match = candidate
                cursor = end
                break
        if match is None:
            raise ValueError(f"cannot align app words at cursor={cursor} to reference token={token!r}")
        segments.append(match)
    if cursor != len(app_words):
        raise ValueError(f"unconsumed app words after reference alignment: {len(app_words) - cursor}")
    return segments


def aligned_word(segment: list[dict[str, Any]], reference_text: str, index: int) -> dict[str, Any]:
    match_text = "".join(str(item.get("matchText", "")) for item in segment)
    if not source_match_equivalent(reference_text, match_text):
        match_text = normalize_for_match(reference_text)
    return {"index": index, "text": reference_text, "matchText": match_text}


def main() -> int:
    args = parse_args()
    content = json.loads(args.content.read_text(encoding="utf-8"))
    refs = reference_tokens(args.sql.read_text(encoding="utf-8", errors="replace"))
    changed_tokens: list[dict[str, Any]] = []
    changed_match_text: list[dict[str, Any]] = []
    merged_segments: list[dict[str, Any]] = []
    basmala_cases: list[str] = []
    output_ayahs: list[dict[str, Any]] = []

    for ayah in content["ayahs"]:
        surah, ayah_number = int(ayah["surah"]), int(ayah["ayah"])
        current_key = (surah, ayah_number)
        if current_key not in refs:
            raise ValueError(f"missing reference ayah {current_key}")
        old_words = list(ayah["words"])
        prefix = old_words[:4] if is_basmala_prefix(surah, ayah_number, old_words) else []
        core = old_words[len(prefix):]
        if prefix:
            basmala_cases.append(f"{surah}:{ayah_number}")
        segments = split_app_segments(core, refs[current_key])
        new_core: list[dict[str, Any]] = []
        for ref_index, (segment, ref_text) in enumerate(zip(segments, refs[current_key])):
            old_texts = [str(item["text"]) for item in segment]
            if len(segment) > 1:
                merged_segments.append({
                    "surah": surah, "ayah": ayah_number, "reference_index": ref_index,
                    "app_text": old_texts, "reference_text": ref_text,
                })
            old_match = "".join(str(item.get("matchText", "")) for item in segment)
            new_word = aligned_word(segment, ref_text, ref_index)
            if len(segment) == 1 and old_texts[0] != ref_text:
                changed_tokens.append({
                    "surah": surah, "ayah": ayah_number, "index": ref_index,
                    "app": old_texts[0], "reference": ref_text,
                })
            if old_match != new_word["matchText"]:
                changed_match_text.append({
                    "surah": surah, "ayah": ayah_number, "index": ref_index,
                    "old": old_match, "new": new_word["matchText"],
                })
            new_core.append(new_word)
        new_words = [
            {"index": index, "text": word["text"], "matchText": word["matchText"]}
            for index, word in enumerate(prefix + new_core)
        ]
        ayah_copy = dict(ayah)
        ayah_copy["words"] = new_words
        ayah_copy["text"] = " ".join(word["text"] for word in new_words)
        ayah_copy["matchText"] = " ".join(word["matchText"] for word in new_words)
        output_ayahs.append(ayah_copy)

    result = dict(content)
    result["canonicalTextUnchanged"] = False
    result["ayahs"] = output_ayahs
    args.output.write_text(json.dumps(result, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    audit = {
        "content_ayah_count": len(output_ayahs),
        "basmala_case_count": len(basmala_cases),
        "basmala_cases": basmala_cases,
        "raw_token_replacements": len(changed_tokens),
        "raw_token_replacement_cases": changed_tokens,
        "merged_segments": len(merged_segments),
        "merged_segment_cases": merged_segments,
        "match_text_replacements": len(changed_match_text),
        "match_text_replacement_cases": changed_match_text,
        "reference_word_rows": sum(len(tokens) for tokens in refs.values()),
        "output_word_count": sum(len(ayah["words"]) for ayah in output_ayahs),
    }
    args.audit.write_text(json.dumps(audit, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({
        "content_ayah_count": audit["content_ayah_count"],
        "basmala_case_count": audit["basmala_case_count"],
        "raw_token_replacements": audit["raw_token_replacements"],
        "merged_segments": audit["merged_segments"],
        "match_text_replacements": audit["match_text_replacements"],
        "output_word_count": audit["output_word_count"],
    }, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
