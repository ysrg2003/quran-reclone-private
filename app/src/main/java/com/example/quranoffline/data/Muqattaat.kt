package com.example.quranoffline.data

/** The pronunciation classification used by the recitation matcher.
 *
 * This is a pronunciation aid, not a complete tajweed judge. The Quran word
 * remains one visual/reference token; these units are used only to understand
 * a reader who says the letter names separately.
 */
enum class MuqattaatMaddCategory {
    NONE,
    NATURAL_TWO_HARAKAT,
    LAZIM_HARFI_SIX_HARAKAT,
    LAZIM_HARFI_FOUR_OR_SIX_HARAKAT,
}

data class MuqattaatLetter(
    val letter: String,
    val shortName: String,
    val fullName: String,
    val spokenForms: Set<String>,
    val maddCategory: MuqattaatMaddCategory,
)

data class MuqattaatPronunciation(
    val visualToken: String,
    val matchToken: String,
    val units: List<MuqattaatLetter>,
) {
    val spokenNames: List<String> get() = units.map { it.shortName }
    val spokenText: String get() = spokenNames.joinToString(" ")
    val displayText: String get() = spokenNames.joinToString("، ")
}

/** Pure, deterministic mapping for the 29 occurrences in the canonical Quran. */
object Muqattaat {
    private const val ALIF = "ا"
    private const val LAM = "ل"
    private const val MIM = "م"
    private const val SAD = "ص"
    private const val KAF = "ك"
    private const val HA = "ه"
    private const val YA = "ي"
    private const val TA = "ط"
    private const val RA = "ر"
    private const val HAA = "ح"
    private const val AIN = "ع"
    private const val SIN = "س"
    private const val QAF = "ق"
    private const val NUN = "ن"

    private val letterDefinitions = mapOf(
        ALIF to MuqattaatLetter(ALIF, "ألف", "ألف", setOf("ألف", "الف", "ا"), MuqattaatMaddCategory.NONE),
        LAM to MuqattaatLetter(LAM, "لام", "لام", setOf("لام", "ل"), MuqattaatMaddCategory.LAZIM_HARFI_SIX_HARAKAT),
        MIM to MuqattaatLetter(MIM, "ميم", "ميم", setOf("ميم", "م"), MuqattaatMaddCategory.LAZIM_HARFI_SIX_HARAKAT),
        SAD to MuqattaatLetter(SAD, "صاد", "صاد", setOf("صاد", "ص"), MuqattaatMaddCategory.LAZIM_HARFI_SIX_HARAKAT),
        KAF to MuqattaatLetter(KAF, "كاف", "كاف", setOf("كاف", "ك"), MuqattaatMaddCategory.LAZIM_HARFI_SIX_HARAKAT),
        HA to MuqattaatLetter(HA, "ها", "هاء", setOf("ها", "هاء", "هَا"), MuqattaatMaddCategory.NATURAL_TWO_HARAKAT),
        YA to MuqattaatLetter(YA, "يا", "ياء", setOf("يا", "ياء", "يَا"), MuqattaatMaddCategory.NATURAL_TWO_HARAKAT),
        TA to MuqattaatLetter(TA, "طا", "طاء", setOf("طا", "طاء", "طَا"), MuqattaatMaddCategory.NATURAL_TWO_HARAKAT),
        RA to MuqattaatLetter(RA, "را", "راء", setOf("را", "راء", "رَا"), MuqattaatMaddCategory.NATURAL_TWO_HARAKAT),
        HAA to MuqattaatLetter(HAA, "حا", "حاء", setOf("حا", "حاء", "حَا"), MuqattaatMaddCategory.NATURAL_TWO_HARAKAT),
        AIN to MuqattaatLetter(AIN, "عين", "عين", setOf("عين"), MuqattaatMaddCategory.LAZIM_HARFI_FOUR_OR_SIX_HARAKAT),
        SIN to MuqattaatLetter(SIN, "سين", "سين", setOf("سين"), MuqattaatMaddCategory.LAZIM_HARFI_SIX_HARAKAT),
        QAF to MuqattaatLetter(QAF, "قاف", "قاف", setOf("قاف"), MuqattaatMaddCategory.LAZIM_HARFI_SIX_HARAKAT),
        NUN to MuqattaatLetter(NUN, "نون", "نون", setOf("نون"), MuqattaatMaddCategory.LAZIM_HARFI_SIX_HARAKAT),
    )

