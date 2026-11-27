package com.example.quranoffline.data

import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A table in the exported report. Cells are String or Number. */
data class ExportTable(val title: String, val headers: List<String>, val rows: List<List<Any>>)

object LogExport {
    private fun stamp(ms: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ms))

    fun sessionsTable(sessions: List<SessionSummary>, surahName: (Int) -> String) = ExportTable(
        "الجلسات",
        listOf("التاريخ", "السورة", "من آية", "إلى آية", "كلمات مطابقة", "إجمالي الكلمات", "غير مؤكد", "الصفحة", "النوع"),
        sessions.sortedByDescending { it.createdAt }.map {
            listOf(stamp(it.createdAt), surahName(it.surah), it.startAyah, it.endAyah, it.matchedWords, it.totalWords, it.uncertainWords, it.page, it.label)
        },
    )

    fun errorsTable(errors: List<CorrectedRecitationError>, surahName: (Int) -> String) = ExportTable(
        "الأخطاء",
        listOf("التاريخ", "السورة", "الآية", "رقم الكلمة", "المتوقع", "المسموع", "التصحيح", "النوع"),
        errors.sortedByDescending { it.correctedAt }.map {
            listOf(stamp(it.correctedAt), surahName(it.surah), it.ayah, it.localWordIndex + 1, it.expected, it.observed.orEmpty(), it.correctedWith.orEmpty(), it.kind.name)
        },
    )

    /** Removes characters XML 1.0 cannot carry, then escapes. */
    fun xml(text: String): String {
        val sb = StringBuilder(text.length + 8)
        for (c in text) {
            when {
                c == '&' -> sb.append("&amp;")
                c == '<' -> sb.append("&lt;")
                c == '>' -> sb.append("&gt;")
                c == '"' -> sb.append("&quot;")
                c.code < 0x20 && c != '\n' && c != '\t' -> {}
                c.code in 0xD800..0xDFFF -> sb.append(c)
                c.code == 0xFFFE || c.code == 0xFFFF -> {}
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun ZipOutputStream.entry(name: String, content: String) {
        putNextEntry(ZipEntry(name)); write(content.toByteArray(Charsets.UTF_8)); closeEntry()
    }

    private fun colName(i: Int): String {
        var n = i; val sb = StringBuilder()
        do { sb.insert(0, ('A' + n % 26)); n = n / 26 - 1 } while (n >= 0)
        return sb.toString()
    }

    private fun sheetName(t: String, used: Set<String>): String {
        val base = t.filter { it !in "[]:*?/\\" }.take(31).ifBlank { "Sheet" }
        var n = base; var i = 2
        while (n in used) { n = base.take(28) + "_" + i; i++ }
        return n
    }

    fun writeXlsx(tables: List<ExportTable>, out: OutputStream) {
        val names = mutableListOf<String>()
        tables.forEach { names += sheetName(it.title, names.toSet()) }
        ZipOutputStream(out).use { z ->
            z.entry("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""" +
                tables.indices.joinToString("") { """<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" } + "</Types>")
            z.entry("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            z.entry("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""" +
                names.indices.joinToString("") { """<sheet name="${xml(names[it])}" sheetId="${it + 1}" r:id="rId${it + 1}"/>""" } + "</sheets></workbook>")
            z.entry("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                tables.indices.joinToString("") { """<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>""" } + "</Relationships>")
            tables.forEachIndexed { ti, t ->
                val sb = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView rightToLeft="1" workbookViewId="0"/></sheetViews><cols><col min="1" max="${t.headers.size.coerceAtLeast(1)}" width="18" customWidth="1"/></cols><sheetData>""")
                fun row(r: Int, cells: List<Any>) {
                    sb.append("<row r=\"$r\">")
                    cells.forEachIndexed { c, v ->
                        val ref = colName(c) + r
                        if (v is Number) sb.append("<c r=\"$ref\"><v>$v</v></c>")
                        else sb.append("<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xml(v.toString())}</t></is></c>")
                    }
                    sb.append("</row>")
                }
                row(1, t.headers)
                t.rows.forEachIndexed { i, r -> row(i + 2, r) }
                sb.append("</sheetData></worksheet>")
                z.entry("xl/worksheets/sheet${ti + 1}.xml", sb.toString())
            }
        }
    }

    fun writeDocx(title: String, tables: List<ExportTable>, out: OutputStream) {
        fun para(text: String, bold: Boolean) =
            """<w:p><w:pPr><w:bidi/><w:jc w:val="left"/></w:pPr><w:r><w:rPr>${if (bold) "<w:b/><w:bCs/>" else ""}<w:rtl/></w:rPr><w:t xml:space="preserve">${xml(text)}</w:t></w:r></w:p>"""
        fun cell(text: String, bold: Boolean) =
            """<w:tc><w:tcPr><w:tcW w:w="0" w:type="auto"/></w:tcPr>${para(text, bold)}</w:tc>"""
        val body = StringBuilder()
        body.append(para(title, true))
        tables.forEach { t ->
            body.append(para(t.title, true))
            body.append("""<w:tbl><w:tblPr><w:tblW w:w="5000" w:type="pct"/><w:bidiVisual/><w:tblBorders><w:top w:val="single" w:sz="4"/><w:left w:val="single" w:sz="4"/><w:bottom w:val="single" w:sz="4"/><w:right w:val="single" w:sz="4"/><w:insideH w:val="single" w:sz="4"/><w:insideV w:val="single" w:sz="4"/></w:tblBorders></w:tblPr>""")
            body.append("<w:tr>" + t.headers.joinToString("") { cell(it, true) } + "</w:tr>")
            t.rows.forEach { r -> body.append("<w:tr>" + r.joinToString("") { cell(it.toString(), false) } + "</w:tr>") }
            if (t.rows.isEmpty()) body.append("<w:tr>" + cell("لا توجد بيانات", false) + t.headers.drop(1).joinToString("") { cell("", false) } + "</w:tr>")
            body.append("</w:tbl>").append(para("", false))
        }
        ZipOutputStream(out).use { z ->
            z.entry("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""")
            z.entry("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""")
            z.entry("word/document.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="900" w:right="700" w:bottom="900" w:left="700" w:header="0" w:footer="0" w:gutter="0"/></w:sectPr></w:body></w:document>""")
        }
    }
}
