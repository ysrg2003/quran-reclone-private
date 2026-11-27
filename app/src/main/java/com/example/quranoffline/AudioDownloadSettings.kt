package com.example.quranoffline

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.quranoffline.data.AudioDownloadManager
import com.example.quranoffline.data.AudioPackSizes
import com.example.quranoffline.data.QuranSuraMetadata

/** Runs [compute] off the main thread on a slow poll. Settings used to walk tens of thousands of files on the UI thread on every download tick. */
@Composable
private fun <T> pollBg(initial: T, everyMs: Long = 2500L, enabled: Boolean = true, compute: () -> T): T {
    var v by remember { mutableStateOf(initial) }
    val latest by rememberUpdatedState(compute)
    LaunchedEffect(enabled) {
        if (enabled) while (true) {
            v = withContext(kotlinx.coroutines.Dispatchers.IO) { latest() }
            kotlinx.coroutines.delay(everyMs)
        }
    }
    return v
}

private fun mb(b: Long) = "%.1f ميغابايت".format(b / 1048576.0)

/** Compact reciter dropdown, shared by Settings and the reader screen. Selection is remembered. */
@Composable
fun ReciterPicker(onChanged: () -> Unit = {}, dark: Boolean = false, pageAyahs: List<Pair<Int, Int>> = emptyList()) {
    val ctx = LocalContext.current
    var selected by remember { mutableStateOf(com.example.quranoffline.data.Reciters.run { ensureLoaded(ctx); currentId }) }
    var open by remember { mutableStateOf(false) }
    val mgrP = remember { AudioDownloadManager.get(ctx) }
    // Computed off the main thread, only while the menu is open: per reciter, downloaded ayahs / 6236 and whether this page is fully local.
    val stats by produceState(emptyMap<Int, Pair<Int, Boolean>>(), open, pageAyahs) {
        if (!open) return@produceState
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.example.quranoffline.data.Reciters.all.associate { r ->
                val st = mgrP.store(r.id)
                val pct = (st.countsBySurah().sum() * 100L / 6236).toInt()
                r.id to (pct to (pageAyahs.isNotEmpty() && pageAyahs.all { (su, ay) -> st.localAyah(su, ay) != null }))
            }
        }
    }
    Box {
        OutlinedButton(onClick = { open = true }, Modifier.fillMaxWidth()) {
            Text("القارئ: ${com.example.quranoffline.data.Reciters.byId(selected).label}", color = if (dark) Color.White else Color.Unspecified)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            com.example.quranoffline.data.Reciters.all.forEach { r ->
                DropdownMenuItem(text = {
                    val st = stats[r.id]
                    Text(if (st == null) r.label else r.label + " • منزّل ${st.first}%" + if (pageAyahs.isNotEmpty()) (if (st.second) " • الصفحة منزّلة" else " • الصفحة غير منزّلة") else "")
                }, onClick = {
                    com.example.quranoffline.data.Reciters.select(ctx, r.id); selected = r.id; open = false; onChanged()
                })
            }
        }
    }
}

