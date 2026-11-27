package com.example.quranoffline.data

import android.content.Context
import java.util.BitSet

/** Khatmat are tracked per kind: HIFZ (tasmee/hifz) and READ (reciters/reading). */
data class KhatmaState(
    val kind: SaveMarkKind,
    val covered: BitSet = BitSet(KhatmaLogic.TOTAL),
    val completed: Int = 0,
    val lastIndex: Int = -1,
    val dayKey: String = "",
    val today: BitSet = BitSet(KhatmaLogic.TOTAL),
    val dailyTarget: Int = 0,
    val reminderOn: Boolean = false,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    /** Time of the first ayah recorded in the current khatma (0 = not started). Used for the finish estimate. */
    val startMillis: Long = 0L,
    /** Unit of dailyTarget: 0 ayahs, 1 pages, 2 surahs. */
    val targetUnit: Int = 0,
    /** Repeat the reminder every N minutes (N and the count are the user's choice) while today's wird is not done; 0 = off. */
    val repeatMinutes: Int = 0,
    /** How many times the reminder repeats (user's choice, 1..50). */
    val repeatCount: Int = 3,
) {
    val coveredCount: Int get() = covered.cardinality()
    val todayCount: Int get() = today.cardinality()
    /** dailyTarget in ayahs. Pages and surahs are averages (6236/604 and 6236/114), so they are approximate. */
    val targetAyahs: Int get() = KhatmaLogic.targetInAyahs(dailyTarget, targetUnit)
    val wirdDone: Boolean get() = if (targetAyahs > 0) todayCount >= targetAyahs else todayCount > 0
}

/** Pure logic, JVM-testable. Ayah indices are global mushaf positions 0..6235. */
object KhatmaLogic {
    const val TOTAL = 6236
    const val AYAHS_PER_PAGE = 6236.0 / 604.0
    const val AYAHS_PER_SURAH = 6236.0 / 114.0

    fun targetInAyahs(value: Int, unit: Int): Int = when (unit) {
        1 -> Math.ceil(value * AYAHS_PER_PAGE).toInt()
        2 -> Math.ceil(value * AYAHS_PER_SURAH).toInt()
        else -> value
    }

    fun record(state: KhatmaState, indices: Collection<Int>, dayKey: String, nowMillis: Long = 0L): KhatmaState {
        val valid = indices.filter { it in 0 until TOTAL }
        if (valid.isEmpty()) return state
        val today = if (state.dayKey == dayKey) state.today.clone() as BitSet else BitSet(TOTAL)
        val covered = state.covered.clone() as BitSet
        valid.forEach { covered.set(it); today.set(it) }
        var completed = state.completed
        var out = covered
        if (covered.cardinality() >= TOTAL) { completed += 1; out = BitSet(TOTAL) }
        val start = if (completed > state.completed) 0L else if (state.startMillis == 0L && nowMillis > 0L) nowMillis else state.startMillis
        return state.copy(covered = out, completed = completed, lastIndex = valid.last(), dayKey = dayKey, today = today, startMillis = start)
    }

    /** remaining ayahs, pace so far (ayahs/day, calendar days since start, today counted), days to finish at that pace and at the daily target. */
    data class Estimate(val remaining: Int, val pacePerDay: Double, val daysByPace: Int?, val daysByTarget: Int?)

    fun estimate(s: KhatmaState, nowMillis: Long): Estimate {
        val covered = s.coveredCount
        val remaining = (TOTAL - covered).coerceAtLeast(0)
        val dayMs = 24L * 60 * 60 * 1000
        val days = if (s.startMillis <= 0L) 1L else ((nowMillis - s.startMillis) / dayMs + 1).coerceAtLeast(1L)
        val pace = if (covered > 0 && s.startMillis > 0L) covered.toDouble() / days else 0.0
        val byPace = if (pace > 0.0) Math.ceil(remaining / pace).toInt() else null
        val byTarget = if (s.targetAyahs > 0) Math.ceil(remaining.toDouble() / s.targetAyahs).toInt() else null
        return Estimate(remaining, pace, byPace, byTarget)
    }

    private const val HEX = "0123456789abcdef"

    fun encode(bits: BitSet): String {
        val bytes = bits.toByteArray()
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) { val v = bytes[i].toInt() and 0xff; out[i * 2] = HEX[v shr 4]; out[i * 2 + 1] = HEX[v and 15] }
        return String(out)
    }

    fun decode(hex: String?): BitSet {
        if (hex.isNullOrEmpty() || hex.length % 2 != 0) return BitSet(TOTAL)
        return runCatching { BitSet.valueOf(ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }) }.getOrDefault(BitSet(TOTAL))
    }

    fun dayKey(millis: Long): String {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = millis }
        return "${c.get(java.util.Calendar.YEAR)}-${c.get(java.util.Calendar.DAY_OF_YEAR)}"
    }
}

class LocalKhatmaStore(context: Context) {
    private val prefs = context.getSharedPreferences("khatmat", Context.MODE_PRIVATE)

    fun load(kind: SaveMarkKind): KhatmaState {
        val k = kind.name
        val today = KhatmaLogic.decode(prefs.getString("$k.today", null))
        val key = prefs.getString("$k.dayKey", "") ?: ""
        return KhatmaState(
            kind = kind,
            covered = KhatmaLogic.decode(prefs.getString("$k.covered", null)),
            completed = prefs.getInt("$k.completed", 0),
            lastIndex = prefs.getInt("$k.last", -1),
            dayKey = key,
            today = if (key == KhatmaLogic.dayKey(System.currentTimeMillis())) today else BitSet(KhatmaLogic.TOTAL),
            dailyTarget = prefs.getInt("$k.target", 0),
            reminderOn = prefs.getBoolean("$k.remOn", false),
            reminderHour = prefs.getInt("$k.remH", 20),
            reminderMinute = prefs.getInt("$k.remM", 0),
            startMillis = prefs.getLong("$k.start", 0L),
            targetUnit = prefs.getInt("$k.unit", 0),
            repeatMinutes = prefs.getInt("$k.rep", 0),
            repeatCount = prefs.getInt("$k.cnt", 3).coerceIn(1, 50),
        )
    }

    fun save(s: KhatmaState) {
        val k = s.kind.name
        prefs.edit()
            .putString("$k.covered", KhatmaLogic.encode(s.covered))
            .putInt("$k.completed", s.completed)
            .putInt("$k.last", s.lastIndex)
            .putString("$k.dayKey", s.dayKey)
            .putString("$k.today", KhatmaLogic.encode(s.today))
            .putInt("$k.target", s.dailyTarget)
            .putBoolean("$k.remOn", s.reminderOn)
            .putInt("$k.remH", s.reminderHour)
            .putInt("$k.remM", s.reminderMinute)
            .putLong("$k.start", s.startMillis)
            .putInt("$k.unit", s.targetUnit)
            .putInt("$k.rep", s.repeatMinutes)
            .putInt("$k.cnt", s.repeatCount)
            .apply()
    }
}
