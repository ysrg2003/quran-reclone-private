package com.example.quranoffline.data
import org.junit.Test
import org.junit.Assert.*
class QuranComAudioTest {
 @Test fun referencesNameExactAyahAndWordInsteadOfEstimatedOffsets(){assertEquals("quranapi://ayah/1/1",QuranComAudio.ayah(1,1));val u=java.net.URI(QuranComAudio.word(1,1,2,"الرحمن",4));assertEquals("/1/1/3",u.path);assertTrue(u.query.contains("count=4"))}
 @Test fun normalizationOnlyAllowsHarakatAndAlefComparison(){assertEquals(QuranComAudio.normalized("ٱلرَّحْمَٰنِ"),QuranComAudio.normalized("الرحمن"));assertNotEquals(QuranComAudio.normalized("الله"),QuranComAudio.normalized("الرحمن"))}
 @Test fun onlyObservedQuranAudioHostsAndHttpsAllowed(){assertEquals("https://audio.qurancdn.com/wbw/001_001_003.mp3",QuranComAudio.media("wbw/001_001_003.mp3","https://audio.qurancdn.com/"));assertTrue(runCatching{QuranComAudio.media("https://example.org/a.mp3","https://audio.qurancdn.com/")}.isFailure);assertTrue(runCatching{QuranComAudio.media("http://audio.qurancdn.com/a.mp3","https://audio.qurancdn.com/")}.isFailure)}
}
