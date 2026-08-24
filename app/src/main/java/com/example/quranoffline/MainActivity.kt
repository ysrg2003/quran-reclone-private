package com.example.quranoffline

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quranoffline.data.AssetMode
import com.example.quranoffline.data.QuranAyah
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
                    listOf("الحفظ والتسميع", "السور", "الأجزاء", "السجل").forEachIndexed { index, title ->
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
                onCorrection = viewModel::playCorrectionForCurrentWord,
                onStopCorrection = viewModel::stopCorrection,
                onPreviousAyah = viewModel::startFromPreviousAyah,
                onSelectSurah = viewModel::selectSurah,
                onPreviousPage = viewModel::previousPage,
                onNextPage = viewModel::nextPage,
                onModeChange = viewModel::setMode,
                onDownload = viewModel::downloadCurrentSurah,
                onDeleteDownload = viewModel::deleteCurrentSurah,
                onSelectEngine = viewModel::setEngine,
                onDownloadModel = viewModel::downloadOfflineModel,
                onUpdateCorrectionSettings = viewModel::updateCorrectionSettings,
                onResetCorrectionSettings = viewModel::resetCorrectionSettings,
                modifier = Modifier.padding(padding),
            )
            1 -> MushafIndexScreen(
                state = state,
                onSelectSurah = { surah ->
                    viewModel.selectSurah(surah)
                    tab = 0
                },
                modifier = Modifier.padding(padding),
            )
            2 -> JuzIndexScreen(
                state = state,
                onSelectSurah = { surah ->
                    viewModel.selectSurah(surah)
                    tab = 0
                },
                modifier = Modifier.padding(padding),
            )
            3 -> HistoryScreen(
                state = state,
                onClear = viewModel::clearSessions,
                onExport = onExportBackup,
                modifier = Modifier.padding(padding),
            )
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
    onCorrection: () -> Unit,
    onStopCorrection: () -> Unit,
    onPreviousAyah: () -> Unit,
    onSelectSurah: (Int) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onModeChange: (AssetMode) -> Unit,
    onDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    onSelectEngine: (RecitationEngine) -> Unit,
    onDownloadModel: () -> Unit,
    onUpdateCorrectionSettings: ((CorrectionSettings) -> CorrectionSettings) -> Unit,
    onResetCorrectionSettings: () -> Unit,
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
                RecitationCard(state, onStart, onCorrection, onStopCorrection, onPreviousAyah)
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
            item { RecitationCard(state, onStart, onCorrection, onStopCorrection, onPreviousAyah) }
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
                    )
                }
            }
        }
        item {
            Text("الإعدادات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF123F35))
        }
        item {
            CorrectionSettingsCard(
                settings = state.correctionSettings,
                onUpdate = onUpdateCorrectionSettings,
                onReset = onResetCorrectionSettings,
            )
        }
        item { EngineCard(state, onSelectEngine, onDownloadModel) }
        item { OnlineAssetCard(state, onModeChange, onDownload, onDeleteDownload) }
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
                            Text("الناتج المسموع", fontWeight = FontWeight.SemiBold)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AyahBlock(
    ayah: QuranAyah,
    hidden: Boolean,
    evaluation: com.example.quranoffline.data.RecitationEvaluation?,
    wordOffset: Int,
    listening: Boolean,
    currentSpokenWord: String?,
    currentSpokenWordIndex: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
        Text("﴿${ayah.ayah}﴾", color = Color(0xFFB6844A), style = MaterialTheme.typography.labelMedium)
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
    }
}

@Composable
private fun RecitationCard(
    state: com.example.quranoffline.data.QuranUiState,
    onStart: () -> Unit,
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
