#!/usr/bin/env python3
"""Validate the Offline FastConformer bundle without network access.

By default this validates the checked-in ZIP manifest and member files without
executing downloaded model content. Pass --root to validate an extracted model
with onnxruntime when that optional dependency is available.
"""
from __future__ import annotations

import argparse
import json
import zipfile
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[1]
DEFAULT_ZIP = PROJECT / "app/src/offline/assets/models/fastconformer-quran-ar-onnx-int8.zip"
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--zip", type=Path, default=DEFAULT_ZIP)
parser.add_argument("--root", type=Path)
args = parser.parse_args()

required_graphs = ("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx")

if args.root:
    root = args.root
    manifest_path = root / "model_manifest.json"
    if not manifest_path.is_file():
        raise SystemExit(f"missing model manifest: {manifest_path}")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    for key, filename in manifest["files"].items():
        path = root / filename
        if not path.is_file() or path.stat().st_size == 0:
            raise SystemExit(f"missing {key}: {path}")
    try:
        import onnxruntime as ort
    except ImportError as exc:
        raise SystemExit("onnxruntime is required for --root graph validation") from exc
    for name in required_graphs:
        session = ort.InferenceSession(str(root / name), providers=["CPUExecutionProvider"])
        if not session.get_inputs() or not session.get_outputs():
            raise SystemExit(f"graph has no inputs/outputs: {name}")
        print(name)
        print("  inputs:", [item.name for item in session.get_inputs()])
        print("  outputs:", [item.name for item in session.get_outputs()])
    print("onnx-assets-ok")
else:
    if not args.zip.is_file() or args.zip.stat().st_size == 0:
        raise SystemExit(f"missing or empty model ZIP: {args.zip}")
    with zipfile.ZipFile(args.zip) as archive:
        members = {info.filename: info for info in archive.infolist()}
        manifest_candidates = [name for name in members if name.endswith("/model_manifest.json") or name == "model_manifest.json"]
        if not manifest_candidates:
            raise SystemExit(f"missing model_manifest.json in {args.zip}")
        manifest_name = manifest_candidates[0]
        prefix = manifest_name.removesuffix("model_manifest.json")
        manifest = json.loads(archive.read(manifest_name).decode("utf-8"))
        for key, filename in manifest["files"].items():
            member_name = prefix + filename
            info = members.get(member_name)
            if info is None or info.file_size == 0:
                raise SystemExit(f"missing or empty {key}: {member_name}")
        missing_graphs = [name for name in required_graphs if prefix + name not in members]
        if missing_graphs:
            raise SystemExit(f"missing required graphs: {missing_graphs}")
        print(f"model-zip={args.zip}")
        print(f"model-zip-bytes={args.zip.stat().st_size}")
        print(f"model-members={len(members)}")
        print("onnx-bundle-structure-ok")
