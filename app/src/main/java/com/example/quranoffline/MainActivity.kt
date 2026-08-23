package com.example.quranoffline

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.quranoffline.data.AssetMode
import com.example.quranoffline.data.QuranAyah
import com.example.quranoffline.data.QuranViewModel
import com.example.quranoffline.data.WordStatus
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
            primary = Color(0xFF246B57),
            secondary = Color(0xFFB6844A),
            background = Color(0xFFF7F8F4),
            surface = Color.White,
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
            TopAppBar(
                title = {
                    Column {
                        Text("وِرد", fontWeight = FontWeight.Bold)
                        Text("تسميع Online مع تنزيل اختياري", style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    Surface(
                        color = Color(0xFFE4F2EA),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Icon(Icons.Default.PlayArrow, null, tint = Color(0xFF246B57))
                            Spacer(Modifier.width(5.dp))
                            Text("Online / محلي", style = MaterialTheme.typography.labelMedium, color = Color(0xFF246B57))
                        }
                    }
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    label = { Text("التسميع") },
                    leadingIcon = { Icon(Icons.Default.Mic, null) },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    label = { Text("السجل") },
                    leadingIcon = { Icon(Icons.Default.History, null) },
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) { padding ->
        if (tab == 0) {
            MemorizationScreen(
                state = state,
                onToggleHidden = viewModel::toggleHidden,
                onStart = {
                    if (state.listening) viewModel.stopListening()
                    else if (state.assetsReady) onRequestMicrophone()
                    else viewModel.startListening()
                },
                onSave = viewModel::saveCurrentSession,
                onSetGoal = viewModel::setDailyGoal,
                onCorrection = viewModel::playCorrectionForCurrentWord,
                onStopCorrection = viewModel::stopCorrection,
                onSelectSurah = viewModel::selectSurah,
                onPreviousPage = viewModel::previousPage,
                onNextPage = viewModel::nextPage,
                onModeChange = viewModel::setMode,
                onDownload = viewModel::downloadCurrentSurah,
                onDeleteDownload = viewModel::deleteCurrentSurah,
                modifier = Modifier.padding(padding),
            )
        } else {
            HistoryScreen(
                state = state,
                onClear = viewModel::clearSessions,
                onExport = onExportBackup,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun MemorizationScreen(
    state: com.example.quranoffline.data.QuranUiState,
    onToggleHidden: () -> Unit,
    onStart: () -> Unit,
    onSave: () -> Unit,
    onSetGoal: (Int) -> Unit,
    onCorrection: () -> Unit,
    onStopCorrection: () -> Unit,
    onSelectSurah: (Int) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onModeChange: (AssetMode) -> Unit,
    onDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8F4))
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.height(10.dp))
            var surahMenuExpanded by remember { mutableStateOf(false) }
            Box {
                Button(onClick = { surahMenuExpanded = true }) {
                    Text("السورة ${state.selectedSurah}")
                }
                DropdownMenu(
                    expanded = surahMenuExpanded,
                    onDismissRequest = { surahMenuExpanded = false },
                ) {
                    (1..114).forEach { surah ->
                        DropdownMenuItem(
                            text = { Text("سورة $surah") },
                            onClick = {
                                surahMenuExpanded = false
                                onSelectSurah(surah)
                            },
                        )
                    }
                }
            }
            Text("آيات ${state.ayahs.firstOrNull()?.ayah ?: 0}–${state.ayahs.lastOrNull()?.ayah ?: 0} من ${state.totalAyahsInSurah}", color = Color(0xFF66736C))
        }
        item {
            OnlineAssetCard(state, onModeChange, onDownload, onDeleteDownload)
        }
        item {
            GoalCard(state.goal, onSetGoal)
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF4EE)),
                shape = RoundedCornerShape(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Lock, null, tint = Color(0xFF246B57))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("مصدر الأصول Online", fontWeight = FontWeight.SemiBold)
                        Text("النص والتوقيتات من GitHub؛ الصوت يُبث أو يُنزّل باختيارك.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("مصحف التدريب", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    var wordOffset = 0
                    state.ayahs.forEach { ayah ->
                        AyahBlock(ayah, state.hidden, state.evaluation, wordOffset)
                        wordOffset += ayah.words.size
                    }
                    OutlinedButton(onClick = onToggleHidden, modifier = Modifier.fillMaxWidth()) {
                        Icon(if (state.hidden) Icons.Default.Visibility else Icons.Default.VisibilityOff, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.hidden) "إظهار الآيات" else "إخفاء الآيات واختبرني")
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onPreviousPage, modifier = Modifier.weight(1f)) { Text("السابق") }
                        Button(onClick = onNextPage, modifier = Modifier.weight(1f)) { Text("التالي") }
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF183D33))) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("التسميع المحلي", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (state.listening) "جارٍ التعرف عبر الإنترنت؛ لا يُحفظ صوتك في التطبيق…"
                        else "ابدأ التلاوة؛ التعرف Online والتصحيح الصوتي يمكن بثه دون حفظ.",
                        color = Color(0xFFD7E9DF),
                    )
                    Button(
                        onClick = onStart,
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFD6B277)),
                    ) {
                        Icon(if (state.listening) Icons.Default.Pause else Icons.Default.Mic, null, tint = Color(0xFF183D33))
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.listening) "إيقاف الجلسة" else "ابدأ التسميع", color = Color(0xFF183D33))
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
        if (state.notice.isNotBlank()) {
            item { Text(state.notice, color = Color(0xFF53645B), modifier = Modifier.padding(horizontal = 4.dp)) }
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
private fun AyahBlock(
    ayah: QuranAyah,
    hidden: Boolean,
    evaluation: com.example.quranoffline.data.RecitationEvaluation?,
    wordOffset: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
        Text("﴿${ayah.ayah}﴾", color = Color(0xFFB6844A), style = MaterialTheme.typography.labelMedium)
        if (hidden) {
            Text(
                text = "•  •  •  •  •  •  •",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ayah.words.forEachIndexed { index, word ->
                    val status = evaluation?.words?.getOrNull(wordOffset + index)?.status
                    val background = when (status) {
                        WordStatus.MATCHED -> Color(0xFFDFF1E5)
                        WordStatus.EXTRA_OR_UNEXPECTED -> Color(0xFFFFDDD8)
                        WordStatus.UNCERTAIN -> Color(0xFFFFF0C9)
                        WordStatus.MISSING -> Color(0xFFF0E6E0)
                        null -> Color.Transparent
                    }
                    Surface(color = background, shape = RoundedCornerShape(8.dp)) {
                        Text(word.text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp))
                    }
                }
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
