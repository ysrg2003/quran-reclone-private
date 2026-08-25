from __future__ import annotations

import json
import re
from pathlib import Path
from bs4 import BeautifulSoup, NavigableString, Tag

WORD_HTML = Path('/home/ubuntu/quran-reference-work/UthmanicHafs_V20.html')
OUTPUT = Path('/home/ubuntu/quran-memorization-online/app/src/main/assets/quran/word_reference_layout.json')
ARABIC_DIGITS = set('٠١٢٣٤٥٦٧٨٩۰۱۲۳۴۵۶۷۸۹')
ORNAMENTS = set('۞۩')


def extract_pages() -> list[list[str]]:
    soup = BeautifulSoup(WORD_HTML.read_text(encoding='utf-8', errors='replace'), 'html.parser')
    root = soup.body or soup
    pages: list[list[str]] = []
    current: list[str] = []
    for paragraph in root.find_all('p'):
        if 'page-break-before: always' in paragraph.get('style', '') and current:
            pages.append(current)
            current = []
        lines = ['']
        for node in paragraph.descendants:
            if isinstance(node, Tag) and node.name == 'br':
                lines.append('')
            elif isinstance(node, NavigableString):
                lines[-1] += re.sub(r'\s+', ' ', str(node).replace('\xa0', ' '))
        for line in lines:
            line = re.sub(r'\s+', ' ', line).strip()
            # The HTML exporter places the decorative title image between the
            # first letter and the rest of this heading. The Word text itself is
            # the connected title, so reconnect only that exporter artefact.
            line = line.replace('س ُورَةُ', 'سُورَةُ')
            if line:
                current.append(line)
    if current:
        pages.append(current)
    return pages


def is_sura_heading(text: str) -> bool:
    return 'سُورَةُ' in text


def is_basmala(text: str) -> bool:
    compact = re.sub(r'[\s\u064b-\u065f\u0670\u06d6-\u06ed]', '', text)
    expected = re.sub(r'[\s\u064b-\u065f\u0670\u06d6-\u06ed]', '', 'بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ')
    return compact == expected


def is_footer(text: str) -> bool:
    return text == '2' or text.startswith('خط النسخ الحاسوبي')


def arabic_number(token: str) -> int | None:
    if not token or not all(char in ARABIC_DIGITS for char in token):
        return None
    return int(token.translate(str.maketrans('٠١٢٣٤٥٦٧٨٩۰۱۲۳۴۵۶۷۸۹', '01234567890123456789')))


def line_numbers(text: str) -> list[dict]:
    result = []
    for match in re.finditer(r'\S+', text):
        number = arabic_number(match.group(0))
        if number is not None:
            result.append({'start': match.start(), 'end': match.end(), 'text': match.group(0), 'number': number})
    return result

pages_raw = extract_pages()
if len(pages_raw) != 604:
    raise SystemExit(f'expected 604 Word pages, got {len(pages_raw)}')

surah = 0
current_ayah = 1
pages = []
for page_number, lines in enumerate(pages_raw, start=1):
    output_lines = []
    for line_number, text in enumerate(lines, start=1):
        if is_sura_heading(text):
            surah += 1
            current_ayah = 1
            line_type = 'sura'
        elif is_basmala(text):
            line_type = 'bismillah'
        elif is_footer(text):
            line_type = 'footer'
        else:
            line_type = 'ornament' if set(text) <= ORNAMENTS else 'ayah'
        numbers = line_numbers(text) if line_type == 'ayah' else []
        ayah_keys = []
        if line_type == 'ayah':
            if numbers:
                for number in numbers:
                    key = f'{surah}:{number["number"]}'
                    if key not in ayah_keys:
                        ayah_keys.append(key)
                current_ayah = numbers[-1]['number'] + 1
            else:
                ayah_keys.append(f'{surah}:{current_ayah}')
        output_lines.append({
            'number': line_number,
            'type': line_type,
            'text': text,
            'ayahKeys': ayah_keys,
            'ayahNumbers': numbers,
        })
    pages.append({'page': page_number, 'lines': output_lines})

if surah != 114:
    raise SystemExit(f'expected 114 sura headings, got {surah}')

output = {
    'schemaVersion': 3,
    'source': 'UthmanicHafs_V20.doc supplied by user; extracted without rewriting Quran text',
    'sourceHtmlConversion': 'LibreOffice HTML export used only to read Word paragraphs and explicit line/page breaks',
    'fontFamily': 'KFGQPC HAFS Uthmanic Script',
    'fontAsset': 'fonts/kfgqpc_hafs_uthmanic_script.ttf',
    'pageCount': len(pages),
    'lineCount': sum(len(page['lines']) for page in pages),
    'suraCount': surah,
    'displayContract': 'render line.text verbatim; do not synthesize or replace any glyph, mark, symbol, or ayah number',
    'pages': pages,
}
OUTPUT.parent.mkdir(parents=True, exist_ok=True)
OUTPUT.write_text(json.dumps(output, ensure_ascii=False, separators=(',', ':')) + '\n', encoding='utf-8')
print(json.dumps({key: output[key] for key in ('schemaVersion', 'pageCount', 'lineCount', 'suraCount')}, ensure_ascii=False))
for page in pages[:3]:
    print('PAGE', page['page'])
    for line in page['lines']:
        print(line['type'], line['text'], line['ayahKeys'])
