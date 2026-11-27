package com.example.quranoffline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
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
    onPlainTap: (() -> Unit)? = null,
    showCircle: Boolean = false,
    circleDp: Int = 20,
    onClearSelection: () -> Unit = {},
) {
    val ayahs = androidx.compose.runtime.remember(line, ayahsByKey, pageAyahsByKey) {
        line.ayahKeys.mapNotNull { key -> ayahsByKey[key] ?: pageAyahsByKey[key] }
    }
    val primary = ayahs.firstOrNull()
    val background = when {
        else -> Color.Transparent
    }
    val fontSize = (22f * textScale).sp
    val textColor = Color(0xFF183D33)

    when (line.type) {
        "sura" -> Box(
            Modifier.fillMaxWidth().padding(vertical = 4.dp).drawBehind {
                val teal = Color(0xFF1F7A74); val gold = Color(0xFFC9A55B)
                val w = size.width; val h = size.height
                val r = androidx.compose.ui.geometry.CornerRadius(h * 0.22f, h * 0.22f)
                drawRoundRect(Color(0xFFE6F2EF), cornerRadius = r)
                drawRoundRect(teal, cornerRadius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
                val inset = 5.dp.toPx()
                drawRoundRect(gold, topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = androidx.compose.ui.geometry.Size(w - 2 * inset, h - 2 * inset), cornerRadius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()))
                val rad = h * 0.26f
                drawCircle(teal, rad, androidx.compose.ui.geometry.Offset(h * 0.55f, h / 2))
                drawCircle(gold, rad * 0.55f, androidx.compose.ui.geometry.Offset(h * 0.55f, h / 2))
                drawCircle(teal, rad, androidx.compose.ui.geometry.Offset(w - h * 0.55f, h / 2))
                drawCircle(gold, rad * 0.55f, androidx.compose.ui.geometry.Offset(w - h * 0.55f, h / 2))
            }.padding(vertical = 10.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            Text(
                text = line.text,
                fontFamily = WordReferenceFontFamily,
                fontSize = (24f * textScale).sp,
                color = Color(0xFF123F35),
                textAlign = TextAlign.Center,
                softWrap = false,
            )
        }
        "bismillah" -> Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(
                text = line.text,
                modifier = Modifier.fillMaxWidth()
                    .background(Color(0xFFFFF7E8))
                    .combinedClickable(
                        onClick = { pageAyahs.firstOrNull()?.let { onPlayBasmala(it.surah) } },
                        onLongClick = { pageAyahs.firstOrNull()?.let { onPlayBasmala(it.surah) } },
                    )
                    .padding(vertical = 8.dp),
                fontFamily = WordReferenceFontFamily,
                fontSize = fontSize,
                color = if (state.hidden) Color.Transparent else textColor,
                textAlign = TextAlign.Center,
                softWrap = false,
            )
        }
        "footer" -> Text(
            text = line.text,
            modifier = Modifier.fillMaxWidth().then(if (onPlainTap != null && state.selectedAyahIndices.isEmpty()) Modifier.clickable { onPlainTap() } else Modifier).padding(top = 8.dp),
            fontFamily = WordReferenceFontFamily,
            fontSize = 11.sp,
            color = Color(0xFF8A806D),
            textAlign = TextAlign.Center,
            softWrap = false,
        )
        else -> {
            val segments = androidx.compose.runtime.remember(line) { com.example.quranoffline.data.ayahSegments(line) }
            val layoutHolder = androidx.compose.runtime.remember { arrayOfNulls<androidx.compose.ui.text.TextLayoutResult>(1) }
            val fitState = androidx.compose.runtime.remember(line, textScale) { androidx.compose.runtime.mutableFloatStateOf(1f) }
            val tapCb = androidx.compose.runtime.rememberUpdatedState(Triple(onPlainTap, onTapAyah, onOpenAyahActions))
            val anySelected = selectedIndices.isNotEmpty()
            val selNow = androidx.compose.runtime.rememberUpdatedState(anySelected)
            val primaryNow = androidx.compose.runtime.rememberUpdatedState(primary)
            fun hitAyah(x: Float, y: Float, width: Int): QuranAyah? {
                val r = layoutHolder[0]
                val f = fitState.floatValue
                val tx = width - (width - x) / f // undo the right-anchored horizontal stretch
                val off = r?.getOffsetForPosition(androidx.compose.ui.geometry.Offset(tx, y.coerceAtLeast(0f)))
                val key = if (off != null) com.example.quranoffline.data.ayahKeyAtOffset(segments, off) else null
                return key?.let { ayahsByKey[it] ?: pageAyahsByKey[it] } ?: primaryNow.value
            }
            Box(
            Modifier.fillMaxWidth()
                .background(background)
                .pointerInput(line) {
                    detectTapGestures(
                        onTap = { o ->
                            val (plain, tapAyah, openActions) = tapCb.value
                            if (plain != null && !selNow.value) plain()
                            else hitAyah(o.x, o.y, size.width)?.let { a -> if (selNow.value) tapAyah(a.surah, a.ayah) else openActions(a.surah, a.ayah) }
                        },
                        onLongPress = { o -> hitAyah(o.x, o.y, size.width)?.let { onLongPressAyah(it.surah, it.ayah) } },
                    )
                }
                .padding(vertical = 2.dp),
        ) {
            // Justify: measure the natural ink width once per line/size and stretch it to the full row width (clamped).
            val selectedKeys = androidx.compose.runtime.remember(selectedIndices, segments, ayahIndexByKey) {
                segments.filter { ayahIndexByKey[it.first]?.let(selectedIndices::contains) == true }
            }
            val playKey = state.readerCurrentKey
            val playing = androidx.compose.runtime.remember(segments, playKey) { if (playKey == null) emptyList() else segments.filter { it.first == playKey } }
            Text(
                text = androidx.compose.runtime.remember(line, state.evaluation, state.hidden, ayahsByKey, ayahIndexByKey, wordOffsets, selectedKeys, playing) {
                    val base = annotatedWordReferenceLine(line, state, ayahsByKey, ayahIndexByKey, wordOffsets)
                    if (selectedKeys.isEmpty() && playing.isEmpty()) base else buildAnnotatedString {
                        append(base)
                        selectedKeys.forEach { (_, st, en) -> if (en > st) addStyle(SpanStyle(background = Color(0xFFFFE0A6)), st, en) }
                        playing.forEach { (_, st, en) -> if (en > st) addStyle(SpanStyle(background = Color(0xFFBFE3CF)), st, en) }
                    }
                },
                onTextLayout = { r ->
                    layoutHolder[0] = r
                    if (r.lineCount == 1 && r.size.width > 0) {
                        val ink = r.getLineRight(0) - r.getLineLeft(0)
                        if (ink > 0f) {
                            val f = (r.size.width / ink).coerceIn(0.9f, 1.3f)
                            if (kotlin.math.abs(f - fitState.floatValue) > 0.004f) fitState.floatValue = f
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().graphicsLayer {
                    scaleX = fitState.floatValue; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)
                },
                fontFamily = WordReferenceFontFamily,
                fontSize = fontSize,
                color = if (state.hidden) Color.Transparent else textColor,
                textAlign = TextAlign.Right,
                lineHeight = (34f * textScale).sp,
                softWrap = false,
            )
            if (showCircle) androidx.compose.foundation.layout.Box(
                Modifier.align(androidx.compose.ui.Alignment.TopEnd).padding(end = 2.dp)
                    .size(circleDp.dp)
                    .background(Color(0xE60F5B4D), androidx.compose.foundation.shape.CircleShape)
                    .clickable { onClearSelection() },
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) { Text("×", color = Color.White, fontSize = (circleDp * 0.7f).sp, fontWeight = FontWeight.Bold) }
        }
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
    var lineCursor = 0
    line.ayahKeys.forEach { key ->
        val ayah = ayahsByKey[key] ?: return@forEach
        val ayahIndex = ayahIndexByKey[key] ?: return@forEach
        val range = line.wordRanges[key]
        var searchFrom = if (range != null) lineCursor else 0
        ayah.words.forEachIndexed { localIndex, word ->
            if (range != null && (localIndex < range.first || localIndex >= range.second)) return@forEachIndexed
            val start = line.text.indexOf(word.text, searchFrom)
            if (start < 0) return@forEachIndexed
            val end = start + word.text.length
            val globalIndex = wordOffsets.getOrNull(ayahIndex)?.plus(localIndex) ?: return@forEachIndexed
            val status = evaluation.words.getOrNull(globalIndex)?.status
            val color = statusHighlightColor(status)
            if (color != null) ranges += Triple(start, end, color)
            searchFrom = end
            if (range != null) lineCursor = end
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
