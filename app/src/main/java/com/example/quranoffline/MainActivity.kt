package com.example.quranoffline

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quranoffline.data.AssetMode
import com.example.quranoffline.data.QuranAyah
import com.example.quranoffline.data.QuranWord
import com.example.quranoffline.data.selectionKey
import com.example.quranoffline.data.QuranAyahMetadata
import com.example.quranoffline.data.QuranViewModel
import com.example.quranoffline.data.CorrectionPhase
import com.example.quranoffline.data.CorrectionSettings
import com.example.quranoffline.data.ResumePosition
import com.example.quranoffline.data.RecitationEngine
import com.example.quranoffline.data.WordStatus
import com.example.quranoffline.data.displayPause
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    private val quranViewModel: QuranViewModel by viewModels()
    private val microphonePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) quranViewModel.startListening()
    }
    private val createBackup = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let(quranViewModel::exportBackup)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QuranOfflineTheme {
                QuranOfflineApp(
                    viewModel = quranViewModel,
                    onRequestMicrophone = {
                        microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onExportBackup = { createBackup.launch("quran-online-backup.json") },
                )
            }
        }
    }
}

@Composable
private fun QuranOfflineTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(
            primary = Color(0xFF673AB7),
            onPrimary = Color.White,
            secondary = Color(0xFFFF6F61),
            tertiary = Color(0xFFD6B277),
            background = Color(0xFFF6F5F8),
            surface = Color.White,
            onSurface = Color(0xFF2B2438),
        ),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuranOfflineApp(
    viewModel: QuranViewModel,
    onRequestMicrophone: () -> Unit,
    onExportBackup: () -> Unit,
) {
    val state = viewModel.uiState.value
    var tab by remember { mutableIntStateOf(0) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("المصحف", fontWeight = FontWeight.Bold, color = Color.White)
                            Text("حفظ وتسميع القرآن", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE9DFFF))
                        }
                    },
                    actions = {
                        IconButton(onClick = { tab = 4 }) {
                            Icon(Icons.Default.MenuBook, contentDescription = "القراءة والاستماع", tint = Color.White)
                        }
                        IconButton(onClick = { tab = 5 }) {
                            Icon(Icons.Default.Settings, contentDescription = "الإعدادات", tint = Color.White)
                        }
                        Surface(
                            color = Color(0xFF8055C9),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.padding(end = 12.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Icon(Icons.Default.PlayArrow, null, tint = Color.White)
                                Spacer(Modifier.width(5.dp))
                                Text(if (state.engine == RecitationEngine.ONLINE) "عبر الإنترنت" else "بدون اتصال", style = MaterialTheme.typography.labelMedium, color = Color.White)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF673AB7),
                        titleContentColor = Color.White,
                        actionIconContentColor = Color.White,
                    ),
                )
                ScrollableTabRow(
                    selectedTabIndex = tab,
                    containerColor = Color(0xFF673AB7),
                    contentColor = Color.White,
                    edgePadding = 8.dp,
                    indicator = { positions ->
                        if (tab < positions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(positions[tab]),
                                height = 3.dp,
                                color = Color(0xFFFF6F61),
                            )
                        }
                    },
                ) {
                    listOf("الحفظ والتسميع", "السور", "الأجزاء", "السجل", "القراءة", "الإعدادات").forEachIndexed { index, title ->
                        Tab(
                            selected = tab == index,
                            onClick = { tab = index },
                            text = { Text(title, fontSize = 14.sp, fontWeight = if (tab == index) FontWeight.Bold else FontWeight.Normal) },
                            selectedContentColor = Color.White,
                            unselectedContentColor = Color(0xFFDCCBFF),
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (state.loadingAssets && state.ayahs.isEmpty()) {
            LoadingScreen(state.notice, Modifier.padding(padding))
        } else {
            when (tab) {
            0 -> MemorizationScreen(
                state = state,
                focusRecitation = true,
                onToggleHidden = viewModel::toggleHidden,
                onStart = {
                    if (state.listening) viewModel.stopListening()
                                            else if (state.assetsReady && (state.engine == RecitationEngine.ONLINE || state.modelReady)) onRequestMicrophone()
                        else viewModel.startListening()

                },
                onSave = viewModel::saveCurrentSession,
                onSetGoal = viewModel::setDailyGoal,
                onResume = onRequestMicrophone,
                onSetStart = viewModel::setMemorizationStart,
                onPlayAyah = viewModel::playAyah,
                onPlayBasmala = viewModel::playOpeningBasmala,
                onReadFromHere = viewModel::playFromHere,
                onMemorizeFromHere = viewModel::startMemorizationFrom,
                onCorrection = viewModel::playCorrectionForCurrentWord,
                onStopCorrection = viewModel::stopCorrection,
                onPreviousAyah = viewModel::startFromPreviousAyah,
                onSelectSurah = viewModel::selectSurah,
                onPreviousPage = viewModel::previousPage,
                onNextPage = viewModel::nextPage,
                modifier = Modifier.padding(padding),
            )
            1 -> MushafIndexScreen(
                state = state,
                onSelectSurah = { surah ->
                    viewModel.selectSurah(surah)
                    tab = 4
                },
                modifier = Modifier.padding(padding),
            )
            2 -> JuzIndexScreen(
                state = state,
                onSelectSurah = { surah ->
                    viewModel.selectSurah(surah)
                    tab = 4
                },
                modifier = Modifier.padding(padding),
            )
            3 -> HistoryScreen(
                state = state,
                onClear = viewModel::clearSessions,
                onClearErrors = viewModel::clearCorrectedErrors,
                onExport = onExportBackup,
                modifier = Modifier.padding(padding),
            )
            4 -> ReaderScreen(
                state = state,
                onPlayAyah = viewModel::playAyah,
                onPlayBasmala = viewModel::playOpeningBasmala,
                onReadFromHere = viewModel::playFromHere,
                onReadSurahFromHere = viewModel::playSurahFromHere,
                onPlaySelected = viewModel::playSelectedAyahs,
                onClearSelection = viewModel::clearAyahSelection,
                onTapAyah = viewModel::tapAyahSelection,
                onLongPressAyah = viewModel::beginAyahSelection,
                onSetStart = viewModel::setMemorizationStart,
                onMemorizeFromHere = viewModel::startMemorizationFrom,
                onStop = viewModel::stopReader,
                onScope = viewModel::playReaderScope,
                onPageChanged = viewModel::goToPage,
                onPreviousPage = viewModel::previousPage,
                onNextPage = viewModel::nextPage,
                modifier = Modifier.padding(padding),
            )
            5 -> SettingsScreen(
                state = state,
                onModeChange = viewModel::setMode,
                onDownload = viewModel::downloadCurrentSurah,
                onDeleteDownload = viewModel::deleteCurrentSurah,
                onSelectEngine = viewModel::setEngine,
                onDownloadModel = viewModel::downloadOfflineModel,
                onUpdateCorrectionSettings = viewModel::updateCorrectionSettings,
                onResetCorrectionSettings = viewModel::resetCorrectionSettings,
                modifier = Modifier.padding(padding),
            )
            }
        }
        }
    }
}

@Composable
private fun LoadingScreen(notice: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(Color(0xFFF6F5F8)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 30.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CircularProgressIndicator(color = Color(0xFF673AB7))
                Text("جاري تجهيز ورد", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF2B2438))
                Text(notice.ifBlank { "جاري تحميل النص والتوقيتات…" }, textAlign = TextAlign.Center, color = Color(0xFF8B8792))
            }
        }
    }
}