    private val visualTokenByMatchToken = mapOf(
        "الم" to "الٓمٓ",
        "المص" to "الٓمٓصٓ",
        "الر" to "الٓر",
        "المر" to "الٓمٓر",
        "كهيعص" to "كٓهيعٓصٓ",
        "طه" to "طه",
        "طسم" to "طسٓمٓ",
        "طس" to "طسٓ",
        "يس" to "يسٓ",
        "ص" to "صٓ",
        "حم" to "حمٓ",
        "عسق" to "عٓسٓقٓ",
        "ق" to "قٓ",
        "ن" to "نٓ",
    )

    private val sequenceLetters = mapOf(
        "الم" to listOf(ALIF, LAM, MIM),
        "المص" to listOf(ALIF, LAM, MIM, SAD),
        "الر" to listOf(ALIF, LAM, RA),
        "المر" to listOf(ALIF, LAM, MIM, RA),
        "كهيعص" to listOf(KAF, HA, YA, AIN, SAD),
        "طه" to listOf(TA, HA),
        "طسم" to listOf(TA, SIN, MIM),
        "طس" to listOf(TA, SIN),
        "يس" to listOf(YA, SIN),
        "ص" to listOf(SAD),
        "حم" to listOf(HAA, MIM),
        "عسق" to listOf(AIN, SIN, QAF),
        "ق" to listOf(QAF),
        "ن" to listOf(NUN),
    )

    private val pronunciations: Map<String, MuqattaatPronunciation> = sequenceLetters.mapValues { (matchToken, letters) ->
        MuqattaatPronunciation(
            visualToken = visualTokenByMatchToken.getValue(matchToken),
            matchToken = matchToken,
            units = letters.map { letter -> letterDefinitions.getValue(letter) },
        )
    }

    private val pronunciationByVisualToken = pronunciations.values.associateBy { it.visualToken }
    private val sortedSequences = pronunciations.values.sortedByDescending { it.units.size }

    fun pronunciationFor(word: QuranWord): MuqattaatPronunciation? = pronunciationFor(word.text, word.matchText)

    fun pronunciationFor(visualToken: String, matchToken: String = visualToken): MuqattaatPronunciation? =
        pronunciationByVisualToken[visualToken]

    fun allCanonicalSequences(): Set<String> = pronunciations.keys

    fun definitionForLetter(letter: String): MuqattaatLetter? = letterDefinitions[letter]

    /**
     * Joins a recognizer's separate letter names into one human-readable token.
     * Complete and partial known sequences are joined; ordinary Quran words are
     * untouched. This makes `ألف لام ميم` match the one visual token `الٓمٓ`.
     */
    fun coalesceSpeechTokens(tokens: List<String>): List<String> =
        RecognizerSpacedForms.join(coalesceNames(tokens))

    private fun coalesceNames(tokens: List<String>): List<String> {
        if (tokens.isEmpty()) return tokens
        val normalized = tokens.map(::normalizeSpeech)
        val result = mutableListOf<String>()
        var index = 0
        while (index < normalized.size) {
            val match = sortedSequences.firstNotNullOfOrNull { pronunciation ->
                val forms = pronunciation.units
                if (index + forms.size > normalized.size) return@firstNotNullOfOrNull null
                val slice = normalized.subList(index, index + forms.size)
                if (slice.indices.all { speechNameMatches(forms[it], slice[it]) }) {
                    pronunciation to forms.size
                } else null
            }
            if (match != null) {
                result += match.first.spokenText
                index += match.second
                continue
            }
            val single = letterDefinitions.values.firstOrNull { definition ->
                // "مم" is the ordinary word مِمَّ (86:5), not a stretched letter name.
                normalized[index] != "مم" && speechNameMatches(definition, normalized[index])
            }
            if (single != null) result += single.shortName else result += tokens[index]
            index++
        }
        return result
    }