/** Settings > Download settings. Optional per-reciter offline ayah audio; streaming remains the default. */
@Composable
fun AudioDownloadSettingsCard(suras: List<QuranSuraMetadata>) {
    val ctx = LocalContext.current
    val mgr = remember { AudioDownloadManager.get(ctx) }
    val jobs by mgr.jobs.collectAsState()
    val rev by mgr.revision.collectAsState()
    val meas by mgr.measureStatus.collectAsState()
    var wifi by remember { mutableStateOf(mgr.wifiOnly) }
    var expanded by remember { mutableStateOf(false) }
    var confirmAll by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(com.example.quranoffline.data.Reciters.run { ensureLoaded(ctx); currentId }) }
    val rec = com.example.quranoffline.data.Reciters.byId(picked)
    val usedAll = pollBg(0L) { mgr.usedBytesAll() }
    val usedRec = pollBg(0L) { mgr.usedBytes(picked) }
    val ayahCounts = pollBg(IntArray(115), 1500L, expanded) { mgr.store(picked).countsBySurah() }
    val total = remember(rev, picked) { mgr.totalSize(picked) }
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("إعدادات التنزيل", fontWeight = FontWeight.Bold, color = Color(0xFF123F35))
            Text("تنزيل صوت الآيات من Quran.com اختياري. بدون تنزيل يبقى الصوت يُبثّ من الإنترنت كما كان. الملفات تُحفظ في تخزين التطبيق الخاص فقط ولا تظهر لتطبيقات أخرى، وكل قارئ له مجلد منفصل، وتُحذف عند إلغاء التثبيت أو من هنا.", color = Color(0xFF66736C))
            ReciterPicker(onChanged = { }.let { { picked = com.example.quranoffline.data.Reciters.currentId } })
            Text("حجم صوت المصحف كاملاً لهذا القارئ: ${total?.let { mb(it) } ?: "غير مقاس بعد"}. المنزّل لهذا القارئ: ${mb(usedRec)}. المنزّل لكل القرّاء: ${mb(usedAll)}.")
            if (picked != com.example.quranoffline.data.Reciters.DEFAULT_ID && total == null) {
                val running = meas[picked]
                OutlinedButton(onClick = { mgr.measure(picked, suras.map { it.ayahCount }) }, Modifier.fillMaxWidth()) { Text("قياس الأحجام (في الخلفية)") }
                if (running != null) Text(running, color = Color(0xFF66736C))
            }
            Text("مكان التخزين: تخزين التطبيق الداخلي الخاص (غير قابل للتغيير).", color = Color(0xFF66736C))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("التنزيل عبر Wi-Fi فقط", Modifier.weight(1f))
                Switch(checked = wifi, onCheckedChange = { wifi = it; mgr.wifiOnly = it })
            }
            Text("الصوت: Quran.com / Quran Foundation، القارئ: ${rec.label}.", color = Color(0xFF66736C))
            Text("ملاحظة: تنزيل صوت الكلمات غير مفعّل (نحو 4.4 جيغابايت تقديريًا)؛ النص مدمج أصلًا في التطبيق، وصوت الكلمة يبقى عبر الإنترنت.", color = Color(0xFF66736C))
            OutlinedButton(onClick = { expanded = !expanded }, Modifier.fillMaxWidth()) { Text(if (expanded) "إخفاء قائمة السور" else "اختيار السور للتنزيل") }
            if (expanded) suras.forEach { s ->
                val job = jobs[mgr.key(picked, s.index)] ?: AudioDownloadManager.JobState()
                val have = ayahCounts[s.index]
                val complete = have == s.ayahCount
                val size = mgr.surahSize(picked, s.index)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${s.index}. ${s.arabicName}  (${size?.let { mb(it) } ?: "حجم غير مقاس"})", fontWeight = FontWeight.SemiBold)
                    Text(if (job.running) "$have/${s.ayahCount} آية، ${job.message}" else if (complete) "منزّلة بالكامل" else if (have > 0) "$have/${s.ayahCount} آية. ${job.message}" else job.message.ifBlank { "غير منزّلة" }, color = Color(0xFF66736C))
                    if (job.running && job.total > 0) LinearProgressIndicator(progress = { job.done.toFloat() / job.total }, Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (job.running) OutlinedButton(onClick = { mgr.pause(picked, s.index) }) { Text("إيقاف مؤقت") }
                        else if (!complete) OutlinedButton(onClick = { mgr.start(picked, s.index, s.ayahCount) }) { Text(if (have > 0) "متابعة" else "تنزيل") }
                        if (have > 0 && !job.running) OutlinedButton(onClick = { mgr.delete(picked, s.index) }) { Text("حذف") }
                        if (complete && !job.running) OutlinedButton(onClick = { mgr.delete(picked, s.index); mgr.start(picked, s.index, s.ayahCount) }) { Text("إعادة التنزيل") }
                    }
                }
            }
            if (usedRec > 0) OutlinedButton(onClick = { mgr.deleteReciter(picked) }, Modifier.fillMaxWidth()) { Text("حذف تنزيلات هذا القارئ") }
            if (usedAll > 0) OutlinedButton(onClick = { confirmAll = true }, Modifier.fillMaxWidth()) { Text("حذف كل الصوت المنزّل (كل القرّاء)") }
        }
    }
    if (confirmAll) AlertDialog(onDismissRequest = { confirmAll = false },
        title = { Text("حذف كل الصوت المنزّل؟") }, text = { Text("سيعود الصوت إلى البث من الإنترنت.") },
        confirmButton = { TextButton(onClick = { mgr.deleteAll(); confirmAll = false }) { Text("حذف") } },
        dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("إلغاء") } })
}

