package com.example.quranoffline.data

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em

/** Visual categories that are applied without changing the canonical Quran string. */
enum class QuranTextSpanKind {
    HAMZA_OVERLAY,
    QURAN_MARK_OVERLAY,
}

data class QuranTextSpan(
    val start: Int,
    val end: Int,
    val kind: QuranTextSpanKind,
)

/**
 * Keeps Quran text as-is and adds only visual spans for characters that behave like
 * orthographic overlays in the reference mushaf. Matching and storage never use this layer.
 */
object QuranTextPresentation {
    private val overlayMarks = setOf(
        'ۖ', 'ۗ', 'ۘ', 'ۙ', 'ۚ', 'ۛ', 'ۜ', '۟', '۠', 'ۢ', 'ۤ', 'ۥ', 'ۦ', 'ۧ', 'ۨ',
        '۪', '۫', '۬', 'ۭ', '۩', '۝', '۞',
    )

    private val markerLabels = linkedMapOf(
        'ۖ' to "الوصل أولى",
        'ۗ' to "الوقف أولى",
        'ۘ' to "لازم الوقف",
        'ۙ' to "لا تقف",
        'ۚ' to "جائز الوقف",
        'ۛ' to "وقف المعانقة",
        'ۜ' to "النطق بالسين",
        '۟' to "حرف زائد لا ينطق",
        '۠' to "حرف زائد لا ينطق وصلاً",
        '۩' to "سجدة تلاوة",
        '۝' to "نهاية آية ورقمها",
        '۞' to "بداية ربع حزب",
    )

    fun spans(text: String): List<QuranTextSpan> = buildList {
        text.forEachIndexed { index, character ->
            when {
                isHamzaBetweenLamAndAlif(text, index) -> add(QuranTextSpan(index, index + 1, QuranTextSpanKind.HAMZA_OVERLAY))
                character in overlayMarks -> add(QuranTextSpan(index, index + 1, QuranTextSpanKind.QURAN_MARK_OVERLAY))
            }
        }
    }

    fun markerLabel(character: Char): String? = markerLabels[character]

    fun markerLegend(): List<Pair<Char, String>> = markerLabels.toList()

    private fun isHamzaBetweenLamAndAlif(text: String, index: Int): Boolean {
        if (text.getOrNull(index) != 'ء') return false
        if (index < 2 || text[index - 2] != 'ل' || text[index - 1] != 'ْ') return false
        return text.drop(index + 1).firstOrNull { it !in '\u064B'..'\u065F' } == 'ا'
    }

    /** Returns the exact input text with rendering-only spans; no code point is removed or replaced. */
    fun annotated(text: String): AnnotatedString {
        val result = AnnotatedString.Builder()
        text.forEachIndexed { index, character ->
            val span = when {
                isHamzaBetweenLamAndAlif(text, index) -> QuranTextSpan(
                    index,
                    index + 1,
                    QuranTextSpanKind.HAMZA_OVERLAY,
                )
                character in overlayMarks -> QuranTextSpan(
                    index,
                    index + 1,
                    QuranTextSpanKind.QURAN_MARK_OVERLAY,
                )
                else -> null
            }
            if (span == null) {
                result.append(character)
            } else {
                val style = when (span.kind) {
                    QuranTextSpanKind.HAMZA_OVERLAY -> SpanStyle(
                        fontSize = 0.82.em,
                        baselineShift = androidx.compose.ui.text.style.BaselineShift.Superscript,
                    )
                    QuranTextSpanKind.QURAN_MARK_OVERLAY -> SpanStyle(
                        fontSize = 0.72.em,
                        baselineShift = androidx.compose.ui.text.style.BaselineShift.Superscript,
                    )
                }
                result.withStyle(style) { append(character) }
            }
        }
        return result.toAnnotatedString()
    }
}
