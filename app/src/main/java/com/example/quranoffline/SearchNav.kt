package com.example.quranoffline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quranoffline.data.QuranUiState
import com.example.quranoffline.data.QuranViewModel
import com.example.quranoffline.data.SearchOutcome
import kotlinx.coroutines.delay

@Composable
internal fun PickerField(label: String, valueText: String, options: List<Pair<String, Int>>, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(56.dp))
        Box(Modifier.weight(1f)) {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(valueText, maxLines = 1) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                options.forEach { (title, value) -> DropdownMenuItem(text = { Text(title) }, onClick = { open = false; onPick(value) }) }
            }
        }
    }
}

/** Full-screen search: query, scope dropdown, banner with counts, result cards with the matched words highlighted. */
@Composable
fun QuranSearchOverlay(state: QuranUiState, viewModel: QuranViewModel, onClose: () -> Unit, onOpenAyah: (Int, Int) -> Unit) {
    var query by remember { mutableStateOf("") }
    var scopeSurah by remember { mutableStateOf<Int?>(null) }
    var outcome by remember { mutableStateOf<SearchOutcome?>(null) }
    var scopeOpen by remember { mutableStateOf(false) }
    val suras = state.metadata?.suras.orEmpty()
    LaunchedEffect(Unit) { viewModel.warmSearchIndex() }
    LaunchedEffect(query, scopeSurah) {
        if (query.isBlank()) { outcome = null; return@LaunchedEffect }
        delay(300) // debounce typing
        outcome = viewModel.searchAyahs(query, scopeSurah)
    }
    Column(Modifier.fillMaxSize().background(Color(0xFFF2F3EE))) {
        Surface(color = Color(0xFF0F5B4D)) {
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "رجوع", tint = Color.White) }
                    TextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        placeholder = { Text("ابحث عن كلمة أو أكثر أو آية", color = Color(0xFFD6EEE5)) },
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color.White),
                        modifier = Modifier.weight(1f),
                    )
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "مسح", tint = Color.White) }
                }
                Box {
                    TextButton(onClick = { scopeOpen = true }) {
                        Text("البحث في: ${scopeSurah?.let { s -> suras.firstOrNull { it.index == s }?.arabicName?.let { "سورة $it" } } ?: "القرآن كاملاً"} ▾", color = Color.White)
                    }
                    DropdownMenu(expanded = scopeOpen, onDismissRequest = { scopeOpen = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                        DropdownMenuItem(text = { Text("القرآن كاملاً") }, onClick = { scopeOpen = false; scopeSurah = null })
                        suras.forEach { s -> DropdownMenuItem(text = { Text("سورة ${s.arabicName}") }, onClick = { scopeOpen = false; scopeSurah = s.index }) }
                    }
                }
            }
        }
        val out = outcome
        if (out != null) {
            Surface(color = Color(0xFFE8833A), modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (out.hits.isEmpty()) "لا توجد نتائج لـ '${query.trim()}'" else "ظهرت '${query.trim()}' ${out.occurrences} مرة، في ${out.hits.size} آية" + if (out.usedSubstring) " (تطابق جزئي)" else "",
                    color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 13.sp,
                )
            }
            val byKey = remember(state.allAyahs) { state.allAyahs.associateBy { it.surah * 1000 + it.ayah } }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(out.hits, key = { it.surah * 1000 + it.ayah }) { hit ->
                    val ayah = byKey[hit.surah * 1000 + hit.ayah]
                    val page = viewModel.pageOfAyah(hit.surah, hit.ayah)
                    val name = suras.firstOrNull { it.index == hit.surah }?.arabicName ?: "${hit.surah}"
                    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth().clickable { onOpenAyah(hit.surah, hit.ayah) }) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                buildAnnotatedString {
                                    ayah?.words?.forEachIndexed { i, w ->
                                        if (i in hit.matchedWords) withStyle(SpanStyle(color = Color(0xFFE8833A), fontWeight = FontWeight.Bold)) { append(w.text) } else append(w.text)
                                        append(' ')
                                    }
                                },
                                fontSize = 19.sp, lineHeight = 32.sp,
                            )
                            Text("سورة $name - آية ${hit.ayah}${page?.let { " ، صفحة $it" } ?: ""}", color = Color(0xFF66736C), fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

/** "Go to" dialog: page, juz, surah, ayah (typed), hizb. The field changed last decides where to go. */
@Composable
fun GoToDialog(state: QuranUiState, viewModel: QuranViewModel, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    val md = state.metadata
    val suras = md?.suras.orEmpty()
    var page by remember { mutableStateOf(state.currentPage.toString()) }
    var juz by remember { mutableStateOf<Int?>(null) }
    var surah by remember { mutableStateOf<Int?>(state.selectedSurah) }
    var ayah by remember { mutableStateOf("") }
    var hizb by remember { mutableStateOf<Int?>(null) }
    var last by remember { mutableStateOf("surah") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("الذهاب إلى...") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(page, { page = it.filter(Char::isDigit).take(3); last = "page" }, label = { Text("صفحة") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                PickerField("جزء", juz?.let { "الجزء $it" } ?: "—", (1..30).map { "الجزء $it" to it }, { juz = it; last = "juz" })
                PickerField("سورة", surah?.let { s -> suras.firstOrNull { it.index == s }?.arabicName?.let { "سورة $it" } } ?: "—", suras.map { "${it.index}. ${it.arabicName}" to it.index }, { surah = it; last = "surah" })
                OutlinedTextField(ayah, { ayah = it.filter(Char::isDigit).take(3); last = "surah" }, label = { Text("رقم الآية (اختياري)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                PickerField("حزب", hizb?.let { "حزب $it" } ?: "—", (1..60).map { "حزب $it" to it }, { hizb = it; last = "hizb" })
                error?.let { Text(it, color = Color(0xFFB3261E), fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val target = viewModel.resolveQuickNav(surah, ayah.toIntOrNull(), juz, hizb, page.toIntOrNull(), last)
                if (target == null || target !in 1..604) error = "تحقق من القيم (آية غير موجودة في السورة أو حقل فارغ)." else onGo(target)
            }) { Text("اذهب") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
