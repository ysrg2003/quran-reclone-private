#!/usr/bin/env python3
"""Validate local ONNX graph metadata without network access."""
from pathlib import Path
import json
import sys

try:
    import onnxruntime as ort
except ImportError as exc:
    raise SystemExit("onnxruntime is required for this validation") from exc

root = Path(__file__).resolve().parents[1] / "app/src/main/assets/models/fastconformer-quran-ar-onnx-int8"
manifest = json.loads((root / "model_manifest.json").read_text(encoding="utf-8"))
for key, filename in manifest["files"].items():
    path = root / filename
    if not path.is_file() or path.stat().st_size == 0:
        raise SystemExit(f"missing {key}: {path}")

for name in ("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx"):
    session = ort.InferenceSession(str(root / name), providers=["CPUExecutionProvider"])
    if not session.get_inputs() or not session.get_outputs():
        raise SystemExit(f"graph has no inputs/outputs: {name}")
    print(name)
    print("  inputs:", [item.name for item in session.get_inputs()])
    print("  outputs:", [item.name for item in session.get_outputs()])
print("onnx-assets-ok")
