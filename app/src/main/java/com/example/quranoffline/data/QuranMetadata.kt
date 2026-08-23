package com.example.quranoffline.data

import android.content.Context
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/** Arabic-only structural metadata for the canonical Mushaf order. */
data class QuranSuraMetadata(
    val index: Int,
    val ayahCount: Int,
    val globalStart: Int,
    val arabicName: String,
    val revelationType: RevelationType,
    val revelationOrder: Int,
    val rukuCount: Int,
)

enum class RevelationType {
    MECCAN,
    MEDINAN,
}

data class QuranBoundary(
    val index: Int,
    val surah: Int,
    val ayah: Int,
    val globalAyahIndex: Int,
)

enum class SajdaType {
    RECOMMENDED,
    OBLIGATORY,
}

data class QuranSajdaMetadata(
    val index: Int,
    val surah: Int,
    val ayah: Int,
    val globalAyahIndex: Int,
    val type: SajdaType,
)

data class QuranAyahMetadata(
    val surah: Int,
    val ayah: Int,
    val globalAyahIndex: Int,
    val page: Int,
    val juz: Int,
    val quarter: Int,
    val manzil: Int,
    val ruku: Int,
    val sajda: SajdaType?,
)

data class QuranMetadata(
    val suras: List<QuranSuraMetadata>,
    val juzs: List<QuranBoundary>,
    val quarters: List<QuranBoundary>,
    val manzils: List<QuranBoundary>,
    val rukus: List<QuranBoundary>,
    val pages: List<QuranBoundary>,
    val sajdas: List<QuranSajdaMetadata>,
) {
    private val suraByNumber = suras.associateBy { it.index }
    private val ayahByKey: Map<Long, QuranAyahMetadata> = buildAyahIndex()

    fun sura(number: Int): QuranSuraMetadata? = suraByNumber[number]

    fun ayah(surah: Int, ayah: Int): QuranAyahMetadata? = ayahByKey[key(surah, ayah)]

    /** Fails loudly if metadata and the loaded Quran text do not describe the same Mushaf order. */
    fun requireCompatible(ayahs: List<QuranAyah>) {
        require(suras.size == 114) { "Metadata must contain 114 suras" }
        require(ayahs.size == 6236) { "Quran text must contain 6236 ayahs" }
        suras.forEach { sura ->
            val actual = ayahs.count { it.surah == sura.index }
            require(actual == sura.ayahCount) { "Metadata mismatch for surah ${sura.index}: ${sura.ayahCount} != $actual" }
            val first = ayahs.indexOfFirst { it.surah == sura.index && it.ayah == 1 }
            require(first == sura.globalStart) { "Metadata start mismatch for surah ${sura.index}" }
        }
        ayahs.forEachIndexed { globalIndex, quranAyah ->
            val metadata = ayah(quranAyah.surah, quranAyah.ayah)
            require(metadata?.globalAyahIndex == globalIndex) {
                "Metadata order mismatch at ${quranAyah.surah}:${quranAyah.ayah}"
            }
        }
    }

    private fun buildAyahIndex(): Map<Long, QuranAyahMetadata> {
        val latestJuz = latestBoundaryByAyah(juzs)
        val latestQuarter = latestBoundaryByAyah(quarters)
        val latestManzil = latestBoundaryByAyah(manzils)
        val latestRuku = latestBoundaryByAyah(rukus)
        val latestPage = latestBoundaryByAyah(pages)
        val sajdaByGlobal = sajdas.associateBy { it.globalAyahIndex }
        return buildMap {
            suras.forEach { sura ->
                for (localAyah in 1..sura.ayahCount) {
                    val global = sura.globalStart + localAyah - 1
                    put(
                        key(sura.index, localAyah),
                        QuranAyahMetadata(
                            surah = sura.index,
                            ayah = localAyah,
                            globalAyahIndex = global,
                            page = latestPage[global] ?: 1,
                            juz = latestJuz[global] ?: 1,
                            quarter = latestQuarter[global] ?: 1,
                            manzil = latestManzil[global] ?: 1,
                            ruku = latestRuku[global] ?: 1,
                            sajda = sajdaByGlobal[global]?.type,
                        ),
                    )
                }
            }
        }
    }

    private fun latestBoundaryByAyah(boundaries: List<QuranBoundary>): Map<Int, Int> {
        val result = HashMap<Int, Int>()
        var boundaryIndex = 0
        var current = boundaries.firstOrNull()?.index ?: 1
        for (global in 0 until 6236) {
            while (boundaryIndex + 1 < boundaries.size && boundaries[boundaryIndex + 1].globalAyahIndex <= global) {
                boundaryIndex++
                current = boundaries[boundaryIndex].index
            }
            result[global] = current
        }
        return result
    }

    companion object {
        private fun key(surah: Int, ayah: Int): Long = (surah.toLong() shl 32) or (ayah.toLong() and 0xffffffffL)

        fun parse(xml: String): QuranMetadata {
            require(xml.isNotBlank()) { "Quran metadata XML is empty" }
            val builder = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            }.newDocumentBuilder()
            val root = builder.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))).documentElement
            require(root.tagName == "quran" && root.getAttribute("type") == "metadata") { "Unsupported Quran metadata root" }
            val suraRows = childElements(root, "suras", "sura")
            val suras = suraRows.map { row ->
                QuranSuraMetadata(
                    index = intAttr(row, "index"),
                    ayahCount = intAttr(row, "ayas"),
                    globalStart = intAttr(row, "start"),
                    arabicName = row.getAttribute("name"),
                    revelationType = when (row.getAttribute("type")) {
                        "Meccan" -> RevelationType.MECCAN
                        "Medinan" -> RevelationType.MEDINAN
                        else -> error("Unsupported Arabic surah type")
                    },
                    revelationOrder = intAttr(row, "order"),
                    rukuCount = intAttr(row, "rukus"),
                )
            }
            val starts = suras.associate { it.index to it.globalStart }
            fun boundarySection(parent: String, child: String): List<QuranBoundary> =
                childElements(root, parent, child).map { row ->
                    val surah = intAttr(row, "sura")
                    val ayah = intAttr(row, "aya")
                    QuranBoundary(intAttr(row, "index"), surah, ayah, globalIndex(starts, suras, surah, ayah))
                }
            val sajdas = childElements(root, "sajdas", "sajda").map { row ->
                val surah = intAttr(row, "sura")
                val ayah = intAttr(row, "aya")
                QuranSajdaMetadata(
                    index = intAttr(row, "index"),
                    surah = surah,
                    ayah = ayah,
                    globalAyahIndex = globalIndex(starts, suras, surah, ayah),
                    type = when (row.getAttribute("type")) {
                        "recommended" -> SajdaType.RECOMMENDED
                        "obligatory" -> SajdaType.OBLIGATORY
                        else -> error("Unsupported sajda type")
                    },
                )
            }
            val metadata = QuranMetadata(
                suras = suras,
                juzs = boundarySection("juzs", "juz"),
                quarters = boundarySection("hizbs", "quarter"),
                manzils = boundarySection("manzils", "manzil"),
                rukus = boundarySection("rukus", "ruku"),
                pages = boundarySection("pages", "page"),
                sajdas = sajdas,
            )
            validateStructure(metadata)
            return metadata
        }

        private fun validateStructure(metadata: QuranMetadata) {
            require(metadata.suras.map { it.index } == (1..114).toList())
            require(metadata.suras.sumOf { it.ayahCount } == 6236)
            require(metadata.suras.first().globalStart == 0)
            require(metadata.suras.zipWithNext().all { (a, b) -> a.globalStart + a.ayahCount == b.globalStart })
            require(metadata.suras.last().globalStart + metadata.suras.last().ayahCount == 6236)
            requireBoundaryIndexes(metadata.juzs, 30)
            requireBoundaryIndexes(metadata.quarters, 240)
            requireBoundaryIndexes(metadata.manzils, 7)
            requireBoundaryIndexes(metadata.rukus, 556)
            requireBoundaryIndexes(metadata.pages, 604)
            require(metadata.sajdas.size == 15)
        }

        private fun requireBoundaryIndexes(boundaries: List<QuranBoundary>, expectedCount: Int) {
            require(boundaries.size == expectedCount)
            require(boundaries.map { it.index } == (1..expectedCount).toList())
            require(boundaries.zipWithNext().all { (a, b) -> a.globalAyahIndex < b.globalAyahIndex })
        }

        private fun globalIndex(
            starts: Map<Int, Int>,
            suras: List<QuranSuraMetadata>,
            surah: Int,
            ayah: Int,
        ): Int {
            val sura = suras.firstOrNull { it.index == surah } ?: error("Unknown surah $surah")
            require(ayah in 1..sura.ayahCount) { "Invalid ayah $surah:$ayah" }
            return (starts[surah] ?: error("Missing surah start")) + ayah - 1
        }

        private fun childElements(root: Element, parentName: String, childName: String): List<Element> {
            val parent = root.childNodes.asSequence().filterIsInstance<Element>().firstOrNull { it.tagName == parentName }
                ?: error("Missing metadata section $parentName")
            return parent.childNodes.asSequence().filterIsInstance<Element>().filter { it.tagName == childName }.toList()
        }

        private fun intAttr(element: Element, name: String): Int =
            element.getAttribute(name).toIntOrNull() ?: error("Missing integer attribute $name")

        private fun NodeListSequence(nodeList: org.w3c.dom.NodeList): Sequence<Node> = sequence {
            for (index in 0 until nodeList.length) yield(nodeList.item(index))
        }

        private fun org.w3c.dom.NodeList.asSequence(): Sequence<Node> = NodeListSequence(this)
    }
}

class QuranMetadataRepository(private val context: Context) {
    fun load(): QuranMetadata = context.assets.open("quran/quran-meta-data.xml").bufferedReader(Charsets.UTF_8).use { reader ->
        QuranMetadata.parse(reader.readText())
    }
}

fun RevelationType.displayArabic(): String = when (this) {
    RevelationType.MECCAN -> "مكية"
    RevelationType.MEDINAN -> "مدنية"
}

fun SajdaType.displayArabic(): String = when (this) {
    SajdaType.RECOMMENDED -> "سجدة تلاوة مستحبة"
    SajdaType.OBLIGATORY -> "سجدة تلاوة واجبة"
}
