package com.example.quranoffline

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quranoffline.data.QuranAyah
import com.example.quranoffline.data.QuranUiState
import com.example.quranoffline.data.WordReferenceLine
import com.example.quranoffline.data.selectionKey

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WordReferenceLineView(
    line: WordReferenceLine,
    state: QuranUiState,
    pageAyahs: List<QuranAyah>,
    textScale: Float,
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
    val ayahs = line.ayahKeys.mapNotNull { key ->
        val parts = key.split(':')
        val surah = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
        val ayah = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
        state.allAyahs.firstOrNull { it.surah == surah && it.ayah == ayah }
            ?: pageAyahs.firstOrNull { it.surah == surah && it.ayah == ayah }
    }
    val primary = ayahs.firstOrNull()
    val selected = ayahs.any { state.selectedAyahIndices.contains(state.allAyahs.indexOf(it)) }
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
                text = line.text,
                modifier = Modifier.fillMaxWidth(),
                fontFamily = WordReferenceFontFamily,
                fontSize = fontSize,
                color = textColor,
                textAlign = TextAlign.Right,
                lineHeight = (34f * textScale).sp,
                softWrap = false,
            )
        }
    }
}
