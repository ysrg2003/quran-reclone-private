package com.example.quranoffline

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.quranoffline.BuildConfig
import com.example.quranoffline.data.AssetMode
import com.example.quranoffline.data.QuranAyah
import com.example.quranoffline.data.QuranWord
import com.example.quranoffline.data.selectionKey
import com.example.quranoffline.data.QuranAyahMetadata
import com.example.quranoffline.data.QuranViewModel
import com.example.quranoffline.data.CorrectionPhase
import com.example.quranoffline.data.CorrectionSettings
import com.example.quranoffline.data.ResumePosition
import com.example.quranoffline.data.RecitationInputMode
import com.example.quranoffline.data.WordStatus
import com.example.quranoffline.data.displayPause
import com.example.quranoffline.data.QuranTextPresentation
import com.example.quranoffline.data.WordReferenceLine
import java.text.DateFormat
import java.util.Date

internal val WordReferenceFontFamily = FontFamily(Font(R.font.kfgqpc_hafs_uthmanic_script))

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
        window.decorView.layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
        setContent {
            val appearanceSpec = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(AppearanceStore.load(applicationContext)) }
            val appearance = androidx.compose.runtime.remember(appearanceSpec.value) { appearanceSpec.value.resolve() }
            val editAppearance = androidx.compose.runtime.remember<(AppearanceSpec) -> Unit> { { n -> appearanceSpec.value = n; AppearanceStore.save(applicationContext, n) } }
            androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalAppearance provides appearance, LocalAppearanceSpec provides appearanceSpec.value, LocalAppearanceEdit provides editAppearance) {
            QuranOfflineTheme {
                QuranOfflineApp(
                    viewModel = quranViewModel,
                    onRequestMicrophone = {
                        microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onExportBackup = { createBackup.launch("quran-online-backup.json") },
                    onExportReport = { fmt ->
                        quranViewModel.exportReport(fmt) { file, mime ->
                            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.exports", file)
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType(mime).putExtra(android.content.Intent.EXTRA_STREAM, uri).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            startActivity(android.content.Intent.createChooser(send, null))
                        }
                    },
                )
            }
            }
        }
    }
}

@Composable
private fun QuranOfflineTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LocalAppearance.current.colorScheme(),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuranOfflineApp(
    viewModel: QuranViewModel,
    onRequestMicrophone: () -> Unit,
    onExportBackup: () -> Unit,
    onExportReport: (String) -> Unit = {},
) {
    val state = viewModel.uiState.value
    val context = LocalContext.current
    val startRecitation = {
        if (state.inputMode == RecitationInputMode.TYPING) {
            viewModel.startTypedRecitation()
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            // A selected block decides the start (first selected ayah) and the end (last selected ayah), in every tab.
            val sel = state.selectedAyahIndices
            if (sel.isNotEmpty() && !state.listening) state.allAyahs.getOrNull(sel.first())?.let { viewModel.prepareTasmeeForSelection(it.surah, it.ayah) }
            viewModel.startListening()
        } else {
            onRequestMicrophone()
        }
    }
    val resumeRecitation = startRecitation
    // Opens on the first tab of the user's order (Juz by default); the reader tab (4) opens a full-screen reader, so it is skipped.
    val firstTab = LocalAppearanceSpec.current.tabOrder.firstOrNull { it != 4 } ?: 0
    var tab by remember { mutableIntStateOf(firstTab) }
    var immersive by rememberSaveable { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    // The reading tab is the full-screen page reader; leaving it returns to the memorization tab.
    LaunchedEffect(tab) { if (tab == 4) immersive = true }
    var showSettings by remember { mutableStateOf(false) }
    var showGoTo by remember { mutableStateOf(false) }
    var showLegalNotices by rememberSaveable { mutableStateOf(false) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(Modifier.fillMaxSize()) {
        if (!immersive) Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("المصحف", fontWeight = FontWeight.Bold, color = Color.White)
                            Text("حفظ وتسميع القرآن", style = MaterialTheme.typography.labelSmall, color = Color(0xFFD6EEE5))
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Default.Search, contentDescription = "بحث في القرآن", tint = Color.White)
                        }
                        IconButton(onClick = { showGoTo = true }) {
                            Icon(Icons.Default.Explore, contentDescription = "الانتقال السريع", tint = Color.White)
                        }
                        IconButton(onClick = { immersive = true }) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "قراءة بملء الشاشة", tint = Color.White)
                        }
                        IconButton(onClick = { tab = 4; immersive = true }) {
                            Icon(Icons.Default.MenuBook, contentDescription = "القراءة والاستماع", tint = Color.White)
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "الإعدادات", tint = Color.White)
                        }
                        Surface(
                            color = LocalAppearance.current.chip,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.padding(end = 12.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Icon(Icons.Default.PlayArrow, null, tint = Color.White)
                                Spacer(Modifier.width(5.dp))
                                Text("SpeechRecognizer", style = MaterialTheme.typography.labelMedium, color = Color.White)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = LocalAppearance.current.primary,
                        titleContentColor = Color.White,
                        actionIconContentColor = Color.White,
                    ),
                )
                val tabOrder = LocalAppearanceSpec.current.tabOrder
                ScrollableTabRow(
                    selectedTabIndex = tabOrder.indexOf(tab).coerceAtLeast(0),
                    containerColor = LocalAppearance.current.primary,
                    contentColor = Color.White,
                    edgePadding = 8.dp,
                    indicator = { positions ->
                        val shown = tabOrder.indexOf(tab).coerceAtLeast(0)
                        if (shown < positions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(positions[shown]),
                                height = 3.dp,
                                color = Color(0xFFF2C879),
                            )
                        }
                    },
                ) {
                    tabOrder.forEach { index ->
                        val title = AppearanceSpec.TAB_TITLES[index].orEmpty()
                        Tab(
                            selected = tab == index,
                            onClick = { tab = index },
                            text = { Text(title, fontSize = 14.sp, fontWeight = if (tab == index) FontWeight.Bold else FontWeight.Normal) },
                            selectedContentColor = Color.White,
                            unselectedContentColor = LocalAppearance.current.tabUnselected,
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
                    if (state.listening) viewModel.stopListening() else startRecitation()
                },
                onInputModeChange = viewModel::setRecitationInputMode,
                onTypedInputChange = viewModel::updateTypedInput,
                onSubmitTyped = viewModel::submitTypedRecitation,
                onSave = viewModel::saveCurrentSession,
                onSetGoal = viewModel::setDailyGoal,
                onResume = resumeRecitation,
                onSetStart = viewModel::setMemorizationStart,
                onPlayAyah = viewModel::playAyah,
                onPlayBasmala = viewModel::playOpeningBasmala,
                onReadFromHere = viewModel::playFromHere,
                onMemorizeFromHere = viewModel::startMemorizationFrom,
                onStartAfterOpening = viewModel::startMemorizationAfterOpening,
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
                onSelectSurah = { surah, ayah ->
                    viewModel.openSurahAt(surah, ayah)
                    tab = 4
                },
                modifier = Modifier.padding(padding),
            )
            3 -> HistoryScreen(
                state = state,
                onClear = viewModel::clearSessions,
                onClearErrors = viewModel::clearCorrectedErrors,
                onExport = onExportBackup,
                onExportReport = onExportReport,
                onOpenSession = { id -> viewModel.openSession(id); tab = 4 },
                onOpenMark = { id -> viewModel.openSaveMark(id); tab = 4 },
                onErrorLogToggle = viewModel::setErrorLogEnabled,
                modifier = Modifier.padding(padding),
            )
            4 -> ReaderScreen(
                state = state,
                onToggleHidden = viewModel::toggleHidden,
                onStart = {
                    if (state.listening) viewModel.stopListening() else startRecitation()
                },
                onInputModeChange = viewModel::setRecitationInputMode,
                onTypedInputChange = viewModel::updateTypedInput,
                onSubmitTyped = viewModel::submitTypedRecitation,
                onSave = viewModel::saveCurrentSession,
                onSetStart = viewModel::setMemorizationStart,
                onMemorizeFromHere = viewModel::startMemorizationFrom,
                onStartAfterOpening = viewModel::startMemorizationAfterOpening,
                onCorrection = viewModel::playCorrectionForCurrentWord,
                onStopCorrection = viewModel::stopCorrection,
                onPreviousAyah = viewModel::startFromPreviousAyah,
                onResume = resumeRecitation,
                onPlayAyah = viewModel::playAyah,
                onPlayBasmala = viewModel::playOpeningBasmala,
                onReadFromHere = viewModel::playFromHere,
                onReadSurahFromHere = viewModel::playSurahFromHere,
                onPlaySelected = viewModel::playSelectedAyahs,
                onPlayScopeFromHere = viewModel::playReaderScopeFromHere,
                onBookmark = viewModel::saveBookmark,
                onClearSelection = viewModel::clearAyahSelection,
                onTapAyah = viewModel::tapAyahSelection,
                onLongPressAyah = viewModel::beginAyahSelection,
                onStop = viewModel::stopReader,
                onScope = viewModel::playReaderScope,
                onPageChanged = viewModel::goToPage,
                onPreviousPage = viewModel::previousPage,
                onNextPage = viewModel::nextPage,
                modifier = Modifier.padding(padding),
            )
            6 -> KhatmaScreen(
                state = state,
                onTarget = viewModel::setKhatmaTarget,
                onReminder = viewModel::setKhatmaReminder,
                onTargetUnit = viewModel::setKhatmaTargetUnit,
                onRepeat = viewModel::setKhatmaRepeat,
                onOpen = { kind -> viewModel.openKhatmaPosition(kind); tab = 4 },
                modifier = Modifier.padding(padding),
            )
            }
            if (showLegalNotices) {
                LegalNoticesDialog(onDismiss = { showLegalNotices = false })
            }
        }
        }
        if (tasmeeRangeRequest.value) {
            val anchor = viewModel.tasmeeAnchor() ?: (1 to 1)
            TasmeeRangeSheet(
                state = state, surah = anchor.first, ayah = anchor.second, onDismiss = { tasmeeRangeRequest.value = false },
                onScope = { sc -> viewModel.prepareTasmeeRange(sc, anchor.first, anchor.second) },
                onCustom = { s1, a1, s2, a2 -> viewModel.prepareTasmeeCustom(s1, a1, s2, a2) },
                onNumberRange = { byJuz, a, b -> viewModel.prepareTasmeeByRange(byJuz, a, b) },
            )
        }
        if (immersive) ImmersiveReader(state = state, viewModel = viewModel, onStartListening = startRecitation, onExit = { immersive = false; if (tab == 4) tab = 0 })
        if (showSettings) SettingsLayer(
            state = state, onClose = { showSettings = false },
            onModeChange = viewModel::setMode, onDownload = viewModel::downloadCurrentSurah, onDeleteDownload = viewModel::deleteCurrentSurah,
            onUpdateCorrectionSettings = viewModel::updateCorrectionSettings, onResetCorrectionSettings = viewModel::resetCorrectionSettings,
            onShowLegalNotices = { showLegalNotices = true },
        )
        if (showSearch) QuranSearchOverlay(
            state = state, viewModel = viewModel, onClose = { showSearch = false },
            onOpenAyah = { surah, ayah -> viewModel.pageOfAyah(surah, ayah)?.let { viewModel.jumpToPage(it) }; showSearch = false; tab = 4 },
        )
        if (showGoTo) GoToDialog(state = state, viewModel = viewModel, onDismiss = { showGoTo = false }, onGo = { page -> viewModel.jumpToPage(page); showGoTo = false; tab = 4 })
        }
    }
}

