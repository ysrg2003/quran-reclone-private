#!/usr/bin/env python3
"""Generate Kotlin test fixtures from the supplied Word-derived layout.

Only the canonical `text` field is taken from the Word reference. The
comparison `matchText` field is kept separate and comes from the existing
speech-comparison data when the target word is available.
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

DIGITS = set("٠١٢٣٤٥٦٧٨٩")


def visible_tokens(text: str) -> list[str]:
    return [token for token in text.split() if token and not set(token) <= DIGITS]


def find_line(layout: dict, key: str, *, contains: str | None = None) -> str:
    for page in layout["pages"]:
        for line in page["lines"]:
            if key in line.get("ayahKeys", []):
                if contains is None or contains in line.get("text", ""):
                    return line["text"]
    raise KeyError(key)


def word_at(layout: dict, key: str, index: int, *, contains: str | None = None) -> str:
    tokens = visible_tokens(find_line(layout, key, contains=contains))
    return tokens[index]


def first_token_containing(layout: dict, fragment: str) -> str:
    for page in layout["pages"]:
        for line in page["lines"]:
            for token in visible_tokens(line.get("text", "")):
                if fragment in token:
                    return token
    raise KeyError(fragment)


def quran_match_text(content: dict, key: tuple[int, int], index: int, fallback: str) -> str:
    for ayah in content["ayahs"]:
        if (ayah["surah"], ayah["ayah"]) == key:
            words = ayah.get("words", [])
            if index < len(words):
                return words[index]["matchText"]
    return fallback


def kotlin_string(value: str) -> str:
    return '"' + value.replace('\\', '\\\\').replace('"', '\\"') + '"'


def make_word(layout: dict, content: dict, key: tuple[int, int], index: int, *, output_index: int | None = None, line_key: str | None = None, line_contains: str | None = None) -> str:
    lookup = line_key or f"{key[0]}:{key[1]}"
    visual = word_at(layout, lookup, index, contains=line_contains)
    match = quran_match_text(content, key, index, visual)
    return f"QuranWord({output_index if output_index is not None else index}, {kotlin_string(visual)}, {kotlin_string(match)})"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--layout", type=Path, default=Path("app/src/main/assets/quran/word_reference_layout.json"))
    parser.add_argument("--content", type=Path, default=Path("/home/ubuntu/quran-memorization-assets/quran/quran_content.json"))
    parser.add_argument("--out", type=Path, default=Path("app/src/test/java/com/example/quranoffline/WordReferenceFixtures.kt"))
    args = parser.parse_args()
    layout = json.loads(args.layout.read_text(encoding="utf-8"))
    content = json.loads(args.content.read_text(encoding="utf-8"))

    fatiha = [make_word(layout, content, (1, 1), index) for index in range(4)]
    alamin = make_word(layout, content, (1, 2), 3, output_index=0)
    malik_visual = first_token_containing(layout, "مَٰلِكِ")
    malik_match = quran_match_text(content, (1, 4), 0, malik_visual)
    alif_word = f"QuranWord(0, {kotlin_string(malik_visual)}, {kotlin_string(malik_match)})"
    muqattaat_visual = word_at(layout, "2:1", 0)
    muqattaat_match = quran_match_text(content, (2, 1), 0, "الم")
    kahyain_visual = first_token_containing(layout, "كٓهيعٓصٓ")
    ham_visual = first_token_containing(layout, "حمٓ")
    ordinary_alam_visual = first_token_containing(layout, "أَلَمۡ")
    iyyaka_visual = first_token_containing(layout, "إِيَّاكَ")
    qala_visual = first_token_containing(layout, "قَالَ")
    kataba_visual = first_token_containing(layout, "كَتَبَ")
    yawm_visual = first_token_containing(layout, "يَوۡمِ")
    deen_visual = first_token_containing(layout, "ٱلدِّينِ")
    dalleen_visual = first_token_containing(layout, "ضَّآلِّينَ")
    aqimu_visual = first_token_containing(layout, "قِيمُواْ")
    dhalika_visual = first_token_containing(layout, "ذَٰلِكَ")
    kitab_visual = first_token_containing(layout, "ٱلۡكِتَٰبُ")
    prayer_visual = first_token_containing(layout, "ٱلصَّلَوٰةَ")
    zakat_visual = first_token_containing(layout, "ٱلزَّكَوٰةَ")
    hayat_visual = first_token_containing(layout, "حَيَوٰة")
    ulaika_visual = first_token_containing(layout, "أُوْلَٰٓئِكَ")
    akhira_visual = first_token_containing(layout, "ٱلۡأٓخِرَةِ")
    kafaru_visual = first_token_containing(layout, "كَفَرُواْ")
    laqu_visual = first_token_containing(layout, "لَقُواْ")
    ashtraw_visual = first_token_containing(layout, "شۡتَرَوُاْ")

    output = f'''package com.example.quranoffline

import com.example.quranoffline.data.QuranWord

/**
 * Generated from the supplied Word-derived layout. Do not hand-edit the
 * canonical text literals: U+06E1 (small high dotless head of khah) and all
 * other Uthmanic marks are part of the fixture contract.
 */
object WordReferenceFixtures {{
    val fatihaBasmala: List<QuranWord> = listOf(
        {fatiha[0]},
        {fatiha[1]},
        {fatiha[2]},
        {fatiha[3]},
    )
    val fatihaFirstThree: List<QuranWord> = fatihaBasmala.take(3)

    val alamin: QuranWord = {alamin}
    val malik: QuranWord = {alif_word}
    val baqarahOpeningMuqattaat: QuranWord = QuranWord(0, {kotlin_string(muqattaat_visual)}, {kotlin_string(muqattaat_match)})
    val kahyainSad: QuranWord = QuranWord(0, {kotlin_string(kahyain_visual)}, "كهيعص")
    val ham: QuranWord = QuranWord(0, {kotlin_string(ham_visual)}, "حم")
    val ordinaryAlam: QuranWord = QuranWord(0, {kotlin_string(ordinary_alam_visual)}, "ألم")
    val iyyaka: QuranWord = QuranWord(0, {kotlin_string(iyyaka_visual)}, "إياك")
    val qala: QuranWord = QuranWord(0, {kotlin_string(qala_visual)}, "قال")
    val kataba: QuranWord = QuranWord(0, {kotlin_string(kataba_visual)}, "كتب")
    val yawm: QuranWord = QuranWord(0, {kotlin_string(yawm_visual)}, "يوم")
    val deen: QuranWord = QuranWord(0, {kotlin_string(deen_visual)}, "الدين")
    val dalleen: QuranWord = QuranWord(0, {kotlin_string(dalleen_visual)}, "الضالين")
    val aqimu: QuranWord = QuranWord(0, {kotlin_string(aqimu_visual)}, "أقيموا")
    val dhalika: QuranWord = QuranWord(0, {kotlin_string(dhalika_visual)}, "ذلك")
    val kitab: QuranWord = QuranWord(1, {kotlin_string(kitab_visual)}, "الكتاب")
    val prayer: QuranWord = QuranWord(0, {kotlin_string(prayer_visual)}, "الصلوة")
    val zakat: QuranWord = QuranWord(0, {kotlin_string(zakat_visual)}, "الزكوة")
    val hayat: QuranWord = QuranWord(0, {kotlin_string(hayat_visual)}, "الحيوة")
    val ulaika: QuranWord = QuranWord(0, {kotlin_string(ulaika_visual)}, "أولئك")
    val akhira: QuranWord = QuranWord(0, {kotlin_string(akhira_visual)}, "بالاخرة")
    val kafaru: QuranWord = QuranWord(0, {kotlin_string(kafaru_visual)}, "كفروا")
    val laqu: QuranWord = QuranWord(0, {kotlin_string(laqu_visual)}, "لقوا")
    val ashtraw: QuranWord = QuranWord(0, {kotlin_string(ashtraw_visual)}, "اشتروا")
    const val UTHMANIC_SMALL_HIGH_HEAD_OF_KHAH: Char = '\\u06E1'
}}
'''
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(output, encoding="utf-8")
    print(f"wrote={args.out}")
    print(f"fatiha_basmala={[word_at(layout, '1:1', i) for i in range(4)]}")
    print(f"alamin={word_at(layout, '1:2', 3)}")
    print(f"malik={malik_visual}")
    print(f"muqattaat={muqattaat_visual}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
