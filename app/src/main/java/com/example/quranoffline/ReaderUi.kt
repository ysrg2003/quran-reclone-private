package com.example.quranoffline

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quranoffline.data.QuranUiState
import com.example.quranoffline.data.ReaderScope
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** "الاستماع إلى؟" sheet opened from the play triangle. Anchor = the ayah playback would start from. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ListenToSheet(
    state: QuranUiState,
    surah: Int,
    ayah: Int,
    onDismiss: () -> Unit,
    onPlayAyah: (Int, Int) -> Unit,
    onPlayScope: (ReaderScope, Int, Int) -> Unit,
    onCustom: (Int, Int, Int, Int, Int) -> Unit,
) {
    val md = state.metadata?.ayah(surah, ayah)
    val suraName = state.metadata?.sura(surah)?.arabicName ?: "$surah"
    val quarterInHizb = md?.let { ((it.quarter - 1) % 4) + 1 } ?: 1
    val hizb = md?.let { (it.quarter - 1) / 4 + 1 } ?: 1
    var custom by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text("الاستماع إلى؟", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = Color(0xFF66736C))
            @Composable fun row(label: String, onClick: () -> Unit) {
                Text(label, Modifier.fillMaxWidth().clickable { onDismiss(); onClick() }.padding(horizontal = 24.dp, vertical = 14.dp), fontSize = 17.sp)
            }
            row("آية $ayah") { onPlayAyah(surah, ayah) }
            row("صفحة ${md?.page ?: state.currentPage}") { onPlayScope(ReaderScope.PAGES, surah, ayah) }
            row("سورة $suraName") { onPlayScope(ReaderScope.SURAH, surah, ayah) }
            row("الجزء ${md?.juz ?: 1}") { onPlayScope(ReaderScope.JUZ, surah, ayah) }
            row("الربع $quarterInHizb الحزب $hizb") { onPlayScope(ReaderScope.HIZB, surah, ayah) }
            row("القرآن كاملاً من هنا") { onPlayScope(ReaderScope.QURAN, surah, ayah) }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().clickable { custom = true }.padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, null)
                Spacer(Modifier.width(16.dp))
                Text("مخصص", fontSize = 17.sp)
            }
        }
    }
    if (custom) CustomListenDialog(state, surah, ayah, onDismiss = { custom = false }, onGo = { a, b, c, d, r -> custom = false; onDismiss(); onCustom(a, b, c, d, r) })
}