@Composable
private fun LegalNoticesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val legalFiles = remember {
        listOf(
            "NOTICE.md",
            "LEGAL_LICENSE_RESEARCH_2026-08-27.md",
            "LEGAL_INDEPENDENT_SEARCH_ADDENDUM_2026-08-27.md",
            "LEGAL_INDEPENDENT_SEARCH_NOTES_2026-08-27.md",
            "LEGAL_GENERAL_PRINCIPLES_2026-08-27.md",
            "README.md",
            "MIT-quran-align.txt",
        )
    }
    val notices = remember(context, legalFiles) {
        buildString {
            legalFiles.forEach { filename ->
                append("\n\n========== ").append(filename).append(" ==========\n\n")
                runCatching {
                    context.assets.open("third_party_licenses/$filename").bufferedReader().use { it.readText() }
                }.onSuccess { append(it) }
                    .onFailure { append("تعذر قراءة هذا الملف من داخل التطبيق: ${it.message ?: "خطأ غير معروف"}") }
            }
        }.trim()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إشعارات المصادر وحقوق الاستخدام", textAlign = TextAlign.Right) },
        text = {
            Text(
                notices,
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                textAlign = TextAlign.Right,
                color = Color(0xFF45404D),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
    )
}

@Composable
private fun LoadingScreen(notice: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(Color(0xFFF7FAF8)),
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
                CircularProgressIndicator(color = LocalAppearance.current.primary)
                Text("جاري تجهيز ورد", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF17231F))
                Text(notice.ifBlank { "جاري تحميل النص والتوقيتات…" }, textAlign = TextAlign.Center, color = Color(0xFF52635B))
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
    var surahQuery by remember { mutableStateOf("") }
    val shownSuras = remember(metadata, surahQuery) {
        val all = metadata?.suras.orEmpty()
        val q = com.example.quranoffline.data.normalizeArabicForMatch(surahQuery).replace("سورة", "").trim()
        if (q.isEmpty()) all else all.filter { it.index.toString() == q || com.example.quranoffline.data.normalizeArabicForMatch(it.arabicName).replace("ال", "").contains(q.replace("ال", "")) || com.example.quranoffline.data.normalizeArabicForMatch(it.arabicName).contains(q) }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().background(Color(0xFFF7FAF8)).padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SectionHeader(
                title = "السور",
                subtitle = "اختر السورة لفتح موضع الحفظ والتسميع الحالي.",
            )
        }
        item {
            OutlinedTextField(
                value = surahQuery, onValueChange = { surahQuery = it }, singleLine = true,
                placeholder = { Text("ابحث باسم السورة أو رقمها") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        items(shownSuras, key = { it.index }) { surah ->
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
                    Text("${surah.index}", color = Color(0xFF52635B), style = MaterialTheme.typography.titleMedium)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                        Text("سورة ${surah.arabicName}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("${revelationLabel(surah.revelationType)} • ${surah.ayahCount} آية • صفحة ${metadata?.ayah(surah.index, 1)?.page ?: "—"}", color = Color(0xFF52635B), style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = { onSelectSurah(surah.index) },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = LocalAppearance.current.primary),
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
    onSelectSurah: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metadata = state.metadata
    LazyColumn(
        modifier = modifier.fillMaxSize().background(Color(0xFFF7FAF8)).padding(horizontal = 8.dp),
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
            var expanded by rememberSaveable(juz.index) { mutableStateOf(false) }
            val entries = remember(metadata, juz.index, expanded) {
                if (expanded && metadata != null) com.example.quranoffline.data.JuzSurahs.forJuz(metadata, juz.index) else emptyList()
            }
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("${juz.index}", color = Color(0xFF52635B), style = MaterialTheme.typography.titleMedium)
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                            Text("الجزء ${juz.index}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text("${surah?.arabicName ?: "السورة ${juz.surah}"} • الآية ${juz.ayah} • صفحة ${ayah?.page ?: "—"}", color = Color(0xFF52635B), style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = { onSelectSurah(juz.surah, juz.ayah) }) { Text("فتح") }
                        Text(if (expanded) "▲" else "▼", color = Color(0xFF52635B))
                    }
                    if (expanded) {
                        entries.forEach { e ->
                            val name = metadata?.sura(e.surah)?.arabicName ?: "السورة ${e.surah}"
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onSelectSurah(e.surah, e.startAyah) }
                                    .background(Color(0xFFF1F6F3)).padding(horizontal = 24.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text("${e.surah}", color = Color(0xFF52635B))
                                Text("سورة $name", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                if (e.partial) Text("جزء منها: ${e.startAyah}–${e.endAyah}", color = Color(0xFF9A3412), style = MaterialTheme.typography.bodySmall)
                            }
                        }
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
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, color = Color(0xFF17231F), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Color(0xFF52635B), style = MaterialTheme.typography.bodySmall)
    }
}

private const val SHOW_TODAY_GOAL_BOX = false

@Composable
private fun MemorizationScreen(
    state: com.example.quranoffline.data.QuranUiState,
    focusRecitation: Boolean,
    onToggleHidden: () -> Unit,
    onStart: () -> Unit,
    onInputModeChange: (RecitationInputMode) -> Unit,
    onTypedInputChange: (String) -> Unit,
    onSubmitTyped: () -> Unit,
    onSave: () -> Unit,
    onSetGoal: (Int) -> Unit,
    onResume: () -> Unit,
    onSetStart: (Int, Int, Int, Boolean) -> Unit,
    onPlayAyah: (Int, Int) -> Unit,
    onPlayBasmala: (Int) -> Unit,
    onReadFromHere: (Int, Int) -> Unit,
    onMemorizeFromHere: (Int, Int) -> Unit,
    onStartAfterOpening: (Int, Int, Int) -> Unit = { _, _, _ -> },
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
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // Items before the ayah list: recitation card (1), header, title, muqatta'at, metadata, navigation (5), recitation card if not focus,
    // optional current-word card, hide button (1).
    val showWordCard = state.hidden && state.listening && !state.currentSpokenWord.isNullOrBlank()
    val lead = (if (focusRecitation) 1 else 0) + 5 + (if (!focusRecitation) 1 else 0) + (if (showWordCard) 1 else 0) + 1
    LaunchedEffect(state.currentAyahIndex, state.currentPage, state.listening) {
        if (!state.listening) return@LaunchedEffect
        val cur = state.allAyahs.getOrNull(state.currentAyahIndex) ?: return@LaunchedEffect
        val i = state.ayahs.indexOfFirst { it.surah == cur.surah && it.ayah == cur.ayah }
        if (i >= 0) listState.animateScrollToItem(lead + i)
    }
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8F4))
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (focusRecitation) {
            item {
                RecitationCard(state, onStart, onInputModeChange, onTypedInputChange, onSubmitTyped, onResume, onCorrection, onStopCorrection, onPreviousAyah)
            }
        }
        item {
            Spacer(Modifier.height(10.dp))
            var surahMenuExpanded by remember { mutableStateOf(false) }
            val visibleSurahNumbers = state.ayahs.map { it.surah }.distinct()
            val visibleSurahLabel = visibleSurahLabel(state, visibleSurahNumbers)
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = LocalAppearance.current.deep),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("موضع الحفظ الحالي", color = Color(0xFFD6EEE5), style = MaterialTheme.typography.labelLarge)
                        Text(visibleSurahLabel, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("صفحة ${state.currentPage}/${state.totalPages} • آيات ${visibleAyahRangeLabel(state)}", color = Color(0xFFE4F4EE), style = MaterialTheme.typography.bodySmall)
                    }
                    Box {
                        Button(
                            onClick = { surahMenuExpanded = true },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFF2C879), contentColor = Color(0xFF183D33)),
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
        item { MuqattaatOpeningCard(state, onMemorizeFromHere, onStartAfterOpening) }
        item { MushafMetadataCard(state) }
        item { MushafPageNavigationCard(state, onPreviousPage, onNextPage) }
        if (!focusRecitation) {
            item { RecitationCard(state, onStart, onInputModeChange, onTypedInputChange, onSubmitTyped, onResume, onCorrection, onStopCorrection, onPreviousAyah) }
        }
        if (state.hidden && state.listening && !state.currentSpokenWord.isNullOrBlank()) {
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F3EE))) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("الكلمة الحالية", fontWeight = FontWeight.SemiBold, color = Color(0xFF183D33))
                        Text(state.currentSpokenWord.orEmpty(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFF0B5D4B), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right)
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
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = LocalAppearance.current.paper)) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    AyahBlock(
                        ayah = ayah,
                        hidden = state.hidden,
                        evaluation = state.evaluation,
                        openingBasmalaEvaluation = state.openingBasmalaEvaluation,
                        openingBasmalaWordIndex = state.openingBasmalaWordIndex,
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
        // Hidden in v21.1 (the Khatmat tab covers the daily wird). Code and saved goal data are kept; set to true to bring it back.
        if (SHOW_TODAY_GOAL_BOX) item { GoalCard(state.goal, onSetGoal) }
        if (state.notice.isNotBlank()) {
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F3EE))) {
                    Text(state.notice, color = Color(0xFF36584B), modifier = Modifier.fillMaxWidth().padding(14.dp))
                }
            }
        }
        if (state.openingBasmalaActive && (state.rawTranscript.isNotBlank() || state.openingBasmalaEvaluation != null)) {
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("تقييم البسملة الافتتاحية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        state.openingBasmalaEvaluation?.let { basmalaEvaluation ->
                            Text("مطابق: ${basmalaEvaluation.matched} من ${basmalaEvaluation.total} • غير مؤكد: ${basmalaEvaluation.uncertain} • يحتاج مراجعة: ${basmalaEvaluation.missing + basmalaEvaluation.unexpected}")
                        }
                        if (state.rawTranscript.isNotBlank()) {
                            Text("أول نص أعاده SpeechRecognizer بدون تعديل", fontWeight = FontWeight.SemiBold)
                            Text(state.rawTranscript, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
                        }
                    }
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
                        if (state.rawTranscript.isNotBlank()) {
                            Text("أول نص أعاده SpeechRecognizer بدون تعديل", fontWeight = FontWeight.SemiBold)
                            Text(state.rawTranscript, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
                            Text("هذا هو المرشح النصي الأول كما وصل للتطبيق؛ لا يحوّله التطبيق إلى الرسم القرآني. وقد يطبع المزود بعض المدود والألفات بالرسم الإملائي المعتاد، لأنه تفريغ نصي وليس تمثيلاً صوتياً حرفياً.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF66736C))
                        }
                        if (state.transcript.isNotBlank()) {
                            Text("العرض القرآني بعد الحكم", fontWeight = FontWeight.SemiBold)
                            Text(QuranTextPresentation.annotated(state.transcript), textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
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
                title = "الاستماع إلى البسملة الافتتاحية تلقائياً",
                description = "عند بداية سورة مؤهلة: ابدأ بالآية مباشرة أو قل البسملة ثم الآية؛ سيتعرف النظام على الخيارين دون احتساب البسملة آية.",
                checked = settings.listenForOpeningBasmala,
                enabled = true,
                onCheckedChange = { checked -> onUpdate { it.copy(listenForOpeningBasmala = checked) } },
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
                listOf(ResumePosition.AYAH_START, ResumePosition.FIRST_ERROR).forEach { position ->
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
    ResumePosition.WORD_AFTER_ERROR -> "من أول خطأ"
}

@Composable
private fun EngineCard() {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("محرك التسميع", fontWeight = FontWeight.Bold)
            Text("في Online وOffline يستخدم التطبيق Android SpeechRecognizer؛ لذلك يحتاج التسميع الصوتي إلى اتصال بالإنترنت ولا يُحفظ صوتك داخل التطبيق.", color = Color(0xFF36584B))
            Text("Online يضمّن قواعد البيانات والـpreview ويجلب صوت الحصري عند الحاجة. Offline يضمّن قواعد البيانات وأصوات الحصري كلها داخل التطبيق. وضع التسميع بالكتابة يعمل محلياً في الإصدارين.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF66736C))
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
            if (BuildConfig.OFFLINE_DISTRIBUTION) {
                Text("قواعد البيانات والتوقيتات وأصوات الحصري مضمّنة داخل هذا الإصدار. لا تحتاج قراءة الصوت إلى الشبكة، لكن التسميع عبر Android SpeechRecognizer يحتاج الإنترنت.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF66736C))
            } else {
                Text("هذا إصدار Online؛ النص والتوقيتات داخل التطبيق، والصوت يُبث من الإنترنت عند الحاجة ولا يحتوي محرك تسميع Offline.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF66736C))
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
                Text("${(goal.progress * 100).toInt()}%", color = Color(0xFF0B5D4B), fontWeight = FontWeight.Bold)
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
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F3EE))) {
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
internal fun AyahBlock(
    ayah: QuranAyah,
    hidden: Boolean,
    evaluation: com.example.quranoffline.data.RecitationEvaluation?,
    openingBasmalaEvaluation: com.example.quranoffline.data.RecitationEvaluation?,
    openingBasmalaWordIndex: Int,
    wordOffset: Int,
    listening: Boolean,
    currentSpokenWord: String?,
    currentSpokenWordIndex: Int,
    onSetStart: (Int, Int, Int, Boolean) -> Unit,
    onMemorizeFromHere: (Int, Int) -> Unit,
    onStartAfterOpening: (Int, Int, Int) -> Unit = { _, _, _ -> },
    onPlayAyah: (Int, Int) -> Unit,
    onPlayBasmala: (Int) -> Unit,
    onReadFromHere: (Int, Int) -> Unit,
    onPlainTap: (() -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val appCtx = androidx.compose.ui.platform.LocalContext.current
    val onlySpoken = remember(appCtx) { com.example.quranoffline.data.WordDisplayRules.onlySpoken(appCtx) }
    val openingBasmala = if (ayah.ayah == 1) com.example.quranoffline.data.QuranPresentation.openingBasmalaForSurah(ayah.surah) else null
    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onPlainTap?.invoke() }, onLongClick = { menuExpanded = true }),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("﴿${ayah.ayah}﴾", color = Color(0xFFB6844A), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            Text("اضغط مطولاً للخيارات", style = MaterialTheme.typography.labelSmall, color = Color(0xFF52635B))
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "خيارات الآية", tint = Color(0xFF76552A))
            }
        }
        openingBasmala?.let { basmala ->
            Surface(color = Color(0xFFFFF7E8), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        IconButton(onClick = { onPlayBasmala(ayah.surah) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "تشغيل البسملة الافتتاحية", tint = Color(0xFF76552A))
                        }
                        Text("جملة مستقلة لا تُحسب من كلمات الآية", modifier = Modifier.weight(1f).padding(vertical = 6.dp), textAlign = TextAlign.Right, color = Color(0xFF76552A), style = MaterialTheme.typography.bodyMedium)
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.Start),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        basmala.text.split(Regex("\\s+")).filter(String::isNotBlank).forEachIndexed { index, wordText ->
                            val wordEvaluation = openingBasmalaEvaluation?.words?.getOrNull(index)
                            val status = wordEvaluation?.status
                            val revealed = wordEvaluation?.observed != null
                            val isCurrent = listening && openingBasmalaWordIndex == index && currentSpokenWord != null
                            val background = Color(com.example.quranoffline.data.WordDisplayRules.background(status, isCurrent))
                            val mode = com.example.quranoffline.data.WordDisplayRules.shown(hidden, onlySpoken, status, revealed, isCurrent)
                            if (mode == com.example.quranoffline.data.WordDisplayRules.Shown.HIDDEN) return@forEachIndexed
                            val shownText = when (mode) {
                                com.example.quranoffline.data.WordDisplayRules.Shown.CORRECT_WORD -> wordText
                                com.example.quranoffline.data.WordDisplayRules.Shown.CURRENT_SPOKEN -> currentSpokenWord.orEmpty()
                                else -> "•"
                            }
                            Surface(color = background, shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    text = QuranTextPresentation.annotatedCached(shownText),
                                    color = if (hidden && !revealed && !isCurrent) Color(0xFF76552A) else Color(0xFF76552A),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        val wordFlow: @Composable () -> Unit = {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.Start),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ayah.words.forEachIndexed { index, word ->
                    val globalIndex = wordOffset + index
                    val wordEvaluation = evaluation?.words?.getOrNull(globalIndex)
                    val status = wordEvaluation?.status
                    val revealed = wordEvaluation?.observed != null
                    val isCurrent = listening && globalIndex == currentSpokenWordIndex && !currentSpokenWord.isNullOrBlank()
                    val background = Color(com.example.quranoffline.data.WordDisplayRules.background(status, isCurrent))
                    val mode = com.example.quranoffline.data.WordDisplayRules.shown(hidden, onlySpoken, status, revealed, isCurrent)
                    if (mode == com.example.quranoffline.data.WordDisplayRules.Shown.HIDDEN) return@forEachIndexed
                    val shownText = when (mode) {
                        com.example.quranoffline.data.WordDisplayRules.Shown.CORRECT_WORD -> word.text
                        com.example.quranoffline.data.WordDisplayRules.Shown.CURRENT_SPOKEN -> currentSpokenWord.orEmpty()
                        else -> "•"
                    }
                    Surface(color = background, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = QuranTextPresentation.annotatedCached(shownText),
                            color = if (status != null || isCurrent) Color(0xFF183D33) else LocalAppearance.current.readerText,
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
    onInputModeChange: (RecitationInputMode) -> Unit,
    onTypedInputChange: (String) -> Unit,
    onSubmitTyped: () -> Unit,
    onResume: () -> Unit,
    onCorrection: () -> Unit,
    onStopCorrection: () -> Unit,
    onPreviousAyah: () -> Unit,
) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = LocalAppearance.current.deep)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("حفظ وتسميع القرآن", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "ابدأ من موضعك الحالي، وستظهر الكلمات السابقة بألوانها أثناء المتابعة.",
                color = Color(0xFFD6EEE5),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                onClick = { tasmeeRangeRequest.value = true },
                enabled = !state.listening && !state.correctionPlaying,
                modifier = Modifier.fillMaxWidth(),
                colors = OnDeep.outlinedColors(),
                border = OnDeep.outlinedBorder(!state.listening && !state.correctionPlaying),
            ) { Text("اختيار نطاق التسميع (من ... إلى)") }
            Text("طريقة التسميع", color = Color(0xFFFFE0A3), fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = state.inputMode == RecitationInputMode.VOICE,
                    onClick = { onInputModeChange(RecitationInputMode.VOICE) },
                    label = { Text("بالصوت") },
                    colors = OnDeep.chipColors(), border = OnDeep.chipBorder(state.inputMode == RecitationInputMode.VOICE),
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = state.inputMode == RecitationInputMode.TYPING,
                    onClick = { onInputModeChange(RecitationInputMode.TYPING) },
                    label = { Text("بالكتابة") },
                    colors = OnDeep.chipColors(), border = OnDeep.chipBorder(state.inputMode == RecitationInputMode.TYPING),
                    modifier = Modifier.weight(1f),
                )
            }
            if (state.inputMode == RecitationInputMode.TYPING) {
                OutlinedTextField(
                    value = state.typedInput,
                    onValueChange = onTypedInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.correctionPlaying,
                    label = { Text("اكتب الكلمة أو الآية") },
                    placeholder = { Text("اكتب من موضع الآية الحالية…") },
                    singleLine = false,
                    minLines = 2,
                    maxLines = 5,
                    colors = OnDeep.fieldColors(),
                )
                Button(
                    onClick = onSubmitTyped,
                    enabled = state.typedInput.isNotBlank() && !state.correctionPlaying,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OnDeep.buttonColors(),
                ) { Text("إرسال النص للتحقق") }
                Text(
                    "يُستخدم نفس ترتيب الكلمات والحواجز والتصحيح؛ لا يمكن للنص المكتوب تخطي كلمة أو فتح آية لاحقة.",
                    color = Color(0xFFD6EEE5),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state.openingBasmalaActive) {
                Text(
                    "جلسة البسملة الافتتاحية مستقلة: تُسمع وتُصحح وحدها، ولا تُحسب آية ولا كلمة. بعد انتهائها تبدأ الآية الأولى مباشرة.",
                    color = Color(0xFFFFE0A3),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            state.checkpoint?.let { checkpoint ->
                Text("آخر موضع محفوظ: السورة ${checkpoint.surah}، الآية ${checkpoint.ayah}، الكلمة ${checkpoint.wordIndex + 1}", color = Color(0xFFFFE0A3), style = MaterialTheme.typography.bodySmall)
                if (!state.listening) {
                    OutlinedButton(
                        onClick = onResume,
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.78f)),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("استئناف من الموضع المحفوظ", color = Color.White)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("هدف اليوم", color = Color(0xFFD6EEE5), style = MaterialTheme.typography.labelMedium)
                    Text("${state.goal.completedAyahs} من ${state.goal.targetAyahs} آيات", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Text("${(state.goal.progress * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(
                progress = { state.goal.progress },
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFF2C879),
                trackColor = Color(0xFF397A69),
            )
            Text(
                when {
                    state.inputMode == RecitationInputMode.TYPING && state.listening -> "وضع الكتابة نشط؛ أرسل النص للتحقق بالتسلسل."
                    state.inputMode == RecitationInputMode.TYPING -> "اكتب النص ثم اضغط إرسال؛ لا يحتاج وضع الكتابة إلى ميكروفون."
                    state.listening -> "جارٍ التعرّف عبر الإنترنت؛ لا يُحفظ صوتك في التطبيق…"
                    else -> "ابدأ التلاوة؛ سيستخدم Android SpeechRecognizer عبر الإنترنت."
                },
                color = Color(0xFFD7EFE6),
            )
            Button(
                onClick = onStart,
                enabled = !state.correctionPlaying,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD6B277),
                    disabledContainerColor = Color(0xFF355A4D),
                    disabledContentColor = Color(0xFFC6DED5),
                ),
            ) {
                Icon(if (state.listening) Icons.Default.Pause else Icons.Default.Mic, null, tint = Color(0xFF183D33))
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        state.correctionPlaying -> "يعمل التصحيح الصوتي…"
                        state.listening -> "إيقاف الجلسة"
                        else -> "ابدأ التسميع"
                    },
                    color = if (state.correctionPlaying) Color(0xFFC6DED5) else Color(0xFF183D33),
                )
            }
            state.correctionMessage?.let { message ->
                Text(message, color = Color(0xFFFFE0A3), fontWeight = FontWeight.SemiBold)
            }
            when (state.correctionPhase) {
                CorrectionPhase.PLAYING_FIRST_ERROR -> Text("يُصحّح أول كلمة خاطئة…", color = Color(0xFFFFE0A3))
                CorrectionPhase.PLAYING_WHOLE_AYAH -> Text("تُقرأ الآية كاملة للتصحيح…", color = Color(0xFFFFE0A3))
                CorrectionPhase.WAITING_RESUME -> Text("ابدأ من أول الآية أو من أي كلمة داخلها.", color = Color(0xFFFFE0A3))
                CorrectionPhase.LISTENING -> Unit
            }
            if (state.listening && state.currentAyahIndex >= 0) {
                val currentAyah = state.allAyahs.getOrNull(state.currentAyahIndex)
                val ayahNumber = currentAyah?.ayah
                if (ayahNumber != null) {
                    Text("الآية المتوقعة: $ayahNumber • الكلمة التالية: ${state.currentWordInAyah + 1}", color = Color(0xFFD7EFE6))
                    if (state.currentAyahLocked) {
                        Text("الآية الحالية مقفلة: صحّح كل الكلمات الحمراء قبل الانتقال إلى الآية التالية.", color = Color(0xFFFFE0A3), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (state.listening && !state.correctionPlaying && state.currentAyahIndex > 0) {
                val previousAyah = state.allAyahs.getOrNull(state.currentAyahIndex - 1)
                val previousAyahNumber = previousAyah?.ayah
                if (previousAyahNumber != null) {
                    OutlinedButton(
                        onClick = onPreviousAyah,
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.78f)),
                    ) {
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
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.78f)),
                ) {
                    Icon(if (state.correctionPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.correctionPlaying) "إيقاف التصحيح الصوتي" else "صحّح لي بصوت القارئ", color = Color.White)
                }
            }
            if (state.loadingAssets) {
                Text("جاري تحميل النص والتوقيتات…", color = Color(0xFFFFE0A3))
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
    onExportReport: (String) -> Unit = {},
    onOpenSession: (String) -> Unit,
    onOpenMark: (String) -> Unit = {},
    onErrorLogToggle: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(Color(0xFFF7F8F4)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("سجل الجلسات", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("مخزن على هذا الجهاز فقط", color = Color(0xFF66736C))
            }
            OutlinedButton(onClick = onExport) { Text("نسخة احتياطية") }
            Spacer(Modifier.width(6.dp))
            var reportMenu by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { reportMenu = true }) { Text("تقرير") }
                DropdownMenu(expanded = reportMenu, onDismissRequest = { reportMenu = false }) {
                    listOf("xlsx" to "Excel (.xlsx)", "docx" to "Word (.docx)", "pdf" to "PDF (.pdf)").forEach { (f, t) ->
                        DropdownMenuItem(text = { Text(t) }, onClick = { reportMenu = false; onExportReport(f) })
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            if (state.sessions.isNotEmpty()) {
                IconButton(onClick = onClear) { Icon(Icons.Default.Refresh, contentDescription = "حذف السجل") }
            }
        }
        SaveMarksCard("علامات الحفظ والتسميع", com.example.quranoffline.data.SaveMarkLogic.forKind(state.saveMarks, com.example.quranoffline.data.SaveMarkKind.HIFZ), onOpenMark)
        Spacer(Modifier.height(8.dp))
        SaveMarksCard("علامات القراءة والاستماع", com.example.quranoffline.data.SaveMarkLogic.forKind(state.saveMarks, com.example.quranoffline.data.SaveMarkKind.READ), onOpenMark)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("تسجيل أخطاء التسميع", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Switch(checked = state.errorLogEnabled, onCheckedChange = onErrorLogToggle)
        }
        Spacer(Modifier.height(8.dp))
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
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(session.label, fontWeight = FontWeight.SemiBold)
                            Text("السورة ${session.surah} • الآية ${session.startAyah}–${session.endAyah} • الصفحة ${session.page.takeIf { it > 0 } ?: "—"}")
                            Text("مطابق ${session.matchedWords}/${session.totalWords} • غير مؤكد ${session.uncertainWords}")
                            Text("آخر فتح: ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(session.lastOpenedAt))}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF66736C))
                            Button(onClick = { onOpenSession(session.id) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("فتح واستئناف هذا المرجع")
                            }
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ReaderScreen(
    state: com.example.quranoffline.data.QuranUiState,
    onToggleHidden: () -> Unit,
    onStart: () -> Unit,
    onInputModeChange: (RecitationInputMode) -> Unit,
    onTypedInputChange: (String) -> Unit,
    onSubmitTyped: () -> Unit,
    onSave: () -> Unit,
    onSetStart: (Int, Int, Int, Boolean) -> Unit,
    onMemorizeFromHere: (Int, Int) -> Unit,
    onStartAfterOpening: (Int, Int, Int) -> Unit = { _, _, _ -> },
    onCorrection: () -> Unit,
    onStopCorrection: () -> Unit,
    onPreviousAyah: () -> Unit,
    onResume: () -> Unit,
    onPlayAyah: (Int, Int) -> Unit,
    onPlayBasmala: (Int) -> Unit,
    onReadFromHere: (Int, Int) -> Unit,
    onReadSurahFromHere: (Int, Int) -> Unit,
    onPlaySelected: () -> Unit,
    onPlayScopeFromHere: (com.example.quranoffline.data.ReaderScope, Int, Int) -> Unit,
    onBookmark: (Int, Int) -> Unit,
    onClearSelection: () -> Unit,
    onTapAyah: (Int, Int) -> Unit,
    onLongPressAyah: (Int, Int) -> Unit,
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
        val readerTextScale = state.correctionSettings.mushafTextScale
        var showReaderActions by rememberSaveable { mutableStateOf(false) }
        var actionSurah by rememberSaveable { mutableIntStateOf(0) }
        var actionAyah by rememberSaveable { mutableIntStateOf(0) }
        val sheetAyah = state.allAyahs.firstOrNull { it.surah == actionSurah && it.ayah == actionAyah }
            ?: state.ayahs.firstOrNull()

        LazyColumn(
        state = contentState,
        modifier = modifier.fillMaxSize().background(Color(0xFFF7F8F4)).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { MuqattaatOpeningCard(state, onMemorizeFromHere, onStartAfterOpening) }
        item {
            RecitationCard(state, onStart, onInputModeChange, onTypedInputChange, onSubmitTyped, onResume, onCorrection, onStopCorrection, onPreviousAyah)
        }
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onToggleHidden, modifier = Modifier.weight(1f)) {
                        Icon(if (state.hidden) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.hidden) "إظهار النص" else "إخفاء النص")
                    }
                    OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("حفظ الجلسة")
                    }
                }
            }
        }
        if (state.hidden && state.listening && !state.currentSpokenWord.isNullOrBlank()) {
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F3EE))) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("الكلمة الحالية", fontWeight = FontWeight.SemiBold, color = Color(0xFF183D33))
                        Text(state.currentSpokenWord.orEmpty(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFF0B5D4B), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right)
                    }
                }
            }
        }
        if (state.rawTranscript.isNotBlank() || state.transcript.isNotBlank()) {
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F3EE))) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (state.rawTranscript.isNotBlank()) {
                            Text("أول نص أعاده SpeechRecognizer بدون تعديل", fontWeight = FontWeight.SemiBold, color = Color(0xFF183D33))
                            Text(state.rawTranscript, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right, color = Color(0xFF183D33))
                            Text("هذا هو المرشح النصي الأول كما وصل للتطبيق؛ لا يحوّله التطبيق إلى الرسم القرآني. وقد يطبع المزود بعض المدود والألفات بالرسم الإملائي المعتاد، لأنه تفريغ نصي وليس تمثيلاً صوتياً حرفياً.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF66736C))
                        }
                        if (state.transcript.isNotBlank()) {
                            Text("العرض القرآني بعد الحكم", fontWeight = FontWeight.SemiBold, color = Color(0xFF183D33))
                            Text(QuranTextPresentation.annotated(state.transcript), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right)
                        }
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = LocalAppearance.current.deep)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("قارئ المصحف", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("سورة ${state.metadata?.sura(state.selectedSurah)?.arabicName ?: state.selectedSurah} • الصفحة ${state.currentPage} من ${state.totalPages}", color = Color(0xFFD6EEE5))
                    Text("السحب إلى الشمال يعرض الصفحة السابقة، والسحب إلى اليمين يعرض الصفحة التالية. ترتيب المصحف من الفاتحة إلى الناس من اليمين إلى اليسار.", color = Color(0xFFD6EEE5), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = onPreviousPage, enabled = state.currentPage > 1, modifier = Modifier.weight(1f), colors = OnDeep.outlinedColors(), border = OnDeep.outlinedBorder(state.currentPage > 1)) { Text("السابق") }
                        Button(onClick = onNextPage, enabled = state.currentPage < state.totalPages, modifier = Modifier.weight(1f), colors = OnDeep.buttonColors()) { Text("التالي") }
                    }
                    Text("النطاق الصوتي: ${readerScopeLabel(state.readerScope)}${state.readerCurrentKey?.let { " • الموضع $it" } ?: ""}", color = Color(0xFFFFE0A3), fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.example.quranoffline.data.ReaderScope.values().forEach { scope ->
                            FilterChip(selected = state.readerScope == scope, onClick = { onScope(scope) }, label = { Text(readerScopeLabel(scope)) }, colors = OnDeep.chipColors(), border = OnDeep.chipBorder(state.readerScope == scope))
                        }
                    }
                    ReciterPicker(dark = true)
                    if (state.readerPlaying) {
                        OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth(), colors = OnDeep.outlinedColors(), border = OnDeep.outlinedBorder()) {
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
                    Text("يعرض النص الحرفي المستخرج من ملف Word المرفق وبخط KFGQPC HAFS Uthmanic Script نفسه، مع إبقاء الرموز والضبط وترتيب الأسطر كما هي.", color = Color(0xFF66736C))
                    Text("لخيارات الآية: اضغط مطولاً على أي آية أو استخدم زر النقاط الثلاث.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF66736C))
                }
            }
        }
        item {
            MushafPageSurface(
                pageAyahs = state.ayahs,
                state = state,
                onSetStart = onSetStart,
                onMemorizeFromHere = onMemorizeFromHere,
                onPlayAyah = onPlayAyah,
                onPlayBasmala = onPlayBasmala,
                onReadFromHere = onReadFromHere,
                onReadSurahFromHere = onReadSurahFromHere,
                onPageChanged = onPageChanged,
                onBookmark = onBookmark,
                onOpenReaderActions = {
                    state.ayahs.firstOrNull()?.let { first ->
                        actionSurah = first.surah
                        actionAyah = first.ayah
                        showReaderActions = true
                    }
                },
                onOpenAyahActions = { surah, ayah -> actionSurah = surah; actionAyah = ayah; showReaderActions = true },
                textScale = readerTextScale,
                onTapAyah = onTapAyah,
                onLongPressAyah = { surah, ayah -> actionSurah = surah; actionAyah = ayah; showReaderActions = true },
            )
        }
        }
        if (showReaderActions && sheetAyah != null) {
            ReaderAyahActionsSheet(
                state = state, sheetAyah = sheetAyah, onDismiss = { showReaderActions = false },
                onPlayAyah = onPlayAyah, onPlayScopeFromHere = onPlayScopeFromHere, onBookmark = onBookmark, onStop = onStop,
                onSelect = onLongPressAyah, onSetStart = onSetStart, onMemorizeFromHere = onMemorizeFromHere,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ReaderAyahActionsSheet(
    state: com.example.quranoffline.data.QuranUiState,
    sheetAyah: QuranAyah,
    onDismiss: () -> Unit,
    onPlayAyah: (Int, Int) -> Unit,
    onPlayScopeFromHere: (com.example.quranoffline.data.ReaderScope, Int, Int) -> Unit,
    onBookmark: (Int, Int) -> Unit,
    onStop: () -> Unit,
    onSelect: (Int, Int) -> Unit,
    onSetStart: (Int, Int, Int, Boolean) -> Unit,
    onMemorizeFromHere: (Int, Int) -> Unit,
) {
    val sheetMetadata = state.metadata?.ayah(sheetAyah.surah, sheetAyah.ayah)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("الاستماع إلى", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "سورة ${state.metadata?.sura(sheetAyah.surah)?.arabicName ?: sheetAyah.surah} • الآية ${sheetAyah.ayah} • الصفحة ${sheetMetadata?.page ?: state.currentPage}",
                color = Color(0xFF66736C),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Right,
            )
            val actions = listOf(
                "الآية الحالية" to com.example.quranoffline.data.ReaderScope.AYAH,
                "الصفحة الحالية" to com.example.quranoffline.data.ReaderScope.PAGES,
                "السورة كاملة من هنا" to com.example.quranoffline.data.ReaderScope.SURAH,
                "الجزء الحالي" to com.example.quranoffline.data.ReaderScope.JUZ,
                "الحزب والربع الحالي" to com.example.quranoffline.data.ReaderScope.HIZB,
                "القرآن كاملاً من هنا" to com.example.quranoffline.data.ReaderScope.QURAN,
            )
            actions.forEach { (label, scope) ->
                ListItem(
                    headlineContent = { Text(label) },
                    supportingContent = { Text("يبدأ من ${sheetAyah.surah}:${sheetAyah.ayah} وبالترتيب المصحفي") },
                    modifier = Modifier.fillMaxWidth().combinedClickable(
                        onClick = {
                            onDismiss()
                            if (scope == com.example.quranoffline.data.ReaderScope.AYAH) onPlayAyah(sheetAyah.surah, sheetAyah.ayah)
                            else onPlayScopeFromHere(scope, sheetAyah.surah, sheetAyah.ayah)
                        },
                        onLongClick = {},
                    ),
                )
            }
            ListItem(
                headlineContent = { Text("حفظ هذا الموضع كمرجع") },
                supportingContent = { Text("سيظهر المرجع في السجل ويمكن فتحه واستئنافه لاحقاً") },
                modifier = Modifier.fillMaxWidth().combinedClickable(
                    onClick = {
                        onDismiss()
                        onBookmark(sheetAyah.surah, sheetAyah.ayah)
                    },
                    onLongClick = {},
                ),
            )
            ListItem(
                headlineContent = { Text("تحديد هذه الآية (لتشغيل عدة آيات)") },
                modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onDismiss(); onSelect(sheetAyah.surah, sheetAyah.ayah) }, onLongClick = {}),
            )
            ListItem(
                headlineContent = { Text("تعيين بداية الحفظ من هنا") },
                modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onDismiss(); onSetStart(sheetAyah.surah, sheetAyah.ayah, 0, false) }, onLongClick = {}),
            )
            ListItem(
                headlineContent = { Text("ابدأ التسميع من هنا") },
                modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onDismiss(); onMemorizeFromHere(sheetAyah.surah, sheetAyah.ayah) }, onLongClick = {}),
            )
            if (state.readerPlaying) {
                OutlinedButton(onClick = { onDismiss(); onStop() }, modifier = Modifier.fillMaxWidth()) {
                    Text("إيقاف الاستماع")
                }
            }
            Spacer(Modifier.height(12.dp))
        }
                }

}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun MushafPageSurface(
    pageAyahs: List<QuranAyah>,
    state: com.example.quranoffline.data.QuranUiState,
    onSetStart: (Int, Int, Int, Boolean) -> Unit,
    onMemorizeFromHere: (Int, Int) -> Unit,
    onStartAfterOpening: (Int, Int, Int) -> Unit = { _, _, _ -> },
    onPlayAyah: (Int, Int) -> Unit,
    onPlayBasmala: (Int) -> Unit,
    onReadFromHere: (Int, Int) -> Unit,
    onReadSurahFromHere: (Int, Int) -> Unit,
    onPageChanged: (Int) -> Unit,
    onBookmark: (Int, Int) -> Unit,
    onOpenReaderActions: () -> Unit,
    onOpenAyahActions: (Int, Int) -> Unit,
    textScale: Float,
    onTapAyah: (Int, Int) -> Unit,
    onLongPressAyah: (Int, Int) -> Unit,
    onPlainTap: (() -> Unit)? = null,
    onClearSelection: () -> Unit = {},
    fullBleed: Boolean = false,
    minHeightDp: Int = 0,
) {
    var horizontalDrag by remember { mutableFloatStateOf(0f) }
    // Build the expensive lookup structures once per page surface. Reusing
    // them across lines avoids O(lineCount * allAyahCount) recomposition work.
    val allAyahsByKey = remember(state.allAyahs) { com.example.quranoffline.data.AyahLookup.of(state.allAyahs).byStringKey }
    val pageAyahsByKey = remember(pageAyahs) {
        pageAyahs.associateBy { it.selectionKey() }
    }
    val ayahIndexByKey = remember(state.allAyahs) { com.example.quranoffline.data.AyahLookup.of(state.allAyahs).indexByStringKey }
    val wordOffsets = remember(state.allAyahs) { com.example.quranoffline.data.AyahLookup.of(state.allAyahs).wordOffsetsList }
    val selectedIndices = remember(state.selectedAyahIndices) { state.selectedAyahIndices.toSet() }
    val isOpeningPage = state.currentPage <= 2
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (fullBleed) Modifier else Modifier.heightIn(min = 760.dp))
            .pointerInput(state.currentPage, state.totalPages) {
                detectHorizontalDragGestures(
                    onDragStart = { horizontalDrag = 0f },
                    onHorizontalDrag = { _, dragAmount -> horizontalDrag += dragAmount },
                    onDragEnd = {
                        val targetPage = com.example.quranoffline.data.pageAfterHorizontalSwipe(
                            currentPage = state.currentPage,
                            totalPages = state.totalPages,
                            dragAmount = horizontalDrag,
                        )
                        if (targetPage != state.currentPage) onPageChanged(targetPage)
                        horizontalDrag = 0f
                    },
                    onDragCancel = { horizontalDrag = 0f },
                )
            }
            .combinedClickable(onClick = { if (onPlainTap != null) { if (state.selectedAyahIndices.isEmpty()) onPlainTap() } else onOpenReaderActions() }, onLongClick = {})
        ,
        shape = if (fullBleed) RoundedCornerShape(0.dp) else RoundedCornerShape(if (isOpeningPage) 24.dp else 22.dp),
        border = if (fullBleed) null else BorderStroke(1.dp, Color(0xFFD8C7A7)),
        colors = CardDefaults.cardColors(containerColor = LocalAppearance.current.paper),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (isOpeningPage && !fullBleed) Modifier.border(1.dp, Color(0xFFE4D4B8), RoundedCornerShape(18.dp)).padding(6.dp) else Modifier)
                .then(if (fullBleed) Modifier.padding(horizontal = (READER_EDGE_MARGIN_DP - 2).dp, vertical = READER_EDGE_MARGIN_DP.dp) else Modifier.padding(18.dp)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!fullBleed) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("صفحة ${state.currentPage}", color = Color(0xFF76552A), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                OutlinedButton(onClick = onOpenReaderActions) { Text("الاستماع") }
            }
            if (pageAyahs.isEmpty()) {
                Text("لا توجد آيات محملة لهذه الصفحة حالياً.", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            } else {
                val wordReferenceLines = state.wordReferenceLayout?.linesForPage(state.currentPage).orEmpty()
                if (wordReferenceLines.isEmpty()) {
                    Text(
                        "تعذر قراءة مرجع Word المحلي لهذه الصفحة.",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = Color(0xFF8A6A45),
                    )
                } else {
                    // One shared horizontal scroller for the whole page (was one per line:
                    // 15 scrollable nodes competing with the vertical list and page swipe).
                    // Fit the widest line to the screen width (portrait and landscape) so page swipes are never
                    // swallowed by a sideways scroller. Only when the user enlarges text above 1.0 does the page scroll sideways.
                    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val availablePx = constraints.maxWidth
                    val fit = remember(state.currentPage, state.wordReferenceLayout, availablePx) {
                        val probe = wordReferenceLines.filter { it.type == "ayah" }.sortedByDescending { it.text.length }.take(3)
                        val widest = probe.maxOfOrNull { l ->
                            measurer.measure(
                                androidx.compose.ui.text.AnnotatedString(l.text),
                                style = androidx.compose.ui.text.TextStyle(fontFamily = WordReferenceFontFamily, fontSize = 22.sp),
                                softWrap = false, maxLines = 1,
                            ).size.width
                        } ?: 0
                        if (widest <= 0 || availablePx <= 0 || availablePx == Constraints.Infinity) 1f else (availablePx.toFloat() / widest * 0.97f).coerceIn(0.4f, 2.2f)
                    }
                    val effectiveScale = fit * textScale
                    val sideways = textScale > 1.02f
                    // When the sideways scroll is already at its edge, a further drag past it changes the page.
                    val pageNow by rememberUpdatedState(state.currentPage)
                    val pagesNow by rememberUpdatedState(state.totalPages)
                    val changePage by rememberUpdatedState(onPageChanged)
                    val edgeSwipe = remember {
                        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                            var overshoot = 0f
                            override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                                if (source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) overshoot += available.x
                                return androidx.compose.ui.geometry.Offset.Zero
                            }
                            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                                val target = com.example.quranoffline.data.pageAfterHorizontalSwipe(pageNow, pagesNow, overshoot)
                                overshoot = 0f
                                if (target != pageNow) changePage(target)
                                return androidx.compose.ui.unit.Velocity.Zero
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().then(if (sideways) Modifier.nestedScroll(edgeSwipe).horizontalScroll(rememberScrollState()) else Modifier)) {
                    Column(
                        (if (sideways) Modifier.width(IntrinsicSize.Max) else Modifier.fillMaxWidth()).then(if (fullBleed && minHeightDp > 0) Modifier.heightIn(min = (minHeightDp - 2 * READER_EDGE_MARGIN_DP).coerceAtLeast(0).dp) else Modifier),
                        verticalArrangement = if (fullBleed) Arrangement.SpaceEvenly else Arrangement.spacedBy(8.dp),
                    ) {
                    val circleLine = if (selectedIndices.isEmpty()) -1 else wordReferenceLines.firstOrNull { l -> com.example.quranoffline.data.ayahSegments(l).any { sg -> ayahIndexByKey[sg.first]?.let(selectedIndices::contains) == true } }?.number ?: -1
                    wordReferenceLines.forEach { line ->
                        WordReferenceLineView(
                            centered = isOpeningPage,
                            line = line,
                            state = state,
                            pageAyahs = pageAyahs,
                            textScale = effectiveScale,
                            ayahsByKey = allAyahsByKey,
                            pageAyahsByKey = pageAyahsByKey,
                            ayahIndexByKey = ayahIndexByKey,
                            wordOffsets = wordOffsets,
                            selectedIndices = selectedIndices,
                            onPlayBasmala = onPlayBasmala,
                            onSetStart = onSetStart,
                            onMemorizeFromHere = onMemorizeFromHere,
                            onPlayAyah = onPlayAyah,
                            onReadFromHere = onReadFromHere,
                            onReadSurahFromHere = onReadSurahFromHere,
                            onBookmark = onBookmark,
                            onOpenAyahActions = onOpenAyahActions,
                            onTapAyah = onTapAyah,
                            onLongPressAyah = onLongPressAyah,
                            onPlainTap = onPlainTap,
                            showCircle = line.number == circleLine,
                            circleDp = state.correctionSettings.selectionCircleDp,
                            onClearSelection = onClearSelection,
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
    hasSelection: Boolean,
    onSetStart: (Int, Int, Int, Boolean) -> Unit,
    onMemorizeFromHere: (Int, Int) -> Unit,
    onStartAfterOpening: (Int, Int, Int) -> Unit = { _, _, _ -> },
    onPlayAyah: (Int, Int) -> Unit,
    onReadFromHere: (Int, Int) -> Unit,
    onReadSurahFromHere: (Int, Int) -> Unit,
    onBookmark: (Int, Int) -> Unit,
    onOpenAyahActions: (Int, Int) -> Unit,
    onTapAyah: (Int, Int) -> Unit,
    onLongPressAyah: (Int, Int) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val backgroundColor = when {
        selected -> LocalAppearance.current.selectedHighlight
        active -> LocalAppearance.current.playingHighlight.copy(alpha = if (LocalAppearance.current.darkPaper) 0.6f else 0.35f)
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
            .combinedClickable(onClick = { if (hasSelection) onTapAyah(ayah.surah, ayah.ayah) else onOpenAyahActions(ayah.surah, ayah.ayah) }, onLongClick = { onLongPressAyah(ayah.surah, ayah.ayah) })
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Text(
            QuranTextPresentation.annotated(lineText),
            color = LocalAppearance.current.readerText,
            fontSize = fontSize,
            lineHeight = (fontSize.value * 1.45f).sp,
            softWrap = true,
            textAlign = TextAlign.Right,
        )
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(text = { Text("تعيين بداية الحفظ من هنا") }, onClick = { menuExpanded = false; onSetStart(ayah.surah, ayah.ayah, 0, false) })
            DropdownMenuItem(text = { Text("حفظ هذا الموضع كمرجع") }, onClick = { menuExpanded = false; onBookmark(ayah.surah, ayah.ayah) })
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
    textScale: Float,
    active: Boolean,
    selected: Boolean,
    hasSelection: Boolean,
    onSetStart: (Int, Int, Int, Boolean) -> Unit,
    onMemorizeFromHere: (Int, Int) -> Unit,
    onStartAfterOpening: (Int, Int, Int) -> Unit = { _, _, _ -> },
    onPlayAyah: (Int, Int) -> Unit,
    onPlayBasmala: (Int) -> Unit,
    onReadFromHere: (Int, Int) -> Unit,
    onReadSurahFromHere: (Int, Int) -> Unit,
    onBookmark: (Int, Int) -> Unit,
    onOpenAyahActions: (Int, Int) -> Unit,
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
                        selected -> LocalAppearance.current.selectedHighlight
                        active -> LocalAppearance.current.playingHighlight.copy(alpha = if (LocalAppearance.current.darkPaper) 0.6f else 0.35f)
                        else -> Color.Transparent
                    },
                    RoundedCornerShape(12.dp),
                )
                .combinedClickable(onClick = { if (hasSelection) onTapAyah(ayah.surah, ayah.ayah) else onOpenAyahActions(ayah.surah, ayah.ayah) }, onLongClick = { onLongPressAyah(ayah.surah, ayah.ayah) })
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
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.Start),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ayah.words.forEach { word ->
                    Text(
                        QuranTextPresentation.annotated(word.text),
                        color = LocalAppearance.current.readerText,
                        style = MaterialTheme.typography.headlineSmall,
                        fontSize = (MaterialTheme.typography.headlineSmall.fontSize.value * textScale).sp,
                        lineHeight = (36f * textScale).sp,
                        softWrap = false,
                        textAlign = TextAlign.Right,
                    )
                }
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(text = { Text("تعيين بداية الحفظ من هنا") }, onClick = { menuExpanded = false; onSetStart(ayah.surah, ayah.ayah, 0, false) })
            DropdownMenuItem(text = { Text("حفظ هذا الموضع كمرجع") }, onClick = { menuExpanded = false; onBookmark(ayah.surah, ayah.ayah) })
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
private fun TypographySettingsCard(
    settings: CorrectionSettings,
    onUpdate: ((CorrectionSettings) -> CorrectionSettings) -> Unit,
) {
    val scale = settings.mushafTextScale
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("الخط والعرض", fontWeight = FontWeight.Bold, color = Color(0xFF123F35))
            Text("حجم النص محفوظ في الإعدادات ويطبّق على كتلة كل سطر كاملة، من دون إعادة ترتيب الآيات أو التفافها عند التكبير.", color = Color(0xFF66736C), textAlign = TextAlign.Right)
            Text("الحجم الحالي: ${(scale * 100).toInt()}%", fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onUpdate { it.copy(mushafTextScale = scale - 0.1f) } },
                    enabled = scale > CorrectionSettings.MIN_MUSHAF_TEXT_SCALE,
                    modifier = Modifier.weight(1f),
                ) { Text("تصغير") }
                Button(
                    onClick = { onUpdate { it.copy(mushafTextScale = CorrectionSettings.DEFAULT_MUSHAF_TEXT_SCALE) } },
                    enabled = scale != CorrectionSettings.DEFAULT_MUSHAF_TEXT_SCALE,
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = LocalAppearance.current.primary, contentColor = Color.White),
                ) { Text("الافتراضي") }
                OutlinedButton(
                    onClick = { onUpdate { it.copy(mushafTextScale = scale + 0.1f) } },
                    enabled = scale < CorrectionSettings.MAX_MUSHAF_TEXT_SCALE,
                    modifier = Modifier.weight(1f),
                ) { Text("تكبير") }
            }
            androidx.compose.material3.HorizontalDivider()
            val cd = settings.selectionCircleDp
            Text("حجم دائرة الإجراءات: $cd", fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onUpdate { it.copy(selectionCircleDp = cd - 2) } }, enabled = cd > CorrectionSettings.MIN_SELECTION_CIRCLE_DP, modifier = Modifier.weight(1f)) { Text("أصغر") }
                OutlinedButton(onClick = { onUpdate { it.copy(selectionCircleDp = CorrectionSettings.DEFAULT_SELECTION_CIRCLE_DP) } }, enabled = cd != CorrectionSettings.DEFAULT_SELECTION_CIRCLE_DP, modifier = Modifier.weight(1f)) { Text("الافتراضي") }
                OutlinedButton(onClick = { onUpdate { it.copy(selectionCircleDp = cd + 2) } }, enabled = cd < CorrectionSettings.MAX_SELECTION_CIRCLE_DP, modifier = Modifier.weight(1f)) { Text("أكبر") }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    state: com.example.quranoffline.data.QuranUiState,
    section: Int,
    onModeChange: (AssetMode) -> Unit,
    onDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    onUpdateCorrectionSettings: ((CorrectionSettings) -> CorrectionSettings) -> Unit,
    onResetCorrectionSettings: () -> Unit,
    onShowLegalNotices: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val heading = Color(0xFF123F35)
    LazyColumn(
        modifier = modifier.fillMaxSize().background(Color(0xFFF7F8F4)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (section) {
            0 -> {
                item { TypographySettingsCard(state.correctionSettings, onUpdateCorrectionSettings) }
                item { AppearanceSettingsCard() }
                item { TabOrderSettingsCard() }
            }
            1 -> {
                item { CorrectionSettingsCard(state.correctionSettings, onUpdateCorrectionSettings, onResetCorrectionSettings) }
                item { HiddenViewSettingsCard() }
            }
            2 -> item { QuranComLayoutSettingsCard() }
            3 -> {
                item { EngineCard() }
                item { RecognitionEngineSettingsCard() }
            }
            4 -> {
                item { AudioDownloadSettingsCard(state.metadata?.suras.orEmpty()) }
                item { WordAudioSettingsCard(state.metadata?.suras.orEmpty()) }
                item { BulkDownloadCard(state.metadata?.suras.orEmpty()) }
                item { OnlineAssetCard(state, onModeChange, onDownload, onDeleteDownload) }
            }
            else -> {
                item {
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("قواعد البيانات في هذه النسخة", fontWeight = FontWeight.Bold)
                            Text("البسملة الافتتاحية عنصر مستقل لا يُحسب ضمن كلمات الآية، والحروف المقطعة تُطابق كلمة مرجعية واحدة مع وحدات نطق مستقلة.", color = Color(0xFF66736C))
                        }
                    }
                }
                item {
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("الحقوق وإشعارات المصادر", fontWeight = FontWeight.Bold, color = heading)
                            Text("تتضمن النسخة إشعاراً بالمكونات المفتوحة المصدر وملاحظات الأصول التي تحتاج إذناً خاصاً قبل النشر العام أو التجاري.", color = Color(0xFF66736C), textAlign = TextAlign.Right)
                            OutlinedButton(onClick = onShowLegalNotices, modifier = Modifier.fillMaxWidth()) { Text("عرض إشعارات المصادر") }
                        }
                    }
                }
            }
        }
        if (state.notice.isNotBlank()) item { Text(state.notice, color = Color(0xFF36584B)) }
    }
}

