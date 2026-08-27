"""Verify the bundled Offline M4A set against its generated manifest."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
AUDIO = ROOT / "app/src/offline/assets/quran/audio/husary_aac"
MANIFEST = ROOT / "app/src/offline/assets/quran/audio/husary_aac_manifest.json"
RAW_MANIFEST = ROOT / "app/src/offline/assets/quran/audio/husary_manifest.json"

def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()

manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
files = manifest.get("files", [])
if len(files) != 6236:
    raise SystemExit(f"manifest file count is {len(files)}, expected 6236")
if len(list(AUDIO.glob("*.m4a"))) != 6236:
    raise SystemExit("M4A directory does not contain exactly 6236 files")
if list(AUDIO.glob("*.mp3")):
    raise SystemExit("raw MP3 found inside bundled Offline audio directory")
expected_bytes = int(manifest["total_bytes"])
actual_bytes = 0
for index, item in enumerate(files, start=1):
    path = AUDIO / str(item["name"])
    if not path.is_file() or path.stat().st_size <= 0:
        raise SystemExit(f"missing or empty {path.name}")
    actual_bytes += path.stat().st_size
    if path.stat().st_size != int(item["bytes"]):
        raise SystemExit(f"size mismatch {path.name}")
    if sha256(path) != item["sha256"]:
        raise SystemExit(f"sha256 mismatch {path.name}")
    if index % 500 == 0 or index == len(files):
        print(f"verified={index}/{len(files)}", flush=True)
if actual_bytes != expected_bytes:
    raise SystemExit(f"total bytes mismatch: {actual_bytes} != {expected_bytes}")
if not RAW_MANIFEST.is_file():
    raise SystemExit("source MP3 manifest is missing")
print(f"PASS offline audio: files=6236 bytes={actual_bytes}")