@Composable
private fun MushafIndexScreen(
    state: com.example.quranoffline.data.QuranUiState,
    onSelectSurah: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metadata = state.metadata
    LazyColumn(
        modifier = modifier.fillMaxSize().background(Color(0xFFF6F5F8)).padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SectionHeader(
                title = "السور",
                subtitle = "اختر السورة لفتح موضع الحفظ والتسميع الحالي.",
            )
        }
        items(metadata?.suras.orEmpty(), key = { it.index }) { surah ->
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("${surah.index}", color = Color(0xFF8B8792), style = MaterialTheme.typography.titleMedium)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("سورة ${surah.arabicName}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("${revelationLabel(surah.revelationType)} • ${surah.ayahCount} آية • صفحة ${metadata?.ayah(surah.index, 1)?.page ?: "—"}", color = Color(0xFF8B8792), style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = { onSelectSurah(surah.index) },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7)),
                    ) {
                        Text("فتح")
                    }
                }
            }
        }
    }
}

@Composable
private fun JuzIndexScreen(
    state: com.example.quranoffline.data.QuranUiState,
    onSelectSurah: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metadata = state.metadata
    LazyColumn(
        modifier = modifier.fillMaxSize().background(Color(0xFFF6F5F8)).padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SectionHeader(
                title = "الأجزاء",
                subtitle = "بداية كل جزء حسب ترتيب المصحف وصفحاته.",
            )
        }
        items(metadata?.juzs.orEmpty(), key = { it.index }) { juz ->
            val surah = metadata?.sura(juz.surah)
            val ayah = metadata?.ayah(juz.surah, juz.ayah)
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("${juz.index}", color = Color(0xFF8B8792), style = MaterialTheme.typography.titleMedium)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("الجزء ${juz.index}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("${surah?.arabicName ?: "السورة ${juz.surah}"} • الآية ${juz.ayah} • صفحة ${ayah?.page ?: "—"}", color = Color(0xFF8B8792), style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = { onSelectSurah(juz.surah) }) {
                        Text("فتح")
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, color = Color(0xFF2B2438), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Color(0xFF8B8792), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MemorizationScreen(
    state: com.example.quranoffline.data.QuranUiState,
    focusRecitation: Boolean,
    onToggleHidden: () -> Unit,
    onStart: () -> Unit,
    onSave: () -> Unit,
    onSetGoal: (Int) -> Unit,
    onResume: () -> Unit,
    onSetStart: (Int, Int, Int, Boolean) -> Unit,
    onPlayAyah: (Int, Int) -> Unit,
    onPlayBasmala: (Int) -> Unit,
    onReadFromHere: (Int, Int) -> Unit,
    onMemorizeFromHere: (Int, Int) -> Unit,
    onCorrection: () -> Unit,
    onStopCorrection: () -> Unit,
    onPreviousAyah: () -> Unit,
    onSelectSurah: (Int) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wordOffsets = remember(state.ayahs) {
        IntArray(state.ayahs.size).also { offsets ->
            var offset = 0
            state.ayahs.forEachIndexed { index, ayah ->
                offsets[index] = offset
                offset += ayah.words.size
            }
        }
    }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8F4))
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (focusRecitation) {
            item {
                RecitationCard(state, onStart, onResume, onCorrection, onStopCorrection, onPreviousAyah)
            }
        }
        item {
            Spacer(Modifier.height(10.dp))
            var surahMenuExpanded by remember { mutableStateOf(false) }
            val visibleSurahNumbers = state.ayahs.map { it.surah }.distinct()
            val visibleSurahLabel = visibleSurahLabel(state, visibleSurahNumbers)
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF45238A)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("موضع الحفظ الحالي", color = Color(0xFFE9DFFF), style = MaterialTheme.typography.labelLarge)
                        Text(visibleSurahLabel, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("صفحة ${state.currentPage}/${state.totalPages} • آيات ${visibleAyahRangeLabel(state)}", color = Color(0xFFF1EAFE), style = MaterialTheme.typography.bodySmall)
                    }
                    Box {
                        Button(
                            onClick = { surahMenuExpanded = true },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F61), contentColor = Color.White),
                        ) {
                            Text("اختيار السورة", fontWeight = FontWeight.SemiBold)
                        }
                        DropdownMenu(
                            expanded = surahMenuExpanded,
                            onDismissRequest = { surahMenuExpanded = false },
                        ) {
                            (1..114).forEach { surah ->
                                DropdownMenuItem(
                                    text = { Text("$surah — ${state.metadata?.sura(surah)?.arabicName ?: "سورة $surah"}") },
                                    onClick = {
                                        surahMenuExpanded = false
                                        onSelectSurah(surah)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            Text("مصحف التدريب — صفحات المصحف 1–604", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF123F35))
        }
        item { MushafMetadataCard(state) }
        item { MushafPageNavigationCard(state, onPreviousPage, onNextPage) }
        if (!focusRecitation) {
            item { RecitationCard(state, onStart, onResume, onCorrection, onStopCorrection, onPreviousAyah) }
        }
        if (state.hidden && state.listening && !state.currentSpokenWord.isNullOrBlank()) {
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF4EE))) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("الكلمة الحالية", fontWeight = FontWeight.SemiBold, color = Color(0xFF183D33))
                        Text(state.currentSpokenWord.orEmpty(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFF246B57), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right)
                        Text("تظهر الكلمة التي التقطها الجهاز، مع بقاء الكلمات السابقة ملوّنة داخل الآيات.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF66736C))
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                OutlinedButton(onClick = onToggleHidden, modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                    Icon(if (state.hidden) Icons.Default.Visibility else Icons.Default.VisibilityOff, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.hidden) "إظهار الآيات" else "إخفاء الآيات واختبرني")
                }
            }
        }
        itemsIndexed(
            items = state.ayahs,
            key = { _, ayah -> "${ayah.surah}:${ayah.ayah}" },
        ) { index, ayah ->
            val wordOffset = state.pageWordOffset + wordOffsets.getOrElse(index) { 0 }
            val ayahMetadata = state.metadata?.ayah(ayah.surah, ayah.ayah)
            val previousMetadata = if (index > 0) {
                state.ayahs.getOrNull(index - 1)?.let { previous -> state.metadata?.ayah(previous.surah, previous.ayah) }
            } else null
            if (ayahMetadata != null && (previousMetadata == null || isMushafBoundary(previousMetadata, ayahMetadata))) {
                MushafBoundaryRow(ayahMetadata)
            }
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    AyahBlock(
                        ayah = ayah,
                        hidden = state.hidden,
                        evaluation = state.evaluation,
                        wordOffset = wordOffset,
                        listening = state.listening,
                        currentSpokenWord = state.currentSpokenWord,
                        currentSpokenWordIndex = state.currentSpokenWordIndex,
                        onSetStart = onSetStart,
                        onMemorizeFromHere = onMemorizeFromHere,
                        onPlayAyah = onPlayAyah,
                        onPlayBasmala = onPlayBasmala,
                        onReadFromHere = onReadFromHere,
                    )
                }
            }
        }
        item { GoalCard(state.goal, onSetGoal) }
        if (state.notice.isNotBlank()) {
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF4EE))) {
                    Text(state.notice, color = Color(0xFF36584B), modifier = Modifier.fillMaxWidth().padding(14.dp))
                }
            }
        }
        state.evaluation?.let { evaluation ->
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("ملخص الجلسة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val progress = if (evaluation.total == 0) 0f else evaluation.matched.toFloat() / evaluation.total
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Text("مطابق: ${evaluation.matched} من ${evaluation.total} • غير مؤكد: ${evaluation.uncertain} • يحتاج مراجعة: ${evaluation.missing + evaluation.unexpected}")
                        if (state.transcript.isNotBlank()) {
                            Text("الناتج المسموع بالصيغة القرآنية", fontWeight = FontWeight.SemiBold)
                            Text(state.transcript, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
                        }
                        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Refresh, null)
                            Spacer(Modifier.width(8.dp))
                            Text("احفظ الجلسة محلياً")
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun CorrectionSettingsCard(
    settings: CorrectionSettings,
    onUpdate: ((CorrectionSettings) -> CorrectionSettings) -> Unit,
    onReset: () -> Unit,
) {
    val controlsEnabled = settings.automaticCorrectionEnabled
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("سياسة تصحيح الشيخ", fontWeight = FontWeight.Bold)
                    Text("يمكن تعديل توقيت التصحيح وحساسية الأخطاء محلياً.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF66736C))
                }
                OutlinedButton(onClick = onReset) { Text("الافتراضي") }
            }
            SettingSwitchRow(
                title = "التصحيح التلقائي",
                description = "شغّل المرجع تلقائياً عند ثبوت خطأ أو تخطٍ.",
                checked = settings.automaticCorrectionEnabled,
                enabled = true,
                onCheckedChange = { checked -> onUpdate { it.copy(automaticCorrectionEnabled = checked) } },
            )
            SettingSwitchRow(
                title = "تصحيح الكلمة الخاطئة",
                description = "يطبّق التصحيح على الكلمة التي لا تطابق الموضع المتوقع.",
                checked = settings.correctWrongWords,
                enabled = controlsEnabled,
                onCheckedChange = { checked -> onUpdate { it.copy(correctWrongWords = checked) } },
            )
            SettingSwitchRow(
                title = "تصحيح الكلمات المتخطاة",
                description = "يصحح أول كلمة تركتها عند الانتقال إلى كلمة لاحقة.",
                checked = settings.correctSkippedWords,
                enabled = controlsEnabled,
                onCheckedChange = { checked -> onUpdate { it.copy(correctSkippedWords = checked) } },
            )
            SettingSwitchRow(
                title = "الرجوع التلقائي إلى الآية السابقة",
                description = "عند اكتشاف بدء الآية السابقة، يرجع إليها ويبدأ منها دون ضغط زر.",
                checked = settings.automaticPreviousAyah,
                enabled = controlsEnabled,
                onCheckedChange = { checked -> onUpdate { it.copy(automaticPreviousAyah = checked) } },
            )
            SettingSwitchRow(
                title = "تصحيح القفز بين الآيات",
                description = "يثبت الآية المتوقعة ولا يتبع آية قفزت إليها.",
                checked = settings.correctAyahJumps,
                enabled = controlsEnabled,
                onCheckedChange = { checked -> onUpdate { it.copy(correctAyahJumps = checked) } },
            )
            Text("متى تُقرأ الآية كاملة؟", fontWeight = FontWeight.SemiBold, color = Color(0xFF183D33))
            Text("تُقرأ الكلمة فقط قبل هذا العدد، وتُقرأ الكلمة الأولى ثم الآية كاملة عند بلوغه في الآية نفسها.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF66736C))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                (1..CorrectionSettings.MAX_ERROR_THRESHOLD).forEach { count ->
                    FilterChip(
                        selected = settings.wholeAyahAfterConsecutiveErrors == count,
                        enabled = controlsEnabled,
                        onClick = { onUpdate { it.copy(wholeAyahAfterConsecutiveErrors = count) } },
                        label = { Text("$count") },
                    )
                }
            }
            Text("الافتراضي الحالي: 3 أخطاء متتالية في الآية نفسها.", style = MaterialTheme.typography.labelSmall, color = Color(0xFF66736C))
            SettingSwitchRow(
                title = "تشغيل مقطع أول خطأ قبل الآية",
                description = "عند اختيار الآية كاملة: شغّل مقطع أول خطأ ثم الآية، أو الآية وحدها.",
                checked = settings.playFirstErrorBeforeWholeAyah,
                enabled = controlsEnabled,
                onCheckedChange = { checked -> onUpdate { it.copy(playFirstErrorBeforeWholeAyah = checked) } },
            )
            Text("عدد تكرار مقطع الكلمة", fontWeight = FontWeight.SemiBold, color = Color(0xFF183D33))
            RepeatChoices(
                value = settings.wordRepeatCount,
                enabled = controlsEnabled,
                onSelected = { count -> onUpdate { current -> current.copy(wordRepeatCount = count) } },
            )
            Text("عدد تكرار الآية الكاملة", fontWeight = FontWeight.SemiBold, color = Color(0xFF183D33))
            RepeatChoices(
                value = settings.wholeAyahRepeatCount,
                enabled = controlsEnabled,
                onSelected = { count -> onUpdate { current -> current.copy(wholeAyahRepeatCount = count) } },
            )
            Text("الفاصل بين المقاطع: ${settings.displayPause()}", fontWeight = FontWeight.SemiBold, color = Color(0xFF183D33))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                CorrectionSettings.PAUSE_OPTIONS_MS.forEach { pause ->
                    FilterChip(
                        selected = settings.pauseBetweenPartsMs == pause,
                        enabled = controlsEnabled,
                        onClick = { onUpdate { it.copy(pauseBetweenPartsMs = pause) } },
                        label = { Text(if (pause == 0) "فوري" else "${pause}ms") },
                    )
                }
            }
            Text("هامش مقطع الكلمة قبل/بعد الصوت", fontWeight = FontWeight.SemiBold, color = Color(0xFF183D33))
            PaddingChoiceRow(
                title = "قبل",
                value = settings.clipPaddingBeforeMs,
                enabled = controlsEnabled,
                onSelected = { paddingMs -> onUpdate { current -> current.copy(clipPaddingBeforeMs = paddingMs) } },
            )
            PaddingChoiceRow(
                title = "بعد",
                value = settings.clipPaddingAfterMs,
                enabled = controlsEnabled,
                onSelected = { paddingMs -> onUpdate { current -> current.copy(clipPaddingAfterMs = paddingMs) } },
            )
            Text("موضع الاستئناف بعد التصحيح", fontWeight = FontWeight.SemiBold, color = Color(0xFF183D33))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                ResumePosition.values().forEach { position ->
                    FilterChip(
                        selected = settings.resumePosition == position,
                        enabled = controlsEnabled,
                        onClick = { onUpdate { it.copy(resumePosition = position) } },
                        label = { Text(position.label()) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = if (enabled) Color(0xFF183D33) else Color(0xFF9AA7A0))
            Text(description, style = MaterialTheme.typography.bodySmall, color = if (enabled) Color(0xFF66736C) else Color(0xFFADB7B1))
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RepeatChoices(
    value: Int,
    enabled: Boolean,
    onSelected: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..CorrectionSettings.MAX_REPEAT_COUNT).forEach { count ->
            FilterChip(
                selected = value == count,
                enabled = enabled,
                onClick = { onSelected(count) },
                label = { Text("$count مرة") },
            )
        }
    }
}

