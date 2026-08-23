#!/usr/bin/env python3
"""Validate Arabic metadata boundaries against the canonical Quran JSON sequence."""
from __future__ import annotations

import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def main() -> None:
    metadata_path = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("app/src/main/assets/quran/quran-meta-data.xml")
    text_path = Path(sys.argv[2]) if len(sys.argv) > 2 else Path("/tmp/quran_content_remote.json")
    root = ET.parse(metadata_path).getroot()
    rows = root.find("suras")
    if rows is None:
        raise SystemExit("metadata has no suras section")
    suras = list(rows)
    text = json.loads(text_path.read_text(encoding="utf-8"))
    ayahs = text["ayahs"]
    if len(ayahs) != 6236:
        raise SystemExit(f"expected 6236 JSON ayahs, found {len(ayahs)}")
    expected_global = 0
    for row in suras:
        surah = int(row.attrib["index"])
        count = int(row.attrib["ayas"])
        start = int(row.attrib["start"])
        actual = [item for item in ayahs if item["surah"] == surah]
        if start != expected_global:
            raise SystemExit(f"sura {surah} start={start}, expected={expected_global}")
        if len(actual) != count:
            raise SystemExit(f"sura {surah} ayah-count={count}, JSON={len(actual)}")
        for local, item in enumerate(actual, start=1):
            if item["ayah"] != local:
                raise SystemExit(f"sura {surah} has non-contiguous local ayah at {item['ayah']}, expected={local}")
        expected_global += count
    if expected_global != len(ayahs):
        raise SystemExit(f"metadata total={expected_global}, JSON total={len(ayahs)}")
    for section_name, child_name in (("juzs", "juz"), ("hizbs", "quarter"), ("manzils", "manzil"), ("rukus", "ruku"), ("pages", "page"), ("sajdas", "sajda")):
        section = root.find(section_name)
        if section is None:
            raise SystemExit(f"missing section {section_name}")
        for row in section.findall(child_name):
            key = (int(row.attrib["sura"]), int(row.attrib["aya"]))
            if not any(item["surah"] == key[0] and item["ayah"] == key[1] for item in ayahs):
                raise SystemExit(f"{child_name} boundary points outside JSON: {key}")
    print(f"metadata-json-ayah-order-ok={len(ayahs)}")
    print("metadata-boundaries-resolve-against-json=true")
    print("english-sura-fields-ignored=true")


if __name__ == "__main__":
    main()
