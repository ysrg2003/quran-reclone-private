from __future__ import annotations

import re
from collections import Counter, defaultdict
from pathlib import Path

SQL = Path('/home/ubuntu/tarteel-android-offline/sources/quran.com-images/sql/02-database.sql')
text = SQL.read_text(encoding='utf-8', errors='replace')

def section(table: str) -> str:
    start = text.index(f'INSERT INTO `{table}` VALUES ')
    end = text.index(';', start)
    return text[start:end]

def tuples(payload: str) -> list[str]:
    return re.findall(r'\(([^()]*)\)', payload)

types: dict[int, str] = {}
for row in tuples(section('glyph_type')):
    parts = row.split(',', 2)
    if len(parts) >= 2:
        types[int(parts[0])] = parts[1].strip().strip("'")

glyphs: dict[int, tuple[int, int, int, int | None, int | None]] = {}
for row in tuples(section('glyph')):
    parts = row.split(',', 6)
    if len(parts) >= 6:
        glyph_id = int(parts[0])
        glyph_code = int(parts[2])
        page = int(parts[3])
        type_id = None if parts[4].strip().lower() == 'null' else int(parts[4])
        meta = None if parts[5].strip().lower() == 'null' else int(parts[5])
        glyphs[glyph_id] = (glyph_code, page, type_id or -1, meta or -1, 0)

marker_counts = Counter(types.get(row[2], f'type:{row[2]}') for row in glyphs.values() if types.get(row[2]) != 'word')
print('non-word glyph counts:', dict(marker_counts))
print('glyph type names:', types)
for type_id, name in sorted(types.items()):
    if name in {'word', 'ayah'}:
        continue
    codes = Counter((row[0], row[3]) for row in glyphs.values() if row[2] == type_id)
    print(f'{name} codes/meta:', dict(codes))

# glyph_ayah rows identify an ayah-local position for glyphs that are tied to an ayah.
ayah_by_glyph: defaultdict[int, list[tuple[int, int, int]]] = defaultdict(list)
for row in tuples(section('glyph_ayah')):
    parts = row.split(',')
    if len(parts) >= 5:
        ayah_by_glyph[int(parts[1])].append((int(parts[2]), int(parts[3]), int(parts[4])))

samples: defaultdict[str, list[tuple[int, int, int, int, int, int]]] = defaultdict(list)
for glyph_id, (code, page, type_id, meta, _) in glyphs.items():
    type_name = types.get(type_id, f'type:{type_id}')
    if type_name == 'word':
        continue
    for surah, ayah, pos in ayah_by_glyph.get(glyph_id, []):
        if len(samples[type_name]) < 12:
            samples[type_name].append((glyph_id, code, page, meta, surah, ayah * 1000 + pos))

for name in sorted(samples):
    print(name, samples[name])
