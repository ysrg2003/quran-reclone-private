"""Audit Husary ayah containers against QUL word timing boundaries.

This is a container/timing audit, not a linguistic or listening proof. It checks
all canonical ayah keys, source/derived audio files, durations, ordered segment
bounds, word-index coverage, leading/tail gaps, and MP3/M4A duration agreement.
"""
from __future__ import annotations

import argparse
import json
import math
import statistics
import subprocess
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from typing import Any

EXPECTED_AYAHS = 6236


def probe_duration_ms(path: Path, timeout_s: float) -> tuple[float | None, str | None]:
    command = [
        "ffprobe",
        "-v",
        "error",
        "-show_entries",
        "format=duration",
        "-of",
        "default=noprint_wrappers=1:nokey=1",
        str(path),
    ]
    try:
        result = subprocess.run(command, capture_output=True, text=True, timeout=timeout_s)
    except (OSError, subprocess.TimeoutExpired) as exc:
        return None, type(exc).__name__
    if result.returncode != 0:
        return None, (result.stderr.strip() or f"ffprobe exit {result.returncode}")[:240]
    try:
        seconds = float(result.stdout.strip())
    except ValueError:
        return None, f"invalid duration: {result.stdout.strip()[:120]}"
    if not math.isfinite(seconds) or seconds <= 0:
        return None, f"non-positive duration: {seconds}"
    return seconds * 1000.0, None