internal val settingsSections = listOf(
    "المظهر والخط" to "حجم الخط وشكل عرض النص",
    "الحفظ والتسميع والتصحيح" to "قواعد التصحيح وعرض الإخفاء",
    "تخطيط المصحف" to "تخطيط quran.com أو تخطيط الكلمات",
    "المحرك والتعرف على الصوت" to "محرك التعرف ومحرك التسميع",
    "التنزيلات والصوت" to "أصوات الآيات والكلمات والتنزيل الكامل",
    "حول والحقوق" to "قواعد البيانات وإشعارات المصادر",
)

/** The settings layer: opened only from the gear icon; a list of sections, a tap opens that section. */
@Composable
private fun SettingsLayer(
    state: com.example.quranoffline.data.QuranUiState,
    onClose: () -> Unit,
    onModeChange: (AssetMode) -> Unit,
    onDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    onUpdateCorrectionSettings: ((CorrectionSettings) -> CorrectionSettings) -> Unit,
    onResetCorrectionSettings: () -> Unit,
    onShowLegalNotices: () -> Unit,
) {
    var section by remember { mutableStateOf<Int?>(null) }
    androidx.activity.compose.BackHandler { if (section != null) section = null else onClose() }
    Column(Modifier.fillMaxSize().background(Color(0xFFF7F8F4))) {
        Surface(color = LocalAppearance.current.primary) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (section != null) section = null else onClose() }) {
                    Icon(androidx.compose.material.icons.Icons.Default.ArrowBack, "رجوع", tint = Color.White)
                }
                Text(section?.let { settingsSections[it].first } ?: "الإعدادات", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        }
        val sec = section
        if (sec == null) {
            LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(settingsSections) { index, (title, subtitle) ->
                    Card(onClick = { section = index }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF123F35))
                            Text(subtitle, color = Color(0xFF66736C), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        } else {
            SettingsScreen(state, sec, onModeChange, onDownload, onDeleteDownload, onUpdateCorrectionSettings, onResetCorrectionSettings, onShowLegalNotices)
        }
    }
}

