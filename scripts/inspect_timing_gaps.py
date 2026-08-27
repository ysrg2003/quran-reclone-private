import json
from pathlib import Path

payload = json.loads(Path('app/src/main/assets/quran/audio_timing/Husary_Murattal.json').read_text(encoding='utf-8'))
for key in ('1:5', '1:6', '2:6', '2:14'):
    row = next(item for item in payload['ayahs'] if f"{item['surah']}:{item['ayah']}" == key)
    segments = row['segments']
    print(key)
    for index, segment in enumerate(segments):
        before = None if index == 0 else segment[2] - segments[index - 1][3]
        after = None if index + 1 == len(segments) else segments[index + 1][2] - segment[3]
        print({'word': segment[0], 'start': segment[2], 'end': segment[3], 'gap_before': before, 'gap_after': after})