/** Settings > Recognition engine. System recognizer is the default; Whisper is an optional offline extra. */
@Composable
fun RecognitionEngineSettingsCard() {
    val ctx = LocalContext.current
    val mgr = remember { com.example.quranoffline.data.WhisperModelManager.get(ctx) }
    val st by mgr.state.collectAsState()
    val rev by mgr.revision.collectAsState()
    var choice by remember { mutableStateOf(mgr.choice) }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(st.running) { while (st.running) { kotlinx.coroutines.delay(700); tick++ } }
    val ready = remember(rev, st) { mgr.store.ready() }
    val have = remember(rev, st, tick) { mgr.store.usedBytes() }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) mgr.importUri(uri) }
    val total = com.example.quranoffline.data.WhisperModelStore.SIZE
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("محرك التعرف على التلاوة", fontWeight = FontWeight.Bold, color = Color(0xFF123F35))
            Text("تعرّف النظام (SpeechRecognizer): الافتراضي، يحتاج إلى الإنترنت أثناء التسميع. Whisper: اختياري، يعمل بدون إنترنت تمامًا بعد تنزيل النموذج، وهو الوحيد الذي يعمل دون اتصال.", color = Color(0xFF66736C))
            if (!mgr.arm64) Text("Whisper غير متاح على هذا الجهاز (يتطلب معالج arm64).", color = Color(0xFF9A3B3B))
            else {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("استخدام Whisper (دون إنترنت)", Modifier.weight(1f))
                    Switch(checked = choice == com.example.quranoffline.data.EngineChoice.WHISPER, enabled = ready, onCheckedChange = {
                        choice = if (it) com.example.quranoffline.data.EngineChoice.WHISPER else com.example.quranoffline.data.EngineChoice.SYSTEM; mgr.choice = choice })
                }
                Text(if (ready) "النموذج جاهز: ${mb(total)}، مُتحقَّق منه بـ sha256." else if (have > 0) "تم تنزيل ${mb(have)} من ${mb(total)}" else "النموذج غير منزّل (${mb(total)}).")
                if (st.message.isNotBlank()) Text(st.message, color = Color(0xFF66736C))
                if (st.running && !ready) LinearProgressIndicator(progress = { (have.toFloat() / total).coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (st.running) OutlinedButton(onClick = { mgr.pause() }) { Text("إيقاف مؤقت") }
                    else if (!ready) OutlinedButton(onClick = { mgr.start() }) { Text(if (have > 0) "متابعة" else "تنزيل النموذج") }
                    if (ready && !st.running) OutlinedButton(onClick = { mgr.delete(); choice = mgr.choice }) { Text("حذف النموذج") }
                    if (!ready && !st.running) OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("استيراد ملف النموذج") }
                }
                Text("إن لم يتوفر النموذج أو فشل Whisper يعود التطبيق تلقائيًا إلى تعرّف النظام مع تنبيه ظاهر. Whisper أبطأ على الهواتف الضعيفة، ولم تُقَس سرعته على هاتف حقيقي بعد.", color = Color(0xFF66736C))
            }
            Text("الترخيص: نموذج tarteel-ai/whisper-base-ar-quran بترخيص Apache-2.0 (مصدر بيانات تدريبه غير معلن)، ومحرك whisper.cpp بترخيص MIT. النموذج يُنزَّل من Hugging Face ولا يُضمَّن في التطبيق، ويُحفظ في تخزين التطبيق الخاص.", color = Color(0xFF66736C))
        }
    }
}

