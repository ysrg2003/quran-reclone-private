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
) {
    val coveredCount: Int get() = covered.cardinality()
    val todayCount: Int get() = today.cardinality()
}

/** Pure logic, JVM-testable. Ayah indices are global mushaf positions 0..6235. */
object KhatmaLogic {
    const val TOTAL = 6236

    fun record(state: KhatmaState, indices: Collection<Int>, dayKey: String): KhatmaState {
        val valid = indices.filter { it in 0 until TOTAL }
        if (valid.isEmpty()) return state
        val today = if (state.dayKey == dayKey) state.today.clone() as BitSet else BitSet(TOTAL)
        val covered = state.covered.clone() as BitSet
        valid.forEach { covered.set(it); today.set(it) }
        var completed = state.completed
        var out = covered
        if (covered.cardinality() >= TOTAL) { completed += 1; out = BitSet(TOTAL) }
        return state.copy(covered = out, completed = completed, lastIndex = valid.last(), dayKey = dayKey, today = today)
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
            .apply()
    }
}
