from __future__ import annotations

import json
from pathlib import Path

path = Path('/home/ubuntu/quran-memorization-assets/quran/quran_content.json')
data = json.loads(path.read_text(encoding='utf-8'))
for ayah in data['ayahs']:
    if (ayah['surah'], ayah['ayah']) in {(2, 14), (2, 6), (2, 255), (1, 2)}:
        print(f"{ayah['surah']}:{ayah['ayah']} {ayah.get('sourceText', '')}")
        for word in ayah.get('words', []):
            text = word.get('text', '')
            if any(mark in text for mark in ('۟', 'ٰ', 'ء')) or ayah['ayah'] == 14:
                print(' ', word)