/** Per-surah word audio (optional). Word audio otherwise streams as in v11. */
@Composable
fun WordAudioSettingsCard(suras: List<QuranSuraMetadata>) {
    val ctx = LocalContext.current
    val mgr = remember { AudioDownloadManager.get(ctx) }
    val jobs by mgr.wordJobs.collectAsState()
    val rev by mgr.revision.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val used = pollBg(0L, 4000L) { mgr.wordStore.usedBytes() }
    val wordCounts = pollBg(IntArray(115), 2500L, expanded) { mgr.wordStore.countsBySurah() }
    val (total, exact) = remember(rev) { mgr.wordTotal() }
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("صوت الكلمات (اختياري)", fontWeight = FontWeight.Bold, color = Color(0xFF123F35))
            Text("ملفات صوت كل كلمة من Quran.com، تُحفظ في تخزين التطبيق الخاص فقط. بدون تنزيل يبقى صوت الكلمة عبر الإنترنت كما كان. لا يُستخدم الملف المحلي إلا إذا طابقت الكلمة وعددها بيانات Quran.com.", color = Color(0xFF66736C))
            Text("الحجم الكلي: ${mb(total)} ${if (exact) "(مقاس فعليًا)" else "(تقدير من عيّنة واحدة، ليس قياسًا فعليًا)"}. المنزّل الآن: ${mb(used)}.")
            OutlinedButton(onClick = { mgr.measureWords(suras.map { it.index }) }, Modifier.fillMaxWidth()) { Text("قياس الأحجام الفعلية في الخلفية (${mgr.wordMeasured()}/114)") }
            OutlinedButton(onClick = { expanded = !expanded }, Modifier.fillMaxWidth()) { Text(if (expanded) "إخفاء قائمة السور" else "اختيار السور لتنزيل صوت الكلمات") }
            if (expanded) suras.forEach { s ->
                val job = jobs[s.index] ?: AudioDownloadManager.JobState()
                val have = wordCounts[s.index]
                val size = mgr.wordSurahSize(s.index)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${s.index}. ${s.arabicName}  (${size?.let { mb(it) } ?: "حجم غير مقاس"})", fontWeight = FontWeight.SemiBold)
                    Text(if (job.running) "$have كلمة${if (job.total > 0) " من ${job.total}" else ""}، ${job.message}" else if (have > 0) "$have كلمة منزّلة. ${job.message}" else job.message.ifBlank { "غير منزّلة" }, color = Color(0xFF66736C))
                    if (job.running && job.total > 0) LinearProgressIndicator(progress = { job.done.toFloat() / job.total }, Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (job.running) OutlinedButton(onClick = { mgr.pauseWords(s.index) }) { Text("إيقاف مؤقت") }
                        else OutlinedButton(onClick = { mgr.startWords(s.index) }) { Text(if (have > 0) "متابعة / تحديث" else "تنزيل") }
                        if (have > 0 && !job.running) OutlinedButton(onClick = { mgr.deleteWords(s.index) }) { Text("حذف") }
                    }
                }
            }
            if (used > 0) OutlinedButton(onClick = { confirmDelete = true }, Modifier.fillMaxWidth()) { Text("حذف كل صوت الكلمات") }
            Text("الصوت: Quran.com / Quran Foundation.", color = Color(0xFF66736C))
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("حذف كل صوت الكلمات؟") }, text = { Text("سيعود صوت الكلمة إلى البث من الإنترنت.") },
        confirmButton = { TextButton(onClick = { mgr.deleteAllWords(); confirmDelete = false }) { Text("حذف") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } })
}

