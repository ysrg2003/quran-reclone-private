"""Estimate the total size of the current Husary ayah CDN without downloading files."""
from __future__ import annotations

import argparse
import concurrent.futures
import json
from pathlib import Path

import requests

ROOT = Path(__file__).resolve().parents[1]
CANONICAL = ROOT / "app/src/main/assets/quran/word_reference_ayahs.json"
BASE_URL = "https://audio-cdn.tarteel.ai/quran/husary"

parser = argparse.ArgumentParser()
parser.add_argument("--workers", type=int, default=16)
args = parser.parse_args()
rows = json.loads(CANONICAL.read_text(encoding="utf-8"))["ayahs"]
urls = [f"{BASE_URL}/{row['surah']:03d}{row['ayah']:03d}.mp3" for row in rows]

def head(url: str) -> tuple[str, int, str]:
    response = requests.head(url, timeout=(15, 45), allow_redirects=True)
    response.raise_for_status()
    length = int(response.headers.get("content-length", "0"))
    return url, length, response.headers.get("etag", "")

sizes = []
failures = []
with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as executor:
    futures = [executor.submit(head, url) for url in urls]
    for future in concurrent.futures.as_completed(futures):
        try:
            sizes.append(future.result())
        except Exception as exc:
            failures.append(str(exc))

known = sum(size for _, size, _ in sizes)
print(f"ayahs={len(urls)}")
print(f"head_success={len(sizes)}")
print(f"head_failures={len(failures)}")
print(f"known_bytes={known}")
print(f"known_gib={known / (1024 ** 3):.3f}")
if failures:
    print("first_failure=" + failures[0])
