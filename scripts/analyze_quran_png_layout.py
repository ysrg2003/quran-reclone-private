#!/usr/bin/env python3
"""Measure deterministic page-layout signals from Quran-PNG without OCR."""
from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image


def ink_mask(image: Image.Image) -> np.ndarray:
    rgba = np.asarray(image.convert("RGBA"))
    alpha = rgba[:, :, 3]
    rgb = rgba[:, :, :3]
    # Quran-PNG pages use transparent/background pixels and dark calligraphy.
    # Keep visible pixels that are not near-white; alpha handles transparent PNGs.
    dark = rgb.mean(axis=2) < 245
    return (alpha > 16) & dark


def bands(projection: np.ndarray, threshold: int) -> list[tuple[int, int]]:
    active = projection >= threshold
    result: list[tuple[int, int]] = []
    start = None
    for index, value in enumerate(active):
        if value and start is None:
            start = index
        elif not value and start is not None:
            result.append((start, index - 1))
            start = None
    if start is not None:
        result.append((start, len(active) - 1))
    return result


def summarize(path: Path) -> dict[str, object]:
    with Image.open(path) as original:
        width, height, mode = original.width, original.height, original.mode
        # A 10x reduction is sufficient for row/column projections and keeps the
        # all-pages scan bounded; exact content comparison uses source metadata.
        image = original.resize((260, 421), Image.Resampling.BOX)
        mask = ink_mask(image)
        row = mask.sum(axis=1)
        col = mask.sum(axis=0)
        row_threshold = max(2, int(image.width * 0.004))
        row_bands = bands(row, row_threshold)
        # Merge tiny gaps caused by detached diacritics into the same visual line.
        merged_bands: list[tuple[int, int]] = []
        for start, end in row_bands:
            if merged_bands and start - merged_bands[-1][1] <= 3:
                merged_bands[-1] = (merged_bands[-1][0], end)
            else:
                merged_bands.append((start, end))
        active_rows = np.flatnonzero(row > 0)
        active_cols = np.flatnonzero(col > 0)
        return {
            "file": path.name,
            "width": width,
            "height": height,
            "mode": mode,
            "alpha_info": bool(original.info.get("transparency") is not None or "A" in mode),
            "ink_pixels": int(mask.sum()),
            "ink_bbox": [int(active_cols[0]), int(active_rows[0]), int(active_cols[-1]), int(active_rows[-1])] if active_rows.size and active_cols.size else None,
            "row_threshold": row_threshold,
            "row_bands": [[int(start), int(end)] for start, end in merged_bands],
            "row_band_count": len(merged_bands),
        }


def main() -> None:
    root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("/home/ubuntu/quran-png-reference")
    pages = [root / f"{index:03d}.png" for index in range(1, 605)]
    missing = [str(path) for path in pages if not path.is_file()]
    if missing:
        raise SystemExit(f"missing pages: {missing[:5]}")
    summaries = [summarize(path) for path in pages]
    if any(row["width"] != 2600 or row["height"] != 4206 for row in summaries):
        raise SystemExit("not all 001..604 pages are 2600x4206")
    print(json.dumps({"page_count": len(summaries), "pages": summaries}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
