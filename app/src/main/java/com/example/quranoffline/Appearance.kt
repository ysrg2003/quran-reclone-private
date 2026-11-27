package com.example.quranoffline

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** v24: user-chosen app colours and tab order. Stored once in SharedPreferences, read at launch; tasmee green/red/yellow rules are not part of it. */
data class AppearanceSpec(
    val preset: String = "green",
    val accent: Long? = null,
    val paper: Long? = null,
    val text: Long? = null,
    val tabOrder: List<Int> = DEFAULT_TAB_ORDER,
) {
    companion object {
        /** Tab ids shown in the tab row. Id 5 (settings) lives behind the gear only. */
        /** v24.5: Juz (2) first. */
        val DEFAULT_TAB_ORDER = listOf(2, 0, 1, 3, 4, 6)
        /** Default up to v24.4. A saved order equal to this was never reordered by the user, so it moves to the new default. */
        val LEGACY_DEFAULT_TAB_ORDER = listOf(0, 1, 2, 3, 4, 6)
        val TAB_TITLES = mapOf(0 to "الحفظ والتسميع", 1 to "السور", 2 to "الأجزاء", 3 to "السجل", 4 to "القراءة", 6 to "الختمات")
    }
}

class AppearancePreset(val id: String, val title: String, val accent: Long, val paper: Long, val text: Long)

val APPEARANCE_PRESETS = listOf(
    AppearancePreset("green", "أخضر هادئ (الافتراضي)", 0xFF276456, 0xFFFFFEF8, 0xFF183D33),
    AppearancePreset("blue", "أزرق", 0xFF1F4E8C, 0xFFF8FBFF, 0xFF16263D),
    AppearancePreset("brown", "بني", 0xFF7A4A21, 0xFFF3E7CF, 0xFF3B2A1A),
    AppearancePreset("purple", "بنفسجي", 0xFF5B3A8C, 0xFFFFFBFF, 0xFF2A1B3F),
    AppearancePreset("graphite", "رمادي", 0xFF37474F, 0xFFEDEDED, 0xFF1F2A2E),
    AppearancePreset("night", "ليلي", 0xFF1B2B27, 0xFF14201C, 0xFFE8EFE9),
)

/** The colours the UI reads. Pure function of the spec, so it is cheap to test. */
class Appearance(
    val primary: Color,
    val chip: Color,
    /** Darker shade of the accent for the two green boxes of the tasmee tab and the reader card. Default (v24.2 calmer green 0xFF276456) gives about 0xFF1F5045. */
    val deep: Color,
    val tabUnselected: Color,
    /** Light tint of the accent: selected chips, containers, tonal buttons (replaces the default lavender). */
    val container: Color,
    /** Very light tint: field backgrounds and variant surfaces. */
    val containerLow: Color,
    /** Mid tone for outlines and disabled borders, readable on white. */
    val outline: Color,
    val paper: Color,
    val readerText: Color,
    val darkPaper: Boolean,
    val selectedHighlight: Color,
    val playingHighlight: Color,
)

fun AppearanceSpec.resolve(): Appearance {
    val p = APPEARANCE_PRESETS.firstOrNull { it.id == preset } ?: APPEARANCE_PRESETS[0]
    val primary = Color(accent ?: p.accent)
    val paper = Color(paper ?: p.paper)
    val dark = paper.luminance() < 0.4f
    // A free paper colour without a free text colour gets a readable text colour automatically.
    val textColor = when {
        text != null -> Color(text)
        this.paper != null -> if (dark) Color(0xFFE8EFE9) else Color(0xFF183D33)
        else -> Color(p.text)
    }
    fun mix(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)
    return Appearance(
        primary = primary,
        chip = mix(primary, Color.White, 0.12f),
        deep = mix(primary, Color.Black, 0.2f),
        tabUnselected = mix(primary, Color.White, 0.75f),
        container = mix(primary, Color.White, 0.82f),
        containerLow = mix(primary, Color.White, 0.94f),
        outline = mix(primary, Color(0xFF66736C), 0.55f),
        paper = paper,
        readerText = textColor,
        darkPaper = dark,
        selectedHighlight = if (dark) Color(0xFF6B5A2E) else Color(0xFFFFE0A6),
        playingHighlight = if (dark) Color(0xFF2F5A47) else Color(0xFFBFE3CF),
    )
}

object AppearanceStore {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("quran_appearance", Context.MODE_PRIVATE)
    private const val KEY_JUZ_FIRST_V246 = "tab_order_juz_first_v246"

    fun normalizeOrder(raw: List<Int>): List<Int> {
        val want = AppearanceSpec.DEFAULT_TAB_ORDER
        return if (raw.size == want.size && raw.toSet() == want.toSet()) raw else want
    }

