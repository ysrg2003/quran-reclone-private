package com.example.quranoffline.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One named khatma. [kind] keeps recitation tracking kind-keyed: the active record of a kind receives progress. */
data class KhatmaRecord(val id: String, val name: String, val kind: SaveMarkKind, val state: KhatmaState)

/** Pure list and serialisation logic (JVM-testable). The Android store below only does prefs and migration. */
object KhatmaListLogic {
    const val DEFAULT_HIFZ_NAME = "ختمة التسميع والحفظ"
    const val DEFAULT_READ_NAME = "ختمة القراءة والاستماع"

    fun defaultName(kind: SaveMarkKind) = if (kind == SaveMarkKind.HIFZ) DEFAULT_HIFZ_NAME else DEFAULT_READ_NAME

    fun toJson(r: KhatmaRecord): JSONObject = JSONObject().apply {
        put("id", r.id); put("name", r.name); put("kind", r.kind.name)
        put("covered", KhatmaLogic.encode(r.state.covered))
        put("completed", r.state.completed)
        put("last", r.state.lastIndex)
        put("dayKey", r.state.dayKey)
        put("today", KhatmaLogic.encode(r.state.today))
        put("target", r.state.dailyTarget)
        put("remOn", r.state.reminderOn)
        put("remH", r.state.reminderHour)
        put("remM", r.state.reminderMinute)
        put("start", r.state.startMillis)
        put("unit", r.state.targetUnit)
        put("rep", r.state.repeatMinutes)
        put("cnt", r.state.repeatCount)
    }

    fun fromJson(o: JSONObject): KhatmaRecord {
        val kind = runCatching { SaveMarkKind.valueOf(o.getString("kind")) }.getOrDefault(SaveMarkKind.HIFZ)
        return KhatmaRecord(
            id = o.getString("id"),
            name = o.optString("name", "").ifBlank { defaultName(kind) },
            kind = kind,
            state = KhatmaState(
                kind = kind,
                covered = KhatmaLogic.decode(o.optString("covered", null)),
                completed = o.optInt("completed", 0),
                lastIndex = o.optInt("last", -1),
                dayKey = o.optString("dayKey", ""),
                today = KhatmaLogic.decode(o.optString("today", null)),
                dailyTarget = o.optInt("target", 0),
                reminderOn = o.optBoolean("remOn", false),
                reminderHour = o.optInt("remH", 20),
                reminderMinute = o.optInt("remM", 0),
                startMillis = o.optLong("start", 0L),
                targetUnit = o.optInt("unit", 0),
                repeatMinutes = o.optInt("rep", 0),
                repeatCount = o.optInt("cnt", 3).coerceIn(1, 50),
            ),
        )
    }

    fun encodeList(records: List<KhatmaRecord>): String = JSONArray().apply { records.forEach { put(toJson(it)) } }.toString()

    fun decodeList(raw: String?): List<KhatmaRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> runCatching { fromJson(arr.getJSONObject(i)) }.getOrNull() }
    }

    /** The record that receives progress for [kind]: the stored active id if it is still a record of that kind, else the first one of that kind. */
    fun activeFor(records: List<KhatmaRecord>, kind: SaveMarkKind, activeId: String?): KhatmaRecord? =
        records.firstOrNull { it.id == activeId && it.kind == kind } ?: records.firstOrNull { it.kind == kind }

    /** Returns null when the delete is refused: the last record of a kind must stay, tracking needs one. */
    fun delete(records: List<KhatmaRecord>, id: String): List<KhatmaRecord>? {
        val target = records.firstOrNull { it.id == id } ?: return records
        if (records.count { it.kind == target.kind } <= 1) return null
        return records.filterNot { it.id == id }
    }

    fun replace(records: List<KhatmaRecord>, record: KhatmaRecord): List<KhatmaRecord> =
        if (records.any { it.id == record.id }) records.map { if (it.id == record.id) record else it }
        else records + record

    /** One record per existing kind, carrying the old state verbatim. Ids are supplied by the caller. */
    fun migrate(old: List<KhatmaState>, newId: (Int) -> String): List<KhatmaRecord> =
        old.mapIndexed { index, state -> KhatmaRecord(newId(index), defaultName(state.kind), state.kind, state) }
}