@Composable
private fun CustomListenDialog(state: QuranUiState, surah: Int, ayah: Int, title: String = "استماع مخصص", goLabel: String = "استمع", withRepeat: Boolean = true, onDismiss: () -> Unit, onGo: (Int, Int, Int, Int, Int) -> Unit) {
    val suras = state.metadata?.suras.orEmpty()
    var s1 by remember { mutableStateOf(surah) }
    var a1 by remember { mutableStateOf(ayah.toString()) }
    var s2 by remember { mutableStateOf(surah) }
    var a2 by remember { mutableStateOf(ayah.toString()) }
    var rep by remember { mutableStateOf(1) }
    fun name(s: Int) = suras.firstOrNull { it.index == s }?.arabicName?.let { "سورة $it" } ?: "—"
    val opts = suras.map { "${it.index}. ${it.arabicName}" to it.index }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PickerField("من", name(s1), opts, { s1 = it })
                OutlinedTextField(a1, { a1 = it.filter(Char::isDigit).take(3) }, label = { Text("آية البداية") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                PickerField("إلى", name(s2), opts, { s2 = it })
                OutlinedTextField(a2, { a2 = it.filter(Char::isDigit).take(3) }, label = { Text("آية النهاية") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                if (withRepeat) PickerField("التكرار", "$rep", (1..5).map { "$it" to it }, { rep = it })
            }
        },
        confirmButton = { TextButton(onClick = { onGo(s1, a1.toIntOrNull() ?: 1, s2, a2.toIntOrNull() ?: 1, rep) }) { Text(goLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

/** Radial menu drawn around the long-press point: a close button in the centre and round action buttons on a ring. */
@Composable
internal fun RadialAyahMenu(
    center: Offset,
    onDismiss: () -> Unit,
    actions: List<Triple<ImageVector, String, () -> Unit>>,
) {
    val cfg = LocalConfiguration.current
    val density = LocalDensity.current
    val radius = 84.dp
    val btn = 48.dp
    val rPx = with(density) { radius.toPx() }
    val margin = with(density) { (radius + btn / 2 + 8.dp).toPx() }
    val cx = center.x.coerceIn(margin, with(density) { cfg.screenWidthDp.dp.toPx() } - margin)
    val cy = center.y.coerceIn(margin, with(density) { cfg.screenHeightDp.dp.toPx() } - margin)
    val orange = Color(0xFFE8703A)
    Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() }) {
        @Composable fun bubble(x: Float, y: Float, icon: ImageVector, desc: String, color: Color, onClick: () -> Unit) {
            val half = with(density) { (btn / 2).roundToPx() }
            Box(
                Modifier.offset { IntOffset(x.roundToInt() - half, y.roundToInt() - half) }.size(btn).background(color, CircleShape)
                    .clickable(onClickLabel = desc) { onClick() },
                contentAlignment = Alignment.Center,
            ) { Icon(icon, desc, tint = Color.White) }
        }
        actions.forEachIndexed { i, (icon, desc, act) ->
            val ang = Math.toRadians(-90.0 + i * 360.0 / actions.size)
            bubble(cx + rPx * cos(ang).toFloat(), cy + rPx * sin(ang).toFloat(), icon, desc, orange) { onDismiss(); act() }
        }
        bubble(cx, cy, Icons.Default.Close, "إغلاق", Color(0xFFD94A1E)) { onDismiss() }
    }
}

/**
 * v23 actions circle: stays on screen while a selection exists. It does not intercept touches outside its buttons, so the
 * user can keep tapping ayahs to extend or shrink the selection. Tapping the middle button closes it and clears the
 * selection (onCloseAll). Dragging the middle button moves the whole circle.
 */
@Composable
internal fun ActionsCircle(
    center: Offset,
    btnDp: Int,
    onMove: (Offset) -> Unit,
    onCloseAll: () -> Unit,
    actions: List<Triple<ImageVector, String, () -> Unit>>,
) {
    val cfg = LocalConfiguration.current
    val density = LocalDensity.current
    val btn = btnDp.dp
    val rPx = with(density) { (btnDp * 1.45f).dp.toPx() }
    val margin = rPx + with(density) { (btn / 2).toPx() } + 4f
    val w = with(density) { cfg.screenWidthDp.dp.toPx() }
    val h = with(density) { cfg.screenHeightDp.dp.toPx() }
    val cx = center.x.coerceIn(margin, (w - margin).coerceAtLeast(margin))
    val cy = center.y.coerceIn(margin, (h - margin).coerceAtLeast(margin))
    val orange = Color(0xFFE8703A)
    val half = with(density) { (btn / 2).roundToPx() }
    val iconSize = (btnDp * 0.55f).dp
    Box(Modifier.fillMaxSize()) {
        actions.forEachIndexed { i, (icon, desc, act) ->
            val ang = Math.toRadians(-90.0 + i * 360.0 / actions.size)
            val x = cx + rPx * cos(ang).toFloat()
            val y = cy + rPx * sin(ang).toFloat()
            Box(
                Modifier.offset { IntOffset(x.roundToInt() - half, y.roundToInt() - half) }.size(btn).background(orange, CircleShape)
                    .clickable(onClickLabel = desc) { act() },
                contentAlignment = Alignment.Center,
            ) { Icon(icon, desc, tint = Color.White, modifier = Modifier.size(iconSize)) }
        }
        val centerNow = androidx.compose.runtime.rememberUpdatedState(Offset(cx, cy))
        Box(
            Modifier.offset { IntOffset(cx.roundToInt() - half, cy.roundToInt() - half) }.size(btn).background(Color(0xFFD94A1E), CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume(); onMove(centerNow.value + drag)
                    }
                }
                .clickable(onClickLabel = "إغلاق الدائرة وإلغاء التحديد") { onCloseAll() },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Close, "إغلاق", tint = Color.White, modifier = Modifier.size(iconSize)) }
    }
}

@Composable
internal fun AyahInfoDialog(state: QuranUiState, surah: Int, ayah: Int, onDismiss: () -> Unit) {
    // v25.6 (user 29258668): with several ayahs selected the info describes the whole selection as one block.
    val sel = state.selectedAyahIndices.mapNotNull { state.allAyahs.getOrNull(it) }
    val first = sel.firstOrNull(); val last = sel.lastOrNull()
    if (sel.size > 1 && first != null && last != null) {
        val metas = sel.mapNotNull { state.metadata?.ayah(it.surah, it.ayah) }
        fun nm(n: Int) = state.metadata?.sura(n)?.arabicName ?: "$n"
        val surahs = sel.map { it.surah }.distinct()
        val pages = metas.map { it.page }; val juzs = metas.map { it.juz }
        val hizbs = metas.map { (it.quarter - 1) / 4 + 1 }
        fun rng(v: List<Int>) = if (v.isEmpty()) "—" else if (v.min() == v.max()) "${v.min()}" else "${v.min()} - ${v.max()}"
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("معلومات الآيات المحددة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("عدد الآيات: ${sel.size}")
                    Text(if (surahs.size == 1) "السورة: ${nm(first.surah)} (${first.surah})" else "السور: " + surahs.joinToString("، ") { nm(it) })
                    Text("من: ${nm(first.surah)} آية ${first.ayah}")
                    Text("إلى: ${nm(last.surah)} آية ${last.ayah}")
                    Text("الصفحة: ${rng(pages)}")
                    Text("الجزء: ${rng(juzs)}")
                    Text("الحزب: ${rng(hizbs)}")
                    Text("عدد الكلمات: ${sel.sumOf { it.words.size }}")
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("حسناً") } },
        )
        return
    }
    val md = state.metadata?.ayah(surah, ayah)
    val name = state.metadata?.sura(surah)?.arabicName ?: "$surah"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("معلومات الآية") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("السورة: $name ($surah)")
                Text("الآية: $ayah")
                Text("الصفحة: ${md?.page ?: "—"}")
                Text("الجزء: ${md?.juz ?: "—"}")
                md?.let { Text("الحزب: ${(it.quarter - 1) / 4 + 1} • الربع: ${((it.quarter - 1) % 4) + 1}") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("حسناً") } },
    )
}