    fun load(c: Context): AppearanceSpec {
        val p = prefs(c)
        fun opt(k: String): Long? = if (p.contains(k)) p.getLong(k, 0L) else null
        val stored = normalizeOrder(p.getString("tab_order", null)?.split(',')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty())
        var migrated = if (stored == AppearanceSpec.LEGACY_DEFAULT_TAB_ORDER) AppearanceSpec.DEFAULT_TAB_ORDER else stored
        // v24.6: Juz must be the first tab and the tab the app opens on, on every install, including
        // upgrades that still carry an older saved order (a saved order was overriding the new default).
        // This runs once; after that a deliberate reorder is kept.
        if (!p.getBoolean(KEY_JUZ_FIRST_V246, false)) {
            migrated = listOf(2) + migrated.filter { it != 2 }
            p.edit().putBoolean(KEY_JUZ_FIRST_V246, true).putString("tab_order", migrated.joinToString(",")).apply()
        }
        return AppearanceSpec(p.getString("preset", "green") ?: "green", opt("accent"), opt("paper"), opt("text"), migrated)
    }

    fun save(c: Context, s: AppearanceSpec) {
        val e = prefs(c).edit().putString("preset", s.preset).putString("tab_order", normalizeOrder(s.tabOrder).joinToString(","))
        listOf("accent" to s.accent, "paper" to s.paper, "text" to s.text).forEach { (k, v) -> if (v == null) e.remove(k) else e.putLong(k, v) }
        e.apply()
    }
}

val LocalAppearance = staticCompositionLocalOf { AppearanceSpec().resolve() }
val LocalAppearanceSpec = staticCompositionLocalOf { AppearanceSpec() }
val LocalAppearanceEdit = staticCompositionLocalOf<(AppearanceSpec) -> Unit> { {} }

@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(color)
            .border(if (selected) 3.dp else 1.dp, if (selected) Color(0xFFF2C879) else Color(0xFFB8C2BD), CircleShape)
            .clickable(onClick = onClick),
    )
}

/** Free colour: hue, saturation and brightness sliders. The theme changes when the finger is lifted, not on every tick. */
@Composable
private fun FreeColorRow(title: String, current: Long?, fallback: Color, onChange: (Long?) -> Unit) {
    val base = Color(current ?: fallback.toArgb().toLong() and 0xFFFFFFFFL)
    val hsv = remember(current) { FloatArray(3).also { android.graphics.Color.colorToHSV(base.toArgb(), it) } }
    var h by remember(current) { mutableFloatStateOf(hsv[0]) }
    var s by remember(current) { mutableFloatStateOf(hsv[1]) }
    var v by remember(current) { mutableFloatStateOf(hsv[2]) }
    val live = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))
    fun commit() { onChange(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)).toLong() and 0xFFFFFFFFL) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(live).border(1.dp, Color(0xFFB8C2BD), RoundedCornerShape(8.dp)))
            if (current != null) TextButton(onClick = { onChange(null) }) { Text("إعادة") }
        }
        Slider(value = h, onValueChange = { h = it }, onValueChangeFinished = { commit() }, valueRange = 0f..360f, modifier = Modifier.height(28.dp))
        Slider(value = s, onValueChange = { s = it }, onValueChangeFinished = { commit() }, valueRange = 0f..1f, modifier = Modifier.height(28.dp))
        Slider(value = v, onValueChange = { v = it }, onValueChangeFinished = { commit() }, valueRange = 0f..1f, modifier = Modifier.height(28.dp))
    }
}

@Composable
fun AppearanceSettingsCard() {
    val spec = LocalAppearanceSpec.current
    val edit = LocalAppearanceEdit.current
    val res = LocalAppearance.current
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("ألوان التطبيق", fontWeight = FontWeight.Bold, color = Color(0xFF123F35))
            Text("لا تغيّر ألوان التسميع (أخضر صحيح، أحمر خطأ، أصفر غير مؤكد).", style = MaterialTheme.typography.bodySmall, color = Color(0xFF66736C))
            Text("نمط جاهز", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                APPEARANCE_PRESETS.forEach { p ->
                    Swatch(Color(p.accent), spec.preset == p.id && spec.accent == null && spec.paper == null && spec.text == null) {
                        edit(spec.copy(preset = p.id, accent = null, paper = null, text = null))
                    }
                }
            }
            Text(APPEARANCE_PRESETS.firstOrNull { it.id == spec.preset }?.title ?: "", style = MaterialTheme.typography.bodySmall, color = Color(0xFF66736C))
            Text("لون حر", fontWeight = FontWeight.SemiBold)
            FreeColorRow("لون التطبيق (الأشرطة والتبويبات)", spec.accent, res.primary) { edit(spec.copy(accent = it)) }
            FreeColorRow("لون ورقة المصحف", spec.paper, res.paper) { edit(spec.copy(paper = it)) }
            FreeColorRow("لون نص المصحف", spec.text, res.readerText) { edit(spec.copy(text = it)) }
            Text("يغطي: الأشرطة العلوية والتبويبات والإعدادات، وخلفية القارئ ونص الآيات في القراءة والحفظ. بقية البطاقات تبقى بيضاء.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF66736C))
            OutlinedButton(onClick = { edit(spec.copy(preset = "green", accent = null, paper = null, text = null)) }, modifier = Modifier.fillMaxWidth()) { Text("إعادة الألوان الافتراضية") }
        }
    }
}

