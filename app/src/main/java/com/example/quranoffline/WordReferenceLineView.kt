package com.example.quranoffline

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quranoffline.data.QuranAyah
import com.example.quranoffline.data.QuranUiState
import com.example.quranoffline.data.WordReferenceLine
import com.example.quranoffline.data.WordStatus
import com.example.quranoffline.data.selectionKey

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WordReferenceLineView(
    line: WordReferenceLine,
    state: QuranUiState,
    pageAyahs: List<QuranAyah>,
    textScale: Float,
    ayahsByKey: Map<String, QuranAyah>,
    pageAyahsByKey: Map<String, QuranAyah>,
    ayahIndexByKey: Map<String, Int>,
    wordOffsets: List<Int>,
    selectedIndices: Set<Int>,
    onPlayBasmala: (Int) -> Unit,
    onSetStart: (Int, Int, Int, Boolean) -> Unit,
    onMemorizeFromHere: (Int, Int) -> Unit,
    onPlayAyah: (Int, Int) -> Unit,
    onReadFromHere: (Int, Int) -> Unit,
    onReadSurahFromHere: (Int, Int) -> Unit,
    onBookmark: (Int, Int) -> Unit,
    onOpenAyahActions: (Int, Int) -> Unit,
    onTapAyah: (Int, Int) -> Unit,
    onLongPressAyah: (Int, Int) -> Unit,
) {
    val ayahs = line.ayahKeys.mapNotNull { key -> ayahsByKey[key] ?: pageAyahsByKey[key] }
    val primary = ayahs.firstOrNull()
    val selected = ayahs.any { ayahIndexByKey[it.selectionKey()]?.let(selectedIndices::contains) == true }
    val active = ayahs.any { state.readerCurrentKey == it.selectionKey() }
    val background = when {
        selected -> Color(0xFFFFE0A6)
        active -> Color(0xFFEAF4EE)
        else -> Color.Transparent
    }
    val fontSize = (22f * textScale).sp
    val textColor = Color(0xFF183D33)

    when (line.type) {
        "sura" -> Text(
            text = line.text,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            fontFamily = WordReferenceFontFamily,
            fontSize = (24f * textScale).sp,
            fontWeight = FontWeight.Normal,
            color = Color(0xFF76552A),
            textAlign = TextAlign.Center,
            softWrap = false,
        )
        "bismillah" -> Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(
                text = line.text,
                modifier = Modifier.fillMaxWidth()
                    .background(Color(0xFFFFF7E8))
                    .combinedClickable(
                        onClick = { pageAyahs.firstOrNull()?.let { onPlayBasmala(it.surah) } },
                        onLongClick = { pageAyahs.firstOrNull()?.let { onPlayBasmala(it.surah) } },
                    )
                    .padding(vertical = 8.dp)
                    .horizontalScroll(rememberScrollState(), reverseScrolling = true),
                fontFamily = WordReferenceFontFamily,
                fontSize = fontSize,
                color = textColor,
                textAlign = TextAlign.Center,
                softWrap = false,
            )
        }
        "footer" -> Text(
            text = line.text,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            fontFamily = WordReferenceFontFamily,
            fontSize = 11.sp,
            color = Color(0xFF8A806D),
            textAlign = TextAlign.Center,
            softWrap = false,
        )
        else -> Box(
            Modifier.fillMaxWidth()
                .background(background)
                .combinedClickable(
                    onClick = {
                        primary?.let { ayah ->
                            if (state.selectedAyahIndices.isNotEmpty()) onTapAyah(ayah.surah, ayah.ayah)
                            else onOpenAyahActions(ayah.surah, ayah.ayah)
                        }
                    },
                    onLongClick = { primary?.let { onLongPressAyah(it.surah, it.ayah) } },
                )
                .padding(vertical = 2.dp)
                .horizontalScroll(rememberScrollState(), reverseScrolling = true),
        ) {
            Text(
                text = annotatedWordReferenceLine(line, state, ayahsByKey, ayahIndexByKey, wordOffsets),
                modifier = Modifier.fillMaxWidth(),
                fontFamily = WordReferenceFontFamily,
                fontSize = fontSize,
                color = if (state.hidden) Color.Transparent else textColor,
                textAlign = TextAlign.Right,
                lineHeight = (34f * textScale).sp,
                softWrap = false,
            )
        }
    }
}

/**
 * Adds background spans only; the returned text is byte-for-byte the same as
 * the Word-derived line. Missing word ranges are intentionally skipped because
 * this baseline layout has ayah ranges, not a trusted word-coordinate map.
 */
internal fun annotatedWordReferenceLine(line: WordReferenceLine, state: QuranUiState): AnnotatedString {
    val ayahsByKey = state.allAyahs.associateBy { it.selectionKey() }
    val indexes = state.allAyahs.mapIndexed { index, ayah -> ayah.selectionKey() to index }.toMap()
    return annotatedWordReferenceLine(line, state, ayahsByKey, indexes, buildWordOffsets(state.allAyahs))
}

private fun annotatedWordReferenceLine(
    line: WordReferenceLine,
    state: QuranUiState,
    ayahsByKey: Map<String, QuranAyah>,
    ayahIndexByKey: Map<String, Int>,
    wordOffsets: List<Int>,
): AnnotatedString {
    val evaluation = state.evaluation ?: return AnnotatedString(line.text)
    if (line.type != "ayah" || state.hidden) return AnnotatedString(line.text)

    val ranges = mutableListOf<Triple<Int, Int, Color>>()
    line.ayahKeys.forEach { key ->
        val ayah = ayahsByKey[key] ?: return@forEach
        val ayahIndex = ayahIndexByKey[key] ?: return@forEach
        var searchFrom = 0
        ayah.words.forEachIndexed { localIndex, word ->
            val start = line.text.indexOf(word.text, searchFrom)
            if (start < 0) return@forEachIndexed
            val end = start + word.text.length
            val globalIndex = wordOffsets.getOrNull(ayahIndex)?.plus(localIndex) ?: return@forEachIndexed
            val status = evaluation.words.getOrNull(globalIndex)?.status
            val color = statusHighlightColor(status)
            if (color != null) ranges += Triple(start, end, color)
            searchFrom = end
        }
    }

    return buildAnnotatedString {
        append(line.text)
        ranges.forEach { (start, end, color) ->
            addStyle(SpanStyle(background = color.copy(alpha = 0.34f)), start, end)
        }
    }
}

internal fun buildWordOffsets(ayahs: List<QuranAyah>): List<Int> {
    val offsets = ArrayList<Int>(ayahs.size + 1)
    var offset = 0
    ayahs.forEach { ayah ->
        offsets += offset
        offset += ayah.words.size
    }
    offsets += offset
    return offsets
}

private fun statusHighlightColor(status: WordStatus?): Color? = when (status) {
    WordStatus.MATCHED -> Color(0xFF5CB889)
    WordStatus.MISSING, WordStatus.EXTRA_OR_UNEXPECTED -> Color(0xFFEF767A)
    WordStatus.UNCERTAIN, null -> null
}
