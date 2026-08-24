"""Summarize the deterministic quran.com-images word comparison report."""
from __future__ import annotations

import json
import sys
from collections import Counter
from pathlib import Path
from typing import Any


def difference_pair(row: dict[str, Any]) -> tuple[str, str]:
    difference = row.get("difference") or {}
    left, right = difference.get("app"), difference.get("reference")
    return json.dumps(left, ensure_ascii=False), json.dumps(right, ensure_ascii=False)


def category_for_pair(left: str, right: str) -> str:
    combined = left + right
    if "ء" in combined or "ـٔ" in combined:
        return "hamza-rendering"
    if "ۦ" in combined or "ۥ" in combined or "ۧ" in combined:
        return "small-waw-ya-or-mark"
    if "ٰ" in combined or "ٓ" in combined:
        return "small-alif-or-maddah"
    return "other-orthography"


def main() -> int:
    path = Path(sys.argv[1] if len(sys.argv) > 1 else "/tmp/quran_word_text_comparison.json")
    report = json.loads(path.read_text(encoding="utf-8"))
    true_rows = report.get("true_text_mismatches", [])
    normalized_rows = report.get("normalized_only_cases", [])
    true_categories = Counter()
    normalized_categories = Counter()
    pairs = Counter()
    for row in true_rows:
        left, right = difference_pair(row)
        true_categories[category_for_pair(left, right)] += 1
        pairs[(left, right)] += 1
    for row in normalized_rows:
        left, right = difference_pair(row)
        normalized_categories[category_for_pair(left, right)] += 1
        pairs[(left, right)] += 1
    print("classification-counts=" + repr(report.get("classification_counts", {})))
    print("word-delta-explanation=" + repr(report.get("word_delta_explanation", {})))
    print("true-mismatch-difference-categories=" + repr(dict(true_categories)))
    print("normalized-only-difference-categories=" + repr(dict(normalized_categories)))
    print(f"recorded-non-exact-cases={len(true_rows) + len(normalized_rows) + len(report.get('expected_basmala_cases', [])) + len(report.get('segmentation_cases', []))}")
    print("top-difference-pairs=")
    for (left, right), count in pairs.most_common(30):
        print(f"{count}\t{left}\t{right}")
    if true_rows:
        print("unresolved-true-mismatch-keys=")
        for row in true_rows:
            print(f"{row['surah']}:{row['ayah']} page={row['page']} difference={row['difference']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