/** Download the entire Quran: all ayah audio for the chosen reciter, or all word audio. Confirmation, sizes, pause/resume. */
@Composable
fun BulkDownloadCard(suras: List<QuranSuraMetadata>) {
    val ctx = LocalContext.current
    val mgr = remember { AudioDownloadManager.get(ctx) }
    val bulk by mgr.bulk.collectAsState()
    val rev by mgr.revision.collectAsState()
    var pending by remember { mutableStateOf<AudioDownloadManager.BulkKind?>(null) }
    val reciter = com.example.quranoffline.data.Reciters.run { ensureLoaded(ctx); byId(currentId) }
    val counts = suras.map { it.ayahCount }
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("تنزيل القرآن كاملاً", fontWeight = FontWeight.Bold, color = Color(0xFF123F35))
            Text("يمكن إيقاف التنزيل ومتابعته لاحقًا؛ ما اكتمل من الملفات يبقى. التنزيل يعمل أثناء فتح التطبيق، وإن أغلقه النظام فيُتابع من حيث توقف عند الضغط على المتابعة.", color = Color(0xFF66736C))
            if (bulk.running) {
                Text("${bulk.message} (اكتملت ${bulk.doneSurahs} من 114 سورة)")
                LinearProgressIndicator(progress = { bulk.doneSurahs / 114f }, Modifier.fillMaxWidth())
                OutlinedButton(onClick = { mgr.pauseBulk() }, Modifier.fillMaxWidth()) { Text("إيقاف مؤقت") }
            } else {
                if (bulk.message.isNotBlank()) Text(bulk.message, color = Color(0xFF66736C))
                Button(onClick = { pending = AudioDownloadManager.BulkKind.AYAH }, Modifier.fillMaxWidth()) { Text("تنزيل كل الآيات للقارئ: ${reciter.label}") }
                Button(onClick = { pending = AudioDownloadManager.BulkKind.WORDS }, Modifier.fillMaxWidth()) { Text("تنزيل صوت كل الكلمات") }
            }
        }
    }
    pending?.let { kind ->
        val free = mgr.freeBytes()
        val have by produceState(0L, kind) {
            value = withContext(kotlinx.coroutines.Dispatchers.IO) {
                if (kind == AudioDownloadManager.BulkKind.AYAH) mgr.usedBytes(reciter.id) else mgr.wordStore.usedBytes()
            }
        }
        val (total, exact) = if (kind == AudioDownloadManager.BulkKind.AYAH) mgr.totalSize(reciter.id) to true
        else mgr.wordTotal().let { (it.first as Long?) to it.second }
        val remaining = total?.let { (it - have).coerceAtLeast(0) }
        val enough = remaining != null && free >= remaining + 100L * 1024 * 1024
        AlertDialog(onDismissRequest = { pending = null },
            title = { Text(if (kind == AudioDownloadManager.BulkKind.AYAH) "تنزيل كل الآيات؟" else "تنزيل صوت كل الكلمات؟") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (total == null) "الحجم الكلي غير مقاس بعد لهذا القارئ. اضغط «قياس الأحجام» في إعدادات القارئ أولاً." else "الحجم الكلي: ${mb(total)} ${if (exact) "(مقاس فعليًا)" else "(تقدير وليس قياسًا فعليًا)"}")
                    if (remaining != null) Text("المتبقي للتنزيل: ${mb(remaining)}")
                    Text("المساحة الحرة على الجهاز: ${mb(free)}")
                    if (remaining != null && !enough) Text("المساحة غير كافية (يلزم المتبقي + 100 ميغابايت احتياطي).", color = Color(0xFF9A3B3B))
                    if (!mgr.networkAllowed()) Text("الشبكة غير متاحة أو الوضع Wi-Fi فقط مفعّل ولست على Wi-Fi.", color = Color(0xFF9A3B3B))
                }
            },
            confirmButton = { TextButton(enabled = enough && mgr.networkAllowed(), onClick = { mgr.startBulk(kind, reciter.id, counts); pending = null }) { Text("ابدأ التنزيل") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("إلغاء") } })
    }
}