@Composable
private fun PaddingChoiceRow(
    title: String,
    value: Int,
    enabled: Boolean,
    onSelected: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(32.dp))
        listOf(0, 80, 150, 300).forEach { paddingMs ->
            FilterChip(
                selected = value == paddingMs,
                enabled = enabled,
                onClick = { onSelected(paddingMs) },
                label = { Text(if (paddingMs == 0) "0" else "${paddingMs}ms") },
            )
        }
    }
}

private fun ResumePosition.label(): String = when (this) {
    ResumePosition.AYAH_START -> "أول الآية"
    ResumePosition.FIRST_ERROR -> "أول خطأ"
    ResumePosition.WORD_AFTER_ERROR -> "بعد الخطأ"
}

@Composable
private fun EngineCard(
    state: com.example.quranoffline.data.QuranUiState,
    onSelectEngine: (RecitationEngine) -> Unit,
    onDownloadModel: () -> Unit,
) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("محرك التسميع", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = state.engine == RecitationEngine.ONLINE,
                    onClick = { onSelectEngine(RecitationEngine.ONLINE) },
                    label = { Text("عبر الإنترنت") },
                )
                FilterChip(
                    selected = state.engine == RecitationEngine.OFFLINE,
                    onClick = { onSelectEngine(RecitationEngine.OFFLINE) },
                    label = { Text("بدون اتصال") },
                )
            }
            Text(
                if (state.engine == RecitationEngine.ONLINE) {
                    "التعرّف الصوتي من Android؛ يحتاج الإنترنت ولا يحفظ التطبيق صوتك."
                } else {
                    "المحرك المحلي؛ يعالج الصوت على الجهاز بعد تنزيل النموذج."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF66736C),
            )
            if (state.engine == RecitationEngine.OFFLINE) {
                if (!state.offlineSupported) {
                    Text("هذا الهاتف يعمل بنظام 32-bit، وقد تسبب تشغيل النموذج المحلي فيه بانهيار داخلي. تم إيقاف التشغيل بدون اتصال لمنع إغلاق التطبيق.", color = Color(0xFF9B4D3C))
                } else {
                    Text(if (state.modelReady) "النموذج المحلي جاهز." else "النموذج المحلي غير موجود على الجهاز.", style = MaterialTheme.typography.labelMedium)
                    if (!state.modelReady) {
                        Button(onClick = onDownloadModel, enabled = !state.modelDownloading, modifier = Modifier.fillMaxWidth()) {
                            Text(if (state.modelDownloading) "جاري تنزيل النموذج…" else "تنزيل النموذج المحلي")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineAssetCard(
    state: com.example.quranoffline.data.QuranUiState,
    onModeChange: (AssetMode) -> Unit,
    onDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("طريقة استخدام الأصول", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = state.mode == AssetMode.STREAM,
                    onClick = { onModeChange(AssetMode.STREAM) },
                    label = { Text("بث مباشر") },
                )
                FilterChip(
                    selected = state.mode == AssetMode.DOWNLOAD,
                    onClick = { onModeChange(AssetMode.DOWNLOAD) },
                    label = { Text("تنزيل اختياري") },
                )
            }
            Text(
                if (state.mode == AssetMode.STREAM) {
                    "النص والتوقيتات في الذاكرة فقط، والصوت يُبث من GitHub عند الحاجة دون حفظ ملفه."
                } else {
                    "النص والتوقيتات تُحفظ تلقائياً؛ صوت السورة يُحفظ فقط بعد ضغط زر التنزيل."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF66736C),
            )
            if (state.mode == AssetMode.DOWNLOAD) {
                val total = state.totalAyahsInSurah.coerceAtLeast(1)
                LinearProgressIndicator(
                    progress = { state.downloadedAyahs.toFloat() / total },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("الصوت المحلي للسورة: ${state.downloadedAyahs}/$total آيات", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onDownload, enabled = !state.downloadInProgress, modifier = Modifier.weight(1f)) {
                        Text(if (state.downloadInProgress) "جاري التنزيل…" else "تنزيل السورة")
                    }
                    OutlinedButton(onClick = onDeleteDownload, modifier = Modifier.weight(1f)) { Text("حذف المحلي") }
                }
            }
        }
    }
}

@Composable
private fun GoalCard(
    goal: com.example.quranoffline.data.DailyGoal,
    onSetGoal: (Int) -> Unit,
) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("هدف اليوم", fontWeight = FontWeight.Bold)
                    Text("أنجزت ${goal.completedAyahs} من ${goal.targetAyahs} آيات", color = Color(0xFF66736C))
                }
                Text("${(goal.progress * 100).toInt()}%", color = Color(0xFF246B57), fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(progress = { goal.progress }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(3, 5, 10).forEach { target ->
                    FilterChip(
                        selected = goal.targetAyahs == target,
                        onClick = { onSetGoal(target) },
                        label = { Text("$target آيات") },
                    )
                }
            }
        }
    }
}

