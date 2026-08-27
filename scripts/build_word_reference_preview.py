"""Build a small first-render preview from the canonical Word-derived JSON.

The preview contains complete canonical ayah objects for the first five mushaf
pages only by default. It is a UI warm-start asset; the full canonical JSON remains the
only source used for validation and recitation after background loading.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--canonical", type=Path, required=True)
    parser.add_argument("--layout", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--layout-output", type=Path, required=True)
    parser.add_argument("--pages", type=int, nargs="+", default=[1, 2, 3, 4, 5])
    args = parser.parse_args()

    canonical = json.loads(args.canonical.read_text(encoding="utf-8"))
    layout = json.loads(args.layout.read_text(encoding="utf-8"))
    selected_pages = set(args.pages)
    wanted_keys: list[str] = []
    wanted_set: set[str] = set()
    preview_pages = []
    for page in layout["pages"]:
        if int(page["page"]) not in selected_pages:
            continue
        preview_pages.append(page)
        for line in page["lines"]:
            for key in line.get("ayahKeys", []):
                if key not in wanted_set:
                    wanted_set.add(key)
                    wanted_keys.append(key)

    by_key = {f'{row["surah"]}:{row["ayah"]}': row for row in canonical["ayahs"]}
    missing = [key for key in wanted_keys if key not in by_key]
    if missing:
        raise ValueError(f"Preview keys missing from canonical JSON: {missing[:5]}")

    payload = {
        "schemaVersion": canonical.get("schemaVersion", 2),
        "source": canonical.get("source", ""),
        "comparisonSource": canonical.get("comparisonSource", ""),
        "purpose": "first-render preview only; full canonical asset remains authoritative",
        "ayahCount": len(wanted_keys),
        "wordCount": sum(len(by_key[key].get("words", [])) for key in wanted_keys),
        "previewPages": sorted(set(args.pages)),
        "canonicalAsset": "quran/word_reference_ayahs.json",
        "ayahs": [by_key[key] for key in wanted_keys],
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    layout_payload = dict(layout)
    layout_payload["pages"] = preview_pages
    layout_payload["previewPages"] = sorted(selected_pages)
    args.layout_output.parent.mkdir(parents=True, exist_ok=True)
    args.layout_output.write_text(json.dumps(layout_payload, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(json.dumps({"output": str(args.output), "layoutOutput": str(args.layout_output), "pages": sorted(selected_pages), "ayahs": len(wanted_keys)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