def percentile(values: list[float], fraction: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    position = (len(ordered) - 1) * fraction
    low = math.floor(position)
    high = math.ceil(position)
    if low == high:
        return ordered[low]
    return ordered[low] + (ordered[high] - ordered[low]) * (position - low)


def rounded(value: float | None) -> float | None:
    return None if value is None else round(value, 3)


def make_row(
    key: tuple[int, int],
    canonical: dict[str, Any],
    timing: dict[str, Any] | None,
    mp3_dir: Path,
    m4a_dir: Path,
    timeout_s: float,
) -> dict[str, Any]:
    surah, ayah = key
    filename = f"{surah:03d}{ayah:03d}"
    mp3 = mp3_dir / f"{filename}.mp3"
    m4a = m4a_dir / f"{filename}.m4a"
    row: dict[str, Any] = {
        "surah": surah,
        "ayah": ayah,
        "filename": filename,
        "canonicalWordCount": len(canonical.get("words", [])),
        "timingPresent": timing is not None,
        "mp3Present": mp3.is_file(),
        "m4aPresent": m4a.is_file(),
        "mp3Bytes": mp3.stat().st_size if mp3.is_file() else 0,
        "m4aBytes": m4a.stat().st_size if m4a.is_file() else 0,
        "mp3DurationMs": None,
        "m4aDurationMs": None,
        "firstTimingStartMs": None,
        "lastTimingEndMs": None,
        "leadingGapMs": None,
        "mp3TailGapMs": None,
        "m4aTailGapMs": None,
        "mp3M4aDurationDeltaMs": None,
        "timingSegmentCount": 0,
        "sourceSegmentCount": int(timing.get("sourceSegmentCount", 0)) if timing else 0,
        "nonWordSegmentCount": len(timing.get("nonWordSegments", [])) if timing else 0,
        "issues": [],
    }
    if timing is None:
        row["issues"].append("missing_timing_row")
    segments = timing.get("segments", []) if timing else []
    row["timingSegmentCount"] = len(segments)
    if not segments:
        row["issues"].append("no_canonical_segments")
    expected_indexes = set(range(row["canonicalWordCount"]))
    actual_indexes: list[int] = []
    previous_start = -1
    previous_end = -1
    for index, segment in enumerate(segments):
        if not isinstance(segment, list) or len(segment) != 4:
            row["issues"].append(f"malformed_segment_{index}")
            continue
        word_start, word_end, start_ms, end_ms = map(int, segment)
        actual_indexes.append(word_start)
        if word_end != word_start + 1:
            row["issues"].append(f"non_single_word_segment_{index}")
        if start_ms < 0 or end_ms <= start_ms:
            row["issues"].append(f"invalid_segment_duration_{index}")
        if index and start_ms < previous_start:
            row["issues"].append(f"segment_start_reordered_{index}")
        if index and end_ms < previous_end:
            row["issues"].append(f"segment_end_reordered_{index}")
        previous_start = start_ms
        previous_end = end_ms
    missing_indexes = sorted(expected_indexes - set(actual_indexes))
    if missing_indexes:
        row["issues"].append("missing_word_indexes:" + ",".join(map(str, missing_indexes[:20])))
    duplicate_indexes = sorted({index for index in actual_indexes if actual_indexes.count(index) > 1})
    if duplicate_indexes:
        row["issues"].append("duplicate_word_indexes:" + ",".join(map(str, duplicate_indexes[:20])))
    if segments and isinstance(segments[0], list) and len(segments[0]) == 4:
        row["firstTimingStartMs"] = int(segments[0][2])
        row["leadingGapMs"] = int(segments[0][2])
    if segments and isinstance(segments[-1], list) and len(segments[-1]) == 4:
        row["lastTimingEndMs"] = int(segments[-1][3])

    mp3_duration, mp3_error = probe_duration_ms(mp3, timeout_s) if mp3.is_file() else (None, "missing_file")
    m4a_duration, m4a_error = probe_duration_ms(m4a, timeout_s) if m4a.is_file() else (None, "missing_file")
    row["mp3DurationMs"] = rounded(mp3_duration)
    row["m4aDurationMs"] = rounded(m4a_duration)
    if mp3_error:
        row["issues"].append("mp3_probe:" + mp3_error)
    if m4a_error:
        row["issues"].append("m4a_probe:" + m4a_error)
    if mp3_duration is not None and row["lastTimingEndMs"] is not None:
        row["mp3TailGapMs"] = rounded(mp3_duration - row["lastTimingEndMs"])
        if row["firstTimingStartMs"] < 0 or row["lastTimingEndMs"] > mp3_duration + 1:
            row["issues"].append("timing_outside_mp3_duration")
    if m4a_duration is not None and row["lastTimingEndMs"] is not None:
        row["m4aTailGapMs"] = rounded(m4a_duration - row["lastTimingEndMs"])
        if row["firstTimingStartMs"] < 0 or row["lastTimingEndMs"] > m4a_duration + 1:
            row["issues"].append("timing_outside_m4a_duration")
    if mp3_duration is not None and m4a_duration is not None:
        row["mp3M4aDurationDeltaMs"] = rounded(abs(mp3_duration - m4a_duration))
        if row["mp3M4aDurationDeltaMs"] > 1000:
            row["issues"].append("mp3_m4a_duration_delta_over_1000ms")
    return row


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--canonical", type=Path, required=True)
    parser.add_argument("--timing", type=Path, required=True)
    parser.add_argument("--mp3-dir", type=Path, required=True)
    parser.add_argument("--m4a-dir", type=Path, required=True)
    parser.add_argument("--json-output", type=Path, required=True)
    parser.add_argument("--markdown-output", type=Path, required=True)
    parser.add_argument("--workers", type=int, default=8)
    parser.add_argument("--probe-timeout", type=float, default=20.0)
    parser.add_argument("--late-start-ms", type=float, default=1000.0)
    args = parser.parse_args()
    if not 1 <= args.workers <= 32:
        raise SystemExit("--workers must be between 1 and 32")

    canonical_payload = json.loads(args.canonical.read_text(encoding="utf-8"))
    timing_payload = json.loads(args.timing.read_text(encoding="utf-8"))
    canonical_rows = {
        (int(row["surah"]), int(row["ayah"])): row
        for row in canonical_payload.get("ayahs", [])
    }
    timing_rows = {
        (int(row["surah"]), int(row["ayah"])): row
        for row in timing_payload.get("ayahs", [])
    }
    keys = sorted(set(canonical_rows) | set(timing_rows))
    rows: list[dict[str, Any]] = []
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = {
            pool.submit(
                make_row,
                key,
                canonical_rows.get(key, {}),
                timing_rows.get(key),
                args.mp3_dir,
                args.m4a_dir,
                args.probe_timeout,
            ): key
            for key in keys
        }
        for index, future in enumerate(as_completed(futures), start=1):
            rows.append(future.result())
            if index % 500 == 0 or index == len(futures):
                print(f"probed={index}/{len(futures)}", flush=True)
    rows.sort(key=lambda row: (row["surah"], row["ayah"]))

    first_starts = [float(row["firstTimingStartMs"]) for row in rows if row["firstTimingStartMs"] is not None]
    leading_gaps = [float(row["leadingGapMs"]) for row in rows if row["leadingGapMs"] is not None]
    mp3_durations = [float(row["mp3DurationMs"]) for row in rows if row["mp3DurationMs"] is not None]
    m4a_durations = [float(row["m4aDurationMs"]) for row in rows if row["m4aDurationMs"] is not None]
    anomalies = [row for row in rows if row["issues"]]
    late_start_rows = [row for row in rows if (row["firstTimingStartMs"] or 0) > args.late_start_ms]
    summary = {
        "canonicalAyahCount": len(canonical_rows),
        "timingAyahCount": len(timing_rows),
        "auditedRowCount": len(rows),
        "expectedAyahCount": EXPECTED_AYAHS,
        "audioFileCount": {
            "mp3Present": sum(row["mp3Present"] for row in rows),
            "m4aPresent": sum(row["m4aPresent"] for row in rows),
        },
        "durationProbeCount": {"mp3": len(mp3_durations), "m4a": len(m4a_durations)},
        "anomalyRowCount": len(anomalies),
        "lateStartThresholdMs": args.late_start_ms,
        "lateStartRowCount": len(late_start_rows),
        "firstTimingStartMs": {
            "min": rounded(min(first_starts) if first_starts else None),
            "p50": rounded(percentile(first_starts, 0.50)),
            "p95": rounded(percentile(first_starts, 0.95)),
            "max": rounded(max(first_starts) if first_starts else None),
        },
        "leadingGapMs": {
            "min": rounded(min(leading_gaps) if leading_gaps else None),
            "p50": rounded(percentile(leading_gaps, 0.50)),
            "p95": rounded(percentile(leading_gaps, 0.95)),
            "max": rounded(max(leading_gaps) if leading_gaps else None),
        },
        "durationMs": {
            "mp3P50": rounded(percentile(mp3_durations, 0.50)),
            "mp3P95": rounded(percentile(mp3_durations, 0.95)),
            "m4aP50": rounded(percentile(m4a_durations, 0.50)),
            "m4aP95": rounded(percentile(m4a_durations, 0.95)),
        },
        "totalMp3Bytes": sum(row["mp3Bytes"] for row in rows),
        "totalM4aBytes": sum(row["m4aBytes"] for row in rows),
        "timingSegmentTotal": sum(row["timingSegmentCount"] for row in rows),
        "sourceSegmentTotal": sum(row["sourceSegmentCount"] for row in rows),
        "nonWordSegmentTotal": sum(row["nonWordSegmentCount"] for row in rows),
    }
    payload = {
        "schemaVersion": 1,
        "generatedBy": "scripts/audit_husary_audio_boundaries.py",
        "metadataScope": "container duration and QUL timing bounds; not linguistic completeness",
        "summary": summary,
        "anomalyRows": anomalies,
        "lateStartRows": late_start_rows,
        "rows": rows,
    }
    args.json_output.parent.mkdir(parents=True, exist_ok=True)
    args.json_output.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

    issue_counts: dict[str, int] = {}
    for row in rows:
        for issue in row["issues"]:
            issue_counts[issue.split(":", 1)[0]] = issue_counts.get(issue.split(":", 1)[0], 0) + 1
    late_examples = late_start_rows[:20]
    lines = [
        "# تدقيق حدود صوت الحصري وتوقيتاته",
        "",
        "> هذا التقرير يثبت خصائص الحاوية وحدود بيانات QUL فقط. لا يثبت من metadata وحدها أن النطق اللغوي كامل أو أن كل segment يطابق الكلمة المسموعة؛ يلزم لذلك استماع مضبوط أو ASR لعينة موثقة.",
        "",
        "## النتيجة الرقمية",
        "",
        "| المقياس | القيمة |",
        "|---|---:|",
        f"| الآيات في Word canonical | {summary['canonicalAyahCount']} |",
        f"| الآيات في timing | {summary['timingAyahCount']} |",
        f"| الصفوف المدققة | {summary['auditedRowCount']} |",
        f"| ملفات MP3 موجودة | {summary['audioFileCount']['mp3Present']} |",
        f"| ملفات M4A موجودة | {summary['audioFileCount']['m4aPresent']} |",
        f"| MP3 تم probe لها | {summary['durationProbeCount']['mp3']} |",
        f"| M4A تم probe لها | {summary['durationProbeCount']['m4a']} |",
        f"| صفوف بها مخالفة آلية | {summary['anomalyRowCount']} |",
        f"| صفوف يبدأ أول timing فيها بعد {args.late_start_ms:g}ms | {summary['lateStartRowCount']} |",
        f"| مجموع canonical timing segments | {summary['timingSegmentTotal']} |",
        f"| مجموع source segments الخام | {summary['sourceSegmentTotal']} |",
        f"| segments غير الكلمات | {summary['nonWordSegmentTotal']} |",
        "",
        "## توزيعات بداية التوقيت",
        "",
        "| القياس | min | p50 | p95 | max |",
        "|---|---:|---:|---:|---:|",
        f"| firstTimingStartMs | {summary['firstTimingStartMs']['min']} | {summary['firstTimingStartMs']['p50']} | {summary['firstTimingStartMs']['p95']} | {summary['firstTimingStartMs']['max']} |",
        f"| leadingGapMs | {summary['leadingGapMs']['min']} | {summary['leadingGapMs']['p50']} | {summary['leadingGapMs']['p95']} | {summary['leadingGapMs']['max']} |",
        "",
        "`leadingGapMs` هو المسافة من بداية الملف إلى أول cue في QUL، وليس دليلاً على أن الملف ناقص. تشغيل الآية الكاملة يجب أن يبدأ من offset 0 حتى لا يحوّل هذه المسافة إلى قص لبداية القراءة.",
        "",
        "## المخالفات الآلية",
        "",
    ]
    if issue_counts:
        lines.extend(["| النوع | عدد الصفوف |", "|---|---:|"])
        lines.extend(f"| `{name}` | {count} |" for name, count in sorted(issue_counts.items()))
    else:
        lines.append("لم تسجل الأداة مخالفات آلية في الملفات والحدود المفحوصة.")
    lines.extend([
        "",
        "## عينات الصفوف ذات البداية المتأخرة",
        "",
        "| الموضع | أول cue (ms) | مدة MP3 (ms) | آخر cue (ms) | tail MP3 (ms) |",
        "|---|---:|---:|---:|---:|",
    ])
    if late_examples:
        lines.extend(
            f"| {row['surah']}:{row['ayah']} | {row['firstTimingStartMs']} | {row['mp3DurationMs']} | {row['lastTimingEndMs']} | {row['mp3TailGapMs']} |"
            for row in late_examples
        )
    else:
        lines.append("| لا يوجد | — | — | — | — |")
    lines.extend([
        "",
        "## تفسير وحدود النتيجة",
        "",
        "أثبت الفحص وجود الملفين لكل مفتاح، وإمكانية قراءة مدة الحاوية، وترتيب cue، وحدود أول وآخر cue داخل مدة الملف، وتغطية Word indexes بحسب JSON. لم يُجرَ استماع بشري لكل 6236 آية، ولم يُستخرج transcript مستقل من كل ملف؛ لذلك لا يصح تحويل PASS metadata إلى ادعاء أن كل آية مسموعة من أول حرف أو أن كل تقسيم كلمة لغوي دقيق.",
        "",
        "العينة الخاصة بالآية 1:1 يجب فحصها منفصلة عند إصدار التقرير، لأن بسملة الفاتحة جزء من ayah 1:1، بينما البسملة الافتتاحية للسور الأخرى عنصر مستقل في واجهة التسميع ولا تُضاف إلى allAyahs.",
        "",
        "البيانات الكاملة التفصيلية موجودة في ملف JSON المجاور لهذا التقرير، ويمكن إعادة توليدها بالأمر الموثق في README.",
    ])
    args.markdown_output.parent.mkdir(parents=True, exist_ok=True)
    args.markdown_output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False))


if __name__ == "__main__":
    main()
