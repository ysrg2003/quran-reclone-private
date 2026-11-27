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
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast

@Composable
fun KhatmaScreen(
    state: QuranUiState,
    onTarget: (SaveMarkKind, Int) -> Unit,
    onReminder: (SaveMarkKind, Boolean, Int, Int) -> Unit,
    onOpen: (SaveMarkKind) -> Unit,
    modifier: Modifier = Modifier,
    onTargetUnit: (SaveMarkKind, Int) -> Unit = { _, _ -> },
    onRepeat: (SaveMarkKind, Int, Int) -> Unit = { _, _, _ -> },
) {
    Column(modifier.fillMaxSize().background(Color(0xFFF7F8F4)).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("الختمات", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        KhatmaCard("ختمة التسميع والحفظ", state.khatmaHifz, state, onTarget, onReminder, onOpen, onTargetUnit, onRepeat)
        KhatmaCard("ختمة القراءة والاستماع", state.khatmaRead, state, onTarget, onReminder, onOpen, onTargetUnit, onRepeat)
        Text("تُحسب الآية في ختمة التسميع بعد الانتقال منها، وفي ختمة القراءة عند سماعها بصوت القارئ أو عند تقليب الصفحة للأمام.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF66736C))
    }
}

@Composable
private fun KhatmaCard(
    title: String, k: KhatmaState, state: QuranUiState,
    onTarget: (SaveMarkKind, Int) -> Unit, onReminder: (SaveMarkKind, Boolean, Int, Int) -> Unit, onOpen: (SaveMarkKind) -> Unit,
    onTargetUnit: (SaveMarkKind, Int) -> Unit, onRepeat: (SaveMarkKind, Int, Int) -> Unit,
) {
    val last = state.allAyahs.getOrNull(k.lastIndex)
    val suraName = last?.let { a -> state.metadata?.sura(a.surah)?.arabicName }
    val percent = k.coveredCount * 100f / KhatmaLogic.TOTAL
    var targetText by remember(k.dailyTarget) { mutableStateOf(if (k.dailyTarget > 0) k.dailyTarget.toString() else "") }
    val ctx = LocalContext.current
    var statusTick by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { statusTick++ }
    LaunchedEffect(k.reminderOn, k.reminderHour, k.reminderMinute) { if (k.reminderOn) KhatmaReminders.apply(ctx, k) }
    val est = remember(k.coveredCount, k.startMillis, k.dailyTarget) { KhatmaLogic.estimate(k, System.currentTimeMillis()) }
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text("الختمات المكتملة: ${k.completed}")
            LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
            Text("${k.coveredCount} من ${KhatmaLogic.TOTAL} آية (${"%.1f".format(percent)}%)")
            Text(if (last != null) "وصلت إلى: سورة ${suraName ?: last.surah} • آية ${last.ayah}" else "لم تبدأ هذه الختمة بعد.")
            fun dateAfter(days: Int): String = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale("ar")).format(java.util.Date(System.currentTimeMillis() + days * 86_400_000L))
            if (k.targetAyahs > 0) {
                val fullDays = Math.ceil(KhatmaLogic.TOTAL.toDouble() / k.targetAyahs).toInt()
                Text("ختمة القرآن كاملاً (${KhatmaLogic.TOTAL} آية) بوردك اليومي: $fullDays يوماً، تنتهي في ${dateAfter(fullDays)} إن التزمت", fontWeight = FontWeight.SemiBold)
            } else Text("حدّد الورد اليومي ليظهر عدد أيام ختمة القرآن كاملاً.", color = Color(0xFF66736C))
            if (k.coveredCount > 0) {
                Text("المتبقي: ${est.remaining} آية")
                est.daysByPace?.let { Text("بوتيرتك الحالية (${"%.1f".format(est.pacePerDay)} آية/يوم) تنتهي بعد $it يوماً، في ${dateAfter(it)}") }
                est.daysByTarget?.let { Text("بالورد اليومي (${k.dailyTarget} ${listOf("آية", "صفحة", "سورة")[k.targetUnit.coerceIn(0, 2)]}${if (k.targetUnit > 0) "، تقريبياً" else ""}) تنتهي بعد $it يوماً، في ${dateAfter(it)}") }
            }
            OutlinedButton(onClick = { onOpen(k.kind) }, enabled = last != null, modifier = Modifier.fillMaxWidth()) { Text("افتح موضعي في الختمة") }
            HorizontalDivider()
            Text("الورد اليومي: اليوم ${k.todayCount} آية" + if (k.targetAyahs > 0) " من ${k.targetAyahs} آية" else "")
            if (k.targetAyahs > 0) {
                val wp = (k.todayCount * 100f / k.targetAyahs).coerceAtMost(100f)
                LinearProgressIndicator(progress = { wp / 100f }, modifier = Modifier.fillMaxWidth())
                Text("تقدم ورد اليوم: ${"%.0f".format(wp)}%")
            }
            PickerField("وحدة الورد", listOf("آيات", "صفحات", "سور")[k.targetUnit.coerceIn(0, 2)], listOf("آيات" to 0, "صفحات" to 1, "سور" to 2), { onTargetUnit(k.kind, it) })
            OutlinedTextField(
                value = targetText,
                onValueChange = { v -> targetText = v.filter(Char::isDigit).take(4); onTarget(k.kind, targetText.toIntOrNull() ?: 0) },
                label = { Text("الورد اليومي (العدد بالوحدة المختارة)") }, singleLine = true,
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
                var repMin by remember(k.kind, k.repeatMinutes) { mutableStateOf(if (k.repeatMinutes > 0) k.repeatMinutes.toString() else "") }
                var repCnt by remember(k.kind, k.repeatCount) { mutableStateOf(k.repeatCount.toString()) }
                Text("تكرار التذكير إن لم يكتمل الورد (اتركه فارغاً بلا تكرار)", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = repMin, singleLine = true, modifier = Modifier.weight(1f),
                        label = { Text("كل كم دقيقة") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        onValueChange = { v ->
                            val d = v.filter { it.isDigit() }.take(4); repMin = d
                            onRepeat(k.kind, d.toIntOrNull() ?: 0, repCnt.toIntOrNull() ?: k.repeatCount)
                        },
                    )
                    OutlinedTextField(
                        value = repCnt, singleLine = true, modifier = Modifier.weight(1f),
                        label = { Text("كم مرة (1 إلى 50)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        onValueChange = { v ->
                            val d = v.filter { it.isDigit() }.take(2); repCnt = d
                            d.toIntOrNull()?.let { onRepeat(k.kind, repMin.toIntOrNull() ?: 0, it) }
                        },
                    )
                }
                key(statusTick) { Text(KhatmaReminders.status(ctx, k), style = MaterialTheme.typography.labelSmall, color = Color(0xFF66736C)) }
                OutlinedButton(onClick = {
                    if (Build.VERSION.SDK_INT >= 33 && !KhatmaReminders.notificationsAllowed(ctx)) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    val ok = KhatmaReminders.notify(ctx, k, test = true)
                    Toast.makeText(ctx, if (ok) "أُرسل إشعار تجريبي" else "الإشعارات مغلقة. فعّلها من إعدادات التطبيق", Toast.LENGTH_LONG).show()
                    statusTick++
                }, modifier = Modifier.fillMaxWidth()) { Text("أرسل إشعاراً تجريبياً الآن") }
            }
        }
    }
}
