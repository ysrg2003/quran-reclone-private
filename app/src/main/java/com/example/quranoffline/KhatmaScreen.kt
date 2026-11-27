package com.example.quranoffline

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.quranoffline.data.KhatmaLogic
import com.example.quranoffline.data.KhatmaState
import com.example.quranoffline.data.QuranUiState
import com.example.quranoffline.data.SaveMarkKind

@Composable
fun KhatmaScreen(
    state: QuranUiState,
    onTarget: (SaveMarkKind, Int) -> Unit,
    onReminder: (SaveMarkKind, Boolean, Int, Int) -> Unit,
    onOpen: (SaveMarkKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(Color(0xFFF7F8F4)).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("الختمات", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        KhatmaCard("ختمة التسميع والحفظ", state.khatmaHifz, state, onTarget, onReminder, onOpen)
        KhatmaCard("ختمة القراءة والاستماع", state.khatmaRead, state, onTarget, onReminder, onOpen)
        Text("تُحسب الآية في ختمة التسميع بعد الانتقال منها، وفي ختمة القراءة عند سماعها بصوت القارئ أو عند تقليب الصفحة للأمام.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF66736C))
    }
}

@Composable
private fun KhatmaCard(
    title: String, k: KhatmaState, state: QuranUiState,
    onTarget: (SaveMarkKind, Int) -> Unit, onReminder: (SaveMarkKind, Boolean, Int, Int) -> Unit, onOpen: (SaveMarkKind) -> Unit,
) {
    val last = state.allAyahs.getOrNull(k.lastIndex)
    val suraName = last?.let { a -> state.metadata?.sura(a.surah)?.arabicName }
    val percent = k.coveredCount * 100f / KhatmaLogic.TOTAL
    var targetText by remember(k.dailyTarget) { mutableStateOf(if (k.dailyTarget > 0) k.dailyTarget.toString() else "") }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text("الختمات المكتملة: ${k.completed}")
            LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
            Text("${k.coveredCount} من ${KhatmaLogic.TOTAL} آية (${"%.1f".format(percent)}%)")
            Text(if (last != null) "وصلت إلى: سورة ${suraName ?: last.surah} • آية ${last.ayah}" else "لم تبدأ هذه الختمة بعد.")
            OutlinedButton(onClick = { onOpen(k.kind) }, enabled = last != null, modifier = Modifier.fillMaxWidth()) { Text("افتح موضعي في الختمة") }
            HorizontalDivider()
            Text("الورد اليومي: اليوم ${k.todayCount}" + if (k.dailyTarget > 0) " من ${k.dailyTarget} آية" else " آية")
            OutlinedTextField(
                value = targetText,
                onValueChange = { v -> targetText = v.filter(Char::isDigit).take(4); onTarget(k.kind, targetText.toIntOrNull() ?: 0) },
                label = { Text("الهدف اليومي (عدد الآيات)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("تذكير يومي بالإشعار", modifier = Modifier.weight(1f))
                Switch(checked = k.reminderOn, onCheckedChange = { on ->
                    if (on && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    onReminder(k.kind, on, k.reminderHour, k.reminderMinute)
                })
            }
            if (k.reminderOn) {
                PickerField("الساعة", "%02d".format(k.reminderHour), (0..23).map { "%02d".format(it) to it }, { onReminder(k.kind, true, it, k.reminderMinute) })
                PickerField("الدقيقة", "%02d".format(k.reminderMinute), (0..55 step 5).map { "%02d".format(it) to it }, { onReminder(k.kind, true, k.reminderHour, it) })
                Text("قد يتأخر التذكير بضع دقائق (توفيراً للبطارية).", style = MaterialTheme.typography.labelSmall, color = Color(0xFF66736C))
            }
        }
    }
}
