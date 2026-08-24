package com.example.quranoffline

import com.example.quranoffline.data.CorrectedRecitationError
import com.example.quranoffline.data.CorrectedRecitationErrorCodec
import com.example.quranoffline.data.CorrectionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRecitationErrorStoreTest {
    @Test
    fun codecRoundTripPreservesArabicAndCorrectionKind() {
        val errors = listOf(
            CorrectedRecitationError(
                id = "error-1",
                surah = 2,
                ayah = 3,
                localWordIndex = 4,
                expected = "وَيُقِيمُونَ",
                observed = "ويقيمون",
                correctedWith = "وَيُقِيمُونَ",
                kind = CorrectionKind.WRONG_WORD,
                correctedAt = 1234L,
            ),
            CorrectedRecitationError(
                id = "error-2",
                surah = 2,
                ayah = 3,
                localWordIndex = 5,
                expected = "الصَّلَاةَ",
                observed = null,
                correctedWith = "الصَّلَاةَ",
                kind = CorrectionKind.SKIPPED_WORD,
                correctedAt = 1235L,
            ),
        )

        assertEquals(errors, CorrectedRecitationErrorCodec.decode(CorrectedRecitationErrorCodec.encode(errors)))
    }

    @Test
    fun codecRejectsUnknownSchemaAndMalformedRows() {
        assertTrue(CorrectedRecitationErrorCodec.decode("v99\nerror").isEmpty())
        assertTrue(CorrectedRecitationErrorCodec.decode("v1\nnot-a-row").isEmpty())
    }
}