private fun readerScopeLabel(scope: com.example.quranoffline.data.ReaderScope): String = when (scope) {
    com.example.quranoffline.data.ReaderScope.AYAH -> "آية"
    com.example.quranoffline.data.ReaderScope.SURAH -> "سورة"
    com.example.quranoffline.data.ReaderScope.JUZ -> "جزء"
    com.example.quranoffline.data.ReaderScope.HIZB -> "حزب/ربع"
    com.example.quranoffline.data.ReaderScope.PAGES -> "صفحات"
    com.example.quranoffline.data.ReaderScope.QURAN -> "المصحف"
}


/** Notice about long madd plus, for surahs that open with disjoined letters, a way to start after them. */
@Composable
internal fun MuqattaatOpeningCard(
    state: com.example.quranoffline.data.QuranUiState,
    onFromBeginning: (Int, Int) -> Unit,
    onAfterOpening: (Int, Int, Int) -> Unit,
) {
    val surah = state.selectedSurah
    val after = remember(state.allAyahs, surah) {
        var result: Triple<Int, Int, Int>? = null
        var firstIsOpening = false
        loop@ for (ayah in state.allAyahs.filter { it.surah == surah }.sortedBy { it.ayah }) {
            for ((index, word) in ayah.words.withIndex()) {
                if (com.example.quranoffline.data.Muqattaat.pronunciationFor(word) != null) {
                    firstIsOpening = true
                } else {
                    if (firstIsOpening) result = Triple(surah, ayah.ayah, index)
                    break@loop
                }
            }
        }
        result
    }
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4DC))) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "تنبيه: قد لا يتعرف التطبيق على كلمة تُقرأ بمدّ طويل، خاصة الحروف المقطعة في أول السورة مثل «الم». يمكنك قراءتها مع نفسك دون أن تُحسب عليك.",
                color = Color(0xFF76552A),
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth(),
            )
            if (after != null) {
                Text("هذه السورة تبدأ بحروف مقطعة. اختر طريقة التسميع:", fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
                val (s, a, w) = after
                // Always the ayah's own mushaf number, followed by its first words, so the start is unambiguous.
                val first = state.allAyahs.firstOrNull { it.surah == s && it.ayah == a }?.words?.drop(w)?.take(3)?.joinToString(" ") { it.text }.orEmpty()
                val label = "التسميع من الآية $a" + (if (w > 0) "، الكلمة ${w + 1}" else "") + if (first.isNotBlank()) " «$first»" else ""
                Button(onClick = { onAfterOpening(s, a, w) }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                OutlinedButton(onClick = { onFromBeginning(s, 1) }, modifier = Modifier.fillMaxWidth()) { Text("من بداية السورة") }
            }
        }
    }
}


@Composable
private fun SaveMarksCard(title: String, marks: List<com.example.quranoffline.data.SaveMark>, onOpen: (String) -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            if (marks.isEmpty()) Text("لا توجد علامات بعد؛ تُحفظ تلقائياً.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF66736C))
            marks.forEach { m ->
                OutlinedButton(onClick = { onOpen(m.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text("السورة ${m.surah} • الآية ${m.ayah} • ص ${m.page} • ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(m.updatedAt))}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