/** v24.3: set by the tab-0 recitation card to open the tasmee range sheet (hosted in QuranOfflineApp). */
internal val tasmeeRangeRequest = androidx.compose.runtime.mutableStateOf(false)

/** Tasmee range, same options as quick navigation. Starts where tasmee would start now and stops after the chosen range. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TasmeeRangeSheet(
    state: QuranUiState,
    surah: Int,
    ayah: Int,
    onDismiss: () -> Unit,
    onScope: (ReaderScope) -> Unit,
    onCustom: (Int, Int, Int, Int) -> Unit,
    /** v24.4: when set, adds page-range and juz-range entry rows (byJuz, from, to). */
    onNumberRange: ((Boolean, Int, Int) -> Unit)? = null,
) {
    var numberRange by remember { mutableStateOf<Boolean?>(null) }
    val md = state.metadata?.ayah(surah, ayah)
    val suraName = state.metadata?.sura(surah)?.arabicName ?: "$surah"
    val quarterInHizb = md?.let { ((it.quarter - 1) % 4) + 1 } ?: 1
    val hizb = md?.let { (it.quarter - 1) / 4 + 1 } ?: 1
    var custom by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text("تسميع من سورة $suraName آية $ayah إلى؟", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = Color(0xFF66736C))
            @Composable fun row(label: String, onClick: () -> Unit) {
                Text(label, Modifier.fillMaxWidth().clickable { onDismiss(); onClick() }.padding(horizontal = 24.dp, vertical = 14.dp), fontSize = 17.sp)
            }
            row("آية $ayah فقط") { onScope(ReaderScope.AYAH) }
            row("نهاية الصفحة ${md?.page ?: state.currentPage}") { onScope(ReaderScope.PAGES) }
            row("نهاية سورة $suraName") { onScope(ReaderScope.SURAH) }
            row("نهاية الجزء ${md?.juz ?: 1}") { onScope(ReaderScope.JUZ) }
            row("نهاية الربع $quarterInHizb الحزب $hizb") { onScope(ReaderScope.HIZB) }
            row("المصحف كاملاً (بلا حد)") { onScope(ReaderScope.QURAN) }
            HorizontalDivider()
            if (onNumberRange != null) {
                Text("نطاق صفحات (من صفحة إلى صفحة)", Modifier.fillMaxWidth().clickable { numberRange = false }.padding(horizontal = 24.dp, vertical = 14.dp), fontSize = 17.sp)
                Text("نطاق أجزاء (من جزء إلى جزء)", Modifier.fillMaxWidth().clickable { numberRange = true }.padding(horizontal = 24.dp, vertical = 14.dp), fontSize = 17.sp)
            }
            Row(Modifier.fillMaxWidth().clickable { custom = true }.padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, null)
                Spacer(Modifier.width(16.dp))
                Text("مخصص (من سورة/آية إلى سورة/آية)", fontSize = 17.sp)
            }
        }
    }
    numberRange?.let { byJuz ->
        var f by remember { mutableStateOf("") }
        var t by remember { mutableStateOf("") }
        val fi = f.toIntOrNull(); val ti = t.toIntOrNull()
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { numberRange = null },
            title = { Text(if (byJuz) "تسميع من جزء إلى جزء" else "تسميع من صفحة إلى صفحة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedTextField(value = f, onValueChange = { f = it.filter(Char::isDigit).take(3) }, singleLine = true, label = { Text(if (byJuz) "من الجزء (1-30)" else "من الصفحة (1-604)") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                    androidx.compose.material3.OutlinedTextField(value = t, onValueChange = { t = it.filter(Char::isDigit).take(3) }, singleLine = true, label = { Text(if (byJuz) "إلى الجزء (1-30)" else "إلى الصفحة (1-604)") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(enabled = fi != null && ti != null, onClick = { numberRange = null; onDismiss(); onNumberRange?.invoke(byJuz, fi!!, ti!!) }) { Text("حدّد") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { numberRange = null }) { Text("إلغاء") } },
        )
    }
    if (custom) CustomListenDialog(state, surah, ayah, title = "تسميع مخصص", goLabel = "ابدأ", withRepeat = false, onDismiss = { custom = false }, onGo = { a, b, c, d, _ -> custom = false; onDismiss(); onCustom(a, b, c, d) })
}
