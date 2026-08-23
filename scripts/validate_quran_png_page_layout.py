#!/usr/bin/env python3
"""Validate the 604-page structural layout against Quran-PNG-related references.

This validator intentionally proves structural agreement only. It does not claim
pixel or glyph-position equivalence, because the shipped app does not contain the
reference images or a rights-cleared copy of the reference font/layout engine.
"""
from __future__ import annotations

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path
from typing import Any


EXPECTED_PAGE_COUNT = 604
EXPECTED_WIDTH = 2600
EXPECTED_HEIGHT = 4206


def key_string(key: list[int] | tuple[int, int]) -> str:
    return f"{key[0]}:{key[1]}"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--metadata",
        type=Path,
        default=Path("app/src/main/assets/quran/quran-meta-data.xml"),
        help="Arabic Quran metadata XML",
    )
    parser.add_argument(
        "--content",
        type=Path,
        default=Path("/tmp/quran_content_remote.json"),
        help="Canonical Quran content JSON",
    )
    parser.add_argument(
        "--image-layout",
        type=Path,
        default=Path("/tmp/quran_image_layout_full.json"),
        help="quran.com-images extracted page/line/glyph layout JSON",
    )
    parser.add_argument(
        "--png-dir",
        type=Path,
        default=None,
        help="Optional local Quran-PNG clone directory containing 001.png..604.png",
    )
    return parser.parse_args()


def fail(errors: list[str], message: str) -> None:
    errors.append(message)


def parse_metadata_page_sequences(path: Path, content_keys: list[tuple[int, int]]) -> list[list[tuple[int, int]]]:
    root = ET.fromstring(path.read_text(encoding="utf-8"))
    if root.tag != "quran" or root.attrib.get("type") != "metadata":
        raise ValueError(f"unsupported metadata root in {path}")
    suras_parent = root.find("suras")
    pages_parent = root.find("pages")
    if suras_parent is None or pages_parent is None:
        raise ValueError("metadata is missing suras/pages sections")
    sura_starts: dict[int, int] = {}
    for row in suras_parent.findall("sura"):
        sura_starts[int(row.attrib["index"])] = int(row.attrib["start"])
    if len(sura_starts) != 114:
        raise ValueError(f"metadata sura count={len(sura_starts)}, expected=114")
    page_rows = pages_parent.findall("page")
    if len(page_rows) != EXPECTED_PAGE_COUNT:
        raise ValueError(f"metadata page count={len(page_rows)}, expected={EXPECTED_PAGE_COUNT}")
    page_starts: list[tuple[int, int, int]] = []
    for row in page_rows:
        number = int(row.attrib["index"])
        surah = int(row.attrib["sura"])
        ayah = int(row.attrib["aya"])
        global_index = sura_starts[surah] + ayah - 1
        page_starts.append((number, global_index, surah, ayah))
    page_starts.sort()
    sequences: list[list[tuple[int, int]]] = []
    for index, (number, start, _surah, _ayah) in enumerate(page_starts):
        if number != index + 1:
            raise ValueError(f"metadata page index is not contiguous at {number}")
        end = page_starts[index + 1][1] if index + 1 < len(page_starts) else len(content_keys)
        if not 0 <= start <= end <= len(content_keys):
            raise ValueError(f"metadata page {number} has invalid global range {start}:{end}")
        sequences.append(content_keys[start:end])
    return sequences


def validate_image_layout(
    path: Path,
    expected_pages: list[list[tuple[int, int]]],
    content_key_set: set[tuple[int, int]],
    errors: list[str],
) -> Counter[str]:
    layout = json.loads(path.read_text(encoding="utf-8"))
    pages = layout.get("pages")
    if not isinstance(pages, list) or len(pages) != EXPECTED_PAGE_COUNT:
        fail(errors, f"image-layout page count={len(pages) if isinstance(pages, list) else 'invalid'}, expected={EXPECTED_PAGE_COUNT}")
        return Counter()
    line_counts: Counter[str] = Counter()
    mismatches = 0
    for page_index, (expected_page, actual) in enumerate(zip(expected_pages, pages), start=1):
        number = actual.get("page")
        if number != page_index:
            fail(errors, f"image-layout page numbering is not contiguous near page {number}; expected {page_index}")
        actual_keys = [tuple(key) for key in actual.get("ayah_keys", [])]
        if actual_keys != expected_page:
            mismatches += 1
            fail(
                errors,
                f"page {number} ayah sequence mismatch: metadata={','.join(map(key_string, expected_page))} image-layout={','.join(map(key_string, actual_keys))}",
            )
        line_count = actual.get("line_count")
        line_counts[str(line_count)] += 1
        expected_line_count = 8 if number in (1, 2) else 15
        if line_count != expected_line_count:
            fail(errors, f"page {number} line_count={line_count}, expected={expected_line_count}")
        lines = actual.get("lines", [])
        if len(lines) != line_count:
            fail(errors, f"page {number} declares line_count={line_count} but has {len(lines)} line rows")
        flattened: list[tuple[int, int]] = []
        for expected_line_number, line in enumerate(lines, start=1):
            if line.get("line_number") != expected_line_number:
                fail(errors, f"page {number} line numbering is not contiguous at {line.get('line_number')}")
            line_type = line.get("line_type")
            if line_type not in {"sura", "bismillah", "ayah"}:
                fail(errors, f"page {number} line {expected_line_number} has unexpected line_type={line_type!r}")
            line_keys: list[tuple[int, int]] = []
            for item in line.get("items", []):
                if "surah" not in item or "ayah" not in item:
                    continue
                key = (int(item["surah"]), int(item["ayah"]))
                if key not in content_key_set:
                    fail(errors, f"page {number} line {expected_line_number} references missing ayah {key_string(key)}")
                if key not in line_keys:
                    line_keys.append(key)
            if line_type in {"sura", "bismillah"} and line_keys:
                fail(errors, f"page {number} line {expected_line_number} type={line_type} unexpectedly has ayah keys")
            for key in line_keys:
                if key not in flattened:
                    flattened.append(key)
        if flattened != actual_keys:
            fail(errors, f"page {number} line ayah flattening differs from page ayah_keys")
    if mismatches == 0:
        print("page-ayah-sequences=604/604")
    return line_counts


