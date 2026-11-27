package com.example.quranoffline

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import android.app.Activity
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.quranoffline.data.CorrectionSettings
import com.example.quranoffline.data.QuranUiState
import com.example.quranoffline.data.QuranViewModel
import com.example.quranoffline.data.RecitationInputMode

/**
 * Optional full-screen reader drawn over the normal app. Tap the page to show or hide the two bars.
 * Exit with the X button or Back; the normal screens underneath are untouched.
 */
@Composable
internal fun ImmersiveReader(
    state: QuranUiState,
    viewModel: QuranViewModel,
    onStartListening: () -> Unit,
    onExit: () -> Unit,
) {
    var barsVisible by rememberSaveable { mutableStateOf(true) }
    var radialAyah by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var radialCenter by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var listenAnchor by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var infoAyah by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val lastTouch = remember { floatArrayOf(0f, 0f) } // plain array: updating it never recomposes
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = LocalContext.current
    BackHandler(onBack = onExit)
    val view = LocalView.current
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    val toggle = { barsVisible = !barsVisible }
    val scale = state.correctionSettings.mushafTextScale
    Box(
        Modifier.fillMaxSize().background(Color(0xFFFFFEF8)).pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    e.changes.firstOrNull()?.takeIf { it.pressed }?.let { lastTouch[0] = it.position.x; lastTouch[1] = it.position.y }
                }
            }
        },
    ) {
        Column(
            Modifier.fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { toggle() }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 2.dp),
        ) {
            // Tasmee/hide/results use the same word view as the memorization section, so colours, hide-ayahs,
            // only-spoken-words, opening basmala and long-press options follow exactly the same rules.
            val tasmeeView = state.listening || state.hidden || state.evaluation != null || state.openingBasmalaActive
            if (tasmeeView) {
                val offsets = remember(state.ayahs) {
                    IntArray(state.ayahs.size).also { arr -> var o = 0; state.ayahs.forEachIndexed { k, a -> arr[k] = o; o += a.words.size } }
                }
                Column(
                    Modifier.fillMaxWidth().pointerInput(state.currentPage, state.totalPages) {
                        var drag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { drag = 0f },
                            onHorizontalDrag = { _, d -> drag += d },
                            onDragEnd = {
                                val t = com.example.quranoffline.data.pageAfterHorizontalSwipe(state.currentPage, state.totalPages, drag)
                                if (t != state.currentPage) viewModel.goToPage(t)
                                drag = 0f
                            },
                        )
                    },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MuqattaatOpeningCard(state, viewModel::startMemorizationFrom, viewModel::startMemorizationAfterOpening)
                    if (state.hidden && state.listening && !state.currentSpokenWord.isNullOrBlank()) {
                        Text(
                            state.currentSpokenWord.orEmpty(),
                            modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFF0B5D4B),
                        )
                    }
                    state.ayahs.forEachIndexed { index, ayah ->
                        AyahBlock(
                            ayah = ayah,
                            hidden = state.hidden,
                            evaluation = state.evaluation,
                            openingBasmalaEvaluation = state.openingBasmalaEvaluation,
                            openingBasmalaWordIndex = state.openingBasmalaWordIndex,
                            wordOffset = state.pageWordOffset + offsets.getOrElse(index) { 0 },
                            listening = state.listening,
                            currentSpokenWord = state.currentSpokenWord,
                            currentSpokenWordIndex = state.currentSpokenWordIndex,
                            onSetStart = viewModel::setMemorizationStart,
                            onMemorizeFromHere = viewModel::startMemorizationFrom,
                            onStartAfterOpening = viewModel::startMemorizationAfterOpening,
                            onPlayAyah = viewModel::playAyah,
                            onPlayBasmala = viewModel::playOpeningBasmala,
                            onReadFromHere = viewModel::playFromHere,
                            onPlainTap = toggle,
                        )
                    }
                }
            } else MushafPageSurface(
                pageAyahs = state.ayahs,
                state = state,
                onSetStart = viewModel::setMemorizationStart,
                onMemorizeFromHere = viewModel::startMemorizationFrom,
                onPlayAyah = viewModel::playAyah,
                onPlayBasmala = viewModel::playOpeningBasmala,
                onReadFromHere = viewModel::playFromHere,
                onReadSurahFromHere = viewModel::playSurahFromHere,
                onPageChanged = viewModel::goToPage,
                onBookmark = viewModel::saveBookmark,
                onOpenReaderActions = {},
                onOpenAyahActions = { _, _ -> },
                textScale = scale,
                onTapAyah = viewModel::tapAyahSelection,
                onLongPressAyah = { surah, ayah ->
                    viewModel.beginAyahSelection(surah, ayah)
                    radialCenter = androidx.compose.ui.geometry.Offset(lastTouch[0], lastTouch[1])
                    radialAyah = surah to ayah
                },
                onPlainTap = toggle,
                fullBleed = true,
                minHeightDp = LocalConfiguration.current.screenHeightDp,
            )
        }
        radialAyah?.let { (rs, ra) ->
            RadialAyahMenu(
                center = radialCenter,
                onDismiss = { radialAyah = null },
                actions = listOf(
                    Triple(Icons.Default.PlayArrow, "استماع المحدد فقط") { viewModel.playSelectionOrAyah(rs, ra) },
                    Triple(Icons.Default.ContentCopy, "نسخ المحدد") { viewModel.ayahShareText(rs, ra)?.let { clipboard.setText(androidx.compose.ui.text.AnnotatedString(it)) } },
                    Triple(Icons.Default.Share, "مشاركة المحدد") {
                        viewModel.ayahShareText(rs, ra)?.let { t ->
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, t)
                            context.startActivity(android.content.Intent.createChooser(send, null))
                        }
                    },
                    Triple(Icons.Default.Bookmark, "حفظ علامة") { viewModel.saveBookmark(rs, ra) },
                    Triple(Icons.Default.Mic, "تسميع المحدد فقط") { viewModel.prepareTasmeeForSelection(rs, ra); onStartListening() },
                    Triple(Icons.Default.Info, "معلومات الآية") { infoAyah = rs to ra },
                ),
            )
        }
        infoAyah?.let { (s0, a0) -> AyahInfoDialog(state, s0, a0, onDismiss = { infoAyah = null }) }
        listenAnchor?.let { (ls, la) ->
            ListenToSheet(
                state = state, surah = ls, ayah = la, onDismiss = { listenAnchor = null },
                onPlayAyah = viewModel::playAyah, onPlayScope = viewModel::playReaderScopeFromHere, onCustom = viewModel::playCustomRange,
            )
        }
        if (barsVisible) {
            Surface(
                color = Color(0xF20F5B4D), contentColor = Color.White,
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding(),
            ) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onExit) { Icon(Icons.Default.FullscreenExit, "خروج من ملء الشاشة", tint = Color.White) }
                        Text(
                            "سورة ${state.metadata?.sura(state.selectedSurah)?.arabicName ?: state.selectedSurah} • صفحة ${state.currentPage} من ${state.totalPages}",
                            Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Color.White,
                        )
                        IconButton(onClick = viewModel::toggleHidden) {
                            Icon(if (state.hidden) Icons.Default.Visibility else Icons.Default.VisibilityOff, if (state.hidden) "إظهار الآيات" else "إخفاء الآيات", tint = Color.White)
                        }
                        IconButton(
                            enabled = scale > CorrectionSettings.MIN_MUSHAF_TEXT_SCALE,
                            onClick = { viewModel.updateCorrectionSettings { it.copy(mushafTextScale = scale - 0.1f) } },
                        ) { Text("A-", color = Color.White, fontWeight = FontWeight.Bold) }
                        IconButton(
                            enabled = scale < CorrectionSettings.MAX_MUSHAF_TEXT_SCALE,
                            onClick = { viewModel.updateCorrectionSettings { it.copy(mushafTextScale = scale + 0.1f) } },
                        ) { Text("A+", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                    Surface(color = Color.White, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) { ReciterPicker() }
                }
            }
            Surface(
                color = Color(0xF20F5B4D), contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding(),
            ) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    if (state.notice.isNotBlank()) Text(state.notice, color = Color(0xFFD6EEE5), style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                        IconButton(onClick = viewModel::nextPage, enabled = state.currentPage < state.totalPages) { Icon(Icons.Default.ChevronLeft, "الصفحة التالية", tint = Color.White) }
                        if (state.selectedAyahIndices.isNotEmpty()) {
                            TextButton(onClick = viewModel::playSelectedAyahs) { Text("تشغيل المحدد", color = Color.White) }
                            TextButton(onClick = viewModel::clearAyahSelection) { Text("إلغاء التحديد", color = Color.White) }
                        } else {
                            IconButton(onClick = {
                                if (state.readerPlaying) viewModel.stopReader()
                                else {
                                    val first = state.selectedAyahIndices.firstOrNull()?.let { state.allAyahs.getOrNull(it) } ?: state.ayahs.firstOrNull()
                                    first?.let { listenAnchor = it.surah to it.ayah }
                                }
                            }) {
                                Icon(if (state.readerPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (state.readerPlaying) "إيقاف القراءة" else "تشغيل القراءة", tint = Color.White)
                            }
                        }
                        IconButton(onClick = { if (state.listening) viewModel.stopListening() else onStartListening() }) {
                            Icon(if (state.listening) Icons.Default.Stop else Icons.Default.Mic, if (state.listening) "إيقاف التسميع" else "بدء التسميع", tint = Color.White)
                        }
                        IconButton(onClick = viewModel::previousPage, enabled = state.currentPage > 1) { Icon(Icons.Default.ChevronRight, "الصفحة السابقة", tint = Color.White) }
                    }
                }
            }
        }
    }
}
