"""Download the exact ayah MP3s used by the Online runtime into the Offline flavor."""
from __future__ import annotations

import concurrent.futures
import hashlib
import json
import os
import tempfile
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CANONICAL = ROOT / "app/src/main/assets/quran/word_reference_ayahs.json"
DEST = ROOT.parent / "quran-husary-mp3-source"
MANIFEST = ROOT / "app/src/offline/assets/quran/audio/husary_manifest.json"
BASE_URL = "https://audio-cdn.tarteel.ai/quran/husary"
WORKERS = min(64, max(16, (os.cpu_count() or 4) * 4))

rows = json.loads(CANONICAL.read_text(encoding="utf-8"))["ayahs"]
entries = [(int(row["surah"]), int(row["ayah"])) for row in rows]
DEST.mkdir(parents=True, exist_ok=True)

def file_name(surah: int, ayah: int) -> str:
    return f"{surah:03d}{ayah:03d}.mp3"

def download(item: tuple[int, int]) -> tuple[str, int, str]:
    surah, ayah = item
    name = file_name(surah, ayah)
    target = DEST / name
    if target.is_file() and target.stat().st_size > 0:
        return name, target.stat().st_size, hashlib.sha256(target.read_bytes()).hexdigest()
    url = f"{BASE_URL}/{name}"
    for attempt in range(4):
        tmp = target.with_suffix(".mp3.part")
        try:
            request = urllib.request.Request(url, headers={"User-Agent": "QuranMemorizationOfflineBuilder/1.0"})
            with urllib.request.urlopen(request, timeout=120) as response, tmp.open("wb") as output:
                while True:
                    chunk = response.read(1024 * 1024)
                    if not chunk:
                        break
                    output.write(chunk)
            if tmp.stat().st_size <= 0:
                raise RuntimeError("empty response")
            tmp.replace(target)
            digest = hashlib.sha256(target.read_bytes()).hexdigest()
            return name, target.stat().st_size, digest
        except Exception:
            if tmp.exists():
                tmp.unlink()
            if attempt == 3:
                raise
    raise AssertionError("unreachable")

results: list[tuple[str, int, str]] = []
failures: list[str] = []
with concurrent.futures.ThreadPoolExecutor(max_workers=WORKERS) as executor:
    future_map = {executor.submit(download, item): item for item in entries}
    for index, future in enumerate(concurrent.futures.as_completed(future_map), start=1):
        item = future_map[future]
        try:
            result = future.result()
            results.append(result)
            if index % 250 == 0 or index == len(entries):
                print(f"completed={index}/{len(entries)}", flush=True)
        except Exception as exc:
            failures.append(f"{item[0]:03d}{item[1]:03d}: {exc}")

if failures:
    print("failures:")
    print("\n".join(failures[:20]))
    raise SystemExit(1)

results.sort()
manifest = {
    "source": BASE_URL,
    "source_repository_context": "QUL Husary resource; files downloaded from the same CDN used by Online runtime",
    "generated_by": "scripts/download_husary_audio.py",
    "ayah_count": len(results),
    "total_bytes": sum(size for _, size, _ in results),
    "files": [{"name": name, "bytes": size, "sha256": digest} for name, size, digest in results],
}
MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"downloaded={len(results)}")
print(f"total_bytes={manifest['total_bytes']}")
print(f"total_gib={manifest['total_bytes'] / (1024 ** 3):.3f}")
print(f"manifest={MANIFEST}")
