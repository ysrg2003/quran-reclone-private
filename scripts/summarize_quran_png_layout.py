#!/usr/bin/env python3
from __future__ import annotations

import json
import sys
from collections import Counter
from pathlib import Path

path = Path(sys.argv[1])
data = json.loads(path.read_text(encoding="utf-8"))
pages = data["pages"]
print(f"pages={len(pages)}")
print(f"dimensions={sorted({(p['width'], p['height']) for p in pages})}")
print(f"row_band_counts={dict(sorted(Counter(p['row_band_count'] for p in pages).items()))}")
print(f"alpha_info={dict(Counter(p['alpha_info'] for p in pages))}")
for number in (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 300, 604):
    page = pages[number - 1]
    print(f"page={number} bands={page['row_band_count']} bbox={page['ink_bbox']} rows={page['row_bands']}")
