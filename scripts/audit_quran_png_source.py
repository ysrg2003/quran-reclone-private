from __future__ import annotations

import json
import re
from pathlib import Path

SOURCE_SQL = Path('/home/ubuntu/quran-source-cache/quran.com-images/sql/02-database.sql')
CONTENT_JSON = Path('/home/ubuntu/quran-memorization-assets/quran/quran_content.json')
OUT = Path('/home/ubuntu/quran-memorization-online/docs/quran_png_source_diagnostic.json')


def payload(sql: str, table: str) -> str:
    matches = re.findall(rf"INSERT INTO\s+`{table}`\s+VALUES\s+(.*?);", sql, re.IGNORECASE | re.DOTALL)
    if not matches:
        raise RuntimeError(f'No INSERT payload for {table}')
    return '\n'.join(matches)


def rows(payload_text: str) -> list[list[str | None]]:
    result: list[list[str | None]] = []
    for raw in re.findall(r'\(([^()]*)\)', payload_text):
        fields = []
        for field in re.split(r',(?=(?:[^\']*\'[^\']*\')*[^\']*$)', raw):
            field = field.strip()
            if field.upper() == 'NULL':
                fields.append(None)
            elif len(field) >= 2 and field[0] == "'" and field[-1] == "'":
                fields.append(field[1:-1].replace("\\'", "'"))
            else:
                fields.append(field)
        result.append(fields)
    return result


def as_int(value: str | None) -> int | None:
    return int(value) if value is not None else None


sql = SOURCE_SQL.read_text(encoding='utf-8', errors='replace')
word_rows = rows(payload(sql, 'word'))
glyph_rows = rows(payload(sql, 'glyph'))
line_rows = rows(payload(sql, 'glyph_page_line'))
try:
    bbox_rows = rows(payload(sql, 'glyph_page_line_bbox'))
except RuntimeError:
    bbox_rows = []
content = json.loads(CONTENT_JSON.read_text(encoding='utf-8'))
content_words = {
    (ayah['surah'], ayah['ayah'], word['index'] + 1): word
    for ayah in content['ayahs']
    for word in ayah.get('words', [])
}
word_by_glyph = {as_int(row[1]): row for row in word_rows if len(row) > 1 and row[1] is not None}
glyph_by_id = {as_int(row[0]): row for row in glyph_rows if row and row[0] is not None}
line_by_glyph = {as_int(row[1]): row for row in line_rows if len(row) > 1 and row[1] is not None}

words = []
for row in word_rows:
    if len(row) < 6:
        continue
    key = (as_int(row[3]), as_int(row[4]), as_int(row[5]))
    words.append({
        'wordId': as_int(row[0]), 'glyphId': as_int(row[1]), 'page': as_int(row[2]),
        'surah': key[0], 'ayah': key[1], 'word': key[2],
        'canonicalText': content_words.get(key, {}).get('text'),
        'matchText': content_words.get(key, {}).get('matchText'),
        'rawRow': row,
    })

wanted_texts = {'لَقُوا۟', 'أُو۟لَٰٓئِكَ', 'كَفَرُوا۟', 'ٱلْءَاخِرَةِ', 'ٱلصَّلَوٰةَ'}
wanted_words = [w for w in words if w['canonicalText'] in wanted_texts or (w['surah'], w['ayah'], w['word']) == (2, 14, 2)]
interesting_glyphs = {w['glyphId'] for w in wanted_words}
interesting_lines = [r for r in line_rows if len(r) > 1 and (as_int(r[1]) in interesting_glyphs or (len(r) > 2 and as_int(r[2]) in {1, 2, 3}))]

def line_item(row):
    glyph_id = as_int(row[1])
    glyph = glyph_by_id.get(glyph_id, [])
    word = next((w for w in words if w['glyphId'] == glyph_id), None)
    return {
        'glyphPageLineId': as_int(row[0]), 'glyphId': glyph_id,
        'page': as_int(row[2]) if len(row) > 2 else None,
        'line': as_int(row[3]) if len(row) > 3 else None,
        'position': as_int(row[4]) if len(row) > 4 else None,
        'lineType': row[5] if len(row) > 5 else None,
        'fontFile': glyph[1] if len(glyph) > 1 else None,
        'glyphCode': as_int(glyph[2]) if len(glyph) > 2 else None,
        'glyphTypeId': as_int(glyph[4]) if len(glyph) > 4 else None,
        'word': word,
    }

output = {
    'source': 'https://github.com/GovarJabbar/Quran-PNG/tree/master',
    'generatorSource': 'https://github.com/quran/quran.com-images',
    'sql': str(SOURCE_SQL),
    'counts': {'wordRows': len(word_rows), 'glyphRows': len(glyph_rows), 'lineRows': len(line_rows), 'bboxRows': len(bbox_rows)},
    'schemaNotes': {
        'word': 'word_id,glyph_id,page,surah,ayah,word plus source-specific offsets',
        'glyphPageLine': 'glyph_page_line_id,glyph_id,page,line,position,line_type',
        'glyph': 'glyph_id,font_file,glyph_code,page_number,glyph_type_id,glyph_type_meta,description; glyph_code is font-specific',
        'bbox': 'the checked SQL dump has no INSERT rows for glyph_page_line_bbox; Page.pm records them when generated',
    },
    'pageNumbering': {'sourcePng': '000.png..604.png', 'metadataPage': '1..604', 'mapping': '001..604 match metadata pages; 000.png is an extra zero-based/cover asset'},
    'wantedWords': wanted_words,
    'sourceLinesPages1to3': [line_item(r) for r in interesting_lines if len(r) > 2 and as_int(r[2]) in {1, 2, 3}],
    'wantedGlyphLines': [line_item(r) for r in line_rows if len(r) > 1 and as_int(r[1]) in interesting_glyphs],
}
OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text(json.dumps(output, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps(output['counts'], ensure_ascii=False))
for word in wanted_words:
    print(word['surah'], word['ayah'], word['word'], word['canonicalText'], word['matchText'], word['glyphId'])
print(f'wrote {OUT}')
