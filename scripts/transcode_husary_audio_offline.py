"""Create Android-compatible, smaller Offline AAC copies from the exact Husary source MP3s."""
from __future__ import annotations

import concurrent.futures
import hashlib
import json
import os
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = Path(os.environ.get("HUSARY_SOURCE_DIR", str(ROOT.parent / "quran-husary-mp3-source")))
DEST = ROOT / "app/src/offline/assets/quran/audio/husary_aac"
MANIFEST = ROOT / "app/src/offline/assets/quran/audio/husary_aac_manifest.json"
WORKERS = min(4, max(2, os.cpu_count() or 2))
BITRATE = "64k"

sources = sorted(SOURCE.glob("*.mp3"))
if len(sources) != 6236:
    raise SystemExit(f"expected 6236 source MP3s, found {len(sources)}")
DEST.mkdir(parents=True, exist_ok=True)

def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()

def convert(source: Path) -> dict[str, object]:
    target = DEST / f"{source.stem}.m4a"
    if not target.is_file() or target.stat().st_size <= 0:
        temporary = target.with_suffix(".m4a.part")
        temporary.unlink(missing_ok=True)
        subprocess.run(
            [
                "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                "-i", str(source), "-map_metadata", "-1", "-vn",
                "-c:a", "aac", "-b:a", BITRATE, "-movflags", "+faststart", "-f", "mp4",
                str(temporary),
            ],
            check=True,
        )
        temporary.replace(target)
    return {
        "name": target.name,
        "source_name": source.name,
        "source_bytes": source.stat().st_size,
        "source_sha256": sha256(source),
        "bytes": target.stat().st_size,
        "sha256": sha256(target),
    }

results: list[dict[str, object]] = []
with concurrent.futures.ThreadPoolExecutor(max_workers=WORKERS) as executor:
    futures = [executor.submit(convert, source) for source in sources]
    for index, future in enumerate(concurrent.futures.as_completed(futures), start=1):
        results.append(future.result())
        if index % 250 == 0 or index == len(sources):
            print(f"completed={index}/{len(sources)}", flush=True)
results.sort(key=lambda item: str(item["name"]))
manifest = {
    "source": "https://audio-cdn.tarteel.ai/quran/husary/{surah:03d}{ayah:03d}.mp3",
    "derived_format": "MPEG-4 AAC-LC",
    "bitrate": BITRATE,
    "generated_by": "scripts/transcode_husary_audio_offline.py",
    "source_count": len(sources),
    "total_source_bytes": sum(int(item["source_bytes"]) for item in results),
    "total_bytes": sum(int(item["bytes"]) for item in results),
    "files": results,
}
MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"converted={len(results)}")
print(f"source_bytes={manifest['total_source_bytes']}")
print(f"derived_bytes={manifest['total_bytes']}")
print(f"derived_gib={int(manifest['total_bytes']) / (1024 ** 3):.3f}")
print(f"manifest={MANIFEST}")