@Composable
private fun MushafPageNavigationCard(
    state: com.example.quranoffline.data.QuranUiState,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("صفحات المصحف", color = Color(0xFF183D33), fontWeight = FontWeight.Bold)
            Text("الصفحة ${state.currentPage} من ${state.totalPages} — مطابقة بنيوية لترقيم الصفحات وتسلسل الآيات في مرجع التحقق؛ لا تُستخدم الصور داخل التطبيق.", color = Color(0xFF66736C), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onPreviousPage, enabled = state.currentPage > 1, modifier = Modifier.weight(1f)) {
                    Text("الصفحة السابقة")
                }
                Button(onClick = onNextPage, enabled = state.currentPage < state.totalPages, modifier = Modifier.weight(1f)) {
                    Text("الصفحة التالية")
                }
            }
        }
    }
}

@Composable
private fun MushafMetadataCard(state: com.example.quranoffline.data.QuranUiState) {
    val metadata = state.metadata
    val visibleSurahs = state.ayahs.map { it.surah }.distinct()
    val first = state.ayahs.firstOrNull()?.let { metadata?.ayah(it.surah, it.ayah) }
    val last = state.ayahs.lastOrNull()?.let { metadata?.ayah(it.surah, it.ayah) }
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF4EE))) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("تنظيم المصحف", color = Color(0xFF183D33), fontWeight = FontWeight.Bold)
            Text(
                buildString {
                    if (visibleSurahs.size <= 1) {
                        val surah = visibleSurahs.firstOrNull()?.let { metadata?.sura(it) }
                        append(surah?.arabicName ?: "سورة ${visibleSurahs.firstOrNull() ?: state.selectedSurah}")
                        surah?.revelationType?.let { append(" • ").append(revelationLabel(it)) }
                        surah?.ayahCount?.let { append(" • $it آية") }
                    } else {
                        append("السور: ")
                        append(visibleSurahs.joinToString("، ") { metadata?.sura(it)?.arabicName ?: "سورة $it" })
                    }
                },
                color = Color(0xFF36584B),
            )
            if (first != null && last != null) {
                Text(
                    "صفحة ${rangeLabel(first.page, last.page)} • الجزء ${rangeLabel(first.juz, last.juz)} • ${quarterRangeLabel(first.quarter, last.quarter)}",
                    color = Color(0xFF36584B),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "المنزل ${rangeLabel(first.manzil, last.manzil)} • الركوع ${rangeLabel(first.ruku, last.ruku)}",
                    color = Color(0xFF36584B),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun MushafBoundaryRow(metadata: QuranAyahMetadata) {
    Surface(color = Color(0xFFF4E8D2), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "صفحة ${metadata.page} • الجزء ${metadata.juz} • ${quarterLabel(metadata.quarter)} • الركوع ${metadata.ruku}",
                color = Color(0xFF6E4B25),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodySmall,
            )
            metadata.sajda?.let { sajda ->
                Text(sajdaLabel(sajda), color = Color(0xFF8A3D2E), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun isMushafBoundary(previous: QuranAyahMetadata, current: QuranAyahMetadata): Boolean =
    previous.page != current.page || previous.juz != current.juz || previous.quarter != current.quarter ||
        previous.manzil != current.manzil || previous.ruku != current.ruku || previous.sajda != current.sajda

private fun visibleSurahLabel(state: com.example.quranoffline.data.QuranUiState, surahs: List<Int>): String = when {
    surahs.isEmpty() -> "المصحف"
    surahs.size == 1 -> "سورة ${state.metadata?.sura(surahs.first())?.arabicName ?: surahs.first()}"
    else -> "السور: ${surahs.joinToString("، ") { state.metadata?.sura(it)?.arabicName ?: "سورة $it" }}"
}

private fun visibleAyahRangeLabel(state: com.example.quranoffline.data.QuranUiState): String {
    val first = state.ayahs.firstOrNull() ?: return "—"
    val last = state.ayahs.lastOrNull() ?: return "${first.surah}:${first.ayah}"
    return if (first.surah == last.surah) {
        "${first.surah}:${first.ayah}–${last.ayah}"
    } else {
        "${first.surah}:${first.ayah}–${last.surah}:${last.ayah}"
    }
}

private fun rangeLabel(first: Int, last: Int): String = if (first == last) "$first" else "$first–$last"

private fun revelationLabel(type: com.example.quranoffline.data.RevelationType): String = when (type) {
    com.example.quranoffline.data.RevelationType.MECCAN -> "مكية"
    com.example.quranoffline.data.RevelationType.MEDINAN -> "مدنية"
}

private fun sajdaLabel(type: com.example.quranoffline.data.SajdaType): String = when (type) {
    com.example.quranoffline.data.SajdaType.RECOMMENDED -> "سجدة تلاوة مستحبة"
    com.example.quranoffline.data.SajdaType.OBLIGATORY -> "سجدة تلاوة واجبة"
}

private fun quarterLabel(quarter: Int): String {
    val hizb = ((quarter - 1) / 4) + 1
    val quarterInHizb = ((quarter - 1) % 4) + 1
    return "الحزب $hizb، الربع $quarterInHizb"
}

private fun quarterRangeLabel(first: Int, last: Int): String =
    if (first == last) quarterLabel(first) else "${quarterLabel(first)} – ${quarterLabel(last)}"

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun AyahBlock(
    ayah: QuranAyah,
    hidden: Boolean,
    evaluation: com.example.quranoffline.data.RecitationEvaluation?,
    wordOffset: Int,
    listening: Boolean,
    currentSpokenWord: String?,
    currentSpokenWordIndex: Int,
    onSetStart: (Int, Int, Int, Boolean) -> Unit,
    onMemorizeFromHere: (Int, Int) -> Unit,
    onPlayAyah: (Int, Int) -> Unit,
    onPlayBasmala: (Int) -> Unit,
    onReadFromHere: (Int, Int) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val openingBasmala = if (ayah.ayah == 1) com.example.quranoffline.data.QuranPresentation.openingBasmalaForSurah(ayah.surah) else null
    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { menuExpanded = true }),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("﴿${ayah.ayah}﴾", color = Color(0xFFB6844A), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            Text("اضغط مطولاً للخيارات", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8B8792))
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "خيارات الآية", tint = Color(0xFF76552A))
            }
        }
        openingBasmala?.let { basmala ->
            Surface(color = Color(0xFFFFF7E8), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp)) {
                    IconButton(onClick = { onPlayBasmala(ayah.surah) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "تشغيل البسملة الافتتاحية", tint = Color(0xFF76552A))
                    }
                    Text("${basmala.text}  ·  جملة مستقلة لا تُحسب من كلمات الآية", modifier = Modifier.weight(1f).padding(vertical = 10.dp), textAlign = TextAlign.Right, color = Color(0xFF76552A), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        val wordFlow: @Composable () -> Unit = {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ayah.words.forEachIndexed { index, word ->
                    val globalIndex = wordOffset + index
                    val wordEvaluation = evaluation?.words?.getOrNull(globalIndex)
                    val status = wordEvaluation?.status
                    val revealed = wordEvaluation?.observed != null
                    val isCurrent = listening && globalIndex == currentSpokenWordIndex && !currentSpokenWord.isNullOrBlank()
                    val background = when (status) {
                        WordStatus.MATCHED -> Color(0xFFDFF1E5)
                        WordStatus.EXTRA_OR_UNEXPECTED -> Color(0xFFFFDDD8)
                        WordStatus.UNCERTAIN -> Color(0xFFFFF0C9)
                        WordStatus.MISSING -> Color(0xFFFFDDD8)
                        null -> if (isCurrent) Color(0xFFDFF1E5) else Color.Transparent
                    }
                    val shownText = when {
                        !hidden -> word.text
                        revealed -> word.text
                        isCurrent -> currentSpokenWord.orEmpty()
                        else -> "•"
                    }
                    Surface(color = background, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = shownText,
                            color = if (hidden && !revealed && !isCurrent) Color(0xFF202A25) else Color(0xFF183D33),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
        wordFlow()
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(text = { Text("تعيين بداية الحفظ من هنا") }, onClick = {
                menuExpanded = false; onSetStart(ayah.surah, ayah.ayah, 0, false)
            })
            if (openingBasmala != null) {
                DropdownMenuItem(text = { Text("تعيين البداية مع البسملة الافتتاحية") }, onClick = {
                    menuExpanded = false; onSetStart(ayah.surah, ayah.ayah, 0, true)
                })
            }
            DropdownMenuItem(text = { Text("ابدأ التسميع من هنا") }, onClick = {
                menuExpanded = false; onMemorizeFromHere(ayah.surah, ayah.ayah)
            })
            DropdownMenuItem(text = { Text("تشغيل الآية") }, onClick = {
                menuExpanded = false; onPlayAyah(ayah.surah, ayah.ayah)
            })
            DropdownMenuItem(text = { Text("بدء القراءة من هنا") }, onClick = {
                menuExpanded = false; onReadFromHere(ayah.surah, ayah.ayah)
            })
        }
    }
}

@Composable
private fun RecitationCard(
    state: com.example.quranoffline.data.QuranUiState,
    onStart: () -> Unit,
    onResume: () -> Unit,
    onCorrection: () -> Unit,
    onStopCorrection: () -> Unit,
    onPreviousAyah: () -> Unit,
) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF45238A))) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("حفظ وتسميع القرآن", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "ابدأ من موضعك الحالي، وستظهر الكلمات السابقة بألوانها أثناء المتابعة.",
                color = Color(0xFFE9DFFF),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.openingBasmalaActive) {
                Text(
                    "جلسة البسملة الافتتاحية مستقلة: تُسمع وتُصحح وحدها، ولا تُحسب آية ولا كلمة. بعد انتهائها تبدأ الآية الأولى مباشرة.",
                    color = Color(0xFFFFE1B5),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            state.checkpoint?.let { checkpoint ->
                Text("آخر موضع محفوظ: السورة ${checkpoint.surah}، الآية ${checkpoint.ayah}، الكلمة ${checkpoint.wordIndex + 1}", color = Color(0xFFFFE1B5), style = MaterialTheme.typography.bodySmall)
                if (!state.listening) {
                    OutlinedButton(onClick = onResume, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("استئناف من الموضع المحفوظ", color = Color.White)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("هدف اليوم", color = Color(0xFFE9DFFF), style = MaterialTheme.typography.labelMedium)
                    Text("${state.goal.completedAyahs} من ${state.goal.targetAyahs} آيات", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Text("${(state.goal.progress * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(
                progress = { state.goal.progress },
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFFF6F61),
                trackColor = Color(0xFF6A4BA8),
            )
            Text(
                when {
                    state.engine == RecitationEngine.OFFLINE && !state.offlineSupported -> "التسميع بدون اتصال موقوف على هذا الهاتف لحماية التطبيق من الإغلاق."
                    state.listening && state.engine == RecitationEngine.ONLINE -> "جارٍ التعرّف عبر الإنترنت؛ لا يُحفظ صوتك في التطبيق…"
                    state.listening -> "جارٍ التعرّف محلياً؛ لا يغادر الصوت جهازك…"
                    state.engine == RecitationEngine.ONLINE -> "ابدأ التلاوة؛ التعرّف عبر الإنترنت والتصحيح النصي يعملان دون حفظ صوتك."
                    else -> "ابدأ التلاوة؛ التعرّف بدون اتصال بعد تجهيز النموذج."
                },
                color = Color(0xFFD7E9DF),
            )
            Button(
                onClick = onStart,
                enabled = !state.correctionPlaying && (state.engine != RecitationEngine.OFFLINE || state.offlineSupported),
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD6B277),
                    disabledContainerColor = Color(0xFF355A4D),
                    disabledContentColor = Color(0xFFC9D9D1),
                ),
            ) {
                Icon(if (state.listening) Icons.Default.Pause else Icons.Default.Mic, null, tint = Color(0xFF183D33))
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        state.engine == RecitationEngine.OFFLINE && !state.offlineSupported -> "بدون اتصال غير متاح على هذا الهاتف"
                        state.correctionPlaying -> "يعمل التصحيح الصوتي…"
                        state.listening -> "إيقاف الجلسة"
                        else -> "ابدأ التسميع"
                    },
                    color = if (state.engine == RecitationEngine.OFFLINE && !state.offlineSupported || state.correctionPlaying) Color(0xFFC9D9D1) else Color(0xFF183D33),
                )
            }
            state.correctionMessage?.let { message ->
                Text(message, color = Color(0xFFFFE1B5), fontWeight = FontWeight.SemiBold)
            }
            when (state.correctionPhase) {
                CorrectionPhase.PLAYING_FIRST_ERROR -> Text("يُصحّح أول كلمة خاطئة…", color = Color(0xFFFFE1B5))
                CorrectionPhase.PLAYING_WHOLE_AYAH -> Text("تُقرأ الآية كاملة للتصحيح…", color = Color(0xFFFFE1B5))
                CorrectionPhase.WAITING_RESUME -> Text("ابدأ من أول الآية أو من أي كلمة داخلها.", color = Color(0xFFFFE1B5))
                CorrectionPhase.LISTENING -> Unit
            }
            if (state.listening && state.currentAyahIndex >= 0) {
                val currentAyah = state.allAyahs.getOrNull(state.currentAyahIndex)
                val ayahNumber = currentAyah?.ayah
                if (ayahNumber != null) {
                    Text("الآية المتوقعة: $ayahNumber • الكلمة التالية: ${state.currentWordInAyah + 1}", color = Color(0xFFD7E9DF))
                    if (state.currentAyahLocked) {
                        Text("الآية الحالية مقفلة: صحّح كل الكلمات الحمراء قبل الانتقال إلى الآية التالية.", color = Color(0xFFFFE1B5), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (state.engine == RecitationEngine.OFFLINE && !state.offlineSupported) {
                Text("بدون اتصال غير متاح بأمان على هذا الهاتف 32-bit؛ استخدم التعرّف عبر الإنترنت أو هاتفاً 64-bit.", color = Color(0xFFFFE1B5))
            }
            if (state.listening && !state.correctionPlaying && state.currentAyahIndex > 0) {
                val previousAyah = state.allAyahs.getOrNull(state.currentAyahIndex - 1)
                val previousAyahNumber = previousAyah?.ayah
                if (previousAyahNumber != null) {
                    OutlinedButton(onClick = onPreviousAyah, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Refresh, null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("ابدأ من الآية السابقة ($previousAyahNumber)", color = Color.White)
                    }
                }
            }
            if (state.currentWordIndex >= 0) {
                OutlinedButton(
                    onClick = if (state.correctionPlaying) onStopCorrection else onCorrection,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(if (state.correctionPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.correctionPlaying) "إيقاف التصحيح الصوتي" else "صحّح لي بصوت القارئ", color = Color.White)
                }
            }
            if (state.loadingAssets) {
                Text("جاري تحميل النص والتوقيتات…", color = Color(0xFFFFE1B5))
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    state: com.example.quranoffline.data.QuranUiState,
    onClear: () -> Unit,
    onClearErrors: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(Color(0xFFF7F8F4)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("سجل الجلسات", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("مخزن على هذا الجهاز فقط", color = Color(0xFF66736C))
            }
            OutlinedButton(onClick = onExport) { Text("تصدير") }
            Spacer(Modifier.width(6.dp))
            if (state.sessions.isNotEmpty()) {
                IconButton(onClick = onClear) { Icon(Icons.Default.Refresh, contentDescription = "حذف السجل") }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (state.correctedErrors.isNotEmpty()) {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4E8))) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("الأخطاء التي صُححت", fontWeight = FontWeight.Bold, color = Color(0xFF6E4B25))
                            Text("${state.correctedErrors.size} خطأ محفوظ محلياً", color = Color(0xFF8A6A45), style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = onClearErrors) { Text("مسح") }
                    }
                    state.correctedErrors.take(5).forEach { error ->
                        Text(
                            "${error.surah}:${error.ayah} • الكلمة ${error.localWordIndex + 1} • المتوقع: ${error.expected} • سُمع: ${error.observed ?: "—"} • صُحح بـ: ${error.correctedWith ?: "—"}",
                            color = Color(0xFF6E4B25),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (state.correctedErrors.size > 5) {
                        Text("يظهر آخر 5 أخطاء هنا؛ يبقى السجل الكامل محفوظاً محلياً.", color = Color(0xFF8A6A45), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        if (state.sessions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("لا توجد جلسات محفوظة بعد.")
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.sessions, key = { it.id }) { session ->
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("سورة ${session.surah} • الآية ${session.startAyah}–${session.endAyah}", fontWeight = FontWeight.SemiBold)
                            Text("مطابق ${session.matchedWords}/${session.totalWords} • غير مؤكد ${session.uncertainWords}")
                            Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(session.createdAt)), style = MaterialTheme.typography.labelSmall, color = Color(0xFF66736C))
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderScreen(
    state: com.example.quranoffline.data.QuranUiState,
    onPlayAyah: (Int, Int) -> Unit,
    onPlayBasmala: (Int) -> Unit,
    onReadFromHere: (Int, Int) -> Unit,
    onReadSurahFromHere: (Int, Int) -> Unit,
    onPlaySelected: () -> Unit,
    onClearSelection: () -> Unit,
    onTapAyah: (Int, Int) -> Unit,
    onLongPressAyah: (Int, Int) -> Unit,
    onSetStart: (Int, Int, Int, Boolean) -> Unit,
    onMemorizeFromHere: (Int, Int) -> Unit,
    onStop: () -> Unit,
    onScope: (com.example.quranoffline.data.ReaderScope) -> Unit,
    onPageChanged: (Int) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val metadata = state.metadata
        val contentState = rememberLazyListState()

        LazyColumn(
        state = contentState,
        modifier = modifier.fillMaxSize().background(Color(0xFFF7F8F4)).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF45238A))) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("قارئ المصحف", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("سورة ${state.metadata?.sura(state.selectedSurah)?.arabicName ?: state.selectedSurah} • الصفحة ${state.currentPage} من ${state.totalPages}", color = Color(0xFFE9DFFF))
                    Text("اسحب الصفحة يميناً أو شمالاً للتنقل. الآيات مرتبة داخل صفحة المصحف، وليست أسطراً منفصلة خارج سياقها.", color = Color(0xFFE9DFFF))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = onPreviousPage, enabled = state.currentPage > 1, modifier = Modifier.weight(1f)) { Text("السابق") }
                        Button(onClick = onNextPage, enabled = state.currentPage < state.totalPages, modifier = Modifier.weight(1f)) { Text("التالي") }
                    }
                    Text("النطاق الصوتي: ${readerScopeLabel(state.readerScope)}${state.readerCurrentKey?.let { " • الموضع $it" } ?: ""}", color = Color(0xFFFFE1B5), fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.example.quranoffline.data.ReaderScope.values().forEach { scope ->
                            FilterChip(selected = state.readerScope == scope, onClick = { onScope(scope) }, label = { Text(readerScopeLabel(scope)) })
                        }
                    }
                    if (state.readerPlaying) {
                        OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("إيقاف القراءة", color = Color.White)
                        }
                    } else {
                        Button(onClick = { onScope(state.readerScope) }, modifier = Modifier.fillMaxWidth(), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFD6B277), contentColor = Color(0xFF183D33))) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("تشغيل من نقطة الحفظ")
                        }
                    }
                }
            }
        }
        if (state.selectedAyahIndices.isNotEmpty()) {
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF2D8))) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("نطاق آيات متصل محدد", fontWeight = FontWeight.Bold, color = Color(0xFF76552A))
                        Text("تم تحديد ${state.selectedAyahIndices.size} آيات متتابعة في ترتيب المصحف. الضغط على آية لاحقة يوسّع النطاق، والضغط على آية سابقة يقتطع ما بعدها.", color = Color(0xFF76552A))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(onClick = onPlaySelected, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("قراءة المحدد")
                            }
                            OutlinedButton(onClick = onClearSelection, modifier = Modifier.weight(1f)) { Text("إلغاء التحديد") }
                        }
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("صفحة المصحف الحالية", fontWeight = FontWeight.Bold, color = Color(0xFF123F35))
                    Text("تمثيل نصي منظم يعتمد حدود الصفحة وتسلسل الآيات في metadata المرجعية، من دون إدراج صور أو خطوط خارجية.", color = Color(0xFF66736C))
                    Text("لخيارات الآية: اضغط مطولاً على أي آية أو استخدم زر النقاط الثلاث.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF66736C))
                }
            }
        }
        item {
            MushafPageSurface(
                pageAyahs = if (metadata == null) emptyList() else state.allAyahs.filter { metadata.ayah(it.surah, it.ayah)?.page == state.currentPage },
                state = state,
                onSetStart = onSetStart,
                onMemorizeFromHere = onMemorizeFromHere,
                onPlayAyah = onPlayAyah,
                onPlayBasmala = onPlayBasmala,
                onReadFromHere = onReadFromHere,
                onReadSurahFromHere = onReadSurahFromHere,
                onPageChanged = onPageChanged,
                onTapAyah = onTapAyah,
                onLongPressAyah = onLongPressAyah,
            )
        }
        }
    }
}

