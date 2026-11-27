package com.example.quranoffline.data

import android.content.Context

data class Reciter(val id: Int, val arabicName: String, val style: String?) {
    val label: String get() = if (style == null) arabicName else "$arabicName (${styleAr(style)})"
    private fun styleAr(s: String) = when (s) { "Murattal" -> "مرتل"; "Mujawwad" -> "مجوّد"; "Muallim" -> "معلّم"; else -> s }
}

/** The 12 recitations api.quran.com/api/v4/resources/recitations lists (checked live). Default stays id 6, as in v11. */
object Reciters {
    const val DEFAULT_ID = 6
    val all = listOf(
        Reciter(6, "محمود خليل الحصري", "Murattal"),
        Reciter(12, "محمود خليل الحصري", "Muallim"),
        Reciter(7, "مشاري راشد العفاسي", null),
        Reciter(3, "عبدالرحمن السديس", null),
        Reciter(10, "سعود الشريم", null),
        Reciter(4, "أبو بكر الشاطري", null),
        Reciter(5, "هاني الرفاعي", null),
        Reciter(11, "محمد الطبلاوي", null),
        Reciter(2, "عبد الباسط عبد الصمد", "Murattal"),
        Reciter(1, "عبد الباسط عبد الصمد", "Mujawwad"),
        Reciter(9, "محمد صديق المنشاوي", "Murattal"),
        Reciter(8, "محمد صديق المنشاوي", "Mujawwad"),
    )
    fun byId(id: Int) = all.firstOrNull { it.id == id } ?: all.first()

    @Volatile var currentId: Int = DEFAULT_ID
        private set
    @Volatile private var loaded = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        val saved = context.applicationContext.getSharedPreferences("quran_reciter", Context.MODE_PRIVATE).getInt("id", DEFAULT_ID)
        currentId = if (all.any { it.id == saved }) saved else DEFAULT_ID
        loaded = true
    }

    fun select(context: Context, id: Int) {
        require(all.any { it.id == id })
        context.applicationContext.getSharedPreferences("quran_reciter", Context.MODE_PRIVATE).edit().putInt("id", id).apply()
        currentId = id; loaded = true
    }
}