/**
 * Named khatmat in prefs "khatmat_v2". The first load migrates the two kind-keyed khatmat of v24.6 and
 * leaves the old "khatmat" prefs untouched, so a rollback to v24.6 still finds its data.
 */
class LocalKhatmaListStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("khatmat_v2", Context.MODE_PRIVATE)

    fun load(): List<KhatmaRecord> {
        migrateIfNeeded()
        return KhatmaListLogic.decodeList(prefs.getString("records", null))
    }

    fun saveAll(records: List<KhatmaRecord>) {
        prefs.edit().putString("records", KhatmaListLogic.encodeList(records)).apply()
    }

    fun activeId(kind: SaveMarkKind): String? = prefs.getString("active_${kind.name}", null)

    fun setActive(kind: SaveMarkKind, id: String) {
        prefs.edit().putString("active_${kind.name}", id).apply()
    }

    fun active(kind: SaveMarkKind): KhatmaRecord? = KhatmaListLogic.activeFor(load(), kind, activeId(kind))

    /** Saves [state] into the active record of its kind, creating one if the kind has none. */
    fun saveState(state: KhatmaState): List<KhatmaRecord> {
        val records = load()
        val active = KhatmaListLogic.activeFor(records, state.kind, activeId(state.kind))
            ?: KhatmaRecord(id = newId(state.kind), name = KhatmaListLogic.defaultName(state.kind), kind = state.kind, state = state)
        val updated = KhatmaListLogic.replace(records, active.copy(state = state))
        saveAll(updated)
        if (activeId(state.kind) == null) setActive(state.kind, active.id)
        return updated
    }

    /** Returns the new list, or null when the delete was refused (last record of its kind). */
    fun delete(id: String): List<KhatmaRecord>? {
        val records = load()
        val next = KhatmaListLogic.delete(records, id) ?: return null
        saveAll(next)
        SaveMarkKind.values().forEach { kind ->
            if (KhatmaListLogic.activeFor(next, kind, activeId(kind))?.id != activeId(kind)) {
                KhatmaListLogic.activeFor(next, kind, activeId(kind))?.let { setActive(kind, it.id) }
            }
        }
        return next
    }

    fun add(name: String, kind: SaveMarkKind, state: KhatmaState? = null): KhatmaRecord {
        val record = KhatmaRecord(
            id = newId(kind),
            name = name.ifBlank { KhatmaListLogic.defaultName(kind) },
            kind = kind,
            state = state ?: KhatmaState(kind = kind),
        )
        saveAll(load() + record)
        if (activeId(kind) == null) setActive(kind, record.id)
        return record
    }

    fun rename(id: String, name: String): List<KhatmaRecord> {
        val records = load()
        val next = records.map { if (it.id == id) it.copy(name = name.ifBlank { it.name }) else it }
        saveAll(next)
        return next
    }

    private fun newId(kind: SaveMarkKind) = "${kind.name.lowercase()}-${System.currentTimeMillis().toString(36)}-${(0..999).random()}"

    private fun migrateIfNeeded() {
        if (prefs.getBoolean("migrated", false)) return
        val old = LocalKhatmaStore(app)
        val states = SaveMarkKind.values().map { old.load(it) }
        val records = KhatmaListLogic.migrate(states) { index -> "legacy-$index" }
        val editor = prefs.edit().putString("records", KhatmaListLogic.encodeList(records))
        records.forEach { editor.putString("active_${it.kind.name}", it.id) }
        editor.putBoolean("migrated", true).apply()
    }
}