def validate_png_directory(path: Path, errors: list[str]) -> tuple[int, int, int]:
    try:
        from PIL import Image
    except ImportError as exc:
        fail(errors, f"Pillow unavailable for PNG validation: {exc}")
        return 0, 0, 0
    missing = 0
    wrong_dimensions = 0
    wrong_mode = 0
    for page in range(1, EXPECTED_PAGE_COUNT + 1):
        image_path = path / f"{page:03d}.png"
        if not image_path.exists():
            missing += 1
            fail(errors, f"missing Quran-PNG page file: {image_path}")
            continue
        try:
            with Image.open(image_path) as image:
                if image.size != (EXPECTED_WIDTH, EXPECTED_HEIGHT):
                    wrong_dimensions += 1
                    fail(errors, f"{image_path.name} dimensions={image.size}, expected={(EXPECTED_WIDTH, EXPECTED_HEIGHT)}")
                if image.mode not in {"P", "RGBA", "LA", "L", "RGB"}:
                    wrong_mode += 1
                    fail(errors, f"{image_path.name} unexpected PNG mode={image.mode}")
        except Exception as exc:  # noqa: BLE001 - report every bad reference file
            wrong_dimensions += 1
            fail(errors, f"could not read {image_path}: {exc}")
    if missing == wrong_dimensions == wrong_mode == 0:
        print(f"quran-png-files=604/604")
        print(f"quran-png-dimensions=604/{EXPECTED_WIDTH}x{EXPECTED_HEIGHT}")
    return missing, wrong_dimensions, wrong_mode


def main() -> int:
    args = parse_args()
    errors: list[str] = []
    content = json.loads(args.content.read_text(encoding="utf-8"))
    ayahs = content.get("ayahs")
    if not isinstance(ayahs, list):
        print("ERROR: content JSON has no ayahs list", file=sys.stderr)
        return 2
    content_keys = [(int(row["surah"]), int(row["ayah"])) for row in ayahs]
    content_key_set = set(content_keys)
    if len(content_keys) != 6236:
        fail(errors, f"content ayah count={len(content_keys)}, expected=6236")
    if len(content_key_set) != len(content_keys):
        fail(errors, "content contains duplicate surah:ayah keys")
    try:
        expected_pages = parse_metadata_page_sequences(args.metadata, content_keys)
    except Exception as exc:  # noqa: BLE001 - turn malformed reference into explicit failure
        print(f"ERROR: metadata parse failed: {exc}", file=sys.stderr)
        return 2
    if len(expected_pages) != EXPECTED_PAGE_COUNT:
        fail(errors, f"metadata-derived page count={len(expected_pages)}, expected={EXPECTED_PAGE_COUNT}")
    if expected_pages and expected_pages[0] != [(1, ayah) for ayah in range(1, 8)]:
        fail(errors, f"page 1 expected boundary differs: {','.join(map(key_string, expected_pages[0]))}")
    if len(expected_pages) >= 2 and expected_pages[1] != [(2, ayah) for ayah in range(1, 6)]:
        fail(errors, f"page 2 expected boundary differs: {','.join(map(key_string, expected_pages[1]))}")
    if len(expected_pages) >= 3 and expected_pages[2] != [(2, ayah) for ayah in range(6, 17)]:
        fail(errors, f"page 3 expected boundary differs: {','.join(map(key_string, expected_pages[2]))}")
    if len(expected_pages) >= 300 and expected_pages[299] != [(18, ayah) for ayah in range(54, 62)]:
        fail(errors, f"page 300 expected boundary differs: {','.join(map(key_string, expected_pages[299]))}")
    if expected_pages and expected_pages[-1][-1] != (114, 6):
        fail(errors, f"page 604 does not end at 114:6; got {key_string(expected_pages[-1][-1])}")
    if sum(len(page) for page in expected_pages) != len(content_keys):
        fail(errors, "metadata page ranges do not cover exactly all canonical ayahs")
    print(f"metadata-pages={len(expected_pages)}")
    print(f"metadata-ayahs-covered={sum(len(page) for page in expected_pages)}/{len(content_keys)}")
    line_counts = validate_image_layout(args.image_layout, expected_pages, content_key_set, errors)
    print(f"image-layout-line-counts={dict(sorted(line_counts.items(), key=lambda item: int(item[0])))}")
    if args.png_dir is not None:
        validate_png_directory(args.png_dir, errors)
    print(f"content-ayahs={len(content_keys)}")
    image_layout = json.loads(args.image_layout.read_text(encoding="utf-8"))
    print(f"image-layout-word-glyphs={image_layout.get('word_glyph_count')}")
    print(f"image-layout-glyph-ayah-rows={image_layout.get('glyph_ayah_count')}")
    print(f"image-layout-unmatched-glyphs={len(image_layout.get('unmatched_page_glyphs', []))}")
    if errors:
        print(f"comparison-errors={len(errors)}", file=sys.stderr)
        for error in errors[:100]:
            print(f"ERROR: {error}", file=sys.stderr)
        if len(errors) > 100:
            print(f"ERROR: ... {len(errors) - 100} more errors omitted", file=sys.stderr)
        return 1
    print("structural-page-comparison=PASS")
    print("pixel-perfect-visual-equivalence=NOT_CLAIMED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