    /** Matches one final recognizer token, including separately spoken names. */
    fun matches(word: QuranWord, observed: String): Boolean = matches(word.text, word.matchText, observed)

    fun matches(visualToken: String, matchToken: String, observed: String): Boolean {
        val pronunciation = pronunciationFor(visualToken, matchToken) ?: return false
        val observedForms = normalizeSpeech(observed).split(' ').filter(String::isNotBlank)
        val spoken = pronunciation.units.flatMap { it.spokenForms }.map(::normalizeSpeech).toSet()
        if (observedForms.size == pronunciation.units.size && observedForms.indices.all { speechNameMatches(pronunciation.units[it], observedForms[it]) }) return true
        return observedForms.size == 1 && canonicalLetters(observedForms.single()) == pronunciation.matchToken && observedForms.single() !in spoken
    }

    fun isPotentialPrefix(word: QuranWord, observed: String): Boolean {
        val pronunciation = pronunciationFor(word) ?: return false
        val observedForms = normalizeSpeech(observed).split(' ').filter(String::isNotBlank)
        if (observedForms.isEmpty() || observedForms.size >= pronunciation.units.size) return false
        return observedForms.indices.all { speechNameMatches(pronunciation.units[it], observedForms[it]) }
    }

    private fun speechNameMatches(definition: MuqattaatLetter, observed: String): Boolean {
        val normalizedObserved = normalizeSpeech(observed)
        return definition.spokenForms.any { form ->
            val expected = normalizeSpeech(form)
            normalizedObserved == expected || collapseLongVowelRepeats(normalizedObserved, expected) == expected || duplicatedOnsetWithMadd(normalizedObserved, expected) || stretchedFinalConsonant(normalizedObserved, expected)
        }
    }

    private fun collapseLongVowelRepeats(observed: String, expected: String): String {
        if (observed.isBlank() || expected.isBlank()) return observed
        val result = StringBuilder()
        var expectedIndex = 0
        var observedIndex = 0
        while (observedIndex < observed.length && expectedIndex < expected.length) {
            val current = observed[observedIndex]
            val target = expected[expectedIndex]
            if (current != target) return observed
            result.append(target)
            observedIndex++
            expectedIndex++
            if (target in "اوي") {
                while (observedIndex < observed.length && observed[observedIndex] == target) observedIndex++
            }
        }
        return if (observedIndex == observed.length && expectedIndex == expected.length) result.toString() else observed
    }

    /**
     * Some Arabic recognizers repeat the initial consonant while decoding a
     * long letter name, e.g. `مميييم`. Accept that narrow form only if the
     * remaining name matches after valid long-vowel collapse; ordinary Quran
     * words never use this path.
     */
    private fun duplicatedOnsetWithMadd(observed: String, expected: String): Boolean {
        if (observed.length <= expected.length || observed.firstOrNull() != expected.firstOrNull()) return false
        return collapseLongVowelRepeats(observed.drop(1), expected) == expected
    }

    /** Provider-compatible tail stretch for letter names only; ordinary words never use this path. */
    private fun stretchedFinalConsonant(observed: String, expected: String): Boolean {
        if (observed.length <= expected.length || !observed.startsWith(expected)) return false
        val tail = observed.drop(expected.length)
        return tail.isNotEmpty() && tail.all { it == expected.last() } && expected.last() !in "اوي"
    }

    private fun canonicalLetters(value: String): String = normalizeSpeech(value)
        .replace("ٓ", "")
        .filter { it in "المصكهيرط حعسقن" }
        .replace(" ", "")

    private fun normalizeSpeech(value: String): String = value
        .replace("أ", "ا")
        .replace("إ", "ا")
        .replace("آ", "ا")
        .replace("ى", "ي")
        .replace("ـ", "")
        .replace("َ", "")
        .replace("ً", "")
        .replace("ُ", "")
        .replace("ٌ", "")
        .replace("ِ", "")
        .replace("ٍ", "")
        .replace("ْ", "")
        .replace("ّ", "")
        .replace("ٰ", "")
        .trim()
}