@Composable
private fun MushafPageMarkerStrip(
    state: com.example.quranoffline.data.QuranUiState,
    pageAyahs: List<QuranAyah>,
) {
    val first = pageAyahs.firstOrNull() ?: return
    val firstMetadata = state.metadata?.ayah(first.surah, first.ayah) ?: return
    val pageSajda = pageAyahs.any { state.metadata?.ayah(it.surah, it.ayah)?.sajda != null }
    val hizb = ((firstMetadata.quarter - 1) / 4) + 1
    val quarterInHizb = ((firstMetadata.quarter - 1) % 4) + 1
    Surface(
        color = Color(0xFFF8EFD9),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "۞ الحزب $hizb • الربع $quarterInHizb • الجزء ${firstMetadata.juz}",
                color = Color(0xFF6E4B25),
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                "ع الركوع ${firstMetadata.ruku}${if (pageSajda) "  ۩ سجدة" else ""}",
                color = if (pageSajda) Color(0xFF8A3D2E) else Color(0xFF6E4B25),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MushafPageSurface(
    pageAyahs: List<QuranAyah>,
    state: com.example.quranoffline.data.QuranUiState,
    onSetStart: (Int, Int, Int, Boolean) -> Unit,
    onMemorizeFromHere: (Int, Int) -> Unit,
    onPlayAyah: (Int, Int) -> Unit,
    onPlayBasmala: (Int) -> Unit,
    onReadFromHere: (Int, Int) -> Unit,
    onReadSurahFromHere: (Int, Int) -> Unit,
    onPageChanged: (Int) -> Unit,
    onTapAyah: (Int, Int) -> Unit,
    onLongPressAyah: (Int, Int) -> Unit,
) {
    var horizontalDrag by remember { mutableFloatStateOf(0f) }
    val isOpeningPage = state.currentPage <= 2
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 760.dp)
            .pointerInput(state.currentPage, state.totalPages) {
                detectHorizontalDragGestures(
                    onDragStart = { horizontalDrag = 0f },
                    onHorizontalDrag = { _, dragAmount -> horizontalDrag += dragAmount },
                    onDragEnd = {
                        when {
                            horizontalDrag <= -80f && state.currentPage < state.totalPages -> onPageChanged(state.currentPage + 1)
                            horizontalDrag >= 80f && state.currentPage > 1 -> onPageChanged(state.currentPage - 1)
                        }
                        horizontalDrag = 0f
                    },
                    onDragCancel = { horizontalDrag = 0f },
                )
            },
        shape = RoundedCornerShape(if (isOpeningPage) 24.dp else 22.dp),
        border = BorderStroke(1.dp, Color(0xFFD8C7A7)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEF8)),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (isOpeningPage) Modifier.border(1.dp, Color(0xFFE4D4B8), RoundedCornerShape(18.dp)).padding(6.dp) else Modifier)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("صفحة ${state.currentPage}", color = Color(0xFF76552A), fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            MushafPageMarkerStrip(state, pageAyahs)
            if (pageAyahs.isEmpty()) {
                Text("لا توجد آيات محملة لهذه الصفحة حالياً.", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            } else {
                val ayahByKey = pageAyahs.associateBy { "${it.surah}:${it.ayah}" }
                val layoutLines = state.pageLayout?.linesForPage(state.currentPage).orEmpty()
                if (layoutLines.isEmpty()) {
                    pageAyahs.forEach { ayah ->
                        PageAyahLine(
                            ayah = ayah,
                            active = state.readerCurrentKey == ayah.selectionKey(),
                            selected = state.selectedAyahIndices.contains(state.allAyahs.indexOf(ayah)),
                            onSetStart = onSetStart,
                            onMemorizeFromHere = onMemorizeFromHere,
                            onPlayAyah = onPlayAyah,
                            onPlayBasmala = onPlayBasmala,
                            onReadFromHere = onReadFromHere,
                            onReadSurahFromHere = onReadSurahFromHere,
                            onTapAyah = onTapAyah,
                            onLongPressAyah = onLongPressAyah,
                        )
                    }
                } else {
                    layoutLines.forEach { line ->
                        when (line.type) {
                            "sura" -> {
                                val surah = line.wordKeys.firstOrNull()?.surah ?: pageAyahs.first().surah
                                Text(
                                    "سورة ${state.metadata?.sura(surah)?.arabicName ?: surah}",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    color = Color(0xFF76552A),
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            "bismillah" -> {
                                val surah = pageAyahs.first().surah
                                com.example.quranoffline.data.QuranPresentation.openingBasmalaForSurah(surah)?.let { basmala ->
                                    Surface(color = Color(0xFFFFF7E8), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp)) {
                                            IconButton(onClick = { onPlayBasmala(surah) }) { Icon(Icons.Default.PlayArrow, contentDescription = "تشغيل البسملة الافتتاحية", tint = Color(0xFF76552A)) }
                                            Text("${basmala.text}  ·  جملة مستقلة لا تدخل في الآية", modifier = Modifier.weight(1f).padding(vertical = 8.dp), textAlign = TextAlign.Right, color = Color(0xFF76552A), style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                            else -> {
                                val lineGroups = line.wordKeys.mapNotNull { key ->
                                    ayahByKey["${key.surah}:${key.ayah}"]?.let { ayah -> ayah to key }
                                }.groupBy { it.first.selectionKey() }
                                if (lineGroups.isNotEmpty()) {
                                    val lineEntries = lineGroups.values.toList()
                                    val lineTextLength = lineEntries.sumOf { entries ->
                                        val ayah = entries.first().first
                                        entries.sumOf { (_, key) -> ayah.words.getOrNull(key.word - 1)?.text?.length ?: 0 } + 6
                                    }
                                    val lineFontSize = when {
                                        lineTextLength >= 78 -> 13.sp
                                        lineTextLength >= 62 -> 15.sp
                                        lineTextLength >= 46 -> 17.sp
                                        lineTextLength >= 32 -> 20.sp
                                        else -> 23.sp
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.End),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        lineEntries.forEach { entries ->
                                            val ayah = entries.first().first
                                            val words = entries.mapNotNull { (_, key) -> ayah.words.getOrNull(key.word - 1) }
                                            PageAyahInline(
                                                ayah = ayah,
                                                words = words,
                                                fontSize = lineFontSize,
                                                showAyahMarker = entries.first().second.word == 1,
                                                showSajdaMarker = state.metadata?.ayah(ayah.surah, ayah.ayah)?.sajda != null,
                                                active = state.readerCurrentKey == ayah.selectionKey(),
                                                selected = state.selectedAyahIndices.contains(state.allAyahs.indexOf(ayah)),
                                                onSetStart = onSetStart,
                                                onMemorizeFromHere = onMemorizeFromHere,
                                                onPlayAyah = onPlayAyah,
                                                onReadFromHere = onReadFromHere,
                                                onReadSurahFromHere = onReadSurahFromHere,
                                                onTapAyah = onTapAyah,
                                                onLongPressAyah = onLongPressAyah,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun PageAyahInline(
    ayah: QuranAyah,
    words: List<QuranWord>,
    fontSize: androidx.compose.ui.unit.TextUnit,
    showAyahMarker: Boolean,
    showSajdaMarker: Boolean,
    active: Boolean,
    selected: Boolean,
    onSetStart: (Int, Int, Int, Boolean) -> Unit,
    onMemorizeFromHere: (Int, Int) -> Unit,
    onPlayAyah: (Int, Int) -> Unit,
    onReadFromHere: (Int, Int) -> Unit,
    onReadSurahFromHere: (Int, Int) -> Unit,
    onTapAyah: (Int, Int) -> Unit,
    onLongPressAyah: (Int, Int) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val backgroundColor = when {
        selected -> Color(0xFFFFE0A6)
        active -> Color(0xFFEAF4EE)
        else -> Color.Transparent
    }
    val lineText = buildString {
        if (showAyahMarker) append("﴿${ayah.ayah}﴾ ")
        if (showSajdaMarker) append("۩ ")
        append(words.joinToString(" ") { it.text })
    }
    Box(
        Modifier
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .combinedClickable(onClick = { onTapAyah(ayah.surah, ayah.ayah) }, onLongClick = { onLongPressAyah(ayah.surah, ayah.ayah) })
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Text(
            lineText,
            color = Color(0xFF183D33),
            fontSize = fontSize,
            lineHeight = (fontSize.value * 1.45f).sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Right,
        )
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(text = { Text("تعيين بداية الحفظ من هنا") }, onClick = { menuExpanded = false; onSetStart(ayah.surah, ayah.ayah, 0, false) })
            if (ayah.ayah == 1 && com.example.quranoffline.data.QuranPresentation.openingBasmalaForSurah(ayah.surah) != null) DropdownMenuItem(text = { Text("تعيين البداية مع البسملة المستقلة") }, onClick = { menuExpanded = false; onSetStart(ayah.surah, ayah.ayah, 0, true) })
            DropdownMenuItem(text = { Text("التسميع والحفظ من هنا") }, onClick = { menuExpanded = false; onMemorizeFromHere(ayah.surah, ayah.ayah) })
            DropdownMenuItem(text = { Text("قراءة الآية بصوت الشيخ") }, onClick = { menuExpanded = false; onPlayAyah(ayah.surah, ayah.ayah) })
            DropdownMenuItem(text = { Text("قراءة السورة من هنا") }, onClick = { menuExpanded = false; onReadSurahFromHere(ayah.surah, ayah.ayah) })
            DropdownMenuItem(text = { Text("قراءة القرآن من هنا") }, onClick = { menuExpanded = false; onReadFromHere(ayah.surah, ayah.ayah) })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun PageAyahLine(
    ayah: QuranAyah,
    active: Boolean,
    selected: Boolean,
    onSetStart: (Int, Int, Int, Boolean) -> Unit,
    onMemorizeFromHere: (Int, Int) -> Unit,
    onPlayAyah: (Int, Int) -> Unit,
    onPlayBasmala: (Int) -> Unit,
    onReadFromHere: (Int, Int) -> Unit,
    onReadSurahFromHere: (Int, Int) -> Unit,
    onTapAyah: (Int, Int) -> Unit,
    onLongPressAyah: (Int, Int) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val openingBasmala = if (ayah.ayah == 1) com.example.quranoffline.data.QuranPresentation.openingBasmalaForSurah(ayah.surah) else null
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        openingBasmala?.let { basmala ->
            Surface(color = Color(0xFFFFF7E8), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp)) {
                    IconButton(onClick = { onPlayBasmala(ayah.surah) }) { Icon(Icons.Default.PlayArrow, contentDescription = "تشغيل البسملة الافتتاحية", tint = Color(0xFF76552A)) }
                    Text("${basmala.text}  ·  جملة مستقلة لا تدخل في الآية", modifier = Modifier.weight(1f).padding(vertical = 8.dp), textAlign = TextAlign.Right, color = Color(0xFF76552A), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Column(
            Modifier.fillMaxWidth()
                .background(
                    when {
                        selected -> Color(0xFFFFE0A6)
                        active -> Color(0xFFEAF4EE)
                        else -> Color.Transparent
                    },
                    RoundedCornerShape(12.dp),
                )
                .combinedClickable(onClick = { onTapAyah(ayah.surah, ayah.ayah) }, onLongClick = { onLongPressAyah(ayah.surah, ayah.ayah) })
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("﴿${ayah.ayah}﴾", color = Color(0xFFB6844A), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "خيارات الآية", tint = Color(0xFF76552A)) }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ayah.words.forEach { word ->
                    Text(
                        word.text,
                        color = Color(0xFF183D33),
                        style = MaterialTheme.typography.headlineSmall,
                        lineHeight = 36.sp,
                        softWrap = false,
                    )
                }
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(text = { Text("تعيين بداية الحفظ من هنا") }, onClick = { menuExpanded = false; onSetStart(ayah.surah, ayah.ayah, 0, false) })
                if (openingBasmala != null) DropdownMenuItem(text = { Text("تعيين البداية مع البسملة المستقلة") }, onClick = { menuExpanded = false; onSetStart(ayah.surah, ayah.ayah, 0, true) })
                DropdownMenuItem(text = { Text("التسميع والحفظ من هنا") }, onClick = { menuExpanded = false; onMemorizeFromHere(ayah.surah, ayah.ayah) })
                DropdownMenuItem(text = { Text("قراءة الآية بصوت الشيخ") }, onClick = { menuExpanded = false; onPlayAyah(ayah.surah, ayah.ayah) })
                DropdownMenuItem(text = { Text("قراءة السورة من هنا") }, onClick = { menuExpanded = false; onReadSurahFromHere(ayah.surah, ayah.ayah) })
                DropdownMenuItem(text = { Text("قراءة القرآن من هنا") }, onClick = { menuExpanded = false; onReadFromHere(ayah.surah, ayah.ayah) })
            }
        }
    }
}


@Composable
private fun SettingsScreen(
    state: com.example.quranoffline.data.QuranUiState,
    onModeChange: (AssetMode) -> Unit,
    onDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    onSelectEngine: (RecitationEngine) -> Unit,
    onDownloadModel: () -> Unit,
    onUpdateCorrectionSettings: ((CorrectionSettings) -> CorrectionSettings) -> Unit,
    onResetCorrectionSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().background(Color(0xFFF7F8F4)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("الإعدادات", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF123F35))
            Text("تُضبط مرة واحدة وتبقى بعيدة عن واجهة الحفظ اليومية.", color = Color(0xFF66736C))
        }
        item { CorrectionSettingsCard(state.correctionSettings, onUpdateCorrectionSettings, onResetCorrectionSettings) }
        item { EngineCard(state, onSelectEngine, onDownloadModel) }
        item { OnlineAssetCard(state, onModeChange, onDownload, onDeleteDownload) }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("قواعد البيانات في هذه النسخة", fontWeight = FontWeight.Bold)
                    Text("البسملة الافتتاحية عنصر مستقل لا يُحسب ضمن كلمات الآية، والحروف المقطعة تُطابق كلمة مرجعية واحدة مع وحدات نطق مستقلة.", color = Color(0xFF66736C))
                }
            }
        }
        if (state.notice.isNotBlank()) item { Text(state.notice, color = Color(0xFF36584B)) }
    }
}

private fun readerScopeLabel(scope: com.example.quranoffline.data.ReaderScope): String = when (scope) {
    com.example.quranoffline.data.ReaderScope.AYAH -> "آية"
    com.example.quranoffline.data.ReaderScope.SURAH -> "سورة"
    com.example.quranoffline.data.ReaderScope.JUZ -> "جزء"
    com.example.quranoffline.data.ReaderScope.PAGES -> "صفحات"
    com.example.quranoffline.data.ReaderScope.QURAN -> "المصحف"
}
