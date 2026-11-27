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
import com.example.quranoffline.data.AudioDownloadManager
import com.example.quranoffline.data.AudioPackSizes
import com.example.quranoffline.data.QuranSuraMetadata

private fun mb(b: Long) = "%.1f ميغابايت".format(b / 1048576.0)

/** Settings > Download settings. Optional offline copy of ayah audio; streaming remains the default. */
@Composable
fun AudioDownloadSettingsCard(suras: List<QuranSuraMetadata>) {
    val ctx = LocalContext.current
    val mgr = remember { AudioDownloadManager.get(ctx) }
    val jobs by mgr.jobs.collectAsState()
    val rev by mgr.revision.collectAsState()
    var wifi by remember { mutableStateOf(mgr.wifiOnly) }
    var expanded by remember { mutableStateOf(false) }
    var confirmAll by remember { mutableStateOf(false) }
    val used = remember(rev, jobs) { mgr.store.usedBytes() }
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("إعدادات التنزيل", fontWeight = FontWeight.Bold, color = Color(0xFF123F35))
            Text("تنزيل صوت الآيات (الحصري، مرتل) من Quran.com اختياري. بدون تنزيل يبقى الصوت يُبثّ من الإنترنت كما كان. الملفات تُحفظ في تخزين التطبيق الخاص فقط، ولا تظهر لتطبيقات أخرى، وتُحذف عند إلغاء التثبيت أو من هنا.", color = Color(0xFF66736C))
            Text("الحجم الكامل لصوت المصحف: ${mb(AudioPackSizes.total)}. المستخدم الآن: ${mb(used)}.")
            Text("مكان التخزين: تخزين التطبيق الداخلي الخاص (غير قابل للتغيير).", color = Color(0xFF66736C))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("التنزيل عبر Wi-Fi فقط", Modifier.weight(1f))
                Switch(checked = wifi, onCheckedChange = { wifi = it; mgr.wifiOnly = it })
            }
            Text("الصوت: Quran.com / Quran Foundation، القارئ: محمود خليل الحصري.", color = Color(0xFF66736C))
            Text("ملاحظة: تنزيل كلمات الآيات الصوتية وبيانات النص غير مفعّل في هذه النسخة؛ النص مدمج أصلًا في التطبيق، وصوت الكلمة يبقى عبر الإنترنت.", color = Color(0xFF66736C))
            OutlinedButton(onClick = { expanded = !expanded }, Modifier.fillMaxWidth()) { Text(if (expanded) "إخفاء قائمة السور" else "اختيار السور للتنزيل") }
            if (expanded) suras.forEach { s ->
                val job = jobs[s.index] ?: AudioDownloadManager.JobState()
                val have = remember(rev, jobs) { mgr.store.surahCount(s.index) }
                val complete = have == s.ayahCount
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${s.index}. ${s.arabicName}  (${mb(AudioPackSizes.surah(s.index))})", fontWeight = FontWeight.SemiBold)
                    Text(if (job.running) "$have/${s.ayahCount} آية، ${job.message}" else if (complete) "منزّلة بالكامل" else if (have > 0) "$have/${s.ayahCount} آية. ${job.message}" else job.message.ifBlank { "غير منزّلة" }, color = Color(0xFF66736C))
                    if (job.running && job.total > 0) LinearProgressIndicator(progress = { job.done.toFloat() / job.total }, Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (job.running) OutlinedButton(onClick = { mgr.pause(s.index) }) { Text("إيقاف مؤقت") }
                        else if (!complete) OutlinedButton(onClick = { mgr.start(s.index, s.ayahCount) }) { Text(if (have > 0) "متابعة" else "تنزيل") }
                        if (have > 0 && !job.running) OutlinedButton(onClick = { mgr.delete(s.index) }) { Text("حذف") }
                        if (complete && !job.running) OutlinedButton(onClick = { mgr.delete(s.index); mgr.start(s.index, s.ayahCount) }) { Text("إعادة التنزيل") }
                    }
                }
            }
            if (used > 0) OutlinedButton(onClick = { confirmAll = true }, Modifier.fillMaxWidth()) { Text("حذف كل الصوت المنزّل") }
        }
    }
    if (confirmAll) AlertDialog(onDismissRequest = { confirmAll = false },
        title = { Text("حذف كل الصوت المنزّل؟") }, text = { Text("سيعود الصوت إلى البث من الإنترنت.") },
        confirmButton = { TextButton(onClick = { mgr.deleteAll(); confirmAll = false }) { Text("حذف") } },
        dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("إلغاء") } })
}
