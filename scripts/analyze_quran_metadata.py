#!/usr/bin/env python3
"""Analyze the Arabic-relevant structure of the supplied Tanzil Quran metadata XML."""
from __future__ import annotations

import json
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path


def attr_int(node: ET.Element, name: str) -> int:
    return int(node.attrib[name])


def key(node: ET.Element) -> tuple[int, int]:
    return attr_int(node, "sura"), attr_int(node, "aya")


def validate_boundary_rows(rows: list[ET.Element], label: str) -> dict[str, object]:
    indices = [attr_int(row, "index") for row in rows]
    locations = [key(row) for row in rows]
    index_ok = indices == list(range(1, len(rows) + 1))
    location_ok = all(locations[i] < locations[i + 1] for i in range(len(locations) - 1))
    return {
        "count": len(rows),
        "index_ok": index_ok,
        "strict_location_order_ok": location_ok,
        "first": locations[0] if locations else None,
        "last": locations[-1] if locations else None,
    }


def main() -> None:
    source = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("/home/ubuntu/upload/quran-meta-data.xml")
    root = ET.parse(source).getroot()
    suras = root.find("suras")
    juzs = root.find("juzs")
    hizbs = root.find("hizbs")
    manzils = root.find("manzils")
    rukus = root.find("rukus")
    pages = root.find("pages")
    sajdas = root.find("sajdas")
    if any(node is None for node in (suras, juzs, hizbs, manzils, rukus, pages, sajdas)):
        raise SystemExit("metadata is missing one or more expected Arabic structural sections")

    sura_rows = list(suras)
    juz_rows = list(juzs)
    quarter_rows = list(hizbs)
    manzil_rows = list(manzils)
    ruku_rows = list(rukus)
    page_rows = list(pages)
    sajda_rows = list(sajdas)

    if len(sura_rows) != 114 or [attr_int(row, "index") for row in sura_rows] != list(range(1, 115)):
        raise SystemExit("suras must contain indexes 1..114")
    if sum(attr_int(row, "ayas") for row in sura_rows) != 6236:
        raise SystemExit("sura ayah totals do not equal 6236")
    starts = [attr_int(row, "start") for row in sura_rows]
    if starts[0] != 0 or any(starts[i] + attr_int(sura_rows[i], "ayas") != starts[i + 1] for i in range(len(starts) - 1)):
        raise SystemExit("sura starts are not contiguous zero-based global ayah offsets")
    if starts[-1] + attr_int(sura_rows[-1], "ayas") != 6236:
        raise SystemExit("last sura does not end at global ayah offset 6236")
    if len(page_rows) != 604 or [attr_int(row, "index") for row in page_rows] != list(range(1, 605)):
        raise SystemExit("pages must contain indexes 1..604")
    if len(juz_rows) != 30 or len(quarter_rows) != 240 or len(manzil_rows) != 7 or len(sajda_rows) != 15:
        raise SystemExit("unexpected Quran metadata section count")

    type_counts = Counter(row.attrib["type"] for row in sura_rows)
    sajda_type_counts = Counter(row.attrib["type"] for row in sajda_rows)
    arabic_sura_names = [row.attrib["name"] for row in sura_rows]
    non_arabic_name_chars = sorted({char for name in arabic_sura_names for char in name if not ("\u0600" <= char <= "\u06ff" or char.isspace())})
    report = {
        "source": str(source),
        "root": {"type": root.attrib.get("type"), "version": root.attrib.get("version"), "license": root.attrib.get("license")},
        "sections": {
            "suras": {"count": len(sura_rows), "ayah_total": sum(attr_int(row, "ayas") for row in sura_rows), "type_counts": dict(type_counts), "arabic_name_chars_outside_arabic_block": non_arabic_name_chars},
            "juzs": validate_boundary_rows(juz_rows, "juzs"),
            "quarters": validate_boundary_rows(quarter_rows, "quarters"),
            "manzils": validate_boundary_rows(manzil_rows, "manzils"),
            "rukus": validate_boundary_rows(ruku_rows, "rukus"),
            "pages": validate_boundary_rows(page_rows, "pages"),
            "sajdas": {"count": len(sajda_rows), "type_counts": dict(sajda_type_counts)},
        },
        "arabic_fields_used": [
            "sura.index", "sura.ayas", "sura.start", "sura.name", "sura.type", "sura.order", "sura.rukus",
            "juz.index/sura/aya", "quarter.index/sura/aya", "manzil.index/sura/aya", "ruku.index/sura/aya",
            "page.index/sura/aya", "sajda.index/sura/aya/type",
        ],
        "ignored_fields": ["sura.tname", "sura.ename", "suras.alias", "juzs.alias", "hizbs.alias", "manzils.alias", "rukus.alias", "pages.alias"],
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
