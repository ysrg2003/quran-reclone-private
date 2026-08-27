"""Print concise facts from a generated Husary boundary audit."""
from __future__ import annotations

import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
payload = json.loads(path.read_text(encoding="utf-8"))
rows = payload["rows"]
anomalies = payload["anomalyRows"]
row_1_1 = next(row for row in rows if row["surah"] == 1 and row["ayah"] == 1)
m4a_only = [
    row for row in anomalies
    if "timing_outside_m4a_duration" in row["issues"]
    and "timing_outside_mp3_duration" not in row["issues"]
]
print(json.dumps({
    "summary": payload["summary"],
    "ayah_1_1": row_1_1,
    "anomaly_examples": anomalies[:5],
    "m4a_only_count": len(m4a_only),
    "m4a_only_examples": [
        {key: row[key] for key in (
            "filename", "mp3DurationMs", "m4aDurationMs", "lastTimingEndMs", "m4aTailGapMs", "issues"
        )}
        for row in m4a_only[:5]
    ],
}, ensure_ascii=False, indent=2))