@Composable
fun TabOrderSettingsCard() {
    val spec = LocalAppearanceSpec.current
    val edit = LocalAppearanceEdit.current
    val order = spec.tabOrder
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("ترتيب التبويبات", fontWeight = FontWeight.Bold, color = Color(0xFF123F35))
            order.forEachIndexed { i, id ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("${i + 1}. ${AppearanceSpec.TAB_TITLES[id].orEmpty()}", modifier = Modifier.weight(1f))
                    TextButton(enabled = i > 0, onClick = { edit(spec.copy(tabOrder = order.toMutableList().also { java.util.Collections.swap(it, i, i - 1) })) }) { Text("▲") }
                    TextButton(enabled = i < order.lastIndex, onClick = { edit(spec.copy(tabOrder = order.toMutableList().also { java.util.Collections.swap(it, i, i + 1) })) }) { Text("▼") }
                }
            }
            OutlinedButton(onClick = { edit(spec.copy(tabOrder = AppearanceSpec.DEFAULT_TAB_ORDER)) }, modifier = Modifier.fillMaxWidth()) { Text("إعادة الترتيب الافتراضي") }
        }
    }
}

/** Colour scheme for the whole app: every Material role derives from the accent, so no component falls back to the default lavender. */
fun Appearance.colorScheme() = androidx.compose.material3.lightColorScheme(
    primary = primary, onPrimary = Color.White,
    primaryContainer = container, onPrimaryContainer = deep,
    secondary = primary, onSecondary = Color.White,
    secondaryContainer = container, onSecondaryContainer = deep,
    tertiary = Color(0xFF8A5A14), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF6E3BE), onTertiaryContainer = Color(0xFF4A3000),
    background = Color(0xFFF7FAF8), onBackground = Color(0xFF17231F),
    surface = Color.White, onSurface = Color(0xFF17231F),
    surfaceVariant = containerLow, onSurfaceVariant = Color(0xFF3F4F48),
    surfaceTint = primary,
    outline = outline, outlineVariant = container,
    inverseSurface = deep, inverseOnSurface = Color.White, inversePrimary = container,
    surfaceContainerLowest = Color.White, surfaceContainerLow = containerLow, surfaceContainer = containerLow,
    surfaceContainerHigh = container, surfaceContainerHighest = container,
)

/** Component colours for the dark accent cards (tab 0, reader card): readable selected, unselected, disabled and hint states. */
object OnDeep {
    private val Tan = Color(0xFFF2C879)
    private val Ink = Color(0xFF183D33)
    @Composable fun chipColors() = androidx.compose.material3.FilterChipDefaults.filterChipColors(
        containerColor = Color.Transparent, labelColor = Color.White, iconColor = Color.White,
        selectedContainerColor = Tan, selectedLabelColor = Ink, selectedLeadingIconColor = Ink,
        disabledContainerColor = Color.Transparent, disabledLabelColor = Color.White.copy(alpha = 0.55f),
    )
    @Composable fun chipBorder(selected: Boolean, enabled: Boolean = true) = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
        enabled = enabled, selected = selected,
        borderColor = Color.White.copy(alpha = 0.8f), selectedBorderColor = Color.Transparent,
        disabledBorderColor = Color.White.copy(alpha = 0.35f),
    )
    @Composable fun fieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White, unfocusedTextColor = Color.White, disabledTextColor = Color.White.copy(alpha = 0.6f),
        focusedBorderColor = Tan, unfocusedBorderColor = Color.White.copy(alpha = 0.75f), disabledBorderColor = Color.White.copy(alpha = 0.35f),
        focusedLabelColor = Tan, unfocusedLabelColor = Color(0xFFD6EEE5), disabledLabelColor = Color.White.copy(alpha = 0.6f),
        focusedPlaceholderColor = Color.White.copy(alpha = 0.7f), unfocusedPlaceholderColor = Color.White.copy(alpha = 0.7f), disabledPlaceholderColor = Color.White.copy(alpha = 0.5f),
        cursorColor = Color.White,
    )
    @Composable fun buttonColors() = androidx.compose.material3.ButtonDefaults.buttonColors(
        containerColor = Tan, contentColor = Ink,
        disabledContainerColor = Color.White.copy(alpha = 0.2f), disabledContentColor = Color.White.copy(alpha = 0.8f),
    )
    @Composable fun outlinedColors() = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
        contentColor = Color.White, disabledContentColor = Color.White.copy(alpha = 0.6f),
    )
    fun outlinedBorder(enabled: Boolean = true) = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = if (enabled) 0.78f else 0.35f))
}
