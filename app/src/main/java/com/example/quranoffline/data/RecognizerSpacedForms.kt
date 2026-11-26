package com.example.quranoffline.data

/**
 * Speech-to-text writes a few Quran words as two tokens (يا أيها, يا قوم, ها أنتم).
 * Consecutive recognizer tokens that form one of those known standard spellings
 * are joined into the single token the matcher compares with the Quran word.
 */
object RecognizerSpacedForms {
    @Volatile private var pairs: Set<String> = emptySet()

    private fun fold(value: String): String = value
        .replace('ٱ', 'ا').replace('آ', 'ا').replace('أ', 'ا').replace('إ', 'ا')
        .replace('ؤ', 'و').replace('ئ', 'ي').replace('ى', 'ي').replace("ء", "")
        .replace(Regex("[\\u0610-\\u061a\\u064b-\\u065f\\u06d6-\\u06ed\\u0670\\u0640]"), "")
        .trim()

    fun register(forms: Collection<String>) {
        pairs = forms.filter { it.split(' ').size in 2..3 && it.substringBefore(' ') in setOf("يا", "ويا", "ها", "فيا") }.map { fold(it) }.toSet()
    }

    fun join(tokens: List<String>): List<String> {
        if (tokens.size < 2 || pairs.isEmpty()) return tokens
        val result = ArrayList<String>(tokens.size)
        var i = 0
        while (i < tokens.size) {
            var n = minOf(3, tokens.size - i)
            while (n >= 2) {
                val key = (0 until n).joinToString(" ") { fold(tokens[i + it]) }
                if (key in pairs) break
                n--
            }
            if (n >= 2) {
                result += (0 until n).joinToString(" ") { tokens[i + it] }
                i += n
            } else {
                result += tokens[i]
                i++
            }
        }
        return result
    }
}
