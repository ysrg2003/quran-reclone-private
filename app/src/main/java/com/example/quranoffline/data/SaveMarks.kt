package com.example.quranoffline.data

import android.content.Context

/** HIFZ = tasmee/memorization. READ = reading and listening to a reciter. The two are kept apart. */
enum class SaveMarkKind { HIFZ, READ }

data class SaveMark(
    val id: String,
    val kind: SaveMarkKind,
    val surah: Int,
    val ayah: Int,
    val page: Int,
    val wordIndex: Int,
    val startedAt: Long,
    val updatedAt: Long,
)

/** Pure logic (no Android, no org.json) so it is unit-testable. */
object SaveMarkLogic {
    const val KEEP_PER_KIND = 3
    const val SESSION_GAP_MS = 30L * 60_000L

    /** Same session (gap under 30 min) updates the newest mark of that kind; otherwise a new mark. Keeps the last 3 per kind. */
    fun upsert(
        marks: List<SaveMark>,
        kind: SaveMarkKind,
        surah: Int,
        ayah: Int,
        page: Int,
        wordIndex: Int,
        now: Long,
        newId: String,
    ): List<SaveMark> {
        val mine = marks.filter { it.kind == kind }.sortedByDescending { it.updatedAt }
        val others = marks.filter { it.kind != kind }
        val latest = mine.firstOrNull()
        val updated = if (latest != null && now - latest.updatedAt in 0..SESSION_GAP_MS) {
            listOf(latest.copy(surah = surah, ayah = ayah, page = page, wordIndex = wordIndex, updatedAt = now)) + mine.drop(1)
        } else {
            listOf(SaveMark(newId, kind, surah, ayah, page, wordIndex, now, now)) + mine
        }
        return others + updated.take(KEEP_PER_KIND)
    }

    fun forKind(marks: List<SaveMark>, kind: SaveMarkKind) = marks.filter { it.kind == kind }.sortedByDescending { it.updatedAt }

    fun encode(marks: List<SaveMark>): String = marks.joinToString("\n") {
        listOf(it.id, it.kind.name, it.surah, it.ayah, it.page, it.wordIndex, it.startedAt, it.updatedAt).joinToString("\t")
    }

    fun decode(raw: String?): List<SaveMark> = raw.orEmpty().lines().mapNotNull { line ->
        val p = line.split('\t')
        if (p.size != 8) return@mapNotNull null
        runCatching {
            SaveMark(p[0], SaveMarkKind.valueOf(p[1]), p[2].toInt(), p[3].toInt(), p[4].toInt(), p[5].toInt(), p[6].toLong(), p[7].toLong())
        }.getOrNull()
    }
}

class LocalSaveMarkStore(context: Context) {
    private val prefs = context.getSharedPreferences("save_marks", Context.MODE_PRIVATE)
    fun list(): List<SaveMark> = SaveMarkLogic.decode(prefs.getString("marks", null))
    fun save(marks: List<SaveMark>) { prefs.edit().putString("marks", SaveMarkLogic.encode(marks)).apply() }
}