/** Settings: what the "hide ayahs" mode shows. Default on: only words already said (or judged) appear. */
@Composable
fun HiddenViewSettingsCard() {
    val ctx = LocalContext.current
    var only by remember { mutableStateOf(com.example.quranoffline.data.WordDisplayRules.onlySpoken(ctx)) }
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("عرض التسميع مع إخفاء الآيات", fontWeight = FontWeight.Bold, color = Color(0xFF123F35))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("إظهار الكلمات التي نُطقت فقط (بدون نقاط للكلمات المتبقية)", Modifier.weight(1f))
                Switch(checked = only, onCheckedChange = { only = it; com.example.quranoffline.data.WordDisplayRules.setOnlySpoken(ctx, it) })
            }
            Text("الألوان كما هي: أخضر للكلمة الصحيحة، أحمر للكلمة الخاطئة أو الزائدة أو المتروكة (تظهر بنصها الصحيح)، أصفر لغير المؤكدة. عند الخطأ يُشغَّل صوت الكلمة الصحيحة كما في النسخ السابقة.", color = Color(0xFF66736C))
        }
    }
}

/** Optional: use quran.com's page and line breaks. Downloaded on request into private storage; restart applies it. */
@Composable
fun QuranComLayoutSettingsCard() {
    val ctx = LocalContext.current
    var installed by remember { mutableStateOf(com.example.quranoffline.data.QuranComLayoutStore.isInstalled(ctx)) }
    var progress by remember { mutableStateOf(-1) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ترتيب المصحف (صفحات وأسطر Quran.com)", fontWeight = FontWeight.Bold, color = Color(0xFF123F35))
            Text("يُنزَّل ترتيب الصفحات والأسطر فقط من Quran.com (604 صفحة، نحو 8 ميغابايت من الإنترنت)، ويبقى نص الآيات هو نص التطبيق نفسه. يُنزَّل تلقائياً أول مرة عند توفر الإنترنت ويُحفظ داخل التطبيق ويُطبَّق دون إعادة تشغيل (عند عدم وجود تسميع أو تشغيل جارٍ). المصدر: Quran.com / Quran Foundation.", color = Color(0xFF66736C))
            Text(if (installed) "الحالة: ترتيب Quran.com مُثبّت." else "الحالة: يُستخدم الترتيب المضمَّن (ملف Word).", fontWeight = FontWeight.Bold)
            if (progress in 0..603) Text("جارٍ التنزيل: ${progress} / 604")
            if (message.isNotEmpty()) Text(message)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = progress < 0, onClick = {
                    progress = 0; message = ""
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val r = com.example.quranoffline.data.QuranComLayoutStore.downloadAndInstall(ctx) { progress = it }
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            progress = -1
                            installed = com.example.quranoffline.data.QuranComLayoutStore.isInstalled(ctx)
                            message = if (r.isSuccess) "تم. سيُطبَّق الترتيب الجديد عند هدوء التطبيق (أو عند التشغيل التالي)." else "تعذر التنزيل: ${r.exceptionOrNull()?.message ?: "خطأ"}. لم يتغير شيء."
                        }
                    }
                }) { Text(if (installed) "إعادة التنزيل" else "تنزيل وتطبيق") }
                if (installed) OutlinedButton(enabled = progress < 0, onClick = {
                    com.example.quranoffline.data.QuranComLayoutStore.remove(ctx); installed = false
                    message = "حُذف. سيُستخدم ترتيب Word، ولن يُنزَّل Quran.com تلقائياً بعد الآن."
                }) { Text("الرجوع إلى ترتيب Word") }
            }
        }
    }
}
