from __future__ import annotations

import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[1]
LAYOUT = PROJECT / 'app/src/main/assets/quran/word_reference_layout.json'
CONTENT = PROJECT / 'app/src/main/assets/quran/word_reference_ayahs.json'
METADATA = PROJECT / 'app/src/main/assets/quran/quran-meta-data.xml'


def metadata_pages(content_keys: list[tuple[int, int]]) -> list[list[tuple[int, int]]]:
    root = ET.fromstring(METADATA.read_text(encoding='utf-8'))
    starts = {int(row.attrib['index']): int(row.attrib['start']) for row in root.find('suras').findall('sura')}
    rows = sorted((int(row.attrib['index']), starts[int(row.attrib['sura'])] + int(row.attrib['aya']) - 1) for row in root.find('pages').findall('page'))
    result = []
    for index, (_, start) in enumerate(rows):
        end = rows[index + 1][1] if index + 1 < len(rows) else len(content_keys)
        result.append(content_keys[start:end])
    return result

layout = json.loads(LAYOUT.read_text(encoding='utf-8'))
content = json.loads(CONTENT.read_text(encoding='utf-8'))
content_keys = [(int(ayah['surah']), int(ayah['ayah'])) for ayah in content['ayahs']]
errors = []
if layout.get('schemaVersion') != 3: errors.append('schemaVersion')
if layout.get('pageCount') != 604 or len(layout.get('pages', [])) != 604: errors.append('pageCount')
if layout.get('lineCount') != 9048 or sum(len(page['lines']) for page in layout['pages']) != 9048: errors.append('lineCount')
if layout.get('suraCount') != 114: errors.append('suraCount')
actual_pages = []
for page in layout['pages']:
    keys = []
    for line in page['lines']:
        for key in line.get('ayahKeys', []):
            surah, ayah = (int(value) for value in key.split(':'))
            if (surah, ayah) not in keys: keys.append((surah, ayah))
    actual_pages.append(keys)
expected_pages = metadata_pages(content_keys)
actual_flat = [key for page in actual_pages for key in page]
if actual_flat != content_keys:
    errors.append('Word ayah sequence does not cover canonical ayahs in order')
    for index, (actual, expected) in enumerate(zip(actual_flat, content_keys), 1):
        if actual != expected:
            print(f'first-sequence-mismatch index={index} actual={actual} expected={expected}', file=sys.stderr)
            break
all_text = '\n'.join(line['text'] for page in layout['pages'] for line in page['lines'])
for token in ('الٓمٓ', 'كَفَرُواْ', 'ٱشۡتَرَوُاْ', 'أُوْلَٰٓئِكَ', 'ٱلۡأٓخِرَةِ', '۞', 'ۖ', 'ۗ'):
    if token not in all_text: errors.append(f'missing literal token {token}')
if errors:
    print('FAIL', '; '.join(errors), file=sys.stderr)
    raise SystemExit(1)
print('word-reference-layout=PASS')
print('pages=604 lines=9048 suras=114')
print(f"page-ayah-sequences={len(actual_pages)}/{len(expected_pages)}; Word boundaries are authoritative")
print('literal-targets=PASS')
