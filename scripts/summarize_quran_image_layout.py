#!/usr/bin/env python3
from __future__ import annotations

import json
import sys
from collections import Counter
from pathlib import Path

path = Path(sys.argv[1])
data = json.loads(path.read_text(encoding="utf-8"))
pages = data["pages"]
print(f"pages={len(pages)} word_glyph_count={data['word_glyph_count']} glyph_ayah_count={data['glyph_ayah_count']} unmatched={len(data['unmatched_page_glyphs'])}")
print("line_count_distribution=" + json.dumps(dict(sorted(Counter(p['line_count'] for p in pages).items()))))
print("ayah_count_distribution=" + json.dumps(dict(sorted(Counter(len(p['ayah_keys']) for p in pages).items()))))
for number in (1, 2, 3, 4, 5, 300, 604):
    page = pages[number - 1]
    line_types = Counter(line["line_type"] for line in page["lines"])
    print(f"page={number} lines={page['line_count']} types={dict(line_types)} ayahs={page['ayah_keys'][:4]}...{page['ayah_keys'][-4:]}")
